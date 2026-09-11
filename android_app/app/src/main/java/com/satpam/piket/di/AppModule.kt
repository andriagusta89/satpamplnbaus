package com.satpam.piket.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.satpam.piket.data.db.AbsensiDao
import com.satpam.piket.data.db.AppDatabase
import com.satpam.piket.data.db.LokasiDao
import com.satpam.piket.data.db.MIGRATION_1_2
import com.satpam.piket.data.db.MIGRATION_2_3
import com.satpam.piket.data.db.MIGRATION_3_4
import com.satpam.piket.data.db.PetugasDao
import com.satpam.piket.data.network.ApiService
import com.satpam.piket.domain.FaceRecognitionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "satpam_prefs")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "satpam_piket_db"
        )
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
        .build()
    }

    @Provides
    fun provideAbsensiDao(db: AppDatabase): AbsensiDao = db.absensiDao()

    @Provides
    fun providePetugasDao(db: AppDatabase): PetugasDao = db.petugasDao()

    @Provides
    fun provideLokasiDao(db: AppDatabase): LokasiDao = db.lokasiDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(dataStore: DataStore<Preferences>): OkHttpClient {
        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val dynamicUrlInterceptor = Interceptor { chain ->
            var request = chain.request()
            try {
                val configuredUrl = runBlocking {
                    dataStore.data.map { it[stringPreferencesKey("api_url")] ?: "" }.firstOrNull()
                }
                if (!configuredUrl.isNullOrBlank()) {
                    var cleanUrl = configuredUrl.trim()
                    if (cleanUrl.endsWith("/dev")) {
                        cleanUrl = cleanUrl.substring(0, cleanUrl.length - 4) + "/exec"
                    }
                    if (!cleanUrl.endsWith("/exec")) {
                        cleanUrl = if (cleanUrl.endsWith("/")) cleanUrl + "exec" else "$cleanUrl/exec"
                    }
                    val targetHttpUrl = cleanUrl.toHttpUrlOrNull()
                    if (targetHttpUrl != null) {
                        val newUrl = request.url.newBuilder()
                            .scheme(targetHttpUrl.scheme)
                            .host(targetHttpUrl.host)
                            .port(targetHttpUrl.port)
                            .encodedPath(targetHttpUrl.encodedPath)
                            .build()
                        request = request.newBuilder().url(newUrl).build()
                    }
                }
            } catch (e: Exception) {
                // Fallback to default request url
            }
            chain.proceed(request)
        }

        return OkHttpClient.Builder()
            .addInterceptor(dynamicUrlInterceptor)
            .addInterceptor(logger)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit {
        val gson = com.google.gson.GsonBuilder()
            .setLenient()
            .registerTypeAdapter(Long::class.java, com.google.gson.JsonDeserializer<Long> { json, _, _ ->
                try {
                    json.asLong
                } catch (e: Exception) {
                    try {
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
                        sdf.parse(json.asString)?.time ?: System.currentTimeMillis()
                    } catch (e2: Exception) {
                        System.currentTimeMillis()
                    }
                }
            })
            .create()

        return Retrofit.Builder()
            .baseUrl("https://script.google.com/macros/s/AKfycbxTIZvMf3DGQM5ecrohFobHNhCGwvZGUNOg-av19T4/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService = retrofit.create(ApiService::class.java)

    @Provides
    @Singleton
    fun provideFaceRecognitionManager(@ApplicationContext context: Context): FaceRecognitionManager {
        return FaceRecognitionManager(context)
    }

    @Provides
    @Singleton
    fun provideGeofenceManager(@ApplicationContext context: Context): com.satpam.piket.domain.GeofenceManager {
        return com.satpam.piket.domain.GeofenceManager(context)
    }

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }
}
