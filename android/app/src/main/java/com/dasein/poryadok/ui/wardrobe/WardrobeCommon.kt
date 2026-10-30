package com.dasein.poryadok.ui.wardrobe

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.dasein.poryadok.ui.common.cardViewport
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import com.dasein.poryadok.data.Outfit
import com.dasein.poryadok.data.WardrobeItem
import com.dasein.poryadok.ui.common.Text
import com.dasein.poryadok.ui.common.TextButton
import com.dasein.poryadok.ui.common.btnLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

/* Маршруты гардероба (как в Fits: шкаф, карточка вещи, редактор наряда, планировщик, подборка). */
const val WARDROBE_EDITOR = "wardrobeEditor"
const val WARDROBE_PLANNER = "wardrobePlanner"
fun wardrobeItemRoute(id: String) = "wardrobeItem/$id"
fun wardrobeSetRoute(id: Long) = "wardrobeSet/$id"

/** Цвет «студийной» подложки под вещами. */
val BoardColor = Color(BOARD_RGB)

/** Что открыть в редакторе наряда: существующий образ, стартовые вещи и день для планировщика. */
object WardrobeBus {
    var pendingOutfit: Long? = null
    var startItems: List<String> = emptyList()
    var planDay: Long? = null
}

/** Открыть редактор наряда. */
fun openEditor(nav: androidx.navigation.NavHostController, outfit: Long? = null, items: List<String> = emptyList(), planDay: Long? = null) {
    WardrobeBus.pendingOutfit = outfit
    WardrobeBus.startItems = items
    WardrobeBus.planDay = planDay
    nav.navigate(WARDROBE_EDITOR)
}

fun Outfit.days(): List<Long> = wornDays.split(",").mapNotNull { it.toLongOrNull() }.sorted()
fun Outfit.itemIds(): List<String> = decodeLayers(layers).first.map { it.id }

/** Дни, когда надевали каждую вещь (по образам в «Нарядах» и «Планировщике»), — только прошедшие и сегодня. */
fun wearsOf(outfits: List<Outfit>, today: Long): Map<String, List<Long>> {
    val m = HashMap<String, MutableList<Long>>()
    outfits.forEach { o ->
        val days = o.days().filter { it <= today }
        o.itemIds().forEach { id -> m.getOrPut(id) { mutableListOf() }.addAll(days) }
    }
    return m.mapValues { it.value.distinct().sorted() }
}

fun WardrobeItem.colorList(): List<Color> = colors.split(",").mapNotNull { c ->
    runCatching { Color(android.graphics.Color.parseColor(c.trim())) }.getOrNull()
}

/** Вещь, вписанная в рамку, с мягкой тенью — как в каталоге. */
@Composable
fun ItemThumb(item: WardrobeItem, modifier: Modifier, shadow: Boolean = true) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, item.src) {
        value = withContext(Dispatchers.IO) { Bitmaps.load(ctx, item.src)?.also { if (shadow) Bitmaps.shadow(item.src, it) } }
    }
    Canvas(modifier) {
        val b = bmp ?: return@Canvas
        val s = min(size.width / b.width, (size.height - 6f) / b.height)
        val w = b.width * s
        val h = b.height * s
        val m = android.graphics.Matrix().apply { setScale(s, s); postTranslate((size.width - w) / 2, (size.height - 6f - h) / 2) }
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
        drawIntoCanvas { c ->
            if (shadow) drawItem(c.nativeCanvas, item.src, b, m, 6f, paint) else c.nativeCanvas.drawBitmap(b, m, paint)
        }
    }
}

/** Простой диалог одного поля: название, бренд, теги, цена… */
@Composable
fun EditTextDialog(title: String, value: String, number: Boolean = false, multiline: Boolean = false, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var v by remember { mutableStateOf(value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    v, { v = it }, singleLine = !multiline, minLines = if (multiline) 3 else 1, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(v.trim()); onDismiss() }) { Text(btnLabel("Готово")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) } },
    )
}

/** «Сохранить изображение»: вещь PNG с прозрачностью в «Галерею» (Pictures/DASEIN). */
suspend fun saveToGallery(ctx: Context, item: WardrobeItem): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val b = Bitmaps.load(ctx, item.src) ?: return@runCatching false
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "wardrobe_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (android.os.Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DASEIN")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
        ctx.contentResolver.openOutputStream(uri)?.use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } != null
    }.getOrDefault(false)
}

