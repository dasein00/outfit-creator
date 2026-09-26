package com.dasein.poryadok.logic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Оцифрованная бумажная тетрадь доходов и расходов (файл JSON или CSV).
 * Суммы и даты не меняются; неоднозначное помечается, а не исправляется.
 */
object Notebook {
    data class Income(val month: Int, val day: Int, val amount: Double, val category: String, val description: String, val source: String, val confidence: String)
    data class NoIncome(val month: Int, val day: Int, val description: String, val source: String, val confidence: String)
    data class Written(val month: Int, val label: String, val amount: Double, val description: String, val source: String, val confidence: String)
    data class Expense(val month: Int, val index: Int, val raw: String, val amount: Double?, val label: String, val category: String, val ambiguous: Boolean, val reason: String)

    data class Data(
        val year: Int,
        val currency: String,
        val incomes: List<Income>,
        val noIncome: List<NoIncome>,
        val written: List<Written>,
        val expenses: List<Expense>,
        val uncertainty: Map<Int, String>,
    )

    const val UNKNOWN = "Неизвестно"

    private val MONTHS = listOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь")
    fun monthName(m: Int): String = MONTHS.getOrElse(m - 1) { "Месяц $m" }

    /** Подписи расходов, которые читаются однозначно, → категории приложения. */
    private val CATEGORY_WORDS = listOf(
        "Продукты" to listOf("мясо", "курица", "хлеб", "еда", "масло", "сыр", "молоко", "продукт", "овощ", "фрукт", "рыба", "яйц"),
        "Транспорт" to listOf("бенз", "заправ", "такси", "проезд", "метро"),
        "Одежда" to listOf("одежд", "обувь"),
        "Связь и интернет" to listOf("связь", "телефон", "интернет"),
        "Жильё и ЖКХ" to listOf("квартир", "аренд", "жкх", "коммун"),
        "Здоровье" to listOf("аптек", "лекарств", "врач"),
    )

    private val NOTE = Regex("""^\s*(\d[\d\s]*(?:[.,]\d+)?)\s*(.*)$""")

    /** Разбирает строку из колонки расходов: «2200 мясо», «400 (?)», «100 курьер (?)». */
    fun parseExpense(month: Int, index: Int, raw: String): Expense {
        val m = NOTE.find(raw.trim())
        if (m == null) return Expense(month, index, raw, null, raw.trim(), UNKNOWN, true, "нет суммы")
        val amount = m.groupValues[1].replace(" ", "").replace(",", ".").toDoubleOrNull()
        val label = m.groupValues[2].trim()
        val unsure = "(?)" in label || "?" in label
        val clean = label.replace("(?)", "").replace("?", "").trim()
        val category = if (unsure || clean.isEmpty()) UNKNOWN
        else CATEGORY_WORDS.firstOrNull { (_, words) -> words.any { clean.lowercase().contains(it) } }?.first ?: UNKNOWN
        val reason = when {
            amount == null -> "нет суммы"
            clean.isEmpty() -> "подпись неразборчива"
            unsure -> "подпись под вопросом"
            category == UNKNOWN -> "категория не распознана"
            else -> ""
        }
        return Expense(month, index, raw, amount, clean, category, reason.isNotEmpty(), reason)
    }

    // ---------- Разбор файлов ----------

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
    private fun JsonElement?.num(): Double? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.let { it.doubleOrNull ?: it.contentOrNull?.replace(" ", "")?.replace(',', '.')?.toDoubleOrNull() }
    private fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.let { it.intOrNull ?: it.contentOrNull?.trim()?.toIntOrNull() }

    fun parse(text: String): Data {
        val t = text.trimStart('﻿', ' ', '\n', '\r', '\t')
        return if (t.startsWith("{")) parseJson(t) else parseCsv(t)
    }

