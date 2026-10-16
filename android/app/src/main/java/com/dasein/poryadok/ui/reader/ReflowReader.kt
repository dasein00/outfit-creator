package com.dasein.poryadok.ui.reader

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.dasein.poryadok.reader.BookManifest
import com.dasein.poryadok.reader.Html
import com.dasein.poryadok.system.ReaderBook
import com.dasein.poryadok.system.ReaderMark
import com.dasein.poryadok.system.ReaderSettings
import com.dasein.poryadok.system.ReaderStore
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.TextButton
import com.dasein.poryadok.ui.common.btnLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.Locale

private val SELECT_ACTIONS = listOf("Выделить", "Заметка", "Копировать", "Перевести", "Поделиться", "Найти", "Озвучить")

/** WebView с меню выделенного текста читалки вместо стандартного. */
@SuppressLint("ViewConstructor")
class BookWebView(ctx: Context) : WebView(ctx) {
    /** Пункт меню и выделение (JSON из R.selection()). */
    var onSelectAction: ((Int, String) -> Unit)? = null

    override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode? = super.startActionMode(Wrap(callback), type)
    override fun startActionMode(callback: ActionMode.Callback): ActionMode? = super.startActionMode(Wrap(callback))

    private inner class Wrap(val base: ActionMode.Callback) : ActionMode.Callback2() {
        private fun fill(menu: Menu) {
            menu.clear()
            SELECT_ACTIONS.forEachIndexed { i, t -> menu.add(0, 100 + i, i, t) }
        }
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean { base.onCreateActionMode(mode, menu); fill(menu); return true }
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean { base.onPrepareActionMode(mode, menu); fill(menu); return true }
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val action = item.itemId - 100
            evaluateJavascript("R.selection()") { res ->
                onSelectAction?.invoke(action, jsString(res))
                mode.finish()
                evaluateJavascript("R.clearSelection()", null)
            }
            return true
        }
        override fun onDestroyActionMode(mode: ActionMode) = base.onDestroyActionMode(mode)
        override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
            if (base is ActionMode.Callback2) base.onGetContentRect(mode, view, outRect) else super.onGetContentRect(mode, view, outRect)
        }
    }
}

/** Результат evaluateJavascript — строка JSON; достаём из неё обычную строку. */
fun jsString(res: String?): String {
    if (res == null || res == "null") return ""
    return runCatching { Json.parseToJsonElement(res).jsonPrimitive.content }.getOrDefault(res.trim('"'))
}

private fun mime(path: String) = when (path.substringAfterLast('.').lowercase()) {
    "html", "htm", "xhtml" -> "text/html"; "js" -> "application/javascript"; "css" -> "text/css"
    "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "gif" -> "image/gif"; "webp" -> "image/webp"; "svg" -> "image/svg+xml"; "bmp" -> "image/bmp"
    "djvu", "djv" -> "image/vnd.djvu"
    else -> "application/octet-stream"
}

/** Ответы WebView для https://book.local/: страница читалки из assets и файлы разобранной книги. */
fun bookResponse(ctx: Context, dir: File?, request: WebResourceRequest, extra: (String) -> File? = { null }): WebResourceResponse? {
    val u = request.url
    if (u.host != "book.local") return WebResourceResponse("text/plain", "utf-8", 404, "Not found", emptyMap(), "".byteInputStream())
    val path = u.path?.trimStart('/').orEmpty()
    return runCatching {
        val stream = when {
            path in setOf("reader.html", "reader.js", "djvu.html", "djvu.js") -> ctx.assets.open("reader/$path")
            else -> {
                val f = extra(path) ?: dir?.let { File(it, path) }
                if (f == null || dir != null && !f.canonicalPath.startsWith(dir.canonicalPath) && extra(path) == null || !f.isFile) return@runCatching WebResourceResponse("text/plain", "utf-8", 404, "Not found", emptyMap(), "".byteInputStream())
                f.inputStream()
            }
        }
        WebResourceResponse(mime(path), "utf-8", stream).apply { responseHeaders = mapOf("Cache-Control" to "no-cache", "Access-Control-Allow-Origin" to "*") }
    }.getOrNull()
}

