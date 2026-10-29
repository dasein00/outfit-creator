package com.dasein.poryadok.ui.wardrobe

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesomeMosaic
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.Outfit
import com.dasein.poryadok.data.WardrobeItem
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.plural
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.Button
import com.dasein.poryadok.ui.common.ChipRow
import com.dasein.poryadok.ui.common.ConfirmDialog
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.HowTo
import com.dasein.poryadok.ui.common.OutlinedButton
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.Text
import com.dasein.poryadok.ui.common.TextButton
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.btnLabel
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Цвет доски для Compose (тот же, что и в превью образа). */
val BoardColor = Color(BOARD_RGB)
private val BoardInk = Color(0xFF2A241C)
private val BoardDim = Color(0xFF8A8174)

class BoardVm : ViewModel() {
    val ed = Board()
}

/** Образ, который нужно открыть в редакторе (из галереи луков). */
object WardrobeBus {
    var pendingOutfit: Long? = null
}

/** Доска флэт-лэя: слои по порядку (последний — сверху), выделение и история для «Отменить/Вернуть». */
class Board {
    var layers by mutableStateOf<List<LayerData>>(emptyList())
    var sel by mutableStateOf<String?>(null)
    var dirty = false
    private val hist = mutableListOf<List<LayerData>>(emptyList())
    private var pos = 0
    var canUndo by mutableStateOf(false)
    var canRedo by mutableStateOf(false)

    fun commit() {
        while (hist.size > pos + 1) hist.removeAt(hist.size - 1)
        hist.add(layers)
        if (hist.size > 60) hist.removeAt(0)
        pos = hist.size - 1
        canUndo = pos > 0; canRedo = false
    }

    private fun restore() { layers = hist[pos]; canUndo = pos > 0; canRedo = pos < hist.size - 1; if (layers.none { it.id == sel }) sel = null }
    fun undo() { if (pos > 0) { pos--; restore() } }
    fun redo() { if (pos < hist.size - 1) { pos++; restore() } }

    fun selected(): LayerData? = layers.firstOrNull { it.id == sel }
    fun replace(l: LayerData) { layers = layers.map { if (it.id == l.id) l else it } }
    fun has(id: String) = layers.any { it.id == id }
}

