package com.dasein.poryadok.ui.reader

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.util.LruCache
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.dasein.poryadok.reader.BookFormat
import com.dasein.poryadok.reader.BookToc
import com.dasein.poryadok.reader.openZip
import com.dasein.poryadok.system.ReaderBook
import com.dasein.poryadok.system.ReaderMark
import com.dasein.poryadok.system.ReaderStore
import com.dasein.poryadok.ui.common.TextButton
import com.dasein.poryadok.ui.common.btnLabel
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.roundToInt

/** Источник страниц-картинок: PDF, DjVu или комикс. */
interface PageSource {
    val count: Int
    /** Оглавление: chapter — номер страницы с нуля. */
    val toc: List<BookToc>
    /** Отношение ширины к высоте страницы, если известно. */
    fun aspect(i: Int): Float?
    suspend fun render(i: Int, width: Int): Bitmap?
    suspend fun text(i: Int): String
    fun close()
}

class PdfSource(private val ctx: Context, private val file: File) : PageSource {
    private val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(pfd)
    private val lock = Mutex()
    private val sizes = HashMap<Int, Float>()
    override val count = renderer.pageCount
    override var toc: List<BookToc> = emptyList(); private set
    private var doc: com.tom_roush.pdfbox.pdmodel.PDDocument? = null
    private val texts = HashMap<Int, String>()

    override fun aspect(i: Int) = sizes[i]

    override suspend fun render(i: Int, width: Int): Bitmap? = withContext(Dispatchers.IO) {
        lock.withLock {
            runCatching {
                renderer.openPage(i).use { p ->
                    sizes[i] = p.width / p.height.toFloat()
                    val w = width.coerceIn(200, 2600)
                    val h = (w * p.height / p.width.toFloat()).roundToInt().coerceIn(1, 6000)
                    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b ->
                        Canvas(b).drawColor(android.graphics.Color.WHITE)
                        p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }.getOrNull()
        }
    }

    private fun pdDoc(): com.tom_roush.pdfbox.pdmodel.PDDocument? {
        doc?.let { return it }
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(ctx.applicationContext)
        return runCatching { com.tom_roush.pdfbox.pdmodel.PDDocument.load(file, com.tom_roush.pdfbox.io.MemoryUsageSetting.setupTempFileOnly()) }.getOrNull()?.also { doc = it }
    }

    /** Оглавление (закладки PDF) — читается в фоне. */
    suspend fun loadToc() = withContext(Dispatchers.IO) {
        runCatching {
            val d = pdDoc() ?: return@runCatching
            val outline = d.documentCatalog.documentOutline ?: return@runCatching
            val out = mutableListOf<BookToc>()
            fun walk(item: com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem?, level: Int) {
                var it0 = item
                var guard = 0
                while (it0 != null && guard++ < 5000) {
                    val page = runCatching { it0.findDestinationPage(d)?.let { p -> d.pages.indexOf(p) } }.getOrNull() ?: -1
                    if (page >= 0) out += BookToc(it0.title.orEmpty().trim(), page, "", level)
                    walk(it0.firstChild, level + 1)
                    it0 = it0.nextSibling
                }
            }
            walk(outline.firstChild, 0)
            toc = out
        }
    }

    override suspend fun text(i: Int): String = withContext(Dispatchers.IO) {
        texts[i]?.let { return@withContext it }
        val t = runCatching {
            val d = pdDoc() ?: return@runCatching ""
            com.tom_roush.pdfbox.text.PDFTextStripper().apply { startPage = i + 1; endPage = i + 1 }.getText(d)
        }.getOrDefault("")
        texts[i] = t
        t
    }

    override fun close() { runCatching { renderer.close() }; runCatching { pfd.close() }; runCatching { doc?.close() } }
}

class CbzSource(file: File) : PageSource {
    private val zip: ZipFile = openZip(file)
    private val entries = zip.entries().asSequence().filter { !it.isDirectory && it.name.substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp") && !it.name.startsWith("__MACOSX") }
        .sortedWith(compareBy(ReaderStore.naturalOrder()) { it.name }).toList()
    private val sizes = HashMap<Int, Float>()
    private val lock = Mutex()
    override val count = entries.size
    override val toc: List<BookToc> = emptyList()
    override fun aspect(i: Int) = sizes[i]
    override suspend fun render(i: Int, width: Int): Bitmap? = withContext(Dispatchers.IO) {
        lock.withLock {
            runCatching {
                val bytes = zip.getInputStream(entries[i]).use { it.readBytes() }
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                sizes[i] = o.outWidth / o.outHeight.toFloat().coerceAtLeast(1f)
                var s = 1; while (o.outWidth / (s * 2) >= width) s *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s })
            }.getOrNull()
        }
    }
    override suspend fun text(i: Int) = ""
    override fun close() { runCatching { zip.close() } }
}

