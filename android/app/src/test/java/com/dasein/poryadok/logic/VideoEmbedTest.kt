package com.dasein.poryadok.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoEmbedTest {
    @Test fun youtube() {
        assertEquals("https://www.youtube.com/embed/dQw4w9WgXcQ?playsinline=1", VideoEmbed.of("https://youtu.be/dQw4w9WgXcQ")?.url)
        assertEquals("https://www.youtube.com/embed/dQw4w9WgXcQ?playsinline=1&start=42", VideoEmbed.of("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42")?.url)
        assertEquals(9f / 16f, VideoEmbed.of("https://youtube.com/shorts/abcDEF12345")!!.ratio)
    }

    @Test fun others() {
        assertEquals("https://www.instagram.com/p/C1abc_D/embed/", VideoEmbed.of("https://www.instagram.com/reel/C1abc_D/?igsh=x")?.url)
        assertEquals("https://vk.com/video_ext.php?oid=-123&id=456&hd=2", VideoEmbed.of("https://vk.com/video-123_456")?.url)
        assertEquals("https://vk.com/video_ext.php?oid=1&id=2&hd=2", VideoEmbed.of("https://vkvideo.ru/video1_2")?.url)
        assertEquals("https://rutube.ru/play/embed/0123456789abcdef0123456789abcdef", VideoEmbed.of("https://rutube.ru/video/0123456789abcdef0123456789abcdef/")?.url)
        assertEquals("https://player.vimeo.com/video/76979871", VideoEmbed.of("https://vimeo.com/76979871")?.url)
        assertNull(VideoEmbed.of("https://vc.ru/some/article"))
        assertEquals("vc.ru", VideoEmbed.provider("https://vc.ru/some/article"))
    }
}
