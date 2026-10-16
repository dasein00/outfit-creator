package com.dasein.poryadok.reader

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.util.Base64
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/** FictionBook 2 (.fb2 и .fb2.zip): разделы верхнего уровня — главы, сноски — отдельной главой, картинки из binary. */
object Fb2Convert {
    private fun Element.kids(): List<Element> = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
    private val Element.name get() = localName ?: tagName.substringAfter(':')
    private fun Element.kid(n: String) = kids().firstOrNull { it.name == n }
    private fun Element.href(): String = (0 until attributes.length).map { attributes.item(it) }.firstOrNull { it.nodeName.endsWith("href") }?.nodeValue.orEmpty()

    fun bytesOf(file: File): ByteArray =
        if (String(file.inputStream().use { s -> ByteArray(2).also { s.read(it) } }, Charsets.ISO_8859_1) == "PK") openZip(file).use { z ->
            val e = z.entries().asSequence().firstOrNull { it.name.lowercase().endsWith(".fb2") } ?: error("В архиве нет файла .fb2")
            z.getInputStream(e).use { it.readBytes() }
        } else file.readBytes()

    private fun parse(bytes: ByteArray): Element {
        val f = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        }
        return runCatching { f.newDocumentBuilder().parse(bytes.inputStream()).documentElement }.recoverCatching {
            // Неверные пространства имён (l:href без объявления) — без их учёта.
            f.isNamespaceAware = false
            f.newDocumentBuilder().parse(bytes.inputStream()).documentElement
        }.getOrElse {
            // Битые файлы: «&» без сущности, мусор в начале — чиним и пробуем ещё раз.
            val text = EpubConvert.decode(bytes).replace(Regex("&(?!(?:#\\d+|#x[0-9a-fA-F]+|[a-zA-Z]+);)"), "&amp;")
            val start = text.indexOf("<FictionBook").takeIf { it >= 0 } ?: 0
            f.newDocumentBuilder().parse(org.xml.sax.InputSource(java.io.StringReader(text.substring(start)))).documentElement
        }
    }

    fun convert(file: File, out: BookWriter): BookManifest {
        val root = parse(bytesOf(file))
        val binaries = root.kids().filter { it.name == "binary" }.associate { it.getAttribute("id") to it }
        fun imageSrc(href: String): String? {
            val id = href.removePrefix("#")
            val b = binaries[id] ?: return null
            return out.image("fb2:$id") { runCatching { Base64.getMimeDecoder().decode(b.textContent.trim()) }.getOrNull() }
        }
        val desc = root.kid("description")
        val ti = desc?.kid("title-info")
        val title = ti?.kid("book-title")?.textContent?.trim().orEmpty()
        val author = ti?.kids()?.filter { it.name == "author" }?.joinToString(", ") { a ->
            listOf("first-name", "middle-name", "last-name").mapNotNull { a.kid(it)?.textContent?.trim()?.takeIf { s -> s.isNotEmpty() } }.joinToString(" ")
                .ifBlank { a.kid("nickname")?.textContent?.trim().orEmpty() }
        }.orEmpty()
        val lang = ti?.kid("lang")?.textContent?.trim().orEmpty()
        val annotation = ti?.kid("annotation")?.textContent?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
        val cover = ti?.kid("coverpage")?.kids()?.firstOrNull { it.name == "image" }?.href()?.let { imageSrc(it) }.orEmpty()

        val bodies = root.kids().filter { it.name == "body" }
        // id → номер главы (для сносок и внутренних ссылок): сначала раскладываем главы.
        data class Part(val title: String, val nodes: List<Element>, val notes: Boolean)
        val parts = mutableListOf<Part>()
        bodies.forEachIndexed { bi, body ->
            val notes = bi > 0 && (body.getAttribute("name").contains("note", true) || body.getAttribute("name").contains("comment", true))
            val kids = body.kids()
            val sections = kids.filter { it.name == "section" }
            val head = kids.filter { it.name != "section" }
            when {
                notes -> parts += Part(body.kid("title")?.textContent?.trim()?.ifBlank { null } ?: "Примечания", kids, true)
                sections.isEmpty() -> parts += Part(title, kids, false)
                else -> {
                    if (head.isNotEmpty()) parts +=Part(head.firstOrNull { it.name == "title" }?.textContent?.trim().orEmpty().ifBlank { title }, head, false)
                    // Большие разделы с подразделами (части книги) режем по подразделам, чтобы главы не были огромными.
                    sections.forEach { s ->
                        val sub = s.kids().filter { it.name == "section" }
                        if (sub.size >= 2 && s.textContent.length > 120_000) {
                            val intro = s.kids().filter { it.name != "section" }
                            if (intro.isNotEmpty()) parts += Part(s.kid("title")?.textContent.orEmpty(), intro, false)
                            sub.forEach { parts += Part(it.kid("title")?.textContent.orEmpty(), listOf(it), false) }
                        } else parts += Part(s.kid("title")?.textContent.orEmpty(), listOf(s), false)
                    }
                }
            }
        }
        val idChapter = HashMap<String, Int>()
        fun collect(e: Element, ch: Int) { e.getAttribute("id").takeIf { it.isNotBlank() }?.let { idChapter.putIfAbsent(it, ch) }; e.kids().forEach { collect(it, ch) } }
        parts.forEachIndexed { i, p -> p.nodes.forEach { collect(it, i) } }

        val toc = mutableListOf<BookToc>()
        fun inline(n: Node, sb: StringBuilder) {
            when (n.nodeType) {
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> sb.append(Html.esc(n.nodeValue))
                Node.ELEMENT_NODE -> {
                    val e = n as Element
                    val tag = when (e.name) {
                        "strong" -> "strong"; "emphasis" -> "em"; "strikethrough" -> "s"; "sub" -> "sub"; "sup" -> "sup"; "code" -> "code"
                        "a" -> "a"; "image" -> "img"; else -> "span"
                    }
                    when (tag) {
                        "img" -> imageSrc(e.href())?.let { sb.append("<img src=\"$it\" alt=\"\">") }
                        "a" -> {
                            val href = e.href()
                            val id = href.removePrefix("#")
                            val note = e.getAttribute("type") == "note"
                            val target = if (href.startsWith("#")) idChapter[id]?.let { "#ch:$it:${Html.esc(id)}" } ?: "#" else Html.esc(href)
                            sb.append("<a href=\"$target\"${if (note) " class=\"note\"" else ""}>")
                            e.childNodes.let { l -> (0 until l.length).forEach { inline(l.item(it), sb) } }
                            sb.append("</a>")
                        }
                        else -> {
                            sb.append("<$tag>"); e.childNodes.let { l -> (0 until l.length).forEach { inline(l.item(it), sb) } }; sb.append("</$tag>")
                        }
                    }
                }
            }
        }
        fun idAttr(e: Element) = e.getAttribute("id").takeIf { it.isNotBlank() }?.let { " id=\"${Html.esc(it)}\"" }.orEmpty()
        fun block(e: Element, sb: StringBuilder, depth: Int, ch: Int) {
            when (e.name) {
                "p" -> { sb.append("<p${idAttr(e)}>"); (0 until e.childNodes.length).forEach { inline(e.childNodes.item(it), sb) }; sb.append("</p>\n") }
                "subtitle" -> { sb.append("<h4${idAttr(e)} class=\"subtitle\">"); (0 until e.childNodes.length).forEach { inline(e.childNodes.item(it), sb) }; sb.append("</h4>\n") }
                "empty-line" -> sb.append("<p class=\"empty\">&nbsp;</p>\n")
                "title" -> {
                    val h = "h${(depth + 1).coerceIn(1, 4)}"
                    val text = e.textContent.trim().replace(Regex("\\s+"), " ")
                    val anchor = "t${toc.size}"
                    if (text.isNotBlank()) toc += BookToc(text, ch, anchor, (depth - 1).coerceAtLeast(0))
                    sb.append("<$h id=\"$anchor\">")
                    e.kids().forEachIndexed { i, p -> if (i > 0) sb.append("<br>"); (0 until p.childNodes.length).forEach { inline(p.childNodes.item(it), sb) } }
                    sb.append("</$h>\n")
                }
                "image" -> imageSrc(e.href())?.let { sb.append("<div class=\"img\"${idAttr(e)}><img src=\"$it\" alt=\"\"></div>\n") }
                "epigraph" -> { sb.append("<blockquote class=\"epigraph\"${idAttr(e)}>"); e.kids().forEach { block(it, sb, depth, ch) }; sb.append("</blockquote>\n") }
                "cite" -> { sb.append("<blockquote${idAttr(e)}>"); e.kids().forEach { block(it, sb, depth, ch) }; sb.append("</blockquote>\n") }
                "text-author" -> { sb.append("<p class=\"author\">"); (0 until e.childNodes.length).forEach { inline(e.childNodes.item(it), sb) }; sb.append("</p>\n") }
                "poem" -> { sb.append("<div class=\"poem\"${idAttr(e)}>"); e.kids().forEach { block(it, sb, depth, ch) }; sb.append("</div>\n") }
                "stanza" -> { sb.append("<div class=\"stanza\">"); e.kids().forEach { block(it, sb, depth, ch) }; sb.append("</div>\n") }
                "v" -> { sb.append("<p class=\"v\">"); (0 until e.childNodes.length).forEach { inline(e.childNodes.item(it), sb) }; sb.append("</p>\n") }
                "table" -> {
                    sb.append("<table>")
                    e.kids().filter { it.name == "tr" }.forEach { tr ->
                        sb.append("<tr>")
                        tr.kids().forEach { td -> val t = if (td.name == "th") "th" else "td"; sb.append("<$t>"); (0 until td.childNodes.length).forEach { inline(td.childNodes.item(it), sb) }; sb.append("</$t>") }
                        sb.append("</tr>")
                    }
                    sb.append("</table>\n")
                }
                "section" -> { sb.append("<section${idAttr(e)}>"); e.kids().forEach { block(it, sb, depth + 1, ch) }; sb.append("</section>\n") }
                "annotation" -> { sb.append("<div class=\"annotation\">"); e.kids().forEach { block(it, sb, depth, ch) }; sb.append("</div>\n") }
                else -> e.kids().forEach { block(it, sb, depth, ch) }
            }
        }
        parts.forEachIndexed { i, p ->
            val sb = StringBuilder()
            p.nodes.forEach { block(it, sb, if (p.notes) 1 else 0, i) }
            val html = sb.toString()
            out.chapter(p.title.trim().replace(Regex("\\s+"), " ").ifBlank { Html.firstWords(html) }, html)
        }
        return out.finish(BookManifest(title = title, author = author, lang = lang, description = annotation, toc = toc, cover = cover))
    }
}
