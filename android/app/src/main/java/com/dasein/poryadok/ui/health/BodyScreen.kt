@file:OptIn(ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.health

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.Hint
import com.dasein.poryadok.data.BodyMetric
import com.dasein.poryadok.data.BodyProfile
import com.dasein.poryadok.data.WeightEntry
import com.dasein.poryadok.logic.BodyComp
import com.dasein.poryadok.logic.BodyMetricView
import com.dasein.poryadok.logic.BodyReading
import com.dasein.poryadok.logic.CalorieGoal
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.Nutrition
import com.dasein.poryadok.logic.Person
import com.dasein.poryadok.logic.Scale
import com.dasein.poryadok.logic.Tone
import com.dasein.poryadok.system.Body
import com.dasein.poryadok.system.Steps
import com.dasein.poryadok.ui.common.DatePickDialog
import com.dasein.poryadok.ui.common.Empty
import com.dasein.poryadok.ui.common.FieldButton
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.Glyphs
import com.dasein.poryadok.ui.common.HGap
import com.dasein.poryadok.ui.common.LineChart
import com.dasein.poryadok.ui.common.NumberField
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Segments
import com.dasein.poryadok.ui.common.Series
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.TimePickDialog
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.num
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

fun toneColor(t: Tone?): Color = when (t) {
    Tone.BLUE -> Color(0xFF6FB2D6)
    Tone.GREEN -> Color(0xFF7DBB6A)
    Tone.YELLOW -> Color(0xFFE4B64C)
    Tone.RED -> Color(0xFFD25B4B)
    null -> Color(0xFF9A9185)
}

internal fun fmt(v: Double, d: Int = 1) = if (d == 0) "%.0f".format(v) else "%.${d}f".format(v).replace('.', ',')
internal fun signed(v: Double) = (if (v > 0) "+" else if (v < 0) "−" else "") + fmt(abs(v))
/** Вес из прежних версий, без подробных показателей. */
internal const val LEGACY = "вес"

@Composable
private fun MetricField(state: androidx.compose.runtime.MutableState<Map<String, String>>, key: String, label: String, suffix: String) {
    NumberField(state.value.getValue(key), { v -> state.value = state.value + (key to v) }, label, suffix = suffix)
    Gap(4.dp)
}

internal val TIME = SimpleDateFormat("d MMM yyyy, HH:mm", Locale("ru"))

internal fun BodyMetric.reading() = BodyReading(
    weight, fatPct, musclePct, muscleKg, waterPct, proteinPct, boneKg, visceral, bmr, metabolicAge, subcutaneousPct, leanKg,
)

/** Шкала из цветных зон с отметкой значения — как в приложении весов. */
@Composable
fun ScaleBar(scale: Scale, value: Double, decimals: Int = 1, showBounds: Boolean = true) {
    val extra = LocalExtra.current
    val lo = scale.bounds.first() - (scale.bounds.last() - scale.bounds.first()).coerceAtLeast(scale.bounds.first() * .2) / scale.bounds.size.coerceAtLeast(1)
    val hi = scale.bounds.last() + (scale.bounds.last() - scale.bounds.first()).coerceAtLeast(scale.bounds.last() * .2) / scale.bounds.size.coerceAtLeast(1)
    val edges = listOf(lo) + scale.bounds + listOf(hi)
    val n = scale.labels.size
    Column(Modifier.fillMaxWidth()) {
        if (showBounds) Row(Modifier.fillMaxWidth()) {
            Box(Modifier.weight(.5f))
            scale.bounds.forEach { b -> Text(fmt(b, decimals), Modifier.weight(1f), fontSize = 10.sp, color = extra.dim, textAlign = TextAlign.Center) }
            Box(Modifier.weight(.5f))
        }
        Canvas(Modifier.fillMaxWidth().height(18.dp)) {
            val gap = 4.dp.toPx()
            val segW = (size.width - gap * (n - 1)) / n
            val h = 6.dp.toPx()
            val y = size.height / 2 - h / 2
            for (i in 0 until n) {
                drawRoundRect(toneColor(scale.tones[i]), Offset(i * (segW + gap), y), Size(segW, h), CornerRadius(h / 2))
            }
            val z = scale.zone(value)
            val frac = ((value - edges[z]) / (edges[z + 1] - edges[z])).coerceIn(0.0, 1.0)
            val x = z * (segW + gap) + (segW * frac).toFloat()
            drawCircle(Color.White, 8.dp.toPx(), Offset(x, size.height / 2))
            drawCircle(toneColor(scale.tones[z]), 6.dp.toPx(), Offset(x, size.height / 2))
        }
        Row(Modifier.fillMaxWidth()) {
            scale.labels.forEach { l -> Text(l, Modifier.weight(1f), fontSize = 10.sp, color = extra.dim, textAlign = TextAlign.Center, maxLines = 1) }
        }
    }
}

@Composable
fun StatusChip(label: String, tone: Tone?) {
    Text(
        label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1B1812),
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(toneColor(tone)).padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
fun BodyTab(nav: NavHostController, profile: BodyProfile, weights: List<WeightEntry>) {
    val extra = LocalExtra.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val metricsDb by observe(emptyList()) { Graph.extra.bodyMetrics() }
    var edit by remember { mutableStateOf<BodyMetric?>(null) }
    var ruler by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var syncs by remember { mutableStateOf(0) }
    val goal = runCatching { CalorieGoal.valueOf(profile.goal) }.getOrDefault(CalorieGoal.DEFICIT)

    val readings = remember(metricsDb, weights) { mergeReadings(metricsDb, weights) }
    val last = readings.lastOrNull()

    fun sync() {
        syncing = true
        scope.launch {
            val n = runCatching { Body.syncHealthConnect(ctx) }.getOrElse { -1 }
            syncing = false
            syncs++
            Toast.makeText(ctx, if (n < 0) "Не удалось прочитать Health Connect" else if (n == 0) "Новых взвешиваний нет" else "Добавлено взвешиваний: $n", Toast.LENGTH_SHORT).show()
        }
    }

    Gap(8.dp)
    if (last == null) {
        Empty(Glyphs.SCALE, "Нет взвешиваний", "Добавьте вес вручную — или подключите весы через Health Connect, и показатели будут подтягиваться сами.")
    } else {
        BodyHeaderCard(last, readings, profile) { nav.navigate(Routes.bodyDetail(last.at)) }
    }
    Gap(8.dp)
    Row {
        Button(onClick = { ruler = true }, Modifier.weight(1f)) { Text("+ Взвешивание") }
        HGap(8.dp)
        OutlinedButton(onClick = { nav.navigate(Routes.BODY_COMPARE) }, enabled = readings.size >= 2, modifier = Modifier.weight(1f)) { Text("Сравнить") }
    }
    Gap(8.dp)
    Tile(onClick = { nav.navigate(Routes.BODY_SCIENCE) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph("ui:v_chart", 26.dp)
            HGap(10.dp)
            Column(Modifier.weight(1f)) {
                Text("Научный анализ по росту и весу", fontWeight = FontWeight.SemiBold)
                Text("ИМТ по ВОЗ, талия/рост, процент жира, базовый обмен, белок и вода — с источниками", fontSize = 12.sp, color = extra.dim)
            }
        }
    }
    Gap(8.dp)
    WeightTrendCard(readings, { nav.navigate(Routes.WEIGHT_TREND) })
    Gap(8.dp)
    HcLinkCard(syncs, syncing, onSync = { sync() }) { io { Graph.prefs.update { it.copy(bodyHc = true) } }; sync() }

    SectionTitle("План и факт по неделям")
    Tile {
        val start = if (profile.startDay > 0) profile.startDay else weights.firstOrNull()?.day ?: Dates.today()
        val planPts = (0..12).map { Nutrition.plannedWeight(profile.startWeight, profile.changeKg, goal, it).toFloat() }
        val factPts = (0..12).map { w -> val from = start + w * 7L; weights.filter { it.day in from..(from + 6) }.lastOrNull()?.kg?.toFloat() }
        LineChart(listOf(Series(planPts, extra.dim, dashed = true), Series(factPts, MaterialTheme.colorScheme.primary)), labels = (0..12 step 2).map { "н${it + 1}" })
        Text("— — план   ● факт", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 6.dp))
    }

    SectionTitle("История")
    readings.reversed().take(60).forEach { m ->
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { nav.navigate(Routes.bodyDetail(m.at)) }.padding(vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("${fmt(m.weight)} кг" + (m.fatPct?.let { " · жир ${fmt(it)}%" } ?: ""), fontWeight = FontWeight.Medium)
                Text(TIME.format(Date(m.at)) + " · " + m.source, fontSize = 12.sp, color = extra.dim)
            }
            TextButton(onClick = { edit = m }) { Text("Изменить", fontSize = 12.sp) }
        }
    }
    if (ruler) WeightRulerDialog(
        last?.weight ?: profile.startWeight, { ruler = false },
        heightCm = profile.heightCm,
    ) { m -> ruler = false; edit = m }
    edit?.let { BodyDialog(it) { edit = null } }
}

