package com.garbageguard.app.ui.screens

import com.garbageguard.app.ui.components.Segmented
import androidx.compose.ui.text.style.TextAlign
import com.garbageguard.app.notify.Sfx
import com.garbageguard.app.notify.Feel
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import com.garbageguard.app.ui.components.feedAspect
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garbageguard.app.data.AlertRow
import com.garbageguard.app.data.Battery
import com.garbageguard.app.data.NetInterface
import com.garbageguard.app.data.Viewer
import com.garbageguard.app.ui.components.Skeleton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.garbageguard.app.ui.components.BatteryGlyph
import com.garbageguard.app.ui.components.BatteryLevel
import com.garbageguard.app.ui.components.Hint
import com.garbageguard.app.ui.components.ReadoutWell
import com.garbageguard.app.ui.components.RollingText
import com.garbageguard.app.ui.components.Tag
import com.garbageguard.app.ui.components.accentOf
import com.garbageguard.app.ui.components.Tone
import com.garbageguard.app.ui.components.batteryLevel
import com.garbageguard.app.ui.components.batterySource
import com.garbageguard.app.ui.components.flowColor
import com.garbageguard.app.ui.components.neuRaised
import com.garbageguard.app.ui.components.signed
import com.garbageguard.app.ui.AppViewModel
import com.garbageguard.app.ui.HistFilter
import com.garbageguard.app.ui.Overlay
import com.garbageguard.app.ui.components.Acts
import com.garbageguard.app.ui.components.BtnKind
import com.garbageguard.app.ui.components.FEED_BLACK
import com.garbageguard.app.ui.components.FeedCanvas
import com.garbageguard.app.ui.components.GgButton
import com.garbageguard.app.ui.components.GgIcons
import com.garbageguard.app.ui.components.GgInput
import com.garbageguard.app.ui.components.GgSheet
import com.garbageguard.app.ui.components.Ico
import com.garbageguard.app.ui.components.SheetText
import com.garbageguard.app.ui.components.SheetTitle
import com.garbageguard.app.ui.components.Soft
import com.garbageguard.app.ui.components.T
import com.garbageguard.app.ui.components.ggBackground
import com.garbageguard.app.ui.components.neuWell
import com.garbageguard.app.ui.components.spin
import androidx.compose.ui.text.input.KeyboardType
import com.garbageguard.app.ui.components.pressable
import com.garbageguard.app.ui.components.blockTouches
import com.garbageguard.app.ui.components.rememberSnapshot
import com.garbageguard.app.ui.theme.Gg

