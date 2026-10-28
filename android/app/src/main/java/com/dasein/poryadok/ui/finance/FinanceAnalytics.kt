@file:OptIn(ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.finance

import com.dasein.poryadok.ui.common.FitText
import com.dasein.poryadok.ui.common.btnLabel
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
import com.dasein.poryadok.ui.common.Text
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

/** Вкладка «Калькуляторы»: вклад, кредит, цель, досрочное погашение, инфляция, бюджет 50/30/20. */
@Composable
fun FinanceCalculators(cur: String) {
    val extra = LocalExtra.current
    var which by rememberSaveable { mutableStateOf(0) }
    val names = listOf("Вклад", "Кредит", "Накопить на цель", "Погасить долг", "Инфляция", "Бюджет 50/30/20")
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
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
