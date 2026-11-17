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
    val endpoint: String,
    val serverPublicKey: String,
    val dnsServers: String,
    val tier: String,
    val loadPercent: Int,
    val sourceType: String, // "BACKEND", "IMPORTED_CONF", "MANUAL"
    val healthStatus: String = "online", // "online", "offline", "local"
    val rawConfig: String = "",
    val assignedAddress: String = "10.8.0.2/32",
    val assignedIpv6: String = "fd80:4e58:5200::2/128",
    val allowedIps: String = "0.0.0.0/0, ::/0",
    val lastPingMs: Int? = null,
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
    val vpnActive: Boolean,
    val serverLabel: String
)