val SET_KINDS = listOf("wish" to "Списки желаний", "pack" to "Упаковка", "own" to "Свои")
fun setKindLabel(k: String) = when (k) { "wish" -> "Список желаний"; "pack" -> "Упаковка"; else -> "Своя подборка" }

/** Свой порядок вещей в «Одежде» («Переместить вещи»): список id в настройках телефона. */
object WardrobeOrder {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("wardrobe", Context.MODE_PRIVATE)
    fun get(ctx: Context): List<String> = sp(ctx).getString("order", "").orEmpty().split(",").filter { it.isNotBlank() }
    fun set(ctx: Context, ids: List<String>) { sp(ctx).edit().putString("order", ids.joinToString(",")).apply() }

    /** Вещи из списка — по нему; новые (которых в списке нет) — в начале, свежие первыми. */
    fun sort(items: List<WardrobeItem>, order: List<String>): List<WardrobeItem> {
        val pos = order.withIndex().associate { it.value to it.index }
        val (known, fresh) = items.partition { it.id in pos }
        return fresh.sortedByDescending { it.addedAt } + known.sortedBy { pos.getValue(it.id) }
    }
}

/** «Переместить вещи»: удерживайте вещь и ведите — она едет за пальцем, пока его не отпустить; у края экрана список сам прокручивается. */
@Composable
fun ReorderGrid(items: List<WardrobeItem>, onCancel: () -> Unit, onDone: (List<String>) -> Unit) {
    val extra = com.dasein.poryadok.ui.theme.LocalExtra.current
    val order = remember { androidx.compose.runtime.mutableStateListOf(*items.map { it.id }.toTypedArray()) }
    val byId = remember(items) { items.associateBy { it.id } }
    var dragging by remember { mutableStateOf<String?>(null) }
    // Палец и захват — в координатах содержимого списка (с учётом прокрутки).
    var finger by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var grab by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val scroll = androidx.compose.foundation.rememberScrollState()
    val bg = tileBg()
    Column(Modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text("Удерживайте вещь и ведите, не отпуская", fontSize = 12.sp, color = extra.dim, modifier = Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text(btnLabel("Отмена")) }
            TextButton(onClick = { onDone(order.toList()) }) { Text(btnLabel("Готово"), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) }
        }
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).cardViewport()) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val cols = 3
            val cw = with(density) { maxWidth.toPx() } / cols
            val ch = cw * 1.15f
            val viewport = with(density) { maxHeight.toPx() }
            val rows = (order.size + cols - 1) / cols
            fun slot(i: Int) = androidx.compose.ui.geometry.Offset((i % cols) * cw, (i / cols) * ch)
            fun indexAt(p: androidx.compose.ui.geometry.Offset): Int {
                val c = (p.x / cw).toInt().coerceIn(0, cols - 1)
                val r = (p.y / ch).toInt().coerceAtLeast(0)
                return (r * cols + c).coerceIn(0, (order.size - 1).coerceAtLeast(0))
            }
            /** Переставить перетаскиваемую вещь туда, где сейчас её центр. */
            fun retarget() {
                val id = dragging ?: return
                val center = finger - grab + androidx.compose.ui.geometry.Offset(cw / 2, ch / 2)
                val to = indexAt(center)
                val from = order.indexOf(id)
                if (from >= 0 && to != from) { order.removeAt(from); order.add(to, id) }
            }
            // Автопрокрутка, пока палец держат у верхнего или нижнего края, — даже без движения.
            androidx.compose.runtime.LaunchedEffect(dragging) {
                while (dragging != null) {
                    val vy = finger.y - scroll.value
                    val step = when {
                        vy > viewport - ch * .6f -> 18f + 30f * ((vy - (viewport - ch * .6f)) / (ch * .6f)).coerceIn(0f, 1f)
                        vy < ch * .6f && scroll.value > 0 -> -(18f + 30f * ((ch * .6f - vy) / (ch * .6f)).coerceIn(0f, 1f))
                        else -> 0f
                    }
                    if (step != 0f) {
                        val used = scroll.scrollBy(step)
                        finger += androidx.compose.ui.geometry.Offset(0f, used)
                        retarget()
                    }
                    kotlinx.coroutines.delay(16)
                }
            }
            Box(Modifier.fillMaxWidth().verticalScroll(scroll, enabled = dragging == null)) {
                Box(
                    Modifier.fillMaxWidth().height(with(density) { (rows * ch + ch * .5f).toDp() })
                        // Один обработчик на всю сетку: перестановки не прерывают перетаскивание.
                        .pointerInput(cw, ch) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { off ->
                                    val i = indexAt(off)
                                    if (i in order.indices) { dragging = order[i]; finger = off; grab = off - slot(i) }
                                },
                                onDragEnd = { dragging = null },
                                onDragCancel = { dragging = null },
                                onDrag = { c, amount -> c.consume(); finger += amount; retarget() },
                            )
                        },
                ) {
                    order.forEach { id ->
                        androidx.compose.runtime.key(id) {
                            val it0 = byId[id]
                            if (it0 != null) {
                                val isDrag = dragging == id
                                val anim by androidx.compose.animation.core.animateOffsetAsState(slot(order.indexOf(id)), label = "slot")
                                val pos = if (isDrag) finger - grab else anim
                                Box(
                                    Modifier
                                        .offset { androidx.compose.ui.unit.IntOffset(pos.x.toInt(), pos.y.toInt()) }
                                        .size(with(density) { cw.toDp() }, with(density) { ch.toDp() })
                                        .zIndex(if (isDrag) 1f else 0f)
                                        .graphicsLayer { scaleX = if (isDrag) 1.08f else 1f; scaleY = scaleX; shadowElevation = if (isDrag) 16f else 0f }
                                        .padding(4.dp)
                                        .clip(tileShape())
                                        .background(bg),
                                ) { ItemThumb(it0, Modifier.fillMaxSize().padding(8.dp), shadow = false) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ---------- вид плашек вещей ---------- */

/** Фоны плашек вещей на выбор: 0 — кремовый по умолчанию, 1 — без фона (цвет экрана). */
val TILE_BACKGROUNDS = listOf(
    0 to "Кремовый", 0xFFFFFFFF.toInt() to "Белый", 0xFFE8E6E1.toInt() to "Светло-серый", 0xFFDCE8F6.toInt() to "Голубой",
    0xFFF6E3E1.toInt() to "Розовый", 0xFFE3EEDD.toInt() to "Мятный", 0xFF2F2A24.toInt() to "Тёмный", 0xFF111111.toInt() to "Чёрный", 1 to "Без фона",
)

object WardrobeLook {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("wardrobe", Context.MODE_PRIVATE)
    /** -1 — ещё не прочитан из настроек. */
    var bg by androidx.compose.runtime.mutableIntStateOf(-1)
    fun load(ctx: Context) { if (bg == -1) bg = sp(ctx).getInt("tile_bg", 0) }
    fun set(ctx: Context, v: Int) { bg = v; sp(ctx).edit().putInt("tile_bg", v).apply() }
    fun color(v: Int): Color = when (v) { 0, -1 -> BoardColor; 1 -> Color.Transparent; else -> Color(v) }
}

/** Цвет фона плашек вещей (выбирается в «Одежде» → сортировка → «Фон плашек»). */
@Composable
fun tileBg(): Color {
    val ctx = LocalContext.current
    WardrobeLook.load(ctx)
    return WardrobeLook.color(WardrobeLook.bg)
}

/** Скругление плашек — как у карточек в остальных разделах. */
@Composable
fun tileShape() = androidx.compose.foundation.shape.RoundedCornerShape(com.dasein.poryadok.ui.theme.LocalLook.current.cardRound.dp)

/** Выбор фона плашек. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TileBgDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val extra = com.dasein.poryadok.ui.theme.LocalExtra.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(btnLabel("Фон плашек")) },
        text = {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
            ) {
                TILE_BACKGROUNDS.forEach { (v, label) ->
                    val on = WardrobeLook.bg == v || (v == 0 && WardrobeLook.bg == -1)
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, modifier = Modifier.clickable { WardrobeLook.set(ctx, v) }) {
                        Box(
                            Modifier.size(52.dp).clip(tileShape()).background(WardrobeLook.color(v))
                                .border(if (on) 2.5.dp else 1.dp, if (on) androidx.compose.material3.MaterialTheme.colorScheme.primary else extra.line, tileShape()),
                        )
                        Text(label, fontSize = 10.sp, color = extra.dim)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(btnLabel("Готово")) } },
    )
}
