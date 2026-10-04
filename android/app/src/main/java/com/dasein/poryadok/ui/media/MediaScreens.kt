@file:OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.dasein.poryadok.ui.media

import com.dasein.poryadok.ui.common.Hint
import com.dasein.poryadok.ui.common.HowTo
import com.dasein.poryadok.ui.common.NumberField
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.MediaItem
import com.dasein.poryadok.data.MediaList
import com.dasein.poryadok.data.MediaListItem
import com.dasein.poryadok.data.MediaKind
import com.dasein.poryadok.data.MediaStatus
import com.dasein.poryadok.data.TopItem
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.MediaHit
import com.dasein.poryadok.logic.MediaParse
import com.dasein.poryadok.logic.MediaCatalog
import com.dasein.poryadok.ui.common.FilterOption
import com.dasein.poryadok.ui.common.SearchField
import com.dasein.poryadok.ui.common.SingleFilter
import com.dasein.poryadok.ui.common.SortButton
import androidx.compose.foundation.layout.Spacer
import com.dasein.poryadok.system.MediaSearch
import com.dasein.poryadok.system.MediaSource
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.ConfirmDialog
import com.dasein.poryadok.ui.common.DatePickDialog
import com.dasein.poryadok.ui.common.Empty
import com.dasein.poryadok.ui.common.FieldButton
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.GlyphPickerDialog
import com.dasein.poryadok.ui.common.HGap
import com.dasein.poryadok.ui.common.IconAction
import com.dasein.poryadok.ui.common.Images
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.common.rememberImage
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private fun kindIcon(kind: Int) = when (kind) { MediaKind.SERIES -> "habit/03"; MediaKind.BOOK -> "habit/11"; else -> "habit/01" }

/** Постер: локальный файл, иначе заглушка с иконкой. */
@Composable
fun Poster(item: MediaItem, width: Dp, modifier: Modifier = Modifier) {
    val img by rememberImage(item.poster.takeIf { it.isNotBlank() }, 600)
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier.width(width).aspectRatio(2f / 3f).clip(shape).background(LocalExtra.current.cardHigh),
        contentAlignment = Alignment.Center,
    ) {
        val b = img
        if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Glyph(kindIcon(item.kind), width * .3f, badge = false)
            Text(item.title, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(6.dp))
        }
    }
}

