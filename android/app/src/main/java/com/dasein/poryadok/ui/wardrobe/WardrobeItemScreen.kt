package com.dasein.poryadok.ui.wardrobe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.WardrobeItem
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.Money
import com.dasein.poryadok.logic.plural
import com.dasein.poryadok.ui.common.ChipRow
import com.dasein.poryadok.ui.common.ConfirmDialog
import com.dasein.poryadok.ui.common.DatePickDialog
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.Text
import com.dasein.poryadok.ui.common.TextButton
import com.dasein.poryadok.ui.common.btnLabel
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.num
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.common.plain
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Палитра для ручного выбора цветов вещи. */
private val COLOR_CHOICES = listOf(
    "#FFFFFF", "#F2EDE4", "#D9C7A7", "#B08A5A", "#7A5230", "#000000", "#4A4A4A", "#9E9E9E",
    "#1F3A5F", "#3D7BD9", "#9CC3F0", "#2E6B4F", "#8DB580", "#C9302C", "#F08A8A", "#F2C94C", "#E67E22", "#8E5BB5",
)

/** Карточка вещи как в Fits: фото с листанием, избранное, категория, бренд, теги, цвета, материалы, сезон, ссылка, цена, заметка. */
@Composable
fun WardrobeItemScreen(nav: NavHostController, startId: String) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val dao = Graph.dao
    val scope = rememberCoroutineScope()
    val all by observe(emptyList()) { dao.wardrobe() }
    val outfits by observe(emptyList()) { dao.outfits() }
    val sets by observe(emptyList()) { dao.wardrobeSets() }
    var id by remember { mutableStateOf(startId) }
    val item = all.firstOrNull { it.id == id }
    // Листание ‹ › — по вещам того же раздела (обычные или архив), новые сначала.
    val order = remember(all, item?.archived) { all.filter { it.archived == (item?.archived ?: false) }.sortedByDescending { it.addedAt } }
    var edit by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var setsDialog by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    val today = Dates.today()
    val worn = remember(outfits, id) { wearsOf(outfits, today)[id].orEmpty() }

    fun save(upd: WardrobeItem) = io { dao.upsertWardrobeItem(upd) }

    Screen(title = "", onBack = { nav.popBackStack() }) { pad ->
        if (item == null) return@Screen
        // Цвета находятся по фото сами, если ещё не заданы.
        androidx.compose.runtime.LaunchedEffect(item.id) {
            if (item.colors.isBlank()) {
                val c = withContext(Dispatchers.Default) { Bitmaps.load(ctx, item.src)?.let { dominantColors(it) } } ?: ""
                if (c.isNotBlank()) dao.upsertWardrobeItem(item.copy(colors = c))
            }
        }
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Box(Modifier.fillMaxWidth().height(340.dp).clip(RoundedCornerShape(20.dp)).background(BoardColor)) {
                ItemThumb(item, Modifier.fillMaxSize().padding(24.dp))
                val i = order.indexOfFirst { it.id == item.id }
                if (i > 0) ArrowButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, Modifier.align(Alignment.CenterStart)) { id = order[i - 1].id }
                if (i in 0 until order.size - 1) ArrowButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, Modifier.align(Alignment.CenterEnd)) { id = order[i + 1].id }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { save(item.copy(favorite = !item.favorite)) }) {
                    Icon(if (item.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Избранное", tint = if (item.favorite) Color(0xFFD9575B) else MaterialTheme.colorScheme.onSurface)
                }
                Box(Modifier.weight(1f))
                TextButton(onClick = { openEditor(nav, items = listOf(item.id)) }) {
                    Icon(Icons.Default.Checkroom, null, Modifier.size(18.dp)); Text(btnLabel("  Создать наряд"))
                }
            }
            Row(Modifier.clickable { edit = "name" }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(item.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.Edit, "Переименовать", Modifier.padding(start = 6.dp).size(16.dp), tint = extra.dim)
            }
            Text(
                if (worn.isEmpty()) "Ещё не надевали" else "Надевали ${worn.size} ${plural(worn.size, "раз", "раза", "раз")}, последний — ${Dates.label(worn.last())}" +
                    if (item.price > 0) " · цена за выход ${Money.format(item.price / worn.size)}" else "",
                fontSize = 12.sp, color = extra.dim,
            )
            Gap(12.dp)
            Group {
                Line(Icons.Default.Category, CATEGORY_LABELS[item.category] ?: item.category) { edit = "category" }
                Line(Icons.Default.Info, item.brand.ifBlank { "Добавить бренд" }, dim = item.brand.isBlank()) { edit = "brand" }
                Line(Icons.Default.LocalOffer, item.tags.ifBlank { "Добавить теги" }, dim = item.tags.isBlank()) { edit = "tags" }
                Line(Icons.Default.Palette, if (item.colors.isBlank()) "Добавить цвета" else "", dim = item.colors.isBlank(), trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        item.colorList().forEach { c -> Box(Modifier.size(18.dp).clip(CircleShape).background(c).border(1.dp, extra.line, CircleShape)) }
                    }
                }) { edit = "colors" }
                Line(Icons.Default.Layers, item.materials.ifBlank { "Добавить материалы" }, dim = item.materials.isBlank()) { edit = "materials" }
                Line(Icons.Default.WbSunny, if (item.season == "любой") "Любой сезон" else item.season.replaceFirstChar { it.uppercase() }) { edit = "season" }
            }
            Gap(10.dp)
            Group {
                Line(Icons.Default.Link, item.link.ifBlank { "Добавить ссылку" }, dim = item.link.isBlank(), onLong = { edit = "link" }) {
                    if (item.link.isBlank()) edit = "link"
                    else runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(item.link)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                Row(Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) { Line(Icons.Default.Payments, if (item.price > 0) Money.format(item.price) else "Цена", dim = item.price <= 0, chevron = false) { edit = "price" } }
                    Box(Modifier.weight(1f)) { Line(Icons.Default.CalendarMonth, if (item.bought > 0) Dates.full(item.bought) else "Дата покупки", dim = item.bought <= 0, chevron = false) { edit = "bought" } }
                }
            }
            Gap(10.dp)
            Group { Line(Icons.Default.Notes, item.note.ifBlank { "Добавить заметку…" }, dim = item.note.isBlank(), chevron = false) { edit = "note" } }
            Gap(10.dp)
            Group { Line(Icons.Default.Checkroom, "Создать наряд") { openEditor(nav, items = listOf(item.id)) } }
            Gap(14.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action(Icons.Default.BookmarkBorder, "В подборку", Modifier.weight(1f)) { setsDialog = true }
                Action(Icons.Default.Download, "Сохранить фото", Modifier.weight(1f)) {
                    scope.launch { toast = if (saveToGallery(ctx, item)) "Сохранено в «Галерею»" else "Не получилось сохранить" }
                }
                Action(if (item.archived) Icons.Default.Unarchive else Icons.Default.Archive, if (item.archived) "Из архива" else "В архив", Modifier.weight(1f)) {
                    save(item.copy(archived = !item.archived))
                    toast = if (item.archived) "Вещь вернулась в гардероб" else "Вещь в архиве — её видно через фильтры"
                }
                Action(Icons.Default.Delete, "Удалить", Modifier.weight(1f), danger = true) { confirm = true }
            }
            toast?.let { Text(it, fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 8.dp)) }
            if (item.addedAt > 0) Text(
                "Добавлено " + Instant.ofEpochMilli(item.addedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EE, d MMMM yyyy г.", Locale("ru"))),
                fontSize = 12.sp, color = extra.dim, modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Gap(40.dp)
        }
    }

    val it0 = item ?: return
    when (edit) {
        "name" -> EditTextDialog("Название", it0.name, onDismiss = { edit = null }) { if (it.isNotBlank()) save(it0.copy(name = it)) }
        "brand" -> EditTextDialog("Бренд", it0.brand, onDismiss = { edit = null }) { save(it0.copy(brand = it)) }
        "tags" -> EditTextDialog("Теги через запятую", it0.tags, onDismiss = { edit = null }) { save(it0.copy(tags = it)) }
        "materials" -> EditTextDialog("Материалы", it0.materials, onDismiss = { edit = null }) { save(it0.copy(materials = it)) }
        "link" -> EditTextDialog("Ссылка на вещь", it0.link, onDismiss = { edit = null }) { save(it0.copy(link = it)) }
        "price" -> EditTextDialog("Цена", if (it0.price > 0) it0.price.plain() else "", number = true, onDismiss = { edit = null }) { save(it0.copy(price = it.num() ?: 0.0)) }
        "note" -> EditTextDialog("Заметка", it0.note, multiline = true, onDismiss = { edit = null }) { save(it0.copy(note = it)) }
        "bought" -> DatePickDialog(it0.bought.takeIf { it > 0 }, onDismiss = { edit = null }, onPick = { save(it0.copy(bought = it ?: 0)) })
        "category" -> ChoiceDialog("Категория", CATEGORIES.map { it to (CATEGORY_LABELS[it] ?: it) }, it0.category, { edit = null }) { save(it0.copy(category = it)) }
        "season" -> ChoiceDialog("Сезон", listOf("любой" to "Любой", "зима" to "Зима", "осень" to "Осень", "весна" to "Весна", "лето" to "Лето"), it0.season, { edit = null }) { save(it0.copy(season = it)) }
        "colors" -> ColorsDialog(it0, { edit = null }) { save(it0.copy(colors = it)) }
    }
    if (setsDialog) {
        var newSet by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { setsDialog = false },
            title = { Text(btnLabel("Добавить в подборку")) },
            text = {
                Column {
                    if (sets.isEmpty()) Text("Подборок пока нет.", color = extra.dim, fontSize = 13.sp)
                    sets.forEach { s ->
                        val ids = s.itemIds.split(",").filter { it.isNotBlank() }
                        val on = it0.id in ids
                        Row(Modifier.fillMaxWidth().clickable {
                            io { dao.upsertWardrobeSet(s.copy(itemIds = (if (on) ids - it0.id else ids + it0.id).joinToString(","))) }
                        }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(on, null)
                            Column {
                                Text(s.name)
                                Text(setKindLabel(s.kind), fontSize = 11.sp, color = extra.dim)
                            }
                        }
                    }
                    TextButton(onClick = { newSet = true }) { Text(btnLabel("+ Новая подборка")) }
                }
            },
            confirmButton = { TextButton(onClick = { setsDialog = false }) { Text(btnLabel("Готово")) } },
        )
        if (newSet) NewSetDialog(onDismiss = { newSet = false }) { s ->
            newSet = false
            io { dao.upsertWardrobeSet(s.copy(itemIds = it0.id)) }
        }
    }
    if (confirm) ConfirmDialog("Удалить вещь?", "«${it0.name}» исчезнет из гардероба и нарядов.", onDismiss = { confirm = false }) {
        confirm = false
        io {
            // Встроенные вещи прячутся, свои — удаляются вместе с фото.
            if (it0.builtin) dao.upsertWardrobeItem(it0.copy(hidden = true))
            else { dao.deleteWardrobeItem(it0); java.io.File(it0.src).delete() }
        }
        Bitmaps.invalidate(it0.src)
        nav.popBackStack()
    }
}

