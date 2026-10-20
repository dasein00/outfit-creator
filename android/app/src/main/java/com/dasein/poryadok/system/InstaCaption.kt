package com.dasein.poryadok.system

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive

/**
 * Достаёт подпись к посту Instagram из того, что отдал сайт: HTML страницы встраивания (блок Caption),
 * JSON внутри страницы или ответа (edge_media_to_caption / caption.text — в том числе экранированный дважды)
 * и мета-теги og:description («N likes … : "подпись"»). Чистый Kotlin — проверяется тестами.
 */
object InstaCaption {
    private val json = Json { isLenient = true }

    private val CAPTION_HTML = Regex("""class="Caption"[^>]*>(.*?)<div class="CaptionComments"""", RegexOption.DOT_MATCHES_ALL)
    private val CAPTION_HTML2 = Regex("""class="Caption"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
    private const val STR = """"((?:[^"\\]|\\.)*)""""
    private val EDGE = Regex("""edge_media_to_caption"\s*:\s*\{\s*"edges"\s*:\s*\[\s*\{\s*"node"\s*:\s*\{[^{}]*?"text"\s*:\s*$STR""")
    private val CAPTION_OBJ = Regex(""""caption"\s*:\s*\{[^{}]*?"text"\s*:\s*$STR""")
    /** Ответ api/v1/media/…/info: в объекте caption поле text может стоять после вложенного user {…}. */
    private val CAPTION_LOOSE = Regex(""""caption"\s*:\s*\{(?:[^{}]|\{[^{}]*\})*?"text"\s*:\s*$STR""")

    /**
     * Instagram без входа вместо поста отдаёт страницу входа — её текст («Welcome back to Instagram. Sign in…»)
     * нельзя принимать за подпись.
     */
    private val WALL = listOf(
        "welcome back to instagram", "sign in to check out", "log in to instagram", "log into instagram", "create an account or log in",
        "see instagram photos and videos", "sign up to see photos", "с возвращением в instagram", "войдите, чтобы", "войдите в instagram",
        "зарегистрируйтесь, чтобы", "смотрите фото и видео",
    )

    fun isLoginWall(text: String): Boolean = text.lowercase().let { l -> WALL.any { it in l } }

    fun fromHtml(html: String): String? {
        val cap = CAPTION_HTML.find(html)?.groupValues?.get(1) ?: CAPTION_HTML2.find(html)?.groupValues?.get(1)
        if (cap != null) {
            val t = plain(cap.replace(Regex("""<a class="CaptionUsername"[^>]*>.*?</a>""", RegexOption.DOT_MATCHES_ALL), "")).trim()
            if (t.length > 15 && !isLoginWall(t)) return t
        }
        // JSON в странице: сначала как есть, потом снятое одно экранирование (JSON внутри строки JSON).
        for (src in listOf(html, html.replace("\\\\", "\u0000").replace("\\\"", "\"").replace("\u0000", "\\"))) {
            for (re in listOf(EDGE, CAPTION_OBJ, CAPTION_LOOSE)) {
                val raw = re.find(src)?.groupValues?.get(1) ?: continue
                val t = runCatching { json.parseToJsonElement("\"$raw\"").jsonPrimitive.content }.getOrNull()?.trim()
                if (!t.isNullOrBlank() && t.length > 15 && !isLoginWall(t)) return t
            }
        }
        return null
    }

    /** Подпись из og:description / description: «12 likes, 3 comments - user on May 1, 2026: "Текст"». */
    fun fromMeta(html: String): String? {
        val d = meta(html, "og:description") ?: meta(html, "description") ?: meta(html, "og:title") ?: return null
        val text = unescape(d)
        val i = listOf(": \"", ": «", ": “").map { text.indexOf(it) }.filter { it >= 0 }.minOrNull()
        val body = if (i != null) text.substring(i + 3) else text
        // Хвост «".» после подписи; точку внутри текста не трогаем — только если за ней кавычка.
        val clean = body.trimEnd().removeSuffix(".").trimEnd().trimEnd('"', '»', '”').trim()
        return clean.takeIf { it.length > 15 && !isLoginWall(it) }
    }

    private fun meta(html: String, name: String): String? =
        Regex("""<meta[^>]+(?:property|name)=["']${Regex.escape(name)}["'][^>]+content=["']([^"']*)["']""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
            ?: Regex("""<meta[^>]+content=["']([^"']*)["'][^>]+(?:property|name)=["']${Regex.escape(name)}["']""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)

    private fun plain(html: String): String = unescape(
        html.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n").replace(Regex("""</(p|div|li)>""", RegexOption.IGNORE_CASE), "\n").replace(Regex("""<[^>]+>"""), "")
    )

    private fun unescape(s: String): String = s.replace("&nbsp;", " ").replace("&#160;", " ").replace("&quot;", "\"").replace("&#34;", "\"")
        .replace("&#39;", "'").replace("&#x27;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&#064;", "@").replace("&amp;", "&")
        .replace(Regex("""&#(\d+);""")) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value }
        .replace(Regex("""&#x([0-9a-fA-F]+);""")) { m -> m.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value }
}
