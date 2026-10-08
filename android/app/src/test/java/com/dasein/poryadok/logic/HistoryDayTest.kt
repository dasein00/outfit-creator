package com.dasein.poryadok.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryDayTest {
    @Test fun contextIsAboutTheEventNotTheCity() {
        val wiki = """{"events":[{"text":"Вооружённые японцы ворвались в сеульский дворец Кёнбоккун и умертвили в спальне королеву Мин.","year":1895,"pages":[
            {"title":"Сеул","description":"столица Республики Корея","extract":"Сеул — столица и крупнейший город Республики Корея."},
            {"title":"Кёнбоккун","description":"дворец в Сеуле","extract":"Кёнбоккун — главный королевский дворец династии Чосон."},
            {"title":"Убийство_королевы_Мин","titles":{"normalized":"Убийство королевы Мин"},"description":"политическое убийство в Корее","extract":"Убийство королевы Мин — убийство корейской королевы японскими агентами 8 октября 1895 года."}]},
            {"text":"Прояпонский переворот в Сеуле, убийство королевы Мин.","year":1895,"pages":[{"title":"Сеул","description":"столица Республики Корея","extract":"Сеул — столица."}]}]}"""
        val ev = HistoryDay.merge(emptyList(), HistoryDay.parseWiki(wiki))
        assertEquals(1, ev.size) // одно и то же событие — одна карточка
        assertTrue(ev[0].context.startsWith("Убийство королевы Мин"))
    }

    @Test fun differentEventsSameYearStay() {
        val a = HistoryDay.Event(1941, "", "Германия напала на Советский Союз", "")
        val b = HistoryDay.Event(1941, "", "Япония атаковала Пёрл-Харбор", "")
        assertFalse(HistoryDay.similar(a, b))
    }
}
