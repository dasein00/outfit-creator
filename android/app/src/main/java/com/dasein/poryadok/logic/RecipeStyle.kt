package com.dasein.poryadok.logic

/**
 * Единый стиль карточек блюд, как в кулинарном приложении: что бы ни прислал ИИ или встроенный разбор,
 * название, продукты, этапы, советы и теги приводятся к одному виду.
 *  • Название — с заглавной, без эмодзи, хештегов, кавычек и точки в конце.
 *  • Продукт — с заглавной, остальное строчными (кроме сокращений вроде «ПП»), без маркеров и двоеточий.
 *  • Этап — без «1.», «Шаг 2:» и эмодзи, с заглавной и точкой в конце; «180 градусов» → «180 °C», «15-20» → «15–20», « - » → « — ».
 *  • Советы и замены — пункты «• …» с точкой в конце. Теги — строчными, без «#».
 */
object RecipeStyle {
    /** Знаки, которые выглядят как эмодзи: пиктограммы, флаги, вариационные селекторы, соединители. */
    private val EMOJI = Regex("[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{2B00}-\\x{2BFF}\\x{FE0F}\\x{200D}\\x{20E3}\\x{1F1E6}-\\x{1F1FF}]")
    private val SPACES = Regex("[ \\t\\u00A0]+")
    private val NUMBERING = Regex("""^\s*(?:(?:шаг|этап)\s*\d+\s*[:.)—–-]?\s*|\d{1,2}\s*[.)]\s+|[•·\-–—*]+\s*)""", RegexOption.IGNORE_CASE)

    private fun base(s: String) = SPACES.replace(EMOJI.replace(s, ""), " ").trim()

    private fun capital(s: String) = s.replaceFirstChar { it.uppercaseChar() }

    /** Всё заглавными («ХАЧАПУРИ ПО-АДЖАРСКИ») — переводим в обычный регистр. */
    private fun unshout(s: String): String {
        val letters = s.filter { it.isLetter() }
        return if (letters.length > 3 && letters.all { it.isUpperCase() }) s.lowercase() else s
    }

    fun title(s: String): String {
        val t = base(s).replace(Regex("#\\S+"), "").replace(Regex("[«»\"“”]"), "").trim().trimEnd('.', '!', ':', ';', ',').trim()
        return capital(unshout(SPACES.replace(t, " ")))
    }

    fun ingredient(s: String): String {
        val t = base(s).replace(NUMBERING, "").trim().trimEnd('.', ':', ';', ',').trim()
        val words = unshout(t).split(' ')
        val out = words.mapIndexed { i, w ->
            val letters = w.filter { it.isLetter() }
            when {
                letters.length in 2..4 && letters.all { it.isUpperCase() } && i > 0 -> w
                else -> w.lowercase()
            }
        }.joinToString(" ")
        return capital(out)
    }

    private fun sentence(s: String): String {
        var t = base(s).replace(NUMBERING, "").trim()
        t = t.replace(Regex("""(\d+)\s*(?:градус\p{L}*|°\s*[CС]?)""", RegexOption.IGNORE_CASE), "$1 °C")
        t = t.replace(Regex("""(\d)\s*-\s*(\d)"""), "$1–$2")
        t = t.replace(" - ", " — ").replace(" – ", " — ")
        t = SPACES.replace(t, " ").trim()
        if (t.isEmpty()) return t
        t = capital(t)
        return if (t.last() in ".!?…)") t else "$t."
    }

    fun step(s: String): String = sentence(s)

    /** Советы или замены: каждая строка — «• Пункт.». */
    fun bullets(s: String): String = s.lines().map { sentence(it) }.filter { it.length > 1 }.joinToString("\n") { "• $it" }

    fun tag(s: String): String = base(s).removePrefix("#").trim().lowercase()

    /** Текст в основном не на русском (английский пост или речь) — его нужно перевести. */
    fun isForeign(text: String): Boolean {
        val lat = text.count { it in 'a'..'z' || it in 'A'..'Z' }
        val cyr = text.count { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }
        return lat >= 40 && lat > cyr * 1.5
    }

    private val JUNK_TITLE = Regex(
        "ингредиент|рецепт|итак|готовим|приготовлени|понадоб|нам нужно|состав|шаг(?![а-яё])|этап|подпис|сохрани|ingredient|recipe|how to|step",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Название — не название блюда: фраза из подписи («итак, ингредиенты», «рецепт дня»), пустое, слишком короткое
     * или длинное, обрывок с двоеточием, текст не по-русски. Такое название придумываем заново по самому рецепту.
     */
    fun isJunkTitle(t: String?): Boolean {
        val s = title(t.orEmpty())
        if (s.length < 3 || s.split(' ').size > 8 || s.endsWith(':') || t.orEmpty().trim().endsWith(':')) return true
        if (s.none { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' }) return true
        return JUNK_TITLE.containsMatchIn(s)
    }
}
