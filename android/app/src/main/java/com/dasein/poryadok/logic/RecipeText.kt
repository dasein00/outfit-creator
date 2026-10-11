package com.dasein.poryadok.logic

import kotlin.math.roundToInt

/**
 * Разбор рецепта из произвольного текста (подпись в Instagram, сообщение, заметка): название, порции,
 * ингредиенты с количеством и единицами, шаги приготовления с минутами. Всё потом можно поправить вручную.
 */
object RecipeText {
    data class Ingredient(val name: String, val amount: Double, val unit: String, val gramsPerUnit: Double = 0.0)
    data class Step(val text: String, val minutes: Int)
    data class Parsed(
        val title: String?,
        val servings: Int?,
        val ingredients: List<Ingredient>,
        val steps: List<Step>,
        val totalMinutes: Int?,
        val tags: List<String>,
    )

    /** Заголовок списка продуктов: «Ингредиенты (на 4 порции):», «Нам понадобится 👇». */
    private val ING_HEAD = Regex("""^(ингредиент|состав\b|продукты|нам (?:понадобится|потребуется|нужно)|понадобится|что нужно|что понадобится)""", RegexOption.IGNORE_CASE)
    private val STEP_HEAD = Regex("""^(приготовлени|способ приготовления|как (?:готовить|приготовить)|готовим\s*[:：]|рецепт\s*[:：]|шаги|пошагов|процесс)""", RegexOption.IGNORE_CASE)
    private fun isHead(re: Regex, line: String) = line.length < 50 && re.containsMatchIn(line) && ingredientOnly(line) == null
    private val HASHTAG = Regex("""#[\p{L}\p{N}_]+""")
    private val NUMBERING = Regex("""^(?:шаг\s*)?\d{1,2}\s*[.)]\s+""", RegexOption.IGNORE_CASE)
    private val LEADING_JUNK = Regex("""^[^\p{L}\p{N}½¼¾⅓⅔]+""")
    private val FRACTIONS = mapOf('½' to 0.5, '¼' to 0.25, '¾' to 0.75, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3)

    /** Единица: регулярка → (единица приложения, граммов в штуке для «шт»). Порядок важен: длинные раньше коротких. */
    private val UNITS: List<Pair<Regex, Pair<String, Double>>> = listOf(
        "килограмм\\p{L}*|кг" to ("кг" to 0.0),
        "миллилитр\\p{L}*|мл" to ("мл" to 0.0),
        "литр\\p{L}*|л" to ("л" to 0.0),
        "грамм\\p{L}*|гр\\.?|г\\.?" to ("г" to 0.0),
        "столов\\p{L}* ложк\\p{L}*|ст\\.?\\s?л\\.?|ст\\.?\\s?ложк\\p{L}*" to ("ст.л." to 0.0),
        "чайн\\p{L}* ложк\\p{L}*|ч\\.?\\s?л\\.?|ч\\.?\\s?ложк\\p{L}*" to ("ч.л." to 0.0),
        "стакан\\p{L}*|ст\\.(?!\\s?л)" to ("стакан" to 0.0),
        "штук\\p{L}*|шт\\.?" to ("шт" to 0.0),
        "зуб\\p{L}*" to ("шт" to 5.0),
        "пуч\\p{L}*" to ("шт" to 30.0),
        "банк\\p{L}*" to ("шт" to 400.0),
        "упаков\\p{L}*|пачк\\p{L}*|пачек" to ("шт" to 200.0),
        "ломтик\\p{L}*|кусоч\\p{L}*" to ("шт" to 20.0),
        "щепот\\p{L}*" to ("щепотка" to 0.0),
    ).map { (re, u) -> Regex("^(?:$re)(?![\\p{L}])", RegexOption.IGNORE_CASE) to u }

