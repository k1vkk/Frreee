package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.UserPreferences
import com.example.data.remote.SessionDto
import com.example.ui.components.NexoraGlowCard
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.StatusCrimson
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.TelemetryMonoStyle
import com.example.ui.theme.TextSecondary

@Composable
fun ProfileScreen(
    preferences: UserPreferences,
    sessions: List<SessionDto>,
    authLoading: Boolean,
    authError: String?,
    authNotice: String?,
    onSignIn: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit,
    onForgotPassword: (String) -> Unit,
    onResetPassword: (String, String, String, () -> Unit) -> Unit,
    onSignOut: () -> Unit,
    onRotateKey: () -> Unit,
    onRevokeSession: (String) -> Unit,
    onClearError: () -> Unit,
    onNavigateHistory: () -> Unit,
    onNavigateSupport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isSignedIn = preferences.authToken.isNotBlank()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showResetFlow by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var resetCode by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }

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
                text = stringResource(R.string.profile_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (!isSignedIn) {
                NexoraGlowCard(modifier = Modifier.fillMaxWidth()) {
                    if (showResetFlow) {
                        Text(
                            text = stringResource(R.string.reset_password_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = NeonCyan
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text(stringResource(R.string.field_email)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { onForgotPassword(email) },
                            enabled = !authLoading && email.isNotBlank(),
                            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.action_send_reset_code), color = NeonCyan)
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = resetCode,
                            onValueChange = { resetCode = it.filter { ch -> ch.isDigit() }.take(6) },
                            label = { Text(stringResource(R.string.field_reset_code)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = { Text(stringResource(R.string.field_new_password)) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (authNotice != null) {
                            Spacer(Modifier.height(10.dp))
                            Text(authNotice, style = MaterialTheme.typography.bodyMedium, color = StatusEmerald)
                        }
                        if (authError != null) {
                            Spacer(Modifier.height(10.dp))
                            Text(authError, style = MaterialTheme.typography.bodyMedium, color = StatusCrimson)
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                onResetPassword(email, resetCode, newPassword) {
                                    showResetFlow = false
                                    resetCode = ""
                                    newPassword = ""
                                }
                            },
                            enabled = !authLoading && email.isNotBlank() && resetCode.length == 6 && newPassword.length >= 8,
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text(stringResource(R.string.action_confirm_reset), fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(6.dp))
                        TextButton(
                            onClick = { showResetFlow = false; onClearError() },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text(stringResource(R.string.action_back_to_signin), color = TextSecondary)
                        }
                    } else {
                        TabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = Color.Transparent,
                            contentColor = NeonCyan
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0; onClearError() },
                                text = { Text(stringResource(R.string.tab_sign_in)) }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1; onClearError() },
                                text = { Text(stringResource(R.string.tab_register)) }
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        if (selectedTab == 1) {
                            OutlinedTextField(
                                value = displayName,
                                onValueChange = { displayName = it },
                                label = { Text(stringResource(R.string.field_full_name)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text(stringResource(R.string.field_email)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_email_input")
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(stringResource(R.string.field_password)) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_password_input")
                        )
                        if (authNotice != null) {
                            Spacer(Modifier.height(10.dp))
                            Text(authNotice, style = MaterialTheme.typography.bodyMedium, color = StatusEmerald)
                        }
                        if (authError != null) {
                            Spacer(Modifier.height(10.dp))
                            Text(authError, style = MaterialTheme.typography.bodyMedium, color = StatusCrimson)
                        }
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = {
                                if (selectedTab == 0) {
                                    onSignIn(email, password)
                                } else {
                                    onRegister(email, password, displayName)
                                }
                            },
                            enabled = !authLoading && email.isNotBlank() && password.length >= 8,
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("auth_submit_button")
                        ) {
                            if (authLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                            } else {
                                Text(
                                    text = if (selectedTab == 0) stringResource(R.string.action_sign_in) else stringResource(R.string.action_register),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (selectedTab == 0) {
                            Spacer(Modifier.height(6.dp))
                            TextButton(
                                onClick = { showResetFlow = true; onClearError() },
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text(stringResource(R.string.action_forgot_password), color = NeonCyan)
                            }
                        }
                    }
                }
            } else {
                NexoraGlowCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = NeonCyan.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(ElectricBlue.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(34.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = preferences.userDisplayName.ifBlank { preferences.userEmail },
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = preferences.userEmail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (preferences.planTier != "free") StatusEmerald.copy(alpha = 0.18f) else ObsidianBorder
                        ) {
                            Text(
                                text = preferences.planTier.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (preferences.planTier != "free") StatusEmerald else TextSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = onSignOut,
                        border = BorderStroke(1.dp, StatusCrimson.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = StatusCrimson, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_sign_out), color = StatusCrimson)
                    }
                }
            }

            // Device WireGuard Curve25519 Key Card
            NexoraGlowCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Key, contentDescription = null, tint = NeonCyan)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.label_wireguard_pubkey), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ObsidianBlack,
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = preferences.wgPublicKey.ifBlank { "Generating Curve25519…" },
                        style = TelemetryMonoStyle.copy(fontSize = 12.sp),
                        color = NeonCyan,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val copiedText = stringResource(R.string.key_copied_msg)
                    OutlinedButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("WireGuard Public Key", preferences.wgPublicKey))
                            Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
                        },
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("copy_pubkey_button")
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_copy_key), color = NeonCyan)
                    }
                    OutlinedButton(
                        onClick = onRotateKey,
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("rotate_pubkey_button")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_rotate_key), color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }

            // Active PostgreSQL Sessions
            if (isSignedIn) {
                NexoraGlowCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Devices, contentDescription = null, tint = NeonCyan)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.label_active_sessions), style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(10.dp))
                    if (sessions.isEmpty()) {
                        Text(stringResource(R.string.sessions_empty), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            sessions.forEach { session ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = ObsidianCard,
                                    border = BorderStroke(1.dp, ObsidianBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(session.deviceLabel, style = MaterialTheme.typography.labelLarge)
                                            Text(
                                                text = session.ipAddress.ifBlank { "PostgreSQL Session" },
                                                style = TelemetryMonoStyle.copy(fontSize = 11.sp),
                                                color = TextSecondary
                                            )
                                        }
                                        if (!session.isCurrent) {
                                            TextButton(onClick = { onRevokeSession(session.id) }) {
                                                Text(stringResource(R.string.action_revoke_session), color = StatusCrimson)
                                            }
                                        } else {
                                            Text(
                                                text = stringResource(R.string.action_connected_node),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = StatusEmerald
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onNavigateHistory,
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.History, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.nav_history), color = MaterialTheme.colorScheme.onBackground)
                }
                OutlinedButton(
                    onClick = onNavigateSupport,
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.SupportAgent, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.nav_support), color = MaterialTheme.colorScheme.onBackground)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
