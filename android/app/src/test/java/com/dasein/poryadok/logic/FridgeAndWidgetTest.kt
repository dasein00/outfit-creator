package com.dasein.poryadok.logic

import com.dasein.poryadok.system.WRow
import com.dasein.poryadok.system.WidgetBlock
import com.dasein.poryadok.system.WidgetConfig
import com.dasein.poryadok.system.WidgetModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FridgeAndWidgetTest {
    private val omelet = listOf("Яйцо куриное", "Молоко 2,5%", "Соль", "Масло растительное")
    private val chicken = listOf("Куриное филе", "Рис", "Лук репчатый", "Соль", "Перец чёрный молотый")

    @Test fun mainIngredientIsRequired() {
        val spec = Fridge.Spec(main = setOf("Куриное филе"))
        assertTrue(Fridge.fit(chicken, spec).ok)
        assertFalse(Fridge.fit(omelet, spec).ok)
    }

    @Test fun missingProductsFilterOut() {
        val spec = Fridge.Spec(have = setOf("Куриное филе", "Рис"), missing = setOf("Лук репчатый"))
        assertFalse(Fridge.fit(chicken, spec).ok)
    }

    @Test fun staplesCountAsAvailable() {
        val spec = Fridge.Spec(have = setOf("Яйцо куриное", "Молоко 2,5%"), onlyHave = true)
        val f = Fridge.fit(omelet, spec)
        assertTrue(f.ok)
        assertEquals(emptyList<String>(), f.lacking)
        val g = Fridge.fit(chicken, Fridge.Spec(have = setOf("Куриное филе"), onlyHave = true))
        assertFalse(g.ok)
        assertEquals(listOf("Рис", "Лук репчатый"), g.lacking)
    }

    @Test fun rankPrefersMoreOwnProducts() {
        val spec = Fridge.Spec(have = setOf("Куриное филе", "Рис", "Яйцо куриное"))
        val a = Fridge.fit(chicken, spec)
        val b = Fridge.fit(omelet, spec)
        assertTrue(Fridge.rank(a) < Fridge.rank(b))
    }

    @Test fun specRoundTrip() {
        val spec = Fridge.Spec(setOf("Куриное филе"), setOf("Рис", "Лук"), setOf("Грибы"), true)
        assertEquals(spec, Fridge.decode(Fridge.encode(spec)))
        assertEquals(Fridge.Spec(), Fridge.decode(""))
    }

    @Test fun weatherDetailsShowOnlyWhenRoomLeft() {
        val cfg = WidgetConfig()
        val rows = listOf(
            WRow(0, null, "Погода", "+12°"),
            WRow(4, null, "Осадки 60 % · Ощущается +9°", extra = true),
            WRow(4, null, "Влажность 80 % · 750 мм", extra = true),
            WRow(3, null, "Задача"),
            WRow(3, null, "Задача 2"),
        )
        // Мало места: погода и задачи, без подробностей.
        val small = WidgetModels.visible(rows, cfg, 90f)
        assertEquals(listOf("Погода", "Задача", "Задача 2"), small.map { it.title })
        // Много места: подробности под погодой, на своих местах.
        val big = WidgetModels.visible(rows, cfg, 200f)
        assertEquals(rows.map { it.title }, big.map { it.title })
    }

    @Test fun migrationHidesWeightAndPutsWeatherFirst() {
        val old = WidgetConfig(blocks = listOf(WidgetBlock("weight", 1), WidgetBlock("tasks"), WidgetBlock("weather", on = false)), rev = 1).normalized()
        assertEquals("weather", old.blocks.first().type)
        assertTrue(old.blocks.first().on)
        assertFalse(old.blocks.first { it.type == "weight" }.on)
        // Повторная нормализация не трогает выбор пользователя.
        val back = old.copy(blocks = old.blocks.map { if (it.type == "weight") it.copy(on = true) else it }).normalized()
        assertTrue(back.blocks.first { it.type == "weight" }.on)
    }
}
