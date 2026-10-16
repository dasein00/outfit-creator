package com.dasein.poryadok.reader

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile

/**
 * Kindle: MOBI, PRC, AZW, AZW3 (KF8). Текст в записях PalmDB, сжатие PalmDOC или HUFF/CDIC.
 * Книги с защитой (DRM) не открываются — в них текст зашифрован.
 */
object MobiConvert {
    class Pdb(private val f: RandomAccessFile) {
        val name: String
        private val offsets: IntArray

        init {
            val head = ByteArray(78).also { f.seek(0); f.readFully(it) }
            name = String(head, 0, 32, Charsets.ISO_8859_1).substringBefore('\u0000')
            val n = u16(head, 76)
            val tbl = ByteArray(n * 8).also { f.readFully(it) }
            offsets = IntArray(n) { u32(tbl, it * 8).toInt() }
        }

        val count get() = offsets.size
        fun record(i: Int): ByteArray {
            if (i !in offsets.indices) return ByteArray(0)
            val start = offsets[i].toLong()
            val end = if (i + 1 < offsets.size) offsets[i + 1].toLong() else f.length()
            val len = (end - start).coerceIn(0, 64L * 1024 * 1024).toInt()
            return ByteArray(len).also { f.seek(start); f.readFully(it) }
        }
    }

