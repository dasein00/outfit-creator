package com.dasein.poryadok.ui.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.navigation.NavHostController
import com.dasein.poryadok.data.Account
import com.dasein.poryadok.data.Budget
import com.dasein.poryadok.data.Category
import com.dasein.poryadok.data.Txn
import com.dasein.poryadok.data.TxnType
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.FinanceStats
import com.dasein.poryadok.logic.Money
import com.dasein.poryadok.ui.common.Bar
import com.dasein.poryadok.ui.common.BarChart
import com.dasein.poryadok.ui.common.Donut
import com.dasein.poryadok.ui.common.Dot
import com.dasein.poryadok.ui.common.DualBarChart
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.LineChart
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Series
import com.dasein.poryadok.ui.common.Text
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.btnLabel
import com.dasein.poryadok.ui.theme.LocalExtra
import com.dasein.poryadok.ui.theme.Palette
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * «Обзор» финансов — одна прокрутка без повторов: сверху итоги выбранного периода (год, месяц, 30/90/180/365 дней,
 * всё время, свой период), затем динамика (месяц — по дням, год — по месяцам, всё время — по годам) с таблицей,
 * темп месяца и здоровье финансов, куда уходят и откуда приходят деньги, 50/30/20, места, подписки, дни недели,
 * проценты, переводы, крупнейшие траты и советы. Капитал и подушка безопасности — во вкладке «Счета».
 */

private val MONTHS = listOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь")

/** Период по ключу: «y:2026», «m:2026-10», «d:90», «all», «c:<от>:<до>». [first] — день первой операции (для «всё время»). */
internal fun finPeriodOf(key: String, first: Long): FinPeriod {
    val today = Dates.today()
    return when {
        key == "all" -> FinPeriod(minOf(first, today), today, key)
        key.startsWith("y:") -> {
            val y = key.removePrefix("y:").toInt()
            FinPeriod(LocalDate.of(y, 1, 1).toEpochDay(), LocalDate.of(y, 12, 31).toEpochDay(), key)
        }
        key.startsWith("m:") -> Dates.monthRange(YearMonth.parse(key.removePrefix("m:"))).let { FinPeriod(it.first, it.last, key) }
        key.startsWith("d:") -> key.removePrefix("d:").toInt().let { n -> FinPeriod(today - n + 1, today, key) }
        key.startsWith("c:") -> key.split(':').let { FinPeriod(it[1].toLong(), it[2].toLong(), key) }
        else -> finPeriodOf("m:${YearMonth.now()}", first)
    }
}

private fun titleOf(p: FinPeriod): String = when {
    p.label == "all" -> "За всё время"
    p.label.startsWith("y:") -> "${p.label.removePrefix("y:")} год"
    p.label.startsWith("m:") -> Dates.monthTitle(YearMonth.parse(p.label.removePrefix("m:")))
    p.label.startsWith("d:") -> "Последние ${p.days} дней"
    else -> "${Dates.short(p.from)} — ${Dates.short(p.to)}"
}

/** Отрезок графика: подпись, дни и подпись строки таблицы. */
private data class Bucket(val label: String, val range: LongRange, val row: String)

/** Шаг графика: месяц — по дням, год — по месяцам, всё время — по годам; скользящие периоды — по дням, неделям или месяцам. */
private fun bucketsOf(p: FinPeriod): List<Bucket> {
    fun days() = (p.from..p.to).map { d -> Bucket(Dates.day(d).dayOfMonth.toString(), d..d, Dates.short(d)) }
    fun weeks() = generateSequence(p.from) { it + 7 }.takeWhile { it <= p.to }.map { s -> Bucket(Dates.short(s), s..minOf(s + 6, p.to), "с ${Dates.short(s)}") }.toList()
    fun months(): List<Bucket> {
        val first = YearMonth.from(Dates.day(p.from)); val last = YearMonth.from(Dates.day(p.to))
        val sameYear = first.year == last.year
        return generateSequence(first) { it.plusMonths(1) }.takeWhile { !it.isAfter(last) }.map { ym ->
            val r = Dates.monthRange(ym)
            Bucket(MONTHS[ym.monthValue - 1].take(3).lowercase(), maxOf(r.first, p.from)..minOf(r.last, p.to), MONTHS[ym.monthValue - 1] + if (sameYear) "" else " ${ym.year}")
        }.toList()
    }
    fun years() = (Dates.day(p.from).year..Dates.day(p.to).year).map { y ->
        Bucket("$y", maxOf(LocalDate.of(y, 1, 1).toEpochDay(), p.from)..minOf(LocalDate.of(y, 12, 31).toEpochDay(), p.to), "$y")
    }
    return when {
        p.label.startsWith("m:") -> days()
        p.label.startsWith("y:") -> months()
        p.label == "all" -> if (p.days > 730) years() else months()
        p.days <= 31 -> days()
        p.days <= 120 -> weeks()
        p.days <= 800 -> months()
        else -> years()
    }
}

