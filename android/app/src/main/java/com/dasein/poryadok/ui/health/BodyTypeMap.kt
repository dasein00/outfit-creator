package com.dasein.poryadok.ui.health

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import com.dasein.poryadok.ui.common.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
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
    listOf(Tone.GREEN, Tone.YELLOW, Tone.RED),
    listOf(Tone.GREEN, Tone.GREEN, Tone.YELLOW),
    listOf(Tone.BLUE, Tone.BLUE, Tone.YELLOW),
)

/** Девять типов карты «талия/рост × ИМТ». Строки — ИМТ (выше нормы, норма, ниже), столбцы — талия/рост (< 0,4; 0,4–0,5; ≥ 0,5). */
private val TYPES = listOf(
    listOf("Телосложение спортсмена", "Крупный, плотный тип", "Ожирение"),
    listOf("Стройный тип", "Здоровый тип", "Скрытое ожирение (живот)"),
    listOf("Дефицит веса", "Худощавый тип", "Жир на животе при малом весе"),
)

/**
 * Модели для каждого типа (assets/body_types). Для «Крупного, плотного» отдельной модели нет —
 * показываем ближайшую по сложению (крупное тело без выраженного живота).
 */
private val MODELS = listOf(
    listOf("athlete", "hidden_obese", "obese"),
    listOf("slim", "healthy", "hidden_obese"),
    listOf("deficit", "lean", "belly_low"),
)

private fun about(row: Int, col: Int): String = when (row to col) {
    0 to 0 -> "ИМТ выше 25, а талия меньше 40% роста — лишний вес даёт не живот, а мышцы и крупный костяк. Так выглядит тело спортсмена: худеть не нужно, держите силовые и белок."
    0 to 1 -> "ИМТ выше нормы, но талия меньше половины роста — жир не скапливается на животе. Это либо много мышц, либо небольшой лишний вес. Следите, чтобы талия не росла: это главный признак риска."
    0 to 2 -> "И ИМТ, и талия выше нормы — лишний жир, в том числе внутренний (на органах). Именно он связан с давлением, диабетом 2 типа и болезнями сердца. Начните с ходьбы 8–10 тыс. шагов, силовых 2–3 раза в неделю и небольшого дефицита калорий. Даже −5% веса и −5 см талии заметно снижают риски."
    1 to 0 -> "Вес в норме, талия очень тонкая относительно роста. Хорошо, если вы так сложены от природы и чувствуете себя хорошо; при слабости или усталости проверьте питание."
    1 to 1 -> "И вес, и талия в норме — самый благоприятный тип по рискам для сердца и обмена веществ. Поддерживайте активность и питание."
    1 to 2 -> "Вес нормальный, но талия больше половины роста — жир скапливается на животе («худой снаружи, полный внутри»). Риск для сердца и обмена веществ повышен, хотя по ИМТ всё в порядке. Помогут силовые тренировки, больше движения в течение дня, меньше сахара и алкоголя, сон 7–9 часов."
    2 to 0 -> "ИМТ ниже 18,5 и очень тонкая талия — веса не хватает. Добавьте калорий и белка (1,6 г на кг), силовые 2–3 раза в неделю; при резкой потере веса покажитесь врачу."
    2 to 1 -> "Вес чуть ниже нормы, талия в норме. Набирайте мышцы силовыми тренировками и достаточным белком."
    else -> "Вес ниже нормы, а талия — больше половины роста: мало мышц и жир на животе. Нужны силовые тренировки и белок, а не диета; стоит обсудить с врачом."
}

private const val WHTR_LOW = 0.4
private const val WHTR_HIGH = 0.5

/**
 * Анализ телосложения по тому, что можно измерить лентой и весами: ИМТ (ВОЗ) по вертикали и отношение талии к росту
 * по горизонтали (NICE, 2022; Ashwell et al., 2012). Талия/рост лучше ИМТ предсказывает риски для сердца и обмена веществ
 * и отличает мышцы от жира на животе. Дополнительно — талия/бёдра (ВОЗ, 2008) и оценка жира RFM (Woolcott & Bergman, 2018).
 */
