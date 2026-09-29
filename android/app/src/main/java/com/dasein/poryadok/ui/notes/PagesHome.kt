@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.dasein.poryadok.ui.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.Page
import com.dasein.poryadok.data.PagesRepo
import com.dasein.poryadok.logic.InlineMarkdown
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.ConfirmDialog
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.HGap
import com.dasein.poryadok.ui.common.Hint
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SHORT = SimpleDateFormat("d MMM, HH:mm", Locale("ru"))

/** Заметки в стиле Notion: избранное, недавние, дерево страниц, поиск по тексту и корзина. */
@Composable
fun PagesHome(nav: NavHostController, embedded: Boolean = false) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val pages by observe(emptyList()) { Graph.pages.pages() }
    val blocks by observe(emptyList()) { Graph.pages.textBlocks() }
    var query by remember { mutableStateOf("") }
    var showTrash by remember { mutableStateOf(false) }
    var purge by remember { mutableStateOf<Page?>(null) }
    val expanded = remember { mutableStateMapOf<Long, Boolean>() }
    LaunchedEffect(Unit) { runCatching { PagesRepo.migrateOldNotes(ctx) } }

    fun open(id: Long) = nav.navigate(Routes.page(id))
    fun create(parent: Long? = null) = io {
        val id = PagesRepo.create(parent)
        withContext(Dispatchers.Main) { open(id) }
    }

    val live = pages.filter { !it.archived }
    val children = live.groupBy { it.parentId }
    val q = query.trim().lowercase()
    val results = remember(q, pages, blocks) {
        if (q.isEmpty()) emptyList() else {
            val hits = blocks.filter { InlineMarkdown.plain(it.text).lowercase().contains(q) }.groupBy { it.pageId }
            live.filter { it.title.lowercase().contains(q) || it.id in hits }.map { p ->
                p to (hits[p.id]?.firstOrNull()?.let { InlineMarkdown.plain(it.text) } ?: "")
            }
        }
    }

    Screen(
        title = if (embedded) "Заметки" else "Заметки",
        onBack = if (embedded) null else ({ nav.popBackStack(); Unit }),
        fab = { ExtendedFloatingActionButton(onClick = { create() }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Страница") }) },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
            item {
                OutlinedTextField(
                    query, { query = it }, placeholder = { Text("Поиск по заголовкам и тексту") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                )
            }
            if (q.isNotEmpty()) {
                item { SectionTitle("Найдено: ${results.size}") }
                items(results, key = { "r" + it.first.id }) { (p, snippet) ->
                    Tile(Modifier.padding(bottom = 6.dp), onClick = { open(p.id) }, padding = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Glyph(p.icon.ifBlank { "ui:document" }, 20.dp, badge = false)
                            HGap(8.dp)
                            Text(p.title.ifBlank { "Без названия" }, fontWeight = FontWeight.SemiBold)
                        }
                        if (snippet.isNotBlank()) Text(snippet, fontSize = 13.sp, color = extra.dim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                return@LazyColumn
            }
            if (live.isEmpty()) item {
                Hint(
                    "pages_intro",
                    "Страницы можно вкладывать друг в друга. В тексте введите «/», чтобы вставить заголовок, список, задачу, цитату, код, картинку или GIF. " +
                        "Быстрые сокращения: «# » — заголовок, «- » — список, «[] » — задача, «> » — цитата, «---» — разделитель. " +
                        "Выделите слово и нажмите B, I, S или A на панели над клавиатурой, чтобы оформить его.",
                    Modifier.padding(top = 12.dp), title = "Как пользоваться",
                )
            }
            val fav = live.filter { it.favorite }
            if (fav.isNotEmpty()) {
                item { SectionTitle("Избранное") }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(fav, key = { "f" + it.id }) { p ->
                            Tile(Modifier.width(150.dp), onClick = { open(p.id) }, padding = 12.dp) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Glyph(p.icon.ifBlank { "ui:document" }, 22.dp, badge = false)
                                    Box(Modifier.weight(1f))
                                    Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp))
                                }
                                Text(p.title.ifBlank { "Без названия" }, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                                Text(SHORT.format(Date(p.updatedAt)), fontSize = 11.sp, color = extra.dim)
                            }
                        }
                    }
                }
            }
            val recent = live.sortedByDescending { it.updatedAt }.take(5)
            if (recent.size >= 2) {
                item { SectionTitle("Недавние") }
                items(recent, key = { "n" + it.id }) { p ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { open(p.id) }.padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Glyph(p.icon.ifBlank { "ui:document" }, 18.dp, badge = false)
                        HGap(10.dp)
                        Text(p.title.ifBlank { "Без названия" }, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(SHORT.format(Date(p.updatedAt)), fontSize = 11.sp, color = extra.dim)
                    }
                }
            }
            if (live.isNotEmpty()) item { SectionTitle("Все страницы") }
            val rows = mutableListOf<Pair<Page, Int>>()
            fun walk(parent: Long?, depth: Int) {
                children[parent].orEmpty().sortedWith(compareBy({ it.sort }, { it.createdAt })).forEach { p ->
                    rows += p to depth
                    if (expanded[p.id] == true) walk(p.id, depth + 1)
                }
            }
            walk(null, 0)
            // Страницы, чей родитель в корзине или удалён, показываем в корне.
            live.filter { it.parentId != null && live.none { x -> x.id == it.parentId } }.forEach { rows += it to 0 }
            items(rows, key = { "t" + it.first.id }) { (p, depth) ->
                TreeRow(
                    p, depth, hasChildren = !children[p.id].isNullOrEmpty(), expanded = expanded[p.id] == true,
                    onToggle = { expanded[p.id] = expanded[p.id] != true },
                    onOpen = { open(p.id) },
                    onFavorite = { io { Graph.pages.upsertPage(p.copy(favorite = !p.favorite)) } },
                    onSub = { expanded[p.id] = true; create(p.id) },
                    onDuplicate = { io { PagesRepo.duplicate(p.id) } },
                    onTrash = { io { PagesRepo.archive(p.id) } },
                )
            }
            val trash = pages.filter { it.archived }
            if (trash.isNotEmpty()) {
                item {
                    TextButton(onClick = { showTrash = !showTrash }, modifier = Modifier.padding(top = 12.dp)) {
                        Text(if (showTrash) "Скрыть корзину" else "Корзина (${trash.size})")
                    }
                }
                if (showTrash) items(trash, key = { "x" + it.id }) { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(p.title.ifBlank { "Без названия" }, Modifier.weight(1f), color = extra.dim)
                        TextButton(onClick = { io { PagesRepo.archive(p.id, false) } }) { Text("Вернуть") }
                        TextButton(onClick = { purge = p }) { Text("Удалить", color = extra.danger) }
                    }
                }
            }
        }
    }
    purge?.let { p ->
        ConfirmDialog("Удалить навсегда?", "«${p.title.ifBlank { "Без названия" }}» и вложенные страницы будут удалены без возможности восстановления.", onDismiss = { purge = null }) {
            io { PagesRepo.deleteForever(p.id) }
        }
    }
}