/** Состояние открытой книги: глава, страница, связь с WebView, чтение вслух. */
class ReflowController(val ctx: Context, var book: ReaderBook, val m: BookManifest, val dir: File, val scope: CoroutineScope) {
    private val main = Handler(Looper.getMainLooper())
    var web: BookWebView? = null
    var settings = ReaderSettings()
    var theme: PageTheme? = null
    private var ready = false
    val chapter = mutableIntStateOf(book.chapter.coerceIn(0, (m.chapters.size - 1).coerceAtLeast(0)))
    val page = mutableIntStateOf(0)
    val pages = mutableIntStateOf(1)
    val offset = mutableIntStateOf(book.offset)
    var chapterLen = 1
    val menu = mutableStateOf(false)
    val loading = mutableStateOf(true)
    val marks = mutableStateOf(ReaderStore.marksOf(book.id))
    /** Откуда ушли по ссылке — чтобы вернуться. */
    val back = mutableStateOf<Pair<Int, Int>?>(null)
    val image = mutableStateOf<ImageBitmap?>(null)
    val editMark = mutableStateOf<ReaderMark?>(null)
    val translate = mutableStateOf<String?>(null)
    val searchQuery = mutableStateOf<String?>(null)
    var lastAction = System.currentTimeMillis()
    private var afterLoad: (() -> Unit)? = null
    private var saveJob: Job? = null
    private val before = IntArray(m.chapters.size + 1).also { a -> m.chapters.forEachIndexed { i, c -> a[i + 1] = a[i] + c.chars } }
    val total = before.last().coerceAtLeast(1)

    fun js(code: String) { web?.evaluateJavascript(code, null) }
    private fun q(s: String) = JsonPrimitive(s).toString()

    fun progress(ch: Int = chapter.intValue, off: Int = offset.intValue): Float {
        val c = m.chapters.getOrNull(ch) ?: return 0f
        val inCh = if (chapterLen > 1 && ch == chapter.intValue) off.toFloat() / chapterLen * c.chars else off.toFloat()
        return ((before[ch] + inCh.coerceIn(0f, c.chars.toFloat())) / total).coerceIn(0f, 1f)
    }

    fun settingsJson(s: ReaderSettings, t: PageTheme): String {
        val o = JsonObject(
            mapOf(
                "paged" to JsonPrimitive(s.paged), "fs" to JsonPrimitive(s.fontSize), "lh" to JsonPrimitive(s.lineHeight), "m" to JsonPrimitive(s.margin),
                "font" to JsonPrimitive(FONTS.getOrNull(s.font)?.second ?: "serif"), "align" to JsonPrimitive(if (s.justify) "justify" else "left"),
                "hyph" to JsonPrimitive(s.hyphens), "indent" to JsonPrimitive(s.indent), "para" to JsonPrimitive(s.paraSpace), "anim" to JsonPrimitive(s.animation),
                "pt" to JsonPrimitive(16), "pb" to JsonPrimitive(10),
                "theme" to JsonObject(mapOf("bg" to JsonPrimitive(hex(t.bg)), "fg" to JsonPrimitive(hex(t.fg)), "dim" to JsonPrimitive(hex(t.dim)), "link" to JsonPrimitive(hex(t.link)), "accent" to JsonPrimitive(hex(t.link)), "dark" to JsonPrimitive(t.dark))),
                "files" to kotlinx.serialization.json.JsonArray(m.chapters.map { JsonPrimitive(it.file) }),
                "endText" to JsonPrimitive("Конец книги"), "noteGo" to JsonPrimitive("Перейти к сноске →"),
                "lang" to JsonPrimitive(book.lang.ifBlank { "ru" }.take(2).lowercase()),
            ),
        )
        return o.toString()
    }

