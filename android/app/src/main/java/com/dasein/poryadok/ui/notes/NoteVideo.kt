package com.dasein.poryadok.ui.notes

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dasein.poryadok.logic.VideoEmbed
import com.dasein.poryadok.ui.common.Text
import com.dasein.poryadok.ui.common.TextButton
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.VideoAddButtons
import com.dasein.poryadok.ui.common.VideoPlayer
import com.dasein.poryadok.ui.common.btnLabel
import com.dasein.poryadok.ui.common.safeBars
import com.dasein.poryadok.ui.theme.LocalExtra

private fun openExternal(ctx: Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * Блок «Видео или ссылка» в статье: ролик YouTube, Instagram, VK, Rutube, TikTok, Vimeo смотрится прямо в заметке,
 * видео с телефона — встроенным плеером, остальные ссылки — карточкой (открывается в браузере).
 */
@Composable
internal fun NoteVideoBlock(
    media: String,
    caption: String,
    selected: Boolean,
    readOnly: Boolean,
    onSelect: () -> Unit,
    onCaption: (String) -> Unit,
    onPick: () -> Unit,
) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(12.dp))
            .then(if (selected) Modifier.border(2.dp, scheme.primary, RoundedCornerShape(12.dp)) else Modifier),
    ) {
        val embed = remember(media) { if (media.startsWith("http")) VideoEmbed.of(media) else null }
        when {
            media.isBlank() -> Box(
                Modifier.fillMaxWidth().height(90.dp).background(extra.card).clickable(onClick = onPick),
                contentAlignment = Alignment.Center,
            ) { Text(btnLabel("Нажмите, чтобы вставить ссылку на видео или выбрать файл"), color = extra.dim, fontSize = 13.sp) }
            media.startsWith("/") -> VideoPlayer(media, Modifier.fillMaxWidth(), maxHeight = 420.dp)
            embed != null -> {
                EmbedWeb(embed)
            }
            else -> Row(
                Modifier.fillMaxWidth().background(extra.card).clickable { openExternal(ctx, media) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🔗", fontSize = 22.sp)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(VideoEmbed.provider(media), fontWeight = FontWeight.SemiBold)
                    Text(media, fontSize = 12.sp, color = extra.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        // Строка под роликом: сайт, «открыть в приложении» и выделение блока (чтобы удалить или переместить).
        if (media.isNotBlank()) Row(
            Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (media.startsWith("/")) "▶ Видео с телефона" else "▶ " + VideoEmbed.provider(media),
                fontSize = 12.sp, color = extra.dim, modifier = Modifier.weight(1f), maxLines = 1,
            )
            if (media.startsWith("http")) Text(
                btnLabel("Открыть ↗"), fontSize = 12.sp, color = scheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { openExternal(ctx, media) }.padding(4.dp),
            )
        }
        BasicTextField(
            value = caption,
            readOnly = readOnly,
            onValueChange = { onCaption(it.replace("\n", " ")) },
            textStyle = TextStyle(fontSize = 13.sp, color = extra.dim),
            cursorBrush = SolidColor(scheme.primary),
            modifier = Modifier.fillMaxWidth().padding(6.dp),
            decorationBox = { inner -> Box { if (caption.isEmpty()) Text(btnLabel("Подпись или заметка к видео"), fontSize = 13.sp, color = extra.dim.copy(alpha = .5f)); inner() } },
        )
    }
}

/** Встроенный плеер сайта (iframe) с полноэкранным режимом. Ссылки, нажатые внутри плеера, открываются снаружи. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EmbedWeb(e: VideoEmbed.Embed) {
    val ctx = LocalContext.current
    var custom by remember { mutableStateOf<View?>(null) }
    var customCb by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    val html = "<!doctype html><html><head><meta name='viewport' content='width=device-width, initial-scale=1'>" +
        "<style>html,body{margin:0;padding:0;background:#000;height:100%;overflow:hidden}" +
        "iframe{position:absolute;top:0;left:0;width:100%;height:100%;border:0}</style></head><body>" +
        "<iframe src='${e.url}' allow='autoplay; encrypted-media; picture-in-picture; fullscreen' allowfullscreen referrerpolicy='strict-origin-when-cross-origin'></iframe>" +
        "</body></html>"
    // Вертикальные ролики (Shorts, клипы, TikTok) — не во всю ширину, чтобы не занимать несколько экранов.
    Box(Modifier.fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { c ->
                WebView(c).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = true
                    webChromeClient = object : WebChromeClient() {
                        override fun onShowCustomView(view: View, callback: CustomViewCallback) { custom = view; customCb = callback }
                        override fun onHideCustomView() { custom = null; customCb = null }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            if (!request.isForMainFrame) return false
                            openExternal(ctx, request.url.toString())
                            return true
                        }
                    }
                    // Адрес страницы задаёт Referer — без него YouTube не показывает встроенный ролик.
                    loadDataWithBaseURL("https://dasein.app/", html, "text/html", "utf-8", null)
                }
            },
            onRelease = { runCatching { it.stopLoading(); it.destroy() } },
            modifier = Modifier.fillMaxWidth(if (e.ratio < 1f) .62f else 1f).aspectRatio(e.ratio),
        )
    }
    custom?.let { v ->
        Dialog(
            onDismissRequest = { customCb?.onCustomViewHidden(); custom = null; customCb = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black).safeBars()) {
                AndroidView(factory = { (v.parent as? ViewGroup)?.removeView(v); v }, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** Вставить видео: ссылка (YouTube, Instagram, VK, Rutube, TikTok, Vimeo, статья) или файл с телефона. */
@Composable
internal fun VideoLinkDialog(onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    var url by remember { mutableStateOf("") }
    val norm = url.trim().let { if (it.isNotEmpty() && !it.startsWith("http") && '.' in it && ' ' !in it) "https://$it" else it }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(btnLabel("Видео или ссылка")) },
        text = {
            Column {
                TextInput(url, { url = it }, "Ссылка: YouTube, Instagram, VK, Rutube, TikTok, статья…")
                TextButton(onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()?.trim()?.let { url = it }
                }) { Text(btnLabel("Вставить из буфера обмена")) }
                val e = if (VideoEmbed.isLink(norm)) VideoEmbed.of(norm) else null
                Text(
                    when {
                        e != null -> "▶ ${e.provider}: видео будет смотреться прямо в заметке."
                        VideoEmbed.isLink(norm) -> "Ссылка сохранится карточкой — нажатие откроет её в браузере."
                        else -> "Видео с YouTube, Instagram, VK, Rutube, TikTok и Vimeo смотрится прямо в статье. Или выберите видео с телефона:"
                    },
                    fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp),
                )
                VideoAddButtons("pages") { path -> onDone(path) }
            }
        },
        confirmButton = { TextButton(enabled = VideoEmbed.isLink(norm), onClick = { onDone(norm) }) { Text(btnLabel("Вставить")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) } },
    )
}
