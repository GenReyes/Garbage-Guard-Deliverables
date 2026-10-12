package com.garbageguard.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.garbageguard.app.data.AlertRow
import com.garbageguard.app.ui.AppViewModel
import com.garbageguard.app.ui.HistFilter
import com.garbageguard.app.ui.Overlay
import com.garbageguard.app.ui.components.Acts
import com.garbageguard.app.ui.components.BtnKind
import com.garbageguard.app.ui.components.GgButton
import com.garbageguard.app.ui.components.GgIcons
import com.garbageguard.app.ui.components.GgSheet
import com.garbageguard.app.ui.components.Hint
import com.garbageguard.app.ui.components.Ico
import com.garbageguard.app.ui.components.Segmented
import com.garbageguard.app.ui.components.SheetTitle
import com.garbageguard.app.ui.components.T
import com.garbageguard.app.ui.components.accentOf
import com.garbageguard.app.ui.components.neuRaised
import com.garbageguard.app.ui.components.neuWell
import com.garbageguard.app.ui.components.pressable
import com.garbageguard.app.ui.theme.Gg
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** The symbol for each kind of record the History page can be narrowed to. */
fun filterIcon(f: HistFilter): ImageVector = when (f) {
    HistFilter.ALL -> GgIcons.Layers
    HistFilter.ALERTS -> GgIcons.Bell
    HistFilter.CAPTURES -> GgIcons.Shutter
    HistFilter.TESTS -> GgIcons.Wrench
    HistFilter.MUTED -> GgIcons.EyeOff
    HistFilter.POWER -> GgIcons.Power
}

/**
 * The History page's tools. One bar of symbols picks the kind of record,
 * all the same size on one line. Under it sit the date range and the two
 * actions. Mark read and Delete act on exactly the records the filter and
 * the dates leave on the page, and the line beneath says what that is.
 */
@Composable
fun HistoryTools(vm: AppViewModel, visible: List<AlertRow>) {
    val c = Gg.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // The same bar as Appearance and Screen saver, with symbols for words:
        // the kind in use is the raised key, the others stay sunk in the tray.
        Segmented(
            options = HistFilter.entries.map { "" },
            selected = vm.filter.ordinal,
            icons = HistFilter.entries.map { filterIcon(it) },
            onSelect = { vm.chooseFilter(HistFilter.entries[it]) },
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 40.dp)
                    .neuWell(c, 20.dp, if (vm.rangeOn) c.tealTint else c.sunk)
                    .pressable { vm.overlay = Overlay.Filter }
                    .padding(horizontal = 11.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Ico(GgIcons.Calendar, size = 14.dp, tint = accentOf(GgIcons.Calendar))
                T(vm.rangeLabel().uppercase(), Modifier.weight(1f), size = 0.74f, color = c.text, spacing = 0.04f, fit = true)
            }
            GgButton("Mark read", vm::markVisibleRead, icon = GgIcons.Check, small = true, enabled = visible.any { !it.read })
            GgButton("Delete", { vm.overlay = Overlay.Delete(null) }, icon = GgIcons.Trash, kind = BtnKind.DANGER, small = true, enabled = visible.isNotEmpty())
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Ico(filterIcon(vm.filter), size = 12.dp, tint = accentOf(filterIcon(vm.filter)))
            T(
                "SHOWING ${vm.scopeWords(visible.size).uppercase()}", Modifier.weight(1f),
                size = 0.68f, weight = FontWeight.Bold, color = c.muted, spacing = 0.04f, fit = true,
            )
        }
    }
}

/**
 * The date range sheet. Dates are picked from a calendar, never typed, so a
 * day that does not exist cannot be entered, and the view model keeps From
 * on or before To.
 */
@Composable
fun DateSheet(vm: AppViewModel, close: () -> Unit) {
    // Null shows the range; true picks the From day, false picks the To day.
    var picking by remember { mutableStateOf<Boolean?>(null) }
    GgSheet(close) {
        val side = picking
        if (side != null) {
            SheetTitle(if (side) "From which day?" else "Up to which day?")
            MonthCalendar(
                selected = if (side) vm.dateFrom else vm.dateTo,
                from = vm.dateFrom, to = vm.dateTo,
                onPick = {
                    if (side) vm.pickFrom(it) else vm.pickTo(it)
                    picking = null
                },
            )
            Acts {
                GgButton("Back", { picking = null }, Modifier.weight(1f), icon = GgIcons.Prev)
                GgButton(
                    "No limit",
                    {
                        if (side) vm.pickFrom(null) else vm.pickTo(null)
                        picking = null
                    },
                    Modifier.weight(1f), icon = GgIcons.Eraser,
                )
            }
        } else {
            SheetTitle("Date range")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateWell("From", vm.dateFrom, Modifier.weight(1f)) { picking = true }
                DateWell("To", vm.dateTo, Modifier.weight(1f)) { picking = false }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                QuickPick("Today", Modifier.weight(1f)) { vm.lastDays(1) }
                QuickPick("7 days", Modifier.weight(1f)) { vm.lastDays(7) }
                QuickPick("30 days", Modifier.weight(1f)) { vm.lastDays(30) }
                QuickPick("Any", Modifier.weight(1f)) { vm.clearDates() }
            }
            Hint("Mark read and Delete act on the records shown.", icon = GgIcons.Ok)
            Acts {
                GgButton("Clear", vm::clearDates, Modifier.weight(1f), icon = GgIcons.Eraser, enabled = vm.rangeOn)
                GgButton("Done", close, Modifier.weight(1f), icon = GgIcons.Check, kind = BtnKind.INK)
            }
        }
    }
}

