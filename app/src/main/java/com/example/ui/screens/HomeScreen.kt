package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.UserPreferences
import com.example.ui.components.GlowingConnectButton
import com.example.ui.components.NexoraBrandLogo
import com.example.ui.components.NexoraGlowCard
import com.example.ui.components.TelemetryMetricTile
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusCrimson
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.TelemetryMonoStyle
import com.example.ui.theme.TextSecondary
import com.example.vpn.VpnConnectionStatus
import com.example.vpn.VpnSessionState
import com.example.vpn.formatBytes
import com.example.vpn.formatDuration
import com.example.vpn.formatSpeedCompact

@Composable
fun HomeScreen(
    vpnState: VpnSessionState,
    preferences: UserPreferences,
    hasServers: Boolean,
    isArabic: Boolean,
    onToggleConnect: () -> Unit,
    onConnectBestServer: () -> Unit,
    onRefreshIp: () -> Unit,
    onToggleLanguage: () -> Unit,
    onNavigateServers: () -> Unit,
    onNavigateSettings: () -> Unit,
    onImportConf: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when (vpnState.status) {
        VpnConnectionStatus.CONNECTED -> StatusEmerald
        VpnConnectionStatus.CONNECTING,
        VpnConnectionStatus.RECONNECTING,
        VpnConnectionStatus.DISCONNECTING -> StatusAmber
        VpnConnectionStatus.ERROR -> StatusCrimson
        VpnConnectionStatus.DISCONNECTED -> TextSecondary
    }
    val statusText = when (vpnState.status) {
        VpnConnectionStatus.DISCONNECTED -> stringResource(R.string.status_disconnected)
        VpnConnectionStatus.CONNECTING -> stringResource(R.string.status_connecting)
        VpnConnectionStatus.RECONNECTING -> stringResource(R.string.status_reconnecting)
        VpnConnectionStatus.CONNECTED -> stringResource(R.string.status_connected)
        VpnConnectionStatus.DISCONNECTING -> stringResource(R.string.status_disconnecting)
        VpnConnectionStatus.ERROR -> stringResource(R.string.status_error)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A1226),
                        ObsidianBlack,
                        ObsidianBlack
                    )
                )
            )
    ) {
        val isWide = maxWidth >= 700.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 960.dp).fillMaxWidth()) {
                // Top Brand Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NexoraBrandLogo()

                    Surface(
                        onClick = onToggleLanguage,
                        shape = RoundedCornerShape(50),
                        color = ObsidianCard,
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier.testTag("quick_language_toggle")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Language,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isArabic) "EN" else "AR",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Setup prompt when neither Backend API nor local WireGuard profiles exist
                AnimatedVisibility(visible = !hasServers) {
                    NexoraGlowCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                        borderColor = ElectricBlue.copy(alpha = 0.6f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.setup_required_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.setup_required_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onNavigateSettings,
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("home_configure_api_btn")
                            ) {
                                Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.action_configure_api))
                            }
                            OutlinedButton(
                                onClick = onImportConf,
                                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.7f)),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("home_import_conf_btn")
                            ) {
                                Icon(Icons.Filled.FileOpen, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.action_import_conf), color = NeonCyan)
                            }
                        }
                    }
                }

                // Error banner if tunnel failed
                AnimatedVisibility(visible = vpnState.errorMessage != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                            .clickable(onClick = onDismissError),
                        shape = RoundedCornerShape(14.dp),
                        color = StatusCrimson.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, StatusCrimson.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = vpnState.errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = StatusCrimson,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                if (isWide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(0.9f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            StatusPill(statusText = statusText, statusColor = statusColor)
                            Spacer(modifier = Modifier.height(10.dp))
                            GlowingConnectButton(
                                status = vpnState.status,
                                onClick = onToggleConnect,
                                buttonSize = 204.dp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            ActiveServerCard(
                                vpnState = vpnState,
                                onNavigateServers = onNavigateServers
                            )
                            if (hasServers && vpnState.status != VpnConnectionStatus.CONNECTED) {
                                Spacer(modifier = Modifier.height(8.dp))
                                BestPingButton(
                                    isSearching = vpnState.isFindingBestServer,
                                    onClick = onConnectBestServer
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1.1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IpComparisonRow(vpnState = vpnState, onRefreshIp = onRefreshIp)
                            SecurityProtectionBanner(vpnState = vpnState, preferences = preferences)
                            TelemetryGrid(
                                vpnState = vpnState,
                                totalBytes = preferences.totalRxBytes + preferences.totalTxBytes,
                                columns = 2
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        StatusPill(statusText = statusText, statusColor = statusColor)
                        Spacer(modifier = Modifier.height(8.dp))
                        GlowingConnectButton(
                            status = vpnState.status,
                            onClick = onToggleConnect,
                            buttonSize = 192.dp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        ActiveServerCard(
                            vpnState = vpnState,
                            onNavigateServers = onNavigateServers
                        )
                        if (hasServers && vpnState.status != VpnConnectionStatus.CONNECTED) {
                            Spacer(modifier = Modifier.height(8.dp))
                            BestPingButton(
                                isSearching = vpnState.isFindingBestServer,
                                onClick = onConnectBestServer
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        IpComparisonRow(vpnState = vpnState, onRefreshIp = onRefreshIp)
                        Spacer(modifier = Modifier.height(10.dp))
                        SecurityProtectionBanner(vpnState = vpnState, preferences = preferences)
                        Spacer(modifier = Modifier.height(10.dp))
                        TelemetryGrid(
                            vpnState = vpnState,
                            totalBytes = preferences.totalRxBytes + preferences.totalTxBytes,
                            columns = 2
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun BestPingButton(
    isSearching: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = !isSearching,
        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("best_server_button")
    ) {
        if (isSearching) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = NeonCyan)
        } else {
            Icon(Icons.Filled.Bolt, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.action_best_server), color = NeonCyan, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SecurityProtectionBanner(
    vpnState: VpnSessionState,
    preferences: UserPreferences
) {
    val isSecured = vpnState.status == VpnConnectionStatus.CONNECTED && vpnState.isDnsLeakProtected
    val dnsSummary = if (vpnState.activeDnsServers.isNotEmpty()) {
        vpnState.activeDnsServers.joinToString(", ")
    } else {
        preferences.customDns
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ObsidianCard,
        border = BorderStroke(
            1.dp,
            if (isSecured) StatusEmerald.copy(alpha = 0.5f) else ObsidianBorder
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = if (isSecured) StatusEmerald else NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.label_dns_protection),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = ElectricBlue.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = if (vpnState.isIpv4Active && vpnState.isIpv6Active) {
                            "IPv4 + IPv6"
                        } else {
                            stringResource(R.string.label_dual_stack)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyan,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Text(
                text = stringResource(R.string.label_dns_secured, dnsSummary),
                style = TelemetryMonoStyle.copy(fontSize = 11.sp),
                color = if (isSecured) StatusEmerald else TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (preferences.killSwitchEnabled || preferences.autoReconnectEnabled) {
                Text(
                    text = stringResource(R.string.label_kill_switch_on),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun StatusPill(statusText: String, statusColor: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = statusColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelLarge,
                color = statusColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ActiveServerCard(
    vpnState: VpnSessionState,
    onNavigateServers: () -> Unit
) {
    val server = vpnState.activeServer
    Surface(
        onClick = onNavigateServers,
        shape = RoundedCornerShape(18.dp),
        color = ObsidianCard,
        border = BorderStroke(
            1.dp,
            if (vpnState.status == VpnConnectionStatus.CONNECTED) NeonCyan.copy(alpha = 0.5f) else ObsidianBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_server_selector_card")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ElectricBlue.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                if (server != null) {
                    Text(
                        text = server.nodeCode.ifBlank { server.countryCode }.uppercase().take(4),
                        style = TelemetryMonoStyle.copy(fontSize = 13.sp),
                        color = NeonCyan
                    )
                } else {
                    Icon(Icons.Filled.Dns, contentDescription = null, tint = NeonCyan)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.label_selected_server),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Text(
                    text = server?.let { "${it.countryName} — ${it.name}" }
                        ?: stringResource(R.string.label_no_server_selected),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (server != null) {
                    Text(
                        text = server.endpoint,
                        style = TelemetryMonoStyle.copy(fontSize = 12.sp),
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = ElectricBlue.copy(alpha = 0.18f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.label_change_server),
                        style = MaterialTheme.typography.labelMedium,
                        color = NeonCyan
                    )
                }
            }
        }
    }
}

@Composable
private fun IpComparisonRow(
    vpnState: VpnSessionState,
    onRefreshIp: () -> Unit
) {
    val beforeIpText = when {
        vpnState.isCheckingOriginalIp -> stringResource(R.string.label_ip_checking)
        !vpnState.originalIp.isNullOrBlank() -> vpnState.originalIp
        else -> stringResource(R.string.label_ip_unavailable)
    }
    val afterIpText = when {
        vpnState.status != VpnConnectionStatus.CONNECTED -> stringResource(R.string.label_ip_not_connected)
        vpnState.isCheckingTunnelIp -> stringResource(R.string.label_ip_checking)
        !vpnState.tunnelIp.isNullOrBlank() -> vpnState.tunnelIp
        else -> stringResource(R.string.label_ip_unavailable)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            color = ObsidianSurface,
            border = BorderStroke(1.dp, ObsidianBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Public,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.label_ip_before),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    IconButton(
                        onClick = onRefreshIp,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("refresh_ip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.action_refresh_ip),
                            tint = NeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = beforeIpText,
                    style = TelemetryMonoStyle.copy(fontSize = 14.sp),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            color = ObsidianSurface,
            border = BorderStroke(
                1.dp,
                if (vpnState.status == VpnConnectionStatus.CONNECTED) StatusEmerald.copy(alpha = 0.55f) else ObsidianBorder
            )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.VerifiedUser,
                        contentDescription = null,
                        tint = if (vpnState.status == VpnConnectionStatus.CONNECTED) StatusEmerald else TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.label_ip_after),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = afterIpText,
                    style = TelemetryMonoStyle.copy(fontSize = 14.sp),
                    color = if (vpnState.status == VpnConnectionStatus.CONNECTED) StatusEmerald else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TelemetryGrid(
    vpnState: VpnSessionState,
    totalBytes: Long,
    columns: Int
) {
    val pingDisplay = when {
        vpnState.pingMs != null -> stringResource(R.string.label_latency_ms, vpnState.pingMs)
        vpnState.activeServer?.lastPingMs != null -> stringResource(R.string.label_latency_ms, vpnState.activeServer.lastPingMs)
        else -> "—"
    }

    val items = listOf(
        Triple(Icons.Filled.ArrowDownward, stringResource(R.string.label_download), formatSpeedCompact(vpnState.downloadSpeedBps) to NeonCyan),
        Triple(Icons.Filled.ArrowUpward, stringResource(R.string.label_upload), formatSpeedCompact(vpnState.uploadSpeedBps) to ElectricBlue),
        Triple(Icons.Filled.NetworkPing, stringResource(R.string.label_ping), pingDisplay to StatusEmerald),
        Triple(Icons.Filled.Timer, stringResource(R.string.label_duration), formatDuration(vpnState.durationSeconds) to StatusAmber),
        Triple(Icons.Filled.DataUsage, stringResource(R.string.label_data_usage), formatBytes(vpnState.sessionRxBytes + vpnState.sessionTxBytes) to NeonCyan),
        Triple(Icons.Filled.Shield, stringResource(R.string.label_total_data), formatBytes(totalBytes) to ElectricBlue)
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(columns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEach { (icon, label, valueAndColor) ->
                    TelemetryMetricTile(
                        icon = icon,
                        label = label,
                        value = valueAndColor.first,
                        accentColor = valueAndColor.second,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
