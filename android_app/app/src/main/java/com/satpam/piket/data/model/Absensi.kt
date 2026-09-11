package com.satpam.piket.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class Shift { PAGI, SIANG, MALAM }
enum class StatusKeterlambatan { TEPAT, TERLAMBAT }

@Entity(tableName = "absensi")
data class Absensi(
    @PrimaryKey val id: String,
    val shiftId: String,          // YYYYMMDD-PAGI/SIANG/MALAM
    val petugasId: String,
    val nama: String,
    val lokasi: String = "",      // nama kantor penempatan
    val tanggal: String,          // yyyy-MM-dd
    val shift: Shift,
    val tipeAbsen: String = "MASUK", // "MASUK" atau "KELUAR"
    val jamMasuk: String,         // HH:mm:ss
    val jamKeluar: String? = null,
    val durasi: Int? = null,      // menit
    val statusKeterlambatan: StatusKeterlambatan,
    val keterangan: String = "",  // catatan/situasi piket saat absen masuk/keluar
    val fotoPath: String,         // local path
    val fotoUrl: String? = null,  // url setelah upload
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isSynced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