    fun applySettings(s: ReaderSettings, t: PageTheme) {
        settings = s; theme = t
        web?.setBackgroundColor(t.bg.toArgb())
        if (ready) js("R.setup(${settingsJson(s, t)})")
    }

    private fun marksJson(ch: Int): String = kotlinx.serialization.json.JsonArray(
        marks.value.filter { it.chapter == ch && it.kind == 1 }.map {
            JsonObject(mapOf("id" to JsonPrimitive(it.id), "s" to JsonPrimitive(it.start), "e" to JsonPrimitive(it.end), "c" to JsonPrimitive(it.color), "n" to JsonPrimitive(it.note.isNotBlank())))
        },
    ).toString()

    /** at — выражение JS: число (символ), 'start', 'end', 'якорь' или {f: доля}. */
    fun load(ch: Int, at: String) {
        val c = ch.coerceIn(0, m.chapters.lastIndex)
        chapter.intValue = c
        loading.value = true
        js("R.load($c, ${q(m.chapters[c].file)}, $at, {marks: ${marksJson(c)}, last: ${c == m.chapters.lastIndex}})")
    }

    /** Загрузить главу и выполнить действие, когда она разложится по страницам. */
    fun loadThen(ch: Int, at: String, then: () -> Unit) { afterLoad = then; load(ch, at) }

    fun refreshMarks() {
        marks.value = ReaderStore.marksOf(book.id)
        js("R.setMarks(${marksJson(chapter.intValue)})")
    }

    fun next() { lastAction = System.currentTimeMillis(); js("R.next()") }
    fun prev() { lastAction = System.currentTimeMillis(); js("R.prev()") }

    /** Переход по доле всей книги (ползунок). */
    fun goFraction(f: Float) {
        val target = (f.coerceIn(0f, 1f) * total).toInt()
        var ch = m.chapters.indices.lastOrNull { before[it] <= target } ?: 0
        if (ch > m.chapters.lastIndex) ch = m.chapters.lastIndex
        val inside = m.chapters[ch].chars.takeIf { it > 0 }?.let { (target - before[ch]).toFloat() / it } ?: 0f
        if (ch == chapter.intValue) js("R.goFraction($inside)") else load(ch, "{f: $inside}")
    }

    fun goToc(ch: Int, anchor: String) { back.value = chapter.intValue to offset.intValue; if (ch == chapter.intValue && anchor.isNotBlank()) js("R.goAnchor(${q(anchor)})") else load(ch, if (anchor.isBlank()) "'start'" else q(anchor)) }
    fun goMark(mk: ReaderMark) { back.value = chapter.intValue to offset.intValue; if (mk.chapter == chapter.intValue) js("R.goOffset(${mk.start})") else load(mk.chapter, "${mk.start}") }
    fun goBack() { back.value?.let { (c, o) -> back.value = null; if (c == chapter.intValue) js("R.goOffset($o)") else load(c, "$o") } }

    fun savePosition(now: Boolean = false) {
        saveJob?.cancel()
        val ch = chapter.intValue; val off = offset.intValue; val p = progress()
        saveJob = scope.launch {
            if (!now) delay(800)
            ReaderStore.savePosition(ctx, book.id, ch, off, p, (total / 1800).coerceAtLeast(1))
        }
    }