@Composable
internal fun BodyComposition(r: BodyReading, p: Person) {
    val extra = LocalExtra.current
    val c = BodyComp.composition(r)
    val m = BodyComp.metrics(r, p).associateBy { it.key }
    SectionTitle("Анализ состава тела")
    Tile {
        Text("Вес = Вода + Жир + Белок + Кость", fontSize = 12.sp, color = extra.dim)
        Gap(8.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            ModelFigure(((r.waterPct ?: 50.0) / 100).toFloat(), Modifier.width(88.dp).height(250.dp))
            HGap(12.dp)
            Column(Modifier.weight(1f)) {
                Text(fmt(r.weight) + " кг", style = MaterialTheme.typography.titleLarge, modifier = Modifier.align(Alignment.End))
                listOf(
                    Triple("Вода", c.water, m.getValue("water")), Triple("Жиры", c.fat, m.getValue("fat")),
                    Triple("Белок", c.protein, m.getValue("protein")), Triple("Костная масса", c.bone, m.getValue("bone")),
                ).forEach { (title, kg, view) ->
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Text(title, Modifier.weight(1f), fontSize = 13.sp)
                        Text(view.label ?: "нет данных", fontSize = 12.sp, color = if (view.tone != null) toneColor(view.tone) else extra.dim)
                    }
                    val frac = if (kg != null) (kg / r.weight * 2.2).toFloat().coerceIn(.05f, 1f) else 0f
                    Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(extra.line)) {
                        Box(Modifier.fillMaxWidth(frac).height(14.dp).clip(RoundedCornerShape(7.dp)).background(toneColor(view.tone ?: Tone.BLUE).copy(alpha = .8f))) {
                            if (kg != null) Text(fmt(kg), fontSize = 10.sp, color = Color(0xFF1B1812), modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        }
        if (c.water == null && c.protein == null) Hint(
            "body_water", "Воду и белок весы OKOK показывают в своём приложении — впишите их в «Изменить», и анализ станет полным. Фигура — ваша модель из гардероба, заливка показывает долю воды.",
            Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
internal fun BodyTypeGrid(r: BodyReading, p: Person) {
    val extra = LocalExtra.current
    val cell = BodyComp.bodyType(r.weight, r.fatPct, p)
    SectionTitle("Анализ типа телосложения")
    Tile {
        if (cell == null) {
            Text("Нужен процент жира — с весов или вручную.", color = extra.dim, fontSize = 13.sp)
            return@Tile
        }
        Row {
            Column(Modifier.width(26.dp).padding(top = 4.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text("ИМТ", fontSize = 10.sp, color = extra.dim)
            }
            Column(Modifier.weight(1f)) {
                BodyComp.BODY_TYPES.forEachIndexed { row, names ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                        names.forEachIndexed { col, name ->
                            val on = cell == row to col
                            Box(
                                Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(10.dp))
                                    .background(if (on) toneColor(if (row == 1 && col == 1) Tone.GREEN else Tone.RED).copy(alpha = .22f) else extra.card)
                                    .border(if (on) 2.dp else 1.dp, if (on) toneColor(if (row == 1 && col == 1) Tone.GREEN else Tone.RED) else extra.line, RoundedCornerShape(10.dp))
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(name, fontSize = 11.sp, textAlign = TextAlign.Center, lineHeight = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                    }
                }
                Text("Жир →", fontSize = 10.sp, color = extra.dim, modifier = Modifier.align(Alignment.End))
            }
        }
        Text(BodyComp.BODY_TYPES[cell.first][cell.second], style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
        Text(BodyComp.typeAbout(cell.first, cell.second), fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        Hint("body_type_grid", "Строки — ИМТ (выше нормы, норма, ниже), столбцы — процент жира (мало, норма, много).", Modifier.padding(top = 8.dp), title = "Как читать таблицу")
    }
}

@Composable
internal fun MetricsList(list: List<BodyMetricView>, openAll: Boolean = false, title: String? = "Показатели тела") {
    val extra = LocalExtra.current
    var open by remember { mutableStateOf<String?>(null) }
    val icons = mapOf(
        "weight" to "sport/11", "bmi" to "train/28", "fat" to "train/26", "fatKg" to "train/27", "skeletal" to "sport/12",
        "muscleKg" to "train/05", "water" to "ui:drop", "protein" to "train/25", "bone" to "train/16", "visceral" to "sport/32",
        "subcut" to "train/26", "lean" to "sport/25", "bmr" to "sport/22", "metaAge" to "cal/12",
        "skeletalKg" to "sport/12", "muscleRate" to "train/05", "waterKg" to "ui:drop", "obesity" to "train/27",
        "age" to "cal/12", "height" to "train/28",
    )
    if (title != null) SectionTitle(title)
    Tile(padding = 6.dp) {
        list.forEach { v ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { open = if (open == v.key) null else v.key }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph(icons[v.key] ?: "ui:stats", 20.dp)
                    Text("  " + v.title + if (v.unit.isNotBlank()) " (${v.unit})" else "", Modifier.weight(1f), fontSize = 14.sp)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(v.text(), fontWeight = FontWeight.SemiBold, color = if (v.value == null) extra.dim else MaterialTheme.colorScheme.onSurface)
                        v.label?.let { Text(it, fontSize = 11.sp, color = toneColor(v.tone)) }
                    }
                }
                AnimatedVisibility(openAll || open == v.key) {
                    Column(Modifier.padding(top = 8.dp)) {
                        if (v.value != null && v.scale != null) ScaleBar(v.scale, v.value, v.decimals.coerceAtLeast(1))
                        Text(v.about, fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 6.dp))
                        if (v.value == null) Text("Нет данных — впишите значение с экрана весов.", fontSize = 12.sp, color = extra.warn)
                    }
                }
            }
        }
    }
}

private enum class Period(val label: String) { DAY("День"), WEEK("Неделя"), MONTH("Месяц"), CUSTOM("Свои") }

@Composable
internal fun TrendSection(readings: List<BodyMetric>, profile: BodyProfile) {
    val extra = LocalExtra.current
    var period by rememberSaveable { mutableStateOf(Period.MONTH) }
    var metric by rememberSaveable { mutableStateOf("weight") }
    val today = Dates.today()
    var from by rememberSaveable { mutableStateOf(today - 30) }
    var to by rememberSaveable { mutableStateOf(today) }
    var pick by remember { mutableStateOf(0) }
    val range = when (period) {
        Period.DAY -> today - 6..today
        Period.WEEK -> today - 7 * 12 + 1..today
        Period.MONTH -> today - 30..today
        Period.CUSTOM -> from..maxOf(from, to)
    }
    fun value(m: BodyMetric): Double? = when (metric) {
        "weight" -> m.weight
        "fat" -> m.fatPct
        "bmi" -> BodyComp.bmi(m.weight, profile.heightCm)
        "muscle" -> m.musclePct
        else -> m.waterPct
    }
    val pts = readings.filter { it.day in range }.mapNotNull { m -> value(m)?.let { m.day to it } }
    val series: List<Pair<String, Double>> = when (period) {
        Period.WEEK -> pts.groupBy { Dates.weekStart(it.first) }.toSortedMap().map { (w, l) -> Dates.short(w) to l.map { it.second }.average() }
        else -> pts.map { Dates.short(it.first) to it.second }
    }
    SectionTitle("Динамика")
    Segments(Period.entries.map { it to it.label }, period, { period = it })
    Gap(6.dp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("weight" to "Вес", "fat" to "Жир %", "bmi" to "ИМТ", "muscle" to "Мышцы %", "water" to "Вода %").forEach { (k, l) -> Pill(l, metric == k) { metric = k } }
    }
    if (period == Period.CUSTOM) Row(Modifier.padding(top = 6.dp)) {
        FieldButton("С", Dates.label(from), Modifier.weight(1f)) { pick = 1 }
        HGap(8.dp)
        FieldButton("По", Dates.label(to), Modifier.weight(1f)) { pick = 2 }
    }
    Gap(6.dp)
    Tile {
        if (series.size < 2) Text("Для графика нужно хотя бы два значения за период.", color = extra.dim, fontSize = 13.sp)
        else LineChart(
            listOf(Series(series.map { it.second.toFloat() }, MaterialTheme.colorScheme.primary)),
            labels = series.map { it.first }.let { l -> if (l.size <= 6) l else l.filterIndexed { i, _ -> i % ((l.size + 5) / 6) == 0 } },
        )
        val t = BodyComp.trend(pts, range.first, range.last)
        if (t != null) {
            Gap(8.dp)
            Text("За период ${Dates.short(range.first)} — ${Dates.short(range.last)} · ${t.count} изм.", fontSize = 12.sp, color = extra.dim)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.weight(1f)) { Text(fmt(t.avg), style = MaterialTheme.typography.titleLarge); Text("Среднее", fontSize = 12.sp, color = extra.dim) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text((if (t.change < 0) "↓ " else if (t.change > 0) "↑ " else "") + fmt(abs(t.change)), style = MaterialTheme.typography.titleLarge, color = if (t.change <= 0) extra.ok else extra.warn)
                    Text("Изменение", fontSize = 12.sp, color = extra.dim)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.weight(1f)) { Text(fmt(t.max.second), style = MaterialTheme.typography.titleLarge); Text("Максимум · ${Dates.short(t.max.first)}", fontSize = 12.sp, color = extra.dim) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) { Text(fmt(t.min.second), style = MaterialTheme.typography.titleLarge); Text("Минимум · ${Dates.short(t.min.first)}", fontSize = 12.sp, color = extra.dim) }
            }
        }
    }
    if (pick == 1) DatePickDialog(from, { pick = 0 }, { it?.let { d -> from = d } }, allowClear = false)
    if (pick == 2) DatePickDialog(to, { pick = 0 }, { it?.let { d -> to = d } }, allowClear = false)
}

/** Ввод всех показателей весов — в том же порядке, что на экране OKOK «Показатели тела». */
@Composable
internal fun BodyDialog(m0: BodyMetric, onDismiss: () -> Unit) {
    var m by remember { mutableStateOf(m0) }
    fun s(v: Double?) = v?.let { if (it == Math.floor(it)) it.toLong().toString() else it.toString().replace('.', ',') } ?: ""
    var weight by remember { mutableStateOf(s(m0.weight)) }
    val f = remember {
        mutableStateOf(
            mapOf(
                "fat" to s(m0.fatPct), "musclePct" to s(m0.musclePct), "muscleKg" to s(m0.muscleKg), "water" to s(m0.waterPct),
                "protein" to s(m0.proteinPct), "bone" to s(m0.boneKg), "visceral" to s(m0.visceral), "bmr" to s(m0.bmr),
                "age" to s(m0.metabolicAge), "subcut" to s(m0.subcutaneousPct),
                "height" to s(m0.heightCm), "waist" to s(m0.waistCm), "hip" to s(m0.hipCm), "neck" to s(m0.neckCm),
            )
        )
    }
    var pickDay by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val isNew = m0.id == 0L
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Взвешивание" else "Изменить взвешивание") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row {
                    FieldButton("Дата", Dates.label(m.day), Modifier.weight(1f)) { pickDay = true }
                    HGap(8.dp)
                    FieldButton("Время", Dates.time(Dates.minutesOf(m.at)), Modifier.weight(1f)) { pickTime = true }
                }
                Gap(6.dp)
                NumberField(weight, { weight = it }, "Вес", suffix = "кг")
                Text("Рост и обхваты — для научных показателей (ИМТ, талия/рост, жир по формуле ВМС США):", fontSize = 12.sp, color = LocalExtra.current.dim, modifier = Modifier.padding(vertical = 6.dp))
                MetricField(f, "height", "Рост", "см")
                MetricField(f, "waist", "Талия", "см")
                MetricField(f, "hip", "Бёдра", "см")
                MetricField(f, "neck", "Шея", "см")
                Text("Остальное — по желанию, с экрана весов:", fontSize = 12.sp, color = LocalExtra.current.dim, modifier = Modifier.padding(vertical = 6.dp))
                MetricField(f, "fat", "Жир", "%")
                MetricField(f, "musclePct", "Скелетная мускулатура", "%")
                MetricField(f, "muscleKg", "Мышечная масса", "кг")
                MetricField(f, "water", "Вода", "%")
                MetricField(f, "protein", "Белок", "%")
                MetricField(f, "bone", "Костная масса", "кг")
                MetricField(f, "visceral", "Висцеральный жир", "ур.")
                MetricField(f, "bmr", "Базовый обмен", "ккал")
                MetricField(f, "age", "Метаболический возраст", "лет")
                MetricField(f, "subcut", "Подкожный жир", "%")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val w = weight.num() ?: return@TextButton
                val v = f.value.mapValues { it.value.num() }
                val out = m.copy(
                    weight = w, fatPct = v["fat"], musclePct = v["musclePct"], muscleKg = v["muscleKg"], waterPct = v["water"],
                    proteinPct = v["protein"], boneKg = v["bone"], visceral = v["visceral"], bmr = v["bmr"], metabolicAge = v["age"],
                    subcutaneousPct = v["subcut"], heightCm = v["height"]?.takeIf { it in 100.0..250.0 }, waistCm = v["waist"],
                    hipCm = v["hip"], neckCm = v["neck"], source = if (m.source.isBlank() || m.source == LEGACY || m.source.startsWith("вручную")) "вручную" else m.source,
                )
                io {
                    Body.save(out)
                    out.heightCm?.let { h -> Graph.dao.profileNow()?.let { p -> if (p.heightCm != h) Graph.dao.upsertProfile(p.copy(heightCm = h)) } }
                }
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (!isNew || m0.source == LEGACY) TextButton(onClick = {
                    io { if (m0.source == LEGACY) Graph.dao.deleteWeightOfDay(m0.day) else Body.delete(m0) }
                    onDismiss()
                }) { Text("Удалить", color = LocalExtra.current.danger) }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
    if (pickDay) DatePickDialog(m.day, { pickDay = false }, { it?.let { d -> m = m.copy(day = d, at = Dates.millis(d, Dates.minutesOf(m.at))) } }, allowClear = false)
    if (pickTime) TimePickDialog(Dates.minutesOf(m.at), { pickTime = false }, { it?.let { t -> m = m.copy(at = Dates.millis(m.day, t)) } }, allowClear = false)
}
