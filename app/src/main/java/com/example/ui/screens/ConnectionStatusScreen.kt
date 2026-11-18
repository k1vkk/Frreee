package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import kotlin.math.roundToInt

@Composable
fun ConnectionStatusScreen(
    vpnState: VpnSessionState,
    preferences: UserPreferences,
    onToggleConnect: () -> Unit,
    onRefreshIp: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = vpnState.status == VpnConnectionStatus.CONNECTED
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("status_back_button")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = NeonCyan
                    )
                }
                Spacer(Modifier.width(4.dp))
                Column {
                    Text(
                        text = stringResource(R.string.conn_status_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.conn_status_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            NexoraGlowCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = statusColor.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(statusText, style = MaterialTheme.typography.titleMedium, color = statusColor, fontWeight = FontWeight.Bold)
                            Text(
                                text = vpnState.activeServer?.let { "${it.countryName} — ${it.name}" }
                                    ?: stringResource(R.string.conn_no_tunnel),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                    Text(
                        text = formatDuration(vpnState.durationSeconds),
                        style = TelemetryMonoStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                        color = NeonCyan
                    )
                }

                if (vpnState.activeServer != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Endpoint: ${vpnState.activeServer.endpoint.ifBlank { "pending_vps" }}",
                        style = TelemetryMonoStyle.copy(fontSize = 12.sp),
                        color = TextSecondary
                    )
                    if (vpnState.activeServer.sourceType == "BACKEND") {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${stringResource(R.string.conn_server_load)}: ${stringResource(
                                R.string.label_server_telemetry,
                                vpnState.activeServer.cpuUsage.roundToInt(),
                                vpnState.activeServer.ramUsage.roundToInt(),
                                vpnState.activeServer.activeUsers
                            )}",
                            style = TelemetryMonoStyle.copy(fontSize = 11.sp),
                            color = NeonCyan
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onToggleConnect,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isConnected) StatusCrimson else ElectricBlue
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).testTag("status_toggle_vpn_button")
                    ) {
                        Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isConnected) stringResource(R.string.action_disconnect_now)
                            else stringResource(R.string.action_connect_node),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    OutlinedButton(
                        onClick = onRefreshIp,
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_refresh_ip), color = NeonCyan)
                    }
                }
            }

            // Live Throughput + Transfer Totals
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TelemetryMetricTile(
                    icon = Icons.Filled.ArrowDownward,
                    label = stringResource(R.string.label_download),
                    value = formatSpeedCompact(vpnState.downloadSpeedBps),
                    accentColor = NeonCyan,
                    modifier = Modifier.weight(1f)
                )
                TelemetryMetricTile(
                    icon = Icons.Filled.ArrowUpward,
                    label = stringResource(R.string.label_upload),
                    value = formatSpeedCompact(vpnState.uploadSpeedBps),
                    accentColor = ElectricBlue,
                    modifier = Modifier.weight(1f)
                )
            }

            // Interface IPv4 / IPv6 & Leak Inspection Card
            NexoraGlowCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = if (isConnected) StatusEmerald else NeonCyan)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.label_dns_protection), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(10.dp))

                KeyValueRow(
                    label = stringResource(R.string.label_ip_before),
                    value = vpnState.originalIp ?: stringResource(R.string.label_ip_unavailable)
                )
                KeyValueRow(
                    label = stringResource(R.string.label_ip_after),
                    value = if (isConnected) (vpnState.tunnelIp ?: stringResource(R.string.label_ip_checking))
                    else stringResource(R.string.label_ip_not_connected),
                    valueColor = if (isConnected && vpnState.tunnelIp != null) StatusEmerald else TextSecondary
                )
                KeyValueRow(
                    label = stringResource(R.string.conn_ipv4_assigned),
                    value = vpnState.tunnelIpv4Assigned.ifBlank { "—" }
                )
                KeyValueRow(
                    label = stringResource(R.string.conn_ipv6_assigned),
                    value = vpnState.tunnelIpv6Assigned.ifBlank { "—" }
                )
                KeyValueRow(
                    label = stringResource(R.string.conn_active_dns),
                    value = if (vpnState.activeDnsServers.isNotEmpty()) vpnState.activeDnsServers.joinToString(", ")
                    else preferences.customDns
                )
                KeyValueRow(
                    label = stringResource(R.string.conn_rx_tx_totals),
                    value = "↓ ${formatBytes(vpnState.sessionRxBytes)}  /  ↑ ${formatBytes(vpnState.sessionTxBytes)}"
                )

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShieldBadge(
                        label = stringResource(R.string.conn_ipv4_tunnel),
                        active = vpnState.isIpv4Active
                    )
                    ShieldBadge(
                        label = stringResource(R.string.conn_ipv6_tunnel),
                        active = vpnState.isIpv6Active
                    )
                    ShieldBadge(
                        label = "DNS Shield",
                        active = vpnState.isDnsLeakProtected
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun KeyValueRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onBackground
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = TelemetryMonoStyle.copy(fontSize = 12.sp),
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ShieldBadge(label: String, active: Boolean) {
    val color = if (active) StatusEmerald else TextSecondary
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (active) StatusEmerald.copy(alpha = 0.14f) else ObsidianSurface,
        border = BorderStroke(1.dp, if (active) StatusEmerald.copy(alpha = 0.5f) else ObsidianBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (active) Icons.Filled.Lock else Icons.Filled.Shield,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}
