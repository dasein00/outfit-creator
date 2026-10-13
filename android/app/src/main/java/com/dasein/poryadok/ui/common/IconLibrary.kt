package com.dasein.poryadok.ui.common

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.dasein.poryadok.system.IconSets
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * Библиотека иконок в три раздела: «Мои иконки» (всё добавленное вами), «Мои наборы» (ваши папки)
 * и «Исходный» (встроенные наборы приложения). Общая для окна выбора иконки и экрана «Иконки».
 */
object IconLibrary {
    const val MINE = "mine"
    const val SETS = "sets"
    const val BUILTIN = "builtin"

    /** Встроенный набор: id («ui», «d01», «dish:potmeat»…) и название. */
    data class Group(val id: String, val title: String)

    fun builtinGroups(dishes: Boolean): List<Group> = buildList {
        if (dishes) Glyphs.DISH_SETS.forEach { (t, s) -> add(Group("dish:$s", t)) }
        add(Group("ui", "Основные"))
        Glyphs.SETS.forEach { (t, s) -> add(Group(s, t)) }
        if (!dishes) Glyphs.DISH_SETS.filter { it.second in Glyphs.SHARED_DISH_SETS }.forEach { (t, s) -> add(Group("dish:$s", t)) }
    }

    /** Иконки встроенного набора как в приложении (без вашей перестановки). */
    fun defaultKeys(ctx: Context, id: String): List<String> = when {
        id == "ui" -> Glyphs.UI.map { "ui:$it" }
        id.startsWith("dish:") -> Glyphs.dishKeysOf(ctx, id.removePrefix("dish:"))
        else -> Glyphs.keysOf(ctx, id)
    }

    // ---------- Свой порядок встроенных наборов ----------

    /**
     * Как вы разложили встроенные наборы: порядок иконок в наборе ([keys]), порядок наборов ([order])
     * и удалённые наборы ([hidden]). Сами картинки остаются в приложении — удалённый набор можно вернуть.
     */
    @kotlinx.serialization.Serializable
    data class Layout(
        val keys: Map<String, List<String>> = emptyMap(),
        val order: List<String> = emptyList(),
        val hidden: List<String> = emptyList(),
    )

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun layoutFile(ctx: Context) = java.io.File(ctx.filesDir, "iconsets/builtin.json")

    fun layout(ctx: Context): Layout = runCatching { json.decodeFromString(Layout.serializer(), layoutFile(ctx).readText()) }.getOrDefault(Layout())

    private fun saveLayout(ctx: Context, l: Layout) {
        runCatching { layoutFile(ctx).apply { parentFile?.mkdirs() }.writeText(json.encodeToString(Layout.serializer(), l)) }
        IconSets.bump()
    }

    /** Встроенные наборы в вашем порядке, без удалённых. */
    fun groups(ctx: Context, dishes: Boolean): List<Group> {
        val l = layout(ctx)
        return builtinGroups(dishes).sortedBy { g -> l.order.indexOf(g.id).let { if (it < 0) Int.MAX_VALUE else it } }.filter { it.id !in l.hidden }
    }

    fun hiddenGroups(ctx: Context): List<Group> {
        val h = layout(ctx).hidden
        return (builtinGroups(true) + builtinGroups(false)).distinctBy { it.id }.filter { it.id in h }
    }

    /** Иконки встроенного набора в вашем порядке. */
    fun builtinKeys(ctx: Context, id: String): List<String> = layout(ctx).keys[id] ?: defaultKeys(ctx, id)

    fun setGroupKeys(ctx: Context, id: String, keys: List<String>) {
        val l = layout(ctx)
        saveLayout(ctx, l.copy(keys = l.keys + (id to keys.distinct())))
    }

    fun addToGroup(ctx: Context, id: String, keys: Collection<String>) = setGroupKeys(ctx, id, builtinKeys(ctx, id) + keys)
    fun removeFromGroup(ctx: Context, id: String, keys: Collection<String>) = setGroupKeys(ctx, id, builtinKeys(ctx, id) - keys.toSet())