/** One end of the range: its name, and the day in a well that opens the calendar. */
@Composable
private fun DateWell(label: String, day: LocalDate?, modifier: Modifier, onClick: () -> Unit) {
    val c = Gg.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Ico(GgIcons.Calendar, size = 13.dp, tint = c.bag)
            T(label.uppercase(), size = 0.76f, spacing = 0.05f)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .neuWell(c, 12.dp, if (day != null) c.tealTint else c.sunk)
                .pressable(onClick = onClick)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            T(
                day?.let { vm_day(it) } ?: "Any day", Modifier.weight(1f), size = 1.02f,
                color = if (day != null) c.text else c.muted, tnum = true, fit = true,
            )
            Ico(GgIcons.Next, size = 14.dp, tint = c.muted)
        }
    }
}

/** "12 Oct 2026". */
private fun vm_day(d: LocalDate): String =
    "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${d.year}"

@Composable
private fun QuickPick(label: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Gg.colors
    Box(
        modifier.height(38.dp).neuRaised(c, 12.dp, hard = 2.dp).pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { T(label.uppercase(), size = 0.7f, color = c.text, spacing = 0.04f, maxLines = 1) }
}

/**
 * A month to pick a day from. It is built from the real calendar, so every
 * month has its right number of days, leap years included, and a day in the
 * future cannot be chosen because no record can carry one.
 */
@Composable
private fun MonthCalendar(selected: LocalDate?, from: LocalDate?, to: LocalDate?, onPick: (LocalDate) -> Unit) {
    val c = Gg.colors
    val today = remember { LocalDate.now() }
    val thisMonth = remember { YearMonth.from(today) }
    var month by remember { mutableStateOf(YearMonth.from(selected ?: today)) }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        GgButton(null, { month = month.minusYears(1) }, icon = GgIcons.PrevYear, small = true, square = true)
        GgButton(null, { month = month.minusMonths(1) }, icon = GgIcons.Prev, small = true, square = true)
        T(
            "${month.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase()} ${month.year}", Modifier.weight(1f),
            size = 1.05f, weight = FontWeight.Black, color = c.text, align = TextAlign.Center, tnum = true, spacing = 0.04f, fit = true,
        )
        GgButton(null, { month = month.plusMonths(1) }, icon = GgIcons.Next, small = true, square = true, enabled = month < thisMonth)
        GgButton(
            null, { month = minOf(month.plusYears(1), thisMonth) }, icon = GgIcons.NextYear,
            small = true, square = true, enabled = month < thisMonth,
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN").forEach {
            T(it, Modifier.weight(1f), size = 0.6f, color = c.muted, align = TextAlign.Center, spacing = 0.04f, maxLines = 1)
        }
    }
    val lead = month.atDay(1).dayOfWeek.value - 1 // Monday first
    val days = month.lengthOfMonth()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (week in 0 until 6) {
            if (week * 7 - lead + 1 > days) break
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (col in 0 until 7) {
                    val n = week * 7 + col - lead + 1
                    if (n !in 1..days) {
                        Spacer(Modifier.weight(1f))
                        continue
                    }
                    val day = month.atDay(n)
                    val future = day.isAfter(today)
                    val picked = day == selected
                    val within = from != null && to != null && !day.isBefore(from) && !day.isAfter(to)
                    val shape = RoundedCornerShape(10.dp)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(38.dp)
                            .alpha(if (future) 0.3f else 1f)
                            .then(
                                when {
                                    picked -> Modifier.neuRaised(c, 10.dp, hard = 2.dp, fillColor = c.teal)
                                    within -> Modifier.neuWell(c, 10.dp, c.tealTint)
                                    day == today -> Modifier.border(2.dp, c.teal, shape)
                                    else -> Modifier
                                }
                            )
                            .pressable(enabled = !future) { onPick(day) },
                        contentAlignment = Alignment.Center,
                    ) {
                        T("$n", size = 0.92f, weight = FontWeight.Black, color = if (picked) c.onTeal else c.text, tnum = true)
                    }
                }
            }
        }
    }
}
