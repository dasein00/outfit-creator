package com.dasein.poryadok.ui.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.dasein.poryadok.ui.common.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.reader.BookManifest
import com.dasein.poryadok.system.ReaderMark
import com.dasein.poryadok.system.ReaderStore
import com.dasein.poryadok.ui.common.Button
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.HGap
import com.dasein.poryadok.ui.common.IconAction
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.btnLabel
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.launch

/** Открытая книга: текстовые форматы — страницами в WebView, PDF / DjVu / комиксы — картинками страниц. */
@Composable
fun ReaderBookScreen(nav: NavHostController, id: Long) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { ReaderStore.load(ctx) }
    val data by ReaderStore.state.collectAsState()
    val book = data.books.firstOrNull { it.id == id }
    val extra = LocalExtra.current
    if (book == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Книга не найдена", color = extra.dim) }
        return
    }
    if (book.paged) { PagedReader(nav, book); return }
    var manifest by remember(id) { mutableStateOf<BookManifest?>(null) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    LaunchedEffect(id) {
        runCatching { ReaderStore.prepare(ctx, book) }.onSuccess { manifest = it }.onFailure { error = it.message ?: it.toString() }
    }
    val m = manifest
    when {
        error != null -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Не удалось открыть книгу", fontWeight = FontWeight.SemiBold)
            Text(error!!, color = extra.dim, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            Button(onClick = { nav.popBackStack() }, modifier = Modifier.padding(top = 16.dp)) { Text(btnLabel("Назад")) }
        }
        m == null -> Column(Modifier.fillMaxSize().background(pageTheme(data.settings.theme).bg), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text("Готовлю книгу…", color = extra.dim, modifier = Modifier.padding(top = 12.dp))
        }
        else -> ReflowReader(nav, book, m, ReaderStore.cacheDir(ctx, book.id))
    }
}

/** Все выделения и заметки из всех книг: поиск, переход к месту, выгрузка текстом. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReaderNotesScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    LaunchedEffect(Unit) { ReaderStore.load(ctx) }
    val data by ReaderStore.state.collectAsState()
    var q by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(-1) }
    var edit by remember { mutableStateOf<ReaderMark?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val books = data.books.associateBy { it.id }
    val list = data.marks.filter { m ->
        books.containsKey(m.bookId) && (kind < 0 || m.kind == kind || (kind == 1 && m.kind == 2)) &&
            (q.isBlank() || m.text.contains(q, true) || m.note.contains(q, true) || books[m.bookId]?.title?.contains(q, true) == true)
    }.sortedByDescending { it.createdAt }
    Screen(
        "Заметки и цитаты", onBack = { nav.popBackStack() },
        actions = {
            if (list.isNotEmpty()) IconAction("d18/13", "Выгрузить") {
                shareText(ctx, list.groupBy { it.bookId }.entries.joinToString("\n\n————\n\n") { (bid, ms) -> books[bid]?.let { exportNotes(it, ms) }.orEmpty() })
            }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 32.dp)) {
            item {
                OutlinedTextField(q, { q = it }, placeholder = { Text(btnLabel("Поиск по заметкам")) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("Все", kind < 0) { kind = -1 }
                    Pill("Выделения и заметки", kind == 1) { kind = 1 }
                    Pill("Закладки", kind == 0) { kind = 0 }
                }
                if (list.isEmpty()) Text(
                    "Здесь собираются выделения, заметки и закладки из всех книг. В книге выделите текст долгим нажатием и выберите «Выделить» или «Заметка».",
                    color = extra.dim, fontSize = 13.sp, modifier = Modifier.padding(vertical = 16.dp),
                )
            }
            items(list, key = { it.id }) { m ->
                val b = books[m.bookId] ?: return@items
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(14.dp)).background(extra.card)
                        .combinedClickable(onClick = { scope.launch { ReaderStore.saveBook(ctx, (ReaderStore.book(b.id) ?: b).copy(chapter = m.chapter, offset = m.start)); nav.navigate(readerBook(b.id)) } }, onLongClick = { edit = m }).padding(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (m.kind == 1) Box(Modifier.size(10.dp).clip(CircleShape).background(MARK_COLORS[m.color.coerceIn(0, 4)]))
                        else Glyph(if (m.kind == 0) "d17/10" else "d18/00", 16.dp, badge = false)
                        HGap(8.dp)
                        Text("${b.title} · ${(m.pct * 100).toInt()}%", fontSize = 11.sp, color = extra.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (m.text.isNotBlank()) Text(m.text, fontSize = 14.sp, maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    if (m.note.isNotBlank()) Text("✎ ${m.note}", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
    edit?.let { m -> data.books.firstOrNull { it.id == m.bookId }?.let { b -> MarkDialog(b, m, { edit = null }) {} } }
}