/** Draws whichever sheet or viewer the ViewModel has open. */
@Composable
fun OverlayHost(
    vm: AppViewModel,
    alerts: List<AlertRow>,
    battery: Battery,
    viewers: List<Viewer>,
    onFixBattery: () -> Unit,
    onAlertSounds: () -> Unit,
    onAllowAlerts: () -> Unit,
) {
    val close = { vm.overlay = null }
    vm.openAlertSounds = onAlertSounds
    when (val o = vm.overlay) {
        null -> {}
        Overlay.Mute -> MuteSheet(vm, close)
        Overlay.PiBattery -> PiBatterySheet(battery, close)
        Overlay.Network -> NetworkSheet(vm, close)
        Overlay.Viewers -> ViewersSheet(viewers, vm.clientId, close)
        Overlay.Firebase -> FirebaseSheet(vm, close)
        Overlay.Restart -> GgSheet(close) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Ico(GgIcons.Warn, size = 30.dp, tint = Gg.colors.bad)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SheetTitle("Restart Garbage-Guard on the Pi?")
                    SheetText("Detection, the camera and the dashboard stop for a few seconds, then start again. Settings, the monitored area and history are kept.")
                }
            }
            Acts {
                GgButton("Cancel", close, Modifier.weight(1f))
                GgButton("Restart", vm::restartPi, Modifier.weight(1f), icon = GgIcons.Refresh, kind = BtnKind.DANGER)
            }
        }
        Overlay.Shutdown -> GgSheet(close) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Ico(GgIcons.Power, size = 30.dp, tint = Gg.colors.bad)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SheetTitle("Shut down the Pi?")
                    SheetText("Detection, alerts and the dashboard stop. The Pi goes offline within a minute.")
                    SheetText("It can only be started again with the power switch on the UPS HAT, at the Pi.", mutedSmall = true)
                }
            }
            Acts {
                GgButton("Cancel", close, Modifier.weight(1f))
                GgButton("Shut down", vm::shutdownPi, Modifier.weight(1f), icon = GgIcons.Power, kind = BtnKind.DANGER)
            }
        }
        Overlay.Filter -> DateSheet(vm, close)
        is Overlay.Delete -> DeleteSheet(vm, alerts.firstOrNull { it.id == o.id }, o.id == null, vm.visibleRows(alerts).size, close)
        is Overlay.Viewer -> {
            val row = alerts.firstOrNull { it.id == o.id }
            if (row == null) LaunchedEffect(o) { close() } else SnapshotViewer(row, vm.snapshotUrl(row.snapshot), vm.cleanUrl(row.clean), { vm.overlay = Overlay.Delete(row.id) }, close)
        }
        Overlay.ClearArea -> GgSheet(close) {
            SheetTitle("Clear monitored area?")
            SheetText("The whole frame will be monitored and counted.")
            Acts {
                GgButton("Cancel", close, Modifier.weight(1f))
                GgButton("Clear", vm::clearArea, Modifier.weight(1f), icon = GgIcons.Eraser, kind = BtnKind.DANGER)
            }
        }
        Overlay.Battery -> GgSheet(close) {
            SheetTitle("Keep alerts working")
            SheetText("Android puts apps to sleep to save battery, which can delay or block alerts. Allow Garbage-Guard to keep running in the background.")
            SheetText("If no prompt appears: Settings, Apps, Garbage-Guard, Battery, Unrestricted.", mutedSmall = true)
            Acts {
                GgButton("Later", close, Modifier.weight(1f))
                GgButton("Allow", onFixBattery, Modifier.weight(1f), icon = GgIcons.Battery, kind = BtnKind.TEAL)
            }
        }
        Overlay.NotifPrompt -> GgSheet(close) {
            Ico(GgIcons.Bell, Modifier.align(Alignment.CenterHorizontally), size = 34.dp, tint = Gg.colors.teal)
            SheetTitle("Get alerts when the app is closed", center = true)
            SheetText(
                "Garbage-Guard sends a notification when the accumulation limit is reached, with a snapshot and a Mute button.",
                center = true,
            )
            Acts {
                GgButton("Not now", close, Modifier.weight(1f))
                GgButton("Allow alerts", onAllowAlerts, Modifier.weight(1f), kind = BtnKind.TEAL)
            }
        }
    }
}

/**
 * The Firebase settings page, the phone's twin of the Pi's Network, Firebase
 * screen: four fields, Test, Test and save, and Restore working.
 */
@Composable
private fun FirebaseSheet(vm: AppViewModel, close: () -> Unit) {
    val c = Gg.colors
    GgSheet(close) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Ico(GgIcons.Cloud, size = 18.dp, tint = c.cloud)
            T("FIREBASE", Modifier.weight(1f), size = 1.02f, weight = FontWeight.Black, color = c.text, spacing = 0.03f)
            Tag(if (vm.cloudReady) "Ready" else "Not set up", if (vm.cloudReady) Tone.OK else Tone.WARN)
        }
        Column(
            Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()).padding(end = 4.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FirebaseField(GgIcons.Link, "Database URL", vm.fbUrl, { vm.fbUrl = it }, "https://\u2026firebasedatabase.app", KeyboardType.Uri)
            FirebaseField(GgIcons.Key, "Web API key", vm.fbKey, { vm.fbKey = it }, "AIza\u2026", KeyboardType.Ascii)
            FirebaseField(GgIcons.Mail, "Email", vm.fbEmail, { vm.fbEmail = it }, "phone account", KeyboardType.Email)
            FirebaseField(GgIcons.Shield, "Password", vm.fbPassword, { vm.fbPassword = it }, "at least 6 characters", KeyboardType.Password, secret = true)
        }
        if (vm.fbMessage.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .neuRaised(c, 12.dp, hard = 2.dp, fillColor = if (vm.fbBusy) c.tealTint else if (vm.fbPassed) c.okTint else c.badTint)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Ico(
                    if (vm.fbBusy) GgIcons.Refresh else if (vm.fbPassed) GgIcons.Ok else GgIcons.Warn,
                    if (vm.fbBusy) Modifier.spin() else Modifier,
                    size = 16.dp, tint = if (vm.fbBusy) c.teal else if (vm.fbPassed) c.onlineText else c.bad,
                )
                T(vm.fbMessage, Modifier.weight(1f), size = 0.82f, weight = FontWeight.Bold, color = c.text, lineHeight = 1.3f)
            }
        }
        Acts {
            GgButton("Test", { vm.testFirebase(save = false) }, Modifier.weight(1f), icon = GgIcons.Signal, enabled = !vm.fbBusy)
            GgButton("Test and save", { vm.testFirebase(save = true) }, Modifier.weight(1f), icon = GgIcons.Save, kind = BtnKind.TEAL, enabled = !vm.fbBusy)
        }
        Acts {
            GgButton("Restore working", vm::restoreFirebase, Modifier.weight(1f), icon = GgIcons.Undo, enabled = !vm.fbBusy)
            GgButton("Close", close, Modifier.weight(1f))
        }
        Hint("The password stays encrypted on this phone.", icon = GgIcons.Shield)
    }
}