    // ---------- JS → приложение ----------
    inner class Bridge {
        @JavascriptInterface fun ready() = main.post { onReady() }.let { }
        @JavascriptInterface fun loaded(ch: Int) = main.post { loading.value = false; afterLoad?.let { afterLoad = null; it() } }.let { }
        @JavascriptInterface fun pos(json: String) = main.post { onPos(json) }.let { }
        @JavascriptInterface fun edge(dir: Int) = main.post {
            val c = chapter.intValue + dir
            if (c in m.chapters.indices) load(c, if (dir > 0) "'start'" else "'end'")
            else if (dir > 0) { menu.value = true }
        }.let { }
        @JavascriptInterface fun tap(z: String) = main.post { if (z == "menu") menu.value = !menu.value }.let { }
        @JavascriptInterface fun link(ch: Int, anchor: String) = main.post { goToc(ch, anchor) }.let { }
        @JavascriptInterface fun mark(id: String) = main.post { marks.value.firstOrNull { it.id.toString() == id }?.let { editMark.value = it } }.let { }
        @JavascriptInterface fun ext(url: String) = main.post { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }.let { }
        @JavascriptInterface fun image(src: String) = main.post {
            scope.launch {
                val f = File(dir, src.substringAfter("book.local/").trimStart('/'))
                image.value = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() }.getOrNull() }
            }
        }.let { }
        @JavascriptInterface fun log(s: String) { android.util.Log.d("DASEIN", "reader: $s") }
    }

    private fun onReady() {
        ready = true
        theme?.let { js("R.setup(${settingsJson(settings, it)})") }
        load(chapter.intValue, "${book.offset}")
    }

    private fun onPos(json: String) {
        val o = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return
        val ch = o["ch"]?.jsonPrimitive?.int ?: return
        if (ch != chapter.intValue) return
        page.intValue = o["page"]?.jsonPrimitive?.int ?: 0
        pages.intValue = o["pages"]?.jsonPrimitive?.int ?: 1
        offset.intValue = o["offset"]?.jsonPrimitive?.int ?: 0
        chapterLen = (o["len"]?.jsonPrimitive?.int ?: 1).coerceAtLeast(1)
        lastAction = System.currentTimeMillis()
        val atEnd = runCatching { o["atEnd"]?.jsonPrimitive?.boolean }.getOrNull() == true
        savePosition()
        if (atEnd && ch == m.chapters.lastIndex) scope.launch { ReaderStore.savePosition(ctx, book.id, ch, offset.intValue, 1f) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun create(context: Context): BookWebView = BookWebView(context).also { w ->
        web = w
        w.settings.javaScriptEnabled = true
        w.settings.allowFileAccess = false
        w.settings.allowContentAccess = false
        w.settings.textZoom = 100
        w.settings.builtInZoomControls = false
        w.settings.setSupportZoom(false)
        w.isVerticalScrollBarEnabled = false
        w.isHorizontalScrollBarEnabled = false
        w.overScrollMode = View.OVER_SCROLL_NEVER
        theme?.let { w.setBackgroundColor(it.bg.toArgb()) }
        w.addJavascriptInterface(Bridge(), "A")
        w.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? = bookResponse(ctx, dir, request)
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.host != "book.local") runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, request.url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                return true
            }
        }
        w.onSelectAction = { action, json -> onSelect(action, json) }
        w.loadUrl("https://book.local/reader.html")
    }

    // ---------- выделение текста ----------
    private fun onSelect(action: Int, json: String) {
        val o = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return
        val s = o["s"]?.jsonPrimitive?.int ?: return
        val e = o["e"]?.jsonPrimitive?.int ?: return
        val text = o["text"]?.jsonPrimitive?.content.orEmpty().trim()
        if (text.isEmpty()) return
        when (action) {
            0, 1 -> scope.launch {
                val mk = ReaderStore.addMark(ctx, ReaderMark(0, book.id, 1, chapter.intValue, s, e, text.take(4000), color = lastColor(), pct = progress(chapter.intValue, s)))
                refreshMarks()
                if (action == 1) editMark.value = mk
            }
            2 -> copyText(ctx, text)
            3 -> translate.value = text
            4 -> shareText(ctx, quote(book, text))
            5 -> searchQuery.value = text.take(100)
            6 -> speakFrom(s)
        }
    }

    private fun lastColor() = marks.value.filter { it.kind == 1 }.maxByOrNull { it.createdAt }?.color ?: 0

    // ---------- закладки ----------
    fun bookmarkHere(): ReaderMark? {
        val perPage = (chapterLen / pages.intValue.coerceAtLeast(1)).coerceAtLeast(1)
        return marks.value.firstOrNull { it.kind == 0 && it.chapter == chapter.intValue && it.start in offset.intValue until offset.intValue + perPage }
    }

    fun toggleBookmark() {
        val here = bookmarkHere()
        if (here != null) { scope.launch { ReaderStore.deleteMark(ctx, here.id); marks.value = ReaderStore.marksOf(book.id) }; return }
        val ch = chapter.intValue; val off = offset.intValue
        web?.evaluateJavascript("R.ttsText($off, 160)") { res ->
            val text = runCatching { Json.parseToJsonElement(jsString(res)).jsonObject["text"]?.jsonPrimitive?.content }.getOrNull().orEmpty()
            scope.launch {
                ReaderStore.addMark(ctx, ReaderMark(0, book.id, 0, ch, off, off, text.replace(Regex("\\s+"), " ").trim().take(160), pct = progress(ch, off)))
                marks.value = ReaderStore.marksOf(book.id)
            }
        }
    }

    // ---------- чтение вслух ----------
    private var tts: TextToSpeech? = null
    val speaking = mutableStateOf(false)
    private var lastEnd = -1
    private var ttsReady = false

    fun speakFrom(from: Int) {
        speaking.value = true
        val t = tts
        if (t == null) {
            tts = TextToSpeech(ctx.applicationContext) { status ->
                main.post {
                    if (status != TextToSpeech.SUCCESS) { speaking.value = false; android.widget.Toast.makeText(ctx, "Синтез речи недоступен", android.widget.Toast.LENGTH_SHORT).show(); return@post }
                    ttsReady = true
                    val lang = book.lang.ifBlank { "ru" }.take(2)
                    tts?.setLanguage(Locale(lang))
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String) { val (s, e) = id.split(':').let { it[1].toInt() to it[2].toInt() }; main.post { if (speaking.value) js("R.ttsMark($s, $e)") } }
                        override fun onDone(id: String) { val e = id.split(':')[2].toInt(); if (e == lastEnd) main.post { if (speaking.value) speakChunk(e) } }
                        @Deprecated("") override fun onError(id: String) { main.post { speaking.value = false } }
                    })
                    speakChunk(from)
                }
            }
        } else if (ttsReady) { t.stop(); speakChunk(from) }
    }

    private fun speakChunk(from: Int) {
        val t = tts ?: return
        t.setSpeechRate(settings.ttsRate)
        web?.evaluateJavascript("R.ttsText($from, 1200)") { res ->
            val o = runCatching { Json.parseToJsonElement(jsString(res)).jsonObject }.getOrNull()
            val s0 = o?.get("s")?.jsonPrimitive?.int ?: 0
            val text = o?.get("text")?.jsonPrimitive?.content.orEmpty()
            if (text.isBlank()) {
                // Глава закончилась — следующая.
                val c = chapter.intValue + 1
                if (c in m.chapters.indices) { afterLoad = { speakChunk(0) }; load(c, "'start'") } else stopSpeaking()
                return@evaluateJavascript
            }
            var pos = 0
            val parts = Regex("[^.!?…\\n]+[.!?…]*[\\s»\"”)]*|\\n+").findAll(text).toList()
            parts.forEach { mt ->
                val seg = mt.value
                val st = s0 + mt.range.first
                val en = s0 + mt.range.last + 1
                pos = en
                if (seg.isNotBlank()) t.speak(seg.trim(), TextToSpeech.QUEUE_ADD, Bundle(), "u:$st:$en")
            }
            lastEnd = s0 + text.length
            // Последний кусок мог быть пустым — привязываем окончание к реальному последнему высказыванию.
            parts.lastOrNull { it.value.isNotBlank() }?.let { lastEnd = s0 + it.range.last + 1 }
            if (pos == 0) stopSpeaking()
        }
    }

    fun stopSpeaking() {
        speaking.value = false
        tts?.stop()
        js("R.ttsClear()")
    }

    fun release() {
        savePosition(now = true)
        tts?.stop(); tts?.shutdown(); tts = null
        web?.let { it.removeJavascriptInterface("A"); it.destroy() }
        web = null
    }
}