@Composable
fun WardrobeScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val dao = Graph.dao
    val scope = rememberCoroutineScope()
    val allItems by observe(emptyList()) { dao.wardrobe() }
    val outfits by observe(emptyList()) { dao.outfits() }
    val items = remember(allItems) { allItems.associateBy { it.id } }
    val ed = viewModel { BoardVm() }.ed
    var tab by rememberSaveable { mutableStateOf(0) }
    var category by rememberSaveable { mutableStateOf("все") }
    var season by rememberSaveable { mutableStateOf("все") }
    var saveDialog by remember { mutableStateOf(false) }
    var addPending by remember { mutableStateOf<String?>(null) }
    var openItem by remember { mutableStateOf<WardrobeItem?>(null) }
    var busy by remember { mutableStateOf(false) }

    // Сколько раз надевали каждую вещь — по отметкам «Ношу сегодня» в луках.
    val wears = remember(outfits) {
        val m = HashMap<String, MutableList<Long>>()
        outfits.forEach { o ->
            val days = o.wornDays.split(",").mapNotNull { it.toLongOrNull() }
            decodeLayers(o.layers).first.forEach { l -> m.getOrPut(l.id) { mutableListOf() }.addAll(days) }
        }
        m.mapValues { it.value.sorted() }
    }

    LaunchedEffect(items, WardrobeBus.pendingOutfit) {
        val id = WardrobeBus.pendingOutfit ?: return@LaunchedEffect
        if (items.isEmpty()) return@LaunchedEffect
        WardrobeBus.pendingOutfit = null
        val o = outfits.firstOrNull { it.id == id } ?: dao.outfits().first().firstOrNull { it.id == id } ?: return@LaunchedEffect
        val (ls, v2) = decodeLayers(o.layers)
        val known = ls.filter { it.id in items }
        ed.layers = if (v2) known else autoLayout(ctx, known.map { it.id }, items)
        ed.sel = null
        ed.commit()
        tab = 1
    }

    /** Положить вещь на доску или убрать её. В «одиночных» категориях новая вещь заменяет прежнюю на её месте. */
    fun toggle(item: WardrobeItem) {
        if (ed.has(item.id)) {
            ed.layers = ed.layers.filter { it.id != item.id }
            if (ed.sel == item.id) ed.sel = null
        } else {
            val bmp = Bitmaps.load(ctx, item.src)
            val w = bmp?.width ?: 10
            val h = bmp?.height ?: 10
            val prev = if (item.category in SINGLE) ed.layers.firstOrNull { items[it.id]?.category == item.category } else null
            val acc = ed.layers.count { items[it.id]?.category == "аксессуары" }
            val q = slotQuad(item.category, acc, w, h)
            val rest = ed.layers.filter { it.id != prev?.id }
            // Новый слой встаёт по порядку категорий: обувь и низ — под верхом, аксессуары — сверху.
            val z = Z_ORDER[item.category] ?: 6
            val at = rest.indexOfFirst { (Z_ORDER[items[it.id]?.category] ?: 6) > z }.let { if (it < 0) rest.size else it }
            ed.layers = rest.toMutableList().apply { add(at, LayerData(item.id, q)) }
            ed.sel = item.id
        }
        ed.commit()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch { addPending = prepareUserItem(ctx, uri); busy = false }
        }
    }
    fun pick() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    Screen(
        title = "Гардероб",
        onBack = { nav.popBackStack() },
        actions = {
            TextButton(onClick = { nav.navigate(Routes.OUTFITS) }) {
                Icon(Icons.Default.PhotoLibrary, null)
                Text("  Луки ${outfits.size}")
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Шкаф", tab == 0, glyph = "ui:hanger") { tab = 0 }
                Pill(if (ed.layers.isEmpty()) "Образ" else "Образ · ${ed.layers.size}", tab == 1) { tab = 1 }
            }
            if (tab == 0) Closet(
                allItems, category, season, wears, ed, busy,
                onCategory = { category = it }, onSeason = { season = it },
                onAdd = { pick() }, onOpen = { openItem = it },
                modifier = Modifier.weight(1f),
            ) else BoardTab(ed, items, allItems, category, busy, onCategory = { category = it }, onToggle = { toggle(it) }, onAdd = { pick() }, onSave = { saveDialog = true }, modifier = Modifier.weight(1f))
        }
    }

    if (saveDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { saveDialog = false },
            title = { Text(btnLabel("Сохранить лук")) },
            text = {
                Column {
                    Text("Раскладка сохранится картинкой и откроется в редакторе как есть.", fontSize = 13.sp, color = extra.dim)
                    Gap(8.dp)
                    TextInput(name, { name = it }, "Например: осень, город")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val ls = ed.layers
                    val seasons = ls.mapNotNull { items[it.id]?.season }.filter { it != "любой" }
                    val season0 = seasons.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: "все"
                    io {
                        val preview = withContext(Dispatchers.Default) { renderPreview(ctx, ls, items) } ?: ""
                        dao.upsertOutfit(
                            Outfit(name = name.trim().ifBlank { "Без названия" }, season = season0, createdAt = System.currentTimeMillis(), preview = preview, layers = encodeLayers(ls))
                        )
                    }
                    saveDialog = false
                }) { Text(btnLabel("Сохранить")) }
            },
            dismissButton = { TextButton(onClick = { saveDialog = false }) { Text(btnLabel("Отмена")) } },
        )
    }

    addPending?.let { path ->
        ItemDialog(
            item = WardrobeItem(id = "", name = "", category = category.takeIf { it in CATEGORIES } ?: "верх", season = "любой", src = path, pct = ""),
            isNew = true, onBoard = false, worn = emptyList(),
            onDismiss = { java.io.File(path).delete(); addPending = null },
            onSave = { it0 ->
                val item = it0.copy(id = "user_" + System.currentTimeMillis().toString(36), name = it0.name.trim().ifBlank { "Своя вещь" })
                io { dao.upsertWardrobeItem(item) }
                addPending = null
            },
        )
    }
    openItem?.let { it0 ->
        ItemDialog(
            item = it0, isNew = false, onBoard = ed.has(it0.id), worn = wears[it0.id].orEmpty(),
            onDismiss = { openItem = null },
            onSave = { upd -> io { dao.upsertWardrobeItem(upd.copy(name = upd.name.trim().ifBlank { it0.name })) }; openItem = null },
            onToggleBoard = { toggle(it0); openItem = null; if (ed.has(it0.id)) tab = 1 },
            onDelete = {
                if (ed.has(it0.id)) { ed.layers = ed.layers.filter { it.id != it0.id }; ed.commit() }
                io {
                    // Встроенные вещи прячутся (вернутся при сбросе), свои — удаляются вместе с фото.
                    if (it0.builtin) dao.upsertWardrobeItem(it0.copy(hidden = true))
                    else { dao.deleteWardrobeItem(it0); java.io.File(it0.src).delete() }
                }
                Bitmaps.invalidate(it0.src)
                openItem = null
            },
        )
    }
}

