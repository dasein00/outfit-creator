@file:OptIn(ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.finance

import com.dasein.poryadok.ui.common.cardViewport
import com.dasein.poryadok.ui.common.swipeTabs
import com.dasein.poryadok.ui.common.tabSlide
import com.dasein.poryadok.ui.common.swipeTabsOf
import com.dasein.poryadok.ui.common.FitText
import com.dasein.poryadok.ui.common.btnLabel
import androidx.compose.foundation.clickable
import com.dasein.poryadok.ui.common.HowTo
import com.dasein.poryadok.ui.common.GlyphRow
import com.dasein.poryadok.ui.common.Glyphs
import com.dasein.poryadok.ui.common.Glyph
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
import com.dasein.poryadok.ui.common.Text
import com.dasein.poryadok.ui.common.TextButton
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

/** Подразделы финансов по порядку (номер — в Routes.finance). */
val FIN_TABS = listOf("Операции", "Счета", "Обзор", "Расходы", "Доходы", "Календарь", "Бюджеты", "Регулярные", "Категории", "Калькуляторы")

@Composable
fun FinanceScreen(nav: NavHostController, settings: Settings, initialTab: Int, onBack: (() -> Unit)? = null) {
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
    var align by remember { mutableStateOf(false) }
    val nbNotes by observe(emptyList()) { Graph.extra.financeNotes() }

    Screen(
        title = "Финансы",
        onBack = onBack,
        actions = {
            IconAction(Ic.bank, "Сбербанк") { nav.navigate(Routes.SBER) }
            IconAction(Ic.document, "Тетрадь финансов") { nav.navigate(Routes.FIN_NOTEBOOK) }
        },
        fab = {
            FloatingActionButton(
                onClick = {
                    when (tab) {
                        6 -> editBudget = Budget(categoryId = -1, monthly = 0.0)
                        7 -> editRecurring = Recurring(title = "", amount = 0.0, accountId = accounts.firstOrNull()?.id ?: 0, nextDay = Dates.today())
                        1 -> editAccount = Account(name = "")
                        8 -> editCategory = Category(name = "", emoji = "ui:grid")
                        4 -> nav.navigate(Routes.txn(0, income = true))
                        else -> nav.navigate(Routes.txn(0))
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
            ) { Icon(Icons.Default.Add, "Добавить") }
        },
    ) { pad ->
        Column(Modifier.padding(pad).swipeTabs(tab, FIN_TABS.size) { tab = it }) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp, containerColor = MaterialTheme.colorScheme.background) {
                FIN_TABS.forEachIndexed { i, t ->
                    Tab(tab == i, onClick = { tab = i }, text = { Text(t) })
                }
            }
            // Месяц — для операций и бюджетов; у обзора, расходов, доходов и календаря свой выбор периода.
            if (tab == 0 || tab == 6) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { ymText = ym.minusMonths(1).toString() }) { Icon(Icons.Default.ChevronLeft, "Раньше") }
                Text(Dates.monthTitle(ym), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { ymText = ym.plusMonths(1).toString() }) { Icon(Icons.Default.ChevronRight, "Позже") }
            }
            val range = Dates.monthRange(ym)
            val month = txns.filter { it.day in range }
            val monthNotes = nbNotes.filter { it.year == ym.year && it.month == ym.monthValue }
            if (tab == 0 && monthNotes.isNotEmpty()) {
                val undated = monthNotes.filter { it.kind == NoteKind.UNDATED_EXPENSE }
                val written = monthNotes.filter { it.kind == NoteKind.WRITTEN_TOTAL }
                Tile(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), onClick = { nav.navigate(Routes.FIN_NOTEBOOK) }, padding = 10.dp) {
                    Text(btnLabel("Тетрадь за месяц"), fontSize = 12.sp, color = LocalExtra.current.dim)
                    Text(
                        (if (undated.isNotEmpty()) "Расходы без даты: ${Money.format(undated.sumOf { it.amount ?: 0.0 }, cur)} (${undated.size})" else "") +
                            (if (written.isNotEmpty()) (if (undated.isNotEmpty()) " · " else "") + written.joinToString { "${it.label} ${Money.format(it.amount ?: 0.0, cur)}" } else ""),
                        fontSize = 13.sp, maxLines = 2,
                    )
                }
            }
            Column(Modifier.tabSlide(tab)) {
                when (tab) {
                    0 -> Operations(nav, month, cats, accounts, cur)
                    1 -> AccountsTab(nav, accounts, txns, cats, cur, onAlign = { align = true }, onStatement = { nav.navigate(STATEMENT_ROUTE) }) { editAccount = it }
                    // «Обзор»: итоги периода, затем метрики обзора, аналитики и инфографики — без повторов.
                    2 -> OverviewTab(nav, txns, cats, accounts, budgets, cur) { editCategory = it }
                    3 -> FlowTab(nav, txns, cats, accounts, income = false, cur = cur)
                    4 -> FlowTab(nav, txns, cats, accounts, income = true, cur = cur)
                    5 -> SpendCalendar(nav, txns, cats, accounts, cur)
                    6 -> Budgets(month, cats, budgets, ym, cur) { editBudget = it }
                    7 -> RecurringList(recurring, cats, accounts, cur) { editRecurring = it }
                    8 -> CategoriesTab(txns, cats, budgets, cur, onEdit = { editCategory = it }) { editBudget = it }
                    9 -> FinanceCalculators(cur)
                }
            }
        }
    }
    editAccount?.let { AccountDialog(it, txns, cur) { editAccount = null } }
    if (align) BalanceAlignDialog(accounts.filter { !it.archived }, txns, cur) { align = false }
    editBudget?.let { BudgetDialog(it, cats.filter { c -> !c.income }, cur) { editBudget = null } }
    editRecurring?.let { RecurringDialog(it, cats, accounts, cur) { editRecurring = null } }
    editCategory?.let { CategoryDialog(it) { editCategory = null } }
}

