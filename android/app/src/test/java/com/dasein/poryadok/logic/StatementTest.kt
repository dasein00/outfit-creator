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
    fun sberSavingsStatement() {
        val text = """
            Выписка по счёту «Накопительный счёт»
            За период 28.01.2025 — 14.10.2026
            Номер счёта 40817 810 0 0000 0000001 Пополнение 252 792,60
            Процентная ставка 6.5% годовых Списание 186 200,00
            Иванов Остаток на 28.01.2025 0,00
            Срок счёта Бессрочный Остаток на 14.10.2026 66 592,60
            ДАТА НАИМЕНОВАНИЕ ОПЕРАЦИИ ДОПОЛНИТЕЛЬНАЯ ИНФОРМАЦИЯ СУММА ОСТАТОК
            28.09.2026 Капитализация вклада 08, № 1234567890-37 +365,61 66 592,60
            к/с 47411 810 0 0000 0000001 За период с 29. 08. 2026 по 28. 09. 2026
            66 226, 99 ₽ * 31 день * 6. 5% / 365 дней) =
            06.05.2026 Списание 03, № 1234567890-32 -5 000,00 64 800,33
            Тип операции — безналично Получатель: И. ИВАН ИВАНОВИЧ
            к/с 40817 810 0 0000 0000001 Наименование услуги: Перевод между
            счетами одного клиента
            Лицевой счёт: •••• 1111
            Продолжение на следующей странице
            28.01.2025 Зачисление 02, № 1234567890-2 +226 822,83 226 822,83
            Плательщик: И. ИВАН ИВАНОВИЧ Счет плательщика: •••• 1111
            28.01.2025 Открытие счета 01, № 1234567890-1 +0,00 0,00
            Дата формирования документа 14.10.2026
        """.trimIndent()
        val h = Statement.header(text)
        assertEquals("Накопительный счёт", h.title)
        assertTrue(h.savings)
        assertEquals("40817810000000000001", h.number)
        assertEquals(6.5, h.rate!!, 0.001)
        assertEquals(66592.60, h.closing!!, 0.001)
        assertEquals(day(2026, 10, 14), h.closingDay)
        val ops = Statement.fromText(text) { t, inc -> BankSms.guessCategory(t, inc) }
        assertEquals(3, ops.size)
        assertTrue(ops[0].income)
        assertEquals(365.61, ops[0].amount, 0.001)
        assertEquals(66592.60, ops[0].balance!!, 0.001)
        assertEquals("1234567890-37", ops[0].ref)
        assertEquals("Проценты", ops[0].category)
        assertFalse(ops[1].income)
        assertEquals(5000.0, ops[1].amount, 0.001)
        assertTrue(ops[1].transfer)
        assertEquals("1111", ops[1].peer)
        assertTrue(ops[2].income)
        assertEquals(226822.83, ops[2].amount, 0.001)
        // Повторная загрузка той же выписки даёт те же ключи.
        assertEquals(com.dasein.poryadok.system.StatementImport.keys(ops, h, "x"), com.dasein.poryadok.system.StatementImport.keys(Statement.fromText(text), h, "x"))
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
