package com.garbageguard.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
 * The Pi's buzzer and vibration motor, the phone's twin of the dashboard's
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

        // The vibration motor has its own switch, apart from the buzzer's.
        if (b.vibeOn != null) {
            val vibe = flag("vibe_on", b.vibeOn)
            if (!b.vibeAvailable) Warning("The vibration motor is not available: ${b.vibeNote ?: "no reason given"}")
            SettingRow("Pi vibration", "The motor in the Pi's case shakes on an alert", GgIcons.Phone) {
                AutoSwitch(vibe, { vm.editBuzzer("vibe_on", !vibe, b.vibeOn) }, label = null, icon = null)
            }
            if (vibe) {
                Field(GgIcons.Gauge, "Vibration strength") {
                    Segmented(
                        listOf("Low", "Medium", "High"),
                        selected = LEVELS.indexOf(word("vibe_strength", b.vibeStrength)).coerceAtLeast(0),
                        lock = "buzzer",
                        onSelect = { vm.editBuzzer("vibe_strength", LEVELS[it], b.vibeStrength) },
                    )
                }
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
        if (b.vibeOn != null) {
            GgButton(
                "Test vibration", vm::testVibe, Modifier.fillMaxWidth(), icon = GgIcons.Phone,
                enabled = reachable && b.vibeAvailable && !vm.remote, lock = "vibe_test",
            )
        }
        Hint(
            if (vm.remote && b.vibeOn != null) "Tests use the saved volume. Test vibration needs the Pi's Wi-Fi."
            else "Tests use the volume saved on the Pi, so save first.",
            icon = GgIcons.Ok,
        )
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
