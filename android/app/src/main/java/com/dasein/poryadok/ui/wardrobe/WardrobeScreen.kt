package com.dasein.poryadok.ui.wardrobe

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.Outfit
import com.dasein.poryadok.data.WardrobeItem
import com.dasein.poryadok.data.WardrobeSet
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.plural
import com.dasein.poryadok.ui.common.ChipRow
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.HowTo
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.Text
import com.dasein.poryadok.ui.common.TextButton
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.btnLabel
import com.dasein.poryadok.ui.common.cardViewport
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.common.rememberImage
import com.dasein.poryadok.ui.common.safeBottom
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SORTS = listOf("Свой порядок", "Сначала новые", "А–Я", "Чаще носят", "Реже носят")

/** Гардероб в духе Fits: «Одежда», «Наряды», «Подборки»; сверху — планировщик и статистика. */
@Composable
fun WardrobeScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val dao = Graph.dao
    val scope = rememberCoroutineScope()
    val allItems by observe(emptyList()) { dao.wardrobe() }
    val outfits by observe(emptyList()) { dao.outfits() }
    val sets by observe(emptyList()) { dao.wardrobeSets() }
    val today = Dates.today()
    val wears = remember(outfits, today) { wearsOf(outfits, today) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var stats by remember { mutableStateOf(false) }
    var addPending by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var newSet by remember { mutableStateOf(false) }
    var openOutfit by remember { mutableStateOf<Outfit?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch { addPending = prepareUserItem(ctx, uri); busy = false }
        }
    }

    Screen(
        title = "Гардероб",
        onBack = { nav.popBackStack() },
        actions = {
            IconButton(onClick = { nav.navigate(WARDROBE_PLANNER) }) { Icon(Icons.Default.CalendarMonth, "Планировщик") }
            IconButton(onClick = { stats = true }) { Icon(Icons.Default.PieChart, "Статистика") }
        },
        fab = {
            FloatingActionButton(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, onClick = {
                when (tab) {
                    0 -> picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    1 -> openEditor(nav)
                    else -> newSet = true
                }
            }) { Icon(Icons.Default.Add, "Добавить") }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                listOf("Одежда", "Наряды", "Подборки").forEachIndexed { i, t ->
                    Tab(tab == i, onClick = { tab = i }, text = { Text(btnLabel(t), fontWeight = if (tab == i) FontWeight.SemiBold else FontWeight.Normal) })
                }
            }
            when (tab) {
                0 -> ClothesTab(nav, allItems, wears, busy) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                1 -> OutfitsTab(nav, outfits, today) { openOutfit = it }
                else -> SetsTab(nav, sets, allItems) { newSet = true }
            }
        }
    }

    addPending?.let { path -> NewItemDialog(path, onDismiss = { java.io.File(path).delete(); addPending = null }) { item ->
        addPending = null
        io {
            val colors = withContext(Dispatchers.Default) { Bitmaps.load(ctx, item.src)?.let { dominantColors(it) } ?: "" }
            dao.upsertWardrobeItem(item.copy(colors = colors))
        }
    } }
    openOutfit?.let { o -> OutfitDialog(o, nav) { openOutfit = null } }
    if (newSet) NewSetDialog(onDismiss = { newSet = false }) { s ->
        newSet = false
        io { val id = dao.upsertWardrobeSet(s); withContext(Dispatchers.Main) { nav.navigate(wardrobeSetRoute(id)) } }
    }
    if (stats) StatsSheet(allItems, outfits, wears) { stats = false }
}

