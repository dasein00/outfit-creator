@file:OptIn(ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dasein.poryadok.logic.Paging
import com.dasein.poryadok.logic.TextQuery
import com.dasein.poryadok.ui.theme.LocalExtra

/** Размеры страницы, из которых можно выбрать. */
val PAGE_SIZES = listOf(10, 20, 50, 100)

/**
 * Полоса страниц: ‹ 1 … 4 [5] 6 … 120 › и кнопка «Стр.» — перейти на страницу по номеру.
 */
@Composable
fun PageBar(page: Int, pages: Int, onPage: (Int) -> Unit, modifier: Modifier = Modifier) {
    if (pages <= 1) return
    val scheme = MaterialTheme.colorScheme
    val extra = LocalExtra.current
    var ask by remember { mutableStateOf(false) }
    if (ask) GoToPageDialog(page, pages, { ask = false }) { onPage(it); ask = false }
    FlowRow(
        modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PageCell("‹", false, enabled = page > 0) { onPage(page - 1) }
        Paging.window(page, pages).forEach { n ->
            if (n < 0) Text("…", Modifier.padding(horizontal = 4.dp, vertical = 8.dp), color = extra.dim)
            else PageCell("${n + 1}", n == page) { onPage(n) }
        }
        PageCell("›", false, enabled = page < pages - 1) { onPage(page + 1) }
        Surface(
            onClick = { ask = true }, shape = RoundedCornerShape(10.dp), color = Color.Transparent,
            border = BorderStroke(1.dp, scheme.primary),
        ) {
            Text("Стр. №", Modifier.padding(horizontal = 10.dp, vertical = 8.dp), fontSize = 13.sp, color = scheme.primary)
        }
    }
}

@Composable
private fun PageCell(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(10.dp),
        color = if (selected) scheme.primary else Color.Transparent,
        border = if (selected) null else BorderStroke(1.dp, scheme.outline.copy(alpha = if (enabled) 1f else .3f)),
    ) {
        Text(
            label, Modifier.padding(horizontal = 11.dp, vertical = 8.dp), fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = when {
                selected -> scheme.onPrimary
                enabled -> scheme.onSurface
                else -> scheme.onSurface.copy(alpha = .3f)
            },
        )
    }
}

@Composable
fun GoToPageDialog(page: Int, pages: Int, onDismiss: () -> Unit, onGo: (Int) -> Unit) {
    var text by remember { mutableStateOf("${page + 1}") }
    val n = text.trim().toIntOrNull()
    val ok = n != null && n in 1..pages
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Перейти на страницу") },
        text = {
            Column {
                OutlinedTextField(
                    text, { text = it.filter(Char::isDigit).take(6) }, singleLine = true,
                    label = { Text("Номер от 1 до $pages") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { if (ok) onGo(n!! - 1) }),
                    isError = text.isNotBlank() && !ok,
                )
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("Первая", false) { onGo(0) }
                    Pill("Середина", false) { onGo(pages / 2) }
                    Pill("Последняя", false) { onGo(pages - 1) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (ok) onGo(n!! - 1) }, enabled = ok) { Text("Перейти") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Строка «Найдено 312 · стр. 4 из 16» и выбор размера страницы. */
@Composable
fun PageInfo(total: Int, page: Int, size: Int, onSize: (Int) -> Unit, noun: String = "записей") {
    val extra = LocalExtra.current
    val pages = Paging.pages(total, size)
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        val from = if (total == 0) 0 else page * size + 1
        val to = minOf(total, (page + 1) * size)
        Text("$from–$to из $total $noun · стр. ${page + 1} из $pages", Modifier.weight(1f), fontSize = 12.sp, color = extra.dim)
        Text("по", fontSize = 12.sp, color = extra.dim)
        PAGE_SIZES.forEach { s ->
            Text(
                "$s", fontSize = 12.sp, fontWeight = if (s == size) FontWeight.Bold else FontWeight.Normal,
                color = if (s == size) MaterialTheme.colorScheme.primary else extra.dim,
                modifier = Modifier.padding(start = 6.dp).clickable { onSize(s) }.padding(horizontal = 2.dp, vertical = 4.dp),
            )
        }
    }
}

/** Поле поиска с подсказкой синтаксиса: "фраза", -исключить, слово|слово. */
@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    var help by remember { mutableStateOf(false) }
    Column(modifier) {
        OutlinedTextField(
            value, onChange, Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text(placeholder) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Clear, "Очистить") }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = RoundedCornerShape(14.dp),
        )
        Text(
            if (help) "Скрыть подсказку" else "Как искать точнее?", fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp).clickable { help = !help },
        )
        if (help) Text(
            "• несколько слов — найдутся записи со всеми словами, окончания не важны\n" +
                "• \"чёрная дыра\" в кавычках — точное выражение\n" +
                "• -кофе — исключить записи с этим словом\n" +
                "• кошки|собаки или «кошки или собаки» — любое из слов",
            fontSize = 12.sp, lineHeight = 17.sp, color = LocalExtra.current.dim, modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Текст с подсвеченными совпадениями запроса. */
fun highlighted(text: String, q: TextQuery.Query, color: Color): AnnotatedString {
    if (q.isEmpty) return AnnotatedString(text)
    val ranges = TextQuery.ranges(text, q)
    if (ranges.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        ranges.forEach { r ->
            if (r.first >= 0 && r.last < text.length) addStyle(SpanStyle(background = color, fontWeight = FontWeight.SemiBold), r.first, r.last + 1)
        }
    }
}
