@file:OptIn(ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.finance

import androidx.compose.foundation.clickable
import com.dasein.poryadok.ui.common.Ic
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.Account
import com.dasein.poryadok.data.Budget
import com.dasein.poryadok.data.Category
import com.dasein.poryadok.data.Recurring
import com.dasein.poryadok.data.Settings
import com.dasein.poryadok.data.Txn
import com.dasein.poryadok.data.TxnType
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.Money
import com.dasein.poryadok.logic.Repeat
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.IconAction
import com.dasein.poryadok.data.NoteKind
import com.dasein.poryadok.ui.common.Bar
import com.dasein.poryadok.ui.common.BarChart
import com.dasein.poryadok.ui.common.ColorPicker
import com.dasein.poryadok.ui.common.DatePickDialog
import com.dasein.poryadok.ui.common.Donut
import com.dasein.poryadok.ui.common.Dot
import com.dasein.poryadok.ui.common.EmojiPicker
import com.dasein.poryadok.ui.common.Empty
import com.dasein.poryadok.ui.common.FieldButton
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.NumberField
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Stat
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.num
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.common.plain
import com.dasein.poryadok.ui.theme.LocalExtra
import com.dasein.poryadok.ui.theme.Palette
import java.time.YearMonth

fun balanceOf(a: Account, txns: List<Txn>): Double {
    var b = a.initial
    txns.forEach { t ->
        when (t.type) {
            TxnType.INCOME -> if (t.accountId == a.id) b += t.amount
            TxnType.EXPENSE -> if (t.accountId == a.id) b -= t.amount
            TxnType.TRANSFER -> {
                if (t.accountId == a.id) b -= t.amount
                if (t.toAccountId == a.id) b += t.amount
            }
        }
    }
    return b
}

@Composable
fun FinanceScreen(nav: NavHostController, settings: Settings, initialTab: Int) {
    val dao = Graph.dao
    val cur = settings.currency
    var tab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }
    var ymText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val ym = YearMonth.parse(ymText)
    val txns by observe(emptyList()) { dao.txns() }
    val cats by observe(emptyList()) { dao.categories() }
    val accounts by observe(emptyList()) { dao.accounts() }
    val budgets by observe(emptyList()) { dao.budgets() }
    val recurring by observe(emptyList()) { dao.recurring() }
    var editAccount by remember { mutableStateOf<Account?>(null) }
    var editBudget by remember { mutableStateOf<Budget?>(null) }
    var editRecurring by remember { mutableStateOf<Recurring?>(null) }
    var editCategory by remember { mutableStateOf<Category?>(null) }
    val nbNotes by observe(emptyList()) { Graph.extra.financeNotes() }

    Screen(
        title = "Финансы",
        actions = {
            IconAction(Ic.bank, "Сбербанк") { nav.navigate(Routes.SBER) }
            IconAction(Ic.document, "Тетрадь финансов") { nav.navigate(Routes.FIN_NOTEBOOK) }
        },
        fab = {
            FloatingActionButton(
                onClick = {
                    when (tab) {
                        2 -> editBudget = Budget(categoryId = -1, monthly = 0.0)
                        3 -> editRecurring = Recurring(title = "", amount = 0.0, accountId = accounts.firstOrNull()?.id ?: 0, nextDay = Dates.today())
                        4 -> editAccount = Account(name = "")
                        else -> nav.navigate(Routes.txn(0))
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
            ) { Icon(Icons.Default.Add, "Добавить") }
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp, containerColor = MaterialTheme.colorScheme.background) {
                listOf("Операции", "Обзор", "Бюджеты", "Регулярные", "Счета").forEachIndexed { i, t ->
                    Tab(tab == i, onClick = { tab = i }, text = { Text(t) })
                }
            }
            if (tab <= 2) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { ymText = ym.minusMonths(1).toString() }) { Icon(Icons.Default.ChevronLeft, "Раньше") }
                Text(Dates.monthTitle(ym), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { ymText = ym.plusMonths(1).toString() }) { Icon(Icons.Default.ChevronRight, "Позже") }
            }
            val range = Dates.monthRange(ym)
            val month = txns.filter { it.day in range }
            val monthNotes = nbNotes.filter { it.year == ym.year && it.month == ym.monthValue }
            if (tab <= 1 && monthNotes.isNotEmpty()) {
                val undated = monthNotes.filter { it.kind == NoteKind.UNDATED_EXPENSE }
                val written = monthNotes.filter { it.kind == NoteKind.WRITTEN_TOTAL }
                Tile(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), onClick = { nav.navigate(Routes.FIN_NOTEBOOK) }, padding = 10.dp) {
                    Text("Тетрадь за месяц", fontSize = 12.sp, color = LocalExtra.current.dim)
                    Text(
                        (if (undated.isNotEmpty()) "Расходы без даты: ${Money.format(undated.sumOf { it.amount ?: 0.0 }, cur)} (${undated.size})" else "") +
                            (if (written.isNotEmpty()) (if (undated.isNotEmpty()) " · " else "") + written.joinToString { "${it.label} ${Money.format(it.amount ?: 0.0, cur)}" } else ""),
                        fontSize = 13.sp, maxLines = 2,
                    )
                }
            }
            when (tab) {
                0 -> Operations(nav, month, cats, accounts, cur)
                1 -> Overview(txns, month, cats, ym, cur) { editCategory = it }
                2 -> Budgets(month, cats, budgets, ym, cur) { editBudget = it }
                3 -> RecurringList(recurring, cats, accounts, cur) { editRecurring = it }
                4 -> Accounts(accounts, txns, cur) { editAccount = it }
            }
        }
    }
    editAccount?.let { AccountDialog(it) { editAccount = null } }
    editBudget?.let { BudgetDialog(it, cats.filter { c -> !c.income }, cur) { editBudget = null } }
    editRecurring?.let { RecurringDialog(it, cats, accounts, cur) { editRecurring = null } }
    editCategory?.let { CategoryDialog(it) { editCategory = null } }
}

