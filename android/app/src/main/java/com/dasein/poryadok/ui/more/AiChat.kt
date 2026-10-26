package com.dasein.poryadok.ui.more

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.system.Ai
import com.dasein.poryadok.ui.common.ConfirmDialog
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.Glyphs
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.Text
import com.dasein.poryadok.ui.common.TextButton
import com.dasein.poryadok.ui.common.btnLabel
import com.dasein.poryadok.ui.common.rememberImage
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val AI_CHAT_ROUTE = "aiChat"

/**
 * ИИ-чат: обычный разговор с бесплатным ИИ (OpenRouter, при сбое — запасные Groq, Gemini, Mistral) и разбор вложений:
 * фото, файлы (текст, PDF, Word) и видео (кадры + распознанная речь). Переписки хранятся на телефоне в files/ai_chat.
 */
object AiChats {
    @Serializable
    data class Msg(
        val role: String,
        val text: String,
        /** Картинки (фото и кадры видео) — пути к JPEG в files/ai_chat/media. */
        val images: List<String> = emptyList(),
        /** Подписи вложений для показа: «📄 отчёт.pdf», «🎬 видео.mp4». */
        val files: List<String> = emptyList(),
        /** Текст из файлов и речь из видео — уходит ИИ вместе с сообщением. */
        val extra: String = "",
        val at: Long = 0,
    )

    @Serializable
    data class Chat(val id: Long, val title: String = "", val msgs: List<Msg> = emptyList(), val updatedAt: Long = 0)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun dir(ctx: Context) = File(ctx.filesDir, "ai_chat").apply { mkdirs() }
    fun mediaDir(ctx: Context) = File(dir(ctx), "media").apply { mkdirs() }
    private fun file(ctx: Context, id: Long) = File(dir(ctx), "c$id.json")

    fun list(ctx: Context): List<Chat> = dir(ctx).listFiles { f -> f.name.endsWith(".json") }.orEmpty()
        .mapNotNull { runCatching { json.decodeFromString(Chat.serializer(), it.readText()) }.getOrNull() }.sortedByDescending { it.updatedAt }

    fun load(ctx: Context, id: Long): Chat? = runCatching { json.decodeFromString(Chat.serializer(), file(ctx, id).readText()) }.getOrNull()
    fun save(ctx: Context, c: Chat) { runCatching { file(ctx, c.id).writeText(json.encodeToString(Chat.serializer(), c)) } }
    fun delete(ctx: Context, c: Chat) {
        c.msgs.flatMap { it.images }.forEach { runCatching { File(it).delete() } }
        file(ctx, c.id).delete()
    }

    const val SYSTEM = "Ты дружелюбный и толковый ИИ-помощник в приложении DASEIN. Отвечай по-русски, ясно и по делу; " +
        "если вопрос сложный — по шагам, с короткими абзацами и списками. Если прислали фото — внимательно опиши и разбери, что на нём важно для вопроса. " +
        "Если прислали текст файла — опирайся на него и цитируй нужные места. Если прислали кадры видео и расшифровку речи — " +
        "разбери видео целиком: что происходит, о чём говорят, главные мысли. Не выдумывай факты; если не уверен — так и скажи."

    /** Реплики для ИИ: текст сообщения + текст вложений; картинки читаются с диска. */
    fun turns(msgs: List<Msg>): List<Ai.Turn> = msgs.mapIndexed { i, m ->
        val text = listOf(m.text, m.extra).filter { it.isNotBlank() }.joinToString("\n\n")
        val imgs = if (i >= msgs.size - 4) m.images.mapNotNull { runCatching { File(it).readBytes() }.getOrNull() } else emptyList()
        Ai.Turn(m.role, text, imgs)
    }

    /** Название чата — по первому вопросу. */
    fun titleOf(text: String) = text.trim().replace('\n', ' ').take(40).ifBlank { "Чат" }
}

