@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.common

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.dasein.poryadok.ui.common.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dasein.poryadok.system.IconSets
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/** Замена любой иконки долгим нажатием на неё. */
object IconEdit {
    /** Ключ иконки, которую сейчас меняют. */
    val target = mutableStateOf<String?>(null)
}

/** Можно ли менять иконку долгим нажатием: в окнах выбора иконки — нельзя. */
val LocalIconEditable = compositionLocalOf { true }

/**
 * Долгое нажатие на иконку открывает замену. Обычное нажатие проходит дальше — кнопка под иконкой работает как раньше.
 */
fun Modifier.iconLongPress(key: String): Modifier = pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        val released = withTimeoutOrNull((viewConfiguration.longPressTimeoutMillis * 0.85).toLong()) {
            while (true) {
                val ev = awaitPointerEvent()
                val c = ev.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                if (!c.pressed) return@withTimeoutOrNull true
                if (abs(c.position.x - down.position.x) > slop || abs(c.position.y - down.position.y) > slop) return@withTimeoutOrNull true
            }
        }
        if (released == null) {
            IconEdit.target.value = key
            // Дальше жест принадлежит замене иконки: кнопка под ней не нажимается.
            while (true) {
                val ev = awaitPointerEvent()
                ev.changes.forEach { it.consume() }
                if (ev.changes.none { it.pressed }) break
            }
        }
    }
}

/** Окно замены иконки и импорт набора. Ставится один раз в корне приложения. */
@Composable
fun IconEditHost() {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val key = IconEdit.target.value ?: return
    var pick by remember(key) { mutableStateOf(false) }
    var import by remember(key) { mutableStateOf(false) }
    var editIcon by remember(key) { mutableStateOf<String?>(null) }
    val over = IconSets.overrides.value[key]
    val shown = over ?: key
    if (!pick && !import && editIcon == null) AlertDialog(
        onDismissRequest = { IconEdit.target.value = null },
        title = { Text(btnLabel("Иконка")) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.runtime.CompositionLocalProvider(LocalIconEditable provides false) {
                        Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(extra.card), contentAlignment = Alignment.Center) { Glyph(key, 40.dp) }
                    }
                    Text(
                        if (over != null) "  Иконка заменена. Новую можно выбрать из базы или вернуть исходную." else "  Заменить эту иконку везде в приложении.",
                        fontSize = 13.sp, color = extra.dim, modifier = Modifier.weight(1f),
                    )
                }
                Button(onClick = { pick = true }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text(btnLabel("Выбрать из базы иконок")) }
                if (IconSets.canEdit(shown)) OutlinedButton(onClick = { editIcon = shown }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text("Редактировать: отразить, повернуть, наклонить")
                }
                OutlinedButton(onClick = { import = true }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text(btnLabel("Добавить новый набор из картинки")) }
                if (over != null) TextButton(onClick = { IconSets.setOverride(ctx, key, null); IconEdit.target.value = null }, modifier = Modifier.fillMaxWidth()) {
                    Text(btnLabel("Вернуть исходную"))
                }
            }
        },
        confirmButton = { TextButton(onClick = { IconEdit.target.value = null }) { Text(btnLabel("Закрыть")) } },
    )
    if (pick) androidx.compose.runtime.CompositionLocalProvider(LocalIconEditable provides false) {
        GlyphPickerDialog(over ?: key, onDismiss = { pick = false }) { picked ->
            IconSets.setOverride(ctx, key, picked)
            IconEdit.target.value = null
        }
    }
    editIcon?.let { k -> IconEditorDialog(k) { editIcon = null } }
    if (import) IconSetImportDialog(onDismiss = { import = false }) { firstKey ->
        // Новый набор добавлен — сразу открываем выбор, чтобы поставить иконку из него.
        import = false
        if (firstKey != null) pick = true
    }
}

/**
 * Новый набор из картинки: приложение нарезает иконки, выравнивает их в квадрат 192×192 с прозрачным фоном
 * и предлагает отметить, какие добавить в базу.
 */
