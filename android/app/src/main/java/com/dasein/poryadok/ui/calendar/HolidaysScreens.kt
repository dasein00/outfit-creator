@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.calendar

import android.content.Context
import com.dasein.poryadok.ui.common.HowTo
import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.CustomDay
import com.dasein.poryadok.data.DayEntry
import com.dasein.poryadok.data.DayKindTag
import com.dasein.poryadok.data.HolidayMark
import com.dasein.poryadok.data.HolidayRepo
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.Holiday
import com.dasein.poryadok.logic.HolidayCats
import com.dasein.poryadok.logic.HolidayRules
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.DatePickDialog
import com.dasein.poryadok.ui.common.FieldButton
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.HGap
import com.dasein.poryadok.ui.common.NumberField
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Segments
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.UiState
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.theme.LocalExtra
import java.time.LocalDate

/** Цвета для выделения праздников и своих дней. */
internal val MARK_COLORS = listOf(
    0xFFD25B4B, 0xFFE08A3C, 0xFFE0B040, 0xFF8CC46E, 0xFF4BA38C, 0xFF5B8DD2, 0xFFB06FC4, 0xFFE06A9A,
).map { it.toInt() }

private const val HIDDEN_KEY = "holiday_hidden"

internal fun hiddenCats(ctx: Context): Set<String> = UiState.str(ctx, HIDDEN_KEY, "").split(',').filter { it.isNotBlank() }.toSet()

/** Праздники и свои дни на дату — для «Главного», месяца и вкладки «Праздники». */
@Composable
fun rememberDayEntries(day: Long, version: Int = 0): List<DayEntry> {
    val ctx = LocalContext.current
    val custom by observe(emptyList()) { Graph.days.customDays() }
    val marks by observe(emptyList()) { Graph.days.marks() }
    return remember(day, custom, marks, version) { HolidayRepo.entries(ctx, Dates.day(day), custom, marks, hiddenCats(ctx)) }
}

@Composable
internal fun ColorDot(color: Int, size: Int = 12) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Color(color)))
}

@Composable
internal fun EntryRow(e: DayEntry, onClick: () -> Unit, dateText: String? = null, expanded: Boolean? = null) {
    val extra = LocalExtra.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(36.dp).clip(RoundedCornerShape(2.dp)).background(Color(e.color)))
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(e.name, fontWeight = FontWeight.Medium)
            Text(listOfNotNull(dateText, e.subtitle.takeIf { it.isNotBlank() }).joinToString(" · "), fontSize = 12.sp, color = extra.dim)
        }
        if (e.off) Text(
            if (e.cat == "am" || e.cat == "arm") "выходной в Армении" else "выходной", fontSize = 11.sp, color = Color(0xFFD25B4B),
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x22D25B4B)).padding(horizontal = 6.dp, vertical = 2.dp),
        )
        if (expanded != null) Text(if (expanded) "▴" else "▾", fontSize = 16.sp, color = extra.dim, modifier = Modifier.padding(start = 8.dp))
    }
}

private fun daysWord(n: Long): String {
    val m10 = n % 10
    val m100 = n % 100
    return "$n " + when {
        m10 == 1L && m100 != 11L -> "день"
        m10 in 2L..4L && m100 !in 12L..14L -> "дня"
        else -> "дней"
    }
}

/** «через 5 дней», «сегодня», «завтра». */
internal fun whenText(d: LocalDate): String {
    val n = d.toEpochDay() - Dates.today()
    return when {
        n == 0L -> "сегодня"
        n == 1L -> "завтра"
        n == 2L -> "послезавтра"
        n > 0 -> "через ${daysWord(n)}"
        else -> "${daysWord(-n)} назад"
    }
}

/**
 * Праздник или свой день в списке: по нажатию раскрывается подробная информация (история, традиции, факты,
 * даты, выделение цветом), повторное нажатие — сворачивает.
 */
