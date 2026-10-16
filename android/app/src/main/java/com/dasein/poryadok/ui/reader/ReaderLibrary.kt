package com.dasein.poryadok.ui.reader

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.dasein.poryadok.ui.common.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.MediaKind
import com.dasein.poryadok.logic.plural
import com.dasein.poryadok.system.ReaderBook
import com.dasein.poryadok.system.ReaderStore
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.Bar
import com.dasein.poryadok.ui.common.Button
import com.dasein.poryadok.ui.common.ButtonsRow
import com.dasein.poryadok.ui.common.ConfirmDialog
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.HGap
import com.dasein.poryadok.ui.common.IconAction
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.TextButton
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.btnLabel
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.rememberImage
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

const val READER_ROUTE = "reader"
const val READER_NOTES_ROUTE = "readerNotes"
fun readerBook(id: Long) = "readerBook/$id"

/** Файл, открытый из другого приложения («Открыть с помощью» → DASEIN), ждёт экрана читалки. */
object ReaderInbox { val pending = mutableStateOf<Uri?>(null) }

private val STATUS = listOf("Не начата", "Читаю", "Прочитана", "Хочу прочитать")
private val SORTS = listOf("Недавние", "Название", "Автор", "Добавлены", "Прогресс")

/** Значок формата и короткое описание прогресса книги. */
fun ReaderBook.progressLabel(): String = when {
    status == 2 -> "прочитана"
    progress <= 0f -> fmt.label
    else -> "${(progress * 100).toInt()}%"
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ReaderLibraryScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { ReaderStore.load(ctx) }
    val data by ReaderStore.state.collectAsState()
    var filter by remember { mutableStateOf(-1) }
    var q by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf<ReaderBook?>(null) }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }
    val s = data.settings

    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        importing = "…"
        scope.launch {
            val res = ReaderStore.import(ctx, uris) { name -> importing = name }
            importing = null
            val ok = res.mapNotNull { it.getOrNull() }
            errors = res.mapNotNull { it.exceptionOrNull()?.message ?: it.exceptionOrNull()?.toString() }
            if (ok.size == 1 && errors.isEmpty()) nav.navigate(readerBook(ok[0].id))
            else if (ok.isNotEmpty()) Toast.makeText(ctx, "Добавлено: ${ok.size} ${plural(ok.size, "книга", "книги", "книг")}", Toast.LENGTH_SHORT).show()
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { importUris(it) }
    // Книга, открытая из файлового менеджера.
    val inbox by ReaderInbox.pending
    LaunchedEffect(inbox) { inbox?.let { ReaderInbox.pending.value = null; importUris(listOf(it)) } }

    val books = data.books.filter { b ->
        (filter < 0 || b.status == filter || (filter == 0 && b.status == 0)) &&
            (q.isBlank() || b.title.contains(q, true) || b.author.contains(q, true) || b.sourceName.contains(q, true))
    }.let { l ->
        when (s.sort) {
            1 -> l.sortedBy { it.title.lowercase() }
            2 -> l.sortedBy { it.author.lowercase().ifBlank { "яяя" } }
            3 -> l.sortedByDescending { it.addedAt }
            4 -> l.sortedByDescending { it.progress }
            else -> l.sortedByDescending { maxOf(it.openedAt, it.addedAt) }
        }
    }
    val current = data.books.filter { it.openedAt > 0 && it.status == 1 }.maxByOrNull { it.openedAt }

    Screen(
        "Читалка", onBack = { nav.popBackStack() },
        actions = {
            IconAction("d18/00", "Заметки и цитаты") { nav.navigate(READER_NOTES_ROUTE) }
            IconAction(if (s.grid) "d18/02" else "d01/04", "Вид") { io { ReaderStore.saveSettings(ctx, s.copy(grid = !s.grid)) } }
        },
        fab = { ExtendedFloatingActionButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text(btnLabel("+ Книги")) } },
    ) { pad ->
        val cols = if (s.grid) 3 else 1
        LazyVerticalGrid(
            GridCells.Fixed(cols), Modifier.padding(pad),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    current?.let { b -> ContinueCard(b) { nav.navigate(readerBook(b.id)) } }
                    val (today, week) = ReaderStore.minutes(data)
                    val done = data.books.count { it.status == 2 }
                    if (data.books.isNotEmpty()) Text(
                        "Сегодня $today мин · за неделю $week мин · прочитано $done ${plural(done, "книга", "книги", "книг")} из ${data.books.size}",
                        fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                    if (data.books.size > 6) OutlinedTextField(
                        q, { q = it }, placeholder = { Text(btnLabel("Поиск по названию и автору")) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(14.dp),
                    )
                    if (data.books.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        Pill("Все", filter < 0) { filter = -1 }
                        listOf(1, 3, 2, 0).forEach { st -> Pill(STATUS[st], filter == st) { filter = if (filter == st) -1 else st } }
                        Pill("↕ ${SORTS[s.sort]}", false) { io { ReaderStore.saveSettings(ctx, s.copy(sort = (s.sort + 1) % SORTS.size)) } }
                    }
                }
            }
            if (data.books.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().padding(top = 40.dp, start = 12.dp, end = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Glyph("books/08", 72.dp, badge = false)
                    Text("Своя библиотека книг", fontWeight = FontWeight.SemiBold, fontSize = 18.sp, modifier = Modifier.padding(top = 12.dp))
                    Text(
                        "Нажмите «+ Книги» и выберите файлы: EPUB, FB2 (и .fb2.zip), MOBI, AZW3, PDF, DjVu, TXT, RTF, DOCX, ODT, HTML, CBZ. " +
                            "Книгу можно открыть и прямо из файлового менеджера или Telegram — «Открыть с помощью» → DASEIN.\n\n" +
                            "В читалке: страницы или прокрутка, шрифты и темы, оглавление, закладки, выделения цветом и заметки, поиск, сноски по нажатию, чтение вслух, перевод слова, яркость, ночной режим для PDF и DjVu.",
                        fontSize = 13.sp, color = extra.dim, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp),
                    )
                    Gap(16.dp)
                    Button(onClick = { picker.launch(arrayOf("*/*")) }) { Text(btnLabel("Добавить книги")) }
                }
            }
            items(books, key = { it.id }) { b ->
                Box(Modifier.combinedClickable(onClick = { nav.navigate(readerBook(b.id)) }, onLongClick = { menu = b })) {
                    if (s.grid) GridBook(b) else ListBook(b) { menu = b }
                }
            }
        }
    }
    importing?.let { name ->
        AlertDialog(
            onDismissRequest = {}, confirmButton = {},
            title = { Text("Добавляю книгу") },
            text = { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(28.dp)); HGap(14.dp); Text(name, maxLines = 3, overflow = TextOverflow.Ellipsis) } },
        )
    }
    if (errors.isNotEmpty()) AlertDialog(
        onDismissRequest = { errors = emptyList() },
        confirmButton = { TextButton(onClick = { errors = emptyList() }) { Text(btnLabel("Понятно")) } },
        title = { Text("Не получилось открыть") },
        text = { Text(errors.joinToString("\n\n")) },
    )
    menu?.let { b -> BookMenuDialog(nav, b) { menu = null } }
}

