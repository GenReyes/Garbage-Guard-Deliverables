package com.garbageguard.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.net.Uri
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
 * Turns new history rows into Android notifications, each kind with its own
 * sound. On Android a notification's sound belongs to its channel, so every
 * kind of event has a channel. That is also what lets the right sound play
 * while the app is in the background or closed.
 */
object Alerts {
    const val CHANNEL_WATCH = "gg_watch"
    const val ACTION_MUTE = "com.garbageguard.app.MUTE_15"
    const val EXTRA_NOTIFICATION_ID = "notification_id"

    /** How one kind of event is announced. */
    private class Kind(
        val channel: String,
        val channelName: String,
        val about: String,
        val sound: Int,
        val importance: Int,
        val title: String,
        /** Raised by someone pressing a button, so on-screen it only needs a sound. */
        val routine: Boolean,
    )

    private val kinds = mapOf(
        EventType.ACCUMULATION to Kind(
            "gg_limit_v3", "Limit reached", "The accumulation limit was reached. Sounds a buzzer.",
            R.raw.gg_buzzer, NotificationManager.IMPORTANCE_HIGH, "Accumulation alert", routine = false,
        ),
        EventType.TEST to Kind(
            "gg_test_v3", "Test alerts", "Someone pressed Test alert.",
            R.raw.gg_test, NotificationManager.IMPORTANCE_HIGH, "Test alert", routine = false,
        ),
        EventType.AUTO_MUTED to Kind(
            "gg_automute_v3", "Auto-muted alerts", "An alert was raised and auto-mute silenced it.",
            R.raw.gg_automute, NotificationManager.IMPORTANCE_DEFAULT, "Alert auto-muted", routine = false,
        ),
        EventType.MANUAL to Kind(
            "gg_capture_v3", "Captures", "A snapshot was captured from another screen.",
            R.raw.gg_capture, NotificationManager.IMPORTANCE_DEFAULT, "Snapshot captured", routine = true,
        ),
        EventType.MUTED to Kind(
            "gg_mute_v3", "Alert muted", "The alert was muted from another screen.",
            R.raw.gg_mute, NotificationManager.IMPORTANCE_DEFAULT, "Alert muted", routine = true,
        ),
        EventType.UNMUTED to Kind(
            "gg_unmute_v3", "Alert unmuted", "The mute was lifted.",
            R.raw.gg_unmute, NotificationManager.IMPORTANCE_DEFAULT, "Alert unmuted", routine = true,
        ),
    )

    private var source: String? = null
    private var newestSeen = 0L

    fun ensureChannels(context: Context) {
        Sounds.init(context)
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        // A channel's sound cannot be changed once made, so the first version's channel is retired.
        manager.deleteNotificationChannel("gg_alerts")
        val audio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        kinds.values.forEach { kind ->
            manager.createNotificationChannel(
                NotificationChannel(kind.channel, kind.channelName, kind.importance).apply {
                    description = kind.about
                    setSound(Uri.parse("android.resource://${context.packageName}/raw/${context.resources.getResourceEntryName(kind.sound)}"), audio)
                    enableVibration(kind.importance >= NotificationManager.IMPORTANCE_HIGH)
                }
            )
        }
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_WATCH, "Watching the Pi", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Keeps the connection to the Pi alive so alerts arrive while the app is closed."
            }
        )
    }

    /**
     * Call with each fresh history list. The first list from a source only
     * sets the starting point, so old records never announce themselves.
     *
     * After that each new row is announced once. Alerts always raise a
     * notification. Routine events (capture, mute, unmute) only play their
     * sound while the app is on screen, since the person holding the phone
     * most likely caused them, and raise a notification when it is not.
     * Suppressed spikes are not alerts and stay silent.
     */
    fun onRows(
        context: Context,
        from: String,
        rows: List<AlertRow>,
        scope: CoroutineScope,
        foreground: Boolean,
        snapshotUrl: (String) -> String?,
    ) {
        val newest = rows.maxOfOrNull { it.id } ?: 0L
        if (source != from) {
            source = from
            newestSeen = newest
            return
        }
        val fresh = rows.filter { it.id > newestSeen && it.event in kinds }
        if (newest > newestSeen) newestSeen = newest
        fresh.sortedBy { it.id }.takeLast(3).forEach { row ->
            val kind = kinds.getValue(row.event)
            if (kind.routine && foreground) {
                Sounds.play(kind.sound)
            } else {
                scope.launch {
                    val picture = row.snapshot?.let(snapshotUrl)?.let { fetch(it) }
                    show(context, row, kind, picture)
                }
            }
        }
    }

    private fun show(context: Context, row: AlertRow, kind: Kind, picture: Bitmap?) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val id = (row.id % Int.MAX_VALUE).toInt()
        val immutable = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            immutable,
        )
        val items = "${row.smoothedCount} ${if (row.smoothedCount == 1) "item" else "items"}"
        val text = when (row.event) {
            EventType.ACCUMULATION, EventType.TEST -> "$items on the water, limit is ${row.thresholdUsed}."
            EventType.AUTO_MUTED -> "$items on the water. Auto-mute silenced the alert."
            EventType.MANUAL -> "$items in view when the snapshot was taken."
            else -> "Changed on the Pi or another phone."
        }
        val high = kind.importance >= NotificationManager.IMPORTANCE_HIGH
        val builder = NotificationCompat.Builder(context, kind.channel)
            .setSmallIcon(R.drawable.ic_stat_gg)
            .setColor(0xFF128C84.toInt())
            .setContentTitle(kind.title)
            .setContentText(text)
            .setPriority(if (high) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (high) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(open)
        if (picture != null) {
            builder.setLargeIcon(picture)
            builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(picture).bigLargeIcon(null as Bitmap?))
        }
        if (row.event == EventType.ACCUMULATION) {
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

    private suspend fun fetch(url: String): Bitmap? {
        val cloudScheme = com.garbageguard.app.ui.components.RemoteImages.CLOUD
        if (url.startsWith(cloudScheme)) {
            return com.garbageguard.app.ui.components.RemoteImages.cloud?.invoke(url.removePrefix(cloudScheme))
        }
        return fetchHttp(url)
    }

    private suspend fun fetchHttp(url: String): Bitmap? = withContext(Dispatchers.IO) {
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