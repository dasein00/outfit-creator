@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.health

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.BodyMetric
import com.dasein.poryadok.data.BodyProfile
import com.dasein.poryadok.data.WeightEntry
import com.dasein.poryadok.logic.BodyComp
import com.dasein.poryadok.logic.BodyReading
import com.dasein.poryadok.logic.CalorieGoal
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.Person
import com.dasein.poryadok.logic.Tone
import com.dasein.poryadok.system.Body
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.DatePickDialog
import com.dasein.poryadok.ui.common.FieldButton
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.Glyphs
import com.dasein.poryadok.ui.common.HGap
import com.dasein.poryadok.ui.common.Hint
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Segments
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.TimePickDialog
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.common.rememberUiFlag
import com.dasein.poryadok.ui.theme.LocalExtra
import java.util.Date
import kotlin.math.abs
import kotlin.math.roundToInt

/** Все взвешивания: подробные записи весов плюс старые записи веса без подробностей. По возрастанию времени. */
internal fun mergeReadings(metrics: List<BodyMetric>, weights: List<WeightEntry>): List<BodyMetric> {
    val days = metrics.map { it.day }.toSet()
    return (metrics + weights.filter { it.day !in days }.map { BodyMetric(at = Dates.millis(it.day, 8 * 60), day = it.day, weight = it.kg, source = LEGACY) })
        .sortedBy { it.at }
}

internal data class BodyData(val profile: BodyProfile, val readings: List<BodyMetric>) {
    val person get() = Person(profile.male, profile.heightCm, profile.age)
}

@Composable
internal fun rememberBodyData(): BodyData {
    val profile by observe(null) { Graph.dao.profile() }
    val weights by observe(emptyList()) { Graph.dao.weights() }
    val metrics by observe(emptyList()) { Graph.extra.bodyMetrics() }
    val p = profile ?: BodyProfile()
    val readings = remember(metrics, weights) { mergeReadings(metrics, weights) }
    return BodyData(p, readings)
}

private fun hide(s: String, hidden: Boolean) = if (hidden) s.replace(Regex("\\d"), "*") else s

/** Верхняя карточка раздела (область 1): вес, статус, шкала. Нажатие открывает подробности. */
@Composable
internal fun BodyHeaderCard(last: BodyMetric, readings: List<BodyMetric>, profile: BodyProfile, onClick: () -> Unit) {
    val extra = LocalExtra.current
    var hidden by rememberUiFlag("hide_weight", false)
    val ws = BodyComp.weightScale(profile.heightCm)
    val zone = ws.zone(last.weight)
    val goal = runCatching { CalorieGoal.valueOf(profile.goal) }.getOrDefault(CalorieGoal.DEFICIT)
    Tile(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(last.source.substringBefore(" · ").ifBlank { "Весы" }, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(TIME.format(Date(last.at)), fontSize = 12.sp, color = extra.dim)
            }
            Glyph(Glyphs.SCALE, 26.dp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            IconButton(onClick = { hidden = !hidden }) {
                Icon(if (hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (hidden) "Показать вес" else "Скрыть вес", tint = extra.dim)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(hide(fmt(last.weight, 2), hidden), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.SemiBold)
                    Text(" кг", color = extra.dim, modifier = Modifier.padding(bottom = 10.dp))
                }
            }
            StatusChip(ws.labels[zone], ws.tones[zone])
        }
        Gap(4.dp)
        ScaleBar(ws, last.weight, 2, showBounds = false)
        Gap(8.dp)
        val prev = readings.lastOrNull { it.at < last.at }
        val month = readings.filter { it.day > Dates.today() - 30 }
        val best = if (goal == CalorieGoal.SURPLUS) month.maxByOrNull { it.weight } else month.minByOrNull { it.weight }
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(prev?.let { hide(signed(last.weight - it.weight), hidden) } ?: "—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Чем в прошлый раз", fontSize = 12.sp, color = extra.dim)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(best?.let { hide(fmt(it.weight), hidden) } ?: "—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Лучшее за 30 дней", fontSize = 12.sp, color = extra.dim)
            }
        }
        Text("Нажмите, чтобы открыть все показатели →", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
    }
}

