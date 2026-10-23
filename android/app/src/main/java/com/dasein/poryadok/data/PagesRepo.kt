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
            // Повседневные дела.
            "Список покупок" to listOf(
                b(BlockType.H3, "Овощи и фрукты"), b(BlockType.TODO),
                b(BlockType.H3, "Молочное и яйца"), b(BlockType.TODO),
                b(BlockType.H3, "Мясо и рыба"), b(BlockType.TODO),
                b(BlockType.H3, "Хлеб и крупы"), b(BlockType.TODO),
                b(BlockType.H3, "Бытовое и гигиена"), b(BlockType.TODO),
                b(BlockType.H3, "Аптека"), b(BlockType.TODO),
            ),
            "План дня" to listOf(
                b(BlockType.CALLOUT, "Главное дело дня"),
                b(BlockType.H3, "Утро"), b(BlockType.TODO),
                b(BlockType.H3, "День"), b(BlockType.TODO),
                b(BlockType.H3, "Вечер"), b(BlockType.TODO),
                b(BlockType.H3, "Не забыть"), b(BlockType.BULLET),
            ),
            "План недели" to listOf(
                b(BlockType.CALLOUT, "Три главные цели недели"),
                b(BlockType.H3, "Понедельник"), b(BlockType.TODO), b(BlockType.H3, "Вторник"), b(BlockType.TODO),
                b(BlockType.H3, "Среда"), b(BlockType.TODO), b(BlockType.H3, "Четверг"), b(BlockType.TODO),
                b(BlockType.H3, "Пятница"), b(BlockType.TODO), b(BlockType.H3, "Выходные"), b(BlockType.TODO),
            ),
            "Меню на неделю" to listOf(
                b(BlockType.H3, "Понедельник"), b(BlockType.BULLET, "Завтрак: "), b(BlockType.BULLET, "Обед: "), b(BlockType.BULLET, "Ужин: "),
                b(BlockType.H3, "Вторник"), b(BlockType.BULLET, "Завтрак: "), b(BlockType.BULLET, "Обед: "), b(BlockType.BULLET, "Ужин: "),
                b(BlockType.H3, "Среда"), b(BlockType.BULLET, "Завтрак: "), b(BlockType.BULLET, "Обед: "), b(BlockType.BULLET, "Ужин: "),
                b(BlockType.H3, "Четверг"), b(BlockType.BULLET, "Завтрак: "), b(BlockType.BULLET, "Обед: "), b(BlockType.BULLET, "Ужин: "),
                b(BlockType.H3, "Пятница"), b(BlockType.BULLET, "Завтрак: "), b(BlockType.BULLET, "Обед: "), b(BlockType.BULLET, "Ужин: "),
                b(BlockType.H3, "Выходные"), b(BlockType.BULLET, "Завтрак: "), b(BlockType.BULLET, "Обед: "), b(BlockType.BULLET, "Ужин: "),
                b(BlockType.H2, "Купить"), b(BlockType.TODO),
            ),
            "Сборы в поездку" to listOf(
                b(BlockType.CALLOUT, "Куда, даты, погода"),
                b(BlockType.H3, "Документы и деньги"), b(BlockType.TODO, "Паспорт"), b(BlockType.TODO, "Билеты"), b(BlockType.TODO, "Карта и наличные"),
                b(BlockType.H3, "Одежда"), b(BlockType.TODO),
                b(BlockType.H3, "Гигиена и аптечка"), b(BlockType.TODO, "Зубная щётка и паста"), b(BlockType.TODO, "Лекарства"),
                b(BlockType.H3, "Техника"), b(BlockType.TODO, "Телефон и зарядка"), b(BlockType.TODO, "Пауэрбанк"),
                b(BlockType.H3, "Перед выходом"), b(BlockType.TODO, "Выключить свет и воду"), b(BlockType.TODO, "Закрыть окна"),
            ),
            "Уборка" to listOf(
                b(BlockType.H3, "Каждый день"), b(BlockType.TODO, "Помыть посуду"), b(BlockType.TODO, "Протереть столы"), b(BlockType.TODO, "Вынести мусор"),
                b(BlockType.H3, "Раз в неделю"), b(BlockType.TODO, "Пропылесосить"), b(BlockType.TODO, "Помыть полы"), b(BlockType.TODO, "Сменить постельное бельё"), b(BlockType.TODO, "Ванная и туалет"),
                b(BlockType.H3, "Раз в месяц"), b(BlockType.TODO, "Холодильник"), b(BlockType.TODO, "Духовка и плита"), b(BlockType.TODO, "Окна и зеркала"),
            ),
            "Бюджет на месяц" to listOf(
                b(BlockType.CALLOUT, "Доход за месяц: "),
                b(BlockType.H3, "Обязательные платежи"), b(BlockType.TODO, "Аренда / ипотека: "), b(BlockType.TODO, "Коммунальные: "), b(BlockType.TODO, "Связь и интернет: "),
                b(BlockType.H3, "Продукты и быт"), b(BlockType.BULLET),
                b(BlockType.H3, "Накопления"), b(BlockType.BULLET),
                b(BlockType.H3, "Крупные покупки"), b(BlockType.TODO),
            ),
            "Встреча / звонок" to listOf(
                b(BlockType.CALLOUT, "С кем, когда, где"),
                b(BlockType.H3, "Вопросы"), b(BlockType.NUMBER),
                b(BlockType.H3, "Договорились"), b(BlockType.BULLET),
                b(BlockType.H3, "Что сделать после"), b(BlockType.TODO),
            ),
            "Подарки" to listOf(
                b(BlockType.H3, "Кому и к какому дню"), b(BlockType.BULLET),
                b(BlockType.H3, "Идеи"), b(BlockType.BULLET),
                b(BlockType.H3, "Купить"), b(BlockType.TODO),
            ),
            "Дела по дому" to listOf(
                b(BlockType.H3, "Починить"), b(BlockType.TODO),
                b(BlockType.H3, "Оплатить"), b(BlockType.TODO),
                b(BlockType.H3, "Записаться"), b(BlockType.TODO, "Врач"), b(BlockType.TODO, "Парикмахер"),
                b(BlockType.H3, "Позвонить"), b(BlockType.TODO),
            ),
            "Конспект книги" to listOf(
                b(BlockType.CALLOUT, "Автор, год, рейтинг"),
                b(BlockType.H2, "Главные идеи"), b(BlockType.NUMBER),
                b(BlockType.H2, "Цитаты"), b(BlockType.QUOTE),
                b(BlockType.H2, "Что применю"), b(BlockType.TODO),
            ),
        )
    }
}
