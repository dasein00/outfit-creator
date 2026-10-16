package com.dasein.poryadok.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.view.WindowManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.dasein.poryadok.system.ReaderBook
import com.dasein.poryadok.system.ReaderMark
import com.dasein.poryadok.system.ReaderSettings
import com.dasein.poryadok.system.ReaderStore
import com.dasein.poryadok.ui.common.ButtonsRow
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.HGap
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.TextButton
import com.dasein.poryadok.ui.common.btnLabel
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Тема страницы читалки. */
data class PageTheme(val name: String, val bg: Color, val fg: Color, val dim: Color, val link: Color, val dark: Boolean)

@Composable
fun pageTheme(i: Int): PageTheme {
    val app = MaterialTheme.colorScheme
    return when (i) {
        0 -> PageTheme("День", Color(0xFFFFFFFF), Color(0xFF1E1E1E), Color(0xFF8A8A8A), Color(0xFFB0611E), false)
        2 -> PageTheme("Ночь", Color(0xFF1C1B1A), Color(0xFFC9C3B8), Color(0xFF7D776E), Color(0xFFE0A15E), true)
        3 -> PageTheme("Чёрная", Color(0xFF000000), Color(0xFFA8A8A8), Color(0xFF666666), Color(0xFFD08A45), true)
        4 -> PageTheme("Как в приложении", app.background, app.onBackground, LocalExtra.current.dim, app.primary, app.background.luminance() < .4f)
        5 -> PageTheme("Серая", Color(0xFFE6E6E3), Color(0xFF2A2A2A), Color(0xFF808080), Color(0xFF9C5A1F), false)
        6 -> PageTheme("Мята", Color(0xFFDDEEDF), Color(0xFF203024), Color(0xFF6F8573), Color(0xFF2E7D57), false)
        else -> PageTheme("Сепия", Color(0xFFF4ECD8), Color(0xFF5B4636), Color(0xFF9C8B75), Color(0xFFA5541A), false)
    }
}
val THEME_ORDER = listOf(0, 1, 5, 6, 2, 3, 4)

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/** Шрифты читалки (системные шрифты Android). */
val FONTS = listOf(
    "С засечками" to "serif", "Без засечек" to "sans-serif", "Узкий" to "sans-serif-condensed",
    "Моноширинный" to "monospace", "Рукописный" to "cursive", "Noto Serif" to "'Noto Serif', serif",
)
fun fontFamilyCompose(i: Int): FontFamily = when (i) { 1, 2 -> FontFamily.SansSerif; 3 -> FontFamily.Monospace; 4 -> FontFamily.Cursive; else -> FontFamily.Serif }

/** Цвета выделений: жёлтый, зелёный, синий, розовый, подчёркивание. */
val MARK_COLORS = listOf(Color(0xFFFFD54F), Color(0xFF81C784), Color(0xFF64B5F6), Color(0xFFF06292), Color(0xFFE8913A))

fun hex(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)

tailrec fun Context.activity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }

/** Кнопки громкости листают страницы, пока открыта книга. */
object ReaderKeys {
    @Volatile var handler: ((Int) -> Boolean)? = null
}

