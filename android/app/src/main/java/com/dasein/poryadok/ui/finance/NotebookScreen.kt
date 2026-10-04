package com.dasein.poryadok.ui.finance

import com.dasein.poryadok.ui.common.Hint
import com.dasein.poryadok.ui.common.HowTo
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.FinanceNote
import com.dasein.poryadok.data.NoteKind
import com.dasein.poryadok.data.NotebookImport
import com.dasein.poryadok.data.Settings
import com.dasein.poryadok.logic.Money
import com.dasein.poryadok.logic.Notebook
import com.dasein.poryadok.ui.common.AppIcon
import com.dasein.poryadok.ui.common.ConfirmDialog
import com.dasein.poryadok.ui.common.Empty
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.HGap
import com.dasein.poryadok.ui.common.Ic
import com.dasein.poryadok.ui.common.NumberField
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Файл, открытый в DASEIN из другого приложения («Открыть с помощью» / «Поделиться»). */
object NotebookInbox {
    val pending = mutableStateOf<Uri?>(null)
}

private suspend fun readText(ctx: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("Не удалось открыть файл")
}

@Composable
fun NotebookScreen(nav: NavHostController, settings: Settings) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    val notes by observe<List<FinanceNote>?>(null) { Graph.extra.financeNotes() }
    val txns by observe(emptyList()) { Graph.dao.txns() }
    val records by observe(emptyList()) { Graph.extra.importRecordsLike("nb-inc") }
    val tableRecords by observe(emptyList()) { Graph.extra.importRecordsLike("nb-day") }
    var preview by remember { mutableStateOf<Notebook.Data?>(null) }
    var report by remember { mutableStateOf<NotebookImport.Report?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var yearDialog by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }
    val cur = settings.currency

    fun open(uri: Uri) {
        scope.launch {
            runCatching { Notebook.parse(readText(ctx, uri)) }
                .onSuccess { preview = it; error = null; report = null }
                .onFailure { error = "Файл не распознан: ${it.message ?: it.javaClass.simpleName}. Нужна таблица CSV (Дата; Расход; Доход; Примечание) или JSON/CSV из оцифровки тетради." }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { open(it) } }
    LaunchedEffect(NotebookInbox.pending.value) { NotebookInbox.pending.value?.let { NotebookInbox.pending.value = null; open(it) } }

    val incomeIds = records.map { it.targetId }.toSet()
    val incomeByMonth = txns.filter { it.id in incomeIds }.groupBy { LocalDate.ofEpochDay(it.day).let { d -> d.year to d.monthValue } }
    val allNotes = notes.orEmpty().filter { it.importKey.startsWith("nb:") }
    val tableIds = tableRecords.map { it.targetId }.toSet()
    val tableByMonth = txns.filter { it.id in tableIds }.groupBy { LocalDate.ofEpochDay(it.day).let { d -> d.year to d.monthValue } }
    val months = (allNotes.map { it.year to it.month } + incomeByMonth.keys).toSortedSet(compareBy<Pair<Int, Int>>({ it.first }, { it.second })).toList()

    Screen("Тетрадь финансов", onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row {
                AppIcon(Ic.document, 32.dp)
                HGap(12.dp)
                Hint("notebook_about", "Загрузка записей с бумаги. Таблица «Дата · Расход · Доход · Примечание» (CSV) — каждая строка станет расходом или доходом " +
                        "на счёте «${NotebookImport.ACCOUNT}» в свой день, категория подберётся по примечанию. Оцифровка тетради (JSON или CSV): дневные суммы — доходы, " +
                        "итоги, расходы без даты и выходные — месячные записи. Суммы и даты не меняются, повторная загрузка не создаёт дублей.", title = "Что делает импорт")
            }
            Gap(10.dp)
            Button(onClick = { picker.launch(arrayOf("application/json", "text/*", "text/csv", "application/octet-stream")) }, Modifier.fillMaxWidth()) {
                Text("Выбрать файл тетради")
            }
            error?.let { Text(it, color = extra.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }

            report?.let { r -> ReportCard(r, cur) }

            if (tableByMonth.isNotEmpty()) TableMonths(tableByMonth, cur)
            if (months.isNotEmpty() || tableByMonth.isNotEmpty()) {
                Row(Modifier.padding(top = 8.dp)) {
                    if (months.isNotEmpty()) {
                        OutlinedButton(onClick = { yearDialog = true }, Modifier.weight(1f)) { Text("Изменить год") }
                        HGap(8.dp)
                    }
                    OutlinedButton(onClick = { confirmWipe = true }, Modifier.weight(1f)) { Text("Удалить данные") }
                }
                if (months.isNotEmpty()) Text(
                    "Год в тетради не был указан — при импорте использован ${months.first().first}. Если он другой, поменяйте: месяцы и числа останутся прежними.",
                    fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (notes != null && months.isEmpty() && tableByMonth.isEmpty() && report == null && preview == null) {
                Empty(Ic.notebook, "Тетрадь ещё не загружена", "Выберите таблицу расходов и доходов (.csv) или файл оцифровки тетради — после загрузки здесь появятся месяцы, итоги и записи для проверки.")
            }
            months.forEach { (y, m) ->
                val monthNotes = allNotes.filter { it.year == y && it.month == m }
                MonthCard(y, m, incomeByMonth[y to m].orEmpty().sumOf { it.amount }, incomeByMonth[y to m].orEmpty().size, monthNotes, cur)
            }
            HowTo("fin_notebook")
            Gap(40.dp)
        }
    }

    preview?.let { d ->
        var year by remember(d) { mutableStateOf(d.year.toString()) }
        val a = remember(d) { Notebook.analyze(d) }
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text("Проверка перед импортом") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (d.dated.isNotEmpty() || d.skipped.isNotEmpty()) {
                        TablePreview(d, cur)
                    } else {
                        Text("Доходов по дням: ${d.incomes.size} на ${Money.format(d.incomes.sumOf { it.amount }, cur)}")
                        Text("Дней без записи: ${d.noIncome.size}")
                        Text("Сумм, записанных отдельно: ${d.written.size}")
                        Text("Расходов без даты: ${d.expenses.size}")
                        Text("Требуют проверки: ${a.ambiguous.size}", color = if (a.ambiguous.isNotEmpty()) extra.warn else MaterialTheme.colorScheme.onSurface)
                        Gap(8.dp)
                        NumberField(year, { year = it }, "Год записей", decimal = false)
                        Text("В тетради год не указан, по умолчанию — ${d.year}. Повторный импорт того же файла дублей не создаст.", fontSize = 12.sp, color = extra.dim)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val y = year.toIntOrNull()?.takeIf { it in 2000..2100 } ?: d.year
                    preview = null
                    scope.launch { report = NotebookImport.apply(d, y) }
                }) { Text("Импортировать") }
            },
            dismissButton = { TextButton(onClick = { preview = null }) { Text("Отмена") } },
        )
    }
    if (yearDialog) {
        var y by remember { mutableStateOf(months.firstOrNull()?.first?.toString() ?: "") }
        AlertDialog(
            onDismissRequest = { yearDialog = false },
            title = { Text("Год записей тетради") },
            text = { NumberField(y, { y = it }, "Год", decimal = false) },
            confirmButton = {
                TextButton(onClick = {
                    val v = y.toIntOrNull()
                    if (v != null && v in 2000..2100) scope.launch { NotebookImport.changeYear(v) }
                    yearDialog = false
                }) { Text("Применить") }
            },
            dismissButton = { TextButton(onClick = { yearDialog = false }) { Text("Отмена") } },
        )
    }
    if (confirmWipe) ConfirmDialog(
        "Удалить данные тетради?", "Будут удалены загруженные из файлов расходы, доходы и месячные записи. Файл можно будет загрузить заново.",
        onDismiss = { confirmWipe = false },
    ) { scope.launch { NotebookImport.removeAll(); report = null } }
}

