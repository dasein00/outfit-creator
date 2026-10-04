package com.dasein.poryadok.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PaperTableTest {
    private val csv = """
        Дата;Расход;Доход;Примечание
        03.09.2026;1 250,50;;продукты в Пятёрочке
        ;300;;аптека, таблетки
        04.09.2026;;22 000;пенсия
        05.09.26;4 800 ₽;;квартплата за август
        06.09.2026;150;500;кофе / вернули долг
        07.09.2026;;;
        сентябрь;200;;что-то
        08.09.2026;abc;;ошибка
        Итого;6500,5;22500;
    """.trimIndent()

    @Test fun recognisesTemplateTable() {
        assertTrue(PaperTable.isTable(csv))
        assertFalse(PaperTable.isTable("date,year,month,month_ru,day,type,amount_rub"))
    }

    @Test fun parsesRowsWithDatesAmountsAndCategories() {
        val d = Notebook.parse(csv)
        assertEquals(6, d.dated.size)
        val first = d.dated[0]
        assertEquals(LocalDate.of(2026, 9, 3).toEpochDay(), first.epochDay)
        assertEquals(1250.5, first.amount, 0.001)
        assertEquals("Продукты", first.category)
        // Пустая дата — как строкой выше.
        assertEquals(first.epochDay, d.dated[1].epochDay)
        assertEquals("Здоровье", d.dated[1].category)
        assertEquals("Пенсия", d.dated[2].category)
        assertTrue(d.dated[2].income)
        assertEquals("Жильё и ЖКХ", d.dated[3].category)
        assertEquals(4800.0, d.dated[3].amount, 0.001)
        // Расход и доход в одной строке — две операции.
        assertEquals(2, d.dated.count { it.row == 6 })
        assertEquals(2026, d.year)
        assertEquals(2, d.skipped.size)
    }

    @Test fun amountsAndDates() {
        assertEquals(1500.0, PaperTable.amountOf("1 500 р.")!!, 0.001)
        assertEquals(99.9, PaperTable.amountOf("99,90")!!, 0.001)
        assertNull(PaperTable.amountOf(""))
        assertEquals(LocalDate.of(2026, 3, 5), PaperTable.dateOf("5.3", 2026))
        assertEquals(LocalDate.of(2026, 3, 5), PaperTable.dateOf("2026-03-05", 2000))
        assertEquals(LocalDate.of(2026, 3, 5), PaperTable.dateOf("05/03/2026", 2000))
        assertEquals(LocalDate.of(2026, 3, 5), PaperTable.dateOf("46086", 2000))
        assertNull(PaperTable.dateOf("31.02.2026", 2026))
    }

    @Test fun categoryWordsDoNotMatchInsideOtherWords() {
        assertEquals("Продукты", PaperTable.categoryOf("магазин", false))
        assertEquals("Другое", PaperTable.categoryOf("Светлане за помощь", false))
        assertEquals("Жильё и ЖКХ", PaperTable.categoryOf("за свет", false))
        assertEquals("Зарплата", PaperTable.categoryOf("ЗП за сентябрь", true))
        assertEquals("Другое", PaperTable.categoryOf("", true))
    }
}
