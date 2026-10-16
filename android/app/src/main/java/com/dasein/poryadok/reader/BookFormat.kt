package com.dasein.poryadok.reader

import java.io.File
import java.util.zip.ZipFile

/** Форматы читалки. [paged] — постраничные (PDF, DjVu, комиксы): страница показывается картинкой. */
enum class BookFormat(val label: String, val paged: Boolean = false) {
    EPUB("EPUB"), FB2("FB2"), FB2ZIP("FB2"), MOBI("MOBI"), AZW3("AZW3"), TXT("TXT"), HTML("HTML"), RTF("RTF"), DOCX("DOCX"), ODT("ODT"),
    PDF("PDF", true), DJVU("DjVu", true), CBZ("CBZ", true);

    companion object {
        val EXTENSIONS = listOf("epub", "fb2", "fbz", "zip", "mobi", "azw", "azw3", "kf8", "prc", "pdb", "txt", "text", "md", "html", "htm", "xhtml", "rtf", "docx", "odt", "pdf", "djvu", "djv", "cbz")

        /** Формат по содержимому файла, а если не ясно — по расширению. null — не книга. */
        fun detect(file: File, name: String): BookFormat? {
            val head = ByteArray(80)
            val n = runCatching { file.inputStream().use { it.read(head) } }.getOrDefault(0)
            if (n <= 0) return null
            val s = String(head, 0, n, Charsets.ISO_8859_1)
            val ext = name.substringAfterLast('.', "").lowercase()
            return when {
                s.startsWith("%PDF") -> PDF
                s.startsWith("AT&TFORM") || s.startsWith("FORM") && s.contains("DJV") -> DJVU
                s.startsWith("{\\rtf") -> RTF
                n >= 68 && (s.substring(60, 68) == "BOOKMOBI" || s.substring(60, 68) == "TEXtREAd") -> if (ext == "azw3" || ext == "kf8") AZW3 else MOBI
                s.startsWith("PK") -> zipKind(file, ext)
                ext == "fb2" || s.contains("<FictionBook") -> FB2
                ext in setOf("html", "htm", "xhtml") || s.trimStart().startsWith("<!DOCTYPE html", true) || s.trimStart().startsWith("<html", true) -> HTML
                ext in setOf("txt", "text", "md") -> TXT
                ext == "djvu" || ext == "djv" -> DJVU
                // Неизвестное расширение, но это обычный текст.
                head.take(n).count { it in 0..8 } == 0 -> TXT
                else -> null
            }
        }

        private fun zipKind(file: File, ext: String): BookFormat? = runCatching {
            openZip(file).use { z ->
                val names = z.entries().asSequence().map { it.name }.toList()
                when {
                    "mimetype" in names && z.getInputStream(z.getEntry("mimetype")).use { String(it.readBytes()).trim() } == "application/epub+zip" -> EPUB
                    names.any { it.startsWith("META-INF/container.xml") } -> EPUB
                    names.any { it == "word/document.xml" } -> DOCX
                    names.any { it == "content.xml" } && names.any { it == "mimetype" } -> ODT
                    names.any { it.lowercase().endsWith(".fb2") } -> FB2ZIP
                    names.count { it.lowercase().substringAfterLast('.') in setOf("jpg", "jpeg", "png", "webp", "gif") } > 0 -> CBZ
                    ext == "epub" -> EPUB
                    else -> null
                }
            }
        }.getOrNull()
    }
}
