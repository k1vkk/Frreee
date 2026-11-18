package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerProfileDao {
    @Query("SELECT * FROM server_profiles ORDER BY isFavorite DESC, sourceType ASC, countryName ASC")
    fun observeServers(): Flow<List<ServerProfileEntity>>

    @Query("SELECT * FROM server_profiles WHERE id = :id LIMIT 1")
    suspend fun getServerById(id: String): ServerProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(servers: List<ServerProfileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(server: ServerProfileEntity)

    @Query("UPDATE server_profiles SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("UPDATE server_profiles SET lastPingMs = :pingMs, lastJitterMs = :jitterMs, packetLossPercent = :lossPercent WHERE id = :id")
    suspend fun updateDiagnostics(id: String, pingMs: Int?, jitterMs: Int?, lossPercent: Float)

    @Query("UPDATE server_profiles SET lastPingMs = :pingMs WHERE id = :id")
    suspend fun updatePing(id: String, pingMs: Int?)

    @Query("DELETE FROM server_profiles WHERE sourceType = 'BACKEND' AND id NOT IN (:activeIds)")
    suspend fun deleteStaleBackendServers(activeIds: List<String>)

    @Query("DELETE FROM server_profiles WHERE sourceType = 'BACKEND'")
    suspend fun deleteAllBackendServers()

    @Query("DELETE FROM server_profiles WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface SpeedTestDao {
    @Query("SELECT * FROM speed_test_results ORDER BY timestamp DESC LIMIT 50")
    fun observeHistory(): Flow<List<SpeedTestResultEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(result: SpeedTestResultEntity)

    @Query("DELETE FROM speed_test_results WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM speed_test_results")
    suspend fun clearAll()
}

@Dao
interface ConnectionHistoryDao {
    @Query("SELECT * FROM connection_history ORDER BY connectedAt DESC LIMIT 100")
    fun observeHistory(): Flow<List<ConnectionHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ConnectionHistoryEntity): Long

    @Query(
        """
        UPDATE connection_history
        SET disconnectedAt = :disconnectedAt,
            durationSeconds = :durationSeconds,
            rxBytes = :rxBytes,
            txBytes = :txBytes,
            status = :status,
            disconnectReason = :reason
        WHERE id = :id
        """
    )
    suspend fun finalizeSession(
        id: Long,
        disconnectedAt: Long,
        durationSeconds: Long,
        rxBytes: Long,
        txBytes: Long,
        status: String,
        reason: String
    )

    @Query("DELETE FROM connection_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM connection_history")
    suspend fun clearAll()
}
