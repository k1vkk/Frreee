package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ServerProfileEntity::class, SpeedTestResultEntity::class],
    version = 2,
    exportSchema = false
)
abstract class NexoraDatabase : RoomDatabase() {
    abstract fun serverProfileDao(): ServerProfileDao
    abstract fun speedTestDao(): SpeedTestDao

    companion object {
        @Volatile
        private var instance: NexoraDatabase? = null

        fun getInstance(context: Context): NexoraDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NexoraDatabase::class.java,
                    "nexora_vpn.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