/* ---------------- Одежда ---------------- */

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ClothesTab(nav: NavHostController, all: List<WardrobeItem>, wears: Map<String, List<Long>>, busy: Boolean, onAdd: () -> Unit) {
    val extra = LocalExtra.current
    val ctx = LocalContext.current
    // Фильтр по типам вещей (рубашки, куртки, джинсы…), несколько сразу; пусто — все.
    var typesCsv by rememberSaveable { mutableStateOf("") }
    val types = typesCsv.split(",").filter { it.isNotBlank() }.toSet()
    fun setTypes(t: Set<String>) { typesCsv = t.joinToString(",") }
    var order by remember { mutableStateOf(WardrobeOrder.get(ctx)) }
    var reorder by remember { mutableStateOf(false) }
    var bgDialog by remember { mutableStateOf(false) }
    var typeQuery by remember { mutableStateOf("") }
    var season by rememberSaveable { mutableStateOf("все") }
    var color by rememberSaveable { mutableStateOf("") }
    var favOnly by rememberSaveable { mutableStateOf(false) }
    var archive by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(0) }
    var filters by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val pool = all.filter { it.archived == archive }
    val shown = pool.filter {
        (types.isEmpty() || it.kind() in types) && (season == "все" || it.season == season || it.season == "любой") &&
            (!favOnly || it.favorite) && (color.isBlank() || color in it.colors)
    }.let { l ->
        when (sort) {
            0 -> WardrobeOrder.sort(l, order)
            2 -> l.sortedBy { it.name.lowercase() }
            3 -> l.sortedByDescending { wears[it.id]?.size ?: 0 }
            4 -> l.sortedBy { wears[it.id]?.size ?: 0 }
            else -> l.sortedByDescending { it.addedAt }
        }
    }
    // Есть ли каждый тип в шкафу — фильтр показывает только существующие, основные сначала.
    val kindCounts = remember(pool) { pool.groupingBy { it.kind() }.eachCount() }
    val presentKinds = KIND_ORDER.filter { (kindCounts[it] ?: 0) > 0 }
    if (reorder) {
        ReorderGrid(WardrobeOrder.sort(pool, order), onCancel = { reorder = false }) { ids ->
            WardrobeOrder.set(ctx, ids); order = ids; sort = 0; reorder = false
        }
        return
    }
    val active = listOf(season != "все", color.isNotBlank(), favOnly, archive, types.size > 1).count { it }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Pill("Все", types.isEmpty()) { setTypes(emptySet()) } }
                items(presentKinds) { k -> Pill("${KIND_PLURAL[k] ?: k} · ${kindCounts[k]}", k in types) { setTypes(if (types == setOf(k)) emptySet() else setOf(k)) } }
            }
            IconButton(onClick = { filters = true }) {
                Box {
                    Icon(Icons.Default.FilterList, "Фильтры")
                    if (active > 0) Box(Modifier.align(Alignment.TopEnd).size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                }
            }
            Box {
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.Default.Sort, "Сортировка") }
                DropdownMenu(sortMenu, { sortMenu = false }) {
                    SORTS.forEachIndexed { i, s -> DropdownMenuItem({ Text(btnLabel(s), fontWeight = if (i == sort) FontWeight.SemiBold else FontWeight.Normal) }, { sort = i; sortMenu = false }) }
                    DropdownMenuItem({ Text(btnLabel("Переместить вещи…")) }, { sortMenu = false; reorder = true })
                    DropdownMenuItem({ Text(btnLabel("Фон плашек…")) }, { sortMenu = false; bgDialog = true })
                }
            }
        }
        if (archive) Text("Архив: вещи, которые вы убрали", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(horizontal = 16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            // Как в других разделах: список в скруглённом окне — плашки у краёв скругляются при прокрутке.
            modifier = Modifier.weight(1f).cardViewport(),
        ) {
            item {
                Box(
                    Modifier.aspectRatio(.82f).clip(tileShape()).border(1.dp, extra.line, tileShape()).clickable(onClick = onAdd),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Add, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(btnLabel(if (busy) "Убираю фон…" else "Вещь"), fontSize = 11.sp, color = extra.dim)
                    }
                }
            }
            // «Все» в своём порядке или по новизне — группами по типам, как в Fits: «Рубашки», «Футболки»…
            val groups = if (types.isEmpty() && sort <= 1) shown.groupBy { it.kind() }.toList().sortedBy { KIND_ORDER.indexOf(it.first).let { i -> if (i < 0) 99 else i } }
            else listOf("" to shown)
            groups.forEach { (k, list) ->
            if (k.isNotEmpty()) item(span = { GridItemSpan(3) }, key = "h_$k") {
                Text("${KIND_PLURAL[k] ?: k} · ${list.size}", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
            }
            items(list, key = { it.id }) { it0 ->
                Box(Modifier.aspectRatio(.82f).clip(tileShape()).background(tileBg()).clickable { nav.navigate(wardrobeItemRoute(it0.id)) }) {
                    ItemThumb(it0, Modifier.fillMaxSize().padding(10.dp))
                    if (it0.favorite) Icon(Icons.Default.Favorite, "избранное", Modifier.align(Alignment.TopEnd).padding(6.dp).size(14.dp), tint = Color(0xFFD9575B))
                }
            }
            }
            if (shown.isEmpty()) item(span = { GridItemSpan(3) }) {
                Text(
                    if (pool.isEmpty()) "У вас ещё нет вещей. Добавьте первую — фото на ровном фоне." else "Под фильтры ничего не подходит.",
                    color = extra.dim, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp),
                )
            }
            item(span = { GridItemSpan(3) }) { HowTo("wardrobe") }
        }
    }
    if (bgDialog) TileBgDialog { bgDialog = false }
    if (filters) {
        val state = rememberModalBottomSheetState()
        val palette = remember(all) { all.flatMap { it.colors.split(",") }.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(24) }
        ModalBottomSheet(onDismissRequest = { filters = false }, sheetState = state) {
            Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState()).safeBottom()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(btnLabel("Фильтры"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { season = "все"; color = ""; favOnly = false; archive = false; setTypes(emptySet()) }) { Text(btnLabel("Сбросить")) }
                }
                // Категории: поиск и выбор нескольких типов, по группам.
                Text(btnLabel("Категории"), fontSize = 12.sp, color = extra.dim)
                Gap(4.dp)
                TextInput(typeQuery, { typeQuery = it }, "Поиск категории")
                CATEGORIES.forEach { c ->
                    val ks = kindsOf(c).filter { k -> (kindCounts[k] ?: 0) > 0 && (typeQuery.isBlank() || (KIND_PLURAL[k] ?: k).contains(typeQuery.trim(), true)) }
                        .sortedBy { KIND_ORDER.indexOf(it) }
                    if (ks.isNotEmpty()) {
                        Text(CATEGORY_LABELS[c] ?: c, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ks.forEach { k -> Pill("${KIND_PLURAL[k] ?: k} · ${kindCounts[k]}", k in types) { setTypes(if (k in types) types - k else types + k) } }
                        }
                    }
                }
                Gap(12.dp)
                Text(btnLabel("Сезон"), fontSize = 12.sp, color = extra.dim)
                Gap(4.dp)
                ChipRow(SEASONS, season) { season = it }
                if (palette.isNotEmpty()) {
                    Gap(12.dp)
                    Text(btnLabel("Цвет"), fontSize = 12.sp, color = extra.dim)
                    Gap(6.dp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(palette) { hex ->
                            val c = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)
                            Box(
                                Modifier.size(32.dp).clip(CircleShape).background(c)
                                    .border(if (color == hex) 3.dp else 1.dp, if (color == hex) MaterialTheme.colorScheme.primary else extra.line, CircleShape)
                                    .clickable { color = if (color == hex) "" else hex },
                            )
                        }
                    }
                }
                Gap(12.dp)
                SwitchRow("Только избранное", favOnly) { favOnly = it }
                SwitchRow("Показать архив", archive) { archive = it }
                Gap(8.dp)
            }
        }
    }
}

