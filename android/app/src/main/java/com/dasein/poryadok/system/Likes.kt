package com.dasein.poryadok.system

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.dasein.poryadok.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * «Понравилось»: события истории, культуры, факты Small Talks и праздники, отмеченные «+».
 * Хранится в files/likes (попадает в резервную копию). Выгрузка — файл, который можно отправить
 * Claude, чтобы подобрать больше похожих новостей и фактов.
 */
object Likes {
    const val HISTORY = "history"
    const val CULTURE = "culture"
    const val FACT = "fact"
    const val HOLIDAY = "holiday"
    val KIND_NAMES = linkedMapOf(HISTORY to "История", CULTURE to "Культура", FACT to "Интересные факты", HOLIDAY to "Праздники")

    @Serializable
    data class Like(
        val kind: String,
        val title: String,
        val text: String = "",
        /** Дата в календаре (ММ-ДД), если есть. */
        val date: String = "",
        val year: Int = 0,
        /** Тема, страна, категория — чтобы было понятно, что именно нравится. */
        val tags: List<String> = emptyList(),
        val at: Long = 0,
    ) {
        val key get() = "$kind|$year|${title.trim().lowercase()}"
    }

    @Serializable
    private data class Store(val likes: List<Like> = emptyList())

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val state = MutableStateFlow<List<Like>>(emptyList())
    @Volatile private var loaded = false

    private fun file(ctx: Context) = File(File(ctx.filesDir, "likes").apply { mkdirs() }, "likes.json")

    fun flow(ctx: Context): StateFlow<List<Like>> {
        if (!loaded) {
            loaded = true
            state.value = runCatching { json.decodeFromString(Store.serializer(), file(ctx).readText()).likes }.getOrDefault(emptyList())
        }
        return state
    }

    fun isLiked(list: List<Like>, l: Like) = list.any { it.key == l.key }

    fun toggle(ctx: Context, l: Like) {
        val cur = flow(ctx).value
        val next = if (isLiked(cur, l)) cur.filter { it.key != l.key } else listOf(l.copy(at = System.currentTimeMillis())) + cur
        state.value = next
        val f = file(ctx)
        Graph.scope.launch(Dispatchers.IO) { runCatching { f.writeText(json.encodeToString(Store.serializer(), Store(next))) } }
    }

    fun remove(ctx: Context, l: Like) { if (isLiked(flow(ctx).value, l)) toggle(ctx, l) }

    /** Текст для выгрузки: понятен и человеку, и Claude (с просьбой подобрать похожее). */
    fun exportText(list: List<Like>): String = buildString {
        appendLine("DASEIN — что мне понравилось (${list.size})")
        appendLine("Выгружено: ${SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date())}")
        appendLine()
        appendLine("Подбери, пожалуйста, больше похожих исторических событий, культурных новостей, интересных фактов и праздников — по темам, странам и эпохам, которые здесь чаще встречаются.")
        KIND_NAMES.forEach { (k, name) ->
            val part = list.filter { it.kind == k }
            if (part.isEmpty()) return@forEach
            appendLine()
            appendLine("## $name (${part.size})")
            part.forEach { l ->
                append("- ")
                if (l.year != 0) append("${l.year}. ")
                append(l.title)
                if (l.date.isNotBlank()) append(" [${l.date}]")
                if (l.tags.isNotEmpty()) append(" {${l.tags.joinToString(", ")}}")
                appendLine()
                if (l.text.isNotBlank() && l.text != l.title) appendLine("  ${l.text.replace('\n', ' ').take(400)}")
            }
        }
    }

    /** Готовит файл и открывает «Поделиться» (Telegram, почта, диск, сохранить). */
    suspend fun share(ctx: Context) = withContext(Dispatchers.IO) {
        val list = flow(ctx).value
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "DASEIN — понравившееся.txt")
        f.writeText(exportText(list))
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "DASEIN — понравившееся").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        withContext(Dispatchers.Main) {
            ctx.startActivity(Intent.createChooser(send, "Выгрузить понравившееся").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
