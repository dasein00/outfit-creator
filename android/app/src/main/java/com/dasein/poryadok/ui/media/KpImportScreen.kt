@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.media

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.MediaItem
import com.dasein.poryadok.data.MediaKind
import com.dasein.poryadok.data.MediaList
import com.dasein.poryadok.data.MediaListItem
import com.dasein.poryadok.data.MediaStatus
import com.dasein.poryadok.logic.MediaHit
import com.dasein.poryadok.logic.MediaParse
import com.dasein.poryadok.system.MediaSearch
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.HGap
import com.dasein.poryadok.ui.common.InfoBox
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.common.rememberUiFlag
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Скрипт собирает фильмы со страницы: ссылки на /film/ID/ или /series/ID/, название, год, картинку и оценку. */
private const val COLLECT_JS = """
(function(){
  var out={}, order=[];
  var links=document.querySelectorAll('a[href*="/film/"],a[href*="/series/"]');
  for(var i=0;i<links.length;i++){
    var a=links[i]; var href=a.getAttribute('href')||'';
    var m=href.match(/\/(film|series)\/(\d+)\/?(\?.*)?$/); if(!m) continue;
    var id=m[2];
    var box=a.closest('li,tr,article,[class*="item"],[class*="Item"],[class*="film"],[class*="Film"],[class*="card"],[class*="Card"]')||a.parentElement;
    if(!out[id]){out[id]={id:id,type:m[1],title:'',text:'',img:'',vote:''};order.push(id);}
    var o=out[id];
    var t=(a.textContent||'').trim();
    if(t && !/^[\d.,\s]+$/.test(t) && (o.title==='' || t.length>o.title.length && t.length<200)) o.title=t;
    var txt=box?(box.innerText||''):'';
    if(txt.length>o.text.length && txt.length<1500) o.text=txt;
    if(!o.img && box){var im=box.querySelector('img'); if(im) o.img=im.getAttribute('src')||im.getAttribute('data-src')||'';}
    if(!o.vote && box){var v=box.querySelector('[class*="myVote"],[class*="MyVote"],[class*="userVote"],[class*="UserVote"],[class*="my-vote"],[class*="vote_"]'); if(v){var n=(v.textContent||'').trim(); if(/^\d{1,2}$/.test(n)) o.vote=n;}}
  }
  return JSON.stringify(order.map(function(k){return out[k]}));
})()
"""

private const val NEXT_JS = """
(function(){
  var a=document.querySelector('a[rel="next"]'); if(a&&a.href) return a.href;
  var all=document.querySelectorAll('a');
  for(var i=0;i<all.length;i++){var t=(all[i].textContent||'').trim().toLowerCase();
    if(t==='»'||t==='›'||t==='→'||t.indexOf('вперёд')===0||t.indexOf('вперед')===0||t.indexOf('следующая')===0||t.indexOf('далее')===0) return all[i].href||'';}
  var p=document.querySelector('[class*="paginator"] [class*="next"] a,[class*="pagination"] a[class*="next"],[class*="Pagination"] a[class*="next"]');
  return p&&p.href?p.href:'';
})()
"""

private const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"

private fun decodeJsString(v: String?): String =
    runCatching { (Json.parseToJsonElement(v ?: "null") as? JsonPrimitive)?.contentOrNull }.getOrNull().orEmpty()

