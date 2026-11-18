package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import com.example.data.local.UserPreferences
import com.example.data.remote.SupportTicketDto
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
import com.example.ui.viewmodel.ApiHealthState
import com.example.vpn.VpnSessionState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SupportCenterScreen(
    preferences: UserPreferences,
    vpnState: VpnSessionState,
    apiHealth: ApiHealthState,
    tickets: List<SupportTicketDto>,
    supportLoading: Boolean,
    supportNotice: String?,
    supportError: String?,
    onSubmitTicket: (String, String, String, Map<String, String>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var expandedFaq by remember { mutableIntStateOf(-1) }
    var category by remember { mutableStateOf("tunnel") }
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    val diagnosticsMap = remember(preferences, vpnState, apiHealth) {
        mapOf(
            "appVersion" to BuildConfig.VERSION_NAME,
            "device" to "${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT})",
            "apiConfigured" to preferences.apiBaseUrl.isNotBlank().toString(),
            "apiHealth" to apiHealth.toString(),
            "vpnStatus" to vpnState.status.name,
            "activeNode" to (vpnState.activeServer?.nodeCode ?: "none"),
            "wgKeyReady" to preferences.wgPublicKey.isNotBlank().toString(),
            "killSwitch" to preferences.killSwitchEnabled.toString(),
            "dnsShield" to preferences.dnsLeakProtectionEnabled.toString(),
            "ipv6Shield" to preferences.ipv6LeakProtectionEnabled.toString()
        )
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
                IconButton(onClick = onBack, modifier = Modifier.testTag("support_back_button")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = NeonCyan
                    )
                }
                Spacer(Modifier.width(4.dp))
                Column {
                    Text(
                        text = stringResource(R.string.support_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.support_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            // Live System Readiness Audit Card
            NexoraGlowCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.BugReport, contentDescription = null, tint = NeonCyan)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.support_diag_title), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                diagnosticsMap.forEach { (k, v) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(k, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text(
                            text = v,
                            style = TelemetryMonoStyle.copy(fontSize = 11.sp),
                            color = NeonCyan
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                val copiedMsg = stringResource(R.string.diag_copied_msg)
                OutlinedButton(
                    onClick = {
                        val bundleText = diagnosticsMap.entries.joinToString("\n") { "${it.key}=${it.value}" }
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("NEXORA Diagnostics", bundleText))
                        Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
                    },
                    border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth().testTag("copy_diagnostics_button")
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_copy_diag), color = NeonCyan)
                }
            }

            // FAQ Section
            Text(
                text = stringResource(R.string.support_faq_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            val faqs = listOf(
                stringResource(R.string.faq_q1) to stringResource(R.string.faq_a1),
                stringResource(R.string.faq_q2) to stringResource(R.string.faq_a2),
                stringResource(R.string.faq_q3) to stringResource(R.string.faq_a3)
            )
            faqs.forEachIndexed { idx, (q, a) ->
                val expanded = expandedFaq == idx
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ObsidianCard,
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedFaq = if (expanded) -1 else idx }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(q, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Icon(
                                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null,
                                tint = NeonCyan
                            )
                        }
                        AnimatedVisibility(visible = expanded) {
                            Text(
                                text = a,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }

            // Submit Support Ticket Card
            NexoraGlowCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SupportAgent, contentDescription = null, tint = NeonCyan)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.support_ticket_title), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                if (preferences.authToken.isBlank()) {
                    Text(
                        text = stringResource(R.string.support_sign_in_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusAmber
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("tunnel", "billing", "account", "speed").forEach { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat.uppercase()) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricBlue.copy(alpha = 0.25f),
                                    selectedLabelColor = NeonCyan,
                                    containerColor = ObsidianSurface,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text(stringResource(R.string.field_ticket_subject)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("ticket_subject_input")
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text(stringResource(R.string.field_ticket_message)) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth().testTag("ticket_message_input")
                    )
                    if (supportNotice != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(supportNotice, style = MaterialTheme.typography.bodySmall, color = StatusEmerald)
                    }
                    if (supportError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(supportError, style = MaterialTheme.typography.bodySmall, color = StatusCrimson)
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            onSubmitTicket(category, subject, message, diagnosticsMap)
                            subject = ""
                            message = ""
                        },
                        enabled = !supportLoading && subject.isNotBlank() && message.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        modifier = Modifier.fillMaxWidth().testTag("submit_ticket_button")
                    ) {
                        if (supportLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_submit_ticket), fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (tickets.isNotEmpty()) {
                Text(stringResource(R.string.support_my_tickets), style = MaterialTheme.typography.titleMedium)
                tickets.forEach { t ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ObsidianCard,
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(t.subject, style = MaterialTheme.typography.titleSmall)
                                Text(t.status.uppercase(), style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                            }
                            if (!t.message.isNullOrBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(t.message, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}
