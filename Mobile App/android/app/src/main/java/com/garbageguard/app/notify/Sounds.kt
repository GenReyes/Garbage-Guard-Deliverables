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
/**
 * The app's own sound for each kind of action. They are the same tunes the
 * Pi's buzzer plays for the same action (tools/make_sounds.py builds them from
 * the Pi's sound table), so the phone and the touchscreen sound alike.
 * Rising pitch means forward, on, open or OK; falling means back, off, close or no.
 */
enum class Sfx(val res: Int, val volume: Float = 0.6f) {
    TAP(R.raw.gg_sfx_tap),
    SWITCH_ON(R.raw.gg_sfx_switch_on),
    SWITCH_OFF(R.raw.gg_sfx_switch_off),
    CHOICE(R.raw.gg_sfx_choice),
    STEP_UP(R.raw.gg_sfx_step_up),
    STEP_DOWN(R.raw.gg_sfx_step_down),
    TAB(R.raw.gg_sfx_tab),
    OPEN(R.raw.gg_sfx_open),
    BACK(R.raw.gg_sfx_back),
    CONFIRM(R.raw.gg_sfx_confirm),
    DANGER(R.raw.gg_sfx_danger),
    DENIED(R.raw.gg_sfx_denied),
    LOGO(R.raw.gg_sfx_logo),
    THEME_LIGHT(R.raw.gg_sfx_theme_light),
    THEME_DARK(R.raw.gg_sfx_theme_dark),
    POINT(R.raw.gg_sfx_point),
    POINT_UNDO(R.raw.gg_sfx_point_undo),

    /** The app opening. */
    BOOT(R.raw.gg_sfx_boot, 0.8f),
    /** The Pi confirmed a change. */
    SAVED(R.raw.gg_sfx_saved, 0.7f),
    /** The camera stopped or came back. */
    CAM_LOST(R.raw.gg_sfx_cam_lost, 0.7f),
    CAM_BACK(R.raw.gg_sfx_cam_back, 0.7f),
    /** The Pi itself was lost or reached again. */
    LINK_UP(R.raw.gg_sfx_wifi_ok, 0.7f),
    LINK_LOST(R.raw.gg_sfx_wifi_fail, 0.7f),
}

object Sounds {
    private var pool: SoundPool? = null
    private val loaded = HashMap<Int, Int>()
    private val ready = HashSet<Int>()
    private var bootWanted = false
    @Volatile private var last = 0L

    fun init(context: Context) {
        if (pool != null) return
        val app = context.applicationContext
        val made = SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        pool = made
        made.setOnLoadCompleteListener { p, sample, status ->
            if (status != 0) return@setOnLoadCompleteListener
            ready.add(sample)
            // The opening chime is asked for before the sounds have loaded; it plays as soon as it can.
            if (bootWanted && sample == loaded[Sfx.BOOT.res]) {
                bootWanted = false
                if (Feel.sounds) p.play(sample, Sfx.BOOT.volume, Sfx.BOOT.volume, 1, 0, 1f)
            }
        }
        (Sfx.entries.map { it.res } + listOf(R.raw.gg_capture, R.raw.gg_mute, R.raw.gg_unmute, R.raw.gg_slice, R.raw.gg_miss, R.raw.gg_combo, R.raw.gg_bomb, R.raw.gg_boss, R.raw.gg_win, R.raw.gg_block, R.raw.gg_dodge, R.raw.gg_cast)).forEach {
            loaded[it] = made.load(app, it, 1)
        }
    }

    fun play(sfx: Sfx) = play(sfx.res, sfx.volume)

    /**
     * For sounds that follow from another action, such as a sheet sliding up:
     * skipped when the key that caused it has just made its own sound.
     */
    fun playIfQuiet(sfx: Sfx) {
        if (android.os.SystemClock.uptimeMillis() - last > 250) play(sfx)
    }

    /** The opening chime, once the app starts. Waits for the sound to load if it has not yet. */
    fun boot() {
        val id = loaded[Sfx.BOOT.res]
        if (id != null && id in ready) play(Sfx.BOOT) else bootWanted = true
    }

    fun play(resource: Int, volume: Float = 1f) {
        if (!Feel.sounds) return
        val id = loaded[resource] ?: return
        last = android.os.SystemClock.uptimeMillis()
        pool?.play(id, volume, volume, 1, 0, 1f)
    }
}