package com.dasein.poryadok.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeTextTest {
    private val insta = """
        🍗 Курица в сливочном соусе с грибами
        Ингредиенты (на 4 порции):
        • Куриное филе — 600 г
        • Шампиньоны 300гр
        • Сливки 20% — 200 мл
        • Лук — 1 шт
        • Чеснок — 2 зубчика
        • Мука 1 ст.л.
        • Соль, перец — по вкусу
        Приготовление:
        1. Курицу нарезать кубиками и обжарить 5-7 минут.
        2. Добавить лук и грибы, жарить 10 минут.
        3. Влить сливки, посыпать мукой и тушить 15 минут под крышкой.
        #рецепт #курица
    """.trimIndent()

    @Test fun instagramPost() {
        val p = RecipeText.parse(insta)
        assertEquals("Курица в сливочном соусе с грибами", p.title)
        assertEquals(4, p.servings)
        val names = p.ingredients.map { it.name }
        assertEquals(listOf("Куриное филе", "Шампиньоны", "Сливки 20%", "Лук", "Чеснок", "Мука", "Соль", "Перец"), names)
        assertEquals(600.0, p.ingredients[0].amount, 0.01); assertEquals("г", p.ingredients[0].unit)
        assertEquals(300.0, p.ingredients[1].amount, 0.01); assertEquals("г", p.ingredients[1].unit)
        assertEquals("мл", p.ingredients[2].unit)
        assertEquals("шт", p.ingredients[4].unit); assertEquals(5.0, p.ingredients[4].gramsPerUnit, 0.01)
        assertEquals("ст.л.", p.ingredients[5].unit)
        assertEquals("по вкусу", p.ingredients[6].unit)
        assertEquals(3, p.steps.size)
        assertEquals(listOf(7, 10, 15), p.steps.map { it.minutes })
        assertTrue(p.steps[0].text.startsWith("Курицу нарезать"))
        assertEquals(listOf("рецепт", "курица"), p.tags)
    }

    @Test fun noHeadersAndParagraph() {
        val p = RecipeText.parse(
            """
            Сырники
            Творог 500 г
            2 яйца
            Мука 3 ст. л.
            Сахар 2 ст.л
            Масло для жарки
            Смешать творог, яйца и сахар. Добавить муку и сформировать небольшие сырники руками. Обжарить на сковороде по 3 минуты с каждой стороны до румяной корочки.
            """.trimIndent(),
        )
        assertEquals("Сырники", p.title)
        assertEquals(listOf("Творог", "Яйца", "Мука", "Сахар", "Масло для жарки"), p.ingredients.map { it.name })
        assertEquals("шт", p.ingredients[1].unit)
        assertTrue(p.steps.size >= 2)
        assertNull(p.servings)
    }

    @Test fun lines() {
        assertNull(RecipeText.ingredient("Разогреть духовку до 180 градусов"))
        val e = RecipeText.ingredient("½ ч.л. соды")!!.single()
        assertEquals(0.5, e.amount, 0.001); assertEquals("ч.л.", e.unit); assertEquals("Соды", e.name)
        assertEquals(3.5, RecipeText.ingredient("Картофель 3-4 шт")!!.single().amount, 0.01)
        assertEquals(90, RecipeText.minutesIn("Запекать 1,5 часа"))
    }
}

class RecipeTextUserExamplesTest {
    @Test fun doughWithNotes() {
        val p = RecipeText.parse(
            """
            Для теста:

            -Греческий йогурт 250 гр

            -пшеничная мука 150 Гр + 10-20 гр на обвалку

            -соль (при желании разрыхлитель, но я о нем забываю)

            Из сыров очень рекомендую взять сулугуни, он

            отлично плавится а если еще добавить зелень (лук /

            укроп/кинзу) то вообще сказка

            24 июля

            меньше
            """.trimIndent(),
        )
        assertEquals(listOf("Греческий йогурт", "Пшеничная мука", "Соль"), p.ingredients.map { it.name })
        assertEquals(250.0, p.ingredients[0].amount, 0.01)
        assertEquals(150.0, p.ingredients[1].amount, 0.01)
        assertEquals("г", p.ingredients[1].unit)
        assertEquals(0, p.steps.size)
        // Продукты из пояснения находятся отдельно по базе.
        val t = RecipeText.tokens("Из сыров очень рекомендую взять сулугуни, добавить зелень (лук / укроп/кинзу)")
        assertTrue(RecipeText.mentions(t, "сулугуни"))
        assertTrue(RecipeText.mentions(t, "лук"))
        assertTrue(RecipeText.mentions(t, "кинза"))
    }

    @Test fun listWithoutAmounts() {
        val p = RecipeText.parse(
            """
            МИНИ ПИЦЦЫ В ЛАВАШЕ

            Классическая докторская колбаса Нарлен обладает плотной, упругой, но при этом нежной текстурой, что идеально подходит для этого рецепта

            Ингредиенты:

            Докторская колбаса Narlen

            Сыр

            Майонез

            Лаваш

            Яйца

            Масло для жарки

            Приготовление:

            1. Лаваш нарезать кругами.
            2. Смазать майонезом, выложить колбасу и сыр.
            3. Обжарить на масле 3 минуты.
            """.trimIndent(),
        )
        assertEquals("МИНИ ПИЦЦЫ В ЛАВАШЕ", p.title)
        assertEquals(listOf("Докторская колбаса Narlen", "Сыр", "Майонез", "Лаваш", "Яйца", "Масло для жарки"), p.ingredients.map { it.name })
        assertEquals(3, p.steps.size)
        assertEquals(3, p.steps[2].minutes)
    }
}
