package com.dasein.poryadok.ui.health

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.logic.Pressure
import com.dasein.poryadok.system.PressureStore
import com.dasein.poryadok.ui.common.ConfirmDialog
import com.dasein.poryadok.ui.common.DatePickDialog
import com.dasein.poryadok.ui.common.FitText
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.HGap
import com.dasein.poryadok.ui.common.HowTo
import com.dasein.poryadok.ui.common.Ic
import com.dasein.poryadok.ui.common.IconAction
import com.dasein.poryadok.ui.common.NumberField
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Segments
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.TimePickDialog
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

object PressureRoutes {
    const val HOME = "pressure"
    fun add(person: Long, id: Long = 0) = "pressureAdd?person=$person&id=$id"
    fun person(id: Long) = "pressurePerson/$id"
}

private val zone: ZoneId get() = ZoneId.systemDefault()
private fun c(v: Long) = Color(v)
private fun fmtTime(t: Long) = Instant.ofEpochMilli(t).atZone(zone).format(DateTimeFormatter.ofPattern("d MMM, HH:mm"))
private fun fmtHour(t: Long) = Instant.ofEpochMilli(t).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))

/** Главный экран: кто, последний замер с выводом, обзор, дневник, аналитика и протоколы. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun PressureHomeScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { PressureStore.reload(ctx) }
    val diary by PressureStore.flow(ctx).collectAsState()
    val d = diary ?: PressureStore.Diary()
    val person = d.people.firstOrNull { it.id == d.current } ?: d.people.firstOrNull()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var doctor by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Pressure.Reading?>(null) }
    var ortho by remember { mutableStateOf(false) }
    var imported by remember { mutableStateOf<Pressure.Imported?>(null) }
    val importer = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            }
            val r = text?.let { Pressure.parseTable(it) }
            if (r == null || r.readings.isEmpty()) Toast.makeText(ctx, "В файле не нашлось замеров. Нужны колонки: дата, время, верхнее, нижнее, пульс.", Toast.LENGTH_LONG).show()
            else imported = r
        }
    }
    Screen(
        "Давление и пульс", onBack = { nav.popBackStack() },
        actions = {
            IconAction("ui:folder", "Загрузить замеры из файла") { importer.launch(arrayOf("text/*", "application/vnd.ms-excel", "application/octet-stream", "*/*")) }
            if (person != null) IconAction("ui:people", "Профиль") { nav.navigate(PressureRoutes.person(person.id)) }
        },
        fab = {
            if (person != null) ExtendedFloatingActionButton(
                onClick = { nav.navigate(PressureRoutes.add(person.id)) },
                containerColor = MaterialTheme.colorScheme.primary,
                icon = { Icon(Icons.Default.Add, null) }, text = { Text("Замер") },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            // Люди: вы и родственники.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                d.people.sortedBy { it.createdAt }.forEach { p ->
                    Pill(p.name + (p.age()?.let { ", $it" } ?: ""), p.id == person?.id, glyph = p.glyph) {
                        // Повторное касание выбранного — открыть профиль.
                        if (p.id == person?.id) nav.navigate(PressureRoutes.person(p.id)) else scope.launch { PressureStore.select(ctx, p.id) }
                    }
                }
                Pill("+ Человек", false) { nav.navigate(PressureRoutes.person(0)) }
            }
            if (person == null) {
                Gap(12.dp)
                Tile {
                    Text("Дневник давления для вас и близких", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Text(
                        "Добавьте себя, маму, бабушку или дедушку: возраст, рост, вес и болезни. Приложение рассчитает, какое давление для каждого — норма " +
                            "(по рекомендациям Минздрава РФ и Европейского общества кардиологов), а после каждого замера подскажет: всё в порядке, понаблюдать, " +
                            "к врачу или срочно 103.",
                        fontSize = 14.sp, color = extra.dim, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp),
                    )
                    Button(onClick = { nav.navigate(PressureRoutes.person(0)) }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("Добавить человека") }
                    OutlinedButton(onClick = { importer.launch(arrayOf("text/*", "application/vnd.ms-excel", "application/octet-stream", "*/*")) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Text("Загрузить замеры из файла")
                    }
                }
                HowTo("pressure")
                Gap(80.dp)
                return@Column
            }
            val rs = d.readings.filter { it.personId == person.id }.sortedByDescending { it.time }
            val target = Pressure.target(person)
            Gap(10.dp)
            LatestCard(person, rs.firstOrNull(), target) { nav.navigate(PressureRoutes.add(person.id, it.id)) }
            Gap(10.dp)
            Segments(listOf(0 to "Обзор", 1 to "Дневник", 2 to "Аналитика", 3 to "Советы"), tab, { tab = it })
            Gap(8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill("Просто", !doctor) { doctor = false }
                Pill("Для врача", doctor) { doctor = true }
                Text("  режим оценки", fontSize = 12.sp, color = LocalExtra.current.dim)
            }
            Gap(10.dp)
            when (tab) {
                0 -> Overview(person, rs, target, doctor)
                1 -> Journal(rs, { nav.navigate(PressureRoutes.add(person.id, it.id)) }) { deleting = it }
                2 -> Analytics(person, rs, target, doctor)
                else -> Protocols(person, rs) { ortho = true }
            }
            HowTo("pressure")
            Gap(96.dp)
        }
    }
    deleting?.let { r ->
        ConfirmDialog("Удалить замер ${r.sys}/${r.dia}?", fmtTime(r.time), onDismiss = { deleting = null }) {
            scope.launch { PressureStore.deleteReading(ctx, r.id) }; deleting = null
        }
    }
    if (ortho && person != null) OrthostaticDialog(person) { ortho = false }
    imported?.let { im -> ImportDialog(im, d.people, person?.id) { imported = null } }
}

