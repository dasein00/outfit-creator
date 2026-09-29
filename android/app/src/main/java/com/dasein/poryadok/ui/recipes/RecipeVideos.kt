package com.dasein.poryadok.ui.recipes

import android.content.Intent
import android.net.Uri
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.viewinterop.AndroidView
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
    fun list(r: Recipe): List<String> = r.videos.lines().map { it.trim() }.filter { it.isNotEmpty() }
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
        io { Graph.extra.setVideos(r.id, list.joinToString("\n")) }
    }
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

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            Toast.makeText(ctx, "Копирую видео…", Toast.LENGTH_SHORT).show()
            val path = Media.importRaw(ctx, uri, "recipes/video")
            if (path == null) Toast.makeText(ctx, "Не удалось добавить видео", Toast.LENGTH_SHORT).show()
            else save(videos + path)
        }
    }

    SectionTitle("Видео рецепта")
    if (videos.isEmpty()) Text(
        "Прикрепите ролик из галереи или ссылку на видео (YouTube, VK, Rutube) — он будет под рукой во время готовки.",
        fontSize = 13.sp, color = extra.dim, modifier = Modifier.padding(bottom = 8.dp),
    )
    videos.forEachIndexed { i, v ->
        Tile(Modifier.padding(bottom = 8.dp)) {
            if (RecipeVideos.isLink(v)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("▶ ${RecipeVideos.hostLabel(v)}", fontWeight = FontWeight.SemiBold)
                        Text(v, fontSize = 12.sp, color = extra.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(onClick = {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(v))) }
                            .onFailure { Toast.makeText(ctx, "Нет приложения, чтобы открыть ссылку", Toast.LENGTH_SHORT).show() }
                    }) { Text("Смотреть") }
                }
            } else if (File(v).exists()) {
                LocalVideo(v, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Color.Black))
            } else {
                Text("Файл видео не найден", color = extra.dim)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Видео ${i + 1}", fontSize = 12.sp, color = extra.dim, modifier = Modifier.weight(1f))
                TextButton(onClick = { remove = v }) { Text("Убрать", color = extra.danger) }
            }
        }
    }
    Row {
        OutlinedButton(onClick = { picker.launch(arrayOf("video/*")) }, Modifier.weight(1f)) { Text("+ Из галереи") }
        HGap(8.dp)
        OutlinedButton(onClick = { addLink = true }, Modifier.weight(1f)) { Text("+ Ссылка") }
    }

    if (addLink) {
        var text by remember { mutableStateOf("") }
        val url = RecipeVideos.normalizeLink(text)
        AlertDialog(
            onDismissRequest = { addLink = false },
            title = { Text("Ссылка на видео") },
            text = {
                Column {
                    TextInput(text, { text = it }, "https://…")
                    Gap(6.dp)
                    Text("Видео откроется в приложении YouTube, VK, Rutube или в браузере.", fontSize = 12.sp, color = extra.dim)
                }
            },
            confirmButton = {
                TextButton(enabled = url != null, onClick = { url?.let { save(videos + it) }; addLink = false }) { Text("Добавить") }
            },
            dismissButton = { TextButton(onClick = { addLink = false }) { Text("Отмена") } },
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

/** Проигрыватель локального файла: не стартует сам, внизу стандартные кнопки. */
@Composable
private fun LocalVideo(path: String, modifier: Modifier) {
    AndroidView(
        factory = { c ->
            VideoView(c).apply {
                val mc = MediaController(c)
                mc.setAnchorView(this)
                setMediaController(mc)
                setVideoPath(path)
                setOnPreparedListener { seekTo(1) }
                setOnClickListener { if (isPlaying) pause() else start() }
            }
        },
        onRelease = { it.stopPlayback() },
        modifier = modifier,
    )
}
