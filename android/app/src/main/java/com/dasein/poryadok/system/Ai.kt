package com.dasein.poryadok.system

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL

/**
 * Бесплатный ИИ. Основной — OpenRouter: один ключ — много бесплатных моделей (Qwen, Kimi, DeepSeek, Llama…).
 * Запасные — Groq, Google Gemini и Mistral (у каждого свой бесплатный ключ): если OpenRouter не отвечает или кончился
 * дневной лимит, запрос сам уходит к следующему, у которого есть ключ; первым потом пробуется тот, что ответил последним.
 * Ключи хранятся только на телефоне. Если модель занята или исчезла — пробуем следующую.
 */
object Ai {
    private const val BASE = "https://openrouter.ai/api/v1"
    private val json = Json { ignoreUnknownKeys = true }

    /** Предпочтительный порядок: сначала Qwen и Kimi, потом DeepSeek и остальные. */
    private val PREFER = listOf("qwen", "kimi", "moonshot", "deepseek", "llama", "gemma", "mistral")

    /** Запасной список на случай, если список моделей не загрузился (имена бесплатных моделей меняются). */
    private val FALLBACK = listOf(
        "qwen/qwen3-235b-a22b:free", "moonshotai/kimi-k2:free", "deepseek/deepseek-chat-v3-0324:free",
        "meta-llama/llama-3.3-70b-instruct:free", "google/gemma-3-27b-it:free",
    )

    private fun sp(ctx: Context) = ctx.getSharedPreferences("ai", Context.MODE_PRIVATE)
    fun key(ctx: Context): String = sp(ctx).getString("key", "").orEmpty()
    fun setKey(ctx: Context, k: String) = sp(ctx).edit().putString("key", k.trim()).apply()
    /** Есть ключ хотя бы одного ИИ. */
    fun enabled(ctx: Context) = key(ctx).isNotBlank() || BACKUPS.any { it.key(ctx).isNotBlank() }

    /** Запасной ИИ с совместимым API (как у OpenAI): адрес, ключ и модели (первая — основная, [vision] — понимают картинки). */
    class Backup(val id: String, val title: String, val base: String, val models: List<String>, val vision: List<String>, val keyUrl: String, val keyHint: String) {
        fun key(ctx: Context): String = if (id == "groq") VideoSpeech.groqKey(ctx) else sp(ctx).getString("key_$id", "").orEmpty().trim()
        fun setKey(ctx: Context, k: String) = if (id == "groq") VideoSpeech.setGroqKey(ctx, k) else sp(ctx).edit().putString("key_$id", k.trim()).apply()
    }

    val BACKUPS = listOf(
        Backup(
            "groq", "Groq", "https://api.groq.com/openai/v1",
            listOf("llama-3.3-70b-versatile", "moonshotai/kimi-k2-instruct", "qwen/qwen3-32b", "meta-llama/llama-4-scout-17b-16e-instruct", "llama-3.1-8b-instant"),
            listOf("meta-llama/llama-4-scout-17b-16e-instruct", "meta-llama/llama-4-maverick-17b-128e-instruct"),
            "https://console.groq.com/keys", "Ключ Groq (gsk_…)",
        ),
        Backup(
            "gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai",
            listOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-2.5-flash-lite"), listOf("gemini-2.5-flash", "gemini-2.0-flash"),
            "https://aistudio.google.com/apikey", "Ключ Gemini (AIza…)",
        ),
        Backup(
            "mistral", "Mistral", "https://api.mistral.ai/v1",
            listOf("mistral-small-latest", "open-mistral-nemo", "mistral-large-latest"), listOf("mistral-small-latest", "pixtral-12b-2409"),
            "https://console.mistral.ai/api-keys", "Ключ Mistral",
        ),
    )

    /** Порядок ИИ: тот, что ответил последним, — первым; потом OpenRouter и запасные. */
    private fun order(ctx: Context): List<String> {
        val all = listOf("openrouter") + BACKUPS.map { it.id }
        val last = sp(ctx).getString("last_provider", "").orEmpty()
        return (listOf(last).filter { it in all } + all).distinct()
    }

    /** Ответ без «размышлений» модели (<think>…</think> у Qwen и DeepSeek). */
    private fun clean(s: String) = s.replace(Regex("(?s)<think>.*?</think>"), "").trim()

