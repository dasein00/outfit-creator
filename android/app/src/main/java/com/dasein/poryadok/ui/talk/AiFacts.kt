package com.dasein.poryadok.ui.talk

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import com.dasein.poryadok.ui.common.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dasein.poryadok.logic.SmallTalk
import com.dasein.poryadok.system.Ai
import com.dasein.poryadok.system.Likes
import com.dasein.poryadok.system.TalkOnline
import com.dasein.poryadok.ui.common.AiButton
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.Glyphs
import com.dasein.poryadok.ui.common.LikeButton
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.TextButton
import com.dasein.poryadok.ui.common.btnLabel
import com.dasein.poryadok.ui.common.likedColor
import com.dasein.poryadok.ui.common.rememberLikes
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import java.io.File

/**
 * Факты от ИИ: три случайных (с учётом того, что вы отмечали «+») или три на выбранную тему.
 * Понравившиеся факты попадают в «Понравившееся» и в каталог Small Talks — и следующие подборки подстраиваются под них.
 */
object AiFacts {
    @Serializable
    data class Item(val text: String, val sphere: String = "", val topic: String = "", val at: Long = 0)

    @Serializable
    private data class Store(val items: List<Item> = emptyList())

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun file(ctx: Context) = File(File(ctx.filesDir, "likes").apply { mkdirs() }, "ai_facts.json")

    fun history(ctx: Context): List<Item> = runCatching { json.decodeFromString(Store.serializer(), file(ctx).readText()).items }.getOrDefault(emptyList())

    private fun remember(ctx: Context, items: List<Item>) = runCatching {
        val all = (items + history(ctx)).distinctBy { it.text.lowercase() }.take(150)
        file(ctx).writeText(json.encodeToString(Store.serializer(), Store(all)))
    }