/** DjVu через DjVu.js в невидимом WebView: страница приходит картинкой JPEG и кэшируется на диске. */
@SuppressLint("SetJavaScriptEnabled")
class DjvuSource(private val ctx: Context, private val file: File, private val cache: File) : PageSource {
    private val main = Handler(Looper.getMainLooper())
    var web: WebView? = null
    private val opened = CompletableDeferred<Boolean>()
    private val waiting = HashMap<String, CompletableDeferred<Bitmap?>>()
    private val textWait = HashMap<Int, CompletableDeferred<String>>()
    private val texts = HashMap<Int, String>()
    private var sizes: List<Float> = emptyList()
    override var count = 0; private set
    override var toc: List<BookToc> = emptyList(); private set
    var error: String? = null; private set
    private var seq = 0

    override fun aspect(i: Int) = sizes.getOrNull(i)

    inner class Bridge {
        @JavascriptInterface fun ready() { main.post { web?.evaluateJavascript("D.open('book.djvu')", null) } }
        @JavascriptInterface fun opened(json: String) {
            runCatching {
                val o = Json.parseToJsonElement(json).jsonObject
                count = o["pages"]?.jsonPrimitive?.int ?: 0
                sizes = o["sizes"]?.jsonArray?.map { a -> a.jsonArray.let { it[0].jsonPrimitive.int / it[1].jsonPrimitive.int.coerceAtLeast(1).toFloat() } }.orEmpty()
                toc = o["toc"]?.jsonArray?.mapNotNull { e ->
                    val t = e.jsonObject["t"]?.jsonPrimitive?.content.orEmpty()
                    val p = e.jsonObject["p"]?.jsonPrimitive?.int ?: 0
                    val l = e.jsonObject["l"]?.jsonPrimitive?.int ?: 0
                    if (t.isBlank() || p <= 0 || Regex("(?i)\\.djvu?$").containsMatchIn(t)) null else BookToc(t, p - 1, "", l)
                }.orEmpty()
            }
            opened.complete(count > 0)
        }
        @JavascriptInterface fun page(n: Int, b64: String, w: Int, h: Int, id: Int) {
            val key = "$n:$id"
            val bmp = runCatching {
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                File(cache, "p${n}_$id.jpg").writeBytes(bytes)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
            synchronized(waiting) { waiting.remove(key) }?.complete(bmp)
        }
        @JavascriptInterface fun text(n: Int, t: String) { texts[n - 1] = t; synchronized(textWait) { textWait.remove(n - 1) }?.complete(t) }
        @JavascriptInterface fun fail(s: String) {
            android.util.Log.w("DASEIN", "djvu: $s")
            if (!opened.isCompleted) { error = s; opened.complete(false) }
            Regex("page (\\d+)").find(s)?.groupValues?.get(1)?.toIntOrNull()?.let { n ->
                synchronized(waiting) { waiting.keys.filter { it.startsWith("$n:") }.map { waiting.remove(it) } }.forEach { it?.complete(null) }
            }
        }
    }

    fun create(c: Context): WebView = WebView(c).also { w ->
        web = w
        cache.mkdirs()
        w.settings.javaScriptEnabled = true
        w.settings.allowFileAccess = false
        w.addJavascriptInterface(Bridge(), "A")
        w.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                bookResponse(ctx, null, request) { p -> if (p == "book.djvu") file else null }
        }
        w.loadUrl("https://book.local/djvu.html")
    }

    suspend fun awaitOpen(): Boolean = withTimeoutOrNull(120_000) { opened.await() } ?: false

