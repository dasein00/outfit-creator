package com.dasein.poryadok.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CookingTest {
    private val chicken = Macros(113.0, 23.6, 1.9, 0.4)

    @Test fun gramsByUnit() {
        assertEquals(150.0, Cooking.grams(150.0, "г"), 0.0)
        assertEquals(1500.0, Cooking.grams(1.5, "кг"), 0.0)
        assertEquals(110.0, Cooking.grams(2.0, "шт", 55.0), 0.0)
        assertEquals(30.0, Cooking.grams(2.0, "ст.л."), 0.0)
        assertEquals(0.0, Cooking.grams(0.0, "по вкусу"), 0.0)
    }

    @Test fun perServingAndScale() {
        val list = listOf(Ingr("Куриное филе", 400.0, "г", 400.0, chicken))
        val m = Cooking.perServing(list, 2)
        assertEquals(226.0, m.kcal, 0.01)
        assertEquals(47.2, m.protein, 0.01)
        val scaled = Cooking.scale(list, 1.5)
        assertEquals(600.0, scaled[0].amount, 0.0)
        assertEquals(678.0, Cooking.total(scaled).kcal, 0.01)
    }

    @Test fun amountFormatting() {
        assertEquals("1,5", Cooking.amount(1.5))
        assertEquals("120", Cooking.amount(120.0))
        assertEquals("0,33", Cooking.amount(1.0 / 3))
        assertEquals("13", Cooking.amount(12.6))
    }

    @Test fun shoppingMergesSameProductAcrossUnits() {
        val lines = Cooking.mergeShopping(
            listOf(
                Ingr("Куриное филе", 400.0, "г", 400.0, chicken, "Мясо"),
                Ingr("куриное филе ", 0.7, "кг", 700.0, chicken, "Мясо"),
                Ingr("Соль", 0.0, "по вкусу", 0.0, Macros(), "Бакалея"),
                Ingr("Морковь", 2.0, "шт", 160.0, Macros(), "Овощи"),
            )
        )
        assertEquals(2, lines.size)
        assertEquals("Овощи", lines[0].category)
        val meat = lines.first { it.category == "Мясо" }
        assertEquals("кг", meat.unit)
        assertEquals(1.1, meat.amount, 1e-9)
    }

    @Test fun autofillHitsTargetAndRespectsExclusions() {
        val c = listOf(
            Cooking.Candidate(1, listOf(MealType.BREAKFAST), Macros(350.0, 20.0, 10.0, 40.0), 10, "Завтраки", setOf("Овсяные хлопья")),
            Cooking.Candidate(2, listOf(MealType.BREAKFAST), Macros(300.0, 25.0, 12.0, 20.0), 15, "Завтраки", setOf("Яйцо куриное")),
            Cooking.Candidate(3, listOf(MealType.LUNCH), Macros(500.0, 40.0, 15.0, 45.0), 40, "Обеды", setOf("Куриное филе")),
            Cooking.Candidate(4, listOf(MealType.LUNCH), Macros(450.0, 30.0, 20.0, 30.0), 30, "Обеды", setOf("Лосось")),
            Cooking.Candidate(5, listOf(MealType.DINNER), Macros(400.0, 35.0, 10.0, 20.0), 25, "Ужины", setOf("Треска")),
        )
        val picks = Cooking.autofill(c, Cooking.AutoParams(kcal = 1500, meals = listOf(MealType.DINNER, MealType.BREAKFAST, MealType.LUNCH), exclude = setOf("лосось")))
        assertEquals(listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER), picks.map { it.first })
        assertFalse(picks.any { it.second == 4L })
        val total = picks.sumOf { (_, id, s) -> c.first { it.id == id }.perServing.kcal * s }
        assertTrue("total=$total", total in 1100.0..1900.0)
    }

    @Test fun mealsCsv() {
        assertEquals(listOf(0, 4), MealType.parse("0, 4,9,x"))
    }
}

class NotebookTest {
    private val sample = """
        {"currency":"RUB","year_assumption":2026,
         "normalized_records":[
           {"date":"2026-03-01","year":2026,"month":3,"day":1,"type":"income","amount_rub":1500,"category":"зарплата/ежедневный доход","description":null,"source":"фото","confidence":"medium"},
           {"date":"2026-03-02","year":2026,"month":3,"day":2,"type":"income","amount_rub":2000,"category":"зарплата/ежедневный доход","description":null,"source":"фото","confidence":"medium"},
           {"date":null,"year":2026,"month":3,"day":3,"type":"no_income_recorded","amount_rub":null,"category":null,"description":"выходной","source":"фото","confidence":"medium"},
           {"date":null,"year":2026,"month":3,"day":null,"type":"monthly_note","amount_rub":3500,"category":"итого","description":"вверху","source":"фото","confidence":"medium"},
           {"date":null,"year":2026,"month":3,"day":null,"type":"monthly_note","amount_rub":9000,"category":"расход","description":"внизу","source":"фото","confidence":"medium"},
           {"date":null,"year":2026,"month":3,"day":null,"type":"monthly_note","amount_rub":700,"category":"остаток","description":"","source":"фото","confidence":"medium"}
         ],
         "monthly_information":{"2026-03":{"raw_expense_notes":["2200 мясо","500 бенз","100 курьер (?)","500 бенз","прочие записи справа"],"uncertainty":"часть неразборчива"}}}
    """.trimIndent()

