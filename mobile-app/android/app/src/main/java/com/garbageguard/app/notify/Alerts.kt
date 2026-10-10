package com.garbageguard.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.garbageguard.app.MainActivity
import com.garbageguard.app.R
import com.garbageguard.app.data.AlertRow
import com.garbageguard.app.data.EventType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Turns new history rows into Android notifications: the heads-up banner
 * and lock-screen card from the mockup, with the snapshot and a Mute button.
 */
object Alerts {
    const val CHANNEL_ALERT = "gg_alerts"
    const val CHANNEL_WATCH = "gg_watch"
    const val ACTION_MUTE = "com.garbageguard.app.MUTE_15"
    const val EXTRA_NOTIFICATION_ID = "notification_id"

    private var source: String? = null
    private var newestSeen = 0L

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERT, "Accumulation alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Shown when the accumulation limit is reached."
                enableVibration(true)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_WATCH, "Watching the Pi", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Keeps the connection to the Pi alive so alerts arrive while the app is closed."
            }
        )
    }

    /**
     * Call with each fresh history list. The first list from a source only
     * sets the starting point, so old records never notify. After that, each
     * new accumulation or test row raises one notification.
     */
    fun onRows(
        context: Context,
        from: String,
        rows: List<AlertRow>,
        scope: CoroutineScope,
        snapshotUrl: (String) -> String?,
    ) {
        val newest = rows.maxOfOrNull { it.id } ?: 0L
        if (source != from) {
            source = from
            newestSeen = newest
            return
        }
        val fresh = rows.filter {
            it.id > newestSeen && (it.event == EventType.ACCUMULATION || it.event == EventType.TEST)
        }
        if (newest > newestSeen) newestSeen = newest
        fresh.sortedBy { it.id }.takeLast(3).forEach { row ->
            scope.launch {
                val picture = row.snapshot?.let(snapshotUrl)?.let { fetch(it) }
                show(context, row, picture)
            }
        }
    }

    private fun show(context: Context, row: AlertRow, picture: Bitmap?) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val id = (row.id % Int.MAX_VALUE).toInt()
        val immutable = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            immutable,
        )
        val test = row.event == EventType.TEST
        val builder = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_stat_gg)
            .setColor(0xFF128C84.toInt())
            .setContentTitle(if (test) "Test alert" else "Accumulation alert")
            .setContentText("${row.smoothedCount} ${if (row.smoothedCount == 1) "item" else "items"} on the water, limit is ${row.thresholdUsed}.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(open)
        if (picture != null) {
            builder.setLargeIcon(picture)
            builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(picture).bigLargeIcon(null as Bitmap?))
        }
        if (!test) {
            val mute = PendingIntent.getBroadcast(
                context, id,
                Intent(context, MuteReceiver::class.java).setAction(ACTION_MUTE).putExtra(EXTRA_NOTIFICATION_ID, id),
                immutable,
            )
            builder.addAction(0, "Mute 15 min", mute)
        }
        builder.addAction(0, "Open", open)
        try {
            manager.notify(id, builder.build())
        } catch (_: SecurityException) {
            // Permission was withdrawn between the check and the post.
        }
    }

    private suspend fun fetch(url: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 2500
                conn.readTimeout = 4000
                if (conn.responseCode != 200) null else conn.inputStream.use { BitmapFactory.decodeStream(it) }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }
}
