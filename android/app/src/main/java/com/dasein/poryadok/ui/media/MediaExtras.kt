@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.MediaItem
import com.dasein.poryadok.data.MediaKind
import com.dasein.poryadok.data.MediaStatus
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.MediaCatalog
import com.dasein.poryadok.ui.common.Bar
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.HGap
import com.dasein.poryadok.ui.common.NumberField
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.SheetHeader
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.num
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlin.math.roundToInt

/** Порядок в коллекции. */
enum class MediaSort(val label: String) {
    NEW("Сначала новые"), MY("По моей оценке"), EXT("По рейтингу каталога"), TITLE("По названию"),
    YEAR("По году выхода"), FINISHED("По дате просмотра"), PROGRESS("По прогрессу"),
}

fun sortMedia(list: List<MediaItem>, sort: MediaSort): List<MediaItem> = when (sort) {
    MediaSort.NEW -> list.sortedByDescending { it.createdAt }
    MediaSort.MY -> list.sortedWith(compareByDescending<MediaItem> { it.myRating }.thenByDescending { it.externalRating ?: 0.0 })
    MediaSort.EXT -> list.sortedByDescending { it.externalRating ?: 0.0 }
    MediaSort.TITLE -> list.sortedBy { it.title.lowercase() }
    MediaSort.YEAR -> list.sortedByDescending { it.year ?: 0 }
    MediaSort.FINISHED -> list.sortedByDescending { it.finishedDay ?: Long.MIN_VALUE }
    MediaSort.PROGRESS -> list.sortedByDescending { MediaCatalog.progressShare(it) ?: -1f }
}

/** +1 серия или страница; на последней — отметка «просмотрено» с сегодняшней датой. */
fun stepProgress(m: MediaItem, delta: Int): MediaItem {
    val total = MediaCatalog.total(m)
    val p = (m.progress + delta).coerceAtLeast(0).let { if (total > 0) it.coerceAtMost(total) else it }
    val started = m.startedDay ?: Dates.today()
    val status = when {
        total > 0 && p >= total -> MediaStatus.DONE
        p > 0 && m.status == MediaStatus.PLANNED -> MediaStatus.IN_PROGRESS
        else -> m.status
    }
    return m.copy(
        progress = p, startedDay = started, status = status,
        finishedDay = if (status == MediaStatus.DONE && m.status != MediaStatus.DONE) Dates.today() else m.finishedDay,
    )
}

