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
        /** Остальное заполняет ИИ; без него — [RecipeClassify]. */
        val category: String? = null,
        val meals: List<Int>? = null,
        val difficulty: Int? = null,
        val prepMinutes: Int? = null,
        val tips: String? = null,
        val swaps: String? = null,
        /** 1–2 предложения о блюде (от ИИ) — вступление в описании карточки. */
        val about: String? = null,
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
            (first.length > 3 && (first.endsWith("ть") || first.endsWith("те") || first.endsWith("йте") || first.endsWith("ите"))) ||
            (first.length > 4 && (first.endsWith("ем") || first.endsWith("им") || first.endsWith("ём")) && line.trim().split(' ').size >= 3)
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
        // «Мука — 200 г», «Молоко 1 стакан», «Яйца: 2 шт», «Мука 150 г + 10–20 г на обвалку», «Сливки 20% — 200 мл».
        // Берём первое количество с единицей (проценты жирности — не количество); без единицы — первое число.
        val all = QTY_ANY.findAll(line).filter { it.range.first > 0 && line.getOrNull(it.range.last + 1) != '%' && line.getOrNull(it.range.last) != '%' }.toList()
        if (all.isEmpty()) return null
        val withUnit = all.firstOrNull { unitAt(line.substring(it.range.last + 1)) != null }
        val m = withUnit ?: all.first()
        val rest = line.substring(m.range.last + 1)
        val u = unitAt(rest)
        val before = line.substring(0, m.range.first)
        val tail = (if (u != null) rest.substring(u.third) else rest).trim()
        val nameRaw = if (before.any { it.isLetter() }) before else tail
        // Без единицы и с длинным хвостом — это скорее действие («Варим 20 минут…»).
        if (u == null && (tail.length > 25 || MINUTES.containsMatchIn(line))) return null
        val name = cleanName(nameRaw.substringBefore('(').trimEnd())
        if (name.isEmpty() || !name.any { it.isLetter() } || name.split(' ').size > 6) return null
        return listOf(Ingredient(name, amountOf(m), u?.first ?: "шт", u?.second ?: 0.0))
    }

    /** Обвязка экрана на скриншоте поста: строка состояния, счётчики, кнопки, комментарии. */
    private val OCR_JUNK = Regex(
        """^(\d{1,2}:\d{2}.*|.*\b(vpn|lte|4g|5g)\b.*|\d{1,3}\s*%|.*отмет\p{L}* «?нравится»?.*|.*\bнравится\b.*\d.*|посмотреть (все )?(\d+ )?коммент\p{L}*.*|""" +
            """добавьте комментарий.*|.*\bкомментари\p{L}*\s*$|подписаться|подписки|подписан\p{L}*|отправить|сохранить|поделиться|оригинальное аудио.*|""" +
            """(view|see) (all )?\d* ?comments?.*|liked by.*|\d+ (likes?|comments?)|follow(ing)?|more|less|see translation|\d+\s*(ч|мин|дн|нед|h|m|d|w)\.?)$""",
        RegexOption.IGNORE_CASE,
    )

    /** Текст со скриншотов поста (строки распознавания) → подпись без обвязки экрана и мусора Instagram. */
    fun cleanOcr(lines: List<String>): String = lines.map { it.trim() }
        .filter { it.length > 1 && !OCR_JUNK.matches(it) && !JUNK.matches(it) }
        .fold(mutableListOf<String>()) { acc, l -> if (acc.lastOrNull() != l) acc += l; acc }
        .joinToString("\n")

    /** Строка-мусор из Instagram: дата поста, «ещё», «меньше», «показать перевод». */
    private val JUNK = Regex("""^(\d{1,2}\s+(январ|феврал|март|апрел|ма[яй]|июн|июл|август|сентябр|октябр|ноябр|декабр)\p{L}*(\s+\d{4})?(\s*г\.?)?|меньше|ещё|еще|показать перевод|показать оригинал|перевод|нравится|ответить|подробнее|читать далее|… ?ещё|\.\.\. ?ещё)$""", RegexOption.IGNORE_CASE)

    private val SPEECH = setOf("очень", "рекомендую", "советую", "если", "можно", "нужно", "лучше", "это", "он", "она", "они", "я", "мы", "вы", "то", "что", "как", "чтобы", "вообще", "который", "которая")

    /** Продукт без количества: «Соль (при желании разрыхлитель)», «Сыр», «Масло для жарки». */
    private fun bareIngredient(line: String, bullet: Boolean): Ingredient? {
        // Продолжение предыдущей фразы («укроп/кинзу) то вообще сказка») — не продукт.
        if (!bullet && (line.first().isLowerCase() || ')' in line.substringBefore('('))) return null
        val raw = line.substringBefore('(').substringBefore(',').substringBefore(" — ").substringBefore(" - ").trim()
        val name = cleanName(raw)
        if (name.isEmpty() || !name.any { it.isLetter() }) return null
        // Слова обычной речи — это пояснение, а не продукт («Из сыров очень рекомендую взять…»).
        if (!bullet && raw.lowercase().split(' ').any { it in SPEECH }) return null
        val words = raw.split(' ').size
        return if ((bullet && words <= 6) || (words <= 5 && line.length <= 45)) Ingredient(name, 0.0, "по вкусу") else null
    }

    private fun isStepLike(line: String, numbered: Boolean): Boolean =
        numbered || looksLikeAction(line) || (line.length > 60 && line.split(' ').size > 8)

    /** Шаги, если все действия написаны одним абзацем: делим по предложениям, короткие склеиваем. */
    private fun splitParagraph(p: String): List<String> {
        val parts = p.split(Regex("""(?<=[.!?])\s+(?=[А-ЯЁA-Z])""")).map { it.trim() }.filter { it.isNotEmpty() }
        val out = ArrayList<String>()
        parts.forEach { s -> if (out.isNotEmpty() && (out.last().length < 50 || s.length < 25)) out[out.lastIndex] = out.last() + " " + s else out += s }
        return out
    }

    fun parse(text: String): Parsed {
        val tags = HASHTAG.findAll(text).map { it.value.removePrefix("#") }.toList()
        // Строки без маркеров списка; запоминаем, был ли маркер («-», «•», эмодзи) — это признак пункта списка.
        val lines = text.replace("\r", "").replace(HASHTAG, "").lines()
            .map { raw ->
                val t = raw.trim()
                val bullet = t.isNotEmpty() && !t[0].isLetterOrDigit() && t[0] !in "½¼¾⅓⅔"
                val numbered = NUMBERING.containsMatchIn(t)
                Triple(t.replace(LEADING_JUNK, "").replace(Regex("""\s+"""), " ").trim(), bullet, numbered)
            }
            .filter { it.first.isNotEmpty() && !JUNK.matches(it.first) }
        var title: String? = null
        var servings: Int? = null
        var total: Int? = null
        val ings = ArrayList<Ingredient>()
        val steps = ArrayList<String>()
        var sawStepHead = false
        var mode = 0 // 0 — до разделов (название, описание), 1 — ингредиенты, 2 — шаги
        for ((line, bullet, numbered) in lines) {
            SERVINGS.find(line)?.let { m -> if (servings == null) servings = (m.groupValues[1].ifBlank { m.groupValues[2] }).toIntOrNull() }
            if (TOTAL_TIME.containsMatchIn(line) && MINUTES.containsMatchIn(line) && line.length < 60) { total = minutesIn(line); continue }
            if (isHead(STEP_HEAD, line)) { mode = 2; sawStepHead = true; continue }
            if (isHead(ING_HEAD, line)) { mode = 1; continue }
            // «Для теста:», «Соус:» — подзаголовки; в шагах они остаются как есть.
            if (line.endsWith(":") && line.length < 35 && ingredient(line) == null) { if (mode == 0) mode = 1; continue }
            if (SERVINGS.matches(line)) continue
            val parsed = if (mode == 2 && sawStepHead) null else ingredient(line)
            when {
                parsed != null -> { ings += parsed; if (mode == 0) mode = 1 }
                mode == 2 -> steps += line.replace(NUMBERING, "")
                mode == 0 && title == null && line.length in 3..80 && !isStepLike(line, numbered) -> title = line.trimEnd(':', '.', '!')
                // До списка продуктов длинный текст — описание блюда, этапом он становится, только если это действие.
                if (mode == 0) numbered || looksLikeAction(line) else isStepLike(line, numbered) -> { steps += line.replace(NUMBERING, ""); if (mode == 1) mode = 2 }
                // В списке продуктов строка без количества — продукт «по вкусу»; длинный текст без маркера — пояснение, пропускаем.
                mode == 1 -> bareIngredient(line, bullet)?.let { ings += it }
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

    /**
     * Рецепт из двух источников без интернета: подпись к посту (обычно ингредиенты с граммовками) и речь из видео
     * (обычно — как готовить). Продукты — из поста, плюс те, что прозвучали только в речи; этапы — из поста,
     * а если их там нет — из речи по предложениям.
     */
    fun merge(post: String, speech: String): Parsed {
        val pp = parse(post)
        val sp = parse(speech)
        val have = pp.ingredients.flatMap { keyWords(it.name) }.toMutableSet()
        val extra = sp.ingredients.filter { ing ->
            val w = keyWords(ing.name)
            (w.isNotEmpty() && w.none { k -> have.any { h -> h.startsWith(k.take(maxOf(3, k.length - 1))) || k.startsWith(h.take(maxOf(3, h.length - 1))) } }).also { if (it) have += w }
        }
        val ings = if (pp.ingredients.isNotEmpty()) pp.ingredients + extra else sp.ingredients
        val steps = when {
            pp.steps.size >= 2 -> pp.steps
            sp.steps.size >= 2 -> sp.steps
            else -> splitParagraph(speech.replace(HASHTAG, "").replace(Regex("""\s+"""), " ").trim())
                .filter { it.length >= 20 }.map { Step(it, minutesIn(it)) }.ifEmpty { pp.steps + sp.steps }
        }
        return Parsed(
            title = pp.title ?: sp.title,
            servings = pp.servings ?: sp.servings,
            ingredients = ings,
            steps = steps,
            totalMinutes = pp.totalMinutes ?: sp.totalMinutes,
            tags = (pp.tags + sp.tags).distinct(),
        )
    }

    private val ADJ = Regex("""(ый|ий|ой|ая|яя|ое|ее|ые|ие)$""")

    /** Слова продукта, по которым его узнать в тексте: существительные («сулугуни», «масло», «лук»), без прилагательных. */
    fun keyWords(productName: String): List<String> =
        productName.lowercase().replace('ё', 'е').split(' ', ',', '(', ')', '/', '-').map { it.trim() }
            .filter { it.length >= 3 && it.all { c -> c.isLetter() } && !(it.length > 4 && ADJ.containsMatchIn(it)) }

    /** Встречается ли слово продукта в тексте (с учётом падежей: «сулугуни», «маслом», «луком»). */
    fun mentions(tokens: Set<String>, word: String): Boolean =
        if (word.length <= 3) word in tokens
        else { val stem = if (word.length >= 5) word.dropLast(1) else word; tokens.any { it.startsWith(stem) && it.length - word.length <= 3 } }

    fun tokens(text: String): Set<String> = text.lowercase().replace('ё', 'е').split(Regex("""[^\p{L}]+""")).filter { it.length >= 3 }.toSet()

    /** Порции, если в тексте их нет: по весу блюда, тарелка ≈ 300 г. */
    fun guessServings(totalGrams: Double): Int = (totalGrams / 300.0).roundToInt().coerceIn(1, 12)
}
