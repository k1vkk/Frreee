package com.example.data.remote

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HealthResponse(
    val ok: Boolean,
    val service: String? = null,
    val environment: String? = null,
    val database: String? = null,
    val publicUrl: String? = null,
    val onlineNodes: Int? = null,
    val totalSlots: Int? = null,
    val timestamp: String? = null
)

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val email: String,
    val password: String,
    val deviceLabel: String
)

@JsonClass(generateAdapter = true)
data class RegisterRequest(
    val email: String,
    val password: String,
    val displayName: String,
    val deviceLabel: String
)

@JsonClass(generateAdapter = true)
data class UserDto(
    val id: String,
    val email: String,
    val displayName: String,
    val role: String = "user",
    val planTier: String = "free",
    val subscriptionStatus: String = "active",
    val expiresAt: String? = null
)

@JsonClass(generateAdapter = true)
data class ForgotPasswordRequest(
    val email: String
)

@JsonClass(generateAdapter = true)
data class ResetPasswordRequest(
    val email: String,
    val code: String,
    val newPassword: String
)

@JsonClass(generateAdapter = true)
data class BasicMessageResponse(
    val ok: Boolean = true,
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    val token: String,
    val user: UserDto
)

@JsonClass(generateAdapter = true)
data class SessionDto(
    val id: String,
    val deviceLabel: String,
    val ipAddress: String,
    val createdAt: String,
    val lastActiveAt: String,
    val isCurrent: Boolean
)

@JsonClass(generateAdapter = true)
data class MeResponse(
    val user: UserDto,
    val sessions: List<SessionDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ServerDto(
    val id: String,
    val nodeCode: String = "",
    val name: String,
    val countryName: String,
    val countryCode: String,
    val city: String,
    val endpoint: String,
    val serverPublicKey: String,
    val dnsServers: String = "1.1.1.1, 1.0.0.1, 2606:4700:4700::1111, 2606:4700:4700::1001",
    val tier: String = "free",
    val healthStatus: String = "online",
    val latencyMs: Int? = null,
    val lastHealthCheckAt: String? = null,
    val loadPercent: Int = 0
)

@JsonClass(generateAdapter = true)
data class ServersResponse(
    val servers: List<ServerDto>
)

@JsonClass(generateAdapter = true)
data class BestServerResponse(
    val server: ServerDto
)

@JsonClass(generateAdapter = true)
data class ProvisionRequest(
    val serverId: String,
    val clientPublicKey: String
)

@JsonClass(generateAdapter = true)
data class ProvisionResponse(
    val ok: Boolean = true,
    val serverId: String? = null,
    val nodeCode: String? = null,
    val serverName: String? = null,
    val countryName: String? = null,
    val assignedAddress: String,
    val assignedIpv4: String? = null,
    val assignedIpv6: String? = null,
    val serverPublicKey: String,
    val endpoint: String,
    val dnsServers: String,
    val allowedIps: String,
    val mtu: Int = 1420,
    val persistentKeepalive: Int = 25,
    val wgQuickConfig: String? = null
)

@JsonClass(generateAdapter = true)
data class VpnDisconnectRequest(
    val serverId: String? = null,
    val clientPublicKey: String? = null,
    val rxBytes: Long? = null,
    val txBytes: Long? = null
)

@JsonClass(generateAdapter = true)
data class VpnDisconnectResponse(
    val ok: Boolean,
    val disconnected: Boolean = true,
    val peersClosed: Int = 0,
    val timestamp: String? = null
)

@JsonClass(generateAdapter = true)
data class VpnStatusResponse(
    val connected: Boolean,
    val status: String = "disconnected",
    val agentPeerPresent: Boolean = false,
    val serverId: String? = null,
    val nodeCode: String? = null,
    val serverName: String? = null,
    val countryName: String? = null,
    val endpoint: String? = null,
    val assignedIpv4: String? = null,
    val assignedIpv6: String? = null,
    val lastHandshakeAt: String? = null,
    val lastConnectedAt: String? = null,
    val rxBytes: Long = 0L,
    val txBytes: Long = 0L,
    val healthStatus: String? = null,
    val latencyMs: Int? = null
)

@JsonClass(generateAdapter = true)
data class VerifyPurchaseRequest(
    val productId: String,
    val purchaseToken: String,
    val packageName: String
)

@JsonClass(generateAdapter = true)
data class VerifyPurchaseResponse(
    val ok: Boolean,
    val planTier: String,
    val status: String,
    val expiresAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SubscriptionMeResponse(
    val planTier: String = "free",
    val status: String = "active",
    val productId: String? = null,
    val expiresAt: String? = null,
    val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class TelemetryErrorRequest(
    val level: String = "error",
    val source: String = "android-client",
    val nodeCode: String? = null,
    val message: String,
    val details: String? = null
)

@JsonClass(generateAdapter = true)
data class IpResponse(
    val ip: String
)