@Composable
private fun FirebaseField(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    keyboard: KeyboardType,
    secret: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Ico(icon, size = 13.dp, tint = accentOf(icon))
            T(label.uppercase(), size = 0.72f, spacing = 0.05f, color = Gg.colors.text)
        }
        GgInput(value, onChange, placeholder, keyboard = keyboard, secret = secret)
    }
}

private fun duration(seconds: Int): String = when {
    seconds >= 3600 -> "${seconds / 3600} h ${(seconds % 3600) / 60} m"
    seconds >= 60 -> "${seconds / 60} m ${seconds % 60} s"
    else -> "$seconds s"
}

/** The Pi's connected-phones panel: every phone and browser watching right now. */
@Composable
private fun ViewersSheet(viewers: List<Viewer>, myId: String, close: () -> Unit) {
    val c = Gg.colors
    GgSheet(close) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Ico(GgIcons.Phone, size = 18.dp, tint = c.text)
            T("WATCHING NOW", Modifier.weight(1f), size = 1.02f, weight = FontWeight.Black, color = c.text, spacing = 0.03f)
            Tag("${viewers.size} connected", if (viewers.isEmpty()) Tone.WARN else Tone.OK)
        }
        if (viewers.isEmpty()) {
            SheetText("The Pi lists nobody at the moment. A phone appears here within a second or two of connecting.")
        } else {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(end = 4.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                viewers.forEach { v ->
                    val app = v.kind == "app"
                    val mine = v.id.isNotEmpty() && v.id == myId
                    Column(
                        Modifier.fillMaxWidth().neuRaised(c, 14.dp, edge = if (mine) c.teal else c.edge).padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Ico(if (app) GgIcons.Phone else GgIcons.Eye, size = 16.dp, tint = c.teal)
                            T(
                                v.device.ifEmpty { if (app) "Phone" else "Browser" }.uppercase(), Modifier.weight(1f),
                                size = 0.84f, weight = FontWeight.Black, color = c.text, spacing = 0.04f, maxLines = 1,
                            )
                            if (mine) Tag("This phone", Tone.OK)
                            if (v.mode.isNotEmpty()) Tag(if (v.mode == "background") "In background" else "Open", if (v.mode == "background") Tone.WARN else Tone.OK)
                        }
                        val lines = buildList {
                            add("Type" to if (app) "Garbage-Guard app" + (if (v.appVersion.isNotEmpty()) " ${v.appVersion}" else "") else v.browser.ifEmpty { "Browser" })
                            if (v.os.isNotEmpty()) add("System" to v.os)
                            if (v.ip.isNotEmpty()) add("Address" to v.ip + if (v.hostname.isNotEmpty()) " (${v.hostname})" else "")
                            if (v.mac.isNotEmpty()) add((if (v.via == "shared") "Router MAC" else "MAC") to v.mac)
                            add("Connected" to duration(v.connectedSeconds))
                            when (v.via) {
                                "direct" -> add("Link" to "Direct")
                                "shared" -> add("Link" to "Shared address")
                                "remote" -> add("Link" to "Remote")
                                "cloud" -> add("Link" to "Firebase")
                            }
                        }
                        lines.forEach { (label, value) ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                T(label.uppercase(), Modifier.width(86.dp), size = 0.62f, color = c.muted, spacing = 0.06f, fit = true)
                                T(value, Modifier.weight(1f), size = 0.78f, weight = FontWeight.Bold, color = c.text, tnum = true, fit = true)
                            }
                        }
                        // The dashboard's note, so a router's address is not mistaken for the phone's own.
                        if (v.via == "shared") Hint("Behind a shared router, so the address is the router's.", icon = GgIcons.Signal)
                        if (v.via == "remote") Hint("Reaching the Pi from outside its network.", icon = GgIcons.Signal)
                        if (v.via == "cloud") Hint("Through Firebase, so there is no address or MAC.", icon = GgIcons.Cloud)
                    }
                }
            }
        }
        GgButton("Close", close, Modifier.fillMaxWidth())
    }
}

