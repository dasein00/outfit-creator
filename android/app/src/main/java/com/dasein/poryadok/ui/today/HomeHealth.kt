package com.dasein.poryadok.ui.today

import com.dasein.poryadok.ui.common.FitText
import androidx.compose.runtime.remember
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.Pressure
import com.dasein.poryadok.system.PressureStore
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.ProgressRing
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.health.PressureRoutes
import com.dasein.poryadok.ui.health.sleepMinutes
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Мини-график: линия по точкам, последняя точка выделена. */
@Composable
fun Sparkline(values: List<Float>, color: Color, modifier: Modifier = Modifier, band: ClosedFloatingPointRange<Float>? = null) {
    if (values.size < 2) return
    val lo0 = minOf(values.min(), band?.start ?: values.min()); val hi0 = maxOf(values.max(), band?.endInclusive ?: values.max())
    val pad = ((hi0 - lo0) * .15f).coerceAtLeast(1f)
    val lo = lo0 - pad; val hi = hi0 + pad
    Canvas(modifier) {
        fun x(i: Int) = size.width * i / (values.size - 1)
        fun y(v: Float) = size.height * (1f - (v - lo) / (hi - lo))
        band?.let { b -> drawRect(Color(0xFF3E9B5B).copy(alpha = .13f), Offset(0f, y(b.endInclusive)), androidx.compose.ui.geometry.Size(size.width, y(b.start) - y(b.endInclusive))) }
        for (i in 0 until values.size - 1) drawLine(color, Offset(x(i), y(values[i])), Offset(x(i + 1), y(values[i + 1])), 3.5f)
        drawCircle(color, 5.5f, Offset(x(values.size - 1), y(values.last())))
    }
}

private fun timeAgo(t: Long): String {
    val m = (System.currentTimeMillis() - t) / 60_000
    return when {
        m < 60 -> "${m.coerceAtLeast(1)} мин назад"
        m < 24 * 60 -> "${m / 60} ч назад"
        else -> Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM"))
    }
}

/**
 * Плашка «Давление» на главном. Компактно — одна строка; обычно — последний замер, вывод, средние и график;
 * крупно — ещё и сводка по всем родственникам: у кого всё хорошо, а кому стоит позвонить.
 */
