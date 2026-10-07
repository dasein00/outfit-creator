package com.dasein.poryadok.ui.calendar

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.logic.CultureDay
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.system.CultureRepo
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.PrevNext
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.theme.LocalExtra
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CDAY = DateTimeFormatter.ofPattern("d MMMM", Locale("ru"))
private val REGION_COLOR = mapOf("Россия" to Color(0xFFD25B4B), "США" to Color(0xFF5B8DD2), "Мир" to Color(0xFF4BA38C))

private fun kindLabel(i: CultureDay.Item, now: Int) = if (i.kind == CultureDay.BIRTH) {
    val n = i.yearsAgo(now)
    "Родился(ась) в ${i.yearLabel}" + if (n > 0 && n % 5 == 0) " · $n лет со дня рождения" else ""
} else "${i.yearLabel} · ${i.yearsAgo(now).let { if (it > 0) "$it лет назад" else "в этом году" }}"

/** Календарь → «Культура»: дни рождения звёзд и культурные события дня с фильтрами по стране и типу. */
@Composable
fun CultureTab(selectedDay: Long) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    var day by rememberSaveable(selectedDay) { mutableLongStateOf(selectedDay) }
    val date = LocalDate.ofEpochDay(day)
    var items by remember(day) { mutableStateOf<List<CultureDay.Item>?>(null) }
    var region by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(day) {
        items = CultureRepo.day(ctx, date, online = false)
        items = CultureRepo.day(ctx, date, online = true)
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            Text("‹", fontSize = 26.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { day -= 1 }.padding(horizontal = 14.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(date.format(CDAY), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("В этот день в культуре", fontSize = 12.sp, color = extra.dim)
            }
            Text("›", fontSize = 26.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { day += 1 }.padding(horizontal = 14.dp))
        }
        if (day != Dates.today()) TextButton(onClick = { day = Dates.today() }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("К сегодняшнему дню") }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("Вся культура", kind.isEmpty()) { kind = "" }
            Pill("Дни рождения", kind == CultureDay.BIRTH) { kind = if (kind == CultureDay.BIRTH) "" else CultureDay.BIRTH }
            Pill("Премьеры и события", kind == CultureDay.EVENT) { kind = if (kind == CultureDay.EVENT) "" else CultureDay.EVENT }
        }
        Row(Modifier.padding(top = 6.dp, bottom = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("Все страны", region.isEmpty()) { region = "" }
            CultureDay.REGIONS.forEach { r -> Pill(r, region == r) { region = if (region == r) "" else r } }
        }
        val list = items
        when {
            list == null -> Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> {
                val shown = list.filter { (kind.isEmpty() || it.kind == kind) && (region.isEmpty() || it.region == region) }
                if (shown.isEmpty()) Text(
                    if (list.isEmpty()) "Без интернета на этот день в подборке ничего нет. Подключитесь — дни рождения звёзд и премьеры подгрузятся из Википедии и сохранятся на телефоне."
                    else "Под эти фильтры ничего нет — попробуйте «Все страны».",
                    color = extra.dim, fontSize = 14.sp,
                ) else Text("Найдено: ${shown.size}. Нажмите, чтобы прочитать подробнее.", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(bottom = 6.dp))
                shown.forEach { CultureCard(it, date) }
            }
        }
        Text("Источники: подборка DASEIN и раздел «В этот день» русской Википедии.", fontSize = 11.sp, color = extra.dim, modifier = Modifier.padding(vertical = 16.dp))
    }
}

@Composable
private fun RegionBadge(r: String) {
    val c = REGION_COLOR[r] ?: MaterialTheme.colorScheme.primary
    Text(r, fontSize = 11.sp, color = c, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.copy(alpha = .14f)).padding(horizontal = 7.dp, vertical = 2.dp))
}

