package com.dasein.poryadok.ui.common

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dasein.poryadok.system.InstaWeb
import com.dasein.poryadok.ui.theme.LocalExtra

/**
 * Вход в Instagram во встроенном браузере — один раз. Instagram без входа всё чаще не показывает посты;
 * после входа подписи к постам загружаются по ссылке. Пароль вводится на самой странице Instagram, приложение его не видит.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InstaLoginDialog(onDone: () -> Unit) {
    val extra = LocalExtra.current
    var logged by remember { mutableStateOf(InstaWeb.loggedIn()) }
    var failed by remember { mutableStateOf<String?>(null) }
    var web by remember { mutableStateOf<WebView?>(null) }
    // Instagram часто отвечает ошибкой одной из страниц входа — пробуем по очереди другие адреса и вид «как компьютер».
    var attempt by remember { mutableStateOf(0) }
    // Одна неудача страницы приходит и как ошибка сети, и как ошибка HTTP — считаем её один раз.
    var failedAttempt by remember { mutableStateOf(-1) }
    fun open(w: WebView?, i: Int) {
        w ?: return
        val (u, desktop) = InstaWeb.LOGIN_TRIES[i % InstaWeb.LOGIN_TRIES.size]
        InstaWeb.setup(w, desktop)
        w.loadUrl(u)
    }
    fun onFail(w: WebView, why: String) {
        if (failedAttempt == attempt) return
        failedAttempt = attempt
        if (attempt + 1 < InstaWeb.LOGIN_TRIES.size) { attempt++; open(w, attempt) }
        else failed = why
    }
    fun close() { InstaWeb.flush(); onDone() }
    Dialog(onDismissRequest = { close() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeBars(withIme = true)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(btnLabel("Вход в Instagram"), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Text(
                        if (logged) "Вход выполнен — нажмите «Готово»." else "Войдите своим аккаунтом. Это нужно один раз, чтобы загружались подписи к постам.",
                        fontSize = 12.sp, color = if (logged) extra.ok else extra.dim,
                    )
                }
                Button(onClick = { close() }, modifier = Modifier.padding(start = 8.dp)) { Text(btnLabel("Готово")) }
            }
            failed?.let { err ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Instagram не открылся ($err). Проверьте, что VPN работает для всех приложений, а не только для браузера, и нажмите «Повторить». " +
                            "Можно и без входа: кнопка «Текст описания со скриншота» в окне рецепта.",
                        fontSize = 12.sp, color = extra.danger, modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { failed = null; attempt = 0; failedAttempt = -1; open(web, 0) }) { Text(btnLabel("Повторить")) }
                }
            }
            AndroidView(
                factory = { c ->
                    WebView(c).apply {
                        web = this
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean =
                                InstaWeb.foreign(request.url?.toString())
                            override fun onReceivedError(view: WebView, request: android.webkit.WebResourceRequest, error: android.webkit.WebResourceError) {
                                if (request.isForMainFrame) onFail(view, error.description?.toString() ?: "ошибка сети")
                            }
                            override fun onReceivedHttpError(view: WebView, request: android.webkit.WebResourceRequest, response: android.webkit.WebResourceResponse) {
                                if (request.isForMainFrame && response.statusCode >= 400) onFail(view, "код ${response.statusCode}")
                            }
                            override fun onPageFinished(view: WebView, u: String) {
                                logged = InstaWeb.loggedIn()
                                if (logged) InstaWeb.flush()
                            }
                        }
                        open(this, 0)
                    }
                },
                onRelease = { runCatching { it.stopLoading(); it.destroy() } },
                modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds(),
            )
        }
    }
}
