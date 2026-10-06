@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.health

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dasein.poryadok.logic.Pressure
import com.dasein.poryadok.logic.PressureInsight
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.theme.LocalExtra
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val Z: ZoneId get() = ZoneId.systemDefault()
private val RU = Locale("ru")
private const val DAY = 86_400_000L
private val SYS_COL = Color(0xFFD9542B)
private val DIA_COL = Color(0xFF4C8BD6)

private fun signed(v: Int) = if (v > 0) "+$v" else "$v"
private fun arrow(v: Int, eps: Int = 2) = when { v >= eps -> "↑"; v <= -eps -> "↓"; else -> "→" }

/** Состояние по данным дневника — 4 уровня, с кнопкой «Почему?». В режиме врача — подробный вывод. */
@Composable
fun StatusCard(a: PressureInsight.Assessment, doctor: Boolean) {
    val extra = LocalExtra.current
    var why by remember { mutableStateOf(false) }
    val col = Color(a.status.color)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(col.copy(alpha = .13f)).padding(14.dp)) {
        Text("СОСТОЯНИЕ ПО ДАННЫМ ДНЕВНИКА", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = extra.dim)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(col))
            Text("  ${a.status.title}", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = col, modifier = Modifier.weight(1f))
            Text(
                "ⓘ Почему?", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { why = true }.padding(6.dp),
            )
        }
        Text(if (doctor) a.conclusion else a.simple, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
        if (doctor) a.basis.take(4).forEach { Text("• $it", fontSize = 12.sp, color = extra.dim, lineHeight = 16.sp) }
        Text("Это интерпретация данных, а не диагноз. Решения о лечении принимает врач.", fontSize = 11.sp, color = extra.dim, modifier = Modifier.padding(top = 6.dp))
    }
    if (why) AlertDialog(
        onDismissRequest = { why = false },
        title = { Text("Почему приложение так считает") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(a.conclusion, fontWeight = FontWeight.Medium)
                Text("Оценка сформирована на основании:", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                a.basis.forEach { Text("• $it", fontSize = 14.sp, lineHeight = 20.sp) }
                Text("Как считается уровень", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                Text(
                    "🟢 стабильное — среднее за 7 дней ниже порога и без роста; 🟡 требует наблюдения — у порога, рост к прошлой неделе ≥5 мм или превышение без устойчивости; " +
                        "🟠 устойчиво повышенное — выше порога и за 7, и за 14 дней; 🔴 требует медицинской оценки — среднее ≥160/100, повторные значения ≥180/110 " +
                        "или повышение при диабете, болезни почек или сердца. Нужно минимум 3 дня и 4 замера за неделю.",
                    fontSize = 13.sp, lineHeight = 18.sp,
                )
                Text("Источники: ${PressureInsight.SOURCES}.", fontSize = 12.sp, color = LocalExtra.current.dim, modifier = Modifier.padding(top = 10.dp))
            }
        },
        confirmButton = { TextButton(onClick = { why = false }) { Text("Понятно") } },
    )
}