/** Карточка тренда (область 2): изменение за год и маленький график. Нажатие открывает экран тренда. */
@Composable
fun WeightTrendCard(readings: List<BodyMetric>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val extra = LocalExtra.current
    val today = Dates.today()
    val pts = readings.filter { it.day > today - 365 }.let { if (it.size >= 2) it else readings.takeLast(30) }
    Tile(modifier, onClick = onClick, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph("ui:stats", 22.dp)
                    Text("  Тренд веса", fontWeight = FontWeight.SemiBold)
                }
                if (pts.size >= 2) {
                    val change = pts.last().weight - pts.first().weight
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp)) {
                        Text(signed(change), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = if (change > 0) extra.warn else extra.ok)
                        Text(" кг", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(bottom = 3.dp))
                    }
                    Text("с ${Dates.short(pts.first().day)} · сейчас ${fmt(pts.last().weight)} кг", fontSize = 11.sp, color = extra.dim)
                } else Text("Нужно хотя бы два взвешивания", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 6.dp))
            }
            if (pts.size >= 2) Sparkline(pts.map { it.weight.toFloat() }, Modifier.width(140.dp).height(56.dp))
        }
    }
}

private val WD = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

/**
 * Вес за неделю для главной: реальное значение каждого дня (последнее взвешивание дня),
 * изменение за неделю, минимум и максимум. Если за неделю взвешиваний мало — последние 7.
 */
@Composable
fun WeekWeightCard(readings: List<BodyMetric>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val extra = LocalExtra.current
    val today = Dates.today()
    val byDay = readings.groupBy { it.day }.mapValues { (_, l) -> l.maxBy { it.at }.weight }
    val week = (today - 6..today).mapNotNull { d -> byDay[d]?.let { d to it } }
    val pts = if (week.size >= 2) week else byDay.entries.sortedBy { it.key }.takeLast(7).map { it.key to it.value }
    val isWeek = week.size >= 2
    Tile(modifier, onClick = onClick, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph("ui:v_weight", 22.dp)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(if (isWeek) "Вес за неделю" else "Последние взвешивания", fontWeight = FontWeight.SemiBold)
                Text(if (isWeek) "${Dates.short(today - 6)} — ${Dates.short(today)}" else "за неделю меньше двух замеров", fontSize = 11.sp, color = extra.dim)
            }
            pts.lastOrNull()?.let { Text(fmt(it.second) + " кг", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
        }
        if (pts.size >= 2) {
            val change = pts.last().second - pts.first().second
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                @Composable
                fun Stat(value: String, label: String, color: Color = MaterialTheme.colorScheme.onSurface, mod: Modifier) {
                    Column(mod) {
                        Text(value, fontWeight = FontWeight.SemiBold, color = color)
                        Text(label, fontSize = 11.sp, color = extra.dim)
                    }
                }
                Stat(
                    (if (abs(change) < 0.05) "0,0" else signed(change)) + " кг", "изменение",
                    if (change > 0.05) extra.warn else if (change < -0.05) extra.ok else MaterialTheme.colorScheme.onSurface, Modifier.weight(1f),
                )
                Stat(fmt(pts.minOf { it.second }), "минимум", mod = Modifier.weight(1f))
                Stat(fmt(pts.maxOf { it.second }), "максимум", mod = Modifier.weight(1f))
                Stat("${pts.size}", "замеров", mod = Modifier.weight(.7f))
            }
            TrendChart(
                pts, false, Modifier.fillMaxWidth().height(170.dp).padding(top = 6.dp),
                dayLabels = { d -> val x = Dates.day(d); WD[x.dayOfWeek.value - 1] + " " + x.dayOfMonth },
            )
        } else Text("Добавьте хотя бы два взвешивания — здесь появится график.", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun Sparkline(values: List<Float>, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        var lo = values.min(); var hi = values.max()
        if (hi - lo < .5f) { hi += .25f; lo -= .25f }
        fun x(i: Int) = i * size.width / (values.size - 1)
        fun y(v: Float) = size.height - (v - lo) / (hi - lo) * size.height * .85f - size.height * .05f
        val line = Path().apply { values.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) } }
        val area = Path().apply { addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = .25f), color.copy(alpha = 0f))))
        drawPath(line, color, style = Stroke(2.dp.toPx()))
        drawCircle(color, 3.dp.toPx(), Offset(x(values.lastIndex), y(values.last())))
    }
}

