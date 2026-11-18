package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "server_profiles")
data class ServerProfileEntity(
    @PrimaryKey val id: String,
    val nodeCode: String = "",
    val name: String,
    val countryName: String,
    val countryCode: String,
    val city: String,
    val publicIp: String = "",
    val serverPort: Int = 51820,
    val endpoint: String,
    val serverPublicKey: String,
    val dnsServers: String,
    val tier: String,
    val loadPercent: Int,
    val cpuUsage: Float = 0f,
    val ramUsage: Float = 0f,
    val networkTrafficBytes: Long = 0L,
    val activeUsers: Int = 0,
    val sourceType: String, // "BACKEND", "IMPORTED_CONF", "MANUAL"
    val healthStatus: String = "online", // "online", "offline", "pending_vps", "local"
    val rawConfig: String = "",
    val assignedAddress: String = "10.8.0.2/32",
    val assignedIpv6: String = "fd80:4e58:5200::2/128",
    val allowedIps: String = "0.0.0.0/0, ::/0",
    val lastPingMs: Int? = null,
    val lastJitterMs: Int? = null,
    val packetLossPercent: Float = 0f,
    val isFavorite: Boolean = false,
    val lastHealthCheckAt: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "speed_test_results")
data class SpeedTestResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val pingMs: Int,
    val jitterMs: Int,
    val downloadMbps: Double,
    val uploadMbps: Double,
    val maxDownloadMbps: Double = 0.0,
    val maxUploadMbps: Double = 0.0,
    val vpnActive: Boolean,
    val serverLabel: String,
    val testServer: String = "",
    val publicIp: String = "",
    val ispHostname: String = "",
    val dataUsedBytes: Long = 0L,
    val sustainTimeSec: Int = 2,
    val provider: String = "speedofme"
)

@Entity(tableName = "connection_history")
data class ConnectionHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: String,
    val nodeCode: String,
    val serverName: String,
    val countryName: String,
    val endpoint: String,
    val connectedAt: Long,
    val disconnectedAt: Long? = null,
    val durationSeconds: Long = 0L,
    val rxBytes: Long = 0L,
    val txBytes: Long = 0L,
    val status: String = "connected", // "connected", "disconnected", "failed"
    val disconnectReason: String = ""
)
