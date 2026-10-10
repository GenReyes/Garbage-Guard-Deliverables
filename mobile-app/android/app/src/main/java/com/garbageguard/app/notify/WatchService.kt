package com.garbageguard.app.notify

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.garbageguard.app.MainActivity
import com.garbageguard.app.R
import kotlinx.coroutines.launch

/**
 * Keeps the app alive in the background while it follows a real Pi. Android
 * freezes an app soon after it leaves the screen; a foreground service with
 * its own small notification is the supported way to keep polling, which is
 * what lets an alert arrive while the app is closed.
 */
class WatchService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppGraph.init(this)
        val address = AppGraph.prefs(this).getString(AppGraph.KEY_ADDRESS, "").orEmpty()
        if (address.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        // After Android restarts the service on its own, the poll loop is gone too.
        if (!AppGraph.pi.running) AppGraph.pi.start(address)

        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, Alerts.CHANNEL_WATCH)
            .setSmallIcon(R.drawable.ic_stat_gg)
            .setColor(0xFF128C84.toInt())
            .setContentTitle("Watching the Pi for alerts")
            .setContentText(address)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(WATCH_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(WATCH_ID, notification)
            }
        } catch (_: Exception) {
            // Android refused the foreground start. Alerts then only work while the app is open.
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    companion object {
        private const val WATCH_ID = 7001

        fun start(context: Context) {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, WatchService::class.java))
            } catch (_: Exception) {
                // Not allowed from the background; the next time the app opens it starts.
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WatchService::class.java))
        }
    }
}

/** The "Mute 15 min" button on an alert notification. */
class MuteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Alerts.ACTION_MUTE) return
        NotificationManagerCompat.from(context).cancel(intent.getIntExtra(Alerts.EXTRA_NOTIFICATION_ID, 0))
        val pending = goAsync()
        AppGraph.scope.launch {
            try {
                AppGraph.mute?.invoke(900)
            } finally {
                pending.finish()
            }
        }
    }
}
