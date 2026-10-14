package com.dasein.poryadok.logic

import java.time.LocalDate

/**
 * Выписка из банковского приложения → операции. Понимает:
 *  - таблицы (CSV и Excel): Т-Банк, Альфа, ВТБ, Озон и другие — ищет строку заголовков с «Дата» и «Сумма»/«Приход»/«Расход»;
 *  - текст PDF-выписки (СберБанк Онлайн и похожие): строки, начинающиеся с даты, с суммой «1 234,56» или «+5 000,00».
 */
object Statement {
    /**
     * Операция выписки. [balance] — остаток после неё (по нему и [ref] операция узнаётся при повторной загрузке),
     * [ref] — номер документа («1234567890-37»), [transfer] — перевод между своими счетами, [peer] — последние цифры второго счёта.
     */
    data class Op(
        val day: Long, val amount: Double, val income: Boolean, val text: String, val category: String? = null,
        val balance: Double? = null, val ref: String = "", val transfer: Boolean = false, val peer: String = "",
    )

    /** Шапка выписки: название и номер счёта, ставка, остаток на конец периода. */
    data class Header(val title: String = "", val number: String = "", val rate: Double? = null, val closingDay: Long? = null, val closing: Double? = null) {
        val savings get() = listOf("накопит", "вклад", "сберегат", "копилк").any { it in title.lowercase() }
        /** Выписка по карте (или кусок без шапки) — её операции идут на счёт «Сбер», куда пишутся SMS и пуши. */
        val card get() = !savings && (title.isBlank() || "карт" in title.lowercase())
    }

    private val REF = Regex("""(?<!\d)\d{2},\s*№\s*([\d-]{5,})""")
    private val PEER = Regex("""(?:•{2,}|\*{2,})\s?(\d{4})""")
    private val TRANSFER_WORDS = listOf(
        "между счетами одного клиента", "между своими счетами", "перевод между своими", "с вклада на карту", "с карты на вклад",
        "karta-vklad", "vklad-karta", "karta-schet", "schet-karta",
    )

    /** Категории из колонки «Категория» выписки Сбера → категории приложения (недостающие создадутся при импорте). */
    private val SBER_CATEGORIES = mapOf(
        "супермаркеты" to "Продукты", "рестораны и кафе" to "Кафе и рестораны", "здоровье и красота" to "Здоровье",
        "одежда и аксессуары" to "Одежда", "транспорт" to "Транспорт", "отдых и развлечения" to "Развлечения",
        "коммунальные платежи" to "Жильё и ЖКХ", "все для дома" to "Дом", "выдача наличных" to "Наличные",
        "перевод сбп" to "Переводы", "перевод с карты" to "Переводы", "оплата по qr" to "Покупки по QR",
        "автомобиль" to "Транспорт", "образование" to "Образование", "путешествия" to "Путешествия",
    )
    private val SBER_INCOME = mapOf(
        "перевод на карту" to "Переводы от людей", "возврат" to "Возврат", "внесение наличных" to "Наличные",
        "перевод сбп" to "Переводы от людей",
    )

    /** Категория по колонке «Категория» Сбера (текст до описания магазина). */
    fun sberCategory(label: String, income: Boolean): String? {
        val l = label.lowercase()
        return (if (income) SBER_INCOME else SBER_CATEGORIES).entries.firstOrNull { l.startsWith(it.key) }?.value
    }

    /** Колонтитулы и шапки страниц PDF — не часть описания операции. */
    private val PAGE_NOISE = listOf(
        "продолжение на следующей странице", "для проверки подлинности", "выписка по сч", "дата операции (мск)", "страница ",
        "действителен", "дата обработки", "расшифровка операций", "дата формирования",
    )
    private val DETAIL = Regex("""^\S+\s+\d{6}(\s|$)""")
    private val FEE = Regex("""В сумму операции включена комиссия\s*[\d\s,.]+\s*(руб\.?|₽)?""", RegexOption.IGNORE_CASE)

    private val CARD_TAIL = Regex("""\.?\s*Операция по\s+(?:карте|сч[её]ту)\s*\*+\d{4}""", RegexOption.IGNORE_CASE)

    /** Вторая строка Сбера: «875330 PYATEROCHKA 23349 ZERNOGRAD RUS. Операция по карте ****3239» → «PYATEROCHKA 23349 ZERNOGRAD». */
    fun cleanMerchant(s: String): String = CARD_TAIL.replace(FEE.replace(s, ""), "").replace(Regex("\\s+"), " ").trim()
        .replace(Regex("""^\d{6}\s+"""), "").replace(Regex("""\s+RUS\.?$"""), "").trim(' ', '.', ',')