@Composable
private fun UrlImage(url: String, width: Dp) {
    val bmp by produceState<android.graphics.Bitmap?>(null, url) { value = MediaSearch.thumb(url) }
    Box(Modifier.width(width).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(LocalExtra.current.cardHigh)) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

@Composable
fun Stars10(value: Int, size: Int = 22, onChange: ((Int) -> Unit)? = null) {
    Row {
        (1..10).forEach { i ->
            Text(
                if (i <= value) "★" else "☆", fontSize = size.sp, color = MaterialTheme.colorScheme.primary,
                modifier = if (onChange != null) Modifier.clickable { onChange(if (value == i) 0 else i) }.padding(horizontal = 1.dp) else Modifier,
            )
        }
    }
}

private const val FAV = 99

/**
 * Коллекция одного вида как в каталогах-агрегаторах: «Продолжить» с прогрессом, поиск по словам,
 * фильтры кнопками со списком вариантов снизу (статус, жанр, годы, страна, автор, оценки, длительность, списки, метки),
 * сортировка, сетка или список, «Что посмотреть?» и статистика.
 */
@Composable
fun MediaListScreen(nav: NavHostController, kind: Int) {
    val extra = LocalExtra.current
    val all by observe<List<MediaItem>?>(null) { Graph.extra.media() }
    var status by rememberSaveable(kind) { mutableStateOf("") }
    var genre by rememberSaveable(kind) { mutableStateOf("") }
    var decade by rememberSaveable(kind) { mutableStateOf("") }
    var country by rememberSaveable(kind) { mutableStateOf("") }
    var creator by rememberSaveable(kind) { mutableStateOf("") }
    var rating by rememberSaveable(kind) { mutableStateOf("") }
    var ext by rememberSaveable(kind) { mutableStateOf("") }
    var len by rememberSaveable(kind) { mutableStateOf("") }
    var tag by rememberSaveable(kind) { mutableStateOf("") }
    var listKey by rememberSaveable(kind) { mutableStateOf("") }
    var sort by rememberSaveable(kind) { mutableStateOf(MediaSort.NEW) }
    var grid by rememberSaveable(kind) { mutableStateOf(true) }
    var group by rememberSaveable(kind) { mutableStateOf(com.dasein.poryadok.logic.MediaShelf.Group.NONE) }
    var selected by remember(kind) { mutableStateOf(setOf<Long>()) }
    var bulk by remember { mutableStateOf(false) }
    var renameAll by remember { mutableStateOf(false) }
    var q by rememberSaveable(kind) { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var import by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf(false) }
    var random by remember { mutableStateOf(false) }
    val lists by observe(emptyList()) { Graph.extra.mediaLists() }
    val listItems by observe(emptyList()) { Graph.extra.mediaListItems() }
    var newList by remember { mutableStateOf(false) }
    var renameList by remember { mutableStateOf<MediaList?>(null) }
    var deleteList by remember { mutableStateOf<MediaList?>(null) }
    val myLists = lists.filter { it.kind == -1 || it.kind == kind }
    val listFilter = listKey.toLongOrNull() ?: 0L
    val smart = com.dasein.poryadok.logic.MediaShelf.smart(listKey)
    val now = System.currentTimeMillis()
    val names = MediaStatus.names(kind)
    val base = all.orEmpty().filter { it.kind == kind }
    val query = remember(q) { com.dasein.poryadok.logic.TextQuery.parse(q) }
    val split = MediaCatalog::split
    val pass: (MediaItem, String?) -> Boolean = { m, skip ->
        com.dasein.poryadok.logic.TextQuery.matches(listOf(m.title, m.originalTitle, m.creators, m.cast, m.genres, m.description, m.tags).joinToString(" "), query) &&
            (skip == "status" || status.isEmpty() || (status == "fav" && m.favorite) || status == "${m.status}") &&
            (skip == "genre" || genre.isEmpty() || genre in split(m.genres)) &&
            (skip == "decade" || decade.isEmpty() || MediaCatalog.decade(m.year) == decade) &&
            (skip == "country" || country.isEmpty() || country in split(m.countries)) &&
            (skip == "creator" || creator.isEmpty() || creator in split(m.creators)) &&
            (skip == "rating" || MediaCatalog.ratingMatches(rating, m.myRating)) &&
            (skip == "ext" || ext.isEmpty() || (m.externalRating ?: 0.0) >= ext.toDouble()) &&
            (skip == "len" || MediaCatalog.lengthMatches(len, m)) &&
            (skip == "tag" || tag.isEmpty() || tag in split(m.tags)) &&
            (skip == "list" || (smart?.test?.invoke(m, now) ?: (listFilter == 0L || listItems.any { it.listId == listFilter && it.mediaId == m.id })))
    }
    val list = sortMedia(base.filter { pass(it, null) }, sort)
    fun facet(group: String, values: (MediaItem) -> List<String>): List<FilterOption> =
        base.filter { pass(it, group) }.flatMap(values).groupingBy { it }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { FilterOption(it.key, it.key, it.value) }
    fun countIn(group: String, test: (MediaItem) -> Boolean) = base.count { pass(it, group) && test(it) }
    val active = listOf(status, genre, decade, country, creator, rating, ext, len, tag, listKey).count { it.isNotEmpty() }
    Screen(
        title = MediaKind.plural[kind],
        actions = {
            if (selected.isNotEmpty()) {
                TextButton(onClick = { selected = list.map { it.id }.toSet() }) { Text("Все") }
                TextButton(onClick = { selected = emptySet() }) { Text("Отмена") }
            } else IconAction("habit/24", "Найти онлайн") { nav.navigate(Routes.mediaSearch(kind)) }
        },
        fab = {
            if (selected.isNotEmpty()) androidx.compose.material3.ExtendedFloatingActionButton(
                onClick = { bulk = true }, containerColor = MaterialTheme.colorScheme.primary,
                text = { Text("Действия · ${selected.size}") }, icon = { Glyph("ui:folder", 20.dp) },
            ) else Box {
                FloatingActionButton(onClick = { menu = true }, containerColor = MaterialTheme.colorScheme.primary) { Icon(Icons.Default.Add, "Добавить") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Найти в каталоге") }, leadingIcon = { Glyph("habit/24", 20.dp) }, onClick = { menu = false; nav.navigate(Routes.mediaSearch(kind)) })
                    DropdownMenuItem(text = { Text("Заполнить карточку самому") }, leadingIcon = { Glyph("habit/23", 20.dp) }, onClick = { menu = false; nav.navigate(Routes.media(0, kind)) })
                    if (kind != MediaKind.BOOK) DropdownMenuItem(text = { Text("Импорт с Кинопоиска") }, leadingIcon = { Glyph("habit/01", 20.dp) }, onClick = { menu = false; nav.navigate(Routes.kpImport(kind)) })
                    DropdownMenuItem(text = { Text("Импорт списка текстом") }, leadingIcon = { Glyph("habit/26", 20.dp) }, onClick = { menu = false; import = true })
                    DropdownMenuItem(text = { Text("Новая папка") }, leadingIcon = { Glyph("ui:folder", 20.dp) }, onClick = { menu = false; newList = true })
                    DropdownMenuItem(text = { Text("Переименовать папки и статусы") }, leadingIcon = { Glyph("ui:sliders", 20.dp) }, onClick = { menu = false; renameAll = true })
                }
            }
        },
    ) { pad ->
        val items = all
        if (items == null) { Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return@Screen }
        LazyVerticalGrid(
            GridCells.Adaptive(110.dp), Modifier.padding(pad),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    SearchField(q, { q = it }, "Название, режиссёр, актёр, жанр, метка")
                    Gap(8.dp)
                    ContinueShelf(base.filter { it.status == MediaStatus.IN_PROGRESS }.sortedByDescending { it.startedDay ?: 0L }) { nav.navigate(Routes.media(it.id, kind)) }
                    FolderShelf(base, myLists, listItems, listKey, onSelect = { listKey = it }, onNew = { newList = true }, onEdit = { renameList = it }, onRenameAll = { renameAll = true })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SortButton(MediaSort.entries, sort, { it.label }) { sort = it }
                        Spacer(Modifier.weight(1f))
                        SortButton(com.dasein.poryadok.logic.MediaShelf.Group.entries, group, { if (it == com.dasein.poryadok.logic.MediaShelf.Group.NONE) "Группы" else it.label.removePrefix("По ").replaceFirstChar(Char::uppercase) }) { group = it }
                        Text(
                            if (grid) "▦" else "☰", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { grid = !grid }.padding(8.dp),
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SingleFilter(
                            "Статус",
                            names.mapIndexed { i, n -> FilterOption("$i", n, countIn("status") { it.status == i }) } +
                                FilterOption("fav", "Любимые", countIn("status") { it.favorite }),
                            status, allLabel = "Любой", allCount = countIn("status") { true },
                        ) { status = it }
                        SingleFilter("Жанр", facet("genre") { split(it.genres) }, genre, allLabel = "Все жанры") { genre = it }
                        SingleFilter("Годы", facet("decade") { listOfNotNull(MediaCatalog.decade(it.year)) }.sortedByDescending { it.key }, decade, allLabel = "Любые") { decade = it }
                        SingleFilter("Страна", facet("country") { split(it.countries) }, country, allLabel = "Все страны") { country = it }
                        SingleFilter(if (kind == MediaKind.BOOK) "Автор" else "Режиссёр", facet("creator") { split(it.creators) }, creator, allLabel = "Все") { creator = it }
                        SingleFilter(
                            "Моя оценка", MediaCatalog.RATING_OPTIONS.map { (k, l) -> FilterOption(k, l, countIn("rating") { MediaCatalog.ratingMatches(k, it.myRating) }) },
                            rating, allLabel = "Любая",
                        ) { rating = it }
                        if (base.any { it.externalRating != null }) SingleFilter(
                            "Рейтинг", MediaCatalog.EXTERNAL_OPTIONS.map { (k, l) -> FilterOption(k, l, countIn("ext") { m -> (m.externalRating ?: 0.0) >= k.toDouble() }) },
                            ext, allLabel = "Любой",
                        ) { ext = it }
                        if (kind == MediaKind.MOVIE) SingleFilter(
                            "Длительность", MediaCatalog.LENGTH_OPTIONS.map { (k, l) -> FilterOption(k, l, countIn("len") { MediaCatalog.lengthMatches(k, it) }) },
                            len, allLabel = "Любая",
                        ) { len = it }
                        val tags = facet("tag") { split(it.tags) }
                        if (tags.isNotEmpty() || tag.isNotEmpty()) SingleFilter("Метки", tags, tag, allLabel = "Любые") { tag = it }
                    }
                    Gap(8.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { random = true }, Modifier.weight(1f)) {
                            Text(if (kind == MediaKind.BOOK) "🎲 Что почитать?" else "🎲 Что посмотреть?", maxLines = 1, softWrap = false)
                        }
                        OutlinedButton(onClick = { stats = true }, Modifier.weight(1f)) { Text("📊 Статистика", maxLines = 1, softWrap = false) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        Text("Найдено ${list.size} из ${base.size}", fontSize = 12.sp, color = extra.dim, modifier = Modifier.weight(1f))
                        if (active > 0) TextButton(onClick = {
                            status = ""; genre = ""; decade = ""; country = ""; creator = ""; rating = ""; ext = ""; len = ""; tag = ""; listKey = ""
                        }) { Text("Сбросить фильтры", fontSize = 12.sp) }
                    }
                    if (selected.isEmpty() && list.isNotEmpty()) Text(
                        "Долгое нажатие на карточку — выбрать несколько: разложить по папкам, сменить статус, удалить.",
                        fontSize = 11.sp, color = extra.dim,
                    )
                    myLists.firstOrNull { it.id == listFilter }?.let { l ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("«${l.name}»", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            TextButton(onClick = { renameList = l }) { Text("Переименовать", fontSize = 12.sp) }
                            TextButton(onClick = { deleteList = l }) { Text("Удалить", fontSize = 12.sp, color = extra.danger) }
                        }
                    }
                }
            }
            if (list.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Empty(
                    kindIcon(kind), if (base.isEmpty()) "Здесь пока пусто" else "Ничего не нашлось",
                    if (base.isEmpty()) "Найдите ${MediaKind.names[kind].lowercase()} в каталоге (Кинопоиск, Википедия, TVMaze, Google Книги, Open Library), импортируйте свои оценки с Кинопоиска или заполните карточку сами: постер, роли, режиссёр, ваше мнение."
                    else "Измените запрос или сбросьте фильтры.",
                )
            }
            val folderNames: (MediaItem) -> List<String> = { m -> listItems.filter { it.mediaId == m.id }.mapNotNull { li -> myLists.firstOrNull { it.id == li.listId }?.name } }
            val open: (MediaItem) -> Unit = { m -> if (selected.isNotEmpty()) selected = if (m.id in selected) selected - m.id else selected + m.id else nav.navigate(Routes.media(m.id, kind)) }
            val longPress: (MediaItem) -> Unit = { m -> selected = if (m.id in selected) selected - m.id else selected + m.id }
            com.dasein.poryadok.logic.MediaShelf.group(list, group, names, folderNames).forEach { (title, part) ->
                if (title.isNotEmpty()) item(key = "h:$title", span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Text("${part.size}", fontSize = 13.sp, color = extra.dim)
                    }
                }
                if (grid) items(part, key = { "$title/${it.id}" }) { m ->
                    val on = m.id in selected
                    Column(
                        Modifier.clip(RoundedCornerShape(12.dp))
                            .then(if (on) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)) else Modifier)
                            .combinedClickable(onClick = { open(m) }, onLongClick = { longPress(m) }),
                    ) {
                        Box {
                            Poster(m, 200.dp, Modifier.fillMaxWidth())
                            if (m.favorite) Box(Modifier.padding(6.dp).align(Alignment.TopEnd)) { Glyph("habit/28", 16.dp) }
                            if (selected.isNotEmpty()) Box(Modifier.padding(4.dp).align(Alignment.TopStart)) {
                                androidx.compose.material3.Checkbox(on, { longPress(m) })
                            }
                        }
                        MediaCatalog.progressShare(m)?.takeIf { m.status == MediaStatus.IN_PROGRESS }?.let { com.dasein.poryadok.ui.common.Bar(it, MaterialTheme.colorScheme.primary, height = 3.dp) }
                        Text(m.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        Text(
                            listOfNotNull(m.year?.toString(), if (m.myRating > 0) "★ ${m.myRating}" else m.externalRating?.let { "%.1f".format(it) }, names.getOrNull(m.status)?.takeIf { m.status != MediaStatus.DONE }).joinToString(" · "),
                            fontSize = 11.sp, color = extra.dim, maxLines = 1,
                        )
                    }
                } else items(part, key = { "$title/${it.id}" }, span = { GridItemSpan(maxLineSpan) }) { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (selected.isNotEmpty()) androidx.compose.material3.Checkbox(m.id in selected, { longPress(m) })
                        Box(Modifier.weight(1f)) { MediaRow(m, onLong = { longPress(m) }) { open(m) } }
                    }
                }
            }
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { HowTo("media") }
        }
    }
    if (stats) MediaStatsSheet(kind, base) { stats = false }
    if (random) {
        val planned = list.filter { it.status == MediaStatus.PLANNED }.ifEmpty { base.filter { it.status == MediaStatus.PLANNED } }
        RandomPickDialog(kind, planned, onOpen = { random = false; nav.navigate(Routes.media(it.id, kind)) }) { random = false }
    }
    if (import) ImportDialog(kind) { import = false }
    if (renameAll) RenameAllDialog(kind, myLists) { renameAll = false }
    if (bulk) BulkActionsDialog(
        kind, base.filter { it.id in selected }, myLists, listFilter,
        onNewFolder = { newList = true },
        onDone = { bulk = false; selected = emptySet() },
        onDismiss = { bulk = false },
    )
    if (newList) ListNameDialog("Новая папка", "", { newList = false }) { name, glyph ->
        newList = false
        io { val id = Graph.extra.upsertMediaList(MediaList(name = name, glyph = glyph, createdAt = System.currentTimeMillis())); listKey = "$id" }
    }
    renameList?.let { l ->
        ListNameDialog("Папка «${l.name}»", l.name, { renameList = null }, l.glyph, onDelete = { renameList = null; deleteList = l }) { name, glyph ->
            renameList = null
            io { Graph.extra.upsertMediaList(l.copy(name = name, glyph = glyph)) }
        }
    }
    deleteList?.let { l ->
        ConfirmDialog("Удалить папку «${l.name}»?", "Фильмы и книги останутся в коллекции, исчезнет только сама папка.", onDismiss = { deleteList = null }) {
            deleteList = null; listKey = ""
            io { Graph.extra.clearMediaList(l.id); Graph.extra.deleteMediaListRow(l.id) }
        }
    }
}

