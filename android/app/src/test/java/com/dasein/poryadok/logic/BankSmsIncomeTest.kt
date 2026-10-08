package com.dasein.poryadok.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BankSmsIncomeTest {
    private val nnbsp = ' '
    private val nbsp = ' '

    @Test fun salaryPushWithNarrowSpaces() {
        val op = BankSms.parse("ФИЛИАЛ ФБУЗ \"ЦЕНТР ГИГИЕНЫ И ЭПИДЕМИОЛОГИИ\" Зарплата +56${nnbsp}555,43${nbsp}₽ МИР Золотая: 137${nnbsp}853,82${nbsp}₽")!!
        assertEquals(BankSms.Kind.INCOME, op.kind)
        assertEquals(56555.43, op.amount, 0.001)
        assertEquals("Зарплата", op.category)
        assertEquals("ФИЛИАЛ ФБУЗ \"ЦЕНТР ГИГИЕНЫ И ЭПИДЕМИОЛОГИИ\"", op.merchant)
    }

    @Test fun smallSalaryAndRoundSalary() {
        assertEquals(995.72, BankSms.parse("Зарплата +995,72 ₽ МИР Золотая: 126 858,10 ₽")!!.amount, 0.001)
        val op = BankSms.parse("Зарплата +4 784 ₽ МИР Золотая: 131 642,10 ₽")!!
        assertEquals(4784.0, op.amount, 0.001)
        assertEquals(BankSms.Kind.INCOME, op.kind)
    }

    @Test fun incomingTransferPushTakesSenderBeforeAmount() {
        val op = BankSms.parse("Наталья Ивановна С. Входящий перевод +1 150 ₽ МИР Золотая: 139 003,82 ₽")!!
        assertEquals(BankSms.Kind.INCOME, op.kind)
        assertEquals(1150.0, op.amount, 0.001)
        assertEquals("Наталья Ивановна С", op.merchant)
    }

    @Test fun salarySmsFormats() {
        val a = BankSms.parse("MIR-3239 10:05 зачисление зарплаты 56 555.43р Баланс: 137 853.82р")!!
        assertEquals(BankSms.Kind.INCOME, a.kind); assertEquals(56555.43, a.amount, 0.001); assertEquals("Зарплата", a.category)
        val b = BankSms.parse("СЧЁТ3239 10:05 Зачисление заработной платы 4784р Баланс: 1 000р")!!
        assertEquals("Зарплата", b.category); assertEquals(4784.0, b.amount, 0.001)
        assertEquals(BankSms.Kind.INCOME, BankSms.parse("MIR-3239 08:00 Начисление пенсии 21 000р Баланс: 30 000р")!!.kind)
        assertEquals(BankSms.Kind.INCOME, BankSms.parse("СЧЁТ3239 Выплата пособия 9 000р")!!.kind)
    }

    @Test fun codeOnlyAsWord() {
        assertNull(BankSms.parse("Код: 12345. Никому не сообщайте. Покупка 300р"))
        assertNotNull(BankSms.parse("MIR-3239 12:00 Покупка 300р ЗАВОД КОДЕКС Баланс: 1 000р"))
    }

    @Test fun purchasePushWithCardBalance() {
        val op = BankSms.parse("Покупка 318 ₽ PYATEROCHKA МИР Золотая: 10 000 ₽")!!
        assertEquals(BankSms.Kind.EXPENSE, op.kind)
        assertEquals("PYATEROCHKA", op.merchant)
        assertEquals("Продукты", op.category)
    }
}