@Composable
private fun ReportCard(r: NotebookImport.Report, cur: String) {
    val extra = LocalExtra.current
    SectionTitle("Итоги импорта")
    if (r.tableExpenses + r.tableIncomes + r.tableSkipped > 0 || r.tableProblems.isNotEmpty()) Tile {
        Text("Из таблицы добавлено: расходов ${r.tableExpenses}, доходов ${r.tableIncomes}", fontWeight = FontWeight.SemiBold)
        if (r.tableSkipped > 0) Text("Уже были загружены раньше, пропущено: ${r.tableSkipped}", fontSize = 13.sp, color = extra.dim)
        Text("Операции на счёте «${NotebookImport.ACCOUNT}» — их видно в «Финансах» по дням и категориям.", fontSize = 13.sp, color = extra.dim)
        if (r.tableProblems.isNotEmpty()) {
            Gap(6.dp)
            Text("Не загружено — проверьте в таблице (${r.tableProblems.size})", fontWeight = FontWeight.SemiBold, color = extra.warn)
            r.tableProblems.take(30).forEach { Text("• $it", fontSize = 13.sp) }
        }
    }
    if (r.incomeAdded + r.incomeSkipped + r.notesAdded + r.notesSkipped > 0) Tile(Modifier.padding(top = 8.dp)) {
        Text("Доходных записей добавлено: ${r.incomeAdded}" + if (r.incomeSkipped > 0) " (уже были: ${r.incomeSkipped})" else "", fontWeight = FontWeight.SemiBold)
        Text("Месячных записей добавлено: ${r.notesAdded}" + if (r.notesSkipped > 0) " (уже были: ${r.notesSkipped})" else "")
        Text("  · расходов без даты: ${r.expensesAdded}", fontSize = 13.sp, color = extra.dim)
        Text("  · сумм, записанных отдельно: ${r.writtenAdded}", fontSize = 13.sp, color = extra.dim)
        Text("  · дней без записи о доходе: ${r.noIncomeAdded}", fontSize = 13.sp, color = extra.dim)
        Text("Год: ${r.year}", fontSize = 13.sp, color = extra.dim)
        Gap(8.dp)
        Text("Доходы по месяцам", fontWeight = FontWeight.SemiBold)
        r.analysis.months.filter { it.incomeDays > 0 }.forEach {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${Notebook.monthName(it.month)} (${it.incomeDays} дн.)", fontSize = 14.sp)
                Text(Money.format(it.incomeSum, cur), fontSize = 14.sp)
            }
        }
        if (r.written.isNotEmpty()) {
            Gap(8.dp)
            Text("Суммы, написанные в тетради", fontWeight = FontWeight.SemiBold)
            r.written.forEach { w ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${Notebook.monthName(w.month)}: ${w.label}", fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(Money.format(w.amount, cur), fontSize = 14.sp)
                }
            }
        }
        if (r.analysis.ambiguous.isNotEmpty()) {
            Gap(8.dp)
            Text("Нужно проверить вручную (${r.analysis.ambiguous.size})", fontWeight = FontWeight.SemiBold, color = extra.warn)
            r.analysis.ambiguous.forEach { Text("• $it", fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp)) }
        }
    }
}

