package com.dasein.poryadok.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import com.dasein.poryadok.ui.common.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    stroke: Dp = 7.dp,
    content: @Composable () -> Unit = {},
) {
    val track = LocalExtra.current.line
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx()
            val arc = Size(this.size.width - s, this.size.height - s)
            val tl = Offset(s / 2, s / 2)
            drawArc(track, -90f, 360f, false, tl, arc, style = Stroke(s))
            drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, tl, arc, style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}

/**
 * Столбики; последний (текущий) можно выделить. Касание столбика показывает его значение,
 * двумя пальцами — масштаб, двойное касание — весь период. [format] — как подписать значение (например, «5 432 шага»).
 */
@Composable
fun BarChart(
    values: List<Float>,
    labels: List<String>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    highlight: Int = -1,
    target: Float? = null,
    format: (Float) -> String = { chartNumber(it) },
    bubble: ((Int) -> String)? = null,
) {
    val extra = LocalExtra.current
    val dim = extra.dim
    val line = extra.line
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = dim)
    val bubbleStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    val z = rememberChartZoom(values.size)
    // Ось ординат: «круглые» деления от нуля, слева подписи — видно порядок величин.
    val ticks = axisTicks(max(values.maxOrNull() ?: 0f, target ?: 0f))
    val maxV = ticks.last().coerceAtLeast(1f)
    val tickLabels = ticks.map { axisNumber(it) }
    val axisStyle = TextStyle(fontSize = 9.sp, color = dim)
    val ax = (tickLabels.maxOfOrNull { measurer.measure(it, axisStyle).size.width } ?: 0) + with(LocalDensity.current) { 6.dp.toPx() }
    val n = values.size.coerceAtLeast(1)
    val labelH = with(LocalDensity.current) { 18.dp.toPx() }
    // Сверху оставлено место под плашку со значением даже над самым высоким столбиком.
    val topPad = with(LocalDensity.current) { 38.dp.toPx() }
    Canvas(
        modifier.fillMaxWidth().height(height + 18.dp + 38.dp)
            .chartGestures(z, values, ax, (n / 4f).coerceAtLeast(1f)) { o ->
                val slot = z.width * z.scale / n
                ((o.x - ax + z.shift) / slot).toInt().takeIf { it in values.indices } ?: -1
            },
    ) {
        val pw = size.width - ax
        z.width = pw
        val h = size.height - labelH - topPad
        val slot = pw * z.scale / n
        val bw = slot * .62f
        val sel = z.selected
        ticks.forEachIndexed { i, t ->
            val y = topPad + h * (1 - t / maxV)
            if (i > 0) drawLine(line.copy(alpha = .6f), Offset(ax, y), Offset(size.width, y), 1f)
            val tl = measurer.measure(tickLabels[i], axisStyle)
            drawText(tl, topLeft = Offset(ax - tl.size.width - 4f, (y - tl.size.height / 2).coerceAtLeast(0f)))
        }
        clipRect(ax, 0f, size.width, size.height) {
            values.forEachIndexed { i, v ->
                val x0 = ax + i * slot - z.shift
                if (x0 + slot < ax || x0 > size.width) return@forEachIndexed
                val bh = h * (v / maxV)
                val active = if (sel >= 0) i == sel else (highlight == -1 || i == highlight)
                drawRoundRect(
                    if (active) color else color.copy(alpha = .45f), Offset(x0 + (slot - bw) / 2, topPad + h - bh), Size(bw, bh.coerceAtLeast(2f)),
                    CornerRadius(6f, 6f),
                )
            }
            // Подписи под столбиками: все, если помещаются, иначе через одну-две.
            val maxLabelW = labels.maxOfOrNull { measurer.measure(it, labelStyle).size.width } ?: 0
            val every = if (slot <= 0f) 1 else kotlin.math.ceil((maxLabelW + 6f) / slot).toInt().coerceAtLeast(1)
            labels.forEachIndexed { i, l ->
                if (i % every != 0 && i != sel) return@forEachIndexed
                val cx = ax + i * slot - z.shift + slot / 2
                if (cx < ax - slot || cx > size.width + slot) return@forEachIndexed
                val t = measurer.measure(l, if (i == sel) labelStyle.copy(color = onSurface, fontWeight = FontWeight.Bold) else labelStyle)
                drawText(t, topLeft = Offset((cx - t.size.width / 2).coerceIn(ax, (size.width - t.size.width).coerceAtLeast(ax)), topPad + h + 4f))
            }
        }
        if (target != null) {
            val y = topPad + h * (1 - target / maxV)
            drawLine(dim, Offset(ax, y), Offset(size.width, y), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        }
        drawLine(line, Offset(ax, topPad + h), Offset(size.width, topPad + h), 2f)
        drawLine(line, Offset(ax, topPad), Offset(ax, topPad + h), 2f)
        if (sel in values.indices) {
            val cx = ax + sel * slot - z.shift + slot / 2
            val top = topPad + h - h * (values[sel] / maxV)
            val label = labels.getOrNull(sel)?.let { "$it · " } ?: ""
            chartBubble(measurer, bubble?.invoke(sel) ?: (label + format(values[sel])), Offset(cx, top), onSurface, surface, bubbleStyle)
        }
    }
}

