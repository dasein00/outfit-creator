@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.notes

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.BlockType
import com.dasein.poryadok.data.Page
import com.dasein.poryadok.data.PageBlock
import com.dasein.poryadok.data.PagesRepo
import com.dasein.poryadok.logic.InlineMarkdown
import com.dasein.poryadok.logic.MdStyle
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.GlyphPickerDialog
import com.dasein.poryadok.ui.common.HGap
import com.dasein.poryadok.ui.common.MediaView
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.common.rememberMediaPicker
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Невидимый первый символ каждого блока: его удаление значит «Backspace в начале блока». */
private const val ZW = '​'
private const val ZWS = "​"

internal data class BlockKind(val type: String, val label: String, val badge: String, val hint: String)

internal val BLOCK_KINDS = listOf(
    BlockKind(BlockType.TEXT, "Текст", "T", "Обычный абзац"),
    BlockKind(BlockType.H1, "Заголовок 1", "H1", "Крупный заголовок  #"),
    BlockKind(BlockType.H2, "Заголовок 2", "H2", "Средний заголовок  ##"),
    BlockKind(BlockType.H3, "Заголовок 3", "H3", "Малый заголовок  ###"),
    BlockKind(BlockType.BULLET, "Список", "•", "Маркированный список  -"),
    BlockKind(BlockType.NUMBER, "Нумерованный список", "1.", "Список с номерами  1."),
    BlockKind(BlockType.TODO, "Задача", "☐", "Чек-лист  []"),
    BlockKind(BlockType.TOGGLE, "Раскрывающийся блок", "▸", "Скрывает текст под заголовком  >>"),
    BlockKind(BlockType.QUOTE, "Цитата", "❝", "Выделенная цитата  >"),
    BlockKind(BlockType.CALLOUT, "Выноска", "!", "Важная мысль на цветном фоне"),
    BlockKind(BlockType.CODE, "Код", "</>", "Моноширинный текст  ```"),
    BlockKind(BlockType.DIVIDER, "Разделитель", "—", "Горизонтальная линия  ---"),
    BlockKind(BlockType.IMAGE, "Картинка или GIF", "▣", "Из галереи"),
    BlockKind(BlockType.PAGE, "Подстраница", "▤", "Вложенная страница"),
)

/** Показывает разметку: в активном блоке маркеры бледные, в остальных — скрыты, текст оформлен. */
private class MdTransformation(private val focused: Boolean, private val dim: Color, private val codeBg: Color, private val mark: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val spans = InlineMarkdown.spans(raw)
        if (spans.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val out = buildAnnotatedString {
            append(raw)
            val hidden = SpanStyle(color = Color.Transparent, fontSize = 1.sp, letterSpacing = 0.sp)
            val shown = SpanStyle(color = dim)
            spans.forEach { s ->
                val cs = s.start + s.marker
                val ce = s.end - s.marker
                val style = when (s.style) {
                    MdStyle.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                    MdStyle.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                    MdStyle.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                    MdStyle.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
                    MdStyle.CODE -> SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)
                    MdStyle.HIGHLIGHT -> SpanStyle(background = mark)
                }
                addStyle(style, cs, ce)
                addStyle(if (focused) shown else hidden, s.start, cs)
                addStyle(if (focused) shown else hidden, ce, s.end)
            }
        }
        return TransformedText(out, OffsetMapping.Identity)
    }
}

/** Всё состояние редактора живёт здесь; база обновляется с небольшой задержкой целиком. */
private class EditorState(val pageId: Long) {
    val blocks = mutableStateListOf<PageBlock>()
    val values = mutableStateMapOf<Long, TextFieldValue>()
    val deleted = mutableSetOf<Long>()
    val open = mutableStateMapOf<Long, Boolean>()
    var loaded by mutableStateOf(false)
    var focused by mutableStateOf<Long?>(null)
    var selected by mutableStateOf<Long?>(null)
    var focusRequest by mutableStateOf<Pair<Long, Int>?>(null)
    var dirty by mutableIntStateOf(0)

    fun plain(id: Long) = values[id]?.text?.removePrefix(ZWS) ?: blocks.firstOrNull { it.id == id }?.text.orEmpty()