/* ---------------- Шкаф: все вещи сеткой ---------------- */

@Composable
private fun Closet(
    all: List<WardrobeItem>, category: String, season: String, wears: Map<String, List<Long>>, ed: Board, busy: Boolean,
    onCategory: (String) -> Unit, onSeason: (String) -> Unit, onAdd: () -> Unit, onOpen: (WardrobeItem) -> Unit, modifier: Modifier,
) {
    val extra = LocalExtra.current
    val today = Dates.today()
    val shown = all.filter { (category == "все" || it.category == category) && (season == "все" || it.season == season || it.season == "любой") }
        .sortedWith(compareBy<WardrobeItem>({ CATEGORIES.indexOf(it.category).let { i -> if (i < 0) 99 else i } }, { it.name.lowercase() }))
    val never = all.count { wears[it.id].isNullOrEmpty() }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier,
    ) {
        item(span = { GridItemSpan(2) }) {
            // Сводка шкафа — как у приложений-гардеробов: сколько вещей и что давно не носили.
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(extra.card).padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Stat("${all.size}", plural(all.size, "вещь", "вещи", "вещей"))
                Stat("${CATEGORIES.count { c -> all.any { it.category == c } }}", "категорий")
                Stat("$never", "не носили")
            }
        }
        item(span = { GridItemSpan(2) }) {
            Column {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Pill("Все · ${all.size}", category == "все") { onCategory("все") } }
                    items(CATEGORIES) { c ->
                        val n = all.count { it.category == c }
                        Pill("${CATEGORY_LABELS[c]} · $n", category == c) { onCategory(c) }
                    }
                }
                Gap(6.dp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SEASONS) { (id, label) -> Pill(label, season == id, glyph = SEASON_GLYPHS[id]) { onSeason(id) } }
                }
            }
        }
        item {
            Column(
                Modifier.clip(RoundedCornerShape(16.dp))
                    .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = .5f), RoundedCornerShape(16.dp))
                    .clickable(onClick = onAdd),
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(.95f), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text(btnLabel(if (busy) "Убираю фон…" else "Добавить вещь"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text("фото на ровном фоне", fontSize = 11.sp, color = extra.dim, maxLines = 1)
                }
            }
        }
        items(shown, key = { it.id }) { it0 ->
            val worn = wears[it0.id].orEmpty()
            Column(Modifier.clip(RoundedCornerShape(16.dp)).background(extra.card).clickable { onOpen(it0) }) {
                Box(Modifier.fillMaxWidth().aspectRatio(.95f).background(BoardColor)) {
                    ItemThumb(it0, Modifier.fillMaxSize().padding(12.dp))
                    if (ed.has(it0.id)) Box(
                        Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Check, "в образе", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary) }
                }
                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text(it0.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(
                            CATEGORY_LABELS[it0.category] ?: it0.category,
                            when {
                                worn.isEmpty() -> "не носили"
                                today - worn.last() > 30 -> "давно не носили"
                                else -> "${worn.size} ${plural(worn.size, "раз", "раза", "раз")}"
                            },
                        ).joinToString(" · "),
                        fontSize = 11.sp, color = extra.dim, maxLines = 1,
                    )
                }
            }
        }
        item(span = { GridItemSpan(2) }) { HowTo("wardrobe") }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(label, fontSize = 11.sp, color = LocalExtra.current.dim)
    }
}

/** Вещь, вписанная в рамку, с мягкой тенью — как в каталоге. */
@Composable
fun ItemThumb(item: WardrobeItem, modifier: Modifier) {
    val ctx = LocalContext.current
    val bmp by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, item.src) {
        value = withContext(Dispatchers.IO) { Bitmaps.load(ctx, item.src)?.also { Bitmaps.shadow(item.src, it) } }
    }
    Canvas(modifier) {
        val b = bmp ?: return@Canvas
        val s = min(size.width / b.width, (size.height - 6f) / b.height)
        val w = b.width * s
        val h = b.height * s
        val m = android.graphics.Matrix().apply { setScale(s, s); postTranslate((size.width - w) / 2, (size.height - 6f - h) / 2) }
        drawIntoCanvas { c -> drawItem(c.nativeCanvas, item.src, b, m, 6f, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)) }
    }
}

/* ---------------- Образ: флэт-лэй на доске ---------------- */