/** Ваша модель из гардероба с «заливкой» по доле воды — как фигура в отчёте весов. */
@Composable
internal fun ModelFigure(fill: Float, modifier: Modifier) {
    val ctx = LocalContext.current
    val tint = Color(0xFF6FA8DC)
    val img by produceState<ImageBitmap?>(null) {
        value = runCatching { ctx.assets.open("wardrobe/model.webp").use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
    }
    val bmp = img ?: run { Glyph("train/16", 84.dp, badge = false); return }
    Image(
        bmp, "Моя модель", contentScale = ContentScale.Fit,
        modifier = modifier
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                val top = size.height * (1f - fill.coerceIn(0f, 1f))
                drawRect(tint.copy(alpha = .45f), Offset(0f, top), Size(size.width, size.height - top), blendMode = BlendMode.SrcAtop)
            },
    )
}

/** Подробности взвешивания (экран «Детали»): вкладки «Показатели тела» и «Анализ отчёта». */
@Composable
fun BodyDetailScreen(nav: NavHostController, at: Long) {
    val data = rememberBodyData()
    val extra = LocalExtra.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var edit by remember { mutableStateOf<BodyMetric?>(null) }
    var openAll by rememberUiFlag("body_open_all", false)
    val m = data.readings.firstOrNull { it.at == at } ?: data.readings.lastOrNull()
    Screen(
        "Детали", onBack = { nav.popBackStack() },
        actions = {
            if (m != null) TextButton(onClick = { edit = m }) { Text("Изменить") }
            TextButton(onClick = { nav.navigate(Routes.BODY_COMPARE) }) { Text("Сравнить") }
        },
    ) { pad ->
        if (m == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { Text("Взвешиваний пока нет", color = extra.dim) }
            return@Screen
        }
        val r = m.reading()
        val p = data.person
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Segments(listOf(0 to "Показатели тела", 1 to "Анализ отчёта"), tab, { tab = it })
            Gap(10.dp)
            if (tab == 0) {
                val ws = BodyComp.weightScale(data.profile.heightCm)
                val zone = ws.zone(m.weight)
                Tile {
                    StatusChip(ws.labels[zone], ws.tones[zone])
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp)) {
                        Text(fmt(m.weight, 2), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                        Text(" кг", color = extra.dim, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    Text(TIME.format(Date(m.at)) + " · " + m.source, fontSize = 12.sp, color = extra.dim)
                    Gap(10.dp)
                    ScaleBar(ws, m.weight, 2)
                }
                Gap(8.dp)
                Tile {
                    val prev = data.readings.lastOrNull { it.at < m.at }
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("По сравнению с прошлым разом" + (prev?.let { " (${Dates.short(it.day)})" } ?: ""), Modifier.weight(1f), fontSize = 14.sp)
                        Text(prev?.let { signed(m.weight - it.weight) } ?: "—", fontWeight = FontWeight.SemiBold)
                    }
                    val month = data.readings.filter { it.day in (m.day - 29)..m.day }
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("Лучший вес за 30 дней", Modifier.weight(1f), fontSize = 14.sp)
                        Text(month.minByOrNull { it.weight }?.let { fmt(it.weight) } ?: "—", fontWeight = FontWeight.SemiBold)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SectionTitle("Показатели тела") }
                    TextButton(onClick = { openAll = !openAll }) { Text(if (openAll) "Свернуть все" else "Развернуть все") }
                }
                MetricsList(BodyComp.metrics(r, p), openAll, title = null)
                if (r.fatPct == null) {
                    Gap(8.dp)
                    Hint("body_manual", "Весы OKOK показывают все эти показатели в своём приложении, но Google Fit передаёт только вес, жир, кости, безжировую массу, обмен и воду. Остальное можно вписать кнопкой «Изменить» вверху.")
                }
            } else {
                BodyComposition(r, p)
                BodyTypeGrid(r, p)
                AdviceCard(r, p)
            }
            Gap(24.dp)
        }
    }
    edit?.let { BodyDialog(it) { edit = null } }
}

