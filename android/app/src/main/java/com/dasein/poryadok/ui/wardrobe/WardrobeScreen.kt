package com.dasein.poryadok.ui.wardrobe

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixOff
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.ItemFit
import com.dasein.poryadok.data.Outfit
import com.dasein.poryadok.data.WardrobeItem
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.ChipRow
import com.dasein.poryadok.ui.common.ConfirmDialog
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.theme.LocalExtra
import com.dasein.poryadok.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

enum class Mode { TRANSFORM, DISTORT, ERASE }

class EditorVm : ViewModel() {
    val ed = Editor()
    var initialized = false
    var fits: Map<String, ItemFit> = emptyMap()
}

/** Образ, который нужно открыть в редакторе (из галереи). */
object WardrobeBus {
    var pendingOutfit: Long? = null
}

data class Snap(val layers: Map<String, LayerData>, val model: List<Stroke>)

class Editor {
    var layers by mutableStateOf<Map<String, LayerData>>(emptyMap())
    var model by mutableStateOf<List<Stroke>>(emptyList())
    var sel by mutableStateOf<String?>(null)
    var mode by mutableStateOf(Mode.TRANSFORM)
    var brush by mutableFloatStateOf(18f)
    var dirty = false
    private val hist = mutableListOf<Snap>()
    private var pos = -1
    var canUndo by mutableStateOf(false)
    var canRedo by mutableStateOf(false)

    fun commit() {
        while (hist.size > pos + 1) hist.removeAt(hist.size - 1)
        hist.add(Snap(layers, model))
        if (hist.size > 60) hist.removeAt(0)
        pos = hist.size - 1
        canUndo = pos > 0; canRedo = false
    }

    private fun restore(s: Snap) { layers = s.layers; model = s.model; canUndo = pos > 0; canRedo = pos < hist.size - 1 }
    fun undo() { if (pos > 0) { pos--; restore(hist[pos]) } }
    fun redo() { if (pos < hist.size - 1) { pos++; restore(hist[pos]) } }

    fun selected(): LayerData? = if (sel == MODEL_ID) LayerData(MODEL_ID, modelQuad(), model) else layers.values.firstOrNull { it.id == sel }

    fun put(l: LayerData, items: Map<String, WardrobeItem>) {
        if (l.id == MODEL_ID) { model = l.strokes; return }
        val cat = items[l.id]?.category ?: return
        layers = layers + (cat to l)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WardrobeScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val dao = Graph.dao
    val scope = rememberCoroutineScope()
    val allItems by observe(emptyList()) { dao.wardrobe() }
    val outfits by observe(emptyList()) { dao.outfits() }
    val items = remember(allItems) { allItems.associateBy { it.id } }
    val vm = viewModel { EditorVm() }
    val ed = vm.ed
    var season by remember { mutableStateOf("все") }
    var category by remember { mutableStateOf("верх") }
    var saveDialog by remember { mutableStateOf(false) }
    var addPending by remember { mutableStateOf<Prepared?>(null) }
    var deleteItem by remember { mutableStateOf<WardrobeItem?>(null) }
    var busy by remember { mutableStateOf(false) }
    var redraw by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        val f = dao.fitsNow()
        vm.fits = f.associateBy { it.itemId }
        if (!vm.initialized) {
            vm.initialized = true
            f.firstOrNull { it.itemId == MODEL_ID }?.let { m -> fitToLayer(m)?.let { ed.model = it.strokes } }
            ed.commit()
        }
    }
    LaunchedEffect(items, WardrobeBus.pendingOutfit) {
        val id = WardrobeBus.pendingOutfit ?: return@LaunchedEffect
        if (items.isEmpty()) return@LaunchedEffect
        WardrobeBus.pendingOutfit = null
        val o = outfits.firstOrNull { it.id == id } ?: dao.outfits().first().firstOrNull { it.id == id } ?: return@LaunchedEffect
        val ls = runCatching { wjson.decodeFromString(layersSer, o.layers) }.getOrDefault(emptyList())
        ed.layers = ls.mapNotNull { l -> items[l.id]?.let { it.category to l } }.toMap()
        ed.sel = null
        ed.commit()
    }

