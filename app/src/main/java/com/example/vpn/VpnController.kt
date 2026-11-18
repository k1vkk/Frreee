package com.example.vpn

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.PowerManager
import com.example.NexoraConfig
import com.example.data.local.LocalDemoFixtures
import com.example.data.local.ServerProfileDao
import com.example.data.local.ServerProfileEntity
import com.example.data.local.UserPreferencesRepository
import com.example.data.remote.NexoraApiClient
import com.example.data.remote.ProvisionRequest
import com.example.data.remote.ServerDto
import com.example.data.remote.TelemetryErrorRequest
import com.example.data.remote.VpnDisconnectRequest
import com.example.network.NetworkDiagnostics
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.Inet6Address

enum class VpnConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    DISCONNECTING,
    ERROR
}

data class VpnSessionState(
    val status: VpnConnectionStatus = VpnConnectionStatus.DISCONNECTED,
    val activeServer: ServerProfileEntity? = null,
    val originalIp: String? = null,
    val isCheckingOriginalIp: Boolean = false,
    val tunnelIp: String? = null,
    val isCheckingTunnelIp: Boolean = false,
    val tunnelIpv4Assigned: String = "",
    val tunnelIpv6Assigned: String = "",
    val activeDnsServers: List<String> = emptyList(),
    val isDnsLeakProtected: Boolean = false,
    val isIpv4Active: Boolean = false,
    val isIpv6Active: Boolean = false,
    val isFindingBestServer: Boolean = false,
    val downloadSpeedBps: Long = 0L,
    val uploadSpeedBps: Long = 0L,
    val sessionRxBytes: Long = 0L,
    val sessionTxBytes: Long = 0L,
    val pingMs: Int? = null,
    val durationSeconds: Long = 0L,
    val errorMessage: String? = null
)

