package com.dasein.poryadok.ui.health

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dasein.poryadok.data.BodyMetric
import com.dasein.poryadok.logic.BodyComp
import com.dasein.poryadok.logic.BodyReading
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.Person
import com.dasein.poryadok.logic.Tone
import com.dasein.poryadok.ui.common.FoldTile
import com.dasein.poryadok.ui.common.InfoBox
import com.dasein.poryadok.ui.theme.LocalExtra

/** Цвет зоны карты: зелёный — здоровые типы, жёлтый — пограничные, красный — ожирение, синий — недобор. */
private val ZONE_TONES = listOf(
    listOf(Tone.YELLOW, Tone.RED, Tone.RED),
    listOf(Tone.GREEN, Tone.GREEN, Tone.YELLOW),
    listOf(Tone.BLUE, Tone.BLUE, Tone.YELLOW),
)

/**
 * Анализ телосложения: карта «процент жира × ИМТ» с девятью типами, вашей точкой и следом последних замеров,
 * понятные пояснения по двум осям и сколько изменить, чтобы попасть в здоровую зону.
 */
@Composable
internal fun BodyTypeMap(r: BodyReading, p: Person, history: List<BodyMetric>) {
    val extra = LocalExtra.current
    val bmi = BodyComp.bmi(r.weight, p.heightCm)
    val fat = r.fatPct
    val cell = BodyComp.bodyType(r.weight, fat, p)
    val title = cell?.let { BodyComp.BODY_TYPES[it.first][it.second] }
    FoldTile("body_type", "Анализ телосложения", summary = title ?: "нужен % жира", summaryColor = cell?.let { toneColor(ZONE_TONES[it.first][it.second]) }) {
        if (fat == null || cell == null) {
            Text("Нужен процент жира — с весов (Fitdays, Health Connect) или вручную в «Изменить».", color = extra.dim, fontSize = 13.sp)
            return@FoldTile
        }
        val fs = BodyComp.fatScale(p)
        val low = fs.bounds[0]
        val top = fs.bounds[1]
        val trail = history.filter { it.fatPct != null }.sortedBy { it.at }.takeLast(8).map { it.fatPct!! to BodyComp.bmi(it.weight, p.heightCm) }
        TypeCanvas(fat, bmi, low, top, trail)

        // Две оси — словами.
        val bmiText = when {
            bmi >= 30 -> "ожирение по ИМТ"
            bmi >= 25 -> "выше нормы"
            bmi >= 18.5 -> "в норме"
            else -> "ниже нормы"
        }
        val fatText = when {
            fat >= top -> "выше нормы"
            fat >= low -> "в норме"
            else -> "ниже нормы"
        }
        Text(
            "ИМТ ${fmt(bmi)} — $bmiText (норма 18,5–24,9)",
            fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            "Жир ${fmt(fat)}% — $fatText (норма ${fmt(low)}–${fmt(top)}% для ${if (p.male) "мужчин" else "женщин"} ${p.age} лет)",
            fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp),
        )

        // Что сделать: к здоровой зоне при сохранении мышц.
        val lean = r.weight * (1 - fat / 100)
        val plan = when {
            fat >= top -> {
                val targetFat = top - 1
                val targetW = lean / (1 - targetFat / 100)
                val loss = r.weight - targetW
                val newBmi = BodyComp.bmi(targetW, p.heightCm)
                "Путь в «Здоровый тип»: убрать ≈ ${fmt(loss)} кг жира, сохранив мышцы, — вес ≈ ${fmt(targetW)} кг, жир ${fmt(targetFat)}%, ИМТ ${fmt(newBmi)}." +
                    if (newBmi >= 25) " ИМТ останется выше 25 за счёт мышц — это нормально." else ""
            }
            fat < low && bmi < 18.5 -> "Путь в «Здоровый тип»: набрать мышцы — силовые 2–3 раза в неделю и белок 1,6 г/кг."
            bmi >= 25 -> "Жир в норме — высокий ИМТ даёт мышечная масса. Это вес спортсмена, худеть не нужно."
            else -> "Вы в здоровой зоне — поддерживайте активность и питание."
        }
        Text(plan, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp), lineHeight = 19.sp)

        // Движение с прошлого замера.
        val withFat = history.filter { it.fatPct != null }.sortedBy { it.at }
        val prev = withFat.dropLast(1).lastOrNull()
        if (prev != null && withFat.size >= 2) {
            val pf = prev.fatPct!!
            val pb = BodyComp.bmi(prev.weight, p.heightCm)
            fun dist(f: Double, b: Double) = maxOf(0.0, f - top, low - f) / 5 + maxOf(0.0, b - 25, 18.5 - b) / 2
            val closer = dist(fat, bmi) < dist(pf, pb) - 0.01
            val farther = dist(fat, bmi) > dist(pf, pb) + 0.01
            Text(
                "С прошлого замера (${Dates.short(prev.day)}): жир ${signed(fat - pf)} п.п., ИМТ ${signed(bmi - pb)}" +
                    when { closer -> " — ближе к здоровой зоне ✓"; farther -> " — дальше от здоровой зоны"; else -> "" },
                fontSize = 13.sp, color = if (closer) toneColor(Tone.GREEN) else if (farther) toneColor(Tone.RED) else extra.dim,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        InfoBox("body_type_about", "Что это значит и что делать", Modifier.padding(top = 10.dp)) {
            Text(BodyComp.typeAbout(cell.first, cell.second), fontSize = 13.sp, lineHeight = 18.sp)
            Text(
                "ИМТ сам по себе не отличает мышцы от жира, поэтому тип определяется по двум осям: по горизонтали — процент жира, по вертикали — ИМТ. " +
                    "Точка — ваш последний замер, пунктир — путь за последние взвешивания.",
                fontSize = 12.sp, color = extra.dim, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun TypeCanvas(fat: Double, bmi: Double, low: Double, top: Double, trail: List<Pair<Double, Double>>) {
    val extra = LocalExtra.current
    val measurer = rememberTextMeasurer()
    val accent = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val all = trail + (fat to bmi)
    val xMin = minOf(low - 6, all.minOf { it.first } - 2).coerceAtLeast(2.0)
    val xMax = maxOf(top + 10, all.maxOf { it.first } + 3)
    val yMin = minOf(16.0, all.minOf { it.second } - 1)
    val yMax = maxOf(32.0, all.maxOf { it.second } + 1.5)
    val tick = TextStyle(fontSize = 9.sp, color = extra.dim)
    val cellStyle = TextStyle(fontSize = 9.sp, color = extra.dim, textAlign = TextAlign.Center, lineHeight = 11.sp)
    Canvas(Modifier.fillMaxWidth().height(250.dp).padding(top = 4.dp)) {
        val left = 30.dp.toPx()
        val bottom = 30.dp.toPx()
        val w = size.width - left
        val h = size.height - bottom
        fun x(v: Double) = left + (w * ((v - xMin) / (xMax - xMin))).toFloat()
        fun y(v: Double) = (h * (1 - (v - yMin) / (yMax - yMin))).toFloat()
        val xs = listOf(xMin, low, top, xMax)
        val ys = listOf(yMax, 25.0, 18.5, yMin) // строки сверху вниз: ИМТ выше нормы, норма, ниже
        val gap = 2.dp.toPx()
        for (row in 0..2) for (col in 0..2) {
            val x0 = x(xs[col]) + gap
            val x1 = x(xs[col + 1]) - gap
            val y0 = y(ys[row]) + gap
            val y1 = y(ys[row + 1]) - gap
            if (x1 <= x0 || y1 <= y0) continue
            val on = (fat >= xs[col] && (fat < xs[col + 1] || col == 2)) && (bmi <= ys[row] || row == 0) && (bmi > ys[row + 1] || row == 2)
            val c = toneColor(ZONE_TONES[row][col])
            drawRoundRect(c.copy(alpha = if (on) .30f else .12f), Offset(x0, y0), Size(x1 - x0, y1 - y0), CornerRadius(8.dp.toPx()))
            if (on) drawRoundRect(c, Offset(x0, y0), Size(x1 - x0, y1 - y0), CornerRadius(8.dp.toPx()), style = Stroke(1.5.dp.toPx()))
            val maxW = (x1 - x0 - 6.dp.toPx()).toInt()
            if (maxW > 20) {
                val t = measurer.measure(BodyComp.BODY_TYPES[row][col], if (on) cellStyle.copy(color = onSurface, fontWeight = FontWeight.SemiBold) else cellStyle, constraints = Constraints(maxWidth = maxW))
                val ty = if (row == 0) y0 + 4.dp.toPx() else if (row == 2) y1 - t.size.height - 4.dp.toPx() else (y0 + y1 - t.size.height) / 2
                if (t.size.height < y1 - y0) drawText(t, topLeft = Offset((x0 + x1 - t.size.width) / 2, ty))
            }
        }
        // Подписи осей: границы зон.
        listOf(25.0, 18.5).forEach { v ->
            val t = measurer.measure(fmt(v), tick)
            drawText(t, topLeft = Offset(left - t.size.width - 4.dp.toPx(), y(v) - t.size.height / 2))
        }
        val yl = measurer.measure("ИМТ", tick)
        drawText(yl, topLeft = Offset(0f, 0f))
        listOf(low, top).forEach { v ->
            val t = measurer.measure(fmt(v, 0) + "%", tick)
            drawText(t, topLeft = Offset(x(v) - t.size.width / 2, h + 3.dp.toPx()))
        }
        val xl = measurer.measure("процент жира →", tick)
        drawText(xl, topLeft = Offset(size.width - xl.size.width, h + 15.dp.toPx()))

        // След последних замеров и текущая точка.
        if (trail.size >= 2) {
            val path = Path()
            trail.forEachIndexed { i, (f, b) -> if (i == 0) path.moveTo(x(f), y(b)) else path.lineTo(x(f), y(b)) }
            path.lineTo(x(fat), y(bmi))
            drawPath(path, onSurface.copy(alpha = .45f), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
            trail.forEach { (f, b) -> drawCircle(onSurface.copy(alpha = .45f), 3.dp.toPx(), Offset(x(f), y(b))) }
        }
        val pt = Offset(x(fat), y(bmi))
        drawCircle(accent.copy(alpha = .30f), 12.dp.toPx(), pt)
        drawCircle(accent, 6.dp.toPx(), pt)
        drawCircle(extra.card, 2.5.dp.toPx(), pt)
    }
}
