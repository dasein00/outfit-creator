package com.dasein.poryadok.ui.wardrobe

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.dasein.poryadok.ui.common.ChipRow
import com.dasein.poryadok.ui.common.DatePickDialog
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.Text
import com.dasein.poryadok.ui.common.TextButton
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.btnLabel
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.common.safeBottom
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val BoardInk = Color(0xFF2A241C)

/** Фоны коллажа, как в Fits: белый, чёрный, пастель и яркие. */
private val BACKGROUNDS = listOf(
    Color(0xFFFFFFFF), Color(0xFF111111), BoardColor, Color(0xFFE9E4DA), Color(0xFFFBE3E1), Color(0xFFE6464A),
    Color(0xFFF2A7CF), Color(0xFFFCE7F1), Color(0xFFDCE8F6), Color(0xFF3D5A80), Color(0xFFDDEBD9), Color(0xFF55705A),
    Color(0xFFFFF3C4), Color(0xFFC79246),
)

class BoardVm : ViewModel() {
    val ed = Board()
    var loaded = false
    var editing: Outfit? = null
    var planDay: Long? = null
}

/** Доска наряда: слои по порядку (последний — сверху), фон, выделение и история «Отменить/Вернуть». */
class Board {
    var layers by mutableStateOf<List<LayerData>>(emptyList())
    var bg by mutableIntStateOf(0)
    var sel by mutableStateOf<String?>(null)
    /** 0 — коллаж (столбиком), 1 — флэт-лэй (по местам). */
    var mode by mutableIntStateOf(0)
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

private fun layout(ctx: android.content.Context, ed: Board, ids: List<String>, items: Map<String, WardrobeItem>) =
    if (ed.mode == 0) collageLayout(ctx, ids, items) else autoLayout(ctx, ids, items)

/** Редактор наряда как в Fits: холст, «Добавить», «Фон», «Режим», «Далее» — сохранить. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutfitEditorScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val dao = Graph.dao
    val allItems by observe(emptyList()) { dao.wardrobe() }
    val items = remember(allItems) { allItems.associateBy { it.id } }
    val vm = viewModel { BoardVm() }
    val ed = vm.ed
    var addSheet by remember { mutableStateOf(false) }
    var bgSheet by remember { mutableStateOf(false) }
    var modeSheet by remember { mutableStateOf(false) }
    var saveDialog by remember { mutableStateOf(false) }

    LaunchedEffect(items) {
        if (vm.loaded || items.isEmpty()) return@LaunchedEffect
        vm.loaded = true
        vm.planDay = WardrobeBus.planDay
        val oid = WardrobeBus.pendingOutfit
        val start = WardrobeBus.startItems
        WardrobeBus.pendingOutfit = null; WardrobeBus.startItems = emptyList(); WardrobeBus.planDay = null
        if (oid != null) {
            val o = dao.outfits().first().firstOrNull { it.id == oid }
            if (o != null) {
                vm.editing = o
                val (ls, v2) = decodeLayers(o.layers)
                val known = ls.filter { it.id in items }
                ed.layers = if (v2) known else layout(ctx, ed, known.map { it.id }, items)
                ed.bg = o.bg
            }
        } else if (start.isNotEmpty()) {
            ed.layers = layout(ctx, ed, start.filter { it in items }, items)
        }
        ed.commit()
        if (ed.layers.isEmpty()) addSheet = true
    }

    Screen(
        title = if (vm.editing != null) "Наряд" else "Новый наряд",
        onBack = { nav.popBackStack() },
        actions = {
            IconButton(onClick = { ed.undo() }, enabled = ed.canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, "Отменить") }
            IconButton(onClick = { ed.redo() }, enabled = ed.canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, "Вернуть") }
            TextButton(onClick = { saveDialog = true }, enabled = ed.layers.isNotEmpty()) { Text(btnLabel("Далее →"), fontWeight = FontWeight.SemiBold) }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
                val bw = min(maxWidth.value, maxHeight.value * BOARD_W / BOARD_H)
                Box(
                    Modifier.width(bw.dp).aspectRatio(BOARD_W / BOARD_H).clip(RoundedCornerShape(18.dp))
                        .background(if (ed.bg != 0) Color(ed.bg) else BoardColor),
                ) {
                    BoardCanvas(ed, items)
                    if (ed.layers.isEmpty()) Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Пустой наряд", color = BoardInk, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text("Нажмите «Добавить» и выберите вещи. Тяните пальцем, щипком меняйте размер и поворот.", color = Color(0xFF8A8174), fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
            }
            // Выбранная вещь: порядок слоёв и «убрать».
            ed.selected()?.let { s ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(items[s.id]?.name ?: "", fontSize = 12.sp, color = extra.dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    IconButton(onClick = { ed.layers = ed.layers.filter { it.id != s.id } + s; ed.commit() }) { Icon(Icons.Default.FlipToFront, "На передний план") }
                    IconButton(onClick = { ed.layers = listOf(s) + ed.layers.filter { it.id != s.id }; ed.commit() }) { Icon(Icons.Default.FlipToBack, "На задний план") }
                    IconButton(onClick = { ed.layers = ed.layers.filter { it.id != s.id }; ed.sel = null; ed.commit() }) { Icon(Icons.Default.Close, "Убрать", tint = extra.danger) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolButton(Icons.Default.Checkroom, "Добавить", Modifier.weight(1f)) { addSheet = true }
                ToolButton(Icons.Default.FormatColorFill, "Фон", Modifier.weight(1f)) { bgSheet = true }
                ToolButton(Icons.Default.SwapHoriz, "Режим", Modifier.weight(1f)) { modeSheet = true }
            }
        }
    }

    if (addSheet) AddItemsSheet(allItems, ed.layers.map { it.id }.toSet(), onDismiss = { addSheet = false }) { picked ->
        addSheet = false
        val keep = ed.layers.filter { it.id in picked }
        val added = picked.filter { id -> keep.none { it.id == id } }
        if (added.isNotEmpty() || keep.size != ed.layers.size) {
            // Новые вещи — раскладка всего наряда заново, как в Fits; без новых — только убираем лишние.
            ed.layers = if (added.isEmpty()) keep else layout(ctx, ed, keep.map { it.id } + added, items)
            ed.sel = null
            ed.commit()
        }
    }
    if (bgSheet) {
        val state = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { bgSheet = false }, sheetState = state) {
            Column(Modifier.padding(horizontal = 16.dp).safeBottom()) {
                Text(btnLabel("Изменить фон"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally))
                Gap(12.dp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(BACKGROUNDS) { c ->
                        val on = (if (ed.bg == 0) BoardColor else Color(ed.bg)) == c
                        Box(
                            Modifier.size(48.dp, 64.dp).clip(RoundedCornerShape(10.dp)).background(c)
                                .border(if (on) 2.5.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else extra.line, RoundedCornerShape(10.dp))
                                .clickable { ed.bg = if (c == BoardColor) 0 else c.toArgb() },
                        )
                    }
                }
                Gap(12.dp)
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = { ed.bg = 0 }) { Text(btnLabel("Сбросить")) }
                    Box(Modifier.weight(1f))
                    TextButton(onClick = { bgSheet = false }) { Text(btnLabel("Готово"), fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
    if (modeSheet) {
        val state = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { modeSheet = false }, sheetState = state) {
            Column(Modifier.padding(horizontal = 16.dp).safeBottom()) {
                Text(btnLabel("Способ создания наряда"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally))
                Gap(12.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("Коллаж" to "вещи столбиком: верх, низ, обувь", "Флэт-лэй" to "вещи по местам, как на полу").forEachIndexed { i, (t, d) ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                                .border(if (ed.mode == i) 2.dp else 1.dp, if (ed.mode == i) MaterialTheme.colorScheme.primary else extra.line, RoundedCornerShape(14.dp))
                                .clickable {
                                    ed.mode = i
                                    if (ed.layers.isNotEmpty()) { ed.layers = layout(ctx, ed, ed.layers.map { it.id }, items); ed.commit() }
                                    modeSheet = false
                                }
                                .padding(12.dp),
                        ) {
                            Text(t, fontWeight = FontWeight.SemiBold)
                            Text(d, fontSize = 12.sp, color = extra.dim)
                        }
                    }
                }
                Gap(8.dp)
            }
        }
    }
    if (saveDialog) SaveOutfitDialog(vm, items, onDismiss = { saveDialog = false }) { nav.popBackStack() }
}

@Composable
private fun ToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val extra = LocalExtra.current
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(extra.card).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(22.dp))
        Text(btnLabel(label), fontSize = 11.sp)
    }
}

/** «Далее»: название, сезон, день в планировщике — и сохранить (или обновить открытый наряд). */
@Composable
private fun SaveOutfitDialog(vm: BoardVm, items: Map<String, WardrobeItem>, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val ed = vm.ed
    val old = vm.editing
    var name by remember { mutableStateOf(old?.name ?: "") }
    val auto = ed.layers.mapNotNull { items[it.id]?.season }.filter { it != "любой" }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: "все"
    var season by remember { mutableStateOf(old?.season ?: auto) }
    var day by remember { mutableStateOf(vm.planDay) }
    var pickDay by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(btnLabel(if (old != null) "Сохранить наряд" else "Новый наряд")) },
        text = {
            Column {
                TextInput(name, { name = it }, "Название, например: осень, город")
                Gap(10.dp)
                Text(btnLabel("Сезон"), fontSize = 12.sp, color = extra.dim)
                Gap(4.dp)
                ChipRow(SEASONS, season) { season = it }
                Gap(10.dp)
                TextButton(onClick = { pickDay = true }) {
                    Text(if (day == null) "Запланировать на день…" else "В планировщике: ${Dates.full(day!!)}")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val ls = ed.layers
                val bg = ed.bg
                val d = day
                io {
                    val preview = withContext(Dispatchers.Default) { renderPreview(ctx, ls, items, bg) } ?: ""
                    val days = (old?.days().orEmpty() + listOfNotNull(d)).distinct().sorted().joinToString(",")
                    if (old != null && old.preview.isNotBlank() && old.preview != preview) java.io.File(old.preview).delete()
                    Graph.dao.upsertOutfit(
                        (old ?: Outfit(name = "", season = "", createdAt = System.currentTimeMillis(), layers = ""))
                            .copy(name = name.trim().ifBlank { "Без названия" }, season = season, preview = preview, layers = encodeLayers(ls), wornDays = days, bg = bg)
                    )
                }
                onDismiss(); onSaved()
            }) { Text(btnLabel("Сохранить"), fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) } },
    )
    if (pickDay) DatePickDialog(day, onDismiss = { pickDay = false }, onPick = { day = it })
}