/** Вложение, готовое к отправке. */
private data class Attach(val label: String, val images: List<String> = emptyList(), val text: String = "")

private fun nameOf(ctx: Context, uri: Uri): String = runCatching {
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}.getOrNull() ?: uri.lastPathSegment.orEmpty().substringAfterLast('/').ifBlank { "файл" }

/** Картинка → JPEG до [max] px по длинной стороне в папке чата; путь или null. */
private fun saveJpeg(ctx: Context, bmp: Bitmap, max: Int = 1600): String? = runCatching {
    val k = max.toFloat() / maxOf(bmp.width, bmp.height)
    val fit = if (k < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt(), true) else bmp
    val f = File(AiChats.mediaDir(ctx), "i${System.nanoTime()}.jpg")
    f.outputStream().use { fit.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    f.absolutePath
}.getOrNull()

private fun decodeImage(ctx: Context, uri: Uri): Bitmap? = runCatching {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
    var s = 1
    while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= 1600) s *= 2
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }
}.getOrNull()

/** Текст из файла: обычный текст, PDF, Word (.docx). До 30 000 знаков. */
private fun fileText(ctx: Context, uri: Uri, name: String, mime: String): String? = runCatching {
    val low = name.lowercase()
    val t = when {
        mime == "application/pdf" || low.endsWith(".pdf") -> ctx.contentResolver.openInputStream(uri)?.use { input ->
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(ctx)
            com.tom_roush.pdfbox.pdmodel.PDDocument.load(input).use { com.tom_roush.pdfbox.text.PDFTextStripper().getText(it) }
        }
        low.endsWith(".docx") -> ctx.contentResolver.openInputStream(uri)?.use { input ->
            val zip = java.util.zip.ZipInputStream(input)
            var xml: String? = null
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.name == "word/document.xml") { xml = zip.readBytes().decodeToString(); break }
            }
            xml?.replace(Regex("</w:p>"), "\n")?.replace(Regex("<[^>]+>"), "")
        }
        else -> ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
    }
    t?.trim()?.take(30_000)?.takeIf { it.isNotBlank() }
}.getOrNull()

/** Видео → до 6 кадров и (если получится за 3 минуты) расшифровка речи. */
private suspend fun videoAttach(ctx: Context, uri: Uri, name: String, onStage: (String) -> Unit): Attach = withContext(Dispatchers.IO) {
    onStage("Беру кадры из видео…")
    val frames = runCatching {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val n = 6
            (0 until n).mapNotNull { i ->
                val t = if (dur > 0) dur * (2 * i + 1) / (2 * n) else 0L
                r.getFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { saveJpeg(ctx, it, 900) }
            }
        } finally { runCatching { r.release() } }
    }.getOrDefault(emptyList())
    onStage("Распознаю речь в видео…")
    val speech = withTimeoutOrNull(180_000) { runCatching { com.dasein.poryadok.system.VideoSpeech.transcribe(ctx, uri) { onStage(it) } }.getOrNull() }
    val text = buildString {
        append("[Видео «$name»: ${frames.size} кадров по порядку")
        if (!speech.isNullOrBlank()) append("; расшифровка речи ниже]\n$speech") else append("; речи нет или не распознана]")
    }
    Attach("🎬 $name", frames, text)
}

