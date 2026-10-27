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

    @Test fun foreignAndJunk() {
        org.junit.Assert.assertTrue(RecipeStyle.isForeign("Ingredients: 2 cups flour, 1 tsp salt, bake at 350F for 20 minutes"))
        org.junit.Assert.assertFalse(RecipeStyle.isForeign("Творог 360 г, желток 4 шт, мука 50 г, сулугуни 130 г"))
        org.junit.Assert.assertTrue(RecipeStyle.isJunkTitle("🥒 итак, ингредиенты:"))
        org.junit.Assert.assertTrue(RecipeStyle.isJunkTitle("Рецепт дня"))
        org.junit.Assert.assertTrue(RecipeStyle.isJunkTitle("Creamy pasta"))
        org.junit.Assert.assertFalse(RecipeStyle.isJunkTitle("Хачапури по-аджарски из творога"))
    }
}
