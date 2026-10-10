package com.garbageguard.app.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.garbageguard.app.data.AlertRow
import com.garbageguard.app.data.EventType
import com.garbageguard.app.ui.AppViewModel
import com.garbageguard.app.ui.HistFilter
import com.garbageguard.app.ui.Overlay
import com.garbageguard.app.ui.components.DEFAULT_ROI
import com.garbageguard.app.ui.components.FEED_BLACK
import com.garbageguard.app.ui.components.FeedCanvas
import com.garbageguard.app.ui.components.FeedFrame
import com.garbageguard.app.ui.components.GgButton
import com.garbageguard.app.ui.components.GgIcons
import com.garbageguard.app.ui.components.Hd
import com.garbageguard.app.ui.components.Hint
import com.garbageguard.app.ui.components.Ico
import com.garbageguard.app.ui.components.Skeleton
import com.garbageguard.app.ui.components.Soft
import com.garbageguard.app.ui.components.T
import com.garbageguard.app.ui.components.neuShadow
import com.garbageguard.app.ui.components.neuWell
import com.garbageguard.app.ui.components.rememberSnapshot
import com.garbageguard.app.ui.theme.Gg

fun eventIcon(e: EventType): ImageVector = when (e) {
    EventType.ACCUMULATION, EventType.UNMUTED -> GgIcons.Bell
    EventType.SUPPRESSED, EventType.MUTED, EventType.AUTO_MUTED -> GgIcons.EyeOff
    EventType.TEST -> GgIcons.Wrench
    EventType.MANUAL -> GgIcons.Shutter
}

/** A stored snapshot, drawn the way the mockup draws thumbnails. */
fun snapshotFrame(row: AlertRow) = FeedFrame(
    showBoxes = true,
    total = row.smoothedCount.coerceAtMost(12),
    bottle = row.nBottle, bag = row.nBag, container = row.nFoodContainer,
    roi = DEFAULT_ROI, outline = false,
)

@Composable
fun HistoryScreen(vm: AppViewModel, alerts: List<AlertRow>) {
    val c = Gg.colors
    val all = vm.visibleRows(alerts)
    val rows = all.take(vm.shown)
    val unread = alerts.count { !it.read }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Hd(GgIcons.Clock, "History", titleSize = 1.3f) {
            GgButton(null, vm::refreshHistory, icon = GgIcons.Refresh, small = true, square = true)
        }

        // .tools2
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(vm.filter.label, GgIcons.Filter, on = vm.filter != HistFilter.ALL) { vm.overlay = Overlay.Filter }
            FilterChip("Dates", GgIcons.Clock, on = false) { vm.overlay = Overlay.Filter }
            Spacer(Modifier.weight(1f))
            GgButton("Mark all read", vm::markAllRead, icon = GgIcons.Check, small = true, enabled = unread > 0)
        }

        when {
            vm.loading -> repeat(3) { Skeleton(64.dp) }
            all.isEmpty() -> Column(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Ico(GgIcons.Clock, size = 34.dp, tint = c.muted.copy(alpha = 0.5f))
                T(
                    "No events yet. Alerts, captures and tests appear here.",
                    weight = FontWeight.Bold, color = c.muted, align = TextAlign.Center,
                )
            }
            else -> {
                rows.forEach { row ->
                    androidx.compose.runtime.key(row.id) { HistoryCard(vm, row) }
                }
                T(
                    "Showing 1 to ${rows.size} of ${all.size}", Modifier.fillMaxWidth(),
                    size = 0.8f, weight = FontWeight.Bold, color = c.muted, align = TextAlign.Center,
                )
                if (rows.size < all.size) {
                    GgButton("Load older", { vm.shown += 6 }, Modifier.fillMaxWidth())
                }
                Hint(
                    "Tap a row to reveal Delete. Tap a thumbnail to open it.",
                    icon = GgIcons.Eye, modifier = Modifier.fillMaxWidth(), center = true,
                )
            }
        }
    }
}

/** .chipf */
@Composable
fun FilterChip(label: String, icon: ImageVector?, on: Boolean, onClick: () -> Unit) {
    val c = Gg.colors
    Row(
        Modifier
            .defaultMinSize(minHeight = 40.dp)
            .neuWell(c, 20.dp, if (on) c.tealTint else c.sunk)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) Ico(icon, size = 14.dp, tint = c.text)
        T(label, weight = FontWeight.Normal, color = c.text, maxLines = 1)
    }
}

