package com.garbageguard.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The line icons from the mockup's SVG sprite, redrawn as vectors with the
 * same path data so the app and the mockup share one icon set.
 */
private class IconBuilder(private val b: ImageVector.Builder, private val sw: Float) {
    private val ink = SolidColor(Color.Black)

    fun p(d: String) {
        b.addPath(
            pathData = addPathNodes(d), fill = null, stroke = ink, strokeLineWidth = sw,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        )
    }

    fun fill(d: String) {
        b.addPath(pathData = addPathNodes(d), fill = ink)
    }

    private fun circlePath(cx: Float, cy: Float, r: Float) =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

    fun circle(cx: Float, cy: Float, r: Float) = p(circlePath(cx, cy, r))

    fun dot(cx: Float, cy: Float, r: Float) = fill(circlePath(cx, cy, r))

    fun rect(x: Float, y: Float, w: Float, h: Float, rx: Float = 0f) {
        if (rx <= 0f) {
            p("M$x ${y}h${w}v${h}h${-w}z")
        } else {
            val iw = w - 2 * rx
            val ih = h - 2 * rx
            p(
                "M${x + rx} ${y}h${iw}a$rx $rx 0 0 1 $rx ${rx}v${ih}a$rx $rx 0 0 1 ${-rx} ${rx}" +
                    "h${-iw}a$rx $rx 0 0 1 ${-rx} ${-rx}v${-ih}a$rx $rx 0 0 1 $rx ${-rx}z"
            )
        }
    }
}

private fun icon(name: String, sw: Float = 2f, block: IconBuilder.() -> Unit): ImageVector {
    val b = ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    )
    IconBuilder(b, sw).block()
    return b.build()
}

