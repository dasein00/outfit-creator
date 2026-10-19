package com.dasein.poryadok.logic

import com.dasein.poryadok.data.BodyMetric
import com.dasein.poryadok.system.BodyAccounts
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyAccountsTest {
    private val others = listOf(
        BodyMetric(at = 1_000_000, day = 1, weight = 55.2, extId = "hc:a"),
        BodyMetric(at = 5_000_000, day = 1, weight = 54.8),
    )

    @Test fun otherPersonsReadingsAreNotImportedAgain() {
        // Тот же id Health Connect — уже перенесено в другой аккаунт.
        assertTrue(BodyAccounts.isForeign(others, "hc:a", 9_999_999, 80.0))
        // Внесено вручную или по скриншоту: то же время (±10 мин) и тот же вес.
        assertTrue(BodyAccounts.isForeign(others, "hc:b", 5_000_000 + 5 * 60_000, 54.9))
        // Взвешивание владельца в то же время, но с другим весом — его.
        assertFalse(BodyAccounts.isForeign(others, "hc:c", 5_000_000, 82.4))
        assertFalse(BodyAccounts.isForeign(emptyList(), "hc:a", 1_000_000, 55.2))
    }
}
