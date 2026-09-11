package com.satpam.piket.data.network

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.satpam.piket.data.db.AbsensiDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val absensiDao: AbsensiDao,
    private val apiService: ApiService
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val unsyncedList = absensiDao.getUnsyncedAbsensi()
            Log.d(TAG, "Syncing ${unsyncedList.size} unsynced records")

            if (unsyncedList.isEmpty()) return Result.success()

            var allSuccess = true
            for (absensi in unsyncedList) {
                try {
                    var base64Photo: String? = null
                    val fotoFile = File(absensi.fotoPath)
                    if (fotoFile.exists() && fotoFile.length() > 0) {
                        try {
                            val bytes = fotoFile.readBytes()
                            base64Photo = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                            Log.d(TAG, "Foto absensi ${absensi.id} disiapkan (${bytes.size} bytes)")
                        } catch (readErr: Exception) {
                            Log.w(TAG, "Gagal membaca foto absensi ${absensi.id}: ${readErr.message}")
                        }
                    }

                    // 1. Submit data absensi & foto sekaligus (atomic)
                    val response = apiService.submitAbsensi(body = absensi.toRequest(fotoBase64 = base64Photo))
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Failed to submit absensi ${absensi.id}: ${response.code()}")
                        allSuccess = false
                        continue
                    }

                    var fotoUrl: String? = null
                    val resData = response.body()?.data
                    if (resData is Map<*, *>) {
                        fotoUrl = resData["fotoUrl"]?.toString()
                    } else if (resData is String) {
                        fotoUrl = resData
                    }

                    // 2. Tandai sebagai tersinkron
                    absensiDao.markAsSynced(absensi.id, fotoUrl)
                    Log.d(TAG, "Synced absensi ${absensi.id}")

                } catch (e: Exception) {
                    Log.e(TAG, "Error syncing absensi ${absensi.id}: ${e.message}")
                    allSuccess = false
                }
            }

            if (allSuccess) Result.success() else Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        const val TAG = "SyncWorker"
        const val WORK_NAME = "satpam_sync_work"

        fun buildRequest(): PeriodicWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            return PeriodicWorkRequestBuilder<SyncWorker>(
                repeatInterval = 15,
                repeatIntervalTimeUnit = TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
        }

        fun buildOneTimeRequest(): OneTimeWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            return OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()
        }
    }
}
