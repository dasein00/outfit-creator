package com.dasein.poryadok.reader

import org.w3c.dom.Element
import java.io.File
import java.net.URI
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/** EPUB 2 и 3: метаданные из OPF, главы по spine, оглавление из nav или NCX, картинки и обложка. */
object EpubConvert {
    private fun dom(bytes: ByteArray) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        isExpandEntityReferences = false
    }.newDocumentBuilder().apply { setEntityResolver { _, _ -> org.xml.sax.InputSource(java.io.StringReader("")) } }
        .parse(bytes.inputStream()).documentElement

    private fun Element.all(local: String): List<Element> = getElementsByTagNameNS("*", local).let { l -> (0 until l.length).map { l.item(it) as Element } }
    private fun Element.first(local: String): Element? = all(local).firstOrNull()
    private fun Element.kids(local: String): List<Element> = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }.filter { (it.localName ?: it.tagName.substringAfter(':')) == local }

    /** Путь внутри архива: href относительно файла base, без #якоря и %-кодировки. */
    fun resolve(base: String, href: String): String {
        val h = href.substringBefore('#')
        if (h.isEmpty()) return base
        val dir = base.substringBeforeLast('/', "")
        val raw = if (dir.isEmpty()) h else "$dir/$h"
        val parts = ArrayDeque<String>()
        raw.split('/').forEach { p ->
            when (p) { "", "." -> {}; ".." -> parts.removeLastOrNull(); else -> parts.addLast(p) }
        }
        return runCatching { URI(null, null, parts.joinToString("/"), null).path }.getOrDefault(parts.joinToString("/")).let { p ->
            runCatching { java.net.URLDecoder.decode(p.replace("+", "%2B"), "UTF-8") }.getOrDefault(p)
        }
    }

    fun decode(bytes: ByteArray): String {
        val head = String(bytes, 0, minOf(bytes.size, 300), Charsets.ISO_8859_1)
        val enc = Regex("encoding=[\"']([\\w-]+)[\"']").find(head)?.groupValues?.get(1)
            ?: Regex("charset=[\"']?([\\w-]+)").find(head)?.groupValues?.get(1)
        val cs = runCatching { enc?.let { charset(it) } }.getOrNull() ?: Charsets.UTF_8
        return String(bytes, cs).removePrefix("\uFEFF")
    }

    fun convert(file: File, out: BookWriter): BookManifest = openZip(file).use { z ->
        fun bytes(path: String): ByteArray? = (z.getEntry(path) ?: z.entries().asSequence().firstOrNull { it.name.equals(path, true) })?.let { e -> z.getInputStream(e).use { it.readBytes() } }
        val container = bytes("META-INF/container.xml")?.let { dom(it) }
        val opfPath = container?.first("rootfile")?.getAttribute("full-path")?.takeIf { it.isNotBlank() }
            ?: z.entries().asSequence().map { it.name }.firstOrNull { it.endsWith(".opf", true) } ?: error("В EPUB нет описания книги (OPF)")
        val opf = dom(bytes(opfPath) ?: error("Не найден $opfPath"))
        val meta = opf.first("metadata")
        fun dc(name: String) = meta?.all(name)?.map { it.textContent.trim() }?.filter { it.isNotBlank() }.orEmpty()
        data class Item(val id: String, val href: String, val type: String, val props: String)
        val items = opf.first("manifest")?.kids("item").orEmpty().map {
            Item(it.getAttribute("id"), resolve(opfPath, it.getAttribute("href")), it.getAttribute("media-type"), it.getAttribute("properties"))
        }
        val byId = items.associateBy { it.id }
        val spine = opf.first("spine")
        val order = spine?.kids("itemref").orEmpty().filter { it.getAttribute("linear") != "no" }.mapNotNull { byId[it.getAttribute("idref")] }
            .filter { it.type.contains("html") || it.href.endsWith("html", true) || it.href.endsWith(".htm", true) }
            .ifEmpty { items.filter { it.type.contains("html") } }
        val chapterOf = order.mapIndexed { i, it -> it.href to i }.toMap()

        // Обложка: properties="cover-image", meta name="cover", или картинка с cover в имени.
        val coverId = meta?.all("meta")?.firstOrNull { it.getAttribute("name") == "cover" }?.getAttribute("content")
        val coverItem = items.firstOrNull { "cover-image" in it.props } ?: coverId?.let { byId[it] }?.takeIf { it.type.startsWith("image") }
            ?: items.firstOrNull { it.type.startsWith("image") && it.href.contains("cover", true) }
        val cover = coverItem?.let { c -> out.image(c.href) { bytes(c.href) } }.orEmpty()

        fun rewrite(html: String, base: String): String {
            var s = Html.clean(Html.body(html))
            // Картинки (img src, svg image href / xlink:href).
            s = Regex("(?i)(<(?:img|image)\\b[^>]*?\\s(?:src|xlink:href|href)\\s*=\\s*)([\"'])(.*?)\\2").replace(s) { m ->
                val target = resolve(base, Html.decodeEntities(m.groupValues[3]))
                val local = out.image(target) { bytes(target) }
                "${m.groupValues[1]}${m.groupValues[2]}${local ?: ""}${m.groupValues[2]}"
            }
            // Ссылки внутри книги → #ch:номер:якорь (переход делает читалка); внешние — как есть.
            s = Regex("(?i)(<a\\b[^>]*?\\shref\\s*=\\s*)([\"'])(.*?)\\2").replace(s) { m ->
                val href = Html.decodeEntities(m.groupValues[3])
                val q = m.groupValues[2]
                when {
                    href.startsWith("http://") || href.startsWith("https://") || href.startsWith("mailto:") -> m.value
                    else -> {
                        val ch = chapterOf[resolve(base, href)]
                        if (ch != null) "${m.groupValues[1]}$q#ch:$ch:${Html.esc(href.substringAfter('#', ""))}$q" else "${m.groupValues[1]}$q#$q"
                    }
                }
            }
            return s
        }

        // Оглавление: EPUB 3 nav, иначе NCX.
        val toc = mutableListOf<BookToc>()
        fun add(title: String, href: String, base: String, level: Int) {
            val path = resolve(base, href)
            val ch = chapterOf[path] ?: return
            if (title.isNotBlank()) toc += BookToc(title.replace(Regex("\\s+"), " ").trim(), ch, href.substringAfter('#', ""), level)
        }
        items.firstOrNull { "nav" in it.props.split(' ') }?.let { nav ->
            runCatching {
                val root = dom(bytes(nav.href)!!)
                val navEl = root.all("nav").firstOrNull { n -> n.attributes.let { a -> (0 until a.length).any { a.item(it).nodeValue == "toc" } } } ?: root.first("nav")
                fun walk(ol: Element, level: Int) {
                    ol.kids("li").forEach { li ->
                        val a = li.kids("a").firstOrNull() ?: li.kids("span").firstOrNull()
                        if (a != null) add(a.textContent, a.getAttribute("href"), nav.href, level)
                        li.kids("ol").forEach { walk(it, level + 1) }
                    }
                }
                navEl?.kids("ol")?.forEach { walk(it, 0) }
            }
        }
        if (toc.isEmpty()) {
            val ncx = spine?.getAttribute("toc")?.let { byId[it] } ?: items.firstOrNull { it.type == "application/x-dtbncx+xml" }
            ncx?.let { n ->
                runCatching {
                    val root = dom(bytes(n.href)!!)
                    fun walk(e: Element, level: Int) {
                        e.kids("navPoint").forEach { p ->
                            val label = p.first("text")?.textContent.orEmpty()
                            val src = p.kids("content").firstOrNull()?.getAttribute("src").orEmpty()
                            add(label, src, n.href, level)
                            walk(p, level + 1)
                        }
                    }
                    root.first("navMap")?.let { walk(it, 0) }
                }
            }
        }
        // Название главы: из оглавления, иначе первый заголовок, иначе <title>.
        order.forEachIndexed { i, it0 ->
            val doc = bytes(it0.href)?.let { decode(it) } ?: ""
            val fromToc = toc.firstOrNull { it.chapter == i }?.title
            out.chapter(fromToc ?: Html.titleOf(doc), rewrite(doc, it0.href))
        }
        out.finish(
            BookManifest(
                title = dc("title").firstOrNull().orEmpty(), author = dc("creator").joinToString(", "), lang = dc("language").firstOrNull().orEmpty(),
                description = Html.text(dc("description").firstOrNull().orEmpty()).trim(), toc = toc, cover = cover,
            ),
        )
    }
}