@Composable
private fun ArrowButton(icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.padding(8.dp).size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = .8f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color(0xFF2A241C)) }
}

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(LocalExtra.current.card), content = content)
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Line(
    icon: ImageVector, text: String, dim: Boolean = false, chevron: Boolean = true,
    trailing: (@Composable () -> Unit)? = null, onLong: (() -> Unit)? = null, onClick: () -> Unit,
) {
    val extra = LocalExtra.current
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLong).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = extra.dim)
        Text(text, modifier = Modifier.weight(1f).padding(start = 12.dp), color = if (dim) extra.dim else MaterialTheme.colorScheme.onSurface, maxLines = 2)
        trailing?.invoke()
        if (chevron) Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp), tint = extra.dim)
    }
}

@Composable
private fun Action(icon: ImageVector, label: String, modifier: Modifier, danger: Boolean = false, onClick: () -> Unit) {
    val extra = LocalExtra.current
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(extra.card).clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = if (danger) extra.danger else MaterialTheme.colorScheme.onSurface)
        Text(btnLabel(label), fontSize = 10.sp, maxLines = 1, color = if (danger) extra.danger else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ChoiceDialog(title: String, options: List<Pair<String, String>>, value: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { ChipRow(options, value) { onPick(it); onDismiss() } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(btnLabel("Закрыть")) } },
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ColorsDialog(item: WardrobeItem, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val extra = LocalExtra.current
    var picked by remember { mutableStateOf(item.colors.split(",").map { it.trim() }.filter { it.isNotBlank() }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(btnLabel("Цвета вещи")) },
        text = {
            Column {
                Text("Первые цвета найдены по фото. Нажмите, чтобы добавить или убрать.", fontSize = 12.sp, color = extra.dim)
                Gap(10.dp)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (picked + COLOR_CHOICES).distinct().forEach { hex ->
                        val c = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)
                        val on = hex in picked
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(c)
                                .border(if (on) 3.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else extra.line, CircleShape)
                                .clickable { picked = if (on) picked - hex else picked + hex },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(picked.joinToString(",")); onDismiss() }) { Text(btnLabel("Готово")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) } },
    )
}
