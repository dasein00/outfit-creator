package com.dasein.poryadok.data

import android.content.Context
import com.dasein.poryadok.Graph
import com.dasein.poryadok.logic.InlineMarkdown
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicLong

object PagesRepo {
    private val seq = AtomicLong(0)

    /** Уникальный id для нового блока, чтобы редактор мог сохранять список целиком, не дожидаясь базы. */
    fun newBlockId(): Long = System.currentTimeMillis() * 1000 + seq.incrementAndGet() % 1000

    suspend fun create(parentId: Long? = null, title: String = "", template: List<PageBlock> = emptyList()): Long {
        val now = System.currentTimeMillis()
        val p = Graph.pages
        val sort = p.pagesNow().filter { it.parentId == parentId }.maxOfOrNull { it.sort + 1 } ?: 0
        val id = p.upsertPage(Page(parentId = parentId, title = title, sort = sort, createdAt = now, updatedAt = now))
        val blocks = template.ifEmpty { listOf(PageBlock(pageId = id, type = BlockType.TEXT)) }
        p.upsertBlocks(blocks.mapIndexed { i, b -> b.copy(id = newBlockId(), pageId = id, pos = i) })
        if (parentId != null) {
            val siblings = p.blocksNow(parentId)
            p.upsertBlock(PageBlock(id = newBlockId(), pageId = parentId, pos = (siblings.maxOfOrNull { it.pos } ?: -1) + 1, type = BlockType.PAGE, media = id.toString()))
            p.touch(parentId, now)
        }
        return id
    }

    private suspend fun descendants(id: Long): List<Long> {
        val all = Graph.pages.pagesNow()
        val out = mutableListOf<Long>()
        fun walk(x: Long) { all.filter { it.parentId == x }.forEach { out += it.id; walk(it.id) } }
        walk(id)
        return out
    }

    suspend fun archive(id: Long, archived: Boolean = true) {
        val p = Graph.pages
        (listOf(id) + descendants(id)).forEach { pid -> p.pageNow(pid)?.let { p.upsertPage(it.copy(archived = archived)) } }
    }

    suspend fun deleteForever(id: Long) {
        val p = Graph.pages
        (listOf(id) + descendants(id)).forEach { pid ->
            p.deleteBlocksOf(pid)
            p.pageNow(pid)?.let { p.deletePage(it) }
        }
        // Убираем ссылки на удалённую страницу из родителя.
        p.pagesNow().forEach { page ->
            p.blocksNow(page.id).filter { it.type == BlockType.PAGE && it.media == id.toString() }.forEach { p.deleteBlock(it.id) }
        }
    }

    suspend fun duplicate(id: Long): Long? {
        val p = Graph.pages
        val src = p.pageNow(id) ?: return null
        val blocks = p.blocksNow(id).filter { it.type != BlockType.PAGE }
        return create(src.parentId, src.title + " (копия)", blocks).also { nid ->
            p.pageNow(nid)?.let { p.upsertPage(it.copy(icon = src.icon, cover = src.cover)) }
        }
    }

    suspend fun move(id: Long, newParent: Long?) {
        val p = Graph.pages
        val page = p.pageNow(id) ?: return
        if (newParent == id || (newParent != null && newParent in descendants(id))) return
        page.parentId?.let { old -> p.blocksNow(old).filter { it.type == BlockType.PAGE && it.media == id.toString() }.forEach { p.deleteBlock(it.id) } }
        p.upsertPage(page.copy(parentId = newParent))
        if (newParent != null) {
            val siblings = p.blocksNow(newParent)
            p.upsertBlock(PageBlock(id = newBlockId(), pageId = newParent, pos = (siblings.maxOfOrNull { it.pos } ?: -1) + 1, type = BlockType.PAGE, media = id.toString()))
        }
    }

