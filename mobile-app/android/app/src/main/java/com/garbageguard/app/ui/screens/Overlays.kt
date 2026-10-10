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
fun OverlayHost(vm: AppViewModel, alerts: List<AlertRow>, onFixBattery: () -> Unit, onAllowAlerts: () -> Unit) {
    val close = { vm.overlay = null }
    when (val o = vm.overlay) {
        null -> {}
        Overlay.Mute -> MuteSheet(vm, close)
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
                FilterChip(f.label, icon = null, on = vm.filter == f) {
                    vm.filter = f
                    vm.shown = 6
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                T("FROM", size = 0.76f, spacing = 0.05f)
                GgInput(vm.dateFrom, { vm.dateFrom = it }, "yyyy-mm-dd")
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                T("TO", size = 0.76f, spacing = 0.05f)
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
            "${row.smoothedCount}" to "Items",
            "${row.nBottle}" to "Bottle",
            "${row.nBag}" to "Bag",
            "${row.nFoodContainer}" to "Container",
            conf to "Peak confidence",
            "${row.thresholdUsed}" to "Limit then",
        )
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            cells.chunked(3).forEach { line ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    line.forEach { (value, label) ->
                        Column(Modifier.weight(1f)) {
                            T(value, size = 1.3f, weight = FontWeight.Black, color = c.text, tnum = true)
                            T(label.uppercase(), size = 0.66f, color = c.muted, spacing = 0.05f)
                        }
                    }
                }
            }
        }
    }
}
