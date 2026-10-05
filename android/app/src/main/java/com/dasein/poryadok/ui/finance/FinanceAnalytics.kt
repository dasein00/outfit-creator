@file:OptIn(ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.finance

import com.dasein.poryadok.ui.common.FitText
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.HowTo
import com.dasein.poryadok.ui.common.LineChart
import com.dasein.poryadok.ui.common.NumberField
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Series
import com.dasein.poryadok.ui.common.Stat
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.theme.LocalExtra
import com.dasein.poryadok.ui.theme.Palette
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Сумма без копеек: «12 340 ₽». */
fun rub(v: Double, cur: String) = Money.format(v.roundToLong().toDouble(), cur)

fun opsOf(txns: List<Txn>, cats: List<Category>): List<FinanceStats.Op> {
    val names = cats.associate { it.id to it.name }
    return txns.filter { it.type != TxnType.TRANSFER }.map { FinanceStats.Op(it.day, it.amount, it.type == TxnType.INCOME, names[it.categoryId] ?: "Без категории") }
}

private fun pct(v: Double) = "${(v * 100).roundToInt()} %"

/** Вкладка «Аналитика»: оценка финансов, прогноз, темп, 50/30/20, подушка, тренды категорий, дни недели и советы. */
@Composable
fun FinanceAnalytics(all: List<Txn>, cats: List<Category>, accounts: List<Account>, budgets: List<Budget>, ym: YearMonth, cur: String) {
    val extra = LocalExtra.current
    val today = LocalDate.now()
    val ops = remember(all, cats) { opsOf(all, cats) }
    val m = FinanceStats.month(ops, ym)
    val avg = FinanceStats.averages(ops, ym)
    val passed = FinanceStats.daysPassed(ym, today)
    val days = ym.lengthOfMonth()
    val forecast = FinanceStats.forecast(m.expense, passed, days)
    val pace = FinanceStats.pace(ops, ym, today)
    val trends = FinanceStats.categoryTrends(ops, ym)
    val split = FinanceStats.split503020(ops, ym)
    val balance = accounts.filter { !it.archived }.sumOf { balanceOf(it, all) }
    val runway = FinanceStats.runwayMonths(balance, avg.expense.takeIf { it > 0 } ?: m.expense)
    val totalBudget = budgets.firstOrNull { it.categoryId == 0L }?.monthly?.takeIf { it > 0 }
    val budgetUse = totalBudget?.let { (if (ym == YearMonth.now()) forecast else m.expense) / it }
    val health = FinanceStats.health(m.savingsRate, runway, budgetUse, pace)
    val tips = FinanceStats.tips(m, avg, runway, pace, trends, split, totalBudget != null) { rub(it, cur) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        // ---------- Оценка ----------
        Tile {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val c = when { health.score >= 70 -> extra.ok; health.score >= 40 -> extra.warn; else -> extra.danger }
                Donut(listOf(health.score.toFloat() to c, (100 - health.score).toFloat() to extra.line), size = 110.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${health.score}", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = c)
                        Text("из 100", fontSize = 11.sp, color = extra.dim)
                    }
                }
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text("Финансовое здоровье", fontWeight = FontWeight.SemiBold)
                    Text(
                        when { health.score >= 70 -> "Всё под контролем"; health.score >= 40 -> "Есть что улучшить"; else -> "Нужно внимание" },
                        fontSize = 12.sp, color = extra.dim,
                    )
                    health.parts.forEach { (name, v) ->
                        Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(name, fontSize = 11.sp, modifier = Modifier.width(76.dp))
                            Bar(v / 25f, if (v >= 18) extra.ok else if (v >= 10) extra.warn else extra.danger, Modifier.weight(1f), height = 5.dp)
                        }
                    }
                }
            }
        }

        // ---------- Месяц ----------
        SectionTitle(Dates.monthTitle(ym))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(rub(m.income, cur), "доход", Modifier.weight(1f), extra.ok)
            Stat(rub(m.expense, cur), "расход", Modifier.weight(1f), extra.danger)
        }
        Gap(8.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(if (m.income > 0) pct(m.savingsRate) else "—", "отложено", Modifier.weight(1f), if (m.net >= 0) extra.ok else extra.danger)
            Stat(rub(if (passed > 0) m.expense / passed else 0.0, cur), "в день", Modifier.weight(1f))
        }
        if (ym == YearMonth.now() && passed in 1 until days) Tile(Modifier.padding(top = 8.dp)) {
            Text("Прогноз на конец месяца", fontSize = 12.sp, color = extra.dim)
            FitText(rub(forecast, cur), style = MaterialTheme.typography.titleLarge)
            val diff = if (m.income > 0) avg.income.coerceAtLeast(m.income) - forecast else null
            if (totalBudget != null) {
                Bar((forecast / totalBudget).toFloat(), if (forecast > totalBudget) extra.danger else extra.ok, Modifier.padding(vertical = 6.dp))
                Text(
                    if (forecast > totalBudget) "Превысите бюджет на ${rub(forecast - totalBudget, cur)} — чтобы уложиться, тратьте не больше ${rub(Money.dailyAllowance(totalBudget, m.expense, days - passed + 1), cur)} в день"
                    else "Уложитесь в бюджет ${rub(totalBudget, cur)}, останется ≈ ${rub(totalBudget - forecast, cur)}",
                    fontSize = 12.sp, color = extra.dim,
                )
            } else if (diff != null) {
                Text(if (diff >= 0) "Если доход как обычно, останется ≈ ${rub(diff, cur)}" else "Расходы могут превысить доход на ${rub(-diff, cur)}", fontSize = 12.sp, color = extra.dim)
            }
            pace?.let {
                Text(
                    (if (it >= 0) "▲ " else "▼ ") + "${abs(it * 100).roundToInt()} % к тому же числу прошлого месяца",
                    fontSize = 12.sp, color = if (it > 0.1) extra.danger else if (it < 0) extra.ok else extra.dim, modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        // ---------- Накопленные траты по дням ----------
        if (m.expense > 0) {
            SectionTitle("Траты нарастающим итогом")
            Tile {
                val prev = ym.minusMonths(1)
                fun cumulative(mm: YearMonth, upTo: Int): List<Float?> {
                    var s = 0.0
                    val byDay = ops.filter { !it.income && it.day in FinanceStats.range(mm) }.groupBy { LocalDate.ofEpochDay(it.day).dayOfMonth }
                    return (1..days).map { d -> if (d > upTo || d > mm.lengthOfMonth()) null else { s += byDay[d].orEmpty().sumOf { it.amount }; s.toFloat() } }
                }
                LineChart(
                    listOf(
                        Series(cumulative(prev, days), extra.dim, dashed = true, name = "прошлый месяц"),
                        Series(cumulative(ym, if (passed > 0) passed else days), extra.danger, name = "этот месяц"),
                    ),
                    labels = listOf("1", "${days / 2}", "$days"),
                    pointLabels = (1..days).map { "$it ${Dates.monthTitle(ym).lowercase()}" },
                    format = { rub(it.toDouble(), cur) },
                )
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Dot(extra.danger, 8.dp); Text(" этот месяц   ", fontSize = 11.sp, color = extra.dim)
                    Dot(extra.dim, 8.dp); Text(" прошлый", fontSize = 11.sp, color = extra.dim)
                }
            }
        }

        // ---------- 50/30/20 ----------
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
                        Text(pct(v), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("  цель ${pct(n.target)}", fontSize = 11.sp, color = extra.dim)
                    }
                }
                Text(
                    if (m.income > 0) "Доли от дохода месяца. Обязательное — продукты, жильё, транспорт, здоровье, связь; остальное — желания."
                    else "Доходов за месяц нет — показаны доли от расходов.",
                    fontSize = 11.sp, color = extra.dim, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        // ---------- Подушка ----------
        SectionTitle("Подушка безопасности")
        Tile {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("На счетах", fontSize = 12.sp, color = extra.dim)
                    FitText(rub(balance, cur), style = MaterialTheme.typography.titleLarge)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("хватит на", fontSize = 12.sp, color = extra.dim)
                    Text(runway?.let { "%.1f мес.".format(it).replace('.', ',') } ?: "—", style = MaterialTheme.typography.titleLarge,
                        color = when { runway == null -> MaterialTheme.colorScheme.onSurface; runway >= 6 -> extra.ok; runway >= 3 -> extra.warn; else -> extra.danger })
                }
            }
            Bar(((runway ?: 0.0) / 6).toFloat(), if ((runway ?: 0.0) >= 3) extra.ok else extra.warn, Modifier.padding(vertical = 6.dp))
            Text("Ориентир — 3–6 месячных расходов (${rub((avg.expense.takeIf { it > 0 } ?: m.expense) * 3, cur)} – ${rub((avg.expense.takeIf { it > 0 } ?: m.expense) * 6, cur)}).", fontSize = 12.sp, color = extra.dim)
        }

        // ---------- Категории против среднего ----------
        if (trends.isNotEmpty()) {
            SectionTitle("Категории против среднего")
            Tile {
                trends.take(8).forEach { t ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(t.category, Modifier.weight(1f), fontSize = 14.sp)
                        Text(rub(t.amount, cur), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        val ch = t.change
                        Text(
                            when { ch == null -> "  новое"; ch >= 0 -> "  ▲${(ch * 100).roundToInt()}%"; else -> "  ▼${(-ch * 100).roundToInt()}%" },
                            fontSize = 12.sp, modifier = Modifier.width(62.dp), textAlign = TextAlign.End,
                            color = when { ch == null -> extra.dim; ch > 0.2 -> extra.danger; ch < -0.1 -> extra.ok; else -> extra.dim },
                        )
                    }
                }
                Text("Сравнение со средним за 3 прошлых месяца.", fontSize = 11.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp))
            }
        }

        // ---------- Год ----------
        val months = (11 downTo 0).map { ym.minusMonths(it.toLong()) }
        val year = months.map { FinanceStats.month(ops, it) }
        if (year.any { it.income > 0 || it.expense > 0 }) {
            SectionTitle("12 месяцев")
            Tile {
                LineChart(
                    listOf(
                        Series(year.map { it.income.toFloat() }, extra.ok, name = "доход"),
                        Series(year.map { it.expense.toFloat() }, extra.danger, name = "расход"),
                    ),
                    labels = listOf(Dates.monthTitle(months.first()).take(3), Dates.monthTitle(months[6]).take(3), Dates.monthTitle(months.last()).take(3)),
                    pointLabels = months.map { Dates.monthTitle(it) },
                    format = { rub(it.toDouble(), cur) },
                )
                val withData = year.filter { it.income > 0 || it.expense > 0 }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Сбережено за год", fontSize = 11.sp, color = extra.dim)
                        Text(rub(year.sumOf { it.net }, cur), fontWeight = FontWeight.SemiBold, color = if (year.sumOf { it.net } >= 0) extra.ok else extra.danger)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Средний расход", fontSize = 11.sp, color = extra.dim)
                        Text(rub(withData.sumOf { it.expense } / withData.size.coerceAtLeast(1), cur), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // ---------- Дни недели ----------
        val to = today.toEpochDay()
        val wd = FinanceStats.byWeekday(ops, to - 89, to)
        if (wd.values.any { it > 0 }) {
            SectionTitle("В какие дни тратите больше")
            Tile {
                val names = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
                val values = DayOfWeek.entries.map { (wd[it] ?: 0.0).toFloat() }
                BarChart(values, names, Palette.item(4), highlight = values.indexOf(values.max()), format = { rub(it.toDouble(), cur) })
                val top = DayOfWeek.entries[values.indexOf(values.max())]
                Text("Больше всего — ${listOf("понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье")[top.ordinal]}: в среднем ${rub(values.max().toDouble(), cur)} за день (последние 90 дней).", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 6.dp))
            }
        }

        // ---------- Крупные траты ----------
        val big = all.filter { it.type == TxnType.EXPENSE && it.day in FinanceStats.range(ym) }.sortedByDescending { it.amount }.take(5)
        if (big.isNotEmpty()) {
            SectionTitle("Крупнейшие траты месяца")
            Tile {
                big.forEach { t ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text(Dates.label(t.day), Modifier.width(70.dp), fontSize = 12.sp, color = extra.dim)
                        Text(listOfNotNull(cats.firstOrNull { it.id == t.categoryId }?.name, t.note.takeIf { it.isNotBlank() }).joinToString(" · "), Modifier.weight(1f), fontSize = 13.sp, maxLines = 1)
                        Text(rub(t.amount, cur), fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    }
                }
                if (m.expense > 0) Text("Это ${pct(big.sumOf { it.amount } / m.expense)} всех расходов месяца.", fontSize = 11.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp))
            }
        }

        // ---------- Советы ----------
        SectionTitle("Советы")
        tips.forEach { t ->
            Tile(Modifier.padding(bottom = 8.dp)) {
                Text(t.title, fontWeight = FontWeight.SemiBold, color = if (t.warn) extra.warn else MaterialTheme.colorScheme.onSurface)
                Text(t.text, fontSize = 13.sp, color = extra.dim, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        HowTo("finance_analytics")
        Gap(96.dp)
    }
}

/** Вкладка «Калькуляторы»: вклад, кредит, цель, досрочное погашение, инфляция, бюджет 50/30/20. */
@Composable
fun FinanceCalculators(cur: String) {
    val extra = LocalExtra.current
    var which by rememberSaveable { mutableStateOf(0) }
    val names = listOf("Вклад", "Кредит", "Накопить на цель", "Погасить долг", "Инфляция", "Бюджет 50/30/20")
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            names.forEachIndexed { i, n -> Pill(n, which == i) { which = i } }
        }
        Gap(12.dp)
        fun d(s: String) = Money.parse(s) ?: 0.0
        when (which) {
            0 -> {
                var start by rememberSaveable { mutableStateOf("100000") }
                var add by rememberSaveable { mutableStateOf("10000") }
                var rate by rememberSaveable { mutableStateOf("16") }
                var years by rememberSaveable { mutableStateOf("3") }
                Calc("Сложный процент: ежемесячная капитализация и пополнение") {
                    NumberField(start, { start = it }, "Начальная сумма", suffix = cur)
                    NumberField(add, { add = it }, "Пополнение в месяц", suffix = cur)
                    NumberField(rate, { rate = it }, "Ставка", suffix = "% годовых")
                    NumberField(years, { years = it }, "Срок", suffix = "лет")
                    val (total, put, interest) = FinanceStats.deposit(d(start), d(add), d(rate), (d(years) * 12).roundToInt())
                    CalcResult("Через ${years.ifBlank { "0" }} г.", rub(total, cur), extra.ok)
                    CalcLine("Вложено", rub(put, cur)); CalcLine("Проценты", rub(interest, cur))
                    if (total > 0) Bar((put / total).toFloat(), Palette.item(1), Modifier.padding(top = 6.dp))
                    Note("Закрашенная часть полосы — ваши взносы, остальное — проценты. Проценты по вкладам сверх необлагаемого лимита облагаются НДФЛ.")
                }
            }
            1 -> {
                var sum by rememberSaveable { mutableStateOf("1000000") }
                var rate by rememberSaveable { mutableStateOf("20") }
                var years by rememberSaveable { mutableStateOf("5") }
                Calc("Кредит или ипотека — аннуитетный платёж") {
                    NumberField(sum, { sum = it }, "Сумма кредита", suffix = cur)
                    NumberField(rate, { rate = it }, "Ставка", suffix = "% годовых")
                    NumberField(years, { years = it }, "Срок", suffix = "лет")
                    val (pay, total, over) = FinanceStats.loan(d(sum), d(rate), (d(years) * 12).roundToInt())
                    CalcResult("Платёж в месяц", rub(pay, cur), MaterialTheme.colorScheme.onSurface)
                    CalcLine("Всего выплатите", rub(total, cur)); CalcLine("Переплата", rub(over, cur))
                    if (d(sum) > 0) Note("Переплата — ${pct(over / d(sum))} от суммы. Платёж по кредитам лучше держать до 30–40 % дохода.")
                }
            }
            2 -> {
                var target by rememberSaveable { mutableStateOf("300000") }
                var have by rememberSaveable { mutableStateOf("0") }
                var months by rememberSaveable { mutableStateOf("12") }
                var rate by rememberSaveable { mutableStateOf("12") }
                Calc("Сколько откладывать каждый месяц") {
                    NumberField(target, { target = it }, "Цель", suffix = cur)
                    NumberField(have, { have = it }, "Уже есть", suffix = cur)
                    NumberField(months, { months = it }, "Срок", suffix = "мес.", decimal = false)
                    NumberField(rate, { rate = it }, "Доходность (0 — копилка)", suffix = "% годовых")
                    val need = FinanceStats.saveFor(d(target), d(have), d(rate), d(months).roundToInt())
                    CalcResult("Откладывать в месяц", rub(need, cur), extra.ok)
                    CalcLine("В день", rub(need / 30.4, cur))
                    Note("Совет: настройте в «Регулярных» автоматический перевод в день зарплаты.")
                }
            }
            3 -> {
                var debt by rememberSaveable { mutableStateOf("200000") }
                var rate by rememberSaveable { mutableStateOf("25") }
                var pay by rememberSaveable { mutableStateOf("10000") }
                var extraPay by rememberSaveable { mutableStateOf("3000") }
                Calc("Досрочное погашение: насколько быстрее и выгоднее") {
                    NumberField(debt, { debt = it }, "Остаток долга", suffix = cur)
                    NumberField(rate, { rate = it }, "Ставка", suffix = "% годовых")
                    NumberField(pay, { pay = it }, "Платёж в месяц", suffix = cur)
                    NumberField(extraPay, { extraPay = it }, "Добавлять сверху", suffix = cur)
                    val base = FinanceStats.payoffMonths(d(debt), d(rate), d(pay))
                    val fast = FinanceStats.payoffMonths(d(debt), d(rate), d(pay) + d(extraPay))
                    if (base == null) CalcResult("Платёж не покрывает проценты", "долг растёт", extra.danger)
                    else {
                        CalcResult("Срок с доплатой", "${fast ?: base} мес.", extra.ok)
                        CalcLine("Без доплаты", "$base мес.")
                        val saved = d(pay) * base - (d(pay) + d(extraPay)) * (fast ?: base)
                        CalcLine("Экономия на процентах ≈", rub(saved.coerceAtLeast(0.0), cur))
                    }
                    Note("Гасите в первую очередь долг с самой высокой ставкой («лавина») или самый маленький («снежный ком») — для мотивации.")
                }
            }
            4 -> {
                var sum by rememberSaveable { mutableStateOf("100000") }
                var infl by rememberSaveable { mutableStateOf("8") }
                var years by rememberSaveable { mutableStateOf("5") }
                Calc("Что останется от денег «под подушкой»") {
                    NumberField(sum, { sum = it }, "Сумма сегодня", suffix = cur)
                    NumberField(infl, { infl = it }, "Инфляция", suffix = "% в год")
                    NumberField(years, { years = it }, "Через", suffix = "лет")
                    val real = FinanceStats.inflation(d(sum), d(infl), d(years))
                    CalcResult("Покупательная способность", rub(real, cur), extra.warn)
                    CalcLine("Потеря", rub(d(sum) - real, cur))
                    Note("Чтобы деньги не обесценивались, доходность вклада или вложений должна быть выше инфляции.")
                }
            }
            else -> {
                var income by rememberSaveable { mutableStateOf("60000") }
                Calc("Разделите доход по правилу 50/30/20") {
                    NumberField(income, { income = it }, "Доход в месяц", suffix = cur)
                    val v = d(income)
                    CalcResult("Обязательное — 50 %", rub(v * 0.5, cur), Palette.item(1))
                    Note("Жильё и ЖКХ, продукты, транспорт, лекарства, связь, кредиты.")
                    CalcResult("Желания — 30 %", rub(v * 0.3, cur), Palette.item(4))
                    Note("Кафе, развлечения, одежда сверх нужного, хобби, подарки.")
                    CalcResult("Сбережения — 20 %", rub(v * 0.2, cur), extra.ok)
                    Note("Подушка безопасности, цели, вклады и инвестиции, досрочное погашение долгов.")
                }
            }
        }
        Gap(96.dp)
    }
}

@Composable
private fun Calc(title: String, content: @Composable () -> Unit) {
    Tile {
        Text(title, fontWeight = FontWeight.SemiBold)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) { content() }
    }
}

@Composable
private fun CalcResult(label: String, value: String, color: Color) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(label, fontSize = 12.sp, color = LocalExtra.current.dim)
        FitText(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun CalcLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), fontSize = 14.sp, color = LocalExtra.current.dim)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Note(text: String) = Text(text, fontSize = 12.sp, color = LocalExtra.current.dim, lineHeight = 16.sp)
