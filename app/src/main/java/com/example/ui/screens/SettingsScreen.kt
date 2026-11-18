package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.UserPreferences
import com.example.ui.components.NexoraGlowCard
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusCrimson
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.ApiHealthState

data class InstalledAppItem(
    val packageName: String,
    val label: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    apiHealth: ApiHealthState,
    onSaveAndTestApi: (String) -> Unit,
    onRefreshBackendHealth: () -> Unit,
    onLanguageSelected: (String) -> Unit,
    onSaveWireGuardSettings: (String, Int, Boolean, Boolean, Boolean, Boolean, Boolean, Boolean) -> Unit,
    onToggleExcludedPackage: (String) -> Unit,
    onNavigateHistory: () -> Unit,
    onNavigateSupport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var apiUrlInput by remember(preferences.apiBaseUrl) { mutableStateOf(preferences.apiBaseUrl) }
    var dnsInput by remember(preferences.customDns) { mutableStateOf(preferences.customDns) }
    var mtuInput by remember(preferences.tunnelMtu) { mutableStateOf(preferences.tunnelMtu.toString()) }
    var autoConnect by remember(preferences.autoConnect) { mutableStateOf(preferences.autoConnect) }
    var killSwitch by remember(preferences.killSwitchEnabled) { mutableStateOf(preferences.killSwitchEnabled) }
    var autoReconnect by remember(preferences.autoReconnectEnabled) { mutableStateOf(preferences.autoReconnectEnabled) }
    var autoFailover by remember(preferences.autoFailoverEnabled) { mutableStateOf(preferences.autoFailoverEnabled) }
    var dnsLeakProtection by remember(preferences.dnsLeakProtectionEnabled) { mutableStateOf(preferences.dnsLeakProtectionEnabled) }
    var ipv6LeakProtection by remember(preferences.ipv6LeakProtectionEnabled) { mutableStateOf(preferences.ipv6LeakProtectionEnabled) }
    var showSplitTunnelDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0A1226), ObsidianBlack)))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            // Configurable Node.js + PostgreSQL HTTPS API Endpoint
            NexoraGlowCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Cloud, contentDescription = null, tint = NeonCyan)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.section_backend), style = MaterialTheme.typography.titleMedium)
                    }
                    IconButton(
                        onClick = onRefreshBackendHealth,
                        modifier = Modifier.testTag("refresh_backend_health_button")
                    ) {
                        if (apiHealth is ApiHealthState.Checking) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = NeonCyan)
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.action_sync_servers),
                                tint = NeonCyan
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiUrlInput,
                    onValueChange = { apiUrlInput = it },
                    label = { Text(stringResource(R.string.field_api_url)) },
                    placeholder = { Text(stringResource(R.string.hint_api_url), color = TextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("api_url_input")
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onSaveAndTestApi(apiUrlInput) },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                    modifier = Modifier.fillMaxWidth().testTag("save_test_api_button")
                ) {
                    Text(stringResource(R.string.action_save_test_api), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                when (apiHealth) {
                    ApiHealthState.NotConfigured -> Text(
                        text = stringResource(R.string.api_status_not_set),
                        style = MaterialTheme.typography.bodyMedium,
                        color = StatusAmber
                    )
                    ApiHealthState.Checking -> Text(
                        text = stringResource(R.string.label_ip_checking),
                        style = MaterialTheme.typography.bodyMedium,
                        color = StatusAmber
                    )
                    is ApiHealthState.Healthy -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = StatusEmerald, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.api_status_ok, apiHealth.info),
                            style = MaterialTheme.typography.bodyMedium,
                            color = StatusEmerald
                        )
                    }
                    is ApiHealthState.Unreachable -> Text(
                        text = stringResource(R.string.api_status_error, apiHealth.reason),
                        style = MaterialTheme.typography.bodyMedium,
                        color = StatusCrimson
                    )
                }
            }

            // Language Switcher
            NexoraGlowCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Language, contentDescription = null, tint = NeonCyan)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.section_language), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val options = listOf(
                        "system" to stringResource(R.string.lang_system),
                        "ar" to stringResource(R.string.lang_arabic),
                        "en" to stringResource(R.string.lang_english)
                    )
                    options.forEach { (mode, label) ->
                        FilterChip(
                            selected = preferences.languageMode == mode,
                            onClick = { onLanguageSelected(mode) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricBlue.copy(alpha = 0.25f),
                                selectedLabelColor = NeonCyan,
                                containerColor = ObsidianSurface,
                                labelColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("lang_chip_$mode")
                        )
                    }
                }
            }

            // WireGuard Protocol Settings
            NexoraGlowCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Tune, contentDescription = null, tint = NeonCyan)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.section_wireguard), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = dnsInput,
                    onValueChange = { dnsInput = it },
                    label = { Text(stringResource(R.string.field_custom_dns)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = mtuInput,
                    onValueChange = { mtuInput = it.filter { ch -> ch.isDigit() } },
                    label = { Text(stringResource(R.string.field_mtu)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                SettingToggleRow(stringResource(R.string.toggle_auto_connect), autoConnect) { autoConnect = it }
                SettingToggleRow(stringResource(R.string.toggle_kill_switch), killSwitch) { killSwitch = it }
                SettingToggleRow(stringResource(R.string.toggle_auto_reconnect), autoReconnect) { autoReconnect = it }
                SettingToggleRow(stringResource(R.string.toggle_auto_failover), autoFailover) { autoFailover = it }
                SettingToggleRow(stringResource(R.string.toggle_dns_leak_protection), dnsLeakProtection) { dnsLeakProtection = it }
                SettingToggleRow(stringResource(R.string.toggle_ipv6_leak_protection), ipv6LeakProtection) { ipv6LeakProtection = it }

                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        val parsedMtu = mtuInput.toIntOrNull() ?: 1420
                        onSaveWireGuardSettings(
                            dnsInput,
                            parsedMtu,
                            autoConnect,
                            killSwitch,
                            autoReconnect,
                            autoFailover,
                            dnsLeakProtection,
                            ipv6LeakProtection
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                    modifier = Modifier.fillMaxWidth().testTag("save_wg_settings_button")
                ) {
                    Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
                }
            }

            // Split Tunneling & Always-On VPN & Battery
            Surface(
                onClick = { showSplitTunnelDialog = true },
                shape = RoundedCornerShape(16.dp),
                color = ObsidianCard,
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("split_tunneling_row")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Apps, contentDescription = null, tint = NeonCyan)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.setting_split_tunneling), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = stringResource(R.string.setting_split_tunneling_desc, preferences.excludedPackages.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }

            Surface(
                onClick = {
                    val intent = Intent(Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                },
                shape = RoundedCornerShape(16.dp),
                color = ObsidianCard,
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("always_on_vpn_row")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Security, contentDescription = null, tint = StatusEmerald)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.setting_always_on_vpn), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = stringResource(R.string.setting_always_on_vpn_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = TextSecondary)
                }
            }

            Surface(
                onClick = {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                },
                shape = RoundedCornerShape(16.dp),
                color = ObsidianCard,
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("battery_optimization_row")
            ) {
                val pm = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
                val ignoring = remember { pm.isIgnoringBatteryOptimizations(context.packageName) }
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.BatteryChargingFull, contentDescription = null, tint = NeonCyan)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.setting_battery_optimization), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (ignoring) stringResource(R.string.setting_battery_unrestricted)
                            else stringResource(R.string.setting_battery_optimized),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = TextSecondary)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Surface(
                    onClick = onNavigateHistory,
                    shape = RoundedCornerShape(14.dp),
                    color = ObsidianCard,
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.History, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.nav_history), style = MaterialTheme.typography.labelLarge)
                    }
                }
                Surface(
                    onClick = onNavigateSupport,
                    shape = RoundedCornerShape(14.dp),
                    color = ObsidianCard,
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.SupportAgent, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.nav_support), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showSplitTunnelDialog) {
        SplitTunnelingDialog(
            excludedPackages = preferences.excludedPackages,
            onTogglePackage = onToggleExcludedPackage,
            onDismiss = { showSplitTunnelDialog = false }
        )
    }
}

@Composable
private fun SettingToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SplitTunnelingDialog(
    excludedPackages: Set<String>,
    onTogglePackage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val launcherApps = remember {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { resolveInfo ->
                InstalledAppItem(
                    packageName = resolveInfo.activityInfo.packageName,
                    label = resolveInfo.loadLabel(pm).toString()
                )
            }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .sortedBy { it.label.lowercase() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = { Text(stringResource(R.string.split_tunnel_dialog_title)) },
        text = {
            LazyColumn(modifier = Modifier.height(340.dp)) {
                items(launcherApps, key = { it.packageName }) { app ->
                    val checked = excludedPackages.contains(app.packageName)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTogglePackage(app.packageName) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { onTogglePackage(app.packageName) })
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(app.label, style = MaterialTheme.typography.bodyLarge)
                            Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_done), color = NeonCyan)
            }
        }
    )
}
