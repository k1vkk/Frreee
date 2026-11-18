package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ServerProfileEntity::class, SpeedTestResultEntity::class],
    version = 3,
    exportSchema = false
)
abstract class NexoraDatabase : RoomDatabase() {
    abstract fun serverProfileDao(): ServerProfileDao
    abstract fun speedTestDao(): SpeedTestDao

    companion object {
        @Volatile
        private var instance: NexoraDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE speed_test_results ADD COLUMN maxDownloadMbps REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE speed_test_results ADD COLUMN maxUploadMbps REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE speed_test_results ADD COLUMN testServer TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE speed_test_results ADD COLUMN publicIp TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE speed_test_results ADD COLUMN ispHostname TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE speed_test_results ADD COLUMN dataUsedBytes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE speed_test_results ADD COLUMN sustainTimeSec INTEGER NOT NULL DEFAULT 2")
            }
        }

        fun getInstance(context: Context): NexoraDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NexoraDatabase::class.java,
                    "nexora_vpn.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
