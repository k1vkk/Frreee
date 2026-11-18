package com.example.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.abs
import kotlin.math.roundToInt

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
    val provider: String = "speedofme",
    val errorType: SpeedTestErrorType? = null,
    val errorMessage: String? = null
)

data class ServerProbeResult(
    val endpoint: String,
    val pingMs: Int?,
    val jitterMs: Int?,
    val packetLossPercent: Float,
    val reachable: Boolean,
    val samplesAttempted: Int,
    val samplesSucceeded: Int
)

class NetworkDiagnostics(@Suppress("unused") private val okHttpClient: OkHttpClient) {

    suspend fun measureTcpPing(endpoint: String, timeoutMs: Int = 2500): Int? =
        withContext(Dispatchers.IO) {
            val (host, port) = parseHostPort(endpoint) ?: return@withContext null
            measureSingleRtt(host, port, timeoutMs)
        }

    suspend fun measureServerProbe(
        endpoint: String,
        samples: Int = 4,
        timeoutMs: Int = 2000
    ): ServerProbeResult = withContext(Dispatchers.IO) {
        val parsed = parseHostPort(endpoint)
            ?: return@withContext ServerProbeResult(
                endpoint = endpoint,
                pingMs = null,
                jitterMs = null,
                packetLossPercent = 100f,
                reachable = false,
                samplesAttempted = samples,
                samplesSucceeded = 0
            )
        val (host, port) = parsed
        val rtts = mutableListOf<Int>()
        for (i in 0 until samples) {
            val rtt = measureSingleRtt(host, port, timeoutMs)
            if (rtt != null) {
                rtts.add(rtt)
            }
        }
        val loss = ((samples - rtts.size).toFloat() / samples.coerceAtLeast(1).toFloat()) * 100f
        if (rtts.isEmpty()) {
            return@withContext ServerProbeResult(
                endpoint = endpoint,
                pingMs = null,
                jitterMs = null,
                packetLossPercent = 100f,
                reachable = false,
                samplesAttempted = samples,
                samplesSucceeded = 0
            )
        }
        val avgPing = rtts.average().roundToInt().coerceAtLeast(1)
        val jitter = if (rtts.size >= 2) {
            val deltas = (1 until rtts.size).map { idx -> abs(rtts[idx] - rtts[idx - 1]) }
            deltas.average().roundToInt()
        } else 0
        ServerProbeResult(
            endpoint = endpoint,
            pingMs = avgPing,
            jitterMs = jitter,
            packetLossPercent = loss,
            reachable = true,
            samplesAttempted = samples,
            samplesSucceeded = rtts.size
        )
    }

    private fun measureSingleRtt(host: String, port: Int, timeoutMs: Int): Int? {
        // Attempt TCP socket connect (or TLS/HTTP ports if WireGuard UDP-only firewall rejects TCP)
        val probePorts = listOf(port, 443, 80, 51821).distinct()
        for (targetPort in probePorts.take(2)) {
            var socket: Socket? = null
            try {
                socket = Socket()
                val startNs = System.nanoTime()
                socket.connect(InetSocketAddress(host, targetPort), timeoutMs)
                val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).toInt()
                return elapsedMs.coerceAtLeast(1)
            } catch (_: Exception) {
            } finally {
                runCatching { socket?.close() }
            }
        }
        // Fallback to system ICMP ping binary if TCP ports are firewalled
        return measureIcmpPing(host, (timeoutMs / 1000).coerceIn(1, 3))
    }

    private fun measureIcmpPing(host: String, timeoutSec: Int): Int? {
        if (host.isBlank() || host.any { !it.isLetterOrDigit() && it != '.' && it != ':' && it != '-' }) {
            return null
        }
        return runCatching {
            val process = ProcessBuilder("/system/bin/ping", "-c", "1", "-W", timeoutSec.toString(), host)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exit = process.waitFor()
            if (exit != 0) return null
            val match = Regex("""time=([0-9]+(?:\.[0-9]+)?)\s*ms""").find(output)
            match?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.roundToInt()?.coerceAtLeast(1)
        }.getOrNull()
    }

    fun parseHostPort(endpoint: String): Pair<String, Int>? {
        val clean = endpoint.trim()
        if (clean.isEmpty() || clean.equals("pending_vps", ignoreCase = true)) return null
        val lastColon = clean.lastIndexOf(':')
        if (lastColon <= 0) return clean to 51820
        val host = clean.substring(0, lastColon).trim('[', ']')
        if (host.isBlank() || host.equals("Unknown Endpoint", ignoreCase = true)) return null
        val port = clean.substring(lastColon + 1).toIntOrNull() ?: 51820
        return host to port
    }
}
