package com.dasein.poryadok.ui.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.data.Account
import com.dasein.poryadok.data.Category
import com.dasein.poryadok.data.Txn
import com.dasein.poryadok.data.TxnType
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.Money
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.BarChart
import com.dasein.poryadok.ui.common.DatePickDialog
import com.dasein.poryadok.ui.common.Donut
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Text
import com.dasein.poryadok.ui.common.TextButton
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.btnLabel
import com.dasein.poryadok.ui.theme.LocalExtra
import com.dasein.poryadok.ui.theme.Palette
import java.time.YearMonth
import kotlin.math.abs

/*
 * «Расходы» и «Доходы» с выбором периода (1/7/30/90/180/360 дней, месяц, свой период) и «Календарь трат».
 */

/** Период анализа: дни включительно и подпись. */
data class FinPeriod(val from: Long, val to: Long, val label: String) {
    val days get() = (to - from + 1).coerceAtLeast(1)
    /** Такой же по длине период прямо перед этим — для сравнения. */
    fun previous() = FinPeriod(from - days, from - 1, "")
}

private val QUICK = listOf(1, 7, 30, 90, 180, 360)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeriodPicker(period: FinPeriod, onPick: (FinPeriod) -> Unit) {
    val today = Dates.today()
    var pickFrom by remember { mutableStateOf(false) }
    var pickTo by remember { mutableStateOf<Long?>(null) }
    var months by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        QUICK.forEach { n ->
            val p = FinPeriod(today - n + 1, today, if (n == 1) "сегодня" else "$n дн.")
            Pill(if (n == 1) "1 день" else "$n дней", period.from == p.from && period.to == p.to && period.label == p.label) { onPick(p) }
        }
        Pill("Месяц", months || period.label.startsWith("м:")) { months = !months }
        Pill("Свой период", period.label == "свой") { pickFrom = true }
    }
    if (months) Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val cur = if (period.label.startsWith("м:")) YearMonth.parse(period.label.removePrefix("м:")) else YearMonth.now()
        fun set(ym: YearMonth) { val r = Dates.monthRange(ym); onPick(FinPeriod(r.first, r.last, "м:$ym")) }
        IconButton(onClick = { set(cur.minusMonths(1)) }) { Icon(Icons.Default.ChevronLeft, "Раньше") }
        Text(Dates.monthTitle(cur), Modifier.weight(1f).clickable { set(cur) }, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = { set(cur.plusMonths(1)) }) { Icon(Icons.Default.ChevronRight, "Позже") }
    }
    if (pickFrom) DatePickDialog(period.from, onDismiss = { pickFrom = false }, allowClear = false, onPick = { d ->
        pickFrom = false
        if (d != null) pickTo = d
    })
    pickTo?.let { from ->
        DatePickDialog(maxOf(from, period.to), onDismiss = { pickTo = null }, allowClear = false, onPick = { d ->
            pickTo = null
            if (d != null) onPick(FinPeriod(minOf(from, d), maxOf(from, d), "свой"))
        })
    }
}

private fun periodTitle(p: FinPeriod): String = when {
    p.label.startsWith("м:") -> Dates.monthTitle(YearMonth.parse(p.label.removePrefix("м:")))
    p.days == 1L -> Dates.full(p.from)
    else -> "${Dates.short(p.from)} — ${Dates.short(p.to)} · ${p.days} дн."
}

/** Столбики по дням / неделям / месяцам — в зависимости от длины периода. */
private fun buckets(list: List<Txn>, p: FinPeriod): Pair<List<Float>, List<String>> {
    val byDay = list.groupBy { it.day }.mapValues { (_, l) -> l.sumOf { it.amount } }
    return when {
        p.days <= 31 -> (p.from..p.to).map { (byDay[it] ?: 0.0).toFloat() } to (p.from..p.to).map { Dates.day(it).dayOfMonth.toString() }
        p.days <= 120 -> {
            val starts = generateSequence(p.from) { it + 7 }.takeWhile { it <= p.to }.toList()
            starts.map { s -> (s..minOf(s + 6, p.to)).sumOf { byDay[it] ?: 0.0 }.toFloat() } to starts.map { Dates.short(it) }
        }
        else -> {
            val first = YearMonth.from(Dates.day(p.from)); val last = YearMonth.from(Dates.day(p.to))
            val ms = generateSequence(first) { it.plusMonths(1) }.takeWhile { !it.isAfter(last) }.toList()
            ms.map { ym -> val r = Dates.monthRange(ym); list.filter { it.day in r }.sumOf { it.amount }.toFloat() } to
                ms.map { MONTHS_SHORT[it.monthValue - 1] }
        }
    }
}

private val MONTHS_SHORT = listOf("янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")

