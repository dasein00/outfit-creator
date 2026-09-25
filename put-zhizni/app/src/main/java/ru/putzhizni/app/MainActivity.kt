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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifications.ensureChannel(this)

        web = WebView(this)
        setContentView(web)
        bridge = Bridge(this)

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

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url
                if (url.host != HOST) return null
                var path = url.path ?: "/"
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
                    i.type = "*/*"
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

        if (savedInstanceState != null) web.restoreState(savedInstanceState)
        else web.loadUrl("https://$HOST/index.html")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        web.evaluateJavascript("window.__onResume&&window.__onResume()", null)
    }

    override fun onPause() {
        web.evaluateJavascript("window.__onPause&&window.__onPause()", null)
        web.onPause()
        super.onPause()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
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

    fun js(code: String) = runOnUiThread { web.evaluateJavascript(code, null) }

    fun setBars(hex: String, light: Boolean) = runOnUiThread {
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
        }
    }

    companion object {
        const val HOST = "putzhizni.local"
        const val REQ_CHOOSER = 11
        const val REQ_SAVE = 12
        const val REQ_OPEN = 13
        const val REQ_NOTIF = 21
        const val REQ_STEPS = 22
        val PERM_NOTIF = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else ""
        val PERM_STEPS = if (Build.VERSION.SDK_INT >= 29) Manifest.permission.ACTIVITY_RECOGNITION else ""

        fun mimeOf(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
            "html" -> "text/html"
            "js" -> "application/javascript"
            "css" -> "text/css"
            "png" -> "image/png"
            "svg" -> "image/svg+xml"
            "json" -> "application/json"
            "webp" -> "image/webp"
            "jpg", "jpeg" -> "image/jpeg"
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
      r.onload=function(){var s=String(r.result); Android.saveFileBase64(name, b.type||'application/octet-stream', s.substring(s.indexOf(',')+1));};
      r.readAsDataURL(b); return;
    }
    return click.call(this);
  };
  window.print=function(){Android.print(document.title||'Путь жизни');};
  var bar=document.createElement('div');
  bar.innerHTML='<button style="position:fixed;left:10px;top:10px;z-index:99999;padding:10px 14px;border-radius:20px;border:1px solid #c9b48a;background:#f7efdd;color:#241c15;font:15px sans-serif;box-shadow:0 2px 8px rgba(0,0,0,.15)">← Путь жизни</button>';
  bar.firstChild.onclick=function(){location.href='https://putzhizni.local/index.html#/more'};
  document.body.appendChild(bar.firstChild);
})();
"""
    }
}
