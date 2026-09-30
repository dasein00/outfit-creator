package com.dasein.poryadok.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class BankSmsKindTest {
    @Test fun incomingTransferWithAmountBeforeSender() {
        val op = BankSms.parse("MIR-3239 09:14 Перевод 1000р от Екатерина Щ. Баланс: 5 230р")!!
        assertEquals(BankSms.Kind.INCOME, op.kind)
        assertEquals(1000.0, op.amount, 0.0)
        assertEquals("от Екатерина Щ", op.merchant)
    }

    @Test fun incomingSbpTransfer() {
        val op = BankSms.parse("СЧЁТ3239 12:01 Перевод из Т-Банк +2 500р от ИВАН И. Баланс: 10 000р")!!
        assertEquals(BankSms.Kind.INCOME, op.kind)
        assertEquals(2500.0, op.amount, 0.0)
    }

    @Test fun pushWithPlusSign() {
        assertEquals(BankSms.Kind.INCOME, BankSms.parse("Екатерина Викторовна Щ. Входящий перевод +1 000 ₽")!!.kind)
        assertEquals(BankSms.Kind.INCOME, BankSms.parse("Перевод +1 000 ₽ от Екатерины Щ.")!!.kind)
    }

    @Test fun outgoingTransfersStayExpenses() {
        assertEquals(BankSms.Kind.EXPENSE, BankSms.parse("MIR-3239 10:00 Перевод 773,38р через СБП в Яндекс на запрос от Яндекс Баланс: 900р")!!.kind)
        assertEquals(BankSms.Kind.EXPENSE, BankSms.parse("MIR-3239 10:00 Перевод 2 392р Бениамин К. Баланс: 900р")!!.kind)
        assertEquals(BankSms.Kind.EXPENSE, BankSms.parse("Перевод отправлен: 360 ₽ Наталья Ивановна С.")!!.kind)
        assertEquals(BankSms.Kind.EXPENSE, BankSms.parse("Покупка −318 ₽ PYATEROCHKA")!!.kind)
    }

    @Test fun cardNumberDashIsNotASign() {
        val op = BankSms.parse("MIR-3239 14:05 Покупка 350р PYATEROCHKA Баланс: 12 000р")!!
        assertEquals(BankSms.Kind.EXPENSE, op.kind)
        assertEquals(350.0, op.amount, 0.0)
    }

    @Test fun dashMerchantIsCleaned() {
        assertEquals("", BankSms.parse("Оплата 318 ₽ —")!!.merchant)
    }

    @Test fun oldNotesAreRecognisedAsIncome() {
        assertTrue(BankSms.noteLooksIncoming("Сбер: от Екатерина Щ · MIR-3239"))
        assertFalse(BankSms.noteLooksIncoming("Сбер: через СБП в Яндекс на запрос от"))
        assertFalse(BankSms.noteLooksIncoming("Сбер: Пятёрочка · MIR-3239"))
    }
}

class SmallTalkTest {
    private val facts = (1..10).map { Fact(it, if (it <= 5) "Кино" else "Наука", "Факт $it") }

    @Test fun deckWalksOnlySelectedSpheresAndKeepsHistory() {
        val rnd = Random(3)
        val kino = SmallTalk.filter(facts, setOf("Кино"))
        var d = SmallTalk.ensure(kino, SmallTalk.Deck(), rnd)
        val seen = mutableSetOf(SmallTalk.current(kino, d)!!.id)
        repeat(4) { d = SmallTalk.next(kino, d, rnd); seen += SmallTalk.current(kino, d)!!.id }
        assertEquals(setOf(1, 2, 3, 4, 5), seen)
        assertEquals(5, SmallTalk.read(kino, d))
        // Переключились на все сферы: история Кино не пропала, новые факты — только непрочитанные.
        var all = SmallTalk.next(facts, d, rnd)
        assertTrue(SmallTalk.current(facts, all)!!.id > 5)
        all = SmallTalk.prev(facts, all, rnd)
        assertTrue(SmallTalk.current(facts, all)!!.id <= 5)
    }

