package com.garbageguard.app.notify

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
    fun tap(view: View) {
        if (haptics) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        Sounds.play(R.raw.gg_tap, 0.55f)
    }

    /** A switch, tab or choice changing. */
    fun toggle(view: View) {
        if (haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        Sounds.play(R.raw.gg_switch, 0.55f)
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