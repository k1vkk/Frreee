package com.example.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Official SpeedOf.Me JavaScript API integration via an isolated, hardened Android WebView
 * and @JavascriptInterface bridge. Runs purely for internet speed diagnostics (never as a VPN/proxy).
 */
class SpeedOfMeSpeedTestEngine(
    private val context: Context
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _progress = MutableStateFlow(SpeedTestProgress(provider = "speedofme"))
    val progress: StateFlow<SpeedTestProgress> = _progress.asStateFlow()

    private var webView: WebView? = null
    private var isRunning: Boolean = false
    private var includeUploadFlag: Boolean = true
    private var activeSustainTimeSec: Int = 2
    private var baselineUidBytes: Long = 0L
    private var watchdogJob: Job? = null
    private var trafficTickerJob: Job? = null
    private var onCompletedCallback: ((SpeedTestProgress) -> Unit)? = null

    fun resolveCredentials(
        overrideApiKey: String? = null,
        overrideDomain: String? = null
    ): Pair<String, String>? {
        val rawKey = (overrideApiKey?.takeIf { it.isNotBlank() } ?: BuildConfig.SPEEDOFME_API_KEY).trim()
        val rawDomain = normalizeDomain(overrideDomain?.takeIf { it.isNotBlank() } ?: BuildConfig.SPEEDOFME_DOMAIN)
        if (isPlaceholderOrBlank(rawKey, "YOUR_SPEEDOFME_API_KEY") ||
            isPlaceholderOrBlank(rawDomain, "YOUR_REGISTERED_DOMAIN")
        ) {
            return null
        }
        return rawKey to rawDomain
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun attachWebView(view: WebView) {
        if (webView === view) return
        webView = view
        configureWebView(view)
    }

    fun detachWebView(view: WebView) {
        if (webView === view) {
            if (isRunning) {
                stop(resetToIdle = false)
            }
            view.removeJavascriptInterface("NexoraSpeedBridge")
            webView = null
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(view: WebView) {
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            setGeolocationEnabled(false)
            loadsImagesAutomatically = false
            blockNetworkImage = true
        }

        val bridge = SpeedOfMeJsBridge(
            onProgressJson = { json -> mainHandler.post { handleProgress(json) } },
            onCompletedJson = { json -> mainHandler.post { handleCompleted(json) } },
            onApiError = { code, message -> mainHandler.post { handleApiError(code, message) } },
            onScriptFailed = { reason ->
                mainHandler.post {
                    failTest(SpeedTestErrorType.SCRIPT_LOAD_FAILED, reason)
                }
            }
        )

        view.removeJavascriptInterface("NexoraSpeedBridge")
        view.addJavascriptInterface(bridge, "NexoraSpeedBridge")

        view.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                if (isRunning && consoleMessage?.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    val msg = consoleMessage.message().orEmpty()
                    if (msg.contains("SomApi", ignoreCase = true) ||
                        msg.contains("speedof.me", ignoreCase = true) ||
                        msg.contains("domain", ignoreCase = true)
                    ) {
                        handleApiError("CONSOLE", msg)
                    }
                }
                return true
            }
        }

        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean = true

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (!isRunning) return
                val url = request?.url?.toString().orEmpty()
                if (request?.isForMainFrame == true || url.contains("speedof.me", ignoreCase = true)) {
                    failTest(
                        SpeedTestErrorType.CONNECTION_UNAVAILABLE,
                        error?.description?.toString()
                    )
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                if (!isRunning) return
                val url = request?.url?.toString().orEmpty()
                if (url.contains("speedof.me/api", ignoreCase = true) && (errorResponse?.statusCode ?: 200) >= 400) {
                    failTest(
                        SpeedTestErrorType.SCRIPT_LOAD_FAILED,
                        "HTTP ${errorResponse?.statusCode}"
                    )
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (!isRunning || url == "about:blank") return
                view?.evaluateJavascript(
                    """
                    (function() {
                      if (typeof SomApi === 'undefined') {
                        NexoraSpeedBridge.onScriptLoadFailed('SpeedOf.Me script failed to initialize.');
                      } else if (!window.__nexoraStarted) {
                        window.__nexoraStarted = true;
                        try {
                          SomApi.startTest();
                        } catch (e) {
                          NexoraSpeedBridge.onError('START_ERR', e && e.message ? e.message : 'Unable to start SomApi');
                        }
                      }
                    })();
                    """.trimIndent(),
                    null
                )
            }
        }
    }

    fun startTest(
        ecoMode: Boolean,
        includeUpload: Boolean,
        overrideApiKey: String? = null,
        overrideDomain: String? = null,
        onCompleted: (SpeedTestProgress) -> Unit
    ) {
        if (isRunning) return

        val sustainTime = if (ecoMode) 2 else 4
        activeSustainTimeSec = sustainTime
        includeUploadFlag = includeUpload
        onCompletedCallback = onCompleted

        val creds = resolveCredentials(overrideApiKey, overrideDomain)
        if (creds == null) {
            _progress.value = SpeedTestProgress(
                phase = SpeedTestPhase.ERROR,
                sustainTimeSec = sustainTime,
                provider = "speedofme",
                errorType = SpeedTestErrorType.MISSING_CONFIGURATION,
                errorMessage = "SPEEDOFME_API_KEY / SPEEDOFME_DOMAIN not configured"
            )
            return
        }
        val (apiKey, domain) = creds

        if (!isNetworkConnected()) {
            _progress.value = SpeedTestProgress(
                phase = SpeedTestPhase.ERROR,
                sustainTimeSec = sustainTime,
                provider = "speedofme",
                errorType = SpeedTestErrorType.CONNECTION_UNAVAILABLE,
                errorMessage = "No active internet connection"
            )
            return
        }

        val view = webView
        if (view == null) {
            _progress.value = SpeedTestProgress(
                phase = SpeedTestPhase.ERROR,
                sustainTimeSec = sustainTime,
                provider = "speedofme",
                errorType = SpeedTestErrorType.UNKNOWN,
                errorMessage = "Speed test WebView engine is not attached"
            )
            return
        }

        isRunning = true
        baselineUidBytes = currentUidTotalBytes()
        _progress.value = SpeedTestProgress(
            phase = SpeedTestPhase.PREPARING,
            progressFraction = 0.04f,
            sustainTimeSec = sustainTime,
            provider = "speedofme"
        )

        startTrafficTicker()
        resetWatchdog()

        val html = buildSpeedOfMeHtml(
            apiKey = apiKey,
            domain = domain,
            sustainTime = sustainTime,
            includeUpload = includeUpload
        )

        mainHandler.post {
            if (!isRunning) return@post
            view.stopLoading()
            view.clearCache(true)
            view.loadDataWithBaseURL(
                "https://$domain/",
                html,
                "text/html",
                "UTF-8",
                null
            )
        }
    }

    fun stop(resetToIdle: Boolean = true) {
        isRunning = false
        onCompletedCallback = null
        watchdogJob?.cancel()
        watchdogJob = null
        trafficTickerJob?.cancel()
        trafficTickerJob = null
        mainHandler.post {
            webView?.apply {
                stopLoading()
                loadUrl("about:blank")
            }
        }
        if (resetToIdle) {
            _progress.value = SpeedTestProgress(
                phase = SpeedTestPhase.IDLE,
                sustainTimeSec = activeSustainTimeSec,
                provider = "speedofme"
            )
        }
    }

    private fun handleProgress(jsonStr: String) {
        if (!isRunning) return
        resetWatchdog()
        runCatching {
            val obj = JSONObject(jsonStr)
            val type = obj.optString("type").lowercase()
            val percentDone = obj.optDouble("percentDone", 0.0).coerceIn(0.0, 100.0).toFloat()
            val currentSpeed = obj.optDouble("currentSpeed", Double.NaN)
            val validSpeed = if (!currentSpeed.isNaN() && currentSpeed >= 0.0) currentSpeed else null

            val usedBytes = measureConsumedBytes()
            _progress.update { prev ->
                when {
                    type.contains("latency") || type.contains("ping") -> {
                        prev.copy(
                            phase = SpeedTestPhase.PING,
                            progressFraction = 0.06f + (percentDone / 100f) * 0.14f,
                            dataUsedBytes = usedBytes
                        )
                    }
                    type.contains("download") -> {
                        val speed = validSpeed ?: prev.liveMbps
                        val endFrac = if (includeUploadFlag) 0.45f else 0.75f
                        prev.copy(
                            phase = SpeedTestPhase.DOWNLOAD,
                            liveMbps = speed,
                            downloadMbps = speed,
                            maxDownloadMbps = maxOf(prev.maxDownloadMbps, speed),
                            progressFraction = 0.20f + (percentDone / 100f) * endFrac,
                            dataUsedBytes = usedBytes
                        )
                    }
                    type.contains("upload") -> {
                        val speed = validSpeed ?: prev.liveMbps
                        prev.copy(
                            phase = SpeedTestPhase.UPLOAD,
                            liveMbps = speed,
                            uploadMbps = speed,
                            maxUploadMbps = maxOf(prev.maxUploadMbps, speed),
                            progressFraction = 0.65f + (percentDone / 100f) * 0.31f,
                            dataUsedBytes = usedBytes
                        )
                    }
                    else -> prev.copy(dataUsedBytes = usedBytes)
                }
            }
        }
    }

    private fun handleCompleted(jsonStr: String) {
        if (!isRunning) return
        isRunning = false
        watchdogJob?.cancel()
        watchdogJob = null
        trafficTickerJob?.cancel()
        trafficTickerJob = null

        val parsed = runCatching {
            val obj = JSONObject(jsonStr)
            val download = obj.optSafeDouble("download", _progress.value.downloadMbps)
            val upload = if (includeUploadFlag) {
                obj.optSafeDouble("upload", _progress.value.uploadMbps)
            } else 0.0
            val maxDown = obj.optSafeDouble("maxDownload", maxOf(download, _progress.value.maxDownloadMbps))
            val maxUp = if (includeUploadFlag) {
                obj.optSafeDouble("maxUpload", maxOf(upload, _progress.value.maxUploadMbps))
            } else 0.0
            val latency = obj.optSafeDouble("latency", _progress.value.pingMs.toDouble()).roundToInt().coerceAtLeast(0)
            val jitter = obj.optSafeDouble("jitter", _progress.value.jitterMs.toDouble()).roundToInt().coerceAtLeast(0)
            val testServer = obj.optString("testServer").trim()
            val ip = obj.optString("ip_address").trim()
            val hostname = obj.optString("hostname").trim()

            SpeedTestProgress(
                phase = SpeedTestPhase.COMPLETE,
                liveMbps = download,
                pingMs = latency,
                jitterMs = jitter,
                downloadMbps = download,
                uploadMbps = upload,
                maxDownloadMbps = maxDown,
                maxUploadMbps = maxUp,
                progressFraction = 1f,
                testServer = testServer,
                publicIp = ip,
                ispHostname = hostname,
                dataUsedBytes = measureConsumedBytes(),
                sustainTimeSec = activeSustainTimeSec,
                provider = "speedofme"
            )
        }

        mainHandler.post {
            webView?.loadUrl("about:blank")
        }

        parsed.onSuccess { finalState ->
            _progress.value = finalState
            val cb = onCompletedCallback
            onCompletedCallback = null
            cb?.invoke(finalState)
        }.onFailure { err ->
            failTest(SpeedTestErrorType.UNKNOWN, err.localizedMessage)
        }
    }

    private fun handleApiError(code: String, message: String) {
        if (!isRunning) return
        val normalized = "$code $message".lowercase()
        val errorType = when {
            code == "1001" ||
                normalized.contains("invalid account") ||
                normalized.contains("invalid key") ||
                normalized.contains("api key") ||
                (normalized.contains("account") && normalized.contains("invalid")) -> {
                SpeedTestErrorType.INVALID_API_KEY
            }
            code == "1002" ||
                normalized.contains("domain") ||
                normalized.contains("authorized") ||
                normalized.contains("origin") ||
                normalized.contains("referrer") ||
                normalized.contains("referer") -> {
                SpeedTestErrorType.DOMAIN_NOT_AUTHORIZED
            }
            normalized.contains("network") ||
                normalized.contains("offline") ||
                normalized.contains("connection") ||
                normalized.contains("fetch") -> {
                SpeedTestErrorType.CONNECTION_UNAVAILABLE
            }
            normalized.contains("timeout") -> SpeedTestErrorType.TIMEOUT
            else -> SpeedTestErrorType.UNKNOWN
        }
        val detail = listOf(code.takeIf { it.isNotBlank() && it != "CONSOLE" }, message.takeIf { it.isNotBlank() })
            .filterNotNull()
            .joinToString(": ")
        failTest(errorType, detail.ifBlank { null })
    }

    private fun failTest(errorType: SpeedTestErrorType, detail: String?) {
        val used = if (baselineUidBytes > 0L) measureConsumedBytes() else 0L
        isRunning = false
        onCompletedCallback = null
        watchdogJob?.cancel()
        watchdogJob = null
        trafficTickerJob?.cancel()
        trafficTickerJob = null
        mainHandler.post {
            webView?.apply {
                stopLoading()
                loadUrl("about:blank")
            }
        }
        _progress.value = _progress.value.copy(
            phase = SpeedTestPhase.ERROR,
            dataUsedBytes = used,
            provider = "speedofme",
            errorType = errorType,
            errorMessage = detail
        )
    }

    private fun resetWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            delay(35_000L)
            if (isRunning) {
                failTest(SpeedTestErrorType.TIMEOUT, "No response from SpeedOf.Me servers within 35s")
            }
        }
    }

    private fun startTrafficTicker() {
        trafficTickerJob?.cancel()
        trafficTickerJob = scope.launch {
            while (isActive && isRunning) {
                val used = measureConsumedBytes()
                _progress.update { it.copy(dataUsedBytes = used) }
                delay(500L)
            }
        }
    }

    private fun measureConsumedBytes(): Long {
        val now = currentUidTotalBytes()
        return if (baselineUidBytes > 0L && now >= baselineUidBytes) {
            now - baselineUidBytes
        } else 0L
    }

    private fun currentUidTotalBytes(): Long {
        val uid = Process.myUid()
        val rx = TrafficStats.getUidRxBytes(uid)
        val tx = TrafficStats.getUidTxBytes(uid)
        val safeRx = if (rx >= 0L) rx else 0L
        val safeTx = if (tx >= 0L) tx else 0L
        return safeRx + safeTx
    }

    private fun isNetworkConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun normalizeDomain(raw: String): String {
        return raw.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore('/')
            .trim()
    }

    private fun isPlaceholderOrBlank(value: String, placeholder: String): Boolean {
        return value.isBlank() ||
            value.equals(placeholder, ignoreCase = true) ||
            value.contains("YOUR_", ignoreCase = true) ||
            value.contains("yourdomain", ignoreCase = true)
    }

    private fun JSONObject.optSafeDouble(key: String, fallback: Double): Double {
        val v = optDouble(key, Double.NaN)
        return if (!v.isNaN() && v >= 0.0) v else fallback
    }

    private fun buildSpeedOfMeHtml(
        apiKey: String,
        domain: String,
        sustainTime: Int,
        includeUpload: Boolean
    ): String {
        val quotedKey = JSONObject.quote(apiKey)
        val quotedDomain = JSONObject.quote(domain)
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
            </head>
            <body>
              <script src="https://speedof.me/api/api.js"
                      type="text/javascript"
                      onerror="NexoraSpeedBridge.onScriptLoadFailed('Failed to download https://speedof.me/api/api.js');"></script>
              <script type="text/javascript">
                (function() {
                  if (typeof SomApi === 'undefined') {
                    return;
                  }
                  SomApi.account = $quotedKey;
                  SomApi.domainName = $quotedDomain;
                  SomApi.config.sustainTime = $sustainTime;
                  SomApi.config.testServerEnabled = true;
                  SomApi.config.userInfoEnabled = true;
                  SomApi.config.latencyTestEnabled = true;
                  SomApi.config.uploadTestEnabled = $includeUpload;
                  SomApi.config.progress.enabled = true;
                  SomApi.config.progress.verbose = true;

                  SomApi.onProgress = function(progress) {
                    try {
                      NexoraSpeedBridge.onProgress(JSON.stringify(progress || {}));
                    } catch (e) {}
                  };

                  SomApi.onTestCompleted = function(testResult) {
                    try {
                      NexoraSpeedBridge.onCompleted(JSON.stringify(testResult || {}));
                    } catch (e) {
                      NexoraSpeedBridge.onError('PARSE_ERR', String(e));
                    }
                  };

                  SomApi.onError = function(error) {
                    try {
                      var code = (error && error.code !== undefined) ? String(error.code) : '';
                      var msg = (error && error.message) ? String(error.message) : 'SpeedOf.Me API error';
                      NexoraSpeedBridge.onError(code, msg);
                    } catch (e) {
                      NexoraSpeedBridge.onError('UNKNOWN', 'SpeedOf.Me error');
                    }
                  };
                })();
              </script>
            </body>
            </html>
        """.trimIndent()
    }
}

class SpeedOfMeJsBridge(
    private val onProgressJson: (String) -> Unit,
    private val onCompletedJson: (String) -> Unit,
    private val onApiError: (String, String) -> Unit,
    private val onScriptFailed: (String) -> Unit
) {
    @JavascriptInterface
    fun onProgress(json: String) {
        onProgressJson(json)
    }

    @JavascriptInterface
    fun onCompleted(json: String) {
        onCompletedJson(json)
    }

    @JavascriptInterface
    fun onError(code: String, message: String) {
        onApiError(code, message)
    }

    @JavascriptInterface
    fun onScriptLoadFailed(reason: String) {
        onScriptFailed(reason)
    }
}