@Composable
private fun BoardTab(
    ed: Board, items: Map<String, WardrobeItem>, all: List<WardrobeItem>, category: String, busy: Boolean,
    onCategory: (String) -> Unit, onToggle: (WardrobeItem) -> Unit, onAdd: () -> Unit, onSave: () -> Unit, modifier: Modifier,
) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    Column(modifier) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
            // Доска 3:4, вписанная в свободное место.
            val bw = min(maxWidth.value, maxHeight.value * BOARD_W / BOARD_H)
            Box(
                Modifier.width(bw.dp).aspectRatio(BOARD_W / BOARD_H).clip(RoundedCornerShape(18.dp))
                    .background(Brush.verticalGradient(listOf(BoardColor, Color(0xFFEAE5DC)))),
            ) {
                BoardCanvas(ed, items)
                if (ed.layers.isEmpty()) Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Соберите образ", color = BoardInk, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text("Выберите вещи ниже — они разложатся как в каталоге. Двигайте пальцем, щипком меняйте размер и поворот.", color = BoardDim, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }

        // Панель действий: для выбранной вещи — порядок слоёв и «убрать», иначе — раскладка и очистка.
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { ed.undo() }, enabled = ed.canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, "Отменить") }
            IconButton(onClick = { ed.redo() }, enabled = ed.canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, "Вернуть") }
            Box(Modifier.weight(1f))
            val s = ed.selected()
            if (s != null) {
                Text(items[s.id]?.name ?: "", fontSize = 12.sp, color = extra.dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(2f))
                IconButton(onClick = { ed.layers = ed.layers.filter { it.id != s.id } + s; ed.commit() }) { Icon(Icons.Default.FlipToFront, "На передний план") }
                IconButton(onClick = { ed.layers = listOf(s) + ed.layers.filter { it.id != s.id }; ed.commit() }) { Icon(Icons.Default.FlipToBack, "На задний план") }
                IconButton(onClick = { items[s.id]?.let(onToggle) }) { Icon(Icons.Default.Close, "Убрать", tint = extra.danger) }
            } else {
                TextButton(onClick = { ed.layers = autoLayout(ctx, ed.layers.map { it.id }, items); ed.commit() }, enabled = ed.layers.isNotEmpty()) {
                    Icon(Icons.Default.AutoAwesomeMosaic, null, Modifier.size(18.dp)); Text(btnLabel("  Разложить"), fontSize = 13.sp)
                }
                TextButton(onClick = { ed.layers = emptyList(); ed.sel = null; ed.commit() }, enabled = ed.layers.isNotEmpty()) { Text(btnLabel("Очистить"), fontSize = 13.sp) }
            }
        }

        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Pill("Все", category == "все") { onCategory("все") } }
            items(CATEGORIES) { c -> Pill(CATEGORY_LABELS[c] ?: c, category == c) { onCategory(c) } }
        }
        val rack = all.filter { category == "все" || it.category == category }
            .sortedBy { CATEGORIES.indexOf(it.category).let { i -> if (i < 0) 99 else i } }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Column(
                    Modifier.width(76.dp).height(96.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, extra.line, RoundedCornerShape(12.dp)).clickable(onClick = onAdd),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                    Text(btnLabel(if (busy) "…" else "Вещь"), fontSize = 11.sp, color = extra.dim)
                }
            }
            items(rack, key = { it.id }) { it0 ->
                val on = ed.has(it0.id)
                Box(
                    Modifier.width(76.dp).height(96.dp).clip(RoundedCornerShape(12.dp)).background(BoardColor)
                        .border(if (on) 2.dp else 0.dp, if (on) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp))
                        .clickable { onToggle(it0) },
                ) {
                    ItemThumb(it0, Modifier.fillMaxSize().padding(8.dp))
                    if (on) Box(
                        Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Check, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onPrimary) }
                }
            }
        }
        Button(onClick = onSave, enabled = ed.layers.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
            Text(btnLabel("Сохранить лук"))
        }
    }
}

