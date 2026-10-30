package com.dasein.poryadok.ui.wardrobe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.WardrobeItem
import com.dasein.poryadok.ui.common.ChipRow
import com.dasein.poryadok.ui.common.ConfirmDialog
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Text
import com.dasein.poryadok.ui.common.TextButton
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.btnLabel
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.num
import com.dasein.poryadok.ui.common.safeBottom
import com.dasein.poryadok.ui.theme.LocalExtra

/** Выбор категории вещи из всех разделов, с поиском; «Изменить категории…» открывает список категорий. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KindPickerDialog(current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val extra = LocalExtra.current
    var q by remember { mutableStateOf("") }
    var manage by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(btnLabel("Категория вещи")) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text("От категории зависит размер вещи в наряде.", fontSize = 12.sp, color = extra.dim)
                Gap(6.dp)
                TextInput(q, { q = it }, "Поиск категории")
                CATEGORIES.forEach { c ->
                    val ks = kindsOf(c).filter { q.isBlank() || Kinds.label(it).contains(q.trim(), true) || Kinds.plural(it).contains(q.trim(), true) }
                    if (ks.isNotEmpty()) {
                        Text(CATEGORY_LABELS[c] ?: c, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ks.forEach { k -> Pill(Kinds.label(k), k == current) { onPick(k); onDismiss() } }
                        }
                    }
                }
                Gap(8.dp)
                TextButton(onClick = { manage = true }) { Text(btnLabel("Изменить категории…")) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(btnLabel("Закрыть")) } },
    )
    if (manage) KindsManagerSheet { manage = false }
}

/**
 * Категории гардероба: по разделам, с числом вещей. Можно добавить свою, переименовать, поменять длину,
 * удалить (встроенную — скрыть) и открыть вещи категории, чтобы перенести их в другую или удалить.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KindsManagerSheet(onDismiss: () -> Unit) {
    val extra = LocalExtra.current
    val all by com.dasein.poryadok.ui.common.observe(emptyList()) { Graph.dao.wardrobe() }
    val counts = remember(all, Kinds.cfg) { all.groupingBy { it.kind() }.eachCount() }
    var edit by remember { mutableStateOf<String?>(null) }
    var addTo by remember { mutableStateOf<String?>(null) }
    var delete by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<String?>(null) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState()).safeBottom()) {
            Text(btnLabel("Категории"), style = MaterialTheme.typography.titleLarge)
            Text("Нажмите на категорию, чтобы увидеть её вещи и перенести их.", fontSize = 12.sp, color = extra.dim)
            CATEGORIES.forEach { c ->
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(CATEGORY_LABELS[c] ?: c, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { addTo = c }) { Text(btnLabel("+ Категория"), fontSize = 12.sp) }
                }
                kindsOf(c).forEach { k ->
                    Row(
                        Modifier.fillMaxWidth().clip(tileShape()).clickable { open = k }.padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                            Text(Kinds.plural(k))
                            Text("${counts[k] ?: 0} · ≈${Kinds.cm(k).toInt()} см" + if (Kinds.custom(k) != null) " · своя" else "", fontSize = 11.sp, color = extra.dim)
                        }
                        IconButton(onClick = { edit = k }) { Icon(Icons.Default.Edit, "Изменить", tint = extra.dim) }
                        IconButton(onClick = { delete = k }) { Icon(Icons.Default.Delete, "Удалить", tint = extra.dim) }
                    }
                }
            }
            if (Kinds.cfg.hidden.isNotEmpty() || Kinds.cfg.renamed.isNotEmpty()) {
                Gap(8.dp)
                TextButton(onClick = { Kinds.restoreBuiltins() }) { Text(btnLabel("Вернуть встроенные категории")) }
            }
            Gap(12.dp)
        }
    }
    edit?.let { k -> KindEditDialog(k, Kinds.parentOf(k), onDismiss = { edit = null }) }
    addTo?.let { c -> KindEditDialog(null, c, onDismiss = { addTo = null }) }
    delete?.let { k ->
        val n = counts[k] ?: 0
        ConfirmDialog(
            "Удалить категорию «${Kinds.plural(k)}»?",
            if (n > 0) "$n вещ. перейдут в другую категорию своего раздела." else "",
            onDismiss = { delete = null },
        ) {
            val parent = Kinds.parentOf(k)
            Kinds.remove(k)
            val moved = all.filter { it.kind() == k || it.pct == k }
            io { moved.forEach { Graph.dao.upsertWardrobeItem(it.copy(pct = guessKind(it.name, parent).takeIf { g -> g != k } ?: (kindsOf(parent).firstOrNull() ?: ""))) } }
            delete = null
        }
    }
    open?.let { k -> KindItemsSheet(k, all.filter { it.kind() == k }) { open = null } }
}

/** Добавить ([k] = null) или изменить категорию: названия, раздел, длина в см. */
@Composable
private fun KindEditDialog(k: String?, parent0: String, onDismiss: () -> Unit) {
    val extra = LocalExtra.current
    var single by remember { mutableStateOf(k?.let { Kinds.label(it) } ?: "") }
    var plural by remember { mutableStateOf(k?.let { Kinds.plural(it) } ?: "") }
    var parent by remember { mutableStateOf(parent0) }
    var cm by remember { mutableStateOf(k?.let { Kinds.cm(it).toInt().toString() } ?: "60") }
    val builtin = k != null && Kinds.custom(k) == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(btnLabel(if (k == null) "Новая категория" else "Категория")) },
        text = {
            Column {
                TextInput(plural, { plural = it }, "Название, например: Жилеты")
                Gap(6.dp)
                TextInput(single, { single = it }, "Одна вещь, например: Жилет")
                Gap(6.dp)
                TextInput(cm, { cm = it }, "Длина вещи, см (для размера в наряде)")
                if (!builtin) {
                    Gap(8.dp)
                    Text(btnLabel("Раздел"), fontSize = 12.sp, color = extra.dim)
                    Gap(4.dp)
                    ChipRow(CATEGORIES.map { it to (CATEGORY_LABELS[it] ?: it) }, parent) { parent = it }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val p = plural.trim().ifBlank { single.trim() }
                val s = single.trim().ifBlank { p }
                if (p.isNotBlank()) {
                    val len = (cm.num() ?: 60.0).toFloat().coerceIn(5f, 200f)
                    if (k == null) Kinds.add(s, p, parent, len) else Kinds.edit(k, s, p, parent, len)
                }
                onDismiss()
            }) { Text(btnLabel("Сохранить")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) } },
    )
}