    fun parseJson(text: String): Data {
        val root = json.parseToJsonElement(text) as JsonObject
        val year = root["year_assumption"].int() ?: 2026
        val currency = root["currency"].str() ?: "RUB"
        val incomes = mutableListOf<Income>()
        val off = mutableListOf<NoIncome>()
        val written = mutableListOf<Written>()
        val records = root["normalized_records"] as? JsonArray
        if (records != null) {
            records.forEach { e ->
                val o = e as? JsonObject ?: return@forEach
                val (m, d) = monthDay(o)
                val src = o["source"].str().orEmpty()
                val conf = o["confidence"].str().orEmpty()
                val desc = o["description"].str().orEmpty()
                when (o["type"].str()) {
                    "income" -> {
                        val a = o["amount_rub"].num()
                        if (a != null && m != null && d != null) incomes += Income(m, d, a, o["category"].str().orEmpty(), desc, src, conf)
                    }
                    "no_income_recorded" -> if (m != null && d != null) off += NoIncome(m, d, desc, src, conf)
                    "monthly_note" -> {
                        val a = o["amount_rub"].num()
                        if (a != null && m != null) written += Written(m, o["category"].str().orEmpty(), a, desc, src, conf)
                    }
                }
            }
        } else {
            (root["daily_income"] as? JsonArray)?.forEach { e ->
                val o = e as? JsonObject ?: return@forEach
                val (m, d) = monthDay(o)
                val a = o["amount_rub"].num()
                if (a != null && m != null && d != null) incomes += Income(m, d, a, o["category"].str().orEmpty(), "", "", "")
            }
        }
        val expenses = mutableListOf<Expense>()
        val uncertainty = mutableMapOf<Int, String>()
        (root["monthly_information"] as? JsonObject)?.forEach { (ym, v) ->
            val o = v as? JsonObject ?: return@forEach
            val m = ym.substringAfter('-').toIntOrNull() ?: return@forEach
            o["uncertainty"].str()?.let { uncertainty[m] = it }
            (o["raw_expense_notes"] as? JsonArray)?.forEachIndexed { i, n -> n.str()?.let { expenses += parseExpense(m, i, it) } }
            if (records == null) (o["written_totals"] as? JsonArray)?.forEach { w ->
                val wo = w as? JsonObject ?: return@forEach
                val a = wo["amount"].num() ?: return@forEach
                written += Written(m, wo["label"].str().orEmpty(), a, "", "", "")
            }
        }
        return Data(year, currency, incomes, off, written, expenses, uncertainty)
    }

    private fun monthDay(o: JsonObject): Pair<Int?, Int?> {
        val date = o["date"].str()
        if (date != null && date.length >= 10) return date.substring(5, 7).toIntOrNull() to date.substring(8, 10).toIntOrNull()
        return o["month"].int() to o["day"].int()
    }

    /** CSV из той же оцифровки: date,year,month,month_ru,day,type,amount_rub,category,description,source,confidence. */
    fun parseCsv(text: String): Data {
        val lines = text.lines().filter { it.isNotBlank() }
        val header = splitCsv(lines.first().trimStart('﻿')).map { it.trim() }
        fun col(row: List<String>, name: String) = header.indexOf(name).let { if (it >= 0) row.getOrNull(it)?.trim().orEmpty() else "" }
        val incomes = mutableListOf<Income>()
        val off = mutableListOf<NoIncome>()
        val written = mutableListOf<Written>()
        val expenses = mutableListOf<Expense>()
        var year = 2026
        lines.drop(1).forEach { line ->
            val r = splitCsv(line)
            col(r, "year").toIntOrNull()?.let { year = it }
            val date = col(r, "date")
            val m = if (date.length >= 10) date.substring(5, 7).toIntOrNull() else col(r, "month").toIntOrNull()
            val d = if (date.length >= 10) date.substring(8, 10).toIntOrNull() else col(r, "day").toIntOrNull()
            val a = col(r, "amount_rub").replace(" ", "").replace(',', '.').toDoubleOrNull()
            val src = col(r, "source")
            val conf = col(r, "confidence")
            val desc = col(r, "description")
            when (col(r, "type")) {
                "income" -> if (a != null && m != null && d != null) incomes += Income(m, d, a, col(r, "category"), desc, src, conf)
                "no_income_recorded" -> if (m != null && d != null) off += NoIncome(m, d, desc, src, conf)
                "monthly_note" -> if (a != null && m != null) written += Written(m, col(r, "category"), a, desc, src, conf)
                "expense" -> if (m != null) {
                    val raw = listOf(a?.let { Cooking.amount(it) }.orEmpty(), col(r, "category"), desc).filter { it.isNotBlank() }.joinToString(" ")
                    expenses += parseExpense(m, expenses.count { it.month == m }, raw)
                }
            }
        }
        return Data(year, "RUB", incomes, off, written, expenses, emptyMap())
    }

