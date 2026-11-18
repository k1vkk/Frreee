package com.example.ui.viewmodel

import android.app.Application
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.NexoraApp
import com.example.R
import com.example.data.local.SpeedTestResultEntity
import com.example.data.remote.SpeedTestUploadRequest
import com.example.network.RealHttpSpeedTestEngine
import com.example.network.SpeedOfMeSpeedTestEngine
import com.example.network.SpeedTestProgress
import com.example.vpn.VpnConnectionStatus
import com.example.vpn.VpnSessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SpeedHistoryFilter {
    ALL,
    DIRECT,
    VPN
}

data class VpnComparisonMetrics(
    val downloadDiffMbps: Double,
    val downloadDiffPercent: Double?,
    val uploadDiffMbps: Double,
    val uploadDiffPercent: Double?,
    val pingDiffMs: Int,
    val pingDiffPercent: Double?,
    val jitterDiffMs: Int,
    val jitterDiffPercent: Double?
)

data class VpnComparisonUiState(
    val baselineTest: SpeedTestResultEntity? = null,
    val targetTest: SpeedTestResultEntity? = null,
    val isCustomSelection: Boolean = false,
    val metrics: VpnComparisonMetrics? = null
)

class SpeedTestViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NexoraApp
    private val speedTestDao = app.database.speedTestDao()
    private val prefsRepo = app.prefsRepo
    private val vpnController = app.vpnController
    private val apiClient = app.apiClient

    private val somEngine = SpeedOfMeSpeedTestEngine(app.applicationContext)
    private val httpEngine = RealHttpSpeedTestEngine(app.applicationContext, apiClient.okHttpClient)

    private val useHttpFallback = MutableStateFlow(somEngine.resolveCredentials() == null)

    val progress: StateFlow<SpeedTestProgress> = combine(
        somEngine.progress,
        httpEngine.progress,
        useHttpFallback
    ) { som, http, isHttp ->
        if (isHttp) http else som
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SpeedTestProgress(
            provider = if (somEngine.resolveCredentials() != null) "speedofme" else "cloudflare_http"
        )
    )

    val vpnState: StateFlow<VpnSessionState> = vpnController.state

    val ecoMode: StateFlow<Boolean> = prefsRepo.preferencesFlow
        .map { it.speedTestEcoMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val includeUpload: StateFlow<Boolean> = prefsRepo.preferencesFlow
        .map { it.speedTestIncludeUpload }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val history: StateFlow<List<SpeedTestResultEntity>> = speedTestDao.observeHistory().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _historyFilter = MutableStateFlow(SpeedHistoryFilter.ALL)
    val historyFilter: StateFlow<SpeedHistoryFilter> = _historyFilter.asStateFlow()

    val filteredHistory: StateFlow<List<SpeedTestResultEntity>> = combine(
        history,
        _historyFilter
    ) { list, filter ->
        when (filter) {
            SpeedHistoryFilter.ALL -> list
            SpeedHistoryFilter.DIRECT -> list.filter { !it.vpnActive }
            SpeedHistoryFilter.VPN -> list.filter { it.vpnActive }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val customBaselineId = MutableStateFlow<Long?>(null)
    private val customTargetId = MutableStateFlow<Long?>(null)

    val comparisonState: StateFlow<VpnComparisonUiState> = combine(
        history,
        customBaselineId,
        customTargetId
    ) { list, baseId, targetId ->
        val isCustom = baseId != null || targetId != null
        val baseline = if (baseId != null) {
            list.firstOrNull { it.id == baseId }
        } else {
            list.firstOrNull { !it.vpnActive }
        }
        val target = if (targetId != null) {
            list.firstOrNull { it.id == targetId }
        } else {
            list.firstOrNull { it.vpnActive }
        }
        val metrics = if (baseline != null && target != null && baseline.id != target.id) {
            calculateMetrics(baseline, target)
        } else {
            null
        }
        VpnComparisonUiState(
            baselineTest = baseline,
            targetTest = target,
            isCustomSelection = isCustom,
            metrics = metrics
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VpnComparisonUiState())

    fun attachWebView(webView: WebView) {
        somEngine.attachWebView(webView)
    }

    fun detachWebView(webView: WebView) {
        somEngine.detachWebView(webView)
    }

    fun startSpeedTest() {
        viewModelScope.launch {
            val prefs = prefsRepo.preferencesFlow.first()
            val vpnSnapshot = vpnController.state.value
            val vpnOn = vpnSnapshot.status == VpnConnectionStatus.CONNECTED
            val label = if (vpnOn && vpnSnapshot.activeServer != null) {
                "${vpnSnapshot.activeServer.countryName} (${vpnSnapshot.activeServer.name})"
            } else {
                app.getString(R.string.label_direct_isp)
            }

            // Check local BuildConfig credentials or remote /api/speedtest/config credentials
            var creds = somEngine.resolveCredentials()
            if (creds == null) {
                val service = apiClient.createService(prefs.apiBaseUrl)
                if (service != null) {
                    runCatching {
                        val cfgRes = withContext(Dispatchers.IO) { service.getSpeedTestConfig() }
                        val cfg = cfgRes.body()
                        if (cfgRes.isSuccessful && cfg?.speedOfMeConfigured == true) {
                            creds = somEngine.resolveCredentials(cfg.apiKey, cfg.domain)
                        }
                    }
                }
            }

            val onDone: (SpeedTestProgress) -> Unit = { finalResult ->
                viewModelScope.launch {
                    speedTestDao.insert(
                        SpeedTestResultEntity(
                            pingMs = finalResult.pingMs,
                            jitterMs = finalResult.jitterMs,
                            downloadMbps = finalResult.downloadMbps,
                            uploadMbps = finalResult.uploadMbps,
                            maxDownloadMbps = finalResult.maxDownloadMbps,
                            maxUploadMbps = finalResult.maxUploadMbps,
                            vpnActive = vpnOn,
                            serverLabel = label,
                            testServer = finalResult.testServer,
                            publicIp = finalResult.publicIp,
                            ispHostname = finalResult.ispHostname,
                            dataUsedBytes = finalResult.dataUsedBytes,
                            sustainTimeSec = finalResult.sustainTimeSec,
                            provider = finalResult.provider
                        )
                    )
                    if (prefs.authToken.isNotBlank()) {
                        val service = apiClient.createService(prefs.apiBaseUrl)
                        if (service != null) {
                            withContext(Dispatchers.IO) {
                                runCatching {
                                    service.uploadSpeedTestResult(
                                        bearer = "Bearer ${prefs.authToken}",
                                        body = SpeedTestUploadRequest(
                                            serverId = vpnSnapshot.activeServer?.id?.takeIf { vpnOn },
                                            provider = finalResult.provider,
                                            vpnActive = vpnOn,
                                            serverLabel = label,
                                            pingMs = finalResult.pingMs,
                                            jitterMs = finalResult.jitterMs,
                                            downloadMbps = finalResult.downloadMbps,
                                            uploadMbps = finalResult.uploadMbps,
                                            maxDownloadMbps = finalResult.maxDownloadMbps,
                                            maxUploadMbps = finalResult.maxUploadMbps,
                                            dataUsedBytes = finalResult.dataUsedBytes,
                                            testServer = finalResult.testServer.ifBlank { null },
                                            publicIp = finalResult.publicIp.ifBlank { null }
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (creds != null) {
                useHttpFallback.value = false
                httpEngine.stop(resetToIdle = false)
                somEngine.startTest(
                    ecoMode = prefs.speedTestEcoMode,
                    includeUpload = prefs.speedTestIncludeUpload,
                    overrideApiKey = creds!!.first,
                    overrideDomain = creds!!.second,
                    onCompleted = onDone
                )
            } else {
                useHttpFallback.value = true
                somEngine.stop(resetToIdle = false)
                httpEngine.startTest(
                    ecoMode = prefs.speedTestEcoMode,
                    includeUpload = prefs.speedTestIncludeUpload,
                    onCompleted = onDone
                )
            }
        }
    }

    fun stopSpeedTest() {
        somEngine.stop(resetToIdle = true)
        httpEngine.stop(resetToIdle = true)
    }

    fun setEcoMode(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setSpeedTestEcoMode(enabled)
        }
    }

    fun setIncludeUpload(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setSpeedTestIncludeUpload(enabled)
        }
    }

    fun setHistoryFilter(filter: SpeedHistoryFilter) {
        _historyFilter.value = filter
    }

    fun selectAsBaseline(entryId: Long) {
        customBaselineId.value = entryId
        if (customTargetId.value == null) {
            customTargetId.value = comparisonState.value.targetTest?.id
        }
    }

    fun selectAsTarget(entryId: Long) {
        customTargetId.value = entryId
        if (customBaselineId.value == null) {
            customBaselineId.value = comparisonState.value.baselineTest?.id
        }
    }

    fun resetComparisonToDefault() {
        customBaselineId.value = null
        customTargetId.value = null
    }

    fun deleteHistoryEntry(id: Long) {
        viewModelScope.launch {
            if (customBaselineId.value == id) customBaselineId.value = null
            if (customTargetId.value == id) customTargetId.value = null
            speedTestDao.deleteById(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            customBaselineId.value = null
            customTargetId.value = null
            speedTestDao.clearAll()
        }
    }

    override fun onCleared() {
        somEngine.stop(resetToIdle = false)
        httpEngine.stop(resetToIdle = false)
        super.onCleared()
    }

    private fun calculateMetrics(
        before: SpeedTestResultEntity,
        after: SpeedTestResultEntity
    ): VpnComparisonMetrics {
        val dlDiff = after.downloadMbps - before.downloadMbps
        val ulDiff = after.uploadMbps - before.uploadMbps
        val pingDiff = after.pingMs - before.pingMs
        val jitterDiff = after.jitterMs - before.jitterMs
        return VpnComparisonMetrics(
            downloadDiffMbps = dlDiff,
            downloadDiffPercent = pct(before.downloadMbps, dlDiff),
            uploadDiffMbps = ulDiff,
            uploadDiffPercent = pct(before.uploadMbps, ulDiff),
            pingDiffMs = pingDiff,
            pingDiffPercent = pct(before.pingMs.toDouble(), pingDiff.toDouble()),
            jitterDiffMs = jitterDiff,
            jitterDiffPercent = pct(before.jitterMs.toDouble(), jitterDiff.toDouble())
        )
    }

    private fun pct(base: Double, delta: Double): Double? {
        return if (base > 0.001) (delta / base) * 100.0 else null
    }
}
