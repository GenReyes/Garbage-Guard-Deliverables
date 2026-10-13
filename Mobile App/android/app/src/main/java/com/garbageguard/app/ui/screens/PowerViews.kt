package com.garbageguard.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garbageguard.app.data.Battery
import com.garbageguard.app.data.ConnState
import com.garbageguard.app.data.LiveState
import com.garbageguard.app.ui.AppViewModel
import com.garbageguard.app.ui.components.BtnKind
import com.garbageguard.app.ui.components.GgButton
import com.garbageguard.app.ui.components.GgIcons
import com.garbageguard.app.ui.components.Ico
import com.garbageguard.app.ui.components.LocalOnColour
import com.garbageguard.app.ui.components.Soft
import com.garbageguard.app.ui.components.T
import com.garbageguard.app.ui.components.breathe
import com.garbageguard.app.ui.components.neuRaised
import com.garbageguard.app.ui.components.ring
import com.garbageguard.app.ui.theme.Gg
import kotlinx.coroutines.delay

/** How the Pi's own screen is described to the person holding the phone. */
fun screenWord(screen: String): String = when (screen) {
    "dim" -> "Dimmed"
    "off" -> "Off"
    else -> "On"
}

/**
 * The Monitor page's line about the Pi's own screen: whether it is on,
 * dimmed or off, with a key to wake it from the phone. It is left out for a
 * Pi that does not report a screen saver.
 */
@Composable
fun PiScreenChip(vm: AppViewModel, state: LiveState) {
    if (state.screenMode == null || state.conn == ConnState.NO_PI) return
    val c = Gg.colors
    val awake = state.screen == "on"
    Row(
        Modifier
            .fillMaxWidth()
            .neuRaised(c, 14.dp, fillColor = if (awake) c.raised else c.tealTint)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Ico(if (awake) GgIcons.Sun else GgIcons.Moon, size = 19.dp, tint = if (awake) c.warnText else c.bag)
        Column(Modifier.weight(1f)) {
            T("PI SCREEN: ${screenWord(state.screen).uppercase()}", size = 0.84f, weight = FontWeight.Black, color = c.text, spacing = 0.04f, fit = true)
            T(
                if (awake) "Detection runs with the screen on or off" else "Asleep to save power. Detection still runs",
                size = 0.76f, weight = FontWeight.Bold, color = c.muted, fit = true,
            )
        }
        if (awake) GgButton("Sleep", vm::sleepPi, icon = GgIcons.Moon, small = true, lock = "screen")
        else CompositionLocalProvider(LocalOnColour provides true) {
            GgButton("Wake", vm::wakePi, icon = GgIcons.Sun, kind = BtnKind.TEAL, small = true, lock = "screen")
        }
    }
}

/**
 * Shown over every page while the Pi is counting down to a low-battery
 * shutdown, and while it is powering off. The time is counted down on the
 * phone between readings from the Pi, so it moves every second.
 */
@Composable
fun ShutdownBanner(vm: AppViewModel, battery: Battery, modifier: Modifier = Modifier) {
    val counting = battery.shutdownState == "counting"
    val poweringOff = battery.shutdownState == "powering_off"
    if (!counting && !poweringOff) return
    val c = Gg.colors

    var left by remember(battery.shutdownInS) { mutableIntStateOf(battery.shutdownInS ?: 0) }
    LaunchedEffect(battery.shutdownInS, counting) {
        while (counting && left > 0) {
            delay(1000)
            left--
        }
    }
    val pulse = 0.82f + 0.18f * breathe(1200)
    Row(
        modifier
            .fillMaxWidth()
            .neuRaised(c, 14.dp, hard = 4.dp, soft = Soft.LG, fillColor = c.bad.copy(alpha = pulse), edge = c.edge)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Ico(GgIcons.Power, Modifier.ring(), size = 22.dp, tint = c.onBad)
        Column(Modifier.weight(1f)) {
            T(
                if (poweringOff) "THE PI IS SHUTTING DOWN" else "PI SHUTTING DOWN IN ${left / 60}:${(left % 60).toString().padStart(2, '0')}",
                size = 0.9f, weight = FontWeight.Black, color = c.onBad, tnum = true, spacing = 0.03f, fit = true,
            )
            T(
                when {
                    poweringOff -> "It goes offline within a minute"
                    battery.shutdownFloor -> "Battery at ${battery.percent}%. Only the charger can stop it"
                    else -> "Battery at ${battery.percent}%. Plug in the charger or cancel"
                },
                size = 0.76f, weight = FontWeight.Bold, color = c.onBad, fit = true,
            )
        }
        if (counting && !battery.shutdownFloor) CompositionLocalProvider(LocalOnColour provides true) {
            GgButton("Cancel", vm::cancelShutdown, icon = GgIcons.X, small = true, lock = "cancel_shutdown")
        }
    }
}
