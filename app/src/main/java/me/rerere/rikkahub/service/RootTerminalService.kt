package me.rerere.rikkahub.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ROOT_TERMINAL_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.data.root.RootManager
import me.rerere.rikkahub.data.root.TerminalSessionTransport
import org.koin.android.ext.android.inject

class RootTerminalService : Service() {
    companion object {
        const val ACTION_START = "me.rerere.rikkahub.action.ROOT_TERMINAL_START"
        const val NOTIFICATION_ID = 2002
        private const val WAKE_LOCK_TAG = "RikkaHub:RootTerminal"
    }

    private val powerManager by lazy {
        getSystemService(Context.POWER_SERVICE) as PowerManager
    }
    private var wakeLock: PowerManager.WakeLock? = null
    private val rootManager: RootManager by inject()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var stateObserverJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START && intent != null) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundCompat()
        startObservingState()
        return START_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startObservingState() {
        if (stateObserverJob != null) return
        stateObserverJob = serviceScope.launch {
            rootManager.status.collect { status ->
                if (status.terminalSession.isActive) {
                    acquireWakeLock()
                    NotificationManagerCompat.from(this@RootTerminalService)
                        .notify(NOTIFICATION_ID, buildNotification())
                } else {
                    releaseWakeLock()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        val lock = wakeLock ?: return
        if (lock.isHeld) {
            lock.release()
        }
        wakeLock = null
    }

    private fun buildNotification(): Notification {
        val session = rootManager.status.value.terminalSession
        val contentIntent = packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
            PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        return NotificationCompat.Builder(this, ROOT_TERMINAL_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.small_icon)
            .setContentTitle(session.title.ifBlank { getString(R.string.notification_root_terminal_running) })
            .setContentText(session.transport.notificationText())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .apply {
                contentIntent?.let { setContentIntent(it) }
            }
            .build()
    }

    private fun TerminalSessionTransport?.notificationText(): String {
        return when (this) {
            TerminalSessionTransport.LOCAL_ROOT -> getString(R.string.notification_root_terminal_running_description)
            TerminalSessionTransport.SSH -> getString(R.string.notification_ssh_terminal_running_description)
            null -> getString(R.string.notification_root_terminal_running_description)
        }
    }
}