@Composable
fun SwitchRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(btnLabel(label), modifier = Modifier.weight(1f))
        Switch(on, onChange)
    }
}

/* ---------------- Наряды ---------------- */

@Composable
private fun OutfitsTab(nav: NavHostController, outfits: List<Outfit>, today: Long, onOpen: (Outfit) -> Unit) {
    val extra = LocalExtra.current
    var season by rememberSaveable { mutableStateOf("все") }
    val shown = outfits.filter { season == "все" || it.season == season }
    if (outfits.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("Ваши наряды отображаются здесь", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, textAlign = TextAlign.Center)
            Gap(4.dp)
            Text("Собирайте коллажи из своих вещей и планируйте, что надеть.", color = extra.dim, fontSize = 13.sp, textAlign = TextAlign.Center)
            Gap(12.dp)
            com.dasein.poryadok.ui.common.Button(onClick = { openEditor(nav) }) { Text(btnLabel("Создать наряд")) }
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.cardViewport(),
    ) {
        item(span = { GridItemSpan(2) }) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SEASONS) { (id, label) -> Pill(label, season == id, glyph = SEASON_GLYPHS[id]) { season = id } }
            }
        }
        items(shown, key = { it.id }) { o ->
            val img by rememberImage(o.preview.ifBlank { null }, 500)
            val days = o.days()
            val worn = days.filter { it <= today }
            val next = days.firstOrNull { it > today }
            Column(Modifier.clip(RoundedCornerShape(16.dp)).background(extra.card).clickable { onOpen(o) }) {
                Box(Modifier.fillMaxWidth().aspectRatio(.75f).background(if (o.bg != 0) Color(o.bg) else BoardColor)) {
                    img?.let { Image(it, o.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                    if (today in days) Text(
                        "сегодня", fontSize = 10.sp, color = Color(0xFF241C0D),
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(6.dp)).background(com.dasein.poryadok.ui.theme.Palette.Brass).padding(horizontal = 5.dp),
                    )
                }
                Column(Modifier.padding(10.dp)) {
                    Text(o.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        when {
                            next != null -> "в планах: ${Dates.label(next)}"
                            worn.isEmpty() -> "ещё не надевали"
                            else -> "${worn.size} ${plural(worn.size, "раз", "раза", "раз")}"
                        },
                        fontSize = 12.sp, color = extra.dim,
                    )
                }
            }
        }
    }
}

