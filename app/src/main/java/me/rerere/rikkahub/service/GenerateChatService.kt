package me.rerere.rikkahub.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import me.rerere.rikkahub.R
import me.rerere.rikkahub.KEEP_ALIVE_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.RouteActivity
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "GenerateChatService"

/**
 * Foreground service that keeps the process alive during active AI generation.
 * Acquires an ON_AFTER_RELEASE WakeLock when generation starts and releases it
 * when all active generations complete. This prevents the OS from killing the
 * process during streaming, thinking, or tool call execution.
 */
class GenerateChatService : Service() {
    companion object {
        const val ACTION_GENERATION_START = "me.rerere.rikkahub.action.GENERATION_START"
        const val ACTION_GENERATION_STOP = "me.rerere.rikkahub.action.GENERATION_STOP"
        const val NOTIFICATION_ID = 3002
        private const val WAKE_LOCK_TAG = "RikkaHub:GenerateChat"
    }

    private val powerManager by lazy {
        getSystemService(Context.POWER_SERVICE) as PowerManager
    }
    private var wakeLock: PowerManager.WakeLock? = null
    private val activeGenerationCount = AtomicInteger(0)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_GENERATION_START -> onGenerationStart()
            ACTION_GENERATION_STOP -> onGenerationStop()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    private fun onGenerationStart() {
        val count = activeGenerationCount.incrementAndGet()
        Log.i(TAG, "onGenerationStart: count=$count")
        startForegroundCompat()
        acquireWakeLock()
    }

    private fun onGenerationStop() {
        val count = activeGenerationCount.decrementAndGet().coerceAtLeast(0)
        Log.i(TAG, "onGenerationStop: count=$count")
        if (count <= 0) {
            activeGenerationCount.set(0)
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
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

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
            WAKE_LOCK_TAG
        ).apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 1000L) // 10 minute timeout safety
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
        val contentIntent = packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
            PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        return NotificationCompat.Builder(this, KEEP_ALIVE_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.small_icon)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_generate_chat))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .build()
    }
}
