package com.dasein.poryadok.logic

/** Отрезок форматирования: [start, end) вместе с маркерами, marker — длина маркера с каждой стороны. */
data class MdSpan(val style: MdStyle, val start: Int, val end: Int, val marker: Int)

enum class MdStyle(val open: String, val close: String) {
    BOLD("**", "**"), STRIKE("~~", "~~"), HIGHLIGHT("==", "=="), CODE("`", "`"), ITALIC("*", "*"), UNDERLINE("__", "__")
}

/** Простая строчная разметка для заметок. Маркеры остаются в тексте, но показываются бледными — как в Notion/Obsidian. */
object InlineMarkdown {
    private val ORDER = listOf(MdStyle.BOLD, MdStyle.UNDERLINE, MdStyle.STRIKE, MdStyle.HIGHLIGHT, MdStyle.CODE, MdStyle.ITALIC)

    fun spans(text: String): List<MdSpan> {
        val out = mutableListOf<MdSpan>()
        val taken = BooleanArray(text.length)
        for (style in ORDER) {
            var i = 0
            while (i < text.length) {
                val s = text.indexOf(style.open, i)
                if (s < 0) break
                if (taken[s] || (style == MdStyle.ITALIC && isDouble(text, s, '*'))) { i = s + 1; continue }
                val from = s + style.open.length
                var e = text.indexOf(style.close, from)
                while (e >= 0 && style == MdStyle.ITALIC && isDouble(text, e, '*')) e = text.indexOf(style.close, e + 2)
                if (e < 0) break
                if (e == from || text.substring(from, e).contains('\n') || (s until e + style.close.length).any { taken[it] }) { i = s + 1; continue }
                val end = e + style.close.length
                out += MdSpan(style, s, end, style.open.length)
                // Код не содержит вложенной разметки, остальные стили могут вкладываться друг в друга.
                if (style == MdStyle.CODE) for (k in s until end) taken[k] = true
                else { for (k in 0 until style.open.length) { taken[s + k] = true; taken[e + k] = true } }
                i = end
            }
        }
        return out.sortedBy { it.start }
    }

    private fun isDouble(t: String, i: Int, c: Char) = (i + 1 < t.length && t[i + 1] == c) || (i > 0 && t[i - 1] == c)

    /** Текст без маркеров — для поиска, экспорта и заголовков. */
    fun plain(text: String): String {
        val sp = spans(text)
        if (sp.isEmpty()) return text
        val drop = BooleanArray(text.length)
        sp.forEach { s -> for (k in 0 until s.marker) { drop[s.start + k] = true; drop[s.end - 1 - k] = true } }
        return buildString { text.forEachIndexed { i, ch -> if (!drop[i]) append(ch) } }
    }

    /** Обернуть выделение маркерами стиля или снять их, если выделение уже обёрнуто. Возвращает текст и новое выделение. */
    fun toggle(text: String, selStart: Int, selEnd: Int, style: MdStyle): Triple<String, Int, Int> {
        val a = minOf(selStart, selEnd).coerceIn(0, text.length)
        val b = maxOf(selStart, selEnd).coerceIn(0, text.length)
        val o = style.open; val c = style.close
        if (a >= o.length && b + c.length <= text.length && text.substring(a - o.length, a) == o && text.substring(b, b + c.length) == c) {
            return Triple(text.substring(0, a - o.length) + text.substring(a, b) + text.substring(b + c.length), a - o.length, b - o.length)
        }
        return Triple(text.substring(0, a) + o + text.substring(a, b) + c + text.substring(b), a + o.length, b + o.length)
    }
}
