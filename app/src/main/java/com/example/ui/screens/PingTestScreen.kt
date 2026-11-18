package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.ServerProfileEntity
import com.example.data.remote.ServerDto
import com.example.data.remote.ServersHealthSummaryDto
import com.example.network.ServerProbeResult
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
import com.example.ui.theme.TelemetryMonoStyle
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun PingTestScreen(
    servers: List<ServerProfileEntity>,
    gccSlotsHealth: List<ServerDto>,
    healthSummary: ServersHealthSummaryDto,
    probeResults: Map<String, ServerProbeResult>,
    customProbeResult: ServerProbeResult?,
    isPingingAll: Boolean,
    isProbingCustom: Boolean,
    onMeasureAllPings: () -> Unit,
    onProbeCustomEndpoint: (String) -> Unit,
    onConnectBestServer: () -> Unit,
    onConnectServer: (ServerProfileEntity) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var customHost by remember { mutableStateOf("1.1.1.1:443") }

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
                IconButton(onClick = onBack, modifier = Modifier.testTag("ping_back_button")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = NeonCyan
                    )
                }
                Spacer(Modifier.width(4.dp))
                Column {
                    Text(
                        text = stringResource(R.string.ping_test_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.ping_test_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            // Custom Host Probe Card (works anytime for real RTT/Jitter/Packet Loss diagnostics)
            NexoraGlowCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Public, contentDescription = null, tint = NeonCyan)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_probe_host), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = customHost,
                    onValueChange = { customHost = it },
                    label = { Text(stringResource(R.string.ping_custom_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("custom_ping_input")
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onProbeCustomEndpoint(customHost) },
                        enabled = !isProbingCustom && customHost.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        modifier = Modifier.weight(1f).testTag("run_custom_ping_button")
                    ) {
                        if (isProbingCustom) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(Icons.Filled.NetworkPing, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_probe_host), fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onMeasureAllPings,
                        enabled = !isPingingAll && servers.isNotEmpty(),
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f))
                    ) {
                        Text(stringResource(R.string.action_ping_all), color = NeonCyan)
                    }
                }
                if (customProbeResult != null) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ObsidianSurface,
                        border = BorderStroke(
                            1.dp,
                            if (customProbeResult.reachable) StatusEmerald.copy(alpha = 0.55f) else StatusCrimson.copy(alpha = 0.55f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = customProbeResult.endpoint,
                                style = TelemetryMonoStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                color = NeonCyan
                            )
                            Spacer(Modifier.height(4.dp))
                            val pingStr = customProbeResult.pingMs?.let { stringResource(R.string.label_latency_ms, it) }
                                ?: stringResource(R.string.label_latency_timeout)
                            val jitterStr = stringResource(R.string.label_jitter_ms, customProbeResult.jitterMs ?: 0)
                            val lossStr = stringResource(R.string.label_packet_loss, customProbeResult.packetLossPercent.roundToInt())
                            val samplesStr = stringResource(
                                R.string.label_probe_samples,
                                customProbeResult.samplesSucceeded,
                                customProbeResult.samplesAttempted
                            )
                            Text(
                                text = "$pingStr  •  $jitterStr  •  $lossStr  •  $samplesStr",
                                style = TelemetryMonoStyle.copy(fontSize = 12.sp),
                                color = if (customProbeResult.reachable) StatusEmerald else StatusCrimson
                            )
                        }
                    }
                }
            }

            if (servers.isNotEmpty()) {
                Button(
                    onClick = onConnectBestServer,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = ObsidianBlack),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_best_server), fontWeight = FontWeight.Bold)
                }

                servers.forEach { srv ->
                    val probe = probeResults[srv.id]
                    val pingMs = probe?.pingMs ?: srv.lastPingMs
                    val jitterMs = probe?.jitterMs ?: srv.lastJitterMs
                    val loss = probe?.packetLossPercent ?: srv.packetLossPercent
                    val pingColor = when {
                        pingMs == null -> TextSecondary
                        pingMs < 90 -> StatusEmerald
                        pingMs < 180 -> StatusAmber
                        else -> StatusCrimson
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ObsidianCard,
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("${srv.countryName} — ${srv.name}", style = MaterialTheme.typography.titleSmall)
                                Text(srv.endpoint, style = TelemetryMonoStyle.copy(fontSize = 11.sp), color = TextSecondary)
                                Text(
                                    text = buildString {
                                        append(pingMs?.let { stringResource(R.string.label_latency_ms, it) } ?: stringResource(R.string.label_latency_untested))
                                        if (jitterMs != null) append(" • ${stringResource(R.string.label_jitter_ms, jitterMs)}")
                                        append(" • ${stringResource(R.string.label_packet_loss, loss.roundToInt())}")
                                    },
                                    style = TelemetryMonoStyle.copy(fontSize = 11.sp),
                                    color = pingColor
                                )
                            }
                            Button(
                                onClick = { onConnectServer(srv) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                            ) {
                                Text(stringResource(R.string.action_connect_node))
                            }
                        }
                    }
                }
            }

            // Transparent Backend GCC Topology Slots (Never fake pending_vps slots)
            if (gccSlotsHealth.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.label_gcc_slots_title,
                        healthSummary.online,
                        healthSummary.total
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonCyan
                )
                gccSlotsHealth.forEach { slot ->
                    val isOnline = slot.healthStatus == "online"
                    val badgeColor = if (isOnline) StatusEmerald else StatusAmber
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ObsidianCard,
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${slot.nodeCode} • ${slot.countryName} — ${slot.city}",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = badgeColor.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = if (isOnline) "ONLINE" else stringResource(R.string.label_pending_vps),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = badgeColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            if (!isOnline) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.ping_pending_vps_note),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            } else if (!slot.endpoint.isNullOrBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "${slot.endpoint} • CPU ${slot.cpuUsage.roundToInt()}% • RAM ${slot.ramUsage.roundToInt()}%",
                                    style = TelemetryMonoStyle.copy(fontSize = 11.sp),
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}