    private val NUM = """(\d+(?:[.,]\d+)?(?:\s*/\s*\d+)?|[½¼¾⅓⅔])(?:\s*[-–—]\s*(\d+(?:[.,]\d+)?))?"""
    private val QTY_FRONT = Regex("""^$NUM\s*""")
    private val QTY_ANY = Regex("""(?<![\p{L}\d])$NUM\s*""")
    private val TO_TASTE = Regex("""по вкусу|для подачи|по желанию|на кончике ножа""", RegexOption.IGNORE_CASE)
    private val SERVINGS = Regex("""(?:на\s+)?(\d{1,2})\s*(?:порц\p{L}*|персон\p{L}*|человек)|порци\p{L}*\s*[:\-–—]?\s*(\d{1,2})""", RegexOption.IGNORE_CASE)
    private val MINUTES = Regex("""(\d+(?:[.,]\d+)?)(?:\s*[-–—]\s*(\d+(?:[.,]\d+)?))?\s*(минут\p{L}*|мин\.?|час\p{L}*|ч\.)(?![\p{L}])""", RegexOption.IGNORE_CASE)
    private val TOTAL_TIME = Regex("""(?:время приготовления|время|готовится|готовим)\s*[:\-–—]?\s*""", RegexOption.IGNORE_CASE)

    private fun num(s: String): Double {
        val t = s.trim().replace(',', '.')
        if (t.length == 1 && t[0] in FRACTIONS) return FRACTIONS.getValue(t[0])
        if ('/' in t) { val (a, b) = t.split('/').map { it.trim().toDoubleOrNull() ?: 0.0 }; return if (b > 0) a / b else 0.0 }
        return t.toDoubleOrNull() ?: 0.0
    }

    private fun amountOf(m: MatchResult): Double {
        val a = num(m.groupValues[1])
        val b = m.groupValues[2].takeIf { it.isNotBlank() }?.let { num(it) }
        return if (b != null && b > a) (a + b) / 2 else a
    }

    /** Минуты в тексте шага: «20 минут», «1,5 часа», «10–15 мин» (берётся большее). */
    fun minutesIn(text: String): Int {
        val m = MINUTES.find(text) ?: return 0
        val a = num(m.groupValues[1]); val b = m.groupValues[2].takeIf { it.isNotBlank() }?.let { num(it) } ?: a
        val v = maxOf(a, b)
        return (if (m.groupValues[3].lowercase().startsWith("ч")) v * 60 else v).roundToInt()
    }

    private fun unitAt(s: String): Triple<String, Double, Int>? {
        for ((re, u) in UNITS) re.find(s)?.let { return Triple(u.first, u.second, it.value.length) }
        return null
    }

    private fun cleanName(s: String): String = s.replace(Regex("""\s+"""), " ").trim().trim('—', '–', '-', ':', ',', '.', ';', '*', '•', ' ')
        .replace(Regex("""^(?:и|из|с)\s+""", RegexOption.IGNORE_CASE), "")
        .replaceFirstChar { it.uppercase() }

    /** Похоже на действие, а не на продукт: «Разогреть духовку до 180 градусов», «Смешайте…». */
    private fun looksLikeAction(line: String): Boolean {
        val first = line.trim().substringBefore(' ').lowercase().trim(',', '.', ':')
        return "°" in line || Regex("""градус""", RegexOption.IGNORE_CASE).containsMatchIn(line) ||
            (first.length > 3 && (first.endsWith("ть") || first.endsWith("те") || first.endsWith("йте") || first.endsWith("ите")))
    }

    /** Строка ингредиента без проверки заголовков (для самих заголовков). */
    private fun ingredientOnly(line: String): List<Ingredient>? = if (line.trimEnd().endsWith(":")) null else ingredient(line)

    /** Строка ингредиента → один или несколько ингредиентов («Соль, перец — по вкусу» — два). null — это не ингредиент. */
    fun ingredient(line0: String): List<Ingredient>? {
        val line = line0.replace(NUMBERING, "").trim()
        if (line.isEmpty() || line.length > 90 || looksLikeAction(line)) return null
        if (TO_TASTE.containsMatchIn(line)) {
            val name = cleanName(line.replace(TO_TASTE, ""))
            if (name.isEmpty()) return null
            return name.split(',', '/').map { cleanName(it) }.filter { it.isNotEmpty() }.map { Ingredient(it, 0.0, "по вкусу") }
        }
        // «2 яйца», «200 г муки», «½ ч.л. соды»
        QTY_FRONT.find(line)?.let { m ->
            val rest = line.substring(m.range.last + 1)
            val u = unitAt(rest)
            val name = cleanName(if (u != null) rest.substring(u.third) else rest)
            if (name.isNotEmpty() && name.any { it.isLetter() }) return listOf(Ingredient(name, amountOf(m), u?.first ?: "шт", u?.second ?: 0.0))
        }
        // «Мука — 200 г», «Молоко 1 стакан», «Яйца: 2 шт», «Куриное филе 500гр»
        val m = QTY_ANY.findAll(line).lastOrNull { it.range.first > 0 } ?: return null
        val rest = line.substring(m.range.last + 1)
        val u = unitAt(rest)
        val tail = if (u != null) rest.substring(u.third) else rest
        // После количества не должно быть длинного текста — иначе это шаг («Варим 20 минут…»).
        if (tail.trim().length > 25) return null
        val name = cleanName(line.substring(0, m.range.first) + " " + tail.trim().trimStart('(').trimEnd(')'))
        if (name.isEmpty() || !name.any { it.isLetter() } || name.split(' ').size > 6) return null
        if (u == null && MINUTES.containsMatchIn(line)) return null
        return listOf(Ingredient(name, amountOf(m), u?.first ?: "шт", u?.second ?: 0.0))
    }