    /** Текст страницы в Markdown — для «Поделиться». */
    suspend fun markdown(id: Long): String {
        val p = Graph.pages
        val page = p.pageNow(id) ?: return ""
        val titles = p.pagesNow().associate { it.id to it.title }
        var n = 0
        val body = p.blocksNow(id).joinToString("\n") { b ->
            if (b.type != BlockType.NUMBER) n = 0
            when (b.type) {
                BlockType.H1 -> "# ${b.text}"
                BlockType.H2 -> "## ${b.text}"
                BlockType.H3 -> "### ${b.text}"
                BlockType.BULLET -> "  ".repeat(b.indent) + "- ${b.text}"
                BlockType.NUMBER -> { n++; "  ".repeat(b.indent) + "$n. ${b.text}" }
                BlockType.TODO -> "- [${if (b.checked) "x" else " "}] ${b.text}"
                BlockType.QUOTE -> "> ${b.text}"
                BlockType.CALLOUT -> "> 💡 ${b.text}"
                BlockType.CODE -> "```\n${b.text}\n```"
                BlockType.DIVIDER -> "---"
                BlockType.IMAGE -> "![${b.text}](${b.media.substringAfterLast('/')})"
                BlockType.PAGE -> "📄 ${titles[b.media.toLongOrNull()] ?: "Страница"}"
                BlockType.TOGGLE -> "▸ ${b.text}\n${b.media.lines().joinToString("\n") { "    $it" }}"
                else -> b.text
            }
        }
        return "# ${page.title.ifBlank { "Без названия" }}\n\n$body"
    }

    /** Один раз переносит заметки из старого раздела в страницы. */
    suspend fun migrateOldNotes(ctx: Context) {
        val sp = ctx.getSharedPreferences("ui_state", Context.MODE_PRIVATE)
        if (sp.getBoolean("pages_migrated", false)) return
        val notes = Graph.dao.notes().first()
        notes.sortedBy { it.updatedAt }.forEach { n ->
            val blocks = n.body.lines().map { line ->
                val t = line.trimStart()
                when {
                    n.checklist && (t.startsWith("[x]") || t.startsWith("[ ]")) -> PageBlock(pageId = 0, type = BlockType.TODO, text = t.drop(3).trim(), checked = t.startsWith("[x]"))
                    n.checklist -> PageBlock(pageId = 0, type = BlockType.TODO, text = t)
                    else -> PageBlock(pageId = 0, type = BlockType.TEXT, text = line)
                }
            }
            val id = create(null, n.title.ifBlank { InlineMarkdown.plain(n.body.lineSequence().firstOrNull().orEmpty()).take(40) }, blocks)
            Graph.pages.pageNow(id)?.let { Graph.pages.upsertPage(it.copy(favorite = n.pinned, archived = n.archived, createdAt = n.updatedAt, updatedAt = n.updatedAt)) }
        }
        sp.edit().putBoolean("pages_migrated", true).apply()
    }

    /** Шаблоны для пустой страницы. */
    fun templates(): List<Pair<String, List<PageBlock>>> {
        fun b(type: String, text: String = "", checked: Boolean = false) = PageBlock(pageId = 0, type = type, text = text, checked = checked)
        return listOf(
            "Статья" to listOf(
                b(BlockType.CALLOUT, "Главная мысль статьи в одном предложении"),
                b(BlockType.H2, "Введение"), b(BlockType.TEXT),
                b(BlockType.H2, "Основная часть"), b(BlockType.TEXT),
                b(BlockType.H2, "Вывод"), b(BlockType.TEXT),
            ),
            "Дневник" to listOf(
                b(BlockType.H3, "Что произошло сегодня"), b(BlockType.TEXT),
                b(BlockType.H3, "За что я благодарен"), b(BlockType.BULLET),
                b(BlockType.H3, "Чему научился"), b(BlockType.TEXT),
                b(BlockType.H3, "Настроение"), b(BlockType.TEXT),
            ),
            "Мысли" to listOf(
                b(BlockType.QUOTE, "Мысль или цитата"),
                b(BlockType.TEXT, "Почему это важно:"), b(BlockType.BULLET),
                b(BlockType.TEXT, "Что с этим делать:"), b(BlockType.TODO),
            ),
            "Список дел" to listOf(b(BlockType.TODO), b(BlockType.TODO), b(BlockType.TODO)),
            "Конспект книги" to listOf(
                b(BlockType.CALLOUT, "Автор, год, рейтинг"),
                b(BlockType.H2, "Главные идеи"), b(BlockType.NUMBER),
                b(BlockType.H2, "Цитаты"), b(BlockType.QUOTE),
                b(BlockType.H2, "Что применю"), b(BlockType.TODO),
            ),
        )
    }
}
