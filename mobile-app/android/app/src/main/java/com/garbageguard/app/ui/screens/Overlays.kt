package com.garbageguard.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import com.garbageguard.app.ui.components.T
import com.garbageguard.app.ui.components.ggBackground
import com.garbageguard.app.ui.components.neuWell
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
    onAllowAlerts: () -> Unit,
) {
    val close = { vm.overlay = null }
    when (val o = vm.overlay) {
        null -> {}
        Overlay.Mute -> MuteSheet(vm, close)
        Overlay.PiBattery -> PiBatterySheet(battery, close)
        Overlay.Network -> NetworkSheet(vm, close)
        Overlay.Viewers -> ViewersSheet(viewers, vm.clientId, close)
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
        Overlay.Filter -> FilterSheet(vm, close)
        is Overlay.Delete -> DeleteSheet(vm, alerts.firstOrNull { it.id == o.id }, o.id == null, close)
        is Overlay.Viewer -> {
            val row = alerts.firstOrNull { it.id == o.id }
            if (row == null) LaunchedEffect(o) { close() } else SnapshotViewer(row, vm.snapshotUrl(row.snapshot), close)
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
                            add("Address" to v.ip + if (v.hostname.isNotEmpty()) " (${v.hostname})" else "")
                            if (v.mac.isNotEmpty()) add("MAC" to v.mac)
                            add("Connected" to duration(v.connectedSeconds))
                        }
                        lines.forEach { (label, value) ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                T(label.uppercase(), Modifier.width(76.dp), size = 0.62f, color = c.muted, spacing = 0.06f, maxLines = 1)
                                T(value, Modifier.weight(1f), size = 0.78f, weight = FontWeight.Bold, color = c.text, tnum = true)
                            }
                        }
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
                T(label.uppercase(), Modifier.width(64.dp), size = 0.62f, color = c.muted, spacing = 0.06f, maxLines = 1)
                T(value, Modifier.weight(1f), size = 0.78f, weight = FontWeight.Bold, color = c.text, tnum = true)
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
        Hint("A minus sign means the pack is powering the Pi. A plus sign means it is charging.", icon = GgIcons.Ok)
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
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
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
private fun FilterSheet(vm: AppViewModel, close: () -> Unit) {
    GgSheet(close) {
        SheetTitle("Filter history")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HistFilter.entries.forEach { f ->
                val symbol = when (f) {
                    HistFilter.ALL -> GgIcons.Layers
                    HistFilter.ALERTS -> GgIcons.Bell
                    HistFilter.CAPTURES -> GgIcons.Shutter
                    HistFilter.TESTS -> GgIcons.Wrench
                    HistFilter.MUTED -> GgIcons.EyeOff
                }
                FilterChip(f.label, icon = symbol, on = vm.filter == f) {
                    vm.filter = f
                    vm.shown = 6
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Ico(GgIcons.Calendar, size = 13.dp, tint = Gg.colors.bag)
                    T("FROM", size = 0.76f, spacing = 0.05f)
                }
                GgInput(vm.dateFrom, { vm.dateFrom = it }, "yyyy-mm-dd")
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Ico(GgIcons.Calendar, size = 13.dp, tint = Gg.colors.bag)
                    T("TO", size = 0.76f, spacing = 0.05f)
                }
                GgInput(vm.dateTo, { vm.dateTo = it }, "yyyy-mm-dd")
            }
        }
        Acts {
            GgButton("Reset", { vm.filter = HistFilter.ALL }, Modifier.weight(1f))
            GgButton("Apply", close, Modifier.weight(1f), icon = GgIcons.Check, kind = BtnKind.INK)
        }
        GgButton(
            "Delete this range", { vm.overlay = Overlay.Delete(null) }, Modifier.fillMaxWidth(),
            icon = GgIcons.Trash, kind = BtnKind.DANGER,
        )
    }
}

@Composable
private fun DeleteSheet(vm: AppViewModel, row: AlertRow?, range: Boolean, close: () -> Unit) {
    val c = Gg.colors
    GgSheet(close) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Ico(GgIcons.Warn, size = 30.dp, tint = c.bad)
            SheetTextIn(
                when {
                    range -> "Delete all records in this range and their snapshot images? This cannot be undone."
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
private fun SnapshotViewer(row: AlertRow, imageUrl: String?, close: () -> Unit) {
    val image = rememberSnapshot(imageUrl)
    val c = Gg.colors
    var fullSize by remember { mutableStateOf(false) }
    BackHandler { if (fullSize) fullSize = false else close() }

    if (fullSize) {
        // Full size means the whole screen, sideways, like the live feed.
        LandscapeImmersive()
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { fullSize = false },
            contentAlignment = Alignment.Center,
        ) { FeedCanvas(snapshotFrame(row), Modifier.fillMaxSize(), cover = false, image = image) }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .ggBackground(c)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Ico(GgIcons.Shutter, size = 16.dp, tint = c.text)
            T("${row.event.label}, ${row.timestamp.drop(5)}", Modifier.weight(1f), weight = FontWeight.Black, color = c.text)
            GgButton("Full size", { fullSize = true }, icon = GgIcons.Eye, small = true)
            GgButton("Close", close, kind = BtnKind.INK, small = true)
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(c.edge))
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).background(FEED_BLACK)) {
            // A real snapshot is evidence, so it is fitted whole, not cropped.
            FeedCanvas(snapshotFrame(row), Modifier.fillMaxSize(), cover = image == null, image = image)
        }
        val conf = row.peakConfidence?.let { "${Math.round(it * 100)}%" } ?: "–"
        val cells = listOf(
            Triple("${row.smoothedCount}", "Items", GgIcons.Layers),
            Triple("${row.nBottle}", "Bottle", GgIcons.Bottle),
            Triple("${row.nBag}", "Bag", GgIcons.Bag),
            Triple("${row.nFoodContainer}", "Container", GgIcons.Container),
            Triple(conf, "Peak confidence", GgIcons.Target),
            Triple("${row.thresholdUsed}", "Limit then", GgIcons.Gauge),
        )
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            cells.chunked(3).forEach { line ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    line.forEach { (value, label, symbol) ->
                        Column(Modifier.weight(1f)) {
                            T(value, size = 1.3f, weight = FontWeight.Black, color = c.text, tnum = true)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Ico(symbol, size = 12.dp, tint = accentOf(symbol))
                                T(label.uppercase(), size = 0.66f, color = c.muted, spacing = 0.05f)
                            }
                        }
                    }
                }
            }
        }
    }
}