/** The Pi's network screen, read-only: changing addresses stays on the Pi's own screen. */
@Composable
private fun NetworkSheet(vm: AppViewModel, close: () -> Unit) {
    val c = Gg.colors
    val net = vm.net
    GgSheet(close) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Ico(GgIcons.Signal, size = 18.dp, tint = c.text)
            T("PI NETWORK", Modifier.weight(1f), size = 1.02f, weight = FontWeight.Black, color = c.text, spacing = 0.03f)
            if (net != null) Tag(if (net.internet) "Internet" else "No internet", if (net.internet) Tone.OK else Tone.WARN)
            GgButton(null, vm::loadNetwork, icon = GgIcons.Refresh, small = true, square = true, enabled = !vm.netLoading)
        }
        when {
            net == null && vm.netLoading -> repeat(2) { Skeleton(74.dp) }
            net == null -> SheetText("The Pi did not send network details. It may be out of reach, or running software from before this feature.")
            net.interfaces.isEmpty() -> SheetText("The Pi reported no Wi-Fi or cable connections.")
            else -> Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(end = 4.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                net.interfaces.forEach { NetCard(it) }
                if (net.cameraIp.isNotEmpty()) Hint("Camera address ${net.cameraIp}.", icon = GgIcons.Cam)
            }
        }
        Hint("Addresses can only be changed on the Pi's own screen.", icon = GgIcons.Ok)
        GgButton("Close", close, Modifier.fillMaxWidth())
    }
}

@Composable
private fun NetCard(n: NetInterface) {
    val c = Gg.colors
    val up = n.state.startsWith("connected")
    val (icon, title) = when (n.role) {
        "wifi" -> GgIcons.Signal to "Wi-Fi"
        "camera" -> GgIcons.Cam to "Camera cable"
        else -> GgIcons.Link to "Internet cable"
    }
    Column(
        Modifier.fillMaxWidth().neuRaised(c, 14.dp).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Ico(icon, size = 16.dp, tint = if (up) c.teal else c.muted)
            T(title.uppercase(), Modifier.weight(1f), size = 0.84f, weight = FontWeight.Black, color = c.text, spacing = 0.04f, maxLines = 1)
            if (n.isDefault) Tag("Internet", Tone.OK)
            Tag(if (up) "Connected" else n.state.ifEmpty { "Off" }, if (up) Tone.OK else Tone.WARN)
        }
        val lines = buildList {
            if (n.role == "wifi" && n.ssid.isNotEmpty()) {
                add("Network" to n.ssid + (n.signal?.let { ", signal $it%" } ?: ""))
                val band = listOf(n.frequency, n.channel.takeIf { it.isNotEmpty() }?.let { "channel $it" }, n.rate, n.security)
                    .filter { !it.isNullOrEmpty() }.joinToString(", ")
                if (band.isNotEmpty()) add("Link" to band)
            } else if (n.connection.isNotEmpty()) add("Profile" to n.connection)
            if (n.ips.isNotEmpty()) add("Address" to n.ips.joinToString(", ") + if (n.method.isNotEmpty()) " (${if (n.method == "manual") "fixed" else "automatic"})" else "")
            if (n.gateway.isNotEmpty()) add("Gateway" to n.gateway)
            if (n.dns.isNotEmpty()) add("DNS" to n.dns.joinToString(", "))
            if (n.speed.isNotEmpty()) add("Speed" to n.speed)
            if (n.mac.isNotEmpty()) add("MAC" to "${n.mac} (${n.device})")
        }
        lines.forEach { (label, value) ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                T(label.uppercase(), Modifier.width(64.dp), size = 0.62f, color = c.muted, spacing = 0.06f, fit = true)
                T(value, Modifier.weight(1f), size = 0.78f, weight = FontWeight.Bold, color = c.text, tnum = true, fit = true)
            }
        }
    }
}

