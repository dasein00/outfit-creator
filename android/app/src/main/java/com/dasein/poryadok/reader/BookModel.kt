package com.dasein.poryadok.reader

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Глава подготовленной книги: файл с HTML-фрагментом (содержимое body) и длина её текста в символах. */
@Serializable
data class BookChapter(val title: String, val file: String, val chars: Int)

/** Пункт оглавления: глава и якорь внутри неё (id элемента), уровень вложенности. */
@Serializable
data class BookToc(val title: String, val chapter: Int, val anchor: String = "", val level: Int = 0)

/** Книга, разобранная в единый вид для читалки (кроме постраничных PDF, DjVu и комиксов). */
@Serializable
data class BookManifest(
    val version: Int = BookConvert.VERSION,
    val title: String = "",
    val author: String = "",
    val lang: String = "",
    val description: String = "",
    val chapters: List<BookChapter> = emptyList(),
    val toc: List<BookToc> = emptyList(),
    /** Обложка: путь картинки внутри папки книги («i/cover.jpg») или пусто. */
    val cover: String = "",
) {
    val totalChars get() = chapters.sumOf { it.chars }.coerceAtLeast(1)
}

/** Куда складывается разобранная книга: главы в c/, картинки в i/, оглавление в book.json. */
class BookWriter(val dir: File) {
    private val chapters = mutableListOf<BookChapter>()
    private val images = HashMap<String, String>()
    /** Номер главы, который знает конвертер (по порядку вызовов chapter), → первая настоящая глава после деления длинных. */
    private val logicalFirst = mutableListOf<Int>()
    val count get() = logicalFirst.size

    init {
        dir.deleteRecursively()
        File(dir, "c").mkdirs()
        File(dir, "i").mkdirs()
    }

    /**
     * Добавляет главу и возвращает её номер для ссылок (#ch:номер:якорь). Очень длинные главы делятся на части
     * по абзацам — так страницы листаются без задержек; ссылки и оглавление потом пересчитываются.
     */
    fun chapter(title: String, html: String): Int {
        val logical = logicalFirst.size
        logicalFirst += chapters.size
        val pieces = split(html)
        pieces.forEachIndexed { k, piece ->
            val name = "c/%04d.html".format(chapters.size)
            File(dir, name).writeText(piece)
            val t = title.trim().take(200)
            chapters += BookChapter(if (k == 0 || t.isBlank()) t else "$t (${k + 1})", name, Html.textLength(piece))
        }
        return logical
    }

    private val block = Regex("(?i)<(/?)(div|section|blockquote|ul|ol|table)\\b[^>]*>")
    private val blockEnd = Regex("(?i)</(p|div|section|blockquote|h\\d|table|ul|ol)>")

    /** Делит длинный HTML между абзацами; открытые обёртки (section, div, blockquote) закрываются и открываются заново. */
    private fun split(html: String): List<String> {
        if (html.length < MAX * 2) return listOf(html)
        val outList = mutableListOf<String>()
        val stack = ArrayDeque<Pair<String, String>>()
        var scanned = 0
        fun scan(to: Int) {
            block.findAll(html, scanned).takeWhile { it.range.first < to }.forEach { m ->
                val name = m.groupValues[2].lowercase()
                if (m.groupValues[1] == "/") stack.indexOfLast { it.first == name }.takeIf { it >= 0 }?.let { stack.removeAt(it) }
                else if (!m.value.endsWith("/>")) stack.addLast(name to m.value)
            }
            scanned = to
        }
        var prefix = ""
        var start = 0
        while (html.length - start > MAX * 3 / 2) {
            var pos = start + MAX
            var cut = -1
            while (true) {
                val m = blockEnd.find(html, pos) ?: break
                val c = m.range.last + 1
                scan(c)
                if (stack.none { it.first in setOf("table", "ul", "ol") }) { cut = c; break }
                pos = c
            }
            if (cut < 0 || cut >= html.length - 100) break
            outList += prefix + html.substring(start, cut) + stack.asReversed().joinToString("") { "</${it.first}>" }
            prefix = stack.joinToString("") { it.second.replace(Regex("\\sid\\s*=\\s*(\"[^\"]*\"|'[^']*')"), "") }
            start = cut
        }
        outList += prefix + html.substring(start)
        return outList
    }