@Composable
private fun OverviewPeriodPicker(key: String, first: Long, onKey: (String) -> Unit) {
    val extra = LocalExtra.current
    var open by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(false) }
    val p = finPeriodOf(key, first)
    Box {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, extra.line, RoundedCornerShape(14.dp))
                .clickable { open = true }.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(btnLabel("Период: "), fontSize = 14.sp, color = extra.dim)
            Text(
                when { key == "all" -> "всё время"; key.startsWith("y:") -> "год"; key.startsWith("m:") -> "месяц"; key.startsWith("d:") -> "${p.days} дней"; else -> "свой" },
                Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            )
            Text("▾", color = extra.dim)
        }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem({ Text(btnLabel("Месяц")) }, { open = false; onKey("m:${YearMonth.now()}") })
            DropdownMenuItem({ Text(btnLabel("Год")) }, { open = false; onKey("y:${LocalDate.now().year}") })
            listOf(30, 90, 180, 365).forEach { n -> DropdownMenuItem({ Text("Последние $n дней") }, { open = false; onKey("d:$n") }) }
            DropdownMenuItem({ Text(btnLabel("Всё время")) }, { open = false; onKey("all") })
            DropdownMenuItem({ Text(btnLabel("Свой период…")) }, { open = false; custom = true })
        }
    }
    if (key.startsWith("y:") || key.startsWith("m:")) Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        fun shift(d: Long) = onKey(
            if (key.startsWith("y:")) "y:${key.removePrefix("y:").toInt() + d}" else "m:${YearMonth.parse(key.removePrefix("m:")).plusMonths(d)}",
        )
        IconButton(onClick = { shift(-1) }) { Icon(Icons.Default.ChevronLeft, "Раньше") }
        Text(titleOf(p), Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        IconButton(onClick = { shift(1) }) { Icon(Icons.Default.ChevronRight, "Позже") }
    }
    if (custom) com.dasein.poryadok.ui.common.DateRangeDialog(p.from, p.to, onDismiss = { custom = false }) { f, t ->
        custom = false
        onKey("c:$f:$t")
    }
}