@Composable
private fun AdviceCard(r: BodyReading, p: Person) {
    val extra = LocalExtra.current
    val a = BodyComp.advice(r, p)
    SectionTitle("Рекомендации по контролю веса")
    Tile {
        @Composable
        fun Line(glyph: String, title: String, delta: Double?, unit: String) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                Glyph(glyph, 26.dp)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    if (delta != null) {
                        val frac = (1f - (abs(delta) / (r.weight * .35)).toFloat()).coerceIn(.05f, 1f)
                        Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(extra.line)) {
                            Box(Modifier.fillMaxWidth(frac).height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.primary))
                        }
                    }
                }
                Text(delta?.let { signed(it) + " " + unit } ?: "—", fontWeight = FontWeight.SemiBold, fontSize = 18.sp, modifier = Modifier.padding(start = 10.dp))
            }
        }
        Line(Glyphs.SCALE, "Вес", a.weightDelta, "кг")
        Text(
            "Идеальный вес для роста ${p.heightCm.roundToInt()} см — ${fmt(a.ideal)} кг. " + when {
                a.weightDelta < -0.5 -> "Чтобы достичь его, нужно сбросить ${fmt(-a.weightDelta)} кг: следите за рационом и умеренно увеличьте активность."
                a.weightDelta > 0.5 -> "До него не хватает ${fmt(a.weightDelta)} кг: добавьте калорий и силовых тренировок."
                else -> "Вы в пределах идеального веса — поддерживайте его."
            },
            fontSize = 13.sp, color = extra.dim, modifier = Modifier.padding(bottom = 6.dp),
        )
        Line("sport/12", "Мышечная масса", a.muscleDelta, "кг")
        Line("train/26", "Жиры", a.fatDelta, "кг")
        Hint("body_advice", "Цель по жиру — верхняя граница здоровой зоны (${fmt(BodyComp.healthyFatTop(p))}%) при идеальном весе. Мышечная масса — сколько её останется при таком составе. Это ориентир, а не норма: при силовых тренировках нормально весить больше идеального.", title = "Как считается")
    }
}

private enum class TrendPeriod(val label: String) { DAY("День"), WEEK("Неделя"), MONTH("Месяц"), CUSTOM("Свои") }

/** Экран тренда (область 2): период, график с подписями точек, среднее, изменение, максимум и минимум. */
@Composable
fun WeightTrendScreen(nav: NavHostController) {
    val data = rememberBodyData()
    val extra = LocalExtra.current
    val today = Dates.today()
    var period by rememberSaveable { mutableStateOf(TrendPeriod.CUSTOM) }
    var metric by rememberSaveable { mutableStateOf("weight") }
    val first = data.readings.firstOrNull()?.day ?: today - 30
    var from by rememberSaveable(first) { mutableStateOf(maxOf(first, today - 365)) }
    var to by rememberSaveable { mutableStateOf(today) }
    var pick by remember { mutableIntStateOf(0) }
    val range = when (period) {
        TrendPeriod.DAY -> today - 6..today
        TrendPeriod.WEEK -> today - 7 * 12 + 1..today
        TrendPeriod.MONTH -> today - 364..today
        TrendPeriod.CUSTOM -> from..maxOf(from, to)
    }
    fun value(m: BodyMetric): Double? = when (metric) {
        "weight" -> m.weight
        "fat" -> m.fatPct
        "bmi" -> BodyComp.bmi(m.weight, data.profile.heightCm)
        "muscle" -> m.muscleKg ?: m.musclePct?.let { m.weight * it / 100 }
        "visceral" -> m.visceral
        else -> m.waterPct
    }
    val pts = data.readings.filter { it.day in range }.mapNotNull { m -> value(m)?.let { m.day to it } }
    // День — каждое взвешивание (последнее за день), неделя и месяц — средние.
    val series: List<Pair<Long, Double>> = when (period) {
        TrendPeriod.WEEK -> pts.groupBy { Dates.weekStart(it.first) }.toSortedMap().map { (w, l) -> w to l.map { it.second }.average() }
        TrendPeriod.MONTH -> pts.groupBy { Dates.day(it.first).withDayOfMonth(1).toEpochDay() }.toSortedMap().map { (m, l) -> m to l.map { it.second }.average() }
        else -> pts.groupBy { it.first }.toSortedMap().map { (d, l) -> d to l.last().second }
    }
    Screen("Тренд", onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Segments(TrendPeriod.entries.map { it to it.label }, period, { period = it })
            Gap(8.dp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("weight" to "Вес", "fat" to "Жир %", "bmi" to "ИМТ", "muscle" to "Мышцы кг", "water" to "Вода %", "visceral" to "Висц. жир")
                    .forEach { (k, l) -> Pill(l, metric == k) { metric = k } }
            }
            if (period == TrendPeriod.CUSTOM) Row(Modifier.padding(top = 8.dp)) {
                FieldButton("С", Dates.full(from), Modifier.weight(1f)) { pick = 1 }
                HGap(8.dp)
                FieldButton("По", Dates.full(to), Modifier.weight(1f)) { pick = 2 }
            }
            Gap(10.dp)
            Tile {
                Text("${Dates.full(range.first)} — ${Dates.full(range.last)}", fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Gap(8.dp)
                if (series.size < 2) Text("Для графика нужно хотя бы два значения за период.", color = extra.dim, fontSize = 13.sp)
                else TrendChart(series, period == TrendPeriod.MONTH, Modifier.fillMaxWidth().height(260.dp))
            }
            val t = BodyComp.trend(pts, range.first, range.last)
            if (t != null) {
                Gap(10.dp)
                Tile {
                    Text("За ${range.last - range.first + 1} дн. · ${t.count} измерений", fontSize = 12.sp, color = extra.dim)
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        Column(Modifier.weight(1f)) { Text(fmt(t.avg), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold); Text("Среднее", fontSize = 12.sp, color = extra.dim) }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text((if (t.change < 0) "↓ " else if (t.change > 0) "↑ " else "") + fmt(abs(t.change)), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = if (t.change > 0 && metric != "muscle") extra.warn else extra.ok)
                            Text("Изменение", fontSize = 12.sp, color = extra.dim)
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Column(Modifier.weight(1f)) { Text(fmt(t.max.second), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold); Text("Максимум · ${Dates.full(t.max.first)}", fontSize = 12.sp, color = extra.dim) }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) { Text(fmt(t.min.second), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold); Text("Минимум · ${Dates.full(t.min.first)}", fontSize = 12.sp, color = extra.dim) }
                    }
                }
            }
            Gap(24.dp)
        }
    }
    if (pick == 1) DatePickDialog(from, { pick = 0 }, { it?.let { d -> from = d } }, allowClear = false)
    if (pick == 2) DatePickDialog(to, { pick = 0 }, { it?.let { d -> to = d } }, allowClear = false)
}

