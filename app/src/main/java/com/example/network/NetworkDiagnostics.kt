package com.example.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.roundToInt

enum class SpeedTestPhase {
    IDLE,
    PING,
    DOWNLOAD,
    UPLOAD,
    COMPLETE,
    ERROR
}

data class SpeedTestProgress(
    val phase: SpeedTestPhase = SpeedTestPhase.IDLE,
    val liveMbps: Double = 0.0,
    val pingMs: Int = 0,
    val jitterMs: Int = 0,
    val downloadMbps: Double = 0.0,
    val uploadMbps: Double = 0.0,
    val progressFraction: Float = 0f,
    val errorMessage: String? = null
)

class NetworkDiagnostics(private val okHttpClient: OkHttpClient) {

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

    suspend fun runSpeedTest(
        backendBaseUrl: String,
        onProgress: (SpeedTestProgress) -> Unit
    ): Result<SpeedTestProgress> = withContext(Dispatchers.IO) {
        try {
            val cleanBackend = backendBaseUrl.trim().trimEnd('/')
            val useBackend = cleanBackend.startsWith("https://") &&
                !cleanBackend.contains("localhost") &&
                !cleanBackend.contains("127.0.0.1")
            val pingHostPort = if (useBackend) {
                extractUrlHostPort(cleanBackend)
            } else {
                "speed.cloudflare.com" to 443
            }

            // Phase 1: Ping & Jitter across 5 probes
            onProgress(SpeedTestProgress(phase = SpeedTestPhase.PING, progressFraction = 0.08f))
            val samples = mutableListOf<Int>()
            for (i in 0 until 5) {
                if (!coroutineContext.isActive) return@withContext Result.failure(InterruptedException("Cancelled"))
                val ms = measureTcpPing("${pingHostPort.first}:${pingHostPort.second}", 3000)
                if (ms != null) samples.add(ms)
                onProgress(
                    SpeedTestProgress(
                        phase = SpeedTestPhase.PING,
                        pingMs = samples.minOrNull() ?: 0,
                        progressFraction = 0.08f + (i + 1) * 0.03f
                    )
                )
            }
            if (samples.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Network unreachable during latency probe"))
            }
            val avgPing = samples.average().roundToInt()
            val jitter = if (samples.size > 1) {
                samples.zipWithNext { a, b -> abs(a - b) }.average().roundToInt()
            } else 0

            // Phase 2: Real HTTP streaming download measurement
            val downloadUrl = if (useBackend) {
                "$cleanBackend/api/v1/network/speedtest/download?bytes=6291456"
            } else {
                "https://speed.cloudflare.com/__down?bytes=6291456"
            }
            val dlRequest = Request.Builder()
                .url(downloadUrl)
                .header("Cache-Control", "no-store")
                .build()

            var downloadMbps = 0.0
            okHttpClient.newCall(dlRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("Download probe HTTP ${response.code}"))
                }
                val stream = response.body?.byteStream()
                    ?: return@withContext Result.failure(IllegalStateException("Empty download stream"))
                val buffer = ByteArray(32 * 1024)
                var totalBytes = 0L
                val expectedBytes = response.body?.contentLength()?.takeIf { it > 0 } ?: 6_291_456L
                val startNs = System.nanoTime()
                var lastEmitNs = startNs

                while (coroutineContext.isActive) {
                    val read = stream.read(buffer)
                    if (read == -1) break
                    totalBytes += read
                    val nowNs = System.nanoTime()
                    val elapsedSec = ((nowNs - startNs) / 1_000_000_000.0).coerceAtLeast(0.05)
                    downloadMbps = (totalBytes * 8.0) / elapsedSec / 1_000_000.0
                    if (nowNs - lastEmitNs > 120_000_000L) {
                        lastEmitNs = nowNs
                        val frac = 0.25f + (totalBytes.toFloat() / expectedBytes.toFloat()).coerceIn(0f, 1f) * 0.35f
                        onProgress(
                            SpeedTestProgress(
                                phase = SpeedTestPhase.DOWNLOAD,
                                liveMbps = downloadMbps,
                                pingMs = avgPing,
                                jitterMs = jitter,
                                downloadMbps = downloadMbps,
                                progressFraction = frac
                            )
                        )
                    }
                }
                val finalSec = ((System.nanoTime() - startNs) / 1_000_000_000.0).coerceAtLeast(0.05)
                downloadMbps = (totalBytes * 8.0) / finalSec / 1_000_000.0
            }

            // Phase 3: Real HTTP streaming upload measurement
            val uploadUrl = if (useBackend) {
                "$cleanBackend/api/v1/network/speedtest/upload"
            } else {
                "https://speed.cloudflare.com/__up"
            }
            val uploadTotalBytes = 2_097_152L // 2 MB real payload
            var uploadMbps = 0.0
            val uploadStartNs = System.nanoTime()
            val body = object : RequestBody() {
                override fun contentType() = "application/octet-stream".toMediaType()
                override fun contentLength() = uploadTotalBytes
                override fun writeTo(sink: BufferedSink) {
                    val chunk = ByteArray(32 * 1024) { 0x5A }
                    var sent = 0L
                    var lastEmit = System.nanoTime()
                    while (sent < uploadTotalBytes) {
                        val toWrite = minOf(chunk.size.toLong(), uploadTotalBytes - sent).toInt()
                        sink.write(chunk, 0, toWrite)
                        sink.flush()
                        sent += toWrite
                        val now = System.nanoTime()
                        val elapsed = ((now - uploadStartNs) / 1_000_000_000.0).coerceAtLeast(0.05)
                        uploadMbps = (sent * 8.0) / elapsed / 1_000_000.0
                        if (now - lastEmit > 120_000_000L) {
                            lastEmit = now
                            val frac = 0.60f + (sent.toFloat() / uploadTotalBytes.toFloat()).coerceIn(0f, 1f) * 0.35f
                            onProgress(
                                SpeedTestProgress(
                                    phase = SpeedTestPhase.UPLOAD,
                                    liveMbps = uploadMbps,
                                    pingMs = avgPing,
                                    jitterMs = jitter,
                                    downloadMbps = downloadMbps,
                                    uploadMbps = uploadMbps,
                                    progressFraction = frac
                                )
                            )
                        }
                    }
                }
            }

            val upReq = Request.Builder().url(uploadUrl).post(body).build()
            okHttpClient.newCall(upReq).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("Upload probe HTTP ${response.code}"))
                }
            }
            val uploadSec = ((System.nanoTime() - uploadStartNs) / 1_000_000_000.0).coerceAtLeast(0.05)
            uploadMbps = (uploadTotalBytes * 8.0) / uploadSec / 1_000_000.0

            val finalState = SpeedTestProgress(
                phase = SpeedTestPhase.COMPLETE,
                liveMbps = downloadMbps,
                pingMs = avgPing,
                jitterMs = jitter,
                downloadMbps = downloadMbps,
                uploadMbps = uploadMbps,
                progressFraction = 1f
            )
            onProgress(finalState)
            Result.success(finalState)
        } catch (e: Exception) {
            Result.failure(e)
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

    private fun extractUrlHostPort(url: String): Pair<String, Int> {
        val isHttps = url.startsWith("https://")
        val withoutScheme = url.substringAfter("://").substringBefore('/')
        val host = withoutScheme.substringBefore(':')
        val port = withoutScheme.substringAfter(':', "").toIntOrNull() ?: if (isHttps) 443 else 80
        return host to port
    }
}
