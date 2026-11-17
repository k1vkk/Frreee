package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.BuildConfig
import com.wireguard.crypto.KeyPair
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nexora_prefs")

private fun resolveConfiguredDefaultApiUrl(): String {
    val candidate = BuildConfig.NEXORA_API_BASE_URL.trim().trimEnd('/')
    return if (candidate.startsWith("https://") &&
        !candidate.contains("localhost") &&
        !candidate.contains("yourdomain.com")
    ) candidate else ""
}

data class UserPreferences(
    val apiBaseUrl: String = resolveConfiguredDefaultApiUrl(),
    val authToken: String = "",
    val userEmail: String = "",
    val userDisplayName: String = "",
    val planTier: String = "free",
    val wgPrivateKey: String = "",
    val wgPublicKey: String = "",
    val selectedServerId: String = "",
    val languageMode: String = "system",
    val customDns: String = "1.1.1.1, 1.0.0.1, 2606:4700:4700::1111, 2606:4700:4700::1001",
    val tunnelMtu: Int = 1420,
    val autoConnect: Boolean = false,
    val killSwitchEnabled: Boolean = true,
    val autoReconnectEnabled: Boolean = true,
    val dnsLeakProtectionEnabled: Boolean = true,
    val excludedPackages: Set<String> = emptySet(),
    val totalRxBytes: Long = 0L,
    val totalTxBytes: Long = 0L
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val API_BASE_URL = stringPreferencesKey("api_base_url")
        val AUTH_TOKEN = stringPreferencesKey("auth_token")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val USER_DISPLAY_NAME = stringPreferencesKey("user_display_name")
        val PLAN_TIER = stringPreferencesKey("plan_tier")
        val WG_PRIVATE_KEY = stringPreferencesKey("wg_private_key")
        val WG_PUBLIC_KEY = stringPreferencesKey("wg_public_key")
        val SELECTED_SERVER_ID = stringPreferencesKey("selected_server_id")
        val LANGUAGE_MODE = stringPreferencesKey("language_mode")
        val CUSTOM_DNS = stringPreferencesKey("custom_dns")
        val TUNNEL_MTU = intPreferencesKey("tunnel_mtu")
        val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        val KILL_SWITCH = booleanPreferencesKey("kill_switch_enabled")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect_enabled")
        val DNS_LEAK_PROTECTION = booleanPreferencesKey("dns_leak_protection_enabled")
        val EXCLUDED_PACKAGES = stringSetPreferencesKey("excluded_packages")
        val TOTAL_RX_BYTES = longPreferencesKey("total_rx_bytes")
        val TOTAL_TX_BYTES = longPreferencesKey("total_tx_bytes")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            apiBaseUrl = (prefs[Keys.API_BASE_URL] ?: "").ifBlank { resolveConfiguredDefaultApiUrl() },
            authToken = prefs[Keys.AUTH_TOKEN] ?: "",
            userEmail = prefs[Keys.USER_EMAIL] ?: "",
            userDisplayName = prefs[Keys.USER_DISPLAY_NAME] ?: "",
            planTier = prefs[Keys.PLAN_TIER] ?: "free",
            wgPrivateKey = prefs[Keys.WG_PRIVATE_KEY] ?: "",
            wgPublicKey = prefs[Keys.WG_PUBLIC_KEY] ?: "",
            selectedServerId = prefs[Keys.SELECTED_SERVER_ID] ?: "",
            languageMode = prefs[Keys.LANGUAGE_MODE] ?: "system",
            customDns = prefs[Keys.CUSTOM_DNS]
                ?: "1.1.1.1, 1.0.0.1, 2606:4700:4700::1111, 2606:4700:4700::1001",
            tunnelMtu = prefs[Keys.TUNNEL_MTU] ?: 1420,
            autoConnect = prefs[Keys.AUTO_CONNECT] ?: false,
            killSwitchEnabled = prefs[Keys.KILL_SWITCH] ?: true,
            autoReconnectEnabled = prefs[Keys.AUTO_RECONNECT] ?: true,
            dnsLeakProtectionEnabled = prefs[Keys.DNS_LEAK_PROTECTION] ?: true,
            excludedPackages = prefs[Keys.EXCLUDED_PACKAGES] ?: emptySet(),
            totalRxBytes = prefs[Keys.TOTAL_RX_BYTES] ?: 0L,
            totalTxBytes = prefs[Keys.TOTAL_TX_BYTES] ?: 0L
        )
    }

    suspend fun ensureWireGuardKeyPair(): Pair<String, String> {
        val current = preferencesFlow.first()
        if (current.wgPrivateKey.isNotBlank() && current.wgPublicKey.isNotBlank()) {
            return current.wgPrivateKey to current.wgPublicKey
        }
        return rotateWireGuardKeyPair()
    }

    suspend fun rotateWireGuardKeyPair(): Pair<String, String> {
        val keyPair = KeyPair()
        val priv = keyPair.privateKey.toBase64()
        val pub = keyPair.publicKey.toBase64()
        context.dataStore.edit { prefs ->
            prefs[Keys.WG_PRIVATE_KEY] = priv
            prefs[Keys.WG_PUBLIC_KEY] = pub
        }
        return priv to pub
    }

    suspend fun setApiBaseUrl(url: String) {
        val normalized = url.trim().trimEnd('/')
        context.dataStore.edit { prefs ->
            prefs[Keys.API_BASE_URL] = normalized
        }
    }

    suspend fun setAuthSession(token: String, email: String, displayName: String, planTier: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTH_TOKEN] = token
            prefs[Keys.USER_EMAIL] = email
            prefs[Keys.USER_DISPLAY_NAME] = displayName
            prefs[Keys.PLAN_TIER] = planTier
        }
    }

    suspend fun updatePlanTier(planTier: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PLAN_TIER] = planTier
        }
    }

    suspend fun clearAuthSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.AUTH_TOKEN)
            prefs.remove(Keys.USER_EMAIL)
            prefs.remove(Keys.USER_DISPLAY_NAME)
            prefs[Keys.PLAN_TIER] = "free"
        }
    }

    suspend fun setSelectedServerId(serverId: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SELECTED_SERVER_ID] = serverId
        }
    }

    suspend fun setLanguageMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LANGUAGE_MODE] = mode
        }
    }

    suspend fun setWireGuardSettings(
        customDns: String,
        mtu: Int,
        autoConnect: Boolean,
        killSwitch: Boolean,
        autoReconnect: Boolean,
        dnsLeakProtection: Boolean
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CUSTOM_DNS] = customDns.trim()
                .ifBlank { "1.1.1.1, 1.0.0.1, 2606:4700:4700::1111, 2606:4700:4700::1001" }
            prefs[Keys.TUNNEL_MTU] = mtu.coerceIn(1280, 1500)
            prefs[Keys.AUTO_CONNECT] = autoConnect
            prefs[Keys.KILL_SWITCH] = killSwitch
            prefs[Keys.AUTO_RECONNECT] = autoReconnect
            prefs[Keys.DNS_LEAK_PROTECTION] = dnsLeakProtection
        }
    }

    suspend fun setExcludedPackages(packages: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.EXCLUDED_PACKAGES] = packages
        }
    }

    suspend fun addTrafficBytes(rxDelta: Long, txDelta: Long) {
        if (rxDelta <= 0L && txDelta <= 0L) return
        context.dataStore.edit { prefs ->
            prefs[Keys.TOTAL_RX_BYTES] = (prefs[Keys.TOTAL_RX_BYTES] ?: 0L) + rxDelta.coerceAtLeast(0L)
            prefs[Keys.TOTAL_TX_BYTES] = (prefs[Keys.TOTAL_TX_BYTES] ?: 0L) + txDelta.coerceAtLeast(0L)
        }
    }
}