/** .ev, the coloured event pill. */
@Composable
fun EventPill(event: EventType) {
    val c = Gg.colors
    val bg: Color
    val fg: Color
    when (event) {
        EventType.ACCUMULATION -> { bg = c.bad; fg = c.onBad }
        EventType.SUPPRESSED -> { bg = c.sunk; fg = c.muted }
        EventType.TEST -> { bg = c.warn; fg = Color(0xFF16130F) }
        EventType.MANUAL -> { bg = c.teal; fg = c.onTeal }
        else -> { bg = c.raised; fg = c.text }
    }
    Row(
        Modifier
            .neuShadow(c, 12.dp, 2.dp, Soft.NONE)
            .clip(CircleShape)
            .background(bg)
            .border(2.dp, c.edge, CircleShape)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Ico(eventIcon(event), size = 12.dp, tint = fg)
        T(event.label, size = 0.78f, color = fg, maxLines = 1)
    }
}

/** .hwrap, one history row with Delete hidden behind it. */
@Composable
private fun HistoryCard(vm: AppViewModel, row: AlertRow) {
    val c = Gg.colors
    val swiped = vm.swipedId == row.id
    val shift by animateDpAsState(if (swiped) (-96).dp else 0.dp, tween(200), label = "swipe")
    val shape = RoundedCornerShape(14.dp)
    val fade = if (row.read) 0.6f else 1f

    Box(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(shape)) {
        // .hact
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.End) {
            Column(
                Modifier
                    .width(110.dp)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(c.bad)
                    .border(2.dp, c.edge, shape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        vm.overlay = Overlay.Delete(row.id)
                    }
                    .padding(start = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
            ) {
                Ico(GgIcons.Trash, size = 16.dp, tint = c.onBad)
                T("Delete", size = 0.78f, weight = FontWeight.Black, color = c.onBad)
            }
        }
        // .hcard
        Row(
            Modifier
                .fillMaxWidth()
                .offset(x = shift)
                .clip(shape)
                .background(if (row.event == EventType.ACCUMULATION) c.badTint else c.raised)
                .border(2.dp, c.edge, shape)
                .pointerInput(row.id) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if (total < -40f) vm.swipedId = row.id
                            else if (total > 40f && vm.swipedId == row.id) vm.swipedId = null
                        },
                    ) { _, dx -> total += dx }
                }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    vm.swipedId = if (swiped) null else row.id
                }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // .dot
            Box(
                Modifier
                    .size(28.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        vm.toggleRead(row)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(16.dp)
                        .alpha(if (row.read) 0.5f else 1f)
                        .drawBehind {
                            if (!row.read) drawCircle(c.badTint, radius = size.minDimension / 2 + 3.dp.toPx())
                        }
                        .clip(CircleShape)
                        .background(if (row.read) Color.Transparent else c.bad)
                        .border(2.dp, c.edge, CircleShape),
                )
            }
            Column(Modifier.weight(1f).alpha(fade), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                EventPill(row.event)
                T(row.timestamp, size = 0.78f, weight = FontWeight.Bold, color = c.muted, tnum = true, maxLines = 1)
            }
            T(
                "${row.smoothedCount}", Modifier.widthIn(min = 30.dp).alpha(fade),
                size = 1.3f, weight = FontWeight.Black, color = c.text, align = TextAlign.End, tnum = true,
            )
            // .thumb
            val thumbShape = RoundedCornerShape(7.dp)
            Box(
                Modifier
                    .size(width = 54.dp, height = 40.dp)
                    .alpha(fade)
                    .clip(thumbShape)
                    .background(if (row.snapshot != null) FEED_BLACK else c.sunk)
                    .border(2.dp, c.edge, thumbShape)
                    .then(
                        if (row.snapshot != null) Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() }, indication = null,
                        ) { vm.overlay = Overlay.Viewer(row.id) } else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (row.snapshot != null) FeedCanvas(snapshotFrame(row), Modifier.fillMaxSize(), image = rememberSnapshot(vm.snapshotUrl(row.snapshot)))
                else T("No image", size = 0.6f, weight = FontWeight.Bold, color = c.muted, maxLines = 1)
            }
        }
    }
}
