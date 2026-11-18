package com.example.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Socket

enum class SpeedTestPhase {
    IDLE,
    PREPARING,
    PING,
    DOWNLOAD,
    UPLOAD,
    COMPLETE,
    ERROR
}

enum class SpeedTestErrorType {
    MISSING_CONFIGURATION,
    CONNECTION_UNAVAILABLE,
    INVALID_API_KEY,
    DOMAIN_NOT_AUTHORIZED,
    TIMEOUT,
    SCRIPT_LOAD_FAILED,
    UNKNOWN
}

data class SpeedTestProgress(
    val phase: SpeedTestPhase = SpeedTestPhase.IDLE,
    val liveMbps: Double = 0.0,
    val pingMs: Int = 0,
    val jitterMs: Int = 0,
    val downloadMbps: Double = 0.0,
    val uploadMbps: Double = 0.0,
    val maxDownloadMbps: Double = 0.0,
    val maxUploadMbps: Double = 0.0,
    val progressFraction: Float = 0f,
    val testServer: String = "",
    val publicIp: String = "",
    val ispHostname: String = "",
    val dataUsedBytes: Long = 0L,
    val sustainTimeSec: Int = 2,
    val errorType: SpeedTestErrorType? = null,
    val errorMessage: String? = null
)

class NetworkDiagnostics(@Suppress("unused") private val okHttpClient: OkHttpClient) {

    suspend fun measureTcpPing(endpoint: String, timeoutMs: Int = 2500): Int? =
        withContext(Dispatchers.IO) {
            val (host, port) = parseHostPort(endpoint) ?: return@withContext null
            var socket: Socket? = null
            try {
                socket = Socket()
                val startNs = System.nanoTime()
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).toInt()
                elapsedMs.coerceAtLeast(1)
            } catch (_: Exception) {
                null
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
            }
        }

    private fun parseHostPort(endpoint: String): Pair<String, Int>? {
        val clean = endpoint.trim()
        if (clean.isEmpty()) return null
        val lastColon = clean.lastIndexOf(':')
        if (lastColon <= 0) return clean to 443
        val host = clean.substring(0, lastColon).trim('[', ']')
        val port = clean.substring(lastColon + 1).toIntOrNull() ?: 443
        return host to port
    }
}
