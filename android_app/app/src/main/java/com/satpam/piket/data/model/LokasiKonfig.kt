package com.satpam.piket.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lokasi_konfig")
data class LokasiKonfig(
    @PrimaryKey val namaLokasi: String,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeter: Int = 50,
    val updatedAt: String = ""
)