@Composable
private fun Operations(nav: NavHostController, monthAll: List<Txn>, cats: List<Category>, accounts: List<Account>, cur: String) {
    val extra = LocalExtra.current
    // Фильтр по категории (всплывающий список) и поиск по заметке.
    var catFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val month = monthAll.filter { t ->
        (catFilter == null || (t.categoryId ?: -1L) == catFilter) &&
            (query.isBlank() || t.note.contains(query, true) || (cats.firstOrNull { it.id == t.categoryId }?.name?.contains(query, true) == true))
    }
    val inc = month.filter { it.type == TxnType.INCOME }.sumOf { it.amount }
    val exp = month.filter { it.type == TxnType.EXPENSE }.sumOf { it.amount }
    LazyColumn(modifier = Modifier.cardViewport(), contentPadding = PaddingValues(bottom = 96.dp)) {
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
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryDropdown(cats, monthAll.filter { it.type != TxnType.TRANSFER }.groupingBy { it.categoryId ?: -1L }.eachCount(), catFilter, Modifier.weight(1f)) { catFilter = it }
                com.dasein.poryadok.ui.common.TextInput(query, { query = it }, "Поиск", Modifier.weight(1f))
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Выписка", false, glyph = "ui:document") { nav.navigate(STATEMENT_ROUTE) }
                Pill("ИИ", false, glyph = "ui:stats") { nav.navigate(FIN_AI_ROUTE) }
            }
        }
        if (month.isEmpty()) item { Empty(Ic.wallet, "Операций нет", "Записывайте расходы сразу — это занимает пять секунд.") }
        month.groupBy { it.day }.toSortedMap(compareByDescending { it }).forEach { (day, list) ->
            item(key = "d$day") {
                val dayExp = list.filter { it.type == TxnType.EXPENSE }.sumOf { it.amount }
                Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp)) {
                    Text("${Dates.label(day)}, ${Dates.weekdayShort(day)}", Modifier.weight(1f), color = extra.dim, fontSize = 13.sp)
                    if (dayExp > 0) Text(btnLabel("−") + Money.format(dayExp, cur), color = extra.dim, fontSize = 13.sp)
                }
            }
            items(list, key = { it.id }) { t -> TxnRow(t, cats, accounts, cur) { nav.navigate(Routes.txn(t.id)) } }
        }
        item { HowTo("finance") }
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
            Glyph(if (t.type == TxnType.TRANSFER) Glyphs.TRANSFER else c?.emoji ?: "ui:wallet", 24.dp, Modifier.padding(4.dp))
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
private fun Budgets(month: List<Txn>, cats: List<Category>, budgets: List<Budget>, ym: YearMonth, cur: String, onEdit: (Budget) -> Unit) {
    val extra = LocalExtra.current
    val exp = month.filter { it.type == TxnType.EXPENSE }
    val today = Dates.today()
    val range = Dates.monthRange(ym)
    val daysLeft = if (today in range) (range.last - today + 1).toInt() else if (today < range.first) (range.last - range.first + 1).toInt() else 0
    LazyColumn(modifier = Modifier.cardViewport(), contentPadding = PaddingValues(bottom = 96.dp)) {
        if (budgets.isEmpty()) item {
            Empty(Ic.target, "Бюджетов нет", "Задайте лимит на месяц — общий или по категориям. Приложение подскажет, сколько можно тратить в день.")
        }
        items(budgets.sortedBy { if (it.categoryId == 0L) -1 else it.categoryId }, key = { it.categoryId }) { b ->
            val c = cats.firstOrNull { it.id == b.categoryId }
            val spent = if (b.categoryId == 0L) exp.sumOf { it.amount } else exp.filter { it.categoryId == b.categoryId }.sumOf { it.amount }
            val level = Money.budgetLevel(spent, b.monthly)
            val color = when (level) { Money.BudgetLevel.OK -> extra.ok; Money.BudgetLevel.NEAR -> extra.warn; Money.BudgetLevel.OVER -> extra.danger }
            Tile(Modifier.padding(bottom = 10.dp), onClick = { onEdit(b) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph(if (b.categoryId == 0L) "ui:wallet" else c?.emoji ?: "ui:grid", 18.dp)
                    Text(btnLabel("  ") + if (b.categoryId == 0L) "Общий бюджет" else c?.name ?: "?", Modifier.weight(1f), fontWeight = FontWeight.Medium)
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
        item { HowTo("finance") }
    }
}

@Composable
private fun RecurringList(list: List<Recurring>, cats: List<Category>, accounts: List<Account>, cur: String, onEdit: (Recurring) -> Unit) {
    val extra = LocalExtra.current
    val monthlyOut = list.filter { it.active && !it.income }.sumOf {
        when (Repeat.of(it.repeat)) { Repeat.WEEKLY -> it.amount * 52 / 12; Repeat.YEARLY -> it.amount / 12; Repeat.DAILY -> it.amount * 30; else -> it.amount }
    }
    LazyColumn(modifier = Modifier.cardViewport(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item {
            Tile {
                Text(btnLabel("Подписки и регулярные платежи"), color = extra.dim, fontSize = 13.sp)
                FitText("≈ ${Money.format(monthlyOut, cur)} в месяц", style = MaterialTheme.typography.titleLarge)
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
                Glyph(c?.emoji ?: Glyphs.REPEAT, 24.dp)
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
        item { HowTo("finance") }
    }
}

@Composable
private fun AccountDialog(a: Account, txns: List<Txn>, cur: String, onDismiss: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var name by remember { mutableStateOf(a.name) }
    var emoji by remember { mutableStateOf(a.emoji) }
    var initial by remember { mutableStateOf(if (a.initial == 0.0) "" else a.initial.plain()) }
    val balance = remember { balanceOf(a, txns) }
    var actual by remember { mutableStateOf(if (a.id == 0L) "" else balance.plain()) }
    var savings by remember { mutableStateOf(a.id != 0L && AccountMeta.savings(ctx, a.id)) }
    var rate by remember { mutableStateOf(AccountMeta.rate(ctx, a.id).takeIf { a.id != 0L && it > 0 }?.plain().orEmpty()) }
    var confirm by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (a.id == 0L) "Новый счёт" else "Счёт") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TextInput(name, { name = it }, "Название")
                Gap(8.dp)
                if (a.id == 0L) NumberField(initial, { initial = it }, "Остаток сейчас")
                else {
                    NumberField(actual, { actual = it }, "Остаток сейчас", suffix = cur)
                    Text("Впишите, сколько на счёте по данным банка, — остаток выровняется, история операций не изменится.", fontSize = 11.sp, color = LocalExtra.current.dim)
                }
                Gap(8.dp)
                com.dasein.poryadok.ui.common.Segments(listOf(false to "Обычный", true to "Сберегательный"), savings, { savings = it })
                if (savings) NumberField(rate, { rate = it }, "Процент годовых", suffix = "%")
                Gap(8.dp)
                GlyphRow(listOf("ui:card", "ui:coins", "ui:bank", "ui:wallet", "ui:receipt", "cal/39", "ui:stats", "ui:pie", "ui:gift", "ui:notebook"), emoji) { emoji = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) io {
                    val init = if (a.id == 0L) initial.num() ?: 0.0 else a.initial + ((actual.num() ?: balance) - balance)
                    val id = Graph.dao.upsertAccount(a.copy(name = name.trim(), emoji = emoji, initial = init))
                    AccountMeta.set(ctx, if (a.id == 0L) id else a.id, savings, rate.num() ?: 0.0)
                }
                onDismiss()
            }) { Text(btnLabel("Сохранить")) }
        },
        dismissButton = {
            com.dasein.poryadok.ui.common.ButtonsRow {
                if (a.id != 0L) TextButton(onClick = { confirm = true }) { Text(btnLabel("Удалить"), color = LocalExtra.current.danger) }
                TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) }
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
        title = { Text(btnLabel("Бюджет на месяц")) },
        text = {
            Column {
                Box {
                    val c = cats.firstOrNull { it.id == catId }
                    FieldButton("Категория", if (catId == 0L) "Все расходы" else c?.name ?: "", Modifier.fillMaxWidth(), glyph = if (catId == 0L) "ui:wallet" else c?.emoji) {
                        if (b.categoryId < 0) menu = true
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(btnLabel("Все расходы")) }, leadingIcon = { Glyph("ui:wallet", 20.dp) }, onClick = { catId = 0; menu = false })
                        cats.forEach { cat -> DropdownMenuItem(text = { Text(cat.name) }, leadingIcon = { Glyph(cat.emoji, 20.dp) }, onClick = { catId = cat.id; menu = false }) }
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
            }) { Text(btnLabel("Сохранить")) }
        },
        dismissButton = {
            com.dasein.poryadok.ui.common.ButtonsRow {
                if (b.categoryId >= 0) TextButton(onClick = { io { Graph.dao.deleteBudget(b.categoryId) }; onDismiss() }) {
                    Text(btnLabel("Удалить"), color = LocalExtra.current.danger)
                }
                TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) }
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
                    FieldButton("Категория", c?.name ?: "—", Modifier.fillMaxWidth(), glyph = c?.emoji) { catMenu = true }
                    DropdownMenu(expanded = catMenu, onDismissRequest = { catMenu = false }) {
                        cats.filter { it.income == r.income }.forEach { cat ->
                            DropdownMenuItem(text = { Text(cat.name) }, leadingIcon = { Glyph(cat.emoji, 20.dp) }, onClick = { r = r.copy(categoryId = cat.id); catMenu = false })
                        }
                    }
                }
                Gap(8.dp)
                Box {
                    val a = accounts.firstOrNull { it.id == r.accountId }
                    FieldButton("Счёт", a?.name ?: "—", Modifier.fillMaxWidth(), glyph = a?.emoji) { accMenu = true }
                    DropdownMenu(expanded = accMenu, onDismissRequest = { accMenu = false }) {
                        accounts.forEach { acc -> DropdownMenuItem(text = { Text(acc.name) }, leadingIcon = { Glyph(acc.emoji, 20.dp) }, onClick = { r = r.copy(accountId = acc.id); accMenu = false }) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(btnLabel("Добавлять операцию сама"), Modifier.weight(1f)); Switch(r.autoAdd, { r = r.copy(autoAdd = it) })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(btnLabel("Напоминать"), Modifier.weight(1f)); Switch(r.remind, { r = r.copy(remind = it) })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(btnLabel("Активен"), Modifier.weight(1f)); Switch(r.active, { r = r.copy(active = it) })
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
            }) { Text(btnLabel("Сохранить")) }
        },
        dismissButton = {
            com.dasein.poryadok.ui.common.ButtonsRow {
                if (r0.id != 0L) TextButton(onClick = { io { Graph.dao.deleteRecurring(r0) }; onDismiss() }) {
                    Text(btnLabel("Удалить"), color = LocalExtra.current.danger)
                }
                TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) }
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
            }) { Text(btnLabel("Сохранить")) }
        },
        dismissButton = {
            com.dasein.poryadok.ui.common.ButtonsRow {
                if (c.id != 0L) TextButton(onClick = {
                    io { Graph.dao.detachCategory(c.id); Graph.dao.deleteBudget(c.id); Graph.dao.deleteCategory(c) }
                    onDismiss()
                }) { Text(btnLabel("Удалить"), color = LocalExtra.current.danger) }
                TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) }
            }
        },
    )
}