/** Название и иконка своего списка. */
@Composable
fun ListNameDialog(title: String, name0: String, onDismiss: () -> Unit, glyph0: String = "ui:folder", onDelete: (() -> Unit)? = null, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf(name0) }
    var glyph by remember { mutableStateOf(glyph0.ifBlank { "ui:folder" }) }
    var pick by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clip(RoundedCornerShape(12.dp)).clickable { pick = true }.padding(4.dp)) { Glyph(glyph, 28.dp) }
                    HGap(10.dp)
                    Box(Modifier.weight(1f)) { TextInput(name, { name = it }, "Например: Новогодние, Посоветовали, Пересмотреть") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                    listOf("Пересмотреть", "Посоветовали", "Новогодние", "С детьми", "Дорамы", "Аниме", "Лучшее за год", "Классика").forEach { t -> Pill(t, name == t) { name = t } }
                }
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Удалить папку", color = LocalExtra.current.danger) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim(), glyph) }, enabled = name.isNotBlank()) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
    if (pick) GlyphPickerDialog(glyph, { pick = false }) { glyph = it }
}

/** Импорт списка: по строке «Название (год)». Можно вставить список, скопированный с любого сайта. */
@Composable
private fun ImportDialog(kind: Int, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(MediaStatus.DONE) }
    val scope = rememberCoroutineScope()
    val lines = text.lines().mapNotNull { MediaParse.parseListLine(it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Импорт списка") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Вставьте список — по одному в строке, можно с годом: «Интерстеллар (2014)». Подойдёт список, скопированный с Кинопоиска, IMDb, LiveLib или заметки.", fontSize = 12.sp, color = LocalExtra.current.dim)
                Gap(6.dp)
                TextInput(text, { text = it }, "Список", singleLine = false, minLines = 6)
                Gap(6.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MediaStatus.names(kind).forEachIndexed { i, n -> Pill(n, status == i) { status = i } }
                }
                Text("Будет добавлено: ${lines.size}", fontSize = 12.sp, color = LocalExtra.current.dim, modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    val existing = Graph.extra.allMedia()
                    var n = 0
                    lines.forEach { (title, year) ->
                        if (existing.none { it.kind == kind && it.title.equals(title, true) && (year == null || it.year == year) }) {
                            Graph.extra.upsertMedia(MediaItem(kind = kind, title = title, year = year, status = status, createdAt = System.currentTimeMillis() - n))
                            n++
                        }
                    }
                    Toast.makeText(ctx, "Добавлено: $n. Откройте карточку, чтобы дополнить её из каталога.", Toast.LENGTH_LONG).show()
                    onDismiss()
                }
            }, enabled = lines.isNotEmpty()) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Поиск в открытых каталогах и на Кинопоиске. */
