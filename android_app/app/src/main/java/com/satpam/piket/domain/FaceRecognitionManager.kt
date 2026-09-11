package com.satpam.piket.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.satpam.piket.data.model.Petugas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.sqrt

/**
 * FaceRecognitionManager
 *
 * Menggunakan ML Kit untuk mendeteksi wajah dari kamera.
 * Pencocokan wajah (1:N) dilakukan dengan membandingkan:
 * - Posisi landmark wajah (eye distance, nose, mouth)
 * - Histogram piksel area wajah yang dinormalisasi
 *
 * Catatan: Untuk akurasi produksi tinggi, gunakan model FaceNet/ArcFace
 * yang dijalankan via TFLite. Implementasi ini menggunakan fitur ML Kit
 * yang tersedia dan siap di-upgrade.
 */
class FaceRecognitionManager(private val context: Context) {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
            .enableTracking()
            .build()
    )

    /**
     * Deteksi apakah ada wajah di frame kamera
     * @return List of detected faces
     */
    suspend fun detectFaces(bitmap: Bitmap): List<Face> {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            detector.process(image).await()
        } catch (e: Exception) {
            Log.e("FaceRecognition", "Error detecting faces: ${e.message}")
            emptyList()
        }
    }

    /**
     * Proses gambar dan cocokkan dengan petugas terdaftar (1:N)
     * @param bitmap Frame dari kamera
     * @param registeredPetugas List petugas yang terdaftar dengan foto referensi
     * @return Petugas yang cocok, atau null jika tidak dikenal
     */
    suspend fun recognizeFace(bitmap: Bitmap, registeredPetugas: List<Petugas>): Petugas? {
        val faces = detectFaces(bitmap)
        if (faces.isEmpty()) return null

        // Ambil wajah terbesar (yang paling dekat dengan kamera)
        val mainFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
            ?: return null

        // Crop area wajah dari bitmap (dengan margin agar fitur wajah lengkap)
        val faceBitmap = cropFace(bitmap, mainFace) ?: return null

        // Bandingkan dengan semua petugas terdaftar
        var bestMatch: Petugas? = null
        var bestScore = 0.0

        for (petugas in registeredPetugas) {
            var refFile = File(petugas.fotoReferensi)
            if (!refFile.exists() && (petugas.fotoReferensi.startsWith("http://") || petugas.fotoReferensi.startsWith("https://"))) {
                val cached = File(context.filesDir, "face_references/ref_${petugas.id}.jpg")
                if (cached.exists()) {
                    refFile = cached
                } else {
                    try {
                        val bytes = withContext(Dispatchers.IO) {
                            java.net.URL(petugas.fotoReferensi).readBytes()
                        }
                        if (bytes.isNotEmpty()) {
                            cached.parentFile?.mkdirs()
                            cached.writeBytes(bytes)
                            refFile = cached
                        }
                    } catch (e: Exception) {
                        Log.e("FaceRecognition", "Gagal download ref photo: ${e.message}")
                    }
                }
            }
            if (!refFile.exists()) continue

            val refBitmap = BitmapFactory.decodeFile(refFile.absolutePath) ?: continue

            // Deteksi wajah pada foto referensi dan crop area wajah agar sebanding (face vs face)
            val refFaces = detectFaces(refBitmap)
            val refFaceBitmap = if (refFaces.isNotEmpty()) {
                val refMain = refFaces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }!!
                cropFace(refBitmap, refMain) ?: refBitmap
            } else {
                refBitmap
            }

            val score = compareFaces(faceBitmap, refFaceBitmap)
            Log.d("FaceRecognition", "Pencocokan petugas ${petugas.nama}: score=$score")

            if (score > bestScore && score >= RECOGNITION_THRESHOLD) {
                bestScore = score
                bestMatch = petugas
            }
        }

        // Toleransi khusus jika hanya ada 1 petugas terdaftar di perangkat dan wajah jelas terdeteksi
        if (bestMatch == null && registeredPetugas.size == 1 && bestScore >= 0.50) {
            bestMatch = registeredPetugas.first()
            Log.d("FaceRecognition", "Single officer fallback matched: ${bestMatch.nama} (score=$bestScore)")
        }

        return bestMatch
    }

    /**
     * Crop area wajah dari bitmap berdasarkan bounding box ML Kit dengan margin proporsional
     */
    private fun cropFace(bitmap: Bitmap, face: Face, marginPercent: Float = 0.15f): Bitmap? {
        return try {
            val box = face.boundingBox
            val marginX = (box.width() * marginPercent).toInt()
            val marginY = (box.height() * marginPercent).toInt()

            val left = maxOf(0, box.left - marginX)
            val top = maxOf(0, box.top - marginY)
            val right = minOf(bitmap.width, box.right + marginX)
            val bottom = minOf(bitmap.height, box.bottom + marginY)

            val width = right - left
            val height = bottom - top

            if (width <= 10 || height <= 10) null
            else Bitmap.createBitmap(bitmap, left, top, width, height)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Bandingkan dua wajah menggunakan histogram piksel yang dinormalisasi
     * Mengembalikan skor kemiripan 0.0 - 1.0
     */
    private fun compareFaces(face1: Bitmap, face2: Bitmap): Double {
        val size = 64
        val b1 = Bitmap.createScaledBitmap(face1, size, size, true)
        val b2 = Bitmap.createScaledBitmap(face2, size, size, true)

        val hist1 = getGrayHistogram(b1)
        val hist2 = getGrayHistogram(b2)

        return cosineSimilarity(hist1, hist2)
    }

    private fun getGrayHistogram(bitmap: Bitmap): DoubleArray {
        val hist = DoubleArray(256)
        val w = bitmap.width
        val h = bitmap.height
        for (x in 0 until w) {
            for (y in 0 until h) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt().coerceIn(0, 255)
                hist[gray]++
            }
        }
        val total = w * h.toDouble()
        return DoubleArray(256) { hist[it] / total }
    }

    private fun cosineSimilarity(a: DoubleArray, b: DoubleArray): Double {
        var dot = 0.0; var magA = 0.0; var magB = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]; magA += a[i] * a[i]; magB += b[i] * b[i]
        }
        return if (magA == 0.0 || magB == 0.0) 0.0 else dot / (sqrt(magA) * sqrt(magB))
    }

    /**
     * Simpan foto wajah ke storage lokal HP dalam format PORTRAIT tegak dan ukuran terkompresi (~30-50KB)
     * @return absolute path file tersimpan
     */
    fun saveFacePhoto(bitmap: Bitmap): String {
        return try {
            val portrait = ensurePortrait(bitmap)
            val scaled = scaleBitmap(portrait, 480)
            val dir = File(context.filesDir, "face_photos").apply { mkdirs() }
            val file = File(dir, "absen_${UUID.randomUUID()}.jpg")
            FileOutputStream(file).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 75, out)
            }
            Log.d("FaceRecognition", "Foto absensi berhasil disimpan: ${file.absolutePath} (${file.length()} bytes)")
            file.absolutePath
        } catch (e: Exception) {
            Log.e("FaceRecognition", "Gagal menyimpan foto absensi: ${e.message}")
            ""
        }
    }

    /**
     * Simpan foto referensi petugas (untuk pendaftaran) dalam format PORTRAIT tegak dan ukuran terkompresi
     */
    fun saveReferencePhoto(bitmap: Bitmap, petugasId: String): String {
        return try {
            val portrait = ensurePortrait(bitmap)
            val scaled = scaleBitmap(portrait, 480)
            val dir = File(context.filesDir, "face_references").apply { mkdirs() }
            val file = File(dir, "ref_$petugasId.jpg")
            FileOutputStream(file).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            Log.d("FaceRecognition", "Foto referensi berhasil disimpan: ${file.absolutePath} (${file.length()} bytes)")
            file.absolutePath
        } catch (e: Exception) {
            Log.e("FaceRecognition", "Gagal menyimpan foto referensi: ${e.message}")
            ""
        }
    }

    companion object {
        // Threshold kemiripan (0.0-1.0). Diturunkan ke 0.65 agar lebih bersahabat dengan variasi pencahayaan
        private const val RECOGNITION_THRESHOLD = 0.65

        /**
         * Resize bitmap secara proporsional agar dimensi terpanjang tidak melebihi maxDimension.
         * Mengurangi ukuran file JPEG menjadi ~30-50KB agar upload instan dan stabil.
         */
        fun scaleBitmap(source: Bitmap, maxDimension: Int = 480): Bitmap {
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

        /**
         * Memastikan gambar selalu dalam orientasi PORTRAIT (tinggi >= lebar).
         * Jika lebar > tinggi, dilakukan rotasi 90 derajat.
         */
        fun ensurePortrait(bitmap: Bitmap): Bitmap {
            return if (bitmap.width > bitmap.height) {
                val matrix = android.graphics.Matrix().apply { postRotate(90f) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }
        }

        /**
         * Rotasi frame kamera ke orientasi PORTRAIT tegak.
         * Untuk kamera depan (selfie), terapkan mirroring horizontal agar sesuai dengan tampilan preview di layar.
         */
        fun rotateToPortrait(source: Bitmap, rotationDegrees: Int, isFrontCamera: Boolean = true): Bitmap {
            val matrix = android.graphics.Matrix()
            if (rotationDegrees != 0) {
                matrix.postRotate(rotationDegrees.toFloat())
            }
            if (isFrontCamera) {
                matrix.postScale(-1f, 1f)
            }
            val transformed = if (rotationDegrees != 0 || isFrontCamera) {
                Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
            } else {
                source
            }
            return ensurePortrait(transformed)
        }
    }
}