@Composable
fun HomePressureCard(nav: NavHostController, size: Int) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    val diary by PressureStore.flow(ctx).collectAsState()
    val d = diary ?: PressureStore.Diary()
    val p = d.people.firstOrNull { it.id == d.current } ?: d.people.firstOrNull()
    if (p == null) {
        Tile(onClick = { nav.navigate(PressureRoutes.HOME) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph("pressure/00", 36.dp, badge = false)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Давление и пульс", fontWeight = FontWeight.SemiBold)
                    Text("Дневник для вас и близких: норма по возрасту и вывод после каждого замера", fontSize = 12.sp, color = extra.dim)
                }
            }
        }
        return
    }
    val rs = remember(d.readings, p.id) { d.readings.filter { it.personId == p.id }.sortedBy { it.time } }
    val last = rs.lastOrNull()
    val t = Pressure.target(p)
    val v = last?.let { Pressure.verdict(p, it.sys, it.dia, it.pulse, it.symptoms, it.irregular) }
    val cat = last?.let { Pressure.category(it.sys, it.dia) }
    val col = cat?.let { Color(Pressure.color(it)) } ?: MaterialTheme.colorScheme.onSurface
    Tile(onClick = { nav.navigate(PressureRoutes.HOME) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph(p.glyph, if (size == 0) 28.dp else 34.dp, badge = false)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(if (size == 0) p.name else "Давление · ${p.name}", fontSize = 12.sp, color = extra.dim, maxLines = 1)
                if (last == null) Text("Нет замеров", fontWeight = FontWeight.SemiBold)
                else Row(verticalAlignment = Alignment.Bottom) {
                    Text("${last.sys}/${last.dia}", fontSize = if (size == 0) 20.sp else 28.sp, fontWeight = FontWeight.Bold, color = col)
                    last.pulse?.let { Text("  ♥ $it", fontSize = 14.sp, modifier = Modifier.padding(bottom = 3.dp)) }
                }
            }
            if (v != null) LevelChip(v.level)
            Text(
                "+", fontSize = 22.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { nav.navigate(PressureRoutes.add(p.id)) }.padding(horizontal = 12.dp, vertical = 2.dp),
            )
        }
        if (size == 0 || last == null) return@Tile
        Text(
            "${cat!!.title} · ${timeAgo(last.time)} · норма ${t.label}", fontSize = 12.sp, color = extra.dim,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
        )
        v?.let { Text(it.headline, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(it.level.color), modifier = Modifier.padding(top = 2.dp)) }
        val assess = remember(rs, p) { com.dasein.poryadok.logic.PressureInsight.assess(p, rs, System.currentTimeMillis()) }
        Row(Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(assess.status.color).copy(alpha = .12f)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(assess.status.color)))
            Column(Modifier.padding(start = 8.dp)) {
                Text("По дневнику: ${assess.status.title.lowercase()}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(assess.status.color))
                Text(assess.simple, fontSize = 12.sp, maxLines = 2)
            }
        }
        val today = java.time.LocalDate.now()
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            remember(rs, p, today) { com.dasein.poryadok.logic.PressureInsight.days(rs, p, today.minusDays(6), today) }.forEach { c ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(c.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale("ru")), fontSize = 9.sp, color = extra.dim)
                    Box(Modifier.size(14.dp).clip(CircleShape).background(if (c.avg != null) Color(c.color) else extra.line.copy(alpha = .4f)))
                }
            }
        }
        val now = System.currentTimeMillis()
        val s = remember(rs, t) { Pressure.stats(rs, t, now) }
        // Средние по дням за 14 дней — по ним видно тренд лучше, чем по отдельным замерам.
        val byDay = remember(rs) { rs.filter { it.time > now - 14 * 86_400_000L }.groupBy { Pressure.day(it) }.toSortedMap() }
        val daySys = remember(byDay) { byDay.values.map { l -> l.map { it.sys }.average().toFloat() } }
        val dayDia = remember(byDay) { byDay.values.map { l -> l.map { it.dia }.average().toFloat() } }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MiniStat("7 дней", s.last7?.let { "${it.sys}/${it.dia}" } ?: "—", Modifier.weight(1f))
            MiniStat("30 дней", s.last30?.let { "${it.sys}/${it.dia}" } ?: "—", Modifier.weight(1f))
            MiniStat("В норме", s.inTargetShare?.let { "${(it * 100).roundToInt()} %" } ?: "—", Modifier.weight(1f))
            MiniStat("Пульс", s.last7?.pulse?.toString() ?: s.last30?.pulse?.toString() ?: "—", Modifier.weight(1f))
        }
        if (daySys.size >= 2) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(if (size >= 2) 70.dp else 52.dp)) {
                DualSpark(daySys, dayDia, t)
            }
            Row {
                Text("${byDay.firstKey().dayOfMonth}.${"%02d".format(byDay.firstKey().monthValue)}", fontSize = 10.sp, color = extra.dim, modifier = Modifier.weight(1f))
                Text("среднее за день · зелёное — норма", fontSize = 10.sp, color = extra.dim)
                Text("  ${byDay.lastKey().dayOfMonth}.${"%02d".format(byDay.lastKey().monthValue)}", fontSize = 10.sp, color = extra.dim)
            }
        }
        // Короткие выводы: утро/вечер, тренд к прошлому месяцу, пульс, руки, распределение.
        val notes = buildList {
            val m = s.morning; val e = s.evening
            if (m != null && e != null) add("Утро ${m.sys}/${m.dia} · вечер ${e.sys}/${e.dia}" + if (m.sys - e.sys >= 15) " — утренний подъём" else "")
            val l30 = s.last30; val p30 = s.prev30
            if (l30 != null && p30 != null) {
                val dd = l30.sys - p30.sys
                add((if (dd > 0) "▲ +" else if (dd < 0) "▼ " else "= ") + "$dd к прошлому месяцу")
            }
            val low = rs.count { it.time > now - 30 * 86_400_000L && (it.pulse ?: 99) < 50 }
            if (low > 0) add("Пульс ниже 50 — $low раз за месяц: покажите врачу")
            if (rs.any { it.irregular }) add("Был неровный ритм — стоит сделать ЭКГ")
            Pressure.armDifference(rs.filter { it.time > now - 30 * 86_400_000L })?.let { if (it >= 10) add("Разница между руками $it мм — мерить на руке с большим давлением") }
        }
        notes.take(if (size >= 2) 5 else 3).forEach { n ->
            Text("• $n", fontSize = 12.sp, color = if ("врачу" in n || "ЭКГ" in n || "▲" in n) extra.warn else extra.dim, maxLines = 2, modifier = Modifier.padding(top = 2.dp))
        }
        if (size >= 2 && s.byCategory.isNotEmpty()) {
            val total = s.byCategory.values.sum().toFloat()
            Row(Modifier.fillMaxWidth().padding(top = 8.dp).height(10.dp).clip(RoundedCornerShape(5.dp))) {
                Pressure.Category.entries.forEach { k -> s.byCategory[k]?.let { n -> Box(Modifier.weight(n / total).height(10.dp).background(Color(Pressure.color(k)))) } }
            }
            Text(
                Pressure.Category.entries.filter { (s.byCategory[it] ?: 0) > 0 }.joinToString(" · ") { "${it.short.lowercase()} ${((s.byCategory[it] ?: 0) * 100 / total).roundToInt()}%" },
                fontSize = 10.sp, color = extra.dim, maxLines = 2, modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (size >= 2 && d.people.size > 1) {
            Text("Семья", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
            d.people.sortedBy { it.createdAt }.forEach { o ->
                val lr = d.readings.filter { it.personId == o.id }.maxByOrNull { it.time }
                val lv = lr?.let { Pressure.verdict(o, it.sys, it.dia, it.pulse, it.symptoms, it.irregular).level }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { scope.launch { PressureStore.select(ctx, o.id) } }.padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(o.glyph, 22.dp, badge = false)
                    Text("  ${o.name}", Modifier.weight(1f), fontWeight = if (o.id == p.id) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                    Text(lr?.let { "${it.sys}/${it.dia} · ${timeAgo(it.time)}" } ?: "нет замеров", fontSize = 12.sp, color = extra.dim)
                    lv?.let { Box(Modifier.padding(start = 8.dp).size(10.dp).clip(CircleShape).background(Color(it.color))) }
                }
            }
        }
    }
}

/** Две линии (верхнее и нижнее) с полосами нормы. */
@Composable
private fun DualSpark(sys: List<Float>, dia: List<Float>, t: Pressure.Target) {
    val lo = minOf(dia.min(), t.diaLow.toFloat()) - 6; val hi = maxOf(sys.max(), t.sysHigh.toFloat()) + 6
    Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
        fun x(i: Int) = size.width * i / (sys.size - 1).coerceAtLeast(1)
        fun y(v: Float) = size.height * (1f - (v - lo) / (hi - lo))
        val band = Color(0xFF3E9B5B).copy(alpha = .13f)
        drawRect(band, Offset(0f, y(t.sysHigh.toFloat())), androidx.compose.ui.geometry.Size(size.width, y(t.sysLow.toFloat()) - y(t.sysHigh.toFloat())))
        drawRect(band, Offset(0f, y(t.diaHigh.toFloat())), androidx.compose.ui.geometry.Size(size.width, y(t.diaLow.toFloat()) - y(t.diaHigh.toFloat())))
        listOf(sys to Color(0xFFD9542B), dia to Color(0xFF4C8BD6)).forEach { (vs, col) ->
            for (i in 0 until vs.size - 1) drawLine(col, Offset(x(i), y(vs[i])), Offset(x(i + 1), y(vs[i + 1])), 3.5f)
            vs.forEachIndexed { i, v -> drawCircle(col, if (i == vs.size - 1) 6f else 3.5f, Offset(x(i), y(v))) }
        }
    }
}