/** График с плавной линией, заливкой, точками и подписями значений. */
@Composable
fun TrendChart(points: List<Pair<Long, Double>>, monthLabels: Boolean, modifier: Modifier, dayLabels: ((Long) -> String)? = null) {
    val color = MaterialTheme.colorScheme.primary
    val extra = LocalExtra.current
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = extra.dim)
    val valueStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
    Canvas(modifier) {
        val left = 36.dp.toPx(); val bottom = 22.dp.toPx(); val top = 16.dp.toPx(); val right = 12.dp.toPx()
        val w = size.width - left - right; val h = size.height - top - bottom
        val vals = points.map { it.second }
        var lo = vals.min(); var hi = vals.max()
        val pad = ((hi - lo) * .15).coerceAtLeast(.5)
        lo -= pad; hi += pad
        fun x(i: Int) = left + if (points.size == 1) w / 2 else i * w / (points.size - 1)
        fun y(v: Double) = top + h - ((v - lo) / (hi - lo) * h).toFloat()
        // Сетка и подписи оси значений.
        for (k in 0..4) {
            val v = lo + (hi - lo) * k / 4
            val yy = y(v)
            drawLine(extra.line, Offset(left, yy), Offset(left + w, yy), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            val t = measurer.measure("%.1f".format(v), labelStyle)
            drawText(t, topLeft = Offset(left - t.size.width - 6f, yy - t.size.height / 2))
        }
        val line = Path()
        points.forEachIndexed { i, p ->
            val px = x(i); val py = y(p.second)
            if (i == 0) line.moveTo(px, py) else {
                val qx = x(i - 1); val qy = y(points[i - 1].second)
                val cx = (qx + px) / 2
                line.cubicTo(cx, qy, cx, py, px, py)
            }
        }
        val area = Path().apply { addPath(line); lineTo(x(points.lastIndex), top + h); lineTo(x(0), top + h); close() }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = .18f), color.copy(alpha = .02f)), startY = top, endY = top + h))
        drawPath(line, color, style = Stroke(2.5.dp.toPx()))
        // Точки и подписи: все, если их немного, иначе первая, последняя, максимум и минимум.
        val maxI = vals.indices.maxBy { vals[it] }; val minI = vals.indices.minBy { vals[it] }
        val show = if (points.size <= 12) points.indices.toSet() else setOf(0, points.lastIndex, maxI, minI)
        points.forEachIndexed { i, p ->
            val c = Offset(x(i), y(p.second))
            drawCircle(Color.White, 4.dp.toPx(), c)
            drawCircle(color, 4.dp.toPx(), c, style = Stroke(2.dp.toPx()))
            if (i in show) {
                val t = measurer.measure("%.1f".format(p.second), valueStyle)
                drawText(t, topLeft = Offset((c.x - t.size.width / 2).coerceIn(0f, size.width - t.size.width), c.y - t.size.height - 6f))
            }
        }
        // Подписи дат: каждая точка, если задан формат дня, иначе первая, середина и последняя.
        (if (dayLabels != null) points.indices.toList() else listOf(0, points.size / 2, points.lastIndex).distinct()).forEach { i ->
            val d = Dates.day(points[i].first)
            val s = dayLabels?.invoke(points[i].first) ?: if (monthLabels) "%02d.%02d".format(d.monthValue, d.year % 100) else "%02d.%02d".format(d.dayOfMonth, d.monthValue)
            val t = measurer.measure(s, labelStyle)
            drawText(t, topLeft = Offset((x(i) - t.size.width / 2).coerceIn(0f, size.width - t.size.width), top + h + 6f))
        }
    }
}

