package com.dasein.poryadok.logic

import java.time.LocalDate
import kotlin.math.abs

/**
 * Простая таблица расходов и доходов, которую удобно заполнять с телефона:
 * Дата · Расход · Доход · Примечание (и, по желанию, Категория).
 * Каждая строка становится настоящей операцией в свой день; категория угадывается по примечанию.
 */
object PaperTable {
    /** Одна операция из таблицы. [row] — номер строки в файле, для сообщений. */
    data class Row(val epochDay: Long, val amount: Double, val income: Boolean, val note: String, val category: String, val row: Int)

    private fun header(text: String): Pair<List<String>, Char>? {
        val first = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return null
        val sep = listOf(';', '\t', ',').maxBy { c -> first.count { it == c } }
        return Notebook.splitCsv(first.trimStart('﻿'), sep).map { it.trim().lowercase() } to sep
    }

    /** Таблица из шаблона или любая с колонками «Дата» и «Расход»/«Доход». */
    fun isTable(text: String): Boolean {
        val h = header(text)?.first ?: return false
        return h.any { it.startsWith("дата") } && h.any { it.startsWith("расход") || it.startsWith("доход") }
    }

    /** Сумма в любом написании: «1 500», «1500,50», «1 500 ₽», «300 р.». Минус не важен — колонка уже говорит, расход это или доход. */
    fun amountOf(raw: String): Double? {
        val t = raw.lowercase().replace(' ', ' ').replace(' ', ' ')
            .replace("руб", "").replace("₽", "").replace("р.", "").replace("р", "")
            .replace(" ", "").replace(',', '.').trim().trimEnd('.')
        if (t.isEmpty()) return null
        return t.toDoubleOrNull()?.let { abs(it) }?.takeIf { it > 0 }
    }

