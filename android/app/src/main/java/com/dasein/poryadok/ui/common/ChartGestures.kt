package com.dasein.poryadok.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import kotlin.math.abs

/**
 * Состояние графика: масштаб по горизонтали, сдвиг и выбранная точка.
 * Два пальца — увеличить/уменьшить, один палец — листать увеличенный график,
 * касание — показать значение, двойное касание — вернуть весь период.
 */
class ChartZoom {
    var scale by mutableFloatStateOf(1f)
    var shift by mutableFloatStateOf(0f)
    /** Ширина области данных в пикселях — задаётся при отрисовке. */
    var width by mutableFloatStateOf(1f)
    var selected by mutableIntStateOf(-1)

    fun clamp() {
        shift = shift.coerceIn(0f, (width * (scale - 1f)).coerceAtLeast(0f))
    }

    fun reset() {
        scale = 1f; shift = 0f; selected = -1
    }

    /** Экранная координата точки [pos] (0..1 по всему периоду) с учётом масштаба. */
    fun x(left: Float, pos: Float): Float = left + pos * width * scale - shift
}

@Composable
fun rememberChartZoom(key: Any?): ChartZoom = remember(key) { ChartZoom() }

/**
 * Жесты графика. [left] — отступ области данных слева в пикселях, [maxScale] — предельное увеличение,
 * [pick] — индекс точки по координате касания (или -1).
 */
fun Modifier.chartGestures(z: ChartZoom, key: Any?, left: Float, maxScale: Float, pick: (Offset) -> Int): Modifier = this
    .pointerInput(key, maxScale) {
        if (maxScale <= 1f) return@pointerInput
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent()
                val pressed = event.changes.count { it.pressed }
                if (pressed >= 2) {
                    val zoom = event.calculateZoom()
                    val pan = event.calculatePan()
                    val c = event.calculateCentroid(useCurrent = true)
                    val anchor = (c.x - left + z.shift) / z.scale
                    val ns = (z.scale * zoom).coerceIn(1f, maxScale)
                    z.shift = anchor * ns - (c.x - left) - pan.x
                    z.scale = ns
                    z.clamp()
                    event.changes.forEach { it.consume() }
                } else if (pressed == 1 && z.scale > 1f) {
                    val pan = event.calculatePan()
                    if (abs(pan.x) > abs(pan.y)) {
                        z.shift -= pan.x
                        z.clamp()
                        event.changes.forEach { it.consume() }
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }
    .pointerInput(key) {
        detectTapGestures(
            onTap = { o -> val i = pick(o); z.selected = if (i == z.selected) -1 else i },
            onDoubleTap = { z.reset() },
        )
    }

/** Подсказка со значением над точкой: плашка, которая не выходит за края графика. */
fun DrawScope.chartBubble(measurer: TextMeasurer, text: String, anchor: Offset, bg: Color, fg: Color, style: TextStyle) {
    val t = measurer.measure(text, style.copy(color = fg))
    val padX = 8f * density
    val padY = 4f * density
    val w = t.size.width + padX * 2
    val h = t.size.height + padY * 2
    val x = (anchor.x - w / 2).coerceIn(0f, (size.width - w).coerceAtLeast(0f))
    val y = (anchor.y - h - 8f * density).let { if (it < 0f) anchor.y + 10f * density else it }
    drawRoundRect(bg, Offset(x, y), Size(w, h), CornerRadius(8f * density))
    drawText(t, topLeft = Offset(x + padX, y + padY))
}

/** Число для подписи: целые — с пробелами между тысячами, дробные — с одним знаком. */
fun chartNumber(v: Float): String {
    if (abs(v - Math.round(v)) < 0.05f || abs(v) >= 100f) {
        val n = Math.round(v).toLong()
        val digits = abs(n).toString().reversed().chunked(3).joinToString(" ").reversed()
        return if (n < 0) "-$digits" else digits
    }
    return "%.1f".format(java.util.Locale.US, v).replace('.', ',')
}