@Composable
fun ReflowReader(nav: NavHostController, book: ReaderBook, manifest: BookManifest, dir: File) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val data by ReaderStore.state.collectAsState()
    val s = data.settings
    val theme = pageTheme(s.theme)
    val ctl = remember(book.id) { ReflowController(ctx, book, manifest, dir, scope) }
    LaunchedEffect(s, theme) { ctl.applySettings(s, theme) }
    var panel by remember { mutableStateOf(0) } // 0 — нет, 1 — оглавление, 2 — вид, 3 — закладки и заметки
    val menu by ctl.menu
    ReaderWindow(s, immersive = !menu && panel == 0)
    ReadingTimer(book.id) { ctl.lastAction }
    DisposableEffect(book.id) {
        ReaderKeys.handler = { d -> if (ReaderStore.state.value.settings.volumeKeys && !ctl.menu.value) { if (d > 0) ctl.next() else ctl.prev(); true } else false }
        onDispose { ReaderKeys.handler = null; ctl.release() }
    }
    BackHandler(enabled = menu || panel != 0 || ctl.speaking.value) {
        when {
            panel != 0 -> panel = 0
            menu -> ctl.menu.value = false
            else -> ctl.stopSpeaking()
        }
    }
    val ch = ctl.chapter.intValue
    val pct = (ctl.progress() * 100).toInt()
    Box(Modifier.fillMaxSize().background(theme.bg)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(if (s.fullscreen) WindowInsets.displayCutout else WindowInsets.systemBars)) {
            AndroidView(factory = { c -> ctl.create(c) }, modifier = Modifier.weight(1f).fillMaxWidth())
            StatusLine(
                manifest.chapters.getOrNull(ch)?.title.orEmpty(),
                "${ctl.page.intValue + 1}/${ctl.pages.intValue} · $pct%", theme.dim, s.showClock,
            )
        }
        if (ctl.loading.value) CircularProgressIndicator(Modifier.align(Alignment.Center).size(36.dp), color = theme.dim)
        if (ctl.bookmarkHere() != null && !menu) Glyph("d17/10", 22.dp, Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.displayCutout).padding(top = 2.dp, end = 14.dp), badge = false)
        ctl.back.value?.let {
            Text(
                "← Вернуться", color = theme.link, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.BottomStart).windowInsetsPadding(WindowInsets.navigationBars).padding(start = 12.dp, bottom = 28.dp)
                    .clip(RoundedCornerShape(14.dp)).background(theme.bg).clickable { ctl.goBack() }.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        if (ctl.speaking.value && !menu) TtsBar(theme, s, Modifier.align(Alignment.BottomCenter), onRate = { r -> scope.launch { ReaderStore.saveSettings(ctx, s.copy(ttsRate = r)) } }) { ctl.stopSpeaking() }
        if (menu) ReaderMenu(
            title = book.title, sub = manifest.chapters.getOrNull(ch)?.title.orEmpty(), theme = theme, progress = ctl.progress(),
            pageLabel = "Глава ${ch + 1} из ${manifest.chapters.size} · стр. ${ctl.page.intValue + 1}/${ctl.pages.intValue} · $pct%",
            bookmarked = ctl.bookmarkHere() != null,
            onBack = { nav.popBackStack() }, onClose = { ctl.menu.value = false },
            onSeek = { ctl.goFraction(it) },
            onPrevPart = { if (ch > 0) ctl.load(ch - 1, "'start'") }, onNextPart = { if (ch < manifest.chapters.lastIndex) ctl.load(ch + 1, "'start'") },
            onToc = { panel = 1 }, onMarks = { panel = 3 }, onLook = { panel = 2 }, onBookmark = { ctl.toggleBookmark() },
            onSearch = { ctl.searchQuery.value = "" }, onSpeak = { ctl.menu.value = false; ctl.speakFrom(-1) },
            onNight = { scope.launch { ReaderStore.saveSettings(ctx, s.copy(theme = if (theme.dark) 1 else 2)) } }, night = theme.dark,
        )
        if (panel == 2) BottomPanel(onDismiss = { panel = 0 }) { ReaderSettingsPanel(s, paged = false) { n -> scope.launch { ReaderStore.saveSettings(ctx, n) } } }
    }
    if (panel == 1 || panel == 3) {
        val toc = manifest.toc.mapIndexed { i, t -> Triple(t.title, t.level, i) }
        val cur = manifest.toc.indexOfLast { it.chapter <= ch }.coerceAtLeast(0)
        ContentsDialog(
            book, toc, cur, ctl.marks.value,
            markLabel = { mk -> "${manifest.chapters.getOrNull(mk.chapter)?.title?.take(40).orEmpty()} · ${(mk.pct * 100).toInt()}%" },
            onToc = { i -> manifest.toc.getOrNull(i)?.let { t -> ctl.goToc(t.chapter, t.anchor) }; panel = 0; ctl.menu.value = false },
            onMark = { mk -> ctl.goMark(mk); panel = 0; ctl.menu.value = false },
            onEditMark = { mk -> ctl.editMark.value = mk },
            onDismiss = { panel = 0 }, initialTab = if (panel == 3) 1 else 0,
        )
    }
    ctl.editMark.value?.let { mk -> MarkDialog(book, mk, { ctl.editMark.value = null }) { ctl.refreshMarks() } }
    ctl.translate.value?.let { t -> TranslateDialog(t) { ctl.translate.value = null } }
    ctl.image.value?.let { b -> ImageViewer(b) { ctl.image.value = null } }
    ctl.searchQuery.value?.let { q0 -> SearchDialog(q0, manifest, dir, onDismiss = { ctl.searchQuery.value = null }) { chIndex, k, q ->
        ctl.searchQuery.value = null; ctl.menu.value = false
        ctl.back.value = ctl.chapter.intValue to ctl.offset.intValue
        val code = "R.find(${JsonPrimitive(q)}, $k)"
        if (chIndex == ctl.chapter.intValue) ctl.js(code) else ctl.loadThen(chIndex, "'start'") { ctl.js(code) }
    } }
}

