package com.dasein.poryadok.logic

import com.dasein.poryadok.logic.VideoCut.Clip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoCutTest {
    // Второй кусок — из другого файла и вдвое быстрее: 2 с исходника занимают 1 с ленты.
    private val clips = listOf(Clip(0, 1000, 4000), Clip(1, 10_000, 12_000, 2f), Clip(0, 1000, 4000))

    @Test fun totalsAndOffsets() {
        assertEquals(7000L, VideoCut.total(clips))
        assertEquals(0L, VideoCut.offsetOf(clips, 0))
        assertEquals(3000L, VideoCut.offsetOf(clips, 1))
        assertEquals(4000L, VideoCut.offsetOf(clips, 2))
    }

    @Test fun editedRoundTripWithSpeed() {
        assertEquals(3500L, VideoCut.toEdited(clips, 1, 11_000))
        assertEquals(1 to 11_000L, VideoCut.fromEdited(clips, 3500))
        // Повторённый кусок: то же место исходника, но другое время ленты.
        assertEquals(2 to 1500L, VideoCut.fromEdited(clips, 4500))
        assertEquals(0 to 1000L, VideoCut.fromEdited(clips, -10))
        assertEquals(2 to 4000L, VideoCut.fromEdited(clips, 99_000))
        assertEquals(0 to 0L, VideoCut.fromEdited(emptyList(), 100))
    }

    @Test fun splitAndTrimAtPlayhead() {
        val (l, right) = VideoCut.split(clips, 1500)!!
        assertEquals(4, l.size)
        assertEquals(1, right)
        assertEquals(Clip(0, 1000, 2500), l[0])
        assertEquals(Clip(0, 2500, 4000), l[1])
        // У самого края не режем.
        assertNull(VideoCut.split(clips, 50))
        assertEquals(Clip(1, 11_000, 12_000, 2f), VideoCut.trimBefore(clips, 3500)!![1])
        assertEquals(Clip(1, 10_000, 11_000, 2f), VideoCut.trimAfter(clips, 3500)!![1])
    }

    @Test fun duplicateDeleteMove() {
        assertEquals(4, VideoCut.duplicate(clips, 1).size)
        assertEquals(2, VideoCut.delete(clips, 0).size)
        assertEquals(1, VideoCut.delete(listOf(clips[0]), 0).size)
        assertEquals(clips[1], VideoCut.move(clips, 1, -1)[0])
        assertEquals(clips, VideoCut.move(clips, 0, -1))
    }

    @Test fun ticksDoNotOverlap() {
        assertEquals(10_000L, VideoCut.tickStep(400f / 60_000))
        assertEquals(100L, VideoCut.tickStep(1f))
        assertEquals(250L, VideoCut.tickStep(0.3f))
    }

    @Test fun parsesTypedTime() {
        assertEquals(65_300L, VideoCut.parseTime("1:05.3"))
        assertEquals(65_000L, VideoCut.parseTime("65"))
        assertEquals(7_250L, VideoCut.parseTime("0:07,25"))
        assertEquals(3_723_000L, VideoCut.parseTime("1:02:03"))
        assertNull(VideoCut.parseTime(""))
        assertNull(VideoCut.parseTime("1:75"))
        assertNull(VideoCut.parseTime("a:10"))
    }
}
