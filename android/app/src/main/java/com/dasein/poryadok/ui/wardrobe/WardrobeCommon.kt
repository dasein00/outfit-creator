package com.dasein.poryadok.ui.wardrobe

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.foundation.Canvas
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
