package com.dasein.poryadok.data

import androidx.room.withTransaction
import com.dasein.poryadok.Graph
import com.dasein.poryadok.logic.Cooking
import com.dasein.poryadok.logic.Notebook
import java.time.LocalDate
import java.time.YearMonth

/**
 * Перенос рукописной тетради в приложение.
 * Дневные суммы → доходы на счёт «Тетрадь»; итоги, расходы без даты и дни без записи → месячные заметки.
 * Каждая исходная запись получает ключ, поэтому повторный импорт того же файла ничего не дублирует.
 */
object NotebookImport {
    const val ACCOUNT = "Тетрадь"
    private const val KIND_INCOME = "nb-inc"
    private const val KIND_NOTE = "nb-note"

    data class Report(
        val year: Int,
        val incomeAdded: Int,
        val incomeSkipped: Int,
        val notesAdded: Int,
        val notesSkipped: Int,
        val writtenAdded: Int,
        val expensesAdded: Int,
        val noIncomeAdded: Int,
        val analysis: Notebook.Analysis,
        val written: List<Notebook.Written>,
    )

    private fun incomeKey(i: Notebook.Income) = "nb:inc:%02d-%02d:%s".format(i.month, i.day, Cooking.amount(i.amount))
    private fun offKey(o: Notebook.NoIncome) = "nb:off:%02d-%02d".format(o.month, o.day)
    private fun writtenKey(w: Notebook.Written) = "nb:tot:%02d:%s:%s".format(w.month, w.label, Cooking.amount(w.amount))
    private fun expenseKey(e: Notebook.Expense) = "nb:exp:%02d:%d:%s".format(e.month, e.index, e.raw)

    private fun dayOf(year: Int, month: Int, day: Int): Long {
        val ym = YearMonth.of(year, month)
        return ym.atDay(day.coerceAtMost(ym.lengthOfMonth())).toEpochDay()
    }

    suspend fun apply(data: Notebook.Data, year: Int): Report {
        val dao = Graph.dao
        val x = Graph.extra
        val now = System.currentTimeMillis()
        val account = dao.accountsNow().firstOrNull { it.name == ACCOUNT }?.id
            ?: dao.upsertAccount(Account(name = ACCOUNT, emoji = "📒", color = 7, sort = 50))
        val cats = dao.categoriesNow()
        val salary = cats.firstOrNull { it.income && it.name.startsWith("Зарплата", ignoreCase = true) }?.id
            ?: dao.upsertCategory(Category(name = "Зарплата/ежедневный доход", emoji = "💼", income = true, color = 3))
        var incAdded = 0; var incSkipped = 0; var notesAdded = 0; var notesSkipped = 0
        var written = 0; var expenses = 0; var off = 0

        suspend fun note(key: String, n: FinanceNote): Boolean {
            if (x.importRecord(key) != null) { notesSkipped++; return false }
            val id = x.upsertFinanceNote(n.copy(importKey = key))
            x.putImportRecord(ImportRecord(key, KIND_NOTE, id, now))
            notesAdded++
            return true
        }

        Graph.db.withTransaction {
            data.incomes.forEach { i ->
                val key = incomeKey(i)
                if (x.importRecord(key) != null) { incSkipped++; return@forEach }
                val meta = listOf(
                    "Тетрадь: ${i.category.ifBlank { "зарплата/ежедневный доход" }}",
                    i.description, i.source.takeIf { it.isNotBlank() }?.let { "источник: $it" },
                    i.confidence.takeIf { it.isNotBlank() }?.let { "уверенность: $it" },
                ).filter { !it.isNullOrBlank() }.joinToString(" · ")
                val id = dao.upsertTxn(
                    Txn(type = TxnType.INCOME, amount = i.amount, accountId = account, categoryId = salary, day = dayOf(year, i.month, i.day), note = meta, createdAt = now)
                )
                x.putImportRecord(ImportRecord(key, KIND_INCOME, id, now))
                incAdded++
            }
        }
        Graph.extraDb.withTransaction {
            data.noIncome.forEach { o ->
                if (note(offKey(o), FinanceNote(year = year, month = o.month, day = o.day, kind = NoteKind.NO_INCOME, label = "нет записи о доходе / выходной", raw = o.description, confidence = o.confidence, source = o.source))) off++
            }
            data.written.forEach { w ->
                if (note(writtenKey(w), FinanceNote(year = year, month = w.month, kind = NoteKind.WRITTEN_TOTAL, label = w.label, amount = w.amount, raw = w.description, confidence = w.confidence, source = w.source))) written++
            }
            data.expenses.forEach { e ->
                if (note(
                        expenseKey(e),
                        FinanceNote(
                            year = year, month = e.month, kind = NoteKind.UNDATED_EXPENSE, label = e.label, amount = e.amount, category = e.category,
                            raw = e.raw, ambiguous = e.ambiguous, reason = e.reason, source = "правая колонка тетради, дата не указана",
                        )
                    )
                ) expenses++
            }
        }
        return Report(year, incAdded, incSkipped, notesAdded, notesSkipped, written, expenses, off, Notebook.analyze(data), data.written)
    }

    /** Год в тетради не записан — его можно поменять: месяц и число остаются прежними. */
    suspend fun changeYear(newYear: Int): Int {
        val dao = Graph.dao
        val x = Graph.extra
        var changed = 0
        val txns = dao.txnsNow().associateBy { it.id }
        Graph.db.withTransaction {
            x.importRecordsOf(KIND_INCOME).forEach { r ->
                val t = txns[r.targetId] ?: return@forEach
                val d = LocalDate.ofEpochDay(t.day)
                val nd = dayOf(newYear, d.monthValue, d.dayOfMonth)
                if (nd != t.day) { dao.upsertTxn(t.copy(day = nd)); changed++ }
            }
        }
        Graph.extraDb.withTransaction {
            x.financeNotesNow().filter { it.importKey.startsWith("nb:") && it.year != newYear }.forEach { x.upsertFinanceNote(it.copy(year = newYear)); changed++ }
        }
        return changed
    }

    /** Удалить всё, что пришло из тетради (например, чтобы импортировать исправленный файл). */
    suspend fun removeAll(): Int {
        val dao = Graph.dao
        val x = Graph.extra
        var n = 0
        val txns = dao.txnsNow().associateBy { it.id }
        x.importRecordsOf(KIND_INCOME).forEach { r ->
            txns[r.targetId]?.let { dao.deleteTxn(it); n++ }
            x.deleteImportRecord(r.key)
        }
        x.importRecordsOf(KIND_NOTE).forEach { r -> x.deleteImportRecord(r.key) }
        x.financeNotesNow().filter { it.importKey.startsWith("nb:") }.forEach { x.deleteFinanceNote(it); n++ }
        return n
    }

    /** Отчёт по уже загруженным данным — для экрана тетради. */
    suspend fun currentIncomeByMonth(): Map<Pair<Int, Int>, Double> {
        val x = Graph.extra
        val ids = x.importRecordsOf(KIND_INCOME).map { it.targetId }.toSet()
        return Graph.dao.txnsNow().filter { it.id in ids }.groupBy {
            val d = LocalDate.ofEpochDay(it.day); d.year to d.monthValue
        }.mapValues { (_, v) -> v.sumOf { it.amount } }
    }
}