@Composable
private fun LatestCard(p: Pressure.Person, last: Pressure.Reading?, t: Pressure.Target, onOpen: (Pressure.Reading) -> Unit) {
    val extra = LocalExtra.current
    Tile(onClick = last?.let { { onOpen(it) } }) {
        if (last == null) {
            Text("Замеров пока нет", fontWeight = FontWeight.SemiBold)
            Text("Норма для ${p.name}: ${t.label}", fontSize = 13.sp, color = extra.dim)
            Text(t.why, fontSize = 12.sp, color = extra.dim)
            return@Tile
        }
        val cat = Pressure.category(last.sys, last.dia)
        val v = Pressure.verdict(p, last.sys, last.dia, last.pulse, last.symptoms, last.irregular)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(fmtTime(last.time), fontSize = 12.sp, color = extra.dim)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${last.sys}/${last.dia}", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = c(Pressure.color(cat)))
                    Text(" мм рт. ст.", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(bottom = 8.dp))
                }
                Text(cat.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c(Pressure.color(cat)))
            }
            last.pulse?.let {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("♥ $it", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (last.irregular) "неровный ритм" else "пульс", fontSize = 11.sp, color = if (last.irregular) extra.danger else extra.dim)
                }
            }
        }
        CategoryScale(last.sys, last.dia)
        VerdictBox(v)
    }
}

/** Шкала категорий с отметкой замера — как в приложениях Omron и SmartBP. */
@Composable
private fun CategoryScale(sys: Int, dia: Int) {
    val cats = listOf(Pressure.Category.LOW, Pressure.Category.OPTIMAL, Pressure.Category.NORMAL, Pressure.Category.HIGH_NORMAL, Pressure.Category.GRADE1, Pressure.Category.GRADE2, Pressure.Category.GRADE3)
    val cur = Pressure.category(sys, dia)
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        cats.forEach { k ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(if (k == cur) 10.dp else 6.dp).clip(RoundedCornerShape(3.dp)).background(c(Pressure.color(k)).copy(alpha = if (k == cur) 1f else .45f)))
                if (k == cur) Text("▲", fontSize = 10.sp, color = c(Pressure.color(k)))
            }
        }
    }
}