/** Вкладка «Обзор». [onCategory] — открыть категорию для правки. */
@Composable
fun OverviewTab(
    nav: NavHostController, all: List<Txn>, cats: List<Category>, accounts: List<Account>, budgets: List<Budget>, cur: String,
    onCategory: (Category) -> Unit,
) {
    val extra = LocalExtra.current
    val first = remember(all) { all.minOfOrNull { it.day } ?: Dates.today() }
    var key by rememberSaveable { mutableStateOf("m:${YearMonth.now()}") }
    val p = finPeriodOf(key, first)
    val list = remember(all, p) { all.filter { it.day in p.from..p.to } }
    val flow = list.filter { it.type != TxnType.TRANSFER }
    val prevP = p.previous()
    val prev = remember(all, p) { all.filter { it.day in prevP.from..prevP.to && it.type != TxnType.TRANSFER } }
    val inc = flow.filter { it.type == TxnType.INCOME }
    val exp = flow.filter { it.type == TxnType.EXPENSE }
    val income = inc.sumOf { it.amount }
    val spent = exp.sumOf { it.amount }
    val left = income - spent
    val prevIncome = prev.filter { it.type == TxnType.INCOME }.sumOf { it.amount }
    val prevSpent = prev.filter { it.type == TxnType.EXPENSE }.sumOf { it.amount }
    val monthCount = (p.days / 30.4).coerceAtLeast(1.0)
    val longPeriod = p.days >= 60
    val catById = remember(cats) { cats.associateBy { it.id } }
    val isMonth = key.startsWith("m:")
    val ym = if (isMonth) YearMonth.parse(key.removePrefix("m:")) else YearMonth.now()
    val ops = remember(all, cats) { opsOf(all, cats) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Gap(4.dp)
        OverviewPeriodPicker(key, first) { key = it }
        Gap(8.dp)

        // ---------- Итоги ----------
        Tile(color = MaterialTheme.colorScheme.primary.copy(alpha = .10f)) {
            Text((titleOf(p) + " · ${p.days} дн.").uppercase(), fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(btnLabel("Свободный остаток"), fontSize = 12.sp, color = extra.dim)
                    com.dasein.poryadok.ui.common.FitText(
                        (if (left >= 0) "+" else "−") + Money.format(abs(left), cur), style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold, color = if (left >= 0) extra.ok else extra.danger,
                    )
                }
                // Кольцо — норма сбережений: какая часть дохода осталась.
                val rate = if (income > 0) (left / income).toFloat() else 0f
                com.dasein.poryadok.ui.common.ProgressRing(rate.coerceIn(0f, 1f), if (rate >= .2f) extra.ok else if (rate >= 0f) extra.warn else extra.danger, size = 72.dp, stroke = 8.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (income > 0) "${(rate * 100).roundToInt()}%" else "—", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(btnLabel("отложено"), fontSize = 9.sp, color = extra.dim)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KpiCard("Получено", "+" + Money.format(income, cur), extra.ok, change(income, prevIncome), goodUp = true, Modifier.weight(1f))
                KpiCard("Потрачено", "−" + Money.format(spent, cur), extra.danger, change(spent, prevSpent), goodUp = false, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Small(Money.format(spent / (if (longPeriod) monthCount else p.days.toDouble()), cur), if (longPeriod) "трат в месяц" else "трат в день", Modifier.weight(1f))
                Small("${exp.size}", "покупок", Modifier.weight(1f))
                Small(if (exp.isEmpty()) "—" else Money.format(spent / exp.size, cur), "средний чек", Modifier.weight(1f))
            }
            if (prevIncome > 0 || prevSpent > 0) Text(
                btnLabel("▲▼ — к прошлому такому же периоду"), fontSize = 11.sp, color = extra.dim, modifier = Modifier.padding(top = 6.dp),
            )
        }

        // ---------- Динамика: месяц — по дням, год — по месяцам, всё время — по годам ----------
        val buckets = remember(p) { bucketsOf(p) }
        if (flow.isNotEmpty() && buckets.size >= 2) {
            val rows = buckets.map { b ->
                val l = flow.filter { it.day in b.range }
                Triple(b, l.filter { it.type == TxnType.INCOME }.sumOf { it.amount }, l.filter { it.type == TxnType.EXPENSE }.sumOf { it.amount })
            }
            SectionTitle(
                when {
                    buckets.first().range.let { it.first == it.last } -> "По дням"
                    buckets.first().label.all { it.isDigit() } && buckets.first().label.length == 4 -> "По годам"
                    buckets.first().row.startsWith("с ") -> "По неделям"
                    else -> "По месяцам"
                },
            )
            Tile(padding = 10.dp) {
                DualBarChart(
                    rows.map { it.second.toFloat() }, rows.map { it.third.toFloat() }, rows.map { it.first.label }, extra.ok, extra.danger,
                    format = { Money.format(it.toDouble(), cur) },
                    bubble = { i -> "${rows[i].first.row}: +${Money.format(rows[i].second, cur)} / −${Money.format(rows[i].third, cur)}" },
                )
                Row(Modifier.padding(top = 4.dp, start = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LegendDot(extra.ok, "доходы"); LegendDot(extra.danger, "расходы")
                }
                // Таблица — для длинных периодов (по дням строк слишком много).
                if (!buckets.first().range.let { it.first == it.last }) {
                    Gap(8.dp)
                    TableRow("", "Доходы", "Расходы", "Остаток", header = true)
                    rows.forEach { (b, i, e) -> if (i > 0 || e > 0) TableRow(b.row, Money.format(i, cur), Money.format(e, cur), Money.format(i - e, cur), negative = i - e < 0) }
                    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(extra.line))
                    TableRow("Итого", Money.format(income, cur), Money.format(spent, cur), Money.format(left, cur), bold = true, negative = left < 0)
                    val n = rows.count { it.second > 0 || it.third > 0 }.coerceAtLeast(1)
                    TableRow("В среднем", Money.format(income / n, cur), Money.format(spent / n, cur), Money.format(left / n, cur), negative = left < 0)
                } else {
                    val top = rows.maxByOrNull { it.third }
                    if (top != null && top.third > 0) Text(
                        "Самый дорогой день — ${top.first.row}: ${Money.format(top.third, cur)}", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        // ---------- Темп месяца и прогноз (только для месяца) ----------
        if (isMonth) {
            val today = LocalDate.now()
            val m = FinanceStats.month(ops, ym)
            val days = ym.lengthOfMonth()
            val passed = FinanceStats.daysPassed(ym, today)
            val forecast = FinanceStats.forecast(m.expense, passed, days)
            val pace = FinanceStats.pace(ops, ym, today)
            val totalBudget = budgets.firstOrNull { it.categoryId == 0L }?.monthly?.takeIf { it > 0 }
            if (m.expense > 0) {
                SectionTitle("Темп трат")
                Tile {
                    val prevYm = ym.minusMonths(1)
                    fun cumulative(mm: YearMonth, upTo: Int): List<Float?> {
                        var s = 0.0
                        val byDay = ops.filter { !it.income && it.day in FinanceStats.range(mm) }.groupBy { LocalDate.ofEpochDay(it.day).dayOfMonth }
                        return (1..days).map { d -> if (d > upTo || d > mm.lengthOfMonth()) null else { s += byDay[d].orEmpty().sumOf { it.amount }; s.toFloat() } }
                    }
                    if (ym == YearMonth.now() && passed in 1 until days) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Column(Modifier.weight(1f)) {
                                Text(btnLabel("Прогноз на конец месяца"), fontSize = 12.sp, color = extra.dim)
                                Text(Money.format(forecast, cur), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            }
                            pace?.let {
                                Text(
                                    (if (it >= 0) "▲ " else "▼ ") + "${abs(it * 100).roundToInt()}% к прошлому месяцу",
                                    fontSize = 12.sp, color = if (it > 0.1) extra.danger else if (it < 0) extra.ok else extra.dim,
                                )
                            }
                        }
                        if (totalBudget != null) {
                            Bar((forecast / totalBudget).toFloat(), if (forecast > totalBudget) extra.danger else extra.ok, Modifier.padding(vertical = 6.dp))
                            Text(
                                if (forecast > totalBudget) "Превысите бюджет на ${Money.format(forecast - totalBudget, cur)} — тратьте не больше ${Money.format(Money.dailyAllowance(totalBudget, m.expense, days - passed + 1), cur)} в день"
                                else "Уложитесь в бюджет ${Money.format(totalBudget, cur)}, останется ≈ ${Money.format(totalBudget - forecast, cur)}",
                                fontSize = 12.sp, color = extra.dim,
                            )
                        }
                        Gap(8.dp)
                    }
                    LineChart(
                        listOf(
                            Series(cumulative(prevYm, days), extra.dim, dashed = true, name = "прошлый месяц"),
                            Series(cumulative(ym, if (passed > 0) passed else days), extra.danger, name = "этот месяц"),
                        ),
                        height = 130.dp, labels = listOf("1", "${days / 2}", "$days"),
                        pointLabels = (1..days).map { "$it ${Dates.monthTitle(ym).lowercase()}" }, format = { Money.format(it.toDouble(), cur) },
                    )
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        LegendDot(extra.danger, "этот месяц"); LegendDot(extra.dim, "прошлый")
                    }
                }
            }
            // Финансовое здоровье — оценка месяца.
            val balance = accounts.filter { !it.archived }.sumOf { balanceOf(it, all) }
            val avg = FinanceStats.averages(ops, ym)
            val runway = FinanceStats.runwayMonths(balance, avg.expense.takeIf { it > 0 } ?: m.expense)
            val budgetUse = totalBudget?.let { (if (ym == YearMonth.now()) forecast else m.expense) / it }
            val health = FinanceStats.health(m.savingsRate, runway, budgetUse, pace)
            if (m.income > 0 || m.expense > 0) {
                SectionTitle("Финансовое здоровье")
                Tile {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val c = when { health.score >= 70 -> extra.ok; health.score >= 40 -> extra.warn; else -> extra.danger }
                        Donut(listOf(health.score.toFloat() to c, (100 - health.score).toFloat() to extra.line), size = 96.dp) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${health.score}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = c)
                                Text(btnLabel("из 100"), fontSize = 10.sp, color = extra.dim)
                            }
                        }
                        Column(Modifier.padding(start = 14.dp).weight(1f)) {
                            Text(
                                when { health.score >= 70 -> "Всё под контролем"; health.score >= 40 -> "Есть что улучшить"; else -> "Нужно внимание" },
                                fontWeight = FontWeight.SemiBold,
                            )
                            health.parts.forEach { (name, v) ->
                                Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(name, fontSize = 11.sp, modifier = Modifier.width(78.dp))
                                    Bar(v / 25f, if (v >= 18) extra.ok else if (v >= 10) extra.warn else extra.danger, Modifier.weight(1f), height = 5.dp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---------- Куда уходят и откуда приходят ----------
        val prevByCat = remember(prev) { prev.filter { it.type == TxnType.EXPENSE }.groupBy { it.categoryId }.mapValues { e -> e.value.sumOf { it.amount } } }
        fun byCat(l: List<Txn>) = l.groupBy { it.categoryId }.map { (id, g) -> Triple(catById[id], g.sumOf { it.amount }, g.size) }.sortedByDescending { it.second }
        val expCats = byCat(exp)
        if (expCats.isNotEmpty()) {
            SectionTitle("Куда уходят деньги")
            Tile {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Donut(expCats.mapIndexed { i, (c, v, _) -> v.toFloat() to Palette.item(c?.color ?: i) }, size = 128.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(Money.format(spent, cur), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("${expCats.size} катег.", fontSize = 10.sp, color = extra.dim)
                        }
                    }
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        expCats.take(6).forEachIndexed { i, (c, v, _) ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                Dot(Palette.item(c?.color ?: i), 9.dp)
                                Text(" ${c?.name ?: "Без категории"}", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(" ${(v / spent * 100).roundToInt()}%", fontSize = 12.sp, color = extra.dim)
                            }
                        }
                    }
                }
                Gap(10.dp)
                CatList(expCats, spent, monthCount, longPeriod, cur, prevByCat, onCategory)
                Text(btnLabel("▲▼ — к прошлому периоду · нажмите категорию, чтобы изменить"), fontSize = 11.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp))
            }
        }
        val incCats = byCat(inc)
        if (incCats.isNotEmpty()) {
            SectionTitle("Откуда приходят")
            Tile { CatList(incCats, income, monthCount, longPeriod, cur, null, onCategory) }
        }

        // ---------- 50/30/20 (для месяца) ----------
        if (isMonth) {
            val m = FinanceStats.month(ops, ym)
            val split = FinanceStats.split503020(ops, ym)
            if (m.income > 0 || m.expense > 0) {
                SectionTitle("Правило 50 / 30 / 20")
                Tile {
                    val colors = mapOf(FinanceStats.Need.NEEDS to Palette.item(1), FinanceStats.Need.WANTS to Palette.item(4), FinanceStats.Need.SAVE to extra.ok)
                    Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(extra.line)) {
                        FinanceStats.Need.entries.forEach { n ->
                            val v = (split[n] ?: 0.0).toFloat()
                            if (v > 0.001f) Box(Modifier.weight(v).height(14.dp).background(colors.getValue(n)))
                        }
                        val rest = 1f - FinanceStats.Need.entries.sumOf { split[it] ?: 0.0 }.toFloat()
                        if (rest > 0.001f) Box(Modifier.weight(rest))
                    }
                    FinanceStats.Need.entries.forEach { n ->
                        val v = split[n] ?: 0.0
                        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Dot(colors.getValue(n), 9.dp)
                            Text(" ${n.title}", Modifier.weight(1f), fontSize = 13.sp)
                            Text("${(v * 100).roundToInt()}%", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("  цель ${(n.target * 100).roundToInt()}%", fontSize = 11.sp, color = extra.dim)
                        }
                    }
                }
            }
        }

        // ---------- Места ----------
        val places = exp.groupBy { merchantOf(it.note) }.filterKeys { it.length >= 3 }
            .map { (k, l) -> Triple(k, l.size, l.sumOf { it.amount }) }.sortedByDescending { it.third }.take(8)
        if (places.isNotEmpty()) {
            SectionTitle("Где тратите больше всего")
            Tile {
                val max = places.first().third
                places.forEachIndexed { i, (name, n, sum) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Text("${i + 1}", Modifier.width(22.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Row {
                                Text(name.replaceFirstChar { it.uppercase() }, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                                Text(Money.format(sum, cur), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                            Bar((sum / max).toFloat(), Palette.item(i), Modifier.padding(top = 3.dp), height = 5.dp)
                            Text("$n раз · в среднем ${Money.format(sum / n, cur)}", fontSize = 11.sp, color = extra.dim)
                        }
                    }
                }
            }
        }

        // ---------- Подписки (найденные по операциям) ----------
        val subs = remember(all) { recurringSpends(all) }
        if (subs.isNotEmpty()) {
            SectionTitle("Подписки и регулярные траты")
            Tile {
                subs.take(8).forEach { (name, avg, n) ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(name.replaceFirstChar { it.uppercase() }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("$n мес. подряд", fontSize = 11.sp, color = extra.dim)
                        }
                        Text("≈ ${Money.format(avg, cur)}/мес", fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(
                    "Всего ≈ ${Money.format(subs.sumOf { it.second }, cur)} в месяц, ${Money.format(subs.sumOf { it.second } * 12, cur)} в год",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        // ---------- Дни недели ----------
        if (exp.size >= 5) {
            SectionTitle("Траты по дням недели")
            Tile(padding = 10.dp) {
                val names = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
                val weeks = (p.days / 7.0).coerceAtLeast(1.0)
                val wd = (1..7).map { d -> (exp.filter { LocalDate.ofEpochDay(it.day).dayOfWeek.value == d }.sumOf { it.amount } / weeks).toFloat() }
                val topI = wd.indexOf(wd.max())
                BarChart(wd, names, MaterialTheme.colorScheme.primary, height = 110.dp, highlight = topI, format = { Money.format(it.toDouble(), cur) })
                Text(
                    "Больше всего — ${listOf("понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье")[topI]}: в среднем ${Money.format(wd[topI].toDouble(), cur)} за такой день.",
                    fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        // ---------- Проценты и переводы ----------
        val interest = inc.filter { t -> catById[t.categoryId]?.name == "Проценты" || "капитализац" in t.note.lowercase() || "процент" in t.note.lowercase() }
        val moves = list.filter { it.type == TxnType.TRANSFER }
        if (interest.isNotEmpty() || moves.isNotEmpty()) {
            SectionTitle("Сбережения")
            Tile {
                if (interest.isNotEmpty()) Text("Проценты по вкладам: +${Money.format(interest.sumOf { it.amount }, cur)}", fontWeight = FontWeight.SemiBold, color = extra.ok)
                if (moves.isNotEmpty()) {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    val savingIds = accounts.filter { AccountMeta.savings(ctx, it.id) }.map { it.id }.toSet()
                    val toSav = moves.filter { it.toAccountId in savingIds }.sumOf { it.amount }
                    val fromSav = moves.filter { it.accountId in savingIds }.sumOf { it.amount }
                    if (savingIds.isNotEmpty()) Text(
                        if (toSav >= fromSav) "Отложено в копилку: +${Money.format(toSav - fromSav, cur)} (переведено ${Money.format(toSav, cur)}, снято ${Money.format(fromSav, cur)})"
                        else "Из копилки взято на ${Money.format(fromSav - toSav, cur)} больше, чем положено",
                        fontSize = 13.sp, color = if (toSav >= fromSav) extra.ok else extra.warn, modifier = Modifier.padding(top = 4.dp),
                    ) else Text("Переводов между своими счетами: ${moves.size} на ${Money.format(moves.sumOf { it.amount }, cur)} — не доход и не расход.", fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        // ---------- Крупнейшие ----------
        if (exp.size >= 3) {
            SectionTitle("Крупнейшие траты")
            Tile {
                val big = exp.sortedByDescending { it.amount }.take(5)
                big.forEach { t ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Glyph(catById[t.categoryId]?.emoji ?: "ui:wallet", 18.dp)
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(t.note.ifBlank { catById[t.categoryId]?.name ?: "—" }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                            Text(Dates.label(t.day), fontSize = 11.sp, color = extra.dim)
                        }
                        Text(Money.format(t.amount, cur), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
                if (spent > 0) Text("Это ${(big.sumOf { it.amount } / spent * 100).roundToInt()}% всех расходов периода.", fontSize = 11.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp))
            }
        }

        // ---------- Советы (по месяцу) ----------
        run {
            val m = FinanceStats.month(ops, ym)
            val avg = FinanceStats.averages(ops, ym)
            val balance = accounts.filter { !it.archived }.sumOf { balanceOf(it, all) }
            val runway = FinanceStats.runwayMonths(balance, avg.expense.takeIf { it > 0 } ?: m.expense)
            val tips = FinanceStats.tips(
                m, avg, runway, FinanceStats.pace(ops, ym, LocalDate.now()), FinanceStats.categoryTrends(ops, ym), FinanceStats.split503020(ops, ym),
                budgets.any { it.categoryId == 0L }, { rub(it, cur) },
            )
            if (tips.isNotEmpty()) {
                SectionTitle("Советы")
                tips.forEach { t ->
                    Tile(Modifier.padding(bottom = 8.dp)) {
                        Text(t.title, fontWeight = FontWeight.SemiBold, color = if (t.warn) extra.warn else MaterialTheme.colorScheme.onSurface)
                        Text(t.text, fontSize = 13.sp, color = extra.dim, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
        com.dasein.poryadok.ui.common.Button(onClick = { nav.navigate(FIN_AI_ROUTE) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 50.dp)) {
            com.dasein.poryadok.ui.common.AiLabel("ИИ-разбор: итоги, дубли, советы")
        }
        if (list.isEmpty()) Text("За этот период операций нет.", color = extra.dim, modifier = Modifier.padding(vertical = 16.dp))
        Gap(96.dp)
    }
}

/** Изменение к прошлому периоду, доля: null — сравнивать не с чем. */
private fun change(now: Double, before: Double): Double? = if (before > 0) (now - before) / before else null

@Composable
private fun KpiCard(label: String, value: String, color: Color, ch: Double?, goodUp: Boolean, modifier: Modifier) {
    val extra = LocalExtra.current
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.background.copy(alpha = .6f)).padding(10.dp)) {
        Text(label, fontSize = 11.sp, color = extra.dim)
        com.dasein.poryadok.ui.common.FitText(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color)
        ch?.let {
            val good = if (goodUp) it >= 0 else it <= 0
            Text("${if (it >= 0) "▲" else "▼"} ${abs(it * 100).roundToInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = if (good) extra.ok else extra.warn)
        }
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
private fun LegendDot(c: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(c, 8.dp)
        Text(" $text", fontSize = 11.sp, color = LocalExtra.current.dim)
    }
}

@Composable
private fun TableRow(a: String, b: String, c: String, d: String, header: Boolean = false, bold: Boolean = false, negative: Boolean = false) {
    val extra = LocalExtra.current
    val w = if (header || bold) FontWeight.SemiBold else FontWeight.Normal
    val size = if (header) 11.sp else 13.sp
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(a, Modifier.weight(1.1f), fontSize = size, fontWeight = w, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (header) extra.dim else MaterialTheme.colorScheme.onSurface)
        Text(b, Modifier.weight(1f), fontSize = size, fontWeight = w, textAlign = TextAlign.End, maxLines = 1, color = if (header) extra.dim else extra.ok)
        Text(c, Modifier.weight(1f), fontSize = size, fontWeight = w, textAlign = TextAlign.End, maxLines = 1, color = if (header) extra.dim else extra.danger)
        Text(d, Modifier.weight(1f), fontSize = size, fontWeight = w, textAlign = TextAlign.End, maxLines = 1, color = if (header) extra.dim else if (negative) extra.danger else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun CatList(
    rows: List<Triple<Category?, Double, Int>>, total: Double, monthCount: Double, perMonth: Boolean, cur: String,
    prev: Map<Long?, Double>?, onCategory: (Category) -> Unit,
) {
    val extra = LocalExtra.current
    rows.forEachIndexed { i, (c, v, n) ->
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(enabled = c != null) { c?.let(onCategory) }.padding(vertical = 5.dp, horizontal = 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph(c?.emoji ?: "ui:wallet", 20.dp)
                Text(" ${c?.name ?: "Без категории"}", Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                prev?.get(c?.id)?.takeIf { it > 0 }?.let { b ->
                    val ch = (v - b) / b
                    Text(
                        "${if (ch >= 0) "▲" else "▼"}${abs(ch * 100).roundToInt()}%  ", fontSize = 11.sp,
                        color = if (ch > .2) extra.danger else if (ch < -.1) extra.ok else extra.dim,
                    )
                }
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