    private fun splitCsv(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var q = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && q && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> q = !q
                c == ',' && !q -> { out += sb.toString(); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
    }

    // ---------- Анализ ----------

    data class MonthSummary(
        val month: Int,
        val incomeSum: Double,
        val incomeDays: Int,
        val noIncomeDays: Int,
        val expenseSum: Double,
        val expenseCount: Int,
        val written: List<Written>,
    )

    data class Conflict(val month: Int, val label: String, val written: Double, val computed: Double, val what: String)

    data class Analysis(
        val months: List<MonthSummary>,
        val conflicts: List<Conflict>,
        val ambiguous: List<String>,
    )

    /** С какой вычисленной суммой сравнивать записанную: доходы месяца, расходы или ни с чем. */
    fun compareWith(label: String): String? {
        val l = label.lowercase()
        return when {
            "расход" in l -> "расходы"
            "остаток" in l || "перевод" in l -> null
            "итого" in l || "зарплат" in l || "сумма" in l || "итог" in l -> "доходы"
            else -> null
        }
    }

    fun analyze(d: Data): Analysis {
        val months = (d.incomes.map { it.month } + d.noIncome.map { it.month } + d.written.map { it.month } + d.expenses.map { it.month }).toSortedSet()
        val summaries = months.map { m ->
            MonthSummary(
                m,
                d.incomes.filter { it.month == m }.sumOf { it.amount },
                d.incomes.count { it.month == m },
                d.noIncome.count { it.month == m },
                d.expenses.filter { it.month == m }.sumOf { it.amount ?: 0.0 },
                d.expenses.count { it.month == m },
                d.written.filter { it.month == m },
            )
        }
        val conflicts = summaries.flatMap { s ->
            s.written.mapNotNull { w ->
                when (compareWith(w.label)) {
                    "доходы" -> if (w.amount != s.incomeSum) Conflict(s.month, w.label, w.amount, s.incomeSum, "сумма дневных доходов") else null
                    "расходы" -> if (w.amount != s.expenseSum) Conflict(s.month, w.label, w.amount, s.expenseSum, "сумма расходов из колонки") else null
                    else -> null
                }
            }
        }
        val ambiguous = mutableListOf<String>()
        d.expenses.forEach { e ->
            if (e.ambiguous) ambiguous += "${monthName(e.month)}: «${e.raw}» — ${e.reason}, категория «${e.category}»"
        }
        d.expenses.groupBy { it.month to it.raw.trim() }.filter { it.value.size > 1 }.forEach { (k, v) ->
            ambiguous += "${monthName(k.first)}: «${k.second}» встречается ${v.size} раза — возможен повтор при оцифровке, обе записи сохранены"
        }
        d.incomes.filter { it.confidence.equals("low", true) }.forEach {
            ambiguous += "${monthName(it.month)}, ${it.day}: доход ${Cooking.amount(it.amount)} — низкая уверенность распознавания"
        }
        d.incomes.groupBy { it.month to it.day }.filter { it.value.size > 1 }.forEach { (k, v) ->
            ambiguous += "${monthName(k.first)}, ${k.second}: несколько сумм за один день (${v.joinToString { Cooking.amount(it.amount) }})"
        }
        d.uncertainty.forEach { (m, text) -> ambiguous += "${monthName(m)}: $text" }
        conflicts.forEach {
            ambiguous += "${monthName(it.month)}: в тетради «${it.label}» ${Cooking.amount(it.written)}, а ${it.what} — ${Cooking.amount(it.computed)}. Сохранены оба значения"
        }
        return Analysis(summaries, conflicts, ambiguous)
    }
}