@Composable
fun CultureCard(i: CultureDay.Item, date: LocalDate? = null) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    var open by remember(i) { mutableStateOf(false) }
    val now = LocalDate.now().year
    val like = cultureLike(i, date)
    val liked = com.dasein.poryadok.system.Likes.isLiked(com.dasein.poryadok.ui.common.rememberLikes(), like)
    Tile(Modifier.padding(bottom = 8.dp), onClick = { open = !open }, color = com.dasein.poryadok.ui.common.likedColor(liked)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (i.kind == CultureDay.BIRTH) "🎂" else "🎬", fontSize = 15.sp)
                    RegionBadge(i.region)
                }
                Text(i.name, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, modifier = Modifier.padding(top = 4.dp))
                Text(kindLabel(i, now), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Text(i.text, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp), maxLines = if (open) 20 else 3, overflow = TextOverflow.Ellipsis)
            }
            com.dasein.poryadok.ui.common.LikeButton(like, liked)
        }
        if (open && i.context.isNotBlank()) Text(i.context, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp))
        if (open && i.url.isNotBlank()) TextButton(onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(i.url))) } }) { Text("Читать в Википедии") }
        if (!open && i.context.isNotBlank()) Text("Подробнее ▾", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
    }
}

internal fun cultureLike(i: CultureDay.Item, date: LocalDate?) = com.dasein.poryadok.system.Likes.Like(
    com.dasein.poryadok.system.Likes.CULTURE,
    title = i.name, text = i.text, date = date?.let { "%02d-%02d".format(it.monthValue, it.dayOfMonth) }.orEmpty(), year = i.year,
    tags = listOf(if (i.kind == CultureDay.BIRTH) "день рождения" else "событие", i.region),
)

/** Плашка «В этот день в культуре» на главном: звезда или премьера дня, «Ещё», переход в календарь. */
@Composable
fun HomeCultureCard(nav: NavHostController) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val today = LocalDate.now()
    var items by remember { mutableStateOf(CultureRepo.cached(today).orEmpty()) }
    val pos = com.dasein.poryadok.ui.common.rememberDayPos("culture", today.toEpochDay())
    val likes = com.dasein.poryadok.ui.common.rememberLikes()
    LaunchedEffect(today) {
        if (CultureRepo.cached(today) == null) items = CultureRepo.day(ctx, today, online = false)
        items = CultureRepo.day(ctx, today, online = true).takeIf { it.size >= items.size } ?: items
    }
    val list = items
    val cur = list.getOrNull(pos.value.coerceIn(0, (list.size - 1).coerceAtLeast(0)))
    val curLiked = cur != null && com.dasein.poryadok.system.Likes.isLiked(likes, cultureLike(cur, today))
    Tile(onClick = { nav.navigate(Routes.calendar(4)) }, color = com.dasein.poryadok.ui.common.likedColor(curLiked)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph("habit/01", 22.dp, badge = false)
            Text("  В этот день в культуре", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(today.format(CDAY), fontSize = 12.sp, color = extra.dim)
        }
        if (list.isEmpty()) {
            Text("Дни рождения звёзд и премьеры подгрузятся из Википедии, когда будет интернет.", fontSize = 13.sp, color = extra.dim, modifier = Modifier.padding(top = 6.dp))
            return@Tile
        }
        val n = pos.value.coerceIn(0, list.lastIndex)
        val i = list[n]
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (i.kind == CultureDay.BIRTH) "🎂 День рождения" else "🎬 Событие", fontSize = 12.sp, color = extra.dim)
                    RegionBadge(i.region)
                }
                Text(i.name, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, modifier = Modifier.padding(top = 2.dp))
                Text(kindLabel(i, today.year), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Text(i.text, fontSize = 13.sp, lineHeight = 18.sp, color = extra.dim, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
            com.dasein.poryadok.ui.common.LikeButton(cultureLike(i, today), curLiked)
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            PrevNext(n, list.size, { pos.value = n - 1 }, { pos.value = n + 1 })
            Box(Modifier.weight(1f))
            TextButton(onClick = { nav.navigate(Routes.calendar(4)) }) { Text("Все →") }
        }
    }
}
