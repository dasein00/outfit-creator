package com.dasein.poryadok.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.data.HomeLayout
import com.dasein.poryadok.ui.common.ConfirmDialog
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.more.HUB
import com.dasein.poryadok.ui.theme.LocalExtra

const val HOME_EDIT = "homeEdit"

/** Название и иконка плашки — из каталога или из списка разделов (для ярлыков). */
fun homeTitle(id: String): Triple<String, String, String> {
    if (HomeLayout.isShortcut(id)) {
        val route = id.removePrefix("go:")
        val h = HUB.firstOrNull { it.route == route }
        return Triple(h?.title ?: route, h?.icon ?: "ui:grid", h?.sub ?: "")
    }
    val b = HomeLayout.block(id)
    return Triple(b?.title ?: id, b?.glyph ?: "ui:grid", b?.about ?: "")
}

/** Ряд ярлыков на разделы: треть, половина или во всю ширину. */
@Composable
fun ShortcutRow(nav: NavHostController, entries: List<HomeLayout.Entry>) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        var used = 0f
        entries.forEach { e ->
            val w = when (e.size) { 0 -> 1f / 3; 1 -> .5f; else -> 1f }
            used += w
            val (title0, glyph0, sub) = homeTitle(e.id)
            val look = com.dasein.poryadok.ui.common.rememberSection(e.id.removePrefix("go:"), title0, glyph0)
            val title = look.name; val glyph = look.icon
            Tile(Modifier.weight(w), onClick = { nav.navigate(e.id.removePrefix("go:")) }, padding = 10.dp) {
                if (e.size == 0) Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Glyph(glyph, 30.dp)
                    Text(title, fontSize = 12.sp, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                } else Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph(glyph, if (e.size == 2) 36.dp else 30.dp)
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (sub.isNotBlank()) Text(sub, fontSize = 11.sp, color = LocalExtra.current.dim, maxLines = if (e.size == 2) 2 else 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        // Неполный ряд — пустое место справа, чтобы плитки не растягивались.
        if (used < .99f) Spacer(Modifier.weight(1f - used))
    }
}

/** Настройка «Главного»: порядок, размер, добавить и убрать плашки или ярлыки разделов, отменить, вернуть как было. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeEditScreen(nav: NavHostController) {
    val extra = LocalExtra.current
    val layout = HomeLayout.state.value
    var confirmReset by remember { mutableStateOf(false) }
    Screen("Настроить главное", onBack = { nav.popBackStack() }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp)) {
            item {
                Text(
                    "Стрелками меняйте порядок, кнопками — размер. Крестик убирает плашку. Внизу — что можно добавить: плашки и ярлыки на любой раздел приложения.",
                    fontSize = 13.sp, color = extra.dim,
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { HomeLayout.undo() }, enabled = HomeLayout.canUndo.value, modifier = Modifier.weight(1f)) { Text("Отменить") }
                    OutlinedButton(onClick = { confirmReset = true }, enabled = !HomeLayout.isDefault, modifier = Modifier.weight(1f)) { Text("Как было") }
                }
                SectionTitle("На главном · ${layout.size}")
            }
            itemsIndexed(layout, key = { _, e -> e.id }) { i, e ->
                val (title, glyph, sub) = homeTitle(e.id)
                val sizes = HomeLayout.sizesOf(e.id)
                Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp)).background(extra.card).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", fontSize = 12.sp, color = extra.dim, modifier = Modifier.size(width = 22.dp, height = 18.dp))
                        Glyph(glyph, 26.dp)
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text((if (HomeLayout.isShortcut(e.id)) "Ярлык: " else "") + title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (sub.isNotBlank()) Text(sub, fontSize = 11.sp, color = extra.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { HomeLayout.move(i, -1) }, enabled = i > 0) { Icon(Icons.Default.ArrowUpward, "Выше", Modifier.size(18.dp)) }
                        IconButton(onClick = { HomeLayout.move(i, 1) }, enabled = i < layout.size - 1) { Icon(Icons.Default.ArrowDownward, "Ниже", Modifier.size(18.dp)) }
                        IconButton(onClick = { HomeLayout.remove(e.id) }) { Icon(Icons.Default.Close, "Убрать", Modifier.size(18.dp), tint = extra.danger) }
                    }
                    if (sizes.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(start = 30.dp, top = 2.dp)) {
                        sizes.forEachIndexed { k, label -> Pill(label, e.size == k) { HomeLayout.resize(e.id, k) } }
                    }
                }
            }
            val missing = HomeLayout.CATALOG.filter { b -> layout.none { it.id == b.id } }
            if (missing.isNotEmpty()) {
                item { SectionTitle("Добавить плашку") }
                itemsIndexed(missing, key = { _, b -> "add_" + b.id }) { _, b ->
                    AddRow(b.glyph, b.title, b.about) { HomeLayout.add(b.id) }
                }
            }
            val shortcuts = HUB.filter { h -> layout.none { it.id == "go:" + h.route } }
            item {
                SectionTitle("Ярлык на раздел")
                Text("Маленькие ярлыки встают в ряд по 2–3 — получается панель быстрого доступа.", fontSize = 12.sp, color = extra.dim)
            }
            itemsIndexed(shortcuts, key = { _, h -> "go_" + h.route }) { _, h ->
                AddRow(h.icon, h.title, h.sub) { HomeLayout.add("go:" + h.route, 0) }
            }
        }
    }
    if (confirmReset) ConfirmDialog(
        "Вернуть главное как было?", "Порядок, размеры и набор плашек станут стандартными. Это можно отменить кнопкой «Отменить».",
        confirm = "Вернуть", onDismiss = { confirmReset = false },
    ) { HomeLayout.reset() }
}

@Composable
private fun AddRow(glyph: String, title: String, sub: String, onAdd: () -> Unit) {
    val extra = LocalExtra.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp)).background(extra.card).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(glyph, 26.dp)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotBlank()) Text(sub, fontSize = 11.sp, color = extra.dim, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(
            "+ Добавить", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClick = onAdd).padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}
