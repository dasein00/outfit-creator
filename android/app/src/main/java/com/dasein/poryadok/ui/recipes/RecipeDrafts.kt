package com.dasein.poryadok.ui.recipes

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.Recipe
import com.dasein.poryadok.data.RecipeIngredient
import com.dasein.poryadok.logic.RecipeText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Черновик новой карточки рецепта. [ai]: 0 — нет, 1 — в очереди ИИ, 2 — ИИ заполняет, 3 — ИИ заполнил, 4 — ошибка ИИ.
 * Для очереди: [aiPostUrl] — ссылка на пост (подпись загрузится сама), [aiPost] — текст поста, [aiSpeech] — речь или свой текст,
 * [aiStage] — что очередь делает сейчас (загружает пост, распознаёт речь, ИИ пишет рецепт).
 */
@Serializable
data class RecipeDraftData(
    val id: String,
    val recipe: Recipe,
    val ingredients: List<RecipeIngredient> = emptyList(),
    val steps: List<String> = emptyList(),
    val updatedAt: Long = 0,
    val ai: Int = 0,
    val aiSource: String = "",
    val aiError: String = "",
    val aiPostUrl: String = "",
    val aiPost: String = "",
    val aiSpeech: String = "",
    val aiStage: String = "",
) {
    val title get() = recipe.name.ifBlank { "Без названия" }
}

/**
 * Черновики: незаконченная новая карточка не пропадает при выходе — она сохраняется сама и лежит во вкладке «Черновики»,
 * откуда её можно дозаполнить или удалить. Файлы — files/recipe_drafts/<id>.json.
 */
object RecipeDrafts {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    /** Все черновики, свежие сверху — экраны перерисовываются при изменении. */
    val list = mutableStateOf<List<RecipeDraftData>>(emptyList())
    private var loaded = false

    private fun dir(): File = File(Graph.app.filesDir, "recipe_drafts").apply { mkdirs() }
    private fun file(id: String) = File(dir(), "${id.filter { it.isLetterOrDigit() || it == '_' }}.json")

    fun newId(): String = "d${System.currentTimeMillis()}"

    @Synchronized fun reload() {
        list.value = dir().listFiles { f -> f.extension == "json" }.orEmpty()
            .mapNotNull { f -> runCatching { json.decodeFromString(RecipeDraftData.serializer(), f.readText()) }.getOrNull() }
            .sortedByDescending { it.updatedAt }
        loaded = true
    }

    fun all(): List<RecipeDraftData> { if (!loaded) reload(); return list.value }
    fun get(id: String): RecipeDraftData? = all().firstOrNull { it.id == id }

    @Synchronized fun put(d: RecipeDraftData) {
        val f = file(d.id)
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(json.encodeToString(RecipeDraftData.serializer(), d))
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        reload()
    }

    @Synchronized fun update(id: String, f: (RecipeDraftData) -> RecipeDraftData) { get(id)?.let { put(f(it)) } }

    @Synchronized fun delete(id: String) { file(id).delete(); reload() }

    @Synchronized fun clear() { dir().listFiles().orEmpty().forEach { it.delete() }; reload() }

    /** В черновике есть что-то, кроме пустой формы. */
    fun hasContent(r: Recipe, ingredients: List<RecipeIngredient>, steps: List<String>): Boolean =
        r.name.isNotBlank() || r.notes.isNotBlank() || r.videos.isNotBlank() || r.photo.isNotBlank() || ingredients.isNotEmpty() || steps.any { it.isNotBlank() }

    fun from(d: RecipeDraft, id: String): RecipeDraftData {
        val old = get(id)
        return RecipeDraftData(
            id, d.recipe, d.ingredients.toList(), d.steps.toList(), System.currentTimeMillis(),
            ai = old?.ai ?: 0, aiSource = old?.aiSource.orEmpty(), aiError = old?.aiError.orEmpty(),
            aiPostUrl = old?.aiPostUrl.orEmpty(), aiPost = old?.aiPost.orEmpty(), aiSpeech = old?.aiSpeech.orEmpty(),
        )
    }

    fun load(d: RecipeDraft, data: RecipeDraftData) {
        d.recipe = data.recipe
        d.ingredients.clear(); d.ingredients.addAll(data.ingredients)
        d.steps.clear(); d.steps.addAll(data.steps.ifEmpty { listOf("") })
    }
}

/**
 * Очередь заполнения карточек: нажали «Создать рецепт» — черновик встаёт в очередь, а карточку можно закрыть и делать следующую.
 * Очередь сама дожидается исходников (подпись по ссылке на пост, речь из видео рецепта), потом ИИ сводит их в рецепт
 * (без ключа или сети — встроенный разбор). Черновики заполняются по одному; о начале и конце приходит уведомление,
 * готовые ждут во вкладке «Черновики», пока их не откроют и не сохранят.
 */