/** Полный экран, «не гаснуть» и яркость — пока открыта книга. */
@Composable
fun ReaderWindow(s: ReaderSettings, immersive: Boolean) {
    val ctx = LocalContext.current
    val act = ctx.activity() ?: return
    DisposableEffect(s.keepScreenOn) {
        if (s.keepScreenOn) act.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { act.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    DisposableEffect(s.brightness) {
        val lp = act.window.attributes
        lp.screenBrightness = if (s.brightness < 0) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else s.brightness.coerceIn(0.01f, 1f)
        act.window.attributes = lp
        onDispose {
            val l = act.window.attributes
            l.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            act.window.attributes = l
        }
    }
    val hide = immersive && s.fullscreen
    DisposableEffect(hide) {
        val c = WindowCompat.getInsetsController(act.window, act.window.decorView)
        if (hide) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else c.show(WindowInsetsCompat.Type.systemBars())
        onDispose { c.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

/** Время чтения: каждые 30 секунд, если за последние 3 минуты листали страницы. */
@Composable
fun ReadingTimer(bookId: Long, lastAction: () -> Long) {
    val ctx = LocalContext.current
    LaunchedEffect(bookId) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            if (System.currentTimeMillis() - lastAction() < 180_000) ReaderStore.addTime(ctx, bookId, 30)
        }
    }
}

fun shareText(ctx: Context, text: String) {
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Поделиться"))
}

fun quote(b: ReaderBook, text: String) = "«${text.trim()}»\n— ${listOf(b.author, b.title).filter { it.isNotBlank() }.joinToString(", ")}"

fun copyText(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    cm.setPrimaryClip(android.content.ClipData.newPlainText("Цитата", text))
    android.widget.Toast.makeText(ctx, "Скопировано", android.widget.Toast.LENGTH_SHORT).show()
}

/** Перевод выделенного: модель на телефоне (английский ↔ русский), иначе бесплатный ИИ, если подключён. */
suspend fun translateText(ctx: Context, text: String): String = withContext(Dispatchers.IO) {
    val latin = text.count { it in 'a'..'z' || it in 'A'..'Z' } > text.count { it in 'а'..'я' || it in 'А'..'Я' }
    val offline = com.dasein.poryadok.system.OfflineTranslator
    if (offline.ready()) offline.translate(text, enToRu = latin).getOrNull()?.let { return@withContext it }
    if (com.dasein.poryadok.system.Ai.enabled(ctx)) runCatching {
        return@withContext com.dasein.poryadok.system.Ai.ask(
            ctx, "Ты переводчик. Переведи текст ${if (latin) "на русский" else "на английский"}. Если это одно слово — дай 2–3 значения и пример. Только перевод, без пояснений о себе.", text,
        ).trim()
    }
    "Перевод недоступен: скачайте модель в «Переводчике» (работает без интернета) или подключите бесплатный ИИ в настройках."
}

@Composable
fun TranslateDialog(text: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var result by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(text) { result = translateText(ctx, text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Перевод") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(text, fontSize = 13.sp, color = LocalExtra.current.dim, maxLines = 6, overflow = TextOverflow.Ellipsis)
                Gap(10.dp)
                Text(result ?: "Перевожу…", fontSize = 16.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(btnLabel("Готово")) } },
        dismissButton = { result?.let { r -> TextButton(onClick = { copyText(ctx, r) }) { Text(btnLabel("Копировать")) } } },
    )
}

/** Выделение или закладка: цвет, заметка, копировать, поделиться, удалить. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MarkDialog(book: ReaderBook, mark: ReaderMark, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    var note by remember { mutableStateOf(mark.note) }
    var color by remember { mutableStateOf(mark.color) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(when (mark.kind) { 0 -> "Закладка"; 2 -> "Заметка к странице"; else -> "Выделение" }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (mark.text.isNotBlank()) Text("«${mark.text.take(600)}»", fontSize = 14.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = extra.dim)
                if (mark.kind == 1) Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MARK_COLORS.forEachIndexed { i, c ->
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(if (i == 4) Color.Transparent else c.copy(alpha = .8f))
                                .border(if (color == i) 3.dp else 1.dp, if (color == i) MaterialTheme.colorScheme.onSurface else c, CircleShape)
                                .clickable { color = i },
                            contentAlignment = Alignment.Center,
                        ) { if (i == 4) Text("U", color = c, fontWeight = FontWeight.Bold) }
                    }
                }
                OutlinedTextField(note, { note = it }, label = { Text("Заметка") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { io { ReaderStore.saveMark(ctx, mark.copy(note = note.trim(), color = color)); kotlinx.coroutines.withContext(Dispatchers.Main) { onChanged() } }; onDismiss() }) { Text(btnLabel("Сохранить")) }
        },
        dismissButton = {
            ButtonsRow {
                TextButton(onClick = { io { ReaderStore.deleteMark(ctx, mark.id); kotlinx.coroutines.withContext(Dispatchers.Main) { onChanged() } }; onDismiss() }) { Text(btnLabel("Удалить"), color = extra.danger) }
                if (mark.text.isNotBlank()) TextButton(onClick = { shareText(ctx, quote(book, mark.text) + if (note.isNotBlank()) "\n\n$note" else "") }) { Text(btnLabel("Поделиться")) }
            }
        },
    )
}

/** Оглавление, закладки и заметки книги в одном окне. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContentsDialog(
    book: ReaderBook,
    toc: List<Triple<String, Int, Int>>, // название, уровень, ключ перехода
    currentKey: Int,
    marks: List<ReaderMark>,
    markLabel: (ReaderMark) -> String,
    onToc: (Int) -> Unit,
    onMark: (ReaderMark) -> Unit,
    onEditMark: (ReaderMark) -> Unit,
    onDismiss: () -> Unit,
    initialTab: Int = 0,
) {
    val extra = LocalExtra.current
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf(initialTab) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth(.94f).heightIn(max = 640.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp)) {
            Text(book.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("Оглавление", tab == 0) { tab = 0 }
                Pill("Закладки · ${marks.count { it.kind == 0 }}", tab == 1) { tab = 1 }
                Pill("Заметки · ${marks.count { it.kind != 0 }}", tab == 2) { tab = 2 }
            }
            LazyColumn(Modifier.weight(1f, fill = false)) {
                when (tab) {
                    0 -> {
                        if (toc.isEmpty()) item { Text("В книге нет оглавления.", color = extra.dim, modifier = Modifier.padding(8.dp)) }
                        items(toc) { (t, level, key) ->
                            val here = key == currentKey
                            Text(
                                t, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                fontWeight = if (here) FontWeight.Bold else if (level == 0) FontWeight.Medium else FontWeight.Normal,
                                color = if (here) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontSize = if (level == 0) 15.sp else 14.sp,
                                modifier = Modifier.fillMaxWidth().clickable { onToc(key) }.padding(start = (level * 16).dp + 4.dp, top = 9.dp, bottom = 9.dp),
                            )
                        }
                    }
                    else -> {
                        val list = marks.filter { if (tab == 1) it.kind == 0 else it.kind != 0 }.sortedWith(compareBy({ it.chapter }, { it.start }))
                        if (list.isEmpty()) item {
                            Text(
                                if (tab == 1) "Закладок пока нет. Нажмите значок закладки в меню страницы." else "Выделите текст долгим нажатием и выберите «Выделить» или «Заметка».",
                                color = extra.dim, modifier = Modifier.padding(8.dp),
                            )
                        }
                        items(list, key = { it.id }) { m ->
                            Row(
                                Modifier.fillMaxWidth().combinedClickable(onClick = { onMark(m) }, onLongClick = { onEditMark(m) }).padding(vertical = 8.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                if (m.kind == 1) Box(Modifier.padding(top = 4.dp).size(10.dp).clip(CircleShape).background(MARK_COLORS[m.color.coerceIn(0, 4)]))
                                else Glyph(if (m.kind == 0) "d17/10" else "d18/00", 18.dp, badge = false)
                                HGap(10.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(markLabel(m), fontSize = 11.sp, color = extra.dim)
                                    if (m.text.isNotBlank()) Text(m.text, fontSize = 14.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                                    if (m.note.isNotBlank()) Text("✎ ${m.note}", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, maxLines = 4, overflow = TextOverflow.Ellipsis)
                                }
                                TextButton(onClick = { onEditMark(m) }) { Text("⋮") }
                            }
                        }
                        if (tab == 2 && list.isNotEmpty()) item {
                            TextButton(onClick = { shareText(ctx, exportNotes(book, list)) }) { Text(btnLabel("Выгрузить все заметки текстом")) }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = onDismiss) { Text(btnLabel("Закрыть")) } }
        }
    }
}

fun exportNotes(b: ReaderBook, marks: List<ReaderMark>): String = buildString {
    append(b.title); if (b.author.isNotBlank()) append(" — ").append(b.author); append("\n\n")
    marks.sortedWith(compareBy({ it.chapter }, { it.start })).forEach { m ->
        append("• ${(m.pct * 100).toInt()}%  ")
        if (m.text.isNotBlank()) append("«${m.text.trim()}»")
        if (m.note.isNotBlank()) append("\n   ✎ ${m.note}")
        append("\n\n")
    }
}

/** Настройки вида: шрифт, размер, интервалы, поля, тема, страницы или прокрутка, яркость. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReaderSettingsPanel(s: ReaderSettings, paged: Boolean, onChange: (ReaderSettings) -> Unit) {
    val extra = LocalExtra.current
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (!paged) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Размер", Modifier.width(92.dp), fontSize = 14.sp)
                StepBtn("A−") { onChange(s.copy(fontSize = (s.fontSize - 1).coerceAtLeast(10))) }
                Text("${s.fontSize}", Modifier.width(44.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.SemiBold)
                StepBtn("A+") { onChange(s.copy(fontSize = (s.fontSize + 1).coerceAtMost(48))) }
            }
            Gap(6.dp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FONTS.forEachIndexed { i, (n, _) -> Pill(n, s.font == i) { onChange(s.copy(font = i)) } }
            }
            Gap(8.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Интервал", Modifier.width(92.dp), fontSize = 14.sp)
                listOf(1.2f, 1.35f, 1.5f, 1.7f, 1.9f).forEach { v -> Pill("%.2f".format(v).trimEnd('0').trimEnd(',', '.'), kotlin.math.abs(s.lineHeight - v) < .01f) { onChange(s.copy(lineHeight = v)) }; Spacer(Modifier.width(4.dp)) }
            }
            Gap(6.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Поля", Modifier.width(92.dp), fontSize = 14.sp)
                listOf(8, 14, 20, 28, 40).forEachIndexed { i, v -> Pill(listOf("XS", "S", "M", "L", "XL")[i], s.margin == v) { onChange(s.copy(margin = v)) }; Spacer(Modifier.width(4.dp)) }
            }
            Gap(6.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Абзацы", Modifier.width(92.dp), fontSize = 14.sp)
                listOf(0f, .35f, .8f).forEachIndexed { i, v -> Pill(listOf("слитно", "немного", "отступ")[i], kotlin.math.abs(s.paraSpace - v) < .01f) { onChange(s.copy(paraSpace = v)) }; Spacer(Modifier.width(4.dp)) }
            }
        }
        Gap(10.dp)
        Text("Тема", fontSize = 14.sp)
        Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            THEME_ORDER.forEach { i ->
                val t = pageTheme(i)
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(t.bg).border(if (s.theme == i) 3.dp else 1.dp, if (s.theme == i) MaterialTheme.colorScheme.primary else extra.dim, CircleShape).clickable { onChange(s.copy(theme = i)) },
                    contentAlignment = Alignment.Center,
                ) { Text("Аа", color = t.fg, fontSize = 13.sp, fontFamily = FontFamily.Serif) }
            }
        }
        if (paged) {
            Text("PDF и DjVu", fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                Pill("Листать", s.pdfMode == 0) { onChange(s.copy(pdfMode = 0)) }
                Pill("Лентой", s.pdfMode == 1) { onChange(s.copy(pdfMode = 1)) }
                Pill("Обрезать поля", s.pdfCrop) { onChange(s.copy(pdfCrop = !s.pdfCrop)) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Как есть", "Ночь (инверсия)", "Сепия").forEachIndexed { i, n -> Pill(n, s.pdfColor == i) { onChange(s.copy(pdfColor = i)) } }
            }
        }
        Gap(10.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Яркость", Modifier.width(92.dp), fontSize = 14.sp)
            var b by remember(s.brightness) { mutableFloatStateOf(if (s.brightness < 0) .5f else s.brightness) }
            Slider(b, { b = it }, Modifier.weight(1f), onValueChangeFinished = { onChange(s.copy(brightness = b)) }, enabled = s.brightness >= 0)
            Pill("Авто", s.brightness < 0) { onChange(s.copy(brightness = if (s.brightness < 0) .5f else -1f)) }
        }
        if (!paged) {
            Toggle("Страницы (выкл. — прокрутка)", s.paged) { onChange(s.copy(paged = it)) }
            Toggle("По ширине", s.justify) { onChange(s.copy(justify = it)) }
            Toggle("Переносы слов", s.hyphens) { onChange(s.copy(hyphens = it)) }
            Toggle("Красная строка", s.indent) { onChange(s.copy(indent = it)) }
            Toggle("Анимация листания", s.animation) { onChange(s.copy(animation = it)) }
        }
        Toggle("Листать кнопками громкости", s.volumeKeys) { onChange(s.copy(volumeKeys = it)) }
        Toggle("На весь экран", s.fullscreen) { onChange(s.copy(fullscreen = it)) }
        Toggle("Не гасить экран", s.keepScreenOn) { onChange(s.copy(keepScreenOn = it)) }
        Toggle("Часы и заряд внизу", s.showClock) { onChange(s.copy(showClock = it)) }
        Gap(12.dp)
    }
}

@Composable
private fun StepBtn(t: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(LocalExtra.current.card).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(t, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Toggle(t: String, v: Boolean, on: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { on(!v) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(t, Modifier.weight(1f), fontSize = 14.sp)
        Switch(v, on)
    }
}

/** Картинка во весь экран: щипок — увеличить, двойное нажатие — приблизить или вернуть. */
@Composable
fun ImageViewer(bmp: ImageBitmap, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { z, pan, _ -> scale = (scale * z).coerceIn(1f, 6f); offset = if (scale <= 1f) Offset.Zero else offset + pan }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
            detectTapGestures(onDoubleTap = { if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f }, onTap = { if (scale <= 1f) onDismiss() })
        }.transformable(state), contentAlignment = Alignment.Center) {
            Image(bmp, null, Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y), contentScale = ContentScale.Fit)
        }
    }
}