@Composable
private fun BoardCanvas(ed: Board, items: Map<String, WardrobeItem>) {
    val ctx = LocalContext.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = constraints.maxWidth / BOARD_W
        fun board(o: Offset) = Offset(o.x / scale, o.y / scale)
        fun hit(o: Offset): String? {
            val w = board(o)
            return ed.layers.asReversed().firstOrNull { l ->
                val loc = toLocal(l.q, w.x, w.y) ?: return@firstOrNull false
                if (loc.first !in 0f..1f || loc.second !in 0f..1f) return@firstOrNull false
                val bmp = items[l.id]?.let { Bitmaps.load(ctx, it.src) } ?: return@firstOrNull true
                (bmp.getPixel((loc.first * (bmp.width - 1)).toInt(), (loc.second * (bmp.height - 1)).toInt()) ushr 24) > 16
            }?.id
        }
        Canvas(
            Modifier.fillMaxSize()
                // Касание вещи сразу выделяет её — тянуть можно без отдельного нажатия.
                .pointerInput(scale, items) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        hit(down.position)?.let { ed.sel = it }
                        do {
                            val ev = awaitPointerEvent(PointerEventPass.Final)
                        } while (ev.changes.any { it.pressed })
                        if (ed.dirty) { ed.dirty = false; ed.commit() }
                    }
                }
                .pointerInput(scale) {
                    detectTransformGestures { centroid, pan, zoom, rotation ->
                        val l = ed.selected() ?: return@detectTransformGestures
                        val c = board(centroid)
                        val rad = rotation * PI.toFloat() / 180f
                        val cs = cos(rad)
                        val sn = sin(rad)
                        val q = l.q.toMutableList()
                        for (i in 0 until 4) {
                            val dx = q[2 * i] - c.x
                            val dy = q[2 * i + 1] - c.y
                            q[2 * i] = c.x + zoom * (dx * cs - dy * sn) + pan.x / scale
                            q[2 * i + 1] = c.y + zoom * (dx * sn + dy * cs) + pan.y / scale
                        }
                        ed.replace(l.copy(q = q))
                        ed.dirty = true
                    }
                }
                .pointerInput(scale, items) { detectTapGestures { ed.sel = hit(it) } },
        ) {
            drawIntoCanvas { c -> drawBoard(ctx, c.nativeCanvas, scale, 0f, 0f, ed.layers, items) }
            ed.selected()?.let { l ->
                val p = Path()
                for (i in 0 until 4) {
                    val x = l.q[2 * i] * scale
                    val y = l.q[2 * i + 1] * scale
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close()
                drawPath(p, BoardInk.copy(alpha = .55f), style = DrawStroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
            }
        }
    }
}

/* ---------------- Карточка вещи ---------------- */

@Composable
private fun ItemDialog(
    item: WardrobeItem, isNew: Boolean, onBoard: Boolean, worn: List<Long>,
    onDismiss: () -> Unit, onSave: (WardrobeItem) -> Unit,
    onToggleBoard: (() -> Unit)? = null, onDelete: (() -> Unit)? = null,
) {
    val extra = LocalExtra.current
    var name by remember(item.id, item.src) { mutableStateOf(item.name) }
    var cat by remember(item.id, item.src) { mutableStateOf(item.category) }
    var s by remember(item.id, item.src) { mutableStateOf(item.season) }
    var confirm by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Новая вещь" else item.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(14.dp)).background(BoardColor)) {
                    ItemThumb(item, Modifier.fillMaxSize().padding(14.dp))
                }
                if (!isNew) Text(
                    if (worn.isEmpty()) "Ещё не надевали" else "Надевали ${worn.size} ${plural(worn.size, "раз", "раза", "раз")}, последний — ${Dates.label(worn.last())}",
                    fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 8.dp),
                )
                Gap(8.dp)
                TextInput(name, { name = it }, "Название")
                Gap(10.dp)
                Text(btnLabel("Категория"), fontSize = 12.sp, color = extra.dim)
                Gap(4.dp)
                ChipRow(CATEGORIES.map { it to (CATEGORY_LABELS[it] ?: it) }, cat) { cat = it }
                Gap(10.dp)
                Text(btnLabel("Сезон"), fontSize = 12.sp, color = extra.dim)
                Gap(4.dp)
                ChipRow(listOf("любой" to "Любой", "зима" to "Зима", "осень" to "Осень", "весна" to "Весна", "лето" to "Лето"), s) { s = it }
                if (onToggleBoard != null) {
                    Gap(12.dp)
                    OutlinedButton(onClick = onToggleBoard, modifier = Modifier.fillMaxWidth()) { Text(btnLabel(if (onBoard) "Убрать из образа" else "Добавить в образ")) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(item.copy(name = name, category = cat, season = s)) }) { Text(btnLabel(if (isNew) "Добавить" else "Сохранить")) } },
        dismissButton = {
            com.dasein.poryadok.ui.common.ButtonsRow {
                if (onDelete != null) TextButton(onClick = { confirm = true }) { Text(btnLabel("Удалить"), color = extra.danger) }
                TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) }
            }
        },
    )
    if (confirm && onDelete != null) ConfirmDialog("Удалить вещь?", "«${item.name}» исчезнет из гардероба.", onDismiss = { confirm = false }) { confirm = false; onDelete() }
}
