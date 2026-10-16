package com.dasein.poryadok.reader

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/** Простые форматы: TXT, HTML, RTF, DOCX, ODT. */
object TextConvert {
    // ---------- кодировка ----------

    /** Текст файла: UTF-8/UTF-16 по BOM, строгий UTF-8, иначе лучшая из cp1251 и KOI8-R (по частоте русских букв). */
    fun decodeText(b: ByteArray): String {
        if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte()) return String(b, 3, b.size - 3, Charsets.UTF_8)
        if (b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte()) return String(b, 2, b.size - 2, Charsets.UTF_16LE)
        if (b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte()) return String(b, 2, b.size - 2, Charsets.UTF_16BE)
        runCatching {
            return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(b)).toString()
        }
        val sample = b.copyOf(minOf(b.size, 200_000))
        fun score(cs: String) = String(sample, charset(cs)).count { it in "оеаинтсрвлОЕАИНТСРВЛ" }
        val best = listOf("windows-1251", "KOI8-R").maxBy { score(it) }
        return String(b, charset(best))
    }

    // ---------- TXT ----------

    private val HEADING = Regex(
        "(?U)^\\s*((глава|часть|книга|пролог|эпилог|послесловие|предисловие|вступление|chapter|part|book|prologue|epilogue|preface)\\b.{0,80}|" +
            "(\\d{1,3}|[IVXLC]{1,7})\\.?|\\*\\s*\\*\\s*\\*)\\s*$",
        RegexOption.IGNORE_CASE,
    )

    fun txt(file: File, out: BookWriter): BookManifest {
        val text = decodeText(file.readBytes()).replace("\r\n", "\n").replace('\r', '\n')
        val lines = text.split('\n')
        // Абзацы через пустую строку (текст «сломан» по ширине) или каждая строка — абзац.
        val blank = lines.count { it.isBlank() }
        val paras = mutableListOf<String>()
        if (blank > lines.size / 5 && lines.count { it.length in 50..90 } > lines.size / 3) {
            val sb = StringBuilder()
            lines.forEach { l ->
                if (l.isBlank()) { if (sb.isNotBlank()) paras += sb.toString().trim(); sb.clear() }
                else { if (sb.isNotEmpty()) sb.append(' '); sb.append(l.trim()) }
            }
            if (sb.isNotBlank()) paras += sb.toString().trim()
        } else lines.forEach { if (it.isNotBlank()) paras += it.trim() }
        val isHead = paras.map { it.length < 90 && HEADING.matches(it) && !it.matches(Regex("\\*\\s*\\*\\s*\\*")) }
        val heads = isHead.count { it }
        val chapters = mutableListOf<Pair<String, StringBuilder>>()
        if (heads >= 2) {
            var cur = "" to StringBuilder()
            paras.forEachIndexed { i, p ->
                if (isHead[i]) {
                    if (cur.second.isNotBlank() || cur.first.isNotBlank()) chapters += cur
                    cur = p to StringBuilder("<h2>${Html.esc(p)}</h2>\n")
                } else cur.second.append(para(p))
            }
            chapters += cur
        } else {
            var cur = StringBuilder()
            var n = 0
            paras.forEach { p ->
                cur.append(para(p)); n += p.length
                if (n > 40_000) { chapters += "" to cur; cur = StringBuilder(); n = 0 }
            }
            if (cur.isNotEmpty() || chapters.isEmpty()) chapters += "" to cur
        }
        chapters.forEachIndexed { i, (t, sb) ->
            val html = sb.toString()
            out.chapter(t.ifBlank { if (chapters.size == 1) file.nameWithoutExtension else "Часть ${i + 1}" }, html)
        }
        return out.finish(BookManifest(title = file.nameWithoutExtension))
    }

    private fun para(p: String) = if (p.matches(Regex("\\*\\s*\\*\\s*\\*"))) "<p class=\"sep\">* * *</p>\n" else "<p>${Html.esc(p)}</p>\n"

    // ---------- HTML ----------

    fun html(file: File, out: BookWriter): BookManifest {
        val doc = EpubConvert.decode(file.readBytes())
        val title = Html.titleOf(doc).ifBlank { file.nameWithoutExtension }
        var body = Html.clean(Html.body(doc))
        // Картинки рядом с файлом не видны (нет доступа к папке), поэтому только встроенные data: оставляем.
        body = Regex("(?i)<img\\b[^>]*\\ssrc\\s*=\\s*[\"'](?!data:)[^\"']*[\"'][^>]*>").replace(body, "")
        splitByHeadings(body).forEach { (t, h) -> out.chapter(t.ifBlank { title }, h) }
        return out.finish(BookManifest(title = title))
    }

    /** Режет длинный HTML по заголовкам h1/h2, если они есть. */
    fun splitByHeadings(html: String): List<Pair<String, String>> {
        val parts = html.split(Regex("(?i)(?=<h[12]\\b)")).filter { Html.text(it).isNotBlank() || it.contains("<img", true) }
        if (parts.size < 2 || html.length < 60_000) return listOf(Html.titleOf(html) to html)
        return parts.map { Html.titleOf(it).ifBlank { Html.firstWords(it) } to it }
    }

    // ---------- RTF ----------

    fun rtf(file: File, out: BookWriter): BookManifest {
        val bytes = file.readBytes()
        val s = String(bytes, Charsets.ISO_8859_1)
        val sb = StringBuilder()
        val para = StringBuilder()
        var cs = charset("windows-1251")
        var skip = 0
        var depth = 0
        val skipStack = ArrayDeque<Int>()
        var uc = 1
        var pendingSkip = 0
        val bytesBuf = java.io.ByteArrayOutputStream()
        fun flushBytes() { if (bytesBuf.size() > 0) { para.append(String(bytesBuf.toByteArray(), cs)); bytesBuf.reset() } }
        fun endPara() { flushBytes(); val t = para.toString().trim(); if (t.isNotEmpty()) sb.append("<p>").append(Html.esc(t)).append("</p>\n"); para.clear() }
        var i = 0
        val skipDest = setOf("fonttbl", "colortbl", "stylesheet", "info", "pict", "header", "footer", "headerl", "headerr", "footerl", "footerr", "object", "listtable", "listoverridetable", "rsidtbl", "themedata", "datastore", "latentstyles", "xmlnstbl", "generator", "fldinst")
        while (i < s.length) {
            val c = s[i]
            when (c) {
                '{' -> { skipStack.addLast(skip); depth++; i++ }
                '}' -> { flushBytes(); skip = skipStack.removeLastOrNull() ?: 0; depth--; i++ }
                '\\' -> {
                    i++
                    if (i >= s.length) break
                    val n = s[i]
                    when {
                        n == '\'' -> {
                            val hex = s.substring(i + 1, minOf(i + 3, s.length))
                            i += 3
                            if (pendingSkip > 0) { pendingSkip--; continue }
                            if (skip == 0) hex.toIntOrNull(16)?.let { bytesBuf.write(it) }
                        }
                        n == '*' -> { skip = 1; i++ }
                        n == '\\' || n == '{' || n == '}' -> { if (skip == 0) { flushBytes(); para.append(n) }; i++ }
                        n == '~' -> { if (skip == 0) { flushBytes(); para.append('\u00A0') }; i++ }
                        n == '-' || n == '_' -> i++
                        n == '\n' || n == '\r' -> { if (skip == 0) endPara(); i++ }
                        n.isLetter() -> {
                            val st = i
                            while (i < s.length && s[i].isLetter()) i++
                            val word = s.substring(st, i)
                            val numSt = i
                            if (i < s.length && (s[i] == '-' || s[i].isDigit())) { i++; while (i < s.length && s[i].isDigit()) i++ }
                            val num = s.substring(numSt, i).toIntOrNull()
                            if (i < s.length && s[i] == ' ') i++
                            when (word) {
                                "ansicpg" -> num?.let { cs = runCatching { charset("windows-$it") }.getOrDefault(cs) }
                                "par", "line", "sect", "page" -> if (skip == 0) endPara()
                                "tab" -> if (skip == 0) { flushBytes(); para.append(' ') }
                                "uc" -> uc = num ?: 1
                                "u" -> if (skip == 0 && num != null) { flushBytes(); para.append((if (num < 0) num + 65536 else num).toChar()); pendingSkip = uc }
                                "emdash" -> if (skip == 0) { flushBytes(); para.append('—') }
                                "endash" -> if (skip == 0) { flushBytes(); para.append('–') }
                                "lquote", "rquote" -> if (skip == 0) { flushBytes(); para.append('\'') }
                                "ldblquote" -> if (skip == 0) { flushBytes(); para.append('«') }
                                "rdblquote" -> if (skip == 0) { flushBytes(); para.append('»') }
                                "bullet" -> if (skip == 0) { flushBytes(); para.append('•') }
                                else -> if (word in skipDest) skip = 1
                            }
                        }
                        else -> i++
                    }
                }
                '\r', '\n' -> i++
                else -> {
                    if (pendingSkip > 0) pendingSkip-- else if (skip == 0) bytesBuf.write(c.code)
                    i++
                }
            }
        }
        endPara()
        val html = sb.toString()
        splitByHeadings(html).let { parts ->
            if (parts.size == 1 && html.length > 60_000) chunk(html).forEachIndexed { k, h -> out.chapter("Часть ${k + 1}", h) }
            else parts.forEach { (t, h) -> out.chapter(t.ifBlank { file.nameWithoutExtension }, h) }
        }
        return out.finish(BookManifest(title = file.nameWithoutExtension))
    }

    private fun chunk(html: String): List<String> {
        val outList = mutableListOf<String>()
        val sb = StringBuilder()
        html.split("\n").forEach { l -> sb.append(l).append('\n'); if (sb.length > 50_000) { outList += sb.toString(); sb.clear() } }
        if (sb.isNotBlank()) outList += sb.toString()
        return outList
    }

    // ---------- DOCX / ODT ----------

    private fun dom(bytes: ByteArray): Element = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(bytes.inputStream()).documentElement
    private fun Element.kids(): List<Element> = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
    private val Element.local get() = localName ?: tagName.substringAfter(':')
    private fun Element.attr(local: String): String = (0 until attributes.length).map { attributes.item(it) }.firstOrNull { (it.localName ?: it.nodeName.substringAfter(':')) == local }?.nodeValue.orEmpty()
    private fun Element.desc(local: String): List<Element> = getElementsByTagNameNS("*", local).let { l -> (0 until l.length).map { l.item(it) as Element } }

    fun docx(file: File, out: BookWriter): BookManifest = openZip(file).use { z ->
        fun bytes(p: String) = z.getEntry(p)?.let { e -> z.getInputStream(e).use { it.readBytes() } }
        val rels = bytes("word/_rels/document.xml.rels")?.let { dom(it) }?.desc("Relationship")?.associate { it.getAttribute("Id") to it.getAttribute("Target") }.orEmpty()
        val doc = dom(bytes("word/document.xml") ?: error("Нет текста документа"))
        val title = bytes("docProps/core.xml")?.let { dom(it).desc("title").firstOrNull()?.textContent?.trim() }.orEmpty()
        val author = bytes("docProps/core.xml")?.let { dom(it).desc("creator").firstOrNull()?.textContent?.trim() }.orEmpty()
        val body = doc.desc("body").firstOrNull() ?: error("Пустой документ")
        val sb = StringBuilder()
        fun run(r: Element, p: StringBuilder) {
            val pr = r.kids().firstOrNull { it.local == "rPr" }
            val b = pr?.kids()?.any { it.local == "b" && it.attr("val") !in setOf("0", "false") } == true
            val i = pr?.kids()?.any { it.local == "i" && it.attr("val") !in setOf("0", "false") } == true
            val t = StringBuilder()
            r.kids().forEach { k ->
                when (k.local) {
                    "t" -> t.append(Html.esc(k.textContent))
                    "tab" -> t.append(" ")
                    "br" -> t.append("<br>")
                    "drawing", "pict" -> k.desc("blip").firstOrNull()?.attr("embed")?.let { id -> rels[id] }?.let { target ->
                        val path = EpubConvert.resolve("word/document.xml", target)
                        out.image(path) { bytes(path) }?.let { t.append("<img src=\"$it\" alt=\"\">") }
                    }
                }
            }
            var s = t.toString()
            if (i) s = "<em>$s</em>"
            if (b) s = "<strong>$s</strong>"
            p.append(s)
        }
        fun paragraph(pe: Element) {
            val style = pe.kids().firstOrNull { it.local == "pPr" }?.kids()?.firstOrNull { it.local == "pStyle" }?.attr("val").orEmpty().lowercase()
            val p = StringBuilder()
            pe.kids().forEach { k -> when (k.local) { "r" -> run(k, p); "hyperlink", "ins", "smartTag" -> k.kids().filter { it.local == "r" }.forEach { run(it, p) } } }
            val level = when {
                style == "title" -> 1
                style.startsWith("heading") || style.startsWith("заголовок") -> style.filter { it.isDigit() }.toIntOrNull()?.coerceIn(1, 4)?.let { it + 1 } ?: 2
                else -> 0
            }
            if (level > 0) sb.append("<h$level>$p</h$level>\n") else sb.append("<p>$p</p>\n")
        }
        fun block(e: Element) {
            when (e.local) {
                "p" -> paragraph(e)
                "tbl" -> {
                    sb.append("<table>")
                    e.kids().filter { it.local == "tr" }.forEach { tr ->
                        sb.append("<tr>"); tr.kids().filter { it.local == "tc" }.forEach { tc -> sb.append("<td>"); val save = sb.length; tc.kids().forEach { block(it) }; if (sb.length == save) sb.append(""); sb.append("</td>") }; sb.append("</tr>")
                    }
                    sb.append("</table>\n")
                }
                "sdt" -> e.kids().firstOrNull { it.local == "sdtContent" }?.kids()?.forEach { block(it) }
            }
        }
        body.kids().forEach { block(it) }
        splitByHeadings(sb.toString()).forEach { (t, h) -> out.chapter(t.ifBlank { title.ifBlank { file.nameWithoutExtension } }, h) }
        out.finish(BookManifest(title = title.ifBlank { file.nameWithoutExtension }, author = author))
    }

    fun odt(file: File, out: BookWriter): BookManifest = openZip(file).use { z ->
        fun bytes(p: String) = z.getEntry(p)?.let { e -> z.getInputStream(e).use { it.readBytes() } }
        val doc = dom(bytes("content.xml") ?: error("Нет текста документа"))
        val meta = bytes("meta.xml")?.let { dom(it) }
        val title = meta?.desc("title")?.firstOrNull()?.textContent?.trim().orEmpty()
        val author = meta?.desc("initial-creator")?.firstOrNull()?.textContent?.trim().orEmpty()
        val sb = StringBuilder()
        fun inline(n: Node, p: StringBuilder) {
            when (n.nodeType) {
                Node.TEXT_NODE -> p.append(Html.esc(n.nodeValue))
                Node.ELEMENT_NODE -> {
                    val e = n as Element
                    when (e.local) {
                        "s" -> p.append(" ".repeat(e.attr("c").toIntOrNull() ?: 1))
                        "tab" -> p.append(" ")
                        "line-break" -> p.append("<br>")
                        "image" -> {
                            val href = e.attr("href")
                            out.image(href) { bytes(href) }?.let { p.append("<img src=\"$it\" alt=\"\">") }
                        }
                        "note" -> {}
                        else -> (0 until e.childNodes.length).forEach { inline(e.childNodes.item(it), p) }
                    }
                }
            }
        }
        fun block(e: Element) {
            when (e.local) {
                "h" -> { val l = (e.attr("outline-level").toIntOrNull() ?: 1).coerceIn(1, 3) + 1; val p = StringBuilder(); (0 until e.childNodes.length).forEach { inline(e.childNodes.item(it), p) }; sb.append("<h$l>$p</h$l>\n") }
                "p" -> { val p = StringBuilder(); (0 until e.childNodes.length).forEach { inline(e.childNodes.item(it), p) }; sb.append("<p>$p</p>\n") }
                else -> e.kids().forEach { block(it) }
            }
        }
        doc.desc("text").firstOrNull { it.parentNode?.let { p -> (p.localName ?: "") == "body" } == true }?.kids()?.forEach { block(it) }
        splitByHeadings(sb.toString()).forEach { (t, h) -> out.chapter(t.ifBlank { title.ifBlank { file.nameWithoutExtension } }, h) }
        out.finish(BookManifest(title = title.ifBlank { file.nameWithoutExtension }, author = author))
    }
}
