package com.vaani.core.designsystem.icon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Vaani line-icon set — single-weight, sharp-cornered, Feather/Lucide style,
 * matching docs/icon-sheet.png. Drawn on a Canvas on a 24x24 grid so stroke
 * weight stays crisp and corners stay square (StrokeCap.Butt, no round joins).
 */
enum class VaaniIcon {
    Library, Search, Mic, CheckSquare, Sliders, Play,
    ChevronRight, ChevronLeft, ArrowUp, ArrowDown, MoreH, Close,
    Bluetooth, Key, Shield, Battery, Wifi, Clock, Trash, Download, Refresh, Zap, Waveform,
}

@Composable
fun VaaniIconView(
    icon: VaaniIcon,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    strokeWidth: Dp = 2.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val u = this.size.minDimension / 24f // grid unit
        val sw = strokeWidth.toPx()
        val stroke = Stroke(width = sw, cap = StrokeCap.Butt)
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, p(x1, y1), p(x2, y2), sw, StrokeCap.Butt)

        when (icon) {
            VaaniIcon.Library -> { // three stacked bars, top short-capped
                line(5f, 6f, 19f, 6f); line(11f, 4f, 13f, 4f)
                line(5f, 12f, 19f, 12f); line(5f, 18f, 19f, 18f)
            }
            VaaniIcon.Search -> {
                drawCircle(tint, radius = 6f * u, center = p(10f, 10f), style = stroke)
                line(14.5f, 14.5f, 20f, 20f)
            }
            VaaniIcon.Mic -> {
                // capsule head (square, sharp)
                drawRect(tint, topLeft = p(9f, 3f), size = Size(6f * u, 10f * u), style = stroke)
                // arc stand + base
                line(6f, 11f, 6f, 12f); line(18f, 11f, 18f, 12f)
                drawArc(tint, 0f, 180f, false, topLeft = p(6f, 6f), size = Size(12f * u, 12f * u), style = stroke)
                line(12f, 18f, 12f, 21f); line(8f, 21f, 16f, 21f)
            }
            VaaniIcon.CheckSquare -> {
                drawRect(tint, topLeft = p(4f, 4f), size = Size(16f * u, 16f * u), style = stroke)
                val path = Path().apply { moveTo(8f * u, 12f * u); lineTo(11f * u, 15f * u); lineTo(16.5f * u, 8.5f * u) }
                drawPath(path, tint, style = stroke)
            }
            VaaniIcon.Sliders -> {
                line(4f, 8f, 20f, 8f); line(4f, 16f, 20f, 16f)
                drawRect(tint, topLeft = p(13f, 6f), size = Size(3f * u, 4f * u), style = stroke)
                drawRect(tint, topLeft = p(8f, 14f), size = Size(3f * u, 4f * u), style = stroke)
            }
            VaaniIcon.Play -> {
                val path = Path().apply {
                    moveTo(7f * u, 5f * u); lineTo(19f * u, 12f * u); lineTo(7f * u, 19f * u); close()
                }
                drawPath(path, tint) // filled triangle
            }
            VaaniIcon.ChevronRight -> { line(9f, 5f, 16f, 12f); line(16f, 12f, 9f, 19f) }
            VaaniIcon.ChevronLeft -> { line(15f, 5f, 8f, 12f); line(8f, 12f, 15f, 19f) }
            VaaniIcon.ArrowUp -> { line(12f, 4f, 12f, 20f); line(12f, 4f, 6f, 10f); line(12f, 4f, 18f, 10f) }
            VaaniIcon.ArrowDown -> { line(12f, 4f, 12f, 20f); line(12f, 20f, 6f, 14f); line(12f, 20f, 18f, 14f) }
            VaaniIcon.MoreH -> {
                drawCircle(tint, 1.3f * u, p(6f, 12f)); drawCircle(tint, 1.3f * u, p(12f, 12f)); drawCircle(tint, 1.3f * u, p(18f, 12f))
            }
            VaaniIcon.Close -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
            VaaniIcon.Bluetooth -> {
                val path = Path().apply {
                    moveTo(7f * u, 8f * u); lineTo(17f * u, 16f * u); lineTo(12f * u, 20f * u)
                    lineTo(12f * u, 4f * u); lineTo(17f * u, 8f * u); lineTo(7f * u, 16f * u)
                }
                drawPath(path, tint, style = stroke)
            }
            VaaniIcon.Key -> {
                drawCircle(tint, 3.5f * u, p(8f, 9f), style = stroke)
                line(10.5f, 11.5f, 20f, 21f); line(17f, 18f, 20f, 15f); line(14f, 15f, 16f, 17f)
            }
            VaaniIcon.Shield -> {
                val path = Path().apply {
                    moveTo(12f * u, 3f * u); lineTo(20f * u, 6f * u); lineTo(20f * u, 12f * u)
                    lineTo(12f * u, 21f * u); lineTo(4f * u, 12f * u); lineTo(4f * u, 6f * u); close()
                }
                drawPath(path, tint, style = stroke)
            }
            VaaniIcon.Battery -> {
                drawRect(tint, topLeft = p(3f, 8f), size = Size(16f * u, 8f * u), style = stroke)
                drawRect(tint, topLeft = p(5f, 10f), size = Size(8f * u, 4f * u)) // fill level
                drawRect(tint, topLeft = p(20f, 10f), size = Size(2f * u, 4f * u))
            }
            VaaniIcon.Wifi -> {
                drawArc(tint, 200f, 140f, false, topLeft = p(3f, 5f), size = Size(18f * u, 18f * u), style = stroke)
                drawArc(tint, 210f, 120f, false, topLeft = p(6f, 8f), size = Size(12f * u, 12f * u), style = stroke)
                drawCircle(tint, 1.2f * u, p(12f, 18f))
            }
            VaaniIcon.Clock -> {
                drawCircle(tint, 8f * u, p(12f, 12f), style = stroke)
                line(12f, 12f, 12f, 7f); line(12f, 12f, 16f, 13f)
            }
            VaaniIcon.Trash -> {
                line(4f, 7f, 20f, 7f); line(10f, 4f, 14f, 4f)
                drawRect(tint, topLeft = p(6f, 7f), size = Size(12f * u, 13f * u), style = stroke)
                line(10f, 10f, 10f, 17f); line(14f, 10f, 14f, 17f)
            }
            VaaniIcon.Download -> {
                line(12f, 4f, 12f, 15f); line(12f, 15f, 7f, 10f); line(12f, 15f, 17f, 10f); line(5f, 20f, 19f, 20f)
            }
            VaaniIcon.Refresh -> {
                drawArc(tint, -40f, 300f, false, topLeft = p(5f, 5f), size = Size(14f * u, 14f * u), style = stroke)
                line(17f, 4f, 17f, 9f); line(17f, 9f, 12f, 9f)
            }
            VaaniIcon.Zap -> {
                val path = Path().apply {
                    moveTo(13f * u, 3f * u); lineTo(5f * u, 13f * u); lineTo(11f * u, 13f * u)
                    lineTo(11f * u, 21f * u); lineTo(19f * u, 11f * u); lineTo(13f * u, 11f * u); close()
                }
                drawPath(path, tint, style = stroke)
            }
            VaaniIcon.Waveform -> drawWaveform(tint, u, sw)
        }
    }
}

private fun DrawScope.drawWaveform(tint: Color, u: Float, sw: Float) {
    val heights = floatArrayOf(6f, 12f, 4f, 16f, 8f, 18f, 6f, 12f, 4f)
    heights.forEachIndexed { i, h ->
        val x = (4f + i * 2f) * u
        val half = h / 2f
        drawLine(tint, Offset(x, (12f - half) * u), Offset(x, (12f + half) * u), sw, StrokeCap.Butt)
    }
}
