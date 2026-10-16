package com.dasein.poryadok.system

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File

/** Пример для режима демонстрации и автотеста: справка по читалке (FB2) и PDF на три страницы. */
object ReaderDemo {
    private val CHAPTERS = listOf(
        "Как добавить книги" to listOf(
            "Нажмите «+ Книги» и выберите один или несколько файлов. Подходят EPUB, FB2 и FB2.ZIP, MOBI, AZW3, PDF, DjVu, TXT, RTF, DOCX, ODT, HTML и комиксы CBZ.",
            "Книгу можно открыть и из файлового менеджера, браузера или мессенджера: «Открыть с помощью» → DASEIN.",
            "Если такая книга уже есть в «Фильмы, сериалы, книги», в её карточке появится кнопка «Читать».",
        ),
        "Как читать" to listOf(
            "Нажатие у правого края — следующая страница, у левого — предыдущая, в середине — меню. Страницы можно листать и смахиванием, и кнопками громкости.",
            "В меню: оглавление, закладки и заметки, вид (шрифт, размер, интервалы, поля, тема), чтение вслух и ночной режим. Ползунок внизу — переход в любое место книги.",
            "Сноски открываются по нажатию прямо поверх текста[1].",
        ),
        "Выделения и заметки" to listOf(
            "Удерживайте слово, растяните выделение и выберите: «Выделить», «Заметка», «Копировать», «Перевести», «Поделиться», «Найти» или «Озвучить».",
            "Все выделения и заметки из всех книг собраны в «Заметки и цитаты» — их можно найти поиском и выгрузить текстом.",
        ),
    )

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    fun fb2(): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<FictionBook xmlns=\"http://www.gribuser.ru/xml/fictionbook/2.0\" xmlns:l=\"http://www.w3.org/1999/xlink\">")
        append("<description><title-info><author><first-name>DASEIN</first-name></author><book-title>Как пользоваться читалкой</book-title><lang>ru</lang></title-info></description>")
        append("<body><title><p>Как пользоваться читалкой</p></title>")
        CHAPTERS.forEach { (t, ps) ->
            append("<section><title><p>${esc(t)}</p></title>")
            ps.forEach { p ->
                val withNote = esc(p).replace("[1]", "<a l:href=\"#n1\" type=\"note\">[1]</a>")
                append("<p>$withNote</p>")
            }
            // Длинный текст — чтобы в главе было несколько страниц.
            repeat(6) { append("<p>${esc(ps.joinToString(" "))}</p>") }
            append("</section>")
        }
        append("</body><body name=\"notes\"><title><p>Примечания</p></title><section id=\"n1\"><title><p>1</p></title><p>Это сноска. Нажмите «Перейти к сноске», чтобы открыть её в тексте.</p></section></body></FictionBook>")
    }

    fun pdf(out: File) {
        val doc = PdfDocument()
        val paint = Paint().apply { textSize = 16f; isAntiAlias = true }
        val title = Paint().apply { textSize = 26f; isFakeBoldText = true; isAntiAlias = true }
        for (i in 1..3) {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(420, 595, i).create())
            val c = page.canvas
            c.drawText("Пример PDF — страница $i", 30f, 60f, title)
            var y = 100f
            CHAPTERS.flatMap { it.second }.forEach { p ->
                p.chunked(48).forEach { line -> if (y < 560) { c.drawText(line, 30f, y, paint); y += 22f } }
                y += 8f
            }
            doc.finishPage(page)
        }
        out.outputStream().use { doc.writeTo(it) }
        doc.close()
    }

    suspend fun add(ctx: Context) {
        ReaderStore.load(ctx)
        if (ReaderStore.state.value.books.isNotEmpty()) return
        val f = File(ctx.cacheDir, "demo_reader.fb2").apply { writeText(fb2()) }
        runCatching { ReaderStore.importFile(ctx, f, "Как пользоваться читалкой.fb2") }
        f.delete()
        val p = File(ctx.cacheDir, "demo_reader.pdf")
        runCatching { pdf(p); ReaderStore.importFile(ctx, p, "Пример PDF.pdf") }
        p.delete()
    }
}