@Composable
private fun MonthCard(year: Int, month: Int, incomeSum: Double, incomeDays: Int, notes: List<FinanceNote>, cur: String) {
    val extra = LocalExtra.current
    val written = notes.filter { it.kind == NoteKind.WRITTEN_TOTAL }
    val expenses = notes.filter { it.kind == NoteKind.UNDATED_EXPENSE }
    val off = notes.filter { it.kind == NoteKind.NO_INCOME }
    val expenseSum = expenses.sumOf { it.amount ?: 0.0 }
    SectionTitle("${Notebook.monthName(month)} $year")
    Tile {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Доходы по дням", fontSize = 12.sp, color = extra.dim)
                Text(Money.format(incomeSum, cur), style = MaterialTheme.typography.titleMedium, color = extra.ok)
                Text("$incomeDays дн. с записью", fontSize = 12.sp, color = extra.dim)
            }
            Column(Modifier.weight(1f)) {
                Text("Расходы без даты", fontSize = 12.sp, color = extra.dim)
                Text(Money.format(expenseSum, cur), style = MaterialTheme.typography.titleMedium)
                Text("${expenses.size} записей", fontSize = 12.sp, color = extra.dim)
            }
        }
        if (written.isNotEmpty()) {
            Gap(8.dp)
            Text("Написано в тетради", fontSize = 12.sp, color = extra.dim)
            written.forEach { w ->
                val cmp = Notebook.compareWith(w.label)
                val computed = when (cmp) { "доходы" -> incomeSum; "расходы" -> expenseSum; else -> null }
                val conflict = computed != null && w.amount != computed
                Row(Modifier.fillMaxWidth()) {
                    Text(w.label, Modifier.weight(1f), fontSize = 14.sp)
                    Text(Money.format(w.amount ?: 0.0, cur), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                if (conflict) Text(
                    "⚠ не совпадает: $cmp по записям — ${Money.format(computed!!, cur)}. Сохранены оба значения.",
                    fontSize = 12.sp, color = extra.warn,
                )
            }
        }
        if (expenses.isNotEmpty()) {
            Gap(8.dp)
            Text("Расходы из правой колонки (дата неизвестна)", fontSize = 12.sp, color = extra.dim)
            expenses.forEach { e ->
                Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(e.label.ifBlank { "—" } + " · " + e.category, fontSize = 14.sp)
                        Text("в тетради: «${e.raw}»" + if (e.ambiguous) " — ${e.reason}" else "", fontSize = 11.sp, color = if (e.ambiguous) extra.warn else extra.dim)
                    }
                    Text(e.amount?.let { Money.format(it, cur) } ?: "—", fontSize = 14.sp)
                }
            }
        }
        if (off.isNotEmpty()) {
            Gap(8.dp)
            Text(
                "Без записи о доходе / выходной: " + off.mapNotNull { it.day }.sorted().joinToString(", "),
                fontSize = 12.sp, color = extra.dim,
            )
        }
    }
}

