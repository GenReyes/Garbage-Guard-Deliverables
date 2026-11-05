package com.garbageguard.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garbageguard.app.data.ConnState
import com.garbageguard.app.data.LiveState
import com.garbageguard.app.notify.Sfx
import com.garbageguard.app.ui.AppViewModel
import com.garbageguard.app.ui.AppViewModel.UploadPhase
import com.garbageguard.app.ui.components.BtnKind
import com.garbageguard.app.ui.components.GgButton
import com.garbageguard.app.ui.components.GgCard
import com.garbageguard.app.ui.components.GgIcons
import com.garbageguard.app.ui.components.GgInput
import com.garbageguard.app.ui.components.Hd
import com.garbageguard.app.ui.components.Hint
import com.garbageguard.app.ui.components.Ico
import com.garbageguard.app.ui.components.T
import com.garbageguard.app.ui.components.Tag
import com.garbageguard.app.ui.components.Tone
import com.garbageguard.app.ui.components.neuWell
import com.garbageguard.app.ui.components.pulse
import com.garbageguard.app.ui.theme.Gg

/**
 * Model: the version the Pi runs, and a new one to upload. The .zip of an
 * NCNN export folder is picked with the system file picker and sent raw to
 * POST /api/model/upload over the Pi's own network. Through Firebase the
 * card still shows the running model but cannot upload, since Firebase has
 * no room for a model. "Go back" returns to the model that ran before.
 */
@Composable
fun ModelCard(vm: AppViewModel, state: LiveState) {
    val m = state.model ?: return
    val c = Gg.colors
    val reachable = state.conn != ConnState.NO_PI
    val canUpload = vm.canUploadModel && reachable
    val sending = vm.uploadPhase == UploadPhase.SENDING
    val busy = sending || m.loading
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { vm.chooseModelFile(it) }

    GgCard {
        Hd(GgIcons.Target, "Model") {
            Tag(if (m.loading) "Loading" else m.label.uppercase(), if (m.note != null) Tone.WARN else Tone.OK)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            T(m.label, size = 2.1f, weight = FontWeight.Black, tnum = true, maxLines = 1, lineHeight = 1.05f)
            T(
                m.loadingLabel?.let { "NOW RUNNING. LOADING $it…" } ?: "NOW RUNNING",
                Modifier.weight(1f), size = 0.72f, color = c.muted, spacing = 0.04f, fit = true,
            )
        }
        if (m.note != null) Said(m.note, GgIcons.Warn, bad = true)
        if (m.prev != null && m.prev != m.name) {
            GgButton(
                "Go back to ${m.prevLabel ?: m.prev}", vm::goBackModel, Modifier.fillMaxWidth(),
                icon = GgIcons.Undo, enabled = canUpload && !busy, lock = AppViewModel.LOCK_MODEL, sound = Sfx.BACK,
            )
        }
        Field(GgIcons.Edit, "New version name") {
            GgInput(vm.modelVersionText, vm::changeModelVersion, "v10")
        }
        GgButton(
            "Choose model .zip",
            { pick.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
            Modifier.fillMaxWidth(), icon = GgIcons.Box, enabled = canUpload && !sending, sound = Sfx.OPEN,
        )
        Hint(
            if (vm.modelUri == null) "No model chosen"
            else vm.modelFileName + if (vm.modelFileSize >= 0) ", %.1f MB".format(vm.modelFileSize / 1048576f) else "",
            icon = GgIcons.Box,
        )
        when (vm.uploadPhase) {
            UploadPhase.SENDING -> {
                ProgressBar(vm.uploadPct / 100f, pulsing = false)
                Said(vm.uploadMessage, GgIcons.Up)
            }
            UploadPhase.LOADING -> {
                ProgressBar(1f, pulsing = true)
                Said(vm.uploadMessage, GgIcons.Refresh)
            }
            UploadPhase.DONE_OK -> Said(vm.uploadMessage, GgIcons.Check, good = true)
            UploadPhase.DONE_FAIL -> Said(vm.uploadMessage, GgIcons.Warn, bad = true)
            UploadPhase.IDLE -> if (vm.modelUri != null) vm.modelProblem(state)?.let { Said(it, GgIcons.Warn, bad = true) }
        }
        GgButton(
            "Upload", vm::uploadModel, Modifier.fillMaxWidth(), icon = GgIcons.Up, kind = BtnKind.TEAL,
            enabled = canUpload && !busy && vm.modelProblem(state) == null,
        )
        if (!vm.canUploadModel) {
            Hint("Connect to the Pi's Wi-Fi to upload a model.", icon = GgIcons.Signal, wrap = true)
        } else {
            Hint(
                "Export with yolo export format=ncnn imgsz=960 (the size v8 and v9 use), then zip the *_ncnn_model folder. Up to 64 MB.",
                icon = GgIcons.Ok, wrap = true,
            )
        }
    }
}

/** A line of the card's own news: plain, good or bad. */
@Composable
private fun Said(text: String, icon: ImageVector, good: Boolean = false, bad: Boolean = false) {
    val c = Gg.colors
    val tint = if (bad) c.bad else if (good) c.ok else c.muted
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Ico(icon, size = 12.dp, tint = tint)
        T(text, Modifier.weight(1f), size = 0.78f, weight = if (good || bad) FontWeight.ExtraBold else FontWeight.Normal,
            color = if (good || bad) tint else c.muted, lineHeight = 1.3f)
    }
}

/** The upload bar, in the same shallow well as the Firebase usage bar. */
@Composable
private fun ProgressBar(frac: Float, pulsing: Boolean) {
    val c = Gg.colors
    Row(Modifier.fillMaxWidth().height(12.dp).neuWell(c, 6.dp)) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(frac.coerceIn(0.02f, 1f))
                .pulse(pulsing)
                .background(c.teal),
        )
    }
}