@Composable
internal fun ExpandableEntry(e: DayEntry, day: Long, dateText: String? = null) {
    val extra = LocalExtra.current
    var open by rememberSaveable(e.key, day) { mutableStateOf(false) }
    var edit by remember { mutableStateOf(false) }
    val marks by observe(emptyList()) { Graph.days.marks() }
    Tile(Modifier.padding(bottom = 6.dp).animateContentSize(), padding = 0.dp) {
        EntryRow(e, { open = !open }, dateText, expanded = open)
        if (open) Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
            val c = e.custom
            val h = e.holiday
            if (c != null) {
                Text(DayKindTag.names[c.kind] ?: "", color = extra.dim, fontSize = 13.sp)
                Text("${c.day} ${MONTHS_GEN[c.month - 1]}" + (c.year?.let { " $it" } ?: "") + if (c.yearly) " · каждый год" else "", fontSize = 13.sp)
                if (c.note.isNotBlank()) {
                    Gap(6.dp)
                    Text(c.note, lineHeight = 21.sp)
                }
                Gap(6.dp)
                OutlinedButton(onClick = { edit = true }) { Text("Изменить") }
            } else if (h != null) {
                HolidayDetails(h, marks.firstOrNull { it.holidayId == h.id })
            }
        }
    }
    if (edit) e.custom?.let { CustomDayDialog(it) { edit = false } }
}

/** Подробности о празднике: даты, история, традиции, факты, цвет, «Поделиться». */
@Composable
internal fun HolidayDetails(h: Holiday, mark: HolidayMark?) {
    val extra = LocalExtra.current
    val ctx = LocalContext.current
    val today = LocalDate.now()
    val dates = (today.year..today.year + 2).mapNotNull { y -> HolidayRules.dateIn(h.rule, y) }.filter { it >= today }.take(2)
    val movable = !h.rule.startsWith("fixed")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip(HolidayCats.names[h.cat] ?: "", Color((HolidayCats.colors[h.cat] ?: 0xFF888888L).toInt()))
        if (h.off) Chip(if (h.cat == "am" || h.cat == "arm") "нерабочий день в Армении" else "нерабочий день в России", Color(0xFFD25B4B))
        if (movable) Chip("дата меняется каждый год", extra.dim)
    }
    Gap(8.dp)
    Text(HolidayRules.describe(h.rule), color = extra.dim, fontSize = 13.sp)
    dates.forEach { d ->
        Text(
            "${Dates.weekdayFull(d.toEpochDay())}, ${Dates.full(d.toEpochDay())} — ${whenText(d)}",
            fontWeight = FontWeight.Medium, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp),
        )
    }
    InfoPart("Откуда появился", h.about)
    InfoPart("Как отмечают", h.how)
    InfoPart("Интересно знать", h.facts)
    val like = com.dasein.poryadok.system.Likes.Like(
        com.dasein.poryadok.system.Likes.HOLIDAY, h.name, h.about.take(500), h.rule.removePrefix("fixed:"), 0, listOfNotNull(HolidayCats.names[h.cat]),
    )
    val liked = com.dasein.poryadok.system.Likes.isLiked(com.dasein.poryadok.ui.common.rememberLikes(), like)
    Row(
        Modifier.padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).background(com.dasein.poryadok.ui.common.likedColor(liked)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.dasein.poryadok.ui.common.LikeButton(like, liked, 30)
        Text(if (liked) "  В понравившемся" else "  Нравится — добавить в понравившееся", fontSize = 13.sp)
    }
    Text("Выделить цветом в календаре", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MARK_COLORS.forEach { c ->
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(Color(c))
                    .then(if (mark?.color == c) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                    .clickable { io { if (mark?.color == c) Graph.days.deleteMark(h.id) else Graph.days.upsertMark(HolidayMark(h.id, c)) } },
            )
        }
    }
    Row {
        if (mark != null) TextButton(onClick = { io { Graph.days.deleteMark(h.id) } }) { Text("Без выделения") }
        TextButton(onClick = {
            val text = listOf(h.name, HolidayRules.describe(h.rule), h.about, h.how, h.facts).filter { it.isNotBlank() }.joinToString("\n\n")
            ctx.startActivity(
                Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "Поделиться")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }) { Text("Поделиться") }
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Text(
        text, fontSize = 11.sp, color = color,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = .13f)).padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
