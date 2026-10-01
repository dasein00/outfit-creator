package com.dasein.poryadok.logic

/**
 * Поиск по словам и выражениям для больших баз (факты, истории, рецепты).
 *
 * Синтаксис запроса:
 *  - `луна марс` — должны встретиться оба слова (окончания не важны: «пчёлы» найдёт «пчела»);
 *  - `"чёрная дыра"` — точное выражение в кавычках;
 *  - `-кофе` — исключить записи с этим словом;
 *  - `кошки|собаки` или `кошки или собаки` — любое из слов.
 */
object TextQuery {
    data class Query(
        /** Группы слов: в записи должно найтись хотя бы одно слово из каждой группы. */
        val groups: List<List<String>> = emptyList(),
        val phrases: List<String> = emptyList(),
        val exclude: List<String> = emptyList(),
    ) {
        val isEmpty get() = groups.isEmpty() && phrases.isEmpty() && exclude.isEmpty()

        /** Что подсвечивать в найденном тексте. */
        val highlights get() = groups.flatten() + phrases
    }

    fun norm(s: String): String = s.lowercase().replace('ё', 'е')

    private val ENDINGS = listOf(
        "иями", "ями", "ами", "ого", "его", "ому", "ему", "ыми", "ими", "иях", "ях", "ах", "ов", "ев", "ей", "ой", "ий", "ый",
        "ая", "яя", "ое", "ее", "ые", "ие", "ую", "юю", "ом", "ем", "ам", "ям", "ию", "ия", "ть", "а", "я", "ы", "и", "у", "ю", "о", "е", "ь",
    )

    /** Грубая основа слова: отрезает типичное русское окончание, оставляя не меньше 3–4 букв. */
    fun stem(word: String): String {
        val w = norm(word)
        if (w.length <= 3) return w
        val end = ENDINGS.firstOrNull { w.endsWith(it) && w.length - it.length >= (if (it.length == 1) 3 else 4) } ?: return w
        return w.dropLast(end.length)
    }

    fun parse(raw: String): Query {
        val phrases = mutableListOf<String>()
        val rest = Regex("\"([^\"]*)\"?").replace(raw) { m ->
            norm(m.groupValues[1]).trim().takeIf { it.isNotEmpty() }?.let { phrases += it }
            " "
        }
        val tokens = rest.split(' ', '\t', '\n', ',', ';').map { it.trim() }.filter { it.isNotEmpty() }
        val groups = mutableListOf<MutableList<String>>()
        val exclude = mutableListOf<String>()
        var joinNext = false
        for (t in tokens) {
            val n = norm(t)
            when {
                n == "или" || n == "|" -> joinNext = groups.isNotEmpty()
                n.startsWith("-") && n.length > 1 -> exclude += stem(n.drop(1))
                else -> {
                    val alts = n.split('|').filter { it.isNotEmpty() }.map(::stem)
                    if (alts.isEmpty()) { joinNext = groups.isNotEmpty(); continue }
                    if (joinNext && groups.isNotEmpty()) groups.last() += alts else groups += alts.toMutableList()
                    joinNext = n.endsWith("|")
                }
            }
        }
        return Query(groups, phrases, exclude)
    }

    fun matches(text: String, q: Query): Boolean {
        if (q.isEmpty) return true
        val hay = norm(text)
        return q.groups.all { g -> g.any { it in hay } } && q.phrases.all { it in hay } && q.exclude.none { it in hay }
    }

    fun matches(text: String, raw: String): Boolean = matches(text, parse(raw))

    /** Релевантность: точные выражения и совпадения в заголовке важнее, чем в тексте. */
    fun score(title: String, text: String, q: Query): Int {
        val t = norm(title)
        val b = norm(text)
        var s = 0
        q.phrases.forEach { p -> if (p in t) s += 30; if (p in b) s += 10 }
        q.groups.flatten().forEach { w ->
            if (t.startsWith(w)) s += 12 else if (w in t) s += 8
            if (w in b) s += 2 + minOf(3, Regex(Regex.escape(w)).findAll(b).count())
        }
        return s
    }

    /** Диапазоны совпадений в исходном тексте — для подсветки. */
    fun ranges(text: String, q: Query): List<IntRange> {
        val hay = norm(text)
        val out = mutableListOf<IntRange>()
        q.highlights.filter { it.length >= 2 }.forEach { w ->
            var i = hay.indexOf(w)
            while (i >= 0) {
                // Подсвечиваем слово целиком до его конца, чтобы было видно окончание.
                var end = i + w.length
                if (' ' !in w) while (end < hay.length && hay[end].isLetter()) end++
                out += i until end
                i = hay.indexOf(w, i + w.length)
            }
        }
        return out.sortedBy { it.first }
    }
}

/** Разбивка длинного списка на страницы. */
object Paging {
    fun pages(total: Int, size: Int): Int = if (total <= 0) 1 else (total + size - 1) / size

    fun clamp(page: Int, total: Int, size: Int): Int = page.coerceIn(0, pages(total, size) - 1)

    fun <T> slice(list: List<T>, page: Int, size: Int): List<T> {
        val p = clamp(page, list.size, size)
        return list.subList(minOf(p * size, list.size), minOf((p + 1) * size, list.size))
    }

    /** Номера страниц для полосы: первая, последняя и соседи текущей; -1 — многоточие. */
    fun window(page: Int, pages: Int, around: Int = 1): List<Int> {
        if (pages <= 7) return (0 until pages).toList()
        val set = sortedSetOf(0, pages - 1)
        for (i in page - around..page + around) if (i in 0 until pages) set += i
        if (page <= 2) set += listOf(1, 2, 3)
        if (page >= pages - 3) set += listOf(pages - 4, pages - 3, pages - 2)
        val out = mutableListOf<Int>()
        var prev = -1
        set.filter { it in 0 until pages }.forEach { n -> if (prev >= 0 && n - prev > 1) out += -1; out += n; prev = n }
        return out
    }
}
