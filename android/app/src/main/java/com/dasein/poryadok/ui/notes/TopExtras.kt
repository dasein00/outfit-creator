package com.dasein.poryadok.ui.notes

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.dasein.poryadok.data.MediaItem
import com.dasein.poryadok.data.MediaKind
import com.dasein.poryadok.data.MediaStatus
import com.dasein.poryadok.data.TopItem
import com.dasein.poryadok.data.TopLinks
import com.dasein.poryadok.logic.TopRank
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.media.Poster
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.launch

/** «Что лучше?»: топ расставляется попарными сравнениями — два варианта, нажмите на тот, что нравится больше. */
@Composable
fun TopRankDialog(entries: List<TopItem>, mediaOf: (TopItem) -> MediaItem?, onDone: (List<TopItem>) -> Unit, onDismiss: () -> Unit) {
    val extra = LocalExtra.current
    val rank = remember(entries) { TopRank(entries) }
    var tick by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val t = tick
    val q = rank.question()
    if (q == null) { androidx.compose.runtime.LaunchedEffect(Unit) { onDone(rank.result()) }; return }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Что лучше?") },
        text = {
            Column {
                Text(
                    "Нажмите на то, что нравится больше. Осталось расставить: ${rank.left} из ${rank.total} · вопросов: ${rank.asked}",
                    fontSize = 12.sp, color = extra.dim,
                )
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(true to q.first, false to q.second).forEach { (isNew, item) ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(extra.cardHigh)
                                .clickable { rank.answer(isNew); tick++ }.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            mediaOf(item)?.let { Poster(it, 96.dp) }
                            Text(item.title, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                            if (item.note.isNotBlank()) Text(item.note, fontSize = 11.sp, color = extra.dim, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                TextButton(onClick = { rank.answer(false); tick++ }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Одинаково / не знаю") }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(rank.result()) }) { Text("Сохранить как есть") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Добавить в топ фильмы, сериалы и книги из коллекции — с постерами и переходом в карточку. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddFromCollectionDialog(listId: Long, media: List<MediaItem>, already: Set<Long>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf<Int?>(null) }
    var status by remember { mutableStateOf<Int?>(null) }
    val picked = remember { mutableStateListOf<Long>() }
    val shown = media.filter { m ->
        m.id !in already && (kind == null || m.kind == kind) && (status == null || m.status == status) &&
            (q.isBlank() || q.trim().lowercase().let { s -> s in m.title.lowercase() || s in m.originalTitle.lowercase() || s in m.creators.lowercase() })
    }.sortedWith(compareByDescending<MediaItem> { it.myRating }.thenByDescending { it.favorite }.thenBy { it.title })
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Из коллекции") },
        text = {
            Column {
                TextInput(q, { q = it }, "Название или режиссёр")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    Pill("Всё", kind == null) { kind = null }
                    MediaKind.names.forEachIndexed { i, n -> Pill(n, kind == i) { kind = if (kind == i) null else i } }
                    Pill("Просмотрено", status == MediaStatus.DONE) { status = if (status == MediaStatus.DONE) null else MediaStatus.DONE }
                    Pill("В планах", status == MediaStatus.PLANNED) { status = if (status == MediaStatus.PLANNED) null else MediaStatus.PLANNED }
                }
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()).padding(top = 6.dp)) {
                    if (shown.isEmpty()) Text("Ничего не нашлось", color = extra.dim, modifier = Modifier.padding(8.dp))
                    shown.take(150).forEach { m ->
                        val on = m.id in picked
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { if (on) picked.remove(m.id) else picked.add(m.id) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(on, { if (on) picked.remove(m.id) else picked.add(m.id) })
                            Poster(m, 34.dp)
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(m.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                                Text(TopLinks.noteOf(m), fontSize = 11.sp, color = extra.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val chosen = picked.mapNotNull { id -> media.firstOrNull { it.id == id } }
                scope.launch {
                    val n = TopLinks.addMedia(ctx, listId, chosen)
                    Toast.makeText(ctx, "Добавлено: $n", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            }, enabled = picked.isNotEmpty()) { Text("Добавить (${picked.size})") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Готовые топы из коллекции: по моим оценкам, избранное, лучшее за год — одним касанием. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReadyTops(nav: NavHostController, media: List<MediaItem>, lists: List<com.dasein.poryadok.data.TopList>) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    val year = java.time.LocalDate.now().year
    fun best(k: Int) = media.filter { it.kind == k && it.myRating > 0 }.sortedWith(compareByDescending<MediaItem> { it.myRating }.thenByDescending { it.favorite }).take(25)
    val ideas = listOf(
        Triple("Лучшие фильмы", "habit/01", best(MediaKind.MOVIE)),
        Triple("Лучшие сериалы", "habit/03", best(MediaKind.SERIES)),
        Triple("Лучшие книги", "habit/11", best(MediaKind.BOOK)),
        Triple("Любимое", "habit/28", media.filter { it.favorite }.sortedByDescending { it.myRating }.take(30)),
        Triple(
            "Итоги $year года", "fest/29",
            media.filter { m -> m.status == MediaStatus.DONE && m.finishedDay?.let { java.time.LocalDate.ofEpochDay(it).year == year } == true }
                .sortedByDescending { it.myRating }.take(30),
        ),
        Triple(
            "Самое рейтинговое в планах", "ui:trophy",
            media.filter { it.status == MediaStatus.PLANNED && it.externalRating != null }.sortedByDescending { it.externalRating }.take(20),
        ),
    ).filter { it.third.size >= 2 }
    if (ideas.isEmpty()) return
    Text("Собрать из коллекции", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
    Text("Готовый топ по вашим оценкам — дальше его можно править и уточнить кнопкой «Что лучше?».", fontSize = 12.sp, color = extra.dim)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)) {
        ideas.forEach { (title, glyph, items) ->
            Pill("$title · ${items.size}", false, glyph = glyph) {
                scope.launch {
                    // Такой топ уже есть — дополняем его новым, порядок не трогаем.
                    val old = lists.firstOrNull { it.title == title }
                    val id = if (old != null) old.id.also { TopLinks.addMedia(ctx, it, items) } else TopLinks.createFrom(ctx, title, glyph, items)
                    nav.navigate(Routes.top(id))
                }
            }
        }
    }
}

/** Мини-постеры первых пунктов топа на карточке списка. */
@Composable
fun TopPosters(items: List<MediaItem>) {
    if (items.isEmpty()) return
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.take(5).forEachIndexed { i, m ->
            Box {
                Poster(m, 46.dp)
                Box(
                    Modifier.padding(3.dp).size(16.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) { Text("${i + 1}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) }
            }
        }
    }
}
