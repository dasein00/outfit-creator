package com.dasein.poryadok.ui.talk

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dasein.poryadok.Graph
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.system.Ai
import com.dasein.poryadok.system.Likes
import com.dasein.poryadok.system.Offline
import com.dasein.poryadok.ui.common.AiButton
import com.dasein.poryadok.ui.common.LikeButton
import com.dasein.poryadok.ui.common.Text
import com.dasein.poryadok.ui.common.TextButton
import com.dasein.poryadok.ui.common.btnLabel
import com.dasein.poryadok.ui.common.likedColor
import com.dasein.poryadok.ui.common.rememberLikes
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import java.io.File

/**
 * «Слово дня» в Small Talks: редкие красивые слова — сибарит, бретёр, бадинаж — с определением, этимологией и историей.
 * Встроенная подборка (assets/talk/rare_words.json) листается по дням; с ключом ИИ каждый день подбирается новое слово
 * (без повторов), а кнопкой — ещё слова, в том числе на заданную тему. Понравившиеся — «+» (в «Понравившееся»).
 */
object RareWords {
    @Serializable
    data class Word(
        val w: String, val pos: String = "", val d: String, val e: String = "", val h: String = "", val ex: String = "",
        val ai: Boolean = false, val day: Long = 0, val topic: String = "",
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var baseCache: List<Word>? = null
    /** Меняется, когда добавились слова от ИИ, — экраны перечитывают список. */
    val version = mutableIntStateOf(0)

    fun base(ctx: Context): List<Word> = baseCache ?: runCatching {
        json.decodeFromString(ListSerializer(Word.serializer()), ctx.assets.open("talk/rare_words.json").bufferedReader().readText())
    }.getOrDefault(emptyList()).also { baseCache = it }

    private fun file(ctx: Context) = File(File(ctx.filesDir, "likes").apply { mkdirs() }, "rare_words_ai.json")

    fun aiWords(ctx: Context): List<Word> = runCatching { json.decodeFromString(ListSerializer(Word.serializer()), file(ctx).readText()) }.getOrDefault(emptyList())

    private fun save(ctx: Context, list: List<Word>) {
        runCatching { file(ctx).writeText(json.encodeToString(ListSerializer(Word.serializer()), list)) }
        version.intValue++
    }

    /** Все слова: от ИИ (новые сверху) и встроенные. */
    fun all(ctx: Context): List<Word> = (aiWords(ctx) + base(ctx)).distinctBy { it.w.lowercase() }

    /** Слово дня: подобранное ИИ на этот день или встроенное по номеру дня. */
    fun today(ctx: Context, day: Long = Dates.today()): Word? =
        aiWords(ctx).firstOrNull { it.day == day } ?: base(ctx).takeIf { it.isNotEmpty() }?.let { b -> b[Math.floorMod(day * 7, b.size.toLong()).toInt()] }

    private fun sp(ctx: Context) = ctx.getSharedPreferences("ui_state", Context.MODE_PRIVATE)
    fun autoDaily(ctx: Context) = sp(ctx).getBoolean("rare_ai_daily", true)
    fun setAutoDaily(ctx: Context, on: Boolean) { sp(ctx).edit().putBoolean("rare_ai_daily", on).apply(); version.intValue++ }

    fun like(w: Word) = Likes.Like(Likes.WORD, w.w, w.d, tags = listOfNotNull("слово", w.topic.ifBlank { null }))

    private const val SYSTEM = "Ты лексикограф и автор рубрики «Слово дня» для любителей красивой речи и светской беседы. " +
        "Подбирай редкие, но настоящие слова русского языка — чаще заимствования из французского, латыни, греческого, английского, — " +
        "которые звучат изысканно и украсят разговор: о характерах и типажах людей (бонвиван, сибарит, бретёр, фанфарон), " +
        "о любви, флирте и чувственности (сапиосексуал, гетера, бадинаж, либертинаж, ловелас), о психологии (депривация, апофения, эскапизм), " +
        "о светской жизни, искусстве и острословии (инвектива, бонмо, эпатаж, мезальянс). Не бери слишком простые слова (флирт, хобби, стресс). " +
        "Только реально существующие слова, которые есть в словарях или в устойчивом употреблении; не выдумывай слов, этимологий, дат и имён. " +
        "Если не уверен в детали истории — расскажи её в общих чертах, без конкретных цифр. Пиши по-русски, живо и точно. " +
        "Ответ строго JSON без пояснений: {\"words\":[{\"w\":\"Слово с заглавной буквы\",\"pos\":\"часть речи и род, например «сущ., м. р.»\"," +
        "\"d\":\"определение в 1–2 предложения\",\"e\":\"этимология: язык-источник, исходное слово и его буквальное значение\"," +
        "\"h\":\"история возникновения и употребления: 2–3 предложения с яркой деталью — кто ввёл, откуда пошло, где встречается в литературе\"," +
        "\"ex\":\"пример фразы, как сказать это слово в разговоре\"}]}"

    /** [count] новых слов от ИИ (без повторов). [day] — закрепить первое как слово этого дня. */
    suspend fun generate(ctx: Context, count: Int = 1, topic: String = "", day: Long? = null): List<Word> = withContext(Dispatchers.IO) {
        val known = all(ctx).map { it.w }
        val t = topic.trim()
        val user = (if (t.isNotBlank()) "Подбери ровно $count редких слов на тему «$t». " else "Подбери ровно $count редких слов из разных тем. ") +
            "Не повторяй эти слова: ${known.joinToString(", ")}."
        val obj = Ai.extractJson(Ai.ask(ctx, SYSTEM, user)) ?: throw Ai.AiError("ИИ ответил не по формату — попробуйте ещё раз.")
        val arr = runCatching { obj["words"]!!.jsonArray }.getOrNull() ?: throw Ai.AiError("ИИ не прислал слова — попробуйте ещё раз.")
        fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val low = known.map { it.lowercase() }.toSet()
        val got = arr.mapNotNull { it as? JsonObject }.mapNotNull { o ->
            val w = o.s("w").trim('«', '»', '"', ' ').replaceFirstChar { it.uppercaseChar() }
            if (w.length < 3 || w.lowercase() in low || o.s("d").length < 10) null
            else Word(w, o.s("pos"), o.s("d"), o.s("e"), o.s("h"), o.s("ex"), ai = true, topic = t)
        }.distinctBy { it.w.lowercase() }.take(count)
        if (got.isEmpty()) throw Ai.AiError("ИИ не прислал новых слов — попробуйте ещё раз.")
        val marked = if (day != null) listOf(got.first().copy(day = day)) + got.drop(1) else got
        save(ctx, (marked + aiWords(ctx)).take(500))
        marked
    }

    /** Новое слово дня от ИИ — один раз в день, в фоне (если включено, есть ключ и сеть). */
    fun ensureDaily(ctx: Context) {
        val day = Dates.today()
        if (!autoDaily(ctx) || !Ai.enabled(ctx) || aiWords(ctx).any { it.day == day } || !Offline.hasNetwork(ctx)) return
        if (sp(ctx).getLong("rare_ai_tried", -1) == day) return
        sp(ctx).edit().putLong("rare_ai_tried", day).apply()
        val app = ctx.applicationContext
        Graph.scope.launch { runCatching { generate(app, 1, day = day) } }
    }
}

/** Карточка слова: слово, часть речи, определение; раскрытая — этимология, история и пример. */
@Composable
fun RareWordCard(w: RareWords.Word, open: Boolean, modifier: Modifier = Modifier, title: String? = null, onClick: (() -> Unit)? = null) {
    val extra = LocalExtra.current
    val accent = MaterialTheme.colorScheme.primary
    val likes = rememberLikes()
    val like = RareWords.like(w)
    val liked = Likes.isLiked(likes, like)
    var expanded by remember(w.w, open) { mutableStateOf(open) }
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(likedColor(liked))
            .clickable { if (onClick != null) onClick() else expanded = !expanded }.padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                title?.let { Text(btnLabel(it).uppercase(), fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold, color = accent) }
                Text(w.w, fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 29.sp)
                if (w.pos.isNotBlank()) Text(w.pos + if (w.ai) " · от ИИ" else "", fontSize = 12.sp, color = extra.dim, fontStyle = FontStyle.Italic)
            }
            LikeButton(like, liked)
        }
        Text(w.d, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 8.dp))
        if (expanded) {
            if (w.e.isNotBlank()) Section("Этимология", w.e)
            if (w.h.isNotBlank()) Section("История", w.h)
            if (w.ex.isNotBlank()) Text("«${w.ex.trim('«', '»')}»", fontSize = 14.sp, fontStyle = FontStyle.Italic, color = extra.dim, lineHeight = 20.sp, modifier = Modifier.padding(top = 10.dp))
        } else if (w.e.isNotBlank()) Text(
            btnLabel("Происхождение и история ›"), fontSize = 13.sp, color = accent, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun Section(label: String, text: String) {
    Text(btnLabel(label).uppercase(), fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp))
    Text(text, fontSize = 14.sp, lineHeight = 20.sp)
}