private fun InfoPart(title: String, text: String) {
    if (text.isBlank()) return
    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
    Text(text, lineHeight = 21.sp, fontSize = 14.sp)
}

/** Праздники следующих [days] дней после [from] (важные категории и свои дни). */
internal fun upcomingEntries(
    ctx: Context, from: Long, days: Int, custom: List<CustomDay>, marks: List<HolidayMark>, hidden: Set<String>,
): List<Pair<LocalDate, DayEntry>> = (1..days).flatMap { k ->
    val d = Dates.day(from + k)
    HolidayRepo.entries(ctx, d, custom, marks, hidden)
        .filter { it.cat == "my" || it.off || it.cat in setOf("ru", "am", "orth", "arm", "intl") }
        .map { d to it }
}

private var lastCustom: List<com.dasein.poryadok.data.CustomDay> = emptyList()
private var lastMarks: List<com.dasein.poryadok.data.HolidayMark> = emptyList()

private data class BannerLine(val entry: DayEntry, val label: String, val mine: Boolean)

/**
 * Плашка на «Главном» под приветствием. Первыми — ваши дни сегодня и завтра, затем праздники сегодня;
 * если сегодня ничего нет — ближайшие праздники.
 */
@Composable
fun HolidayBanner(nav: NavHostController) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    // Последние значения из базы — чтобы при возврате плашки на экран она сразу была той же высоты, без прыжка.
    val custom by observe(lastCustom) { Graph.days.customDays() }
    val marks by observe(lastMarks) { Graph.days.marks() }
    lastCustom = custom; lastMarks = marks
    val today = Dates.today()
    val data = remember(today, custom, marks) {
        val hidden = hiddenCats(ctx)
        val t = HolidayRepo.entries(ctx, Dates.day(today), custom, marks, hidden)
        val tm = HolidayRepo.entries(ctx, Dates.day(today + 1), custom, marks, hidden)
        val lines = buildList {
            t.filter { it.custom != null }.forEach { add(BannerLine(it, "сегодня", true)) }
            tm.filter { it.custom != null }.forEach { add(BannerLine(it, "завтра", true)) }
            t.filter { it.custom == null }.forEach { add(BannerLine(it, "сегодня", false)) }
        }
        // Если сегодня ничего нет — ближайшие важные праздники; если и их нет (например, виды скрыты) — любые ближайшие.
        fun soon(list: List<Pair<LocalDate, DayEntry>>) = list.sortedWith(compareBy({ it.first }, { it.second.custom == null }))
            .take(3)
            .map { (d, e) -> BannerLine(e, "${Dates.weekdayShort(d.toEpochDay())}, ${Dates.short(d.toEpochDay())} · ${whenText(d)}", e.custom != null) }
        when {
            lines.isNotEmpty() -> false to lines
            else -> true to soon(upcomingEntries(ctx, today, 45, custom, marks, hidden)).ifEmpty {
                soon((1..30).flatMap { k -> Dates.day(today + k).let { d -> HolidayRepo.entries(ctx, d, custom, marks).map { d to it } } })
            }
        }
    }
    val (upcoming, lines) = data
    if (lines.isEmpty()) {
        // Плашка не исчезает совсем: даже без праздников ведёт в календарь праздников.
        Gap(8.dp)
        Tile(onClick = { nav.navigate(Routes.calendar(2)) }, padding = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph("fest/03", 26.dp, badge = false)
                Text("Праздники и события — открыть календарь", fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp).weight(1f))
                Text("›", fontSize = 22.sp, color = extra.dim)
            }
        }
        return
    }
    Gap(8.dp)
    Tile(onClick = { nav.navigate(Routes.calendar(2)) }, color = Color(lines.first().entry.color).copy(alpha = .14f), padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph("fest/03", 26.dp, badge = false)
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(
                    when {
                        upcoming -> "Сегодня праздников нет · ближайшие"
                        lines.all { it.mine } -> "Ваши события"
                        lines.size == 1 -> "Сегодня праздник"
                        else -> "Праздники и события"
                    },
                    fontSize = 12.sp, color = extra.dim,
                )
                lines.take(3).forEach { l ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
                        ColorDot(l.entry.color, 8)
                        HGap(6.dp)
                        Column {
                            val age = l.entry.subtitle.takeIf { l.mine && l.entry.custom?.kind == DayKindTag.BIRTHDAY && it.startsWith("исполняется") }
                            Text(l.entry.name + (age?.let { " — $it" } ?: ""), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2)
                            if (upcoming || l.label != "сегодня") Text(l.label, fontSize = 12.sp, color = extra.dim)
                        }
                    }
                }
                if (lines.size > 3) Text("и ещё ${lines.size - 3}", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 2.dp))
            }
            Text("›", fontSize = 22.sp, color = extra.dim)
        }
    }
}