    fun load(list: List<PageBlock>) {
        blocks.clear(); values.clear()
        val src = list.ifEmpty { listOf(PageBlock(id = PagesRepo.newBlockId(), pageId = pageId)) }
        src.forEach { b -> blocks += b; values[b.id] = TextFieldValue(ZWS + b.text, TextRange(1)) }
        loaded = true
    }

    fun snapshot(): List<PageBlock> = blocks.mapIndexed { i, b -> b.copy(pos = i, text = if (b.type in BlockType.TEXTUAL) plain(b.id) else b.text) }

    fun save() {
        if (!loaded) return
        val list = snapshot()
        val gone = deleted.toList()
        deleted.clear()
        io {
            val p = Graph.pages
            gone.forEach { p.deleteBlock(it) }
            p.upsertBlocks(list)
            p.touch(pageId, System.currentTimeMillis())
        }
    }

    fun index(id: Long) = blocks.indexOfFirst { it.id == id }

    fun insertAfter(id: Long?, block: PageBlock, text: String = ""): PageBlock {
        val at = if (id == null) blocks.size else index(id) + 1
        blocks.add(at.coerceIn(0, blocks.size), block)
        values[block.id] = TextFieldValue(ZWS + text, TextRange(1 + text.length))
        dirty++
        return block
    }

    fun newBlock(type: String = BlockType.TEXT, indent: Int = 0) = PageBlock(id = PagesRepo.newBlockId(), pageId = pageId, type = type, indent = indent)

    fun remove(id: Long) {
        val i = index(id); if (i < 0) return
        blocks.removeAt(i); values.remove(id); deleted += id
        if (blocks.isEmpty()) insertAfter(null, newBlock())
        dirty++
    }

    fun update(id: Long, f: (PageBlock) -> PageBlock) {
        val i = index(id); if (i < 0) return
        blocks[i] = f(blocks[i]); dirty++
    }

    fun setType(id: Long, type: String) {
        update(id) { it.copy(type = type, indent = if (type in BlockType.CONTINUES) it.indent else 0) }
    }

    fun move(id: Long, delta: Int) {
        val i = index(id); val j = i + delta
        if (i < 0 || j !in blocks.indices) return
        val b = blocks.removeAt(i); blocks.add(j, b); dirty++
    }

    fun onChange(b: PageBlock, nv: TextFieldValue) {
        val idx = index(b.id); if (idx < 0) return
        var v = nv
        // Удалили невидимый символ — это Backspace в начале блока.
        if (!v.text.startsWith(ZW)) {
            val rest = v.text
            when {
                b.indent > 0 -> { update(b.id) { it.copy(indent = it.indent - 1) }; values[b.id] = TextFieldValue(ZWS + rest, TextRange(1)) }
                b.type != BlockType.TEXT -> { setType(b.id, BlockType.TEXT); values[b.id] = TextFieldValue(ZWS + rest, TextRange(1)) }
                idx > 0 && blocks[idx - 1].type !in BlockType.TEXTUAL -> { remove(blocks[idx - 1].id); values[b.id] = TextFieldValue(ZWS + rest, TextRange(1)) }
                idx > 0 -> {
                    val prev = blocks[idx - 1]
                    val pv = values[prev.id] ?: TextFieldValue(ZWS)
                    val join = pv.text.length
                    values[prev.id] = TextFieldValue(pv.text + rest, TextRange(join))
                    remove(b.id)
                    focusRequest = prev.id to join
                }
                else -> values[b.id] = TextFieldValue(ZWS + rest, TextRange(1))
            }
            dirty++
            return
        }
        if (b.type != BlockType.CODE && '\n' in v.text) {
            val lines = v.text.split('\n')
            val first = lines[0]
            if (first == ZWS && lines.size == 2 && lines[1].isEmpty() && b.type in BlockType.CONTINUES) {
                // Enter в пустом пункте списка — выход из списка.
                setType(b.id, BlockType.TEXT); values[b.id] = TextFieldValue(ZWS, TextRange(1)); return
            }
            values[b.id] = TextFieldValue(first, TextRange(first.length))
            var last = b.id
            var cursor = 1
            lines.drop(1).forEachIndexed { k, line ->
                val cont = b.type in BlockType.CONTINUES
                val nb = insertAfter(last, newBlock(if (cont) b.type else BlockType.TEXT, if (cont) b.indent else 0), line)
                last = nb.id
                cursor = if (lines.size == 2) 1 else 1 + line.length
                if (k == lines.size - 2 && lines.size == 2) values[nb.id] = TextFieldValue(ZWS + line, TextRange(1))
            }
            focusRequest = last to cursor
            dirty++
            return
        }
        // Markdown-сокращения в начале абзаца.
        if (b.type == BlockType.TEXT) {
            val t = v.text.drop(1)
            val shortcut = listOf(
                "### " to BlockType.H3, "## " to BlockType.H2, "# " to BlockType.H1, "- " to BlockType.BULLET, "* " to BlockType.BULLET,
                "1. " to BlockType.NUMBER, "[] " to BlockType.TODO, "[ ] " to BlockType.TODO, ">> " to BlockType.TOGGLE, "> " to BlockType.QUOTE,
                "```" to BlockType.CODE, "---" to BlockType.DIVIDER,
            ).firstOrNull { t.startsWith(it.first) }
            if (shortcut != null) {
                val rest = t.removePrefix(shortcut.first)
                if (shortcut.second == BlockType.DIVIDER) {
                    setType(b.id, BlockType.DIVIDER)
                    val nb = insertAfter(b.id, newBlock(), rest)
                    focusRequest = nb.id to 1
                } else {
                    setType(b.id, shortcut.second)
                    values[b.id] = TextFieldValue(ZWS + rest, TextRange(1 + rest.length))
                }
                return
            }
        }
        if (v.selection.min < 1) v = v.copy(selection = TextRange(maxOf(1, v.selection.start), maxOf(1, v.selection.end)))
        values[b.id] = v
        dirty++
    }