    /** Один запасной ИИ: модели по очереди, пока какая-то не ответит. */
    private fun askBackup(ctx: Context, b: Backup, models: List<String>, body: (String) -> String): String {
        val k = b.key(ctx)
        var last = "нет ответа"
        for (m in models) {
            val r = runCatching { clean(post(k, body(m), b.base)) }
            r.getOrNull()?.let { if (it.isNotBlank()) return it }
            last = r.exceptionOrNull()?.message ?: "пустой ответ"
            if ("401" in last || "403" in last) break
        }
        throw AiError(last.take(120))
    }
    fun model(ctx: Context): String = sp(ctx).getString("model", "").orEmpty()
    fun setModel(ctx: Context, m: String) = sp(ctx).edit().putString("model", m).apply()
    fun cachedModels(ctx: Context): List<String> = sp(ctx).getString("models", "").orEmpty().split('\n').filter { it.isNotBlank() }

    /** Текущие бесплатные модели OpenRouter (ключ не нужен). Сортировка — по предпочтению. */
    suspend fun freeModels(ctx: Context): List<String> = withContext(Dispatchers.IO) {
        val list = runCatching {
            val c = URL("$BASE/models").openConnection() as HttpURLConnection
            c.connectTimeout = 10_000; c.readTimeout = 20_000
            try {
                val body = c.inputStream.use { it.readBytes().decodeToString() }
                json.parseToJsonElement(body).jsonObject["data"]!!.jsonArray
                    .mapNotNull { (it as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull }
                    .filter { it.endsWith(":free") }
            } finally { c.disconnect() }
        }.getOrDefault(emptyList())
        val sorted = list.sortedBy { id -> PREFER.indexOfFirst { it in id.lowercase() }.let { if (it < 0) 99 else it } }
        if (sorted.isNotEmpty()) sp(ctx).edit().putString("models", sorted.joinToString("\n")).putLong("models_at", System.currentTimeMillis()).apply()
        sorted
    }

    /** Список бесплатных моделей обновляется сам раз в 2 дня: модели исчезают и появляются, а приложение должно работать без настройки. */
    private suspend fun freshModels(ctx: Context): List<String> {
        val age = System.currentTimeMillis() - sp(ctx).getLong("models_at", 0)
        val cached = cachedModels(ctx)
        if (cached.isNotEmpty() && age < 2 * 86_400_000L) return cached
        return freeModels(ctx).ifEmpty { cached }
    }

    /** Модели, которые недавно не ответили (нет такой, лимит): на сутки уходят в конец очереди. */
    private fun badModels(ctx: Context): Set<String> {
        val now = System.currentTimeMillis()
        return sp(ctx).getString("bad", "").orEmpty().split('\n').mapNotNull { l ->
            val (m, t) = l.split('|').takeIf { it.size == 2 } ?: return@mapNotNull null
            m.takeIf { now - (t.toLongOrNull() ?: 0) < 86_400_000L }
        }.toSet()
    }

    private fun markBad(ctx: Context, m: String) {
        val now = System.currentTimeMillis()
        val keep = sp(ctx).getString("bad", "").orEmpty().split('\n').filter { it.isNotBlank() && !it.startsWith("$m|") }.takeLast(30)
        sp(ctx).edit().putString("bad", (keep + "$m|$now").joinToString("\n")).apply()
    }

    class AiError(msg: String) : Exception(msg)

    /**
     * Запрос к ИИ: [system] — инструкция, [user] — текст. Возвращает ответ модели.
     * Пробует выбранную модель, потом другие бесплатные (до 4 попыток).
     */
    suspend fun ask(ctx: Context, system: String, user: String): String = withContext(Dispatchers.IO) {
        val errors = ArrayList<String>()
        for (id in order(ctx)) {
            val b = BACKUPS.firstOrNull { it.id == id }
            if ((b?.key(ctx) ?: key(ctx)).isBlank()) continue
            val r = runCatching {
                if (b == null) askOpenRouter(ctx, system, user)
                else askBackup(ctx, b, b.models) { m -> chatBody(m, system, user) }
            }
            r.getOrNull()?.let { sp(ctx).edit().putString("last_provider", id).apply(); return@withContext it }
            errors += (b?.title ?: "OpenRouter") + ": " + (r.exceptionOrNull()?.message ?: "нет ответа").take(140)
        }
        if (errors.isEmpty()) throw AiError("Нет ключа ИИ — добавьте ключ OpenRouter, Groq, Gemini или Mistral в «Настройки» → «ИИ-помощник».")
        throw AiError("ИИ не ответил — " + errors.joinToString("; ") + ". Бесплатные модели иногда перегружены — попробуйте чуть позже.")
    }

    /** Проверка каждого ИИ, у которого есть ключ: «OpenRouter ✓», «Groq ✕ 429…». */
    suspend fun check(ctx: Context): List<String> = withContext(Dispatchers.IO) {
        val out = ArrayList<String>()
        if (key(ctx).isNotBlank()) out += "OpenRouter: " + runCatching { askOpenRouter(ctx, "Отвечай одним словом.", "Скажи «работает»") }.fold({ "✓ " + it.take(30) }, { "✕ " + it.message?.take(100) })
        BACKUPS.filter { it.key(ctx).isNotBlank() }.forEach { b ->
            out += b.title + ": " + runCatching { askBackup(ctx, b, b.models) { m -> chatBody(m, "Отвечай одним словом.", "Скажи «работает»") } }
                .fold({ "✓ " + it.take(30) }, { "✕ " + it.message?.take(100) })
        }
        out
    }

    private fun chatBody(model: String, system: String, user: String) = buildJsonObject {
        put("model", model)
        put("temperature", 0.1)
        put("messages", buildJsonArray {
            add(buildJsonObject { put("role", "system"); put("content", system) })
            add(buildJsonObject { put("role", "user"); put("content", user) })
        })
    }.toString()

    private suspend fun askOpenRouter(ctx: Context, system: String, user: String): String {
        val k = key(ctx)
        if (k.isBlank()) throw AiError("Нет ключа OpenRouter — добавьте его в «Настройки» → «ИИ-помощник».")
        // Порядок: выбранная → та, что ответила последней → свежий список бесплатных → запасной; недавно сбойные — в конце.
        val bad = badModels(ctx)
        val lastOk = sp(ctx).getString("last_ok", "").orEmpty()
        val all = (listOf(model(ctx), lastOk) + freshModels(ctx) + FALLBACK).filter { it.isNotBlank() }.distinct()
        val models = all.filter { it !in bad } + all.filter { it in bad }
        var last = "нет ответа"
        var refreshed = false
        var tried = 0
        var queue = models
        while (tried < 6 && queue.isNotEmpty()) {
            val m = queue.first(); queue = queue.drop(1); tried++
            val r = runCatching { call(k, m, system, user) }
            r.getOrNull()?.let { if (it.isNotBlank()) { sp(ctx).edit().putString("last_ok", m).apply(); return clean(it) } }
            last = r.exceptionOrNull()?.message ?: "пустой ответ"
            if ("401" in last) throw AiError("Ключ не подходит (401). Проверьте ключ OpenRouter.")
            markBad(ctx, m)
            // Модель исчезла из бесплатных — один раз перечитываем список с сервера и пробуем новые.
            if (!refreshed && ("404" in last || "No endpoints" in last || "not a valid model" in last)) {
                refreshed = true
                val fresh = freeModels(ctx).filter { it !in bad && it != m }
                if (fresh.isNotEmpty()) queue = (fresh + queue).distinct()
            }
        }
        throw AiError(last.take(140))
    }

    private fun call(key: String, model: String, system: String, user: String): String = post(key, chatBody(model, system, user))

    private fun post(key: String, body: String, base: String = BASE): String {
        val c = URL("$base/chat/completions").openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        c.connectTimeout = 15_000; c.readTimeout = 90_000
        c.setRequestProperty("Authorization", "Bearer $key")
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("HTTP-Referer", "https://github.com/dasein00/outfit-creator")
        c.setRequestProperty("X-Title", "DASEIN")
        try {
            c.outputStream.use { it.write(body.encodeToByteArray()) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes().decodeToString() }.orEmpty()
            if (code !in 200..299) throw AiError("$code ${text.take(160)}")
            val root = json.parseToJsonElement(text).jsonObject
            val msg = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
            return (msg?.get("content") as? JsonPrimitive)?.contentOrNull.orEmpty()
        } finally { c.disconnect() }
    }

    /** Бесплатные модели OpenRouter, которые понимают картинки (список обновляется раз в 2 дня). */
    private suspend fun visionModels(ctx: Context): List<String> = withContext(Dispatchers.IO) {
        val age = System.currentTimeMillis() - sp(ctx).getLong("vision_at", 0)
        val cached = sp(ctx).getString("vision", "").orEmpty().split('\n').filter { it.isNotBlank() }
        if (cached.isNotEmpty() && age < 2 * 86_400_000L) return@withContext cached
        val list = runCatching {
            val c = URL("$BASE/models").openConnection() as HttpURLConnection
            c.connectTimeout = 10_000; c.readTimeout = 20_000
            try {
                val body = c.inputStream.use { it.readBytes().decodeToString() }
                json.parseToJsonElement(body).jsonObject["data"]!!.jsonArray.mapNotNull { it as? JsonObject }.filter { m ->
                    val id = m["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val input = (m["architecture"] as? JsonObject)?.get("input_modalities") as? JsonArray
                    id.endsWith(":free") && input?.any { (it as? JsonPrimitive)?.contentOrNull == "image" } == true
                }.mapNotNull { it["id"]?.jsonPrimitive?.contentOrNull }
            } finally { c.disconnect() }
        }.getOrDefault(emptyList())
        val order = listOf("qwen", "gemma", "llama-4", "mistral", "kimi")
        val sorted = list.sortedBy { id -> order.indexOfFirst { it in id.lowercase() }.let { if (it < 0) 99 else it } }
        if (sorted.isNotEmpty()) sp(ctx).edit().putString("vision", sorted.joinToString("\n")).putLong("vision_at", System.currentTimeMillis()).apply()
        sorted.ifEmpty { cached }
    }

    private val VISION_FALLBACK = listOf(
        "qwen/qwen2.5-vl-72b-instruct:free", "google/gemma-3-27b-it:free", "meta-llama/llama-4-maverick:free",
        "mistralai/mistral-small-3.2-24b-instruct:free",
    )

    /**
     * Запрос к ИИ с картинками ([images] — JPEG): например, переписать текст со скриншота.
     * Пробует бесплатные модели, которые понимают изображения (до 4 попыток).
     */
    suspend fun askImages(ctx: Context, prompt: String, images: List<ByteArray>): String = withContext(Dispatchers.IO) {
        val content = buildJsonArray {
            add(buildJsonObject { put("type", "text"); put("text", prompt) })
            images.forEach { img ->
                add(buildJsonObject {
                    put("type", "image_url")
                    put("image_url", buildJsonObject { put("url", "data:image/jpeg;base64," + android.util.Base64.encodeToString(img, android.util.Base64.NO_WRAP)) })
                })
            }
        }
        fun body(m: String) = buildJsonObject { put("model", m); put("temperature", 0.0); put("messages", buildJsonArray { add(buildJsonObject { put("role", "user"); put("content", content) }) }) }.toString()
        val errors = ArrayList<String>()
        for (id in order(ctx)) {
            val b = BACKUPS.firstOrNull { it.id == id }
            if ((b?.key(ctx) ?: key(ctx)).isBlank()) continue
            val r = runCatching { if (b == null) imagesOpenRouter(ctx, prompt, images) else askBackup(ctx, b, b.vision) { m -> body(m) } }
            r.getOrNull()?.let { return@withContext it }
            errors += (b?.title ?: "OpenRouter") + ": " + (r.exceptionOrNull()?.message ?: "нет ответа").take(100)
        }
        throw AiError("ИИ не прочитал картинку — " + errors.joinToString("; ").ifBlank { "нет ключа" })
    }

    private suspend fun imagesOpenRouter(ctx: Context, prompt: String, images: List<ByteArray>): String = withContext(Dispatchers.IO) {
        val k = key(ctx)
        if (k.isBlank()) throw AiError("Нет ключа OpenRouter")
        val bad = badModels(ctx)
        val all = (visionModels(ctx) + VISION_FALLBACK).distinct()
        val models = (all.filter { it !in bad } + all.filter { it in bad }).take(4)
        var last = "нет ответа"
        for (m in models) {
            val content = buildJsonArray {
                add(buildJsonObject { put("type", "text"); put("text", prompt) })
                images.forEach { img ->
                    add(buildJsonObject {
                        put("type", "image_url")
                        put("image_url", buildJsonObject { put("url", "data:image/jpeg;base64," + android.util.Base64.encodeToString(img, android.util.Base64.NO_WRAP)) })
                    })
                }
            }
            val r = runCatching { post(k, buildJsonObject { put("model", m); put("temperature", 0.0); put("messages", buildJsonArray { add(buildJsonObject { put("role", "user"); put("content", content) }) }) }.toString()) }
            r.getOrNull()?.let { if (it.isNotBlank()) return@withContext it }
            last = r.exceptionOrNull()?.message ?: "пустой ответ"
            if ("401" in last) throw AiError("Ключ не подходит (401). Проверьте ключ OpenRouter.")
            markBad(ctx, m)
        }
        throw AiError("ИИ не прочитал картинку: $last")
    }

    /** JSON из ответа модели: модели любят оборачивать его в ```json … ``` и пояснения. */
    fun extractJson(answer: String): JsonObject? {
        val start = answer.indexOf('{'); val end = answer.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { json.parseToJsonElement(answer.substring(start, end + 1)).jsonObject }.getOrNull()
    }

    fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
    fun JsonObject.num(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull?.replace(',', '.')?.toDoubleOrNull()
    fun JsonObject.arr(k: String) = this[k] as? JsonArray
}
