package com.garbageguard.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import com.garbageguard.app.notify.Feel
import com.garbageguard.app.notify.Sfx
import com.garbageguard.app.ui.components.neuWell
import com.garbageguard.app.ui.components.pressable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garbageguard.app.data.Buzzer
import com.garbageguard.app.data.ConnState
import com.garbageguard.app.data.LiveState
import com.garbageguard.app.ui.AppViewModel
import com.garbageguard.app.ui.components.AutoSwitch
import com.garbageguard.app.ui.components.BtnKind
import com.garbageguard.app.ui.components.GgButton
import com.garbageguard.app.ui.components.GgCard
import com.garbageguard.app.ui.components.GgIcons
import com.garbageguard.app.ui.components.Hd
import com.garbageguard.app.ui.components.Hint
import com.garbageguard.app.ui.components.Ico
import com.garbageguard.app.ui.components.Segmented
import com.garbageguard.app.ui.components.Stepper
import com.garbageguard.app.ui.components.T
import com.garbageguard.app.ui.components.Tag
import com.garbageguard.app.ui.components.Tone
import com.garbageguard.app.ui.theme.Gg

private val LEVELS = listOf("low", "medium", "high")

/** "22:00" as "10:00 PM". Anything that is not a time is shown as it came. */
fun clock12(hhmm: String): String {
    val parts = hhmm.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: return hhmm
    val m = parts.getOrNull(1)?.toIntOrNull() ?: return hhmm
    val hour = if (h % 12 == 0) 12 else h % 12
    return "$hour:${m.toString().padStart(2, '0')} ${if (h < 12) "AM" else "PM"}"
}

/** Moves a time of day by [minutes], going round midnight. */
private fun shiftTime(hhmm: String, minutes: Int): String {
    val parts = hhmm.split(":")
    val now = (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    val next = ((now + minutes) % 1440 + 1440) % 1440
    return "${(next / 60).toString().padStart(2, '0')}:${(next % 60).toString().padStart(2, '0')}"
}

/**
 * The Pi's buzzer, the phone's twin of the dashboard's
 * Sound card. It is named "Pi buzzer" so nobody takes it for the phone's
 * own sounds. Changes are kept on the phone until Save, which sends only
 * what changed, in one command. Left out on a Pi that has no buzzer fields.
 */
@Composable
fun BuzzerCard(vm: AppViewModel, state: LiveState) {
    val b = state.buzzer ?: return
    val c = Gg.colors
    val reachable = state.conn != ConnState.NO_PI
    val edits = vm.buzzerEdits
    val dirty = edits.isNotEmpty()

    fun flag(key: String, saved: Boolean) = edits[key] as? Boolean ?: saved
    fun word(key: String, saved: String) = edits[key] as? String ?: saved
    fun number(key: String, saved: Int) = edits[key] as? Int ?: saved

    val on = flag("on", b.on)
    val repeat = flag("repeat", b.repeat)
    val quiet = flag("quiet_on", b.quietOn)

    GgCard {
        Hd(GgIcons.Speaker, "Pi buzzer") {
            when {
                dirty || vm.locks["buzzer"] != null -> SendingTag(vm, "buzzer", dirty, idle = "UNSAVED")
                !b.available -> Tag("Unavailable", Tone.BAD)
                !b.on -> Tag("Off", Tone.WARN, GgIcons.EyeOff)
                b.quietNow -> Tag("Quiet now", Tone.WARN, GgIcons.Moon)
                else -> Tag("On", Tone.OK)
            }
        }
        if (!b.available) Warning("The buzzer is not available: ${b.note ?: "no reason given"}")
        if (b.quietNow) Hint("Quiet hours are on now, so the buzzer is silent.", icon = GgIcons.Moon)
        if (b.quietNote != null) Warning(b.quietNote)

        SettingRow("Buzzer", "Off silences every sound below", GgIcons.Speaker) {
            AutoSwitch(on, { vm.editBuzzer("on", !on, b.on) }, label = null, icon = null)
        }
        if (on) {
            Field(GgIcons.Signal, "Volume") {
                Segmented(
                    listOf("Low", "Medium", "High"),
                    selected = LEVELS.indexOf(word("volume", b.volume)).coerceAtLeast(0),
                    lock = "buzzer",
                    onSelect = { vm.editBuzzer("volume", LEVELS[it], b.volume) },
                )
            }
            val alerts = flag("alerts", b.alerts)
            SettingRow("Alerts", "The alarm when the limit is reached", GgIcons.Bell) {
                AutoSwitch(alerts, { vm.editBuzzer("alerts", !alerts, b.alerts) }, label = null, icon = null)
            }
            val countdown = flag("countdown", b.countdown)
            SettingRow("Countdown and shutdown", "Low-battery beeps and the shutdown tune", GgIcons.Battery) {
                AutoSwitch(countdown, { vm.editBuzzer("countdown", !countdown, b.countdown) }, label = null, icon = null)
            }
            val ui = flag("ui", b.ui)
            SettingRow("Button sounds", "Ticks on the Pi's own touchscreen", GgIcons.Activity) {
                AutoSwitch(ui, { vm.editBuzzer("ui", !ui, b.ui) }, label = null, icon = null)
            }
            SettingRow("Repeat alarm", "Sounds again while an alert is not muted", GgIcons.Refresh) {
                AutoSwitch(repeat, { vm.editBuzzer("repeat", !repeat, b.repeat) }, label = null, icon = null)
            }
            if (repeat) {
                val every = number("repeat_s", b.repeatS)
                Field(GgIcons.Clock, "Repeat every") {
                    Stepper(
                        every, "sec",
                        onMinus = { vm.editBuzzer("repeat_s", (every - 10).coerceIn(10, 300), b.repeatS) },
                        onPlus = { vm.editBuzzer("repeat_s", (every + 10).coerceIn(10, 300), b.repeatS) },
                        lock = "buzzer",
                    )
                }
            }
            SettingRow("Quiet hours", "A daily window with no buzzer", GgIcons.Moon) {
                AutoSwitch(quiet, { vm.editBuzzer("quiet_on", !quiet, b.quietOn) }, label = null, icon = null)
            }
            if (quiet) {
                val from = word("quiet_from", b.quietFrom)
                val to = word("quiet_to", b.quietTo)
                Field(GgIcons.Moon, "Quiet from") {
                    Stepper(
                        0, "", text = clock12(from),
                        onMinus = { vm.editBuzzer("quiet_from", shiftTime(from, -30), b.quietFrom) },
                        onPlus = { vm.editBuzzer("quiet_from", shiftTime(from, 30), b.quietFrom) },
                        lock = "buzzer",
                    )
                }
                Field(GgIcons.Sun, "Quiet until") {
                    Stepper(
                        0, "", text = clock12(to),
                        onMinus = { vm.editBuzzer("quiet_to", shiftTime(to, -30), b.quietTo) },
                        onPlus = { vm.editBuzzer("quiet_to", shiftTime(to, 30), b.quietTo) },
                        lock = "buzzer",
                    )
                }
                Hint("Quiet hours follow the Pi's clock, in Philippine time.", icon = GgIcons.Clock)
            }
        }

        GgButton("Save", vm::saveBuzzer, Modifier.fillMaxWidth(), icon = GgIcons.Save, kind = BtnKind.INK, enabled = dirty && reachable, lock = "buzzer")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GgButton(
                "Test alarm", { vm.testBuzzer("accumulation") }, Modifier.weight(1f), icon = GgIcons.Bell,
                enabled = reachable && b.available, lock = "buzzer_test",
            )
            GgButton(
                "Test tune", { vm.testBuzzer("test") }, Modifier.weight(1f), icon = GgIcons.Speaker,
                enabled = reachable && b.available, lock = "buzzer_test",
            )
        }
        PiSoundPicker(vm, enabled = reachable && b.available)
        Hint("Tests use the volume saved on the Pi, so save first.", icon = GgIcons.Ok)
    }
}