object RecipeAiQueue {
    val running = mutableStateOf<String?>(null)
    private var job: Job? = null

    /** Поставить в очередь: [post] и [speech] — что уже есть в полях, [postUrl] — пост, подпись которого загрузить. */
    fun enqueue(ctx: Context, data: RecipeDraftData, post: String, speech: String, postUrl: String) {
        RecipeDrafts.put(
            data.copy(
                ai = 1, aiSource = recipeSource(post, speech), aiPost = post, aiSpeech = speech, aiPostUrl = postUrl,
                aiError = "", aiStage = "", updatedAt = System.currentTimeMillis(),
            ),
        )
        kick(ctx)
    }

    /** Запустить обработку очереди (и после перезапуска приложения — прерванные черновики снова в очередь). */
    @Synchronized fun kick(ctx: Context) {
        if (job?.isActive == true) return
        val app = ctx.applicationContext
        job = Graph.scope.launch {
            RecipeDrafts.all().filter { it.ai == 2 }.forEach { d -> RecipeDrafts.put(d.copy(ai = 1)) }
            while (true) {
                val next = RecipeDrafts.all().filter { it.ai == 1 }.minByOrNull { it.updatedAt } ?: break
                process(app, next)
            }
        }
    }

    private fun notify(ctx: Context, id: String, title: String, text: String) = com.dasein.poryadok.system.Alarms.notify(
        ctx, ("recipe_ai_" + id).hashCode() and 0x7fffffff, com.dasein.poryadok.system.Alarms.CH_AI, title, text,
        com.dasein.poryadok.ui.Routes.recipeDraft(id),
    )

