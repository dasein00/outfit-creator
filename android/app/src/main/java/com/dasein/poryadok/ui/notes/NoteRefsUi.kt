package com.dasein.poryadok.ui.notes

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.BlockType
import com.dasein.poryadok.data.PageBlock
import com.dasein.poryadok.data.PagesRepo
import com.dasein.poryadok.logic.InlineMarkdown
import com.dasein.poryadok.logic.NoteRefs
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.Text
import com.dasein.poryadok.ui.common.TextButton
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.btnLabel
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Куда можно сослаться из заметки: заметки, рецепты, книги читалки, коллекция фильмов/сериалов/книг. */
data class RefItem(val target: NoteRefs.Target, val name: String, val sub: String, val glyph: String, val route: String)

object NoteNav {
    /** Всё, на что можно сослаться (для поиска при вставке ссылки). */
    suspend fun candidates(ctx: Context): List<RefItem> = withContext(Dispatchers.IO) {
        val pages = Graph.pages.pagesNow().filter { !it.archived }.map {
            RefItem(NoteRefs.Target.PAGE, it.title.ifBlank { "Без названия" }, "заметка", it.icon.ifBlank { "ui:document" }, Routes.page(it.id))
        }
        val recipes = runCatching { Graph.extra.recipesNow() }.getOrDefault(emptyList()).filter { !it.archived }.map {
            RefItem(NoteRefs.Target.RECIPE, it.name, "рецепт · ${it.category}", "ui:salad", Routes.recipe(it.id))
        }
        val books = runCatching { com.dasein.poryadok.system.ReaderStore.state.value.books }.getOrDefault(emptyList()).map {
            RefItem(NoteRefs.Target.BOOK, it.title, "книга в читалке" + (it.author.takeIf { a -> a.isNotBlank() }?.let { a -> " · $a" } ?: ""), "books/08", com.dasein.poryadok.ui.reader.readerBook(it.id))
        }
        val media = runCatching { Graph.extra.allMedia() }.getOrDefault(emptyList()).map {
            RefItem(NoteRefs.Target.MEDIA, it.title, "коллекция" + (it.year?.let { y -> " · $y" } ?: ""), "d17/00", Routes.media(it.id, it.kind))
        }
        pages + recipes + books + media
    }

    /** Найти объект ссылки: сначала точное совпадение названия, потом начало/вхождение; для заметки без пары — создать её. */
    suspend fun resolve(ctx: Context, target: NoteRefs.Target, name: String): String? {
        val all = candidates(ctx)
        val pool = if (target == NoteRefs.Target.ANY) all else all.filter { it.target == target }
        val n = name.trim().lowercase()
        val hit = pool.firstOrNull { it.name.lowercase() == n }
            ?: pool.firstOrNull { it.name.lowercase().startsWith(n) }
            ?: pool.firstOrNull { it.name.lowercase().contains(n) }
        if (hit != null) return hit.route
        if (target == NoteRefs.Target.PAGE || target == NoteRefs.Target.ANY) {
            val id = withContext(Dispatchers.IO) { PagesRepo.create(null, name.trim()) }
            return Routes.page(id)
        }
        return null
    }

    fun openUrl(ctx: Context, url: String) {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Toast.makeText(ctx, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show() }
    }