    private val ISO = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})$""")
    private val RU = Regex("""^(\d{1,2})[./-](\d{1,2})(?:[./-](\d{2,4}))?$""")

    /** Дата: 05.03.2026, 5.3.26, 05.03 (год — [defaultYear]), 2026-03-05, 05/03/2026 или число из Excel (46086). */
    fun dateOf(raw: String, defaultYear: Int): LocalDate? {
        val t = raw.trim().substringBefore(' ').substringBefore('T')
        if (t.isEmpty()) return null
        return runCatching {
            ISO.find(t)?.destructured?.let { (y, m, d) -> return LocalDate.of(y.toInt(), m.toInt(), d.toInt()) }
            RU.find(t)?.destructured?.let { (d, m, y) ->
                val year = when {
                    y.isEmpty() -> defaultYear
                    y.length == 2 -> 2000 + y.toInt()
                    else -> y.toInt()
                }
                return LocalDate.of(year, m.toInt(), d.toInt())
            }
            t.replace(',', '.').toDoubleOrNull()?.takeIf { it in 20_000.0..80_000.0 }?.let { return LocalDate.of(1899, 12, 30).plusDays(it.toLong()) }
            null
        }.getOrNull()
    }

    /** Слова примечания → категории приложения. Слово совпадает, если начинается с ключа: «газ» найдёт «газ», но не «магазин». */
    private val EXPENSE = listOf(
        "Продукты" to listOf("продукт", "фарш", "сало", "люля", "холодец", "креветк", "конфет", "капуст", "шоколад", "печень", "пельмен", "сосиск", "сметан", "йогурт", "мандарин", "яблок", "банан", "магазин", "пятероч", "магнит", "перекрест", "ашан", "лента", "дикси", "вкусвилл", "мясо", "куриц", "курочк", "колбас", "хлеб", "батон", "молок", "кефир", "творог", "сыр", "овощ", "фрукт", "картош", "рыб", "яйц", "масло", "крупа", "гречк", "рис", "сахар", "чай", "еда", "базар", "рынок", "рынке"),
        "Кафе и рестораны" to listOf("кафе", "ресторан", "столов", "кофе", "пицц", "шаурм", "суши", "бургер"),
        "Транспорт" to listOf("бенз", "заправ", "такси", "проезд", "метро", "автобус", "маршрут", "электрич", "парковк", "бензин"),
        "Жильё и ЖКХ" to listOf("квартплат", "жкх", "коммун", "за свет", "электроэнерг", "электричеств", "за газ", "газоснаб", "водоснаб", "за воду", "аренд", "квартир", "капремонт", "отоплен"),
        "Связь и интернет" to listOf("связь", "телефон", "интернет", "мтс", "билайн", "мегафон", "теле2", "tele2", "ростелеком", "мобильн"),
        "Здоровье" to listOf("аптек", "лекарств", "таблет", "врач", "анализ", "стоматолог", "зуб", "клиник", "больниц", "очки", "капли", "уколы"),
        "Одежда" to listOf("одежд", "обувь", "куртк", "сапог", "платье", "кофт", "брюк", "ботин", "носк", "колгот"),
        "Красота" to listOf("парикмах", "стрижк", "маникюр", "космет", "крем", "краска для волос"),
        "Переводы родным" to listOf("перевод"),
        "Подарки" to listOf("подар", "цвет", "день рожд", "юбилей"),
        "Развлечения" to listOf("кино", "театр", "концерт", "музей", "цирк"),
        "Образование" to listOf("книг", "курс", "школ", "учеб", "тетрад"),
        "Спорт" to listOf("спорт", "бассейн", "фитнес", "трениров"),
        "Подписки" to listOf("подписк"),
        "Путешествия" to listOf("отпуск", "гостиниц", "отель", "путевк", "санатор"),
    )
    private val INCOME = listOf(
        "Пенсия" to listOf("пенси"),
        "Зарплата" to listOf("зарплат", "зп", "аванс", "оклад", "преми", "получк"),
        "Подработка" to listOf("подработ", "халтур", "заказ"),
        "Подарок" to listOf("подар", "дали", "от детей", "от сына", "от дочер", "от внук"),
        "Кэшбэк" to listOf("кэшбэк", "кешбэк", "кешбек", "кэшбек"),
    )

    private fun norm(s: String) = s.lowercase().replace('ё', 'е')

    /** Категория по примечанию: «Продукты», «Пенсия»… или «Другое». */
    fun categoryOf(note: String, income: Boolean): String {
        val text = norm(note)
        val words = text.split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() }
        fun hit(key: String) = norm(key).let { k -> if (' ' in k) text.contains(k) else words.any { it.startsWith(k) } }
        return (if (income) INCOME else EXPENSE).firstOrNull { (_, keys) -> keys.any(::hit) }?.first ?: "Другое"
    }

    /**
     * Разбирает таблицу. В одной строке может быть и расход, и доход — получатся две операции.
     * Пустая дата — та же, что строкой выше (удобно записывать несколько трат за день). Пустые строки пропускаются.
     */
    fun parse(text: String, defaultYear: Int = LocalDate.now().year): Notebook.Data {
        val empty = Notebook.Data(defaultYear, "RUB", emptyList(), emptyList(), emptyList(), emptyList(), emptyMap())
        val (h, sep) = header(text) ?: return empty
        fun idx(vararg names: String) = h.indexOfFirst { c -> names.any { c.startsWith(it) } }
        val iDate = idx("дата")
        val iOut = idx("расход")
        val iIn = idx("доход")
        val iNote = idx("примеч", "на что", "комментар", "описан", "заметк", "что")
        val iCat = idx("категор")
        val rows = mutableListOf<Row>()
        val skipped = mutableListOf<String>()
        var last: LocalDate? = null
        val lines = text.lines()
        val start = lines.indexOfFirst { it.isNotBlank() } + 1
        for (n in start until lines.size) {
            val line = lines[n]
            if (line.isBlank()) continue
            val r = Notebook.splitCsv(line, sep)
            fun cell(i: Int) = if (i >= 0) r.getOrNull(i)?.trim().orEmpty() else ""
            val rowNo = n + 1
            // Строка «Итого» внизу таблицы — не операция.
            if (r.any { norm(it).trim().startsWith("итог") }) continue
            val out = amountOf(cell(iOut))
            val inc = amountOf(cell(iIn))
            if (out == null && inc == null) {
                val raw = (cell(iOut) + " " + cell(iIn)).trim()
                if (raw.isNotEmpty()) skipped += "строка $rowNo: сумма не распознана («$raw»)"
                continue
            }
            val rawDate = cell(iDate)
            val date = if (rawDate.isBlank()) last else dateOf(rawDate, defaultYear)
            if (date == null) {
                skipped += "строка $rowNo: " + if (rawDate.isBlank()) "нет даты" else "дата не распознана («$rawDate»)"
                continue
            }
            last = date
            val note = cell(iNote)
            val cat = cell(iCat)
            out?.let { rows += Row(date.toEpochDay(), it, false, note, cat.ifBlank { categoryOf(note, false) }, rowNo) }
            inc?.let { rows += Row(date.toEpochDay(), it, true, note, cat.ifBlank { categoryOf(note, true) }, rowNo) }
        }
        val year = rows.groupingBy { LocalDate.ofEpochDay(it.epochDay).year }.eachCount().maxByOrNull { it.value }?.key ?: defaultYear
        return empty.copy(year = year, dated = rows, skipped = skipped)
    }
}