/** «Продолжить»: то, что сейчас смотрю или читаю, с прогрессом и кнопкой +1. */
@Composable
fun ContinueShelf(items: List<MediaItem>, onOpen: (MediaItem) -> Unit) {
    if (items.isEmpty()) return
    val extra = LocalExtra.current
    Text("Продолжить", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items, key = { it.id }) { m ->
            Column(Modifier.width(118.dp).clip(RoundedCornerShape(12.dp)).clickable { onOpen(m) }) {
                Poster(m, 160.dp, Modifier.fillMaxWidth())
                Text(m.title, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                MediaCatalog.progressShare(m)?.let { Bar(it, MaterialTheme.colorScheme.primary, height = 4.dp) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(MediaCatalog.progressText(m) ?: MediaStatus.names(m.kind)[m.status], fontSize = 11.sp, color = extra.dim, maxLines = 1, modifier = Modifier.weight(1f))
                    if (m.kind != MediaKind.MOVIE) Text(
                        "+1", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { io { Graph.extra.upsertMedia(stepProgress(m, 1)) } }.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
    Gap(6.dp)
}

/** Строка списка: постер, название, год и жанры, автор, оценки, статус и прогресс. */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun MediaRow(m: MediaItem, onLong: (() -> Unit)? = null, onOpen: () -> Unit) {
    val extra = LocalExtra.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).combinedClickable(onClick = onOpen, onLongClick = onLong).padding(vertical = 4.dp)) {
        Poster(m, 64.dp)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(m.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(m.year?.toString(), MediaCatalog.split(m.genres).take(2).joinToString(", ").ifBlank { null }, m.length.ifBlank { null })
            if (meta.isNotEmpty()) Text(meta.joinToString(" · "), fontSize = 12.sp, color = extra.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (m.creators.isNotBlank()) Text(m.creators, fontSize = 12.sp, color = extra.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                if (m.myRating > 0) Text("★ ${m.myRating}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                m.externalRating?.let { Text("  ${m.source}: %.1f".format(it), fontSize = 12.sp, color = extra.dim) }
                Text("  " + MediaStatus.names(m.kind)[m.status], fontSize = 12.sp, color = extra.dim, maxLines = 1)
                if (m.favorite) Text("  ♥", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
            MediaCatalog.progressText(m)?.let { t ->
                Text(t, fontSize = 11.sp, color = extra.dim)
                MediaCatalog.progressShare(m)?.let { Bar(it, MaterialTheme.colorScheme.primary, height = 4.dp) }
            }
        }
    }
}

/** «Что посмотреть / почитать?»: случайный выбор из списка «Хочу» с учётом фильтров. */
@Composable
fun RandomPickDialog(kind: Int, candidates: List<MediaItem>, source: String = "", onOpen: (MediaItem) -> Unit, onDismiss: () -> Unit) {
    val extra = LocalExtra.current
    var pick by remember { mutableStateOf(candidates.randomOrNull()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (kind == MediaKind.BOOK) "Что почитать?" else "Что посмотреть?") },
        text = {
            val m = pick
            if (m == null) Text("В ${source.ifBlank { "«${MediaStatus.names(kind)[MediaStatus.PLANNED]}»" }} пока пусто. Добавьте туда то, что хотите посмотреть или прочитать. Долгое нажатие на кнопку — выбрать другую папку.")
            else Row {
                Poster(m, 96.dp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(m.title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Text(listOfNotNull(m.year?.toString(), m.genres.ifBlank { null }).joinToString(" · "), fontSize = 12.sp, color = extra.dim)
                    if (m.creators.isNotBlank()) Text(m.creators, fontSize = 12.sp, color = extra.dim)
                    if (m.description.isNotBlank()) Text(m.description, fontSize = 13.sp, maxLines = 5, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    Text("Из ${candidates.size} вариантов" + if (source.isNotBlank()) " · $source" else "", fontSize = 11.sp, color = extra.dim, modifier = Modifier.padding(top = 6.dp))
                }
            }
        },
        confirmButton = { pick?.let { m -> TextButton(onClick = { onOpen(m) }) { Text("Открыть") } } },
        dismissButton = {
            Row {
                if (candidates.size > 1) TextButton(onClick = { pick = (candidates - setOfNotNull(pick)).randomOrNull() ?: pick }) { Text("Другой") }
                TextButton(onClick = onDismiss) { Text("Закрыть") }
            }
        },
    )
}

/** Статистика коллекции: статусы, оценки, жанры, авторы, страны, годы и сколько времени. */
@Composable
fun MediaStatsSheet(kind: Int, items: List<MediaItem>, onDismiss: () -> Unit) {
    val st = remember(items) { MediaCatalog.stats(items) }
    val extra = LocalExtra.current
    val scheme = MaterialTheme.colorScheme
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        SheetHeader("Статистика: ${MediaKind.plural[kind].lowercase()}", onDismiss)
        LazyColumn(Modifier.heightIn(max = 620.dp).padding(horizontal = 20.dp)) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("${st.total}", "всего")
                    MediaStatus.names(kind).forEachIndexed { i, n -> StatTile("${st.byStatus[i] ?: 0}", n.lowercase()) }
                    StatTile("${st.favorites}", "любимых")
                    if (st.rated > 0) StatTile("%.1f".format(st.avgRating), "средняя оценка")
                    val amount: Pair<String, String>? = when (kind) {
                        MediaKind.MOVIE -> if (st.doneMinutes > 0) "${(st.doneMinutes / 60.0).roundToInt()} ч" to "у экрана" else null
                        MediaKind.SERIES -> if (st.doneEpisodes > 0) "${st.doneEpisodes}" to "серий" else null
                        else -> if (st.donePages > 0) "${st.donePages}" to "страниц" else null
                    }
                    amount?.let { StatTile(it.first, it.second) }
                }
            }
            if (st.rated > 0) item {
                SectionTitle("Мои оценки")
                val max = st.ratingBars.maxOrNull()?.coerceAtLeast(1) ?: 1
                Row(Modifier.fillMaxWidth().height(110.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    st.ratingBars.forEachIndexed { i, n ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (n > 0) Text("$n", fontSize = 10.sp, color = extra.dim)
                            Box(
                                Modifier.fillMaxWidth().height((80f * n / max).coerceAtLeast(2f).dp)
                                    .background(if (n > 0) scheme.primary else scheme.outline.copy(alpha = .3f), RoundedCornerShape(4.dp)),
                            )
                            Text("${i + 1}", fontSize = 11.sp, color = extra.dim)
                        }
                    }
                }
            }
            topBlock(this, "Любимые жанры", st.topGenres)
            topBlock(this, if (kind == MediaKind.BOOK) "Авторы" else "Режиссёры", st.topCreators)
            topBlock(this, "Страны", st.topCountries)
            if (st.byYear.isNotEmpty()) item {
                SectionTitle(if (kind == MediaKind.BOOK) "Прочитано по годам" else "Просмотрено по годам")
                st.byYear.forEach { (y, n) ->
                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("$y", Modifier.width(52.dp), fontSize = 14.sp)
                        Box(Modifier.weight(1f)) { Bar(n / (st.byYear.maxOf { it.second }).toFloat(), scheme.primary, height = 8.dp) }
                        Text("  $n", fontSize = 13.sp, color = extra.dim)
                    }
                }
            }
            item { Gap(28.dp) }
        }
    }
}

private fun topBlock(scope: androidx.compose.foundation.lazy.LazyListScope, title: String, top: List<Pair<String, Int>>) {
    if (top.isEmpty()) return
    scope.item {
        val extra = LocalExtra.current
        SectionTitle(title)
        val max = top.maxOf { it.second }.toFloat()
        top.forEach { (name, n) ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.width(130.dp), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.weight(1f)) { Bar(n / max, MaterialTheme.colorScheme.primary, height = 8.dp) }
                Text("  $n", fontSize = 13.sp, color = extra.dim)
            }
        }
    }
}

@Composable
private fun StatTile(value: String, label: String) {
    Tile(padding = 10.dp) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 11.sp, color = LocalExtra.current.dim)
    }
}

