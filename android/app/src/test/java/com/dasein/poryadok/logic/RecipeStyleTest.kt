package com.dasein.poryadok.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeStyleTest {
    @Test fun title() {
        assertEquals("Хачапури по-аджарски", RecipeStyle.title("🥚 ХАЧАПУРИ ПО-АДЖАРСКИ!"))
        assertEquals("Сырники из творога", RecipeStyle.title("«сырники из творога» #завтрак"))
    }

    @Test fun ingredient() {
        assertEquals("Творог 5%", RecipeStyle.ingredient("• ТВОРОГ 5%:"))
        assertEquals("Сыр сулугуни", RecipeStyle.ingredient("Сыр Сулугуни"))
    }

    @Test fun step() {
        assertEquals("Запекайте при 180 °C 15–20 минут.", RecipeStyle.step("3. запекайте при 180 градусов 15-20 минут 🔥"))
        assertEquals("Смешайте творог и желток — до однородности.", RecipeStyle.step("Шаг 1: смешайте творог и желток - до однородности"))
        assertEquals("Подавайте горячим!", RecipeStyle.step("подавайте горячим!"))
    }

    @Test fun bulletsAndTags() {
        assertEquals("• Сулугуни можно заменить моцареллой.\n• Храните в холодильнике.", RecipeStyle.bullets("- сулугуни можно заменить моцареллой\n• храните в холодильнике"))
        assertEquals("итальянская кухня", RecipeStyle.tag("#Итальянская кухня"))
    }
}
