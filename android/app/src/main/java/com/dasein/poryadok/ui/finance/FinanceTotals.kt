package com.dasein.poryadok.ui.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dasein.poryadok.data.Category
import com.dasein.poryadok.data.Txn
import com.dasein.poryadok.data.TxnType
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.Money
import com.dasein.poryadok.ui.common.BarChart
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Text
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.btnLabel
import com.dasein.poryadok.ui.theme.LocalExtra
import com.dasein.poryadok.ui.theme.Palette
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/*
 * «Итоги»: год, месяц, последние 30/90/180/365 дней или свой период — как годовой лист семейного бюджета:
 * доходы, расходы, остаток и норма сбережений, сравнение с прошлым таким же периодом, таблица по месяцам
 * (итого и в среднем в месяц), траты по категориям и доходы по источникам.
 */

private val MONTHS = listOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь")

/** Период итогов по строке-ключу: «y:2026», «m:2026-10», «d:90», «c:<от>:<до>». */
private fun periodOf(key: String): FinPeriod {
    val today = Dates.today()
    return when {
        key.startsWith("y:") -> {
            val y = key.removePrefix("y:").toInt()
            FinPeriod(LocalDate.of(y, 1, 1).toEpochDay(), LocalDate.of(y, 12, 31).toEpochDay(), key)
        }
        key.startsWith("m:") -> Dates.monthRange(YearMonth.parse(key.removePrefix("m:"))).let { FinPeriod(it.first, it.last, key) }
        key.startsWith("d:") -> key.removePrefix("d:").toInt().let { n -> FinPeriod(today - n + 1, today, key) }
        key.startsWith("c:") -> key.split(':').let { FinPeriod(it[1].toLong(), it[2].toLong(), key) }
        else -> periodOf("y:${LocalDate.now().year}")
    }
}

private fun titleOf(p: FinPeriod): String = when {
    p.label.startsWith("y:") -> "${p.label.removePrefix("y:")} год"
    p.label.startsWith("m:") -> Dates.monthTitle(YearMonth.parse(p.label.removePrefix("m:")))
    p.label.startsWith("d:") -> "Последние ${p.days} дней"
    else -> "${Dates.short(p.from)} — ${Dates.short(p.to)}"
}

@Composable
private fun TotalsPeriodPicker(key: String, onKey: (String) -> Unit) {
    val extra = LocalExtra.current
    var open by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(false) }
    val p = periodOf(key)
    Box {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, extra.line, RoundedCornerShape(12.dp))
                .clickable { open = true }.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(btnLabel("Итоги: "), fontSize = 14.sp, color = extra.dim)
            Text(
                when { key.startsWith("y:") -> "год"; key.startsWith("m:") -> "месяц"; key.startsWith("d:") -> "${p.days} дней"; else -> "свой период" },
                Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            )
            Text("▾", color = extra.dim)
        }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem({ Text(btnLabel("Год")) }, { open = false; onKey("y:${LocalDate.now().year}") })
            DropdownMenuItem({ Text(btnLabel("Месяц")) }, { open = false; onKey("m:${YearMonth.now()}") })
            listOf(30, 90, 180, 365).forEach { n -> DropdownMenuItem({ Text("Последние $n дней") }, { open = false; onKey("d:$n") }) }
            DropdownMenuItem({ Text(btnLabel("Свой период…")) }, { open = false; custom = true })
        }
    }
    // Год и месяц листаются стрелками.
    if (key.startsWith("y:") || key.startsWith("m:")) Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        fun shift(d: Long) = onKey(
            if (key.startsWith("y:")) "y:${key.removePrefix("y:").toInt() + d}" else "m:${YearMonth.parse(key.removePrefix("m:")).plusMonths(d)}",
        )
        IconButton(onClick = { shift(-1) }) { Icon(Icons.Default.ChevronLeft, "Раньше") }
        Text(titleOf(p), Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = { shift(1) }) { Icon(Icons.Default.ChevronRight, "Позже") }
    }
    if (custom) com.dasein.poryadok.ui.common.DateRangeDialog(p.from, p.to, onDismiss = { custom = false }) { f, t ->
        custom = false
        onKey("c:$f:$t")
    }
}