    override suspend fun render(i: Int, width: Int): Bitmap? {
        // Ширина «ступеньками», чтобы картинки из кэша подходили и после поворота экрана.
        val bucket = when { width <= 900 -> 900; width <= 1400 -> 1400; else -> 2000 }
        val f = File(cache, "p${i + 1}_$bucket.jpg")
        if (f.isFile) withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull() }?.let { return it }
        val key = "${i + 1}:$bucket"
        val d = synchronized(waiting) { waiting[key] ?: CompletableDeferred<Bitmap?>().also { waiting[key] = it; main.post { web?.evaluateJavascript("D.render(${i + 1}, $bucket, 0.86, $bucket)", null) } } }
        return withTimeoutOrNull(90_000) { d.await() }
    }

    override suspend fun text(i: Int): String {
        texts[i]?.let { return it }
        val d = synchronized(textWait) { textWait[i] ?: CompletableDeferred<String>().also { textWait[i] = it; main.post { web?.evaluateJavascript("D.text(${i + 1})", null) } } }
        return withTimeoutOrNull(60_000) { d.await() }.orEmpty()
    }

    override fun close() {
        seq++
        web?.let { w -> main.post { w.removeJavascriptInterface("A"); w.destroy() } }
        web = null
    }
}

/** Убирает белые поля страницы (для мелкого текста PDF и сканов). */
fun cropMargins(b: Bitmap): Bitmap {
    val sw = 160
    val sh = (b.height * sw / b.width.toFloat()).roundToInt().coerceAtLeast(1)
    val small = Bitmap.createScaledBitmap(b, sw, sh, true)
    val px = IntArray(sw * sh).also { small.getPixels(it, 0, sw, 0, 0, sw, sh) }
    fun dark(p: Int): Boolean { val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val bl = p and 0xFF; return (r * 3 + g * 6 + bl) / 10 < 225 }
    var minX = sw; var maxX = -1; var minY = sh; var maxY = -1
    for (y in 0 until sh) for (x in 0 until sw) if (dark(px[y * sw + x])) { if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y }
    if (maxX < 0 || maxX - minX < sw / 5 || maxY - minY < sh / 5) return b
    val pad = 3
    val sx = b.width / sw.toFloat(); val sy = b.height / sh.toFloat()
    val l = ((minX - pad).coerceAtLeast(0) * sx).toInt(); val t = ((minY - pad).coerceAtLeast(0) * sy).toInt()
    val r = ((maxX + pad + 1).coerceAtMost(sw) * sx).toInt().coerceAtMost(b.width); val bt = ((maxY + pad + 1).coerceAtMost(sh) * sy).toInt().coerceAtMost(b.height)
    return if (r - l > 10 && bt - t > 10) Bitmap.createBitmap(b, l, t, r - l, bt - t) else b
}

