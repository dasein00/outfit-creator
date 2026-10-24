package com.dasein.poryadok.logic

/**
 * Ссылки и теги в тексте заметок (как в Notion/Obsidian):
 *  • [[Название]] или [[тип:Название]] — ссылка на заметку, рецепт, книгу в читалке, фильм/сериал/книгу из коллекции;
 *  • http(s)://… — веб-ссылка;
 *  • #тег — тег страницы (по тегам собирается указатель в списке заметок).
 */
object NoteRefs {
    enum class Kind { LINK, URL, TAG }
    enum class Target(val word: String) { ANY(""), PAGE("заметка"), RECIPE("рецепт"), BOOK("книга"), MEDIA("фильм") }

    /** [start, end) — весь фрагмент с маркерами; [nameStart, nameEnd) — видимое название. */
    data class Ref(val kind: Kind, val start: Int, val end: Int, val nameStart: Int, val nameEnd: Int, val target: Target, val name: String)

    private val LINK = Regex("""\[\[([^\[\]\n]{1,120})]]""")
    private val URL = Regex("""https?://[^\s\[\]()<>«»"]+""")
    private val HEX = Regex("""[0-9a-fA-F]{3}|[0-9a-fA-F]{6}""")
    private val TAG = Regex("""(?<![\p{L}\d_/#&])#([\p{L}\d_]{2,40})""")

    private val WORDS = mapOf(
        "заметка" to Target.PAGE, "страница" to Target.PAGE, "рецепт" to Target.RECIPE, "блюдо" to Target.RECIPE,
        "книга" to Target.BOOK, "фильм" to Target.MEDIA, "сериал" to Target.MEDIA, "медиа" to Target.MEDIA,
    )

    fun refs(text: String): List<Ref> {
        val out = mutableListOf<Ref>()
        LINK.findAll(text).forEach { m ->
            val inner = m.groupValues[1]
            val colon = inner.indexOf(':')
            val word = if (colon > 0) inner.substring(0, colon).trim().lowercase() else ""
            val target = WORDS[word]
            val nameOff = if (target != null) colon + 1 else 0
            val raw = inner.substring(nameOff)
            val lead = raw.length - raw.trimStart().length
            val name = raw.trim()
            if (name.isEmpty()) return@forEach
            val ns = m.range.first + 2 + nameOff + lead
            out += Ref(Kind.LINK, m.range.first, m.range.last + 1, ns, ns + name.length, target ?: Target.ANY, name)
        }
        fun free(s: Int, e: Int) = out.none { s < it.end && e > it.start }
        URL.findAll(text).forEach { m ->
            val url = m.value.trimEnd('.', ',', ';', ':', '!', '?')
            val e = m.range.first + url.length
            if (free(m.range.first, e)) out += Ref(Kind.URL, m.range.first, e, m.range.first, e, Target.ANY, url)
        }
        TAG.findAll(text).forEach { m ->
            val tag = m.groupValues[1]
            // Цвета (#fff, #a1b2c3) и номера (#12) — не теги.
            if (tag.all { it.isDigit() } || HEX.matches(tag)) return@forEach
            if (free(m.range.first, m.range.last + 1)) out += Ref(Kind.TAG, m.range.first, m.range.last + 1, m.range.first, m.range.last + 1, Target.ANY, m.groupValues[1].lowercase())
        }
        return out.sortedBy { it.start }
    }

    fun tags(text: String): Set<String> = refs(text).filter { it.kind == Kind.TAG }.map { it.name }.toSet()

    fun links(text: String): List<Ref> = refs(text).filter { it.kind == Kind.LINK }

    /** Текст ссылки для вставки: [[рецепт:Борщ]]. */
    fun link(target: Target, name: String): String = "[[" + (if (target == Target.ANY) "" else target.word + ":") + name.replace("[", "(").replace("]", ")") + "]]"
}
