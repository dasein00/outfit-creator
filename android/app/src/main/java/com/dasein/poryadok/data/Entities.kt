package com.dasein.poryadok.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/* ---------- задачи, проекты, цели ---------- */

@Serializable
@Entity(tableName = "projects")
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "📁",
    val color: Int = 0,
    val sort: Int = 0,
    val archived: Boolean = false,
)

@Serializable
@Entity(tableName = "tasks", indices = [Index("projectId"), Index("dueDay"), Index("goalId")])
data class TaskItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String = "",
    val projectId: Long? = null,
    val goalId: Long? = null,
    val priority: Int = 0,
    val dueDay: Long? = null,
    val dueMin: Int? = null,
    val remind: Boolean = false,
    val repeat: String = "none",
    val tags: String = "",
    val done: Boolean = false,
    val doneAt: Long? = null,
    val createdAt: Long = 0,
    val sort: Int = 0,
)

@Serializable
@Entity(tableName = "subtasks", indices = [Index("taskId")])
data class Subtask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val title: String,
    val done: Boolean = false,
    val sort: Int = 0,
)

@Serializable
@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val why: String = "",
    val emoji: String = "🎯",
    val color: Int = 0,
    val deadline: Long? = null,
    val target: Double? = null,
    val progress: Double = 0.0,
    val unit: String = "",
    val done: Boolean = false,
    val createdAt: Long = 0,
)

@Serializable
@Entity(tableName = "milestones", indices = [Index("goalId")])
data class Milestone(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val title: String,
    val done: Boolean = false,
    val sort: Int = 0,
)

@Serializable
@Entity(tableName = "focus_sessions")
data class FocusSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val day: Long,
    val minutes: Int,
    val taskId: Long? = null,
    val label: String = "",
)

/* ---------- привычки ---------- */

@Serializable
@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "✅",
    val color: Int = 0,
    val target: Int = 1,
    val unit: String = "",
    val daysMask: Int = 0b1111111,
    val timesPerWeek: Int = 0,
    val remindMin: Int? = null,
    val createdDay: Long = 0,
    val archived: Boolean = false,
    val sort: Int = 0,
)

@Serializable
@Entity(tableName = "habit_logs", primaryKeys = ["habitId", "day"], indices = [Index("day")])
data class HabitLog(
    val habitId: Long,
    val day: Long,
    val value: Int,
)

/* ---------- календарь ---------- */

@Serializable
@Entity(tableName = "events", indices = [Index("day")])
data class EventItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String = "",
    val location: String = "",
    val day: Long,
    val startMin: Int? = null,
    val endMin: Int? = null,
    val color: Int = 0,
    val repeat: String = "none",
    val remindBefore: Int? = null,
)

@Serializable
@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val day: Long,
    val min: Int,
    val repeat: String = "none",
    val done: Boolean = false,
)

/* ---------- финансы ---------- */

@Serializable
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "💳",
    val initial: Double = 0.0,
    val color: Int = 0,
    val sort: Int = 0,
    val archived: Boolean = false,
)

@Serializable
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val color: Int = 0,
    val income: Boolean = false,
    val sort: Int = 0,
)

object TxnType {
    const val EXPENSE = 0
    const val INCOME = 1
    const val TRANSFER = 2
}

@Serializable
@Entity(tableName = "txns", indices = [Index("day"), Index("accountId"), Index("categoryId")])
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: Int,
    val amount: Double,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val day: Long,
    val note: String = "",
    val createdAt: Long = 0,
)

/** Месячный лимит по категории; categoryId = 0 — общий бюджет на все расходы. */
@Serializable
@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey val categoryId: Long,
    val monthly: Double,
)

@Serializable
@Entity(tableName = "recurring")
data class Recurring(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amount: Double,
    val income: Boolean = false,
    val accountId: Long,
    val categoryId: Long? = null,
    val repeat: String = "monthly",
    val nextDay: Long,
    val autoAdd: Boolean = true,
    val remind: Boolean = true,
    val active: Boolean = true,
)

