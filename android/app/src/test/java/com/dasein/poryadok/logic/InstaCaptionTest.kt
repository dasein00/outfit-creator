package com.dasein.poryadok.logic

import com.dasein.poryadok.system.InstaCaption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstaCaptionTest {
    @Test fun embedHtmlCaption() {
        val html = """<div class="Caption"><a class="CaptionUsername" href="#">chef</a><br/>Курица с брокколи<br/>Куриная грудка — 400 г<div class="CaptionComments">x</div></div>"""
        assertEquals("Курица с брокколи\nКуриная грудка — 400 г", InstaCaption.fromHtml(html))
    }

    @Test fun jsonCaptionPlainAndDoubleEscaped() {
        val plain = """{"data":{"xdt_shortcode_media":{"edge_media_to_caption":{"edges":[{"node":{"created_at":"1","text":"Ингредиенты:\nКурица 500 г\nСоус \"терияки\""}}]}}}}"""
        assertEquals("Ингредиенты:\nКурица 500 г\nСоус \"терияки\"", InstaCaption.fromHtml(plain))
        val nested = """<script>window.x = {"contextJSON":"{\"context\":{\"caption\":{\"pk\":\"1\",\"text\":\"Рецепт: \\u0431рокколи 300 г\\nкурица 400 г\"}}}"};</script>"""
        assertEquals("Рецепт: брокколи 300 г\nкурица 400 г", InstaCaption.fromHtml(nested))
    }

    @Test fun metaDescription() {
        val html = """<meta property="og:description" content="1,234 likes, 56 comments - chef on May 1, 2026: &quot;Курица с брокколи. Грудка 400 г, брокколи 300 г&quot;." />"""
        assertEquals("Курица с брокколи. Грудка 400 г, брокколи 300 г", InstaCaption.fromMeta(html))
    }

    @Test fun nothingFound() {
        assertNull(InstaCaption.fromHtml("<html><body>Log in</body></html>"))
    }
}