/** Плашка «Слово дня» в конце главной: нажатие открывает раздел в Small Talks. */
@Composable
fun HomeRareWordCard(onOpen: () -> Unit) {
    val ctx = LocalContext.current
    val v = RareWords.version.intValue
    LaunchedEffect(Unit) { RareWords.ensureDaily(ctx) }
    val w = remember(v, Dates.today()) { RareWords.today(ctx) } ?: return
    RareWordCard(w, open = false, title = "Слово дня", onClick = onOpen)
}

/** Вкладка Small Talks «Слово дня»: слово дня, слова от ИИ (в том числе на тему) и весь словарь. */
@Composable
internal fun RareWordsTab() {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    val v = RareWords.version.intValue
    LaunchedEffect(Unit) { RareWords.ensureDaily(ctx) }
    val today = remember(v) { RareWords.today(ctx) }
    val all = remember(v) { RareWords.all(ctx) }
    val aiOn = remember { Ai.enabled(ctx) }
    var topic by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var fresh by remember { mutableStateOf<List<RareWords.Word>>(emptyList()) }
    var q by remember { mutableStateOf("") }
    fun go(t: String, n: Int) {
        busy = if (t.isBlank()) "one" else "topic"; error = null
        scope.launch {
            runCatching { RareWords.generate(ctx, n, t) }.onSuccess { fresh = it + fresh }.onFailure { error = it.message }
            busy = null
        }
    }
    val shown = remember(all, q) { all.filter { q.isBlank() || it.w.contains(q, true) || it.d.contains(q, true) } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)) {
        today?.let { item { RareWordCard(it, open = true, title = "Слово дня") } }
        item {
            Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(extra.card).padding(14.dp)) {
                Text(btnLabel("Слова от ИИ"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                if (!aiOn) {
                    Text("Нужен бесплатный ключ ИИ: «Настройки» → «ИИ-помощник». Пока — встроенная подборка, слово меняется каждый день.", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp))
                    return@Column
                }
                Text("ИИ подбирает редкие слова в том же стиле — с определением, этимологией и историей — без повторов.", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                AiButton("Новое слово", { go("", 1) }, Modifier.fillMaxWidth(), busy = busy == "one", busyText = "ИИ подбирает слово…", enabled = busy == null)
                OutlinedTextField(
                    topic, { topic = it }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true, shape = RoundedCornerShape(14.dp),
                    placeholder = { Text("Тема: любовь, характер, светская жизнь…") },
                )
                AiButton("5 слов на эту тему", { go(topic, 5) }, Modifier.fillMaxWidth().padding(top = 6.dp), enabled = topic.isNotBlank() && busy == null, busy = busy == "topic", busyText = "Ищу слова…", outlined = true)
                var auto by remember { mutableStateOf(RareWords.autoDaily(ctx)) }
                Row(Modifier.fillMaxWidth().clickable { auto = !auto; RareWords.setAutoDaily(ctx, auto) }.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Каждый день новое слово дня от ИИ", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(auto, { auto = it; RareWords.setAutoDaily(ctx, it) })
                }
                error?.let { Text(it, fontSize = 12.sp, color = extra.danger, modifier = Modifier.padding(top = 6.dp)) }
            }
        }
        items(fresh, key = { "f" + it.w }) { RareWordCard(it, open = true, modifier = Modifier.padding(top = 8.dp)) }
        item {
            Text(btnLabel("Словарь: ${all.size} слов"), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp), placeholder = { Text("Поиск по словарю") })
        }
        items(shown, key = { "w" + it.w }) { RareWordCard(it, open = false, modifier = Modifier.padding(top = 8.dp)) }
        item { if (shown.isEmpty()) TextButton(onClick = { q = "" }) { Text(btnLabel("Ничего не найдено — сбросить поиск")) } }
    }
}