@Composable
fun AiChatScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    val aiOn = remember { Ai.enabled(ctx) }
    var chat by remember { mutableStateOf(AiChats.list(ctx).firstOrNull() ?: AiChats.Chat(System.currentTimeMillis())) }
    var input by remember { mutableStateOf("") }
    var attachments by remember { mutableStateOf<List<Attach>>(emptyList()) }
    var preparing by remember { mutableStateOf<String?>(null) }
    var thinking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showChats by remember { mutableStateOf(false) }
    var attachMenu by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    LaunchedEffect(chat.msgs.size, thinking) { if (chat.msgs.isNotEmpty()) list.animateScrollToItem(chat.msgs.size - 1 + (if (thinking) 1 else 0)) }

    fun addUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            uris.forEach { uri ->
                val mime = ctx.contentResolver.getType(uri).orEmpty()
                val name = nameOf(ctx, uri)
                preparing = "Готовлю «$name»…"
                val a = when {
                    mime.startsWith("image/") -> withContext(Dispatchers.IO) { decodeImage(ctx, uri)?.let { saveJpeg(ctx, it) } }?.let { Attach("🖼 $name", listOf(it)) }
                    mime.startsWith("video/") -> videoAttach(ctx, uri, name) { preparing = it }
                    else -> withContext(Dispatchers.IO) { fileText(ctx, uri, name, mime) }?.let { Attach("📄 $name", text = "[Файл «$name»]\n$it") }
                }
                if (a != null) attachments = attachments + a
                else Toast.makeText(ctx, "Не удалось прочитать «$name»", Toast.LENGTH_SHORT).show()
            }
            preparing = null
        }
    }
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { addUris(it) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { addUris(it) }

    fun send() {
        val text = input.trim()
        if ((text.isEmpty() && attachments.isEmpty()) || thinking) return
        val msg = AiChats.Msg(
            "user", text, attachments.flatMap { it.images }, attachments.map { it.label },
            attachments.map { it.text }.filter { it.isNotBlank() }.joinToString("\n\n"), System.currentTimeMillis(),
        )
        val started = chat.copy(
            title = chat.title.ifBlank { AiChats.titleOf(text.ifBlank { attachments.firstOrNull()?.label.orEmpty() }) },
            msgs = chat.msgs + msg, updatedAt = System.currentTimeMillis(),
        )
        chat = started; input = ""; attachments = emptyList(); error = null; thinking = true
        AiChats.save(ctx, started)
        scope.launch {
            runCatching { Ai.chat(ctx, AiChats.SYSTEM, AiChats.turns(started.msgs)) }
                .onSuccess { answer ->
                    val done = started.copy(msgs = started.msgs + AiChats.Msg("assistant", answer.trim(), at = System.currentTimeMillis()), updatedAt = System.currentTimeMillis())
                    if (chat.id == started.id) chat = done
                    AiChats.save(ctx, done)
                }
                .onFailure { error = it.message ?: "ИИ не ответил" }
            thinking = false
        }
    }

    Screen(
        "ИИ-чат", onBack = { nav.popBackStack() },
        actions = {
            IconButton(onClick = { showChats = true }) { Icon(Icons.AutoMirrored.Filled.List, "Все чаты") }
            IconButton(onClick = { chat = AiChats.Chat(System.currentTimeMillis()); error = null; attachments = emptyList() }) { Icon(Icons.Default.Add, "Новый чат") }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (chat.msgs.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Glyph(Glyphs.AI, 56.dp, badge = false)
                        Text(btnLabel("Спросите что угодно"), fontWeight = FontWeight.SemiBold, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp))
                        Text(
                            if (aiOn) "Можно прикрепить фото, файл (текст, PDF, Word) или видео — ИИ разберёт кадры и речь. Переписка хранится только на телефоне."
                            else "Нужен бесплатный ключ ИИ: «Настройки» → «ИИ-помощник».",
                            fontSize = 13.sp, color = extra.dim, modifier = Modifier.padding(top = 6.dp, start = 16.dp, end = 16.dp),
                        )
                    }
                }
                items(chat.msgs.size) { i -> Bubble(chat.msgs[i]) }
                if (thinking) item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(6.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("  ИИ думает…", fontSize = 13.sp, color = extra.dim)
                    }
                }
                error?.let { e -> item { Text(e, color = extra.danger, fontSize = 13.sp, modifier = Modifier.padding(6.dp)) } }
            }
            // Вложения перед отправкой.
            if (attachments.isNotEmpty() || preparing != null) LazyRow(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(attachments) { a ->
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(extra.card).padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(a.label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp))
                        Icon(Icons.Default.Close, "Убрать", Modifier.size(22.dp).clip(CircleShape).clickable { attachments = attachments - a }.padding(3.dp))
                    }
                }
                preparing?.let { p -> item { Text(p, fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(6.dp)) } }
            }
            // Строка ввода — над клавиатурой (отступ от неё уже в pad).
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom) {
                Box {
                    IconButton(onClick = { attachMenu = true }, enabled = preparing == null) { Icon(Icons.Default.AttachFile, "Прикрепить") }
                    DropdownMenu(attachMenu, { attachMenu = false }) {
                        DropdownMenuItem({ Text(btnLabel("Фото или видео из галереи")) }, {
                            attachMenu = false
                            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        })
                        DropdownMenuItem({ Text(btnLabel("Файл (текст, PDF, Word, видео)")) }, { attachMenu = false; pickFile.launch(arrayOf("*/*")) })
                    }
                }
                OutlinedTextField(
                    input, { input = it }, Modifier.weight(1f).heightIn(max = 160.dp), shape = RoundedCornerShape(18.dp),
                    placeholder = { Text("Сообщение") }, maxLines = 6,
                )
                IconButton(onClick = { send() }, enabled = !thinking && preparing == null && (input.isNotBlank() || attachments.isNotEmpty())) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Отправить", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    if (showChats) ChatsDialog(onPick = { chat = it; showChats = false; error = null }, onDismiss = { showChats = false }, current = chat.id) {
        if (it.id == chat.id) chat = AiChats.Chat(System.currentTimeMillis())
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Bubble(m: AiChats.Msg) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val mine = m.role == "user"
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(18.dp))
                .background(if (mine) MaterialTheme.colorScheme.primary.copy(alpha = .16f) else extra.card)
                .combinedClickable(onClick = {}, onLongClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("DASEIN", m.text))
                    Toast.makeText(ctx, "Текст скопирован", Toast.LENGTH_SHORT).show()
                })
                .padding(12.dp),
        ) {
            if (m.images.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                items(m.images) { p ->
                    val img by rememberImage(p, 400)
                    img?.let { Image(it, null, Modifier.size(84.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop) }
                }
            }
            m.files.filter { !it.startsWith("🖼") }.forEach { Text(it, fontSize = 12.sp, color = extra.dim) }
            if (m.text.isNotBlank()) SelectionContainer { Text(m.text, fontSize = 15.sp, lineHeight = 21.sp) }
        }
    }
}

