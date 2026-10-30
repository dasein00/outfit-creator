package com.dasein.poryadok.ui.wardrobe

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.Outfit
import com.dasein.poryadok.data.WardrobeSet
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.plural
import com.dasein.poryadok.ui.common.Button
import com.dasein.poryadok.ui.common.ConfirmDialog
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.OutlinedButton
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.Text
import com.dasein.poryadok.ui.common.TextButton
import com.dasein.poryadok.ui.common.btnLabel
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.common.rememberImage
import com.dasein.poryadok.ui.theme.LocalExtra
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val RU = Locale("ru")
private val WEEK = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

/** Планировщик нарядов как в Fits: «День» — неделя и карточки дня, «Месяц» — сетка с нарядами по дням. */
@Composable
fun WardrobePlannerScreen(nav: NavHostController) {
    val outfits by observe(emptyList()) { Graph.dao.outfits() }
    val today = Dates.today()
    var tab by rememberSaveable { mutableStateOf(0) }
    var day by rememberSaveable { mutableStateOf(today) }
    var pickFor by remember { mutableStateOf<Long?>(null) }
    var open by remember { mutableStateOf<Outfit?>(null) }
    val byDay = remember(outfits) {
        val m = HashMap<Long, MutableList<Outfit>>()
        outfits.forEach { o -> o.days().forEach { d -> m.getOrPut(d) { mutableListOf() }.add(o) } }
        m
    }

    Screen(
        title = "Планировщик",
        onBack = { nav.popBackStack() },
        actions = { TextButton(onClick = { day = today }) { Text(btnLabel("Сегодня")) } },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                listOf("День", "Месяц").forEachIndexed { i, t -> Tab(tab == i, onClick = { tab = i }, text = { Text(btnLabel(t)) }) }
            }
            if (tab == 0) DayView(day, today, byDay, onDay = { day = it }, onAdd = { pickFor = it }, onOpen = { open = it }, onUnplan = { o, d -> unplan(o, d) })
            else MonthView(today, day, byDay) { d -> day = d; tab = 0 }
        }
    }
    pickFor?.let { d ->
        PickOutfitDialog(outfits, d, onDismiss = { pickFor = null }, onNew = { pickFor = null; openEditor(nav, planDay = d) }) { o ->
            pickFor = null
            io { Graph.dao.upsertOutfit(o.copy(wornDays = (o.days() + d).distinct().sorted().joinToString(","))) }
        }
    }
    open?.let { o -> OutfitDialog(o, nav) { open = null } }
}