/** Прогресс в карточке: «серия 5 из 24», кнопки −1 / +1, всего, пересмотры. */
@Composable
fun ProgressEditor(m: MediaItem, onChange: (MediaItem) -> Unit) {
    val extra = LocalExtra.current
    val book = m.kind == MediaKind.BOOK
    var total by remember(m.id) { mutableStateOf(if (m.progressTotal > 0) "${m.progressTotal}" else "") }
    if (m.kind != MediaKind.MOVIE) {
        val auto = MediaCatalog.count(m.length, m.kind)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (book) "Прочитано страниц" else "Просмотрено серий", Modifier.weight(1f), fontSize = 14.sp)
            OutlinedButton(onClick = { onChange(stepProgress(m, -1)) }) { Text("−1") }
            Text("${m.progress}", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))
            OutlinedButton(onClick = { onChange(stepProgress(m, if (book) 10 else 1)) }) { Text(if (book) "+10" else "+1") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            NumberField(total, { total = it; onChange(m.copy(progressTotal = it.num()?.toInt() ?: 0)) }, if (book) "Всего страниц" else "Всего серий", Modifier.weight(1f), decimal = false)
            HGap(8.dp)
            Text(if (auto != null && m.progressTotal == 0) "из описания: $auto" else "", fontSize = 12.sp, color = extra.dim, modifier = Modifier.weight(1f))
        }
        MediaCatalog.progressShare(m)?.let {
            Gap(6.dp)
            Bar(it, MaterialTheme.colorScheme.primary, height = 6.dp)
            Text("${(it * 100).roundToInt()}% · ${MediaCatalog.progressText(m)}", fontSize = 12.sp, color = extra.dim)
        }
        Gap(8.dp)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (book) "Перечитывал" else "Пересматривал", Modifier.weight(1f), fontSize = 14.sp)
        OutlinedButton(onClick = { onChange(m.copy(rewatch = (m.rewatch - 1).coerceAtLeast(0))) }) { Text("−") }
        Text("${m.rewatch} раз", fontSize = 15.sp, modifier = Modifier.padding(horizontal = 10.dp))
        OutlinedButton(onClick = { onChange(m.copy(rewatch = m.rewatch + 1)) }) { Text("+") }
    }
    m.startedDay?.let { Text("Начато: ${Dates.full(it)}", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp)) }
}

/** «Похожее в коллекции» по жанрам и авторам. */
@Composable
fun SimilarShelf(items: List<MediaItem>, onOpen: (MediaItem) -> Unit) {
    if (items.isEmpty()) return
    SectionTitle("Похожее в коллекции")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items, key = { it.id }) { m ->
            Column(Modifier.width(96.dp).clip(RoundedCornerShape(10.dp)).clickable { onOpen(m) }) {
                Poster(m, 130.dp, Modifier.fillMaxWidth())
                Text(m.title, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                if (m.myRating > 0) Text("★ ${m.myRating}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** Кнопка-пилюля с иконкой и долгим нажатием (у OutlinedButton долгого нажатия нет). */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun IconPillButton(glyph: String, text: String, modifier: Modifier = Modifier, onLong: (() -> Unit)? = null, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(50),
        color = androidx.compose.ui.graphics.Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.clip(RoundedCornerShape(50)).combinedClickable(onClick = onClick, onLongClick = onLong),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
            com.dasein.poryadok.ui.common.Glyph(glyph, 24.dp)
            Text(" $text", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
        }
    }
}