@Composable
fun MediaSearchScreen(nav: NavHostController, kind: Int, fillId: Long = 0) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    val settings by observe(null) { Graph.prefs.settings }
    val sources = MediaSearch.sourcesFor(kind)
    var source by rememberSaveable { mutableStateOf(MediaSource.ALL.name) }
    var q by rememberSaveable { mutableStateOf("") }
    var yearText by rememberSaveable { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<MediaHit>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var token by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf<String?>(null) }
    val src = runCatching { MediaSource.valueOf(source) }.getOrDefault(MediaSource.ALL)
    fun run() {
        if (q.isBlank()) return
        busy = true; error = null; note = null
        // «Дюна 2021» — ищем «Дюна» и оставляем только фильмы 2021 года. Отдельное поле «Год» важнее.
        val (title, yq) = MediaParse.splitYear(q)
        val year = yearText.trim().toIntOrNull() ?: yq
        scope.launch {
            runCatching { MediaSearch.search(src, kind, title) }
                .onSuccess { all ->
                    val byYear = MediaParse.filterYear(all, year)
                    results = if (year != null && byYear.isEmpty()) all else byYear
                    if (year != null) note = if (byYear.isEmpty() && all.isNotEmpty()) "Вышедших именно в $year году не нашлось — показаны все" else "Только $year год · ${byYear.size}"
                    if (all.isEmpty()) error = "Ничего не найдено — попробуйте другое написание или другой источник"
                }
                .onFailure { error = it.message ?: "Нет соединения с интернетом" }
            busy = false
        }
    }
    Screen("Найти: ${MediaKind.names[kind].lowercase()}", onBack = { nav.popBackStack() }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp)) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    sources.forEach { s -> Pill(s.title, s.name == source) { source = s.name; results = emptyList(); error = null } }
                }
                Gap(8.dp)
                if (src == MediaSource.ALL && kind != MediaKind.BOOK) {
                    Hint(
                        "where_all",
                        "«Везде» ищет сразу в Википедии, IMDb, iTunes" + (if (kind == MediaKind.SERIES) " и TVMaze" else "") +
                            (if (!settings?.kinopoiskToken.isNullOrBlank()) ", Кинопоиске" else "") + (if (!settings?.tmdbToken.isNullOrBlank()) ", TMDB" else "") +
                            ". Дорам и азиатских фильмов больше всего в TMDB и IMDb: в IMDb ищите по английскому названию («Crash Landing on You»). " +
                            "Аниме — на вкладке «Шикимори». Не нашлось нигде — «+» → «Заполнить карточку самому».",
                        title = "Где ищем",
                    )
                    Gap(8.dp)
                }
                if (src == MediaSource.TMDB && settings?.tmdbToken.isNullOrBlank()) {
                    var tmdb by rememberSaveable { mutableStateOf("") }
                    Tile {
                        Text("TMDB нужен бесплатный ключ", fontWeight = FontWeight.SemiBold)
                        Hint(
                            "tmdb_token",
                            "Зарегистрируйтесь на themoviedb.org → Настройки → API → «Создать» (тип — личный). Скопируйте «Ключ API» или «Токен доступа» и вставьте сюда. " +
                                "В TMDB почти все дорамы — с русскими названиями, описаниями и постерами. Если сайт не открывается, может понадобиться VPN.",
                            title = "Где взять ключ TMDB",
                        )
                        Gap(6.dp)
                        TextInput(tmdb, { tmdb = it }, "Ключ API или токен")
                        Row {
                            TextButton(onClick = { io { Graph.prefs.update { it.copy(tmdbToken = tmdb.trim()) } } }, enabled = tmdb.isNotBlank()) { Text("Сохранить ключ") }
                            TextButton(onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.themoviedb.org/settings/api"))) } }) { Text("Открыть сайт") }
                        }
                    }
                    Gap(8.dp)
                }
                if (src == MediaSource.KINOPOISK && settings?.kinopoiskToken.isNullOrBlank()) {
                    Tile {
                        Text("Кинопоиску нужен бесплатный личный токен", fontWeight = FontWeight.SemiBold)
                        Hint("kp_token", "Получите его в Telegram у бота @kinopoiskdev_bot (сайт kinopoisk.dev) и вставьте сюда. Без токена фильмы и сериалы ищутся в Википедии.", title = "Где взять токен Кинопоиска")
                        Gap(6.dp)
                        TextInput(token, { token = it }, "Токен")
                        Row {
                            TextButton(onClick = { io { Graph.prefs.update { it.copy(kinopoiskToken = token.trim()) } } }, enabled = token.isNotBlank()) { Text("Сохранить токен") }
                            TextButton(onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/kinopoiskdev_bot"))) } }) { Text("Открыть бота") }
                        }
                    }
                    Gap(8.dp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { TextInput(q, { q = it }, if (kind == MediaKind.BOOK) "Название или автор" else "Название, можно с годом") }
                    HGap(6.dp)
                    Box(Modifier.width(84.dp)) { NumberField(yearText, { yearText = it.filter { c -> c.isDigit() }.take(4) }, "Год") }
                    HGap(6.dp)
                    Button(onClick = { run() }, enabled = !busy && q.isNotBlank()) { Text("Найти") }
                }
                note?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
                if (busy) Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                error?.let { Text(it, color = extra.warn, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp)) }
                Gap(6.dp)
            }
            items(results, key = { it.externalId }) { h ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                    UrlImage(h.posterUrl, 64.dp)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(h.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(h.year?.toString(), h.creators.takeIf { it.isNotBlank() }, h.genres.takeIf { it.isNotBlank() }, h.rating?.let { "★ %.1f".format(it) }, h.source).joinToString(" · "),
                            fontSize = 12.sp, color = extra.dim, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        if (h.description.isNotBlank()) Text(h.description, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row {
                            MediaStatus.names(kind).take(3).forEachIndexed { i, n ->
                                TextButton(onClick = {
                                    adding = h.externalId
                                    scope.launch {
                                        val id = MediaSearch.add(ctx, h, i)
                                        adding = null
                                        Toast.makeText(ctx, "Добавлено: ${h.title}", Toast.LENGTH_SHORT).show()
                                        if (fillId == 0L) nav.navigate(Routes.media(id, kind))
                                    }
                                }, enabled = adding == null) { Text(if (adding == h.externalId) "…" else n, fontSize = 12.sp) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Карточка: всё можно заполнить и поправить самому. */
@Composable
fun MediaCardScreen(nav: NavHostController, id: Long, kind0: Int) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(id == 0L) }
    var m by remember { mutableStateOf(MediaItem(kind = kind0, title = "", createdAt = System.currentTimeMillis())) }
    LaunchedEffect(id) { if (id != 0L) { Graph.extra.mediaItemNow(id)?.let { m = it }; loaded = true } }
    var year by remember(loaded) { mutableStateOf(m.year?.toString() ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    var toTop by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    val lists by observe(emptyList()) { Graph.extra.mediaLists() }
    val listItems by observe(emptyList()) { Graph.extra.mediaListItems() }
    val allMedia by observe(emptyList()) { Graph.extra.media() }
    var newList by remember { mutableStateOf(false) }
    val pickPoster = com.dasein.poryadok.ui.common.rememberImagePickerWithCrop(2f / 3f, "posters") { m = m.copy(poster = it) }
    var recrop by remember { mutableStateOf(false) }
    if (recrop) com.dasein.poryadok.ui.common.CropDialog(com.dasein.poryadok.ui.common.Crop.sourceFor(m.poster), 2f / 3f, "posters", onDismiss = { recrop = false }) {
        recrop = false; m = m.copy(poster = it)
    }
    val book = m.kind == MediaKind.BOOK
    fun save(back: Boolean = true) {
        if (m.title.isBlank()) { Toast.makeText(ctx, "Введите название", Toast.LENGTH_SHORT).show(); return }
        val item = m.copy(title = m.title.trim(), year = year.toIntOrNull())
        scope.launch {
            val newId = Graph.extra.upsertMedia(item)
            if (item.id == 0L) m = item.copy(id = newId)
            if (back) nav.popBackStack()
        }
    }
    Screen(
        if (id == 0L) "Новая карточка" else MediaKind.names[m.kind],
        onBack = { nav.popBackStack() },
        actions = {
            IconAction(if (m.favorite) "habit/28" else "ui:heart", "Любимое") { m = m.copy(favorite = !m.favorite) }
            IconAction("ui:check", "Сохранить") { save() }
        },
    ) { pad ->
        if (!loaded) { Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return@Screen }
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Poster(m, 120.dp, Modifier.clickable { pickPoster() })
                    TextButton(onClick = { pickPoster() }) { Text(if (m.poster.isBlank()) "Загрузить постер" else "Заменить", fontSize = 12.sp) }
                    if (m.poster.isNotBlank()) TextButton(onClick = { recrop = true }) { Text("Кадр", fontSize = 12.sp) }
                    if (m.poster.isNotBlank()) TextButton(onClick = { m = m.copy(poster = "") }) { Text("Убрать", fontSize = 12.sp) }
                }
                HGap(12.dp)
                Column(Modifier.weight(1f)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        MediaKind.names.forEachIndexed { i, n -> Pill(n, m.kind == i, glyph = kindIcon(i)) { m = m.copy(kind = i) } }
                    }
                    Gap(6.dp)
                    TextInput(m.title, { m = m.copy(title = it) }, "Название")
                    Gap(4.dp)
                    TextInput(m.originalTitle, { m = m.copy(originalTitle = it) }, "Оригинальное название")
                    Gap(4.dp)
                    TextInput(year, { year = it.filter(Char::isDigit).take(4) }, "Год")
                    if (m.externalRating != null) Text("${m.source}: ★ %.1f".format(m.externalRating), fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp))
                }
            }
            if (m.externalId.isBlank() && m.title.isNotBlank()) TextButton(onClick = { searching = true }) { Text("Дополнить из каталога…") }

            SectionTitle("Моё")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MediaStatus.names(m.kind).forEachIndexed { i, n ->
                    Pill(n, m.status == i) {
                        // Дата начала и окончания ставятся сами, их можно поправить.
                        m = m.copy(
                            status = i,
                            startedDay = if (i == MediaStatus.IN_PROGRESS && m.startedDay == null) Dates.today() else m.startedDay,
                            finishedDay = if (i == MediaStatus.DONE && m.finishedDay == null) Dates.today() else m.finishedDay,
                        )
                    }
                }
            }
            Gap(8.dp)
            ProgressEditor(m) { m = it }
            Gap(8.dp)
            Text("Моя оценка${if (m.myRating > 0) ": ${m.myRating}/10" else ""}", fontSize = 13.sp, color = extra.dim)
            Stars10(m.myRating, 24) { m = m.copy(myRating = it) }
            Gap(8.dp)
            FieldButton(if (book) "Прочитано" else "Просмотрено", m.finishedDay?.let { Dates.full(it) } ?: "дата не указана", Modifier.fillMaxWidth(), glyph = "cal/06") { pickDate = true }
            Gap(8.dp)
            TextInput(m.review, { m = m.copy(review = it) }, "Моё мнение", singleLine = false, minLines = 3)
            Gap(4.dp)
            TextInput(m.tags, { m = m.copy(tags = it) }, "Мои метки через запятую: в кино, с семьёй, посоветовали")

            SectionTitle(if (book) "О книге" else "О фильме")
            TextInput(m.creators, { m = m.copy(creators = it) }, if (book) "Автор(ы)" else "Режиссёр(ы)")
            Gap(4.dp)
            TextInput(m.genres, { m = m.copy(genres = it) }, "Жанры")
            Gap(4.dp)
            Row {
                Box(Modifier.weight(1f)) { TextInput(m.countries, { m = m.copy(countries = it) }, if (book) "Издательство, страна" else "Страна") }
                HGap(6.dp)
                Box(Modifier.weight(1f)) { TextInput(m.length, { m = m.copy(length = it) }, if (book) "Страниц" else if (m.kind == MediaKind.SERIES) "Сезоны, серии" else "Длительность") }
            }
            Gap(4.dp)
            TextInput(m.description, { m = m.copy(description = it) }, if (book) "Аннотация" else "Сюжет", singleLine = false, minLines = 3)

            if (!book) {
                SectionTitle("В ролях")
                CastEditor(m.cast) { m = m.copy(cast = it) }
            } else {
                SectionTitle("Персонажи и цитаты")
                TextInput(m.cast, { m = m.copy(cast = it) }, "Персонажи, любимые цитаты", singleLine = false, minLines = 3)
            }
            if (m.url.isNotBlank()) TextButton(onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(m.url))) } }) { Text("Открыть на ${m.source}") }

            SectionTitle("Папки")
            if (m.id == 0L) Text("Сохраните карточку, чтобы разложить её по папкам.", fontSize = 12.sp, color = extra.dim)
            else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                lists.filter { it.kind == -1 || it.kind == m.kind }.forEach { l ->
                    val on = listItems.any { it.listId == l.id && it.mediaId == m.id }
                    Pill(l.name, on, glyph = l.glyph.ifBlank { "ui:folder" }) {
                        io { if (on) Graph.extra.removeFromMediaList(l.id, m.id) else Graph.extra.addToMediaList(MediaListItem(l.id, m.id, addedAt = System.currentTimeMillis())) }
                    }
                }
                Pill("+ Новая папка", false) { newList = true }
            }

            if (m.id != 0L) {
                val similar = remember(m.id, allMedia) { MediaCatalog.similar(m, allMedia.filter { it.kind == m.kind }) }
                SimilarShelf(similar) { o -> save(back = false); nav.navigate(Routes.media(o.id, o.kind)) }
            }

            Gap(16.dp)
            Button(onClick = { save() }, Modifier.fillMaxWidth()) { Text("Сохранить") }
            Row {
                OutlinedButton(onClick = { toTop = true }, Modifier.weight(1f), enabled = m.title.isNotBlank()) { Text("В топ") }
                HGap(8.dp)
                if (m.id != 0L) OutlinedButton(onClick = { confirmDelete = true }, Modifier.weight(1f)) { Text("Удалить", color = extra.danger) }
            }
            Gap(40.dp)
        }
    }
    if (pickDate) DatePickDialog(m.finishedDay ?: Dates.today(), { pickDate = false }, { d -> m = m.copy(finishedDay = d) })
    if (confirmDelete) ConfirmDialog("Удалить карточку?", "«${m.title}» исчезнет из коллекции.", onDismiss = { confirmDelete = false }) {
        io { Graph.extra.removeMediaFromLists(m.id); Graph.extra.deleteMedia(m) }; nav.popBackStack()
    }
    if (newList) ListNameDialog("Новая папка", "", { newList = false }) { name, glyph ->
        newList = false
        val mid = m.id
        io {
            val lid = Graph.extra.upsertMediaList(MediaList(name = name, glyph = glyph, createdAt = System.currentTimeMillis()))
            if (mid != 0L) Graph.extra.addToMediaList(MediaListItem(lid, mid, addedAt = System.currentTimeMillis()))
        }
    }
    if (toTop) AddToTopDialog(m) { toTop = false }
    if (searching) FillFromCatalogDialog(m, onDismiss = { searching = false }) { h ->
        scope.launch {
            val full = MediaSearch.details(h)
            val poster = if (m.poster.isBlank()) MediaSearch.downloadPoster(ctx, full.posterUrl).orEmpty() else m.poster
            m = m.copy(
                originalTitle = m.originalTitle.ifBlank { full.originalTitle }, poster = poster, posterUrl = full.posterUrl,
                description = m.description.ifBlank { full.description }, genres = m.genres.ifBlank { full.genres },
                creators = m.creators.ifBlank { full.creators }, cast = m.cast.ifBlank { full.cast }, countries = m.countries.ifBlank { full.countries },
                length = m.length.ifBlank { full.length }, source = full.source, externalId = full.externalId, externalRating = full.rating, url = full.url,
            )
            if (year.isBlank()) year = full.year?.toString() ?: ""
            searching = false
        }
    }
}