    /** Сохраняет картинку под безопасным именем и возвращает путь для src. Одна и та же картинка пишется один раз. */
    fun image(key: String, bytes: () -> ByteArray?): String? {
        images[key]?.let { return it }
        val data = bytes() ?: return null
        val ext = Images.ext(data) ?: return null
        val name = "i/%04d.%s".format(images.size, ext)
        File(dir, name).writeBytes(data)
        images[key] = name
        return name
    }

    fun finish(meta: BookManifest): BookManifest {
        // Где какой якорь (id) после деления глав.
        val ids = HashMap<String, Int>()
        val idRe = Regex("\\sid\\s*=\\s*[\"']([^\"']+)[\"']")
        chapters.forEachIndexed { i, c -> idRe.findAll(File(dir, c.file).readText()).forEach { ids.putIfAbsent(it.groupValues[1], i) } }
        fun real(logical: Int, anchor: String) = anchor.takeIf { it.isNotEmpty() }?.let { ids[Html.decodeEntities(it)] } ?: logicalFirst.getOrNull(logical) ?: 0
        if (chapters.size != logicalFirst.size) chapters.forEach { c ->
            val f = File(dir, c.file)
            val s = f.readText()
            val r = Regex("#ch:(\\d+):([^\"']*)").replace(s) { m -> "#ch:${real(m.groupValues[1].toInt(), m.groupValues[2])}:${m.groupValues[2]}" }
            if (r != s) f.writeText(r)
        }
        val toc = meta.toc.map { it.copy(chapter = real(it.chapter, it.anchor)) }
        // Глава без заголовка получает название из оглавления.
        val named = chapters.mapIndexed { i, c ->
            if (c.title.isNotBlank()) c else c.copy(title = toc.firstOrNull { it.chapter == i }?.title ?: "Глава ${i + 1}")
        }
        val m = meta.copy(chapters = named, toc = toc.ifEmpty { named.mapIndexed { i, c -> BookToc(c.title, i) } })
        File(dir, "book.json").writeText(BookConvert.json.encodeToString(BookManifest.serializer(), m))
        return m
    }

    companion object {
        /** Примерный размер части длинной главы, символов HTML. */
        const val MAX = 60_000
    }
}

object Images {
    fun ext(b: ByteArray): String? = when {
        b.size > 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() -> "jpg"
        b.size > 8 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() && b[2] == 'N'.code.toByte() -> "png"
        b.size > 6 && b[0] == 'G'.code.toByte() && b[1] == 'I'.code.toByte() && b[2] == 'F'.code.toByte() -> "gif"
        b.size > 12 && String(b, 0, 4, Charsets.ISO_8859_1) == "RIFF" && String(b, 8, 4, Charsets.ISO_8859_1) == "WEBP" -> "webp"
        b.size > 2 && b[0] == 'B'.code.toByte() && b[1] == 'M'.code.toByte() -> "bmp"
        b.size > 5 && String(b, 0, minOf(b.size, 200), Charsets.ISO_8859_1).let { it.contains("<svg") } -> "svg"
        else -> null
    }
}

/** Мелочи для HTML: экранирование, длина текста, очистка фрагментов. */
object Html {
    fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private val ENT = mapOf("nbsp" to "\u00A0", "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "mdash" to "—", "ndash" to "–",
        "laquo" to "«", "raquo" to "»", "hellip" to "…", "shy" to "\u00AD", "copy" to "©", "bdquo" to "„", "ldquo" to "“", "rdquo" to "”", "lsquo" to "‘", "rsquo" to "’")