/** Подраздел «Итоги» финансов. */
@Composable
fun TotalsTab(all: List<Txn>, cats: List<Category>, cur: String) {
    val extra = LocalExtra.current
    var key by rememberSaveable { mutableStateOf("y:${LocalDate.now().year}") }
    val p = periodOf(key)
    val list = remember(all, p.from, p.to) { all.filter { it.day in p.from..p.to && it.type != TxnType.TRANSFER } }
    val prevP = p.previous()
    val prev = remember(all, p.from, p.to) { all.filter { it.day in prevP.from..prevP.to && it.type != TxnType.TRANSFER } }
    val inc = list.filter { it.type == TxnType.INCOME }
    val exp = list.filter { it.type == TxnType.EXPENSE }
    val income = inc.sumOf { it.amount }
    val spent = exp.sumOf { it.amount }
    val left = income - spent
    val prevIncome = prev.filter { it.type == TxnType.INCOME }.sumOf { it.amount }
    val prevSpent = prev.filter { it.type == TxnType.EXPENSE }.sumOf { it.amount }
    fun byCat(l: List<Txn>) = l.groupBy { it.categoryId }.map { (id, g) -> Triple(cats.firstOrNull { it.id == id }, g.sumOf { it.amount }, g.size) }.sortedByDescending { it.second }
    val expCats = byCat(exp)
    val incCats = byCat(inc)
    val months = run {
        val first = YearMonth.from(Dates.day(p.from)); val last = YearMonth.from(Dates.day(p.to))
        generateSequence(first) { it.plusMonths(1) }.takeWhile { !it.isAfter(last) }.toList()
    }
    val monthCount = (p.days / 30.4).coerceAtLeast(1.0)

    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Gap(4.dp)
        TotalsPeriodPicker(key) { key = it }
        Gap(8.dp)
        // Год (период) в цифрах.
        Tile {
            Text(titleOf(p) + " · ${p.days} дн.", fontSize = 12.sp, color = extra.dim)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Big("+" + Money.format(income, cur), "Получено", extra.ok, Modifier.weight(1f))
                Big("−" + Money.format(spent, cur), "Потрачено", extra.danger, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Big((if (left >= 0) "+" else "−") + Money.format(abs(left), cur), "Свободный остаток", if (left >= 0) extra.ok else extra.danger, Modifier.weight(1f))
                Big(if (income > 0) "${"%.0f".format(left / income * 100)}%" else "—", "Норма сбережений", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Small(Money.format(spent / (if (p.days >= 60) monthCount else p.days.toDouble()), cur), if (p.days >= 60) "трат в месяц" else "трат в день", Modifier.weight(1f))
                Small("${list.size}", "операций", Modifier.weight(1f))
                Small(expCats.firstOrNull()?.first?.name ?: "—", "главная трата", Modifier.weight(1f))
            }
            // Сравнение с прошлым таким же периодом.
            if (prevIncome > 0 || prevSpent > 0) Column(Modifier.padding(top = 10.dp)) {
                Change("Доходы", income, prevIncome, goodUp = true, cur = cur)
                Change("Расходы", spent, prevSpent, goodUp = false, cur = cur)
                Text(btnLabel("в сравнении с прошлым таким же периодом"), fontSize = 11.sp, color = extra.dim)
            }
        }

        // По месяцам — как в годовом листе: итого и в среднем в месяц.
        if (months.size >= 2) {
            SectionTitle("По месяцам")
            Tile(padding = 8.dp) {
                val sums = months.map { ym ->
                    val r = Dates.monthRange(ym)
                    val l = list.filter { it.day in r }
                    Triple(ym, l.filter { it.type == TxnType.INCOME }.sumOf { it.amount }, l.filter { it.type == TxnType.EXPENSE }.sumOf { it.amount })
                }
                BarChart(
                    sums.map { it.third.toFloat() }, sums.map { MONTHS[it.first.monthValue - 1].take(3).lowercase() }, extra.danger, height = 120.dp,
                    highlight = sums.indexOfFirst { it.third == sums.maxOf { s -> s.third } }, format = { Money.format(it.toDouble(), cur) },
                )
                Gap(6.dp)
                TableRow("Месяц", "Доходы", "Расходы", "Остаток", header = true)
                sums.forEach { (ym, i, e) ->
                    TableRow(MONTHS[ym.monthValue - 1] + if (ym.year != YearMonth.now().year) " ${ym.year % 100}" else "", Money.format(i, cur), Money.format(e, cur), Money.format(i - e, cur), negative = i - e < 0)
                }
                Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(extra.line))
                TableRow("Итого", Money.format(income, cur), Money.format(spent, cur), Money.format(left, cur), header = true, negative = left < 0)
                val n = sums.count { it.second > 0 || it.third > 0 }.coerceAtLeast(1)
                TableRow("В среднем", Money.format(income / n, cur), Money.format(spent / n, cur), Money.format(left / n, cur), negative = left < 0)
            }
        }

        if (expCats.isNotEmpty()) {
            SectionTitle("Траты по категориям")
            Tile { CatList(expCats, spent, monthCount, p.days >= 60, cur) }
        }
        if (incCats.isNotEmpty()) {
            SectionTitle("Доходы по источникам")
            Tile { CatList(incCats, income, monthCount, p.days >= 60, cur) }
        }
        if (list.isEmpty()) Text("За этот период операций нет.", color = extra.dim, modifier = Modifier.padding(vertical = 16.dp))
        Gap(96.dp)
    }
}

