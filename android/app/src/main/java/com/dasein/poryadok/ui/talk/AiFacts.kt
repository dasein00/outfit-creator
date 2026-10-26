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
 * Факты от ИИ: пять случайных (с учётом того, что вы отмечали «+») или пять на выбранную тему; «Ещё 5» подгружает новые без повторов.
 * Те же подборки — для «В этот день в истории» и «В этот день в культуре»: ИИ ищет события именно этой даты, вам остаётся
 * написать тему (или ничего). Понравившиеся факты попадают в «Понравившееся» (а общие — ещё и в каталог Small Talks).
 */
object AiFacts {
    /** Какие факты искать: общие для разговора, события дня в истории или в культуре. */
    enum class Mode { GENERAL, HISTORY, CULTURE }

    @Serializable
    data class Item(val text: String, val sphere: String = "", val topic: String = "", val at: Long = 0, val year: Int = 0, val mode: String = "", val date: String = "")

    @Serializable
    private data class Store(val items: List<Item> = emptyList())

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun file(ctx: Context) = File(File(ctx.filesDir, "likes").apply { mkdirs() }, "ai_facts.json")

    fun history(ctx: Context): List<Item> = runCatching { json.decodeFromString(Store.serializer(), file(ctx).readText()).items }.getOrDefault(emptyList())

    private fun remember(ctx: Context, items: List<Item>) = runCatching {
        val all = (items + history(ctx)).distinctBy { it.text.lowercase() }.take(400)
        file(ctx).writeText(json.encodeToString(Store.serializer(), Store(all)))
    }

