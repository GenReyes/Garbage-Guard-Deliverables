package com.garbageguard.app.notify

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.roundToInt

/**
 * The app's own vibration patterns, for moments that deserve more than a
 * plain click: alerts, warnings, the link dropping, a saved change. Each one
 * has its own rhythm, so the phone can be told apart in a pocket. Plain keys
 * keep Android's touch clicks in [Feel], which follow the phone's own touch
 * settings; these patterns follow the Vibration switch and the strength
 * chosen in Settings.
 */
object Haptics {
    enum class Strength(val label: String, val scale: Float) {
        LIGHT("Light", 0.45f), NORMAL("Normal", 0.75f), STRONG("Strong", 1f),
    }

    @Volatile var strength = Strength.NORMAL

    /**
     * One pattern, as (milliseconds, amplitude) steps. Amplitude runs 1 to 255;
     * 0 is a pause. Phones without amplitude control play every step above 0
     * at their one strength, so the rhythm still tells the patterns apart.
     */
    enum class Buzz(vararg val steps: Pair<Int, Int>) {
        // Notifications and their in-app twins. Alerts are long and hard, routine events short and soft.
        ACCUMULATION(300 to 255, 120 to 0, 300 to 255, 120 to 0, 300 to 255),
        TEST(150 to 200, 100 to 0, 300 to 200),
        AUTO_MUTED(80 to 150, 80 to 0, 80 to 150, 80 to 0, 200 to 200),
        CAPTURE(25 to 200, 40 to 0, 15 to 120),
        MUTED(200 to 170, 100 to 0, 80 to 100),
        UNMUTED(80 to 100, 100 to 0, 200 to 170),
        LOW_BATTERY(700 to 255, 150 to 0, 150 to 200, 100 to 0, 150 to 200),
        COUNTDOWN(150 to 220, 150 to 0, 150 to 220, 150 to 0, 600 to 255),
        SHUTDOWN(400 to 170),

        // In the app only.
        DANGER(60 to 255, 70 to 0, 90 to 255),
        LOGO(40 to 80, 40 to 0, 40 to 150, 40 to 0, 70 to 255),
        BOOT(30 to 40, 30 to 80, 30 to 120, 30 to 160, 40 to 200),
        SAVED(20 to 170, 60 to 0, 20 to 170),
        DENIED(50 to 200, 50 to 0, 50 to 200),
        LINK_LOST(120 to 220, 100 to 150, 120 to 80, 140 to 40),
        LINK_UP(40 to 120, 60 to 0, 70 to 230),
        CAM_LOST(80 to 150, 80 to 60),
        CAM_BACK(40 to 70, 50 to 0, 50 to 160),
        CHARGER_ON(40 to 100, 60 to 0, 80 to 200),
        CHARGER_OFF(80 to 200, 60 to 0, 40 to 100),
        /** The count on the water passed half the limit. */
        LEVEL_HALF(40 to 110, 80 to 0, 40 to 110),
        /** The count passed four fifths of the limit. */
        LEVEL_HIGH(60 to 170, 70 to 0, 60 to 170, 70 to 0, 60 to 170),
        ;

        /**
         * The same rhythm as a notification channel wants it: a pause first,
         * then buzz and pause in turn. Channels cannot carry amplitudes.
         */
        fun channelPattern(): LongArray {
            // Starts on an empty pause; steps of the same kind in a row are joined.
            val out = mutableListOf(0L)
            var on = false
            steps.forEach { (ms, amp) ->
                if ((amp > 0) == on) out[out.lastIndex] = out.last() + ms
                else { out.add(ms.toLong()); on = amp > 0 }
            }
            return out.toLongArray()
        }
    }

    private var vibrator: Vibrator? = null

    fun init(context: Context) {
        if (vibrator != null) return
        val app = context.applicationContext
        vibrator = runCatching {
            if (Build.VERSION.SDK_INT >= 31) app.getSystemService(VibratorManager::class.java)?.defaultVibrator
            else @Suppress("DEPRECATION") (app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
        }.getOrNull()
    }

    /** Plays one pattern, unless vibration is switched off in Settings. */
    fun play(buzz: Buzz) {
        if (!Feel.haptics) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val scale = strength.scale
        val timings = LongArray(buzz.steps.size) { buzz.steps[it].first.toLong() }
        val amps = IntArray(buzz.steps.size) {
            val a = buzz.steps[it].second
            if (a == 0) 0 else (a * scale).roundToInt().coerceIn(1, 255)
        }
        try {
            v.vibrate(VibrationEffect.createWaveform(timings, amps, -1))
        } catch (_: Exception) {
            // No permission or no motor: the sound still plays.
        }
    }

    /** The pattern that goes with a sound, for the sounds that have one. */
    fun forSfx(sfx: Sfx): Buzz? = when (sfx) {
        Sfx.DANGER -> Buzz.DANGER
        Sfx.LOGO -> Buzz.LOGO
        Sfx.DENIED -> Buzz.DENIED
        Sfx.SAVED -> Buzz.SAVED
        Sfx.BOOT -> Buzz.BOOT
        Sfx.LINK_LOST -> Buzz.LINK_LOST
        Sfx.LINK_UP -> Buzz.LINK_UP
        Sfx.CAM_LOST -> Buzz.CAM_LOST
        Sfx.CAM_BACK -> Buzz.CAM_BACK
        else -> null
    }
}