@Composable
private fun Operations(nav: NavHostController, month: List<Txn>, cats: List<Category>, accounts: List<Account>, cur: String) {
    val extra = LocalExtra.current
    val inc = month.filter { it.type == TxnType.INCOME }.sumOf { it.amount }
    val exp = month.filter { it.type == TxnType.EXPENSE }.sumOf { it.amount }
    LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat(Money.format(inc, cur), "доход", Modifier.weight(1f), extra.ok)
                Stat(Money.format(exp, cur), "расход", Modifier.weight(1f), extra.danger)
                Stat(Money.format(inc - exp, cur), "итог", Modifier.weight(1f))
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("− Расход", false) { nav.navigate(Routes.txn(0)) }
                Pill("+ Доход", false) { nav.navigate(Routes.txn(0, income = true)) }
            }
        }
        if (month.isEmpty()) item { Empty(Ic.wallet, "Операций нет", "Записывайте расходы сразу — это занимает пять секунд.") }
        month.groupBy { it.day }.toSortedMap(compareByDescending { it }).forEach { (day, list) ->
            item(key = "d$day") {
                val dayExp = list.filter { it.type == TxnType.EXPENSE }.sumOf { it.amount }
                Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp)) {
                    Text("${Dates.label(day)}, ${Dates.weekdayShort(day)}", Modifier.weight(1f), color = extra.dim, fontSize = 13.sp)
                    if (dayExp > 0) Text("−" + Money.format(dayExp, cur), color = extra.dim, fontSize = 13.sp)
                }
            }
            items(list, key = { it.id }) { t -> TxnRow(t, cats, accounts, cur) { nav.navigate(Routes.txn(t.id)) } }
        }
    }
}