@Composable
fun IconSetImportDialog(onDismiss: () -> Unit, onSaved: (String?) -> Unit) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    var icons by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var chosen by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var busy by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    val folders = remember { IconSets.folders(ctx) }
    var target by remember { mutableStateOf(IconSets.MINE) }
    var message by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            busy = true; message = null
            val r = runCatching { IconSets.slice(ctx, uri) }.getOrDefault(emptyList())
            icons = r; chosen = r.indices.toSet()
            if (r.isEmpty()) message = "Иконки не нашлись. Нужна картинка, где иконки разделены пустым местом (прозрачным или однотонным фоном)."
            busy = false
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 12.dp), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(btnLabel("Новый набор иконок"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(btnLabel("Закрыть")) }
                }
                Text(
                    "Выберите картинку с несколькими иконками (например, сетку из ChatGPT). Приложение само нарежет их, " +
                        "уберёт фон и выровняет по размеру. Отметьте, какие добавить в базу.",
                    fontSize = 13.sp, color = extra.dim,
                )
                Button(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text(if (icons.isEmpty()) "Выбрать картинку" else "Другая картинка") }
                if (busy) Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Text(btnLabel("  Нарезаю…"))
                }
                message?.let { Text(it, color = extra.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
                if (icons.isNotEmpty()) {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Найдено: ${icons.size}, отмечено: ${chosen.size}", fontSize = 13.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { chosen = if (chosen.size == icons.size) emptySet() else icons.indices.toSet() }) {
                            Text(if (chosen.size == icons.size) "Снять все" else "Все")
                        }
                    }
                    // Куда положить: «Мои иконки», любая своя папка или новая.
                    Text(btnLabel("Папка:"), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        folders.forEach { f -> Pill(f.title, target == f.id) { target = f.id } }
                        Pill("+ Новая папка", target == NEW_FOLDER) { target = NEW_FOLDER }
                    }
                    if (target == NEW_FOLDER) TextInput(title, { title = it }, "Название новой папки")
                    Button(
                        onClick = {
                            scope.launch {
                                busy = true
                                val id = IconSets.save(
                                    ctx, title, chosen.sorted().map { icons[it] },
                                    folderId = if (target == NEW_FOLDER) IconSets.MINE else target,
                                    newFolder = if (target == NEW_FOLDER) title.ifBlank { "Новая папка" } else null,
                                )
                                busy = false
                                onSaved(IconSets.keysOf(ctx, id).firstOrNull())
                            }
                        },
                        enabled = !busy && chosen.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp),
                    ) { Text("Добавить в базу (${chosen.size})") }
                    LazyVerticalGrid(GridCells.Adaptive(76.dp), Modifier.weight(1f)) {
                        itemsIndexed(icons) { i, b ->
                            val on = i in chosen
                            Box(
                                Modifier.padding(4.dp).aspectRatio(1f).clip(RoundedCornerShape(14.dp))
                                    .background(com.dasein.poryadok.ui.theme.Palette.Ink2)
                                    .border(if (on) 3.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else extra.line, RoundedCornerShape(14.dp))
                                    .clickable { chosen = if (on) chosen - i else chosen + i },
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(b.asImageBitmap(), null, Modifier.fillMaxSize().padding(10.dp))
                                if (on) Text(btnLabel("✓"), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
                            }
                        }
                    }
                } else Box(Modifier.weight(1f))
            }
        }
    }
}

private const val NEW_FOLDER = "+new"

/** Свои наборы: заголовок и ключи, для вкладок выбора иконки. */
internal fun userSetTabs(ctx: android.content.Context): List<Pair<String, String>> = IconSets.sets(ctx).map { it.title to "u:${it.id}" }

