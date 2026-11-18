package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ServerProfileEntity::class,
        SpeedTestResultEntity::class,
        ConnectionHistoryEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class NexoraDatabase : RoomDatabase() {
    abstract fun serverProfileDao(): ServerProfileDao
    abstract fun speedTestDao(): SpeedTestDao
    abstract fun connectionHistoryDao(): ConnectionHistoryDao

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

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE server_profiles ADD COLUMN publicIp TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE server_profiles ADD COLUMN serverPort INTEGER NOT NULL DEFAULT 51820")
                db.execSQL("ALTER TABLE server_profiles ADD COLUMN cpuUsage REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE server_profiles ADD COLUMN ramUsage REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE server_profiles ADD COLUMN networkTrafficBytes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE server_profiles ADD COLUMN activeUsers INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE server_profiles ADD COLUMN lastJitterMs INTEGER")
                db.execSQL("ALTER TABLE server_profiles ADD COLUMN packetLossPercent REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE speed_test_results ADD COLUMN provider TEXT NOT NULL DEFAULT 'speedofme'")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS connection_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        serverId TEXT NOT NULL,
                        nodeCode TEXT NOT NULL,
                        serverName TEXT NOT NULL,
                        countryName TEXT NOT NULL,
                        endpoint TEXT NOT NULL,
                        connectedAt INTEGER NOT NULL,
                        disconnectedAt INTEGER,
                        durationSeconds INTEGER NOT NULL DEFAULT 0,
                        rxBytes INTEGER NOT NULL DEFAULT 0,
                        txBytes INTEGER NOT NULL DEFAULT 0,
                        status TEXT NOT NULL DEFAULT 'connected',
                        disconnectReason TEXT NOT NULL DEFAULT ''
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): NexoraDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NexoraDatabase::class.java,
                    "nexora_vpn.db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
