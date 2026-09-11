package com.satpam.piket.data.network

import com.satpam.piket.data.model.Absensi
import com.satpam.piket.data.model.Petugas
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

data class AbsensiRequest(
    val id: String,
    val shiftId: String,
    val petugasId: String,
    val nama: String,
    val lokasi: String,
    val tanggal: String,
    val shift: String,
    val tipeAbsen: String = "MASUK",
    val jamMasuk: String,
    val jamKeluar: String?,
    val durasi: Int?,
    val statusKeterlambatan: String,
    val keterangan: String = "",
    val latitude: Double?,
    val longitude: Double?,
    val fotoBase64: String? = null
)

data class ApiResponse(val success: Boolean, val message: String, val data: Any? = null)

data class PendingPetugasRequest(
    val action: String = "submitPendingPetugas",
    val id: String,
    val nama: String,
    val jabatan: String,
    val lokasi: String,          // lokasi penempatan petugas
    val fotoBase64: String       // foto referensi wajah dalam format base64
)

data class UploadFotoJsonRequest(
    val action: String = "uploadFoto",
    val id: String,
    val fotoBase64: String
)

interface ApiService {
    @POST("exec")
    suspend fun submitAbsensi(
        @Query("action") action: String = "submitAbsensi",
        @Body body: AbsensiRequest
    ): Response<ApiResponse>

    @GET("exec")
    suspend fun getPetugasList(
        @Query("action") action: String = "getPetugas"
    ): Response<List<Petugas>>

    @GET("exec")
    suspend fun getDashboardData(
        @Query("action") action: String = "getDashboard",
        @Query("tanggal") tanggal: String
    ): Response<ResponseBody>

    @POST("exec")
    suspend fun uploadFotoJson(
        @Query("action") action: String = "uploadFoto",
        @Body body: UploadFotoJsonRequest
    ): Response<ApiResponse>

    @Multipart
    @POST("exec")
    suspend fun uploadFoto(
        @Query("action") action: String = "uploadFoto",
        @Part("id") id: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<ApiResponse>

    /** Kirim data enrol petugas baru ke backend untuk menunggu approval admin */
    @POST("exec")
    suspend fun submitPendingPetugas(
        @Query("action") action: String = "submitPendingPetugas",
        @Body body: PendingPetugasRequest
    ): Response<ApiResponse>

    /** Ambil setting koordinat GPS dan radius geofence untuk setiap lokasi */
    @GET("exec")
    suspend fun getLokasiKonfig(
        @Query("action") action: String = "getLokasiKonfig"
    ): Response<List<com.satpam.piket.data.model.LokasiKonfig>>

    /** Cek versi aplikasi terbaru dari server backend */
    @GET("exec")
    suspend fun getLatestVersion(
        @Query("action") action: String = "getLatestVersion"
    ): Response<com.satpam.piket.data.model.AppVersionInfo>
}



fun Absensi.toRequest(fotoBase64: String? = null) = AbsensiRequest(
    id = id,
    shiftId = shiftId,
    petugasId = petugasId,
    nama = nama,
    lokasi = lokasi,
    tanggal = tanggal,
    shift = shift.name,
    tipeAbsen = tipeAbsen,
    jamMasuk = jamMasuk,
    jamKeluar = jamKeluar,
    durasi = durasi,
    statusKeterlambatan = statusKeterlambatan.name,
    keterangan = keterangan,
    latitude = latitude,
    longitude = longitude,
    fotoBase64 = fotoBase64
)