@Composable
fun TxnRow(t: Txn, cats: List<Category>, accounts: List<Account>, cur: String, onClick: () -> Unit) {
    val extra = LocalExtra.current
    val c = cats.firstOrNull { it.id == t.categoryId }
    val acc = accounts.firstOrNull { it.id == t.accountId }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(12.dp)).padding(2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (t.type == TxnType.TRANSFER) "🔄" else c?.emoji ?: "💰", fontSize = 22.sp,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).padding(6.dp),
            )
        }
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
                when (t.type) {
                    TxnType.TRANSFER -> "Перевод → " + (accounts.firstOrNull { it.id == t.toAccountId }?.name ?: "?")
                    else -> c?.name ?: "Без категории"
                },
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(listOfNotNull(acc?.name, t.note.takeIf { it.isNotBlank() }).joinToString(" · "), fontSize = 12.sp, color = extra.dim, maxLines = 1)
        }
        Text(
            when (t.type) {
                TxnType.INCOME -> "+" + Money.format(t.amount, cur)
                TxnType.EXPENSE -> "−" + Money.format(t.amount, cur)
                else -> Money.format(t.amount, cur)
            },
            color = when (t.type) { TxnType.INCOME -> extra.ok; TxnType.EXPENSE -> MaterialTheme.colorScheme.onSurface; else -> extra.dim },
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun Overview(all: List<Txn>, month: List<Txn>, cats: List<Category>, ym: YearMonth, cur: String, onCategory: (Category) -> Unit) {
    val extra = LocalExtra.current
    val exp = month.filter { it.type == TxnType.EXPENSE }
    val total = exp.sumOf { it.amount }
    val byCat = exp.groupBy { it.categoryId }.map { (id, l) -> cats.firstOrNull { it.id == id } to l.sumOf { it.amount } }.sortedByDescending { it.second }
    val months = (5 downTo 0).map { ym.minusMonths(it.toLong()) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        Tile {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Donut(byCat.mapIndexed { i, (c, v) -> v.toFloat() to Palette.item(c?.color ?: i) }, size = 150.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Money.format(total, cur), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("расходы", fontSize = 11.sp, color = extra.dim)
                    }
                }
                Column(Modifier.padding(start = 12.dp)) {
                    byCat.take(6).forEachIndexed { i, (c, v) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                            Dot(Palette.item(c?.color ?: i), 9.dp)
                            Text(" ${c?.emoji ?: ""} ${c?.name ?: "Без категории"}", fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f))
                            Text(" ${if (total > 0) (v * 100 / total).toInt() else 0}%", fontSize = 12.sp, color = extra.dim)
                        }
                    }
                }
            }
        }
        SectionTitle("Расходы по категориям")
        Tile {
            if (byCat.isEmpty()) Text("Нет расходов за этот месяц", color = extra.dim)
            byCat.forEachIndexed { i, (c, v) ->
                Column(Modifier.padding(vertical = 6.dp).clickable(enabled = c != null) { c?.let(onCategory) }) {
                    Row {
                        Text("${c?.emoji ?: "💰"} ${c?.name ?: "Без категории"}", Modifier.weight(1f))
                        Text(Money.format(v, cur), fontWeight = FontWeight.Medium)
                    }
                    Bar((v / (byCat.first().second)).toFloat(), Palette.item(c?.color ?: i), Modifier.padding(top = 4.dp), height = 6.dp)
                }
            }
        }
        SectionTitle("Полгода: расходы")
        Tile {
            val monthly = months.map { m -> all.filter { it.type == TxnType.EXPENSE && it.day in Dates.monthRange(m) }.sumOf { it.amount }.toFloat() }
            BarChart(monthly, months.map { Dates.monthTitle(it).take(3) }, extra.danger, highlight = 5)
            val avg = monthly.dropLast(1).filter { it > 0 }.let { if (it.isEmpty()) 0f else it.average().toFloat() }
            if (avg > 0) Text("В среднем ${Money.format(avg.toDouble(), cur)} в месяц", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 6.dp))
        }
        SectionTitle("Полгода: доходы")
        Tile {
            BarChart(
                months.map { m -> all.filter { it.type == TxnType.INCOME && it.day in Dates.monthRange(m) }.sumOf { it.amount }.toFloat() },
                months.map { Dates.monthTitle(it).take(3) }, extra.ok, highlight = 5,
            )
        }
        val days = (Dates.monthRange(ym).last - Dates.monthRange(ym).first + 1).toInt()
        val passed = if (ym == YearMonth.now()) Dates.day(Dates.today()).dayOfMonth else days
        SectionTitle("Итоги месяца")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(Money.format(if (passed > 0) total / passed else 0.0, cur), "в день", Modifier.weight(1f))
            Stat("${exp.size}", "покупок", Modifier.weight(1f))
            Stat(Money.format(exp.maxOfOrNull { it.amount } ?: 0.0, cur), "самая крупная", Modifier.weight(1f))
        }
        SectionTitle("Категории")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            cats.forEach { c -> Pill("${c.emoji} ${c.name}", false) { onCategory(c) } }
            Pill("+ Категория", false) { onCategory(Category(name = "", emoji = "📦")) }
        }
        Gap(96.dp)
    }
}