    /** Сдвинуть набор в списке на [delta]. */
    fun moveGroup(ctx: Context, id: String, delta: Int) {
        val ids = groups(ctx, false).map { it.id }
        val i = ids.indexOf(id); val j = (i + delta).coerceIn(0, ids.lastIndex)
        if (i < 0 || i == j) return
        val l = layout(ctx)
        saveLayout(ctx, l.copy(order = ids.toMutableList().apply { add(j, removeAt(i)) }))
    }

    fun hideGroup(ctx: Context, id: String) { val l = layout(ctx); saveLayout(ctx, l.copy(hidden = (l.hidden + id).distinct())) }
    fun restoreGroups(ctx: Context) { val l = layout(ctx); saveLayout(ctx, l.copy(hidden = emptyList())) }

    /** Вернуть встроенный набор как в приложении: исходный состав и порядок. */
    fun resetGroup(ctx: Context, id: String) { val l = layout(ctx); saveLayout(ctx, l.copy(keys = l.keys - id)) }

    /** В каком встроенном наборе сейчас лежит иконка (с учётом ваших перемещений). */
    fun groupContaining(ctx: Context, key: String): String? =
        layout(ctx).keys.entries.firstOrNull { key in it.value }?.key ?: groupOf(key)

    /**
     * Иконки, которые где-то стоят: в коде приложения, в ваших записях (категории, привычки, страницы, рецепты…),
     * в настройках, виджетах и заменах. Такие наборы удалять нельзя.
     */
    suspend fun usedKeys(ctx: Context): Set<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val re = Regex("ui:[a-z_0-9]+|dish/[a-z0-9]+/\\d+|[a-z0-9]+/\\d\\d")
        val out = HashSet<String>()
        fun scan(s: String) { re.findAll(s).forEach { out += it.value } }
        runCatching { ctx.assets.open("icons/used_in_code.txt").bufferedReader().readLines().forEach { if (it.isNotBlank()) out += it.trim() } }
        IconSets.load(ctx)
        IconSets.overrides.value.forEach { (k, v) -> out += k; out += v }
        val dbs = listOfNotNull(
            runCatching { com.dasein.poryadok.Graph.db.openHelper.readableDatabase }.getOrNull(),
            runCatching { com.dasein.poryadok.Graph.extraDb.openHelper.readableDatabase }.getOrNull(),
        )
        for (db in dbs) runCatching {
            val tables = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%' AND name != 'room_master_table'")
                .use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
            for (t in tables) {
                val cols = db.query("PRAGMA table_info(`$t`)").use { c ->
                    buildList { while (c.moveToNext()) if (c.getString(2).orEmpty().uppercase().contains("TEXT")) add(c.getString(1)) }
                }
                for (col in cols) runCatching {
                    db.query("SELECT DISTINCT `$col` FROM `$t` WHERE length(`$col`) <= 300").use { c ->
                        while (c.moveToNext()) {
                            val v = c.getString(0) ?: continue
                            scan(v)
                            // Старые записи хранят эмодзи — они показываются иконкой.
                            if (v.length <= 12) out += Glyphs.normalize(v)
                        }
                    }
                }
            }
        }
        listOf(java.io.File(ctx.applicationInfo.dataDir, "shared_prefs"), java.io.File(ctx.filesDir, "datastore")).forEach { d ->
            d.listFiles().orEmpty().forEach { f -> runCatching { scan(f.readBytes().decodeToString()) } }
        }
        out
    }

    /** Встроенный набор, в котором лежит иконка. */
    fun groupOf(key: String): String? = when {
        key.startsWith("ui:") -> "ui"
        key.startsWith("dish/") -> "dish:" + key.removePrefix("dish/").substringBefore('/')
        key.startsWith("u:") || key.startsWith("/") -> null
        '/' in key -> key.substringBefore('/')
        else -> null
    }

    /** Свои картинки для иллюстраций блюд: иконки и фото рецептов. */
    fun minePhotos(ctx: Context, dishes: Boolean): List<String> =
        (listOf("icons") + if (dishes) listOf("recipes") else emptyList()).flatMap { dir ->
            java.io.File(ctx.filesDir, dir).listFiles().orEmpty()
                .filter { it.isFile && !it.name.contains("_orig") && it.extension.lowercase() in setOf("png", "jpg", "jpeg", "webp") }
        }.sortedByDescending { it.lastModified() }.map { it.absolutePath }

    fun mine(ctx: Context, dishes: Boolean): List<String> =
        if (dishes) (minePhotos(ctx, true) + IconSets.mine(ctx)).distinct() else IconSets.mine(ctx)
}

