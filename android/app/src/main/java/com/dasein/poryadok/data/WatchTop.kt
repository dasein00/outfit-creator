package com.dasein.poryadok.data

import android.content.Context
import com.dasein.poryadok.Graph
import com.dasein.poryadok.logic.MediaShelf
import kotlinx.coroutines.sync.withLock

/**
 * Топ «Что посмотреть» сам собирается из фильмов и сериалов со статусом «Хочу посмотреть» (или как его переименовали).
 * Новые запланированные добавляются в конец, просмотренные и удалённые — убираются. Порядок, оценки и комментарии в топе — ваши.
 * Связь «пункт топа → карточка» хранится в ui_state, поэтому сам топ можно переименовать и переставлять как обычный.
 */
object WatchTop {
    private const val ID = "watch_top_id"
    private const val LINKS = "watch_top_links"
    private const val OFF = "watch_top_off"
    private const val SKIP = "watch_top_skip"
    private val lock = kotlinx.coroutines.sync.Mutex()

    private fun sp(ctx: Context) = ctx.getSharedPreferences("ui_state", Context.MODE_PRIVATE)

    fun links(ctx: Context): Map<Long, Long> = sp(ctx).getString(LINKS, "").orEmpty().split(',').mapNotNull { p ->
        val (a, b) = p.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
        (a.toLongOrNull() ?: return@mapNotNull null) to (b.toLongOrNull() ?: return@mapNotNull null)
    }.toMap()

    fun listId(ctx: Context): Long = sp(ctx).getLong(ID, 0L)
    fun enabled(ctx: Context) = !sp(ctx).getBoolean(OFF, false)

    /** Включить заново (после удаления топа) или выключить. */
    fun setEnabled(ctx: Context, on: Boolean) = sp(ctx).edit().putBoolean(OFF, !on).apply()

    /** Карточка, к которой привязан пункт топа. */
    fun mediaOf(ctx: Context, itemId: Long): Long? = links(ctx)[itemId]

    private fun skipped(ctx: Context): Set<Long> = sp(ctx).getString(SKIP, "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }.toSet()

    suspend fun sync(ctx: Context) = lock.withLock { syncLocked(ctx) }

    private suspend fun syncLocked(ctx: Context) {
        if (!enabled(ctx)) return
        val dao = Graph.dao
        val b = Graph.db.backupDao()
        val lists = b.allTopList()
        var id = listId(ctx)
        if (lists.none { it.id == id }) {
            id = dao.upsertTopList(TopList(title = "Что посмотреть", emoji = "habit/01", sort = -1))
            sp(ctx).edit().putLong(ID, id).putString(LINKS, "").apply()
        }
        val media = Graph.extra.allMedia().filter { it.kind != MediaKind.BOOK }
        val planned = media.filter { it.status == MediaStatus.PLANNED }.sortedBy { it.createdAt }
        val items = b.allTopItem().filter { it.listId == id }
        val all = links(ctx)
        val linked = all.filterKeys { k -> items.any { it.id == k } }
        // Пункт удалили из топа руками — эту карточку больше не добавляем.
        val skip = skipped(ctx) + all.filterKeys { k -> items.none { it.id == k } }.values
        val (add0, remove) = MediaShelf.watchDiff(planned.map { it.id }, linked)
        val add = add0.filter { it !in skip }
        items.filter { it.id in remove }.forEach { dao.deleteTopItem(it) }
        val next = linked.filterKeys { it !in remove }.toMutableMap()
        var sort = (items.maxOfOrNull { it.sort } ?: -1) + 1
        planned.filter { it.id in add }.forEach { m ->
            val note = listOfNotNull(
                MediaKind.names[m.kind], m.year?.toString(), m.genres.split(',').firstOrNull()?.trim()?.ifBlank { null },
                m.externalRating?.let { "★ %.1f".format(it).replace('.', ',') },
            ).joinToString(" · ")
            val itemId = dao.upsertTopItem(TopItem(listId = id, title = m.title, note = note, sort = sort++))
            next[itemId] = m.id
        }
        sp(ctx).edit().putString(LINKS, next.entries.joinToString(",") { "${it.key}:${it.value}" })
            .putString(SKIP, skip.filter { m -> planned.any { it.id == m } }.joinToString(",")).apply()
    }

    /** Топ удалили вручную — больше не создаём, пока не включат снова. */
    fun onDeleted(ctx: Context, listId: Long) {
        if (listId == listId(ctx)) sp(ctx).edit().putBoolean(OFF, true).remove(ID).remove(LINKS).apply()
    }
}