/** Отчёт о сравнении двух взвешиваний («до» и «после»). */
@Composable
fun BodyCompareScreen(nav: NavHostController) {
    val data = rememberBodyData()
    val extra = LocalExtra.current
    val list = data.readings
    var aAt by rememberSaveable { mutableStateOf(0L) }
    var bAt by rememberSaveable { mutableStateOf(0L) }
    var picking by remember { mutableIntStateOf(0) }
    val b = list.firstOrNull { it.at == bAt } ?: list.lastOrNull()
    val a = list.firstOrNull { it.at == aAt } ?: list.lastOrNull { b != null && it.at < b.at }
    Screen("Сравнение замеров", onBack = { nav.popBackStack() }) { pad ->
        if (a == null || b == null) {
            Box(Modifier.padding(pad).fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Для сравнения нужно хотя бы два взвешивания.", color = extra.dim, textAlign = TextAlign.Center)
            }
            return@Screen
        }
        val p = data.person
        val diffs = BodyComp.compare(a.reading(), b.reading(), p)
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            item {
                Tile {
                    val days = abs(b.day - a.day)
                    Text(if (days == 0L) "В один день" else "За ${days} ${com.dasein.poryadok.logic.plural(days.toInt(), "день", "дня", "дней")}", fontWeight = FontWeight.SemiBold, fontSize = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        listOf("weight" to "вес (кг)", "bmi" to "ИМТ", "fat" to "жир (%)").forEach { (k, l) ->
                            val d = diffs.firstOrNull { it.key == k }
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(d?.delta?.let { if (abs(it) < 0.05) "0,0" else signed(it) } ?: "—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                                    color = when (d?.better) { true -> extra.ok; false -> extra.danger; null -> MaterialTheme.colorScheme.onSurface })
                                Text(l, fontSize = 12.sp, color = extra.dim)
                            }
                        }
                    }
                }
                Gap(10.dp)
                Row {
                    FieldButton("До", TIME.format(Date(a.at)), Modifier.weight(1f)) { picking = 1 }
                    HGap(8.dp)
                    FieldButton("После", TIME.format(Date(b.at)), Modifier.weight(1f)) { picking = 2 }
                }
                Gap(10.dp)
            }
            items(diffs, key = { it.key }) { d ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(d.before?.let { fmt(it, d.decimals) } ?: "—", Modifier.width(70.dp), fontWeight = FontWeight.SemiBold)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(d.title, fontSize = 13.sp, color = extra.dim, textAlign = TextAlign.Center)
                        Text(
                            d.delta?.let { if (abs(it) < 0.05) "0,0" else signed(it) } ?: "—", fontWeight = FontWeight.SemiBold,
                            color = when (d.better) { true -> extra.ok; false -> extra.danger; null -> extra.dim },
                        )
                    }
                    Text(d.after?.let { fmt(it, d.decimals) } ?: "—", Modifier.width(70.dp), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
                }
            }
            item {
                Gap(8.dp)
                Text("Зелёным — изменение в лучшую сторону, красным — в худшую.", fontSize = 12.sp, color = extra.dim)
            }
        }
    }
    if (picking != 0) AlertDialog(
        onDismissRequest = { picking = 0 },
        title = { Text(if (picking == 1) "Замер «до»" else "Замер «после»") },
        text = {
            LazyColumn {
                items(list.reversed(), key = { it.at }) { m ->
                    Text(
                        TIME.format(Date(m.at)) + " · " + fmt(m.weight) + " кг" + (m.fatPct?.let { " · жир ${fmt(it)}%" } ?: ""),
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                            if (picking == 1) aAt = m.at else bAt = m.at
                            picking = 0
                        }.padding(vertical = 10.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { picking = 0 }) { Text("Закрыть") } },
    )
}

/** Быстрое добавление веса линейкой, как в приложении весов. «Подробнее» открывает все показатели. */
@Composable
internal fun WeightRulerDialog(initial: Double, onDismiss: () -> Unit, onMore: (BodyMetric) -> Unit) {
    val extra = LocalExtra.current
    var value by remember { mutableFloatStateOf(((initial * 10).roundToInt() / 10.0).toFloat()) }
    var at by remember { mutableStateOf(System.currentTimeMillis()) }
    var pickDay by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val color = MaterialTheme.colorScheme.primary
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 12.sp, color = extra.dim)
    fun snapped() = (value * 10).roundToInt() / 10.0
    fun metric() = BodyMetric(at = at, day = Dates.dayOf(at), weight = snapped(), source = "вручную")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить вес", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row {
                    FieldButton("Дата", Dates.label(Dates.dayOf(at)), Modifier.weight(1f)) { pickDay = true }
                    HGap(8.dp)
                    FieldButton("Время", Dates.time(Dates.minutesOf(at)), Modifier.weight(1f)) { pickTime = true }
                }
                Gap(8.dp)
                Text(fmt(snapped()), fontSize = 56.sp, fontWeight = FontWeight.Bold, color = color)
                Text("кг", color = color, fontWeight = FontWeight.SemiBold)
                Gap(8.dp)
                Canvas(
                    Modifier.fillMaxWidth().height(90.dp).pointerInput(Unit) {
                        detectHorizontalDragGestures { change, drag ->
                            change.consume()
                            value = (value - drag / 14.dp.toPx() * 0.1f).coerceIn(20f, 250f)
                        }
                    },
                ) {
                    val step = 14.dp.toPx()
                    val cx = size.width / 2
                    val v10 = value * 10
                    val first = (v10 - cx / step).toInt() - 1
                    val last = (v10 + cx / step).toInt() + 1
                    for (t in first..last) {
                        val x = cx + (t - v10) * step
                        val long = t % 10 == 0
                        val mid = t % 5 == 0
                        val len = if (long) 34.dp.toPx() else if (mid) 24.dp.toPx() else 14.dp.toPx()
                        drawLine(extra.dim, Offset(x, 30.dp.toPx()), Offset(x, 30.dp.toPx() + len), if (long) 3f else 2f)
                        if (long) {
                            val tl = measurer.measure("${t / 10}", labelStyle)
                            drawText(tl, topLeft = Offset(x - tl.size.width / 2, 30.dp.toPx() + 38.dp.toPx()))
                        }
                    }
                    // Стрелка-указатель.
                    drawLine(Color.Black.copy(alpha = .85f), Offset(cx, 4.dp.toPx()), Offset(cx, 66.dp.toPx()), 7f)
                    val arrow = Path().apply { moveTo(cx - 9.dp.toPx(), 14.dp.toPx()); lineTo(cx, 0f); lineTo(cx + 9.dp.toPx(), 14.dp.toPx()); close() }
                    drawPath(arrow, Color.Black.copy(alpha = .85f))
                }
                Text("Двигайте линейку влево и вправо", fontSize = 12.sp, color = extra.dim)
                Row(Modifier.padding(top = 6.dp)) {
                    listOf(-1.0f, -0.1f, 0.1f, 1.0f).forEach { d ->
                        OutlinedButton(onClick = { value = (value + d).coerceIn(20f, 250f) }, Modifier.padding(horizontal = 3.dp)) {
                            Text((if (d > 0) "+" else "−") + fmt(abs(d).toDouble()), fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { val m = metric(); io { Body.save(m) }; onDismiss() }) { Text("Подтвердить") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onMore(metric()) }) { Text("Подробнее…") }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
    if (pickDay) DatePickDialog(Dates.dayOf(at), { pickDay = false }, { it?.let { d -> at = Dates.millis(d, Dates.minutesOf(at)) } }, allowClear = false)
    if (pickTime) TimePickDialog(Dates.minutesOf(at), { pickTime = false }, { it?.let { t -> at = Dates.millis(Dates.dayOf(at), t) } }, allowClear = false)
}
