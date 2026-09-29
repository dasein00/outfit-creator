@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.calendar

import android.content.Context
import android.content.Intent
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
import com.dasein.poryadok.logic.HolidayCats
import com.dasein.poryadok.logic.HolidayRules
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.DatePickDialog
import com.dasein.poryadok.ui.common.FieldButton
import com.dasein.poryadok.ui.common.Gap
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
internal fun EntryRow(e: DayEntry, onClick: () -> Unit, dateText: String? = null) {
    val extra = LocalExtra.current
    Tile(Modifier.padding(bottom = 6.dp), onClick = onClick, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(36.dp).clip(RoundedCornerShape(2.dp)).background(Color(e.color)))
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(e.name, fontWeight = FontWeight.Medium)
                Text(listOfNotNull(dateText, e.subtitle.takeIf { it.isNotBlank() }).joinToString(" · "), fontSize = 12.sp, color = extra.dim)
            }
            if (e.off) Text(
                if (e.cat == "am" || e.cat == "arm") "выходной в Армении" else "выходной", fontSize = 11.sp, color = Color(0xFFD25B4B),
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x22D25B4B)).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/** Вкладка «Праздники»: праздники выбранного дня, ближайшие, фильтр по видам и свои дни. */
@Composable
fun HolidaysTab(nav: NavHostController, selected: Long, onSelect: (Long) -> Unit) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    var version by remember { mutableStateOf(0) }
    var hidden by remember { mutableStateOf(hiddenCats(ctx)) }
    var editing by remember { mutableStateOf<CustomDay?>(null) }
    val custom by observe(emptyList()) { Graph.days.customDays() }
    val marks by observe(emptyList()) { Graph.days.marks() }
    val entries = remember(selected, custom, marks, hidden) { HolidayRepo.entries(ctx, Dates.day(selected), custom, marks, hidden) }
    val upcoming = remember(selected, custom, marks, hidden) {
        (1..30).flatMap { k ->
            val d = Dates.day(selected + k)
            HolidayRepo.entries(ctx, d, custom, marks, hidden).filter { it.cat == "my" || it.off || it.cat in setOf("ru", "am", "orth", "arm") }.map { d to it }
        }.take(15)
    }
    fun open(e: DayEntry) {
        if (e.custom != null) editing = e.custom else nav.navigate(Routes.holiday(e.key))
    }

    LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onSelect(selected - 1) }) { Icon(Icons.Default.ChevronLeft, "Раньше") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${Dates.weekdayFull(selected)}, ${Dates.full(selected)}", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    if (selected != Dates.today()) Text("к сегодняшнему дню", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { onSelect(Dates.today()) })
                }
                IconButton(onClick = { onSelect(selected + 1) }) { Icon(Icons.Default.ChevronRight, "Позже") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HolidayCats.ORDER.filter { it != "my" }.forEach { c ->
                    Pill(HolidayCats.names[c] ?: c, c !in hidden) {
                        hidden = if (c in hidden) hidden - c else hidden + c
                        UiState.setStr(ctx, HIDDEN_KEY, hidden.joinToString(","))
                        version++
                    }
                }
            }
            Gap(10.dp)
            Button(onClick = { editing = CustomDay(name = "", month = Dates.day(selected).monthValue, day = Dates.day(selected).dayOfMonth) }, Modifier.fillMaxWidth()) {
                Text("+ Свой праздник, день рождения или особый день")
            }
            SectionTitle(if (entries.isEmpty()) "Праздников нет" else "Праздники дня")
        }
        if (entries.isEmpty()) item { Text("В этот день нет праздников из выбранных категорий.", color = extra.dim, modifier = Modifier.padding(8.dp)) }
        items(entries, key = { "d" + it.key }) { e -> EntryRow(e, { open(e) }) }
        if (upcoming.isNotEmpty()) item { SectionTitle("Ближайшие") }
        items(upcoming, key = { (d, e) -> "u" + d + e.key }) { (d, e) ->
            EntryRow(e, { open(e) }, dateText = "${Dates.weekdayShort(d.toEpochDay())}, ${Dates.short(d.toEpochDay())}")
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
            Text(
                "Праздники России, международные, армянские, православные и Армянской апостольской церкви. Подвижные даты (Пасха и связанные с ней праздники) рассчитываются на каждый год.",
                fontSize = 11.sp, color = extra.dim, modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
    editing?.let { c -> CustomDayDialog(c) { editing = null } }
}

internal val MONTHS_GEN = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")

/** Карточка праздника: история, как отмечают, даты и выделение цветом. */
@Composable
fun HolidayScreen(nav: NavHostController, id: String) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val h = remember(id) { HolidayRepo.byId(ctx, id) }
    val marks by observe(emptyList()) { Graph.days.marks() }
    val mark = marks.firstOrNull { it.holidayId == id }
    Screen(h?.name ?: "Праздник", onBack = { nav.popBackStack(); Unit }) { pad ->
        if (h == null) { Text("Праздник не найден", Modifier.padding(pad).padding(16.dp)); return@Screen }
        val color = mark?.color ?: (HolidayCats.colors[h.cat] ?: 0xFF888888L).toInt()
        val year = LocalDate.now().year
        val dates = (year..year + 2).mapNotNull { y -> HolidayRules.dateIn(h.rule, y) }.filter { it >= LocalDate.now().minusDays(1) }.take(2)
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorDot(color, 14)
                HGap(8.dp)
                Text(HolidayCats.names[h.cat] ?: "", color = extra.dim)
                if (h.off) {
                    HGap(8.dp)
                    Text(if (h.cat == "am" || h.cat == "arm") "нерабочий день в Армении" else "нерабочий день в России", fontSize = 12.sp, color = Color(0xFFD25B4B))
                }
            }
            Gap(8.dp)
            Text(h.name, style = MaterialTheme.typography.headlineSmall)
            Gap(4.dp)
            Text(HolidayRules.describe(h.rule), color = extra.dim)
            dates.forEach { d ->
                Text("${Dates.weekdayFull(d.toEpochDay())}, ${Dates.full(d.toEpochDay())}" + if (d == LocalDate.now()) " — сегодня" else "", fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
            }
            SectionTitle("История и традиции")
            Text(h.about, lineHeight = 22.sp)
            SectionTitle("Выделить цветом в календаре")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MARK_COLORS.forEach { c ->
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(Color(c))
                            .then(if (mark?.color == c) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .clickable { io { Graph.days.upsertMark(HolidayMark(id, c)) } },
                    )
                }
                if (mark != null) TextButton(onClick = { io { Graph.days.deleteMark(id) } }) { Text("Без выделения") }
            }
            Gap(16.dp)
            OutlinedButton(onClick = {
                val text = "${h.name}\n${HolidayRules.describe(h.rule)}\n\n${h.about}"
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "Поделиться").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text("Поделиться") }
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