class VpnController(
    private val appContext: Context,
    private val prefsRepo: UserPreferencesRepository,
    private val serverDao: ServerProfileDao,
    private val apiClient: NexoraApiClient,
    private val diagnostics: NetworkDiagnostics
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val backend: GoBackend by lazy { GoBackend(appContext) }
    private val tunnel = NexoraTunnel()
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val powerManager =
        appContext.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val _state = MutableStateFlow(VpnSessionState())
    val state: StateFlow<VpnSessionState> = _state.asStateFlow()

    private var telemetryJob: Job? = null
    private var reconnectJob: Job? = null
    private var connectedAtMs: Long = 0L
    private var lastRxBytes: Long = 0L
    private var lastTxBytes: Long = 0L
    @Volatile
    private var userRequestedDisconnect: Boolean = true
    @Volatile
    private var isDemoTunnelActive: Boolean = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            scope.launch {
                val prefs = prefsRepo.preferencesFlow.first()
                val current = _state.value
                if (!userRequestedDisconnect &&
                    prefs.autoReconnectEnabled &&
                    (current.status == VpnConnectionStatus.RECONNECTING || current.status == VpnConnectionStatus.ERROR) &&
                    current.activeServer != null
                ) {
                    scheduleAutoReconnect(current.activeServer)
                }
            }
        }

        override fun onLost(network: Network) {
            scope.launch {
                val prefs = prefsRepo.preferencesFlow.first()
                val current = _state.value
                if (!userRequestedDisconnect &&
                    current.status == VpnConnectionStatus.CONNECTED &&
                    prefs.autoReconnectEnabled
                ) {
                    val hasAnyInternet = hasActiveNonVpnNetwork()
                    if (!hasAnyInternet) {
                        _state.update {
                            it.copy(
                                status = VpnConnectionStatus.RECONNECTING,
                                isDnsLeakProtected = false
                            )
                        }
                    }
                }
            }
        }
    }

    init {
        runCatching {
            val req = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(req, networkCallback)
        }

        scope.launch {
            prefsRepo.ensureWireGuardKeyPair()
            refreshOriginalIp()
            val prefs = prefsRepo.preferencesFlow.first()
            if (prefs.selectedServerId.isNotBlank()) {
                serverDao.getServerById(prefs.selectedServerId)?.let { saved ->
                    _state.update { it.copy(activeServer = saved) }
                }
            }
            syncServersFromBackend()
        }
    }

    private fun ServerDto.toEntity(isFavorite: Boolean = false): ServerProfileEntity =
        ServerProfileEntity(
            id = id,
            nodeCode = nodeCode,
            name = name,
            countryName = countryName,
            countryCode = countryCode,
            city = city,
            endpoint = endpoint,
            serverPublicKey = serverPublicKey,
            dnsServers = dnsServers,
            tier = tier,
            loadPercent = loadPercent,
            sourceType = "BACKEND",
            healthStatus = healthStatus,
            lastPingMs = latencyMs,
            lastHealthCheckAt = lastHealthCheckAt,
            isFavorite = isFavorite
        )

    suspend fun syncServersFromBackend(): Result<List<ServerProfileEntity>> {
        val service = apiClient.createService(NexoraConfig.NEXORA_API_BASE_URL)
        if (service != null) {
            try {
                val response = withContext(Dispatchers.IO) { service.getServers() }
                if (response.isSuccessful && response.body() != null) {
                    val existingFavs = serverDao.observeServers().first().associate { it.id to it.isFavorite }
                    val entities = response.body()!!.servers.map { dto ->
                        dto.toEntity(isFavorite = existingFavs[dto.id] ?: false)
                    }
                    if (entities.isEmpty()) {
                        serverDao.deleteAllBackendServers()
                    } else {
                        serverDao.upsertAll(entities)
                        serverDao.deleteStaleBackendServers(entities.map { it.id })
                        if (_state.value.activeServer == null) {
                            val savedId = prefsRepo.preferencesFlow.first().selectedServerId
                            val picked = entities.firstOrNull { it.id == savedId } ?: entities.first()
                            prefsRepo.setSelectedServerId(picked.id)
                            _state.update { it.copy(activeServer = picked) }
                        }
                    }
                    return Result.success(entities)
                } else if (!NexoraConfig.IS_LOCAL_DEMO_MODE) {
                    return Result.failure(IllegalStateException("Backend returned HTTP ${response.code()}"))
                }
            } catch (e: Exception) {
                if (!NexoraConfig.IS_LOCAL_DEMO_MODE) {
                    return Result.failure(e)
                }
            }
        } else if (!NexoraConfig.IS_LOCAL_DEMO_MODE) {
            return Result.failure(
                IllegalStateException("NEXORA_API_BASE_URL is not configured with a valid reachable HTTPS domain (${NexoraConfig.NEXORA_API_BASE_URL}).")
            )
        }

        // Local/Demo Mode fallback: load GCC preview nodes into Room seamlessly
        val existingFavs = serverDao.observeServers().first().associate { it.id to it.isFavorite }
        val demoEntities = LocalDemoFixtures.gccDemoServers(existingFavs)
        serverDao.upsertAll(demoEntities)
        serverDao.deleteStaleBackendServers(demoEntities.map { it.id })
        if (_state.value.activeServer == null) {
            val savedId = prefsRepo.preferencesFlow.first().selectedServerId
            val picked = demoEntities.firstOrNull { it.id == savedId } ?: demoEntities.first()
            prefsRepo.setSelectedServerId(picked.id)
            _state.update { it.copy(activeServer = picked) }
        }
        return Result.success(demoEntities)
    }

    suspend fun fetchBestOnlineServer(): Result<ServerProfileEntity> {
        val service = apiClient.createService(NexoraConfig.NEXORA_API_BASE_URL)
        if (service != null) {
            try {
                val bestRes = withContext(Dispatchers.IO) { service.getBestServer() }
                if (bestRes.isSuccessful && bestRes.body()?.server != null) {
                    val existingFav = serverDao.getServerById(bestRes.body()!!.server.id)?.isFavorite ?: false
                    val entity = bestRes.body()!!.server.toEntity(isFavorite = existingFav)
                    serverDao.upsert(entity)
                    return Result.success(entity)
                }
            } catch (e: Exception) {
                if (!NexoraConfig.IS_LOCAL_DEMO_MODE) {
                    return Result.failure(e)
                }
            }
        }
        val synced = syncServersFromBackend().getOrElse { return Result.failure(it) }
        val candidate = synced.firstOrNull { it.healthStatus != "offline" }
            ?: return Result.failure(IllegalStateException("No online WireGuard servers available on backend."))
        return Result.success(candidate)
    }

    fun prepareVpnIntent(): Intent? = VpnService.prepare(appContext)

    suspend fun refreshOriginalIp() {
        if (_state.value.status == VpnConnectionStatus.CONNECTED) return
        _state.update { it.copy(isCheckingOriginalIp = true) }
        val prefs = prefsRepo.preferencesFlow.first()
        val res = withContext(Dispatchers.IO) {
            apiClient.fetchPublicIp(prefs.apiBaseUrl)
        }
        _state.update {
            it.copy(
                originalIp = res.getOrNull(),
                isCheckingOriginalIp = false
            )
        }
    }

    suspend fun refreshTunnelIp(): String? {
        if (_state.value.status != VpnConnectionStatus.CONNECTED &&
            _state.value.status != VpnConnectionStatus.CONNECTING
        ) return null
        if (isDemoTunnelActive) {
            val demoIp = _state.value.activeServer?.endpoint?.substringBefore(':') ?: "185.199.108.153"
            _state.update { it.copy(tunnelIp = demoIp, isCheckingTunnelIp = false) }
            return demoIp
        }
        _state.update { it.copy(isCheckingTunnelIp = true) }
        val prefs = prefsRepo.preferencesFlow.first()
        val res = withContext(Dispatchers.IO) {
            apiClient.fetchPublicIp(prefs.apiBaseUrl)
        }
        val ip = res.getOrNull()
        _state.update {
            it.copy(
                tunnelIp = ip,
                isCheckingTunnelIp = false
            )
        }
        return ip
    }

    suspend fun selectServer(server: ServerProfileEntity) {
        prefsRepo.setSelectedServerId(server.id)
        _state.update { it.copy(activeServer = server, errorMessage = null) }
    }

    suspend fun findBestServerByPing(servers: List<ServerProfileEntity>): ServerProfileEntity? {
        val candidates = servers.filter { it.healthStatus != "offline" }
        if (candidates.isEmpty()) return null
        _state.update { it.copy(isFindingBestServer = true) }
        return try {
            val measured = coroutineScope {
                candidates.map { srv ->
                    async {
                        val ms = diagnostics.measureTcpPing(srv.endpoint, 2200)
                        val effectiveMs = ms ?: if (NexoraConfig.IS_LOCAL_DEMO_MODE) srv.lastPingMs else null
                        serverDao.updatePing(srv.id, effectiveMs)
                        srv.copy(lastPingMs = effectiveMs) to effectiveMs
                    }
                }.awaitAll()
            }
            measured
                .filter { it.second != null }
                .minByOrNull { it.second!! }
                ?.first
        } finally {
            _state.update { it.copy(isFindingBestServer = false) }
        }
    }

    suspend fun connect(serverOverride: ServerProfileEntity? = null) {
        var target = serverOverride ?: _state.value.activeServer
        if (target == null) {
            val fetched = fetchBestOnlineServer()
            target = fetched.getOrElse { err ->
                _state.update {
                    it.copy(
                        status = VpnConnectionStatus.ERROR,
                        errorMessage = err.localizedMessage ?: "Unable to fetch live WireGuard servers from backend."
                    )
                }
                return
            }
        }

        if (target.healthStatus == "offline") {
            _state.update {
                it.copy(
                    status = VpnConnectionStatus.ERROR,
                    errorMessage = "${target.name} is currently offline."
                )
            }
            return
        }

        val useDemoSimulation = NexoraConfig.IS_LOCAL_DEMO_MODE && target.sourceType == "BACKEND"
        if (!useDemoSimulation && prepareVpnIntent() != null) {
            _state.update {
                it.copy(
                    status = VpnConnectionStatus.ERROR,
                    errorMessage = "System VPN permission required."
                )
            }
            return
        }

        userRequestedDisconnect = false
        isDemoTunnelActive = false
        reconnectJob?.cancel()
        prefsRepo.setSelectedServerId(target.id)
        _state.update {
            it.copy(
                status = VpnConnectionStatus.CONNECTING,
                activeServer = target,
                errorMessage = null,
                tunnelIp = null,
                downloadSpeedBps = 0L,
                uploadSpeedBps = 0L,
                sessionRxBytes = 0L,
                sessionTxBytes = 0L,
                durationSeconds = 0L,
                isDnsLeakProtected = false
            )
        }

        if (_state.value.originalIp == null) {
            refreshOriginalIp()
        }

        try {
            val prefs = prefsRepo.preferencesFlow.first()
            val (privKey, pubKey) = prefsRepo.ensureWireGuardKeyPair()

            var wgQuickFromBackend: String? = null
            var remoteProvisioned = false
            val effectiveProfile = if (target.sourceType == "BACKEND") {
                val service = apiClient.createService(NexoraConfig.NEXORA_API_BASE_URL)
                if (service != null && prefs.authToken.isNotBlank()) {
                    try {
                        val provisionRes = withContext(Dispatchers.IO) {
                            service.vpnConnect(
                                bearer = "Bearer ${prefs.authToken}",
                                body = ProvisionRequest(serverId = target.id, clientPublicKey = pubKey)
                            )
                        }
                        if (provisionRes.isSuccessful && provisionRes.body() != null) {
                            var provisioned = provisionRes.body()!!
                            if (provisioned.wgQuickConfig.isNullOrBlank()) {
                                val configRes = withContext(Dispatchers.IO) {
                                    service.getVpnConfig(
                                        bearer = "Bearer ${prefs.authToken}",
                                        serverId = target.id,
                                        clientPublicKey = pubKey
                                    )
                                }
                                if (configRes.isSuccessful && configRes.body() != null) {
                                    provisioned = configRes.body()!!
                                }
                            }
                            wgQuickFromBackend = provisioned.wgQuickConfig
                            remoteProvisioned = true
                            val v4 = provisioned.assignedIpv4 ?: provisioned.assignedAddress.substringBefore(',').trim()
                            val v6 = provisioned.assignedIpv6 ?: provisioned.assignedAddress.substringAfter(',', "").trim()
                                .ifBlank { "fd80:4e58:5200::2/128" }
                            target.copy(
                                endpoint = provisioned.endpoint,
                                serverPublicKey = provisioned.serverPublicKey,
                                dnsServers = provisioned.dnsServers,
                                assignedAddress = v4,
                                assignedIpv6 = v6,
                                allowedIps = provisioned.allowedIps
                            )
                        } else if (NexoraConfig.IS_LOCAL_DEMO_MODE) {
                            target
                        } else {
                            throw IllegalStateException("Peer provisioning failed (HTTP ${provisionRes.code()})")
                        }
                    } catch (e: Exception) {
                        if (NexoraConfig.IS_LOCAL_DEMO_MODE) {
                            target
                        } else {
                            throw e
                        }
                    }
                } else if (NexoraConfig.IS_LOCAL_DEMO_MODE) {
                    target
                } else {
                    if (service == null) {
                        throw IllegalStateException("Invalid embedded backend URL (${NexoraConfig.NEXORA_API_BASE_URL}).")
                    }
                    throw IllegalStateException("Sign in to your account to generate your encrypted WireGuard config automatically.")
                }
            } else {
                target
            }

            if (NexoraConfig.IS_LOCAL_DEMO_MODE && !remoteProvisioned && effectiveProfile.sourceType == "BACKEND") {
                delay(550L)
                if (userRequestedDisconnect) return
                isDemoTunnelActive = true
                connectedAtMs = System.currentTimeMillis()
                lastRxBytes = 0L
                lastTxBytes = 0L
                val demoTunnelIp = effectiveProfile.endpoint.substringBefore(':')
                val dnsList = prefs.customDns.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                _state.update {
                    it.copy(
                        status = VpnConnectionStatus.CONNECTED,
                        activeServer = effectiveProfile,
                        tunnelIp = demoTunnelIp,
                        tunnelIpv4Assigned = effectiveProfile.assignedAddress,
                        tunnelIpv6Assigned = effectiveProfile.assignedIpv6,
                        activeDnsServers = dnsList,
                        isDnsLeakProtected = prefs.dnsLeakProtectionEnabled && dnsList.isNotEmpty(),
                        isIpv4Active = true,
                        isIpv6Active = true,
                        pingMs = effectiveProfile.lastPingMs ?: 26
                    )
                }
                startTelemetryLoop(effectiveProfile, prefs.dnsLeakProtectionEnabled)
                return
            }

            val wgConfig = WireGuardConfigHelper.buildWireGuardConfig(
                profile = effectiveProfile,
                privateKeyBase64 = privKey,
                assignedIpv4 = effectiveProfile.assignedAddress,
                assignedIpv6 = effectiveProfile.assignedIpv6,
                customDns = prefs.customDns,
                dnsLeakProtectionEnabled = prefs.dnsLeakProtectionEnabled,
                mtu = prefs.tunnelMtu,
                excludedPackages = prefs.excludedPackages,
                wgQuickConfigFromBackend = wgQuickFromBackend
            )

            withContext(Dispatchers.IO) {
                backend.setState(tunnel, Tunnel.State.UP, wgConfig)
            }

            // CRITICAL: Verify real WireGuard handshake & tunnel traffic before reporting CONNECTED.
            val verified = verifyRealHandshakeAndTraffic(NexoraConfig.NEXORA_API_BASE_URL)
            if (!verified) {
                withContext(Dispatchers.IO) {
                    runCatching { backend.setState(tunnel, Tunnel.State.DOWN, null) }
                }
                throw IllegalStateException(
                    "WireGuard handshake failed: ${effectiveProfile.endpoint} did not respond. Verify VPS server is running and public key matches."
                )
            }

            connectedAtMs = System.currentTimeMillis()
            lastRxBytes = 0L
            lastTxBytes = 0L

            val audit = auditTunnelNetwork(prefs.dnsLeakProtectionEnabled)
            _state.update {
                it.copy(
                    status = VpnConnectionStatus.CONNECTED,
                    activeServer = effectiveProfile,
                    tunnelIpv4Assigned = effectiveProfile.assignedAddress,
                    tunnelIpv6Assigned = effectiveProfile.assignedIpv6,
                    activeDnsServers = audit.dnsServers,
                    isDnsLeakProtected = audit.dnsProtected,
                    isIpv4Active = audit.hasIpv4,
                    isIpv6Active = audit.hasIpv6
                )
            }

            startNotificationService()
            startTelemetryLoop(effectiveProfile, prefs.dnsLeakProtectionEnabled)
            scope.launch { refreshTunnelIp() }
        } catch (e: Exception) {
            val prefs = prefsRepo.preferencesFlow.first()
            val message = e.localizedMessage ?: "Failed to establish real WireGuard tunnel"
            if (target.sourceType == "BACKEND" && prefs.authToken.isNotBlank()) {
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        apiClient.createService(prefs.apiBaseUrl)?.reportError(
                            bearer = "Bearer ${prefs.authToken}",
                            body = TelemetryErrorRequest(
                                level = "error",
                                source = "wireguard-tunnel",
                                nodeCode = target.nodeCode.ifBlank { null },
                                message = message
                            )
                        )
                    }
                }
            }
            _state.update {
                it.copy(
                    status = VpnConnectionStatus.ERROR,
                    errorMessage = message
                )
            }
        }
    }

    private suspend fun verifyRealHandshakeAndTraffic(backendBaseUrl: String): Boolean {
        // Trigger a small real HTTP probe through the newly created tun0 interface and inspect GoBackend Rx stats
        val ipCheckDeferred = scope.async(Dispatchers.IO) {
            apiClient.fetchPublicIp(backendBaseUrl)
        }
        repeat(12) {
            delay(500L)
            val stats = withContext(Dispatchers.IO) {
                runCatching { backend.getStatistics(tunnel) }.getOrNull()
            }
            val rx = stats?.totalRx() ?: 0L
            if (rx > 0L) {
                val ipRes = runCatching { ipCheckDeferred.await() }.getOrNull()
                if (ipRes?.isSuccess == true) {
                    _state.update { it.copy(tunnelIp = ipRes.getOrNull()) }
                    return true
                }
                // Even if ipify is blocked, non-zero authenticated WireGuard Rx bytes proves a real peer handshake occurred
                return true
            }
        }
        ipCheckDeferred.cancel()
        return false
    }

    private fun scheduleAutoReconnect(server: ServerProfileEntity) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            _state.update { it.copy(status = VpnConnectionStatus.RECONNECTING) }
            delay(1500L)
            if (!userRequestedDisconnect) {
                connect(server)
            }
        }
    }

    suspend fun disconnect() {
        userRequestedDisconnect = true
        reconnectJob?.cancel()
        telemetryJob?.cancel()
        telemetryJob = null
        _state.update { it.copy(status = VpnConnectionStatus.DISCONNECTING) }
        val previousServer = _state.value.activeServer
        val rxTotal = _state.value.sessionRxBytes
        val txTotal = _state.value.sessionTxBytes
        val wasDemo = isDemoTunnelActive
        isDemoTunnelActive = false
        if (!wasDemo) {
            try {
                withContext(Dispatchers.IO) {
                    backend.setState(tunnel, Tunnel.State.DOWN, null)
                }
            } catch (_: Exception) {
            }
        }
        if (previousServer?.sourceType == "BACKEND") {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val prefs = prefsRepo.preferencesFlow.first()
                    val service = apiClient.createService(prefs.apiBaseUrl)
                    if (service != null && prefs.authToken.isNotBlank() && prefs.wgPublicKey.isNotBlank()) {
                        service.vpnDisconnect(
                            bearer = "Bearer ${prefs.authToken}",
                            body = VpnDisconnectRequest(
                                serverId = previousServer.id,
                                clientPublicKey = prefs.wgPublicKey,
                                rxBytes = rxTotal,
                                txBytes = txTotal
                            )
                        )
                    }
                }
            }
        }
        _state.update {
            it.copy(
                status = VpnConnectionStatus.DISCONNECTED,
                tunnelIp = null,
                tunnelIpv4Assigned = "",
                tunnelIpv6Assigned = "",
                activeDnsServers = emptyList(),
                isDnsLeakProtected = false,
                isIpv4Active = false,
                isIpv6Active = false,
                downloadSpeedBps = 0L,
                uploadSpeedBps = 0L,
                durationSeconds = 0L
            )
        }
        appContext.stopService(Intent(appContext, NexoraVpnNotificationService::class.java))
    }

    fun clearError() {
        _state.update {
            if (it.status == VpnConnectionStatus.ERROR) {
                it.copy(status = VpnConnectionStatus.DISCONNECTED, errorMessage = null)
            } else {
                it.copy(errorMessage = null)
            }
        }
    }

    private fun startNotificationService() {
        val intent = Intent(appContext, NexoraVpnNotificationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(intent)
        } else {
            appContext.startService(intent)
        }
    }

    private fun startTelemetryLoop(server: ServerProfileEntity, dnsLeakProtectionEnabled: Boolean) {
        telemetryJob?.cancel()
        telemetryJob = scope.launch {
            var tickCount = 0
            while (isActive) {
                if (isDemoTunnelActive) {
                    val rxDelta = 148_000L + ((tickCount * 37) % 190) * 1_024L
                    val txDelta = 38_000L + ((tickCount * 19) % 64) * 1_024L
                    lastRxBytes += rxDelta
                    lastTxBytes += txDelta
                    prefsRepo.addTrafficBytes(rxDelta, txDelta)

                    val basePing = server.lastPingMs ?: 26
                    val jitteredPing = (basePing + ((tickCount % 5) - 2)).coerceAtLeast(12)
                    val interactive = powerManager.isInteractive && !powerManager.isPowerSaveMode
                    _state.update {
                        it.copy(
                            downloadSpeedBps = rxDelta * 8L,
                            uploadSpeedBps = txDelta * 8L,
                            sessionRxBytes = lastRxBytes,
                            sessionTxBytes = lastTxBytes,
                            pingMs = jitteredPing,
                            isDnsLeakProtected = dnsLeakProtectionEnabled,
                            isIpv4Active = true,
                            isIpv6Active = true,
                            durationSeconds = ((System.currentTimeMillis() - connectedAtMs) / 1000L).coerceAtLeast(0L)
                        )
                    }
                    tickCount++
                    delay(if (interactive) 1000L else 8000L)
                    continue
                }

                val stats = withContext(Dispatchers.IO) {
                    runCatching { backend.getStatistics(tunnel) }.getOrNull()
                }
                val rx = stats?.totalRx() ?: lastRxBytes
                val tx = stats?.totalTx() ?: lastTxBytes
                val rxDelta = (rx - lastRxBytes).coerceAtLeast(0L)
                val txDelta = (tx - lastTxBytes).coerceAtLeast(0L)
                lastRxBytes = rx
                lastTxBytes = tx

                if (rxDelta > 0 || txDelta > 0) {
                    prefsRepo.addTrafficBytes(rxDelta, txDelta)
                }

                val interactive = powerManager.isInteractive && !powerManager.isPowerSaveMode
                val ping = if (interactive && tickCount % 5 == 0) {
                    diagnostics.measureTcpPing(server.endpoint, 2000)
                } else {
                    _state.value.pingMs
                }

                if (server.sourceType == "BACKEND" && tickCount > 0 && tickCount % 10 == 0) {
                    scope.launch(Dispatchers.IO) {
                        runCatching {
                            val prefs = prefsRepo.preferencesFlow.first()
                            val service = apiClient.createService(NexoraConfig.NEXORA_API_BASE_URL)
                            if (service != null && prefs.authToken.isNotBlank() && prefs.wgPublicKey.isNotBlank()) {
                                service.getVpnStatus(
                                    bearer = "Bearer ${prefs.authToken}",
                                    serverId = server.id,
                                    clientPublicKey = prefs.wgPublicKey
                                )
                            }
                        }
                    }
                }

                val audit = auditTunnelNetwork(dnsLeakProtectionEnabled)

                _state.update {
                    it.copy(
                        downloadSpeedBps = rxDelta * 8L,
                        uploadSpeedBps = txDelta * 8L,
                        sessionRxBytes = rx,
                        sessionTxBytes = tx,
                        pingMs = ping,
                        activeDnsServers = audit.dnsServers,
                        isDnsLeakProtected = audit.dnsProtected,
                        isIpv4Active = audit.hasIpv4,
                        isIpv6Active = audit.hasIpv6,
                        durationSeconds = ((System.currentTimeMillis() - connectedAtMs) / 1000L).coerceAtLeast(0L)
                    )
                }
                tickCount++
                // Adaptive battery optimization: poll every 8s when screen is off or Battery Saver is on
                delay(if (interactive) 1000L else 8000L)
            }
        }
    }

    private data class TunnelAudit(
        val dnsServers: List<String>,
        val dnsProtected: Boolean,
        val hasIpv4: Boolean,
        val hasIpv6: Boolean
    )

    private fun auditTunnelNetwork(dnsLeakProtectionEnabled: Boolean): TunnelAudit {
        val networks = connectivityManager.allNetworks
        for (net in networks) {
            val caps = connectivityManager.getNetworkCapabilities(net) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                val lp = connectivityManager.getLinkProperties(net) ?: continue
                val dnsList = lp.dnsServers.mapNotNull { it.hostAddress }
                val hasV4 = lp.linkAddresses.any { it.address is Inet4Address }
                val hasV6 = lp.linkAddresses.any { it.address is Inet6Address }
                return TunnelAudit(
                    dnsServers = dnsList,
                    dnsProtected = dnsLeakProtectionEnabled && dnsList.isNotEmpty(),
                    hasIpv4 = hasV4,
                    hasIpv6 = hasV6
                )
            }
        }
        return TunnelAudit(emptyList(), false, false, false)
    }

    private fun hasActiveNonVpnNetwork(): Boolean {
        val active = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

fun formatSpeedCompact(bitsPerSec: Long): String {
    return when {
        bitsPerSec >= 1_000_000_000L -> String.format("%.2f Gbps", bitsPerSec / 1_000_000_000.0)
        bitsPerSec >= 1_000_000L -> String.format("%.2f Mbps", bitsPerSec / 1_000_000.0)
        bitsPerSec >= 1_000L -> String.format("%.1f Kbps", bitsPerSec / 1_000.0)
        else -> "$bitsPerSec bps"
    }
}

fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824L -> String.format("%.2f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> String.format("%.2f MB", bytes / 1_048_576.0)
        bytes >= 1_024L -> String.format("%.1f KB", bytes / 1_024.0)
        else -> "$bytes B"
    }
}

fun formatDuration(seconds: Long): String {
    val h = seconds / 3600L
    val m = (seconds % 3600L) / 60L
    val s = seconds % 60L
    return String.format("%02d:%02d:%02d", h, m, s)
}
