package com.dasein.poryadok

import com.dasein.poryadok.data.Account
import com.dasein.poryadok.data.Category
import com.dasein.poryadok.data.Habit
import com.dasein.poryadok.data.HabitLog
import com.dasein.poryadok.data.Reminder
import com.dasein.poryadok.data.TaskItem
import com.dasein.poryadok.data.Txn
import com.dasein.poryadok.data.TxnType
import androidx.room.withTransaction
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.ui.common.Glyphs
import com.dasein.poryadok.logic.Repeat
import com.dasein.poryadok.logic.nextOccurrence
import com.dasein.poryadok.ui.wardrobe.WardrobeSeed
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Операции, которые затрагивают несколько таблиц или содержат правила предметной области. */
object Repo {
    private val dao get() = Graph.dao
    private val seedLock = Mutex()

    suspend fun seed() = seedLock.withLock {
        if (dao.categoryCount() == 0) {
            val out = listOf(
                "Продукты" to "food/24", "Кафе и рестораны" to "fest/29", "Транспорт" to "ui:map", "Жильё и ЖКХ" to "cal/37",
                "Связь и интернет" to "ui:globe", "Здоровье" to "sport/18", "Одежда" to "ui:hanger", "Развлечения" to "habit/08",
                "Подарки" to "fest/01", "Образование" to "fest/15", "Спорт" to "sport/00", "Красота" to "fest/12",
                "Путешествия" to "cal/35", "Подписки" to "sport/34", "Другое" to "ui:grid",
            )
            val inc = listOf("Зарплата" to "ui:coins", "Подработка" to "ui:card", "Подарок" to "fest/01", "Кэшбэк" to "ui:receipt", "Другое" to "ui:wallet")
            out.forEachIndexed { i, (n, e) -> dao.upsertCategory(Category(name = n, emoji = e, color = i, income = false, sort = i)) }
            inc.forEachIndexed { i, (n, e) -> dao.upsertCategory(Category(name = n, emoji = e, color = i + 3, income = true, sort = i)) }
            if (dao.accountsNow().isEmpty()) {
                dao.upsertAccount(Account(name = "Карта", emoji = "ui:card", color = 0, sort = 0))
                dao.upsertAccount(Account(name = "Наличные", emoji = "ui:coins", color = 2, sort = 1))
            }
        }
        WardrobeSeed.run()
    }

    /**
     * Эмодзи прежних версий → фирменные иконки: в списках, целях, привычках, счетах, категориях и топах,
     * а у тренировок и тегов настроения убирается эмодзи из подписи. Выполняется один раз.
     */
    suspend fun migrateGlyphs() {
        if (Graph.prefs.now().glyphsMigrated) return
        val b = Graph.db.backupDao()
        fun g(v: String) = if (Glyphs.isKey(v)) v else Glyphs.normalize(v)
        Graph.db.withTransaction {
            b.putProject(b.allProject().map { it.copy(emoji = g(it.emoji)) })
            b.putGoal(b.allGoal().map { it.copy(emoji = g(it.emoji)) })
            b.putHabit(b.allHabit().map { it.copy(emoji = g(it.emoji)) })
            b.putAccount(b.allAccount().map { it.copy(emoji = g(it.emoji)) })
            b.putCategory(b.allCategory().map { it.copy(emoji = g(it.emoji)) })
            b.putTopList(b.allTopList().map { it.copy(emoji = g(it.emoji)) })
            b.putWorkout(b.allWorkout().map { it.copy(type = Glyphs.stripEmoji(it.type)) })
            b.putMoodEntry(b.allMoodEntry().map { m ->
                m.copy(tags = m.tags.split(",").map { Glyphs.stripEmoji(it) }.filter { it.isNotBlank() }.joinToString(","))
            })
        }
        Graph.prefs.update { it.copy(glyphsMigrated = true) }
    }

    /** Отметить задачу. Повторяющаяся задача переносится на следующую дату, а не закрывается. */
    suspend fun toggleTask(t: TaskItem) {
        if (t.done) {
            dao.upsertTask(t.copy(done = false, doneAt = null))
            return
        }
        val repeat = Repeat.of(t.repeat)
        val due = t.dueDay
        if (repeat != Repeat.NONE && due != null) {
            var next = nextOccurrence(due, repeat)!!
            val today = Dates.today()
            while (next < today) next = nextOccurrence(next, repeat, due)!!
            dao.upsertTask(t.copy(dueDay = next))
            dao.subtasksOfNow(t.id).forEach { dao.upsertSubtask(it.copy(done = false)) }
        } else {
            dao.upsertTask(t.copy(done = true, doneAt = System.currentTimeMillis()))
        }
    }

    suspend fun deleteTask(t: TaskItem) {
        dao.deleteSubtasksOf(t.id)
        dao.deleteTask(t)
    }

    /** Привычка-галочка переключается; счётная привычка прибавляет единицу, после цели — сбрасывается. */
    suspend fun tapHabit(h: Habit, day: Long, current: Int) {
        when {
            h.target <= 1 -> if (current >= 1) dao.deleteHabitLog(h.id, day) else dao.upsertHabitLog(HabitLog(h.id, day, 1))
            current >= h.target -> dao.deleteHabitLog(h.id, day)
            else -> dao.upsertHabitLog(HabitLog(h.id, day, current + 1))
        }
    }

    suspend fun setHabitValue(h: Habit, day: Long, value: Int) {
        if (value <= 0) dao.deleteHabitLog(h.id, day) else dao.upsertHabitLog(HabitLog(h.id, day, value))
    }

    suspend fun deleteHabit(h: Habit) {
        dao.deleteHabitLogsOf(h.id)
        dao.deleteHabit(h)
    }

    /** Добавляет операции по регулярным платежам, срок которых наступил. */
    suspend fun processRecurring() {
        val today = Dates.today()
        for (r in dao.activeRecurring()) {
            var next = r.nextDay
            var guard = 0
            val repeat = Repeat.of(r.repeat)
            while (next <= today && guard++ < 60) {
                if (r.autoAdd) {
                    dao.upsertTxn(
                        Txn(
                            type = if (r.income) TxnType.INCOME else TxnType.EXPENSE,
                            amount = r.amount, accountId = r.accountId, categoryId = r.categoryId,
                            day = next, note = r.title, createdAt = System.currentTimeMillis(),
                        )
                    )
                }
                next = nextOccurrence(next, repeat, r.nextDay) ?: break
            }
            if (next != r.nextDay) dao.upsertRecurring(r.copy(nextDay = next))
        }
    }

    /** Сработавшая повторяющаяся напоминалка сдвигается на следующую дату. */
    suspend fun advanceReminder(r: Reminder) {
        val repeat = Repeat.of(r.repeat)
        if (repeat == Repeat.NONE) return
        val now = System.currentTimeMillis()
        var d = r.day
        var guard = 0
        do {
            d = nextOccurrence(d, repeat, r.day) ?: return
        } while (Dates.millis(d, r.min) <= now && guard++ < 400)
        dao.upsertReminder(r.copy(day = d))
    }
}