    fun header(text: String): Header {
        val t = text.replace('\u00a0', ' ')
        val title = Regex("""Выписка по сч[её]ту\s*«([^»]+)»""").find(t)?.groupValues?.get(1)
            ?: Regex("""Выписка по сч[её]ту ([а-яё ]{3,40})""", RegexOption.IGNORE_CASE).find(t)?.groupValues?.get(1)?.trim().orEmpty()
        val number = Regex("""Номер сч[её]та\s+([\d ]{20,40})""").find(t)?.groupValues?.get(1)?.filter(Char::isDigit)?.take(20).orEmpty()
        val rate = Regex("""(\d+(?:[.,]\d+)?)\s*%\s*годовых""").find(t)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
        val closing = Regex("""Остаток на (\d{2}\.\d{2}\.\d{4})\s+([+\-]?\d[\d ]*,\d{2})""").findAll(t)
            .mapNotNull { m -> parseDate(m.groupValues[1])?.let { d -> parseNumber(m.groupValues[2])?.let { d to it } } }.maxByOrNull { it.first }
        return Header(title, number, rate, closing?.first, closing?.second)
    }

    private val DATE = Regex("""(?<!\d)(\d{2})[./](\d{2})[./](\d{4}|\d{2})(?!\d)|(?<!\d)(\d{4})-(\d{2})-(\d{2})(?!\d)""")
    /** Сумма с копейками: «1 234,56», «-350.00», «+5 000,00 ₽». */
    private val MONEY = Regex("""(?<![\d.,])([+\-\u2212\u2013]?)\s?(\d{1,3}(?:[ \u00a0\u202f\u2009]\d{3})+|\d+)[.,](\d{2})(?!\d|[.,]\d)""")
    private val TIME = Regex("""\b\d{1,2}:\d{2}(:\d{2})?\b""")
    private val INCOME_WORDS = listOf(
        "зачислен", "поступлен", "перевод от", "входящий перевод", "зарплат", "заработная плата", "аванс", "возврат", "кэшбэк", "кешбэк",
        "cashback", "пополнение", "проценты", "капитализац", "refund", "внесение наличных",
    )
    private val SKIP_WORDS = listOf("остаток", "итого", "баланс на", "входящий остаток", "исходящий остаток", "всего ")
    private val FAILED = listOf("failed", "отклон", "отменен", "отменён", "declined")

    fun parseDate(s: String): Long? {
        // Excel хранит дату числом дней от 30.12.1899.
        s.trim().toDoubleOrNull()?.let { n -> return if (n in 30000.0..70000.0) LocalDate.of(1899, 12, 30).plusDays(n.toLong()).toEpochDay() else null }
        val m = DATE.find(s) ?: return null
        return runCatching {
            if (m.groupValues[1].isNotEmpty()) {
                val y = m.groupValues[3].toInt().let { if (it < 100) 2000 + it else it }
                LocalDate.of(y, m.groupValues[2].toInt(), m.groupValues[1].toInt()).toEpochDay()
            } else LocalDate.of(m.groupValues[4].toInt(), m.groupValues[5].toInt(), m.groupValues[6].toInt()).toEpochDay()
        }.getOrNull()
    }

    /** Число из ячейки: «-1 234,56», «1234.5», «−350 ₽». */
    fun parseNumber(s: String): Double? {
        val t = s.replace(' ', ' ').replace(' ', ' ').replace(' ', ' ').replace('−', '-').replace('–', '-')
            .replace(Regex("[^0-9,.+\\- ]"), "").replace(" ", "").trim()
        if (t.isEmpty() || t.none { it.isDigit() }) return null
        // «1.234,56» → «1234.56»; «1,234.56» → «1234.56»; «1234,56» → «1234.56».
        val norm = when {
            ',' in t && '.' in t -> if (t.lastIndexOf(',') > t.lastIndexOf('.')) t.replace(".", "").replace(',', '.') else t.replace(",", "")
            else -> t.replace(',', '.')
        }
        return norm.toDoubleOrNull()
    }

    private fun looksIncome(text: String): Boolean {
        val l = text.lowercase()
        return INCOME_WORDS.any { it in l }
    }

    // ---------- Таблицы ----------