    /** Шаги, если все действия написаны одним абзацем: делим по предложениям, короткие склеиваем. */
    private fun splitParagraph(p: String): List<String> {
        val parts = p.split(Regex("""(?<=[.!?])\s+(?=[А-ЯЁA-Z])""")).map { it.trim() }.filter { it.isNotEmpty() }
        val out = ArrayList<String>()
        parts.forEach { s -> if (out.isNotEmpty() && (out.last().length < 50 || s.length < 25)) out[out.lastIndex] = out.last() + " " + s else out += s }
        return out
    }

    fun parse(text: String): Parsed {
        val tags = HASHTAG.findAll(text).map { it.value.removePrefix("#") }.toList()
        val lines = text.replace("\r", "").replace(HASHTAG, "").lines()
            .map { it.replace(LEADING_JUNK, "").replace(Regex("""\s+"""), " ").trim() }
            .filter { it.isNotEmpty() }
        var title: String? = null
        var servings: Int? = null
        var total: Int? = null
        val ings = ArrayList<Ingredient>()
        val steps = ArrayList<String>()
        var mode = 0 // 0 — до разделов, 1 — ингредиенты, 2 — шаги
        for (raw in lines) {
            val line = raw.trimEnd()
            SERVINGS.find(line)?.let { m -> if (servings == null) servings = (m.groupValues[1].ifBlank { m.groupValues[2] }).toIntOrNull() }
            if (TOTAL_TIME.containsMatchIn(line) && MINUTES.containsMatchIn(line) && line.length < 60) { total = minutesIn(line); continue }
            if (isHead(STEP_HEAD, line)) { mode = 2; continue }
            if (isHead(ING_HEAD, line)) { mode = 1; continue }
            // «Для теста:», «Соус:» — подзаголовки внутри ингредиентов.
            if (line.endsWith(":") && line.length < 30 && ingredient(line) == null) { if (mode == 0) mode = 1; continue }
            if (SERVINGS.matches(line)) continue
            when (mode) {
                2 -> steps += line.replace(NUMBERING, "")
                else -> {
                    val parsed = ingredient(line)
                    when {
                        parsed != null -> { ings += parsed; mode = 1 }
                        mode == 0 && title == null && line.length in 3..80 -> title = line.trimEnd(':', '.', '!')
                        line.length >= 25 || looksLikeAction(line) -> { steps += line.replace(NUMBERING, ""); if (mode == 1) mode = 2 }
                        mode == 0 && title == null -> title = line
                        // «Масло для жарки» — продукт без количества.
                        mode == 1 && line.any { it.isLetter() } -> ings += Ingredient(cleanName(line), 0.0, "по вкусу")
                    }
                }
            }
        }
        val stepTexts = steps.flatMap { if (it.length > 140 && steps.size <= 2) splitParagraph(it) else listOf(it) }
        return Parsed(
            title = title?.take(80),
            servings = servings,
            ingredients = ings,
            steps = stepTexts.map { Step(it, minutesIn(it)) },
            totalMinutes = total,
            tags = tags,
        )
    }

    /** Порции, если в тексте их нет: по весу блюда, тарелка ≈ 300 г. */
    fun guessServings(totalGrams: Double): Int = (totalGrams / 300.0).roundToInt().coerceIn(1, 12)
}