    fun decodeEntities(s: String): String = Regex("&(#x[0-9a-fA-F]+|#\\d+|[a-zA-Z]+);").replace(s) { m ->
        val v = m.groupValues[1]
        when {
            v.startsWith("#x") || v.startsWith("#X") -> v.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it.coerceIn(0, 0x10FFFF))) } ?: m.value
            v.startsWith("#") -> v.drop(1).toIntOrNull()?.let { String(Character.toChars(it.coerceIn(0, 0x10FFFF))) } ?: m.value
            else -> ENT[v] ?: m.value
        }
    }

    /** Текст фрагмента без тегов — так же, как его видит textContent в читалке (для поиска и процентов). */
    fun text(html: String): String = decodeEntities(
        html.replace(Regex("(?is)<(script|style)\\b.*?</\\1>"), "").replace(Regex("<[^>]*>"), ""),
    )

    fun textLength(html: String) = text(html).length

    private val VOID = "area|base|br|col|embed|hr|img|input|link|meta|param|source|track|wbr"

    /** Приводит XHTML-фрагмент к обычному HTML: без скриптов и стилей, без самозакрытых не-пустых тегов (<a/>, <div/>). */
    fun clean(fragment: String): String {
        var s = fragment
        s = s.replace(Regex("(?is)<(script|style|noscript|iframe|object)\\b.*?</\\1\\s*>"), "")
        s = s.replace(Regex("(?is)<(link|meta|base)\\b[^>]*>"), "")
        s = s.replace(Regex("(?is)<\\?.*?\\?>"), "")
        s = s.replace(Regex("(?is)<!--.*?-->"), "")
        s = s.replace(Regex("(?i)\\son\\w+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)"), "")
        s = s.replace(Regex("<(?!(?:$VOID)\\b)([a-zA-Z][\\w:.-]*)((?:\\s[^<>]*?)?)\\s*/>")) { m -> "<${m.groupValues[1]}${m.groupValues[2]}></${m.groupValues[1]}>" }
        return s
    }

    /** Содержимое body (или весь текст, если body нет). */
    fun body(doc: String): String {
        val start = Regex("(?is)<body\\b[^>]*>").find(doc) ?: return doc.replace(Regex("(?is)<head\\b.*?</head>"), "").replace(Regex("(?is)</?html\\b[^>]*>"), "")
        val end = doc.lastIndexOf("</body", ignoreCase = true).takeIf { it > start.range.last } ?: doc.length
        return doc.substring(start.range.last + 1, end)
    }

    fun titleOf(doc: String): String =
        Regex("(?is)<h[1-3]\\b[^>]*>(.*?)</h[1-3]>").find(doc)?.let { decodeEntities(it.groupValues[1].replace(Regex("<[^>]*>"), " ")).trim().replace(Regex("\\s+"), " ") }?.takeIf { it.isNotBlank() }
            ?: Regex("(?is)<title\\b[^>]*>(.*?)</title>").find(doc)?.let { text(it.groupValues[1]).trim() }?.takeIf { it.isNotBlank() }
            ?: ""

    /** Первые слова главы — как название, если заголовка нет. */
    fun firstWords(html: String, n: Int = 60): String {
        // Первая непустая строка (до конца абзаца или переноса), а не весь текст подряд.
        val line = html.split(Regex("(?i)</p>|<br\\s*/?>|</h\\d>|</div>|</li>|</tr>")).map { decodeEntities(it.replace(Regex("<[^>]*>"), " ")).replace(Regex("\\s+"), " ").trim() }
            .firstOrNull { it.isNotBlank() }.orEmpty()
        return if (line.length > n) line.take(n).substringBeforeLast(' ') + "…" else line
    }

    /** Пара строк текста в абзацы. */
    fun paragraphs(text: String): String = text.split('\n').joinToString("\n") { "<p>${esc(it.trim())}</p>" }
}

/** ZIP с именами файлов не в UTF-8 (старые архиваторы) тоже открывается. */
fun openZip(file: File): java.util.zip.ZipFile = runCatching { java.util.zip.ZipFile(file) }.getOrElse { java.util.zip.ZipFile(file, charset("CP437")) }

object BookConvert {
    /** Меняется, когда меняется способ разбора, — тогда книги разбираются заново. */
    const val VERSION = 3
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Разбирает файл книги в папку [dir]. Для постраничных форматов не вызывается. */
    fun convert(file: File, format: BookFormat, dir: File): BookManifest {
        val out = BookWriter(dir)
        val m = when (format) {
            BookFormat.EPUB -> EpubConvert.convert(file, out)
            BookFormat.FB2, BookFormat.FB2ZIP -> Fb2Convert.convert(file, out)
            BookFormat.MOBI, BookFormat.AZW3 -> MobiConvert.convert(file, out)
            BookFormat.TXT -> TextConvert.txt(file, out)
            BookFormat.HTML -> TextConvert.html(file, out)
            BookFormat.RTF -> TextConvert.rtf(file, out)
            BookFormat.DOCX -> TextConvert.docx(file, out)
            BookFormat.ODT -> TextConvert.odt(file, out)
            else -> error("${format.label} показывается постранично")
        }
        if (m.chapters.isEmpty() || m.chapters.all { it.chars == 0 } && m.chapters.none { File(dir, it.file).readText().contains("<img", true) }) error("В файле не найден текст")
        return m
    }

    fun read(dir: File): BookManifest? = runCatching {
        json.decodeFromString(BookManifest.serializer(), File(dir, "book.json").readText())
    }.getOrNull()?.takeIf { it.version == VERSION }
}
