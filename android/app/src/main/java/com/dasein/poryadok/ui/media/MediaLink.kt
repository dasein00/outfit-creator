package com.dasein.poryadok.ui.media

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.data.MediaStatus
import com.dasein.poryadok.system.MediaSearch
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.Button
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.TextButton
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.launch

const val MEDIA_LINK_ROUTE = "mediaLink"

/** Ссылка, которой поделились из Кинопоиска или Шикимори. */
object MediaInbox {
    private var pending: String? = null
    fun put(url: String) { pending = url }
    fun take(): String? = pending.also { pending = null }
}

/**
 * Карточка по ссылке: вставьте ссылку на страницу фильма или сериала на Кинопоиске или аниме на Шикимори —
 * приложение заполнит карточку (название, год, постер, описание, жанры, режиссёров и актёров) и откроет её.
 */
@Composable
fun MediaLinkScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    val shared = remember { MediaInbox.take() }
    var url by remember { mutableStateOf(shared.orEmpty()) }
    var status by remember { mutableStateOf(MediaStatus.PLANNED) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun go() {
        val link = com.dasein.poryadok.system.RecipeLink.findUrl(url) ?: url.trim()
        if (link.isBlank() || busy) return
        busy = true; error = null
        scope.launch {
            runCatching {
                val hit = MediaSearch.byLink(link)
                val id = MediaSearch.add(ctx, hit, status)
                hit.kind to id
            }.onSuccess { (kind, id) ->
                nav.navigate(Routes.media(id, kind)) { popUpTo(MEDIA_LINK_ROUTE) { inclusive = true } }
            }.onFailure { error = it.message ?: "Не получилось" }
            busy = false
        }
    }
    LaunchedEffect(Unit) { if (shared != null) go() }
    Screen("Карточка по ссылке", onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp)) {
            Text("Вставьте ссылку на фильм или сериал на Кинопоиске (kinopoisk.ru/film/…) или на аниме на Шикимори (shikimori.one/animes/…). Можно и просто нажать «Поделиться» на странице Кинопоиска и выбрать DASEIN.", fontSize = 13.sp, color = extra.dim)
            TextInput(url, { url = it }, "Ссылка", Modifier.padding(top = 10.dp))
            TextButton(onClick = {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()?.let { url = it }
            }) { Text("Вставить из буфера обмена") }
            Text("Куда положить:", fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.padding(vertical = 6.dp)) {
                listOf(MediaStatus.PLANNED to "Хочу посмотреть", MediaStatus.IN_PROGRESS to "Смотрю", MediaStatus.DONE to "Просмотрено").forEach { (s, t) ->
                    Pill(t, status == s) { status = s }
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(3.dp))
                }
            }
            Button(onClick = { go() }, enabled = url.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(if (busy) "Заполняю карточку…" else "Создать карточку")
            }
            if (busy) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                CircularProgressIndicator(strokeWidth = 2.dp); Text("  Загружаю данные…", color = extra.dim)
            }
            error?.let { Text(it, color = extra.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)) }
        }
    }
}