/** Деления оси: 0 и 3–5 «круглых» значений (1, 2, 2,5, 5 × 10ⁿ), последнее — не меньше максимума. */
fun axisTicks(maxValue: Float, parts: Int = 4): List<Float> {
    if (maxValue <= 0f) return listOf(0f, 1f)
    val raw = maxValue / parts
    val mag = Math.pow(10.0, kotlin.math.floor(kotlin.math.log10(raw.toDouble()))).toFloat()
    val step = listOf(1f, 2f, 2.5f, 5f, 10f).map { it * mag }.first { it >= raw }
    val top = kotlin.math.ceil(maxValue / step) * step
    return generateSequence(0f) { it + step }.takeWhile { it <= top + step / 2 }.toList()
}

/** Коротко для оси: 950, 1,5 тыс, 12 тыс, 1,2 млн. */
fun axisNumber(v: Float): String {
    val a = kotlin.math.abs(v)
    fun one(x: Float) = (if (x >= 10f || kotlin.math.abs(x - Math.round(x)) < 0.05f) Math.round(x).toString() else "%.1f".format(java.util.Locale.US, x).replace('.', ','))
    return when {
        a >= 1_000_000f -> one(v / 1_000_000f) + " млн"
        a >= 1_000f -> one(v / 1_000f) + " тыс"
        else -> chartNumber(v)
    }
}

data class Series(val points: List<Float?>, val color: Color, val dashed: Boolean = false, val name: String = "")

/**
 * Линии. Касание показывает значения в ближайшей точке, двумя пальцами — масштаб, двойное касание — сброс.
 * [pointLabels] — подписи точек для подсказки (например, даты), [format] — как подписать значение.
 */
@Composable
fun LineChart(
    series: List<Series>,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    labels: List<String> = emptyList(),
    pointLabels: List<String> = emptyList(),
    format: (Float) -> String = { chartNumber(it) },
    bubble: ((Int) -> String)? = null,
) {
    val extra = LocalExtra.current
    val dim = extra.dim
    val line = extra.line
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    val measurer = rememberTextMeasurer()
    val bubbleStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    val all = series.flatMap { s -> s.points.filterNotNull() }
    if (all.isEmpty()) return
    val n = series.maxOf { it.points.size }.coerceAtLeast(1)
    val z = rememberChartZoom(n)
    var lo = all.min()
    var hi = all.max()
    if (hi - lo < 1f) { hi += .5f; lo -= .5f }
    val pad = (hi - lo) * .12f
    lo -= pad; hi += pad
    val topPad = with(LocalDensity.current) { 26.dp.toPx() }
    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier.fillMaxWidth().height(height + 26.dp)
                .chartGestures(z, n, 0f, (n / 4f).coerceAtLeast(1f)) { o ->
                    if (n < 2) 0 else (((o.x + z.shift) / (z.width * z.scale)) * (n - 1)).let { Math.round(it) }.coerceIn(0, n - 1)
                },
        ) {
            z.width = size.width
            val w = size.width
            val h = size.height - topPad
            fun x(i: Int) = if (n > 1) z.x(0f, i / (n - 1f)) else w / 2
            fun y(v: Float) = topPad + h * (1 - (v - lo) / (hi - lo))
            // Ось ординат: значение у каждой линии сетки — видно порядок величин.
            drawIntoCanvas { c ->
                val p = android.graphics.Paint().apply { this.color = dim.toArgb(); textSize = 24f; isAntiAlias = true }
                for (k in 0..3) {
                    val yy = topPad + h * k / 3f
                    drawLine(line, Offset(0f, yy), Offset(w, yy), 1.5f)
                    val v = hi - (hi - lo) * k / 3f
                    c.nativeCanvas.drawText(if (hi - lo >= 1000f) axisNumber(v) else chartNumber(v), 4f, yy - 6f, p)
                }
            }
            clipRect(0f, 0f, w, size.height) {
                series.forEach { s ->
                    if (s.points.isEmpty()) return@forEach
                    val path = Path()
                    var started = false
                    s.points.forEachIndexed { i, v ->
                        if (v == null) return@forEachIndexed
                        val px = x(i); val py = y(v)
                        if (!started) { path.moveTo(px, py); started = true } else path.lineTo(px, py)
                        if (!s.dashed) drawCircle(s.color, if (i == z.selected) 9f else 5f, Offset(px, py))
                    }
                    drawPath(
                        path, s.color,
                        style = Stroke(
                            width = if (s.dashed) 3f else 5f, cap = StrokeCap.Round,
                            pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(14f, 10f)) else null,
                        ),
                    )
                }
            }
            val sel = z.selected
            if (sel in 0 until n) {
                val px = x(sel)
                drawLine(dim, Offset(px, topPad), Offset(px, size.height), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                val vals = series.mapNotNull { s -> s.points.getOrNull(sel)?.let { v -> (if (s.name.isNotBlank()) "${s.name} " else "") + format(v) } }
                val top = series.mapNotNull { it.points.getOrNull(sel) }.maxOrNull()?.let { y(it) } ?: topPad
                if (vals.isNotEmpty()) {
                    val head = pointLabels.getOrNull(sel)?.let { "$it · " } ?: ""
                    chartBubble(measurer, head + vals.joinToString(" · "), Offset(px, top), onSurface, surface, bubbleStyle)
                }
            }
        }
        if (labels.isNotEmpty() && z.scale <= 1.01f) Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            labels.forEach { Text(it, Modifier.weight(1f), fontSize = 10.sp, color = dim, textAlign = TextAlign.Center, maxLines = 1) }
        }
    }
}

