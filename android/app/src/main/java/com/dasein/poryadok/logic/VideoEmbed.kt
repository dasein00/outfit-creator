package com.dasein.poryadok.logic

/**
 * Видео по ссылке внутри заметки: YouTube, Instagram, VK, Rutube, TikTok, Vimeo открываются встроенным плеером сайта
 * прямо в статье (адрес «для встраивания»), остальные ссылки — карточкой, которая открывается в браузере.
 */
object VideoEmbed {
    data class Embed(val provider: String, val url: String, val ratio: Float)

    private fun host(url: String) = Regex("""^https?://([^/?#]+)""", RegexOption.IGNORE_CASE).find(url.trim())?.groupValues?.get(1)?.lowercase()
        ?.removePrefix("www.")?.removePrefix("m.").orEmpty()

    fun isLink(s: String) = Regex("""^https?://\S+$""", RegexOption.IGNORE_CASE).matches(s.trim())

    /** Название сайта для подписи: «YouTube», «Instagram», «vc.ru». */
    fun provider(url: String): String {
        val h = host(url)
        return when {
            "youtu" in h -> "YouTube"
            "instagram" in h -> "Instagram"
            h.startsWith("vk.") || "vkvideo" in h -> "VK Видео"
            "rutube" in h -> "Rutube"
            "tiktok" in h -> "TikTok"
            "vimeo" in h -> "Vimeo"
            "dzen" in h -> "Дзен"
            "t.me" == h -> "Telegram"
            else -> h
        }
    }

    /** Адрес встроенного плеера или null — тогда ссылка показывается карточкой. */
    fun of(url: String): Embed? {
        val u = url.trim()
        val h = host(u)
        fun find(re: String) = Regex(re, RegexOption.IGNORE_CASE).find(u)?.groupValues?.get(1)
        return when {
            "youtu" in h -> {
                val id = find("""youtu\.be/([\w-]{6,})""") ?: find("""[?&]v=([\w-]{6,})""") ?: find("""/(?:shorts|embed|live|v)/([\w-]{6,})""")
                    ?: return null
                val start = find("""[?&](?:t|start)=(\d+)""")
                val shorts = "/shorts/" in u
                Embed("YouTube", "https://www.youtube.com/embed/$id?playsinline=1" + (start?.let { "&start=$it" } ?: ""), if (shorts) 9f / 16f else 16f / 9f)
            }
            "instagram" in h -> find("""/(?:p|reel|reels|tv)/([\w-]+)""")?.let { Embed("Instagram", "https://www.instagram.com/p/$it/embed/", 4f / 5.6f) }
            h.startsWith("vk.") || "vkvideo" in h -> Regex("""(?:video|clip)(-?\d+)_(\d+)""").find(u)?.let { m ->
                Embed("VK Видео", "https://vk.com/video_ext.php?oid=${m.groupValues[1]}&id=${m.groupValues[2]}&hd=2", if ("clip" in u) 9f / 16f else 16f / 9f)
            }
            "rutube" in h -> find("""/(?:video|shorts|play/embed)/([0-9a-f]{20,})""")?.let { Embed("Rutube", "https://rutube.ru/play/embed/$it", 16f / 9f) }
            "tiktok" in h -> find("""/video/(\d+)""")?.let { Embed("TikTok", "https://www.tiktok.com/embed/v2/$it", 9f / 16f) }
            "vimeo" in h -> find("""vimeo\.com/(?:video/)?(\d+)""")?.let { Embed("Vimeo", "https://player.vimeo.com/video/$it", 16f / 9f) }
            else -> null
        }
    }
}