@Composable
private fun ChatsDialog(onPick: (AiChats.Chat) -> Unit, onDismiss: () -> Unit, current: Long, onDeleted: (AiChats.Chat) -> Unit) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    var chats by remember { mutableStateOf(AiChats.list(ctx)) }
    var deleting by remember { mutableStateOf<AiChats.Chat?>(null) }
    val fmt = remember { SimpleDateFormat("d MMM, HH:mm", Locale("ru")) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(btnLabel("Все чаты")) },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                if (chats.isEmpty()) item { Text(btnLabel("Чатов пока нет."), color = extra.dim) }
                items(chats, key = { it.id }) { c ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(c) }.padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(c.title.ifBlank { "Чат" }, fontWeight = if (c.id == current) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${c.msgs.size} сообщ. · ${fmt.format(Date(c.updatedAt))}", fontSize = 12.sp, color = extra.dim)
                        }
                        IconButton(onClick = { deleting = c }) { Icon(Icons.Default.Close, "Удалить") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(btnLabel("Закрыть")) } },
    )
    deleting?.let { c ->
        ConfirmDialog("Удалить чат?", c.title.ifBlank { "Чат" }, onDismiss = { deleting = null }) {
            AiChats.delete(ctx, c); chats = AiChats.list(ctx); onDeleted(c); deleting = null
        }
    }
}