@Composable
private fun LevelChip(l: Pressure.Level) {
    val short = when (l) {
        Pressure.Level.OK -> "норма"; Pressure.Level.WATCH -> "следить"; Pressure.Level.DOCTOR -> "к врачу"
        Pressure.Level.URGENT -> "сегодня к врачу"; Pressure.Level.EMERGENCY -> "103!"
    }
    Text(
        short, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(l.color), maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(l.color).copy(alpha = .15f)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(LocalExtra.current.cardHigh).padding(horizontal = 8.dp, vertical = 5.dp)) {
        Text(label, fontSize = 10.sp, color = LocalExtra.current.dim, maxLines = 1)
        FitText(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * «Здоровье: главное» — основные метрики одним взглядом, как сводка в Apple Health и Samsung Health:
 * давление, пульс, сон, шаги, вода и вес с кольцами и мини-графиками. Крупно — ещё и неделя шагов и сна столбиками.
 */
@Composable
fun HomeHealthCard(nav: NavHostController, size: Int) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val dao = Graph.dao
    val today = Dates.today()
    val dayLogs by observe(emptyList()) { dao.dayLogs() }
    val weights by observe(emptyList()) { dao.weights() }
    val sleep by observe(emptyList()) { dao.sleep() }
    val profile by observe(null) { dao.profile() }
    val diary by PressureStore.flow(ctx).collectAsState()
    val d = diary ?: PressureStore.Diary()
    val me = d.people.firstOrNull { it.id == d.current } ?: d.people.firstOrNull()
    val bp = me?.let { p -> d.readings.filter { it.personId == p.id }.sortedBy { it.time } }.orEmpty()
    val log = dayLogs.firstOrNull { it.day == today }
    val stepsGoal = profile?.stepsGoal ?: 8000
    val waterGoal = profile?.waterGoalMl ?: 2000
    val sleepGoal = profile?.sleepGoalMin ?: 480
    val lastSleep = sleep.maxByOrNull { it.day }?.takeIf { it.day >= today - 1 }
    val sleepMin = lastSleep?.let { sleepMinutes(it) } ?: 0
    val w = weights.sortedBy { it.day }
    val wNow = w.lastOrNull(); val wWeek = w.lastOrNull { it.day <= today - 7 }
    val pulses = bp.filter { it.pulse != null && it.time > System.currentTimeMillis() - 7 * 86_400_000L }.mapNotNull { it.pulse }

    Tile(onClick = { nav.navigate(Routes.HEALTH_HUB) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph("pressure/01", 22.dp, badge = false)
            Text("  Здоровье сегодня", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(Dates.label(today), fontSize = 12.sp, color = extra.dim)
        }
        if (size == 0) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                RingMini((log?.steps ?: 0) / stepsGoal.toFloat(), extra.ok, "sport/19", "${log?.steps ?: 0}")
                RingMini(sleepMin / sleepGoal.toFloat(), Color(0xFF7A6BD1), "sleep/00", if (sleepMin > 0) "${sleepMin / 60}ч${sleepMin % 60}" else "—")
                RingMini((log?.waterMl ?: 0) / waterGoal.toFloat(), Color(0xFF4C8BD6), "ui:drop", "${(log?.waterMl ?: 0) / 100 / 10.0} л")
                val lb = bp.lastOrNull()
                RingMini(if (lb != null) 1f else 0f, lb?.let { Color(Pressure.color(Pressure.category(it.sys, it.dia))) } ?: extra.line, "pressure/00", lb?.let { "${it.sys}/${it.dia}" } ?: "—")
            }
            return@Tile
        }
        val lb = bp.lastOrNull()
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric(
                "pressure/00", "Давление", lb?.let { "${it.sys}/${it.dia}" } ?: "—",
                lb?.let { Pressure.category(it.sys, it.dia).short } ?: "нет замеров",
                lb?.let { Color(Pressure.color(Pressure.category(it.sys, it.dia))) }, Modifier.weight(1f),
                { nav.navigate(PressureRoutes.HOME) },
            ) { if (bp.size >= 2) Sparkline(bp.takeLast(10).map { it.sys.toFloat() }, Color(0xFFD9542B), Modifier.fillMaxWidth().height(22.dp)) }
            Metric(
                "pressure/01", "Пульс", pulses.takeIf { it.isNotEmpty() }?.average()?.roundToInt()?.toString() ?: "—",
                if (pulses.isEmpty()) "за 7 дней" else "средний за 7 дней",
                pulses.takeIf { it.isNotEmpty() }?.average()?.let { if (it in 60.0..100.0) extra.ok else extra.warn }, Modifier.weight(1f),
                { nav.navigate(PressureRoutes.HOME) },
            ) { if (pulses.size >= 2) Sparkline(pulses.takeLast(10).map { it.toFloat() }, Color(0xFFB0457A), Modifier.fillMaxWidth().height(22.dp), 60f..100f) }
            Metric(
                "sleep/00", "Сон", if (sleepMin > 0) "${sleepMin / 60} ч ${sleepMin % 60} м" else "—", "цель ${sleepGoal / 60} ч",
                if (sleepMin == 0) null else if (sleepMin >= sleepGoal - 30) extra.ok else extra.warn, Modifier.weight(1f),
                { nav.navigate(Routes.wellbeing(1)) },
            ) { com.dasein.poryadok.ui.common.Bar(sleepMin / sleepGoal.toFloat(), Color(0xFF7A6BD1), Modifier.padding(top = 8.dp)) }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val steps = log?.steps ?: 0
            Metric("sport/19", "Шаги", "$steps", "из $stepsGoal", if (steps >= stepsGoal) extra.ok else null, Modifier.weight(1f), { nav.navigate(Routes.STEPS) }) {
                com.dasein.poryadok.ui.common.Bar(steps / stepsGoal.toFloat(), extra.ok, Modifier.padding(top = 8.dp))
            }
            val ml = log?.waterMl ?: 0
            Metric("ui:drop", "Вода", "$ml мл", "из $waterGoal", if (ml >= waterGoal) extra.ok else null, Modifier.weight(1f), { nav.navigate(Routes.wellbeing(2)) }) {
                com.dasein.poryadok.ui.common.Bar(ml / waterGoal.toFloat(), Color(0xFF4C8BD6), Modifier.padding(top = 8.dp))
            }
            val delta = if (wNow != null && wWeek != null) wNow.kg - wWeek.kg else null
            Metric(
                "sport/11", "Вес", wNow?.let { "%.1f".format(it.kg).replace('.', ',') } ?: "—",
                delta?.let { (if (it > 0) "+" else "") + "%.1f".format(it).replace('.', ',') + " за неделю" } ?: "кг",
                null, Modifier.weight(1f), { nav.navigate(Routes.health(1)) },
            ) { if (w.size >= 2) Sparkline(w.takeLast(14).map { it.kg.toFloat() }, Color(0xFFC79246), Modifier.fillMaxWidth().height(22.dp)) }
        }
        if (size >= 2) {
            val days = (6 downTo 0).map { today - it }
            Text("Неделя", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 10.dp))
            WeekBars("Шаги", days.map { day -> (dayLogs.firstOrNull { it.day == day }?.steps ?: 0).toFloat() }, stepsGoal.toFloat(), extra.ok)
            WeekBars("Сон", days.map { day -> (sleep.firstOrNull { it.day == day }?.let { sleepMinutes(it) } ?: 0).toFloat() }, sleepGoal.toFloat(), Color(0xFF7A6BD1))
            if (bp.isNotEmpty()) WeekBars(
                "Давление",
                days.map { day -> bp.filter { Pressure.day(it).toEpochDay() == day }.map { it.sys }.takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: 0f },
                me?.let { Pressure.target(it).sysHigh.toFloat() } ?: 130f, Color(0xFFD9542B), overIsBad = true,
            )
        }
    }
}