/** Вкладка «Праздники»: праздники выбранного дня, ближайшие, свои дни и внизу — фильтр по видам. */
@Composable
fun HolidaysTab(nav: NavHostController, selected: Long, onSelect: (Long) -> Unit) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    var hidden by remember { mutableStateOf(hiddenCats(ctx)) }
    var editing by remember { mutableStateOf<CustomDay?>(null) }
    val custom by observe(emptyList()) { Graph.days.customDays() }
    val marks by observe(emptyList()) { Graph.days.marks() }
    val entries = remember(selected, custom, marks, hidden) { HolidayRepo.entries(ctx, Dates.day(selected), custom, marks, hidden) }
    val upcoming = remember(selected, custom, marks, hidden) { upcomingEntries(ctx, selected, 30, custom, marks, hidden).take(15) }

    LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onSelect(selected - 1) }) { Icon(Icons.Default.ChevronLeft, "Раньше") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${Dates.weekdayFull(selected)}, ${Dates.full(selected)}", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    if (selected != Dates.today()) Text(
                        "к сегодняшнему дню", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onSelect(Dates.today()) },
                    )
                }
                IconButton(onClick = { onSelect(selected + 1) }) { Icon(Icons.Default.ChevronRight, "Позже") }
            }
            SectionTitle(if (entries.isEmpty()) "Праздников нет" else "Праздники дня")
        }
        if (entries.isEmpty()) item {
            Text("В этот день нет праздников из выбранных категорий. Ниже — ближайшие.", color = extra.dim, modifier = Modifier.padding(8.dp))
        }
        items(entries, key = { "d" + it.key }) { e -> ExpandableEntry(e, selected) }
        if (upcoming.isNotEmpty()) item { SectionTitle("Ближайшие") }
        items(upcoming, key = { (d, e) -> "u" + d + e.key }) { (d, e) ->
            ExpandableEntry(e, d.toEpochDay(), dateText = "${Dates.weekdayShort(d.toEpochDay())}, ${Dates.short(d.toEpochDay())} · ${whenText(d)}")
        }
        item {
            Gap(10.dp)
            Button(
                onClick = { editing = CustomDay(name = "", month = Dates.day(selected).monthValue, day = Dates.day(selected).dayOfMonth) },
                Modifier.fillMaxWidth(),
            ) { Text("+ Свой праздник, день рождения или особый день") }
        }
        val mine = custom.sortedWith(compareBy({ it.month }, { it.day }))
        if (mine.isNotEmpty()) {
            item { SectionTitle("Мои дни") }
            items(mine, key = { "m" + it.id }) { c ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { editing = c }.padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ColorDot(c.color)
                    HGap(10.dp)
                    Text(c.name, Modifier.weight(1f))
                    Text("${c.day} ${MONTHS_GEN[c.month - 1]}" + if (!c.yearly && c.year != null) " ${c.year}" else "", fontSize = 13.sp, color = extra.dim)
                }
            }
        }
        item {
            SectionTitle("Какие праздники показывать")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HolidayCats.ORDER.filter { it != "my" }.forEach { c ->
                    Pill(HolidayCats.names[c] ?: c, c !in hidden) {
                        hidden = if (c in hidden) hidden - c else hidden + c
                        UiState.setStr(ctx, HIDDEN_KEY, hidden.joinToString(","))
                    }
                }
            }
            Text(
                "Праздники России, международные, армянские, православные и Армянской апостольской церкви. " +
                    "Подвижные даты (Пасха и связанные с ней праздники) рассчитываются на каждый год. Нажмите на праздник, чтобы узнать его историю.",
                fontSize = 11.sp, color = extra.dim, modifier = Modifier.padding(top = 12.dp),
            )
        }
        item { HowTo("holidays") }
    }
    editing?.let { c -> CustomDayDialog(c) { editing = null } }
}