    fun fromRows(rows: List<List<String>>, guess: (String, Boolean) -> String? = { _, _ -> null }): List<Op> {
        val clean = rows.map { r -> r.map { it.trim().trim('"').trim() } }
        val hi = clean.take(40).indexOfFirst { r ->
            val l = r.map { it.lowercase() }
            l.any { "дата" in it || it == "date" } && l.any { c -> listOf("сумма", "приход", "расход", "дебет", "кредит", "amount", "поступлен", "списан").any { it in c } }
        }
        if (hi < 0) return fromText(clean.joinToString("\n") { it.joinToString(" ") }, guess)
        val h = clean[hi].map { it.lowercase() }
        fun col(vararg keys: String, not: List<String> = emptyList()) =
            keys.firstNotNullOfOrNull { k -> h.indexOfFirst { c -> k in c && not.none { it in c } }.takeIf { it >= 0 } } ?: -1
        val dateCol = col("дата операции", "дата проводки", "дата", "date", not = listOf("рожд"))
        val amountCol = col("сумма платежа", "сумма в валюте счета", "сумма в валюте счёта", "сумма операции", "сумма", "amount")
        val inCol = col("приход", "поступлен", "зачислен", "кредит")
        val outCol = col("расход", "списан", "дебет")
        val typeCol = col("тип операции", "тип", "направление")
        val statusCol = col("статус")
        val catCol = col("категория")
        val descCols = h.indices.filter { i -> listOf("описание", "назначение", "контрагент", "получатель", "плательщик", "комментар", "место", "мерчант", "наименование", "description").any { it in h[i] } }
        val body = clean.drop(hi + 1)
        val anyNegative = amountCol >= 0 && body.any { r -> (r.getOrNull(amountCol)?.let(::parseNumber) ?: 0.0) < 0 }
        return body.mapNotNull { r ->
            val day = r.getOrNull(dateCol)?.let(::parseDate) ?: return@mapNotNull null
            if (statusCol >= 0 && FAILED.any { it in r.getOrNull(statusCol).orEmpty().lowercase() }) return@mapNotNull null
            val text = (descCols.mapNotNull { r.getOrNull(it)?.takeIf { s -> s.isNotBlank() } }.distinct().joinToString(" · "))
                .ifBlank { r.filterIndexed { i, _ -> i != dateCol && i != amountCol }.filter { it.any(Char::isLetter) }.joinToString(" ") }
            val cat = r.getOrNull(catCol)?.takeIf { catCol >= 0 && it.isNotBlank() }
            val type = r.getOrNull(typeCol).orEmpty().lowercase()
            val inV = r.getOrNull(inCol)?.let(::parseNumber)?.takeIf { inCol >= 0 && it != 0.0 }
            val outV = r.getOrNull(outCol)?.let(::parseNumber)?.takeIf { outCol >= 0 && it != 0.0 }
            val (amount, income) = when {
                inCol >= 0 && outCol >= 0 && inCol != outCol && (inV != null || outV != null) ->
                    if (inV != null) kotlin.math.abs(inV) to true else kotlin.math.abs(outV!!) to false
                amountCol >= 0 -> {
                    val v = r.getOrNull(amountCol)?.let(::parseNumber) ?: return@mapNotNull null
                    val inc = when {
                        v < 0 -> false
                        "списан" in type || "расход" in type || "дебет" in type -> false
                        "зачисл" in type || "приход" in type || "пополн" in type || "кредит" in type -> true
                        anyNegative -> true
                        else -> looksIncome("$text $cat")
                    }
                    kotlin.math.abs(v) to inc
                }
                else -> return@mapNotNull null
            }
            if (amount <= 0.0) return@mapNotNull null
            if (SKIP_WORDS.any { it in text.lowercase() } && text.length < 40) return@mapNotNull null
            Op(day, amount, income, text.take(120), guess("$text ${cat.orEmpty()}", income) ?: cat)
        }
    }

    // ---------- Текст (PDF) ----------

