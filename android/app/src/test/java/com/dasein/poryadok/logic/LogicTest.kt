package com.dasein.poryadok.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class QuickAddTest {
    private val today = LocalDate.of(2026, 9, 24).toEpochDay() // четверг

    @Test fun fullSentence() {
        val p = QuickAdd.parse("Позвонить маме завтра в 19:30 !2 #семья", today)
        assertEquals("Позвонить маме", p.title)
        assertEquals(today + 1, p.dueDay)
        assertEquals(19 * 60 + 30, p.dueMin)
        assertEquals(2, p.priority)
        assertEquals("семья", p.project)
    }

    @Test fun eveningHour() {
        val p = QuickAdd.parse("Спортзал в 7 вечера", today)
        assertEquals("Спортзал", p.title)
        assertEquals(19 * 60, p.dueMin)
        assertEquals(today, p.dueDay)
    }

    @Test fun weekdayAndRepeat() {
        val p = QuickAdd.parse("Отчёт в понедельник каждую неделю", today)
        assertEquals("Отчёт", p.title)
        assertEquals(LocalDate.of(2026, 9, 28).toEpochDay(), p.dueDay)
        assertEquals(Repeat.WEEKLY, p.repeat)
    }

    @Test fun dayMonth() {
        assertEquals(LocalDate.of(2026, 10, 5).toEpochDay(), QuickAdd.parse("ДР Саши 5 октября", today).dueDay)
        assertEquals(LocalDate.of(2027, 1, 3).toEpochDay(), QuickAdd.parse("Билеты 03.01", today).dueDay)
        assertEquals("Билеты", QuickAdd.parse("Билеты 03.01", today).title)
    }

    @Test fun inDaysAndBangs() {
        val p = QuickAdd.parse("Оплатить интернет через 3 дня !!! @дом", today)
        assertEquals("Оплатить интернет", p.title)
        assertEquals(today + 3, p.dueDay)
        assertEquals(3, p.priority)
        assertEquals(listOf("дом"), p.tags)
    }

    @Test fun plainText() {
        val p = QuickAdd.parse("Просто заметка про книгу", today)
        assertEquals("Просто заметка про книгу", p.title)
        assertNull(p.dueDay)
        assertEquals(0, p.priority)
    }
}

class RecurrenceTest {
    @Test fun monthlyKeepsAnchorDay() {
        val jan31 = LocalDate.of(2026, 1, 31).toEpochDay()
        val feb = nextOccurrence(jan31, Repeat.MONTHLY, jan31)!!
        assertEquals(LocalDate.of(2026, 2, 28).toEpochDay(), feb)
        assertEquals(LocalDate.of(2026, 3, 31).toEpochDay(), nextOccurrence(feb, Repeat.MONTHLY, jan31))
    }

    @Test fun weekdaysSkipWeekend() {
        val fri = LocalDate.of(2026, 9, 25).toEpochDay()
        assertEquals(LocalDate.of(2026, 9, 28).toEpochDay(), nextOccurrence(fri, Repeat.WEEKDAYS))
    }

    @Test fun occurs() {
        val start = LocalDate.of(2026, 9, 1).toEpochDay()
        assertTrue(occursOn(start, Repeat.WEEKLY, start + 14))
        assertFalse(occursOn(start, Repeat.WEEKLY, start + 3))
        assertTrue(occursOn(start, Repeat.MONTHLY, LocalDate.of(2026, 11, 1).toEpochDay()))
        assertFalse(occursOn(start, Repeat.DAILY, start - 1))
        assertEquals(start + 7, upcomingOccurrence(start, Repeat.WEEKLY, start + 5))
    }
}

class HabitStatsTest {
    private val today = LocalDate.of(2026, 9, 24).toEpochDay()

    @Test fun dailyStreakIgnoresPendingToday() {
        val done = setOf(today - 1, today - 2, today - 3, today - 5)
        val s = HabitStats.summary(HabitSchedule(), done, today, today - 10)
        assertEquals(3, s.streak)
        assertEquals(3, s.best)
    }

    @Test fun unscheduledDaysDontBreak() {
        // только по будням; 24.09.2026 — чт
        val sched = HabitSchedule(HabitSchedule.WEEKDAYS)
        val mon = LocalDate.of(2026, 9, 21).toEpochDay()
        val prevFri = LocalDate.of(2026, 9, 18).toEpochDay()
        val done = setOf(prevFri, mon, mon + 1, mon + 2, today)
        assertEquals(5, HabitStats.summary(sched, done, today, prevFri).streak)
    }

    @Test fun weeklyTarget() {
        val ws = Dates.weekStart(today)
        val done = setOf(ws, ws + 2, ws - 7, ws - 5, ws - 3, ws - 14)
        val s = HabitStats.summary(HabitSchedule(timesPerWeek = 2), done, today, ws - 21)
        assertEquals(2, s.streak)
        assertEquals("нед.", s.streakUnit)
    }
}

class NutritionTest {
    @Test fun matchesSpreadsheet() {
        // значения из «Дневника прогресса»: Ж, 168 см, 28 лет, 75 кг, 1.375, дефицит стандартно
        val p = Nutrition.plan(
            BodyInput(false, 168.0, 28, 75.0, 1.375, CalorieGoal.DEFICIT, Intensity.STANDARD, 1.8, 0.9)
        )
        assertEquals(1499.0, p.bmr, 0.01)
        assertEquals(2061.125, p.tdee, 0.01)
        assertEquals(1649, p.targetKcal)
        assertEquals(135, p.proteinG)
        assertEquals(68, p.fatG)
        assertEquals(124, p.carbsG)
    }

    @Test fun levels() {
        assertEquals(Nutrition.Level.OK, Nutrition.level(100))
        assertEquals(Nutrition.Level.WARN, Nutrition.level(80))
        assertEquals(Nutrition.Level.BAD, Nutrition.level(150))
    }

    @Test fun plural() {
        assertEquals("задача", plural(21, "задача", "задачи", "задач"))
        assertEquals("задачи", plural(3, "задача", "задачи", "задач"))
        assertEquals("задач", plural(12, "задача", "задачи", "задач"))
    }
}

class MoneyTest {
    @Test fun budget() {
        assertEquals(Money.BudgetLevel.NEAR, Money.budgetLevel(850.0, 1000.0))
        assertEquals(Money.BudgetLevel.OVER, Money.budgetLevel(1001.0, 1000.0))
        assertEquals(100.0, Money.dailyAllowance(1000.0, 500.0, 5), 0.001)
        assertEquals(1234.5, Money.parse("1 234,5")!!, 0.001)
    }
}
