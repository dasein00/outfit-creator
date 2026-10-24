package com.dasein.poryadok.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CyrOcrTest {
    private val garbled = "MTaK, MHrpegveHTbI:\n• TBOPOr obe3xupeHHbI –360r\n• KeNTOK – 4 WT\nCbip cynyryHu- 130 r\nrOTOBMM:"

    @Test fun detectsGarbled() {
        assertTrue(CyrOcr.looksGarbled(garbled))
        assertFalse(CyrOcr.looksGarbled("Mix the flour with 2 eggs and bake 20 minutes"))
        assertFalse(CyrOcr.looksGarbled("творог обезжиренный — 360 г"))
    }

    @Test fun convertsToCyrillic() {
        val out = CyrOcr.fix(garbled)
        assertTrue(out, "ингредиенты" in out || "мнгредиенты" in out)
        assertTrue(out, "360г" in out)
        assertTrue(out, "130 г" in out)
        assertTrue(out, "4 шт" in out)
    }

    @Test fun keepsEnglish() {
        val en = "Mix the flour with 2 eggs"
        assertEquals(en, CyrOcr.fix(en))
    }
}
