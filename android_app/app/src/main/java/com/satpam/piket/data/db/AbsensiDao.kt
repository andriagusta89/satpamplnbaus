package com.satpam.piket.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.satpam.piket.data.model.Absensi
import kotlinx.coroutines.flow.Flow

@Dao
interface AbsensiDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAbsensi(absensi: Absensi)

    @Update
    suspend fun updateAbsensi(absensi: Absensi)

    @Query("SELECT * FROM absensi WHERE shiftId = :shiftId ORDER BY createdAt ASC")
    fun getAbsensiByShiftId(shiftId: String): Flow<List<Absensi>>

    @Query("SELECT * FROM absensi WHERE isSynced = 0")
    suspend fun getUnsyncedAbsensi(): List<Absensi>

    @Query("UPDATE absensi SET isSynced = 1, fotoUrl = :fotoUrl WHERE id = :id")
    suspend fun markAsSynced(id: String, fotoUrl: String?)

    @Query("SELECT * FROM absensi WHERE tanggal = :tanggal ORDER BY createdAt DESC")
    fun getAbsensiByDate(tanggal: String): Flow<List<Absensi>>

    @Query("SELECT * FROM absensi ORDER BY createdAt DESC")
    fun getAllAbsensi(): Flow<List<Absensi>>

    @Query("SELECT * FROM absensi WHERE petugasId = :petugasId AND tanggal = :tanggal LIMIT 1")
    suspend fun getAbsensiByPetugasAndDate(petugasId: String, tanggal: String): Absensi?

    @Query("SELECT * FROM absensi WHERE petugasId = :petugasId OR (LOWER(nama) = LOWER(:nama) AND :nama != '') ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLastAbsensiByPetugas(petugasId: String, nama: String = ""): Absensi?

    @Query("SELECT * FROM absensi WHERE (petugasId = :petugasId OR (LOWER(nama) = LOWER(:nama) AND :nama != '')) AND tipeAbsen = 'MASUK' ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLastMasukByPetugas(petugasId: String, nama: String = ""): Absensi?

    @Query("SELECT COUNT(*) FROM absensi WHERE tanggal = :tanggal")
    fun countAbsensiByDate(tanggal: String): Flow<Int>
}
