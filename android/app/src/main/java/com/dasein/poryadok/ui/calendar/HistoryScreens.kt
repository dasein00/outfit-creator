package com.dasein.poryadok.ui.calendar

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.HistoryDay
import com.dasein.poryadok.system.HistoryRepo
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.PrevNext
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.theme.LocalExtra
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_FMT = DateTimeFormatter.ofPattern("d MMMM", Locale("ru"))

/** Календарь → «История»: что произошло в этот день, с подробным контекстом каждого события. */
@Composable
fun HistoryTab(selectedDay: Long) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    var day by rememberSaveable(selectedDay) { mutableLongStateOf(selectedDay) }
    val date = LocalDate.ofEpochDay(day)
    var events by remember(day) { mutableStateOf<List<HistoryDay.Event>?>(null) }
    LaunchedEffect(day) {
        // Сначала сохранённое на телефоне — без ожидания сети, потом, если есть сеть, свежее.
        events = HistoryRepo.day(ctx, date, online = false)
        events = HistoryRepo.day(ctx, date, online = true).takeIf { it.size >= events.orEmpty().size } ?: events
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            Text("‹", fontSize = 26.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { day -= 1 }.padding(horizontal = 14.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(date.format(DAY_FMT), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("В этот день в истории", fontSize = 12.sp, color = extra.dim)
            }
            Text("›", fontSize = 26.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { day += 1 }.padding(horizontal = 14.dp))
        }
        if (day != Dates.today()) TextButton(onClick = { day = Dates.today() }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("К сегодняшнему дню") }
        val list = events
        when {
            list == null -> Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> {
                Text("На этот день событий нет без интернета. Подключитесь — события подгрузятся из Википедии и сохранятся на телефоне.", color = extra.dim, fontSize = 14.sp)
                HistoryRepo.nearestBuiltIn(ctx, date)?.let { (d, l) ->
                    Text("Ближайшие события — ${d.format(DAY_FMT)}:", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                    l.forEach { EventCard(it, startOpen = false) }
                }
            }
            else -> {
                Text("Событий: ${list.size}. Нажмите на событие, чтобы прочитать подробный контекст.", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(bottom = 6.dp))
                list.forEachIndexed { i, e -> EventCard(e, startOpen = i == 0, date = date) }
            }
        }
        Text("Источники: подборка DASEIN и раздел «В этот день» русской Википедии.", fontSize = 11.sp, color = extra.dim, modifier = Modifier.padding(top = 16.dp))
        com.dasein.poryadok.ui.common.OfflineStatusLine(Modifier.padding(top = 4.dp, bottom = 16.dp))
    }
}

@Composable
fun EventCard(e: HistoryDay.Event, startOpen: Boolean, date: LocalDate? = null) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    var open by remember(e) { mutableStateOf(startOpen) }
    val like = historyLike(e, date)
    val liked = com.dasein.poryadok.system.Likes.isLiked(com.dasein.poryadok.ui.common.rememberLikes(), like)
    Tile(Modifier.padding(bottom = 8.dp), onClick = { open = !open }, color = com.dasein.poryadok.ui.common.likedColor(liked)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    e.yearLabel, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f)).padding(horizontal = 8.dp, vertical = 2.dp),
                )
                Text(if (e.source == "DASEIN") e.title else e.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 6.dp))
            }
            com.dasein.poryadok.ui.common.LikeButton(like, liked)
        }
        val body = if (e.source == "DASEIN") e.text else ""
        if (body.isNotBlank()) Text(body, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
        if (e.context.isNotBlank()) {
            if (open) {
                Text("Контекст" + if (e.source != "DASEIN" && e.title.isNotBlank()) " · ${e.title}" else "", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = extra.dim, modifier = Modifier.padding(top = 8.dp))
                Text(e.context, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 2.dp))
                if (e.url.isNotBlank()) TextButton(onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(e.url))) } }) { Text("Читать полностью в Википедии") }
            } else Text("Подробнее ▾", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

internal fun historyLike(e: HistoryDay.Event, date: LocalDate?) = com.dasein.poryadok.system.Likes.Like(
    com.dasein.poryadok.system.Likes.HISTORY,
    title = if (e.source == "DASEIN") e.title else e.text,
    text = listOf(if (e.source == "DASEIN") e.text else "", e.context).filter { it.isNotBlank() }.joinToString(" ").take(600),
    date = date?.let { "%02d-%02d".format(it.monthValue, it.dayOfMonth) }.orEmpty(),
    year = e.year,
    tags = listOfNotNull(e.title.takeIf { e.source != "DASEIN" && it.isNotBlank() }),
)

/**
 * Плашка «В этот день в истории» на главном: событие с контекстом, стрелки ‹ › (позиция сохраняется до конца дня),
 * «+» — отметить понравившееся.
 */
@Composable
fun HomeHistoryCard(nav: NavHostController) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val today = LocalDate.now()
    var events by remember { mutableStateOf(HistoryRepo.cached(today).orEmpty()) }
    val pos = com.dasein.poryadok.ui.common.rememberDayPos("history", today.toEpochDay())
    LaunchedEffect(today) {
        // Повторно (при прокрутке назад) — сразу из памяти, без «пустой» плашки и прыжка списка.
        if (HistoryRepo.cached(today) == null) events = HistoryRepo.day(ctx, today, online = false)
        events = HistoryRepo.day(ctx, today, online = true).takeIf { it.size >= events.size } ?: events
    }
    val list = events.ifEmpty { HistoryRepo.nearestBuiltIn(ctx, today)?.second.orEmpty() }
    if (list.isEmpty()) return
    val i = pos.value.coerceIn(0, list.lastIndex)
    val e = list[i]
    val like = historyLike(e, today)
    val liked = com.dasein.poryadok.system.Likes.isLiked(com.dasein.poryadok.ui.common.rememberLikes(), like)
    Tile(onClick = { nav.navigate(Routes.calendar(3)) }, color = com.dasein.poryadok.ui.common.likedColor(liked)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph("cal/23", 22.dp, badge = false)
            Text("  В этот день в истории", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(today.format(DAY_FMT), fontSize = 12.sp, color = extra.dim)
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(e.yearLabel, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(if (e.source == "DASEIN") e.title else e.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            com.dasein.poryadok.ui.common.LikeButton(like, liked)
        }
        val ctxText = listOf(if (e.source == "DASEIN") e.text else "", e.context).filter { it.isNotBlank() }.joinToString(" ")
        if (ctxText.isNotBlank()) Text(ctxText, fontSize = 13.sp, lineHeight = 18.sp, color = extra.dim, maxLines = 5, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            PrevNext(i, list.size, { pos.value = i - 1 }, { pos.value = i + 1 })
            Box(Modifier.weight(1f))
            TextButton(onClick = { nav.navigate(Routes.calendar(3)) }) { Text("Все →") }
        }
    }
}