    fun format(style: MdStyle) {
        val id = focused ?: return
        val v = values[id] ?: return
        val (t, a, e) = InlineMarkdown.toggle(v.text.drop(1), v.selection.start - 1, v.selection.end - 1, style)
        values[id] = TextFieldValue(ZWS + t, TextRange(a + 1, e + 1))
        dirty++
    }
}

private val DATE = SimpleDateFormat("d MMM yyyy, HH:mm", Locale("ru"))

@Composable
fun PageScreen(nav: NavHostController, id: Long) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scheme = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    val page by observe<Page?>(null, id) { Graph.pages.page(id) }
    val pages by observe(emptyList()) { Graph.pages.pages() }
    val st = remember(id) { EditorState(id) }
    val requesters = remember(id) { mutableMapOf<Long, FocusRequester>() }
    val titleFocus = remember { FocusRequester() }
    var title by remember(id) { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var pickIcon by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    var typeMenu by remember { mutableStateOf(false) }
    var imageFor by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(id) { st.load(Graph.pages.blocksNow(id)) }
    LaunchedEffect(page?.id) { if (title == null && page != null) title = page!!.title }
    LaunchedEffect(st.dirty) { if (st.dirty > 0) { delay(400); st.save() } }
    DisposableEffect(id) { onDispose { st.save() } }
    LaunchedEffect(title) {
        val t = title ?: return@LaunchedEffect
        delay(300)
        page?.takeIf { it.title != t }?.let { p -> io { Graph.pages.upsertPage(p.copy(title = t, updatedAt = System.currentTimeMillis())) } }
    }
    LaunchedEffect(st.focusRequest) {
        val (bid, pos) = st.focusRequest ?: return@LaunchedEffect
        st.values[bid]?.let { st.values[bid] = it.copy(selection = TextRange(pos.coerceIn(1, it.text.length))) }
        delay(40)
        runCatching { requesters[bid]?.requestFocus() }
        st.focusRequest = null
    }

    val pickCover = rememberMediaPicker("pages") { path -> page?.let { p -> io { Graph.pages.upsertPage(p.copy(cover = path)) } } }
    val pickImage = rememberMediaPicker("pages") { path ->
        val target = imageFor
        imageFor = null
        if (target != null && st.index(target) >= 0 && st.blocks[st.index(target)].type == BlockType.IMAGE && st.blocks[st.index(target)].media.isBlank()) {
            st.update(target) { it.copy(media = path) }
        } else {
            val nb = st.insertAfter(target ?: st.focused ?: st.blocks.lastOrNull()?.id, st.newBlock(BlockType.IMAGE).copy(media = path))
            st.insertAfter(nb.id, st.newBlock()).also { st.focusRequest = it.id to 1 }
        }
    }

    fun addSubpage(after: Long?) {
        io {
            st.save()
            val child = PagesRepo.create(null)
            Graph.pages.pageNow(child)?.let { Graph.pages.upsertPage(it.copy(parentId = id)) }
            val link = st.newBlock(BlockType.PAGE).copy(media = child.toString())
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                st.insertAfter(after, link)
                st.save()
                nav.navigate(Routes.page(child))
            }
        }
    }

    fun applyKind(target: Long, k: BlockKind) {
        when (k.type) {
            BlockType.IMAGE -> {
                st.values[target] = TextFieldValue(ZWS, TextRange(1))
                if (st.plain(target).isBlank() && st.blocks.getOrNull(st.index(target))?.type == BlockType.TEXT) st.setType(target, BlockType.IMAGE)
                imageFor = target; pickImage()
            }
            BlockType.PAGE -> {
                val empty = st.plain(target).isBlank()
                st.values[target] = TextFieldValue(ZWS, TextRange(1))
                if (empty) st.remove(target)
                addSubpage(if (empty) st.blocks.getOrNull((st.index(target) - 1).coerceAtLeast(0))?.id else target)
            }
            BlockType.DIVIDER -> {
                st.values[target] = TextFieldValue(ZWS, TextRange(1))
                st.setType(target, BlockType.DIVIDER)
                val nb = st.insertAfter(target, st.newBlock())
                st.focusRequest = nb.id to 1
            }
            else -> {
                val txt = st.plain(target).let { if (it.startsWith("/")) "" else it }
                st.values[target] = TextFieldValue(ZWS + txt, TextRange(1 + txt.length))
                st.setType(target, k.type)
                st.focusRequest = target to 1 + txt.length
            }
        }
    }

    val p = page
    val crumbs = remember(pages, p) {
        val byId = pages.associateBy { it.id }
        generateSequence(p?.parentId?.let { byId[it] }) { it.parentId?.let { pid -> byId[pid] } }.toList().reversed()
    }
    val words = st.blocks.sumOf { b -> if (b.type in BlockType.TEXTUAL) InlineMarkdown.plain(st.plain(b.id)).split(Regex("\\s+")).count { it.isNotBlank() } else 0 }
    val isEmpty = st.loaded && st.blocks.size == 1 && st.blocks[0].type == BlockType.TEXT && st.plain(st.blocks[0].id).isBlank()

    Column(Modifier.fillMaxSize().background(scheme.background).systemBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { st.save(); nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                crumbs.forEach { c ->
                    Text(
                        c.title.ifBlank { "Без названия" }, fontSize = 13.sp, color = extra.dim, maxLines = 1,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { nav.navigate(Routes.page(c.id)) }.padding(4.dp),
                    )
                    Text("/", fontSize = 13.sp, color = extra.dim)
                }
                Text((title ?: "").ifBlank { "Без названия" }, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(4.dp))
            }
            if (p != null) IconButton(onClick = { io { Graph.pages.upsertPage(p.copy(favorite = !p.favorite)) } }) {
                Icon(if (p.favorite) Icons.Default.Star else Icons.Default.StarBorder, "Избранное", tint = if (p.favorite) scheme.primary else extra.dim)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Ещё") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Иконка") }, { menu = false; pickIcon = true })
                    DropdownMenuItem({ Text(if (p?.cover.isNullOrBlank()) "Добавить обложку" else "Сменить обложку") }, { menu = false; pickCover() })
                    if (!p?.cover.isNullOrBlank()) DropdownMenuItem({ Text("Убрать обложку") }, { menu = false; p?.let { io { Graph.pages.upsertPage(it.copy(cover = "")) } } })
                    DropdownMenuItem({ Text("Добавить подстраницу") }, { menu = false; addSubpage(st.blocks.lastOrNull()?.id) })
                    DropdownMenuItem({ Text("Переместить…") }, { menu = false; moving = true })
                    DropdownMenuItem({ Text("Дублировать") }, {
                        menu = false
                        st.save()
                        io { PagesRepo.duplicate(id)?.let { nid -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { nav.navigate(Routes.page(nid)) } } }
                    })
                    DropdownMenuItem({ Text("Поделиться текстом") }, {
                        menu = false
                        st.save()
                        io {
                            delay(200)
                            val md = PagesRepo.markdown(id)
                            val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, md) }
                            ctx.startActivity(Intent.createChooser(send, "Поделиться").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    })
                    DropdownMenuItem({ Text("В корзину", color = extra.danger) }, {
                        menu = false
                        st.save()
                        io { PagesRepo.archive(id) }
                        nav.popBackStack()
                    })
                }
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (p != null && p.cover.isNotBlank()) {
                MediaView(p.cover, Modifier.fillMaxWidth().height(160.dp).clickable { pickCover() }, crop = true)
            }
            Column(Modifier.padding(horizontal = 20.dp)) {
                if (p != null && p.icon.isNotBlank()) {
                    Box(Modifier.padding(top = 12.dp).clip(RoundedCornerShape(12.dp)).clickable { pickIcon = true }.padding(4.dp)) { Glyph(p.icon, 44.dp) }
                }
                if (p != null && (p.icon.isBlank() || p.cover.isBlank())) {
                    Row(Modifier.padding(top = 8.dp)) {
                        if (p.icon.isBlank()) TextButton(onClick = { pickIcon = true }) { Text("+ Иконка", color = extra.dim, fontSize = 13.sp) }
                        if (p.cover.isBlank()) TextButton(onClick = { pickCover() }) { Text("+ Обложка", color = extra.dim, fontSize = 13.sp) }
                    }
                }
                BasicTextField(
                    value = title ?: "",
                    onValueChange = { v ->
                        if ('\n' in v) {
                            title = v.replace("\n", "")
                            st.blocks.firstOrNull { it.type in BlockType.TEXTUAL }?.let { st.focusRequest = it.id to 1 }
                        } else title = v
                    },
                    textStyle = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface, lineHeight = 36.sp),
                    cursorBrush = SolidColor(scheme.primary),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp).focusRequester(titleFocus),
                    decorationBox = { inner ->
                        Box {
                            if ((title ?: "").isEmpty()) Text("Без названия", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = extra.dim.copy(alpha = .5f))
                            inner()
                        }
                    },
                )
                if (isEmpty) {
                    Text("Начните писать или выберите шаблон:", fontSize = 13.sp, color = extra.dim)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                        PagesRepo.templates().forEach { (name, blocks) ->
                            Pill(name, false) {
                                st.blocks.forEach { st.deleted += it.id }
                                st.load(blocks.map { it.copy(id = PagesRepo.newBlockId(), pageId = id) })
                                if (title.isNullOrBlank()) title = name
                                st.dirty++
                            }
                        }
                    }
                }

                var number = 0
                st.blocks.forEachIndexed { i, b ->
                    number = if (b.type == BlockType.NUMBER) (if (i > 0 && st.blocks[i - 1].type == BlockType.NUMBER && st.blocks[i - 1].indent == b.indent) number + 1 else 1) else 0
                    key(b.id) {
                        BlockView(
                            st, b, number, requesters.getOrPut(b.id) { FocusRequester() }, pages,
                            onOpenPage = { pid -> st.save(); nav.navigate(Routes.page(pid)) },
                            onKind = { k -> applyKind(b.id, k) },
                            onPickImage = { imageFor = b.id; pickImage() },
                        )
                    }
                }
                // Нажатие под последним блоком — продолжить писать.
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 120.dp).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {
                        val last = st.blocks.lastOrNull()
                        if (last != null && last.type in BlockType.TEXTUAL && st.plain(last.id).isEmpty()) st.focusRequest = last.id to 1
                        else st.insertAfter(null, st.newBlock()).also { st.focusRequest = it.id to 1 }
                    },
                ) {
                    Text(
                        "$words ${wordsLabel(words)}" + (p?.updatedAt?.takeIf { it > 0 }?.let { " · изменено " + DATE.format(Date(it)) } ?: ""),
                        fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
        }

        val fid = st.focused
        val sid = st.selected
        if (fid != null || sid != null) {
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().background(extra.card).horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                @Composable
                fun Tool(label: String, bold: Boolean = false, italic: Boolean = false, strike: Boolean = false, onClick: () -> Unit) {
                    Text(
                        label, fontSize = 16.sp,
                        fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
                        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
                        textDecoration = if (strike) TextDecoration.LineThrough else null,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
                if (fid != null) {
                    Box {
                        Tool("Aa") { typeMenu = true }
                        DropdownMenu(typeMenu, { typeMenu = false }) {
                            BLOCK_KINDS.forEach { k ->
                                DropdownMenuItem(
                                    { Text(k.label) },
                                    { typeMenu = false; applyKind(fid, k) },
                                    leadingIcon = { KindBadge(k) },
                                )
                            }
                        }
                    }
                    Tool("B", bold = true) { st.format(MdStyle.BOLD) }
                    Tool("I", italic = true) { st.format(MdStyle.ITALIC) }
                    Tool("S", strike = true) { st.format(MdStyle.STRIKE) }
                    Tool("U") { st.format(MdStyle.UNDERLINE) }
                    Tool("</>") { st.format(MdStyle.CODE) }
                    Text(
                        "A", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { st.format(MdStyle.HIGHLIGHT) }
                            .background(Color(0x66E0B040)).padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                    Tool("☐") { st.setType(fid, if (st.blocks.getOrNull(st.index(fid))?.type == BlockType.TODO) BlockType.TEXT else BlockType.TODO) }
                    Tool("•") { st.setType(fid, if (st.blocks.getOrNull(st.index(fid))?.type == BlockType.BULLET) BlockType.TEXT else BlockType.BULLET) }
                    Tool("⇥") { st.update(fid) { if (it.type in BlockType.CONTINUES) it.copy(indent = (it.indent + 1).coerceAtMost(4)) else it } }
                    Tool("⇤") { st.update(fid) { it.copy(indent = (it.indent - 1).coerceAtLeast(0)) } }
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).clickable { imageFor = fid; pickImage() }.padding(8.dp)) { Glyph("ui:image", 20.dp, badge = false) }
                }
                val target = fid ?: sid!!
                Tool("↑") { st.move(target, -1) }
                Tool("↓") { st.move(target, 1) }
                Box(Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                    st.selected = null
                    val i = st.index(target)
                    st.remove(target)
                    st.blocks.getOrNull((i - 1).coerceAtLeast(0))?.takeIf { it.type in BlockType.TEXTUAL }?.let { st.focusRequest = it.id to (st.values[it.id]?.text?.length ?: 1) }
                }.padding(8.dp)) { Glyph("ui:trash", 20.dp, badge = false) }
                Tool("Готово") { st.selected = null; st.focused = null; focusManager.clearFocus() }
            }
        }
    }

    if (pickIcon && p != null) GlyphPickerDialog(p.icon, { pickIcon = false }) { g -> io { Graph.pages.upsertPage(p.copy(icon = g)) } }
    if (moving && p != null) MovePageDialog(p, pages, { moving = false }) { parent -> io { PagesRepo.move(id, parent) } }
}

