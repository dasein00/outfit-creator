package com.dasein.poryadok.logic

import com.dasein.poryadok.data.UiTextEngine
import com.dasein.poryadok.data.UiTextEngine.Entry
import org.junit.Assert.assertEquals
import org.junit.Test

class UiTextEngineTest {
    private fun e(id: Int, orig: String, final: String = "", frag: Boolean = false, sug: String = "") =
        Entry(id, "Раздел", "A.kt:$id", frag, orig, sug, final)

    private val entries = listOf(
        e(1, "Стереть", "Удалить"),
        e(2, "Вы прочитали все {1} фактов! Начинаем новый круг.", "Готово: {1} фактов. Начинаем заново."),
        e(3, " · круглые стразы 2,8 мм", " · круглые 2,8 мм", frag = true),
        e(4, "Пульс {1}, давление {2}", "Давление {2}, пульс {1}"),
        e(5, "{1}", "Что угодно"),
        e(6, "Сохранить"),
        e(7, " · осадки {1}%", " · дождь {1}%", frag = true),
    )

    @Test fun exactAndDefault() {
        val en = UiTextEngine(entries, emptyMap())
        assertEquals("Удалить", en.translate("Стереть"))
        assertEquals("Сохранить", en.translate("Сохранить"))
        assertEquals("Своё", en.translate("Своё"))
    }

    @Test fun templatesKeepValues() {
        val en = UiTextEngine(entries, emptyMap())
        assertEquals("Готово: 120 фактов. Начинаем заново.", en.translate("Вы прочитали все 120 фактов! Начинаем новый круг."))
        assertEquals("Давление 120/80, пульс 64", en.translate("Пульс 64, давление 120/80"))
        // Шаблон без слов не трогает остальные тексты.
        assertEquals("Любой текст", en.translate("Любой текст"))
    }

    @Test fun fragmentsInsideComposedText() {
        val en = UiTextEngine(entries, emptyMap())
        assertEquals("Выкладывать 900 страз · круглые 2,8 мм", en.translate("Выкладывать 900 страз · круглые стразы 2,8 мм"))
        assertEquals("Пн 12° · дождь 40%", en.translate("Пн 12° · осадки 40%"))
    }

    @Test fun userEditsWin() {
        val en = UiTextEngine(entries, mapOf("Стереть" to "Стереть всё", "Сохранить" to "Готово", "Кнопка 5" to "Пятая",
            "Вы прочитали все {1} фактов! Начинаем новый круг." to "Вы прочитали все {1} фактов! Начинаем новый круг."))
        assertEquals("Стереть всё", en.translate("Стереть"))
        assertEquals("Готово", en.translate("Сохранить"))
        assertEquals("Пятая", en.translate("Кнопка 5"))
        // «Как было» поверх варианта из документа.
        assertEquals("Вы прочитали все 3 фактов! Начинаем новый круг.", en.translate("Вы прочитали все 3 фактов! Начинаем новый круг."))
    }

    @Test fun findsCatalogRowsForScreenText() {
        val en = UiTextEngine(entries, mapOf("Кнопка 5" to "Пятая"))
        assertEquals(listOf(1), en.find("Стереть").map { it.id })
        assertEquals(listOf(4), en.find("Пульс 64, давление 120/80").map { it.id })
        assertEquals(listOf(3), en.find("Выкладывать 900 страз · круглые стразы 2,8 мм").map { it.id })
        assertEquals(emptyList<Int>(), en.find("Кнопка 5").map { it.id })
        assertEquals(mapOf("Кнопка 5" to "Пятая"), en.extraEdits)
    }

    @Test fun parsesCatalog() {
        val l = UiTextEngine.parse(sequenceOf("9\tРаздел\tA.kt:1\tL\tСтрока\\nвторая\t\tСтрока 2", "плохая строка"))
        assertEquals(1, l.size)
        assertEquals("Строка\nвторая", l[0].orig)
        assertEquals("Строка 2", l[0].default)
    }
}
