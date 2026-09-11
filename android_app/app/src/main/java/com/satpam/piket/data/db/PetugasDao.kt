package com.satpam.piket.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.satpam.piket.data.model.Petugas
import kotlinx.coroutines.flow.Flow

@Dao
interface PetugasDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPetugas(petugas: Petugas)

    @Update
    suspend fun updatePetugas(petugas: Petugas)

    @Query("SELECT * FROM petugas ORDER BY nama ASC")
    fun getAllPetugas(): Flow<List<Petugas>>

    @Query("SELECT * FROM petugas")
    suspend fun getAllPetugasList(): List<Petugas>

    @Query("SELECT * FROM petugas WHERE id = :id")
    suspend fun getPetugasById(id: String): Petugas?

    @Query("SELECT * FROM petugas WHERE aktif = 1 ORDER BY nama ASC")
    suspend fun getActivePetugas(): List<Petugas>

    @Query("SELECT COUNT(*) FROM petugas WHERE aktif = 1")
    fun countActivePetugas(): Flow<Int>

    @Query("UPDATE petugas SET aktif = 0 WHERE id = :id")
    suspend fun deactivatePetugas(id: String)

    @Query("DELETE FROM petugas WHERE id = :id")
    suspend fun deletePetugas(id: String)
}