/** «Расходы» или «Доходы»: итог, сравнение с прошлым периодом, график с осью, категории, крупные операции и список. */
@Composable
fun FlowTab(nav: NavHostController, all: List<Txn>, cats: List<Category>, accounts: List<Account>, income: Boolean, cur: String) {
    val extra = LocalExtra.current
    val today = Dates.today()
    val type = if (income) TxnType.INCOME else TxnType.EXPENSE
    var from by rememberSaveable(income) { mutableStateOf(today - 29) }
    var to by rememberSaveable(income) { mutableStateOf(today) }
    var label by rememberSaveable(income) { mutableStateOf("30 дн.") }
    val period = FinPeriod(from, to, label)
    var catFilter by rememberSaveable(income) { mutableStateOf<Long?>(null) }
    var showAll by remember { mutableStateOf(false) }
    val accent = if (income) extra.ok else extra.danger

    val list = remember(all, from, to, type) { all.filter { it.type == type && it.day in from..to } }
    val prev = remember(all, from, to, type) { period.previous().let { pp -> all.filter { it.type == type && it.day in pp.from..pp.to } } }
    val total = list.sumOf { it.amount }
    val prevTotal = prev.sumOf { it.amount }
    val byCat = list.groupBy { it.categoryId }.map { (id, l) -> Triple(cats.firstOrNull { it.id == id }, l.sumOf { it.amount }, l.size) }.sortedByDescending { it.second }
    val shown = if (catFilter == null) list else list.filter { (it.categoryId ?: -1L) == catFilter }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        PeriodPicker(period) { p -> from = p.from; to = p.to; label = p.label; catFilter = null }
        Gap(8.dp)
        Tile {
            Text(periodTitle(period), fontSize = 12.sp, color = extra.dim)
            Text((if (income) "+" else "−") + Money.format(total, cur), fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = accent)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Mini(Money.format(total / period.days, cur), "в среднем в день", Modifier.weight(1f))
                Mini("${list.size}", "операций", Modifier.weight(1f))
                Mini(if (list.isEmpty()) "—" else Money.format(total / list.size, cur), "средний чек", Modifier.weight(1f))
            }
            if (prevTotal > 0) {
                val ch = (total - prevTotal) / prevTotal * 100
                val good = if (income) ch >= 0 else ch <= 0
                Text(
                    "${if (ch >= 0) "▲" else "▼"} ${"%.0f".format(abs(ch))}% к прошлым ${period.days} дн. (${Money.format(prevTotal, cur)})",
                    fontSize = 12.sp, color = if (good) extra.ok else extra.warn, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        if (list.isNotEmpty() && period.days > 1) {
            SectionTitle(if (period.days <= 31) "По дням" else if (period.days <= 120) "По неделям" else "По месяцам")
            Tile(padding = 8.dp) {
                val (values, labels) = buckets(list, period)
                BarChart(values, labels, accent, height = 130.dp, highlight = values.indexOf(values.maxOrNull() ?: 0f), format = { Money.format(it.toDouble(), cur) })
            }
        }
        if (byCat.isNotEmpty()) {
            SectionTitle("По категориям")
            Tile {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Donut(byCat.mapIndexed { i, (c, v, _) -> v.toFloat() to Palette.item(c?.color ?: i) }, size = 120.dp) {
                        Text("${byCat.size}", fontWeight = FontWeight.SemiBold)
                    }
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        byCat.take(5).forEachIndexed { i, (c, v, _) ->
                            Text("${c?.name ?: "Без категории"} · ${"%.0f".format(v / total * 100)}%", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.item(c?.color ?: i))
                        }
                    }
                }
                Gap(8.dp)
                byCat.forEachIndexed { i, (c, v, n) ->
                    val id = c?.id ?: -1L
                    val sel = catFilter == id
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (sel) MaterialTheme.colorScheme.primary.copy(alpha = .12f) else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { catFilter = if (sel) null else id }.padding(vertical = 6.dp, horizontal = 4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Glyph(c?.emoji ?: "ui:wallet", 20.dp)
                            Text(" ${c?.name ?: "Без категории"}", Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Money.format(v, cur), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                        Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(extra.line)) {
                            Box(Modifier.fillMaxWidth((v / byCat.first().second).toFloat().coerceIn(.02f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Palette.item(c?.color ?: i)))
                        }
                        Text("$n опер. · ${"%.1f".format(v / total * 100).replace('.', ',')}% · ${Money.format(v / period.days, cur)} в день", fontSize = 11.sp, color = extra.dim)
                    }
                }
                Text(btnLabel("Нажмите категорию — ниже останутся только её операции"), fontSize = 11.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (list.size >= 3) {
            SectionTitle("Самые крупные")
            Tile(padding = 6.dp) {
                list.sortedByDescending { it.amount }.take(5).forEach { t -> TxnRow(t, cats, accounts, cur) { nav.navigate(Routes.txn(t.id)) } }
            }
        }
        SectionTitle(if (catFilter != null) "Операции категории" else "Все операции", action = if (catFilter != null) "Сбросить" else null, onAction = { catFilter = null })
        if (shown.isEmpty()) Text(if (income) "Доходов за период нет" else "Расходов за период нет", color = extra.dim, modifier = Modifier.padding(vertical = 12.dp))
        val groups = shown.groupBy { it.day }.toSortedMap(compareByDescending { it }).entries.toList()
        (if (showAll) groups else groups.take(20)).forEach { (day, l) ->
            Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp)) {
                Text("${Dates.label(day)}, ${Dates.weekdayShort(day)}", Modifier.weight(1f), color = extra.dim, fontSize = 13.sp)
                Text(Money.format(l.sumOf { it.amount }, cur), color = extra.dim, fontSize = 13.sp)
            }
            l.forEach { t -> TxnRow(t, cats, accounts, cur) { nav.navigate(Routes.txn(t.id)) } }
        }
        if (!showAll && groups.size > 20) TextButton(onClick = { showAll = true }) { Text("Показать ещё ${groups.size - 20} дн.") }
        Gap(96.dp)
    }
}

@Composable
private fun Mini(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
        Text(label, fontSize = 11.sp, color = LocalExtra.current.dim, maxLines = 1)
    }
}