    fun u16(b: ByteArray, o: Int) = if (o + 1 < b.size) ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF) else 0
    fun u32(b: ByteArray, o: Int): Long = if (o + 3 < b.size) ((b[o].toLong() and 0xFF) shl 24) or ((b[o + 1].toLong() and 0xFF) shl 16) or
        ((b[o + 2].toLong() and 0xFF) shl 8) or (b[o + 3].toLong() and 0xFF) else 0L

    /** PalmDOC LZ77. */
    fun palmDoc(src: ByteArray, len: Int = src.size): ByteArray {
        val out = ByteArrayOutputStream(len * 2)
        var buf = ByteArray(len * 3 + 16)
        var o = 0
        fun put(b: Int) { if (o >= buf.size) buf = buf.copyOf(buf.size * 2); buf[o++] = b.toByte() }
        var i = 0
        while (i < len) {
            val c = src[i++].toInt() and 0xFF
            when {
                c in 1..8 -> repeat(c) { if (i < len) put(src[i++].toInt()) }
                c < 0x80 -> put(c)
                c >= 0xC0 -> { put(' '.code); put(c xor 0x80) }
                else -> {
                    if (i >= len) break
                    val m = ((c shl 8) or (src[i++].toInt() and 0xFF)) and 0x3FFF
                    val dist = m shr 3
                    val n = (m and 7) + 3
                    if (dist in 1..o) repeat(n) { put(buf[o - dist].toInt()) }
                }
            }
        }
        out.write(buf, 0, o)
        return out.toByteArray()
    }

    /** Сжатие HUFF/CDIC (часть книг Amazon). Переложено с KindleUnpack. */
    class Huff(huff: ByteArray, cdics: List<ByteArray>) {
        private val dict1 = Array(256) { LongArray(3) }
        private val mincode = LongArray(33)
        private val maxcode = LongArray(33)
        private val dictionary = mutableListOf<Pair<ByteArray, Boolean>?>()

        init {
            require(String(huff, 0, 4, Charsets.ISO_8859_1) == "HUFF") { "нет HUFF" }
            val off1 = u32(huff, 8).toInt()
            val off2 = u32(huff, 12).toInt()
            for (i in 0 until 256) {
                val v = u32(huff, off1 + i * 4)
                val codelen = v and 0x1F
                val term = v and 0x80
                val max = ((((v ushr 8) + 1) shl (32 - codelen.toInt())) - 1) and 0xFFFFFFFFL
                dict1[i][0] = codelen; dict1[i][1] = term; dict1[i][2] = max
            }
            for (codelen in 1..32) {
                val mn = u32(huff, off2 + (codelen - 1) * 8)
                val mx = u32(huff, off2 + (codelen - 1) * 8 + 4)
                mincode[codelen] = (mn shl (32 - codelen)) and 0xFFFFFFFFL
                maxcode[codelen] = (((mx + 1) shl (32 - codelen)) - 1) and 0xFFFFFFFFL
            }
            cdics.forEach { cdic ->
                require(String(cdic, 0, 4, Charsets.ISO_8859_1) == "CDIC") { "нет CDIC" }
                val phrases = u32(cdic, 8).toInt()
                val bits = u32(cdic, 12).toInt()
                val n = minOf(1 shl bits, phrases - dictionary.size)
                for (k in 0 until n) {
                    val off = u16(cdic, 16 + k * 2)
                    val blen = u16(cdic, 16 + off)
                    val start = 18 + off
                    val l = blen and 0x7FFF
                    dictionary += cdic.copyOfRange(start, minOf(cdic.size, start + l)) to (blen and 0x8000 != 0)
                }
            }
        }

        fun unpack(data0: ByteArray): ByteArray {
            val data = data0 + ByteArray(8)
            var bitsLeft = data0.size.toLong() * 8
            var pos = 0
            fun q(p: Int): Long { var x = 0L; for (k in 0 until 8) x = (x shl 8) or (data[p + k].toLong() and 0xFF); return x }
            var x = q(pos)
            var n = 32
            val out = ByteArrayOutputStream()
            while (true) {
                if (n <= 0) { pos += 4; x = q(pos); n += 32 }
                val code = (x ushr n) and 0xFFFFFFFFL
                val d = dict1[(code ushr 24).toInt()]
                var codelen = d[0].toInt()
                var max = d[2]
                if (d[1] == 0L) {
                    while (codelen < 32 && code < mincode[codelen]) codelen++
                    max = maxcode[codelen]
                }
                n -= codelen
                bitsLeft -= codelen
                if (bitsLeft < 0) break
                val r = ((max - code) ushr (32 - codelen)).toInt()
                if (r !in dictionary.indices) break
                val (slice, flag) = dictionary[r] ?: break
                if (!flag) {
                    dictionary[r] = null
                    val unpacked = unpack(slice)
                    dictionary[r] = unpacked to true
                    out.write(unpacked)
                } else out.write(slice)
            }
            return out.toByteArray()
        }
    }

    private fun trailing(rec: ByteArray, flags: Int): Int {
        var size = rec.size
        fun entry(end: Int): Int {
            var bitpos = 0; var result = 0; var s = end
            if (s <= 0) return 0
            while (true) {
                val v = rec[s - 1].toInt() and 0xFF
                result = result or ((v and 0x7F) shl bitpos)
                bitpos += 7; s--
                if (v and 0x80 != 0 || bitpos >= 28 || s == 0) return result
            }
        }
        var num = 0
        var t = flags shr 1
        while (t != 0) {
            if (t and 1 != 0) num += entry(size - num)
            t = t shr 1
        }
        if (flags and 1 != 0 && size - num - 1 >= 0) num += (rec[size - num - 1].toInt() and 0x3) + 1
        return num.coerceAtMost(size)
    }

    class Header(val rec0: ByteArray, val base: Int) {
        val compression = u16(rec0, 0)
        val textLength = u32(rec0, 4)
        val textRecords = u16(rec0, 8)
        val encryption = u16(rec0, 12)
        val hasMobi = rec0.size > 20 && String(rec0, 16, 4, Charsets.ISO_8859_1) == "MOBI"
        val headerLen = if (hasMobi) u32(rec0, 20).toInt() else 0
        val encoding = if (hasMobi) u32(rec0, 28).toInt() else 1252
        val version = if (hasMobi) u32(rec0, 36).toInt() else 0
        val fullName: String
        val firstImage = if (hasMobi) u32(rec0, 108).toInt() else -1
        val huffRec = if (hasMobi) u32(rec0, 112).toInt() else 0
        val huffCount = if (hasMobi) u32(rec0, 116).toInt() else 0
        val fdst = if (hasMobi && headerLen >= 0xC4) u32(rec0, 0xC0).toInt() else -1
        val extraFlags = if (hasMobi && headerLen >= 0xE4) u16(rec0, 0xF2) else 0
        val exth = HashMap<Int, MutableList<ByteArray>>()
        val charset get() = if (encoding == 65001) Charsets.UTF_8 else charset("windows-1252")

        init {
            fullName = if (hasMobi) {
                val off = u32(rec0, 84).toInt(); val len = u32(rec0, 88).toInt()
                if (off in 1 until rec0.size) String(rec0, off, minOf(len, rec0.size - off), if (encoding == 65001) Charsets.UTF_8 else charset("windows-1252")) else ""
            } else ""
            if (hasMobi && u32(rec0, 0x80) and 0x40 != 0L) {
                val p = 16 + headerLen
                if (p + 12 <= rec0.size && String(rec0, p, 4, Charsets.ISO_8859_1) == "EXTH") {
                    val n = u32(rec0, p + 8).toInt()
                    var o = p + 12
                    repeat(n) {
                        if (o + 8 > rec0.size) return@repeat
                        val type = u32(rec0, o).toInt(); val len = u32(rec0, o + 4).toInt()
                        if (len < 8 || o + len > rec0.size) return@repeat
                        exth.getOrPut(type) { mutableListOf() } += rec0.copyOfRange(o + 8, o + len)
                        o += len
                    }
                }
            }
        }

        fun exthStr(type: Int): String? = exth[type]?.firstOrNull()?.let { String(it, charset).trim() }?.takeIf { it.isNotBlank() }
        fun exthInt(type: Int): Int? = exth[type]?.firstOrNull()?.takeIf { it.size >= 4 }?.let { u32(it, 0).toInt() }
    }

    fun convert(file: File, out: BookWriter): BookManifest = RandomAccessFile(file, "r").use { raf ->
        val pdb = Pdb(raf)
        var h = Header(pdb.record(0), 0)
        if (h.encryption != 0) error("Книга защищена (DRM) — такие файлы Kindle не открываются без ключа магазина")
        // KF8 внутри «двойного» MOBI: берём старую часть (MOBI 6) — она проще и полная.
        val kf8 = h.version >= 8
        val firstImage = h.firstImage
        val raw = rawText(pdb, h, 0)
        val title = h.exthStr(503) ?: h.fullName.ifBlank { pdb.name.replace('_', ' ') }
        val author = h.exth[100]?.joinToString(", ") { String(it, h.charset).trim() }.orEmpty()
        val description = h.exthStr(103)?.let { Html.text(it) }.orEmpty()
        val lang = h.exthStr(524).orEmpty()
        fun imageRecord(index: Int): String? {
            if (firstImage < 0 || firstImage == -1 || index < 0) return null
            val r = firstImage + index
            return out.image("rec:$r") { pdb.record(r).takeIf { Images.ext(it) != null } }
        }
        val cover = h.exthInt(201)?.let { imageRecord(it) }.orEmpty()
        val chapters = if (kf8) kf8Chapters(raw, h, pdb, ::imageRecord) else mobi6Chapters(raw, h, ::imageRecord)
        val ids = HashMap<String, Int>()
        chapters.forEachIndexed { i, c -> Regex("\\sid=\"(fp\\d+)\"").findAll(c).forEach { ids.putIfAbsent(it.groupValues[1], i) } }
        chapters.forEach { c0 ->
            val c = Regex("href=\"#(fp\\d+)\"").replace(c0) { m -> ids[m.groupValues[1]]?.let { "href=\"#ch:$it:${m.groupValues[1]}\"" } ?: "href=\"#\"" }
            out.chapter(Html.titleOf(c).ifBlank { Html.firstWords(c) }, c)
        }
        out.finish(BookManifest(title = title, author = author, description = description, lang = lang, cover = cover))
    }

    private fun rawText(pdb: Pdb, h: Header, base: Int): ByteArray {
        val huff = if (h.compression == 17480) Huff(pdb.record(base + h.huffRec), (1 until h.huffCount).map { pdb.record(base + h.huffRec + it) }) else null
        val out = ByteArrayOutputStream(h.textLength.toInt().coerceIn(0, 50_000_000))
        for (i in 1..h.textRecords) {
            val rec = pdb.record(base + i)
            val len = rec.size - trailing(rec, h.extraFlags)
            val data = rec.copyOf(len.coerceAtLeast(0))
            out.write(
                when (h.compression) {
                    2 -> palmDoc(data)
                    17480 -> huff!!.unpack(data)
                    else -> data
                },
            )
        }
        return out.toByteArray().let { if (h.textLength in 1 until it.size.toLong()) it.copyOf(h.textLength.toInt()) else it }
    }

    /** MOBI 6: одна HTML-страница, главы разделены <mbp:pagebreak/>, ссылки filepos — по смещению в байтах. */
    private fun mobi6Chapters(raw: ByteArray, h: Header, image: (Int) -> String?): List<String> {
        var s = String(raw, Charsets.ISO_8859_1)
        // Якоря для filepos: вставляем <a id="fpN"> в нужные места (с конца, чтобы смещения не сдвигались).
        val targets = Regex("filepos\\s*=\\s*[\"']?0*(\\d+)").findAll(s).mapNotNull { it.groupValues[1].toIntOrNull() }.filter { it in 0..s.length }.distinct().sortedDescending()
        val sb = StringBuilder(s)
        targets.forEach { pos ->
            var p = pos
            val lt = s.lastIndexOf('<', p - 1); val gt = s.lastIndexOf('>', p - 1)
            if (lt > gt) p = s.indexOf('>', p).let { if (it < 0) pos else it + 1 }
            sb.insert(p, "<a id=\"fp$pos\"></a>")
        }
        s = sb.toString()
        s = Regex("filepos\\s*=\\s*[\"']?0*(\\d+)[\"']?").replace(s) { "href=\"#fp${it.groupValues[1]}\"" }
        var text = String(s.toByteArray(Charsets.ISO_8859_1), h.charset)
        text = Regex("(?i)<img\\b([^>]*?)recindex\\s*=\\s*[\"']?(\\d+)[\"']?([^>]*)>").replace(text) { m ->
            image(m.groupValues[2].toInt() - 1)?.let { "<img src=\"$it\" alt=\"\">" } ?: ""
        }
        text = Html.body(text)
        return text.split(Regex("(?i)<mbp:pagebreak\\s*/?>")).map { Html.clean(it.replace(Regex("(?i)</?mbp:[^>]*>"), "")) }
            .filter { Html.text(it).isNotBlank() || it.contains("<img", true) }
            .let { chunks -> mergeSmall(chunks) }
    }

    /** AZW3 / KF8: части по <html …>, картинки kindle:embed, служебные ссылки kindle:pos убираются. */
    private fun kf8Chapters(raw: ByteArray, h: Header, pdb: Pdb, image: (Int) -> String?): List<String> {
        var flow = raw
        if (h.fdst > 0) {
            val f = pdb.record(h.fdst)
            if (f.size >= 12 && String(f, 0, 4, Charsets.ISO_8859_1) == "FDST") {
                val end = u32(f, 16).toInt()
                if (end in 1..raw.size) flow = raw.copyOf(end)
            }
        }
        var text = String(flow, h.charset)
        text = Regex("kindle:embed:([0-9A-Va-v]{4})(\\?[^\"')\\s]*)?").replace(text) { m ->
            image(m.groupValues[1].uppercase().toInt(32) - 1) ?: ""
        }
        text = Regex("(?i)\\shref\\s*=\\s*\"kindle:pos:[^\"]*\"").replace(text, " href=\"#\"")
        val parts = text.split(Regex("(?i)(?=<html\\b)"))
        // Фрагменты KF8 идут после </body></html> своего «скелета», поэтому берём всё, кроме head и обёрток.
        return parts.map { p -> Html.clean(p.replace(Regex("(?is)<head\\b.*?</head>"), "").replace(Regex("(?i)<!DOCTYPE[^>]*>|</?(html|body)\\b[^>]*>"), "")) }
            .filter { Html.text(it).isNotBlank() || it.contains("<img", true) }
            .let { mergeSmall(it) }
    }

    /** Совсем короткие куски (пустая страница, заголовок отдельно от текста) склеиваются со следующими. */
    private fun mergeSmall(chunks: List<String>): List<String> {
        val outList = mutableListOf<String>()
        var carry = ""
        chunks.forEachIndexed { i, c ->
            val joined = carry + c
            if (Html.textLength(joined) < 400 && !joined.contains("<img", true) && i < chunks.lastIndex) carry = joined + "\n"
            else { outList += joined; carry = "" }
        }
        if (carry.isNotBlank()) outList += carry
        return outList.ifEmpty { listOf("<p></p>") }
    }
}