/* ---------------- Подборки ---------------- */

@Composable
private fun SetsTab(nav: NavHostController, sets: List<WardrobeSet>, all: List<WardrobeItem>, onNew: () -> Unit) {
    val extra = LocalExtra.current
    var kind by rememberSaveable { mutableStateOf("") }
    val byId = remember(all) { all.associateBy { it.id } }
    val shown = sets.filter { kind.isBlank() || it.kind == kind }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.cardViewport(),
    ) {
        item(span = { GridItemSpan(2) }) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Pill("Все", kind.isBlank()) { kind = "" } }
                items(SET_KINDS) { (k, label) -> Pill(label, kind == k) { kind = k } }
            }
        }
        item {
            Column(Modifier.clickable(onClick = onNew)) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).border(1.dp, extra.line, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Text(btnLabel("Новая подборка"), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                Text("желания, поездка, своё", fontSize = 12.sp, color = extra.dim)
            }
        }
        items(shown, key = { it.id }) { s ->
            val ids = s.itemIds.split(",").filter { it.isNotBlank() }
            val four = ids.mapNotNull { byId[it] }.take(4)
            Column(Modifier.clickable { nav.navigate(wardrobeSetRoute(s.id)) }) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(tileShape()).background(tileBg())) {
                    if (four.isEmpty()) Icon(Icons.Default.Add, null, Modifier.align(Alignment.Center).size(28.dp), tint = Color(0xFF9A9286))
                    else Column(Modifier.fillMaxSize().padding(6.dp)) {
                        four.chunked(2).forEach { row ->
                            Row(Modifier.weight(1f)) { row.forEach { ItemThumb(it, Modifier.weight(1f).fillMaxSize().padding(4.dp), shadow = false) }; if (row.size == 1) Box(Modifier.weight(1f)) }
                        }
                        if (four.size <= 2) Box(Modifier.weight(1f))
                    }
                }
                Text(s.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                Text("${setKindLabel(s.kind)} · ${ids.size} ${plural(ids.size, "вещь", "вещи", "вещей")}", fontSize = 12.sp, color = extra.dim)
            }
        }
    }
}

@Composable
fun NewSetDialog(onDismiss: () -> Unit, onCreate: (WardrobeSet) -> Unit) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("own") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(btnLabel("Новая подборка")) },
        text = {
            Column {
                TextInput(name, { name = it }, "Название, например: отпуск в Сочи")
                Gap(10.dp)
                ChipRow(SET_KINDS, kind) { kind = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onCreate(WardrobeSet(name = name.trim().ifBlank { setKindLabel(kind) }, kind = kind, createdAt = System.currentTimeMillis()))
            }) { Text(btnLabel("Создать")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) } },
    )
}