    fun fromText(text: String, guess: (String, Boolean) -> String? = { _, _ -> null }): List<Op> {
        val ops = mutableListOf<Op>()
        // Запись начинается со строки, где в начале стоит дата; строки без даты продолжают предыдущую.
        val records = mutableListOf<StringBuilder>()
        text.lines().map { it.replace(' ', ' ').trim() }.filter { it.isNotEmpty() }.forEach { line ->
            if (DATE.find(line)?.range?.first == 0) records += StringBuilder(line) else records.lastOrNull()?.append(' ')?.append(line)
        }
        records.forEach { sb ->
            val rec = sb.toString()
            val low = rec.lowercase()
            val day = parseDate(rec) ?: return@forEach
            if (SKIP_WORDS.any { low.startsWith(it) || " $it" in low.take(40) }) return@forEach
            // Даты и время убираем, чтобы «03.10.2026» и «14:22» не приняли за сумму.
            var body = TIME.replace(DATE.replace(rec, " "), " ").replace(Regex("\\s+"), " ").trim()
            // «Остаток на …», «Итого» после операции — не операция: всё, что дальше, отбрасываем.
            val lowBody = body.lowercase()
            val cut = (SKIP_WORDS + PAGE_NOISE).map { lowBody.indexOf(it) }.filter { it >= 0 }.minOrNull()
            if (cut != null) body = body.substring(0, cut).trim()
            // Вторая строка операции Сбера («дата обработки» + код авторизации) — только описание, сумм в ней нет
            // (а «В сумму операции включена комиссия 50,00 руб.» — уже внутри суммы).
            val money = if (DETAIL.containsMatchIn(rec)) null else MONEY.find(body)
            if (money == null) {
                // Вторая строка Сбера («дата обработки» и описание) — подпись к предыдущей операции.
                ops.lastOrNull()?.let { last ->
                    if (body.isNotEmpty() && last.text.length < 100) {
                        val merchant = cleanMerchant(body)
                        val all = "${last.text} $body".lowercase()
                        // Карта Сбера: в первой строке категория банка, во второй — магазин или получатель.
                        val label = last.text
                        val t = (if (merchant.isNotEmpty()) merchant else label).take(120)
                        val transfer = last.transfer || TRANSFER_WORDS.any { it in all }
                        ops[ops.lastIndex] = last.copy(
                            text = if (merchant.isNotEmpty() && sberCategory(label, last.income) == null && label.length > 2 && !label.equals(merchant, true)) "$label: $merchant".take(120) else t,
                            category = guess("$merchant $label", last.income) ?: sberCategory(label, last.income) ?: last.category,
                            transfer = transfer,
                        )
                    }
                }
                return@forEach
            }
            val amount = (money.groupValues[2].replace(Regex("\\D"), "") + "." + money.groupValues[3]).toDoubleOrNull() ?: return@forEach
            if (amount <= 0.0) return@forEach
            val sign = money.groupValues[1]
            val ref = REF.find(body)?.groupValues?.get(1).orEmpty()
            var desc = REF.replace(body.substring(0, money.range.first), " ")
                .replace(Regex("\\b\\d{6,}\\b"), "").replace(Regex("\\s+"), " ").trim(' ', '-', '\u00b7')
            val income = when (sign) {
                "+" -> true
                "-", "\u2212", "\u2013" -> false
                else -> looksIncome(rec)
            }
            // Остаток после операции — следующая сумма сразу за суммой операции.
            val balance = MONEY.find(body, money.range.last + 1)?.takeIf { it.range.first - money.range.last < 40 }
                ?.let { (it.groupValues[2].replace(Regex("\\D"), "") + "." + it.groupValues[3]).toDoubleOrNull()?.let { v -> if (it.groupValues[1] == "-") -v else v } }
            val lowRec = rec.lowercase()
            val transfer = TRANSFER_WORDS.any { it in lowRec }
            val peer = PEER.find(rec)?.groupValues?.get(1).orEmpty()
            // Короткое «Списание» / «Зачисление» дополняем сутью из второй колонки.
            if (desc.length < 25) {
                val extra = Regex("""(?:Наименование услуги|Назначение платежа|Получатель|Плательщик):\s*([^:]{3,60}?)(?=\s+(?:Лицевой|Счет|Счёт|Наименование|Назначение|Получатель|Плательщик|к/с|Тип операции)|$)""")
                    .find(rec)?.groupValues?.get(1)?.trim()
                if (transfer) desc = "$desc: перевод между своими счетами" + if (peer.isNotEmpty()) " (•••• $peer)" else ""
                else if (!extra.isNullOrBlank()) desc = "$desc: $extra"
            }
            ops += Op(day, amount, income, desc.ifBlank { rec.take(80) }.take(120), guess(rec, income), balance, ref, transfer, peer)
        }
        return ops
    }

    /** CSV с разделителем «;», «,» или табуляцией и кавычками. */
    fun parseCsv(text: String): List<List<String>> {
        val lines = text.removePrefix("﻿").lines().filter { it.isNotBlank() }
        val sample = lines.take(10).joinToString("\n")
        val sep = listOf(';', '\t', ',').maxByOrNull { c -> sample.count { it == c } } ?: ';'
        return lines.map { line ->
            val out = mutableListOf<String>()
            val cur = StringBuilder()
            var q = false
            var i = 0
            while (i < line.length) {
                val c = line[i]
                when {
                    c == '"' && q && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
                    c == '"' -> q = !q
                    c == sep && !q -> { out += cur.toString(); cur.clear() }
                    else -> cur.append(c)
                }
                i++
            }
            out += cur.toString()
            out
        }
    }

    /** Похожа ли операция на уже записанную: та же сумма и тип, день ±1. */
    fun isDuplicate(op: Op, existing: List<Triple<Long, Double, Boolean>>): Boolean =
        existing.any { (d, a, inc) -> inc == op.income && kotlin.math.abs(a - op.amount) < 0.005 && kotlin.math.abs(d - op.day) <= 1 }
}