    fun persist() {
        val l = ed.selected() ?: return
        if (l.id != MODEL_ID) vm.fits = vm.fits + (l.id to ItemFit(l.id, l.q.joinToString(","), wjson.encodeToString(strokesSer, l.strokes)))
        io { if (l.id == MODEL_ID) saveFit(LayerData(MODEL_ID, modelQuad(), ed.model)) else saveFit(l) }
    }

    fun wear(item: WardrobeItem) {
        val cur = ed.layers[item.category]
        if (cur?.id == item.id) {
            ed.layers = ed.layers - item.category
            if (ed.sel == item.id) ed.sel = null
        } else {
            val saved = vm.fits[item.id]?.let { fitToLayer(it) }
            val l = saved ?: LayerData(item.id, defaultQuad(item, Bitmaps.load(ctx, item.src)))
            ed.layers = ed.layers + (item.category to l)
            ed.sel = item.id
        }
        ed.commit()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                addPending = prepareUserItem(ctx, uri, category)
                busy = false
            }
        }
    }

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
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SEASONS) { (id, label) -> Pill(label, season == id) { season = id } }
            }
            Gap(6.dp)
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth()
                    .background(Brush.radialGradient(listOf(Palette.Stage, Palette.Stage2)))
            ) {
                val cw = constraints.maxWidth.toFloat()
                val chh = constraints.maxHeight.toFloat()
                val scale = min(cw / WORLD_W, chh / WORLD_H) * .96f
                val ox = (cw - WORLD_W * scale) / 2
                val oy = (chh - WORLD_H * scale) / 2
                fun world(o: Offset) = Offset((o.x - ox) / scale, (o.y - oy) / scale)
                var corner by remember { mutableIntStateOf(-1) }

                val gestures = when (ed.mode) {
                    Mode.TRANSFORM -> Modifier.pointerInput(scale, ox, oy) {
                        detectTransformGestures { centroid, pan, zoom, rotation ->
                            val l = ed.selected() ?: return@detectTransformGestures
                            if (l.id == MODEL_ID) return@detectTransformGestures
                            val c = world(centroid)
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
                            ed.put(l.copy(q = q), items)
                            ed.dirty = true
                        }
                    }
                    Mode.DISTORT -> Modifier.pointerInput(scale, ox, oy) {
                        detectDragGestures(
                            onDragStart = { off ->
                                val l = ed.selected()
                                corner = -1
                                if (l != null && l.id != MODEL_ID) {
                                    var best = 60f
                                    for (i in 0 until 4) {
                                        val d = hypot(ox + l.q[2 * i] * scale - off.x, oy + l.q[2 * i + 1] * scale - off.y)
                                        if (d < best) { best = d; corner = i }
                                    }
                                }
                            },
                            onDrag = { change, amount ->
                                val l = ed.selected()
                                if (corner >= 0 && l != null) {
                                    change.consume()
                                    val q = l.q.toMutableList()
                                    q[2 * corner] += amount.x / scale
                                    q[2 * corner + 1] += amount.y / scale
                                    ed.put(l.copy(q = q), items)
                                    ed.dirty = true
                                }
                            },
                        )
                    }
                    Mode.ERASE -> Modifier.pointerInput(scale, ox, oy) {
                        detectDragGestures(
                            onDragStart = { off ->
                                val l = ed.selected() ?: return@detectDragGestures
                                val w = world(off)
                                val loc = toLocal(l.q, w.x, w.y) ?: return@detectDragGestures
                                val r = ed.brush / quadWidth(l.q)
                                ed.put(l.copy(strokes = l.strokes + Stroke(r, listOf(loc.first, loc.second))), items)
                                ed.dirty = true
                            },
                            onDrag = { change, _ ->
                                val l = ed.selected()
                                if (l != null && l.strokes.isNotEmpty()) {
                                    change.consume()
                                    val w = world(change.position)
                                    toLocal(l.q, w.x, w.y)?.let { loc ->
                                        val last = l.strokes.last()
                                        ed.put(l.copy(strokes = l.strokes.dropLast(1) + last.copy(pts = last.pts + loc.first + loc.second)), items)
                                    }
                                }
                            },
                        )
                    }
                }

                Canvas(
                    Modifier.fillMaxSize()
                        .then(gestures)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val ev = awaitPointerEvent(PointerEventPass.Final)
                                } while (ev.changes.any { it.pressed })
                                if (ed.dirty) {
                                    ed.dirty = false
                                    ed.commit()
                                    persist()
                                }
                            }
                        }
                        .pointerInput(scale, ox, oy, items) {
                            detectTapGestures { off ->
                                val w = world(off)
                                val hit = ed.layers.values.sortedByDescending { Z_ORDER[items[it.id]?.category] ?: 0 }.firstOrNull { l ->
                                    val loc = toLocal(l.q, w.x, w.y) ?: return@firstOrNull false
                                    if (loc.first !in 0f..1f || loc.second !in 0f..1f) return@firstOrNull false
                                    val bmp = items[l.id]?.let { Bitmaps.load(ctx, it.src) } ?: return@firstOrNull true
                                    val px = (loc.first * (bmp.width - 1)).toInt()
                                    val py = (loc.second * (bmp.height - 1)).toInt()
                                    (bmp.getPixel(px, py) ushr 24) > 16
                                }
                                ed.sel = hit?.id ?: if (ed.mode == Mode.ERASE && w.x in MX0..(MX0 + MW)) MODEL_ID else null
                            }
                        }
                ) {
                    redraw.let { }
                    drawIntoCanvas { c -> drawScene(ctx, c.nativeCanvas, scale, ox, oy, ed.layers.values.toList(), items, ed.model) }
                    val l = ed.selected()
                    if (l != null && l.id != MODEL_ID) {
                        val p = Path()
                        for (i in 0 until 4) {
                            val x = ox + l.q[2 * i] * scale
                            val y = oy + l.q[2 * i + 1] * scale
                            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                        }
                        p.close()
                        drawPath(p, Color(0xCC211D18), style = DrawStroke(2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))))
                        if (ed.mode == Mode.DISTORT) for (i in 0 until 4) {
                            val o = Offset(ox + l.q[2 * i] * scale, oy + l.q[2 * i + 1] * scale)
                            drawCircle(Color(0xFF211D18), 14f, o)
                            drawCircle(Palette.Brass, 10f, o)
                        }
                    }
                }
                if (ed.layers.isEmpty()) Text(
                    "Выберите вещь снизу — она наденется на манекен",
                    color = Color(0xFF6C6355), fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                )
                if (busy) Text("Убираю фон…", color = Color(0xFF211D18), modifier = Modifier.align(Alignment.Center))
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { ed.undo() }, enabled = ed.canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, "Отменить") }
                IconButton(onClick = { ed.redo() }, enabled = ed.canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, "Вернуть") }
                ModeChip("Трансформ", Icons.Default.OpenWith, ed.mode == Mode.TRANSFORM) { ed.mode = Mode.TRANSFORM }
                ModeChip("Искажение", Icons.Default.Transform, ed.mode == Mode.DISTORT) { ed.mode = Mode.DISTORT }
                ModeChip("Ластик", Icons.Default.AutoFixOff, ed.mode == Mode.ERASE) { ed.mode = Mode.ERASE }
                IconButton(onClick = {
                    val l = ed.selected() ?: return@IconButton
                    if (l.id == MODEL_ID) return@IconButton
                    val q = l.q
                    ed.put(l.copy(q = listOf(q[2], q[3], q[0], q[1], q[6], q[7], q[4], q[5])), items)
                    ed.commit(); persist()
                }) { Icon(Icons.Default.Flip, "Отразить") }
                IconButton(onClick = {
                    val l = ed.selected() ?: return@IconButton
                    if (l.id == MODEL_ID) { ed.model = emptyList() } else {
                        val it0 = items[l.id] ?: return@IconButton
                        ed.put(LayerData(l.id, defaultQuad(it0, Bitmaps.load(ctx, it0.src))), items)
                    }
                    ed.commit(); persist()
                }) { Icon(Icons.Default.Refresh, "Сбросить вещь") }
            }
            if (ed.mode == Mode.ERASE) Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Кисть", fontSize = 12.sp, color = extra.dim)
                Slider(ed.brush, { ed.brush = it }, valueRange = 4f..60f, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                Text(if (ed.sel == null) "выберите вещь или манекен" else "", fontSize = 11.sp, color = extra.dim)
            }

            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    FilterChip(
                        selected = ed.sel == MODEL_ID,
                        onClick = { ed.sel = if (ed.sel == MODEL_ID) null else MODEL_ID; if (ed.sel == MODEL_ID) ed.mode = Mode.ERASE },
                        label = { Text("Манекен") },
                    )
                }
                items(ed.layers.values.sortedByDescending { Z_ORDER[items[it.id]?.category] ?: 0 }.toList(), key = { it.id }) { l ->
                    FilterChip(
                        selected = ed.sel == l.id,
                        onClick = { ed.sel = if (ed.sel == l.id) null else l.id },
                        label = { Text(items[l.id]?.name ?: "?", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(110.dp)) },
                        trailingIcon = {
                            Text("×", fontSize = 18.sp, color = extra.danger, modifier = Modifier.clip(RoundedCornerShape(8.dp)).combinedClickable(onClick = {
                                items[l.id]?.let { wear(it) }
                            }))
                        },
                    )
                }
            }

            TabRow(selectedTabIndex = CATEGORIES.indexOf(category), containerColor = MaterialTheme.colorScheme.background) {
                CATEGORIES.forEach { c ->
                    Tab(category == c, onClick = { category = c }, text = { Text(if (c == "аксессуары") "Аксес." else c.replaceFirstChar { it.uppercase() }) })
                }
            }
            val rack = allItems.filter { it.category == category && (season == "все" || it.season == season || it.season == "любой") }
            LazyRow(
                contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(9.dp),
                modifier = Modifier.height(128.dp),
            ) {
                item {
                    Column(
                        Modifier.width(84.dp).height(110.dp).clip(RoundedCornerShape(12.dp))
                            .border(1.dp, extra.line, RoundedCornerShape(12.dp))
                            .combinedClickable(onClick = {
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Text("+", fontSize = 26.sp, color = MaterialTheme.colorScheme.primary)
                        Text("Своё фото", fontSize = 11.sp, color = extra.dim)
                    }
                }
                items(rack, key = { it.id }) { it0 -> RackCard(it0, ed.layers[it0.category]?.id == it0.id, onClick = { wear(it0) }, onLong = { if (!it0.builtin) deleteItem = it0 }) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { saveDialog = true }, enabled = ed.layers.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Сохранить лук") }
                OutlinedButton(onClick = { ed.layers = emptyMap(); ed.sel = null; ed.commit() }) { Text("Снять всё") }
            }
        }
    }

    if (saveDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { saveDialog = false },
            title = { Text("Сохранить образ") },
            text = {
                Column {
                    Text("Лук запомнит подгонку каждой вещи и стёртые места.", fontSize = 13.sp, color = extra.dim)
                    Gap(8.dp)
                    TextInput(name, { name = it }, "Например: осенний рабочий")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val ls = ed.layers.values.toList()
                    val model = ed.model
                    val seasons = ls.mapNotNull { items[it.id]?.season }.filter { it != "любой" }
                    val season0 = seasons.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: "все"
                    io {
                        val preview = withContext(Dispatchers.Default) { renderPreview(ctx, ls, items, model) } ?: ""
                        dao.upsertOutfit(
                            Outfit(
                                name = name.trim().ifBlank { "Без названия" }, season = season0,
                                createdAt = System.currentTimeMillis(), preview = preview,
                                layers = wjson.encodeToString(layersSer, ls),
                            )
                        )
                    }
                    saveDialog = false
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { saveDialog = false }) { Text("Отмена") } },
        )
    }
    addPending?.let { p ->
        var name by remember(p) { mutableStateOf("") }
        var s by remember(p) { mutableStateOf("любой") }
        val preview by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, p) {
            value = withContext(Dispatchers.IO) { android.graphics.BitmapFactory.decodeFile(p.path)?.asImageBitmap() }
        }
        AlertDialog(
            onDismissRequest = { java.io.File(p.path).delete(); addPending = null },
            title = { Text("Новая вещь: $category") },
            text = {
                Column {
                    Box(Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)).background(Palette.Stage), contentAlignment = Alignment.Center) {
                        preview?.let { Image(it, null, Modifier.fillMaxSize().padding(8.dp)) }
                    }
                    Gap(8.dp)
                    TextInput(name, { name = it }, "Название")
                    Gap(8.dp)
                    ChipRow(listOf("любой" to "Любой", "зима" to "Зима", "осень" to "Осень", "весна" to "Весна", "лето" to "Лето"), s) { s = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val item = WardrobeItem(
                        id = "user_" + System.currentTimeMillis().toString(36), name = name.trim().ifBlank { "Своя вещь" },
                        category = category, season = s, src = p.path, pct = p.pct.joinToString(","),
                    )
                    io { dao.upsertWardrobeItem(item) }
                    addPending = null
                    redraw++
                }) { Text("Добавить") }
            },
            dismissButton = { TextButton(onClick = { java.io.File(p.path).delete(); addPending = null }) { Text("Отмена") } },
        )
    }
    deleteItem?.let { it0 ->
        ConfirmDialog("Удалить вещь?", "«${it0.name}» исчезнет из гардероба, фото удалится с телефона.", onDismiss = { deleteItem = null }) {
            if (ed.layers[it0.category]?.id == it0.id) { ed.layers = ed.layers - it0.category; ed.commit() }
            io { dao.deleteFit(it0.id); dao.deleteWardrobeItem(it0); java.io.File(it0.src).delete() }
            Bitmaps.invalidate(it0.src)
        }
    }
}