    @Test fun parsesJsonWithoutChangingValues() {
        val d = Notebook.parse(sample)
        assertEquals(2026, d.year)
        assertEquals(2, d.incomes.size)
        assertEquals(1500.0, d.incomes[0].amount, 0.0)
        assertEquals(3 to 1, d.incomes[0].month to d.incomes[0].day)
        assertEquals(1, d.noIncome.size)
        assertEquals(3, d.written.size)
        assertEquals(5, d.expenses.size)
    }

    @Test fun expenseNotes() {
        val meat = Notebook.parseExpense(3, 0, "2200 мясо")
        assertEquals(2200.0, meat.amount!!, 0.0)
        assertEquals("Продукты", meat.category)
        assertFalse(meat.ambiguous)
        val unsure = Notebook.parseExpense(3, 1, "100 курьер (?)")
        assertEquals(Notebook.UNKNOWN, unsure.category)
        assertTrue(unsure.ambiguous)
        assertEquals("100 курьер (?)", unsure.raw)
        val none = Notebook.parseExpense(3, 2, "прочие записи справа")
        assertNull(none.amount)
        assertTrue(none.ambiguous)
        assertEquals("Транспорт", Notebook.parseExpense(3, 3, "500 бенз").category)
    }

    @Test fun analysisKeepsBothValuesOnConflict() {
        val a = Notebook.analyze(Notebook.parse(sample))
        val m = a.months.single()
        assertEquals(3500.0, m.incomeSum, 0.0)
        assertEquals(3300.0, m.expenseSum, 0.0)
        // «итого 3500» совпадает с суммой доходов, «расход 9000» — нет, «остаток» ни с чем не сравнивается.
        assertEquals(1, a.conflicts.size)
        assertEquals(9000.0, a.conflicts[0].written, 0.0)
        assertEquals(3300.0, a.conflicts[0].computed, 0.0)
        assertTrue(a.ambiguous.any { "500 бенз" in it && "2 раза" in it })
        assertTrue(a.ambiguous.any { "курьер" in it })
    }

    @Test fun parsesCsv() {
        val csv = "﻿date,year,month,month_ru,day,type,amount_rub,category,description,source,confidence\n" +
            "2026-02-01,2026,2,Февраль,1,income,1500,зарплата/ежедневный доход,,фото,medium\n" +
            ",2026,2,Февраль,10,no_income_recorded,,,В тетради нет суммы,фото,medium\n" +
            ",2026,3,Март,,monthly_note,58500,итого,\"Сумма, записана отдельно\",фото,medium\n"
        val d = Notebook.parse(csv)
        assertEquals(1, d.incomes.size)
        assertEquals(2 to 1, d.incomes[0].month to d.incomes[0].day)
        assertEquals(1, d.noIncome.size)
        assertEquals("Сумма, записана отдельно", d.written.single().description)
    }
}

class BankSmsTest {
    @Test fun purchaseSms() {
        val op = BankSms.parse("MIR-1234 14:05 Покупка 350р PYATEROCHKA 1234 Баланс: 12 000.50р")
        assertNotNull(op)
        assertEquals(BankSms.Kind.EXPENSE, op!!.kind)
        assertEquals(350.0, op.amount, 0.0)
        assertEquals("Продукты", op.category)
        assertEquals("MIR-1234", op.card)
    }

    @Test fun incomeWithSpaces() {
        val op = BankSms.parse("СЧЁТ1234 10:00 зачисление зарплаты 45 000р Баланс: 50 100р")!!
        assertEquals(BankSms.Kind.INCOME, op.kind)
        assertEquals(45000.0, op.amount, 0.0)
        assertEquals("Зарплата", op.category)
    }

    @Test fun transferFrom() {
        val op = BankSms.parse("Перевод от Иван И. 1 500,50 ₽")!!
        assertEquals(BankSms.Kind.INCOME, op.kind)
        assertEquals(1500.5, op.amount, 0.001)
    }

    @Test fun pushWithRub() {
        val op = BankSms.parse("Оплата 499 руб. Яндекс Go. Доступно 3 200 руб")!!
        assertEquals(BankSms.Kind.EXPENSE, op.kind)
        assertEquals(499.0, op.amount, 0.0)
        assertEquals("Транспорт", op.category)
    }

    @Test fun ignoresCodes() {
        assertNull(BankSms.parse("Код: 1234. Никому не сообщайте код. Покупка 500р"))
        assertNull(BankSms.parse("Недостаточно средств. Покупка 900р OZON"))
        assertNull(BankSms.parse("Привет! Как дела?"))
    }

    @Test fun sources() {
        assertTrue(BankSms.isSberSource("ru.sberbankmobile", "Покупка"))
        assertTrue(BankSms.isSberSource("com.google.android.apps.messaging", "900"))
        assertFalse(BankSms.isSberSource("com.whatsapp", "Мама"))
    }
}
