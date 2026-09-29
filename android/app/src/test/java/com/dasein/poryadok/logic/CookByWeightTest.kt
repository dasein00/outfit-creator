package com.dasein.poryadok.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class CookByWeightTest {
    private fun ing(name: String, amount: Double, unit: String, grams: Double, cat: String = "Другое") = Ingr(name, amount, unit, grams, Macros(), cat)

    private val teriyaki = listOf(
        ing("Соевый соус", 3.0, "ст.л.", 45.0, "Бакалея"),
        ing("Куриное филе", 500.0, "г", 500.0, "Мясо"),
        ing("Мёд", 1.0, "ст.л.", 15.0, "Бакалея"),
        ing("Чеснок", 2.0, "шт", 10.0, "Овощи"),
        ing("Имбирь", 1.0, "ч.л.", 5.0, "Овощи"),
        ing("Кунжут", 1.0, "щепотка", 1.0, "Бакалея"),
        ing("Соль", 0.0, "по вкусу", 0.0, "Бакалея"),
        ing("Рис белый", 600.0, "г", 600.0, "Крупы"),
    )

    @Test fun picksMeatAsMain() {
        assertEquals(1, Cooking.mainIndex(teriyaki))
        assertEquals(0, Cooking.mainIndex(listOf(ing("Творог 5%", 400.0, "г", 400.0, "Молочные продукты"), ing("Мука", 70.0, "г", 70.0, "Бакалея"))))
        assertEquals(1, Cooking.mainIndex(listOf(ing("Лук", 50.0, "г", 50.0, "Овощи"), ing("Картофель", 600.0, "г", 600.0, "Овощи"))))
    }

    @Test fun scalesByMainWeightAndShowsSmallInGrams() {
        val f = Cooking.factorFor(teriyaki[1], 350.0)!!
        assertEquals(0.7, f, 1e-9)
        val s = Cooking.scale(teriyaki, f)
        assertEquals("350 г", Cooking.weighLabel(s[1]))
        assertEquals("2,1 ст.л. (31 г)", Cooking.weighLabel(s[0]))
        assertEquals("3,5 г", Cooking.weighLabel(s[4]))
        assertEquals("0,7 г", Cooking.weighLabel(s[5]))
        assertEquals("1,4 шт (≈ 7 г)", Cooking.weighLabel(s[3]))
        assertEquals("по вкусу", Cooking.weighLabel(s[6]))
        assertEquals("420 г", Cooking.weighLabel(s[7]))
    }
}