@Composable
private fun ModeChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = on, onClick = onClick,
        label = { if (on) Text(label, fontSize = 12.sp) else Icon(icon, label, Modifier.size(18.dp)) },
        leadingIcon = if (on) ({ Icon(icon, null, Modifier.size(16.dp)) }) else null,
        modifier = Modifier.padding(horizontal = 2.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RackCard(item: WardrobeItem, worn: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val img by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, item.src) {
        value = withContext(Dispatchers.IO) { Bitmaps.load(ctx, item.src)?.asImageBitmap() }
    }
    Column(
        Modifier.width(84.dp).clip(RoundedCornerShape(12.dp))
            .background(if (worn) extra.cardHigh else extra.card)
            .border(1.dp, if (worn) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(5.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(68.dp).clip(RoundedCornerShape(8.dp)).background(Palette.Stage), contentAlignment = Alignment.Center) {
            img?.let { Image(it, item.name, Modifier.fillMaxSize().padding(4.dp)) }
            if (!item.builtin) Text(
                "моя", fontSize = 9.sp, color = Color(0xFF241C0D),
                modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).clip(RoundedCornerShape(6.dp)).background(Palette.Brass).padding(horizontal = 4.dp),
            )
        }
        Text(item.name, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (worn) MaterialTheme.colorScheme.onSurface else extra.dim)
    }
}
