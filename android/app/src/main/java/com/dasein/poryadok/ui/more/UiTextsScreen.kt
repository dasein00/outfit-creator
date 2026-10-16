package com.dasein.poryadok.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text as RawText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.data.UiTextEngine
import com.dasein.poryadok.data.UiTexts
import com.dasein.poryadok.ui.common.ConfirmDialog
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.Text
import com.dasein.poryadok.ui.common.TextButton
import com.dasein.poryadok.ui.theme.LocalExtra

const val UI_TEXTS_ROUTE = "uiTexts"

private enum class TextFilter(val title: String) { ALL("Все"), CHANGED("Изменённые"), MINE("Мои правки"), SAME("Без изменений"), SUGGESTED("Есть предложение") }

/** Текст с экрана, которого нет в каталоге (динамический или составной), — правится как есть. */
private const val SCREEN_SECTION = "Правки с экрана"
private fun screenEntry(raw: String) = UiTextEngine.Entry(-(raw.hashCode().and(0x7fffffff)) - 1, SCREEN_SECTION, "с экрана", false, raw, "", "")

/**
 * Окно режима редактора: удержали текст 3 секунды — правим его. Если текст собран из нескольких строк каталога,
 * сначала выбираем, какую часть менять. Правка сразу видна в «Текстах интерфейса».
 */
@Composable
fun TextEditHost() {
    val raw = UiTexts.editTarget.value ?: return
    val engine = remember(UiTexts.version.intValue) { UiTexts.engine() }
    val found = remember(raw, engine) { engine.find(raw) }
    var pick by remember(raw) { mutableStateOf(found.singleOrNull() ?: if (found.isEmpty()) screenEntry(raw) else null) }
    val close = { UiTexts.editTarget.value = null }
    androidx.compose.runtime.CompositionLocalProvider(com.dasein.poryadok.ui.common.LocalIconEditable provides false) {
        val p = pick
        if (p != null) EditTextDialog(p, engine, close)
        else AlertDialog(
            onDismissRequest = close,
            title = { Text("Какую часть изменить?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    RawText(shownLine(raw), fontSize = 13.sp, color = LocalExtra.current.dim, modifier = Modifier.padding(bottom = 8.dp))
                    (found + screenEntry(raw)).forEach { e ->
                        Variant(if (e.section == SCREEN_SECTION) "Весь текст целиком" else e.section, engine.effective(e)) { pick = e }
                    }
                }
            },
            confirmButton = { TextButton(onClick = close) { Text("Отмена") } },
        )
    }
}

private fun norm(s: String) = s.lowercase().replace('ё', 'е')

/**
 * «Тексты интерфейса»: все надписи приложения — кнопки, плашки, заголовки, подсказки. Поиск по всему приложению сразу,
 * разделы и фильтры; нажатие на текст — свой вариант, предложение дизайнера или как было в коде.
 * Сами строки показываются без подмены (RawText), иначе в списке было бы не видно, что во что превращается.
 */
@Composable
fun UiTextsScreen(nav: NavHostController) {
    val extra = LocalExtra.current
    val ver = UiTexts.version.intValue
    val engine = remember(ver) { UiTexts.engine() }
    val all = remember(engine) { engine.entries + engine.extraEdits.keys.map { screenEntry(it) } }
    val sections = remember(all) { all.map { it.section }.distinct() }
    var query by rememberSaveable { mutableStateOf("") }
    var section by rememberSaveable { mutableStateOf<String?>(null) }
    var filter by rememberSaveable { mutableStateOf(TextFilter.ALL) }
    var edit by remember { mutableStateOf<UiTextEngine.Entry?>(null) }
    var askReset by remember { mutableStateOf(false) }
    val mineCount = remember(engine) { all.count { engine.userValue(it) != null } }

    val words = remember(query) { norm(query).split(' ', ',').filter { it.isNotBlank() } }
    val found = remember(engine, words, filter) {
        all.filter { e ->
            val v = engine.effective(e)
            val pass = when (filter) {
                TextFilter.ALL -> true
                TextFilter.CHANGED -> v != e.orig
                TextFilter.MINE -> engine.userValue(e) != null
                TextFilter.SAME -> v == e.orig
                TextFilter.SUGGESTED -> e.suggestion.isNotEmpty() && e.suggestion != v
            }
            pass && (words.isEmpty() || norm(e.orig + "\n" + v + "\n" + e.suggestion + "\n" + e.final + "\n" + e.ref).let { t -> words.all { it in t } })
        }
    }
    val counts = remember(found) { found.groupingBy { it.section }.eachCount() }
    val shown = remember(found, section) { if (section == null) found else found.filter { it.section == section } }
    val list = rememberLazyListState()
    LaunchedEffect(section, filter, words) { list.scrollToItem(0) }

    Screen("Тексты интерфейса", onBack = { nav.popBackStack() }, actions = {
        if (mineCount > 0) IconButton(onClick = { askReset = true }) { Icon(Icons.Default.Refresh, "Сбросить мои правки") }
    }) { pad ->
        LazyColumn(Modifier.padding(pad), state = list) {
            item(key = "head") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        "Все надписи приложения: кнопки, плашки, заголовки и подсказки. Нажмите на текст, чтобы изменить его, — новый вариант появится везде, где стоит эта надпись.",
                        fontSize = 13.sp, color = extra.dim, lineHeight = 18.sp,
                    )
                    OutlinedTextField(
                        query, { query = it }, Modifier.fillMaxWidth().padding(top = 10.dp), singleLine = true,
                        placeholder = { Text("Найти текст во всём приложении") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, "Очистить") } },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        shape = RoundedCornerShape(14.dp),
                    )
                }
            }
            item(key = "sections") {
                LazyRow(
                    Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                ) {
                    item { Pill("Все разделы · ${found.size}", section == null) { section = null } }
                    items(sections) { s -> Pill("$s · ${counts[s] ?: 0}", section == s) { section = if (section == s) null else s } }
                }
            }
            item(key = "filters") {
                LazyRow(
                    Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                ) {
                    items(TextFilter.entries.toList()) { f ->
                        Pill(if (f == TextFilter.MINE) "${f.title} · $mineCount" else f.title, filter == f) { filter = f }
                    }
                }
            }
            item(key = "count") {
                Text(
                    if (shown.isEmpty()) "Ничего не найдено. Измените запрос, раздел или фильтр." else "Найдено: ${shown.size}",
                    fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
                )
            }
            var last: String? = null
            for (e in shown) {
                if (section == null && e.section != last) {
                    val s = e.section
                    item(key = "h:$s") {
                        RawText(
                            s, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
                        )
                    }
                    last = s
                }
                item(key = e.id) { TextRow(e, engine) { edit = e } }
            }
            item(key = "end") { androidx.compose.foundation.layout.Spacer(Modifier.heightIn(min = 40.dp)) }
        }
    }

    edit?.let { e -> EditTextDialog(e, engine) { edit = null } }
    if (askReset) ConfirmDialog(
        "Сбросить мои правки?", "Все тексты, изменённые здесь или удержанием кнопок ($mineCount), вернутся к стандартному варианту.",
        confirm = "Сбросить", onDismiss = { askReset = false },
    ) { UiTexts.resetAll(); askReset = false }
}