/** Вещи категории: отметить и перенести в другую категорию или удалить. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KindItemsSheet(k: String, items: List<WardrobeItem>, onDismiss: () -> Unit) {
    val extra = LocalExtra.current
    var picked by remember { mutableStateOf(emptySet<String>()) }
    var move by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf(false) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.safeBottom()) {
            Text("${Kinds.plural(k)} · ${items.size}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
            Text("Отметьте вещи и выберите действие", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(horizontal = 20.dp))
            LazyVerticalGrid(
                GridCells.Fixed(3), Modifier.heightIn(max = 440.dp),
                contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.id }) { it0 ->
                    val on = it0.id in picked
                    Box(Modifier.aspectRatio(.85f).clip(tileShape()).background(tileBg()).clickable { picked = if (on) picked - it0.id else picked + it0.id }) {
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
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { picked = if (picked.size == items.size) emptySet() else items.map { it.id }.toSet() }) {
                    Text(btnLabel(if (picked.size == items.size && items.isNotEmpty()) "Снять все" else "Выбрать все"))
                }
                Box(Modifier.weight(1f))
                TextButton(onClick = { move = true }, enabled = picked.isNotEmpty()) { Text(btnLabel("Перенести…")) }
                TextButton(onClick = { del = true }, enabled = picked.isNotEmpty()) { Text(btnLabel("Удалить"), color = extra.danger) }
            }
        }
    }
    if (move) KindPickerDialog(k, onDismiss = { move = false }) { to ->
        val sel = items.filter { it.id in picked }
        io { sel.forEach { Graph.dao.upsertWardrobeItem(it.copy(category = Kinds.parentOf(to), pct = to)) } }
        picked = emptySet()
    }
    if (del) ConfirmDialog("Удалить отмеченные вещи (${picked.size})?", "Встроенные вещи скроются, свои удалятся вместе с фото.", onDismiss = { del = false }) {
        val sel = items.filter { it.id in picked }
        io {
            sel.forEach { if (it.builtin) Graph.dao.upsertWardrobeItem(it.copy(hidden = true)) else { Graph.dao.deleteWardrobeItem(it); java.io.File(it.src).delete() } }
        }
        picked = emptySet(); del = false
    }
}