internal val MONTHS_GEN = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")

/** Карточка праздника: история, как отмечают, даты и выделение цветом. */
@Composable
fun HolidayScreen(nav: NavHostController, id: String) {
    val ctx = LocalContext.current
    val h = remember(id) { HolidayRepo.byId(ctx, id) }
    val marks by observe(emptyList()) { Graph.days.marks() }
    val mark = marks.firstOrNull { it.holidayId == id }
    Screen(h?.name ?: "Праздник", onBack = { nav.popBackStack(); Unit }) { pad ->
        if (h == null) { Text("Праздник не найден", Modifier.padding(pad).padding(16.dp)); return@Screen }
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text(h.name, style = MaterialTheme.typography.headlineSmall)
            Gap(8.dp)
            HolidayDetails(h, mark)
            Gap(40.dp)
        }
    }
}

/** Свой праздник, день рождения или особый день: дата, год, повтор и цвет. */
@Composable
fun CustomDayDialog(initial: CustomDay, onDismiss: () -> Unit) {
    val extra = LocalExtra.current
    var name by remember { mutableStateOf(initial.name) }
    var kind by remember { mutableStateOf(initial.kind) }
    var month by remember { mutableStateOf(initial.month) }
    var day by remember { mutableStateOf(initial.day) }
    var year by remember { mutableStateOf(initial.year?.toString() ?: "") }
    var yearly by remember { mutableStateOf(initial.yearly) }
    var color by remember { mutableStateOf(initial.color) }
    var note by remember { mutableStateOf(initial.note) }
    var pick by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Свой день" else "Изменить день") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Segments(DayKindTag.names.toList(), kind, { kind = it })
                Gap(8.dp)
                TextInput(name, { name = it }, if (kind == DayKindTag.BIRTHDAY) "Чей день рождения" else "Название")
                Gap(8.dp)
                FieldButton("Дата", "$day ${MONTHS_GEN[month - 1]}", Modifier.fillMaxWidth()) { pick = true }
                Gap(8.dp)
                NumberField(year, { year = it }, if (kind == DayKindTag.BIRTHDAY) "Год рождения (для возраста)" else "Год (необязательно)", decimal = false)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Text("Повторять каждый год", Modifier.weight(1f))
                    Switch(yearly, { yearly = it })
                }
                Text("Цвет в календаре", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MARK_COLORS.forEach { c ->
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(Color(c))
                                .then(if (color == c) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .clickable { color = c },
                        )
                    }
                }
                Gap(8.dp)
                TextInput(note, { note = it }, "Заметка", singleLine = false)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) return@TextButton
                val y = year.toIntOrNull()?.takeIf { it in 1800..2200 }
                io {
                    Graph.days.upsertCustomDay(
                        initial.copy(
                            name = name.trim(), kind = kind, month = month, day = day, year = y ?: if (!yearly) LocalDate.now().year else null,
                            yearly = yearly, color = color, note = note, createdAt = if (initial.createdAt == 0L) System.currentTimeMillis() else initial.createdAt,
                        )
                    )
                }
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (initial.id != 0L) TextButton(onClick = { io { Graph.days.deleteCustomDay(initial) }; onDismiss() }) { Text("Удалить", color = extra.danger) }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
    if (pick) {
        val y = year.toIntOrNull()?.takeIf { it in 1800..2200 } ?: LocalDate.now().year
        val init = runCatching { LocalDate.of(y, month, day).toEpochDay() }.getOrDefault(Dates.today())
        DatePickDialog(init, { pick = false }, { it?.let { d -> Dates.day(d).let { ld -> month = ld.monthValue; day = ld.dayOfMonth; if (year.isNotBlank() || !yearly) year = ld.year.toString() } } }, allowClear = false)
    }
}