/** Выбор вещей для наряда: категории сверху, сетка с отметками, «N выбрано» и «Готово». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemsSheet(all: List<WardrobeItem>, initial: Set<String>, onDismiss: () -> Unit, onDone: (List<String>) -> Unit) {
    val extra = LocalExtra.current
    var cat by remember { mutableStateOf("все") }
    var picked by remember { mutableStateOf(initial.toList()) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val shown = all.filter { !it.archived && (cat == "все" || it.category == cat) }
        .sortedBy { CATEGORIES.indexOf(it.category).let { i -> if (i < 0) 99 else i } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.safeBottom()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Pill("Все", cat == "все") { cat = "все" } }
                items(CATEGORIES) { c -> Pill(CATEGORY_LABELS[c] ?: c, cat == c) { cat = c } }
            }
            if (shown.isEmpty()) Text(
                "Здесь пока нет вещей. Добавьте их в «Одежде».", color = extra.dim, fontSize = 13.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(32.dp),
            )
            LazyVerticalGrid(
                GridCells.Fixed(3), Modifier.heightIn(max = 460.dp),
                contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(shown, key = { it.id }) { it0 ->
                    val on = it0.id in picked
                    Box(
                        Modifier.aspectRatio(.85f).clip(RoundedCornerShape(12.dp)).background(BoardColor)
                            .clickable { picked = if (on) picked - it0.id else picked + it0.id },
                    ) {
                        ItemThumb(it0, Modifier.fillMaxSize().padding(8.dp), shadow = false)
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp).clip(CircleShape)
                                .background(if (on) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = .7f))
                                .border(1.dp, if (on) Color.Transparent else Color(0xFF9A9286), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { if (on) Icon(Icons.Default.Check, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onPrimary) }
                    }
                }
            }
            if (picked.isNotEmpty()) LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(picked, key = { "p$it" }) { id ->
                    val it0 = all.firstOrNull { it.id == id } ?: return@items
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(BoardColor).clickable { picked = picked - id }) {
                        ItemThumb(it0, Modifier.fillMaxSize().padding(4.dp), shadow = false)
                        Icon(Icons.Default.Close, "убрать", Modifier.align(Alignment.TopEnd).size(14.dp).clip(CircleShape).background(Color(0xCC211D18)), tint = Color.White)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${picked.size} выбрано", color = extra.dim, modifier = Modifier.weight(1f))
                TextButton(onClick = { onDone(picked) }) { Text(btnLabel("Готово"), fontWeight = FontWeight.SemiBold) }
            }
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
                val ink = if (ed.bg != 0 && Color(ed.bg).let { it.red + it.green + it.blue < 1.2f }) Color.White else BoardInk
                drawPath(p, ink.copy(alpha = .6f), style = DrawStroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
            }
        }
    }
}