/** Панель снизу поверх страницы (настройки вида). */
@Composable
fun BottomPanel(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().clickable(onClick = onDismiss, indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() })) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(.62f).clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(MaterialTheme.colorScheme.surface).clickable(enabled = false) {}.windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp).size(40.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outline))
            content()
        }
    }
}

/** Меню поверх страницы: сверху — назад и действия, снизу — ползунок и разделы. */
@Composable
fun ReaderMenu(
    title: String, sub: String, theme: PageTheme, progress: Float, pageLabel: String, bookmarked: Boolean,
    onBack: () -> Unit, onClose: () -> Unit, onSeek: (Float) -> Unit, onPrevPart: () -> Unit, onNextPart: () -> Unit,
    onToc: () -> Unit, onMarks: () -> Unit, onLook: () -> Unit, onBookmark: () -> Unit, onSearch: () -> Unit, onSpeak: (() -> Unit)?,
    onNight: () -> Unit, night: Boolean, onGoTo: (() -> Unit)? = null, onNote: (() -> Unit)? = null,
) {
    val bar = MaterialTheme.colorScheme.surface.copy(alpha = .97f)
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().clickable(onClick = onClose, indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }))
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().background(bar).windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("←", fontSize = 20.sp) }
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sub.isNotBlank()) Text(sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = com.dasein.poryadok.ui.theme.LocalExtra.current.dim)
            }
            onNote?.let { MenuIcon("d18/00", it) }
            MenuIcon(if (bookmarked) "d17/10" else "d17/11", onBookmark)
            MenuIcon("reading/04", onSearch)
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(bar).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 12.dp, vertical = 6.dp)) {
            var v by remember(progress) { mutableFloatStateOf(progress) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onPrevPart) { Text("‹", fontSize = 22.sp) }
                Slider(v, { v = it }, Modifier.weight(1f), onValueChangeFinished = { onSeek(v) })
                TextButton(onClick = onNextPart) { Text("›", fontSize = 22.sp) }
            }
            Text(
                if (kotlin.math.abs(v - progress) > .001f) "${(v * 100).toInt()}%" else pageLabel, fontSize = 12.sp,
                color = com.dasein.poryadok.ui.theme.LocalExtra.current.dim, modifier = Modifier.align(Alignment.CenterHorizontally).clickable(enabled = onGoTo != null) { onGoTo?.invoke() },
            )
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                MenuButton("reading/05", "Оглавление", onToc)
                MenuButton("d18/00", "Заметки", onMarks)
                MenuButton("d01/09", "Вид", onLook)
                onSpeak?.let { MenuButton("books/09", "Вслух", it) }
                MenuButton(if (night) "d01/06" else "d15/00", if (night) "День" else "Ночь", onNight)
            }
        }
    }
}

