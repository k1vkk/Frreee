package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.NexoraApp
import com.example.data.local.ServerProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class ServerFilter {
    ALL,
    FAVORITES,
    IMPORTED_CONF,
    BACKEND
}

data class ServersUiState(
    val servers: List<ServerProfileEntity> = emptyList(),
    val searchQuery: String = "",
    val selectedFilter: ServerFilter = ServerFilter.ALL,
    val isSyncing: Boolean = false,
    val isPingingAll: Boolean = false,
    val statusMessage: String? = null
)

class ServersViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NexoraApp
    private val serverDao = app.database.serverProfileDao()
    private val prefsRepo = app.prefsRepo
    private val apiClient = app.apiClient
    private val diagnostics = app.diagnostics

    private val searchQuery = MutableStateFlow("")
    private val selectedFilter = MutableStateFlow(ServerFilter.ALL)
    private val isSyncing = MutableStateFlow(false)
    private val isPingingAll = MutableStateFlow(false)
    private val statusMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ServersUiState> = combine(
        serverDao.observeServers(),
        searchQuery,
        selectedFilter,
        isSyncing,
        isPingingAll,
        statusMessage
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val list = values[0] as List<ServerProfileEntity>
        val query = values[1] as String
        val filter = values[2] as ServerFilter
        val syncing = values[3] as Boolean
        val pinging = values[4] as Boolean
        val msg = values[5] as String?

        val filtered = list.filter { item ->
            val isAvailable = item.healthStatus != "offline"
            val matchesFilter = when (filter) {
                ServerFilter.ALL -> true
                ServerFilter.FAVORITES -> item.isFavorite
                ServerFilter.IMPORTED_CONF -> item.sourceType == "IMPORTED_CONF" || item.sourceType == "MANUAL"
                ServerFilter.BACKEND -> item.sourceType == "BACKEND"
            }
            val q = query.trim().lowercase()
            val matchesQuery = q.isEmpty() ||
                item.name.lowercase().contains(q) ||
                item.nodeCode.lowercase().contains(q) ||
                item.countryName.lowercase().contains(q) ||
                item.city.lowercase().contains(q) ||
                item.endpoint.lowercase().contains(q)
            isAvailable && matchesFilter && matchesQuery
        }

        ServersUiState(
            servers = filtered,
            searchQuery = query,
            selectedFilter = filter,
            isSyncing = syncing,
            isPingingAll = pinging,
            statusMessage = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ServersUiState()
    )

    init {
        viewModelScope.launch {
            val prefs = prefsRepo.preferencesFlow.first()
            if (prefs.apiBaseUrl.isNotBlank()) {
                syncServersFromBackend()
            }
        }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setFilter(filter: ServerFilter) {
        selectedFilter.value = filter
    }

    fun clearMessage() {
        statusMessage.value = null
    }

    fun syncServersFromBackend() {
        viewModelScope.launch {
            isSyncing.value = true
            statusMessage.value = null
            try {
                val prefs = prefsRepo.preferencesFlow.first()
                val service = apiClient.createService(prefs.apiBaseUrl)
                if (service == null) {
                    statusMessage.value = "Set a valid Backend API URL in Settings first."
                    return@launch
                }
                val response = withContext(Dispatchers.IO) { service.getServers() }
                if (!response.isSuccessful || response.body() == null) {
                    statusMessage.value = "Backend returned HTTP ${response.code()}"
                    return@launch
                }
                val existingFavs = serverDao.observeServers().first()
                    .associate { it.id to it.isFavorite }
                val entities = response.body()!!.servers.map { dto ->
                    ServerProfileEntity(
                        id = dto.id,
                        nodeCode = dto.nodeCode,
                        name = dto.name,
                        countryName = dto.countryName,
                        countryCode = dto.countryCode,
                        city = dto.city,
                        endpoint = dto.endpoint,
                        serverPublicKey = dto.serverPublicKey,
                        dnsServers = dto.dnsServers,
                        tier = dto.tier,
                        loadPercent = dto.loadPercent,
                        sourceType = "BACKEND",
                        healthStatus = dto.healthStatus,
                        lastPingMs = dto.latencyMs,
                        lastHealthCheckAt = dto.lastHealthCheckAt,
                        isFavorite = existingFavs[dto.id] ?: false
                    )
                }
                if (entities.isEmpty()) {
                    serverDao.deleteAllBackendServers()
                    statusMessage.value = "0 online GCC VPS nodes active in backend. Provision SA01–OM01 VPS nodes first."
                } else {
                    serverDao.upsertAll(entities)
                    serverDao.deleteStaleBackendServers(entities.map { it.id })
                    measureAllPings()
                }
            } catch (e: Exception) {
                statusMessage.value = e.localizedMessage ?: "Failed to sync servers"
            } finally {
                isSyncing.value = false
            }
        }
    }

    fun measureAllPings() {
        viewModelScope.launch {
            isPingingAll.value = true
            try {
                val list = serverDao.observeServers().first()
                coroutineScope {
                    list.map { server ->
                        async {
                            val ping = diagnostics.measureTcpPing(server.endpoint, 2200)
                            serverDao.updatePing(server.id, ping)
                        }
                    }.awaitAll()
                }
            } finally {
                isPingingAll.value = false
            }
        }
    }

    fun toggleFavorite(server: ServerProfileEntity) {
        viewModelScope.launch {
            serverDao.setFavorite(server.id, !server.isFavorite)
        }
    }

    fun addManualServer(
        name: String,
        countryName: String,
        endpoint: String,
        serverPublicKey: String,
        interfaceAddress: String,
        dnsServers: String,
        allowedIps: String
    ) {
        viewModelScope.launch {
            val v4 = interfaceAddress.split(',').firstOrNull { !it.contains(":") }?.trim()
                ?: "10.8.0.2/32"
            val v6 = interfaceAddress.split(',').firstOrNull { it.contains(":") }?.trim()
                ?: "fd80:4e58:5200::2/128"
            val entity = ServerProfileEntity(
                id = "manual_${UUID.randomUUID()}",
                nodeCode = "CUSTOM",
                name = name.trim().ifBlank { "Custom WireGuard" },
                countryName = countryName.trim().ifBlank { "Custom" },
                countryCode = "WG",
                city = endpoint.substringBefore(':'),
                endpoint = endpoint.trim(),
                serverPublicKey = serverPublicKey.trim(),
                dnsServers = dnsServers.trim().ifBlank { "1.1.1.1, 1.0.0.1, 2606:4700:4700::1111, 2606:4700:4700::1001" },
                tier = "free",
                loadPercent = 0,
                sourceType = "MANUAL",
                healthStatus = "local",
                assignedAddress = v4,
                assignedIpv6 = v6,
                allowedIps = allowedIps.trim().ifBlank { "0.0.0.0/0, ::/0" }
            )
            serverDao.upsert(entity)
            val ping = diagnostics.measureTcpPing(entity.endpoint, 2200)
            serverDao.updatePing(entity.id, ping)
        }
    }

    fun deleteServer(serverId: String) {
        viewModelScope.launch {
            serverDao.deleteById(serverId)
        }
    }
}