/** Переход к странице по номеру. */
@Composable
fun GoToDialog(max: Int, current: Int, label: String = "Страница", onDismiss: () -> Unit, onGo: (Int) -> Unit) {
    var v by remember { mutableStateOf("$current") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Перейти") },
        text = {
            OutlinedTextField(v, { v = it.filter(Char::isDigit).take(6) }, label = { Text("$label (1–$max)") }, singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
        },
        confirmButton = { TextButton(onClick = { v.toIntOrNull()?.let { onGo(it.coerceIn(1, max)) }; onDismiss() }) { Text(btnLabel("Перейти")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) } },
    )
}

/** Строка состояния внизу страницы: где мы, сколько прочитано, часы. */
@Composable
fun StatusLine(left: String, right: String, color: Color, showClock: Boolean, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var clock by remember { mutableStateOf("") }
    var battery by remember { mutableStateOf("") }
    LaunchedEffect(showClock) {
        while (showClock) {
            clock = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
            battery = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }?.let { "$it%" }.orEmpty()
            kotlinx.coroutines.delay(30_000)
        }
    }
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(left, fontSize = 11.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (showClock && clock.isNotEmpty()) Text("$clock  $battery", fontSize = 11.sp, color = color, modifier = Modifier.padding(horizontal = 10.dp))
        Text(right, fontSize = 11.sp, color = color)
    }
}
