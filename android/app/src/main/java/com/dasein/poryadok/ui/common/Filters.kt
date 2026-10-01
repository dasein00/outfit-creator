@file:OptIn(ExperimentalMaterial3Api::class)

package com.dasein.poryadok.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dasein.poryadok.ui.theme.LocalExtra

/** Вариант в списке фильтра: ключ, подпись и число подходящих записей (−1 — не показывать). */
data class FilterOption(val key: String, val label: String, val count: Int = -1)

/**
 * Кнопка фильтра «Категория ▾», как в магазинах: по нажатию открывается список вариантов снизу.
 * Когда фильтр задан, кнопка закрашена и показывает выбранное значение.
 */
@Composable
fun FilterButton(title: String, value: String?, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val active = value != null
    Surface(
        onClick = onClick, shape = RoundedCornerShape(12.dp),
        color = if (active) scheme.primary else scheme.surface,
        border = if (active) null else BorderStroke(1.dp, scheme.outline.copy(alpha = .6f)),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 10.dp, top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                value ?: title, fontSize = 14.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp),
                color = if (active) scheme.onPrimary else scheme.onSurface,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            )
            Spacer(Modifier.width(4.dp))
            Text("▾", fontSize = 13.sp, color = if (active) scheme.onPrimary else LocalExtra.current.dim)
        }
    }
}

/** Строка «⇅ Сортировка … Фильтры (2)» над списком. */
@Composable
fun SortFilterBar(sortLabel: String, onSort: () -> Unit, activeFilters: Int, onFilters: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).clickable(onClick = onSort).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Filled.Sort, null, tint = scheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(sortLabel, fontSize = 15.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.clickable(onClick = onFilters).padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.FilterAlt, null, tint = scheme.primary)
            Spacer(Modifier.width(6.dp))
            Text(
                if (activeFilters > 0) "Фильтры · $activeFilters" else "Фильтры", fontSize = 15.sp, maxLines = 1, softWrap = false,
                color = if (activeFilters > 0) scheme.primary else scheme.onSurface,
                fontWeight = if (activeFilters > 0) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

/**
 * Список вариантов фильтра снизу экрана: один вариант (кружки) или несколько (галочки).
 * Выбор применяется кнопкой «Применить», крестик закрывает без изменений.
 */
@Composable
fun OptionSheet(
    title: String,
    options: List<FilterOption>,
    selected: Set<String>,
    multi: Boolean = false,
    onDismiss: () -> Unit,
    onApply: (Set<String>) -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pick by remember { mutableStateOf(selected) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        SheetHeader(title, onDismiss, if (multi && pick.isNotEmpty()) ({ pick = emptySet() }) else null)
        LazyColumn(Modifier.heightIn(max = 460.dp)) {
            items(options, key = { it.key }) { o ->
                val on = o.key in pick
                Row(
                    Modifier.fillMaxWidth().clickable {
                        pick = if (multi) (if (on) pick - o.key else pick + o.key) else setOf(o.key)
                    }.padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(o.label, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
                    if (o.count >= 0) Text("${o.count}", fontSize = 13.sp, color = LocalExtra.current.dim, modifier = Modifier.padding(end = 8.dp))
                    if (multi) Checkbox(on, null) else RadioButton(on, null)
                }
            }
        }
        SheetApply { onApply(pick) }
    }
}

@Composable
fun SheetHeader(title: String, onClose: () -> Unit, onReset: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (onReset != null) TextButton(onClick = onReset) { Text("Сбросить") }
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Закрыть") }
    }
}

@Composable
fun SheetApply(label: String = "Применить", onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp).heightIn(min = 52.dp),
        shape = RoundedCornerShape(26.dp),
    ) { Text(label, fontSize = 16.sp) }
}

/** Обёртка для листа с произвольным содержимым (например, «Все фильтры» или «Холодильник»). */
@Composable
fun FilterSheet(
    title: String,
    onDismiss: () -> Unit,
    onReset: (() -> Unit)? = null,
    applyLabel: String = "Применить",
    onApply: () -> Unit,
    content: @Composable () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surface) {
        SheetHeader(title, onDismiss, onReset)
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) { content() }
        SheetApply(applyLabel, onApply)
    }
}

/** Пункт в «Все фильтры»: заголовок и текущее значение, по нажатию — свой список. */
@Composable
fun FilterRow(title: String, value: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(
            value ?: "Любые", fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (value != null) MaterialTheme.colorScheme.primary else LocalExtra.current.dim,
            modifier = Modifier.padding(start = 12.dp).weight(1f, fill = false),
        )
        Text("  ›", fontSize = 18.sp, color = LocalExtra.current.dim)
    }
    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .25f))
}