private fun pageFilter(mode: Int): ColorFilter? = when (mode) {
    1 -> ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(-0.87f, 0f, 0f, 0f, 235f, 0f, -0.87f, 0f, 0f, 228f, 0f, 0f, -0.87f, 0f, 215f, 0f, 0f, 0f, 1f, 0f)))
    2 -> ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(0.9f, 0f, 0f, 0f, 12f, 0f, 0.83f, 0f, 0f, 8f, 0f, 0f, 0.68f, 0f, 2f, 0f, 0f, 0f, 1f, 0f)))
    else -> null
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun PagedReader(nav: NavHostController, book: ReaderBook) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val extra = LocalExtra.current
    val data by ReaderStore.state.collectAsState()
    val s = data.settings
    val file = ReaderStore.file(ctx, book)
    var failed by remember { mutableStateOf<String?>(null) }
    val source = remember(book.id) {
        runCatching<PageSource> {
            when (book.fmt) {
                BookFormat.PDF -> PdfSource(ctx, file)
                BookFormat.CBZ -> CbzSource(file)
                else -> DjvuSource(ctx, file, ReaderStore.cacheDir(ctx, book.id))
            }
        }.onFailure { failed = it.message ?: "Не удалось открыть файл" }.getOrNull()
    }
    var count by remember { mutableStateOf(source?.count ?: 0) }
    var tocVersion by remember { mutableStateOf(0) }
    LaunchedEffect(source) {
        when (source) {
            is DjvuSource -> if (source.awaitOpen()) { count = source.count; tocVersion++ } else failed = "DjVu не открылся" + (source.error?.let { ": $it" } ?: "")
            is PdfSource -> { source.loadToc(); tocVersion++ }
            else -> {}
        }
        if (source != null && source !is DjvuSource && count == 0) failed = "В файле нет страниц"
    }
    DisposableEffect(source) { onDispose { source?.close() } }
    // DjVu узнаёт число страниц не сразу — тогда и переходим к месту, где остановились.
    var restored by remember { mutableStateOf(false) }
    val cache = remember { object : LruCache<String, Bitmap>(((Runtime.getRuntime().maxMemory() / 6).coerceAtMost(160L * 1024 * 1024)).toInt()) { override fun sizeOf(key: String, value: Bitmap) = value.byteCount } }

    var menu by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(0) }
    var goTo by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf(false) }
    var marks by remember { mutableStateOf(ReaderStore.marksOf(book.id)) }
    var edit by remember { mutableStateOf<ReaderMark?>(null) }
    var pageNote by remember { mutableStateOf(false) }
    var lastAction by remember { mutableStateOf(System.currentTimeMillis()) }
    val pager = rememberPagerState(initialPage = book.chapter.coerceAtLeast(0)) { count.coerceAtLeast(1) }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = book.chapter.coerceAtLeast(0))
    val current = if (s.pdfMode == 0) pager.currentPage else list.firstVisibleItemIndex
    ReaderWindow(s, immersive = !menu && panel == 0)
    ReadingTimer(book.id) { lastAction }

    fun go(p: Int) {
        val t = p.coerceIn(0, (count - 1).coerceAtLeast(0))
        scope.launch { if (s.pdfMode == 0) pager.scrollToPage(t) else list.scrollToItem(t) }
    }
    LaunchedEffect(count) { if (count > 0 && !restored) { restored = true; if (book.chapter in 1 until count && current != book.chapter) go(book.chapter) } }
    fun step(d: Int) {
        lastAction = System.currentTimeMillis()
        scope.launch { if (s.pdfMode == 0) pager.animateScrollToPage((pager.currentPage + d).coerceIn(0, count - 1)) else list.animateScrollBy(d * 600f) }
    }
    DisposableEffect(book.id, s.pdfMode, count) {
        ReaderKeys.handler = { d -> if (ReaderStore.state.value.settings.volumeKeys && !menu && count > 0) { step(d); true } else false }
        onDispose { ReaderKeys.handler = null }
    }
    // Позиция сохраняется при каждом перелистывании.
    LaunchedEffect(count, s.pdfMode) {
        if (count == 0) return@LaunchedEffect
        snapshotFlow { if (s.pdfMode == 0) pager.currentPage else list.firstVisibleItemIndex }.distinctUntilChanged().collect { p ->
            lastAction = System.currentTimeMillis()
            delay(400)
            ReaderStore.savePosition(ctx, book.id, p, 0, (p + 1f) / count, count)
        }
    }
    BackHandler(enabled = menu || panel != 0) { if (panel != 0) panel = 0 else menu = false }
    val theme = pageTheme(s.theme)
    val bg = if (s.pdfColor == 1) Color(0xFF151414) else if (theme.dark) Color(0xFF1C1B1A) else Color(0xFFE9E7E2)
    val filter = pageFilter(s.pdfColor)

    Box(Modifier.fillMaxSize().background(bg)) {
        if (source is DjvuSource) AndroidView(factory = { c -> source.create(c) }, modifier = Modifier.size(1.dp).alpha(0f))
        when {
            failed != null -> Text(failed!!, color = extra.dim, modifier = Modifier.align(Alignment.Center).padding(24.dp))
            count == 0 -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text(if (source is DjvuSource) "Открываю DjVu…" else "Открываю…", color = extra.dim, modifier = Modifier.padding(top = 12.dp))
            }
            else -> BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(if (s.fullscreen) WindowInsets.displayCutout else WindowInsets.systemBars)) {
                val density = LocalDensity.current
                val widthPx = with(density) { maxWidth.toPx() }.roundToInt()
                val renderW = (widthPx * 1.6f).roundToInt().coerceIn(600, 2200)
                val onTap: (Offset) -> Unit = { o ->
                    val x = o.x / widthPx
                    when { x < .28f -> step(-1); x > .72f -> step(1); else -> menu = !menu }
                }
                if (s.pdfMode == 0) HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1, key = { it }) { i ->
                    ZoomPage(source!!, cache, i, renderW, s.pdfCrop, filter, onTap)
                } else LazyColumn(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { o -> menu = !menu }) }, state = list) {
                    items(count, key = { it }) { i -> ListPage(source!!, cache, i, renderW, s.pdfCrop, filter) }
                }
                Text(
                    "${current + 1} / $count", fontSize = 11.sp, color = if (s.pdfColor == 1 || theme.dark) Color(0xFF9A948B) else Color(0xFF6B6B6B),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).background(bg.copy(alpha = .7f)).padding(horizontal = 6.dp),
                )
            }
        }
        val bookmarked = marks.any { it.kind == 0 && it.chapter == current }
        if (menu) ReaderMenu(
            title = book.title, sub = "${book.fmt.label} · ${count} стр.", theme = theme, progress = if (count > 0) (current + 1f) / count else 0f,
            pageLabel = "Страница ${current + 1} из $count — нажмите, чтобы перейти", bookmarked = bookmarked,
            onBack = { nav.popBackStack() }, onClose = { menu = false },
            onSeek = { f -> go(((count - 1) * f).roundToInt()) }, onPrevPart = { step(-1) }, onNextPart = { step(1) },
            onToc = { panel = 1 }, onMarks = { panel = 3 }, onLook = { panel = 2 },
            onBookmark = {
                scope.launch {
                    val here = marks.firstOrNull { it.kind == 0 && it.chapter == current }
                    if (here != null) ReaderStore.deleteMark(ctx, here.id)
                    else ReaderStore.addMark(ctx, ReaderMark(0, book.id, 0, current, text = "Страница ${current + 1}", pct = (current + 1f) / count.coerceAtLeast(1)))
                    marks = ReaderStore.marksOf(book.id)
                }
            },
            onSearch = { if (book.fmt == BookFormat.CBZ) android.widget.Toast.makeText(ctx, "В комиксе нет текста для поиска", android.widget.Toast.LENGTH_SHORT).show() else search = true },
            onSpeak = null,
            onNight = { scope.launch { ReaderStore.saveSettings(ctx, s.copy(pdfColor = if (s.pdfColor == 1) 0 else 1)) } }, night = s.pdfColor == 1,
            onGoTo = { goTo = true }, onNote = { pageNote = true },
        )
        if (panel == 2) BottomPanel(onDismiss = { panel = 0 }) {
            Column {
                ReaderSettingsPanel(s, paged = true) { n -> scope.launch { ReaderStore.saveSettings(ctx, n) } }
            }
        }
    }
    if (panel == 1 || panel == 3) {
        val toc = remember(tocVersion) { source?.toc.orEmpty() }
        ContentsDialog(
            book, toc.map { Triple(it.title, it.level, it.chapter) }, toc.lastOrNull { it.chapter <= current }?.chapter ?: -1, marks,
            markLabel = { m -> "Страница ${m.chapter + 1}" },
            onToc = { p -> go(p); panel = 0; menu = false },
            onMark = { m -> go(m.chapter); panel = 0; menu = false },
            onEditMark = { m -> edit = m },
            onDismiss = { panel = 0 }, initialTab = if (panel == 3) 1 else 0,
        )
    }
    edit?.let { m -> MarkDialog(book, m, { edit = null }) { marks = ReaderStore.marksOf(book.id) } }
    if (goTo) GoToDialog(count, current + 1, onDismiss = { goTo = false }) { go(it - 1); menu = false }
    if (search && source != null) PageSearchDialog(source, count, onDismiss = { search = false }) { p -> search = false; menu = false; go(p) }
    if (pageNote) PageNoteDialog(current + 1, onDismiss = { pageNote = false }) { text ->
        scope.launch {
            ReaderStore.addMark(ctx, ReaderMark(0, book.id, 2, current, note = text, text = "", pct = (current + 1f) / count.coerceAtLeast(1)))
            marks = ReaderStore.marksOf(book.id)
        }
    }
}