    /** Что нравится: частые темы и несколько последних отметок «+». */
    fun interests(likes: List<Likes.Like>): Pair<List<String>, List<String>> {
        val tags = likes.flatMap { it.tags }.filter { it.isNotBlank() && it != "ИИ" }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }.take(8)
        val samples = likes.take(12).map { (it.title.ifBlank { it.text }).replace('\n', ' ').take(110) }
        return tags to samples
    }

    fun like(i: Item) = Likes.Like(Likes.FACT, i.text, tags = listOfNotNull(i.sphere.ifBlank { null }, i.topic.ifBlank { null }, "ИИ").distinct())

    /** Три факта. [topic] пусто — случайные (с учётом интересов, если [useInterests]). */
    suspend fun ask(ctx: Context, topic: String, useInterests: Boolean, likes: List<Likes.Like>): List<Item> = withContext(Dispatchers.IO) {
        val spheres = SmallTalk.SPHERES.map { it.first }
        val (tags, samples) = interests(likes)
        val avoid = history(ctx).take(30).map { it.text.take(70) }
        val system = "Ты автор рубрики интересных фактов для светской беседы. Пиши по-русски. Только достоверные, проверяемые факты " +
            "без выдумок и сомнительных цифр; каждый — 1–3 предложения, с неожиданной деталью, которую интересно пересказать собеседнику. " +
            "Ответ строго JSON без пояснений: {\"facts\":[{\"sphere\":\"…\",\"text\":\"…\"}]}. Поле sphere — одно из: ${spheres.joinToString(", ")}."
        val user = buildString {
            if (topic.isNotBlank()) append("Дай ровно 3 малоизвестных интересных факта на тему «${topic.trim()}». ")
            else append("Дай ровно 3 случайных малоизвестных интересных факта из разных сфер. ")
            if (useInterests && (tags.isNotEmpty() || samples.isNotEmpty())) {
                append("Мне нравятся темы: ${tags.joinToString(", ").ifBlank { "—" }}. Примеры того, что мне понравилось: ")
                append(samples.joinToString(" | "))
                if (topic.isBlank()) append(". Два факта — близко к этим интересам, один — из новой, неожиданной сферы. ")
                else append(". Подавай факты так, чтобы они были близки моим интересам. ")
            }
            if (avoid.isNotEmpty()) append("Не повторяй эти факты: ${avoid.joinToString(" | ")}.")
        }
        val obj = Ai.extractJson(Ai.ask(ctx, system, user)) ?: throw Ai.AiError("ИИ ответил не по формату — попробуйте ещё раз.")
        val arr = runCatching { obj["facts"]!!.jsonArray }.getOrNull() ?: throw Ai.AiError("ИИ не прислал факты — попробуйте ещё раз.")
        val now = System.currentTimeMillis()
        val items = arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val t = (o["text"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            val s = (o["sphere"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            if (t.length < 15) null else Item(t, if (s in spheres) s else TalkOnline.SPHERE_WIKI, topic.trim(), now)
        }.take(3)
        if (items.isEmpty()) throw Ai.AiError("ИИ не прислал факты — попробуйте ещё раз.")
        remember(ctx, items)
        items
    }

    /** Отметка «+»: факт и в «Понравившееся», и в каталог Small Talks. */
    fun toggleLike(ctx: Context, i: Item, liked: Boolean) {
        Likes.toggle(ctx, like(i))
        if (!liked && TalkOnline.addFacts(ctx, i.sphere.ifBlank { TalkOnline.SPHERE_WIKI }, listOf(i.text)) > 0) TalkRepo.invalidate()
    }
}

/** Блок «Факты от ИИ»: две кнопки, поле темы, подсказки из интересов, найденные факты с «+». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiFactsPanel(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    val aiOn = remember { Ai.enabled(ctx) }
    val likes = rememberLikes()
    var topic by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var useInterests by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<List<AiFacts.Item>>(emptyList()) }
    var showHistory by remember { mutableStateOf(false) }
    val (tags, _) = remember(likes) { AiFacts.interests(likes) }
    fun go(t: String) {
        busy = if (t.isBlank()) "random" else "topic"; error = null
        scope.launch {
            runCatching { AiFacts.ask(ctx, t, useInterests, likes) }.onSuccess { result = it }.onFailure { error = it.message ?: it.toString() }
            busy = null
        }
    }
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(extra.card).padding(14.dp)) {
        com.dasein.poryadok.ui.common.Editable {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph(Glyphs.AI, 26.dp, badge = false)
                Text(btnLabel("Факты от ИИ"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (!aiOn) {
            Text("Нужен бесплатный ключ OpenRouter: «Настройки» → «ИИ-помощник». После этого здесь можно получать по 3 интересных факта.", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 6.dp))
            return@Column
        }
        Text(
            "Отмечайте «+» понравившиеся факты — следующие подборки будут ближе к вашим интересам. Удержание кнопки — сменить её название и иконку.",
            fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        AiButton("3 случайных факта", { go("") }, Modifier.fillMaxWidth(), busy = busy == "random", busyText = "ИИ подбирает факты…", enabled = busy == null)
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(topic, { topic = it }, Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(14.dp), placeholder = { Text("Тема: космос, кофе, Рим…") })
        }
        AiButton("3 факта на эту тему", { go(topic) }, Modifier.fillMaxWidth().padding(top = 6.dp), enabled = topic.isNotBlank() && busy == null, busy = busy == "topic", busyText = "Ищу факты на тему…", outlined = true)
        if (tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
            Text("Ваши интересы:", fontSize = 12.sp, color = extra.dim, modifier = Modifier.align(Alignment.CenterVertically))
            tags.forEach { t -> Pill(t, topic == t) { topic = t; go(t) } }
        }
        Row(Modifier.fillMaxWidth().clickable { useInterests = !useInterests }.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Учитывать понравившееся", fontSize = 13.sp, modifier = Modifier.weight(1f))
            Switch(useInterests, { useInterests = it })
        }
        error?.let { Text(it, fontSize = 12.sp, color = extra.danger, modifier = Modifier.padding(top = 6.dp)) }
        result.forEach { i -> AiFactRow(i, likes) }
        val hist = remember(result, showHistory) { if (showHistory) AiFacts.history(ctx).filter { h -> result.none { it.text == h.text } }.take(40) else emptyList() }
        TextButton(onClick = { showHistory = !showHistory }) { Text(if (showHistory) "Скрыть прежние факты от ИИ" else "Прежние факты от ИИ") }
        hist.forEach { i -> AiFactRow(i, likes) }
    }
}

@Composable
private fun AiFactRow(i: AiFacts.Item, likes: List<Likes.Like>) {
    val ctx = LocalContext.current
    val liked = Likes.isLiked(likes, AiFacts.like(i))
    Row(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(14.dp)).background(likedColor(liked)).padding(10.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(listOf(i.sphere, i.topic).filter { it.isNotBlank() }.distinct().joinToString(" · "), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            Text(i.text, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.clickable { TalkRepo.share(ctx, i.text) })
        }
        LikeButton(AiFacts.like(i), liked) { AiFacts.toggleLike(ctx, i, liked) }
    }
}

/** Окно с «Фактами от ИИ» — для кнопки на главной. */
@Composable
fun AiFactsDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth(.94f).heightIn(max = 680.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).verticalScroll(rememberScrollState()).padding(6.dp)) {
            AiFactsPanel()
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(btnLabel("Закрыть")) }
        }
    }
}
