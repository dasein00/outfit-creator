package com.dasein.poryadok.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeMergeTest {
    private val post = """
        Курица со сливками
        Ингредиенты:
        - Куриное филе — 600 г
        - Сливки 20% — 200 мл
        - Соль по вкусу
        #ужин #курица
    """.trimIndent()

    private val speech = "Сначала нарезаем курицу кубиками и обжариваем на сильном огне пять минут до румяной корочки. " +
        "Потом вливаем сливки, убавляем огонь и тушим под крышкой минут десять, пока соус не загустеет. " +
        "В конце солим, перемешиваем и подаём с гарниром."

    @Test fun ingredientsFromPostStepsFromSpeech() {
        val p = RecipeText.merge(post, speech)
        assertEquals("Курица со сливками", p.title)
        assertTrue(p.ingredients.any { it.name.startsWith("Куриное филе") && it.amount == 600.0 })
        assertTrue(p.ingredients.any { it.name.startsWith("Сливки") })
        assertTrue("${p.steps}", p.steps.size >= 2)
        assertTrue(p.steps.first().text.contains("нарезаем"))
        assertTrue("ужин" in p.tags)
    }

    @Test fun postStepsWinWhenPresent() {
        val full = post + "\nПриготовление:\n1. Нарежьте филе.\n2. Обжарьте 5 минут.\n3. Влейте сливки и тушите 10 минут."
        val p = RecipeText.merge(full, speech)
        assertEquals(3, p.steps.size)
        assertEquals(10, p.steps[2].minutes)
    }
}