@Composable
private fun Budgets(month: List<Txn>, cats: List<Category>, budgets: List<Budget>, ym: YearMonth, cur: String, onEdit: (Budget) -> Unit) {
    val extra = LocalExtra.current
    val exp = month.filter { it.type == TxnType.EXPENSE }
    val today = Dates.today()
    val range = Dates.monthRange(ym)
    val daysLeft = if (today in range) (range.last - today + 1).toInt() else if (today < range.first) (range.last - range.first + 1).toInt() else 0
    LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp)) {
        if (budgets.isEmpty()) item {
            Empty(Ic.target, "Бюджетов нет", "Задайте лимит на месяц — общий или по категориям. Приложение подскажет, сколько можно тратить в день.")
        }
        items(budgets.sortedBy { if (it.categoryId == 0L) -1 else it.categoryId }, key = { it.categoryId }) { b ->
            val c = cats.firstOrNull { it.id == b.categoryId }
            val spent = if (b.categoryId == 0L) exp.sumOf { it.amount } else exp.filter { it.categoryId == b.categoryId }.sumOf { it.amount }
            val level = Money.budgetLevel(spent, b.monthly)
            val color = when (level) { Money.BudgetLevel.OK -> extra.ok; Money.BudgetLevel.NEAR -> extra.warn; Money.BudgetLevel.OVER -> extra.danger }
            Tile(Modifier.padding(bottom = 10.dp), onClick = { onEdit(b) }) {
                Row {
                    Text(if (b.categoryId == 0L) "💼 Общий бюджет" else "${c?.emoji ?: ""} ${c?.name ?: "?"}", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Text("${Money.format(spent, cur)} из ${Money.format(b.monthly, cur)}", fontSize = 13.sp, color = extra.dim)
                }
                Bar((spent / b.monthly).toFloat(), color, Modifier.padding(vertical = 8.dp))
                Text(
                    when (level) {
                        Money.BudgetLevel.OVER -> "Перерасход ${Money.format(spent - b.monthly, cur)}"
                        else -> "Осталось ${Money.format(b.monthly - spent, cur)}" +
                            if (daysLeft > 0) " · ${Money.format(Money.dailyAllowance(b.monthly, spent, daysLeft), cur)} в день" else ""
                    },
                    fontSize = 12.sp, color = if (level == Money.BudgetLevel.OVER) extra.danger else extra.dim,
                )
            }
        }
    }
}

