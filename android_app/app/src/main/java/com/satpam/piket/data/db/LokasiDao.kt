package com.satpam.piket.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.satpam.piket.data.model.LokasiKonfig
import kotlinx.coroutines.flow.Flow

@Dao
interface LokasiDao {
    @Query("SELECT * FROM lokasi_konfig")
    fun getAllLokasi(): Flow<List<LokasiKonfig>>

    @Query("SELECT * FROM lokasi_konfig")
    suspend fun getAllLokasiList(): List<LokasiKonfig>

    @Query("SELECT * FROM lokasi_konfig WHERE LOWER(TRIM(namaLokasi)) = LOWER(TRIM(:nama)) LIMIT 1")
    suspend fun getLokasiByName(nama: String): LokasiKonfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateLokasi(lokasi: LokasiKonfig)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<LokasiKonfig>)
}
