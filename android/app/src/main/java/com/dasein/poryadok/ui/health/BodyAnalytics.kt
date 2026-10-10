package com.dasein.poryadok.ui.health

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dasein.poryadok.data.BodyMetric
import com.dasein.poryadok.logic.BodyComp
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Segments
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlin.math.abs

/** Показатель для графика «Аналитики»: как в Fitdays — вес, ИМТ, жир, вода, мышцы, обмен, пульс и т. д. */
private class Chartable(val title: String, val unit: String, val digits: Int, val value: (BodyMetric) -> Double?)

private fun chartables(heightCm: Double) = listOf(
    Chartable("Вес", "кг", 1) { it.weight },
    Chartable("ИМТ", "", 1) { m -> heightCm.takeIf { it > 50 }?.let { BodyComp.bmi(m.weight, it) } },
    Chartable("Телесный жир", "%", 1) { it.fatPct },
    Chartable("Вода", "%", 1) { it.waterPct },
    Chartable("Скелетные мышцы", "%", 1) { it.musclePct },
    Chartable("Базовый обмен", "ккал", 0) { it.bmr },
    Chartable("Масса тела без жира", "кг", 1) { m -> m.leanKg ?: m.fatPct?.let { m.weight * (1 - it / 100) } },
    Chartable("Подкожный жир", "%", 1) { it.subcutaneousPct },
    Chartable("Пульс", "уд/мин", 0) { it.heartRate },
    Chartable("Сердечный индекс", "л/мин/м²", 1) { it.cardiacIndex },
    Chartable("Висцеральный жир", "", 0) { it.visceral },
    Chartable("Мышечная масса", "кг", 1) { it.muscleKg },
    Chartable("Костная масса", "кг", 1) { it.boneKg },
    Chartable("Белок", "%", 1) { it.proteinPct },
    Chartable("Возраст тела", "лет", 0) { it.metabolicAge },
)

/**
 * «Аналитика»: графики всех показателей с весов за выбранный период — плавная линия с градиентной заливкой
 * в цвете акцента приложения (меняется в настройках оформления).
 */
