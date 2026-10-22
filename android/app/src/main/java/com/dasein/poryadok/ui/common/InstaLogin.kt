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
                    Text("Страница не загрузилась ($err). Проверьте интернет и VPN и нажмите «Повторить».", fontSize = 12.sp, color = extra.danger, modifier = Modifier.weight(1f))
                    TextButton(onClick = { failed = null; web?.loadUrl(InstaWeb.LOGIN_URL) }) { Text(btnLabel("Повторить")) }
                }
            }
            AndroidView(
                factory = { c ->
                    WebView(c).apply {
                        InstaWeb.setup(this)
                        web = this
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean =
                                InstaWeb.foreign(request.url?.toString())
                            override fun onReceivedError(view: WebView, request: android.webkit.WebResourceRequest, error: android.webkit.WebResourceError) {
                                if (request.isForMainFrame) failed = error.description?.toString() ?: "ошибка сети"
                            }
                            override fun onPageFinished(view: WebView, u: String) {
                                logged = InstaWeb.loggedIn()
                                if (logged) InstaWeb.flush()
                            }
                        }
                        loadUrl(InstaWeb.LOGIN_URL)
                    }
                },
                onRelease = { runCatching { it.stopLoading(); it.destroy() } },
                modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds(),
            )
        }
    }
}
