package com.dasein.poryadok.system

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.MediaItem
import com.dasein.poryadok.data.MediaKind
import com.dasein.poryadok.data.MediaStatus
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.reader.BookConvert
import com.dasein.poryadok.reader.BookFormat
import com.dasein.poryadok.reader.BookManifest
import com.dasein.poryadok.reader.openZip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.roundToInt

/** Книга в читалке. Файл лежит в files/books/, обложка — в files/reader/covers/. */
@Serializable
data class ReaderBook(
    val id: Long,
    val title: String,
    val author: String = "",
    val format: String,
    /** Имя файла в files/books. */
    val file: String,
    val size: Long = 0,
    val sourceName: String = "",
    val addedAt: Long = 0,
    val openedAt: Long = 0,
    val cover: String = "",
    val lang: String = "",
    val description: String = "",
    /** Связанная карточка в «Фильмы, сериалы, книги» (0 — нет). */
    val mediaId: Long = 0,
    /** Прочитано, 0…1. */
    val progress: Float = 0f,
    /** Где остановились: глава и символ в ней (для PDF/DjVu — страница, offset = 0). */
    val chapter: Int = 0,
    val offset: Int = 0,
    /** Страниц (PDF, DjVu, комикс) или «условных страниц» по 1800 знаков для текста. */
    val pages: Int = 0,
    /** 0 — не начата, 1 — читаю, 2 — прочитана, 3 — хочу прочитать. */
    val status: Int = 0,
    val secondsRead: Long = 0,
    val finishedDay: Long? = null,
    val shelf: String = "",
    val favorite: Boolean = false,
) {
    val fmt get() = runCatching { BookFormat.valueOf(format) }.getOrDefault(BookFormat.TXT)
    val paged get() = fmt.paged
}

/** Закладка (kind 0), выделение цветом (1) или заметка к странице (2). Для текста start/end — символы в главе. */
@Serializable
data class ReaderMark(
    val id: Long,
    val bookId: Long,
    val kind: Int,
    val chapter: Int,
    val start: Int = 0,
    val end: Int = 0,
    val text: String = "",
    val note: String = "",
    val color: Int = 0,
    val createdAt: Long = 0,
    /** Место в книге в процентах — для сортировки и подписи. */
    val pct: Float = 0f,
)

@Serializable
data class ReaderSettings(
    val fontSize: Int = 19,
    val lineHeight: Float = 1.5f,
    val margin: Int = 20,
    val font: Int = 0,
    /** 0 — день, 1 — сепия, 2 — ночь, 3 — чёрная, 4 — как в приложении, 5 — серая. */
    val theme: Int = 1,
    val justify: Boolean = true,
    val hyphens: Boolean = true,
    val indent: Boolean = true,
    /** true — листать страницы, false — прокручивать. */
    val paged: Boolean = true,
    val animation: Boolean = true,
    /** Яркость окна 0…1, -1 — системная. */
    val brightness: Float = -1f,
    val keepScreenOn: Boolean = true,
    val volumeKeys: Boolean = true,
    val fullscreen: Boolean = true,
    val showClock: Boolean = true,
    /** PDF и DjVu: 0 — листать по странице, 1 — лентой сверху вниз. */
    val pdfMode: Int = 0,
    val pdfCrop: Boolean = false,
    /** PDF и DjVu: 0 — как есть, 1 — ночь (инверсия), 2 — сепия. */
    val pdfColor: Int = 0,
    val ttsRate: Float = 1f,
    val ttsPitch: Float = 1f,
    val paraSpace: Float = 0.35f,
    val sort: Int = 0,
    val grid: Boolean = true,
)

@Serializable
data class ReaderLibraryData(
    val books: List<ReaderBook> = emptyList(),
    val marks: List<ReaderMark> = emptyList(),
    val settings: ReaderSettings = ReaderSettings(),
    /** Секунды чтения по дням (номер дня → секунды). */
    val days: Map<Long, Int> = emptyMap(),
    val nextId: Long = 1,
)

/**
 * Библиотека читалки: книги, позиции, закладки, выделения и заметки, настройки и время чтения.
 * Всё в files/reader/library.json и files/books — попадает в резервную копию (раздел «Читалка»).
 */
