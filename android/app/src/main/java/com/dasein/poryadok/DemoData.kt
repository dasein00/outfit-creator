package com.dasein.poryadok

import com.dasein.poryadok.data.BalanceWheel
import com.dasein.poryadok.data.BodyProfile
import com.dasein.poryadok.data.Budget
import com.dasein.poryadok.data.DayLog
import com.dasein.poryadok.data.EventItem
import com.dasein.poryadok.data.FocusSession
import com.dasein.poryadok.data.FoodEntry
import com.dasein.poryadok.data.Goal
import com.dasein.poryadok.data.Habit
import com.dasein.poryadok.data.HabitLog
import com.dasein.poryadok.data.Measurement
import com.dasein.poryadok.data.Milestone
import com.dasein.poryadok.data.MoodEntry
import com.dasein.poryadok.data.Note
import com.dasein.poryadok.data.Project
import com.dasein.poryadok.data.Recurring
import com.dasein.poryadok.data.Reminder
import com.dasein.poryadok.data.SleepEntry
import com.dasein.poryadok.data.Subtask
import com.dasein.poryadok.data.TaskItem
import com.dasein.poryadok.data.TopItem
import com.dasein.poryadok.data.TopList
import com.dasein.poryadok.data.Txn
import com.dasein.poryadok.data.TxnType
import com.dasein.poryadok.data.WeightEntry
import com.dasein.poryadok.data.Workout
import com.dasein.poryadok.logic.Dates
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/** Пример данных, чтобы сразу увидеть, как всё работает. Добавляется только в пустое приложение. */
object DemoData {
    private val lock = Mutex()

    suspend fun fill(): Boolean = lock.withLock { fillLocked() }