@Composable
private fun VerdictBox(v: Pressure.Verdict) {
    val col = c(v.level.color)
    Column(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(14.dp)).background(col.copy(alpha = .14f)).padding(12.dp)) {
        Text(v.level.title.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = col)
        Text(v.headline, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(top = 2.dp))
        Text(v.explain, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
        v.actions.forEach { a -> Text("• $a", fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp)) }
        if (v.level == Pressure.Level.EMERGENCY) {
            val ctx = LocalContext.current
            Button(
                onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:103"))) } },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = col),
            ) { Text("Позвонить 103", color = Color.White, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun Overview(p: Pressure.Person, rs: List<Pressure.Reading>, t: Pressure.Target, doctor: Boolean) {
    val extra = LocalExtra.current
    val now = System.currentTimeMillis()
    // 1. Что происходит  2. Меняется ли  3. Что разумно сделать.
    StatusCard(com.dasein.poryadok.logic.PressureInsight.assess(p, rs, now), doctor)
    if (doctor) DoctorStats(rs)
    Gap(8.dp)
    WeekStrip(p, rs)
    Gap(8.dp)
    DeviationCard(rs)
    Gap(8.dp)
    ZoomChart(p, rs)
    Gap(8.dp)
    DynamicsCard(rs)
    Gap(8.dp)
    DayProfileCard(rs)
    Gap(8.dp)
    StabilityCard(rs)
    Gap(8.dp)
    PressureCalendar(p, rs)
    ImportantNow(p, rs)
    SectionTitle("Ваша норма")
    Tile {
        Text(t.label, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(t.why, fontSize = 12.sp, color = extra.dim, lineHeight = 16.sp)
        Text("Пульс в покое: 60–100 уд/мин · порог для домашних замеров: 135/85", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp))
    }
    val pr = Pressure.protocol(rs, LocalDate.now())
    SectionTitle("Протокол 7 дней")
    Tile {
        Text("Утро и вечер по 2 замера 7 дней подряд — так врачи ставят и проверяют диагноз (ESH).", fontSize = 13.sp, color = extra.dim)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val today = LocalDate.now()
            (6 downTo 0).forEach { back ->
                val day = today.minusDays(back.toLong())
                val dayRs = rs.filter { Pressure.day(it) == day }
                val m = dayRs.any { Pressure.isMorning(it) }; val e = dayRs.any { Pressure.isEvening(it) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(day.dayOfMonth.toString(), fontSize = 11.sp, color = extra.dim)
                    Dot2(m, "У"); Gap(2.dp); Dot2(e, "В")
                }
            }
        }
        Text(pr.verdict, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun Dot2(on: Boolean, label: String) {
    Box(
        Modifier.size(22.dp).clip(CircleShape).background(if (on) Color(0xFF3E9B5B) else LocalExtra.current.cardHigh),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 10.sp, color = if (on) Color.White else LocalExtra.current.dim) }
}

@Composable
private fun Legend(col: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(col)); Text(" $text", fontSize = 11.sp, color = LocalExtra.current.dim)
    }
}

@Composable
private fun AvgTile(label: String, a: Pressure.Avg?, t: Pressure.Target, modifier: Modifier) {
    val extra = LocalExtra.current
    Tile(modifier, padding = 10.dp) {
        Text("Среднее · $label", fontSize = 11.sp, color = extra.dim)
        FitText(a?.let { "${it.sys}/${it.dia}" } ?: "—", fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
            color = a?.let { if (t.contains(it.sys, it.dia)) Color(0xFF3E9B5B) else c(Pressure.color(Pressure.category(it.sys, it.dia))) } ?: Color.Unspecified)
        Text(a?.let { "${it.n} зам." + (it.pulse?.let { p -> " · ♥ $p" } ?: "") } ?: "нет данных", fontSize = 10.sp, color = extra.dim)
    }
}

/** График: верхнее и нижнее по времени, зелёная полоса — целевой диапазон, пунктиры — 140 и 90. */
@Composable
private fun TrendChart(rs: List<Pressure.Reading>, t: Pressure.Target) {
    val extra = LocalExtra.current
    if (rs.size < 2) { Text("Нужно хотя бы 2 замера", fontSize = 12.sp, color = extra.dim); return }
    val lo = minOf(rs.minOf { it.dia }, t.diaLow) - 10
    val hi = maxOf(rs.maxOf { it.sys }, t.sysHigh, 150) + 10
    val t0 = rs.first().time; val t1 = rs.last().time.coerceAtLeast(t0 + 1)
    val line = extra.line
    Canvas(Modifier.fillMaxWidth().height(180.dp)) {
        fun y(v: Int) = size.height * (1f - (v - lo).toFloat() / (hi - lo))
        fun x(tm: Long) = size.width * ((tm - t0).toFloat() / (t1 - t0))
        drawRect(Color(0xFF3E9B5B).copy(alpha = .14f), Offset(0f, y(t.sysHigh)), Size(size.width, y(t.sysLow) - y(t.sysHigh)))
        drawRect(Color(0xFF3E9B5B).copy(alpha = .10f), Offset(0f, y(t.diaHigh)), Size(size.width, y(t.diaLow) - y(t.diaHigh)))
        listOf(140, 90).forEach { v ->
            drawLine(line, Offset(0f, y(v)), Offset(size.width, y(v)), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        }
        listOf(rs.map { it.time to it.sys } to Color(0xFFD9542B), rs.map { it.time to it.dia } to Color(0xFF4C8BD6)).forEach { (pts, col) ->
            pts.zipWithNext().forEach { (a, b) -> drawLine(col, Offset(x(a.first), y(a.second)), Offset(x(b.first), y(b.second)), 4f) }
            pts.forEach { (tm, v) -> drawCircle(col, 6f, Offset(x(tm), y(v))) }
        }
    }
    Row { Text("${hi}", fontSize = 10.sp, color = extra.dim, modifier = Modifier.weight(1f)); Text("пунктир — 140 и 90", fontSize = 10.sp, color = extra.dim) }
}

@Composable
private fun PulseChart(rs: List<Pressure.Reading>) {
    val pts = rs.filter { it.pulse != null }.sortedBy { it.time }
    if (pts.size < 2) return
    val lo = minOf(pts.minOf { it.pulse!! }, 55) - 5; val hi = maxOf(pts.maxOf { it.pulse!! }, 105) + 5
    val t0 = pts.first().time; val t1 = pts.last().time.coerceAtLeast(t0 + 1)
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        fun y(v: Int) = size.height * (1f - (v - lo).toFloat() / (hi - lo))
        fun x(tm: Long) = size.width * ((tm - t0).toFloat() / (t1 - t0))
        drawRect(Color(0xFF3E9B5B).copy(alpha = .12f), Offset(0f, y(100)), Size(size.width, y(60) - y(100)))
        pts.zipWithNext().forEach { (a, b) -> drawLine(Color(0xFFB0457A), Offset(x(a.time), y(a.pulse!!)), Offset(x(b.time), y(b.pulse!!)), 4f) }
        pts.forEach { drawCircle(if (it.irregular) Color(0xFFC0262D) else Color(0xFFB0457A), if (it.irregular) 9f else 5f, Offset(x(it.time), y(it.pulse!!))) }
    }
}

@Composable
private fun Journal(rs: List<Pressure.Reading>, onEdit: (Pressure.Reading) -> Unit, onDelete: (Pressure.Reading) -> Unit) {
    val extra = LocalExtra.current
    if (rs.isEmpty()) { Text("Здесь будут все замеры. Нажмите «Замер».", color = extra.dim); return }
    rs.groupBy { Pressure.day(it) }.forEach { (day, list) ->
        val a = Pressure.avg(list)!!
        Row(Modifier.padding(top = 10.dp, bottom = 4.dp)) {
            Text(day.format(DateTimeFormatter.ofPattern("d MMMM, EEEE")), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (list.size > 1) Text("среднее ${a.sys}/${a.dia}", fontSize = 12.sp, color = extra.dim)
        }
        list.forEach { r -> ReadingRow(r, { onEdit(r) }) { onDelete(r) } }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReadingRow(r: Pressure.Reading, onClick: () -> Unit, onLong: () -> Unit) {
    val extra = LocalExtra.current
    val cat = Pressure.category(r.sys, r.dia)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp)).background(extra.card)
            .combinedClickable(onClick = onClick, onLongClick = onLong).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 6.dp, height = 34.dp).clip(RoundedCornerShape(3.dp)).background(c(Pressure.color(cat))))
        HGap(10.dp)
        Text(fmtHour(r.time), fontSize = 13.sp, color = extra.dim, modifier = Modifier.width(44.dp))
        Column(Modifier.weight(1f)) {
            Text("${r.sys}/${r.dia}" + (r.pulse?.let { "  ♥ $it" } ?: ""), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            val sub = listOfNotNull(cat.short, "аритмия".takeIf { r.irregular }, r.tags.joinToString(", ").ifBlank { null }, r.symptoms.joinToString(", ").ifBlank { null }, r.note.ifBlank { null })
            Text(sub.joinToString(" · "), fontSize = 11.sp, color = if (r.symptoms.isNotEmpty() || r.irregular) extra.warn else extra.dim, maxLines = 2)
        }
    }
}

@Composable
private fun Analytics(p: Pressure.Person, rs: List<Pressure.Reading>, t: Pressure.Target, doctor: Boolean) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val now = System.currentTimeMillis()
    val s = Pressure.stats(rs, t, now)
    if (rs.size < 3) { Text("Для аналитики нужно хотя бы 3 замера. Лучше — неделя утром и вечером.", color = extra.dim); return }
    WeeklyReportCard(p, rs)
    if (!doctor) DoctorStats(rs)
    PatternsCard(rs)
    SectionTitle("Подробности")
    @Composable
    fun Insight(title: String, value: String, text: String, col: Color = MaterialTheme.colorScheme.onSurface) {
        Tile(Modifier.padding(bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(value, fontWeight = FontWeight.Bold, color = col)
            }
            Text(text, fontSize = 13.sp, color = extra.dim, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
    val l30 = s.last30
    if (l30 != null && s.prev30 != null) {
        val dSys = l30.sys - s.prev30.sys; val dDia = l30.dia - s.prev30.dia
        Insight(
            "К прошлому месяцу", (if (dSys > 0) "+" else "") + "$dSys/" + (if (dDia > 0) "+" else "") + "$dDia",
            when {
                dSys <= -5 -> "Давление заметно снизилось — то, что вы делаете, работает."
                dSys >= 5 -> "Давление выросло. Проверьте режим лекарств, соль, сон и стресс; если рост держится — к врачу."
                else -> "Без существенных изменений (колебания до 5 мм — норма)."
            },
            if (dSys >= 5) extra.danger else if (dSys <= -5) extra.ok else MaterialTheme.colorScheme.onSurface,
        )
    }
    s.slopePerWeek?.let { k ->
        Insight(
            "Тренд", (if (k > 0) "+" else "") + "%.1f мм/нед".format(k).replace('.', ','),
            if (k > 2) "Верхнее давление стабильно растёт — стоит обсудить с врачом." else if (k < -2) "Верхнее давление снижается." else "Давление стабильное.",
        )
    }
    val m = s.morning; val e = s.evening
    if (m != null && e != null) {
        val diff = m.sys - e.sys
        Insight(
            "Утро и вечер", "${m.sys}/${m.dia} · ${e.sys}/${e.dia}",
            when {
                diff >= 15 -> "Утром давление выше вечернего на $diff — «утренний подъём». Он повышает риск инсультов: скажите врачу, иногда меняют время приёма лекарств."
                diff <= -15 -> "Вечером давление выше утреннего на ${-diff}. Проверьте вечерние соль, алкоголь, стресс; скажите врачу."
                else -> "Утро и вечер почти одинаковые — хорошо."
            },
        )
    }
    Insight(
        "Колебания", "±%.0f".format(s.sysSd),
        when {
            s.sysSd >= 15 -> "Давление сильно скачет (разброс верхнего ±${s.sysSd.roundToInt()}). Большая вариабельность — отдельный фактор риска. Проверьте технику замера и регулярность лекарств."
            s.sysSd >= 10 -> "Умеренные колебания — обычно из-за разной обстановки замеров. Мерьте в одно время и по технике."
            else -> "Давление ровное."
        },
    )
    val cats = s.byCategory
    if (cats.isNotEmpty()) {
        SectionTitle("Распределение за 30 дней")
        Tile {
            val total = cats.values.sum().toFloat()
            Row(Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(6.dp))) {
                Pressure.Category.entries.forEach { k -> cats[k]?.let { n -> Box(Modifier.weight(n / total).height(18.dp).background(c(Pressure.color(k)))) } }
            }
            Pressure.Category.entries.forEach { k ->
                cats[k]?.let { n ->
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(c(Pressure.color(k))))
                        Text("  ${k.title}", fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text("$n · ${(n * 100 / total).roundToInt()} %", fontSize = 13.sp, color = extra.dim)
                    }
                }
            }
        }
    }
    if (rs.count { it.pulse != null } >= 2) {
        SectionTitle("Пульс")
        Tile(padding = 10.dp) {
            PulseChart(rs.filter { it.time > now - 30 * 86_400_000L })
            val irr = rs.count { it.irregular }
            Text("Зелёная полоса — 60–100 уд/мин. " + if (irr > 0) "Неровный ритм отмечен $irr раз — покажите врачу, сделайте ЭКГ." else "Неровного ритма не было.", fontSize = 12.sp, color = if (irr > 0) extra.warn else extra.dim)
        }
    }
    Pressure.armDifference(rs)?.let { dArm ->
        Insight("Разница между руками", "$dArm мм", if (dArm > 15) "Больше 15 — скажите врачу: это бывает при сужении артерий. Мерьте на руке с бо́льшим давлением." else if (dArm > 10) "Мерьте на руке, где давление выше." else "В норме (до 10 мм).")
    }
    val maxR = s.max; val minR = s.min
    if (maxR != null && minR != null) Insight("Максимум и минимум", "${maxR.sys}/${maxR.dia} · ${minR.sys}/${minR.dia}", "За 30 дней: максимум ${fmtTime(maxR.time)}, минимум ${fmtTime(minR.time)}.")
    SectionTitle("Для врача")
    Text("Отчёт со средними, утром и вечером, и таблицей замеров — врачу в мессенджер или на печать.", fontSize = 12.sp, color = extra.dim)
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, Pressure.report(p, rs, now))
            runCatching { ctx.startActivity(Intent.createChooser(send, "Отчёт для врача")) }
        }, modifier = Modifier.weight(1f)) { Text("Отчёт") }
        OutlinedButton(onClick = {
            runCatching {
                val dir = java.io.File(ctx.cacheDir, "share").apply { mkdirs() }
                val f = java.io.File(dir, "Давление_${p.name}.csv").apply { writeText(PressureStore.csv(p, rs)) }
                val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Таблица замеров"))
            }.onFailure { Toast.makeText(ctx, "Не удалось: ${it.message}", Toast.LENGTH_SHORT).show() }
        }, modifier = Modifier.weight(1f)) { Text("Таблица CSV") }
    }
}