@Composable
private fun Big(value: String, label: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = color, maxLines = 1)
        Text(label, fontSize = 12.sp, color = LocalExtra.current.dim, maxLines = 1)
    }
}

@Composable
private fun Small(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, fontSize = 11.sp, color = LocalExtra.current.dim, maxLines = 1)
    }
}

@Composable
private fun Change(label: String, now: Double, before: Double, goodUp: Boolean, cur: String) {
    val extra = LocalExtra.current
    if (before <= 0) return
    val ch = (now - before) / before * 100
    val good = if (goodUp) ch >= 0 else ch <= 0
    Text(
        "$label: ${if (ch >= 0) "▲" else "▼"} ${"%.0f".format(abs(ch))}% (было ${Money.format(before, cur)})",
        fontSize = 13.sp, color = if (good) extra.ok else extra.warn,
    )
}

@Composable
private fun TableRow(a: String, b: String, c: String, d: String, header: Boolean = false, negative: Boolean = false) {
    val extra = LocalExtra.current
    val w = if (header) FontWeight.SemiBold else FontWeight.Normal
    val size = if (header) 12.sp else 13.sp
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(a, Modifier.weight(1.1f), fontSize = size, fontWeight = w, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (header) extra.dim else MaterialTheme.colorScheme.onSurface)
        Text(b, Modifier.weight(1f), fontSize = size, fontWeight = w, textAlign = TextAlign.End, maxLines = 1, color = if (header && a == "Месяц") extra.dim else extra.ok)
        Text(c, Modifier.weight(1f), fontSize = size, fontWeight = w, textAlign = TextAlign.End, maxLines = 1, color = if (header && a == "Месяц") extra.dim else extra.danger)
        Text(d, Modifier.weight(1f), fontSize = size, fontWeight = w, textAlign = TextAlign.End, maxLines = 1, color = if (header && a == "Месяц") extra.dim else if (negative) extra.danger else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun CatList(rows: List<Triple<Category?, Double, Int>>, total: Double, monthCount: Double, perMonth: Boolean, cur: String) {
    val extra = LocalExtra.current
    rows.forEachIndexed { i, (c, v, n) ->
        Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph(c?.emoji ?: "ui:wallet", 20.dp)
                Text(" ${c?.name ?: "Без категории"}", Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Money.format(v, cur), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(extra.line)) {
                Box(Modifier.fillMaxWidth((v / rows.first().second).toFloat().coerceIn(.02f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Palette.item(c?.color ?: i)))
            }
            Text(
                "${"%.1f".format(if (total > 0) v / total * 100 else 0.0).replace('.', ',')}% · $n опер." +
                    if (perMonth) " · ${Money.format(v / monthCount, cur)} в месяц" else "",
                fontSize = 11.sp, color = extra.dim,
            )
        }
    }
}