/** Три раздела библиотеки — переключатель сверху. */
@Composable
fun IconLibraryTabs(level: String, mineCount: Int, setsCount: Int, onLevel: (String) -> Unit) {
    Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Pill("Мои иконки · $mineCount", level == IconLibrary.MINE) { onLevel(IconLibrary.MINE) }
        Pill("Мои наборы · $setsCount", level == IconLibrary.SETS) { onLevel(IconLibrary.SETS) }
        Pill("Исходный", level == IconLibrary.BUILTIN) { onLevel(IconLibrary.BUILTIN) }
    }
}

/** Строка набора в списке: несколько иконок для узнавания, название и число. */
@Composable
fun IconGroupRow(title: String, keys: List<String>, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    val extra = LocalExtra.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(16.dp)).background(extra.card)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.size(width = 112.dp, height = 30.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            keys.take(4).forEach { k ->
                Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) { IconCell(k, 24.dp) }
            }
        }
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${keys.size} иконок", fontSize = 12.sp, color = extra.dim)
        }
        trailing()
        Text(btnLabel("›"), fontSize = 22.sp, color = extra.dim, modifier = Modifier.padding(start = 6.dp))
    }
}

/** Список наборов: свои папки или встроенные. */
@Composable
fun IconGroupList(
    groups: List<Pair<String, String>>, // id → название
    keysOf: (String) -> List<String>,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
    trailing: @Composable (String) -> Unit = {},
    onOpen: (String) -> Unit,
) {
    LazyColumn(modifier) {
        item { header() }
        items(groups, key = { it.first }) { (id, title) ->
            val keys = remember(id, IconSets.version.intValue) { keysOf(id) }
            IconGroupRow(title, keys, { onOpen(id) }) { trailing(id) }
        }
    }
}

