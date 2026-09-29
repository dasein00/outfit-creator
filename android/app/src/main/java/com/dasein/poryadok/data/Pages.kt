package com.dasein.poryadok.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/** Страница заметок в стиле Notion: может лежать внутри другой страницы. */
@Serializable
@Entity(tableName = "pages", indices = [Index("parentId")])
data class Page(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parentId: Long? = null,
    val title: String = "",
    val icon: String = "",
    /** Обложка: путь к картинке или пусто. */
    val cover: String = "",
    val favorite: Boolean = false,
    val archived: Boolean = false,
    val sort: Int = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

object BlockType {
    const val TEXT = "text"
    const val H1 = "h1"
    const val H2 = "h2"
    const val H3 = "h3"
    const val BULLET = "bullet"
    const val NUMBER = "number"
    const val TODO = "todo"
    const val QUOTE = "quote"
    const val CALLOUT = "callout"
    const val CODE = "code"
    const val DIVIDER = "divider"
    const val IMAGE = "image"
    const val PAGE = "page"
    const val TOGGLE = "toggle"

    /** Блоки, в которых Enter продолжает тот же тип. */
    val CONTINUES = setOf(BULLET, NUMBER, TODO)
    val TEXTUAL = setOf(TEXT, H1, H2, H3, BULLET, NUMBER, TODO, QUOTE, CALLOUT, CODE, TOGGLE)
}

/**
 * Блок страницы. text — текст с разметкой (**жирный**, *курсив*, ~~зачёркнутый~~, `код`, ==выделение==).
 * media — путь к картинке/GIF (для image), id страницы (для page) или скрытый текст (для toggle).
 */
@Serializable
@Entity(tableName = "page_blocks", indices = [Index("pageId")])
data class PageBlock(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pageId: Long,
    val pos: Int = 0,
    val type: String = BlockType.TEXT,
    val text: String = "",
    val checked: Boolean = false,
    val media: String = "",
    val indent: Int = 0,
)

@Dao
interface PageDao {
    @Query("SELECT * FROM pages ORDER BY sort, createdAt") fun pages(): Flow<List<Page>>
    @Query("SELECT * FROM pages") suspend fun pagesNow(): List<Page>
    @Query("SELECT * FROM pages WHERE id = :id") fun page(id: Long): Flow<Page?>
    @Query("SELECT * FROM pages WHERE id = :id") suspend fun pageNow(id: Long): Page?
    @Upsert suspend fun upsertPage(p: Page): Long
    @Delete suspend fun deletePage(p: Page)
    @Query("UPDATE pages SET updatedAt = :at WHERE id = :id") suspend fun touch(id: Long, at: Long)

    @Query("SELECT * FROM page_blocks WHERE pageId = :pageId ORDER BY pos") fun blocks(pageId: Long): Flow<List<PageBlock>>
    @Query("SELECT * FROM page_blocks WHERE pageId = :pageId ORDER BY pos") suspend fun blocksNow(pageId: Long): List<PageBlock>
    @Query("SELECT * FROM page_blocks") suspend fun allBlocks(): List<PageBlock>
    @Query("SELECT * FROM page_blocks WHERE type IN ('text','h1','h2','h3','bullet','number','todo','quote','callout','code','toggle')") fun textBlocks(): Flow<List<PageBlock>>
    @Upsert suspend fun upsertBlock(b: PageBlock): Long
    @Upsert suspend fun upsertBlocks(b: List<PageBlock>)
    @Query("DELETE FROM page_blocks WHERE id = :id") suspend fun deleteBlock(id: Long)
    @Query("DELETE FROM page_blocks WHERE pageId = :pageId") suspend fun deleteBlocksOf(pageId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putPages(items: List<Page>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putBlocks(items: List<PageBlock>)
    @Query("DELETE FROM pages") suspend fun wipePages()
    @Query("DELETE FROM page_blocks") suspend fun wipeBlocks()
}