/** Everything the touchscreen's battery panel shows, laid out for a phone. */
@Composable
private fun PiBatterySheet(b: Battery, close: () -> Unit) {
    val c = Gg.colors
    val level = batteryLevel(b)
    GgSheet(close) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Ico(if (b.ok && b.onAc) GgIcons.Plug else GgIcons.Battery, size = 18.dp, tint = c.text)
            T("PI BATTERY", Modifier.weight(1f), size = 1.02f, weight = FontWeight.Black, color = c.text, spacing = 0.03f)
            when {
                !b.ok -> Tag("No UPS", Tone.WARN)
                level == BatteryLevel.BAD && !b.charging -> Tag("Low", Tone.BAD)
                b.charging -> Tag("Charging", Tone.OK)
                b.onAc -> Tag("On AC", Tone.OK)
                else -> Tag("On battery", Tone.WARN)
            }
        }

        if (!b.ok) {
            SheetText("This Pi is not reporting a battery. The readout appears when the UPS HAT is attached and the Pi software is up to date.")
            GgButton("Close", close, Modifier.fillMaxWidth())
            return@GgSheet
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BatteryGlyph(b, width = 124.dp, height = 56.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    RollingText("${b.percent}", size = 2.6f, color = if (level == BatteryLevel.BAD) c.bad else c.text, spacing = -0.03f)
                    T("%", Modifier.padding(start = 2.dp, bottom = 6.dp), size = 1f, color = c.muted)
                }
                T(batterySource(b), size = 0.82f, color = c.text)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReadoutWell("VOLTAGE", "%.2f".format(b.voltage), "V", c.text, Modifier.weight(1f), GgIcons.Bolt)
            ReadoutWell("CURRENT", signed(b.current, 2), "A", flowColor(b), Modifier.weight(1f), GgIcons.Activity)
            ReadoutWell("POWER", signed(b.power, 1), "W", flowColor(b), Modifier.weight(1f), GgIcons.Plug)
        }

        if (b.cells.size == 4) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Ico(GgIcons.Layers, size = 12.dp, tint = c.bottle)
                    T("CELLS", size = 0.66f, color = c.muted, spacing = 0.07f)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    b.cells.forEachIndexed { i, v ->
                        Column(
                            Modifier.weight(1f).neuRaised(c, 10.dp, hard = 2.dp).padding(horizontal = 8.dp, vertical = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            T("${i + 1}", size = 0.58f, color = c.muted)
                            T("%.2f V".format(v), size = 0.78f, weight = FontWeight.Black, color = c.text, tnum = true, maxLines = 1)
                        }
                    }
                }
            }
        }

        val input = if (b.onAc && b.inputV != null && b.inputW != null && b.inputV > 0.5f) {
            "Charger input %.1f V, %.1f W.".format(b.inputV, b.inputW)
        } else null
        val capacity = b.capacityMah?.let { "About %,d mAh remaining.".format(it) }
        listOfNotNull(input, capacity).takeIf { it.isNotEmpty() }?.let {
            Hint(it.joinToString(" "), icon = GgIcons.Battery)
        }
        Hint("Minus means powering the Pi. Plus means charging.", icon = GgIcons.Ok)
        GgButton("Close", close, Modifier.fillMaxWidth())
    }
}