@Composable
private fun MenuIcon(glyph: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Glyph(glyph, 24.dp, badge = false) }
}

@Composable
private fun MenuButton(glyph: String, label: String, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Glyph(glyph, 26.dp, badge = false)
        Text(label, fontSize = 11.sp)
    }
}

/** Плеер чтения вслух: скорость и стоп. */
@Composable
private fun TtsBar(theme: PageTheme, s: ReaderSettings, modifier: Modifier, onRate: (Float) -> Unit, onStop: () -> Unit) {
    Row(
        modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 24.dp).clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .96f)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph("books/09", 22.dp, badge = false)
        listOf(0.8f, 1f, 1.25f, 1.5f, 2f).forEach { r ->
            Text(
                "×${r.toString().trimEnd('0').trimEnd('.')}", fontSize = 13.sp, fontWeight = if (kotlin.math.abs(s.ttsRate - r) < .01f) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onRate(r) }.padding(horizontal = 7.dp, vertical = 6.dp),
            )
        }
        TextButton(onClick = onStop) { Text(btnLabel("Стоп")) }
    }
}

/** Поиск по всей книге: каждая находка с кусочком текста вокруг. */
@Composable
fun SearchDialog(initial: String, m: BookManifest, dir: File, onDismiss: () -> Unit, onPick: (Int, Int, String) -> Unit) {
    val extra = com.dasein.poryadok.ui.theme.LocalExtra.current
    var q by remember { mutableStateOf(initial) }
    var results by remember { mutableStateOf<List<Triple<Int, Int, String>>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(q) {
        if (q.trim().length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(350)
        searching = true
        val needle = q.trim().lowercase()
        results = withContext(Dispatchers.IO) {
            val out = mutableListOf<Triple<Int, Int, String>>()
            m.chapters.forEachIndexed { ci, c ->
                if (out.size >= 300) return@forEachIndexed
                val text = runCatching { Html.text(File(dir, c.file).readText()) }.getOrDefault("")
                val low = text.lowercase()
                var from = 0; var k = 0
                while (out.size < 300) {
                    val at = low.indexOf(needle, from)
                    if (at < 0) break
                    val snip = text.substring((at - 40).coerceAtLeast(0), (at + needle.length + 60).coerceAtMost(text.length)).replace(Regex("\\s+"), " ")
                    out += Triple(ci, k, snip)
                    k++; from = at + 1
                }
            }
            out
        }
        searching = false
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Поиск в книге") },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Слово или фраза") })
                Text(if (searching) "Ищу…" else if (q.trim().length >= 2) "Найдено: ${results.size}${if (results.size >= 300) "+" else ""}" else "", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(vertical = 4.dp))
                LazyColumn(Modifier.fillMaxWidth().height(380.dp)) {
                    items(results) { (ci, k, snip) ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(ci, k, q.trim()) }.padding(vertical = 8.dp)) {
                            Text(m.chapters[ci].title.take(60), fontSize = 11.sp, color = extra.dim)
                            Text("…$snip…", fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(btnLabel("Закрыть")) } },
    )
}