@Composable
private fun RingMini(progress: Float, color: Color, glyph: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ProgressRing(progress, color, size = 48.dp, stroke = 5.dp) { Glyph(glyph, 22.dp, badge = false) }
        FitText(value, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Metric(glyph: String, label: String, value: String, sub: String, color: Color?, modifier: Modifier, onClick: () -> Unit, chart: @Composable () -> Unit) {
    val extra = LocalExtra.current
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(extra.cardHigh).clickable(onClick = onClick).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph(glyph, 16.dp, badge = false)
            Text(" $label", fontSize = 11.sp, color = extra.dim, maxLines = 1)
        }
        FitText(value, Modifier.padding(top = 2.dp), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color ?: MaterialTheme.colorScheme.onSurface)
        Text(sub, fontSize = 10.sp, color = extra.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        chart()
    }
}

@Composable
private fun WeekBars(label: String, values: List<Float>, goal: Float, color: Color, overIsBad: Boolean = false, height: Dp = 34.dp) {
    val extra = LocalExtra.current
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
        Text(label, fontSize = 11.sp, color = extra.dim, modifier = Modifier.padding(end = 6.dp).size(width = 62.dp, height = 14.dp))
        val top = maxOf(values.maxOrNull() ?: 0f, goal) * 1.05f
        values.forEach { v ->
            val bad = overIsBad && v > goal
            Box(Modifier.weight(1f).height(height), contentAlignment = Alignment.BottomCenter) {
                Box(
                    Modifier.fillMaxWidth(.6f).height(height * (if (top > 0) (v / top).coerceIn(0.04f, 1f) else 0.04f))
                        .clip(RoundedCornerShape(3.dp)).background(if (v <= 0f) extra.line else if (bad) Color(0xFFD9542B) else color.copy(alpha = if (!overIsBad && v >= goal) 1f else .6f)),
                )
            }
        }
    }
}