@Composable
private fun MuteSheet(vm: AppViewModel, close: () -> Unit) {
    val c = Gg.colors
    val options = listOf(300 to "5 minutes", 900 to "15 minutes", 1800 to "30 minutes", 3600 to "1 hour", 0 to "Until I unmute")
    GgSheet(close) {
        SheetTitle("Mute alert")
        SheetText("Alerts stay in History. The video keeps counting.")
        Column {
            options.forEach { (seconds, label) ->
                val on = vm.muteChoice == seconds
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .drawBehind {
                            // border-bottom: 1px dashed shade
                            drawLine(
                                c.shade, Offset(0f, size.height), Offset(size.width, size.height),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                            )
                        }
                        .pressable(toggle = true) {
                            vm.chooseMuteLength(seconds)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.size(22.dp).neuWell(c, 11.dp), contentAlignment = Alignment.Center) {
                        if (on) Box(Modifier.size(10.dp).clip(CircleShape).background(c.teal))
                    }
                    T(label, color = c.text)
                }
            }
        }
        Acts {
            GgButton("Cancel", close, Modifier.weight(1f))
            GgButton("Mute", vm::mute, Modifier.weight(1f), icon = GgIcons.EyeOff, kind = BtnKind.INK)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeleteSheet(vm: AppViewModel, row: AlertRow?, range: Boolean, count: Int, close: () -> Unit) {
    val c = Gg.colors
    GgSheet(close) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Ico(GgIcons.Warn, size = 30.dp, tint = c.bad)
            SheetTextIn(
                when {
                    range -> "Delete ${vm.scopeWords(count)} and their snapshot images? This cannot be undone."
                    row != null -> "Delete this ${row.event.label.lowercase()} from ${row.timestamp}?"
                    else -> "Delete this record?"
                },
                Modifier.weight(1f),
            )
        }
        Acts {
            GgButton("Cancel", close, Modifier.weight(1f))
            GgButton("Delete", vm::confirmDelete, Modifier.weight(1f), icon = GgIcons.Trash, kind = BtnKind.DANGER)
        }
    }
}

@Composable
private fun SheetTextIn(text: String, modifier: Modifier) {
    T(text, modifier, size = 0.9f, weight = FontWeight.Normal, color = Gg.colors.text, lineHeight = 1.35f)
}