/* ---------- здоровье и тело ---------- */

@Serializable
@Entity(tableName = "body_profile")
data class BodyProfile(
    @PrimaryKey val id: Int = 1,
    val male: Boolean = false,
    val heightCm: Double = 168.0,
    val age: Int = 28,
    val startWeight: Double = 70.0,
    val activity: Double = 1.375,
    val goal: String = "DEFICIT",
    val intensity: String = "STANDARD",
    val changeKg: Double = 5.0,
    val proteinPerKg: Double = 1.8,
    val fatPerKg: Double = 0.9,
    val startDay: Long = 0,
    val waterGoalMl: Int = 2000,
    val sleepGoalMin: Int = 480,
    val stepsGoal: Int = 8000,
)

@Serializable
@Entity(tableName = "weights")
data class WeightEntry(
    @PrimaryKey val day: Long,
    val kg: Double,
)

@Serializable
@Entity(tableName = "measurements")
data class Measurement(
    @PrimaryKey val day: Long,
    val chest: Double? = null,
    val waist: Double? = null,
    val belly: Double? = null,
    val hips: Double? = null,
    val arm: Double? = null,
)

@Serializable
@Entity(tableName = "food", indices = [Index("day")])
data class FoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val meal: Int = 0,
    val name: String,
    val kcal: Int = 0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0,
)

/** Дневные счётчики: вода, шаги, заметка об активности. */
@Serializable
@Entity(tableName = "day_logs")
data class DayLog(
    @PrimaryKey val day: Long,
    val waterMl: Int = 0,
    val steps: Int = 0,
    val activity: String = "",
)

@Serializable
@Entity(tableName = "workouts", indices = [Index("day")])
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val type: String,
    val minutes: Int = 0,
    val kcal: Int = 0,
    val exercises: String = "",
    val note: String = "",
)

@Serializable
@Entity(tableName = "sleep")
data class SleepEntry(
    @PrimaryKey val day: Long,
    val bedMin: Int,
    val wakeMin: Int,
    val quality: Int = 3,
    val note: String = "",
)

@Serializable
@Entity(tableName = "moods", indices = [Index("day")])
data class MoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val day: Long,
    val level: Int,
    val tags: String = "",
    val note: String = "",
)

@Serializable
@Entity(tableName = "progress_photos")
data class ProgressPhoto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val path: String,
    val pose: String = "",
)

@Serializable
@Entity(tableName = "balance_wheel")
data class BalanceWheel(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val scores: String,
)

/* ---------- заметки и топы ---------- */

@Serializable
@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val body: String = "",
    val color: Int = 0,
    val pinned: Boolean = false,
    val checklist: Boolean = false,
    val archived: Boolean = false,
    val updatedAt: Long = 0,
)

@Serializable
@Entity(tableName = "top_lists")
data class TopList(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val emoji: String = "🏆",
    val sort: Int = 0,
)

@Serializable
@Entity(tableName = "top_items", indices = [Index("listId")])
data class TopItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val title: String,
    val note: String = "",
    val rating: Int = 0,
    val sort: Int = 0,
)

/* ---------- гардероб ---------- */

@Serializable
@Entity(tableName = "wardrobe_items")
data class WardrobeItem(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val season: String,
    /** "asset:wardrobe/x.webp" для встроенных или абсолютный путь к файлу. */
    val src: String,
    /** [ширина % от манекена, центр X %, верх Y %, (высота % роста)] */
    val pct: String,
    val builtin: Boolean = false,
    val sketch: Boolean = false,
    val hidden: Boolean = false,
)

/** Сохранённая подгонка вещи: четырёхугольник в мировых координатах и мазки ластика. id "__model__" — манекен. */
@Serializable
@Entity(tableName = "item_fits")
data class ItemFit(
    @PrimaryKey val itemId: String,
    val quad: String,
    val strokes: String = "[]",
)

@Serializable
@Entity(tableName = "outfits")
data class Outfit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val season: String,
    val createdAt: Long,
    val preview: String = "",
    val layers: String,
    val wornDays: String = "",
)
