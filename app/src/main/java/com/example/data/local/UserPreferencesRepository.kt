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
import com.example.NexoraConfig
import com.wireguard.crypto.KeyPair
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nexora_prefs")

data class UserPreferences(
    val apiBaseUrl: String = NexoraConfig.DEFAULT_API_BASE_URL,
    val authToken: String = "",
    val userEmail: String = "",
    val userDisplayName: String = "",
    val planTier: String = "free",
    val subscriptionStatus: String = "active",
    val subscriptionExpiresAt: String? = null,
    val wgPrivateKey: String = "",
    val wgPublicKey: String = "",
    val selectedServerId: String = "",
    val languageMode: String = "system",
    val customDns: String = "1.1.1.1, 1.0.0.1, 2606:4700:4700::1111, 2606:4700:4700::1001",
    val tunnelMtu: Int = 1420,
    val autoConnect: Boolean = false,
    val killSwitchEnabled: Boolean = true,
    val autoReconnectEnabled: Boolean = true,
    val autoFailoverEnabled: Boolean = true,
    val dnsLeakProtectionEnabled: Boolean = true,
    val ipv6LeakProtectionEnabled: Boolean = true,
    val excludedPackages: Set<String> = emptySet(),
    val totalRxBytes: Long = 0L,
    val totalTxBytes: Long = 0L,
    val speedTestEcoMode: Boolean = true,
    val speedTestIncludeUpload: Boolean = true
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val API_BASE_URL = stringPreferencesKey("api_base_url")
        val AUTH_TOKEN = stringPreferencesKey("auth_token")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val USER_DISPLAY_NAME = stringPreferencesKey("user_display_name")
        val PLAN_TIER = stringPreferencesKey("plan_tier")
        val SUBSCRIPTION_STATUS = stringPreferencesKey("subscription_status")
        val SUBSCRIPTION_EXPIRES_AT = stringPreferencesKey("subscription_expires_at")
        val WG_PRIVATE_KEY = stringPreferencesKey("wg_private_key")
        val WG_PUBLIC_KEY = stringPreferencesKey("wg_public_key")
        val SELECTED_SERVER_ID = stringPreferencesKey("selected_server_id")
        val LANGUAGE_MODE = stringPreferencesKey("language_mode")
        val CUSTOM_DNS = stringPreferencesKey("custom_dns")
        val TUNNEL_MTU = intPreferencesKey("tunnel_mtu")
        val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        val KILL_SWITCH = booleanPreferencesKey("kill_switch_enabled")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect_enabled")
        val AUTO_FAILOVER = booleanPreferencesKey("auto_failover_enabled")
        val DNS_LEAK_PROTECTION = booleanPreferencesKey("dns_leak_protection_enabled")
        val IPV6_LEAK_PROTECTION = booleanPreferencesKey("ipv6_leak_protection_enabled")
        val EXCLUDED_PACKAGES = stringSetPreferencesKey("excluded_packages")
        val TOTAL_RX_BYTES = longPreferencesKey("total_rx_bytes")
        val TOTAL_TX_BYTES = longPreferencesKey("total_tx_bytes")
        val SPEED_TEST_ECO_MODE = booleanPreferencesKey("speed_test_eco_mode")
        val SPEED_TEST_INCLUDE_UPLOAD = booleanPreferencesKey("speed_test_include_upload")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val storedUrl = NexoraConfig.normalizeProductionHttpsUrl(prefs[Keys.API_BASE_URL])
        UserPreferences(
            apiBaseUrl = storedUrl ?: NexoraConfig.DEFAULT_API_BASE_URL,
            authToken = CryptoPrefsHelper.decrypt(prefs[Keys.AUTH_TOKEN] ?: ""),
            userEmail = prefs[Keys.USER_EMAIL] ?: "",
            userDisplayName = prefs[Keys.USER_DISPLAY_NAME] ?: "",
            planTier = prefs[Keys.PLAN_TIER] ?: "free",
            subscriptionStatus = prefs[Keys.SUBSCRIPTION_STATUS] ?: "active",
            subscriptionExpiresAt = prefs[Keys.SUBSCRIPTION_EXPIRES_AT],
            wgPrivateKey = CryptoPrefsHelper.decrypt(prefs[Keys.WG_PRIVATE_KEY] ?: ""),
            wgPublicKey = prefs[Keys.WG_PUBLIC_KEY] ?: "",
            selectedServerId = prefs[Keys.SELECTED_SERVER_ID] ?: "",
            languageMode = prefs[Keys.LANGUAGE_MODE] ?: "system",
            customDns = prefs[Keys.CUSTOM_DNS]
                ?: "1.1.1.1, 1.0.0.1, 2606:4700:4700::1111, 2606:4700:4700::1001",
            tunnelMtu = prefs[Keys.TUNNEL_MTU] ?: 1420,
            autoConnect = prefs[Keys.AUTO_CONNECT] ?: false,
            killSwitchEnabled = prefs[Keys.KILL_SWITCH] ?: true,
            autoReconnectEnabled = prefs[Keys.AUTO_RECONNECT] ?: true,
            autoFailoverEnabled = prefs[Keys.AUTO_FAILOVER] ?: true,
            dnsLeakProtectionEnabled = prefs[Keys.DNS_LEAK_PROTECTION] ?: true,
            ipv6LeakProtectionEnabled = prefs[Keys.IPV6_LEAK_PROTECTION] ?: true,
            excludedPackages = prefs[Keys.EXCLUDED_PACKAGES] ?: emptySet(),
            totalRxBytes = prefs[Keys.TOTAL_RX_BYTES] ?: 0L,
            totalTxBytes = prefs[Keys.TOTAL_TX_BYTES] ?: 0L,
            speedTestEcoMode = prefs[Keys.SPEED_TEST_ECO_MODE] ?: true,
            speedTestIncludeUpload = prefs[Keys.SPEED_TEST_INCLUDE_UPLOAD] ?: true
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
            prefs[Keys.WG_PRIVATE_KEY] = CryptoPrefsHelper.encrypt(priv)
            prefs[Keys.WG_PUBLIC_KEY] = pub
        }
        return priv to pub
    }

    suspend fun setApiBaseUrl(url: String) {
        val normalized = NexoraConfig.normalizeProductionHttpsUrl(url) ?: ""
        context.dataStore.edit { prefs ->
            if (normalized.isBlank()) {
                prefs.remove(Keys.API_BASE_URL)
            } else {
                prefs[Keys.API_BASE_URL] = normalized
            }
        }
    }

    suspend fun setAuthSession(
        token: String,
        email: String,
        displayName: String,
        planTier: String,
        subscriptionStatus: String = "active",
        expiresAt: String? = null
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTH_TOKEN] = CryptoPrefsHelper.encrypt(token)
            prefs[Keys.USER_EMAIL] = email
            prefs[Keys.USER_DISPLAY_NAME] = displayName
            prefs[Keys.PLAN_TIER] = planTier
            prefs[Keys.SUBSCRIPTION_STATUS] = subscriptionStatus
            if (expiresAt != null) {
                prefs[Keys.SUBSCRIPTION_EXPIRES_AT] = expiresAt
            } else {
                prefs.remove(Keys.SUBSCRIPTION_EXPIRES_AT)
            }
        }
    }

    suspend fun updateSubscription(planTier: String, status: String = "active", expiresAt: String? = null) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PLAN_TIER] = planTier
            prefs[Keys.SUBSCRIPTION_STATUS] = status
            if (expiresAt != null) {
                prefs[Keys.SUBSCRIPTION_EXPIRES_AT] = expiresAt
            }
        }
    }

    suspend fun clearAuthSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.AUTH_TOKEN)
            prefs.remove(Keys.USER_EMAIL)
            prefs.remove(Keys.USER_DISPLAY_NAME)
            prefs.remove(Keys.SUBSCRIPTION_EXPIRES_AT)
            prefs[Keys.PLAN_TIER] = "free"
            prefs[Keys.SUBSCRIPTION_STATUS] = "active"
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
        autoFailover: Boolean,
        dnsLeakProtection: Boolean,
        ipv6LeakProtection: Boolean
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CUSTOM_DNS] = customDns.trim()
                .ifBlank { "1.1.1.1, 1.0.0.1, 2606:4700:4700::1111, 2606:4700:4700::1001" }
            prefs[Keys.TUNNEL_MTU] = mtu.coerceIn(1280, 1500)
            prefs[Keys.AUTO_CONNECT] = autoConnect
            prefs[Keys.KILL_SWITCH] = killSwitch
            prefs[Keys.AUTO_RECONNECT] = autoReconnect
            prefs[Keys.AUTO_FAILOVER] = autoFailover
            prefs[Keys.DNS_LEAK_PROTECTION] = dnsLeakProtection
            prefs[Keys.IPV6_LEAK_PROTECTION] = ipv6LeakProtection
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

    suspend fun setSpeedTestEcoMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SPEED_TEST_ECO_MODE] = enabled
        }
    }

    suspend fun setSpeedTestIncludeUpload(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SPEED_TEST_INCLUDE_UPLOAD] = enabled
        }
    }
}
