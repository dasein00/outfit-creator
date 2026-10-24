package com.dasein.poryadok.logic

/**
 * Распознавание текста на телефоне (ML Kit) знает только латиницу: русский текст со скриншота приходит
 * похожими латинскими буквами — «MHrpegveHTbI», «TBOPOr». Здесь такие слова переводятся обратно в кириллицу по
 * начертанию букв. Это запасной путь без интернета — с ИИ текст со скриншота читается целиком и точно.
 */
object CyrOcr {
    private val PAIRS = listOf("bI" to "ы", "bl" to "ы", "bi" to "ы", "bı" to "ы", "IO" to "ю", "Io" to "ю")
    private val MAP = mapOf(
        'A' to 'а', 'a' to 'а', 'B' to 'в', 'b' to 'ь', 'C' to 'с', 'c' to 'с', 'D' to 'д', 'd' to 'д', 'E' to 'е', 'e' to 'е',
        'F' to 'ф', 'G' to 'д', 'g' to 'д', 'H' to 'н', 'h' to 'н', 'I' to 'и', 'i' to 'и', 'ı' to 'и', 'l' to 'л', 'K' to 'к', 'k' to 'к',
        'M' to 'м', 'm' to 'м', 'N' to 'п', 'n' to 'п', 'O' to 'о', 'o' to 'о', 'P' to 'р', 'p' to 'р', 'R' to 'я', 'r' to 'г',
        'S' to 'с', 's' to 'с', 'T' to 'т', 't' to 'т', 'U' to 'и', 'u' to 'и', 'V' to 'и', 'v' to 'и', 'W' to 'ш', 'w' to 'ш',
        'X' to 'х', 'x' to 'х', 'Y' to 'у', 'y' to 'у', 'Z' to 'з', 'z' to 'з', 'Ő' to 'б', 'ő' to 'б', 'Ö' to 'б', 'ö' to 'б',
        'J' to 'у', 'j' to 'у', 'Q' to 'о', 'q' to 'д', 'f' to 'ф', '@' to 'а',
    )
    private val DIGITS = mapOf('3' to 'з', '4' to 'ч', '6' to 'б', '0' to 'о')
    private val WORD = Regex("""[\p{L}\d@]+""")

    private fun isLatin(c: Char) = c in 'a'..'z' || c in 'A'..'Z' || c in "ŐőÖöı"
    private fun isCyr(c: Char) = c in 'а'..'я' || c in 'А'..'Я' || c == 'ё' || c == 'Ё'

    /** Слово похоже на русское, прочитанное латиницей: заглавные посреди строчных, «bI» на конце, смесь с кириллицей. */
    private fun telltale(w: String): Boolean {
        if (w.none(::isLatin)) return false
        if (w.any(::isCyr) || w.any { it in "ŐőÖöı" }) return true
        if (w.endsWith("bI") || w.endsWith("bl")) return true
        val letters = w.filter { it.isLetter() }
        if (letters.drop(1).any { it.isUpperCase() } && letters.any { it.isLowerCase() }) return true
        return w.length > 2 && w.indices.drop(1).dropLast(1).any { w[it].isDigit() && w[it - 1].isLetter() && w[it + 1].isLetter() }
    }

    /** Весь текст — русский, прочитанный латиницей. */
    fun looksGarbled(text: String): Boolean {
        val words = WORD.findAll(text).map { it.value }.filter { w -> w.count(::isLatin) >= 2 }.toList()
        if (words.isEmpty()) return false
        val tell = words.count(::telltale)
        val cyr = text.count(::isCyr); val lat = text.count(::isLatin)
        return tell >= 2 && tell * 4 >= words.size && cyr < lat
    }

    private fun convert(w: String): String {
        val letters = w.count { it.isLetter() }
        val digitsToo = letters > w.count { it.isDigit() }
        var s = w
        PAIRS.forEach { (a, b) -> s = s.replace(a, b) }
        val out = StringBuilder()
        s.forEachIndexed { i, c ->
            out.append(
                when {
                    isCyr(c) -> c.lowercaseChar()
                    c.isDigit() -> if (digitsToo && (i > 0 && s[i - 1].isLetter() || i + 1 < s.length && s[i + 1].isLetter())) DIGITS[c] ?: c else c
                    else -> MAP[c] ?: c
                },
            )
        }
        return out.toString()
    }

    /** Перевести латинские «двойники» в кириллицу. Обычный английский текст не трогается. */
    fun fix(text: String): String {
        val all = looksGarbled(text)
        return text.lines().joinToString("\n") { line ->
            var first = true
            WORD.replace(line) { m ->
                val w = m.value
                val res = if ((all && w.any(::isLatin)) || (telltale(w) && w.any(::isCyr))) convert(w) else w
                (if (first && res != w) res.replaceFirstChar { it.uppercaseChar() } else res).also { first = false }
            }
        }
    }
}
