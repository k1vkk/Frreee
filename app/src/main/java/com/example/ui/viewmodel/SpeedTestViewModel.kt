package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.NexoraApp
import com.example.data.local.SpeedTestResultEntity
import com.example.network.SpeedTestPhase
import com.example.network.SpeedTestProgress
import com.example.vpn.VpnConnectionStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SpeedTestViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NexoraApp
    private val diagnostics = app.diagnostics
    private val speedTestDao = app.database.speedTestDao()
    private val prefsRepo = app.prefsRepo
    private val vpnController = app.vpnController

    private val _progress = MutableStateFlow(SpeedTestProgress())
    val progress: StateFlow<SpeedTestProgress> = _progress.asStateFlow()

    val history: StateFlow<List<SpeedTestResultEntity>> = speedTestDao.observeHistory().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private var activeJob: Job? = null

    fun startSpeedTest() {
        if (activeJob?.isActive == true) return
        activeJob = viewModelScope.launch {
            val prefs = prefsRepo.preferencesFlow.first()
            val vpnState = vpnController.state.value
            val vpnOn = vpnState.status == VpnConnectionStatus.CONNECTED
            val label = if (vpnOn && vpnState.activeServer != null) {
                "${vpnState.activeServer.countryName} (${vpnState.activeServer.name})"
            } else if (prefs.apiBaseUrl.isNotBlank()) {
                prefs.apiBaseUrl.substringAfter("://").substringBefore('/')
            } else {
                "Cloudflare Edge"
            }

            val result = diagnostics.runSpeedTest(prefs.apiBaseUrl) { update ->
                _progress.value = update
            }

            result.onSuccess { final ->
                speedTestDao.insert(
                    SpeedTestResultEntity(
                        pingMs = final.pingMs,
                        jitterMs = final.jitterMs,
                        downloadMbps = final.downloadMbps,
                        uploadMbps = final.uploadMbps,
                        vpnActive = vpnOn,
                        serverLabel = label
                    )
                )
            }.onFailure { err ->
                _progress.value = _progress.value.copy(
                    phase = SpeedTestPhase.ERROR,
                    errorMessage = err.localizedMessage ?: "Diagnostic failed"
                )
            }
        }
    }

    fun stopSpeedTest() {
        activeJob?.cancel()
        activeJob = null
        _progress.value = SpeedTestProgress(phase = SpeedTestPhase.IDLE)
    }
}