/**
 * Импорт своих списков с Кинопоиска через встроенный браузер: вы входите в аккаунт и открываете
 * страницу оценок или папки, а приложение собирает фильмы со всех её страниц.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun KpImportScreen(nav: NavHostController, kind: Int) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    var web by remember { mutableStateOf<WebView?>(null) }
    var opened by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("https://www.kinopoisk.ru/") }
    var startUrl by remember { mutableStateOf("https://www.kinopoisk.ru/") }
    var profile by remember { mutableStateOf("") }
    var allPages by rememberUiFlag("kp_all_pages", true)
    var collecting by remember { mutableStateOf(false) }
    var pages by remember { mutableIntStateOf(0) }
    val found = remember { mutableStateMapOf<String, MediaParse.KpItem>() }
    var review by remember { mutableStateOf(false) }
    // Сбор по страницам: после загрузки очередной страницы — собрать и перейти к следующей.
    var autoNext by remember { mutableStateOf(false) }

    fun collect(then: (() -> Unit)? = null) {
        val w = web ?: return
        w.evaluateJavascript(COLLECT_JS) { res ->
            val items = runCatching { MediaParse.kpItems(decodeJsString(res)) }.getOrDefault(emptyList())
            items.forEach { found[it.id] = it }
            pages++
            if (then != null) then()
            else Toast.makeText(ctx, "С этой страницы: ${items.size}. Всего собрано: ${found.size}", Toast.LENGTH_SHORT).show()
        }
    }

    fun nextPage() {
        val w = web ?: return
        w.evaluateJavascript(NEXT_JS) { res ->
            val next = decodeJsString(res)
            if (autoNext && next.isNotBlank() && next != w.url && pages < 60) w.loadUrl(next)
            else {
                autoNext = false; collecting = false
                Toast.makeText(ctx, "Готово: собрано ${found.size} с $pages стр.", Toast.LENGTH_LONG).show()
                if (found.isNotEmpty()) review = true
            }
        }
    }

    fun start() {
        collecting = true
        pages = 0
        if (allPages) { autoNext = true; collect { nextPage() } } else collect { collecting = false; if (found.isNotEmpty()) review = true }
    }

    Screen("Импорт с Кинопоиска", onBack = { if (web?.canGoBack() == true) web?.goBack() else nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.padding(horizontal = 12.dp)) {
                InfoBox("kp_import", "Как перенести свои списки") {
                    listOf(
                        "Войдите в аккаунт Кинопоиска прямо здесь (если попросит — пройдите проверку «я не робот»).",
                        "Откройте нужный список: «Оценки», «Буду смотреть», «Любимые» или любую свою папку. Быстрее всего — вписать номер профиля ниже и нажать «Мои оценки».",
                        "Нажмите «Собрать». С включённым «Все страницы» приложение само пролистает список до конца.",
                        "Проверьте найденное, выберите статус и список — и добавьте. Постеры скачаются сами; с токеном Кинопоиска карточки ещё и дополнятся режиссёрами и актёрами.",
                    ).forEachIndexed { i, t ->
                        Row(Modifier.padding(top = 4.dp)) {
                            Text("${i + 1}.", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(end = 6.dp))
                            Text(t, fontSize = 13.sp)
                        }
                    }
                    Text(
                        "Автоматической синхронизации с аккаунтом нет: у Кинопоиска нет открытого доступа к личным спискам. Повторный импорт дублей не создаёт — так можно обновлять коллекцию.",
                        fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Box(Modifier.weight(1f)) { TextInput(profile, { profile = it }, "Номер или ссылка профиля") }
                    HGap(6.dp)
                    OutlinedButton(onClick = {
                        val id = Regex("(\\d{3,})").find(profile)?.groupValues?.get(1)
                        if (id == null) Toast.makeText(ctx, "Номер профиля — цифры из ссылки kinopoisk.ru/user/…", Toast.LENGTH_LONG).show()
                        else {
                            val target = "https://www.kinopoisk.ru/user/$id/votes/"
                            if (web == null) { startUrl = target; opened = true } else web?.loadUrl(target)
                        }
                    }) { Text("Мои оценки", fontSize = 12.sp) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(allPages, { allPages = it })
                    Text("  Все страницы", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("Собрано: ${found.size}", fontSize = 13.sp, color = extra.dim)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { start() }, enabled = !collecting && web != null, modifier = Modifier.weight(1f)) {
                        Text(if (collecting) "Собираю… стр. $pages" else "Собрать")
                    }
                    OutlinedButton(onClick = { review = true }, enabled = found.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Проверить и добавить") }
                }
                Text(url, fontSize = 10.sp, color = extra.dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 2.dp))
            }
            if (!opened) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Button(onClick = { opened = true }) { Text("Открыть Кинопоиск") }
                }
            } else AndroidView(
                factory = { c ->
                    WebView(c).apply {
                        setBackgroundColor(android.graphics.Color.WHITE)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = DESKTOP_UA
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, u: String, favicon: Bitmap?) { url = u }
                            override fun onPageFinished(view: WebView, u: String) {
                                url = u
                                if (autoNext) scope.launch { delay(1200); collect { nextPage() } }
                            }
                        }
                        loadUrl(startUrl)
                        web = this
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds(),
            )
        }
    }
    if (review) ReviewSheet(kind, found.values.toList(), { review = false }) { n ->
        review = false
        Toast.makeText(ctx, "Добавлено: $n. Постеры и подробности подгружаются в фоне.", Toast.LENGTH_LONG).show()
        nav.popBackStack()
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ReviewSheet(kind: Int, items: List<MediaParse.KpItem>, onDismiss: () -> Unit, onDone: (Int) -> Unit) {
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    val lists by observe(emptyList()) { Graph.extra.mediaLists() }
    var status by remember { mutableIntStateOf(if (items.any { it.vote != null }) MediaStatus.DONE else MediaStatus.PLANNED) }
    var listId by remember { mutableStateOf(0L) }
    var newList by remember { mutableStateOf(false) }
    val chosen = remember { mutableStateMapOf<String, Boolean>().apply { items.forEach { put(it.id, true) } } }
    var busy by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("Найдено: ${items.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Статус для всех", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MediaStatus.names(kind).forEachIndexed { i, n -> Pill(n, status == i) { status = i } }
            }
            Text("Добавить ещё и в список", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("Без списка", listId == 0L) { listId = 0L }
                lists.filter { it.kind == -1 || it.kind == kind }.forEach { l -> Pill(l.name, listId == l.id, glyph = l.glyph.ifBlank { "ui:folder" }) { listId = l.id } }
                Pill("+ Новый", false) { newList = true }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                TextButton(onClick = { items.forEach { chosen[it.id] = true } }) { Text("Выбрать все") }
                TextButton(onClick = { items.forEach { chosen[it.id] = false } }) { Text("Снять все") }
            }
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(items, key = { it.id }) { it2 ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(chosen[it2.id] == true, { v -> chosen[it2.id] = v })
                        Column(Modifier.weight(1f)) {
                            Text(it2.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(it2.year?.toString(), if (it2.series) "сериал" else null, it2.vote?.let { v -> "моя оценка $v" }).joinToString(" · "),
                                fontSize = 12.sp, color = extra.dim,
                            )
                        }
                    }
                }
            }
            Gap(8.dp)
            Button(
                onClick = {
                    busy = true
                    scope.launch {
                        val n = importKp(items.filter { chosen[it.id] == true }, kind, status, listId)
                        busy = false
                        onDone(n)
                    }
                },
                enabled = !busy && chosen.values.any { it },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (busy) "Добавляю…" else "Добавить ${chosen.values.count { it }}") }
            Gap(24.dp)
        }
    }
    if (newList) ListNameDialog("Новый список", "", { newList = false }) { name, glyph ->
        newList = false
        scope.launch { listId = Graph.extra.upsertMediaList(MediaList(name = name, glyph = glyph, createdAt = System.currentTimeMillis())) }
    }
}

/**
 * Добавляет собранное в коллекцию без дублей (по номеру Кинопоиска и по «название + год»),
 * кладёт в выбранный список и в фоне скачивает постеры и подробности.
 */