/** «Ландшафт давления»: цветная лента последних 7 дней, среднее и тренд к прошлой неделе. */
@Composable
fun WeekStrip(p: Pressure.Person, rs: List<Pressure.Reading>) {
    val extra = LocalExtra.current
    val today = LocalDate.now()
    val cells = PressureInsight.days(rs, p, today.minusDays(6), today)
    val now = System.currentTimeMillis()
    val a7 = Pressure.avg(PressureInsight.window(rs, now - 7 * DAY, now))
    val b7 = Pressure.avg(PressureInsight.window(rs, now - 14 * DAY, now - 7 * DAY))
    Tile {
        Text("Неделя", fontSize = 12.sp, color = extra.dim)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            cells.forEach { c ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(c.date.dayOfWeek.getDisplayName(TextStyle.SHORT, RU).uppercase(), fontSize = 11.sp, color = if (c.date == today) MaterialTheme.colorScheme.primary else extra.dim)
                    Box(
                        Modifier.padding(top = 4.dp).size(28.dp).clip(CircleShape)
                            .background(if (c.avg != null) Color(c.color) else Color.Transparent)
                            .border(1.dp, if (c.avg == null) extra.line else Color.Transparent, CircleShape),
                    )
                    Text(c.avg?.sys?.toString() ?: "—", fontSize = 10.sp, color = extra.dim, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Среднее за 7 дней", fontSize = 12.sp, color = extra.dim)
                Text(a7?.let { "${it.sys}/${it.dia}" } ?: "—", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            }
            if (a7 != null && b7 != null) Column(horizontalAlignment = Alignment.End) {
                val d = a7.sys - b7.sys
                Text("Тренд к прошлой неделе", fontSize = 12.sp, color = extra.dim)
                Text("${arrow(d)} ${abs(d)} мм рт. ст.", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = if (d >= 3) extra.danger else if (d <= -3) extra.ok else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** «Отклонение от моего обычного»: последний замер против среднего за 30 дней. */
@Composable
fun DeviationCard(rs: List<Pressure.Reading>) {
    val last = rs.maxByOrNull { it.time } ?: return
    val d = PressureInsight.deviation(rs, last) ?: return
    val extra = LocalExtra.current
    Tile {
        Text("Отклонение от вашего обычного", fontSize = 12.sp, color = extra.dim)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Обычно (30 дней): ${d.usual.sys}/${d.usual.dia}", fontSize = 13.sp)
                Text("Последний: ${last.sys}/${last.dia}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("САД ${signed(d.dSys)}", fontWeight = FontWeight.Bold, color = if (d.dSys >= 15) extra.danger else if (d.dSys <= -15) DIA_COL else MaterialTheme.colorScheme.onSurface)
                Text("ДАД ${signed(d.dDia)}", fontWeight = FontWeight.Bold, color = if (d.dDia >= 10) extra.danger else MaterialTheme.colorScheme.onSurface)
            }
        }
        Text(d.text, fontSize = 13.sp, color = if (d.notable) extra.warn else extra.dim, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/**
 * График САД и ДАД: периоды 7 дней … 1 год, «среднее за день» или «все измерения»,
 * масштаб двумя пальцами, сдвиг пальцем после приближения, двойное касание — вернуть, касание — значение точки.
 */
@Composable
fun ZoomChart(p: Pressure.Person, rs: List<Pressure.Reading>) {
    val extra = LocalExtra.current
    var range by rememberSaveable { mutableIntStateOf(30) }
    var daily by rememberSaveable { mutableStateOf(true) }
    val now = System.currentTimeMillis()
    val pts = remember(rs, range, daily) { PressureInsight.points(rs, now - range * DAY, now, daily) }
    val t = Pressure.target(p)
    Tile(padding = 10.dp) {
        Text("Артериальное давление", fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
            listOf(7 to "7 дней", 30 to "30 дней", 90 to "3 мес", 180 to "6 мес", 365 to "1 год").forEach { (d, s) -> Pill(s, range == d) { range = d } }
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("Среднее за день", daily) { daily = true }
            Pill("Все измерения", !daily) { daily = false }
        }
        if (pts.size < 2) {
            Text("За этот период мало данных для графика.", fontSize = 13.sp, color = extra.dim, modifier = Modifier.padding(vertical = 20.dp))
            return@Tile
        }
        val t0 = minOf(pts.first().time, now - range * DAY); val t1 = now
        val full = (t1 - t0).coerceAtLeast(DAY)
        var viewStart by remember(pts) { mutableLongStateOf(t0) }
        var viewSpan by remember(pts) { mutableLongStateOf(full) }
        var selected by remember(pts) { mutableStateOf<PressureInsight.Point?>(null) }
        val minSpan = if (daily) 3 * DAY else DAY / 2
        val lo = (minOf(pts.minOf { it.dia }, t.diaLow.toFloat()) - 8).coerceAtLeast(30f)
        val hi = maxOf(pts.maxOf { it.sys }, t.sysHigh.toFloat(), 140f) + 8
        val visible = pts.filter { it.time in viewStart..(viewStart + viewSpan) }
        val meanSys = visible.map { it.sys }.average().takeIf { !it.isNaN() }?.toFloat()
        val meanDia = visible.map { it.dia }.average().takeIf { !it.isNaN() }?.toFloat()
        val lineCol = extra.line; val dimCol = extra.dim.toArgb(); val onCol = MaterialTheme.colorScheme.onSurface.toArgb()
        val df = DateTimeFormatter.ofPattern(if (viewSpan > 120 * DAY) "MMM" else "d MMM", RU)
        Box(Modifier.fillMaxWidth().height(230.dp).padding(top = 8.dp)) {
            Canvas(
                Modifier.fillMaxWidth().height(230.dp)
                    .pointerInput(pts) {
                        // Два пальца — масштаб и сдвиг; один палец сдвигает только приближенный график по горизонтали,
                        // иначе жест достаётся прокрутке экрана.
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val ev = awaitPointerEvent(PointerEventPass.Main)
                                val pressed = ev.changes.filter { it.pressed }
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                if (pressed.size >= 2) {
                                    val a = pressed[0]; val b = pressed[1]
                                    val prevD = abs(a.previousPosition.x - b.previousPosition.x).coerceAtLeast(20f)
                                    val d = abs(a.position.x - b.position.x).coerceAtLeast(20f)
                                    val cx = (a.position.x + b.position.x) / 2; val pcx = (a.previousPosition.x + b.previousPosition.x) / 2
                                    val tAt = viewStart + (cx / w * viewSpan).toLong()
                                    val ns = (viewSpan * prevD / d).toLong().coerceIn(minSpan, full)
                                    var st = tAt - (cx / w * ns).toLong() - ((cx - pcx) / w * ns).toLong()
                                    st = st.coerceIn(t0, t0 + full - ns)
                                    viewSpan = ns; viewStart = st
                                    pressed.forEach { if (it.positionChanged()) it.consume() }
                                } else if (pressed.size == 1 && viewSpan < full) {
                                    val c = pressed[0]
                                    val dx = c.position.x - c.previousPosition.x; val dy = c.position.y - c.previousPosition.y
                                    if (abs(dx) > abs(dy)) {
                                        viewStart = (viewStart - (dx / w * viewSpan).toLong()).coerceIn(t0, t0 + full - viewSpan)
                                        if (c.positionChanged()) c.consume()
                                    }
                                }
                            } while (ev.changes.any { it.pressed })
                        }
                    }
                    .pointerInput(pts) {
                        detectTapGestures(
                            onDoubleTap = { viewStart = t0; viewSpan = full; selected = null },
                            onTap = { o ->
                                val tm = viewStart + (o.x / size.width * viewSpan).toLong()
                                selected = pts.minByOrNull { abs(it.time - tm) }?.takeIf { abs(it.time - tm) < viewSpan / 8 }
                            },
                        )
                    },
            ) {
                val left = 34f; val bottom = 26f
                val cw = size.width - left; val ch = size.height - bottom
                fun x(tm: Long) = left + cw * ((tm - viewStart).toFloat() / viewSpan)
                fun y(v: Float) = ch * (1f - (v - lo) / (hi - lo))
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 24f; color = dimCol }
                // Сетка и шкала значений.
                var g = ((lo / 20).toInt() + 1) * 20
                while (g < hi) {
                    drawLine(lineCol.copy(alpha = .5f), Offset(left, y(g.toFloat())), Offset(size.width, y(g.toFloat())), 1f)
                    drawIntoCanvas { it.nativeCanvas.drawText("$g", 0f, y(g.toFloat()) + 8f, paint) }
                    g += 20
                }
                // Целевой диапазон.
                val band = Color(0xFF3E9B5B).copy(alpha = .13f)
                drawRect(band, Offset(left, y(t.sysHigh.toFloat())), Size(cw, y(t.sysLow.toFloat()) - y(t.sysHigh.toFloat())))
                drawRect(band, Offset(left, y(t.diaHigh.toFloat())), Size(cw, y(t.diaLow.toFloat()) - y(t.diaHigh.toFloat())))
                clipRect(left = left, top = 0f, right = size.width, bottom = ch) {
                    // Средние за видимый период — пунктиром.
                    meanSys?.let { drawLine(SYS_COL.copy(alpha = .6f), Offset(left, y(it)), Offset(size.width, y(it)), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))) }
                    meanDia?.let { drawLine(DIA_COL.copy(alpha = .6f), Offset(left, y(it)), Offset(size.width, y(it)), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))) }
                    val r = if (daily || viewSpan < 20 * DAY) 5f else 3f
                    listOf(SYS_COL to { q: PressureInsight.Point -> q.sys }, DIA_COL to { q: PressureInsight.Point -> q.dia }).forEach { (col, f) ->
                        pts.zipWithNext().forEach { (a, b) ->
                            if (b.time >= viewStart - viewSpan / 10 && a.time <= viewStart + viewSpan * 11 / 10) drawLine(col, Offset(x(a.time), y(f(a))), Offset(x(b.time), y(f(b))), 3f)
                        }
                        visible.forEach { q -> drawCircle(col, r, Offset(x(q.time), y(f(q)))) }
                    }
                }
                // Даты по оси времени.
                listOf(0f, .5f, 1f).forEach { k ->
                    val tm = viewStart + (viewSpan * k).toLong()
                    val s = Instant.ofEpochMilli(tm).atZone(Z).format(df)
                    paint.textAlign = when (k) { 0f -> Paint.Align.LEFT; 1f -> Paint.Align.RIGHT; else -> Paint.Align.CENTER }
                    drawIntoCanvas { it.nativeCanvas.drawText(s, left + cw * k, size.height - 4f, paint) }
                }
                selected?.let { q ->
                    val sx = x(q.time)
                    drawLine(lineCol, Offset(sx, 0f), Offset(sx, ch), 2f)
                    val label = Instant.ofEpochMilli(q.time).atZone(Z).format(DateTimeFormatter.ofPattern(if (daily) "d MMM" else "d MMM HH:mm", RU)) +
                        " · ${q.sys.roundToInt()}/${q.dia.roundToInt()}" + (q.pulse?.let { " ♥${it.roundToInt()}" } ?: "")
                    paint.textAlign = Paint.Align.CENTER; paint.color = onCol; paint.textSize = 28f
                    val tx = sx.coerceIn(left + 150f, size.width - 150f)
                    drawRect(Color.Black.copy(alpha = .06f), Offset(tx - 160f, 2f), Size(320f, 38f))
                    drawIntoCanvas { it.nativeCanvas.drawText(label, tx, 30f, paint) }
                }
            }
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(SYS_COL)); Text("САД", fontSize = 11.sp, color = extra.dim)
            Box(Modifier.size(10.dp).clip(CircleShape).background(DIA_COL)); Text("ДАД", fontSize = 11.sp, color = extra.dim)
            Text("- - среднее", fontSize = 11.sp, color = extra.dim)
            Box(Modifier.size(10.dp).background(Color(0xFF3E9B5B).copy(alpha = .3f))); Text("цель", fontSize = 11.sp, color = extra.dim)
        }
        Text(
            (meanSys?.let { "Среднее на экране: ${it.roundToInt()}/${meanDia?.roundToInt()} · " } ?: "") +
                "двумя пальцами — приблизить, пальцем — сдвинуть, двойное касание — весь период",
            fontSize = 11.sp, color = extra.dim, modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** «Тренд здоровья»: изменение средних САД, ДАД и пульса за 30 дней к предыдущим 30. */
@Composable
fun DynamicsCard(rs: List<Pressure.Reading>, days: Int = 30) {
    val d = PressureInsight.dynamics(rs, days, System.currentTimeMillis()) ?: return
    val extra = LocalExtra.current
    Tile {
        Text("Динамика за $days дней", fontWeight = FontWeight.SemiBold)
        Row(Modifier.padding(top = 6.dp)) {
            listOf(Triple("САД", d.dSys, "мм"), Triple("ДАД", d.dDia, "мм")).plus(listOfNotNull(d.dPulse?.let { Triple("Пульс", it, "уд/мин") })).forEach { (l, v, u) ->
                Column(Modifier.weight(1f)) {
                    Text(l, fontSize = 12.sp, color = extra.dim)
                    Text("${signed(v)} $u ${arrow(v, 1)}", fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                        color = if (l == "Пульс") MaterialTheme.colorScheme.onSurface else if (v >= 3) extra.danger else if (v <= -3) extra.ok else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Text("Сейчас ${d.now.sys}/${d.now.dia} · было ${d.before.sys}/${d.before.dia}", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp))
        Text(d.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = when (d.better) { true -> extra.ok; false -> extra.danger; null -> MaterialTheme.colorScheme.onSurface }, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Суточный профиль: утро, день, вечер. */
@Composable
fun DayProfileCard(rs: List<Pressure.Reading>) {
    val now = System.currentTimeMillis()
    val prof = PressureInsight.dayProfile(PressureInsight.window(rs, now - 30 * DAY, now))
    if (prof.morning == null && prof.evening == null) return
    val extra = LocalExtra.current
    Tile {
        Text("Суточный профиль · 30 дней", fontWeight = FontWeight.SemiBold)
        Row(Modifier.padding(top = 6.dp)) {
            listOf(Triple("🌅 Утро", prof.morning, "4–11 ч"), Triple("☀️ День", prof.day, "12–16 ч"), Triple("🌙 Вечер", prof.evening, "17–23 ч")).forEach { (l, a, h) ->
                Column(Modifier.weight(1f)) {
                    Text(l, fontSize = 13.sp)
                    Text(a?.let { "${it.sys}/${it.dia}" } ?: "—", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(a?.let { "$h · ${it.n} зам." } ?: h, fontSize = 10.sp, color = extra.dim)
                }
            }
        }
        prof.pattern?.let { Text(it, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
        Text("Это наблюдение по дневнику, не оценка суточного ритма — для неё нужно суточное мониторирование (СМАД).", fontSize = 11.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Календарь давления: месяц, размер и цвет точки — среднее за день. */
@Composable
fun PressureCalendar(p: Pressure.Person, rs: List<Pressure.Reading>) {
    val extra = LocalExtra.current
    var ym by remember { mutableStateOf(YearMonth.now()) }
    var picked by remember(ym) { mutableStateOf<PressureInsight.DayCell?>(null) }
    val cells = PressureInsight.days(rs, p, ym.atDay(1), ym.atEndOfMonth())
    Tile {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹", fontSize = 22.sp, modifier = Modifier.clip(CircleShape).clickable { ym = ym.minusMonths(1) }.padding(horizontal = 12.dp))
            Text(ym.month.getDisplayName(TextStyle.FULL_STANDALONE, RU).replaceFirstChar { it.uppercase() } + " ${ym.year}", fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Text("›", fontSize = 22.sp, modifier = Modifier.clip(CircleShape).clickable { if (ym < YearMonth.now()) ym = ym.plusMonths(1) }.padding(horizontal = 12.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach { Text(it, fontSize = 11.sp, color = extra.dim, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
        }
        val lead = ym.atDay(1).dayOfWeek.value - 1
        val all: List<PressureInsight.DayCell?> = List(lead) { null } + cells
        all.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                (0 until 7).forEach { i ->
                    val c = week.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(1f).clickable(enabled = c != null) { picked = c }, contentAlignment = Alignment.Center) {
                        if (c != null) {
                            val a = c.avg
                            if (a == null) Box(Modifier.size(8.dp).clip(CircleShape).border(1.dp, extra.line, CircleShape))
                            else {
                                val k = ((a.sys - 100) / 70f).coerceIn(0f, 1f)
                                Box(Modifier.size((12 + 20 * k).dp).clip(CircleShape).background(Color(c.color)))
                            }
                            Text("${c.date.dayOfMonth}", fontSize = 9.sp, color = extra.dim, modifier = Modifier.align(Alignment.BottomEnd).padding(1.dp))
                        }
                    }
                }
            }
        }
        val withData = cells.count { it.avg != null }
        Text(
            picked?.let { c -> "${c.date.dayOfMonth} ${c.date.month.getDisplayName(TextStyle.FULL, RU)}: " + (c.avg?.let { "среднее ${it.sys}/${it.dia}, замеров ${it.n}" + (it.pulse?.let { pl -> ", пульс $pl" } ?: "") } ?: "нет замеров") }
                ?: "Дней с замерами: $withData из ${cells.size}. Чем крупнее точка — тем выше среднее за день; цвет — уровень.",
            fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Стабильность за 14 дней. */
@Composable
fun StabilityCard(rs: List<Pressure.Reading>) {
    val s = PressureInsight.stability(rs, System.currentTimeMillis()) ?: return
    val extra = LocalExtra.current
    Tile {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Стабильность", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${s.score} %", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (s.high) extra.warn else extra.ok)
        }
        com.dasein.poryadok.ui.common.Bar(s.score / 100f, if (s.high) extra.warn else extra.ok, Modifier.padding(top = 6.dp), height = 10.dp)
        Text("Доля замеров в пределах ±10 мм от вашего среднего · разброс САД ±${s.sdSys.roundToInt()}, ДАД ±${s.sdDia.roundToInt()} · ${s.n} замеров за 14 дней", fontSize = 11.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp))
        Text(s.text, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/** «Что сейчас важно» — динамический блок подсказок. */
@Composable
fun ImportantNow(p: Pressure.Person, rs: List<Pressure.Reading>) {
    val tips = PressureInsight.important(p, rs, System.currentTimeMillis())
    if (tips.isEmpty()) return
    val extra = LocalExtra.current
    SectionTitle("Что сейчас важно")
    tips.forEach { t ->
        val col = when (t.kind) { "alert" -> extra.danger; "watch" -> extra.warn; "ok" -> extra.ok; else -> MaterialTheme.colorScheme.primary }
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(14.dp)).background(col.copy(alpha = .1f)).padding(12.dp)) {
            Box(Modifier.padding(top = 4.dp).size(10.dp).clip(CircleShape).background(col))
            Column(Modifier.padding(start = 10.dp)) {
                Text(t.title, fontWeight = FontWeight.SemiBold, color = col)
                Text(t.text, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}

/** «Что можно изменить» — персональный порядок. */
@Composable
fun ChangesCard(p: Pressure.Person, rs: List<Pressure.Reading>) {
    val extra = LocalExtra.current
    SectionTitle("Сегодня для вас актуально")
    Tile {
        PressureInsight.changes(p, rs).take(7).forEachIndexed { i, (t, s) ->
            Row(Modifier.padding(vertical = 4.dp)) {
                Text("${i + 1}.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(width = 24.dp, height = 20.dp))
                Column { Text(t, fontWeight = FontWeight.Medium); Text(s, fontSize = 12.sp, color = extra.dim, lineHeight = 16.sp) }
            }
        }
    }
}

/** Отчёт за неделю + PDF для врача. */
@Composable
fun WeeklyReportCard(p: Pressure.Person, rs: List<Pressure.Reading>) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val w = PressureInsight.weekly(p, rs, System.currentTimeMillis())
    SectionTitle("Отчёт за неделю")
    Tile {
        @Composable
        fun Line(l: String, v: String, bold: Boolean = false) = Row(Modifier.padding(vertical = 2.dp)) {
            Text(l, fontSize = 13.sp, color = extra.dim, modifier = Modifier.weight(1f)); Text(v, fontSize = 13.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
        }
        Line("Давление контролировалось", "${w.daysCovered}/7 дней")
        Line("Измерений", "${w.n}")
        Line("Среднее", w.avg?.let { "${it.sys}/${it.dia}" } ?: "—", true)
        Line("Предыдущая неделя", w.prev?.let { "${it.sys}/${it.dia}" } ?: "—")
        if (w.avg != null && w.prev != null) Line("Изменение", "${signed(w.avg.sys - w.prev.sys)}/${signed(w.avg.dia - w.prev.dia)}")
        Line("Тренд", w.trend)
        Line("Качество мониторинга", w.quality)
        w.note?.let { Text("На что обратить внимание: $it", fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
        Text("Рекомендация: ${w.advice}", fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
        Button(onClick = { sharePdf(ctx, p, rs) }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("Сформировать PDF для врача") }
    }
}

/** Режим врача: статистика за период. */
@Composable
fun DoctorStats(rs: List<Pressure.Reading>) {
    val extra = LocalExtra.current
    var days by remember { mutableIntStateOf(30) }
    val d = PressureInsight.doctor(rs, days, System.currentTimeMillis())
    SectionTitle("Сводка для врача")
    Tile {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(14, 30, 90).forEach { k -> Pill("$k дней", days == k) { days = k } } }
        val df = DateTimeFormatter.ofPattern("d.MM HH:mm")
        val lines = listOf(
            "Среднее домашнее АД" to (d.mean?.let { "${it.sys}/${it.dia} мм рт. ст." } ?: "—"),
            "n" to "${d.n} измерений",
            "Утро" to (d.morning?.let { "${it.sys}/${it.dia} (n=${it.n})" } ?: "—"),
            "Вечер" to (d.evening?.let { "${it.sys}/${it.dia} (n=${it.n})" } ?: "—"),
            "SD" to "${"%.1f".format(d.sdSys)}/${"%.1f".format(d.sdDia)}",
            "Тренд САД" to (d.slopePerWeek?.let { "${"%+.1f".format(it)} мм/нед" } ?: "—"),
            "Доля ≥135/85" to "${d.elevatedShare} %",
            "Максимум" to (d.maxR?.let { "${it.sys}/${it.dia} · ${Instant.ofEpochMilli(it.time).atZone(Z).format(df)}" } ?: "—"),
            "Минимум" to (d.minR?.let { "${it.sys}/${it.dia} · ${Instant.ofEpochMilli(it.time).atZone(Z).format(df)}" } ?: "—"),
            "Пульс" to (d.mean?.pulse?.let { "ср. $it, мин ${d.pulseMin}, макс ${d.pulseMax}" } ?: "—"),
            "К предыдущему периоду" to (if (d.mean != null && d.prev != null) "${signed(d.mean.sys - d.prev.sys)}/${signed(d.mean.dia - d.prev.dia)}" else "—"),
        )
        lines.forEach { (l, v) ->
            Row(Modifier.padding(vertical = 2.dp)) { Text(l, fontSize = 13.sp, color = extra.dim, modifier = Modifier.weight(1f)); Text(v, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
        }
    }
}

/** Закономерности в дневнике. */
@Composable
fun PatternsCard(rs: List<Pressure.Reading>) {
    val list = PressureInsight.patterns(rs, System.currentTimeMillis())
    val extra = LocalExtra.current
    SectionTitle("Анализ закономерностей")
    Tile {
        if (list.isEmpty()) Text("Устойчивых закономерностей пока не найдено. Отмечайте обстоятельства замера (сон, кофе, стресс, пропуск лекарства) — так находится больше связей.", fontSize = 13.sp, color = extra.dim)
        list.forEach { Text("• $it", fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(vertical = 2.dp)) }
        Text("Это наблюдения по вашим записям, а не причины: связь не означает, что одно вызывает другое.", fontSize = 11.sp, color = extra.dim, modifier = Modifier.padding(top = 6.dp))
    }
}

/** PDF для врача: профиль, оценка, сводка, график по дням и таблица замеров за 30 дней. */
fun sharePdf(ctx: Context, p: Pressure.Person, rs: List<Pressure.Reading>) {
    runCatching {
        val now = System.currentTimeMillis()
        val doc = PdfDocument()
        val W = 595; val H = 842
        val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f }
        val bold = Paint(txt).apply { isFakeBoldText = true; textSize = 11f }
        val head = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 16f; isFakeBoldText = true }
        var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, 1).create())
        var c = page.canvas
        var y = 40f
        fun line(s: String, paint: Paint = txt, dy: Float = 14f) { c.drawText(s, 36f, y, paint); y += dy }
        fun newPageIfNeeded(n: Int = 1) {
            if (y + 14f * n > H - 40) { doc.finishPage(page); page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, doc.pages.size + 1).create()); c = page.canvas; y = 40f }
        }
        line("Дневник артериального давления — ${p.name}", head, 20f)
        line(listOfNotNull(p.age()?.let { "$it лет" }, if (p.sex == 1) "женщина" else "мужчина", p.heightCm?.let { "рост $it см" }, p.weightKg?.let { "вес ${it.roundToInt()} кг" }, p.bmi?.let { "ИМТ ${"%.1f".format(it)}" }, p.waistCm?.let { "талия $it см" }).joinToString(", "))
        val dx = listOfNotNull("гипертония".takeIf { p.hypertension }, "на лечении".takeIf { p.treated }, "диабет".takeIf { p.diabetes }, "болезнь почек".takeIf { p.kidney }, "ИБС/инфаркт/инсульт".takeIf { p.heart }, "курение".takeIf { p.smoker }, "холестерин".takeIf { p.cholesterol })
        if (dx.isNotEmpty()) line("Факторы и состояния: ${dx.joinToString(", ")}")
        if (p.meds.isNotBlank()) line("Лекарства: ${p.meds}")
        line("Цель: ${Pressure.target(p).label} · сформировано ${LocalDate.now()}", dy = 20f)
        val a = PressureInsight.assess(p, rs, now)
        line("Оценка по данным дневника: ${a.status.title}", bold)
        a.basis.forEach { line("  • $it") }
        y += 6f
        val d = PressureInsight.doctor(rs, 30, now)
        line("Сводка за 30 дней", bold)
        line("Среднее: ${d.mean?.let { "${it.sys}/${it.dia}" } ?: "—"}, n = ${d.n}; утро ${d.morning?.let { "${it.sys}/${it.dia}" } ?: "—"}; вечер ${d.evening?.let { "${it.sys}/${it.dia}" } ?: "—"}")
        line("SD: ${"%.1f".format(d.sdSys)}/${"%.1f".format(d.sdDia)}; доля ≥135/85: ${d.elevatedShare} %; тренд САД: ${d.slopePerWeek?.let { "%+.1f мм/нед".format(it) } ?: "—"}")
        line("Пульс: ${d.mean?.pulse ?: "—"} (мин ${d.pulseMin ?: "—"}, макс ${d.pulseMax ?: "—"})", dy = 18f)
        // График средних за день.
        val pts = PressureInsight.points(rs, now - 30 * DAY, now, true)
        if (pts.size >= 2) {
            val gx = 36f; val gw = W - 72f; val gy = y; val gh = 150f
            val lo = (pts.minOf { it.dia } - 10).coerceAtLeast(30f); val hi = pts.maxOf { it.sys } + 10
            val grid = Paint().apply { color = 0x33000000; strokeWidth = .5f }
            val tick = Paint(txt).apply { textSize = 8f }
            var g = ((lo / 20).toInt() + 1) * 20
            while (g < hi) { val yy = gy + gh * (1 - (g - lo) / (hi - lo)); c.drawLine(gx, yy, gx + gw, yy, grid); c.drawText("$g", gx - 18f, yy + 3f, tick); g += 20 }
            val t0 = pts.first().time; val t1 = pts.last().time.coerceAtLeast(t0 + 1)
            fun px(tm: Long) = gx + gw * (tm - t0) / (t1 - t0)
            fun py(v: Float) = gy + gh * (1 - (v - lo) / (hi - lo))
            listOf(SYS_COL.toArgb() to { q: PressureInsight.Point -> q.sys }, DIA_COL.toArgb() to { q: PressureInsight.Point -> q.dia }).forEach { (col, f) ->
                val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col; strokeWidth = 1.6f }
                pts.zipWithNext().forEach { (u, v) -> c.drawLine(px(u.time), py(f(u)), px(v.time), py(f(v)), lp) }
                pts.forEach { q -> c.drawCircle(px(q.time), py(f(q)), 2f, lp) }
            }
            y = gy + gh + 22f
        }
        line("Замеры за 30 дней", bold)
        val df = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        PressureInsight.window(rs, now - 30 * DAY, now).sortedBy { it.time }.forEach { r ->
            newPageIfNeeded()
            line(Instant.ofEpochMilli(r.time).atZone(Z).format(df) + "   ${r.sys}/${r.dia}" + (r.pulse?.let { "   пульс $it" } ?: "") +
                (if (r.arm == 1) "   правая" else "   левая") + (if (r.irregular) "   аритмия" else "") + (if (r.tags.isNotEmpty()) "   " + r.tags.joinToString(", ") else ""), dy = 12f)
        }
        newPageIfNeeded(3)
        y += 8f
        line("Составлено в приложении DASEIN по данным домашнего дневника. Не является медицинским заключением.", Paint(txt).apply { textSize = 8f })
        line("Источники алгоритмов: ${PressureInsight.SOURCES}.", Paint(txt).apply { textSize = 8f })
        doc.finishPage(page)
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "Давление_${p.name}_${LocalDate.now()}.pdf")
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "PDF для врача"))
    }.onFailure { android.widget.Toast.makeText(ctx, "Не удалось сформировать PDF: ${it.message}", android.widget.Toast.LENGTH_LONG).show() }
}