    private suspend fun process(ctx: Context, data: RecipeDraftData) {
        running.value = data.id
        fun stage(s: String) = RecipeDrafts.update(data.id) { it.copy(ai = 2, aiStage = s) }
        RecipeDrafts.put(data.copy(ai = 2, aiStage = "Готовлю исходники…"))
        val name = data.recipe.name.ifBlank { "новая карточка" }
        val who = if (com.dasein.poryadok.system.Ai.enabled(ctx)) "ИИ" else "Разбор"
        notify(ctx, data.id, "$who начал создавать рецепт", "«$name» — уведомлю, когда карточка будет готова.")
        try {
            // 1. Подпись к посту по ссылке, если её ещё нет.
            var post = data.aiPost.trim()
            if (post.isBlank() && data.aiPostUrl.startsWith("http")) {
                stage("Загружаю описание из поста…")
                runCatching { com.dasein.poryadok.system.RecipeLink.fetch(data.aiPostUrl) }.onSuccess { post = it.trim() }
            }
            // 2. Речь из видео рецепта: ждём распознавание (уже идущее в фоне — не запускается второй раз).
            var speech = data.aiSpeech.trim()
            if (speech.isBlank()) {
                val videos = RecipeVideos.list(data.recipe).filter { it.startsWith("/") && File(it).exists() }
                if (videos.isNotEmpty()) stage("Распознаю речь из видео…")
                speech = videos.mapNotNull { v -> runCatching { com.dasein.poryadok.system.VideoSpeech.transcribeFile(ctx, v) }.getOrNull()?.trim()?.takeIf { it.isNotBlank() } }
                    .joinToString("\n\n")
            }
            val source = recipeSource(post, speech)
            if (source.isBlank()) throw IllegalStateException("нет ни описания поста, ни речи из видео — добавьте ссылку на пост, видео или текст")
            // 3. ИИ сводит пост и речь; без ключа, сети или если ИИ не ответил — встроенный разбор.
            val offline = { if (post.isNotBlank() && speech.isNotBlank()) RecipeText.merge(post, speech) else RecipeText.parse(source) }
            val online = com.dasein.poryadok.system.Ai.enabled(ctx) && com.dasein.poryadok.system.Offline.hasNetwork(ctx)
            var viaAi = false
            val p = if (online) {
                stage("ИИ пишет рецепт…")
                runCatching { aiParse(ctx, source) }.onSuccess { viaAi = true }.getOrElse { offline() }
            } else offline()
            val d = RecipeDraft()
            withContext(Dispatchers.Main) {
                RecipeDrafts.load(d, data)
                applyParsed(d, p, source, viaAi)
                if (RecipeVideos.source(d.recipe) == null && data.aiPostUrl.startsWith("http")) d.recipe = RecipeVideos.withSource(d.recipe, data.aiPostUrl)
            }
            if (viaAi) {
                stage("Оформляю описание…")
                runCatching { com.dasein.poryadok.system.RecipeLink.prettyDescription(ctx, source, d.recipe.name) }
                    .onSuccess { if (it.length > 40) withContext(Dispatchers.Main) { d.recipe = d.recipe.copy(notes = it) } }
            }
            val done = withContext(Dispatchers.Main) { RecipeDrafts.from(d, data.id) }.copy(ai = 3, aiError = "", aiStage = "", aiPost = post, aiSpeech = speech, aiSource = source)
            RecipeDrafts.put(done)
            notify(
                ctx, data.id, "$who закончил рецепт «${done.title}»",
                (if (viaAi || who != "ИИ") "" else "ИИ не ответил — карточку заполнил встроенный разбор. ") + "Откройте её в «Черновиках», проверьте и сохраните.",
            )
            withContext(Dispatchers.Main) { Toast.makeText(ctx, "Рецепт «${done.title}» готов — карточка в «Черновиках»", Toast.LENGTH_LONG).show() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            RecipeDrafts.update(data.id) { it.copy(ai = 4, aiStage = "", aiError = e.message ?: "ошибка ИИ") }
            notify(ctx, data.id, "Рецепт «$name» не создан", e.message ?: "ошибка ИИ")
        } finally {
            running.value = null
        }
    }
}

/** Вкладка «Черновики»: незаконченные карточки и заполненные ИИ — продолжить, удалить или очистить все. */
@androidx.compose.runtime.Composable
fun RecipeDraftsTab(nav: androidx.navigation.NavHostController) {
    val extra = com.dasein.poryadok.ui.theme.LocalExtra.current
    val list by RecipeDrafts.list
    val running by RecipeAiQueue.running
    var confirmAll by androidx.compose.runtime.remember { mutableStateOf(false) }
    var deleting by androidx.compose.runtime.remember { mutableStateOf<RecipeDraftData?>(null) }
    androidx.compose.foundation.lazy.LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
        item {
            com.dasein.poryadok.ui.common.Text(
                "Незаконченные новые карточки сохраняются здесь сами. Карточки, отправленные ИИ, заполняются по очереди и ждут тут, пока вы их не откроете и не сохраните.",
                fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(vertical = 6.dp),
            )
            if (list.size > 1) com.dasein.poryadok.ui.common.OutlinedButton(onClick = { confirmAll = true }, modifier = Modifier.fillMaxWidth()) {
                com.dasein.poryadok.ui.common.Text(com.dasein.poryadok.ui.common.btnLabel("Очистить все черновики"))
            }
        }
        if (list.isEmpty()) item { com.dasein.poryadok.ui.common.Empty("doc/00", "Черновиков нет", "Начните новый рецепт — если выйти, не сохранив, он появится здесь.") }
        items(list.size, key = { list[it].id }) { i ->
            val d = list[i]
            com.dasein.poryadok.ui.common.Tile(Modifier.padding(top = 8.dp), onClick = { nav.navigate(com.dasein.poryadok.ui.Routes.recipeDraft(d.id)) }) {
                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RecipeThumb(d.recipe, 48.dp)
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        com.dasein.poryadok.ui.common.Text(d.title, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, maxLines = 2)
                        val status = when {
                            d.id == running || d.ai == 2 -> "✨ " + d.aiStage.ifBlank { "ИИ заполняет…" }
                            d.ai == 1 -> "⏳ В очереди ИИ"
                            d.ai == 3 -> "✓ Заполнено ИИ — откройте и сохраните"
                            d.ai == 4 -> "ИИ не смог: ${d.aiError}"
                            else -> "${d.ingredients.size} ингр. · ${d.steps.count { it.isNotBlank() }} этапов"
                        }
                        com.dasein.poryadok.ui.common.Text(status, fontSize = 12.sp, color = if (d.ai == 3) extra.ok else if (d.ai == 4) extra.danger else extra.dim, maxLines = 2)
                        com.dasein.poryadok.ui.common.Text(java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale("ru")).format(java.util.Date(d.updatedAt)), fontSize = 11.sp, color = extra.dim)
                    }
                    androidx.compose.material3.IconButton(onClick = { deleting = d }) { com.dasein.poryadok.ui.common.Glyph("ui:trash", 22.dp, badge = false) }
                }
            }
        }
    }
    deleting?.let { d ->
        com.dasein.poryadok.ui.common.ConfirmDialog("Удалить черновик?", d.title, onDismiss = { deleting = null }) { com.dasein.poryadok.ui.common.io { RecipeDrafts.delete(d.id) }; deleting = null }
    }
    if (confirmAll) com.dasein.poryadok.ui.common.ConfirmDialog("Удалить все черновики?", "${list.size} шт.", onDismiss = { confirmAll = false }) {
        com.dasein.poryadok.ui.common.io { RecipeDrafts.clear() }; confirmAll = false
    }
}
