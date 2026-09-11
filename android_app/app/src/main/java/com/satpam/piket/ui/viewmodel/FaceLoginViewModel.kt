package com.satpam.piket.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.satpam.piket.data.db.AbsensiDao
import com.satpam.piket.data.db.LokasiDao
import com.satpam.piket.data.db.PetugasDao
import com.satpam.piket.data.model.Absensi
import com.satpam.piket.data.model.Petugas
import com.satpam.piket.data.model.Shift
import com.satpam.piket.data.network.ApiService
import com.satpam.piket.data.network.toRequest
import com.satpam.piket.domain.FaceRecognitionManager
import com.satpam.piket.domain.GeofenceCheckResult
import com.satpam.piket.domain.GeofenceManager
import com.satpam.piket.domain.ShiftManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.UUID
import javax.inject.Inject

sealed class FaceLoginState {
    object Idle : FaceLoginState()
    object Scanning : FaceLoginState()
    data class FaceDetected(val label: String) : FaceLoginState()
    data class Recognized(val petugas: Petugas, val hasMasuk: Boolean = false) : FaceLoginState()
    object NotRecognized : FaceLoginState()
    object CheckingLocation : FaceLoginState()
    data class LocationTooFar(
        val petugas: Petugas,
        val distanceMeters: Float,
        val radiusMeter: Int,
        val namaLokasi: String
    ) : FaceLoginState()
    data class LocationError(val message: String) : FaceLoginState()
    data class CheckInSuccess(val absensi: Absensi) : FaceLoginState()
    data class Error(val message: String) : FaceLoginState()
}