@Composable
internal fun BodyTypeMap(r: BodyReading, p: Person, history: List<BodyMetric>) {
    val extra = LocalExtra.current
    val measurements by com.dasein.poryadok.ui.common.observe<List<com.dasein.poryadok.data.Measurement>?>(null) { com.dasein.poryadok.Graph.dao.measurements() }
    val ms = measurements ?: return
    // Все замеры талии: из «Замеров» и из записей веса, где талия вписана.
    // Открыто прошлое взвешивание — берём замеры не позже его дня, а не более свежие.
    val upTo = history.lastOrNull()?.day ?: Long.MAX_VALUE
    val waists = (ms.mapNotNull { m -> m.waist?.let { Triple(m.day, it, m.hips) } } +
        history.mapNotNull { h -> h.waistCm?.let { Triple(h.day, it, h.hipCm) } }).filter { it.first <= upTo }.sortedBy { it.first }
    val bmi = BodyComp.bmi(r.weight, p.heightCm)
    val last = waists.lastOrNull()
    val waist = last?.second
    // Бёдра — из того же замера, что и талия: отношение двух разных дней не имеет смысла.
    val hip = last?.third
    val whtr = waist?.let { it / p.heightCm }
    val row = when { bmi >= 25 -> 0; bmi >= 18.5 -> 1; else -> 2 }
    val col = whtr?.let { when { it < WHTR_LOW -> 0; it < WHTR_HIGH -> 1; else -> 2 } }
    val title = col?.let { TYPES[row][it] }
    FoldTile("body_type", "Анализ телосложения", summary = title ?: "нужна талия", summaryColor = col?.let { toneColor(ZONE_TONES[row][it]) }) {
        if (whtr == null || col == null || waist == null) {
            Text(
                "Для анализа нужен обхват талии — измерьте лентой посередине между нижним ребром и тазовой костью, на выдохе, " +
                    "и впишите в «Здоровье» → «Замеры». По росту, весу и талии приложение определит тип телосложения по научной методике (ИМТ × талия/рост).",
                color = extra.dim, fontSize = 13.sp, lineHeight = 18.sp,
            )
            return@FoldTile
        }
        // След: каждый замер талии с весом на тот день.
        fun weightOn(day: Long) = history.lastOrNull { it.day <= day }?.weight ?: history.firstOrNull()?.weight
        val trail = waists.dropLast(1).takeLast(8).mapNotNull { (d, w, _) -> weightOn(d)?.let { kg -> w / p.heightCm to BodyComp.bmi(kg, p.heightCm) } }
        // Модель типа: сначала — ваш тип по последнему замеру; касание по клетке графика показывает другой тип.
        // При новом заходе снова ваш (выбор не запоминается).
        var pick by androidx.compose.runtime.remember(row, col) { androidx.compose.runtime.mutableStateOf(row to col) }
        TypeModelCard(pick, own = row to col)
        TypeCanvas(whtr, bmi, trail, pick) { r2, c2 -> pick = r2 to c2 }
        Text(
            "Нажмите на любой тип на графике — модель покажет, как он выглядит.",
            fontSize = 11.sp, color = extra.dim, modifier = Modifier.padding(top = 2.dp),
        )

        val bmiText = when {
            bmi >= 30 -> "ожирение по ИМТ"
            bmi >= 25 -> "выше нормы"
            bmi >= 18.5 -> "в норме"
            else -> "ниже нормы"
        }
        val whtrText = when {
            whtr >= 0.6 -> "высокий риск"
            whtr >= WHTR_HIGH -> "повышенный риск"
            whtr >= WHTR_LOW -> "в норме"
            else -> "очень низкое"
        }
        Text("ИМТ ${fmt(bmi)} — $bmiText (норма 18,5–24,9)", fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
        Text(
            "Талия/рост ${fmt(whtr, 2)} — $whtrText (норма 0,40–0,49): талия ${fmt(waist)} см при росте ${fmt(p.heightCm, 0)} см",
            fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp),
        )
        if (hip != null) {
            val whr = waist / hip
            val lim = if (p.male) 0.90 else 0.85
            Text(
                "Талия/бёдра ${fmt(whr, 2)} — ${if (whr >= lim) "выше нормы" else "в норме"} (ВОЗ: меньше ${fmt(lim, 2)} у ${if (p.male) "мужчин" else "женщин"})",
                fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp),
            )
        }
        val rfm = com.dasein.poryadok.logic.BodyScience.rfm(p.male, p.heightCm, waist)
        Text(
            "Оценка жира по росту и талии (RFM): ≈ ${fmt(rfm)}%" + (r.fatPct?.let { " · весы показывают ${fmt(it)}%" } ?: ""),
            fontSize = 13.sp, color = extra.dim, modifier = Modifier.padding(top = 2.dp),
        )

        // Что сделать, чтобы попасть в «Здоровый тип».
        val targetWaist = p.heightCm * WHTR_HIGH - 1
        val maxW = 24.9 * (p.heightCm / 100) * (p.heightCm / 100)
        val minW = 18.5 * (p.heightCm / 100) * (p.heightCm / 100)
        val plan = buildString {
            when {
                col == 2 -> {
                    append("Путь в «Здоровый тип»: уменьшить талию на ≈ ${fmt(waist - targetWaist)} см — до ${fmt(targetWaist, 0)} см (меньше половины роста).")
                    if (row == 0) append(" По ИМТ — вес до ${fmt(maxW)} кг (сейчас ${fmt(r.weight)}).")
                    append(" В среднем −1 кг веса — это около −1 см талии.")
                }
                row == 0 && col == 1 -> append("Талия в норме — главное не давать ей расти. Если хотите ИМТ ниже 25 — вес до ${fmt(maxW)} кг; при большой мышечной массе это не обязательно.")
                row == 0 -> append("Талия тонкая — высокий ИМТ дают мышцы. Это вес спортсмена, худеть не нужно.")
                row == 2 -> append("Путь в «Здоровый тип»: набрать до ${fmt(minW)} кг (ИМТ 18,5) за счёт мышц — силовые 2–3 раза в неделю и белок 1,6 г/кг.")
                else -> append("Вы в здоровой зоне — держите талию меньше ${fmt(p.heightCm / 2, 0)} см и вес ${fmt(minW)}–${fmt(maxW)} кг.")
            }
        }
        Text(plan, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp), lineHeight = 19.sp)

        // Движение с прошлого замера талии.
        val prev = waists.dropLast(1).lastOrNull()
        if (prev != null) {
            val pw = prev.second / p.heightCm
            val pb = weightOn(prev.first)?.let { BodyComp.bmi(it, p.heightCm) } ?: bmi
            fun dist(x: Double, b: Double) = maxOf(0.0, x - WHTR_HIGH, WHTR_LOW - x) * 20 + maxOf(0.0, b - 25, 18.5 - b) / 2
            val closer = dist(whtr, bmi) < dist(pw, pb) - 0.01
            val farther = dist(whtr, bmi) > dist(pw, pb) + 0.01
            Text(
                "С прошлого замера талии (${Dates.short(prev.first)}): талия ${signed(waist - prev.second)} см, ИМТ ${signed(bmi - pb)}" +
                    when { closer -> " — ближе к здоровой зоне ✓"; farther -> " — дальше от здоровой зоны"; else -> "" },
                fontSize = 13.sp, color = if (closer) toneColor(Tone.GREEN) else if (farther) toneColor(Tone.RED) else extra.dim,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        InfoBox("body_type_about", "Что это значит и что делать", Modifier.padding(top = 10.dp), hint = false) {
            Text(about(row, col), fontSize = 13.sp, lineHeight = 18.sp)
            Text(
                "Тип определяется по двум измеряемым показателям. По вертикали — ИМТ (вес / рост², классификация ВОЗ). " +
                    "По горизонтали — отношение талии к росту: британский NICE (2022) советует держать талию меньше половины роста, " +
                    "а мета-анализ Ashwell и соавт. (2012) показал, что этот показатель лучше ИМТ предсказывает давление, диабет и болезни сердца. " +
                    "Вместе они отличают мышцы от жира на животе без весов с «процентом жира», которые часто ошибаются. " +
                    "RFM (Woolcott и Bergman, 2018) — оценка процента жира по росту и талии, близкая к измерениям DXA. " +
                    "Точка — последний замер, пунктир — путь по прошлым замерам талии. Измеряйте талию раз в 2 недели, утром, на выдохе.",
                fontSize = 12.sp, color = extra.dim, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** Модель выбранного типа и пара строк о нём; плавная смена при выборе другой клетки. */
@Composable
private fun TypeModelCard(pick: Pair<Int, Int>, own: Pair<Int, Int>) {
    val extra = LocalExtra.current
    val (row, col) = pick
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        androidx.compose.animation.Crossfade(MODELS[row][col], label = "bodyType") { key ->
            TypeModel(key, Modifier.width(104.dp).height(200.dp))
        }
        androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(TYPES[row][col], fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = toneColor(ZONE_TONES[row][col]))
            Text(
                if (pick == own) "Ваш тип по последнему замеру" else "Пример типа · ваш — «${TYPES[own.first][own.second]}»",
                fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 2.dp),
            )
            Text(about(row, col), fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

private val modelCache = android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(9)

@Composable
private fun TypeModel(key: String, modifier: Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val img by androidx.compose.runtime.produceState(modelCache.get(key), key) {
        if (value == null) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                ctx.assets.open("body_types/$key.webp").use { android.graphics.BitmapFactory.decodeStream(it) }?.asImageBitmap()
            }.getOrNull()
        }?.also { modelCache.put(key, it) }
    }
    androidx.compose.foundation.layout.Box(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
        img?.let { androidx.compose.foundation.Image(it, TYPES.flatten().getOrNull(MODELS.flatten().indexOf(key)), Modifier.fillMaxWidth(), contentScale = androidx.compose.ui.layout.ContentScale.Fit) }
    }
}

/** Отступы области графика под оси с подписями. */
private val AXIS_LEFT = 38.dp
private val AXIS_BOTTOM = 34.dp
private val AXIS_TOP = 14.dp

@Composable
private fun TypeCanvas(fat: Double, bmi: Double, trail: List<Pair<Double, Double>>, picked: Pair<Int, Int>, onPick: (Int, Int) -> Unit) {
    val low = WHTR_LOW
    val top = WHTR_HIGH
    val extra = LocalExtra.current
    val measurer = rememberTextMeasurer()
    val accent = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val all = trail + (fat to bmi)
    val xMin = minOf(0.32, all.minOf { it.first } - 0.02)
    val xMax = maxOf(0.66, all.maxOf { it.first } + 0.03)
    val yMin = minOf(16.0, all.minOf { it.second } - 1)
    val yMax = maxOf(32.0, all.maxOf { it.second } + 1.5)
    val tick = TextStyle(fontSize = 9.sp, color = extra.dim)
    val cellStyle = TextStyle(fontSize = 9.sp, color = extra.dim, textAlign = TextAlign.Center, lineHeight = 11.sp)
    val pickNow by androidx.compose.runtime.rememberUpdatedState(onPick)
    val xs = listOf(xMin, low, top, xMax)
    val ys = listOf(yMax, 25.0, 18.5, yMin) // строки сверху вниз: ИМТ выше нормы, норма, ниже
    Canvas(
        Modifier.fillMaxWidth().height(270.dp).padding(top = 4.dp).pointerInput(xMin, xMax, yMin, yMax) {
            // Касание по клетке — показать модель этого типа.
            detectTapGestures { o ->
                val left = AXIS_LEFT.toPx(); val topPad = AXIS_TOP.toPx()
                val w = size.width - left; val h = size.height - AXIS_BOTTOM.toPx() - topPad
                if (o.x < left || o.y < topPad || o.y > topPad + h) return@detectTapGestures
                val vx = xMin + (o.x - left) / w * (xMax - xMin)
                val vy = yMin + (1 - (o.y - topPad) / h) * (yMax - yMin)
                val col = when { vx < low -> 0; vx < top -> 1; else -> 2 }
                val row = when { vy >= 25.0 -> 0; vy >= 18.5 -> 1; else -> 2 }
                pickNow(row, col)
            }
        },
    ) {
        val left = AXIS_LEFT.toPx()
        val topPad = AXIS_TOP.toPx()
        val bottom = AXIS_BOTTOM.toPx()
        val w = size.width - left
        val h = size.height - bottom - topPad
        fun x(v: Double) = left + (w * ((v - xMin) / (xMax - xMin))).toFloat()
        fun y(v: Double) = topPad + (h * (1 - (v - yMin) / (yMax - yMin))).toFloat()
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
            val isPick = picked == row to col
            // Выбранный для просмотра тип (если это не ваш) — кремовая пунктирная рамка.
            if (isPick && !on) drawRoundRect(
                androidx.compose.ui.graphics.Color(0xFFFBDFAB), Offset(x0, y0), Size(x1 - x0, y1 - y0), CornerRadius(8.dp.toPx()),
                style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))),
            )
            val maxW = (x1 - x0 - 6.dp.toPx()).toInt()
            if (maxW > 20) {
                val t = measurer.measure(TYPES[row][col], if (on || isPick) cellStyle.copy(color = onSurface, fontWeight = FontWeight.SemiBold) else cellStyle, constraints = Constraints(maxWidth = maxW))
                val ty = if (row == 0) y0 + 4.dp.toPx() else if (row == 2) y1 - t.size.height - 4.dp.toPx() else (y0 + y1 - t.size.height) / 2
                if (t.size.height < y1 - y0) drawText(t, topLeft = Offset((x0 + x1 - t.size.width) / 2, ty))
            }
        }
        // Оси: ордината — ИМТ, абсцисса — талия / рост; со стрелками, делениями и подписями.
        val axis = onSurface.copy(alpha = .7f)
        val ax = left - 4.dp.toPx()
        val ay = topPad + h + 4.dp.toPx()
        val sw = 1.4.dp.toPx()
        val arrow = 6.dp.toPx()
        drawLine(axis, Offset(ax, ay), Offset(ax, 2.dp.toPx()), strokeWidth = sw)
        drawPath(Path().apply { moveTo(ax, 0f); lineTo(ax - arrow / 2, arrow); lineTo(ax + arrow / 2, arrow); close() }, axis)
        drawLine(axis, Offset(ax, ay), Offset(size.width - 2.dp.toPx(), ay), strokeWidth = sw)
        drawPath(Path().apply { moveTo(size.width, ay); lineTo(size.width - arrow, ay - arrow / 2); lineTo(size.width - arrow, ay + arrow / 2); close() }, axis)
        val bold = tick.copy(color = onSurface, fontWeight = FontWeight.SemiBold)
        // Деления ИМТ: границы зон ВОЗ и ожирение (30).
        listOf(18.5, 25.0, 30.0).filter { it > yMin && it < yMax }.forEach { v ->
            drawLine(axis, Offset(ax - 4.dp.toPx(), y(v)), Offset(ax, y(v)), strokeWidth = sw)
            val t = measurer.measure(fmt(v), tick)
            drawText(t, topLeft = Offset(ax - t.size.width - 6.dp.toPx(), y(v) - t.size.height / 2))
        }
        val yl = measurer.measure("ИМТ", bold)
        drawText(yl, topLeft = Offset(ax + 6.dp.toPx(), 0f))
        // Деления талии/роста: 0,40 и 0,50 — границы нормы, 0,60 — высокий риск.
        listOf(low, top, 0.6).filter { it > xMin && it < xMax }.forEach { v ->
            drawLine(axis, Offset(x(v), ay), Offset(x(v), ay + 4.dp.toPx()), strokeWidth = sw)
            val t = measurer.measure(fmt(v, 2), tick)
            drawText(t, topLeft = Offset(x(v) - t.size.width / 2, ay + 5.dp.toPx()))
        }
        val xl = measurer.measure("талия / рост", bold)
        drawText(xl, topLeft = Offset(size.width - xl.size.width - arrow, ay + 17.dp.toPx()))

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
