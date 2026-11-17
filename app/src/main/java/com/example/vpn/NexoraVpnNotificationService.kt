package com.example.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.NexoraApp
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class NexoraVpnNotificationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectorJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            val controller = (application as NexoraApp).vpnController
            serviceScope.launch {
                controller.disconnect()
                stopSelf()
            }
            return START_NOT_STICKY
        }

        val initialNotification = buildNotification(
            serverName = getString(R.string.app_name),
            statsText = getString(R.string.status_connecting)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        collectorJob?.cancel()
        val controller = (application as NexoraApp).vpnController
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        collectorJob = serviceScope.launch {
            controller.state.collectLatest { vpnState ->
                when (vpnState.status) {
                    VpnConnectionStatus.DISCONNECTED -> {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                    VpnConnectionStatus.CONNECTED -> {
                        val server = vpnState.activeServer?.let { "${it.countryName} • ${it.name}" }
                            ?: getString(R.string.app_name)
                        val stats = getString(
                            R.string.vpn_notification_stats,
                            formatSpeedCompact(vpnState.downloadSpeedBps),
                            formatSpeedCompact(vpnState.uploadSpeedBps),
                            formatDuration(vpnState.durationSeconds)
                        )
                        nm.notify(
                            NOTIFICATION_ID,
                            buildNotification(
                                serverName = getString(R.string.vpn_notification_connected, server),
                                statsText = stats
                            )
                        )
                    }
                    else -> {
                        nm.notify(
                            NOTIFICATION_ID,
                            buildNotification(
                                serverName = getString(R.string.app_name),
                                statsText = getString(R.string.status_connecting)
                            )
                        )
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        collectorJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.vpn_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.vpn_notification_channel_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(serverName: String, statsText: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val disconnectIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, NexoraVpnNotificationService::class.java).apply {
                action = ACTION_DISCONNECT
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(serverName)
            .setContentText(statsText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .addAction(0, getString(R.string.action_disconnect), disconnectIntent)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "nexora_vpn_tunnel"
        const val NOTIFICATION_ID = 4401
        const val ACTION_DISCONNECT = "com.example.vpn.ACTION_DISCONNECT"
    }
}
