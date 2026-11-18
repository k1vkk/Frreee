package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.SpeedTestResultEntity
import com.example.network.SpeedTestPhase
import com.example.network.SpeedTestProgress
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
import com.example.ui.viewmodel.VpnComparisonUiState
import java.text.DateFormat
import java.util.Date
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun AnimatedSpeedGauge(
    progress: SpeedTestProgress,
    phaseText: String,
    isRunning: Boolean,
    modifier: Modifier = Modifier
) {
    // Non-linear square-root scale makes low Yemen/mobile speeds (1-15 Mbps) AND high speeds (100+ Mbps) both visibly responsive
    val targetFraction = sqrt((progress.liveMbps / 120.0).coerceIn(0.0, 1.0)).toFloat()
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "gaugeFraction"
    )
    val animatedMbps by animateFloatAsState(
        targetValue = progress.liveMbps.toFloat(),
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "gaugeNumericMbps"
    )

    val pulseTransition = rememberInfiniteTransition(label = "gaugePulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val ringGlowColor = when (progress.phase) {
        SpeedTestPhase.ERROR -> StatusCrimson
        SpeedTestPhase.COMPLETE -> StatusEmerald
        SpeedTestPhase.UPLOAD -> ElectricBlue
        else -> NeonCyan
    }

    Box(
        modifier = modifier.size(236.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 18.dp.toPx()
            val pad = strokeWidth / 2f + 14.dp.toPx()
            val arcSize = Size(size.width - pad * 2, size.height - pad * 2)
            val topLeft = Offset(pad, pad)
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = size.minDimension / 2f - 4.dp.toPx()

            // Subtle ambient glow halo while testing
            if (isRunning || progress.phase == SpeedTestPhase.COMPLETE) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ringGlowColor.copy(alpha = if (isRunning) pulseAlpha * 0.22f else 0.14f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = size.minDimension / 2f
                    ),
                    radius = size.minDimension / 2f,
                    center = center
                )
            }

            // Perimeter tick marks along the 260-degree sweep
            val totalTicks = 26
            val tickInner = outerRadius - 6.dp.toPx()
            for (i in 0..totalTicks) {
                val frac = i.toFloat() / totalTicks.toFloat()
                val angleDeg = 140.0 + 260.0 * frac
                val rad = angleDeg * PI / 180.0
                val isLit = frac <= animatedFraction
                val start = Offset(
                    x = center.x + (tickInner * cos(rad)).toFloat(),
                    y = center.y + (tickInner * sin(rad)).toFloat()
                )
                val end = Offset(
                    x = center.x + (outerRadius * cos(rad)).toFloat(),
                    y = center.y + (outerRadius * sin(rad)).toFloat()
                )
                drawLine(
                    color = if (isLit) NeonCyan.copy(alpha = 0.85f) else ObsidianBorder,
                    start = start,
                    end = end,
                    strokeWidth = if (i % 5 == 0) 2.5.dp.toPx() else 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Background track
            drawArc(
                color = ObsidianBorder,
                startAngle = 140f,
                sweepAngle = 260f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Active gradient arc
            if (animatedFraction > 0.005f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(ElectricBlue, NeonCyan, StatusEmerald, ElectricBlue)
                    ),
                    startAngle = 140f,
                    sweepAngle = 260f * animatedFraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // Glowing tip indicator dot
            val tipDeg = 140.0 + 260.0 * animatedFraction
            val tipRad = tipDeg * PI / 180.0
            val arcRadius = arcSize.width / 2f
            val tipPoint = Offset(
                x = center.x + (arcRadius * cos(tipRad)).toFloat(),
                y = center.y + (arcRadius * sin(tipRad)).toFloat()
            )
            if (isRunning || animatedFraction > 0.01f) {
                drawCircle(
                    color = Color.White,
                    radius = 5.dp.toPx(),
                    center = tipPoint
                )
                drawCircle(
                    color = ringGlowColor.copy(alpha = 0.55f),
                    radius = 9.dp.toPx(),
                    center = tipPoint
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = phaseText,
                style = MaterialTheme.typography.labelSmall,
                color = ringGlowColor,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = String.format("%.2f", animatedMbps),
                style = TelemetryMonoStyle.copy(fontSize = 38.sp, fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = stringResource(R.string.label_mbps),
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            if (isRunning) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${(progress.progressFraction * 100f).toInt().coerceIn(0, 100)}%",
                    style = TelemetryMonoStyle.copy(fontSize = 12.sp),
                    color = NeonCyan
                )
            }
        }
    }
}

@Composable
fun PhaseStepperRow(
    phase: SpeedTestPhase,
    progressFraction: Float,
    includeUpload: Boolean,
    modifier: Modifier = Modifier
) {
    val activeStep = when (phase) {
        SpeedTestPhase.PREPARING, SpeedTestPhase.PING -> 1
        SpeedTestPhase.DOWNLOAD -> 2
        SpeedTestPhase.UPLOAD -> 3
        SpeedTestPhase.COMPLETE -> 4
        else -> 0
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PhaseStepChip(
                label = stringResource(R.string.step_ping),
                isDone = activeStep > 1,
                isActive = activeStep == 1,
                modifier = Modifier.weight(1f)
            )
            PhaseStepChip(
                label = stringResource(R.string.step_download),
                isDone = activeStep > 2,
                isActive = activeStep == 2,
                modifier = Modifier.weight(1f)
            )
            if (includeUpload) {
                PhaseStepChip(
                    label = stringResource(R.string.step_upload),
                    isDone = activeStep > 3,
                    isActive = activeStep == 3,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        val animatedProgress by animateFloatAsState(
            targetValue = progressFraction.coerceIn(0f, 1f),
            animationSpec = tween(240),
            label = "linearStepProgress"
        )
        LinearProgressIndicator(
            progress = { animatedProgress },
            color = if (phase == SpeedTestPhase.COMPLETE) StatusEmerald else NeonCyan,
            trackColor = ObsidianBorder,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(4.dp))
        )
    }
}

@Composable
private fun PhaseStepChip(
    label: String,
    isDone: Boolean,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val bg by animateColorAsState(
        targetValue = when {
            isDone -> StatusEmerald.copy(alpha = 0.16f)
            isActive -> ElectricBlue.copy(alpha = 0.25f)
            else -> ObsidianSurface
        },
        animationSpec = tween(220),
        label = "stepBg"
    )
    val border by animateColorAsState(
        targetValue = when {
            isDone -> StatusEmerald.copy(alpha = 0.6f)
            isActive -> NeonCyan
            else -> ObsidianBorder
        },
        animationSpec = tween(220),
        label = "stepBorder"
    )
    val contentColor = when {
        isDone -> StatusEmerald
        isActive -> NeonCyan
        else -> TextSecondary
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = bg,
        border = BorderStroke(1.dp, border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isDone) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = StatusEmerald,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(4.dp))
            } else if (isActive) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(NeonCyan)
                )
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                fontWeight = if (isActive || isDone) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun VpnComparisonCard(
    comparisonState: VpnComparisonUiState,
    onResetToDefault: () -> Unit,
    modifier: Modifier = Modifier
) {
    NexoraGlowCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = if (comparisonState.metrics != null) NeonCyan.copy(alpha = 0.45f) else ObsidianBorder
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.CompareArrows, contentDescription = null, tint = NeonCyan)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = stringResource(R.string.comparison_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (comparisonState.isCustomSelection) {
                        Text(
                            text = stringResource(R.string.comparison_custom_pair),
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonCyan
                        )
                    }
                }
            }
            if (comparisonState.isCustomSelection) {
                TextButton(
                    onClick = onResetToDefault,
                    modifier = Modifier.testTag("reset_comparison_button")
                ) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.comparison_reset), color = NeonCyan)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val stacked = maxWidth < 340.dp
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ComparisonEndpointBox(
                        title = stringResource(R.string.comparison_before_vpn),
                        entry = comparisonState.baselineTest,
                        accent = NeonCyan,
                        modifier = Modifier.fillMaxWidth()
                    )
                    ComparisonEndpointBox(
                        title = stringResource(R.string.comparison_after_vpn),
                        entry = comparisonState.targetTest,
                        accent = StatusEmerald,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ComparisonEndpointBox(
                        title = stringResource(R.string.comparison_before_vpn),
                        entry = comparisonState.baselineTest,
                        accent = NeonCyan,
                        modifier = Modifier.weight(1f)
                    )
                    ComparisonEndpointBox(
                        title = stringResource(R.string.comparison_after_vpn),
                        entry = comparisonState.targetTest,
                        accent = StatusEmerald,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        val metrics = comparisonState.metrics
        if (metrics != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DeltaChip(
                    label = stringResource(R.string.comparison_delta_download),
                    valueText = formatSignedMbps(metrics.downloadDiffMbps, metrics.downloadDiffPercent),
                    isImprovement = metrics.downloadDiffMbps >= 0.0,
                    modifier = Modifier.weight(1f)
                )
                DeltaChip(
                    label = stringResource(R.string.comparison_delta_upload),
                    valueText = formatSignedMbps(metrics.uploadDiffMbps, metrics.uploadDiffPercent),
                    isImprovement = metrics.uploadDiffMbps >= 0.0,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DeltaChip(
                    label = stringResource(R.string.comparison_delta_ping),
                    valueText = formatSignedMs(metrics.pingDiffMs, metrics.pingDiffPercent),
                    isImprovement = metrics.pingDiffMs <= 0,
                    modifier = Modifier.weight(1f)
                )
                DeltaChip(
                    label = stringResource(R.string.comparison_delta_jitter),
                    valueText = formatSignedMs(metrics.jitterDiffMs, metrics.jitterDiffPercent),
                    isImprovement = metrics.jitterDiffMs <= 0,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.comparison_needs_pair),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun ComparisonEndpointBox(
    title: String,
    entry: SpeedTestResultEntity?,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, if (entry != null) accent.copy(alpha = 0.4f) else ObsidianBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (entry == null) {
                Text(
                    text = stringResource(R.string.comparison_not_tested_yet),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            } else {
                Text(
                    text = entry.serverLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = String.format("↓ %.1f  ↑ %.1f Mbps", entry.downloadMbps, entry.uploadMbps),
                    style = TelemetryMonoStyle.copy(fontSize = 12.sp),
                    color = accent
                )
                Text(
                    text = "${entry.pingMs} ms • Jitter ${entry.jitterMs} ms",
                    style = TelemetryMonoStyle.copy(fontSize = 11.sp),
                    color = TextSecondary
                )
                Text(
                    text = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(entry.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun DeltaChip(
    label: String,
    valueText: String,
    isImprovement: Boolean,
    modifier: Modifier = Modifier
) {
    val color = if (isImprovement) StatusEmerald else StatusCrimson
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = valueText,
                style = TelemetryMonoStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatSignedMbps(diff: Double, pct: Double?): String {
    val sign = if (diff >= 0.0) "+" else ""
    val pctStr = pct?.let { String.format(" (%+.0f%%)", it) }.orEmpty()
    return String.format("%s%.2f Mbps%s", sign, diff, pctStr)
}

private fun formatSignedMs(diff: Int, pct: Double?): String {
    val sign = if (diff > 0) "+" else ""
    val pctStr = pct?.let { String.format(" (%+.0f%%)", it) }.orEmpty()
    return "$sign$diff ms$pctStr"
}