/** .viewer, the snapshot with its numbers. */
@Composable
private fun SnapshotViewer(row: AlertRow, imageUrl: String?, cleanUrl: String?, onDelete: () -> Unit, close: () -> Unit) {
    // Annotated is the snapshot as saved, boxes and outline drawn in; Clean is the camera picture alone.
    var clean by rememberSaveable(row.id) { mutableStateOf(false) }
    val image = rememberSnapshot(if (clean && cleanUrl != null) cleanUrl else imageUrl)
    val c = Gg.colors
    var fullSize by remember { mutableStateOf(false) }
    val view = LocalView.current
    BackHandler {
        Feel.play(view, Sfx.BACK)
        if (fullSize) fullSize = false else close()
    }

    if (fullSize) {
        // Full size means the whole screen, sideways, like the live feed.
        LandscapeImmersive()
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pressable(sound = Sfx.BACK) { fullSize = false },
            contentAlignment = Alignment.Center,
        ) { FeedCanvas(snapshotFrame(row).let { if (clean) it.copy(showBoxes = false) else it }, Modifier.fillMaxSize(), cover = false, image = image) }
        return
    }

    val limit = row.thresholdUsed
    val pct = if (limit > 0) Math.round(row.smoothedCount * 100f / limit) else 0
    val tone = when { pct >= 100 -> c.bad; pct >= 50 -> c.warn; else -> c.ok }
    val conf = row.peakConfidence?.let { "${Math.round(it * 100)}%" } ?: "–"

    val header: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EventPill(row.event)
            Box(Modifier.weight(1f)) {
                when {
                    cleanUrl != null -> Segmented(
                        listOf("Annotated", "Clean"), selected = if (clean) 1 else 0,
                        onSelect = { clean = it == 1 },
                    )
                    // Over Firebase the clean picture stays on the Pi; say so instead of offering it.
                    row.hasClean -> T(
                        "CLEAN COPY ON THE PI'S WI-FI", Modifier.fillMaxWidth(), size = 0.62f,
                        color = c.muted, align = TextAlign.Center, spacing = 0.04f, fit = true,
                    )
                }
            }
            GgButton("Close", close, kind = BtnKind.INK, small = true)
        }
    }
    // Every reading sits in one raised sheet, each figure in a shallow well,
    // so the page reads as one calm surface and still uses all the room it is given.
    val readings: @Composable (Modifier) -> Unit = { place ->
        Column(place.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                Modifier.fillMaxWidth().weight(1f).neuRaised(c, 16.dp, hard = 4.dp, soft = Soft.LG).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Ico(GgIcons.Layers, size = 14.dp, tint = accentOf(GgIcons.Layers))
                    T("ITEMS IN THE AREA", Modifier.weight(1f), size = 0.7f, color = c.muted, spacing = 0.06f, fit = true)
                    Ico(GgIcons.Gauge, size = 14.dp, tint = tone)
                    T("$pct% OF LIMIT", size = 0.7f, weight = FontWeight.Black, color = tone, tnum = true, spacing = 0.05f, maxLines = 1)
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    T("${row.smoothedCount}", size = 2.9f, weight = FontWeight.Black, color = c.text, tnum = true, lineHeight = 1f)
                    T("OF $limit ITEMS", Modifier.padding(bottom = 6.dp), size = 0.9f, color = c.muted, spacing = 0.04f, maxLines = 1)
                }
                Row(Modifier.fillMaxWidth().height(12.dp).neuWell(c, 6.dp)) {
                    val f = (pct / 100f).coerceIn(0f, 1f)
                    if (f > 0f) Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(tone))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCell("${row.nBottle}", "Bottle", GgIcons.Bottle, c.bottle, Modifier.weight(1f))
                    StatCell("${row.nBag}", "Bag", GgIcons.Bag, c.bag, Modifier.weight(1f))
                    StatCell("${row.nFoodContainer}", "Container", GgIcons.Container, c.container, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCell(conf, "Confidence", GgIcons.Target, accentOf(GgIcons.Target), Modifier.weight(1f))
                    StatCell(row.timestamp.drop(11).take(5), "Time", GgIcons.Clock, accentOf(GgIcons.Clock), Modifier.weight(1f))
                    StatCell(row.timestamp.take(10).drop(5), "Date", GgIcons.Calendar, accentOf(GgIcons.Calendar), Modifier.weight(1f))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GgButton("Delete", onDelete, Modifier.weight(1f), icon = GgIcons.Trash, kind = BtnKind.DANGER)
                GgButton("Full size", { fullSize = true }, Modifier.weight(1.4f), icon = GgIcons.Expand, kind = BtnKind.TEAL, sound = Sfx.OPEN)
            }
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .ggBackground(c)
            .blockTouches()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        val wide = maxWidth
        val tall = maxHeight
        if (wide > tall) {
            // A wide window, such as a tablet on its side: the picture takes
            // the full height and the readings stand in a column beside it.
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight().background(FEED_BLACK).pressable(sound = Sfx.OPEN) { fullSize = true }) {
                    FeedCanvas(snapshotFrame(row).let { if (clean) it.copy(showBoxes = false) else it }, Modifier.fillMaxSize(), cover = false, image = image)
                }
                Box(Modifier.fillMaxHeight().width(2.dp).background(c.edge))
                Column(Modifier.width(minOf(wide * 0.42f, 380.dp)).fillMaxHeight()) {
                    header()
                    readings(Modifier.weight(1f))
                }
            }
        } else {
            // The readings keep a fixed, comfortable height. Everything above
            // them is the viewing pane: the whole snapshot, centred on black,
            // as large as the width allows and never cropped.
            // On a tall phone the readings grow a little, so the black above and
            // below the picture stays a slim margin, not an empty band.
            val spare = tall - 60.dp - wide / feedAspect(image) - 28.dp
            val sheet = spare.coerceIn(minOf(tall * 0.4f, 330.dp), 440.dp)
            Column(Modifier.fillMaxSize()) {
                header()
                Box(Modifier.fillMaxWidth().height(2.dp).background(c.edge))
                Box(Modifier.fillMaxWidth().weight(1f).background(FEED_BLACK).pressable(sound = Sfx.OPEN) { fullSize = true }) {
                    FeedCanvas(snapshotFrame(row).let { if (clean) it.copy(showBoxes = false) else it }, Modifier.fillMaxSize(), cover = false, image = image)
                }
                Box(Modifier.fillMaxWidth().height(2.dp).background(c.edge))
                readings(Modifier.height(sheet))
            }
        }
    }
}

/** One reading on the snapshot sheet: its symbol and name above the figure. */
@Composable
private fun StatCell(value: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, modifier: Modifier) {
    val c = Gg.colors
    // Each figure rests in its own shallow well, sunk into the raised sheet.
    Column(modifier.neuWell(c, 11.dp).padding(horizontal = 9.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Ico(icon, size = 13.dp, tint = tint)
            T(label.uppercase(), Modifier.weight(1f), size = 0.6f, color = c.muted, spacing = 0.05f, fit = true)
        }
        T(value, Modifier.fillMaxWidth(), size = 1.55f, weight = FontWeight.Black, color = c.text, tnum = true, lineHeight = 1f, fit = true)
    }
}
