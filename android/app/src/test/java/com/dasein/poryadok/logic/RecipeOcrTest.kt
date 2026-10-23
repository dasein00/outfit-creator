package com.dasein.poryadok.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeOcrTest {
    @Test fun screenChromeIsDropped() {
        val lines = listOf(
            "8:07", "VPN 55", "chef_anna", "Подписаться", "Сырники на завтрак", "Творог 5% — 400 г", "Яйцо — 1 шт",
            "1 234 отметок «Нравится»", "Посмотреть все комментарии (56)", "ещё", "3 дн.",
        )
        assertEquals("chef_anna\nСырники на завтрак\nТворог 5% — 400 г\nЯйцо — 1 шт", RecipeText.cleanOcr(lines))
    }
}
