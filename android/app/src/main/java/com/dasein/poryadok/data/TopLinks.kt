package com.dasein.poryadok.data

import android.content.Context
import com.dasein.poryadok.Graph

/**
 * Связь «пункт топа → карточка фильма, сериала или книги» для любых топов (у «Что посмотреть» — своя, в WatchTop).
 * Хранится в ui_state, чтобы не менять базу: по связи в топе видны постеры, а нажатие открывает карточку.
 */
object TopLinks {
    private const val KEY = "top_links"
    private fun sp(ctx: Context) = ctx.getSharedPreferences("ui_state", Context.MODE_PRIVATE)

    fun all(ctx: Context): Map<Long, Long> = sp(ctx).getString(KEY, "").orEmpty().split(',').mapNotNull { p ->
        val (a, b) = p.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
        (a.toLongOrNull() ?: return@mapNotNull null) to (b.toLongOrNull() ?: return@mapNotNull null)
    }.toMap() + WatchTop.links(ctx)

    private fun save(ctx: Context, m: Map<Long, Long>) =
        sp(ctx).edit().putString(KEY, m.entries.joinToString(",") { "${it.key}:${it.value}" }).apply()

    fun mediaOf(ctx: Context, itemId: Long): Long? = WatchTop.mediaOf(ctx, itemId) ?: all(ctx)[itemId]

    fun link(ctx: Context, itemId: Long, mediaId: Long) {
        val own = all(ctx) - WatchTop.links(ctx).keys
        save(ctx, own + (itemId to mediaId))
    }

    /** Короткая подпись пункта по карточке: «Фильм · 2019 · драма · ★ 8,1». */
    fun noteOf(m: MediaItem): String = listOfNotNull(
        MediaKind.names[m.kind], m.year?.toString(), m.genres.split(',').firstOrNull()?.trim()?.ifBlank { null },
        if (m.myRating > 0) "моя ${m.myRating}/10" else m.externalRating?.let { "★ %.1f".format(it).replace('.', ',') },
    ).joinToString(" · ")

    /** Добавить карточки в топ (в конец, без повторов). Возвращает, сколько добавлено. */
    suspend fun addMedia(ctx: Context, listId: Long, items: List<MediaItem>): Int {
        val existing = Graph.db.backupDao().allTopItem().filter { it.listId == listId }
        val links = all(ctx)
        val have = existing.mapNotNull { links[it.id] }.toSet()
        var sort = (existing.maxOfOrNull { it.sort } ?: -1) + 1
        var n = 0
        items.filter { it.id !in have }.forEach { m ->
            val id = Graph.dao.upsertTopItem(TopItem(listId = listId, title = m.title, note = noteOf(m), rating = m.myRating.coerceIn(0, 10), sort = sort++))
            link(ctx, id, m.id); n++
        }
        return n
    }

    /** Новый топ из карточек коллекции (например, «Лучшие фильмы» по моим оценкам). */
    suspend fun createFrom(ctx: Context, title: String, glyph: String, items: List<MediaItem>): Long {
        val sort = Graph.db.backupDao().allTopList().size
        val id = Graph.dao.upsertTopList(TopList(title = title, emoji = glyph, sort = sort))
        addMedia(ctx, id, items)
        return id
    }
}
