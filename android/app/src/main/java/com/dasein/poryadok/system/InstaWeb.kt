package com.dasein.poryadok.system

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.dasein.poryadok.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.resume

/**
 * Instagram во встроенном браузере (невидимом): страница открывается как у человека — со скриптами и сохранённым входом,
 * и подпись берётся из того, что браузер нарисовал. Вход выполняется один раз в окне «Войти в Instagram»;
 * куки браузера потом используются и обычными запросами.
 */
object InstaWeb {
    const val LOGIN_URL = "https://www.instagram.com/accounts/login/"

    /** Выполнен ли вход в Instagram во встроенном браузере (есть кука сессии). */
    fun loggedIn(): Boolean = runCatching {
        CookieManager.getInstance().getCookie("https://www.instagram.com/")?.contains("sessionid=") == true
    }.getOrDefault(false)

    /** Сохранить куки на диск — чтобы вход пережил перезапуск приложения. */
    fun flush() { runCatching { CookieManager.getInstance().flush() } }

    private val json = Json { isLenient = true }

    /**
     * Instagram страницам встроенного браузера часто отвечает переходом в своё приложение (intent://, instagram://)
     * или отказом по пометке «wv» в имени браузера — тогда WebView показывает «Не удаётся загрузить веб-страницу».
     * Поэтому браузер представляется обычным Chrome, а переходы не по http(s) игнорируются.
     */
    fun setup(w: WebView, desktop: Boolean = false) {
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.settings.userAgentString = if (desktop) DESKTOP_UA else mobileUa(w)
        runCatching { CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(w, true) }
        // Без заголовка с именем приложения — для сайта это обычный браузер.
        runCatching {
            if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST))
                androidx.webkit.WebSettingsCompat.setRequestedWithHeaderOriginAllowList(w.settings, emptySet())
        }
    }

    private var defaultUa: String? = null
    private fun mobileUa(w: WebView): String {
        val base = defaultUa ?: w.settings.userAgentString.also { defaultUa = it }
        return base.replace("; wv", "").replace(Regex("""Version/\d+(\.\d+)*\s"""), "")
    }

    const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

    /** Куда пробовать войти по очереди, если Instagram не отдаёт страницу: (адрес, как компьютер). */
    val LOGIN_TRIES = listOf(
        "https://www.instagram.com/" to false,
        "https://www.instagram.com/accounts/login/" to true,
        "https://www.instagram.com/" to true,
        "https://m.instagram.com/accounts/login/" to false,
    )

    /** Ссылка не для браузера (приложение Instagram, intent://, market://) — не открываем. */
    fun foreign(url: String?): Boolean = url != null && !url.startsWith("http://") && !url.startsWith("https://")

    // Заголовок, текст первого h1 (в нём подпись на странице поста) и вся страница.
    private const val JS = "(function(){var h=document.querySelector('h1');" +
        "return JSON.stringify([document.title||'',h?h.innerText:'',document.documentElement.outerHTML]);})()"

    /** Подпись к посту со страницы [url] или null. */
    suspend fun caption(url: String): String? {
        val parts = page(url) ?: return null
        val (_, h1, html) = parts
        return InstaCaption.fromHtml(html) ?: InstaCaption.fromMeta(html)
            ?: h1.trim().takeIf { it.length > 15 && !InstaCaption.isLoginWall(it) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun page(url: String): List<String>? = withTimeoutOrNull(30_000) {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val main = Handler(Looper.getMainLooper())
                val w = WebView(Graph.app)
                var done = false
                fun finish(v: List<String>?) {
                    if (done) return
                    done = true
                    runCatching { w.stopLoading(); w.destroy() }
                    if (cont.isActive) cont.resume(v)
                }
                setup(w)
                // Невидимому браузеру тоже нужен размер — иначе часть страниц не рисует содержимое.
                w.layout(0, 0, 1080, 2200)
                var scheduled = false
                w.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean = foreign(request.url?.toString())
                    override fun onPageFinished(view: WebView, u: String) {
                        if (scheduled) return
                        scheduled = true
                        // Даём скриптам дорисовать подпись.
                        main.postDelayed({
                            if (done) return@postDelayed
                            view.evaluateJavascript(JS) { r -> finish(decode(r)) }
                        }, 3000)
                    }
                }
                cont.invokeOnCancellation { main.post { if (!done) { done = true; runCatching { w.stopLoading(); w.destroy() } } } }
                w.loadUrl(url)
            }
        }
    }

    /** evaluateJavascript отдаёт строку в кавычках JSON, внутри которой наш JSON-массив. */
    private fun decode(r: String?): List<String>? = runCatching {
        val inner = json.parseToJsonElement(r ?: return null).jsonPrimitive.content
        (json.parseToJsonElement(inner) as JsonArray).map { it.jsonPrimitive.content }.takeIf { it.size == 3 }
    }.getOrNull()
}
