package com.satpam.piket.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.satpam.piket.data.model.Absensi
import com.satpam.piket.data.model.LokasiKonfig
import com.satpam.piket.data.model.Petugas
import com.satpam.piket.data.model.Shift
import com.satpam.piket.data.model.StatusKeterlambatan

class Converters {
    @TypeConverter fun fromShift(shift: Shift): String = shift.name
    @TypeConverter fun toShift(value: String): Shift = Shift.valueOf(value)
    @TypeConverter fun fromStatus(status: StatusKeterlambatan): String = status.name
    @TypeConverter fun toStatus(value: String): StatusKeterlambatan = StatusKeterlambatan.valueOf(value)
}

/** Migration v1→v2: tambah kolom lokasi ke tabel petugas */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE petugas ADD COLUMN lokasi TEXT NOT NULL DEFAULT ''")
    }
}

/** Migration v2→v3: buat tabel lokasi_konfig */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS lokasi_konfig (
                namaLokasi TEXT NOT NULL PRIMARY KEY,
                latitude REAL NOT NULL DEFAULT 0.0,
                longitude REAL NOT NULL DEFAULT 0.0,
                radiusMeter INTEGER NOT NULL DEFAULT 50,
                updatedAt TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
    }
}

/** Migration v3→v4: tambah kolom lokasi, tipeAbsen, keterangan ke tabel absensi */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE absensi ADD COLUMN lokasi TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE absensi ADD COLUMN tipeAbsen TEXT NOT NULL DEFAULT 'MASUK'")
        database.execSQL("ALTER TABLE absensi ADD COLUMN keterangan TEXT NOT NULL DEFAULT ''")
    }
}

@Database(
    entities = [Absensi::class, Petugas::class, LokasiKonfig::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun absensiDao(): AbsensiDao
    abstract fun petugasDao(): PetugasDao
    abstract fun lokasiDao(): LokasiDao
}

