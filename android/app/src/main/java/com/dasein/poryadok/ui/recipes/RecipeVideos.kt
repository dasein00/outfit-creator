@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.recipes

import android.content.Intent
import com.dasein.poryadok.ui.common.btnLabel
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import com.dasein.poryadok.ui.common.OutlinedButton
import com.dasein.poryadok.ui.common.Text
import com.dasein.poryadok.ui.common.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.Recipe
import com.dasein.poryadok.ui.common.ConfirmDialog
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.HGap
import com.dasein.poryadok.ui.common.Media
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.launch
import java.io.File

/** Видео к рецепту: файлы из галереи (копируются в папку приложения) и ссылки (YouTube, VK, Rutube…). */
object RecipeVideos {
    fun list(r: Recipe): List<String> = r.videos.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith(SRC) }

    /** Источник рецепта (ссылка на пост, откуда взяли описание) хранится в том же поле строкой «src:…». */
    private const val SRC = "src:"
    fun source(r: Recipe): String? = r.videos.lines().map { it.trim() }.firstOrNull { it.startsWith(SRC) }?.removePrefix(SRC)?.takeIf { it.isNotBlank() }
    fun join(videos: List<String>, source: String?): String = (videos + listOfNotNull(source?.let { SRC + it })).joinToString("\n")
    fun withSource(r: Recipe, url: String): Recipe = r.copy(videos = join(list(r), url.trim()))
    fun isLink(v: String) = v.startsWith("http://") || v.startsWith("https://")

    fun normalizeLink(text: String): String? {
        val t = text.trim()
        if (t.isEmpty() || t.any { it.isWhitespace() }) return null
        val url = if (isLink(t)) t else "https://$t"
        return url.takeIf { runCatching { Uri.parse(it).host }.getOrNull()?.contains('.') == true }
    }

    fun hostLabel(url: String): String {
        val host = runCatching { Uri.parse(url).host }.getOrNull().orEmpty().removePrefix("www.").removePrefix("m.")
        return when {
            host.contains("youtu") -> "YouTube"
            host.contains("rutube") -> "Rutube"
            host.contains("vk.com") || host.contains("vkvideo") -> "VK Видео"
            host.contains("dzen") -> "Дзен"
            host.contains("tiktok") -> "TikTok"
            host.contains("instagram") -> "Instagram"
            else -> host.ifEmpty { "Ссылка" }
        }
    }
}

/** Видео в карточке рецепта: изменения сразу сохраняются в базе. */
@Composable
fun RecipeVideosSection(r: Recipe) {
    RecipeVideosEditor(r.id, RecipeVideos.list(r), deleteFiles = true) { list ->
        io { Graph.extra.setVideos(r.id, RecipeVideos.join(list, RecipeVideos.source(r))) }
    }
    RecipeSourceButton(r)
}

/** «Источник» — открыть пост или страницу, откуда взято описание рецепта. */
@Composable
fun RecipeSourceButton(r: Recipe) {
    val ctx = LocalContext.current
    val url = RecipeVideos.source(r) ?: return
    com.dasein.poryadok.ui.common.OutlinedButton(
        onClick = {
            runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { android.widget.Toast.makeText(ctx, "Не удалось открыть ссылку", android.widget.Toast.LENGTH_SHORT).show() }
        },
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    ) { com.dasein.poryadok.ui.common.Text("🔗 Источник · " + RecipeVideos.hostLabel(url)) }
}

