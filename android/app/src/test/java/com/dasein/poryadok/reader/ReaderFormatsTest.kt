package com.dasein.poryadok.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ReaderFormatsTest {
    @get:Rule val tmp = TemporaryFolder()

    private val para = "Это тестовый абзац книги для проверки читалки. В нём есть «кавычки», тире — и многоточие… "

    private fun convert(name: String, bytes: ByteArray): Pair<BookManifest, File> {
        val f = File(tmp.root, name).apply { writeBytes(bytes) }
        val fmt = BookFormat.detect(f, name) ?: error("формат не определён")
        val dir = File(tmp.root, "out_$name")
        return BookConvert.convert(f, fmt, dir) to dir
    }

    @Test
    fun fb2WithNotesAndCp1251() {
        val fb = """<?xml version="1.0" encoding="windows-1251"?>
<FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0" xmlns:l="http://www.w3.org/1999/xlink">
<description><title-info><author><first-name>Лев</first-name><last-name>Толстой</last-name></author><book-title>Повесть</book-title><lang>ru</lang></title-info></description>
<body><section><title><p>Глава I</p></title><p>$para<a l:href="#n1" type="note">[1]</a></p></section>
<section><title><p>Глава II</p></title><p>$para</p></section></body>
<body name="notes"><section id="n1"><p>Текст сноски.</p></section></body></FictionBook>"""
        val (m, dir) = convert("book.fb2", fb.toByteArray(charset("windows-1251")))
        assertEquals("Повесть", m.title)
        assertEquals("Лев Толстой", m.author)
        assertEquals(3, m.chapters.size)
        assertEquals(listOf("Глава I", "Глава II", "Примечания"), m.chapters.map { it.title })
        val ch0 = File(dir, m.chapters[0].file).readText()
        assertTrue(ch0, ch0.contains("href=\"#ch:2:n1\""))
        assertTrue(ch0.contains("«кавычки»"))
    }

    @Test
    fun epubSpineTocAndImages() {
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10, 0, 0)
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            fun put(n: String, s: String) { z.putNextEntry(ZipEntry(n)); z.write(s.toByteArray()); z.closeEntry() }
            put("mimetype", "application/epub+zip")
            put("META-INF/container.xml", """<?xml version="1.0"?><container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>""")
            put("OEBPS/content.opf", """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" xmlns:dc="http://purl.org/dc/elements/1.1/"><metadata><dc:title>Книга</dc:title><dc:creator>Автор</dc:creator></metadata>
<manifest><item id="a" href="Text/a.xhtml" media-type="application/xhtml+xml"/><item id="b" href="Text/b.xhtml" media-type="application/xhtml+xml"/><item id="img" href="Images/c.png" media-type="image/png" properties="cover-image"/><item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/></manifest>
<spine toc="ncx"><itemref idref="a"/><itemref idref="b"/></spine></package>""")
            put("OEBPS/toc.ncx", """<?xml version="1.0"?><ncx xmlns="http://www.daisy.org/z3986/2005/ncx/"><navMap><navPoint><navLabel><text>Первая</text></navLabel><content src="Text/a.xhtml"/></navPoint><navPoint><navLabel><text>Вторая</text></navLabel><content src="Text/b.xhtml#x"/></navPoint></navMap></ncx>""")
            put("OEBPS/Text/a.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>t</title><script>alert(1)</script></head><body><p>$para<a href="b.xhtml#x">дальше</a></p><img src="../Images/c.png"/><a id="z"/></body></html>""")
            put("OEBPS/Text/b.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml"><body><h2 id="x">Вторая</h2><p>$para</p></body></html>""")
            z.putNextEntry(ZipEntry("OEBPS/Images/c.png")); z.write(png); z.closeEntry()
        }
        val (m, dir) = convert("b.epub", bos.toByteArray())
        assertEquals("Книга", m.title)
        assertEquals(listOf("Первая", "Вторая"), m.chapters.map { it.title })
        assertEquals("i/0000.png", m.cover)
        val a = File(dir, m.chapters[0].file).readText()
        assertTrue(a, a.contains("href=\"#ch:1:x\""))
        assertTrue(a.contains("src=\"i/0000.png\""))
        assertTrue("скрипт убран", !a.contains("alert"))
        assertTrue("самозакрытый <a/> раскрыт", a.contains("<a id=\"z\"></a>"))
        assertEquals("x", m.toc[1].anchor)
    }

    @Test
    fun palmDocBackReference() {
        val packed = byteArrayOf('a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(), 0x80.toByte(), 0x18, 0xE1.toByte())
        assertEquals("abcabc a", String(MobiConvert.palmDoc(packed), Charsets.ISO_8859_1))
    }

    /** Простейший MOBI 6: две главы через pagebreak, ссылка filepos, EXTH с автором и названием. */
    @Test
    fun mobi6() {
        val html0 = "<html><body><h1>Глава 1</h1><p>${para.repeat(6)}<a filepos=0000000000>к началу</a></p><mbp:pagebreak/><h1>Глава 2</h1><p>${para.repeat(6)}</p></body></html>"
        // Ссылка ведёт на первый абзац — смещение в байтах UTF-8.
        val pos = html0.substring(0, html0.indexOf("<p>")).toByteArray(Charsets.UTF_8).size
        val html = html0.replace("filepos=0000000000", "filepos=%010d".format(pos))
        val text = html.toByteArray(Charsets.UTF_8)
        // PalmDOC без сжатия повторов: байты >= 0x80 и 0x00..0x08 кладём блоками «длина + байты».
        fun pack(b: ByteArray): ByteArray {
            val o = ByteArrayOutputStream(); var i = 0
            while (i < b.size) { val n = minOf(8, b.size - i); o.write(n); o.write(b, i, n); i += n }
            return o.toByteArray()
        }
        val recs = text.toList().chunked(4096).map { pack(it.toByteArray()) }
        val exth = ByteArrayOutputStream().apply {
            fun rec(type: Int, v: ByteArray) { write(int(type)); write(int(v.size + 8)); write(v) }
            val body = ByteArrayOutputStream().also { e ->
                fun r(type: Int, v: ByteArray) { e.write(int(type)); e.write(int(v.size + 8)); e.write(v) }
                r(100, "Иван Петров".toByteArray()); r(503, "Тестовая книга".toByteArray())
            }.toByteArray()
            write("EXTH".toByteArray()); write(int(12 + body.size)); write(int(2)); write(body)
        }.toByteArray()
        val rec0 = ByteArray(16 + 232 + exth.size + 16)
        fun put16(o: Int, v: Int) { rec0[o] = (v shr 8).toByte(); rec0[o + 1] = v.toByte() }
        fun put32(o: Int, v: Long) { for (k in 0..3) rec0[o + k] = (v shr (24 - 8 * k)).toByte() }
        put16(0, 2); put32(4, text.size.toLong()); put16(8, recs.size); put16(10, 4096)
        "MOBI".toByteArray().copyInto(rec0, 16); put32(20, 232); put32(28, 65001); put32(36, 6)
        put32(84, 0); put32(88, 0); put32(108, 0xFFFFFFFFL); put32(0x80, 0x40)
        exth.copyInto(rec0, 16 + 232)
        val records = listOf(rec0) + recs
        val head = ByteArray(78)
        "test".toByteArray().copyInto(head, 0)
        "BOOKMOBI".toByteArray().copyInto(head, 60)
        head[76] = (records.size shr 8).toByte(); head[77] = records.size.toByte()
        val out = ByteArrayOutputStream()
        out.write(head)
        var off = 78 + records.size * 8 + 2
        records.forEachIndexed { i, r -> out.write(int(off)); out.write(int(i)); off += r.size }
        out.write(0); out.write(0)
        records.forEach { out.write(it) }
        val (m, dir) = convert("t.mobi", out.toByteArray())
        assertEquals("Тестовая книга", m.title)
        assertEquals("Иван Петров", m.author)
        assertEquals(2, m.chapters.size)
        assertEquals("Глава 1", m.chapters[0].title)
        val c0 = File(dir, m.chapters[0].file).readText()
        assertTrue(c0, c0.contains("id=\"fp$pos\"") && c0.contains("href=\"#ch:0:fp$pos\""))
    }

    private fun int(v: Int) = byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())

    @Test
    fun txtCp1251Chapters() {
        val t = "Глава 1. Начало\n" + (1..20).joinToString("\n") { para } + "\nГлава 2. Конец\n" + (1..20).joinToString("\n") { para }
        val (m, _) = convert("book.txt", t.toByteArray(charset("windows-1251")))
        assertEquals(listOf("Глава 1. Начало", "Глава 2. Конец"), m.chapters.map { it.title })
        assertTrue(m.totalChars > 3000)
    }

    @Test
    fun rtfCyrillic() {
        fun esc(s: String) = s.map { c -> if (c.code < 128) c.toString() else "\\'%02x".format(c.toString().toByteArray(charset("windows-1251"))[0].toInt() and 0xFF) }.joinToString("")
        val rtf = "{\\rtf1\\ansi\\ansicpg1251{\\fonttbl{\\f0 Times;}}\\f0 ${esc("Привет, мир")}\\par ${esc(para)}\\u8364?\\par}"
        val (m, dir) = convert("a.rtf", rtf.toByteArray(Charsets.ISO_8859_1))
        val c = File(dir, m.chapters[0].file).readText()
        assertTrue(c, c.contains("Привет, мир") && c.contains("€") && !c.contains("Times"))
    }

    @Test
    fun longChapterIsSplitAndLinksFollow() {
        val big = (1..1500).joinToString("\n") { "<p>$para $it</p>" }
        val fb = """<?xml version="1.0" encoding="utf-8"?><FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0" xmlns:l="http://www.w3.org/1999/xlink">
<description><title-info><book-title>Длинная</book-title></title-info></description>
<body><section><title><p>Одна глава</p></title>${(1..1500).joinToString("") { "<p>$para $it</p>" }}<p id="end">конец<a l:href="#n1" type="note">[1]</a></p></section></body>
<body name="notes"><section id="n1"><p>сноска</p></section></body></FictionBook>"""
        assertTrue(big.length > 100_000)
        val (m, dir) = convert("long.fb2", fb.toByteArray())
        assertTrue("глава разделена: ${m.chapters.size}", m.chapters.size >= 3)
        val last = m.chapters.dropLast(1).last()
        val html = File(dir, last.file).readText()
        val notes = m.chapters.lastIndex
        assertTrue(html, html.contains("href=\"#ch:$notes:n1\""))
        // Каждая часть — целый HTML: section закрыт.
        m.chapters.forEach { c -> val s = File(dir, c.file).readText(); assertEquals(Regex("<section").findAll(s).count(), Regex("</section>").findAll(s).count()) }
    }
}