@Composable
internal fun BodyCharts(readings: List<BodyMetric>, heightCm: Double) {
    val extra = LocalExtra.current
    var period by rememberSaveable { mutableIntStateOf(0) }
    val today = Dates.today()
    val sorted = remember(readings) { readings.sortedBy { it.at } }
    val shown: List<Pair<Long, BodyMetric>> = when (period) {
        // Последние замеры — каждое взвешивание.
        0 -> sorted.takeLast(7).map { it.day to it }
        // Неделя и месяц — последнее взвешивание за день; год — по неделям.
        1 -> sorted.filter { it.day > today - 7 }.groupBy { it.day }.map { (d, l) -> d to l.last() }
        2 -> sorted.filter { it.day > today - 30 }.groupBy { it.day }.map { (d, l) -> d to l.last() }
        else -> sorted.filter { it.day > today - 365 }.groupBy { Dates.weekStart(it.day) }.map { (w, l) -> w to l.last() }
    }
    SectionTitle("Графики показателей", Modifier.padding(top = 8.dp))
    Segments(listOf(0 to "Последние", 1 to "Неделя", 2 to "Месяц", 3 to "Год"), period, { period = it })
    val color = MaterialTheme.colorScheme.primary
    var any = false
    chartables(heightCm).forEach { c ->
        val pts = shown.mapNotNull { (d, m) -> c.value(m)?.let { d to it } }
        if (pts.isEmpty()) return@forEach
        any = true
        // Изменение — к предыдущему взвешиванию с этим показателем, как в Fitdays.
        val history = sorted.mapNotNull { c.value(it) }
        val last = history.last()
        val delta = if (history.size >= 2) last - history[history.size - 2] else null
        val deltaText = if (delta != null && abs(delta) >= 0.05) "  " + (if (delta > 0) "▲ " else "▼ ") + fmt(abs(delta), c.digits) else ""
        com.dasein.poryadok.ui.common.FoldTile(
            "chart_${c.title}", c.title + if (c.unit.isNotEmpty()) " (${c.unit})" else "",
            Modifier.padding(top = 8.dp), summary = fmt(last, c.digits) + deltaText,
        ) {
            AreaChart(pts, c.digits, color, extra.dim, extra.line, Modifier.padding(top = 2.dp))
        }
    }
    if (!any) Text("Пока нет взвешиваний за этот период.", color = extra.dim, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
}

/** Плавная кривая с градиентной заливкой: подписи минимума и максимума слева, дат — снизу. */
@Composable
private fun AreaChart(points: List<Pair<Long, Double>>, digits: Int, color: Color, dim: Color, grid: Color, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val bg = LocalExtra.current.card
    val label = TextStyle(fontSize = 9.sp, color = dim)
    Canvas(modifier.fillMaxWidth().height(130.dp)) {
        val left = 34.dp.toPx()
        val right = 10.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = 18.dp.toPx()
        val w = size.width - left - right
        val h = size.height - top - bottom
        val vs = points.map { it.second }
        var lo = vs.min()
        var hi = vs.max()
        val span = (hi - lo).takeIf { it > 1e-9 } ?: maxOf(abs(hi) * 0.04, 1.0)
        lo -= span * 0.35
        hi += span * 0.35
        if (vs.min() >= 0 && lo < 0) lo = 0.0
        fun x(i: Int) = left + if (points.size == 1) w / 2 else w * i / (points.size - 1)
        fun y(v: Double) = top + h * (1 - ((v - lo) / (hi - lo))).toFloat()

        // Сетка: три пунктирные линии с подписями.
        listOf(hi, (hi + lo) / 2, lo).forEach { v ->
            val yy = y(v)
            drawLine(grid, Offset(left, yy), Offset(left + w, yy), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            val t = measurer.measure(fmt(v, if (digits == 0 && hi - lo < 6) 1 else digits), label)
            drawText(t, topLeft = Offset(left - t.size.width - 4.dp.toPx(), yy - t.size.height / 2))
        }

        val line = Path()
        points.forEachIndexed { i, (_, v) ->
            val px = x(i)
            val py = y(v)
            if (i == 0) line.moveTo(px, py)
            else {
                val ox = x(i - 1)
                val oy = y(points[i - 1].second)
                val cx = (ox + px) / 2
                line.cubicTo(cx, oy, cx, py, px, py)
            }
        }
        if (points.size > 1) {
            val fill = Path().apply {
                addPath(line)
                lineTo(x(points.lastIndex), top + h)
                lineTo(x(0), top + h)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = .55f), color.copy(alpha = .18f), color.copy(alpha = 0f)), startY = top, endY = top + h))
            drawPath(line, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        }
        points.forEachIndexed { i, (_, v) ->
            drawCircle(bg, 4.dp.toPx(), Offset(x(i), y(v)))
            drawCircle(color, 4.dp.toPx(), Offset(x(i), y(v)), style = Stroke(2.dp.toPx()))
        }

        // Даты: не больше пяти подписей, чтобы не налезали.
        val step = maxOf(1, (points.size + 4) / 5)
        points.forEachIndexed { i, (d, _) ->
            if (i % step != 0 && i != points.lastIndex) return@forEachIndexed
            if (i != points.lastIndex && points.lastIndex - i < step && i != 0) return@forEachIndexed
            val date = Dates.day(d)
            val t = measurer.measure("%02d/%02d".format(date.dayOfMonth, date.monthValue), label)
            val tx = (x(i) - t.size.width / 2).coerceIn(left - 6.dp.toPx(), size.width - t.size.width.toFloat())
            drawText(t, topLeft = Offset(tx, top + h + 4.dp.toPx()))
        }
    }
}