@HiltViewModel
class FaceLoginViewModel @Inject constructor(
    private val absensiDao: AbsensiDao,
    private val petugasDao: PetugasDao,
    private val lokasiDao: LokasiDao,
    private val faceRecognitionManager: FaceRecognitionManager,
    private val geofenceManager: GeofenceManager,
    private val apiService: ApiService
) : ViewModel() {

    private val _state = MutableStateFlow<FaceLoginState>(FaceLoginState.Idle)
    val state: StateFlow<FaceLoginState> = _state.asStateFlow()

    private val _currentShift = MutableStateFlow(ShiftManager.getCurrentShift())
    val currentShift: StateFlow<Shift> = _currentShift.asStateFlow()

    private val _shiftId = MutableStateFlow(ShiftManager.generateShiftId())
    val shiftId: StateFlow<String> = _shiftId.asStateFlow()

    private val _currentTime = MutableStateFlow(ShiftManager.getCurrentTimeString())
    val currentTime: StateFlow<String> = _currentTime.asStateFlow()

    private var lastCaptureBitmap: Bitmap? = null

    init {
        // Sinkronisasi data titik lokasi & radius dari backend
        viewModelScope.launch {
            try {
                val response = apiService.getLokasiKonfig()
                if (response.isSuccessful && response.body() != null) {
                    lokasiDao.insertAll(response.body()!!)
                }
            } catch (e: Exception) {
                // Gunakan cache database lokal jika offline
            }
        }

        // Sinkronisasi status petugas yang disetujui admin di backend
        viewModelScope.launch {
            try {
                val response = apiService.getPetugasList()
                if (response.isSuccessful && response.body() != null) {
                    val serverApproved = response.body()!!
                    val localList = petugasDao.getAllPetugas().firstOrNull() ?: emptyList()
                    for (local in localList) {
                        if (!local.aktif) {
                            val isApproved = serverApproved.any {
                                it.id == local.id || it.nama.equals(local.nama, ignoreCase = true)
                            }
                            if (isApproved) {
                                petugasDao.updatePetugas(local.copy(aktif = true))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Abaikan jika offline
            }
        }
    }

    fun updateTime() {
        _currentTime.value = ShiftManager.getCurrentTimeString()
        _currentShift.value = ShiftManager.getCurrentShift()
        _shiftId.value = ShiftManager.generateShiftId()
    }

    fun processFrame(bitmap: Bitmap) {
        viewModelScope.launch {
            _state.value = FaceLoginState.Scanning
            try {
                val activePetugas = petugasDao.getActivePetugas()
                if (activePetugas.isEmpty()) {
                    _state.value = FaceLoginState.Error("Tidak ada petugas terdaftar. Daftarkan petugas di Pengaturan.")
                    return@launch
                }

                val faces = faceRecognitionManager.detectFaces(bitmap)
                if (faces.isEmpty()) {
                    _state.value = FaceLoginState.Idle
                    return@launch
                }

                _state.value = FaceLoginState.FaceDetected("Wajah terdeteksi, mencocokkan...")

                val petugas = faceRecognitionManager.recognizeFace(bitmap, activePetugas)
                if (petugas != null) {
                    // Simpan salinan bitmap aman yang telah di-scale agar tidak hilang atau di-recycle CameraX
                    lastCaptureBitmap = FaceRecognitionManager.scaleBitmap(FaceRecognitionManager.ensurePortrait(bitmap), 480)
                    val lastRecord = absensiDao.getLastAbsensiByPetugas(petugas.id, petugas.nama)
                    val hasMasuk = lastRecord != null && lastRecord.tipeAbsen == "MASUK"
                    _state.value = FaceLoginState.Recognized(petugas, hasMasuk)
                } else {
                    _state.value = FaceLoginState.NotRecognized
                }
            } catch (e: Exception) {
                _state.value = FaceLoginState.Error("Error: ${e.message}")
            }
        }
    }

    fun confirmCheckIn(petugas: Petugas, tipeAbsen: String = "MASUK", keterangan: String = "") {
        viewModelScope.launch {
            _state.value = FaceLoginState.CheckingLocation
            try {
                // Aturan: Tidak bisa Lepas Piket (KELUAR) jika belum pernah absen masuk!
                if (tipeAbsen == "KELUAR") {
                    val lastRecord = absensiDao.getLastAbsensiByPetugas(petugas.id, petugas.nama)
                    val hasMasuk = lastRecord != null && lastRecord.tipeAbsen == "MASUK"
                    if (!hasMasuk) {
                        _state.value = FaceLoginState.Error("Tidak bisa lepas piket! Petugas ${petugas.nama} belum melakukan Absen Masuk.")
                        return@launch
                    }
                }

                // 1. Ambil config lokasi petugas dari DB lokal
                val lokasiNama = petugas.lokasi.ifBlank { "Kantor UP3 Baubau" }
                val lokasiKonfig = lokasiDao.getLokasiByName(lokasiNama)

                // 2. Validasi Geofence GPS
                var userLat: Double? = null
                var userLng: Double? = null

                when (val geoResult = geofenceManager.checkGeofence(lokasiKonfig)) {
                    is GeofenceCheckResult.TooFar -> {
                        _state.value = FaceLoginState.LocationTooFar(
                            petugas = petugas,
                            distanceMeters = geoResult.distanceMeters,
                            radiusMeter = geoResult.radiusMeter,
                            namaLokasi = geoResult.namaLokasi
                        )
                        return@launch
                    }
                    is GeofenceCheckResult.NoPermission -> {
                        _state.value = FaceLoginState.LocationError("Izin lokasi GPS belum diaktifkan. Mohon izinkan akses lokasi.")
                        return@launch
                    }
                    is GeofenceCheckResult.LocationUnavailable -> {
                        _state.value = FaceLoginState.LocationError("Sinyal GPS tidak ditemukan. Pastikan GPS perangkat Anda telah aktif.")
                        return@launch
                    }
                    is GeofenceCheckResult.Valid -> {
                        userLat = geoResult.latitude
                        userLng = geoResult.longitude
                    }
                    is GeofenceCheckResult.Unconfigured -> {
                        userLat = geoResult.latitude
                        userLng = geoResult.longitude
                    }
                }

                val bitmap = lastCaptureBitmap
                val fotoPath = if (bitmap != null) {
                    faceRecognitionManager.saveFacePhoto(bitmap)
                } else ""

                val now = System.currentTimeMillis()
                val shift = ShiftManager.getCurrentShift(now)
                val shiftId = ShiftManager.generateShiftId(now)
                val status = ShiftManager.calculateLateStatus(now, shift)
                val timeStr = ShiftManager.getCurrentTimeString()

                val lastMasuk = if (tipeAbsen == "KELUAR") absensiDao.getLastMasukByPetugas(petugas.id, petugas.nama) else null
                val jamMasukStr = if (tipeAbsen == "MASUK") timeStr else (lastMasuk?.jamMasuk ?: "")
                val jamKeluarStr = if (tipeAbsen == "KELUAR") timeStr else null
                val durasiMnt = if (tipeAbsen == "KELUAR" && lastMasuk != null) {
                    ShiftManager.calculateDuration(lastMasuk.createdAt, now)
                } else null
                val keteranganStr = keterangan.ifBlank { "Situasi aman terkendali" }

                val absensi = Absensi(
                    id = UUID.randomUUID().toString(),
                    shiftId = shiftId,
                    petugasId = petugas.id,
                    nama = petugas.nama,
                    lokasi = lokasiNama,
                    tanggal = ShiftManager.getCurrentDateString(),
                    shift = shift,
                    tipeAbsen = tipeAbsen,
                    jamMasuk = jamMasukStr,
                    jamKeluar = jamKeluarStr,
                    durasi = durasiMnt,
                    statusKeterlambatan = status,
                    keterangan = keteranganStr,
                    fotoPath = fotoPath,
                    latitude = userLat,
                    longitude = userLng,
                    isSynced = false
                )

                absensiDao.insertAbsensi(absensi)
                _state.value = FaceLoginState.CheckInSuccess(absensi)

                // Segera kirim ke backend jika online agar notifikasi WhatsApp pergantian shift terkirim real-time
                viewModelScope.launch {
                    try {
                        var base64Photo: String? = null
                        val fotoFile = File(absensi.fotoPath)
                        if (fotoFile.exists() && fotoFile.length() > 0) {
                            try {
                                val bytes = fotoFile.readBytes()
                                base64Photo = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                                android.util.Log.d("FaceLogin", "Foto absensi ${absensi.id} disiapkan (${bytes.size} bytes, base64 length: ${base64Photo.length})")
                            } catch (readErr: Exception) {
                                android.util.Log.w("FaceLogin", "Gagal membaca foto absensi: ${readErr.message}")
                            }
                        } else {
                            android.util.Log.w("FaceLogin", "Foto absensi tidak ditemukan / kosong di path: ${absensi.fotoPath}")
                        }

                        val req = absensi.toRequest(fotoBase64 = base64Photo)
                        val submitResp = apiService.submitAbsensi(body = req)
                        if (submitResp.isSuccessful) {
                            var fotoUrl: String? = null
                            val resData = submitResp.body()?.data
                            if (resData is Map<*, *>) {
                                fotoUrl = resData["fotoUrl"]?.toString()
                            } else if (resData is String) {
                                fotoUrl = resData
                            }
                            absensiDao.markAsSynced(absensi.id, fotoUrl)
                            android.util.Log.i("FaceLogin", "Absensi ${absensi.id} berhasil disinkronkan, fotoUrl: $fotoUrl")
                        } else {
                            android.util.Log.w("FaceLogin", "Submit absensi backend gagal: code=${submitResp.code()}, msg=${submitResp.message()}")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("FaceLogin", "Gagal auto-sync: ${e.message}")
                        // Fallback ke SyncWorker jika offline
                    }
                }

            } catch (e: Exception) {
                _state.value = FaceLoginState.Error("Gagal menyimpan absensi: ${e.message}")
            }
        }
    }

    fun resetState() {
        _state.value = FaceLoginState.Idle
        lastCaptureBitmap = null
    }
}