    @Test fun newRoundAfterAllRead() {
        val rnd = Random(1)
        val kino = SmallTalk.filter(facts, setOf("Кино"))
        var d = SmallTalk.ensure(kino, SmallTalk.Deck(), rnd)
        repeat(4) { d = SmallTalk.next(kino, d, rnd) }
        d = SmallTalk.next(kino, d, rnd)
        assertEquals(1, SmallTalk.read(kino, d))
        assertNotNull(SmallTalk.current(kino, d))
    }

    @Test fun favoritesToggle() {
        val a = SmallTalk.toggle(emptyList(), 5)
        assertEquals(listOf(5), a)
        assertEquals(emptyList<Int>(), SmallTalk.toggle(a, 5))
    }

    @Test fun trafficLightAndDailyPick() {
        assertEquals(0, SmallTalk.light(0))
        assertEquals(0, SmallTalk.light(19))
        assertEquals(1, SmallTalk.light(20))
        assertEquals(2, SmallTalk.light(40))
        val list = listOf("a", "b", "c")
        assertEquals("b", SmallTalk.ofDay(list, 4))
        assertEquals("c", SmallTalk.ofDay(list, -1))
    }

    @Test fun storiesRoundTrip() {
        val list = listOf(SmallTalk.MyStory("Паспорт", "Ехал в отпуск", "Забыл паспорт", "Успел"), SmallTalk.MyStory("Кот", "a", "b", "c"))
        assertEquals(list, SmallTalk.decodeStories(SmallTalk.encodeStories(list)))
        assertEquals(emptyList<SmallTalk.MyStory>(), SmallTalk.decodeStories(""))
    }

    @Test fun healthTagsBecomeOneSphere() {
        assertEquals("Здоровье", SmallTalk.sphereOf("Сон"))
        assertEquals("Кино", SmallTalk.sphereOf("Кино"))
    }

    @Test fun assetBaseIsWellFormed() {
        val dir = listOf("src/main/assets/smalltalk", "app/src/main/assets/smalltalk").map(::File).first { it.isDirectory }
        val facts = SmallTalk.parseFacts(dir.listFiles()!!.filter { it.name.startsWith("facts") }.joinToString("\n") { it.readText() })
        assertTrue("фактов мало: ${facts.size}", facts.size >= 500)
        val spheres = SmallTalk.SPHERES.map { it.first }.toSet()
        facts.forEach { assertTrue("неизвестная сфера ${it.tag}", it.tag in spheres) }
        SmallTalk.SPHERES.filter { it.first != "Здоровье" }.forEach { (s, _) -> assertTrue("пустая сфера $s", facts.count { it.tag == s } >= 20) }
        val stories = SmallTalk.parseStories(File(dir, "stories.txt").readText())
        assertTrue(stories.size >= 40)
        stories.forEach { assertTrue("неизвестная сфера ${it.sphere}", it.sphere in spheres) }
        assertTrue(SmallTalk.parseQuestions(File(dir, "questions.txt").readText()).size >= 80)
        val guide = SmallTalk.parseGuide(File(dir, "guide.txt").readText())
        assertTrue("приёмов мало: ${guide.size}", guide.size >= 60)
        guide.forEach { assertTrue("нет источника у ${it.title}", it.source.isNotBlank()) }
        assertTrue(SmallTalk.parseQuestions(File(dir, "phrases.txt").readText()).size >= 60)
        val topics = SmallTalk.parseTopics(File(dir, "topics.txt").readText())
        assertTrue(topics.size >= 30)
        topics.forEach { assertTrue("мало вопросов у ${it.name}", it.questions.size >= 3) }
        assertEquals((1..12).toSet(), SmallTalk.parseMonths(File(dir, "months.txt").readText()).keys)
        assertTrue(SmallTalk.parseLines(File(dir, "opinions.txt").readText()).size >= 40)
        assertTrue(SmallTalk.parseExercises(File(dir, "exercises.txt").readText()).size >= 14)
    }
}
