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
 * Бесплатный ИИ через OpenRouter: один ключ — много бесплатных моделей (Qwen, Kimi, DeepSeek, Llama…).
 * Ключ хранится только на телефоне. Если модель занята или исчезла из бесплатных — пробуем следующую.
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
    fun enabled(ctx: Context) = key(ctx).isNotBlank()
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
        if (sorted.isNotEmpty()) sp(ctx).edit().putString("models", sorted.joinToString("\n")).apply()
        sorted
    }

    class AiError(msg: String) : Exception(msg)

    /**
     * Запрос к ИИ: [system] — инструкция, [user] — текст. Возвращает ответ модели.
     * Пробует выбранную модель, потом другие бесплатные (до 4 попыток).
     */
    suspend fun ask(ctx: Context, system: String, user: String): String = withContext(Dispatchers.IO) {
        val k = key(ctx)
        if (k.isBlank()) throw AiError("Нет ключа OpenRouter — добавьте его в «Настройки» → «ИИ-помощник».")
        val models = (listOf(model(ctx)) + cachedModels(ctx) + FALLBACK).filter { it.isNotBlank() }.distinct()
        var last = "нет ответа"
        for (m in models.take(4)) {
            val r = runCatching { call(k, m, system, user) }
            r.getOrNull()?.let { if (it.isNotBlank()) return@withContext it }
            last = r.exceptionOrNull()?.message ?: "пустой ответ"
            if ("401" in last) throw AiError("Ключ не подходит (401). Проверьте ключ OpenRouter.")
        }
        throw AiError("ИИ не ответил: $last. Бесплатные модели иногда перегружены — попробуйте чуть позже.")
    }

    private fun call(key: String, model: String, system: String, user: String): String {
        val body = buildJsonObject {
            put("model", model)
            put("temperature", 0.1)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                add(buildJsonObject { put("role", "user"); put("content", user) })
            })
        }.toString()
        val c = URL("$BASE/chat/completions").openConnection() as HttpURLConnection
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
