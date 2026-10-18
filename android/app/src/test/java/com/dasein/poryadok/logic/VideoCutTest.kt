package com.dasein.poryadok.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoCutTest {
    private val segs = listOf(1000L..4000L, 10_000L..12_000L, 1000L..4000L)

    @Test fun totalsAndOffsets() {
        assertEquals(8000L, VideoCut.total(segs))
        assertEquals(0L, VideoCut.offsetOf(segs, 0))
        assertEquals(3000L, VideoCut.offsetOf(segs, 1))
        assertEquals(5000L, VideoCut.offsetOf(segs, 2))
    }

    @Test fun editedRoundTrip() {
        assertEquals(3500L, VideoCut.toEdited(segs, 1, 10_500))
        assertEquals(1 to 10_500L, VideoCut.fromEdited(segs, 3500))
        // Повторённый кусок: то же место исходника, но другое время монтажа.
        assertEquals(2 to 1500L, VideoCut.fromEdited(segs, 5500))
        assertEquals(0 to 1000L, VideoCut.fromEdited(segs, -10))
        assertEquals(2 to 4000L, VideoCut.fromEdited(segs, 99_000))
        assertEquals(0 to 0L, VideoCut.fromEdited(emptyList(), 100))
    }

    @Test fun ticksDoNotOverlap() {
        // Минута на 400 px — делим по 10 секунд.
        assertEquals(10_000L, VideoCut.tickStep(400f / 60_000))
        // Сильное увеличение — десятые доли секунды.
        assertEquals(100L, VideoCut.tickStep(1f))
        assertEquals(250L, VideoCut.tickStep(0.3f))
    }

    @Test fun parsesTypedTime() {
        assertEquals(65_300L, VideoCut.parseTime("1:05.3"))
        assertEquals(65_000L, VideoCut.parseTime("1:05"))
        assertEquals(65_000L, VideoCut.parseTime("65"))
        assertEquals(7_250L, VideoCut.parseTime("0:07,25"))
        assertEquals(3_723_000L, VideoCut.parseTime("1:02:03"))
        assertNull(VideoCut.parseTime(""))
        assertNull(VideoCut.parseTime("1:75"))
        assertNull(VideoCut.parseTime("a:10"))
        assertNull(VideoCut.parseTime("1::2"))
    }
}