@Composable
private fun RecurringList(list: List<Recurring>, cats: List<Category>, accounts: List<Account>, cur: String, onEdit: (Recurring) -> Unit) {
    val extra = LocalExtra.current
    val monthlyOut = list.filter { it.active && !it.income }.sumOf {
        when (Repeat.of(it.repeat)) { Repeat.WEEKLY -> it.amount * 52 / 12; Repeat.YEARLY -> it.amount / 12; Repeat.DAILY -> it.amount * 30; else -> it.amount }
    }
    LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp)) {
        item {
            Tile {
                Text("Подписки и регулярные платежи", color = extra.dim, fontSize = 13.sp)
                Text("≈ ${Money.format(monthlyOut, cur)} в месяц", style = MaterialTheme.typography.titleLarge)
                Text("Операции добавляются автоматически в день платежа, накануне придёт напоминание.", fontSize = 12.sp, color = extra.dim)
            }
            Gap()
        }
        if (list.isEmpty()) item { Empty(Ic.receipt, "Пока пусто", "Добавьте аренду, связь, подписки или зарплату.") }
        items(list, key = { it.id }) { r ->
            val c = cats.firstOrNull { it.id == r.categoryId }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onEdit(r) }.padding(vertical = 10.dp, horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(c?.emoji ?: "🔁", fontSize = 22.sp)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(r.title, color = if (r.active) MaterialTheme.colorScheme.onSurface else extra.dim)
                    Text(
                        "${Repeat.of(r.repeat).label} · след. ${Dates.label(r.nextDay)}" +
                            (accounts.firstOrNull { it.id == r.accountId }?.let { " · ${it.name}" } ?: ""),
                        fontSize = 12.sp, color = extra.dim,
                    )
                }
                Text((if (r.income) "+" else "−") + Money.format(r.amount, cur), color = if (r.income) extra.ok else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun Accounts(accounts: List<Account>, txns: List<Txn>, cur: String, onEdit: (Account) -> Unit) {
    val extra = LocalExtra.current
    val total = accounts.sumOf { balanceOf(it, txns) }
    LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp)) {
        item {
            Tile {
                Text("Всего на счетах", color = extra.dim, fontSize = 13.sp)
                Text(Money.format(total, cur), style = MaterialTheme.typography.headlineMedium)
            }
            Gap()
        }
        items(accounts, key = { it.id }) { a ->
            Tile(Modifier.padding(bottom = 8.dp), onClick = { onEdit(a) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(a.emoji, fontSize = 24.sp)
                    Text(a.name, Modifier.weight(1f).padding(start = 12.dp))
                    val b = balanceOf(a, txns)
                    Text(Money.format(b, cur), fontWeight = FontWeight.SemiBold, color = if (b < 0) extra.danger else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun AccountDialog(a: Account, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(a.name) }
    var emoji by remember { mutableStateOf(a.emoji) }
    var initial by remember { mutableStateOf(if (a.initial == 0.0) "" else a.initial.plain()) }
    var confirm by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (a.id == 0L) "Новый счёт" else "Счёт") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TextInput(name, { name = it }, "Название")
                Gap(8.dp)
                NumberField(initial, { initial = it }, "Начальный остаток")
                Gap(8.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("💳", "💵", "🏦", "💰", "🪙", "📈", "🐷", "💶", "💲").forEach { e -> Pill(e, emoji == e) { emoji = e } }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) io { Graph.dao.upsertAccount(a.copy(name = name.trim(), emoji = emoji, initial = initial.num() ?: 0.0)) }
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (a.id != 0L) TextButton(onClick = { confirm = true }) { Text("Удалить", color = LocalExtra.current.danger) }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
    if (confirm) com.dasein.poryadok.ui.common.ConfirmDialog(
        "Удалить счёт «${a.name}»?", "Все операции по этому счёту тоже удалятся.", onDismiss = { confirm = false },
    ) {
        io { Graph.dao.deleteTxnsOfAccount(a.id); Graph.dao.deleteAccount(a) }
        onDismiss()
    }
}

@Composable
private fun BudgetDialog(b: Budget, cats: List<Category>, cur: String, onDismiss: () -> Unit) {
    var catId by remember { mutableStateOf(if (b.categoryId < 0) 0L else b.categoryId) }
    var amount by remember { mutableStateOf(if (b.monthly > 0) b.monthly.plain() else "") }
    var menu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Бюджет на месяц") },
        text = {
            Column {
                Box {
                    val c = cats.firstOrNull { it.id == catId }
                    FieldButton("Категория", if (catId == 0L) "💼 Все расходы" else "${c?.emoji} ${c?.name}", Modifier.fillMaxWidth()) {
                        if (b.categoryId < 0) menu = true
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("💼 Все расходы") }, onClick = { catId = 0; menu = false })
                        cats.forEach { cat -> DropdownMenuItem(text = { Text("${cat.emoji} ${cat.name}") }, onClick = { catId = cat.id; menu = false }) }
                    }
                }
                Gap(8.dp)
                NumberField(amount, { amount = it }, "Лимит", suffix = cur)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = amount.num()
                if (v != null && v > 0) io { Graph.dao.upsertBudget(Budget(catId, v)) }
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (b.categoryId >= 0) TextButton(onClick = { io { Graph.dao.deleteBudget(b.categoryId) }; onDismiss() }) {
                    Text("Удалить", color = LocalExtra.current.danger)
                }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}

@Composable
private fun RecurringDialog(r0: Recurring, cats: List<Category>, accounts: List<Account>, cur: String, onDismiss: () -> Unit) {
    var r by remember { mutableStateOf(r0) }
    var amount by remember { mutableStateOf(if (r0.amount > 0) r0.amount.plain() else "") }
    var pickDate by remember { mutableStateOf(false) }
    var catMenu by remember { mutableStateOf(false) }
    var accMenu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (r0.id == 0L) "Регулярный платёж" else r0.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Расход", !r.income) { r = r.copy(income = false) }
                    Pill("Доход", r.income) { r = r.copy(income = true) }
                }
                Gap(8.dp)
                TextInput(r.title, { r = r.copy(title = it) }, "Название (Netflix, аренда…)")
                Gap(8.dp)
                NumberField(amount, { amount = it }, "Сумма", suffix = cur)
                Gap(8.dp)
                FieldButton("Следующий платёж", Dates.label(r.nextDay), Modifier.fillMaxWidth()) { pickDate = true }
                Gap(8.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(Repeat.WEEKLY, Repeat.MONTHLY, Repeat.YEARLY).forEach { rep -> Pill(rep.label, r.repeat == rep.code) { r = r.copy(repeat = rep.code) } }
                }
                Gap(8.dp)
                Box {
                    val c = cats.firstOrNull { it.id == r.categoryId }
                    FieldButton("Категория", c?.let { "${it.emoji} ${it.name}" } ?: "—", Modifier.fillMaxWidth()) { catMenu = true }
                    DropdownMenu(expanded = catMenu, onDismissRequest = { catMenu = false }) {
                        cats.filter { it.income == r.income }.forEach { cat ->
                            DropdownMenuItem(text = { Text("${cat.emoji} ${cat.name}") }, onClick = { r = r.copy(categoryId = cat.id); catMenu = false })
                        }
                    }
                }
                Gap(8.dp)
                Box {
                    val a = accounts.firstOrNull { it.id == r.accountId }
                    FieldButton("Счёт", a?.let { "${it.emoji} ${it.name}" } ?: "—", Modifier.fillMaxWidth()) { accMenu = true }
                    DropdownMenu(expanded = accMenu, onDismissRequest = { accMenu = false }) {
                        accounts.forEach { acc -> DropdownMenuItem(text = { Text("${acc.emoji} ${acc.name}") }, onClick = { r = r.copy(accountId = acc.id); accMenu = false }) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Добавлять операцию сама", Modifier.weight(1f)); Switch(r.autoAdd, { r = r.copy(autoAdd = it) })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Напоминать", Modifier.weight(1f)); Switch(r.remind, { r = r.copy(remind = it) })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Активен", Modifier.weight(1f)); Switch(r.active, { r = r.copy(active = it) })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = amount.num()
                if (r.title.isNotBlank() && v != null && v > 0 && r.accountId != 0L) {
                    val cur2 = r.copy(title = r.title.trim(), amount = v)
                    io { Graph.dao.upsertRecurring(cur2); com.dasein.poryadok.Repo.processRecurring() }
                }
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (r0.id != 0L) TextButton(onClick = { io { Graph.dao.deleteRecurring(r0) }; onDismiss() }) {
                    Text("Удалить", color = LocalExtra.current.danger)
                }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
    if (pickDate) DatePickDialog(r.nextDay, onDismiss = { pickDate = false }, onPick = { it?.let { d -> r = r.copy(nextDay = d) } }, allowClear = false)
}

@Composable
private fun CategoryDialog(c: Category, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(c.name) }
    var emoji by remember { mutableStateOf(c.emoji) }
    var color by remember { mutableStateOf(c.color) }
    var income by remember { mutableStateOf(c.income) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (c.id == 0L) "Новая категория" else "Категория") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (c.id == 0L) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Расход", !income) { income = false }
                    Pill("Доход", income) { income = true }
                }
                Gap(8.dp)
                TextInput(name, { name = it }, "Название")
                Gap(8.dp)
                ColorPicker(color) { color = it }
                Gap(8.dp)
                EmojiPicker(emoji) { emoji = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) io { Graph.dao.upsertCategory(c.copy(name = name.trim(), emoji = emoji, color = color, income = income)) }
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (c.id != 0L) TextButton(onClick = {
                    io { Graph.dao.detachCategory(c.id); Graph.dao.deleteBudget(c.id); Graph.dao.deleteCategory(c) }
                    onDismiss()
                }) { Text("Удалить", color = LocalExtra.current.danger) }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}
