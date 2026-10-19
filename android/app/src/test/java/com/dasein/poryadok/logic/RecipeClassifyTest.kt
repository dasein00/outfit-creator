package com.dasein.poryadok.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeClassifyTest {
    @Test fun soupIsLunch() {
        val p = RecipeText.parse("Борщ\nИнгредиенты:\nСвёкла — 300 г\nКапуста — 400 г\nГовядина — 500 г\nПриготовление:\n1. Варим бульон 90 минут.\n2. Добавляем овощи и варим 20 минут.")
        val g = RecipeClassify.guess(p, "Борщ")
        assertEquals("Супы", g.category)
        assertEquals(listOf(MealType.LUNCH), g.meals)
        assertTrue(g.cookMin >= 110)
    }

    @Test fun breakfastFromTitle() {
        val p = RecipeText.parse("Сырники из творога\nТворог 5% — 400 г\nЯйцо — 1 шт\nМука — 3 ст.л.\nЖарим 3 минуты с каждой стороны.")
        val g = RecipeClassify.guess(p, "Сырники из творога")
        assertEquals("Завтраки", g.category)
        assertTrue(MealType.BREAKFAST in g.meals)
        assertTrue("вегетарианские" in g.tags)
        assertEquals(1, g.difficulty)
    }

    @Test fun worldCuisineMainDish() {
        val text = "Сегодня готовим плов. Баранина 1 кг, рис 1 кг, морковь 1 кг. Обжариваем мясо, добавляем морковь, потом рис, томим 40 минут."
        val p = RecipeText.parse(text)
        val g = RecipeClassify.guess(p, text)
        assertEquals(WORLD_CATEGORY, g.category)
        assertTrue("узбекская кухня" in g.tags)
        assertEquals("Плов", g.title)
    }

    @Test fun saidMealWinsAndTipsFound() {
        val text = "Курица с овощами — идеально на ужин. Совет: не пересушите грудку, держите не больше 20 минут. Вместо брокколи можно взять цветную капусту."
        val g = RecipeClassify.guess(RecipeText.parse(text), text)
        assertEquals(listOf(MealType.DINNER), g.meals)
        assertTrue(g.tips, g.tips.contains("не пересушите"))
        assertTrue(g.swaps, g.swaps.contains("цветную капусту"))
    }

    @Test fun totalTimeIsSplit() {
        val p = RecipeText.parse("Омлет\nВремя приготовления: 15 минут\nЯйца — 3 шт\nМолоко — 50 мл")
        val g = RecipeClassify.guess(p, "Омлет")
        assertEquals(15, g.prepMin + g.cookMin)
        assertTrue("быстро" in g.tags)
    }
}
