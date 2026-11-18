package com.example.ui.screens

import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.DataSaverOn
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.data.local.SpeedTestResultEntity
import com.example.network.SpeedTestErrorType
import com.example.network.SpeedTestPhase
import com.example.network.SpeedTestProgress
import com.example.ui.components.AnimatedSpeedGauge
import com.example.ui.components.NexoraGlowCard
import com.example.ui.components.PhaseStepperRow
import com.example.ui.components.TelemetryMetricTile
import com.example.ui.components.VpnComparisonCard
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
import com.example.ui.viewmodel.SpeedHistoryFilter
import com.example.ui.viewmodel.VpnComparisonUiState
import com.example.vpn.VpnConnectionStatus
import com.example.vpn.VpnSessionState
import com.example.vpn.formatBytes
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeedTestScreen(
    progress: SpeedTestProgress,
    history: List<SpeedTestResultEntity>,
    historyFilter: SpeedHistoryFilter,
    comparisonState: VpnComparisonUiState,
    vpnState: VpnSessionState,
    ecoMode: Boolean,
    includeUpload: Boolean,
    onAttachWebView: (WebView) -> Unit,
    onDetachWebView: (WebView) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSetEcoMode: (Boolean) -> Unit,
    onSetIncludeUpload: (Boolean) -> Unit,
    onSetHistoryFilter: (SpeedHistoryFilter) -> Unit,
    onSelectAsBaseline: (Long) -> Unit,
    onSelectAsTarget: (Long) -> Unit,
    onResetComparison: () -> Unit,
    onDeleteEntry: (Long) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var attachedWebView by remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(attachedWebView) {
        val view = attachedWebView
        onDispose {
            if (view != null) {
                onDetachWebView(view)
            }
        }
    }

    val isRunning = progress.phase == SpeedTestPhase.PREPARING ||
        progress.phase == SpeedTestPhase.PING ||
        progress.phase == SpeedTestPhase.DOWNLOAD ||
        progress.phase == SpeedTestPhase.UPLOAD

    val vpnConnected = vpnState.status == VpnConnectionStatus.CONNECTED

    val phaseText = when (progress.phase) {
        SpeedTestPhase.IDLE -> stringResource(R.string.phase_idle)
        SpeedTestPhase.PREPARING -> stringResource(R.string.phase_preparing)
        SpeedTestPhase.PING -> stringResource(R.string.phase_ping)
        SpeedTestPhase.DOWNLOAD -> stringResource(R.string.phase_download)
        SpeedTestPhase.UPLOAD -> stringResource(R.string.phase_upload)
        SpeedTestPhase.COMPLETE -> stringResource(R.string.phase_complete)
        SpeedTestPhase.ERROR -> stringResource(R.string.phase_error)
    }

    val errorDisplayText = if (progress.phase == SpeedTestPhase.ERROR) {
        when (progress.errorType) {
            SpeedTestErrorType.MISSING_CONFIGURATION -> stringResource(R.string.speed_err_missing_config)
            SpeedTestErrorType.CONNECTION_UNAVAILABLE -> stringResource(R.string.speed_err_offline)
            SpeedTestErrorType.INVALID_API_KEY -> stringResource(R.string.speed_err_invalid_key)
            SpeedTestErrorType.DOMAIN_NOT_AUTHORIZED -> stringResource(R.string.speed_err_unauthorized_domain)
            SpeedTestErrorType.SCRIPT_LOAD_FAILED -> stringResource(R.string.speed_err_script_load)
            SpeedTestErrorType.TIMEOUT -> stringResource(R.string.speed_err_timeout)
            else -> stringResource(
                R.string.speed_err_unknown,
                progress.errorMessage.orEmpty().ifBlank { "Unknown" }
            )
        }
    } else null

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0A1226), ObsidianBlack)))
    ) {
        // Isolated, non-visible Android WebView strictly hosting official SpeedOf.Me JS API
        AndroidView(
            factory = { ctx ->
                WebView(ctx).also { view ->
                    attachedWebView = view
                    onAttachWebView(view)
                }
            },
            modifier = Modifier
                .size(1.dp)
                .alpha(0f)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth()) {
                // Header + Active VPN Context Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = stringResource(R.string.speed_test_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = stringResource(R.string.speed_test_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (vpnConnected) StatusEmerald.copy(alpha = 0.14f) else ObsidianCard,
                        border = BorderStroke(
                            1.dp,
                            if (vpnConnected) StatusEmerald.copy(alpha = 0.55f) else ObsidianBorder
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (vpnConnected) StatusEmerald else NeonCyan)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (vpnConnected) {
                                    vpnState.activeServer?.nodeCode?.ifBlank { vpnState.activeServer.name }
                                        ?: stringResource(R.string.label_vpn_on)
                                } else {
                                    stringResource(R.string.label_vpn_off)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (vpnConnected) StatusEmerald else NeonCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Main Circular Gauge + Phase Stepper Card
                NexoraGlowCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = when {
                        progress.phase == SpeedTestPhase.ERROR -> StatusCrimson.copy(alpha = 0.6f)
                        isRunning -> NeonCyan.copy(alpha = 0.65f)
                        progress.phase == SpeedTestPhase.COMPLETE -> StatusEmerald.copy(alpha = 0.5f)
                        else -> ObsidianBorder
                    }
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AnimatedSpeedGauge(
                            progress = progress,
                            phaseText = phaseText,
                            isRunning = isRunning
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        PhaseStepperRow(
                            phase = progress.phase,
                            progressFraction = progress.progressFraction,
                            includeUpload = includeUpload
                        )

                        if (progress.testServer.isNotBlank() || progress.dataUsedBytes > 0L) {
                            Spacer(modifier = Modifier.height(10.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (progress.testServer.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = ObsidianSurface,
                                        border = BorderStroke(1.dp, ObsidianBorder)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.label_test_server_node, progress.testServer),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NeonCyan,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                if (progress.dataUsedBytes > 0L) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = ObsidianSurface,
                                        border = BorderStroke(1.dp, ObsidianBorder)
                                    ) {
                                        Text(
                                            text = stringResource(
                                                R.string.label_test_data_consumed,
                                                formatBytes(progress.dataUsedBytes)
                                            ),
                                            style = TelemetryMonoStyle.copy(fontSize = 11.sp),
                                            color = TextSecondary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(visible = errorDisplayText != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = StatusCrimson.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, StatusCrimson.copy(alpha = 0.55f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.ErrorOutline,
                                            contentDescription = null,
                                            tint = StatusCrimson,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = errorDisplayText.orEmpty(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = StatusCrimson
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = onStart,
                                        border = BorderStroke(1.dp, StatusCrimson.copy(alpha = 0.7f)),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("retry_speed_test_button")
                                    ) {
                                        Icon(Icons.Filled.Refresh, contentDescription = null, tint = StatusCrimson, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.action_retry_test), color = StatusCrimson, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = { if (isRunning) onStop() else onStart() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRunning) StatusCrimson else ElectricBlue
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("start_speed_test_button")
                        ) {
                            Icon(
                                imageVector = if (isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isRunning) stringResource(R.string.action_stop_test)
                                else stringResource(R.string.action_start_test),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 4 Real-Time Telemetry Metric Tiles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TelemetryMetricTile(
                        icon = Icons.Filled.ArrowDownward,
                        label = stringResource(R.string.label_download),
                        value = if (progress.downloadMbps > 0.0) String.format("%.2f Mbps", progress.downloadMbps) else "—",
                        accentColor = NeonCyan,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryMetricTile(
                        icon = Icons.Filled.ArrowUpward,
                        label = stringResource(R.string.label_upload),
                        value = if (progress.uploadMbps > 0.0) String.format("%.2f Mbps", progress.uploadMbps) else "—",
                        accentColor = ElectricBlue,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TelemetryMetricTile(
                        icon = Icons.Filled.NetworkPing,
                        label = stringResource(R.string.label_ping),
                        value = if (progress.pingMs > 0) "${progress.pingMs} ms" else "—",
                        accentColor = StatusEmerald,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryMetricTile(
                        icon = Icons.Filled.Timeline,
                        label = stringResource(R.string.label_jitter),
                        value = if (progress.jitterMs > 0) "${progress.jitterMs} ms" else "—",
                        accentColor = StatusAmber,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Data Usage Optimization Card (Tailored for slow/limited networks & Yemen)
                NexoraGlowCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.DataSaverOn, contentDescription = null, tint = StatusEmerald)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.label_data_mode),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = ecoMode,
                            onClick = { if (!isRunning) onSetEcoMode(true) },
                            enabled = !isRunning,
                            label = { Text(stringResource(R.string.mode_eco_label)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = StatusEmerald.copy(alpha = 0.22f),
                                selectedLabelColor = StatusEmerald,
                                containerColor = ObsidianSurface,
                                labelColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("eco_mode_chip")
                        )
                        FilterChip(
                            selected = !ecoMode,
                            onClick = { if (!isRunning) onSetEcoMode(false) },
                            enabled = !isRunning,
                            label = { Text(stringResource(R.string.mode_standard_label)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricBlue.copy(alpha = 0.25f),
                                selectedLabelColor = NeonCyan,
                                containerColor = ObsidianSurface,
                                labelColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("standard_mode_chip")
                        )
                        FilterChip(
                            selected = includeUpload,
                            onClick = { if (!isRunning) onSetIncludeUpload(!includeUpload) },
                            enabled = !isRunning,
                            label = { Text(stringResource(R.string.toggle_include_upload)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricBlue.copy(alpha = 0.22f),
                                selectedLabelColor = NeonCyan,
                                containerColor = ObsidianSurface,
                                labelColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("upload_toggle_chip")
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.eco_mode_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // VPN Before vs After Comparison Card
                VpnComparisonCard(
                    comparisonState = comparisonState,
                    onResetToDefault = onResetComparison
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Test History Header + Clear Action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.label_history),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (history.isNotEmpty()) {
                        TextButton(
                            onClick = onClearHistory,
                            modifier = Modifier.testTag("clear_history_button")
                        ) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = StatusCrimson, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.history_clear_all), color = StatusCrimson)
                        }
                    }
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val filters = listOf(
                        SpeedHistoryFilter.ALL to stringResource(R.string.filter_history_all),
                        SpeedHistoryFilter.DIRECT to stringResource(R.string.filter_history_direct),
                        SpeedHistoryFilter.VPN to stringResource(R.string.filter_history_vpn)
                    )
                    filters.forEach { (filter, label) ->
                        FilterChip(
                            selected = historyFilter == filter,
                            onClick = { onSetHistoryFilter(filter) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricBlue.copy(alpha = 0.25f),
                                selectedLabelColor = NeonCyan,
                                containerColor = ObsidianCard,
                                labelColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("history_filter_${filter.name.lowercase()}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (history.isEmpty()) {
                    NexoraGlowCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Speed, contentDescription = null, tint = NeonCyan)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.history_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        history.forEach { entry ->
                            val isBaseline = comparisonState.baselineTest?.id == entry.id
                            val isTarget = comparisonState.targetTest?.id == entry.id
                            HistoryEntryCard(
                                entry = entry,
                                isBaseline = isBaseline,
                                isTarget = isTarget,
                                onSelectAsBaseline = { onSelectAsBaseline(entry.id) },
                                onSelectAsTarget = { onSelectAsTarget(entry.id) },
                                onDelete = { onDeleteEntry(entry.id) }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun HistoryEntryCard(
    entry: SpeedTestResultEntity,
    isBaseline: Boolean,
    isTarget: Boolean,
    onSelectAsBaseline: () -> Unit,
    onSelectAsTarget: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ObsidianCard,
        border = BorderStroke(
            width = if (isBaseline || isTarget) 1.5.dp else 1.dp,
            color = when {
                isBaseline -> NeonCyan.copy(alpha = 0.7f)
                isTarget -> StatusEmerald.copy(alpha = 0.7f)
                else -> ObsidianBorder
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_card_${entry.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (entry.vpnActive) StatusEmerald.copy(alpha = 0.18f) else ObsidianBorder
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (entry.vpnActive) {
                                    Icon(
                                        Icons.Filled.Shield,
                                        contentDescription = null,
                                        tint = StatusEmerald,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(Modifier.width(3.dp))
                                }
                                Text(
                                    text = if (entry.vpnActive) stringResource(R.string.label_vpn_on) else stringResource(R.string.label_vpn_off),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (entry.vpnActive) StatusEmerald else TextSecondary
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = entry.serverLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    val metaParts = buildList {
                        add(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.timestamp)))
                        if (entry.testServer.isNotBlank()) add(entry.testServer)
                        if (entry.dataUsedBytes > 0L) add(formatBytes(entry.dataUsedBytes))
                    }.joinToString(" • ")
                    Text(
                        text = metaParts,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = String.format("↓ %.1f  ↑ %.1f Mbps", entry.downloadMbps, entry.uploadMbps),
                        style = TelemetryMonoStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        color = NeonCyan
                    )
                    Text(
                        text = "${entry.pingMs} ms • ${entry.jitterMs} ms",
                        style = TelemetryMonoStyle.copy(fontSize = 11.sp),
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onSelectAsBaseline,
                        border = BorderStroke(1.dp, if (isBaseline) NeonCyan else ObsidianBorder),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text(
                            text = stringResource(R.string.action_set_before),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isBaseline) NeonCyan else TextSecondary,
                            fontWeight = if (isBaseline) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    OutlinedButton(
                        onClick = onSelectAsTarget,
                        border = BorderStroke(1.dp, if (isTarget) StatusEmerald else ObsidianBorder),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text(
                            text = stringResource(R.string.action_set_after),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isTarget) StatusEmerald else TextSecondary,
                            fontWeight = if (isTarget) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = stringResource(R.string.action_delete_node),
                        tint = StatusCrimson
                    )
                }
            }
        }
    }
}