    /** Страницы, которые ссылаются на эту (обратные ссылки). */
    suspend fun backlinks(pageId: Long, title: String): List<Pair<Long, String>> = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext emptyList()
        val t = title.trim().lowercase()
        val pages = Graph.pages.pagesNow().filter { !it.archived }.associateBy { it.id }
        Graph.pages.allBlocks().filter { it.pageId != pageId && it.pageId in pages }
            .filter { b -> NoteRefs.links(b.text).any { (it.target == NoteRefs.Target.PAGE || it.target == NoteRefs.Target.ANY) && it.name.lowercase() == t } }
            .map { it.pageId }.distinct().mapNotNull { id -> pages[id]?.let { id to it.title.ifBlank { "Без названия" } } }
    }

    /** Страницы с тегом. */
    suspend fun withTag(tag: String): List<Pair<Long, String>> = withContext(Dispatchers.IO) {
        val pages = Graph.pages.pagesNow().filter { !it.archived }.associateBy { it.id }
        Graph.pages.allBlocks().filter { tag in NoteRefs.tags(it.text) }.map { it.pageId }.distinct()
            .mapNotNull { id -> pages[id]?.let { id to it.title.ifBlank { "Без названия" } } }
    }

    // ---------- Экспорт: файлы Markdown, которые открываются на компьютере (Obsidian, Notion, любой редактор) ----------

    private fun safe(name: String) = name.ifBlank { "Без названия" }.replace(Regex("""[\\/:*?"<>|\n]"""), " ").trim().take(80)

    /** Все заметки в ZIP: папки повторяют вложенность страниц, картинки — в «media/». */
    suspend fun exportZip(ctx: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        val pages = Graph.pages.pagesNow().filter { !it.archived }
        val byId = pages.associateBy { it.id }
        fun path(id: Long): String {
            val chain = generateSequence(byId[id]) { p -> p.parentId?.let { byId[it] } }.toList().reversed()
            return chain.joinToString("/") { safe(it.title) }
        }
        var n = 0
        ctx.contentResolver.openOutputStream(uri)?.use { os ->
            ZipOutputStream(os).use { zip ->
                val usedMedia = mutableSetOf<String>()
                pages.forEach { p ->
                    zip.putNextEntry(ZipEntry(path(p.id) + ".md"))
                    zip.write(PagesRepo.markdown(p.id).toByteArray())
                    zip.closeEntry(); n++
                    Graph.pages.blocksNow(p.id).filter { (it.type == BlockType.IMAGE || it.type == BlockType.VIDEO) && it.media.startsWith("/") }.forEach { b ->
                        val f = File(b.media)
                        if (f.exists() && usedMedia.add(f.name)) { zip.putNextEntry(ZipEntry("media/" + f.name)); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry() }
                    }
                }
            }
        }
        n
    }

    /** Одна страница — файлом .md. */
    suspend fun exportPage(ctx: Context, id: Long, uri: Uri) = withContext(Dispatchers.IO) {
        ctx.contentResolver.openOutputStream(uri)?.use { it.write(PagesRepo.markdown(id).toByteArray()) }
    }

    /** Импорт .md / .txt: заголовки, списки, задачи, цитаты, код и разделители становятся блоками. */
    suspend fun importFile(ctx: Context, uri: Uri, parent: Long? = null): Long? = withContext(Dispatchers.IO) {
        val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: return@withContext null
        val name = runCatching {
            ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()?.substringBeforeLast('.') ?: "Импорт"
        val blocks = mutableListOf<PageBlock>()
        var code: StringBuilder? = null
        var title = name
        text.replace("\r", "").lines().forEach { raw ->
            val line = raw.trimEnd()
            if (line.trimStart().startsWith("```")) {
                if (code == null) code = StringBuilder() else { blocks += PageBlock(pageId = 0, type = BlockType.CODE, text = code.toString().trimEnd()); code = null }
                return@forEach
            }
            code?.let { it.appendLine(line); return@forEach }
            val t = line.trimStart()
            val indent = ((line.length - t.length) / 2).coerceAtMost(4)
            when {
                t.isEmpty() -> {}
                t.startsWith("# ") && blocks.isEmpty() && title == name -> title = t.removePrefix("# ").trim()
                t.startsWith("### ") -> blocks += PageBlock(pageId = 0, type = BlockType.H3, text = t.removePrefix("### "))
                t.startsWith("## ") -> blocks += PageBlock(pageId = 0, type = BlockType.H2, text = t.removePrefix("## "))
                t.startsWith("# ") -> blocks += PageBlock(pageId = 0, type = BlockType.H1, text = t.removePrefix("# "))
                t.startsWith("- [ ] ") || t.startsWith("- [x] ") || t.startsWith("- [X] ") ->
                    blocks += PageBlock(pageId = 0, type = BlockType.TODO, text = t.drop(6), checked = t[3] != ' ', indent = indent)
                t.startsWith("- ") || t.startsWith("* ") || t.startsWith("• ") -> blocks += PageBlock(pageId = 0, type = BlockType.BULLET, text = t.drop(2), indent = indent)
                Regex("""^\d+[.)] """).containsMatchIn(t) -> blocks += PageBlock(pageId = 0, type = BlockType.NUMBER, text = t.substringAfter(' '), indent = indent)
                t.startsWith("> ") -> blocks += PageBlock(pageId = 0, type = BlockType.QUOTE, text = t.removePrefix("> "))
                t == "---" || t == "***" -> blocks += PageBlock(pageId = 0, type = BlockType.DIVIDER)
                else -> blocks += PageBlock(pageId = 0, type = BlockType.TEXT, text = t)
            }
        }
        PagesRepo.create(parent, title, blocks)
    }
}

