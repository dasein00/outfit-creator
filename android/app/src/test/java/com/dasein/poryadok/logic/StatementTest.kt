package com.dasein.poryadok.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatementTest {
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()

    @Test
    fun sberPdfText() {
        val text = """
            Расшифровка операций
            Дата операции Дата обработки Категория Сумма в валюте счёта Остаток средств
            03.10.2026 14:22 123456 Супермаркеты 1 234,56 45 678,90
            04.10.2026 PYATEROCHKA 1234 Moscow RUS. Операция по карте ****1234
            05.10.2026 09:01 654321 Прочие поступления +50 000,00 95 678,90
            06.10.2026 Зарплата. Операция по счёту ****1234
            Остаток на 31.10.2026 95 678,90
        """.trimIndent()
        val ops = Statement.fromText(text) { t, inc -> BankSms.guessCategory(t, inc) }
        assertEquals(2, ops.size)
        assertEquals(1234.56, ops[0].amount, 0.001)
        assertFalse(ops[0].income)
        assertEquals(day(2026, 10, 3), ops[0].day)
        assertTrue(ops[0].text.contains("PYATEROCHKA"))
        assertEquals("Продукты", ops[0].category)
        assertEquals(50000.0, ops[1].amount, 0.001)
        assertTrue(ops[1].income)
    }

    @Test
    fun tbankCsv() {
        val csv = "Дата операции;Дата платежа;Номер карты;Статус;Сумма операции;Валюта операции;Сумма платежа;Валюта платежа;Кэшбэк;Категория;MCC;Описание\n" +
            "\"03.10.2026 12:00:00\";\"03.10.2026\";\"*1234\";\"OK\";\"-350,00\";\"RUB\";\"-350,00\";\"RUB\";\"\";\"Кафе\";\"5814\";\"Кофейня\"\n" +
            "\"04.10.2026 09:00:00\";\"04.10.2026\";\"\";\"OK\";\"5000,00\";\"RUB\";\"5000,00\";\"RUB\";\"\";\"Переводы\";\"\";\"Иван И.\"\n" +
            "\"05.10.2026 10:00:00\";\"05.10.2026\";\"*1234\";\"FAILED\";\"-100,00\";\"RUB\";\"-100,00\";\"RUB\";\"\";\"Кафе\";\"\";\"Отказ\"\n"
        val ops = Statement.fromRows(Statement.parseCsv(csv))
        assertEquals(2, ops.size)
        assertEquals(350.0, ops[0].amount, 0.001)
        assertFalse(ops[0].income)
        assertEquals("Кофейня", ops[0].text)
        assertTrue(ops[1].income)
        assertEquals(day(2026, 10, 4), ops[1].day)
    }

    @Test
    fun debitCreditColumns() {
        val rows = listOf(
            listOf("Выписка по счёту"),
            listOf("Дата", "Описание", "Приход", "Расход"),
            listOf("2026-10-01", "Пополнение", "1 000,00", ""),
            listOf("45930", "Магнит", "", "250.5"),
        )
        val ops = Statement.fromRows(rows)
        assertEquals(2, ops.size)
        assertTrue(ops[0].income)
        assertEquals(250.5, ops[1].amount, 0.001)
        assertFalse(ops[1].income)
        assertEquals(LocalDate.of(1899, 12, 30).plusDays(45930).toEpochDay(), ops[1].day)
    }

    @Test
    fun duplicates() {
        val op = Statement.Op(day(2026, 10, 3), 350.0, false, "Кофе")
        assertTrue(Statement.isDuplicate(op, listOf(Triple(day(2026, 10, 4), 350.0, false))))
        assertFalse(Statement.isDuplicate(op, listOf(Triple(day(2026, 10, 3), 350.0, true))))
    }
}
