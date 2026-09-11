package com.satpam.piket.domain

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.satpam.piket.data.model.AppVersionInfo
import com.satpam.piket.data.network.ApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: ApiService,
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        const val CHANNEL_ID = "app_update_channel"
        const val NOTIFICATION_ID = 9901
        val API_URL_PREF = stringPreferencesKey("api_url")
    }

    fun getCurrentVersionCode(): Int {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            PackageInfoCompat.getLongVersionCode(pInfo).toInt()
        } catch (e: Exception) {
            1
        }
    }

    fun getCurrentVersionName(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    /**
     * Memeriksa versi terbaru dari backend.
     * Otomatis menyinkronkan URL backend jika di-set di backend,
     * dan mengembalikan AppVersionInfo jika ada versi APK baru di server.
     */
    suspend fun checkUpdate(): AppVersionInfo? {
        return try {
            val response = apiService.getLatestVersion()
            if (response.isSuccessful && response.body() != null) {
                val info = response.body()!!

                // Otomatis sinkronkan URL backend dari server ke APK user
                if (!info.apiUrl.isNullOrBlank()) {
                    try {
                        dataStore.edit { prefs ->
                            if (prefs[API_URL_PREF] != info.apiUrl) {
                                prefs[API_URL_PREF] = info.apiUrl
                            }
                        }
                    } catch (e: Exception) {
                        // Safe fallback
                    }
                }

                val currentCode = getCurrentVersionCode()
                if (info.versionCode > currentCode && info.downloadUrl.isNotBlank()) {
                    showSystemNotification(info)
                    info
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Membuka link download file APK di Google Drive melalui browser atau Download Manager
     */
    fun openDownloadPage(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Abaikan jika tidak dapat membuka browser
        }
    }

    private fun showSystemNotification(info: AppVersionInfo) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Pembaruan Aplikasi",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifikasi rilis versi terbaru Satpam Piket"
                }
                nm.createNotificationChannel(channel)
            }

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notes = if (info.releaseNotes.isNotBlank()) {
                info.releaseNotes
            } else {
                "Tersedia pembaruan fitur & perbaikan sistem."
            }

            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("🚀 Update Satpam Piket v${info.versionName}")
                .setContentText("Versi baru tersedia di Google Drive. Ketuk untuk download APK.")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "Versi ${info.versionName} (Build ${info.versionCode}) telah tersedia di Google Drive.\n\n" +
                        "Catatan:\n$notes\n\n" +
                        "Ketuk notifikasi ini untuk mengunduh file APK."
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            nm.notify(NOTIFICATION_ID, notif)
        } catch (e: Exception) {
            // Notification error handled safely
        }
    }
}
