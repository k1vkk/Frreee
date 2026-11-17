package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.SpeedTestResultEntity
import com.example.network.SpeedTestPhase
import com.example.network.SpeedTestProgress
import com.example.ui.components.NexoraGlowCard
import com.example.ui.components.TelemetryMetricTile
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusCrimson
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.TelemetryMonoStyle
import com.example.ui.theme.TextSecondary
import java.text.DateFormat
import java.util.Date

@Composable
fun SpeedTestScreen(
    progress: SpeedTestProgress,
    history: List<SpeedTestResultEntity>,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRunning = progress.phase == SpeedTestPhase.PING ||
        progress.phase == SpeedTestPhase.DOWNLOAD ||
        progress.phase == SpeedTestPhase.UPLOAD

    val phaseText = when (progress.phase) {
        SpeedTestPhase.IDLE -> stringResource(R.string.phase_idle)
        SpeedTestPhase.PING -> stringResource(R.string.phase_ping)
        SpeedTestPhase.DOWNLOAD -> stringResource(R.string.phase_download)
        SpeedTestPhase.UPLOAD -> stringResource(R.string.phase_upload)
        SpeedTestPhase.COMPLETE -> stringResource(R.string.phase_complete)
        SpeedTestPhase.ERROR -> stringResource(R.string.phase_error)
    }

    val animatedFraction by animateFloatAsState(
        targetValue = (progress.liveMbps / 200.0).toFloat().coerceIn(0f, 1f),
        animationSpec = tween(250),
        label = "gaugeSweep"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0A1226), ObsidianBlack)))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth()) {
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

            Spacer(modifier = Modifier.height(18.dp))

            // Radial Speedometer Gauge
            NexoraGlowCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = if (isRunning) NeonCyan.copy(alpha = 0.6f) else ObsidianBorder
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(230.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 18.dp.toPx()
                            val pad = strokeWidth / 2f + 10.dp.toPx()
                            val arcSize = Size(size.width - pad * 2, size.height - pad * 2)
                            val topLeft = Offset(pad, pad)

                            drawArc(
                                color = ObsidianBorder,
                                startAngle = 140f,
                                sweepAngle = 260f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )

                            drawArc(
                                brush = Brush.sweepGradient(
                                    colors = listOf(ElectricBlue, NeonCyan, StatusEmerald)
                                ),
                                startAngle = 140f,
                                sweepAngle = 260f * animatedFraction,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = phaseText,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (progress.phase == SpeedTestPhase.ERROR) StatusCrimson else NeonCyan,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = String.format("%.1f", progress.liveMbps),
                                style = TelemetryMonoStyle.copy(fontSize = 42.sp),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = stringResource(R.string.label_mbps),
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                        }
                    }

                    if (progress.errorMessage != null) {
                        Text(
                            text = progress.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = StatusCrimson
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Button(
                        onClick = { if (isRunning) onStop() else onStart() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRunning) StatusCrimson else ElectricBlue
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("start_speed_test_button")
                    ) {
                        Icon(
                            imageVector = if (isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isRunning) stringResource(R.string.action_stop_test) else stringResource(R.string.action_start_test),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

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
            Spacer(modifier = Modifier.height(10.dp))
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

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.label_history),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
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
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = ObsidianCard,
                            border = BorderStroke(1.dp, ObsidianBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (entry.vpnActive) StatusEmerald.copy(alpha = 0.18f) else ObsidianBorder
                                        ) {
                                            Text(
                                                text = if (entry.vpnActive) stringResource(R.string.label_vpn_on) else stringResource(R.string.label_vpn_off),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (entry.vpnActive) StatusEmerald else TextSecondary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = entry.serverLabel,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.timestamp)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = String.format("↓ %.1f  ↑ %.1f Mbps", entry.downloadMbps, entry.uploadMbps),
                                        style = TelemetryMonoStyle.copy(fontSize = 13.sp),
                                        color = NeonCyan
                                    )
                                    Text(
                                        text = "${entry.pingMs} ms • ${entry.jitterMs} ms",
                                        style = TelemetryMonoStyle.copy(fontSize = 11.sp),
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