/** Что будет загружено из простой таблицы: суммы, месяцы, категории и строки с ошибками. */
@Composable
private fun TablePreview(d: Notebook.Data, cur: String) {
    val extra = LocalExtra.current
    val out = d.dated.filterNot { it.income }
    val inc = d.dated.filter { it.income }
    Text("Расходов: ${out.size} на ${Money.format(out.sumOf { it.amount }, cur)}")
    Text("Доходов: ${inc.size} на ${Money.format(inc.sumOf { it.amount }, cur)}")
    if (d.dated.isNotEmpty()) {
        val days = d.dated.map { it.epochDay }
        Text("Даты: с ${LocalDate.ofEpochDay(days.min()).format(DMY)} по ${LocalDate.ofEpochDay(days.max()).format(DMY)}", fontSize = 13.sp, color = extra.dim)
        Gap(6.dp)
        Text("Расходы по категориям", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        out.groupBy { it.category }.entries.sortedByDescending { e -> e.value.sumOf { it.amount } }.forEach { (c, l) ->
            Row(Modifier.fillMaxWidth()) {
                Text("$c (${l.size})", Modifier.weight(1f), fontSize = 13.sp)
                Text(Money.format(l.sumOf { it.amount }, cur), fontSize = 13.sp)
            }
        }
    }
    if (d.skipped.isNotEmpty()) {
        Gap(6.dp)
        Text("Не загрузятся (${d.skipped.size})", fontWeight = FontWeight.SemiBold, color = extra.warn, fontSize = 14.sp)
        d.skipped.take(15).forEach { Text("• $it", fontSize = 12.sp) }
    }
    Gap(6.dp)
    Text("Повторная загрузка того же файла дублей не создаст.", fontSize = 12.sp, color = extra.dim)
}

private val DMY = java.time.format.DateTimeFormatter.ofPattern("d.MM.yyyy")

/** Загруженное из таблицы, по месяцам: расходы, доходы, разница. */
@Composable
private fun TableMonths(byMonth: Map<Pair<Int, Int>, List<com.dasein.poryadok.data.Txn>>, cur: String) {
    val extra = LocalExtra.current
    SectionTitle("Из таблицы")
    Tile {
        byMonth.entries.sortedWith(compareByDescending<Map.Entry<Pair<Int, Int>, List<com.dasein.poryadok.data.Txn>>> { it.key.first }.thenByDescending { it.key.second }).forEach { (ym, l) ->
            val out = l.filter { it.type == com.dasein.poryadok.data.TxnType.EXPENSE }.sumOf { it.amount }
            val inc = l.filter { it.type == com.dasein.poryadok.data.TxnType.INCOME }.sumOf { it.amount }
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("${Notebook.monthName(ym.second)} ${ym.first}", fontWeight = FontWeight.Medium)
                    Text("${l.size} записей", fontSize = 12.sp, color = extra.dim)
                }
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    Text("−" + Money.format(out, cur), fontSize = 14.sp)
                    Text("+" + Money.format(inc, cur), fontSize = 14.sp, color = extra.ok)
                }
            }
        }
    }
}
