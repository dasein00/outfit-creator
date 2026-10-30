package com.dasein.poryadok.ui.wardrobe

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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

/** «Переместить вещи»: сетка, где вещь удерживают и перетаскивают на новое место; у края экрана список сам прокручивается. */
@Composable
fun ReorderGrid(items: List<WardrobeItem>, onCancel: () -> Unit, onDone: (List<String>) -> Unit) {
    val extra = com.dasein.poryadok.ui.theme.LocalExtra.current
    val order = remember { androidx.compose.runtime.mutableStateListOf(*items.map { it.id }.toTypedArray()) }
    val byId = remember(items) { items.associateBy { it.id } }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragPos by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val scroll = androidx.compose.foundation.rememberScrollState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text("Удерживайте вещь и тяните на новое место", fontSize = 12.sp, color = extra.dim, modifier = Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text(btnLabel("Отмена")) }
            TextButton(onClick = { onDone(order.toList()) }) { Text(btnLabel("Готово"), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) }
        }
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp)) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val cols = 3
            val cw = with(density) { maxWidth.toPx() } / cols
            val ch = cw * 1.15f
            val viewport = with(density) { maxHeight.toPx() }
            val rows = (order.size + cols - 1) / cols
            fun slot(i: Int) = androidx.compose.ui.geometry.Offset((i % cols) * cw, (i / cols) * ch)
            Box(Modifier.fillMaxWidth().verticalScroll(scroll, enabled = dragging == null)) {
                Box(Modifier.fillMaxWidth().height(with(density) { (rows * ch + ch * .5f).toDp() })) {
                    order.forEachIndexed { i, id ->
                        androidx.compose.runtime.key(id) {
                            val it0 = byId[id] ?: return@key
                            val isDrag = dragging == id
                            val target = slot(i)
                            val anim by androidx.compose.animation.core.animateOffsetAsState(target, label = "slot")
                            val pos = if (isDrag) dragPos else anim
                            Box(
                                Modifier
                                    .offset { androidx.compose.ui.unit.IntOffset(pos.x.toInt(), pos.y.toInt()) }
                                    .size(with(density) { cw.toDp() }, with(density) { ch.toDp() })
                                    .zIndex(if (isDrag) 1f else 0f)
                                    .graphicsLayer { scaleX = if (isDrag) 1.08f else 1f; scaleY = scaleX; shadowElevation = if (isDrag) 16f else 0f }
                                    .padding(4.dp)
                                    .pointerInput(id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { dragging = id; dragPos = slot(order.indexOf(id)) },
                                            onDragEnd = { dragging = null },
                                            onDragCancel = { dragging = null },
                                            onDrag = { ch0, amount ->
                                                ch0.consume()
                                                dragPos += amount
                                                // Автопрокрутка у верхнего и нижнего края.
                                                val vy = dragPos.y - scroll.value
                                                val step = when {
                                                    vy > viewport - ch * .9f -> 24f
                                                    vy < ch * .2f && scroll.value > 0 -> -24f
                                                    else -> 0f
                                                }
                                                if (step != 0f) {
                                                    scope.launch { scroll.scrollBy(step) }
                                                    dragPos += androidx.compose.ui.geometry.Offset(0f, step)
                                                }
                                                val cx = (dragPos.x + cw / 2).coerceIn(0f, cw * cols - 1)
                                                val cy = (dragPos.y + ch / 2).coerceAtLeast(0f)
                                                val to = ((cy / ch).toInt() * cols + (cx / cw).toInt()).coerceIn(0, order.size - 1)
                                                val from = order.indexOf(id)
                                                if (to != from && from >= 0) { order.removeAt(from); order.add(to, id) }
                                            },
                                        )
                                    }
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                                    .background(BoardColor),
                            ) { ItemThumb(it0, Modifier.fillMaxSize().padding(8.dp), shadow = false) }
                        }
                    }
                }
            }
        }
    }
}