@Composable
private fun ContinueCard(b: ReaderBook, onOpen: () -> Unit) {
    val extra = LocalExtra.current
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = .18f), extra.card)))
            .combinedClickableCompat(onOpen).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(b, 62.dp)
        HGap(12.dp)
        Column(Modifier.weight(1f)) {
            Text("Продолжить чтение", fontSize = 12.sp, color = extra.dim)
            Text(b.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (b.author.isNotBlank()) Text(b.author, fontSize = 12.sp, color = extra.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Gap(6.dp)
            Bar(b.progress, MaterialTheme.colorScheme.primary, height = 5.dp)
            Text("${(b.progress * 100).toInt()}% · ${b.secondsRead / 60} мин", fontSize = 11.sp, color = extra.dim, modifier = Modifier.padding(top = 2.dp))
        }
        HGap(8.dp)
        Glyph("reading/01", 34.dp, badge = false)
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit) = this.combinedClickable(onClick = onClick)

@Composable
private fun GridBook(b: ReaderBook) {
    val extra = LocalExtra.current
    Column {
        Box {
            BookCover(b, null)
            Text(
                b.fmt.label, fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = .55f)).padding(horizontal = 5.dp, vertical = 1.dp),
            )
            if (b.status == 2) Text(
                "✓", fontSize = 12.sp, color = Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(4.dp).clip(RoundedCornerShape(10.dp)).background(extra.ok).padding(horizontal = 6.dp),
            )
        }
        if (b.progress > 0f && b.status != 2) Bar(b.progress, MaterialTheme.colorScheme.primary, Modifier.padding(top = 4.dp), height = 3.dp)
        Text(b.title, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp, modifier = Modifier.padding(top = 4.dp))
        if (b.author.isNotBlank()) Text(b.author, fontSize = 11.sp, color = extra.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ListBook(b: ReaderBook, onMenu: () -> Unit) {
    val extra = LocalExtra.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(extra.card).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        BookCover(b, 48.dp)
        HGap(12.dp)
        Column(Modifier.weight(1f)) {
            Text(b.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOf(b.author, b.fmt.label, b.progressLabel()).filter { it.isNotBlank() }.distinct().joinToString(" · "), fontSize = 12.sp, color = extra.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (b.progress > 0f && b.status != 2) Bar(b.progress, MaterialTheme.colorScheme.primary, Modifier.padding(top = 6.dp), height = 3.dp)
        }
        TextButton(onClick = onMenu) { Text("⋮", fontSize = 18.sp) }
    }
}

/** Обложка книги или «корешок» с названием, если обложки нет. [width] null — во всю ширину ячейки. */
@Composable
fun BookCover(b: ReaderBook, width: Dp?, modifier: Modifier = Modifier) {
    val img by rememberImage(b.cover.takeIf { it.isNotBlank() }, 480)
    val shape = RoundedCornerShape(if (width != null && width < 70.dp) 6.dp else 10.dp)
    val m = (if (width != null) modifier.width(width) else modifier.fillMaxWidth()).aspectRatio(2f / 3f).clip(shape)
    val bmp = img
    if (bmp != null) Image(bmp, null, m, contentScale = ContentScale.Crop)
    else {
        val hues = listOf(0xFF8D6E63, 0xFF5C6BC0, 0xFF26A69A, 0xFFAB47BC, 0xFFEF6C00, 0xFF546E7A, 0xFF7CB342, 0xFFC2185B)
        val c = Color(hues[(b.id % hues.size).toInt()])
        Box(m.background(Brush.verticalGradient(listOf(c, c.copy(alpha = .75f)))).padding(6.dp), contentAlignment = Alignment.Center) {
            val small = width != null && width < 70.dp
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(b.title, color = Color.White, fontSize = if (small) 8.sp else 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = if (small) 4 else 5, overflow = TextOverflow.Ellipsis, lineHeight = if (small) 10.sp else 16.sp)
                if (!small && b.author.isNotBlank()) Text(b.author, color = Color.White.copy(alpha = .85f), fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

/** Действия с книгой: читать, сведения, обложка, связь с «Книгами», статус, удаление. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookMenuDialog(nav: NavHostController, b: ReaderBook, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    var title by remember { mutableStateOf(b.title) }
    var author by remember { mutableStateOf(b.author) }
    var status by remember { mutableStateOf(b.status) }
    var cover by remember { mutableStateOf(b.cover) }
    var confirm by remember { mutableStateOf(false) }
    var linkPick by remember { mutableStateOf(false) }
    val pickCover = com.dasein.poryadok.ui.common.rememberImagePickerWithCrop(2f / 3f, "posters") { cover = it }
    fun save() = io { ReaderStore.book(b.id)?.let { cur -> ReaderStore.saveBook(ctx, cur.copy(title = title.trim().ifBlank { cur.title }, author = author.trim(), status = status, cover = cover, finishedDay = if (status == 2) cur.finishedDay ?: com.dasein.poryadok.logic.Dates.today() else cur.finishedDay)) } }
    AlertDialog(
        onDismissRequest = { save(); onDismiss() },
        title = { Text("О книге") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row {
                    Box(Modifier.combinedClickableCompat { pickCover() }) { BookCover(b.copy(cover = cover, title = title), 70.dp) }
                    HGap(12.dp)
                    Column(Modifier.weight(1f)) {
                        Text("${b.fmt.label} · ${b.size / 1024} КБ" + if (b.pages > 0) " · ${b.pages} стр." else "", fontSize = 12.sp, color = extra.dim)
                        Text(b.sourceName, fontSize = 11.sp, color = extra.dim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { pickCover() }) { Text(btnLabel("Сменить обложку"), fontSize = 12.sp) }
                    }
                }
                TextInput(title, { title = it }, "Название")
                Gap(4.dp)
                TextInput(author, { author = it }, "Автор")
                Gap(8.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 3, 2, 0).forEach { st -> Pill(STATUS[st], status == st) { status = st } }
                }
                Gap(8.dp)
                if (b.mediaId != 0L) {
                    TextButton(onClick = { save(); onDismiss(); nav.navigate(Routes.media(b.mediaId, MediaKind.BOOK)) }) { Text(btnLabel("Карточка в «Книгах» →")) }
                    TextButton(onClick = { io { ReaderStore.link(ctx, b.id, 0) }; onDismiss() }) { Text(btnLabel("Отвязать от карточки"), color = extra.dim) }
                } else {
                    TextButton(onClick = { io { ReaderStore.addToMedia(ctx, ReaderStore.book(b.id) ?: b) }; Toast.makeText(ctx, "Добавлено в «Книги»", Toast.LENGTH_SHORT).show(); onDismiss() }) {
                        Text(btnLabel("Добавить в «Книги» (Топы)"))
                    }
                    TextButton(onClick = { linkPick = true }) { Text(btnLabel("Связать с карточкой из «Книг»…")) }
                }
                if (b.description.isNotBlank()) Text(b.description, fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = { TextButton(onClick = { save(); onDismiss(); nav.navigate(readerBook(b.id)) }) { Text(btnLabel("Читать")) } },
        dismissButton = {
            ButtonsRow {
                TextButton(onClick = { confirm = true }) { Text(btnLabel("Удалить"), color = extra.danger) }
                TextButton(onClick = { save(); onDismiss() }) { Text(btnLabel("Готово")) }
            }
        },
    )
    if (confirm) ConfirmDialog("Удалить книгу?", "Файл, закладки и заметки этой книги будут удалены из приложения.", onDismiss = { confirm = false }) {
        io { ReaderStore.delete(ctx, b) }
        onDismiss()
    }
    if (linkPick) MediaLinkDialog(b, { linkPick = false; onDismiss() })
}

/** Выбор карточки книги из «Фильмы, сериалы, книги». */
@Composable
private fun MediaLinkDialog(b: ReaderBook, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    var list by remember { mutableStateOf<List<com.dasein.poryadok.data.MediaItem>>(emptyList()) }
    var q by remember { mutableStateOf(b.title.take(20)) }
    LaunchedEffect(Unit) { list = Graph.extra.media().first().filter { it.kind == MediaKind.BOOK } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Какая это книга?") },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Поиск") })
                Column(Modifier.height(320.dp).verticalScroll(rememberScrollState())) {
                    val shown = list.filter { q.isBlank() || it.title.contains(q, true) || it.creators.contains(q, true) }.ifEmpty { list }
                    if (list.isEmpty()) Text("В «Книгах» пока нет карточек.", color = extra.dim, modifier = Modifier.padding(8.dp))
                    shown.take(80).forEach { m ->
                        Text(
                            m.title + if (m.creators.isNotBlank()) " — ${m.creators}" else "",
                            modifier = Modifier.fillMaxWidth().combinedClickableCompat { io { ReaderStore.link(ctx, b.id, m.id) }; onDismiss() }.padding(vertical = 10.dp, horizontal = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) } },
    )
}

/**
 * Кнопка в карточке книги («Фильмы, сериалы, книги» → «Книги»): если книга есть в читалке — «Читать»
 * (сразу открывает её на том месте, где остановились), если нет — добавить файл книги.
 */
@Composable
fun MediaReadButton(nav: NavHostController, m: com.dasein.poryadok.data.MediaItem) {
    if (m.kind != MediaKind.BOOK || m.id == 0L) return
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { ReaderStore.load(ctx) }
    val data by ReaderStore.state.collectAsState()
    val b = remember(data.books, m.id, m.title) { ReaderStore.forMedia(m) }
    var busy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val r = ReaderStore.import(ctx, listOf(uri), mediaId = m.id).firstOrNull()
            busy = false
            r?.onSuccess { book -> ReaderStore.link(ctx, book.id, m.id); nav.navigate(readerBook(book.id)) }
                ?.onFailure { Toast.makeText(ctx, it.message ?: "Не удалось открыть файл", Toast.LENGTH_LONG).show() }
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        if (b != null) {
            Button(onClick = {
                scope.launch {
                    if (b.mediaId != m.id) ReaderStore.link(ctx, b.id, m.id)
                    nav.navigate(readerBook(b.id))
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Glyph("reading/01", 20.dp, badge = false)
                HGap(8.dp)
                Text(if (b.progress > 0f && b.status != 2) "Читать · ${(b.progress * 100).toInt()}%" else "Читать")
            }
            Text("В читалке: ${b.fmt.label}" + if (b.secondsRead > 60) " · читали ${b.secondsRead / 60} мин" else "", fontSize = 11.sp, color = extra.dim, modifier = Modifier.padding(top = 2.dp, start = 4.dp))
        } else {
            com.dasein.poryadok.ui.common.OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Glyph("books/08", 20.dp, badge = false)
                HGap(8.dp)
                Text(if (busy) "Добавляю…" else "Добавить файл книги в читалку")
            }
        }
    }
}
