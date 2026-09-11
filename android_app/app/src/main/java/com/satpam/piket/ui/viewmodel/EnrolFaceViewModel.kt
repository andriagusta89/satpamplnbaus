package com.satpam.piket.ui.viewmodel

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.satpam.piket.data.db.PetugasDao
import com.satpam.piket.data.model.Petugas
import com.satpam.piket.data.network.ApiService
import com.satpam.piket.data.network.PendingPetugasRequest
import com.satpam.piket.domain.FaceRecognitionManager
import com.satpam.piket.ui.screen.API_URL_KEY
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.inject.Inject

/**
 * State untuk layar enrol wajah petugas
 */
sealed class EnrolFaceState {
    object Idle : EnrolFaceState()
    object Scanning : EnrolFaceState()
    /** Wajah sudah terdeteksi, siap di-capture */
    data class FaceDetected(val faceBitmap: Bitmap) : EnrolFaceState()
    object Capturing : EnrolFaceState()
    /** Tersimpan lokal, sedang mengirim ke server untuk approval */
    object SendingToServer : EnrolFaceState()
    /** Berhasil — menunggu approval admin di dashboard */
    data class Success(val petugasId: String) : EnrolFaceState()
    data class Error(val message: String) : EnrolFaceState()
}

@HiltViewModel
class EnrolFaceViewModel @Inject constructor(
    private val petugasDao: PetugasDao,
    private val faceRecognitionManager: FaceRecognitionManager,
    private val apiService: ApiService,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    private val _state = MutableStateFlow<EnrolFaceState>(EnrolFaceState.Idle)
    val state: StateFlow<EnrolFaceState> = _state.asStateFlow()

    /**
     * Analisa frame kamera, deteksi apakah ada wajah.
     * Dipanggil setiap frame saat kamera aktif.
     */
    fun detectFace(bitmap: Bitmap) {
        viewModelScope.launch {
            _state.value = EnrolFaceState.Scanning
            try {
                val faces = faceRecognitionManager.detectFaces(bitmap)
                _state.value = if (faces.isNotEmpty()) EnrolFaceState.FaceDetected(bitmap)
                               else EnrolFaceState.Idle
            } catch (e: Exception) {
                _state.value = EnrolFaceState.Idle
            }
        }
    }

    /**
     * Ambil foto wajah, simpan lokal, lalu kirim ke backend untuk approval admin.
     *
     * Alur:
     * 1. Simpan foto referensi di storage lokal HP
     * 2. Simpan data Petugas ke Room DB (aktif = false, menunggu approve)
     * 3. Kirim ke Google Apps Script -> sheet pending_petugas
     * 4. Setelah admin approve di dashboard, admin bisa aktifkan manual atau via sync
     */
    fun captureAndRegister(nama: String, jabatan: String, lokasi: String) {
        val currentState = _state.value
        if (currentState !is EnrolFaceState.FaceDetected) return

        viewModelScope.launch {
            _state.value = EnrolFaceState.Capturing
            try {
                val bitmap = currentState.faceBitmap
                val existingPetugas = petugasDao.getAllPetugasList()
                val petugasId = com.satpam.piket.domain.PetugasIdGenerator.generateNextId(existingPetugas)

                // 1. Simpan foto referensi ke storage lokal
                val fotoPath = faceRecognitionManager.saveReferencePhoto(bitmap, petugasId)

                // 2. Simpan ke Room DB — aktif = false sampai admin approve
                val petugas = Petugas(
                    id = petugasId,
                    nama = nama,
                    jabatan = jabatan,
                    lokasi = lokasi,
                    fotoReferensi = fotoPath,
                    aktif = false
                )
                petugasDao.insertPetugas(petugas)

                // 3. Kirim ke backend
                _state.value = EnrolFaceState.SendingToServer
                sendToBackend(bitmap, petugasId, nama, jabatan, lokasi)

            } catch (e: Exception) {
                _state.value = EnrolFaceState.Error("Gagal mendaftarkan: ${e.message}")
            }
        }
    }

    /** Resize bitmap agar ringan dan cepat diupload */
    private fun scaleBitmap(source: Bitmap, maxDimension: Int = 480): Bitmap {
        val width = source.width
        val height = source.height
        if (width <= maxDimension && height <= maxDimension) return source

        val ratio = width.toFloat() / height.toFloat()
        val targetWidth: Int
        val targetHeight: Int
        if (ratio > 1) {
            targetWidth = maxDimension
            targetHeight = (maxDimension / ratio).toInt()
        } else {
            targetHeight = maxDimension
            targetWidth = (maxDimension * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }

    /** Encode bitmap ke JPEG base64 dan POST ke Google Apps Script */
    private suspend fun sendToBackend(bitmap: Bitmap, id: String, nama: String, jabatan: String, lokasi: String) {
        try {
            Log.d("EnrolFace", "Menyiapkan foto dan mengirim pendaftaran $nama ($id) ke backend...")
            val portrait = com.satpam.piket.domain.FaceRecognitionManager.ensurePortrait(bitmap)
            val scaled = scaleBitmap(portrait, 480)
            val stream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 75, stream)
            val fotoBase64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)

            val response = apiService.submitPendingPetugas(
                body = PendingPetugasRequest(
                    id = id,
                    nama = nama,
                    jabatan = jabatan,
                    lokasi = lokasi,
                    fotoBase64 = fotoBase64
                )
            )

            if (response.isSuccessful && response.body()?.success == true) {
                Log.i("EnrolFace", "Berhasil terkirim ke backend: ${response.body()?.message}")
            } else {
                Log.w("EnrolFace", "Backend response: code=${response.code()}, message=${response.body()?.message}")
            }
            _state.value = EnrolFaceState.Success(id)
        } catch (e: Exception) {
            Log.e("EnrolFace", "Gagal kirim ke backend: ${e.message}", e)
            // Tetap success lokal agar data tidak hilang di HP
            _state.value = EnrolFaceState.Success(id)
        }
    }

    fun resetState() {
        _state.value = EnrolFaceState.Idle
    }
}
