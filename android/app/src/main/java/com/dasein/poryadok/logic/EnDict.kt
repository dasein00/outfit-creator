package com.dasein.poryadok.logic

/**
 * Большой англо-русский словарь без интернета (Викисловарь, CC BY-SA): слово, транскрипция,
 * место в списке частотности английского (0 — редкое) и значения по частям речи.
 * Строка файла: «слово\tтранскрипция\tчастотность\tчасть~пояснение~перевод;;…».
 */
object EnDict {
    data class Sense(val pos: String, val gloss: String, val ru: String)

    data class Entry(val en: String, val ipa: String, val rank: Int, val senses: List<Sense>) {
        /** Короткий перевод — первые варианты первого значения. */
        val shortRu: String get() = senses.firstOrNull()?.ru?.split(", ")?.take(2)?.joinToString(", ").orEmpty()
        val common: Boolean get() = rank in 1..5000
        internal val key = en.lowercase()
        internal val ruLower = senses.joinToString(" | ") { it.ru.lowercase() }
    }

    fun parseLine(line: String): Entry? {
        if (line.isEmpty() || line[0] == '#') return null
        val p = line.split('\t')
        if (p.size < 4) return null
        val senses = p[3].split(";;").mapNotNull { s ->
            val f = s.split('~')
            if (f.size < 3 || f[2].isBlank()) null else Sense(f[0], f[1], f[2])
        }
        if (senses.isEmpty()) return null
        return Entry(p[0], p[1], p[2].toIntOrNull() ?: 0, senses)
    }

    fun parse(text: String): List<Entry> = text.lineSequence().mapNotNull(::parseLine).toList()

    private fun rankKey(e: Entry) = if (e.rank > 0) e.rank else 1_000_000 + e.en.length

    private fun isCyrillic(s: String) = s.any { it in 'а'..'я' || it == 'ё' }

    /**
     * Поиск по-английски (точное совпадение, начало слова, слово внутри выражения) или по-русски
     * (перевод начинается с запроса). Сначала частые слова.
     */
    fun search(all: List<Entry>, query: String, limit: Int = 80): List<Entry> {
        val q = query.trim().lowercase().replace('ё', 'е')
        if (q.isEmpty()) return emptyList()
        return if (isCyrillic(q)) {
            val exact = Regex("(^|[ ,|(])" + Regex.escape(q) + "($|[ ,|)\\[])")
            val start = Regex("(^|[ ,|(])" + Regex.escape(q))
            val found = all.filter { start.containsMatchIn(it.ruLower.replace('ё', 'е')) }
            found.sortedWith(compareBy<Entry>({ if (exact.containsMatchIn(it.ruLower.replace('ё', 'е'))) 0 else 1 }, { if (' ' in it.en) 1 else 0 }, ::rankKey)).take(limit)
        } else {
            val exact = all.filter { it.key == q }
            val prefix = all.filter { it.key != q && it.key.startsWith(q) }.sortedWith(compareBy(::rankKey))
            val inner = if (q.length < 3) emptyList() else all.filter { !it.key.startsWith(q) && (it.key.contains(" $q") || it.key.contains("-$q")) }.sortedWith(compareBy(::rankKey))
            (exact + prefix + inner).take(limit)
        }
    }

    /** Самые частые слова — для подборки, когда поиск пуст. */
    fun frequent(all: List<Entry>, n: Int): List<Entry> = all.filter { it.rank > 0 && ' ' !in it.en }.sortedBy { it.rank }.take(n)
}
