package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusCrimson
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.TelemetryMonoStyle
import com.example.ui.theme.TextSecondary
import com.example.vpn.VpnConnectionStatus

@Composable
fun NexoraBrandLogo(
    modifier: Modifier = Modifier,
    showProtocolBadge: Boolean = true
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(34.dp)) {
            val w = size.width
            val h = size.height
            val shieldPath = Path().apply {
                moveTo(w * 0.5f, h * 0.06f)
                lineTo(w * 0.90f, h * 0.24f)
                lineTo(w * 0.84f, h * 0.64f)
                lineTo(w * 0.5f, h * 0.94f)
                lineTo(w * 0.16f, h * 0.64f)
                lineTo(w * 0.10f, h * 0.24f)
                close()
            }
            drawPath(
                path = shieldPath,
                brush = Brush.linearGradient(
                    colors = listOf(ElectricBlue, NeonCyan),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                ),
                style = Stroke(width = 2.6.dp.toPx())
            )
            // Stylized geometric 'N' inside shield
            val nPath = Path().apply {
                moveTo(w * 0.34f, h * 0.66f)
                lineTo(w * 0.34f, h * 0.34f)
                lineTo(w * 0.66f, h * 0.66f)
                lineTo(w * 0.66f, h * 0.34f)
            }
            drawPath(
                path = nPath,
                color = NeonCyan,
                style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.brand_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = 1.4.sp
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = stringResource(R.string.brand_tag),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan
                )
            }
            if (showProtocolBadge) {
                Text(
                    text = stringResource(R.string.brand_protocol_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    letterSpacing = 1.2.sp
                )
            }
        }
    }
}

@Composable
fun NexoraGlowCard(
    modifier: Modifier = Modifier,
    borderColor: Color = ObsidianBorder,
    containerColor: Color = ObsidianCard,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = containerColor.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun GlowingConnectButton(
    status: VpnConnectionStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 196.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 400f),
        label = "buttonScale"
    )

    val coreAccent by animateColorAsState(
        targetValue = when (status) {
            VpnConnectionStatus.CONNECTED -> NeonCyan
            VpnConnectionStatus.CONNECTING,
            VpnConnectionStatus.RECONNECTING,
            VpnConnectionStatus.DISCONNECTING -> StatusAmber
            VpnConnectionStatus.ERROR -> StatusCrimson
            VpnConnectionStatus.DISCONNECTED -> ElectricBlue
        },
        animationSpec = tween(320),
        label = "coreAccent"
    )

    val secondaryAccent by animateColorAsState(
        targetValue = when (status) {
            VpnConnectionStatus.CONNECTED -> StatusEmerald
            VpnConnectionStatus.CONNECTING,
            VpnConnectionStatus.RECONNECTING,
            VpnConnectionStatus.DISCONNECTING -> ElectricBlue
            VpnConnectionStatus.ERROR -> StatusCrimson
            VpnConnectionStatus.DISCONNECTED -> Color(0xFF1A3A70)
        },
        animationSpec = tween(320),
        label = "secondaryAccent"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (status == VpnConnectionStatus.CONNECTING || status == VpnConnectionStatus.RECONNECTING) 1100 else 2400,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseProgress"
    )

    val actionLabel = when (status) {
        VpnConnectionStatus.DISCONNECTED, VpnConnectionStatus.ERROR -> stringResource(R.string.action_connect)
        VpnConnectionStatus.CONNECTING -> stringResource(R.string.action_connecting)
        VpnConnectionStatus.RECONNECTING -> stringResource(R.string.status_reconnecting)
        VpnConnectionStatus.CONNECTED -> stringResource(R.string.action_disconnect)
        VpnConnectionStatus.DISCONNECTING -> stringResource(R.string.status_disconnecting)
    }

    Box(
        modifier = modifier
            .size(buttonSize + 52.dp)
            .scale(pressScale),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = (buttonSize.toPx() / 2f)

            // Ambient radial halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        coreAccent.copy(alpha = if (status == VpnConnectionStatus.CONNECTED) 0.34f else 0.20f),
                        secondaryAccent.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.minDimension / 2f
                ),
                radius = size.minDimension / 2f,
                center = center
            )

            // Animated expanding pulse rings when active or connecting
            if (status == VpnConnectionStatus.CONNECTED || status == VpnConnectionStatus.CONNECTING) {
                val maxExpand = 26.dp.toPx()
                val ring1Radius = baseRadius + maxExpand * pulseProgress
                val ring1Alpha = (1f - pulseProgress) * 0.55f
                drawCircle(
                    color = coreAccent.copy(alpha = ring1Alpha),
                    radius = ring1Radius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )

                val delayed = (pulseProgress + 0.5f) % 1f
                val ring2Radius = baseRadius + maxExpand * delayed
                val ring2Alpha = (1f - delayed) * 0.4f
                drawCircle(
                    color = secondaryAccent.copy(alpha = ring2Alpha),
                    radius = ring2Radius,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }

        Box(
            modifier = Modifier
                .size(buttonSize)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ObsidianSurfaceElevated,
                            ObsidianBlack
                        )
                    )
                )
                .border(
                    width = 3.dp,
                    brush = Brush.linearGradient(listOf(coreAccent, secondaryAccent)),
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true, color = coreAccent),
                    role = Role.Button,
                    onClick = onClick
                )
                .testTag("connect_power_button"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PowerSettingsNew,
                    contentDescription = actionLabel,
                    tint = coreAccent,
                    modifier = Modifier.size(54.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
fun TelemetryMetricTile(
    icon: ImageVector,
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = ObsidianCard.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, ObsidianBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                style = TelemetryMonoStyle.copy(fontSize = 16.sp),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
