package com.dasein.poryadok.ui.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.Budget
import com.dasein.poryadok.data.Category
import com.dasein.poryadok.data.Txn
import com.dasein.poryadok.data.TxnType
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.Money
import com.dasein.poryadok.ui.common.Bar
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.OutlinedButton
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Text
import com.dasein.poryadok.ui.common.TextButton
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.btnLabel
import com.dasein.poryadok.ui.common.cardViewport
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.theme.LocalExtra
import com.dasein.poryadok.ui.theme.Palette
import java.time.YearMonth

/**
 * «Категории»: все категории расходов и доходов со статистикой — за этот месяц, в среднем за 3 прошлых месяца,
 * число операций, лимит (бюджет) с полосой. Здесь же: новая категория, правка (название, иконка, цвет),
 * лимит на месяц, объединение двух категорий в одну (операции и лимит переносятся) и сортировка.
 */
@Composable
fun CategoriesTab(all: List<Txn>, cats: List<Category>, budgets: List<Budget>, cur: String, onEdit: (Category) -> Unit, onBudget: (Budget) -> Unit) {
    val extra = LocalExtra.current
    var income by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(0) }
    var merging by remember { mutableStateOf<Category?>(null) }
    val ym = YearMonth.now()
    val month = Dates.monthRange(ym)
    val past = Dates.monthRange(ym.minusMonths(3)).first..Dates.monthRange(ym.minusMonths(1)).last
    val type = if (income) TxnType.INCOME else TxnType.EXPENSE
    val stats = remember(all, cats, income) {
        val l = all.filter { it.type == type }
        cats.filter { it.income == income }.map { c ->
            val mine = l.filter { it.categoryId == c.id }
            CatStat(c, mine.filter { it.day in month }.sumOf { it.amount }, mine.filter { it.day in past }.sumOf { it.amount } / 3, mine.size, mine.maxOfOrNull { it.day })
        }
    }
    val sorted = when (sort) {
        1 -> stats.sortedBy { it.c.name.lowercase() }
        2 -> stats.sortedByDescending { it.count }
        else -> stats.sortedWith(compareByDescending<CatStat> { it.month }.thenByDescending { it.avg })
    }
    val monthTotal = stats.sumOf { it.month }
    val uncategorized = all.count { it.type == type && it.categoryId == null && it.day in month }

    LazyColumn(Modifier.cardViewport(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill("Расходы", !income) { income = false }
                Pill("Доходы", income) { income = true }
                Box(Modifier.weight(1f))
                Box {
                    var open by remember { mutableStateOf(false) }
                    val names = listOf("По сумме", "А–Я", "По частоте")
                    TextButton(onClick = { open = true }) { Text(names[sort] + " ▾", fontSize = 13.sp) }
                    DropdownMenu(open, { open = false }) { names.forEachIndexed { i, n -> DropdownMenuItem({ Text(n) }, { open = false; sort = i }) } }
                }
            }
            Tile(Modifier.padding(top = 6.dp), padding = 12.dp) {
                Text(btnLabel(Dates.monthTitle(ym)).uppercase(), fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Text(
                    (if (income) "+" else "−") + Money.format(monthTotal, cur), fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                    color = if (income) extra.ok else extra.danger,
                )
                Text(
                    "${stats.size} категорий · ${stats.count { it.month > 0 }} с операциями в этом месяце" +
                        if (uncategorized > 0) " · без категории: $uncategorized" else "",
                    fontSize = 12.sp, color = extra.dim,
                )
            }
            OutlinedButton(
                onClick = { onEdit(Category(name = "", emoji = "ui:grid", income = income)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            ) { Text(btnLabel(if (income) "+ Категория дохода" else "+ Категория расхода")) }
        }
        items(sorted, key = { it.c.id }) { s ->
            val c = s.c
            val budget = budgets.firstOrNull { it.categoryId == c.id }
            val color = Palette.item(c.color)
            Tile(Modifier.padding(top = 8.dp), onClick = { onEdit(c) }, padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = .18f)), contentAlignment = Alignment.Center) { Glyph(c.emoji, 24.dp) }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(c.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(
                                "${s.count} опер.",
                                s.avg.takeIf { it > 0 }?.let { "в среднем ${Money.format(it, cur)}/мес" },
                                s.last?.let { "последняя ${Dates.label(it)}" },
                            ).joinToString(" · "),
                            fontSize = 11.sp, color = extra.dim, maxLines = 2,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(Money.format(s.month, cur), fontWeight = FontWeight.SemiBold, color = if (s.month > 0) MaterialTheme.colorScheme.onSurface else extra.dim)
                        if (s.avg > 0 && s.month > 0) {
                            val ch = (s.month - s.avg) / s.avg
                            Text("${if (ch >= 0) "▲" else "▼"}${kotlin.math.abs(ch * 100).toInt()}%", fontSize = 11.sp,
                                color = if (income) (if (ch >= 0) extra.ok else extra.warn) else (if (ch > .2) extra.danger else if (ch < -.1) extra.ok else extra.dim))
                        }
                    }
                }
                // Лимит на месяц (для расходов): полоса и остаток.
                if (!income && budget != null && budget.monthly > 0) {
                    val level = Money.budgetLevel(s.month, budget.monthly)
                    val bc = when (level) { Money.BudgetLevel.OK -> extra.ok; Money.BudgetLevel.NEAR -> extra.warn; Money.BudgetLevel.OVER -> extra.danger }
                    Bar((s.month / budget.monthly).toFloat(), bc, Modifier.padding(top = 8.dp), height = 6.dp)
                    Text(
                        if (level == Money.BudgetLevel.OVER) "Лимит ${Money.format(budget.monthly, cur)} превышен на ${Money.format(s.month - budget.monthly, cur)}"
                        else "Лимит ${Money.format(budget.monthly, cur)} · осталось ${Money.format(budget.monthly - s.month, cur)}",
                        fontSize = 11.sp, color = if (level == Money.BudgetLevel.OVER) extra.danger else extra.dim, modifier = Modifier.padding(top = 2.dp),
                    )
                } else if (monthTotal > 0 && s.month > 0) {
                    Bar((s.month / monthTotal).toFloat(), color, Modifier.padding(top = 8.dp), height = 4.dp)
                }
                Row(Modifier.padding(top = 2.dp)) {
                    if (!income) TextButton(onClick = { onBudget(budget ?: Budget(categoryId = c.id, monthly = 0.0)) }) {
                        Text(btnLabel(if (budget != null) "Изменить лимит" else "Задать лимит"), fontSize = 12.sp)
                    }
                    TextButton(onClick = { merging = c }) { Text(btnLabel("Объединить…"), fontSize = 12.sp) }
                }
            }
        }
    }
    merging?.let { from ->
        MergeDialog(from, cats.filter { it.income == from.income && it.id != from.id }, onDismiss = { merging = null }) { to ->
            merging = null
            io {
                all.filter { it.categoryId == from.id }.forEach { Graph.dao.upsertTxn(it.copy(categoryId = to.id)) }
                Graph.dao.deleteBudget(from.id)
                Graph.dao.deleteCategory(from)
            }
        }
    }
}

private data class CatStat(val c: Category, val month: Double, val avg: Double, val count: Int, val last: Long?)

/** Объединить: все операции и лимит категории [from] переходят в выбранную, а [from] удаляется. */
@Composable
private fun MergeDialog(from: Category, targets: List<Category>, onDismiss: () -> Unit, onPick: (Category) -> Unit) {
    var pick by remember { mutableStateOf<Category?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Объединить «${from.name}» с…") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("Все операции «${from.name}» перейдут в выбранную категорию, а «${from.name}» будет удалена.", fontSize = 13.sp, color = LocalExtra.current.dim)
                targets.forEach { t ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (pick == t) MaterialTheme.colorScheme.primary.copy(alpha = .14f) else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { pick = t }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Glyph(t.emoji, 20.dp)
                        Text("  " + t.name)
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = pick != null, onClick = { pick?.let(onPick) }) { Text(btnLabel("Объединить")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) } },
    )
}