private fun unplan(o: Outfit, d: Long) = io { Graph.dao.upsertOutfit(o.copy(wornDays = (o.days() - d).joinToString(","))) }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayView(
    day: Long, today: Long, byDay: Map<Long, List<Outfit>>,
    onDay: (Long) -> Unit, onAdd: (Long) -> Unit, onOpen: (Outfit) -> Unit, onUnplan: (Outfit, Long) -> Unit,
) {
    val extra = LocalExtra.current
    val date = LocalDate.ofEpochDay(day)
    val monday = date.with(DayOfWeek.MONDAY)
    var confirm by remember { mutableStateOf<Outfit?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onDay(day - 7) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Прошлая неделя") }
            (0..6).forEach { i ->
                val d = monday.plusDays(i.toLong()).toEpochDay()
                val sel = d == day
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onDay(d) }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(WEEK[i], fontSize = 11.sp, color = extra.dim)
                    Box(
                        Modifier.padding(top = 2.dp).size(32.dp).clip(CircleShape)
                            .background(if (sel) MaterialTheme.colorScheme.onSurface else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${LocalDate.ofEpochDay(d).dayOfMonth}", fontWeight = if (d == today || sel) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (sel) MaterialTheme.colorScheme.surface else if (d == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Box(Modifier.padding(top = 2.dp).size(5.dp).clip(CircleShape).background(if (byDay[d].isNullOrEmpty()) Color.Transparent else MaterialTheme.colorScheme.primary))
                }
            }
            IconButton(onClick = { onDay(day + 7) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Следующая неделя") }
        }
        Text(
            date.format(DateTimeFormatter.ofPattern("EEEE, d MMM", RU)), fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), textAlign = TextAlign.Center,
        )
        val list = byDay[day].orEmpty()
        val state = rememberLazyListState()
        LaunchedEffect(day) { state.scrollToItem(0) }
        LazyRow(
            Modifier.weight(1f).fillMaxWidth(), state = state,
            contentPadding = PaddingValues(start = 32.dp, end = 32.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(list, key = { "o" + it.id }) { o ->
                val img by rememberImage(o.preview.ifBlank { null }, 700)
                Column(Modifier.fillMaxHeight().width(260.dp)) {
                    Box(
                        Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (o.bg != 0) Color(o.bg) else BoardColor)
                            .combinedClickable(onClick = { onOpen(o) }, onLongClick = { confirm = o }),
                    ) {
                        img?.let { Image(it, o.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(o.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(top = 6.dp))
                        IconButton(onClick = { confirm = o }) { Icon(Icons.Default.Delete, "Убрать из дня", tint = extra.dim) }
                    }
                }
            }
            item(key = "add") {
                Box(
                    Modifier.fillMaxHeight().width(260.dp).padding(bottom = 48.dp).clip(RoundedCornerShape(20.dp)).background(extra.card)
                        .border(1.dp, extra.line, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Button(onClick = { onAdd(day) }) { Icon(Icons.Default.Add, null); Text(btnLabel(" Добавить наряд")) }
                }
            }
        }
    }
    confirm?.let { o ->
        ConfirmDialog("Убрать «${o.name}» из этого дня?", "Сам наряд останется в «Нарядах».", confirm = "Убрать", onDismiss = { confirm = null }) {
            onUnplan(o, day); confirm = null
        }
    }
}

@Composable
private fun MonthView(today: Long, day: Long, byDay: Map<Long, List<Outfit>>, onPick: (Long) -> Unit) {
    val extra = LocalExtra.current
    val now = YearMonth.from(LocalDate.ofEpochDay(today))
    val months = (-3..6).map { now.plusMonths(it.toLong()) }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = 3)
    LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 40.dp)) {
        items(months, key = { it.toString() }) { ym ->
            Column(Modifier.padding(top = 14.dp)) {
                Text(
                    ym.atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy", RU)).replaceFirstChar { it.uppercase() },
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                )
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) { WEEK.forEach { Text(it, fontSize = 11.sp, color = extra.dim, modifier = Modifier.weight(1f), textAlign = TextAlign.Center) } }
                val lead = ym.atDay(1).dayOfWeek.value - 1
                val cells = lead + ym.lengthOfMonth()
                (0 until (cells + 6) / 7).forEach { w ->
                    Row(Modifier.fillMaxWidth()) {
                        (0..6).forEach { c ->
                            val n = w * 7 + c - lead + 1
                            Box(Modifier.weight(1f).padding(2.dp)) {
                                if (n in 1..ym.lengthOfMonth()) {
                                    val d = ym.atDay(n).toEpochDay()
                                    MonthCell(n, d == today, d == day, byDay[d].orEmpty().firstOrNull()) { onPick(d) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthCell(n: Int, isToday: Boolean, selected: Boolean, o: Outfit?, onClick: () -> Unit) {
    val extra = LocalExtra.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(if (isToday) MaterialTheme.colorScheme.onSurface else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) { Text("$n", fontSize = 12.sp, color = if (isToday) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface) }
        Box(
            Modifier.padding(top = 2.dp).fillMaxWidth().aspectRatio(.72f).clip(RoundedCornerShape(8.dp))
                .background(if (o != null) (if (o.bg != 0) Color(o.bg) else BoardColor) else extra.card)
                .border(if (selected) 1.5.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (o != null) {
                val img by rememberImage(o.preview.ifBlank { null }, 200)
                img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            } else Icon(Icons.Default.Add, null, Modifier.size(16.dp), tint = extra.dim)
        }
    }
}

/** Выбрать наряд на день — или собрать новый. */
@Composable
private fun PickOutfitDialog(outfits: List<Outfit>, day: Long, onDismiss: () -> Unit, onNew: () -> Unit, onPick: (Outfit) -> Unit) {
    val extra = LocalExtra.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Наряд на ${Dates.full(day)}") },
        text = {
            Column {
                OutlinedButton(onClick = onNew, modifier = Modifier.fillMaxWidth()) { Text(btnLabel("+ Собрать новый наряд")) }
                Gap(10.dp)
                if (outfits.isEmpty()) Text("Сохранённых нарядов пока нет.", color = extra.dim, fontSize = 13.sp)
                LazyVerticalGrid(GridCells.Fixed(3), Modifier.heightIn(max = 420.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(outfits, key = { it.id }) { o ->
                        val img by rememberImage(o.preview.ifBlank { null }, 300)
                        Column(Modifier.clickable { onPick(o) }) {
                            Box(Modifier.fillMaxWidth().aspectRatio(.75f).clip(RoundedCornerShape(10.dp)).background(if (o.bg != 0) Color(o.bg) else BoardColor)) {
                                img?.let { Image(it, o.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                            }
                            Text(o.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) } },
    )
}

/* ---------------- Подборка ---------------- */

/** Подборка: вещи сеткой, «Добавить вещи», наряд из подборки (например, для поездки), переименовать и удалить. */
@Composable
fun WardrobeSetScreen(nav: NavHostController, id: Long) {
    val extra = LocalExtra.current
    val sets by observe(emptyList()) { Graph.dao.wardrobeSets() }
    val all by observe(emptyList()) { Graph.dao.wardrobe() }
    val set = sets.firstOrNull { it.id == id }
    var add by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val ids = set?.itemIds?.split(",")?.filter { it.isNotBlank() }.orEmpty()
    val inSet = ids.mapNotNull { i -> all.firstOrNull { it.id == i } }
    fun save(s: WardrobeSet) = io { Graph.dao.upsertWardrobeSet(s) }

    Screen(
        title = set?.name ?: "",
        onBack = { nav.popBackStack() },
        actions = {
            IconButton(onClick = { rename = true }) { Icon(Icons.Default.Edit, "Переименовать") }
            IconButton(onClick = { confirm = true }) { Icon(Icons.Default.Delete, "Удалить") }
        },
    ) { pad ->
        if (set == null) return@Screen
        LazyVerticalGrid(
            GridCells.Fixed(3), Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                Column {
                    Text("${setKindLabel(set.kind)} · ${inSet.size} ${plural(inSet.size, "вещь", "вещи", "вещей")}", color = extra.dim, fontSize = 13.sp)
                    Gap(8.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { add = true }, modifier = Modifier.weight(1f)) { Text(btnLabel("+ Вещи")) }
                        Button(onClick = { openEditor(nav, items = ids) }, enabled = ids.isNotEmpty(), modifier = Modifier.weight(1f)) { Text(btnLabel("Создать наряд")) }
                    }
                }
            }
            items(inSet, key = { it.id }) { it0 ->
                Box(Modifier.aspectRatio(.82f).clip(RoundedCornerShape(14.dp)).background(BoardColor).clickable { nav.navigate(wardrobeItemRoute(it0.id)) }) {
                    ItemThumb(it0, Modifier.fillMaxSize().padding(10.dp))
                }
            }
            if (inSet.isEmpty()) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                Text("Подборка пустая. Нажмите «+ Вещи», чтобы добавить.", color = extra.dim, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
            }
        }
    }
    if (set == null) return
    if (add) AddItemsSheet(all, ids.toSet(), onDismiss = { add = false }) { picked -> add = false; save(set.copy(itemIds = picked.joinToString(","))) }
    if (rename) EditTextDialog("Название подборки", set.name, onDismiss = { rename = false }) { if (it.isNotBlank()) save(set.copy(name = it)) }
    if (confirm) ConfirmDialog("Удалить подборку «${set.name}»?", "Вещи останутся в гардеробе.", onDismiss = { confirm = false }) {
        confirm = false
        io { Graph.dao.deleteWardrobeSet(set) }
        nav.popBackStack()
    }
}
