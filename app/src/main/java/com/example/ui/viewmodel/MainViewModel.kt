package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.NexoraApp
import com.example.R
import com.example.billing.BillingUiState
import com.example.data.local.ServerProfileEntity
import com.example.data.local.UserPreferences
import com.example.vpn.VpnConnectionStatus
import com.example.vpn.VpnSessionState
import com.example.vpn.WireGuardConfigHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NexoraApp
    private val vpnController = app.vpnController
    private val prefsRepo = app.prefsRepo
    private val serverDao = app.database.serverProfileDao()
    private val billingManager = app.billingManager

    val vpnState: StateFlow<VpnSessionState> = vpnController.state
    val billingState: StateFlow<BillingUiState> = billingManager.state

    val preferences: StateFlow<UserPreferences> = prefsRepo.preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = UserPreferences()
    )

    val servers: StateFlow<List<ServerProfileEntity>> = serverDao.observeServers().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _vpnPermissionRequest = MutableSharedFlow<Intent>()
    val vpnPermissionRequest: SharedFlow<Intent> = _vpnPermissionRequest.asSharedFlow()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    private var pendingServerToConnect: ServerProfileEntity? = null

    init {
        viewModelScope.launch {
            vpnController.syncServersFromBackend()
            val prefs = prefsRepo.preferencesFlow.first()
            if (prefs.autoConnect) {
                val saved = if (prefs.selectedServerId.isNotBlank()) {
                    serverDao.getServerById(prefs.selectedServerId)
                } else {
                    serverDao.observeServers().first().firstOrNull {
                        it.healthStatus != "offline" && it.healthStatus != "pending_vps" && it.endpoint.contains(":")
                    }
                }
                if (saved != null &&
                    saved.healthStatus != "offline" &&
                    saved.healthStatus != "pending_vps" &&
                    saved.endpoint.contains(":") &&
                    vpnController.prepareVpnIntent() == null
                ) {
                    vpnController.connect(saved)
                }
            }
        }
    }

    fun syncServers() {
        viewModelScope.launch {
            vpnController.syncServersFromBackend().onFailure { err ->
                _uiMessage.value = err.localizedMessage ?: app.getString(R.string.servers_empty_body)
            }
        }
    }

    fun onConnectClicked() {
        val current = vpnState.value
        viewModelScope.launch {
            when (current.status) {
                VpnConnectionStatus.CONNECTED,
                VpnConnectionStatus.CONNECTING,
                VpnConnectionStatus.RECONNECTING -> {
                    vpnController.disconnect()
                }
                else -> {
                    val target = current.activeServer?.takeIf {
                        it.healthStatus != "offline" && it.healthStatus != "pending_vps" && it.endpoint.contains(":")
                    } ?: servers.value.firstOrNull {
                        it.healthStatus != "offline" && it.healthStatus != "pending_vps" && it.endpoint.contains(":")
                    }
                    if (target != null) {
                        connectToServer(target)
                    } else {
                        val fetched = vpnController.fetchBestOnlineServer()
                        fetched.onSuccess { server ->
                            connectToServer(server)
                        }.onFailure { err ->
                            _uiMessage.value = err.localizedMessage
                                ?: app.getString(R.string.servers_empty_body)
                        }
                    }
                }
            }
        }
    }

    fun connectBestServerByPing() {
        viewModelScope.launch {
            var list = serverDao.observeServers().first()
            if (list.isEmpty()) {
                val syncResult = vpnController.syncServersFromBackend()
                if (syncResult.isFailure) {
                    _uiMessage.value = syncResult.exceptionOrNull()?.localizedMessage
                        ?: app.getString(R.string.servers_empty_body)
                    return@launch
                }
                list = serverDao.observeServers().first()
            }
            if (list.isEmpty()) {
                _uiMessage.value = app.getString(R.string.error_no_reachable_server)
                return@launch
            }
            val best = vpnController.findBestServerByPing(list)
            if (best == null) {
                _uiMessage.value = app.getString(R.string.error_no_reachable_server)
                return@launch
            }
            connectToServer(best)
        }
    }

    fun connectToServer(server: ServerProfileEntity) {
        viewModelScope.launch {
            val prepareIntent = vpnController.prepareVpnIntent()
            if (prepareIntent != null) {
                pendingServerToConnect = server
                _vpnPermissionRequest.emit(prepareIntent)
            } else {
                vpnController.connect(server)
            }
        }
    }

    fun onVpnPermissionResult(granted: Boolean) {
        viewModelScope.launch {
            if (granted) {
                vpnController.connect(pendingServerToConnect)
                pendingServerToConnect = null
            } else {
                pendingServerToConnect = null
                _uiMessage.value = app.getString(R.string.error_vpn_permission_denied)
            }
        }
    }

    fun refreshIps() {
        viewModelScope.launch {
            if (vpnState.value.status == VpnConnectionStatus.CONNECTED) {
                vpnController.refreshTunnelIp()
            } else {
                vpnController.refreshOriginalIp()
            }
        }
    }

    fun importConfFromUri(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val fileName = resolveFileName(uri) ?: "nexora.conf"
                app.contentResolver.openInputStream(uri)?.use { stream ->
                    WireGuardConfigHelper.parseConfToProfile(stream, fileName)
                } ?: Result.failure(IllegalStateException("Unable to read selected file"))
            }
            result.onSuccess { profile ->
                serverDao.upsert(profile)
                vpnController.selectServer(profile)
                _uiMessage.value = "${profile.name} (${profile.endpoint})"
            }.onFailure { err ->
                _uiMessage.value = err.localizedMessage ?: "Invalid WireGuard .conf file"
            }
        }
    }

    fun clearMessage() {
        _uiMessage.value = null
        vpnController.clearError()
    }

    private fun resolveFileName(uri: Uri): String? {
        app.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                return cursor.getString(idx)
            }
        }
        return uri.lastPathSegment
    }
}
