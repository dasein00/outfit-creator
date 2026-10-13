@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.training

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dasein.poryadok.data.ExKind
import com.dasein.poryadok.data.TrainingRepo
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.ui.common.LineChart
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Series
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.theme.LocalExtra

private fun n1(v: Double) = if (v == Math.floor(v)) v.toInt().toString() else ((v * 10).toInt() / 10.0).toString().replace('.', ',')
private fun big(v: Double) = String.format(java.util.Locale.US, "%,d", v.toLong()).replace(',', ' ')

/** Дни, когда был поставлен рекорд (по расчётному максимуму, лучшему подходу или времени). */
internal fun recordDays(kind: Int, h: List<TrainingRepo.ExerciseDay>): Set<Long> {
    var best = 0.0
    val out = HashSet<Long>()
    h.sortedBy { it.day }.forEach { d ->
        val v = when (kind) { ExKind.TIME -> d.bestSeconds.toDouble(); ExKind.REPS -> d.bestReps.toDouble(); else -> d.best1rm }
        if (v > best + 1e-6) { if (best > 0) out += d.day; best = v }
    }
    return out
}

/**
 * Рекорды и графики упражнения (как в Strong): лучшие значения с датами, рост с первой тренировки
 * и график по выбранной метрике — расчётный максимум, максимальный вес, тоннаж, повторы.
 */
@Composable
internal fun ExerciseRecords(kind: Int, h: List<TrainingRepo.ExerciseDay>) {
    val extra = LocalExtra.current
    val accent = MaterialTheme.colorScheme.primary
    if (h.isEmpty()) return
    val sets = h.flatMap { d -> d.sets.map { d.day to it } }
    data class Rec(val title: String, val value: String, val day: Long?)
    val recs = buildList {
        when (kind) {
            ExKind.TIME -> {
                sets.maxByOrNull { it.second.seconds }?.let { add(Rec("Самый долгий подход", "${it.second.seconds} с", it.first)) }
                h.maxByOrNull { d -> d.sets.sumOf { it.seconds } }?.let { d -> add(Rec("Больше всего за тренировку", "${d.sets.sumOf { it.seconds }} с", d.day)) }
                add(Rec("Всего под нагрузкой", "${sets.sumOf { it.second.seconds } / 60} мин", null))
            }
            ExKind.REPS -> {
                sets.maxByOrNull { it.second.reps }?.let { add(Rec("Лучший подход", "${it.second.reps} раз", it.first)) }
                h.maxByOrNull { d -> d.sets.sumOf { it.reps } }?.let { d -> add(Rec("Больше всего за тренировку", "${d.sets.sumOf { it.reps }} раз", d.day)) }
                add(Rec("Всего повторений", big(sets.sumOf { it.second.reps }.toDouble()), null))
            }
            else -> {
                sets.maxWithOrNull(compareBy({ it.second.weight }, { it.second.reps }))?.let { add(Rec("Максимальный вес", "${n1(it.second.weight)} кг × ${it.second.reps}", it.first)) }
                h.maxByOrNull { it.best1rm }?.let { add(Rec("Расчётный 1ПМ", TrainingRepo.e1rmText(it.best1rm), it.day)) }
                sets.maxByOrNull { it.second.weight * it.second.reps }?.let { add(Rec("Лучший подход по объёму", "${big(it.second.weight * it.second.reps)} кг", it.first)) }
                h.maxByOrNull { it.volume }?.let { add(Rec("Тоннаж за тренировку", "${big(it.volume)} кг", it.day)) }
                add(Rec("Тоннаж за всё время", "${big(h.sumOf { it.volume })} кг", null))
            }
        }
        add(Rec("Тренировок с упражнением", "${h.size}", null))
    }
    SectionTitle("Рекорды")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 2) {
        recs.forEach { r ->
            Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(extra.card).padding(12.dp)) {
                Text(r.value, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = accent)
                Text(r.title, fontSize = 12.sp)
                r.day?.let { Text(Dates.label(it), fontSize = 11.sp, color = extra.dim) }
            }
        }
    }
    // Рост с первой тренировки.
    if (h.size >= 2) {
        val first = h.first(); val last = h.last()
        val (a, b, unit) = when (kind) {
            ExKind.TIME -> Triple(first.bestSeconds.toDouble(), last.bestSeconds.toDouble(), "с")
            ExKind.REPS -> Triple(first.bestReps.toDouble(), last.bestReps.toDouble(), "раз")
            else -> Triple(first.best1rm, last.best1rm, "кг")
        }
        if (a > 0) {
            val d = b - a
            Text(
                (if (d >= 0) "+" else "−") + "${n1(kotlin.math.abs(d))} $unit (${if (d >= 0) "+" else "−"}${kotlin.math.abs(d / a * 100).toInt()}%) с ${Dates.label(first.day)}",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (d >= 0) extra.ok else extra.warn, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
    val metrics = when (kind) {
        ExKind.TIME -> listOf("Лучший подход", "Всего за тренировку")
        ExKind.REPS -> listOf("Лучший подход", "Всего за тренировку")
        else -> listOf("1ПМ", "Макс. вес", "Тоннаж", "Повторы")
    }
    var m by remember(kind) { mutableIntStateOf(0) }
    SectionTitle("График")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
        metrics.forEachIndexed { i, t -> Pill(t, m == i) { m = i } }
    }
    val values = h.map { d ->
        when (kind) {
            ExKind.TIME -> if (m == 0) d.bestSeconds.toFloat() else d.sets.sumOf { it.seconds }.toFloat()
            ExKind.REPS -> if (m == 0) d.bestReps.toFloat() else d.sets.sumOf { it.reps }.toFloat()
            else -> when (m) { 0 -> d.best1rm.toFloat(); 1 -> d.sets.maxOf { it.weight }.toFloat(); 2 -> d.volume.toFloat(); else -> d.sets.sumOf { it.reps }.toFloat() }
        }
    }
    Tile {
        LineChart(
            listOf(Series(values, accent)),
            labels = h.map { Dates.short(it.day) }.let { l -> if (l.size > 6) l.filterIndexed { i, _ -> i % (l.size / 6 + 1) == 0 } else l },
        )
    }
    // Тоннаж по неделям — столбиками в тексте: видно, растёт ли общая нагрузка.
    if (kind == ExKind.WEIGHT && h.size >= 3) {
        val weeks = h.groupBy { Dates.weekStart(it.day) }.toSortedMap().entries.toList().takeLast(8)
        val max = weeks.maxOf { e -> e.value.sumOf { it.volume } }.coerceAtLeast(1.0)
        SectionTitle("Тоннаж по неделям")
        weeks.forEach { (wk, list) ->
            val v = list.sumOf { it.volume }
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(Dates.short(wk), fontSize = 11.sp, color = extra.dim, modifier = Modifier.padding(end = 8.dp).weight(.22f))
                Row(Modifier.weight(.58f)) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.fillMaxWidth((v / max).toFloat().coerceIn(.02f, 1f)).padding(vertical = 3.dp).clip(RoundedCornerShape(4.dp)).background(accent)
                            .padding(vertical = 4.dp),
                    )
                }
                Text("${big(v)} кг", fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp).weight(.2f))
            }
        }
    }
}