@Composable
private fun TreeRow(
    p: Page,
    depth: Int,
    hasChildren: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onSub: () -> Unit,
    onDuplicate: () -> Unit,
    onTrash: () -> Unit,
) {
    val extra = LocalExtra.current
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).combinedClickable(onClick = onOpen, onLongClick = { menu = true })
                .padding(start = (depth * 18).dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (hasChildren) (if (expanded) "▾" else "▸") else " ", fontSize = 16.sp, color = extra.dim,
                modifier = Modifier.width(22.dp).clip(RoundedCornerShape(6.dp)).clickable(enabled = hasChildren, onClick = onToggle),
            )
            Glyph(p.icon.ifBlank { "ui:document" }, 20.dp, badge = false)
            HGap(10.dp)
            Text(p.title.ifBlank { "Без названия" }, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            if (p.favorite) Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.primary)
        }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem({ Text(if (p.favorite) "Убрать из избранного" else "В избранное") }, { menu = false; onFavorite() })
            DropdownMenuItem({ Text("Добавить подстраницу") }, { menu = false; onSub() })
            DropdownMenuItem({ Text("Дублировать") }, { menu = false; onDuplicate() })
            DropdownMenuItem({ Text("В корзину", color = extra.danger) }, { menu = false; onTrash() })
        }
    }
}
