package com.dasein.poryadok.ui.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.Account
import com.dasein.poryadok.data.Category
import com.dasein.poryadok.data.Settings
import com.dasein.poryadok.data.Txn
import com.dasein.poryadok.data.TxnType
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.ui.common.Bar
import com.dasein.poryadok.ui.common.BarChart
import com.dasein.poryadok.ui.common.Button
import com.dasein.poryadok.ui.common.Donut
import com.dasein.poryadok.ui.common.FitText
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.LineChart
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Segments
import com.dasein.poryadok.ui.common.Series
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.btnLabel
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.theme.LocalExtra
import com.dasein.poryadok.ui.theme.Palette
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

const val FIN_INFO_ROUTE = "finInfo"

/** Подпись операции без служебного: «Сбер: Пятёрочка 1234 · Моск» → «пятёрочка». */
private fun merchantOf(note: String): String =
    note.removePrefix("Сбер:").substringBefore(" · ").substringBefore(':').replace(Regex("[0-9*•#№]+"), " ")
        .replace(Regex("\\s+"), " ").trim().lowercase().take(32)

/**
 * Инфографика финансов: капитал, поток денег, структура трат и доходов, места, подписки, календарь, проценты по вкладам.
 * Всё считается на телефоне и обновляется сразу, как только появляется новая операция (выписка, SMS, пуш, вручную).
 */