    /** Что нравится: частые темы и несколько последних отметок «+». */
    fun interests(likes: List<Likes.Like>): Pair<List<String>, List<String>> {
        val tags = likes.flatMap { it.tags }.filter { it.isNotBlank() && it != "ИИ" }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }.take(8)
        val samples = likes.take(12).map { (it.title.ifBlank { it.text }).replace('\n', ' ').take(110) }
        return tags to samples
    }

    fun like(i: Item): Likes.Like = when (i.mode) {
        Mode.HISTORY.name -> Likes.Like(Likes.HISTORY, i.text, date = i.date, year = i.year, tags = listOfNotNull(i.topic.ifBlank { null }, "ИИ"))
        Mode.CULTURE.name -> Likes.Like(Likes.CULTURE, i.text, date = i.date, year = i.year, tags = listOfNotNull(i.sphere.ifBlank { null }, i.topic.ifBlank { null }, "ИИ"))
        else -> Likes.Like(Likes.FACT, i.text, tags = listOfNotNull(i.sphere.ifBlank { null }, i.topic.ifBlank { null }, "ИИ").distinct())
    }

    private const val RULES = "Только достоверные, проверяемые сведения из надёжных источников: никаких мифов, городских легенд, «учёные доказали» без имени " +
        "и округлённых до сенсации цифр. Если сомневаешься в факте или дате — не включай его. Пиши по-русски, простым живым языком, " +
        "без вводных «Интересно, что…» и «Знаете ли вы». Факты в подборке — о разных сторонах темы и не повторяют друг друга."

    private val DAY = java.time.format.DateTimeFormatter.ofPattern("d MMMM", java.util.Locale("ru"))

    /**
     * [count] фактов. [topic] пусто — случайные (с учётом интересов, если [useInterests]); [avoid] — уже показанные.
     * Для [Mode.HISTORY] и [Mode.CULTURE] нужна [date]: события именно этого дня и месяца.
     */
    suspend fun ask(
        ctx: Context, topic: String, useInterests: Boolean, likes: List<Likes.Like>, count: Int = 5,
        avoid: List<String> = emptyList(), mode: Mode = Mode.GENERAL, date: java.time.LocalDate? = null,
    ): List<Item> = withContext(Dispatchers.IO) {
        val spheres = SmallTalk.SPHERES.map { it.first }
        val (tags, samples) = interests(likes)
        val seen = (avoid + history(ctx).filter { it.mode == (if (mode == Mode.GENERAL) "" else mode.name) || mode == Mode.GENERAL && it.mode.isEmpty() }.map { it.text })
            .distinct().take(40).map { it.take(80) }
        val t = topic.trim()
        val day = date?.format(DAY).orEmpty()
        val system = when (mode) {
            Mode.GENERAL -> "Ты автор рубрики «Интересные факты для разговора» в журнале для любознательных. Дай собеседнику факты, которые хочется " +
                "пересказать за ужином: неожиданные и точные, с яркой деталью — числом, именем, местом, — которая удивляет. Каждый факт — 1–3 предложения. " +
                RULES + " Ответ строго JSON без пояснений: {\"facts\":[{\"sphere\":\"…\",\"text\":\"…\"}]}. Поле sphere — одно из: ${spheres.joinToString(", ")}."
            Mode.HISTORY -> "Ты историк и автор рубрики «В этот день в истории». Подбирай реальные события, случившиеся именно в указанный день и месяц " +
                "(в любой год): открытия, изобретения, сражения, договоры, рекорды, первые разы в истории, курьёзы. Каждое — 2–3 предложения: что произошло " +
                "и почему это интересно (неожиданная деталь или последствия). Год обязателен и должен быть точным. " + RULES +
                " Ответ строго JSON без пояснений: {\"facts\":[{\"year\":1961,\"text\":\"…\"}]}."
            Mode.CULTURE -> "Ты культуролог и автор рубрики «В этот день в культуре». Подбирай реальные культурные события, случившиеся именно в указанный " +
                "день и месяц (в любой год): дни рождения писателей, художников, актёров, музыкантов, режиссёров; премьеры фильмов, спектаклей и опер; " +
                "выход книг и альбомов; открытия музеев и театров. Для дня рождения — кто это, чем знаменит и одна живая деталь из биографии. " +
                "Каждое — 2–3 предложения, год обязателен и точен. " + RULES +
                " Ответ строго JSON без пояснений: {\"facts\":[{\"year\":1799,\"kind\":\"Дни рождения|Премьеры и события\",\"text\":\"…\"}]}."
        }
        val user = buildString {
            if (mode != Mode.GENERAL) append("Дата: $day. ")
            when {
                mode == Mode.GENERAL && t.isNotBlank() -> append("Дай ровно $count малоизвестных интересных фактов на тему «$t». ")
                mode == Mode.GENERAL -> append("Дай ровно $count случайных малоизвестных интересных фактов из разных сфер. ")
                t.isNotBlank() -> append("Дай ровно $count событий этой даты на тему «$t». ")
                else -> append("Дай ровно $count самых интересных событий этой даты из разных эпох и стран. ")
            }
            if (mode == Mode.GENERAL && useInterests && (tags.isNotEmpty() || samples.isNotEmpty())) {
                append("Мне нравятся темы: ${tags.joinToString(", ").ifBlank { "—" }}. Примеры того, что мне понравилось: ")
                append(samples.joinToString(" | "))
                if (t.isBlank()) append(". Большая часть фактов — близко к этим интересам, один-два — из новой, неожиданной сферы. ")
                else append(". Подавай факты так, чтобы они были близки моим интересам. ")
            }
            if (seen.isNotEmpty()) append("Не повторяй уже известное: ${seen.joinToString(" | ")}.")
        }
        val obj = Ai.extractJson(Ai.ask(ctx, system, user)) ?: throw Ai.AiError("ИИ ответил не по формату — попробуйте ещё раз.")
        val arr = runCatching { obj["facts"]!!.jsonArray }.getOrNull() ?: throw Ai.AiError("ИИ не прислал факты — попробуйте ещё раз.")
        val now = System.currentTimeMillis()
        val md = date?.let { "%02d-%02d".format(it.monthValue, it.dayOfMonth) }.orEmpty()
        val known = seen.map { it.lowercase().take(60) }.toSet()
        val items = arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val text = (o["text"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            val s = (o["sphere"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            val kind = (o["kind"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            val year = (o["year"] as? JsonPrimitive)?.contentOrNull?.trim()?.toIntOrNull() ?: 0
            if (text.length < 15 || text.lowercase().take(60) in known) null
            else when (mode) {
                Mode.GENERAL -> Item(text, if (s in spheres) s else TalkOnline.SPHERE_WIKI, t, now)
                Mode.HISTORY -> Item(text, "История", t, now, year, mode.name, md)
                Mode.CULTURE -> Item(text, kind.ifBlank { "Культура" }, t, now, year, mode.name, md)
            }
        }.take(count)
        if (items.isEmpty()) throw Ai.AiError("ИИ не прислал новых фактов — попробуйте ещё раз или другую тему.")
        remember(ctx, items)
        items
    }

    /** Отметка «+»: факт в «Понравившееся»; общий факт — ещё и в каталог Small Talks. */
    fun toggleLike(ctx: Context, i: Item, liked: Boolean) {
        Likes.toggle(ctx, like(i))
        if (!liked && i.mode.isEmpty() && TalkOnline.addFacts(ctx, i.sphere.ifBlank { TalkOnline.SPHERE_WIKI }, listOf(i.text)) > 0) TalkRepo.invalidate()
    }
}

/**
 * Блок «Факты от ИИ»: пишете тему — ИИ подбирает 5 фактов, «Ещё 5» подгружает следующие, «+» — в избранное («Понравившееся»).
 * [mode] и [date] — для «В этот день в истории» и «в культуре»: события именно этой даты.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiFactsPanel(modifier: Modifier = Modifier, mode: AiFacts.Mode = AiFacts.Mode.GENERAL, date: java.time.LocalDate? = null) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    val aiOn = remember { Ai.enabled(ctx) }
    val likes = rememberLikes()
    val general = mode == AiFacts.Mode.GENERAL
    var topic by remember(date) { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var useInterests by remember { mutableStateOf(true) }
    var result by remember(date) { mutableStateOf<List<AiFacts.Item>>(emptyList()) }
    var lastTopic by remember(date) { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }
    val (tags, _) = remember(likes) { AiFacts.interests(likes) }
    fun go(t: String, more: Boolean = false) {
        busy = if (more) "more" else if (t.isBlank()) "random" else "topic"; error = null
        scope.launch {
            runCatching { AiFacts.ask(ctx, t, useInterests, likes, 5, if (more) result.map { it.text } else emptyList(), mode, date) }
                .onSuccess { got -> result = if (more) result + got else got; lastTopic = t }
                .onFailure { error = it.message ?: it.toString() }
            busy = null
        }
    }
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(extra.card).padding(14.dp)) {
        com.dasein.poryadok.ui.common.Editable {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph(Glyphs.AI, 26.dp, badge = false)
                Text(btnLabel(if (general) "Факты от ИИ" else "Ещё факты этого дня от ИИ"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (!aiOn) {
            Text("Нужен бесплатный ключ ИИ: «Настройки» → «ИИ-помощник». После этого здесь можно получать по 5 интересных фактов.", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 6.dp))
            return@Column
        }
        Text(
            if (general) "Напишите тему — ИИ подберёт 5 интересных фактов, «Ещё 5» — следующие. «+» — в избранное: следующие подборки будут ближе к вашим интересам."
            else "Если базовых событий мало — напишите тему (космос, музыка, Россия…) или оставьте пустым: ИИ найдёт 5 событий именно этой даты, «Ещё 5» — следующие.",
            fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        if (general) AiButton("5 случайных фактов", { go("") }, Modifier.fillMaxWidth(), busy = busy == "random", busyText = "ИИ подбирает факты…", enabled = busy == null)
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                topic, { topic = it }, Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(14.dp),
                placeholder = { Text(if (general) "Тема: космос, кофе, Рим…" else "Тема (необязательно): наука, кино, войны…") },
            )
        }
        AiButton(
            if (general) "5 фактов на эту тему" else if (topic.isBlank()) "5 событий этого дня" else "5 событий дня на эту тему",
            { go(topic) }, Modifier.fillMaxWidth().padding(top = 6.dp),
            enabled = (topic.isNotBlank() || !general) && busy == null, busy = busy == "topic" || (!general && busy == "random"),
            busyText = "ИИ ищет факты…", outlined = general,
        )
        if (general && tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
            Text("Ваши интересы:", fontSize = 12.sp, color = extra.dim, modifier = Modifier.align(Alignment.CenterVertically))
            tags.forEach { t -> Pill(t, topic == t) { topic = t; go(t) } }
        }
        if (general) Row(Modifier.fillMaxWidth().clickable { useInterests = !useInterests }.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Учитывать понравившееся", fontSize = 13.sp, modifier = Modifier.weight(1f))
            Switch(useInterests, { useInterests = it })
        }
        error?.let { Text(it, fontSize = 12.sp, color = extra.danger, modifier = Modifier.padding(top = 6.dp)) }
        result.forEach { i -> AiFactRow(i, likes) }
        if (result.isNotEmpty()) AiButton(
            "Ещё 5 фактов", { go(lastTopic, more = true) }, Modifier.fillMaxWidth().padding(top = 8.dp),
            busy = busy == "more", busyText = "Подгружаю ещё…", enabled = busy == null, outlined = true,
        )
        val modeKey = if (general) "" else mode.name
        val md = date?.let { "%02d-%02d".format(it.monthValue, it.dayOfMonth) }.orEmpty()
        val hist = remember(result, showHistory) {
            if (showHistory) AiFacts.history(ctx).filter { h -> h.mode == modeKey && (general || h.date == md) && result.none { it.text == h.text } }.take(40) else emptyList()
        }
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
            Text(listOfNotNull(i.year.takeIf { it != 0 }?.let { "$it г." }, i.sphere.ifBlank { null }, i.topic.ifBlank { null }).distinct().joinToString(" · "), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
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