private suspend fun importKp(items: List<MediaParse.KpItem>, kind: Int, status: Int, listId: Long): Int {
    val existing = Graph.extra.allMedia()
    val now = System.currentTimeMillis()
    val added = ArrayList<Long>()
    items.forEachIndexed { i, k ->
        val ext = "kp:${k.id}"
        val itemKind = if (k.series) MediaKind.SERIES else MediaKind.MOVIE
        val same = existing.firstOrNull { it.externalId == ext }
            ?: existing.firstOrNull { it.title.equals(k.title, true) && it.year == k.year && it.kind != MediaKind.BOOK }
        val id = if (same != null) {
            if (k.vote != null && same.myRating == 0) Graph.extra.upsertMedia(same.copy(myRating = k.vote))
            same.id
        } else {
            Graph.extra.upsertMedia(
                MediaItem(
                    kind = itemKind, title = k.title, year = k.year, posterUrl = k.posterUrl, status = status,
                    myRating = k.vote ?: 0, source = "Кинопоиск", externalId = ext,
                    url = "https://www.kinopoisk.ru/${if (k.series) "series" else "film"}/${k.id}/", createdAt = now - i,
                ),
            ).also { added += it }
        }
        if (listId != 0L) Graph.extra.addToMediaList(MediaListItem(listId, id, addedAt = now))
    }
    Graph.scope.launch { enrich(added) }
    return added.size
}

/** Фоновая догрузка: подробности с Кинопоиска (если есть токен) и постеры. */
private suspend fun enrich(ids: List<Long>) {
    val ctx = Graph.app
    val hasToken = Graph.prefs.now().kinopoiskToken.isNotBlank()
    ids.forEach { id ->
        val m = Graph.extra.mediaItemNow(id) ?: return@forEach
        runCatching {
            var item = m
            if (hasToken && m.externalId.startsWith("kp:")) {
                val full = MediaSearch.details(MediaHit(kind = m.kind, title = m.title, year = m.year, posterUrl = m.posterUrl, source = "Кинопоиск", externalId = m.externalId))
                item = item.copy(
                    kind = full.kind, originalTitle = full.originalTitle.ifBlank { item.originalTitle }, description = full.description.ifBlank { item.description },
                    genres = full.genres.ifBlank { item.genres }, creators = full.creators.ifBlank { item.creators }, cast = full.cast.ifBlank { item.cast },
                    countries = full.countries.ifBlank { item.countries }, length = full.length.ifBlank { item.length },
                    externalRating = full.rating ?: item.externalRating, posterUrl = full.posterUrl.ifBlank { item.posterUrl },
                )
            }
            if (item.poster.isBlank() && item.posterUrl.isNotBlank()) MediaSearch.downloadPoster(ctx, item.posterUrl)?.let { item = item.copy(poster = it) }
            if (item != m) Graph.extra.upsertMedia(item)
        }
        delay(250)
    }
}