@Composable
private fun PageNoteDialog(page: Int, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var t by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Заметка к странице $page") },
        text = { OutlinedTextField(t, { t = it }, minLines = 4, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Мысль, цитата, вопрос…") }) },
        confirmButton = { TextButton(onClick = { if (t.isNotBlank()) onSave(t.trim()); onDismiss() }) { Text(btnLabel("Сохранить")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) } },
    )
}

/** Страница в режиме листания: щипок — увеличение, двойное нажатие — приблизить. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ZoomPage(src: PageSource, cache: LruCache<String, Bitmap>, i: Int, w: Int, crop: Boolean, filter: ColorFilter?, onTap: (Offset) -> Unit) {
    val bmp by produceState<Bitmap?>(cache.get("$i:$w:$crop"), i, w, crop) {
        if (value == null) value = src.render(i, w)?.let { b -> if (crop) withContext(Dispatchers.Default) { cropMargins(b) } else b }?.also { cache.put("$i:$w:$crop", it) }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { z, pan, _ ->
        scale = (scale * z).coerceIn(1f, 5f)
        offset = if (scale <= 1.01f) Offset.Zero else offset + pan * scale
    }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(i) {
                detectTapGestures(
                    onDoubleTap = { if (scale > 1.01f) { scale = 1f; offset = Offset.Zero } else scale = 2.2f },
                    onTap = { o -> if (scale <= 1.01f) onTap(o) },
                )
            }
            .transformable(state, canPan = { scale > 1.01f }),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b == null) CircularProgressIndicator(Modifier.size(28.dp))
        else Image(
            b.asImageBitmap(), null, Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            contentScale = ContentScale.Fit, colorFilter = filter,
        )
    }
}

/** Страница в ленте: во всю ширину, высота по пропорциям страницы. */
@Composable
private fun ListPage(src: PageSource, cache: LruCache<String, Bitmap>, i: Int, w: Int, crop: Boolean, filter: ColorFilter?) {
    val bmp by produceState<Bitmap?>(cache.get("$i:$w:$crop"), i, w, crop) {
        if (value == null) value = src.render(i, w)?.let { b -> if (crop) withContext(Dispatchers.Default) { cropMargins(b) } else b }?.also { cache.put("$i:$w:$crop", it) }
    }
    val b = bmp
    val ratio = b?.let { it.width / it.height.toFloat() } ?: src.aspect(i) ?: 0.7f
    Box(Modifier.fillMaxWidth().aspectRatio(ratio.coerceIn(.2f, 3f)).padding(bottom = 4.dp), contentAlignment = Alignment.Center) {
        if (b == null) CircularProgressIndicator(Modifier.size(24.dp))
        else Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, colorFilter = filter)
    }
}