/** Актёры и роли построчно: «Имя — роль». */
@Composable
private fun CastEditor(cast: String, onChange: (String) -> Unit) {
    val rows = cast.lines().filter { it.isNotBlank() }
    val extra = LocalExtra.current
    rows.forEachIndexed { i, line ->
        val name = line.substringBefore(" — ").trim()
        val role = line.substringAfter(" — ", "").trim()
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
            Box(Modifier.weight(1f)) { TextInput(name, { v -> onChange(rows.toMutableList().also { it[i] = if (role.isBlank()) v else "$v — $role" }.joinToString("\n")) }, "Актёр") }
            HGap(6.dp)
            Box(Modifier.weight(1f)) { TextInput(role, { v -> onChange(rows.toMutableList().also { it[i] = if (v.isBlank()) name else "$name — $v" }.joinToString("\n")) }, "Роль") }
            Text("✕", Modifier.clip(RoundedCornerShape(8.dp)).clickable { onChange(rows.toMutableList().also { it.removeAt(i) }.joinToString("\n")) }.padding(8.dp), color = extra.dim)
        }
    }
    TextButton(onClick = { onChange((rows + "Актёр").joinToString("\n")) }) { Text("+ Добавить роль") }
}

@Composable
private fun AddToTopDialog(m: MediaItem, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val lists by observe(emptyList()) { Graph.dao.topLists() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить в топ") },
        text = {
            Column {
                if (lists.isEmpty()) Text("Сначала создайте топ во вкладке «Топы».", color = LocalExtra.current.dim)
                lists.forEach { l ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                            io {
                                val count = Graph.dao.topItems().first().count { it.listId == l.id }
                                Graph.dao.upsertTopItem(TopItem(listId = l.id, title = m.title + (m.year?.let { " ($it)" } ?: ""), rating = m.myRating, note = m.review.take(200), sort = count))
                            }
                            Toast.makeText(ctx, "Добавлено в «${l.title}»", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { Glyph(l.emoji, 20.dp); Text("  " + l.title) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

@Composable
private fun FillFromCatalogDialog(m: MediaItem, onDismiss: () -> Unit, onPick: (MediaHit) -> Unit) {
    val sources = MediaSearch.sourcesFor(m.kind)
    var results by remember { mutableStateOf<List<MediaHit>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(m.title) {
        for (s in sources) {
            val r = runCatching { MediaSearch.search(s, m.kind, m.title) }.getOrNull()
            if (!r.isNullOrEmpty()) { results = r.sortedByDescending { if (m.year != null && it.year == m.year) 1 else 0 }; return@LaunchedEffect }
        }
        results = emptyList(); error = "Не нашлось в каталогах"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Найдено: ${m.title}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (results == null) CircularProgressIndicator()
                error?.let { Text(it) }
                results.orEmpty().take(10).forEach { h ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(h) }.padding(6.dp)) {
                        UrlImage(h.posterUrl, 40.dp)
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(h.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(h.year?.toString(), h.creators.takeIf { it.isNotBlank() }, h.source).joinToString(" · "), fontSize = 11.sp, color = LocalExtra.current.dim)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}