object GgIcons {
    val Shield by lazy {
        icon("shield") {
            p("M12 2.6 20 6v6c0 4.6-3.3 8.4-8 9.4C7.3 20.4 4 16.6 4 12V6z")
            p("M7.6 12.6c1.3-1 2.2-1 3.4 0s2.1 1 3.4 0 2.2-1 3.4 0")
        }
    }
    val Cam by lazy {
        icon("cam") {
            rect(2.5f, 6f, 19f, 13f, 3f)
            p("M8.5 6l1.4-2.3h4.2L15.5 6")
            circle(12f, 12.6f, 3.4f)
        }
    }
    val Gear by lazy {
        icon("gear") {
            circle(12f, 12f, 3.1f)
            p("M12 2.6v2.6M12 18.8v2.6M21.4 12h-2.6M5.2 12H2.6M18.6 5.4l-1.8 1.8M7.2 16.8l-1.8 1.8M18.6 18.6l-1.8-1.8M7.2 7.2 5.4 5.4")
        }
    }
    val Poly by lazy { icon("poly") { p("M4 8L13 4L20 10L17 19L7 18z") } }
    val Clock by lazy {
        icon("clock") {
            circle(12f, 12f, 8.6f)
            p("M12 7.2V12l3.4 2.1")
        }
    }
    val Sun by lazy {
        icon("sun") {
            circle(12f, 12f, 4f)
            p("M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.3 5.3l2 2M16.7 16.7l2 2M18.7 5.3l-2 2M7.3 16.7l-2 2")
        }
    }
    val Moon by lazy { icon("moon") { p("M20 14.5A8.5 8.5 0 1 1 9.5 4a6.8 6.8 0 0 0 10.5 10.5z") } }
    val Signal by lazy {
        icon("signal") {
            p("M4.4 15.2a10.6 10.6 0 0 1 15.2 0")
            p("M7.8 18.3a6 6 0 0 1 8.4 0")
            dot(12f, 20.6f, 1.8f)
        }
    }
    val NoLink by lazy {
        icon("nolink") {
            p("M9.6 14.4 7.4 16.6a3.6 3.6 0 0 1-5.1-5.1l2.4-2.4")
            p("M14.4 9.6l2.2-2.2a3.6 3.6 0 0 1 5.1 5.1l-2.4 2.4")
            p("M3.2 3.2l17.6 17.6")
        }
    }
    val Bell by lazy {
        icon("bell") {
            p("M18 9.4a6 6 0 1 0-12 0c0 6.1-2.3 7.4-2.3 7.4h16.6S18 15.5 18 9.4")
            p("M13.7 20.4a2 2 0 0 1-3.4 0")
        }
    }
    val Check by lazy { icon("check") { p("M4.4 12.6 9.6 18 19.8 6.6") } }
    val Save by lazy {
        icon("save") {
            p("M4.5 4.5h12L19.5 7.5v12h-15z")
            p("M8 4.5v5h7v-5")
            rect(8f, 13f, 7f, 6.5f)
        }
    }
    val Shutter by lazy {
        icon("shutter") {
            rect(2.5f, 6.5f, 19f, 13.5f, 3f)
            p("M8.6 6.5 10 3.9h4l1.4 2.6")
            circle(12f, 13.2f, 3.6f)
        }
    }
    val Pen by lazy {
        icon("pen") {
            p("M3.5 20.5l1-4.4L15.6 5a2.3 2.3 0 0 1 3.3 3.3L7.9 19.5z")
            p("M14 6.6l3.4 3.4")
        }
    }
    val Edit by lazy {
        icon("edit") {
            p("M12.6 4.6H4.4v15h15v-8.2")
            p("M17.4 3.2 20.8 6.6 12 15.4l-4 .6.6-4z")
        }
    }
    val Eraser by lazy {
        icon("eraser") {
            p("M8.2 20.4H20")
            p("M15.4 3.8 20.2 8.6a1.8 1.8 0 0 1 0 2.6l-8 8H6.6l-3-3a1.8 1.8 0 0 1 0-2.6l9.2-9.8a1.8 1.8 0 0 1 2.6 0z")
        }
    }
    val Undo by lazy {
        icon("undo") {
            p("M9.6 6.4 4 12l5.6 5.6")
            p("M4 12h10.4a5.6 5.6 0 0 1 0 11.2")
        }
    }
    val Refresh by lazy {
        icon("refresh") {
            p("M20.4 12a8.4 8.4 0 1 1-2.5-6")
            p("M20.6 4.4v5.2h-5.2")
        }
    }
    val Trash by lazy {
        icon("trash") {
            p("M3.6 6.4h16.8")
            p("M9.2 6.4V4.2h5.6v2.2")
            p("M5.6 6.4h12.8l-1 14H6.6z")
            p("M10.2 10.4v6M13.8 10.4v6")
        }
    }
    val Warn by lazy {
        icon("warn") {
            p("M12 3.6 22 20.4H2z")
            p("M12 9.6v4.6")
            dot(12f, 17.4f, 1.2f)
        }
    }
    val Ok by lazy {
        icon("ok") {
            circle(12f, 12f, 8.6f)
            p("M8.2 12.4 11 15.2l5-5.6")
        }
    }
    val Up by lazy {
        icon("up") {
            p("M12 19V6")
            p("M6.4 11.6 12 6l5.6 5.6")
        }
    }
    val Eye by lazy {
        icon("eye") {
            p("M1.8 12S5.6 5.4 12 5.4 22.2 12 22.2 12 18.4 18.6 12 18.6 1.8 12 1.8 12")
            circle(12f, 12f, 3.1f)
        }
    }
    val EyeOff by lazy {
        icon("eyeoff") {
            p("M3 3l18 18")
            p("M10.6 6a9.6 9.6 0 0 1 11.6 6 12 12 0 0 1-2.6 3.5")
            p("M6.4 7.6A12.2 12.2 0 0 0 1.8 12S5.6 18.6 12 18.6a9.4 9.4 0 0 0 3.2-.55")
        }
    }
    val Box by lazy {
        icon("box") {
            p("M3.6 7.4 12 3.4l8.4 4v9.2L12 20.6l-8.4-4z")
            p("M3.6 7.4 12 11.5l8.4-4.1M12 11.5v9.1")
        }
    }
    val Wrench by lazy {
        icon("wrench") {
            p("M15.6 3.4a5.4 5.4 0 0 0-5.1 7.1L3.4 17.6l3 3 7.1-7.1a5.4 5.4 0 0 0 6.9-6.6l-3.1 3.1-2.9-.6-.6-2.9z")
        }
    }
    val X by lazy { icon("x", 2.2f) { p("M6 6l12 12M18 6L6 18") } }
    val Filter by lazy { icon("filter") { p("M3 5h18l-7 8v6l-4-2v-4z") } }
    val Battery by lazy {
        icon("battery") {
            rect(3f, 7f, 16f, 10f, 2f)
            p("M21 10.5v3")
            p("M7 10v4M11 10v4")
        }
    }
    val Expand by lazy { icon("expand", 2.2f) { p("M9.5 3.5H3.5v6M14.5 3.5h6v6M20.5 14.5v6h-6M3.5 14.5v6h6") } }
    val Shrink by lazy { icon("shrink", 2.2f) { p("M3.5 9.5h6v-6M20.5 9.5h-6v-6M14.5 20.5v-6h6M9.5 20.5v-6h-6") } }
    val Plug by lazy {
        icon("plug") {
            p("M9 3v4M15 3v4")
            p("M6.5 7h11v3.5a5.5 5.5 0 0 1-11 0z")
            p("M12 16v5")
        }
    }
    val Bolt by lazy { icon("bolt") { fill("M13.4 2.5 5 13.6h6l-1.4 7.9L19 10.3h-6.1z") } }
    val BoltEdge by lazy { icon("boltedge", 2.6f) { p("M13.4 2.5 5 13.6h6l-1.4 7.9L19 10.3h-6.1z") } }
    val Link by lazy {
        icon("link") {
            p("M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1")
            p("M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1")
        }
    }
}
