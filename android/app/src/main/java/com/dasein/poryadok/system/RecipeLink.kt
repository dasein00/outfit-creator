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

    private fun get(url: String): String = get(url, UA, "ru-RU,ru;q=0.9,en;q=0.8")

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

    /**
     * Подпись к посту или рилсу Instagram. Instagram часто меняет страницы и всё чаще без входа показывает
     * страницу входа, поэтому пробуем по очереди:
     * 1) страницу встраивания (embed/captioned) — подпись в HTML или в JSON внутри страницы;
     * 2) данные поста по коду (api/v1 и GraphQL) — с входом в Instagram во встроенном браузере работают надёжно;
     * 3) страницу поста «глазами» превью-бота соцсетей — подпись в og:description;
     * 4) обычную страницу поста;
     * 5) страницу во встроенном браузере (как у человека: с выполнением скриптов и сохранённым входом).
     */
    private suspend fun instagram(url: String): String {
        val code = Regex("""instagram\.com/(?:[^/?#]+/)?(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)""").find(url)?.groupValues?.get(1)
        val post = if (code != null) "https://www.instagram.com/p/$code/" else url
        val tries = buildList<() -> String?> {
            if (code != null) {
                add { InstaCaption.fromHtml(get("https://www.instagram.com/p/$code/embed/captioned/")) }
                add { InstaCaption.fromHtml(get("https://www.instagram.com/reel/$code/embed/captioned/")) }
                mediaId(code)?.let { id -> add { InstaCaption.fromHtml(api("https://www.instagram.com/api/v1/media/$id/info/")) } }
                for (doc in listOf("8845758582119845", "10015901848480474", "9510064595728286")) add { InstaCaption.fromHtml(graphql(code, doc)) }
            }
            add { InstaCaption.fromMeta(get(post, BOT_UA, "en-US,en;q=0.8")) }
            add { get(post).let { InstaCaption.fromMeta(it) ?: InstaCaption.fromHtml(it) } }
        }
        for (t in tries) runCatching { t() }.getOrNull()?.takeIf { it.length > 15 }?.let { return it }
        // Встроенный браузер: страница встраивания, потом сама страница поста.
        for (u in listOfNotNull(code?.let { "https://www.instagram.com/p/$it/embed/captioned/" }, post)) {
            runCatching { InstaWeb.caption(u) }.getOrNull()?.takeIf { it.length > 15 }?.let { return it }
        }
        throw InstagramLogin(
            if (InstaWeb.loggedIn()) "Instagram не отдал подпись этого поста — скопируйте текст поста вручную"
            else "Instagram показывает пост только после входа. Нажмите «Войти в Instagram» — это нужно один раз",
        )
    }

    /** Пост не загрузился, потому что Instagram требует вход: в окне импорта появляется кнопка «Войти в Instagram». */
    class InstagramLogin(message: String) : Exception(message)

    /** Номер поста по коду из ссылки (код — число в base64 Instagram). */
    internal fun mediaId(code: String): String? = runCatching {
        val abc = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        code.take(11).fold(java.math.BigInteger.ZERO) { n, ch ->
            val i = abc.indexOf(ch); require(i >= 0); n.shiftLeft(6).add(java.math.BigInteger.valueOf(i.toLong()))
        }.toString()
    }.getOrNull()

    private const val BOT_UA = "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)"

    /** Куки встроенного браузера для Instagram: если там выполнен вход, запросы идут от имени пользователя. */
    private fun cookies(url: String): String? =
        // Только при настоящем входе (кука сессии): обрывки куки от неудачной попытки входа Instagram принимает за подозрительный
        // браузер и вместо поста отдаёт страницу входа — без них страница встраивания открывается как раньше.
        if ("instagram.com" in url) runCatching { android.webkit.CookieManager.getInstance().getCookie(url) }.getOrNull()
            ?.takeIf { it.contains("sessionid=") } else null

    private fun csrf(cookie: String?): String? = cookie?.split(';')?.map { it.trim() }?.firstOrNull { it.startsWith("csrftoken=") }?.substringAfter('=')

    private fun get(url: String, ua: String, lang: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = true
        c.connectTimeout = 12_000; c.readTimeout = 25_000
        c.setRequestProperty("User-Agent", ua)
        c.setRequestProperty("Accept-Language", lang)
        if (ua != BOT_UA) cookies(url)?.let { c.setRequestProperty("Cookie", it) }
        try {
            if (c.responseCode !in 200..299) error("сайт ответил ${c.responseCode}")
            return c.inputStream.use { it.readBytes().decodeToString() }
        } finally { c.disconnect() }
    }

    /** Данные поста, как их берёт сайт Instagram (с входом во встроенном браузере). */
    private fun api(url: String): String {
        val cookie = cookies(url) ?: error("нет входа")
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 12_000; c.readTimeout = 25_000
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("X-IG-App-ID", "936619743392459")
        c.setRequestProperty("X-Requested-With", "XMLHttpRequest")
        c.setRequestProperty("Cookie", cookie)
        csrf(cookie)?.let { c.setRequestProperty("X-CSRFToken", it) }
        try {
            if (c.responseCode !in 200..299) error("сайт ответил ${c.responseCode}")
            return c.inputStream.use { it.readBytes().decodeToString() }
        } finally { c.disconnect() }
    }

    /** Открытый запрос данных поста по коду (без входа), которым пользуются встраивания Instagram. */
    private fun graphql(code: String, docId: String): String {
        val c = URL("https://www.instagram.com/graphql/query").openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        c.connectTimeout = 12_000; c.readTimeout = 25_000
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        c.setRequestProperty("X-IG-App-ID", "936619743392459")
        c.setRequestProperty("X-FB-LSD", "AVqbxe3J_YA")
        c.setRequestProperty("X-ASBD-ID", "129477")
        c.setRequestProperty("Sec-Fetch-Site", "same-origin")
        cookies("https://www.instagram.com/")?.let { ck -> c.setRequestProperty("Cookie", ck); csrf(ck)?.let { c.setRequestProperty("X-CSRFToken", it) } }
        val vars = java.net.URLEncoder.encode("""{"shortcode":"$code","fetch_tagged_user_count":null,"hoisted_comment_id":null,"hoisted_reply_id":null}""", "UTF-8")
        try {
            c.outputStream.use { it.write("variables=$vars&doc_id=$docId&lsd=AVqbxe3J_YA".toByteArray()) }
            if (c.responseCode !in 200..299) error("сайт ответил ${c.responseCode}")
            return c.inputStream.use { it.readBytes().decodeToString() }
        } finally { c.disconnect() }
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
            "Ингредиенты — строками «- Продукт — 200 г». Шаги — с глагола в повелительном наклонении на «вы», с огнём, температурой в °C и временем. " +
            "Без хештегов, ссылок, рекламы и эмодзи. Ответ — только сам текст.\n" + com.dasein.poryadok.ui.recipes.RECIPE_STYLE
        return Ai.ask(ctx, system, "Блюдо: $name\n\n" + source.take(6000)).trim().removePrefix("```markdown").removePrefix("```").removeSuffix("```").trim()
    }
}