@Composable
fun FinanceInfographicScreen(nav: NavHostController, settings: Settings) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val cur = settings.currency
    val txns by observe(emptyList()) { Graph.dao.txns() }
    val cats by observe(emptyList()) { Graph.dao.categories() }
    val accounts by observe(emptyList()) { Graph.dao.accounts() }
    var months by remember { mutableStateOf(3) }
    val today = Dates.today()
    val since = if (months == 0) Long.MIN_VALUE else LocalDate.ofEpochDay(today).minusMonths(months.toLong()).toEpochDay()
    val period = txns.filter { it.day in since..today }
    val exp = period.filter { it.type == TxnType.EXPENSE }
    val inc = period.filter { it.type == TxnType.INCOME }
    val expSum = exp.sumOf { it.amount }
    val incSum = inc.sumOf { it.amount }
    val days = ((today - (period.minOfOrNull { it.day } ?: today)) + 1).coerceAtLeast(1)
    val catName = cats.associateBy { it.id }
    fun catOf(t: Txn): Category? = catName[t.categoryId]

    Screen("Инфографика финансов", onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Segments(listOf(1 to "Месяц", 3 to "3 мес", 12 to "Год", 0 to "Всё"), months, { months = it })
            Gap(10.dp)

            // ---------- Капитал ----------
            val active = accounts.filter { !it.archived }
            val total = active.sumOf { balanceOf(it, txns) }
            val savingIds = active.filter { AccountMeta.savings(ctx, it.id) }.map { it.id }.toSet()
            val savings = active.filter { it.id in savingIds }.sumOf { balanceOf(it, txns) }
            Tile(color = MaterialTheme.colorScheme.primary.copy(alpha = .14f)) {
                Text(btnLabel("Капитал сейчас"), fontSize = 12.sp, color = extra.dim)
                FitText(rub(total, cur), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                if (savingIds.isNotEmpty()) {
                    val share = if (total > 0) (savings / total).toFloat().coerceIn(0f, 1f) else 0f
                    Bar(share, extra.ok, Modifier.padding(top = 6.dp), height = 8.dp)
                    Text("в сбережениях ${rub(savings, cur)} (${(share * 100).roundToInt()}%) · на картах ${rub(total - savings, cur)}", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp))
                }
                val capital = remember(txns, accounts) { capitalByMonth(active, txns, 12) }
                if (capital.size >= 2) {
                    LineChart(
                        listOf(Series(capital.map { it.second.toFloat() }, MaterialTheme.colorScheme.primary, name = "капитал")),
                        Modifier.padding(top = 8.dp), height = 110.dp,
                        labels = capital.map { monthShort(it.first) }, format = { rub(it.toDouble(), cur) },
                    )
                }
            }

            // ---------- Ключевые цифры ----------
            SectionTitle("Ключевые цифры")
            val net = incSum - expSum
            val kpis = listOf(
                Triple("Доходы", rub(incSum, cur), extra.ok),
                Triple("Расходы", rub(expSum, cur), extra.danger),
                Triple(if (net >= 0) "Отложено" else "Перерасход", rub(abs(net), cur), if (net >= 0) extra.ok else extra.danger),
                Triple("Норма сбережений", if (incSum > 0) "${(net / incSum * 100).roundToInt()}%" else "—", MaterialTheme.colorScheme.primary),
                Triple("В день", rub(expSum / days, cur), MaterialTheme.colorScheme.onSurface),
                Triple("Средний чек", if (exp.isNotEmpty()) rub(expSum / exp.size, cur) else "—", MaterialTheme.colorScheme.onSurface),
            )
            kpis.chunked(2).forEach { row ->
                Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (label, value, color) ->
                        Tile(Modifier.weight(1f), padding = 12.dp) {
                            Text(label, fontSize = 11.sp, color = extra.dim)
                            FitText(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color)
                        }
                    }
                }
            }
            Text("Операций: ${period.size} · покупок ${exp.size} · поступлений ${inc.size} · за $days дн.", fontSize = 12.sp, color = extra.dim)

            // ---------- Поток денег по месяцам ----------
            val flow = remember(txns) { flowByMonth(txns, 12) }
            if (flow.any { it.second > 0 || it.third > 0 }) {
                SectionTitle("Доходы и расходы по месяцам")
                Tile {
                    LineChart(
                        listOf(
                            Series(flow.map { it.second.toFloat() }, extra.ok, name = "доходы"),
                            Series(flow.map { it.third.toFloat() }, extra.danger, name = "расходы"),
                        ),
                        height = 150.dp, labels = flow.map { monthShort(it.first) }, format = { rub(it.toDouble(), cur) },
                    )
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Legend(extra.ok, "доходы"); Legend(extra.danger, "расходы")
                    }
                }
            }

            // ---------- Структура расходов ----------
            if (exp.isNotEmpty()) {
                SectionTitle("Куда уходят деньги")
                Shares(exp.groupBy { catOf(it)?.name ?: "Без категории" }.mapValues { e -> e.value.sumOf { it.amount } }, cats, expSum, cur)
            }
            if (inc.isNotEmpty()) {
                SectionTitle("Откуда приходят")
                Shares(inc.groupBy { catOf(it)?.name ?: "Без категории" }.mapValues { e -> e.value.sumOf { it.amount } }, cats, incSum, cur)
            }

            // ---------- Места и получатели ----------
            val places = exp.groupBy { merchantOf(it.note) }.filterKeys { it.length >= 3 }
                .map { (k, l) -> Triple(k, l.size, l.sumOf { it.amount }) }.sortedByDescending { it.third }.take(10)
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
                                    Text(rub(sum, cur), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                }
                                Bar((sum / max).toFloat(), Palette.item(i), Modifier.padding(top = 3.dp), height = 5.dp)
                                Text("$n раз · в среднем ${rub(sum / n, cur)}", fontSize = 11.sp, color = extra.dim)
                            }
                        }
                    }
                }
            }

            // ---------- Подписки и регулярные платежи ----------
            val subs = remember(txns) { recurringSpends(txns) }
            if (subs.isNotEmpty()) {
                SectionTitle("Регулярные платежи и подписки")
                Tile {
                    subs.take(8).forEach { (name, avg, n) ->
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(name.replaceFirstChar { it.uppercase() }, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("≈ ${rub(avg, cur)}/мес", fontWeight = FontWeight.SemiBold)
                        }
                        Text("$n мес. подряд", fontSize = 11.sp, color = extra.dim)
                    }
                    Text("Всего ≈ ${rub(subs.sumOf { it.second }, cur)} в месяц, ${rub(subs.sumOf { it.second } * 12, cur)} в год", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
                }
            }

            // ---------- Календарь трат ----------
            SectionTitle("Календарь трат: последние 5 недель")
            Tile {
                val start = today - 34 - (LocalDate.ofEpochDay(today - 34).dayOfWeek.value - 1)
                val byDay = txns.filter { it.type == TxnType.EXPENSE && it.day >= start }.groupBy { it.day }.mapValues { e -> e.value.sumOf { it.amount } }
                val max = byDay.values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach { Text(it, Modifier.weight(1f), fontSize = 10.sp, color = extra.dim, textAlign = TextAlign.Center) }
                }
                (0 until 6).forEach { w ->
                    val weekStart = start + w * 7
                    if (weekStart > today) return@forEach
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (0 until 7).forEach { d ->
                            val day = weekStart + d
                            val v = byDay[day] ?: 0.0
                            val k = (v / max).toFloat()
                            Box(
                                Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(6.dp))
                                    .background(if (day > today) Color.Transparent else if (v <= 0) extra.line.copy(alpha = .5f) else extra.danger.copy(alpha = .18f + .72f * k)),
                                contentAlignment = Alignment.Center,
                            ) { if (day <= today) Text("${LocalDate.ofEpochDay(day).dayOfMonth}", fontSize = 9.sp, color = if (k > .55f) Color.White else extra.dim) }
                        }
                    }
                }
                val top = byDay.maxByOrNull { it.value }
                if (top != null) Text("Самый дорогой день — ${Dates.label(top.key)}: ${rub(top.value, cur)}", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 8.dp))
            }

            // ---------- Дни недели ----------
            if (exp.isNotEmpty()) {
                SectionTitle("Траты по дням недели")
                Tile {
                    val wd = (1..7).map { d -> exp.filter { LocalDate.ofEpochDay(it.day).dayOfWeek.value == d }.sumOf { it.amount }.toFloat() }
                    BarChart(wd, listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"), MaterialTheme.colorScheme.primary, height = 110.dp, highlight = wd.indexOf(wd.max()), format = { rub(it.toDouble(), cur) })
                }
            }

            // ---------- Проценты по вкладам ----------
            val interest = period.filter { t -> t.type == TxnType.INCOME && (catOf(t)?.name == "Проценты" || "капитализац" in t.note.lowercase() || "процент" in t.note.lowercase()) }
            if (interest.isNotEmpty()) {
                SectionTitle("Проценты по вкладам")
                Tile {
                    Text("Заработано ${rub(interest.sumOf { it.amount }, cur)}", fontWeight = FontWeight.SemiBold, color = extra.ok)
                    val byMonth = interest.groupBy { YearMonth.from(LocalDate.ofEpochDay(it.day)) }.toSortedMap()
                    if (byMonth.size >= 2) BarChart(byMonth.values.map { l -> l.sumOf { it.amount }.toFloat() }, byMonth.keys.map(::monthShort), extra.ok, height = 100.dp, format = { rub(it.toDouble(), cur) })
                    val rateInfo = active.filter { it.id in savingIds }.mapNotNull { a -> AccountMeta.rate(ctx, a.id).takeIf { it > 0 }?.let { r -> AccountMeta.monthly(balanceOf(a, txns), r) } }.sum()
                    if (rateInfo > 0) Text("При нынешнем остатке ≈ +${rub(rateInfo, cur)} в месяц", fontSize = 12.sp, color = extra.dim)
                }
            }

            // ---------- Переводы между своими ----------
            val moves = period.filter { it.type == TxnType.TRANSFER }
            if (moves.isNotEmpty()) {
                SectionTitle("Переводы между своими счетами")
                Tile {
                    val toSav = moves.filter { it.toAccountId in savingIds }.sumOf { it.amount }
                    val fromSav = moves.filter { it.accountId in savingIds }.sumOf { it.amount }
                    if (savingIds.isNotEmpty()) {
                        Text("В сбережения: ${rub(toSav, cur)} · из сбережений: ${rub(fromSav, cur)}", fontSize = 14.sp)
                        Text(if (toSav >= fromSav) "Копилка растёт на ${rub(toSav - fromSav, cur)}" else "Из копилки взято на ${rub(fromSav - toSav, cur)} больше, чем положено", fontSize = 13.sp, color = if (toSav >= fromSav) extra.ok else extra.warn)
                    } else Text("Переводов: ${moves.size} на ${rub(moves.sumOf { it.amount }, cur)} — они не считаются ни доходом, ни расходом.", fontSize = 13.sp)
                }
            }

            // ---------- Крупнейшие ----------
            if (exp.isNotEmpty()) {
                SectionTitle("Крупнейшие траты")
                Tile {
                    exp.sortedByDescending { it.amount }.take(5).forEach { t ->
                        Row(Modifier.padding(vertical = 3.dp)) {
                            Text("${Dates.label(t.day)} · ${t.note.ifBlank { catOf(t)?.name ?: "—" }}", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                            Text(rub(t.amount, cur), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }

            Gap(14.dp)
            Button(onClick = { nav.navigate(FIN_AI_ROUTE) }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { com.dasein.poryadok.ui.common.AiLabel("ИИ-разбор: итоги, дубли, советы") }
            Gap(40.dp)
        }
    }
}

@Composable
private fun Legend(c: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(c))
        Text("  $text", fontSize = 11.sp, color = LocalExtra.current.dim)
    }
}

/** Пончик и полоски долей по категориям. */
@Composable
private fun Shares(map: Map<String, Double>, cats: List<Category>, total: Double, cur: String) {
    val extra = LocalExtra.current
    val list = map.entries.sortedByDescending { it.value }
    val top = list.take(7)
    val rest = list.drop(7).sumOf { it.value }
    val items = top.map { it.key to it.value } + if (rest > 0) listOf("Остальное" to rest) else emptyList()
    fun color(i: Int, name: String) = cats.firstOrNull { it.name == name }?.let { Palette.item(it.color + i) } ?: Palette.item(i)
    Tile {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Donut(items.mapIndexed { i, (n, v) -> v.toFloat() to color(i, n) }, size = 120.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(btnLabel("всего"), fontSize = 10.sp, color = extra.dim)
                    Text(rub(total, cur), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                items.forEachIndexed { i, (n, v) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(color(i, n)))
                        Text(" $n", Modifier.weight(1f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${(v / total * 100).roundToInt()}%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        items.take(5).forEachIndexed { i, (n, v) ->
            Row(Modifier.padding(top = 6.dp)) { Text(n, Modifier.weight(1f), fontSize = 12.sp); Text(rub(v, cur), fontSize = 12.sp, color = extra.dim) }
            Bar((v / items.first().second).toFloat(), color(i, n), Modifier.padding(top = 2.dp), height = 5.dp)
        }
    }
}

private val MONTHS = listOf("янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")
private fun monthShort(ym: YearMonth) = MONTHS[ym.monthValue - 1]

/** Доходы и расходы по последним [n] месяцам. */
private fun flowByMonth(txns: List<Txn>, n: Int): List<Triple<YearMonth, Double, Double>> {
    val now = YearMonth.now()
    return (n - 1 downTo 0).map { k ->
        val ym = now.minusMonths(k.toLong())
        val r = ym.atDay(1).toEpochDay()..ym.atEndOfMonth().toEpochDay()
        val l = txns.filter { it.day in r }
        Triple(ym, l.filter { it.type == TxnType.INCOME }.sumOf { it.amount }, l.filter { it.type == TxnType.EXPENSE }.sumOf { it.amount })
    }
}

/** Капитал на конец каждого из последних [n] месяцев (переводы между своими счетами его не меняют). */
private fun capitalByMonth(accounts: List<Account>, txns: List<Txn>, n: Int): List<Pair<YearMonth, Double>> {
    if (txns.isEmpty()) return emptyList()
    val ids = accounts.map { it.id }.toSet()
    val start = accounts.sumOf { it.initial }
    val first = YearMonth.from(LocalDate.ofEpochDay(txns.minOf { it.day }))
    val now = YearMonth.now()
    return (n - 1 downTo 0).map { now.minusMonths(it.toLong()) }.filter { !it.isBefore(first) }.map { ym ->
        val end = ym.atEndOfMonth().toEpochDay()
        var b = start
        txns.filter { it.day <= end }.forEach { t ->
            when (t.type) {
                TxnType.INCOME -> if (t.accountId in ids) b += t.amount
                TxnType.EXPENSE -> if (t.accountId in ids) b -= t.amount
                TxnType.TRANSFER -> { if (t.accountId in ids) b -= t.amount; if (t.toAccountId in ids) b += t.amount }
            }
        }
        ym to b
    }
}

/** Регулярные траты: одно место 3+ месяца подряд с похожей суммой (разброс до 20%). (название, в месяц, месяцев) */
private fun recurringSpends(txns: List<Txn>): List<Triple<String, Double, Int>> {
    val since = LocalDate.now().minusMonths(6).toEpochDay()
    return txns.filter { it.type == TxnType.EXPENSE && it.day >= since }.groupBy { merchantOf(it.note) }.filterKeys { it.length >= 3 }
        .mapNotNull { (name, l) ->
            val byMonth = l.groupBy { YearMonth.from(LocalDate.ofEpochDay(it.day)) }.mapValues { e -> e.value.sumOf { it.amount } }
            if (byMonth.size < 3) return@mapNotNull null
            val v = byMonth.values
            val avg = v.average()
            val sd = sqrt(v.sumOf { (it - avg) * (it - avg) } / v.size)
            if (avg <= 0 || sd / avg > 0.2) null else Triple(name, avg, byMonth.size)
        }.sortedByDescending { it.second }
}
