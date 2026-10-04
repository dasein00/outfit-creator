package com.dasein.poryadok.ui.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.TxnType
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.FinanceStats
import com.dasein.poryadok.logic.Money
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.Bar
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.theme.LocalExtra
import com.dasein.poryadok.ui.theme.Palette
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Карточка «Деньги» на главном экране — как в банковских приложениях:
 * сколько всего денег, итог месяца (доход/расход/осталось), сколько можно тратить сегодня,
 * темп к прошлому месяцу, структура трат, ближайший платёж и быстрые кнопки.
 */
@Composable
fun HomeMoneyCard(nav: NavHostController, cur: String) {
    val extra = LocalExtra.current
    val dao = Graph.dao
    val txns by observe(emptyList()) { dao.txns() }
    val cats by observe(emptyList()) { dao.categories() }
    val accounts by observe(emptyList()) { dao.accounts() }
    val budgets by observe(emptyList()) { dao.budgets() }
    val recurring by observe(emptyList()) { dao.recurring() }
    val today = LocalDate.now()
    val ym = YearMonth.now()
    val ops = remember(txns, cats) { opsOf(txns, cats) }
    val m = FinanceStats.month(ops, ym)
    val balance = accounts.filter { !it.archived }.sumOf { balanceOf(it, txns) }
    val spentToday = txns.filter { it.type == TxnType.EXPENSE && it.day == today.toEpochDay() }.sumOf { it.amount }
    val pace = FinanceStats.pace(ops, ym, today)
    val budget = budgets.firstOrNull { it.categoryId == 0L }?.monthly?.takeIf { it > 0 }
    val daysLeft = ym.lengthOfMonth() - today.dayOfMonth + 1
    val byCat = ops.filter { !it.income && it.day in FinanceStats.range(ym) }.groupBy { it.category }
        .map { (c, l) -> c to l.sumOf { it.amount } }.sortedByDescending { it.second }
    val next = recurring.filter { it.active && it.nextDay >= today.toEpochDay() }.minByOrNull { it.nextDay }

    Tile(onClick = { nav.navigate(Routes.FINANCE) }) {
        // Всего денег и итог месяца
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("Всего на счетах", fontSize = 12.sp, color = extra.dim)
                Text(rub(balance, cur), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Сегодня", fontSize = 12.sp, color = extra.dim)
                Text(if (spentToday > 0) "−" + rub(spentToday, cur) else "0 $cur", style = MaterialTheme.typography.titleMedium)
            }
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Kpi("Доход", "+" + rub(m.income, cur), extra.ok, Modifier.weight(1f))
            Kpi("Расход", "−" + rub(m.expense, cur), extra.danger, Modifier.weight(1f))
            Kpi(if (m.net >= 0) "Осталось" else "Минус", rub(abs(m.net), cur), if (m.net >= 0) MaterialTheme.colorScheme.onSurface else extra.danger, Modifier.weight(1f))
        }

        // Бюджет и «можно тратить в день»
        if (budget != null) {
            val level = Money.budgetLevel(m.expense, budget)
            Bar(
                (m.expense / budget).toFloat(),
                when (level) { Money.BudgetLevel.OK -> extra.ok; Money.BudgetLevel.NEAR -> extra.warn; Money.BudgetLevel.OVER -> extra.danger },
                Modifier.padding(top = 10.dp),
            )
            Row(Modifier.padding(top = 4.dp)) {
                Text(
                    if (m.expense > budget) "Бюджет превышен на ${rub(m.expense - budget, cur)}"
                    else "Можно тратить ${rub(Money.dailyAllowance(budget, m.expense, daysLeft), cur)} в день",
                    Modifier.weight(1f), fontSize = 12.sp, color = if (m.expense > budget) extra.danger else extra.dim,
                )
                Text("${(m.expense / budget * 100).roundToInt()} % из ${rub(budget, cur)}", fontSize = 12.sp, color = extra.dim)
            }
        } else if (m.income > 0) {
            Text(
                "Отложено ${(m.savingsRate * 100).roundToInt().coerceAtLeast(0)} % дохода · ориентир 20 %",
                fontSize = 12.sp, color = if (m.savingsRate >= 0.2) extra.ok else extra.dim, modifier = Modifier.padding(top = 8.dp),
            )
        }

        // Структура трат одной полосой
        if (byCat.isNotEmpty() && m.expense > 0) {
            Row(Modifier.padding(top = 10.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))) {
                byCat.take(5).forEachIndexed { i, (_, v) -> Box(Modifier.weight(v.toFloat().coerceAtLeast(0.001f)).height(8.dp).background(Palette.item(i))) }
                val rest = byCat.drop(5).sumOf { it.second }
                if (rest > 0) Box(Modifier.weight(rest.toFloat()).height(8.dp).background(extra.line))
            }
            Text(
                byCat.take(3).joinToString("  ·  ") { (c, v) -> "$c ${(v / m.expense * 100).roundToInt()} %" },
                fontSize = 11.sp, color = extra.dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
            )
        }

        // Темп и ближайший платёж
        pace?.let {
            Text(
                (if (it >= 0) "▲ " else "▼ ") + "${abs(it * 100).roundToInt()} % трат к этому же дню прошлого месяца",
                fontSize = 12.sp, color = if (it > 0.1) extra.danger else if (it < 0) extra.ok else extra.dim, modifier = Modifier.padding(top = 6.dp),
            )
        }
        next?.let { r ->
            val inDays = (r.nextDay - today.toEpochDay()).toInt()
            Text(
                "${if (r.income) "Поступление" else "Платёж"} «${r.title}» ${if (r.income) "+" else "−"}${rub(r.amount, cur)} · " +
                    when (inDays) { 0 -> "сегодня"; 1 -> "завтра"; else -> "${Dates.label(r.nextDay)}" },
                fontSize = 12.sp, color = if (inDays <= 2 && !r.income) extra.warn else extra.dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("− Расход", false) { nav.navigate(Routes.txn(0)) }
            Pill("+ Доход", false) { nav.navigate(Routes.txn(0, income = true)) }
            Pill("Аналитика", false) { nav.navigate(Routes.finance(2)) }
        }
    }
}

@Composable
private fun Kpi(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(LocalExtra.current.cardHigh).padding(horizontal = 8.dp, vertical = 6.dp)) {
        Text(label, fontSize = 11.sp, color = LocalExtra.current.dim)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