/** Правка иконки (своей или встроенной): отражение, поворот, наклон, размер; шаг назад, сохранить, вернуть как было. */
@Composable
fun IconEditorDialog(key: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    val src = remember(key) { IconSets.original(ctx, key) }
    val saved = remember(key) { IconSets.edit(ctx, key) }
    var e by remember(key) { mutableStateOf(saved) }
    val history = remember(key) { androidx.compose.runtime.mutableStateListOf<IconSets.Edit>() }
    var dragStart by remember { mutableStateOf<IconSets.Edit?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    fun step(n: IconSets.Edit) { if (n != e) { history.add(e); e = n } }
    fun drag(n: IconSets.Edit) { if (dragStart == null) dragStart = e; e = n }
    fun dragEnd() { dragStart?.let { if (it != e) history.add(it) }; dragStart = null }
    val preview = remember(e, src) { src?.let { IconSets.render(it, e).asImageBitmap() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().systemBarsPadding().padding(12.dp), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp).androidxVerticalScroll()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(btnLabel("Правка иконки"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(btnLabel("Закрыть")) }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
                    // Как на тёмной и на светлой теме.
                    Box(Modifier.size(150.dp).clip(RoundedCornerShape(20.dp)).background(com.dasein.poryadok.ui.theme.Palette.Ink2), contentAlignment = Alignment.Center) {
                        preview?.let { Image(it, null, Modifier.size(120.dp)) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { step(e.copy(flipH = !e.flipH)) }, modifier = Modifier.weight(1f)) { Text(btnLabel("⇋ Отразить")) }
                    OutlinedButton(onClick = { step(e.copy(flipV = !e.flipV)) }, modifier = Modifier.weight(1f)) { Text(btnLabel("⇅ Перевернуть")) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    OutlinedButton(onClick = { step(e.copy(angle = norm(e.angle - 90))) }, modifier = Modifier.weight(1f)) { Text(btnLabel("↺ 90°")) }
                    OutlinedButton(onClick = { step(e.copy(angle = norm(e.angle + 90))) }, modifier = Modifier.weight(1f)) { Text(btnLabel("↻ 90°")) }
                }
                Text("Поворот: ${e.angle.toInt()}°", fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                androidx.compose.material3.Slider(e.angle, { drag(e.copy(angle = it)) }, valueRange = -180f..180f, onValueChangeFinished = ::dragEnd)
                Text("Наклон по горизонтали: ${(e.skewX * 100).toInt()}", fontSize = 13.sp)
                androidx.compose.material3.Slider(e.skewX, { drag(e.copy(skewX = it)) }, valueRange = -0.6f..0.6f, onValueChangeFinished = ::dragEnd)
                Text("Наклон по вертикали: ${(e.skewY * 100).toInt()}", fontSize = 13.sp)
                androidx.compose.material3.Slider(e.skewY, { drag(e.copy(skewY = it)) }, valueRange = -0.6f..0.6f, onValueChangeFinished = ::dragEnd)
                Text("Размер: ${(e.scale * 100).toInt()}%", fontSize = 13.sp)
                androidx.compose.material3.Slider(e.scale, { drag(e.copy(scale = it)) }, valueRange = 0.5f..1.5f, onValueChangeFinished = ::dragEnd)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { e = history.removeAt(history.lastIndex) }, enabled = history.isNotEmpty(), modifier = Modifier.weight(1f)) { Text(btnLabel("Шаг назад")) }
                    OutlinedButton(onClick = { step(IconSets.Edit()) }, enabled = !e.isIdentity, modifier = Modifier.weight(1f)) { Text(btnLabel("Как в оригинале")) }
                }
                Button(
                    onClick = { scope.launch { IconSets.saveEdit(ctx, key, e); onDismiss() } },
                    enabled = e != saved, modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                ) { Text(btnLabel("Сохранить")) }
                if (!saved.isIdentity) TextButton(onClick = { scope.launch { IconSets.revert(ctx, key); onDismiss() } }, modifier = Modifier.fillMaxWidth()) {
                    Text(btnLabel("Вернуть как было (до всех правок)"))
                }
                if (!IconSets.isBuiltin(key)) TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text(btnLabel("Удалить иконку из набора"), color = extra.danger) }
                if (IconSets.isBuiltin(key)) Text("Это встроенная иконка: правка применится везде, где она стоит в приложении.", fontSize = 12.sp, color = extra.dim)
                Text("Правки всегда считаются от исходной картинки — качество не теряется, вернуть можно в любой момент.", fontSize = 12.sp, color = extra.dim)
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(btnLabel("Удалить иконку?")) },
        text = { Text("Там, где она стоит, вернётся исходная иконка.") },
        confirmButton = {
            TextButton(onClick = {
                IconSets.overrides.value.filterValues { it == key }.keys.forEach { IconSets.setOverride(ctx, it, null) }
                IconSets.deleteIcon(ctx, key); confirmDelete = false; onDismiss()
            }) { Text(btnLabel("Удалить"), color = extra.danger) }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(btnLabel("Отмена")) } },
    )
}

private fun norm(a: Float): Float { var x = a; while (x > 180f) x -= 360f; while (x < -180f) x += 360f; return x }

@Composable
private fun Modifier.androidxVerticalScroll(): Modifier = this.verticalScroll(androidx.compose.foundation.rememberScrollState())