private fun wordsLabel(n: Int): String {
    val m10 = n % 10; val m100 = n % 100
    return when {
        m10 == 1 && m100 != 11 -> "слово"
        m10 in 2..4 && m100 !in 12..14 -> "слова"
        else -> "слов"
    }
}

@Composable
internal fun KindBadge(k: BlockKind) {
    Box(
        Modifier.size(34.dp).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) { Text(k.badge, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun BlockView(
    st: EditorState,
    b: PageBlock,
    number: Int,
    fr: FocusRequester,
    pages: List<Page>,
    onOpenPage: (Long) -> Unit,
    onKind: (BlockKind) -> Unit,
    onPickImage: () -> Unit,
) {
    val extra = LocalExtra.current
    val scheme = MaterialTheme.colorScheme
    val isFocused = st.focused == b.id
    val isSelected = st.selected == b.id
    val indent = (b.indent * 22).dp

    when (b.type) {
        BlockType.DIVIDER -> Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                .then(if (isSelected) Modifier.background(scheme.primary.copy(alpha = .12f)) else Modifier)
                .clickable { st.selected = b.id; st.focused = null }.padding(vertical = 12.dp),
        ) { HorizontalDivider(color = extra.dim.copy(alpha = .5f)) }

        BlockType.IMAGE -> Column(
            Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(12.dp))
                .then(if (isSelected) Modifier.border(2.dp, scheme.primary, RoundedCornerShape(12.dp)) else Modifier),
        ) {
            if (b.media.isBlank()) {
                Box(
                    Modifier.fillMaxWidth().height(90.dp).background(extra.card).clickable(onClick = onPickImage),
                    contentAlignment = Alignment.Center,
                ) { Text("Нажмите, чтобы выбрать картинку или GIF", color = extra.dim, fontSize = 13.sp) }
            } else {
                MediaView(b.media, Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 360.dp).clickable { st.selected = b.id; st.focused = null })
            }
            BasicTextField(
                value = b.text,
                onValueChange = { t -> st.update(b.id) { it.copy(text = t.replace("\n", " ")) } },
                textStyle = TextStyle(fontSize = 13.sp, color = extra.dim),
                cursorBrush = SolidColor(scheme.primary),
                modifier = Modifier.fillMaxWidth().padding(6.dp),
                decorationBox = { inner -> Box { if (b.text.isEmpty()) Text("Подпись", fontSize = 13.sp, color = extra.dim.copy(alpha = .5f)); inner() } },
            )
        }

        BlockType.PAGE -> {
            val child = pages.firstOrNull { it.id == b.media.toLongOrNull() }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .then(if (isSelected) Modifier.background(scheme.primary.copy(alpha = .12f)) else Modifier)
                    .combinedClickable(onClick = { child?.let { onOpenPage(it.id) } }, onLongClick = { st.selected = b.id; st.focused = null })
                    .padding(vertical = 8.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph(child?.icon?.takeIf { it.isNotBlank() } ?: "ui:document", 20.dp, badge = false)
                HGap(8.dp)
                Text(
                    child?.let { it.title.ifBlank { "Без названия" } + if (it.archived) " (в корзине)" else "" } ?: "Страница удалена",
                    fontWeight = FontWeight.Medium, textDecoration = TextDecoration.Underline,
                    color = if (child == null || child.archived) extra.dim else scheme.onSurface,
                )
            }
        }

        else -> {
            val v = st.values[b.id] ?: TextFieldValue(ZWS, TextRange(1))
            val plain = v.text.removePrefix(ZWS)
            val base = when (b.type) {
                BlockType.H1 -> TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp)
                BlockType.H2 -> TextStyle(fontSize = 21.sp, fontWeight = FontWeight.SemiBold, lineHeight = 27.sp)
                BlockType.H3 -> TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp)
                BlockType.QUOTE -> TextStyle(fontSize = 16.sp, fontStyle = FontStyle.Italic, lineHeight = 23.sp)
                BlockType.CODE -> TextStyle(fontSize = 14.sp, fontFamily = FontFamily.Monospace, lineHeight = 20.sp)
                else -> TextStyle(fontSize = 16.sp, lineHeight = 23.sp)
            }
            val done = b.type == BlockType.TODO && b.checked
            val style = base.copy(
                color = if (done) extra.dim else scheme.onSurface,
                textDecoration = if (done) TextDecoration.LineThrough else null,
            )
            val placeholder = when (b.type) {
                BlockType.H1 -> "Заголовок 1"; BlockType.H2 -> "Заголовок 2"; BlockType.H3 -> "Заголовок 3"
                BlockType.TODO -> "Задача"; BlockType.BULLET, BlockType.NUMBER -> "Пункт списка"; BlockType.QUOTE -> "Цитата"
                BlockType.CALLOUT -> "Важная мысль"; BlockType.CODE -> "Код"; BlockType.TOGGLE -> "Заголовок раскрывающегося блока"
                else -> "Пишите или введите «/» для команд"
            }
            val slash = isFocused && plain.startsWith("/") && '\n' !in plain
            val query = plain.drop(1).lowercase()
            val kinds = BLOCK_KINDS.filter { query.isBlank() || it.label.lowercase().contains(query) || it.hint.lowercase().contains(query) }

            val field: @Composable (Modifier) -> Unit = { m ->
                Box(m) {
                    BasicTextField(
                        value = v,
                        onValueChange = { st.onChange(b, it) },
                        textStyle = style,
                        cursorBrush = SolidColor(scheme.primary),
                        visualTransformation = MdTransformation(isFocused, extra.dim.copy(alpha = .6f), extra.card, Color(0x66E0B040)),
                        modifier = Modifier.fillMaxWidth().focusRequester(fr).onFocusChanged { f ->
                            if (f.isFocused) { st.focused = b.id; st.selected = null } else if (st.focused == b.id) st.focused = null
                        },
                        decorationBox = { inner ->
                            Box {
                                if (plain.isEmpty() && (isFocused || b.type != BlockType.TEXT)) Text(placeholder, style = style.copy(color = extra.dim.copy(alpha = .5f), textDecoration = null))
                                inner()
                            }
                        },
                    )
                    DropdownMenu(slash && kinds.isNotEmpty(), { st.values[b.id] = TextFieldValue(ZWS, TextRange(1)) }, properties = PopupProperties(focusable = false)) {
                        kinds.forEach { k ->
                            DropdownMenuItem(
                                { Column { Text(k.label); Text(k.hint, fontSize = 11.sp, color = extra.dim) } },
                                { onKind(k) },
                                leadingIcon = { KindBadge(k) },
                            )
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(start = indent, top = if (b.type == BlockType.H1) 14.dp else if (b.type in setOf(BlockType.H2, BlockType.H3)) 10.dp else 3.dp, bottom = 3.dp)) {
                when (b.type) {
                    BlockType.BULLET -> {
                        Text(if (b.indent % 2 == 0) "•" else "◦", style = style, modifier = Modifier.width(22.dp))
                        field(Modifier.weight(1f))
                    }
                    BlockType.NUMBER -> {
                        Text("$number.", style = style.copy(textDecoration = null), modifier = Modifier.widthIn(min = 24.dp).padding(end = 4.dp))
                        field(Modifier.weight(1f))
                    }
                    BlockType.TODO -> {
                        Box(
                            Modifier.padding(top = 3.dp, end = 8.dp).size(20.dp).clip(RoundedCornerShape(5.dp))
                                .background(if (b.checked) scheme.primary else Color.Transparent)
                                .border(2.dp, if (b.checked) scheme.primary else extra.dim, RoundedCornerShape(5.dp))
                                .clickable { st.update(b.id) { it.copy(checked = !it.checked) } },
                            contentAlignment = Alignment.Center,
                        ) { if (b.checked) Text("✓", color = scheme.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                        field(Modifier.weight(1f))
                    }
                    BlockType.QUOTE -> {
                        Box(Modifier.padding(end = 10.dp).width(3.dp).height(24.dp).background(scheme.primary))
                        field(Modifier.weight(1f))
                    }
                    BlockType.CALLOUT -> Surface(color = scheme.primary.copy(alpha = .10f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp)) {
                            Glyph("ui:bulb", 20.dp, badge = false)
                            HGap(8.dp)
                            field(Modifier.weight(1f))
                        }
                    }
                    BlockType.CODE -> Surface(color = extra.card, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        field(Modifier.padding(10.dp))
                    }
                    BlockType.TOGGLE -> {
                        val open = st.open[b.id] == true
                        Column(Modifier.fillMaxWidth()) {
                            Row {
                                Text(
                                    "▶", fontSize = 13.sp,
                                    modifier = Modifier.padding(top = 4.dp, end = 6.dp).rotate(if (open) 90f else 0f).clip(RoundedCornerShape(4.dp))
                                        .clickable { st.open[b.id] = !open },
                                )
                                field(Modifier.weight(1f))
                            }
                            AnimatedVisibility(open) {
                                BasicTextField(
                                    value = b.media,
                                    onValueChange = { t -> st.update(b.id) { it.copy(media = t) } },
                                    textStyle = TextStyle(fontSize = 15.sp, color = scheme.onSurface, lineHeight = 21.sp),
                                    cursorBrush = SolidColor(scheme.primary),
                                    modifier = Modifier.fillMaxWidth().padding(start = 22.dp, top = 4.dp),
                                    decorationBox = { inner -> Box { if (b.media.isEmpty()) Text("Скрытый текст", color = extra.dim.copy(alpha = .5f)); inner() } },
                                )
                            }
                        }
                    }
                    else -> field(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun MovePageDialog(page: Page, pages: List<Page>, onDismiss: () -> Unit, onMove: (Long?) -> Unit) {
    val banned = remember(pages, page) {
        val out = mutableSetOf(page.id)
        fun walk(x: Long) { pages.filter { it.parentId == x }.forEach { out += it.id; walk(it.id) } }
        walk(page.id)
        out
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Переместить в…") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Верхний уровень", fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onMove(null); onDismiss() }.padding(10.dp),
                )
                pages.filter { !it.archived && it.id !in banned }.sortedBy { it.title }.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onMove(p.id); onDismiss() }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Glyph(p.icon.ifBlank { "ui:document" }, 18.dp, badge = false)
                        Spacer(Modifier.width(8.dp))
                        Text(p.title.ifBlank { "Без названия" })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