    private suspend fun fillLocked(): Boolean {
        val dao = Graph.dao
        Repo.seed()
        if (dao.tasks().first().isNotEmpty() || dao.habitsNow().isNotEmpty()) return false
        val rnd = Random(42)
        val today = Dates.today()
        val now = System.currentTimeMillis()

        val work = dao.upsertProject(Project(name = "Работа", emoji = "💼", color = 2))
        val home = dao.upsertProject(Project(name = "Дом", emoji = "🏠", color = 1, sort = 1))
        val goal = dao.upsertGoal(Goal(title = "Пробежать 10 км", why = "Больше энергии и здоровья", emoji = "🏃", color = 5, deadline = today + 60, createdAt = now))
        dao.upsertGoal(Goal(title = "Накопить на отпуск", emoji = "✈️", color = 3, target = 150000.0, progress = 62000.0, unit = "₽", deadline = today + 120, createdAt = now))
        listOf("Купить кроссовки" to true, "Пробежать 3 км без остановки" to true, "Пробежать 5 км" to false, "Пробежать 10 км" to false)
            .forEachIndexed { i, (t, d) -> dao.upsertMilestone(Milestone(goalId = goal, title = t, done = d, sort = i)) }

        val t1 = dao.upsertTask(TaskItem(title = "Подготовить отчёт за квартал", projectId = work, priority = 3, dueDay = today, dueMin = 15 * 60, remind = true, createdAt = now))
        listOf("Собрать цифры", "Сделать графики", "Отправить руководителю").forEachIndexed { i, s ->
            dao.upsertSubtask(Subtask(taskId = t1, title = s, done = i == 0, sort = i))
        }
        dao.upsertTask(TaskItem(title = "Оплатить интернет", projectId = home, priority = 2, dueDay = today - 1, createdAt = now))
        dao.upsertTask(TaskItem(title = "Позвонить маме", dueDay = today, dueMin = 19 * 60, repeat = "weekly", createdAt = now))
        dao.upsertTask(TaskItem(title = "Пробежка 5 км", goalId = goal, dueDay = today + 1, dueMin = 7 * 60 + 30, priority = 1, createdAt = now))
        dao.upsertTask(TaskItem(title = "Купить продукты", projectId = home, dueDay = today + 2, tags = "магазин", createdAt = now))
        dao.upsertTask(TaskItem(title = "Записаться к стоматологу", priority = 2, dueDay = today + 5, createdAt = now))
        dao.upsertTask(TaskItem(title = "Прочитать книгу про привычки", createdAt = now))
        dao.upsertTask(TaskItem(title = "Разобрать почту", projectId = work, done = true, doneAt = now - 3_600_000, dueDay = today, createdAt = now))

        val habits = listOf(
            Habit(name = "Выпить воду", emoji = "💧", target = 8, unit = "стаканов", color = 2, createdDay = today - 40),
            Habit(name = "Зарядка", emoji = "🤸", color = 1, createdDay = today - 40, remindMin = 8 * 60),
            Habit(name = "Чтение", emoji = "📚", color = 4, createdDay = today - 40),
            Habit(name = "Английский", emoji = "🇬🇧", color = 3, timesPerWeek = 3, createdDay = today - 40),
        ).map { it.copy(id = dao.upsertHabit(it)) }
        for (d in (today - 40) until today) habits.forEach { h ->
            if (rnd.nextFloat() < .75f) dao.upsertHabitLog(HabitLog(h.id, d, if (h.target > 1) h.target - rnd.nextInt(0, 2) else 1))
        }
        dao.upsertHabitLog(HabitLog(habits[0].id, today, 3))
        dao.upsertHabitLog(HabitLog(habits[1].id, today, 1))

        dao.upsertEvent(EventItem(title = "Встреча с командой", day = today, startMin = 11 * 60, endMin = 12 * 60, color = 2, remindBefore = 10, location = "Переговорная"))
        dao.upsertEvent(EventItem(title = "Йога", day = today - 2, startMin = 19 * 60, endMin = 20 * 60, repeat = "weekly", color = 6))
        dao.upsertEvent(EventItem(title = "День рождения Саши", day = today + 4, color = 3))
        dao.upsertReminder(Reminder(title = "Принять витамины", day = today, min = 21 * 60, repeat = "daily"))

        val accounts = dao.accountsNow()
        val card = accounts.firstOrNull()?.id ?: return true
        val cats = dao.categories().first()
        fun cat(name: String) = cats.firstOrNull { it.name == name }?.id
        for (d in (today - 75)..today) {
            if (rnd.nextFloat() < .7f) dao.upsertTxn(Txn(type = TxnType.EXPENSE, amount = (300 + rnd.nextInt(2500)).toDouble(), accountId = card, categoryId = cat("Продукты"), day = d, createdAt = now))
            if (rnd.nextFloat() < .35f) dao.upsertTxn(Txn(type = TxnType.EXPENSE, amount = (250 + rnd.nextInt(900)).toDouble(), accountId = card, categoryId = cat("Кафе и рестораны"), day = d, note = "Кофе", createdAt = now))
            if (rnd.nextFloat() < .25f) dao.upsertTxn(Txn(type = TxnType.EXPENSE, amount = (150 + rnd.nextInt(600)).toDouble(), accountId = card, categoryId = cat("Транспорт"), day = d, note = "Такси", createdAt = now))
            if (Dates.day(d).dayOfMonth == 10) dao.upsertTxn(Txn(type = TxnType.INCOME, amount = 95000.0, accountId = card, categoryId = cat("Зарплата"), day = d, createdAt = now))
            if (Dates.day(d).dayOfMonth == 25) dao.upsertTxn(Txn(type = TxnType.INCOME, amount = 60000.0, accountId = card, categoryId = cat("Зарплата"), day = d, createdAt = now))
        }
        dao.upsertBudget(Budget(0, 70000.0))
        cat("Кафе и рестораны")?.let { dao.upsertBudget(Budget(it, 8000.0)) }
        dao.upsertRecurring(Recurring(title = "Аренда квартиры", amount = 35000.0, accountId = card, categoryId = cat("Жильё и ЖКХ"), nextDay = today + 6))
        dao.upsertRecurring(Recurring(title = "Музыка", amount = 199.0, accountId = card, categoryId = cat("Подписки"), nextDay = today + 12))

        dao.upsertProfile(BodyProfile(male = false, heightCm = 168.0, age = 28, startWeight = 75.0, startDay = Dates.weekStart(today) - 42))
        var w = 75.0
        for (k in 0..6) { dao.upsertWeight(WeightEntry(Dates.weekStart(today) - 42 + k * 7L, w)); w = Math.round((w - .5 - rnd.nextDouble() * .6) * 10) / 10.0 }
        dao.upsertMeasurement(Measurement(today - 42, 96.0, 78.0, 90.0, 104.0, 30.0))
        dao.upsertMeasurement(Measurement(today - 28, 95.0, 76.5, 88.0, 103.0, 29.5))
        dao.upsertMeasurement(Measurement(today - 14, 94.0, 75.0, 86.5, 102.0, 29.5))
        dao.upsertFood(FoodEntry(day = today, meal = 0, name = "Овсянка с ягодами", kcal = 380, protein = 12.0, fat = 8.0, carbs = 62.0))
        dao.upsertFood(FoodEntry(day = today, meal = 1, name = "Курица с рисом", kcal = 560, protein = 45.0, fat = 14.0, carbs = 60.0))
        dao.upsertFood(FoodEntry(day = today, meal = 3, name = "Творог", kcal = 180, protein = 24.0, fat = 5.0, carbs = 8.0))
        dao.upsertDayLog(DayLog(today, waterMl = 1250, steps = 6400))
        listOf(1, 3, 5, 8, 10, 12, 15).forEach { dao.upsertWorkout(Workout(day = today - it, type = if (it % 2 == 0) "🏋️ Силовая" else "🏃 Бег", minutes = 45, kcal = 350, exercises = if (it % 2 == 0) "Присед 4×8 50 кг\nЖим 3×10 30 кг" else "")) }
        for (d in (today - 20)..today) {
            dao.upsertSleep(SleepEntry(d, 23 * 60 + rnd.nextInt(-40, 60).coerceAtMost(59), 7 * 60 + rnd.nextInt(-30, 50), 2 + rnd.nextInt(4)))
            val level = 2 + rnd.nextInt(4)
            dao.upsertMood(MoodEntry(at = Dates.millis(d, 21 * 60), day = d, level = level, tags = if (level >= 4) "🏋️ Спорт,🫂 Друзья" else "💼 Работа,😤 Стресс"))
        }
        for (d in (today - 6)..today) dao.insertFocus(FocusSession(startedAt = Dates.millis(d, 10 * 60), day = d, minutes = 25 * (1 + rnd.nextInt(3)), label = "Подготовить отчёт за квартал"))
        dao.upsertWheel(BalanceWheel(day = today, scores = "7,6,5,8,6,7,4,5"))

        dao.upsertNote(Note(title = "Идеи для отпуска", body = "Грузия в октябре\nКазань на выходные\nАлтай летом", color = 3, pinned = true, updatedAt = now))
        dao.upsertNote(Note(title = "Список покупок", body = "[x] Молоко\n[ ] Хлеб\n[ ] Яблоки\n[ ] Кофе", checklist = true, color = 1, updatedAt = now - 86_400_000))
        dao.upsertNote(Note(title = "Мысль дня", body = "Порядок снаружи помогает порядку внутри.", updatedAt = now - 2 * 86_400_000))
        val films = dao.upsertTopList(TopList(title = "Топ фильмов", emoji = "🎬"))
        listOf("Интерстеллар" to 10, "Амели" to 9, "Брат" to 9, "Начало" to 8).forEachIndexed { i, (t, r) ->
            dao.upsertTopItem(TopItem(listId = films, title = t, rating = r, sort = i))
        }
        dao.upsertTopList(TopList(title = "Места, где хочу побывать", emoji = "✈️", sort = 1))
        return true
    }
}
