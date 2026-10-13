package com.dasein.poryadok.system

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.HttpURLConnection
import java.net.URL

/**
 * Рецепт по ссылке: с сайтов — из разметки рецепта (schema.org Recipe), из Instagram — подпись к посту.
 * Возвращает текст, который дальше разбирается как обычное описание (встроенным разбором или ИИ).
 */
object RecipeLink {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private const val UA = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    fun findUrl(text: String): String? = Regex("""https?://\S+""").find(text)?.value?.trimEnd('.', ',', ')', '»', '"')

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = true
        c.connectTimeout = 12_000; c.readTimeout = 25_000
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8")
        try {
            if (c.responseCode !in 200..299) error("сайт ответил ${c.responseCode}")
            return c.inputStream.use { it.readBytes().decodeToString() }
        } finally { c.disconnect() }
    }

    suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val u = url.trim()
        if ("instagram.com" in u) return@withContext instagram(u)
        val html = get(u)
        ldRecipe(html)?.let { return@withContext it }
        // Нет разметки рецепта — берём заголовок, описание и видимый текст страницы.
        val title = meta(html, "og:title") ?: Regex("""<title[^>]*>(.*?)</title>""", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1)
        val body = plain(html.substringAfter("<body", html)).take(6000)
        listOfNotNull(title?.let(::unescape), meta(html, "og:description")?.let(::unescape), body).joinToString("\n\n")
    }

    /** Подпись к посту Instagram: страница «embed/captioned» отдаётся без входа; запасной путь — og:description. */
    private fun instagram(url: String): String {
        val code = Regex("""instagram\.com/(?:[^/]+/)?(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)""").find(url)?.groupValues?.get(1)
        if (code != null) runCatching {
            val html = get("https://www.instagram.com/p/$code/embed/captioned/")
            val cap = Regex("""class="Caption"[^>]*>(.*?)<div class="CaptionComments"""", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1)
                ?: Regex("""class="Caption"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1)
            if (cap != null) {
                val t = plain(cap.replace(Regex("""<a class="CaptionUsername"[^>]*>.*?</a>""", RegexOption.DOT_MATCHES_ALL), "")).trim()
                if (t.length > 20) return t
            }
        }
        val html = get(url)
        val d = meta(html, "og:description") ?: meta(html, "description") ?: error("Instagram не отдал подпись — скопируйте текст поста вручную")
        // «123 likes, 4 comments - user on May 1, 2024: "Текст рецепта"» → только текст.
        return unescape(d).substringAfter(": \"", unescape(d)).removeSuffix("\"").trim()
    }

    private fun meta(html: String, name: String): String? =
        Regex("""<meta[^>]+(?:property|name)=["']${Regex.escape(name)}["'][^>]+content=["']([^"']*)["']""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
            ?: Regex("""<meta[^>]+content=["']([^"']*)["'][^>]+(?:property|name)=["']${Regex.escape(name)}["']""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)

    /** schema.org Recipe из <script type="application/ld+json"> → текст «название, порции, ингредиенты, шаги». */
    internal fun ldRecipe(html: String): String? {
        val blocks = Regex("""<script[^>]+application/ld\+json[^>]*>(.*?)</script>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .findAll(html).map { it.groupValues[1].trim() }
        for (b in blocks) {
            val root = runCatching { json.parseToJsonElement(b) }.getOrNull() ?: continue
            val r = findRecipe(root) ?: continue
            fun s(k: String) = (r[k] as? JsonPrimitive)?.contentOrNull?.let(::unescape)
            val ingr = (r["recipeIngredient"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.let(::unescape) }
            val steps = instructions(r["recipeInstructions"])
            if (ingr.isEmpty() && steps.isEmpty()) continue
            val yield = r["recipeYield"].let { y -> (y as? JsonPrimitive)?.contentOrNull ?: (y as? JsonArray)?.firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull } }
            return buildString {
                s("name")?.let { appendLine(it) }
                s("description")?.let { appendLine(it) }
                yield?.let { appendLine("Порций: $it") }
                appendLine(); appendLine("Ингредиенты:"); ingr.forEach { appendLine("- $it") }
                appendLine(); appendLine("Приготовление:"); steps.forEachIndexed { i, st -> appendLine("${i + 1}. $st") }
            }
        }
        return null
    }

    private fun findRecipe(e: JsonElement): JsonObject? = when (e) {
        is JsonArray -> e.firstNotNullOfOrNull { findRecipe(it) }
        is JsonObject -> {
            val t = e["@type"]
            val isRecipe = (t as? JsonPrimitive)?.contentOrNull == "Recipe" || (t as? JsonArray)?.any { (it as? JsonPrimitive)?.contentOrNull == "Recipe" } == true
            if (isRecipe) e else (e["@graph"]?.let { findRecipe(it) } ?: e["mainEntity"]?.let { findRecipe(it) })
        }
        else -> null
    }

    private fun instructions(e: JsonElement?): List<String> = when (e) {
        null -> emptyList()
        is JsonPrimitive -> e.contentOrNull?.let { plain(it) }?.split(Regex("\n+"))?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        is JsonArray -> e.flatMap { instructions(it) }
        is JsonObject -> (e["itemListElement"]?.let { instructions(it) })
            ?: listOfNotNull((e["text"] as? JsonPrimitive)?.contentOrNull?.let { unescape(plain(it)).trim() }?.takeIf { it.isNotEmpty() })
        else -> emptyList()
    }

    private fun plain(html: String): String = unescape(
        html.replace(Regex("""(?is)<(script|style|noscript)[^>]*>.*?</\1>"""), " ")
            .replace(Regex("""(?i)<br\s*/?>|</p>|</li>|</div>|</h\d>"""), "\n")
            .replace(Regex("<[^>]+>"), " "),
    ).replace(Regex("[ \t ]+"), " ").replace(Regex("\n\\s*\n\\s*\n+"), "\n\n").trim()

    private fun unescape(s: String): String = s.replace("&nbsp;", " ").replace("&#160;", " ").replace("&quot;", "\"").replace("&#34;", "\"")
        .replace("&#39;", "'").replace("&#x27;", "'").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">")
        .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value }
        .replace(Regex("&#x([0-9a-fA-F]+);")) { m -> m.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value }
        .replace("&amp;", "&")

    /** Красивое описание блюда для карточки (ИИ): Markdown, который потом можно править вручную. */
    suspend fun prettyDescription(ctx: android.content.Context, source: String, name: String): String {
        val system = "Ты редактор кулинарного журнала. Из исходного текста рецепта (часто это короткая подпись из Instagram) " +
            "сделай красивое, понятное описание блюда на русском в Markdown: «# Название», 1–2 строки — что за блюдо и чем хорошо; " +
            "«## Ингредиенты» — список с количеством; «## Приготовление» — нумерованные шаги, понятные новичку (огонь, время, как понять, что готово); " +
            "«## Советы» — 2–4 пункта (замены, подача, хранение). Не выдумывай продукты, которых нет в тексте; если количество не указано — так и оставь. " +
            "Без хештегов, ссылок, рекламы и эмодзи в каждом слове. Ответ — только сам текст."
        return Ai.ask(ctx, system, "Блюдо: $name\n\n" + source.take(6000)).trim().removePrefix("```markdown").removePrefix("```").removeSuffix("```").trim()
    }
}
