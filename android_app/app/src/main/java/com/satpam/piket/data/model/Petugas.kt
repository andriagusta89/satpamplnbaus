package com.satpam.piket.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "petugas")
data class Petugas(
    @PrimaryKey val id: String,
    val nama: String,
    val jabatan: String = "",
    val lokasi: String = "",      // lokasi penempatan (Kantor UP3, ULP, dsb)
    val fotoReferensi: String = "",    // path foto referensi wajah di lokal HP atau URL Drive
    val aktif: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