/**
 * Every sound the Pi's buzzer knows, by the name the API uses, with the words
 * the app shows. The same list as SOUNDS in the Pi's gg_buzzer.py.
 */
val PI_SOUNDS = listOf(
    "accumulation" to "Alarm", "test" to "Test tune", "boot" to "Power on", "ready" to "Detection ready",
    "saved" to "Saved", "wifi_ok" to "Wi-Fi connected", "wifi_fail" to "Wi-Fi failed",
    "cam_lost" to "Camera lost", "cam_back" to "Camera back", "phone_join" to "Phone joined",
    "manual" to "Capture", "muted" to "Muted", "unmuted" to "Unmuted", "auto_muted" to "Auto-muted",
    "countdown" to "Low-battery beep", "shutdown_low" to "Low-battery shutdown", "shutdown_manual" to "Shut down",
    "logo" to "Logo", "tap" to "Tap", "switch_on" to "Switch on", "switch_off" to "Switch off",
    "choice" to "Choice", "step_up" to "Plus", "step_down" to "Minus", "tab" to "Tab",
    "open" to "Open", "back" to "Back", "confirm" to "Confirm", "danger" to "Danger", "denied" to "Denied",
    "theme_light" to "Light theme", "theme_dark" to "Dark theme", "minimize" to "Minimize",
    "restore" to "Restore", "point" to "Point", "point_undo" to "Undo point", "wake" to "Wake",
)

/** Picks any of the Pi's sounds and plays it on the Pi's buzzer. */
@Composable
private fun PiSoundPicker(vm: AppViewModel, enabled: Boolean) {
    val c = Gg.colors
    val view = LocalView.current
    var open by remember { mutableStateOf(false) }
    var pick by rememberSaveable { mutableStateOf("boot") }
    val label = PI_SOUNDS.firstOrNull { it.first == pick }?.second ?: pick
    Field(GgIcons.Speaker, "Play a Pi sound") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .neuWell(c, 12.dp)
                        .pressable(sound = Sfx.OPEN) { open = true }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    T(label, Modifier.weight(1f), size = 0.9f, weight = FontWeight.Bold, color = c.text, maxLines = 1)
                    T("\u25BE", size = 0.8f, color = c.muted)
                }
                DropdownMenu(
                    expanded = open,
                    onDismissRequest = { open = false },
                    containerColor = c.raised,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(2.dp, c.edge),
                    modifier = Modifier.heightIn(max = 360.dp),
                ) {
                    PI_SOUNDS.forEach { (name, text) ->
                        DropdownMenuItem(
                            text = { T(text, size = 0.9f, color = if (name == pick) c.teal else c.text) },
                            onClick = {
                                Feel.play(view, Sfx.CHOICE)
                                open = false
                                pick = name
                            },
                        )
                    }
                }
            }
            GgButton("Play", { vm.testBuzzer(pick) }, icon = GgIcons.Speaker, enabled = enabled, lock = "buzzer_test")
        }
    }
}

/** A red line for something that is wrong on the Pi. */
@Composable
private fun Warning(text: String) {
    val c = Gg.colors
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Ico(GgIcons.Warn, size = 14.dp, tint = c.bad)
        T(text, Modifier.weight(1f), size = 0.8f, weight = FontWeight.Bold, color = c.bad, lineHeight = 1.3f)
    }
}
