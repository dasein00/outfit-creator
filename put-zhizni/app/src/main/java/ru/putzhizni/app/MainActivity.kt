package ru.putzhizni.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

/**
 * Единственный экран приложения: WebView с офлайн-интерфейсом из assets/www.
 * Интерфейс отдаётся с виртуального адреса https://putzhizni.local/, чтобы у страницы
 * было нормальное происхождение (localStorage, fetch к погодному API).
 */
class MainActivity : Activity() {

    lateinit var web: WebView
    lateinit var bridge: Bridge
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var webAlive = false
    private val renderCrashes = ArrayList<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        CrashLog.guard(this, "Канал уведомлений") { Notifications.ensureChannel(this) }
        bridge = Bridge(this)

        // Состояние WebView (история переходов) больше не сохраняется в Bundle: при долгой работе оно
        // разрастается сверх лимита Android в 1 МБ, и при сворачивании приложение падало
        // (TransactionTooLargeException). Данные и так живут в localStorage — запоминаем только экран.
        val route = intent?.getStringExtra("route").orEmpty().ifEmpty { savedInstanceState?.getString("route").orEmpty() }
        try { createWeb(route) } catch (e: Throwable) {
            // WebView недоступен (например, системный компонент WebView обновляется) — не вылетаем, а объясняем.
            CrashLog.write(this, "WebView не создан: " + android.util.Log.getStackTraceString(e).take(3000))
            webAlive = false
            setContentView(android.widget.TextView(this).apply {
                setPadding(48, 96, 48, 48); textSize = 17f
                text = "Не удалось открыть экран: системный компонент Android System WebView недоступен или обновляется.\n\nОбновите «Android System WebView» и «Chrome» в Google Play и откройте приложение снова."
            })
        }
        CrashLog.guard(this, "Фоновое обновление") { Background.schedule(this) }
    }

    /** Режим облегчённой графики включается сам после повторных падений отрисовки (или вручную в настройках). */
    private fun lite(): Boolean = getSharedPreferences("flags", MODE_PRIVATE).getBoolean("lite", false)
    fun setLite(on: Boolean) { getSharedPreferences("flags", MODE_PRIVATE).edit().putBoolean("lite", on).apply() }

    private fun currentRoute(): String = try {
        val u = web.url ?: ""
        if (u.contains("index.html") && u.contains("#/")) u.substringAfter("#/") else ""
    } catch (_: Throwable) { "" }

    private fun createWeb(route: String) {
        web = WebView(this)
        webAlive = true
        setContentView(web)

        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.mediaPlaybackRequiresUserGesture = false
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.textZoom = 100
        web.overScrollMode = View.OVER_SCROLL_NEVER
        web.addJavascriptInterface(bridge, "Android")
        // Пока экран открыт, система не должна выгружать процесс отрисовки ради памяти (оболочки
        // Realme/ColorOS делают это агрессивнее других); в фоне — обычный приоритет.
        try { web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true) } catch (_: Throwable) {}

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url
                if (url.host != HOST) return null
                var path = url.path ?: "/"
                if (path.startsWith("/photos/")) {
                    val f = Photos.file(this@MainActivity, path.removePrefix("/photos/"))
                    return if (f != null) WebResourceResponse("image/jpeg", null, f.inputStream())
                    else WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                }
                if (path == "/" || path.isEmpty()) path = "/index.html"
                return try {
                    val stream = assets.open("www" + path)
                    val resp = WebResourceResponse(mimeOf(path), "utf-8", stream)
                    resp.responseHeaders = mapOf("Cache-Control" to "no-cache")
                    resp
                } catch (e: Exception) {
                    WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(),
                        ByteArrayInputStream(ByteArray(0)))
                }
            }

            // Процесс отрисовки WebView упал (чаще всего — нехватка памяти). Без этого обработчика Android
            // закрывает всё приложение; вместо этого записываем причину и пересоздаём экран.
            override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                val route = currentRoute()
                CrashLog.write(this@MainActivity, "WebView: процесс отрисовки завершён (" + (if (detail.didCrash()) "сбой" else "выгружен системой из-за памяти") + "), экран: " + route)
                webAlive = false
                try { (view.parent as? android.view.ViewGroup)?.removeView(view); view.destroy() } catch (_: Throwable) {}
                // Два падения за 3 минуты — включаем облегчённую графику.
                val now = System.currentTimeMillis()
                renderCrashes.removeAll { now - it > 180_000 }
                renderCrashes.add(now)
                if (renderCrashes.size >= 2 && !lite()) { setLite(true); CrashLog.write(this@MainActivity, "Включён облегчённый режим графики") }
                // Новый WebView вместо recreate(): пересоздание Activity с мёртвым WebView само могло падать.
                if (!isFinishing && !isDestroyed) CrashLog.guard(this@MainActivity, "Перезапуск экрана") { createWeb(route) }
                return true
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.host == HOST) return false
                // Внешние ссылки открываем в браузере.
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, url)); true
                } catch (e: Exception) { true }
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (url.contains("numerology.html")) view.evaluateJavascript(NUMEROLOGY_SHIM, null)
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = callback
                return try {
                    val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
                    i.addCategory(Intent.CATEGORY_OPENABLE)
                    // только фото (расклады, обрезка) — сразу галерея; смешанный список (фото + PDF + текст) —
                    // любые файлы, но с подсказкой нужных типов
                    val acc = params.acceptTypes.flatMap { it.split(",") }.map { it.trim() }.filter { it.isNotEmpty() }
                    val mimes = acc.mapNotNull { t -> when {
                        t.contains("/") -> t
                        t.equals(".pdf", true) -> "application/pdf"
                        t.equals(".txt", true) -> "text/plain"
                        t.equals(".json", true) -> "application/json"
                        else -> null } }.distinct()
                    if (mimes.isNotEmpty() && mimes.all { it.startsWith("image") }) i.type = "image/*"
                    else { i.type = "*/*"; if (mimes.size > 1) i.putExtra(Intent.EXTRA_MIME_TYPES, mimes.toTypedArray()) }
                    startActivityForResult(i, REQ_CHOOSER)
                    true
                } catch (e: Exception) {
                    fileChooserCallback = null; false
                }
            }

            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                android.util.Log.d("PutZhizni", "${m.message()} @${m.sourceId()}:${m.lineNumber()}")
                return true
            }
        }

        web.loadUrl("https://$HOST/index.html" + (if (lite()) "?lite=1" else "") + if (route.isNotEmpty()) "#/$route" else "")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!webAlive) return
        val route = intent.getStringExtra("route").orEmpty()
        if (route.isNotEmpty()) {
            if (web.url?.contains("index.html") == true) js("window.__openRoute&&window.__openRoute(" + org.json.JSONObject.quote(route) + ")")
            else web.loadUrl("https://$HOST/index.html#/$route")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("route", currentRoute().take(200))
    }

    override fun onResume() {
        super.onResume()
        if (!webAlive) return
        CrashLog.guard(this, "onResume") {
            web.onResume()
            web.evaluateJavascript("window.__onResume&&window.__onResume()", null)
        }
    }

    override fun onPause() {
        if (webAlive) CrashLog.guard(this, "onPause") {
            web.evaluateJavascript("window.__onPause&&window.__onPause()", null)
            web.onPause()
        }
        super.onPause()
    }

    // Система просит освободить память — интерфейс сбрасывает необязательные справочники.
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (webAlive && level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) CrashLog.guard(this, "onTrimMemory") {
            web.evaluateJavascript("window.__onLowMemory&&window.__onLowMemory($level)", null)
        }
    }

    override fun onDestroy() {
        if (webAlive) try { webAlive = false; web.destroy() } catch (_: Throwable) {}
        super.onDestroy()
    }

    /** Действие в главном потоке; ошибка записывается в журнал сбоев, а не закрывает приложение. */
    fun ui(block: () -> Unit) = runOnUiThread { CrashLog.guard(this, "UI") { block() } }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!webAlive) { @Suppress("DEPRECATION") super.onBackPressed(); return }
        if (web.url?.contains("numerology.html") == true) {
            web.loadUrl("https://$HOST/index.html#/more")
            return
        }
        web.evaluateJavascript("window.__onBack?window.__onBack():false") { r ->
            if (r != "true") {
                @Suppress("DEPRECATION")
                super.onBackPressed()
            }
        }
    }

    fun js(code: String) = ui { if (webAlive) web.evaluateJavascript(code, null) }

    fun setBars(hex: String, light: Boolean) = ui {
        try {
            val c = Color.parseColor(hex)
            window.statusBarColor = c
            window.navigationBarColor = c
            var flags = window.decorView.systemUiVisibility
            flags = if (light) flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            else flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv() and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            window.decorView.systemUiVisibility = flags
        } catch (_: Exception) {}
    }

    fun askPermission(perm: String, code: Int): Boolean {
        if (checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) return true
        requestPermissions(arrayOf(perm), code)
        return false
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        val ok = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        when (requestCode) {
            REQ_NOTIF -> bridge.callback("notif", ok.toString())
            REQ_SMS -> if (ok) bridge.smsRead() else bridge.callback("sms", "")
            REQ_STEPS -> {
                bridge.callback("stepsPerm", ok.toString())
                if (ok) bridge.requestSteps()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val uri = if (resultCode == RESULT_OK) data?.data else null
        when (requestCode) {
            REQ_CHOOSER -> {
                fileChooserCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
                fileChooserCallback = null
            }
            REQ_SAVE -> bridge.onSaveResult(uri)
            REQ_OPEN -> bridge.onOpenResult(uri)
            REQ_HC -> bridge.onHcPermissionResult()
        }
    }

    companion object {
        const val HOST = "putzhizni.local"
        const val REQ_CHOOSER = 11
        const val REQ_SAVE = 12
        const val REQ_OPEN = 13
        const val REQ_NOTIF = 21
        const val REQ_STEPS = 22
        const val REQ_SMS = 23
        const val REQ_HC = 14
        val PERM_NOTIF = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else ""
        val PERM_STEPS = if (Build.VERSION.SDK_INT >= 29) Manifest.permission.ACTIVITY_RECOGNITION else ""

        fun mimeOf(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
            "html" -> "text/html"
            "js", "mjs" -> "application/javascript"
            "css" -> "text/css"
            "png" -> "image/png"
            "svg" -> "image/svg+xml"
            "json" -> "application/json"
            "webp" -> "image/webp"
            "jpg", "jpeg" -> "image/jpeg"
            "woff2" -> "font/woff2"
            else -> "application/octet-stream"
        }

        /** Для встроенного модуля нумерологии: скачивание файлов и печать через Android. */
        const val NUMEROLOGY_SHIM = """
(function(){
  if(window.__pzShim)return; window.__pzShim=true;
  var blobs={}; var orig=URL.createObjectURL.bind(URL);
  URL.createObjectURL=function(b){var u=orig(b); if(b instanceof Blob) blobs[u]=b; return u;};
  var click=HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click=function(){
    var b=blobs[this.href];
    if(this.download && b){
      var name=this.download, r=new FileReader();
      r.onload=function(){var s=String(r.result); Android.saveFileBase64(name, (b.type||'application/octet-stream').split(';')[0], s.substring(s.indexOf(',')+1));};
      r.readAsDataURL(b); return;
    }
    return click.call(this);
  };
  window.print=function(){Android.print(document.title||'Путь жизни');};
})();
"""
    }
}
