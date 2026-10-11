package com.garbageguard.app.notify

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.garbageguard.app.R

/**
 * Short sounds played by the app itself while it is on screen. Alerts that
 * must sound while the app is closed go through notification channels in
 * [Alerts] instead, because only Android can play those.
 */
object Sounds {
    private var pool: SoundPool? = null
    private val loaded = HashMap<Int, Int>()

    fun init(context: Context) {
        if (pool != null) return
        val app = context.applicationContext
        val made = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        pool = made
        listOf(R.raw.gg_capture, R.raw.gg_mute, R.raw.gg_unmute, R.raw.gg_slice, R.raw.gg_miss, R.raw.gg_tap, R.raw.gg_switch, R.raw.gg_combo, R.raw.gg_bomb, R.raw.gg_boss, R.raw.gg_win).forEach {
            loaded[it] = made.load(app, it, 1)
        }
    }

    fun play(resource: Int, volume: Float = 1f) {
        if (!Feel.sounds) return
        val id = loaded[resource] ?: return
        pool?.play(id, volume, volume, 1, 0, 1f)
    }
}