/** Список видео рецепта с добавлением из галереи и по ссылке. [deleteFiles] — удалять файл при удалении из списка. */
@Composable
fun RecipeVideosEditor(recipeId: Long, videos: List<String>, deleteFiles: Boolean, onChange: (List<String>) -> Unit) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    var addLink by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf<String?>(null) }
    fun save(list: List<String>) = onChange(list.distinct())

    com.dasein.poryadok.ui.common.FoldSection("recipe_videos", "Видео рецепта") {
    if (videos.isEmpty()) Text(
        "Прикрепите ролик из галереи или ссылку на видео (YouTube, VK, Rutube) — он будет под рукой во время готовки.",
        fontSize = 13.sp, color = extra.dim, modifier = Modifier.padding(bottom = 8.dp),
    )
    videos.forEachIndexed { i, v ->
        // Как у упражнений в «Тренировках»: проигрыватель, «Монтаж», «Заменить», «Сжать», «Убрать».
        var replacing by remember(v) { mutableStateOf(false) }
        Tile(Modifier.padding(bottom = 8.dp), padding = 10.dp) {
            if (RecipeVideos.isLink(v)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("▶ ${RecipeVideos.hostLabel(v)}", fontWeight = FontWeight.SemiBold)
                        Text(v, fontSize = 12.sp, color = extra.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(onClick = {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(v))) }
                            .onFailure { Toast.makeText(ctx, "Нет приложения, чтобы открыть ссылку", Toast.LENGTH_SHORT).show() }
                    }) { Text(btnLabel("Смотреть")) }
                }
            } else if (File(v).exists()) {
                com.dasein.poryadok.ui.common.VideoPlayer(v, Modifier.fillMaxWidth(), maxHeight = 360.dp)
            } else {
                Text(btnLabel("Файл видео не найден"), color = extra.dim)
            }
            val size = if (RecipeVideos.isLink(v)) 0L else File(v).length()
            // Старый файл после сжатия, монтажа или замены — путь обновляем во всех сохранённых рецептах с ним
            // (и в этом тоже, даже если правка ещё не сохранена), иначе видео «пропало» бы при выходе без сохранения.
            val swap: (String) -> Unit = { new ->
                save(videos.map { if (it == v) new else it })
                com.dasein.poryadok.system.VideoSpeech.prefetch(ctx, new)
                io {
                    Graph.extra.recipesNow().filter { v in RecipeVideos.list(it) }
                        .forEach { r -> Graph.extra.setVideos(r.id, RecipeVideos.list(r).map { x -> if (x == v) new else x }.joinToString("\n")) }
                }
            }
            if (size > 0) Text(
                "Видео ${i + 1} · ${com.dasein.poryadok.system.VideoCompress.mb(size)}",
                fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp),
            )
            androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                if (size > 0) com.dasein.poryadok.ui.common.EditVideoButton(v, swap)
                if (size > 0) TextButton(onClick = { replacing = !replacing }) { Text(btnLabel(if (replacing) "Не заменять" else "Заменить")) }
                if (size > 0) com.dasein.poryadok.ui.common.CompressVideoButton(v, swap)
                TextButton(onClick = { remove = v }) { Text(btnLabel("Убрать"), color = extra.danger) }
            }
            if (replacing) {
                Text("Новое видео встанет на место этого — сначала откроется монтаж.", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(vertical = 4.dp))
                com.dasein.poryadok.ui.common.VideoAddButtons("recipes/video", galleryLabel = "Из галереи", filesLabel = "Из файлов") { new ->
                    replacing = false
                    swap(new)
                    // Прежний файл больше не нужен, если его нет в других рецептах.
                    if (deleteFiles) io {
                        if (Graph.extra.recipesNow().none { it.id != recipeId && v in RecipeVideos.list(it) }) runCatching { File(v).delete() }
                    }
                }
            }
        }
    }
    Row {
        Column(Modifier.weight(1f)) {
            // Речь из нового видео распознаётся сразу в фоне — для «Рецепта из видео» его не нужно загружать второй раз.
            com.dasein.poryadok.ui.common.VideoAddButtons("recipes/video") { path -> save(videos + path); com.dasein.poryadok.system.VideoSpeech.prefetch(ctx, path) }
            OutlinedButton(onClick = { addLink = true }, Modifier.fillMaxWidth()) { Text(btnLabel("+ Ссылка на видео (YouTube, VK, Rutube)")) }
        }
    }

    }
    if (addLink) {
        var text by remember { mutableStateOf("") }
        val url = RecipeVideos.normalizeLink(text)
        AlertDialog(
            onDismissRequest = { addLink = false },
            title = { Text(btnLabel("Ссылка на видео")) },
            text = {
                Column {
                    TextInput(text, { text = it }, "https://…")
                    Gap(6.dp)
                    Text("Видео откроется в приложении YouTube, VK, Rutube или в браузере.", fontSize = 12.sp, color = extra.dim)
                }
            },
            confirmButton = {
                TextButton(enabled = url != null, onClick = { url?.let { save(videos + it) }; addLink = false }) { Text(btnLabel("Добавить")) }
            },
            dismissButton = { TextButton(onClick = { addLink = false }) { Text(btnLabel("Отмена")) } },
        )
    }
    remove?.let { v ->
        ConfirmDialog(
            title = "Убрать видео?",
            text = if (RecipeVideos.isLink(v) || !deleteFiles) "Видео будет убрано из рецепта." else "Файл видео будет удалён из приложения.",
            confirm = "Убрать",
            onDismiss = { remove = null },
        ) {
            save(videos - v)
            // Файл удаляем, только если он не прикреплён к другому рецепту (например, к копии).
            if (deleteFiles && !RecipeVideos.isLink(v)) io {
                if (Graph.extra.recipesNow().none { it.id != recipeId && v in RecipeVideos.list(it) }) runCatching { File(v).delete() }
            }
            remove = null
        }
    }
}