/** Вставить ссылку: поиск по заметкам, рецептам, книгам и коллекции; можно создать новую заметку с таким названием. */
@Composable
fun LinkPickerDialog(initial: String, onDismiss: () -> Unit, onPick: (NoteRefs.Target, String) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val extra = LocalExtra.current
    var q by remember { mutableStateOf(initial) }
    var all by remember { mutableStateOf<List<RefItem>>(emptyList()) }
    LaunchedEffect(Unit) { all = NoteNav.candidates(ctx) }
    val n = q.trim().lowercase()
    val shown = (if (n.isEmpty()) all else all.filter { it.name.lowercase().contains(n) }).sortedBy { if (it.name.lowercase().startsWith(n)) 0 else 1 }.take(40)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(btnLabel("Ссылка")) },
        text = {
            Column {
                TextInput(q, { q = it }, "Заметка, рецепт, книга, фильм…")
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()).padding(top = 6.dp)) {
                    if (n.isNotEmpty()) Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onPick(NoteRefs.Target.PAGE, q.trim()) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Glyph("ui:document", 20.dp)
                        Text("  Заметка «${q.trim()}» (создастся при нажатии)", fontSize = 14.sp)
                    }
                    shown.forEach { item ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onPick(item.target, item.name) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Glyph(item.glyph, 20.dp)
                            Column(Modifier.padding(start = 10.dp)) {
                                Text(item.name, fontSize = 14.sp, maxLines = 1)
                                Text(item.sub, fontSize = 11.sp, color = extra.dim, maxLines = 1)
                            }
                        }
                    }
                    if (all.isNotEmpty() && shown.isEmpty() && n.isEmpty()) Text("Пока не на что ссылаться", color = extra.dim)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(btnLabel("Закрыть")) } },
    )
}

/** Открыть ссылку из заметки: веб — в браузере, тег — список страниц с ним, остальное — нужный раздел приложения. */
suspend fun openRef(ctx: Context, nav: NavHostController, r: NoteRefs.Ref, onTag: (String) -> Unit) {
    when (r.kind) {
        NoteRefs.Kind.URL -> NoteNav.openUrl(ctx, r.name)
        NoteRefs.Kind.TAG -> onTag(r.name)
        NoteRefs.Kind.LINK -> {
            val route = NoteNav.resolve(ctx, r.target, r.name)
            if (route != null) nav.navigate(route) else Toast.makeText(ctx, "«${r.name}» не найдено", Toast.LENGTH_SHORT).show()
        }
    }
}

/** Страницы с тегом — по нажатию на #тег. */
@Composable
fun TagPagesDialog(nav: NavHostController, tag: String, onDismiss: () -> Unit) {
    var list by remember { mutableStateOf<List<Pair<Long, String>>?>(null) }
    LaunchedEffect(tag) { list = NoteNav.withTag(tag) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("#$tag") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                list?.forEach { (id, t) ->
                    Text(t, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onDismiss(); nav.navigate(Routes.page(id)) }.padding(vertical = 8.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(btnLabel("Закрыть")) } },
    )
}

/** Ссылка в тексте выглядит как название; маркеры скобок прячутся при чтении. */
internal fun plainName(text: String) = InlineMarkdown.plain(text)
