package com.dasein.poryadok.data

/**
 * Подмена текстов интерфейса. Каталог — все надписи приложения из исходников: в шаблонах на месте чисел и названий,
 * которые приложение подставляет само, стоят {1}, {2}… Свои варианты берутся из правок пользователя, иначе — из
 * встроенного «окончательного варианта». Части составных фраз (начинаются с пробела, « · » и т. п.) заменяются
 * внутри строки, остальное — только при полном совпадении. Чистый Kotlin — проверяется unit-тестами.
 */
class UiTextEngine(val entries: List<Entry>, private val user: Map<String, String>) {
    data class Entry(
        val id: Int, val section: String, val ref: String, val fragment: Boolean,
        val orig: String, val suggestion: String, val final: String,
    ) {
        val hasSlots get() = SLOT.containsMatchIn(orig)
        /** Вариант по умолчанию: из документа, иначе — как в коде. */
        val default get() = final.ifEmpty { orig }
    }

    private class Rule(val regex: Regex, val chunk: String, val value: String)

    private val exact = HashMap<String, String>()
    private val templates = ArrayList<Rule>()
    private val fragments = ArrayList<Rule>()
    private val plainFragments = ArrayList<Pair<String, String>>()
    private val cache = HashMap<String, String>()

    init {
        val known = HashSet<String>()
        for (e in entries) {
            known += e.orig
            val v = effective(e)
            if (v == e.orig) continue
            when {
                e.fragment && !e.hasSlots -> plainFragments += e.orig to v
                e.fragment -> rule(e.orig, v)?.let { fragments += it }
                !e.hasSlots -> exact[e.orig] = v
                else -> rule(e.orig, v)?.let { templates += it }
            }
        }
        // Правки удержанием кнопки — по тексту, который был на экране.
        for ((k, v) in user) if (k !in known && v.isNotBlank() && v != k) exact[k] = v
        plainFragments.sortByDescending { it.first.length }
    }

    fun effective(e: Entry): String = user[e.orig]?.takeIf { it.isNotEmpty() } ?: e.default

    fun userValue(e: Entry): String? = user[e.orig]

    fun translate(s: String): String {
        if (s.isEmpty() || s.none { it.isLetter() }) return s
        exact[s]?.let { return it }
        cache[s]?.let { return it }
        var out: String? = null
        for (t in templates) {
            if (t.chunk.isNotEmpty() && !s.contains(t.chunk)) continue
            val m = t.regex.matchEntire(s) ?: continue
            out = fill(t.value, m.groupValues); break
        }
        if (out == null) {
            var r = s
            for ((from, to) in plainFragments) if (r.contains(from)) r = r.replace(from, to)
            for (f in fragments) if (f.chunk.isEmpty() || r.contains(f.chunk)) r = f.regex.replace(r) { fill(f.value, it.groupValues) }
            out = r
        }
        if (cache.size > 3000) cache.clear()
        cache[s] = out
        return out
    }

    companion object {
        val SLOT = Regex("""\{(\d+)\}""")

        private fun rule(orig: String, value: String): Rule? {
            val chunks = SLOT.split(orig)
            // Шаблон без слов вокруг подстановки («{1}») совпал бы с любым текстом.
            if (chunks.sumOf { c -> c.count { it.isLetter() } } < 2) return null
            val sb = StringBuilder()
            var last = 0
            for (m in SLOT.findAll(orig)) {
                sb.append(Regex.escape(orig.substring(last, m.range.first))).append("(.*?)")
                last = m.range.last + 1
            }
            sb.append(Regex.escape(orig.substring(last)))
            return Rule(Regex(sb.toString(), RegexOption.DOT_MATCHES_ALL), chunks.maxBy { it.length }, value)
        }

        /** {n} в варианте → n-я подстановка из текста на экране (номер по порядку в исходном шаблоне). */
        private fun fill(value: String, groups: List<String>): String =
            SLOT.replace(value) { m -> groups.getOrNull(m.groupValues[1].toInt()) ?: "" }

        /** Строка каталога: id, раздел, файл:строка, L/F, исходный, предложение, окончательный (\t и \n экранированы). */
        fun parse(lines: Sequence<String>): List<Entry> = lines.mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 7) return@mapNotNull null
            Entry(p[0].toIntOrNull() ?: return@mapNotNull null, p[1], p[2], p[3] == "F", unesc(p[4]), unesc(p[5]), unesc(p[6]))
        }.toList()

        private fun unesc(s: String): String {
            if ('\\' !in s) return s
            val sb = StringBuilder(s.length)
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    sb.append(when (s[i + 1]) { 'n' -> '\n'; 't' -> '\t'; else -> s[i + 1] }); i += 2
                } else { sb.append(c); i++ }
            }
            return sb.toString()
        }
    }
}
