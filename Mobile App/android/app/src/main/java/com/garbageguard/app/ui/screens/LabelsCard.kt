package com.garbageguard.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garbageguard.app.data.BoxLabels
import com.garbageguard.app.data.ConnState
import com.garbageguard.app.data.LiveState
import com.garbageguard.app.ui.AppViewModel
import com.garbageguard.app.ui.components.FEED_BLACK
import com.garbageguard.app.ui.components.GgCard
import com.garbageguard.app.ui.components.GgIcons
import com.garbageguard.app.ui.components.Hd
import com.garbageguard.app.ui.components.Hint
import com.garbageguard.app.ui.components.Ico
import com.garbageguard.app.ui.components.T
import com.garbageguard.app.ui.components.accentOf
import com.garbageguard.app.ui.components.neuWell
import com.garbageguard.app.ui.components.pressable
import com.garbageguard.app.ui.theme.Gg

/**
 * Box labels: what the Pi writes above each detection box. The Pi burns the
 * text into the frame, so this one setting changes the Pi's screen, the
 * dashboard, the app and every snapshot saved from now on. Shown only when
 * the Pi reports the setting.
 */
@Composable
fun BoxLabelsCard(vm: AppViewModel, state: LiveState) {
    val labels = state.labels ?: return
    val reachable = state.conn != ConnState.NO_PI
    val busy = vm.isWaiting(AppViewModel.LOCK_LABELS)
    GgCard {
        Hd(GgIcons.Box, "Box labels") {
            SendingTag(vm, AppViewModel.LOCK_LABELS, changed = false)
        }
        LabelCheck("Track ID", "#53, the number that follows one item", GgIcons.Target, labels.trackId, reachable && !busy) {
            vm.setLabels(labels.copy(trackId = !labels.trackId))
        }
        LabelCheck("Class name", "bottle, bag or food_container", GgIcons.Layers, labels.className, reachable && !busy) {
            vm.setLabels(labels.copy(className = !labels.className))
        }
        LabelCheck("Confidence", "How sure the model is, as a percentage", GgIcons.Gauge, labels.confidence, reachable && !busy) {
            vm.setLabels(labels.copy(confidence = !labels.confidence))
        }
        LabelPreview(labels)
        Hint(
            "The box colour already tells the class: blue bottle, purple bag, green container. Fewer words keep crowded boxes readable.",
            icon = GgIcons.Ok, wrap = true,
        )
    }
}

/** One choice: a square tick box in a shallow well, its name and a line under it. */
@Composable
private fun LabelCheck(label: String, sub: String, icon: ImageVector, on: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = Gg.colors
    val box = RoundedCornerShape(6.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .neuWell(c, 12.dp, if (on) c.tealTint else c.sunk)
            .pressable(toggle = true, enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(box)
                .background(if (on) c.teal else c.raised)
                .border(2.dp, c.edge, box),
            contentAlignment = Alignment.Center,
        ) { if (on) Ico(GgIcons.Check, size = 15.dp, tint = c.onTeal) }
        Ico(icon, size = 16.dp, tint = accentOf(icon))
        Column(Modifier.weight(1f)) {
            T(label.uppercase(), size = 0.82f, spacing = 0.04f, fit = true, color = if (enabled) c.text else c.muted)
            T(sub, size = 0.74f, weight = FontWeight.Bold, color = c.muted, fit = true)
        }
    }
}

/** How a box will read on the black screen, drawn the way the Pi draws it. */
@Composable
private fun LabelPreview(labels: BoxLabels) {
    val c = Gg.colors
    val green = Color(0xFF3FD06E)
    val text = labels.sample()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(FEED_BLACK)
            .border(2.dp, c.edge, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        T("PREVIEW", size = 0.6f, weight = FontWeight.Black, color = Color(0xFFB9B2A6), spacing = 0.06f, maxLines = 1)
        Column {
            if (text.isNotEmpty()) T(
                text, Modifier.background(green).padding(horizontal = 4.dp, vertical = 1.dp),
                size = 0.74f, weight = FontWeight.Black, color = Color(0xFF0B0A09), maxLines = 1,
            )
            Box(Modifier.size(width = 64.dp, height = 30.dp).border(2.dp, green))
        }
        if (text.isEmpty()) T("Outline only", Modifier.weight(1f), size = 0.74f, weight = FontWeight.Bold, color = Color(0xFFB9B2A6), fit = true)
    }
}
