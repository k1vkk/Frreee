package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Legitimate HTTPS throughput & latency measurement engine using Cloudflare Speed Edge endpoints.
 * Invoked only when SpeedOf.Me API credentials are not configured on either device or backend.
 * Measures real byte transfer over HTTPS (never invents or simulates speeds).
 */
class RealHttpSpeedTestEngine(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _progress = MutableStateFlow(SpeedTestProgress(provider = "cloudflare_http"))
    val progress: StateFlow<SpeedTestProgress> = _progress.asStateFlow()

    private var activeJob: Job? = null

    fun startTest(
        ecoMode: Boolean,
        includeUpload: Boolean,
        onCompleted: (SpeedTestProgress) -> Unit
    ) {
        if (activeJob?.isActive == true) return
        if (!isNetworkConnected()) {
            _progress.value = SpeedTestProgress(
                phase = SpeedTestPhase.ERROR,
                provider = "cloudflare_http",
                errorType = SpeedTestErrorType.CONNECTION_UNAVAILABLE,
                errorMessage = "No active internet connection"
            )
            return
        }

        val sustainSec = if (ecoMode) 2 else 4
        val dlChunkBytes = if (ecoMode) 1_500_000L else 6_000_000L
        val ulChunkBytes = if (ecoMode) 512_000 else 2_000_000

        activeJob = scope.launch(Dispatchers.IO) {
            var totalBytesUsed = 0L
            try {
                _progress.value = SpeedTestProgress(
                    phase = SpeedTestPhase.PING,
                    progressFraction = 0.08f,
                    sustainTimeSec = sustainSec,
                    testServer = "Cloudflare Edge HTTPS",
                    provider = "cloudflare_http"
                )

                // Phase 1: Real Multi-Sample HTTP Latency + Jitter
                val rtts = mutableListOf<Int>()
                for (i in 0 until 5) {
                    if (!isActive) throw CancellationException()
                    val t0 = System.nanoTime()
                    val req = Request.Builder()
                        .url("https://speed.cloudflare.com/__down?bytes=0")
                        .header("Cache-Control", "no-store")
                        .build()
                    okHttpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) throw IllegalStateException("Latency probe HTTP ${resp.code}")
                        resp.body?.bytes()
                    }
                    val ms = ((System.nanoTime() - t0) / 1_000_000L).toInt().coerceAtLeast(1)
                    rtts.add(ms)
                    _progress.update {
                        it.copy(
                            pingMs = rtts.average().roundToInt(),
                            progressFraction = 0.08f + ((i + 1) / 5f) * 0.14f
                        )
                    }
                }
                val avgPing = rtts.average().roundToInt().coerceAtLeast(1)
                val jitter = if (rtts.size >= 2) {
                    (1 until rtts.size).map { abs(rtts[it] - rtts[it - 1]) }.average().roundToInt()
                } else 0

                // Phase 2: Real HTTPS Download Throughput
                _progress.update {
                    it.copy(
                        phase = SpeedTestPhase.DOWNLOAD,
                        pingMs = avgPing,
                        jitterMs = jitter,
                        progressFraction = 0.25f
                    )
                }

                val dlReq = Request.Builder()
                    .url("https://speed.cloudflare.com/__down?bytes=$dlChunkBytes")
                    .header("Cache-Control", "no-store")
                    .build()

                var maxDl = 0.0
                var avgDl = 0.0
                okHttpClient.newCall(dlReq).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("Download HTTP ${resp.code}")
                    val source = resp.body?.source() ?: throw IllegalStateException("Empty download stream")
                    val buffer = ByteArray(32_768)
                    val startNs = System.nanoTime()
                    var downloaded = 0L
                    var lastReportNs = startNs
                    while (isActive) {
                        val read = source.read(buffer, 0, buffer.size)
                        if (read == -1) break
                        downloaded += read
                        totalBytesUsed += read
                        val now = System.nanoTime()
                        val elapsedSec = (now - startNs) / 1_000_000_000.0
                        if (elapsedSec > 0.08 && (now - lastReportNs) >= 120_000_000L) {
                            lastReportNs = now
                            val mbps = (downloaded * 8.0) / (elapsedSec * 1_000_000.0)
                            if (mbps > maxDl) maxDl = mbps
                            val frac = (downloaded.toFloat() / dlChunkBytes.toFloat()).coerceIn(0f, 1f)
                            val dlRange = if (includeUpload) 0.40f else 0.70f
                            _progress.update {
                                it.copy(
                                    liveMbps = mbps,
                                    downloadMbps = mbps,
                                    maxDownloadMbps = maxDl,
                                    dataUsedBytes = totalBytesUsed,
                                    progressFraction = 0.25f + frac * dlRange
                                )
                            }
                        }
                    }
                    val totalSec = ((System.nanoTime() - startNs) / 1_000_000_000.0).coerceAtLeast(0.01)
                    avgDl = (downloaded * 8.0) / (totalSec * 1_000_000.0)
                    if (avgDl > maxDl) maxDl = avgDl
                }

                var avgUl = 0.0
                var maxUl = 0.0

                // Phase 3: Real HTTPS Upload Throughput
                if (includeUpload) {
                    _progress.update {
                        it.copy(
                            phase = SpeedTestPhase.UPLOAD,
                            downloadMbps = avgDl,
                            maxDownloadMbps = maxDl,
                            liveMbps = 0.0,
                            progressFraction = 0.68f
                        )
                    }
                    val ulStartNs = System.nanoTime()
                    val chunk = ByteArray(32_768) { 0x5A }
                    val ulBody = object : RequestBody() {
                        override fun contentType() = "application/octet-stream".toMediaType()
                        override fun contentLength(): Long = ulChunkBytes.toLong()
                        override fun writeTo(sink: BufferedSink) {
                            var sent = 0
                            var lastUi = System.nanoTime()
                            while (sent < ulChunkBytes && isActive) {
                                val toWrite = minOf(chunk.size, ulChunkBytes - sent)
                                sink.write(chunk, 0, toWrite)
                                sent += toWrite
                                totalBytesUsed += toWrite
                                val now = System.nanoTime()
                                val sec = (now - ulStartNs) / 1_000_000_000.0
                                if (sec > 0.08 && (now - lastUi) >= 120_000_000L) {
                                    lastUi = now
                                    val mbps = (sent * 8.0) / (sec * 1_000_000.0)
                                    if (mbps > maxUl) maxUl = mbps
                                    val frac = (sent.toFloat() / ulChunkBytes.toFloat()).coerceIn(0f, 1f)
                                    _progress.update {
                                        it.copy(
                                            liveMbps = mbps,
                                            uploadMbps = mbps,
                                            maxUploadMbps = maxUl,
                                            dataUsedBytes = totalBytesUsed,
                                            progressFraction = 0.68f + frac * 0.28f
                                        )
                                    }
                                }
                            }
                        }
                    }
                    val ulReq = Request.Builder()
                        .url("https://speed.cloudflare.com/__up")
                        .post(ulBody)
                        .header("Cache-Control", "no-store")
                        .build()
                    okHttpClient.newCall(ulReq).execute().use { resp ->
                        if (!resp.isSuccessful) throw IllegalStateException("Upload HTTP ${resp.code}")
                    }
                    val ulTotalSec = ((System.nanoTime() - ulStartNs) / 1_000_000_000.0).coerceAtLeast(0.01)
                    avgUl = (ulChunkBytes * 8.0) / (ulTotalSec * 1_000_000.0)
                    if (avgUl > maxUl) maxUl = avgUl
                }

                val finalState = SpeedTestProgress(
                    phase = SpeedTestPhase.COMPLETE,
                    liveMbps = avgDl,
                    pingMs = avgPing,
                    jitterMs = jitter,
                    downloadMbps = avgDl,
                    uploadMbps = avgUl,
                    maxDownloadMbps = maxDl,
                    maxUploadMbps = maxUl,
                    progressFraction = 1f,
                    testServer = "Cloudflare Edge HTTPS",
                    dataUsedBytes = totalBytesUsed,
                    sustainTimeSec = sustainSec,
                    provider = "cloudflare_http"
                )
                _progress.value = finalState
                scope.launch(Dispatchers.Main) {
                    onCompleted(finalState)
                }
            } catch (_: CancellationException) {
                // Stopped cleanly by user
            } catch (e: Exception) {
                _progress.value = _progress.value.copy(
                    phase = SpeedTestPhase.ERROR,
                    dataUsedBytes = totalBytesUsed,
                    provider = "cloudflare_http",
                    errorType = SpeedTestErrorType.CONNECTION_UNAVAILABLE,
                    errorMessage = e.localizedMessage ?: "HTTPS speed probe failed"
                )
            }
        }
    }

    fun stop(resetToIdle: Boolean = true) {
        activeJob?.cancel()
        activeJob = null
        if (resetToIdle) {
            _progress.value = SpeedTestProgress(phase = SpeedTestPhase.IDLE, provider = "cloudflare_http")
        }
    }

    private fun isNetworkConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