@Composable
fun Donut(slices: List<Pair<Float, Color>>, modifier: Modifier = Modifier, size: Dp = 150.dp, center: @Composable () -> Unit = {}) {
    val track = LocalExtra.current.line
    val total = slices.sumOf { it.first.toDouble() }.toFloat()
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = 22.dp.toPx()
            val arc = Size(this.size.width - s, this.size.height - s)
            val tl = Offset(s / 2, s / 2)
            if (total <= 0f) {
                drawArc(track, 0f, 360f, false, tl, arc, style = Stroke(s))
                return@Canvas
            }
            var start = -90f
            slices.forEach { (v, c) ->
                val sweep = 360f * v / total
                drawArc(c, start, (sweep - 1.2f).coerceAtLeast(.5f), false, tl, arc, style = Stroke(s))
                start += sweep
            }
        }
        center()
    }
}

/** Тепловая карта в духе GitHub: столбцы — недели, строки — дни недели. */
@Composable
fun Heatmap(
    values: Map<Long, Float>,
    lastDay: Long,
    weeks: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val empty = LocalExtra.current.line
    Canvas(modifier.fillMaxWidth().height(((weeks.coerceAtMost(26)) * 0 + 7 * 15).dp)) {
        val gap = 3.dp.toPx()
        val cell = ((size.width - gap * (weeks - 1)) / weeks).coerceAtMost((size.height - gap * 6) / 7)
        val lastMonday = com.dasein.poryadok.logic.Dates.weekStart(lastDay)
        for (w in 0 until weeks) {
            val ws = lastMonday - 7L * (weeks - 1 - w)
            for (d in 0 until 7) {
                val day = ws + d
                if (day > lastDay) continue
                val v = values[day] ?: 0f
                val c = if (v <= 0f) empty else color.copy(alpha = .3f + .7f * v.coerceIn(0f, 1f))
                drawRoundRect(c, Offset(w * (cell + gap), d * (cell + gap)), Size(cell, cell), CornerRadius(4f, 4f))
            }
        }
    }
}

/** Радар для «колеса баланса»: значения 0..10. */
@Composable
fun Radar(values: List<Float>, labels: List<String>, color: Color, modifier: Modifier = Modifier, size: Dp = 260.dp) {
    val line = LocalExtra.current.line
    val dim = LocalExtra.current.dim
    val text = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.size(size)) {
        val n = values.size
        val cx = this.size.width / 2
        val cy = this.size.height / 2
        val r = this.size.minDimension / 2 * .72f
        fun pt(i: Int, k: Float) = Offset(
            cx + r * k * cos(2 * PI * i / n - PI / 2).toFloat(),
            cy + r * k * sin(2 * PI * i / n - PI / 2).toFloat(),
        )
        for (ring in 1..5) {
            val p = Path()
            for (i in 0 until n) { val o = pt(i, ring / 5f); if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y) }
            p.close()
            drawPath(p, line, style = Stroke(1.5f))
        }
        for (i in 0 until n) drawLine(line, Offset(cx, cy), pt(i, 1f), 1.5f)
        val poly = Path()
        values.forEachIndexed { i, v -> val o = pt(i, v / 10f); if (i == 0) poly.moveTo(o.x, o.y) else poly.lineTo(o.x, o.y) }
        poly.close()
        drawPath(poly, color.copy(alpha = .35f))
        drawPath(poly, color, style = Stroke(4f))
        drawIntoCanvas { c ->
            val p = android.graphics.Paint().apply {
                this.color = text.toArgb(); textSize = 28f; isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER
            }
            val pv = android.graphics.Paint(p).apply { this.color = dim.toArgb(); textSize = 24f }
            labels.forEachIndexed { i, l ->
                val o = pt(i, 1.22f)
                c.nativeCanvas.drawText(l, o.x, o.y, p)
                c.nativeCanvas.drawText(values[i].toInt().toString(), o.x, o.y + 26f, pv)
            }
        }
    }
}