object ReaderStore {
    private const val TAG = "DASEIN"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Mutex()
    private val _state = MutableStateFlow(ReaderLibraryData())
    val state: StateFlow<ReaderLibraryData> = _state
    @Volatile private var loaded = false

    fun booksDir(ctx: Context) = File(ctx.filesDir, "books").apply { mkdirs() }
    private fun dataDir(ctx: Context) = File(ctx.filesDir, "reader").apply { mkdirs() }
    private fun libFile(ctx: Context) = File(dataDir(ctx), "library.json")
    fun coversDir(ctx: Context) = File(dataDir(ctx), "covers").apply { mkdirs() }
    /** Разобранные книги и картинки страниц — не в копии, создаются заново при открытии. */
    fun cacheDir(ctx: Context, id: Long) = File(ctx.noBackupFilesDir, "reader/$id")
    fun file(ctx: Context, b: ReaderBook) = File(booksDir(ctx), b.file)

    fun load(ctx: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val d = runCatching { json.decodeFromString(ReaderLibraryData.serializer(), libFile(ctx).readText()) }.getOrElse { ReaderLibraryData() }
            // Копия с другого телефона: пути к обложкам — в папку этого приложения.
            val base = ctx.filesDir.absolutePath
            _state.value = d.copy(books = d.books.map { b ->
                if (b.cover.isNotBlank() && !File(b.cover).exists()) b.copy(cover = b.cover.replace(Regex("^.*?/files/"), "$base/")) else b
            })
            loaded = true
        }
    }

    /** После загрузки резервной копии — перечитать с диска. */
    fun reload(ctx: Context) { loaded = false; load(ctx) }

    private suspend fun update(ctx: Context, f: (ReaderLibraryData) -> ReaderLibraryData) = withContext(Dispatchers.IO) {
        lock.withLock {
            load(ctx)
            val n = f(_state.value)
            _state.value = n
            runCatching {
                val tmp = File(dataDir(ctx), "library.json.tmp")
                tmp.writeText(json.encodeToString(ReaderLibraryData.serializer(), n))
                if (!tmp.renameTo(libFile(ctx))) { libFile(ctx).delete(); tmp.renameTo(libFile(ctx)) }
            }.onFailure { Log.e(TAG, "reader save", it) }
        }
    }

    fun book(id: Long): ReaderBook? = _state.value.books.firstOrNull { it.id == id }

    suspend fun saveBook(ctx: Context, b: ReaderBook) = update(ctx) { d -> d.copy(books = d.books.map { if (it.id == b.id) b else it }) }
    suspend fun saveSettings(ctx: Context, s: ReaderSettings) = update(ctx) { it.copy(settings = s) }

    suspend fun delete(ctx: Context, b: ReaderBook) {
        update(ctx) { d -> d.copy(books = d.books.filter { it.id != b.id }, marks = d.marks.filter { it.bookId != b.id }) }
        withContext(Dispatchers.IO) {
            file(ctx, b).delete()
            if (b.cover.isNotBlank()) File(b.cover).delete()
            cacheDir(ctx, b.id).deleteRecursively()
        }
    }

    // ---------- закладки, выделения, заметки ----------

    suspend fun addMark(ctx: Context, m: ReaderMark): ReaderMark {
        var made = m
        update(ctx) { d -> made = m.copy(id = d.nextId, createdAt = System.currentTimeMillis()); d.copy(marks = d.marks + made, nextId = d.nextId + 1) }
        return made
    }
    suspend fun saveMark(ctx: Context, m: ReaderMark) = update(ctx) { d -> d.copy(marks = d.marks.map { if (it.id == m.id) m else it }) }
    suspend fun deleteMark(ctx: Context, id: Long) = update(ctx) { d -> d.copy(marks = d.marks.filter { it.id != id }) }
    fun marksOf(bookId: Long) = _state.value.marks.filter { it.bookId == bookId }

    // ---------- позиция и время ----------

    suspend fun savePosition(ctx: Context, id: Long, chapter: Int, offset: Int, progress: Float, pages: Int = 0) {
        val now = System.currentTimeMillis()
        var linked: ReaderBook? = null
        update(ctx) { d ->
            d.copy(books = d.books.map { b ->
                if (b.id != id) b else {
                    val p = progress.coerceIn(0f, 1f)
                    val status = when {
                        p >= 0.995f && b.status != 2 -> 2
                        b.status == 0 || b.status == 3 -> 1
                        else -> b.status
                    }
                    b.copy(chapter = chapter, offset = offset, progress = p, openedAt = now, pages = if (pages > 0) pages else b.pages, status = status,
                        finishedDay = if (status == 2 && b.finishedDay == null) Dates.today() else b.finishedDay).also { linked = it }
                }
            })
        }
        linked?.let { syncMedia(it) }
    }

    suspend fun addTime(ctx: Context, id: Long, seconds: Int) {
        if (seconds <= 0) return
        val day = Dates.today()
        update(ctx) { d ->
            d.copy(
                books = d.books.map { if (it.id == id) it.copy(secondsRead = it.secondsRead + seconds) else it },
                days = d.days + (day to ((d.days[day] ?: 0) + seconds)),
            )
        }
    }

    // ---------- связь с «Книгами» ----------

    fun norm(s: String) = s.lowercase().replace('ё', 'е').replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    /** Книга читалки для карточки книги: по связи, иначе по названию (и автору, если он есть). */
    fun forMedia(m: MediaItem): ReaderBook? {
        if (m.kind != MediaKind.BOOK) return null
        val books = _state.value.books
        books.firstOrNull { it.mediaId == m.id }?.let { return it }
        val titles = listOf(m.title, m.originalTitle).map { norm(it) }.filter { it.length >= 2 }
        if (titles.isEmpty()) return null
        val author = norm(m.creators).split(' ').filter { it.length >= 3 }
        fun authorOk(b: ReaderBook) = author.isEmpty() || norm(b.author).isBlank() || author.any { it in norm(b.author) }
        return books.firstOrNull { b -> b.mediaId == 0L && norm(b.title) in titles && authorOk(b) }
            ?: books.firstOrNull { b -> b.mediaId == 0L && titles.any { t -> t.length >= 5 && (norm(b.title).startsWith(t) || norm(b.sourceName).contains(t)) } && authorOk(b) }
    }

    suspend fun link(ctx: Context, bookId: Long, mediaId: Long) = update(ctx) { d ->
        d.copy(books = d.books.map { if (it.id == bookId) it.copy(mediaId = mediaId) else if (it.mediaId == mediaId) it.copy(mediaId = 0) else it })
    }

    /** Прогресс читалки переносится в карточку книги: страницы, статус «Читаю» / «Прочитано», даты. */
    private suspend fun syncMedia(b: ReaderBook) {
        if (b.mediaId == 0L) return
        runCatching {
            val x = Graph.extra
            val m = x.mediaItemNow(b.mediaId) ?: return
            val total = if (b.pages > 0) b.pages else m.progressTotal
            val done = if (total > 0) (b.progress * total).roundToInt().coerceIn(0, total) else m.progress
            val status = when {
                b.status == 2 -> MediaStatus.DONE
                m.status == MediaStatus.PLANNED || m.status == MediaStatus.DROPPED -> MediaStatus.IN_PROGRESS
                else -> m.status
            }
            val n = m.copy(
                progress = done, progressTotal = total, status = status,
                startedDay = m.startedDay ?: Dates.today(),
                finishedDay = if (status == MediaStatus.DONE) m.finishedDay ?: b.finishedDay ?: Dates.today() else m.finishedDay,
            )
            if (n != m) x.upsertMedia(n)
        }.onFailure { Log.w(TAG, "reader media sync", it) }
    }

    /** Карточка в «Книгах» для книги из читалки (если её ещё нет). */
    suspend fun addToMedia(ctx: Context, b: ReaderBook): Long {
        val x = Graph.extra
        val poster = if (b.cover.isNotBlank()) withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(ctx.filesDir, "posters").apply { mkdirs() }
                File(dir, "reader_${b.id}_${System.currentTimeMillis()}.jpg").also { File(b.cover).copyTo(it, true) }.absolutePath
            }.getOrDefault("")
        } else ""
        val id = x.upsertMedia(
            MediaItem(
                kind = MediaKind.BOOK, title = b.title, creators = b.author, poster = poster, description = b.description,
                status = if (b.status == 2) MediaStatus.DONE else MediaStatus.IN_PROGRESS, source = "читалка", createdAt = System.currentTimeMillis(),
                progressTotal = b.pages, progress = (b.progress * b.pages).roundToInt(), startedDay = Dates.today(),
            ),
        )
        link(ctx, b.id, id)
        return id
    }

    // ---------- добавление книг ----------

    private fun displayName(ctx: Context, uri: Uri): String = runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "книга"

    /** Название из имени файла: без расширения, «(Z-Library)», подчёркиваний и т.п. */
    fun titleFromName(name: String): Pair<String, String> {
        var s = name.substringBeforeLast('.').let { if (it.endsWith(".fb2", true)) it.dropLast(4) else it }
        s = s.replace(Regex("\\((z-library|z-lib\\.org|litres|flibusta|royallib\\.com|www\\.[^)]+)\\)", RegexOption.IGNORE_CASE), "")
        s = s.replace('_', ' ').replace(Regex("\\s+"), " ").trim()
        // «Автор - Название» или «Автор. Название».
        val m = Regex("^([^-—.]{3,40}?)\\s+[-—]\\s+(.{2,})$").find(s)
        return if (m != null) m.groupValues[2].trim() to m.groupValues[1].trim() else s to ""
    }

    /** Копирует файлы книг в приложение. Возвращает добавленные книги (уже имеющиеся — тоже, без повтора). */
    suspend fun import(ctx: Context, uris: List<Uri>, mediaId: Long = 0, onProgress: (String) -> Unit = {}): List<Result<ReaderBook>> = withContext(Dispatchers.IO) {
        load(ctx)
        uris.map { uri ->
            runCatching {
                val name = displayName(ctx, uri)
                onProgress(name)
                val tmp = File(ctx.cacheDir, "import_${System.nanoTime()}")
                ctx.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } } ?: error("Не удалось открыть файл")
                try {
                    importFile(ctx, tmp, name, mediaId)
                } finally { tmp.delete() }
            }.onFailure { Log.w(TAG, "reader import", it) }
        }
    }

    suspend fun importFile(ctx: Context, tmp: File, name: String, mediaId: Long = 0): ReaderBook {
        val fmt = BookFormat.detect(tmp, name) ?: error("«$name» — неизвестный формат. Подходят: EPUB, FB2, MOBI, AZW3, PDF, DjVu, TXT, RTF, DOCX, ODT, HTML, CBZ")
        val size = tmp.length()
        _state.value.books.firstOrNull { it.size == size && it.sourceName == name }?.let { return it }
        var id = 0L
        update(ctx) { d -> id = d.nextId; d.copy(nextId = d.nextId + 1) }
        val ext = when (fmt) {
            BookFormat.FB2ZIP -> "fb2.zip"; BookFormat.MOBI -> name.substringAfterLast('.', "mobi").lowercase().takeIf { it.length in 2..5 } ?: "mobi"
            else -> fmt.name.lowercase()
        }
        val fileName = "$id.$ext"
        val dest = File(booksDir(ctx), fileName)
        tmp.copyTo(dest, overwrite = true)
        val (t0, a0) = titleFromName(name)
        var book = ReaderBook(id = id, title = t0, author = a0, format = fmt.name, file = fileName, size = size, sourceName = name, addedAt = System.currentTimeMillis(), mediaId = mediaId)
        // Название, автор и обложка из самой книги.
        runCatching {
            when {
                fmt == BookFormat.PDF -> {
                    book = book.copy(pages = pdfPages(dest))
                    pdfCover(dest)?.let { book = book.copy(cover = saveCover(ctx, id, it)) }
                    pdfTitle(ctx, dest)?.let { (t, a) -> book = book.copy(title = t.ifBlank { book.title }, author = a.ifBlank { book.author }) }
                }
                fmt == BookFormat.CBZ -> cbzCover(dest)?.let { book = book.copy(cover = saveCover(ctx, id, it)) }
                fmt == BookFormat.DJVU -> {}
                else -> {
                    val m = prepare(ctx, book.copy(), dest)
                    book = book.copy(
                        title = m.title.ifBlank { book.title }, author = m.author.ifBlank { book.author }, lang = m.lang, description = m.description.take(2000),
                        pages = (m.totalChars / 1800).coerceAtLeast(1),
                    )
                    if (m.cover.isNotBlank()) runCatching {
                        BitmapFactory.decodeFile(File(cacheDir(ctx, id), m.cover).absolutePath)?.let { book = book.copy(cover = saveCover(ctx, id, it)) }
                    }
                }
            }
        }.onFailure {
            if (!fmt.paged) { dest.delete(); throw it }
            Log.w(TAG, "reader meta", it)
        }
        update(ctx) { d -> d.copy(books = listOf(book) + d.books) }
        // Связь с карточкой «Книги», если такая уже есть.
        if (mediaId == 0L) runCatching {
            val media = Graph.extra.media().first().filter { it.kind == MediaKind.BOOK }
            media.firstOrNull { m -> forMedia(m)?.id == book.id && _state.value.books.none { it.mediaId == m.id } }?.let { link(ctx, book.id, it.id); book = book.copy(mediaId = it.id) }
        }
        return book
    }

    /** Разобранная книга из кэша; если кэша нет или он старый — разбирает заново. */
    suspend fun prepare(ctx: Context, b: ReaderBook, src: File = file(ctx, b)): BookManifest = withContext(Dispatchers.IO) {
        val dir = cacheDir(ctx, b.id)
        BookConvert.read(dir) ?: BookConvert.convert(src, b.fmt, dir)
    }

    private fun saveCover(ctx: Context, id: Long, bmp: Bitmap): String {
        val w = 360
        val scaled = if (bmp.width > w) Bitmap.createScaledBitmap(bmp, w, (bmp.height * w / bmp.width.toFloat()).roundToInt().coerceAtLeast(1), true) else bmp
        val f = File(coversDir(ctx), "$id.jpg")
        f.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        return f.absolutePath
    }

    suspend fun setCover(ctx: Context, b: ReaderBook, bmp: Bitmap) = saveBook(ctx, b.copy(cover = withContext(Dispatchers.IO) { saveCover(ctx, b.id, bmp) }))

    fun pdfPages(f: File): Int = runCatching {
        ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd -> PdfRenderer(pfd).use { it.pageCount } }
    }.getOrDefault(0)

    private fun pdfCover(f: File): Bitmap? = runCatching {
        ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { r ->
                r.openPage(0).use { p ->
                    val w = 360; val h = (w * p.height / p.width.toFloat()).roundToInt().coerceIn(1, 1200)
                    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                        Canvas(bmp).drawColor(Color.WHITE)
                        p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }
    }.getOrNull()

    private fun pdfTitle(ctx: Context, f: File): Pair<String, String>? = runCatching {
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(ctx.applicationContext)
        com.tom_roush.pdfbox.pdmodel.PDDocument.load(f, com.tom_roush.pdfbox.io.MemoryUsageSetting.setupTempFileOnly()).use { d ->
            val i = d.documentInformation
            (i.title?.trim().orEmpty()) to (i.author?.trim().orEmpty())
        }
    }.getOrNull()?.takeIf { it.first.length in 3..200 }

    private fun cbzCover(f: File): Bitmap? = runCatching {
        openZip(f).use { z ->
            val e = z.entries().asSequence().filter { !it.isDirectory && it.name.substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif") }
                .sortedWith(compareBy(naturalOrder()) { it.name }).firstOrNull() ?: return null
            val bytes = z.getInputStream(e).use { it.readBytes() }
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
            var s = 1; while (o.outWidth / s > 720) s *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s })
        }
    }.getOrNull()

    /** Естественный порядок имён: «2.jpg» раньше «10.jpg». */
    fun naturalOrder(): Comparator<String> = Comparator { a, b ->
        val ra = Regex("(\\d+)|(\\D+)").findAll(a.lowercase()).map { it.value }.toList()
        val rb = Regex("(\\d+)|(\\D+)").findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(ra.size, rb.size)) {
            val x = ra[i]; val y = rb[i]
            val c = if (x[0].isDigit() && y[0].isDigit()) x.toBigInteger().compareTo(y.toBigInteger()) else x.compareTo(y)
            if (c != 0) return@Comparator c
        }
        ra.size - rb.size
    }

    /** Минут чтения сегодня и за 7 дней. */
    fun minutes(d: ReaderLibraryData): Pair<Int, Int> {
        val today = Dates.today()
        return ((d.days[today] ?: 0) / 60) to ((0..6).sumOf { d.days[today - it] ?: 0 } / 60)
    }
}