@Composable
private fun Protocols(p: Pressure.Person, rs: List<Pressure.Reading>, onOrtho: () -> Unit) {
    val extra = LocalExtra.current
    ChangesCard(p, rs)
    SectionTitle("Что снижает давление")
    Text("Эффект — среднее снижение верхнего давления по исследованиям (ESC/ESH, DASH). Эффекты складываются.", fontSize = 12.sp, color = extra.dim)
    Pressure.habits(p).forEach { h ->
        Tile(Modifier.padding(top = 6.dp)) {
            Row { Text(h.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Text(h.effect, color = extra.ok, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
            Text(h.how, fontSize = 13.sp, color = extra.dim, lineHeight = 18.sp)
        }
    }
    SectionTitle("Как правильно мерить")
    Tile { Pressure.TECHNIQUE.forEachIndexed { i, s -> Text("${i + 1}. $s", fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(vertical = 2.dp)) } }
    SectionTitle("Ортостатическая проба")
    Tile {
        Text(
            "Падает ли давление, когда человек встаёт. Особенно важно для пожилых и тех, кто принимает лекарства от давления: резкое падение — частая причина головокружений и падений.",
            fontSize = 13.sp, color = extra.dim, lineHeight = 18.sp,
        )
        OutlinedButton(onClick = onOrtho, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Пройти пробу") }
    }
    SectionTitle("Когда звонить 103 / 112")
    Tile {
        listOf(
            "Давление 180/120 и выше вместе с болью в груди, одышкой, слабостью или онемением руки, ноги, половины лица, нарушением речи или зрения, спутанностью.",
            "Признаки инсульта по тесту «УДАР»: Улыбка кривая, Движение — одна рука не поднимается, Артикуляция — речь невнятная, Решение — сразу 103. Запомните время начала.",
            "Боль в груди дольше 5 минут, особенно с холодным потом и страхом.",
            "При беременности: 160/110 и выше, головная боль с мушками перед глазами, боль под рёбрами справа.",
            "Пульс выше 130 или ниже 40 с плохим самочувствием, обморок.",
        ).forEach { Text("• $it", fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(vertical = 2.dp)) }
    }
    SectionTitle("Классификация")
    Tile {
        listOf(
            Pressure.Category.OPTIMAL to "ниже 120 и ниже 80", Pressure.Category.NORMAL to "120–129 и/или 80–84",
            Pressure.Category.HIGH_NORMAL to "130–139 и/или 85–89", Pressure.Category.GRADE1 to "140–159 и/или 90–99",
            Pressure.Category.GRADE2 to "160–179 и/или 100–109", Pressure.Category.GRADE3 to "180 и выше и/или 110 и выше",
            Pressure.Category.LOW to "ниже 90/60",
        ).forEach { (k, v) ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(c(Pressure.color(k))))
                Text("  ${k.title}", fontSize = 13.sp, modifier = Modifier.weight(1f)); Text(v, fontSize = 13.sp, color = extra.dim)
            }
        }
        Text(
            "Дома пороги ниже, чем у врача: гипертония по домашним замерам — среднее 135/85 и выше. В США (AHA 2017) гипертонией считают уже 130/80. " +
                "Источники: клинические рекомендации Минздрава РФ «Артериальная гипертензия у взрослых», ESC/ESH 2018, ESH 2023, ESC 2024.",
            fontSize = 11.sp, color = extra.dim, lineHeight = 15.sp, modifier = Modifier.padding(top = 6.dp),
        )
    }
    Text(
        "Приложение помогает вести дневник и понимать цифры, но не ставит диагноз и не назначает лечение. Лекарства и дозы меняет только врач.",
        fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun OrthostaticDialog(p: Pressure.Person, onDismiss: () -> Unit) {
    var ls by remember { mutableStateOf("") }; var ld by remember { mutableStateOf("") }
    var s1 by remember { mutableStateOf("") }; var d1 by remember { mutableStateOf("") }
    var s3 by remember { mutableStateOf("") }; var d3 by remember { mutableStateOf("") }
    val standing = listOfNotNull(
        s1.toIntOrNull()?.let { a -> d1.toIntOrNull()?.let { b -> a to b } },
        s3.toIntOrNull()?.let { a -> d3.toIntOrNull()?.let { b -> a to b } },
    )
    val res = if (ls.toIntOrNull() != null && ld.toIntOrNull() != null && standing.isNotEmpty()) Pressure.orthostatic(ls.toInt(), ld.toInt(), standing) else null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ортостатическая проба · ${p.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("1. Полежать спокойно 5 минут и измерить лёжа.\n2. Встать и измерить через 1 минуту.\n3. Ещё раз — через 3 минуты стоя.\nРядом должен быть человек: при головокружении сразу сесть.", fontSize = 13.sp)
                @Composable
                fun Pair2(label: String, a: String, b: String, onA: (String) -> Unit, onB: (String) -> Unit) {
                    Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { NumberField(a, onA, "Верхнее", decimal = false) }
                        Box(Modifier.weight(1f)) { NumberField(b, onB, "Нижнее", decimal = false) }
                    }
                }
                Pair2("Лёжа", ls, ld, { ls = it }, { ld = it })
                Pair2("Стоя, 1 минута", s1, d1, { s1 = it }, { d1 = it })
                Pair2("Стоя, 3 минуты", s3, d3, { s3 = it }, { d3 = it })
                res?.let { r -> Text(r.text, fontWeight = FontWeight.Medium, color = if (r.positive) LocalExtra.current.danger else LocalExtra.current.ok, modifier = Modifier.padding(top = 10.dp)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
    )
}

/** Новый замер или правка: крупные поля, быстрый ввод «135 85 72», второй замер для среднего, метки и самочувствие. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PressureAddScreen(nav: NavHostController, personId: Long, id: Long) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    val d = PressureStore.now(ctx)
    val person = d.people.firstOrNull { it.id == personId } ?: run { Screen("Замер", onBack = { nav.popBackStack() }) { Text("Человек не найден", Modifier.padding(it).padding(16.dp)) }; return }
    val old = d.readings.firstOrNull { it.id == id }
    var sys by remember { mutableStateOf(old?.sys?.toString().orEmpty()) }
    var dia by remember { mutableStateOf(old?.dia?.toString().orEmpty()) }
    var pulse by remember { mutableStateOf(old?.pulse?.toString().orEmpty()) }
    var second by remember { mutableStateOf(false) }
    var sys2 by remember { mutableStateOf("") }; var dia2 by remember { mutableStateOf("") }; var pulse2 by remember { mutableStateOf("") }
    var quick by remember { mutableStateOf("") }
    var time by remember { mutableStateOf(old?.time ?: System.currentTimeMillis()) }
    var arm by remember { mutableIntStateOf(old?.arm ?: d.readings.lastOrNull { it.personId == personId }?.arm ?: 0) }
    var position by remember { mutableIntStateOf(old?.position ?: 0) }
    var irregular by remember { mutableStateOf(old?.irregular ?: false) }
    var tags by remember { mutableStateOf(old?.tags?.toSet() ?: emptySet()) }
    var symptoms by remember { mutableStateOf(old?.symptoms?.toSet() ?: emptySet()) }
    var note by remember { mutableStateOf(old?.note.orEmpty()) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    // Итог: среднее из двух замеров, если второй введён.
    val s1 = sys.toIntOrNull(); val d1 = dia.toIntOrNull()
    val s2 = sys2.toIntOrNull()?.takeIf { second }; val dd2 = dia2.toIntOrNull()?.takeIf { second }
    val fs = if (s1 != null && s2 != null) (s1 + s2 + 1) / 2 else s1
    val fd = if (d1 != null && dd2 != null) (d1 + dd2 + 1) / 2 else d1
    val ps = listOfNotNull(pulse.toIntOrNull(), pulse2.toIntOrNull()?.takeIf { second })
    val fp = ps.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
    val valid = fs != null && fd != null && fs in 50..300 && fd in 30..200 && fd < fs
    fun save() {
        if (!valid) { Toast.makeText(ctx, "Проверьте цифры: верхнее больше нижнего", Toast.LENGTH_SHORT).show(); return }
        val r = Pressure.Reading(
            id = old?.id ?: 0, personId = personId, time = time, sys = fs!!, dia = fd!!, pulse = fp, arm = arm, position = position, irregular = irregular,
            tags = tags.toList(), symptoms = symptoms.toList(),
            note = listOfNotNull(note.trim().ifBlank { null }, if (s2 != null && old == null) "среднее из 2 замеров ($s1/$d1 и $s2/$dd2)" else null).joinToString(" · "),
        )
        scope.launch { PressureStore.upsertReading(ctx, r); nav.popBackStack() }
    }
    Screen(
        (if (old == null) "Замер · " else "Правка · ") + person.name, onBack = { nav.popBackStack() },
        actions = { IconAction("ui:check", "Сохранить") { save() } },
    ) { pad ->
        Column(Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                quick, { v -> quick = v; Pressure.parse(v)?.let { (a, b, pl) -> sys = a.toString(); dia = b.toString(); pl?.let { pulse = it.toString() } } },
                label = { Text("Быстро: 135 85 72") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Gap(8.dp)
            BigFields(sys, dia, pulse, { sys = it }, { dia = it }, { pulse = it })
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { second = !second }.padding(top = 4.dp)) {
                Checkbox(second, { second = it })
                Text("Второй замер через 1–2 минуты (запишется среднее — так точнее)", fontSize = 13.sp)
            }
            if (second) BigFields(sys2, dia2, pulse2, { sys2 = it }, { dia2 = it }, { pulse2 = it })
            if (valid) {
                Gap(6.dp)
                if (s2 != null) Text("Запишется среднее: $fs/$fd" + (fp?.let { ", пульс $it" } ?: ""), fontWeight = FontWeight.SemiBold)
                VerdictBox(Pressure.verdict(person, fs!!, fd!!, fp, symptoms.toList(), irregular))
            }
            SectionTitle("Когда")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val dt = Instant.ofEpochMilli(time).atZone(zone)
                OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.weight(1f)) { Text(dt.format(DateTimeFormatter.ofPattern("d MMM yyyy"))) }
                OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.weight(1f)) { Text(dt.format(DateTimeFormatter.ofPattern("HH:mm"))) }
            }
            Text("Рука", fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            Segments(listOf(0 to "Левая", 1 to "Правая"), arm, { arm = it })
            Text("Положение", fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            Segments(listOf(0 to "Сидя", 1 to "Лёжа", 2 to "Стоя"), position, { position = it })
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Неровный ритм", fontSize = 14.sp)
                    Text("Значок аритмии на тонометре или пульс «скачет»", fontSize = 12.sp, color = extra.dim)
                }
                Switch(irregular, { irregular = it })
            }
            SectionTitle("Обстоятельства")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Pressure.TAGS.forEach { t -> Pill(t, t in tags) { tags = if (t in tags) tags - t else tags + t } }
            }
            SectionTitle("Самочувствие")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Pressure.MILD_SYMPTOMS.forEach { t -> Pill(t, t in symptoms) { symptoms = if (t in symptoms) symptoms - t else symptoms + t } }
            }
            Text("Тревожные симптомы", fontSize = 13.sp, color = extra.danger, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Pressure.DANGER_SYMPTOMS.forEach { t -> Pill(t, t in symptoms) { symptoms = if (t in symptoms) symptoms - t else symptoms + t } }
            }
            Gap(8.dp)
            TextInput(note, { note = it }, "Заметка", singleLine = false)
            Button(onClick = { save() }, enabled = valid, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp)) { Text("Сохранить", fontSize = 17.sp) }
            Gap(60.dp)
        }
    }
    if (pickDate) DatePickDialog(Instant.ofEpochMilli(time).atZone(zone).toLocalDate().toEpochDay(), { pickDate = false }, { day ->
        if (day != null) {
            val cur = Instant.ofEpochMilli(time).atZone(zone)
            time = LocalDate.ofEpochDay(day).atTime(cur.hour, cur.minute).atZone(zone).toInstant().toEpochMilli()
        }
    }, allowClear = false)
    if (pickTime) {
        val cur = Instant.ofEpochMilli(time).atZone(zone)
        TimePickDialog(cur.hour * 60 + cur.minute, { pickTime = false }, { m ->
            if (m != null) time = LocalDateTime.of(cur.toLocalDate(), java.time.LocalTime.of(m / 60, m % 60)).atZone(zone).toInstant().toEpochMilli()
        }, allowClear = false)
    }
}

/** Три крупных поля — удобно вносить замеры бабушек и дедушек. */
@Composable
private fun BigFields(sys: String, dia: String, pulse: String, onSys: (String) -> Unit, onDia: (String) -> Unit, onPulse: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Triple("Верхнее", sys, onSys), Triple("Нижнее", dia, onDia), Triple("Пульс", pulse, onPulse)).forEach { (label, v, on) ->
            OutlinedTextField(
                v, { on(it.filter { ch -> ch.isDigit() }.take(3)) }, label = { Text(label) }, singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
            )
        }
    }
}

/** Профиль человека: от него зависит, какое давление — норма. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PressurePersonScreen(nav: NavHostController, id: Long) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    val old = PressureStore.now(ctx).people.firstOrNull { it.id == id }
    var p by remember { mutableStateOf(old ?: Pressure.Person(id = 0, name = if (PressureStore.now(ctx).people.isEmpty()) "Я" else "")) }
    var birth by remember { mutableStateOf(p.birthYear?.toString().orEmpty()) }
    var height by remember { mutableStateOf(p.heightCm?.toString().orEmpty()) }
    var weight by remember { mutableStateOf(p.weightKg?.let { "%.1f".format(it).replace(",", ".").removeSuffix(".0") }.orEmpty()) }
    var cs by remember { mutableStateOf(p.customSys?.toString().orEmpty()) }
    var waist by remember { mutableStateOf(p.waistCm?.toString().orEmpty()) }
    var cd by remember { mutableStateOf(p.customDia?.toString().orEmpty()) }
    var confirm by remember { mutableStateOf(false) }
    val full = p.copy(
        birthYear = birth.toIntOrNull()?.takeIf { it in 1900..LocalDate.now().year }, heightCm = height.toIntOrNull()?.takeIf { it in 50..250 },
        weightKg = weight.replace(',', '.').toDoubleOrNull()?.takeIf { it in 20.0..350.0 },
        customSys = cs.toIntOrNull()?.takeIf { it in 90..200 }, customDia = cd.toIntOrNull()?.takeIf { it in 50..120 },
        waistCm = waist.toIntOrNull()?.takeIf { it in 40..250 },
    )
    fun save() {
        if (full.name.isBlank()) { Toast.makeText(ctx, "Введите имя", Toast.LENGTH_SHORT).show(); return }
        scope.launch {
            val newId = PressureStore.upsertPerson(ctx, full.copy(name = full.name.trim()))
            PressureStore.select(ctx, newId)
            nav.popBackStack()
        }
    }
    Screen(
        if (old == null) "Новый человек" else p.name, onBack = { nav.popBackStack() },
        actions = {
            if (old != null) IconAction(Ic.trash, "Удалить") { confirm = true }
            IconAction("ui:check", "Сохранить") { save() }
        },
    ) { pad ->
        Column(Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            TextInput(p.name, { p = p.copy(name = it) }, "Имя: я, мама, бабушка Валя…")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                listOf("pressure/27", "pressure/22", "pressure/01", "pressure/13", "pressure/21", "pressure/05", "ui:smile", "habit/28").forEach { g ->
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(if (p.glyph == g) MaterialTheme.colorScheme.primary.copy(alpha = .2f) else extra.card)
                            .clickable { p = p.copy(glyph = g) },
                        contentAlignment = Alignment.Center,
                    ) { Glyph(g, 24.dp) }
                }
            }
            Gap(8.dp)
            Segments(listOf(0 to "Мужчина", 1 to "Женщина"), p.sex, { p = p.copy(sex = it) })
            Gap(8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { NumberField(birth, { birth = it.take(4) }, "Год рождения", decimal = false) }
                Box(Modifier.weight(1f)) { NumberField(height, { height = it.take(3) }, "Рост", suffix = "см", decimal = false) }
                Box(Modifier.weight(1f)) { NumberField(weight, { weight = it.take(5) }, "Вес", suffix = "кг") }
            }
            Box(Modifier.padding(top = 6.dp)) { NumberField(waist, { waist = it.take(3) }, "Окружность талии (необязательно)", suffix = "см", decimal = false) }
            full.bmi?.let { b ->
                Text(
                    "ИМТ ${"%.1f".format(b)} — " + when { b < 18.5 -> "недостаток веса"; b < 25 -> "норма"; b < 30 -> "избыточный вес"; else -> "ожирение" },
                    fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp),
                )
            }
            SectionTitle("Здоровье")
            @Composable
            fun Flag(title: String, sub: String, on: Boolean, set: (Boolean) -> Unit) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Column(Modifier.weight(1f)) { Text(title, fontSize = 14.sp); if (sub.isNotBlank()) Text(sub, fontSize = 12.sp, color = extra.dim) }
                    Switch(on, set)
                }
            }
            Flag("Гипертония (повышенное давление)", "Врач ставил диагноз или давление часто выше 140/90", p.hypertension) { p = p.copy(hypertension = it) }
            Flag("Принимает лекарства от давления", "", p.treated) { p = p.copy(treated = it) }
            Flag("Сахарный диабет", "", p.diabetes) { p = p.copy(diabetes = it) }
            Flag("Болезнь почек", "", p.kidney) { p = p.copy(kidney = it) }
            Flag("ИБС, инфаркт или инсульт в прошлом", "", p.heart) { p = p.copy(heart = it) }
            Flag("Курит", "", p.smoker) { p = p.copy(smoker = it) }
            Flag("Повышенный холестерин", "", p.cholesterol) { p = p.copy(cholesterol = it) }
            Flag("Ослабленный, были падения", "Нужна помощь в быту, кружится голова при вставании", p.frail) { p = p.copy(frail = it) }
            if (p.sex == 1) Flag("Беременность", "", p.pregnant) { p = p.copy(pregnant = it) }
            Gap(6.dp)
            TextInput(p.meds, { p = p.copy(meds = it) }, "Лекарства и дозы (для отчёта врачу)", singleLine = false)
            SectionTitle("Норма давления")
            val t = Pressure.target(full)
            Tile {
                Text(t.label, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(t.why, fontSize = 13.sp, color = extra.dim, lineHeight = 18.sp)
            }
            Text("Если врач назначил свою цель — впишите её, она важнее расчётной:", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { NumberField(cs, { cs = it.take(3) }, "Верхнее до", decimal = false) }
                Box(Modifier.weight(1f)) { NumberField(cd, { cd = it.take(3) }, "Нижнее до", decimal = false) }
            }
            Gap(6.dp)
            TextInput(p.note, { p = p.copy(note = it) }, "Заметка", singleLine = false)
            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(50.dp)) { Text("Сохранить") }
            Gap(60.dp)
        }
    }
    if (confirm && old != null) ConfirmDialog("Удалить «${old.name}»?", "Удалятся и все замеры этого человека.", onDismiss = { confirm = false }) {
        scope.launch { PressureStore.deletePerson(ctx, old.id); nav.popBackStack() }
    }
}

/** Загрузка замеров: сколько, за какой период, кому — существующему человеку или новому (имя из файла). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImportDialog(im: Pressure.Imported, people: List<Pressure.Person>, current: Long?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    val byName = im.name?.let { n -> people.firstOrNull { it.name.equals(n, ignoreCase = true) } }
    // 0 — новый человек
    var target by remember { mutableStateOf(byName?.id ?: if (im.name != null) 0L else current ?: 0L) }
    var newName by remember { mutableStateOf(im.name ?: "") }
    var busy by remember { mutableStateOf(false) }
    val rs = im.readings
    val df = DateTimeFormatter.ofPattern("d MMM yyyy")
    val a = Pressure.avg(rs)
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Загрузить замеры") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("${rs.size} замеров · ${Instant.ofEpochMilli(rs.first().time).atZone(zone).format(df)} — ${Instant.ofEpochMilli(rs.last().time).atZone(zone).format(df)}", fontWeight = FontWeight.SemiBold)
                a?.let { Text("Среднее ${it.sys}/${it.dia}" + (it.pulse?.let { p -> ", пульс $p" } ?: ""), fontSize = 13.sp, color = extra.dim) }
                val arms = rs.count { it.arm == 1 }
                if (arms > 0) Text("Правая рука: $arms, левая: ${rs.size - arms}", fontSize = 13.sp, color = extra.dim)
                if (im.skipped > 0) Text("Пропущено строк без цифр или даты: ${im.skipped}", fontSize = 12.sp, color = extra.warn)
                Text("Чьи замеры", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    people.forEach { p -> Pill(p.name, target == p.id, glyph = p.glyph) { target = p.id } }
                    Pill("+ Новый человек", target == 0L) { target = 0L }
                }
                if (target == 0L) {
                    Gap(6.dp)
                    TextInput(newName, { newName = it }, "Имя: папа, бабушка…")
                    Text("Возраст, рост, вес и болезни можно заполнить потом в профиле — от них зависит норма.", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp))
                }
                Text("Повторы (то же время и те же цифры) не добавятся.", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && (target != 0L || newName.isNotBlank()), onClick = {
                busy = true
                scope.launch {
                    val pid = if (target == 0L) PressureStore.upsertPerson(ctx, Pressure.Person(id = 0, name = newName.trim())) else target
                    val n = PressureStore.importReadings(ctx, pid, rs)
                    PressureStore.select(ctx, pid)
                    Toast.makeText(ctx, "Загружено замеров: $n" + if (n < rs.size) " (повторов: ${rs.size - n})" else "", Toast.LENGTH_LONG).show()
                    busy = false; onDismiss()
                }
            }) { Text(if (busy) "…" else "Загрузить") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Отмена") } },
    )
}