/** Новая вещь из фото: превью без фона, название и категория. */
@Composable
private fun NewItemDialog(path: String, onDismiss: () -> Unit, onAdd: (WardrobeItem) -> Unit) {
    val extra = LocalExtra.current
    var name by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("верх") }
    var season by remember { mutableStateOf("любой") }
    // Тип задаёт реальный размер вещи (пальто длиннее рубашки); пока не выбран вручную — угадывается по названию.
    var kind by remember { mutableStateOf<String?>(null) }
    val kindNow = kind?.takeIf { it in kindsOf(cat) } ?: guessKind(name, cat)
    val preview = remember(path) { WardrobeItem(id = "new", name = "", category = cat, season = "", src = path, pct = "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(btnLabel("Новая вещь")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().height(200.dp).clip(tileShape()).background(tileBg())) {
                    ItemThumb(preview, Modifier.fillMaxSize().padding(12.dp))
                }
                Gap(8.dp)
                TextInput(name, { name = it }, "Название")
                Gap(10.dp)
                Text(btnLabel("Категория"), fontSize = 12.sp, color = extra.dim)
                Gap(4.dp)
                ChipRow(CATEGORIES.map { it to (CATEGORY_LABELS[it] ?: it) }, cat) { cat = it; kind = null }
                if (kindsOf(cat).size > 1) {
                    Gap(10.dp)
                    Text(btnLabel("Тип — от него зависит размер"), fontSize = 12.sp, color = extra.dim)
                    Gap(4.dp)
                    ChipRow(kindsOf(cat).map { it to (KIND_LABELS[it] ?: it) }, kindNow) { kind = it }
                }
                Gap(10.dp)
                Text(btnLabel("Сезон"), fontSize = 12.sp, color = extra.dim)
                Gap(4.dp)
                ChipRow(listOf("любой" to "Любой", "зима" to "Зима", "осень" to "Осень", "весна" to "Весна", "лето" to "Лето"), season) { season = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onAdd(
                    WardrobeItem(
                        id = "user_" + System.currentTimeMillis().toString(36), name = name.trim().ifBlank { "Своя вещь" }, category = cat, season = season,
                        src = path, pct = kindNow, addedAt = System.currentTimeMillis(),
                    )
                )
            }) { Text(btnLabel("Добавить")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) } },
    )
}

/* ---------------- Статистика ---------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsSheet(all: List<WardrobeItem>, outfits: List<Outfit>, wears: Map<String, List<Long>>, onDismiss: () -> Unit) {
    val extra = LocalExtra.current
    val state = rememberModalBottomSheetState()
    val active = all.filter { !it.archived }
    val spent = active.sumOf { it.price }
    val never = active.filter { wears[it.id].isNullOrEmpty() }
    val top = active.sortedByDescending { wears[it.id]?.size ?: 0 }.filter { (wears[it.id]?.size ?: 0) > 0 }.take(5)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState()).safeBottom()) {
            Text(btnLabel("Статистика гардероба"), style = MaterialTheme.typography.titleLarge)
            Gap(12.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatCell("${active.size}", plural(active.size, "вещь", "вещи", "вещей"))
                StatCell("${outfits.size}", plural(outfits.size, "наряд", "наряда", "нарядов"))
                StatCell("${never.size}", "не носили")
            }
            if (spent > 0) {
                Gap(10.dp)
                Text("Стоимость гардероба: ${com.dasein.poryadok.logic.Money.format(spent)}", fontSize = 13.sp)
            }
            Gap(14.dp)
            Text(btnLabel("По категориям"), fontSize = 12.sp, color = extra.dim)
            CATEGORIES.forEach { c ->
                val n = active.count { it.category == c }
                if (n > 0) Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(CATEGORY_LABELS[c] ?: c, modifier = Modifier.weight(.35f), fontSize = 13.sp)
                    Box(Modifier.weight(.55f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(extra.line)) {
                        Box(Modifier.fillMaxWidth(n / active.size.coerceAtLeast(1).toFloat()).height(8.dp).background(MaterialTheme.colorScheme.primary))
                    }
                    Text("$n", modifier = Modifier.weight(.1f), textAlign = TextAlign.End, fontSize = 13.sp)
                }
            }
            if (top.isNotEmpty()) {
                Gap(14.dp)
                Text(btnLabel("Чаще всего носите"), fontSize = 12.sp, color = extra.dim)
                Gap(6.dp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(top, key = { it.id }) { it0 ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(72.dp).clip(tileShape()).background(tileBg())) { ItemThumb(it0, Modifier.fillMaxSize().padding(6.dp), shadow = false) }
                            Text("${wears[it0.id]?.size ?: 0} раз", fontSize = 11.sp, color = extra.dim)
                        }
                    }
                }
            }
            if (never.isNotEmpty()) {
                Gap(14.dp)
                Text(btnLabel("Ни разу не надевали — может, пора?"), fontSize = 12.sp, color = extra.dim)
                Gap(6.dp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(never.take(12), key = { "n" + it.id }) { it0 ->
                        Box(Modifier.size(64.dp).clip(tileShape()).background(tileBg())) { ItemThumb(it0, Modifier.fillMaxSize().padding(6.dp), shadow = false) }
                    }
                }
            }
            Gap(12.dp)
        }
    }
}

@Composable
private fun StatCell(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(label, fontSize = 11.sp, color = LocalExtra.current.dim)
    }
}
