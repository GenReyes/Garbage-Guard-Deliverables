package com.garbageguard.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.garbageguard.app.ui.AppViewModel
import com.garbageguard.app.ui.SetupState
import com.garbageguard.app.ui.components.BrandLogo
import com.garbageguard.app.ui.components.BtnKind
import com.garbageguard.app.ui.components.GgButton
import com.garbageguard.app.ui.components.GgCard
import com.garbageguard.app.ui.components.GgIcons
import com.garbageguard.app.ui.components.GgInput
import com.garbageguard.app.ui.components.Hint
import com.garbageguard.app.ui.components.Ico
import com.garbageguard.app.ui.components.T
import com.garbageguard.app.ui.components.logoPainter
import com.garbageguard.app.ui.components.neuRaised
import com.garbageguard.app.ui.components.spin
import com.garbageguard.app.ui.theme.Gg

/** First launch: the badge, the address field and the connection test. */
@Composable
fun SetupScreen(vm: AppViewModel) {
    val c = Gg.colors
    BackHandler(enabled = vm.everConnected) { vm.cancelSetup() }
    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            GgButton(
                null, { vm.toggleTheme(c.isDark) }, icon = if (c.isDark) GgIcons.Sun else GgIcons.Moon,
                small = true, square = true,
            )
        }
        Column(
            Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BrandLogo(200.dp)
            Hint("Enter the address shown on the Pi to start.", center = true, size = 0.9f)
        }

        GgCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Ico(GgIcons.Link, size = 14.dp, tint = c.muted)
                    T("PI ADDRESS", size = 0.76f, spacing = 0.05f)
                }
                GgInput(
                    value = vm.address,
                    onChange = vm::editAddress,
                    placeholder = AppViewModel.DEFAULT_ADDRESS,
                    keyboard = KeyboardType.Uri,
                )
            }
            when (vm.setupState) {
                SetupState.OK -> {
                    StatusBar(GgIcons.Ok, "Connected to CAM 01. Latency ${vm.setupLatency} ms.", c.okTint)
                    GgButton("Continue", vm::finishSetup, Modifier.fillMaxWidth(), icon = GgIcons.Check, kind = BtnKind.TEAL)
                }
                SetupState.FAILED -> {
                    StatusBar(GgIcons.NoLink, vm.setupMessage, c.badTint, iconTint = c.bad)
                    GgButton("Try again", vm::testConnection, Modifier.fillMaxWidth(), icon = GgIcons.Refresh)
                }
                SetupState.TESTING -> StatusBar(GgIcons.Refresh, "Testing connection", c.tealTint, spinning = true)
                SetupState.EMPTY ->
                    GgButton("Test connection", vm::testConnection, Modifier.fillMaxWidth(), icon = GgIcons.Link, kind = BtnKind.TEAL)
            }
            Hint(
                "The phone and the Pi must be on the same Wi-Fi or hotspot. On a phone hotspot, the address changes each time.",
                icon = GgIcons.Signal,
            )
        }

        // Not in the mockup: lets the app be shown with no Pi nearby.
        GgButton("Try with demo data", vm::useDemo, Modifier.fillMaxWidth(), icon = GgIcons.Eye)
        Hint("Demo data is invented on the phone. Nothing is sent to or read from a Pi.", center = true, modifier = Modifier.fillMaxWidth())
    }
}

/** The .mute bar reused as a result strip. */
@Composable
private fun StatusBar(
    icon: ImageVector,
    text: String,
    fill: Color,
    iconTint: Color = Gg.colors.text,
    spinning: Boolean = false,
) {
    val c = Gg.colors
    Row(
        Modifier
            .fillMaxWidth()
            .neuRaised(c, 14.dp, fillColor = fill)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Ico(icon, if (spinning) Modifier.spin() else Modifier, size = 18.dp, tint = iconTint)
        T(text, Modifier.weight(1f), size = 0.88f, color = c.text)
    }
}