/** Поиск по тексту PDF или текстовому слою DjVu. */
@Composable
private fun PageSearchDialog(src: PageSource, count: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val extra = LocalExtra.current
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    var progress by remember { mutableStateOf("") }
    LaunchedEffect(q) {
        results = emptyList()
        val needle = q.trim().lowercase()
        if (needle.length < 2) { progress = ""; return@LaunchedEffect }
        delay(400)
        val out = mutableListOf<Pair<Int, String>>()
        var anyText = false
        for (p in 0 until count) {
            progress = "Страница ${p + 1} из $count…"
            val t = src.text(p)
            if (t.isNotBlank()) anyText = true
            val low = t.lowercase()
            var from = 0
            while (out.size < 300) {
                val at = low.indexOf(needle, from); if (at < 0) break
                out += p to t.substring((at - 40).coerceAtLeast(0), (at + needle.length + 60).coerceAtMost(t.length)).replace(Regex("\\s+"), " ")
                from = at + 1
            }
            results = out.toList()
            if (out.size >= 300) break
        }
        progress = if (!anyText) "В файле нет текстового слоя — это скан, искать можно только по картинкам." else "Найдено: ${out.size}"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Поиск") },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Слово или фраза") })
                Text(progress, fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(vertical = 4.dp))
                LazyColumn(Modifier.fillMaxWidth().height(360.dp)) {
                    items(results.size) { k ->
                        val (p, snip) = results[k]
                        Column(Modifier.fillMaxWidth().clickable { onPick(p) }.padding(vertical = 8.dp)) {
                            Text("Страница ${p + 1}", fontSize = 11.sp, color = extra.dim)
                            Text("…$snip…", fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(btnLabel("Закрыть")) } },
    )
}