/** Как показывать текст в списке: переносы строк — значком, чтобы строка оставалась строкой. */
private fun shownLine(s: String) = s.replace("\n", " ⏎ ").ifBlank { "(пусто)" }

@Composable
private fun TextRow(e: UiTextEngine.Entry, engine: UiTextEngine, onClick: () -> Unit) {
    val extra = LocalExtra.current
    val v = engine.effective(e)
    val mine = engine.userValue(e) != null
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 3.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(extra.card).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        RawText(shownLine(v), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
        if (v != e.orig) RawText("Было: " + shownLine(e.orig), fontSize = 12.sp, color = extra.dim, lineHeight = 16.sp)
        if (e.suggestion.isNotEmpty() && e.suggestion != v)
            RawText("Предложение: " + shownLine(e.suggestion), fontSize = 12.sp, color = extra.dim, fontStyle = FontStyle.Italic, lineHeight = 16.sp)
        Row(Modifier.padding(top = 2.dp)) {
            RawText(
                e.ref + when {
                    mine -> " · моя правка"
                    e.final.isNotEmpty() -> " · из документа"
                    else -> ""
                },
                fontSize = 11.sp, color = if (mine) MaterialTheme.colorScheme.primary else extra.dim,
            )
        }
    }
}

@Composable
internal fun EditTextDialog(e: UiTextEngine.Entry, engine: UiTextEngine, onClose: () -> Unit) {
    val extra = LocalExtra.current
    var value by remember(e) { mutableStateOf(engine.effective(e)) }
    val lead = e.orig.takeWhile { it.isWhitespace() }
    val trail = e.orig.takeLastWhile { it.isWhitespace() }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Текст интерфейса") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Нажмите на вариант, чтобы взять его, или напишите свой.", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(bottom = 6.dp))
                Variant(if (e.section == SCREEN_SECTION) "Текст на экране" else "Как в коде", e.orig) { value = e.orig }
                if (e.suggestion.isNotEmpty()) Variant("Предложение дизайнера", e.suggestion) { value = e.suggestion }
                if (e.final.isNotEmpty()) Variant("Ваш вариант из документа", e.final) { value = e.final }
                OutlinedTextField(
                    value, { value = it }, Modifier.fillMaxWidth().padding(top = 8.dp),
                    label = { Text("Как будет в приложении") }, shape = RoundedCornerShape(14.dp),
                )
                if (e.hasSlots) Text(
                    "{1}, {2}… — сюда приложение само подставит числа и названия. Их можно переставить или убрать.",
                    fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 6.dp),
                )
                if (e.fragment) Text(
                    "Это часть составной фразы — заменяется внутри строки, пробелы по краям сохранятся.",
                    fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp),
                )
                if (e.section == SCREEN_SECTION) Text(
                    "Этого текста нет в общем списке (он собирается из чисел и слов) — правка сработает, когда на экране будет ровно такой текст.",
                    fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp),
                )
                Text(e.section + " · " + e.ref, fontSize = 11.sp, color = extra.dim, modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                var v = value
                if (lead.isNotEmpty() && !v.startsWith(lead)) v = lead + v.trimStart()
                if (trail.isNotEmpty() && !v.endsWith(trail)) v = v.trimEnd() + trail
                // Пустое поле или вариант по умолчанию — своя правка не нужна.
                UiTexts.set(e.orig, if (v.isBlank() || v == e.default) null else v)
                onClose()
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Отмена") } },
    )
}

@Composable
private fun Variant(title: String, text: String, onTake: () -> Unit) {
    val extra = LocalExtra.current
    Column(
        Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(10.dp)).background(extra.card)
            .clickable(onClick = onTake).padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(title, fontSize = 11.sp, color = extra.dim)
        RawText(shownLine(text), fontSize = 14.sp, lineHeight = 19.sp)
    }
}