/** Картинка иконки в ячейке: своя картинка — круглое фото, остальное — как в приложении. */
@Composable
fun IconCell(k: String, size: Dp) {
    if (k.startsWith("/")) {
        val img by rememberImage(k, 256)
        Box(Modifier.size(size * 1.3f).clip(androidx.compose.foundation.shape.CircleShape)) {
            img?.let { androidx.compose.foundation.Image(it, null, Modifier.size(size * 1.3f), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
        }
    } else GlyphRaw(k, null, size)
}

/**
 * Сетка иконок: нажатие — [onTap]; если [reorderable] — удерживайте иконку и тащите, чтобы поставить её на другое место
 * (новый порядок уходит в [onReorder]). Иначе удержание вызывает [onLongTap].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReorderGrid(
    keys: List<String>,
    cellMin: Dp,
    modifier: Modifier = Modifier,
    reorderable: Boolean,
    onTap: (String) -> Unit,
    onReorder: (List<String>) -> Unit = {},
    onLongTap: ((String) -> Unit)? = null,
    cell: @Composable (key: String, dragging: Boolean) -> Unit,
) {
    val state = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var order by remember(keys) { mutableStateOf(keys) }
    var dragKey by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    // После перестановки ждём, пока сетка разложится заново, иначе следующий шаг считается по старым местам.
    var expectAt by remember { mutableStateOf<androidx.compose.ui.unit.IntOffset?>(null) }
    var scrollJob by remember { mutableStateOf<Job?>(null) }
    val latestOrder = androidx.compose.runtime.rememberUpdatedState(order)
    val latestTap = androidx.compose.runtime.rememberUpdatedState(onTap)
    val latestLong = androidx.compose.runtime.rememberUpdatedState(onLongTap)
    val latestReorder = androidx.compose.runtime.rememberUpdatedState(onReorder)

    fun hit(p: Offset) = state.layoutInfo.visibleItemsInfo.firstOrNull {
        p.x >= it.offset.x && p.x < it.offset.x + it.size.width && p.y >= it.offset.y && p.y < it.offset.y + it.size.height
    }

    fun dragBy(delta: Offset) {
        val k = dragKey ?: return
        offset += delta
        val info = state.layoutInfo
        val me = info.visibleItemsInfo.firstOrNull { it.key == k } ?: return
        if (expectAt != null && me.offset != expectAt) return
        expectAt = null
        val c = Offset(me.offset.x + me.size.width / 2f, me.offset.y + me.size.height / 2f) + offset
        // У края — прокручиваем, иконка остаётся под пальцем.
        val edge = 90f
        val dir = when {
            c.y > info.viewportEndOffset - edge -> 1
            c.y < info.viewportStartOffset + edge -> -1
            else -> 0
        }
        if (dir != 0 && scrollJob?.isActive != true) scrollJob = scope.launch {
            val used = state.scrollBy(dir * 28f)
            offset += Offset(0f, used)
        }
        val t = hit(c)?.takeIf { it.key != k } ?: return
        val l = latestOrder.value
        val from = l.indexOf(k); val to = l.indexOf(t.key as String)
        if (from < 0 || to < 0) return
        val first = state.firstVisibleItemIndex; val firstOff = state.firstVisibleItemScrollOffset
        order = l.toMutableList().apply { add(to, removeAt(from)) }
        offset += Offset((me.offset.x - t.offset.x).toFloat(), (me.offset.y - t.offset.y).toFloat())
        expectAt = t.offset
        // Сетка держится за первую видимую иконку — не даём ей уехать вслед за перетаскиваемой.
        if (from == first || to == first) scope.launch { state.scrollToItem(first, firstOff) }
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun finish() {
        if (dragKey == null) return
        dragKey = null; offset = Offset.Zero; expectAt = null
        if (latestOrder.value != keys) latestReorder.value(latestOrder.value)
    }

    LazyVerticalGrid(
        GridCells.Adaptive(cellMin),
        modifier.pointerInput(reorderable, keys) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val slop = viewConfiguration.touchSlop
                var moved = false
                val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                        if (!c.pressed) return@withTimeoutOrNull true
                        if (abs(c.position.x - down.position.x) > slop || abs(c.position.y - down.position.y) > slop) { moved = true; return@withTimeoutOrNull true }
                    }
                }
                val key = hit(down.position)?.key as? String
                when {
                    released != null -> if (!moved && key != null) latestTap.value(key)
                    key == null -> {}
                    reorderable -> {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        dragKey = key; offset = Offset.Zero; expectAt = null
                        try {
                            while (true) {
                                val ev = awaitPointerEvent(PointerEventPass.Initial)
                                val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!c.pressed) { c.consume(); break }
                                val d = c.positionChange()
                                c.consume()
                                if (d != Offset.Zero) dragBy(d)
                            }
                        } finally { finish() }
                    }
                    else -> {
                        latestLong.value?.invoke(key)
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            ev.changes.forEach { it.consume() }
                            if (ev.changes.none { it.pressed }) break
                        }
                    }
                }
            }
        },
        state = state,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
    ) {
        items(order, key = { it }) { k ->
            val dragging = k == dragKey
            Box(
                Modifier
                    .then(if (dragging) Modifier.zIndex(1f) else Modifier.animateItem())
                    .graphicsLayer {
                        if (dragging) { translationX = offset.x; translationY = offset.y; scaleX = 1.12f; scaleY = 1.12f; alpha = .92f }
                    },
            ) { cell(k, dragging) }
        }
    }
}

/** Количество своих папок (без «Мои иконки»). */
internal fun userFolders(ctx: Context) = IconSets.folders(ctx).filter { it.id != IconSets.MINE }

@Composable
internal fun rememberLibraryCounts(dishes: Boolean): Pair<Int, Int> {
    val ctx = LocalContext.current
    val ver = IconSets.version.intValue
    return remember(ver, dishes) { IconLibrary.mine(ctx, dishes).size to userFolders(ctx).size }
}
