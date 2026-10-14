package com.garbageguard.app.notify

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import com.garbageguard.app.R

/**
 * How the app feels under the finger: a small vibration and a soft sound on
 * keys and switches. Both can be turned off in Settings. Alert sounds are
 * separate; those belong to Android's notification settings.
 */
object Feel {
    @Volatile var sounds = true
    @Volatile var haptics = true

    /** A key press. */
    fun tap(view: View) = play(view, Sfx.TAP)

    /** A choice changing. */
    fun toggle(view: View) = play(view, Sfx.CHOICE)

    /** Any control: its own sound and a vibration that fits it. */
    fun play(view: View, sfx: Sfx) {
        // Warnings, the logo and failures have patterns of their own; other keys use Android's clicks.
        val buzz = Haptics.forSfx(sfx)
        if (buzz != null) Haptics.play(buzz)
        else if (haptics) view.performHapticFeedback(hapticFor(sfx))
        Sounds.play(sfx)
    }

    /** A sound and its vibration with no key involved, such as the Pi confirming a change. */
    fun event(sfx: Sfx) {
        Haptics.forSfx(sfx)?.let(Haptics::play)
        Sounds.play(sfx)
    }

    private fun hapticFor(sfx: Sfx): Int {
        val v = Build.VERSION.SDK_INT
        val key = HapticFeedbackConstants.VIRTUAL_KEY
        return when (sfx) {
            Sfx.CONFIRM -> if (v >= 30) HapticFeedbackConstants.CONFIRM else key
            Sfx.SWITCH_ON -> if (v >= 34) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.KEYBOARD_TAP
            Sfx.SWITCH_OFF -> if (v >= 34) HapticFeedbackConstants.TOGGLE_OFF else HapticFeedbackConstants.KEYBOARD_TAP
            Sfx.STEP_UP, Sfx.STEP_DOWN, Sfx.CHOICE, Sfx.POINT, Sfx.POINT_UNDO ->
                if (v >= 30) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.KEYBOARD_TAP
            Sfx.BACK -> HapticFeedbackConstants.CLOCK_TICK
            Sfx.TAB, Sfx.THEME_LIGHT, Sfx.THEME_DARK -> HapticFeedbackConstants.KEYBOARD_TAP
            else -> key
        }
    }

    /** Something weighty: a long hold recognised, a round lost. */
    fun thump(view: View) {
        if (haptics) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    /** A light tick with no sound of its own, for moments that already make one. */
    fun tick(view: View) {
        if (haptics) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
}