/** Календарь трат: сумма расходов в каждом дне (чем больше — тем ярче), нажатие раскрывает операции дня. */
@Composable
fun SpendCalendar(nav: NavHostController, all: List<Txn>, cats: List<Category>, accounts: List<Account>, cur: String) {
    val extra = LocalExtra.current
    val today = Dates.today()
    var ymText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val ym = YearMonth.parse(ymText)
    var selected by rememberSaveable { mutableStateOf<Long?>(today) }
    val range = Dates.monthRange(ym)
    val month = all.filter { it.day in range && it.type != TxnType.TRANSFER }
    val exp = month.filter { it.type == TxnType.EXPENSE }.groupBy { it.day }.mapValues { (_, l) -> l.sumOf { it.amount } }
    val inc = month.filter { it.type == TxnType.INCOME }.groupBy { it.day }.mapValues { (_, l) -> l.sumOf { it.amount } }
    val maxDay = exp.values.maxOrNull() ?: 0.0
    val accent = extra.danger
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { ymText = ym.minusMonths(1).toString() }) { Icon(Icons.Default.ChevronLeft, "Раньше") }
            Text(Dates.monthTitle(ym), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { ymText = ym.plusMonths(1).toString() }) { Icon(Icons.Default.ChevronRight, "Позже") }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Mini("−" + Money.format(exp.values.sum(), cur), "расходы", Modifier.weight(1f))
            Mini("+" + Money.format(inc.values.sum(), cur), "доходы", Modifier.weight(1f))
            Mini("${exp.size}", "дней с тратами", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach { Text(it, Modifier.weight(1f), fontSize = 11.sp, color = extra.dim, textAlign = TextAlign.Center) }
        }
        val first = range.first
        val lead = Dates.day(first).dayOfWeek.value - 1
        val cells = List(lead) { null } + (range.first..range.last).toList()
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                (0 until 7).forEach { i ->
                    val d = week.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(.8f)) {
                        if (d != null) {
                            val e = exp[d] ?: 0.0
                            val k = if (maxDay > 0) (e / maxDay).toFloat() else 0f
                            val sel = d == selected
                            Column(
                                Modifier.fillMaxWidth().aspectRatio(.8f).clip(RoundedCornerShape(8.dp))
                                    .background(if (e > 0) accent.copy(alpha = .12f + .55f * k) else extra.card)
                                    .then(if (sel) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier)
                                    .clickable { selected = if (sel) null else d }.padding(2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    Dates.day(d).dayOfMonth.toString(), fontSize = 12.sp,
                                    fontWeight = if (d == today) FontWeight.Bold else FontWeight.Normal,
                                    color = if (d == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                                if (e > 0) Text(compactMoney(e), fontSize = 9.sp, maxLines = 1, softWrap = false)
                                if ((inc[d] ?: 0.0) > 0) Text("+" + compactMoney(inc[d] ?: 0.0), fontSize = 8.sp, color = extra.ok, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }
        }
        selected?.takeIf { it in range }?.let { d ->
            val dayList = month.filter { it.day == d }
            SectionTitle("${Dates.label(d)}, ${Dates.weekdayShort(d)}")
            if (dayList.isEmpty()) Text("В этот день операций нет", color = extra.dim)
            else Tile(padding = 6.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)) {
                    Text("Расходы: " + Money.format(exp[d] ?: 0.0, cur), Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    if ((inc[d] ?: 0.0) > 0) Text("Доходы: +" + Money.format(inc[d] ?: 0.0, cur), fontSize = 13.sp, color = extra.ok)
                }
                dayList.sortedByDescending { it.amount }.forEach { t -> TxnRow(t, cats, accounts, cur) { nav.navigate(Routes.txn(t.id)) } }
            }
        }
        Gap(96.dp)
    }
}

/** Коротко для клетки календаря: 850, 1,2к, 15к. */
private fun compactMoney(v: Double): String = when {
    v >= 1_000_000 -> "%.1fм".format(v / 1_000_000).replace('.', ',')
    v >= 10_000 -> "${(v / 1000).toInt()}к"
    v >= 1_000 -> "%.1fк".format(v / 1000).replace('.', ',')
    else -> v.toInt().toString()
}
