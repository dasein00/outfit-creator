package com.dasein.poryadok.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LifeDao {
    /* ---- проекты и задачи ---- */
    @Query("SELECT * FROM projects WHERE archived = 0 ORDER BY sort, id")
    fun projects(): Flow<List<Project>>
    @Upsert suspend fun upsertProject(p: Project): Long
    @Delete suspend fun deleteProject(p: Project)
    @Query("UPDATE tasks SET projectId = NULL WHERE projectId = :projectId")
    suspend fun detachProject(projectId: Long)

    @Query("SELECT * FROM tasks ORDER BY done, CASE WHEN dueDay IS NULL THEN 1 ELSE 0 END, dueDay, dueMin, priority DESC, sort, id")
    fun tasks(): Flow<List<TaskItem>>
    @Query("SELECT * FROM tasks WHERE id = :id")
    fun task(id: Long): Flow<TaskItem?>
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun taskNow(id: Long): TaskItem?
    @Query("SELECT * FROM tasks WHERE done = 0 AND remind = 1 AND dueDay IS NOT NULL")
    suspend fun tasksWithReminders(): List<TaskItem>
    @Query("SELECT * FROM tasks WHERE done = 0 AND dueDay IS NOT NULL AND dueDay <= :day")
    suspend fun openTasksUntil(day: Long): List<TaskItem>
    @Upsert suspend fun upsertTask(t: TaskItem): Long
    @Delete suspend fun deleteTask(t: TaskItem)
    @Query("DELETE FROM tasks WHERE done = 1")
    suspend fun deleteDoneTasks()

    @Query("SELECT * FROM subtasks ORDER BY sort, id")
    fun subtasks(): Flow<List<Subtask>>
    @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY sort, id")
    fun subtasksOf(taskId: Long): Flow<List<Subtask>>
    @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY sort, id")
    suspend fun subtasksOfNow(taskId: Long): List<Subtask>
    @Upsert suspend fun upsertSubtask(s: Subtask): Long
    @Delete suspend fun deleteSubtask(s: Subtask)
    @Query("DELETE FROM subtasks WHERE taskId = :taskId")
    suspend fun deleteSubtasksOf(taskId: Long)

    @Query("SELECT * FROM goals ORDER BY done, CASE WHEN deadline IS NULL THEN 1 ELSE 0 END, deadline, id")
    fun goals(): Flow<List<Goal>>
    @Query("SELECT * FROM goals WHERE id = :id")
    fun goal(id: Long): Flow<Goal?>
    @Upsert suspend fun upsertGoal(g: Goal): Long
    @Delete suspend fun deleteGoal(g: Goal)
    @Query("UPDATE tasks SET goalId = NULL WHERE goalId = :goalId")
    suspend fun detachGoal(goalId: Long)

    @Query("SELECT * FROM milestones ORDER BY sort, id")
    fun milestones(): Flow<List<Milestone>>
    @Upsert suspend fun upsertMilestone(m: Milestone): Long
    @Delete suspend fun deleteMilestone(m: Milestone)
    @Query("DELETE FROM milestones WHERE goalId = :goalId")
    suspend fun deleteMilestonesOf(goalId: Long)

    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC")
    fun focusSessions(): Flow<List<FocusSession>>
    @Insert suspend fun insertFocus(f: FocusSession): Long
    @Delete suspend fun deleteFocus(f: FocusSession)

    /* ---- привычки ---- */
    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY sort, id")
    fun habits(): Flow<List<Habit>>
    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY sort, id")
    suspend fun habitsNow(): List<Habit>
    @Query("SELECT * FROM habits WHERE id = :id")
    fun habit(id: Long): Flow<Habit?>
    @Upsert suspend fun upsertHabit(h: Habit): Long
    @Delete suspend fun deleteHabit(h: Habit)
    @Query("SELECT * FROM habit_logs")
    fun habitLogs(): Flow<List<HabitLog>>
    @Query("SELECT * FROM habit_logs WHERE day = :day")
    suspend fun habitLogsOn(day: Long): List<HabitLog>
    @Upsert suspend fun upsertHabitLog(l: HabitLog)
    @Query("DELETE FROM habit_logs WHERE habitId = :habitId AND day = :day")
    suspend fun deleteHabitLog(habitId: Long, day: Long)
    @Query("DELETE FROM habit_logs WHERE habitId = :habitId")
    suspend fun deleteHabitLogsOf(habitId: Long)

    /* ---- календарь ---- */
    @Query("SELECT * FROM events ORDER BY day, startMin")
    fun events(): Flow<List<EventItem>>
    @Query("SELECT * FROM events")
    suspend fun eventsNow(): List<EventItem>
    @Upsert suspend fun upsertEvent(e: EventItem): Long
    @Delete suspend fun deleteEvent(e: EventItem)

    @Query("SELECT * FROM reminders ORDER BY done, day, min")
    fun reminders(): Flow<List<Reminder>>
    @Query("SELECT * FROM reminders WHERE done = 0")
    suspend fun openReminders(): List<Reminder>
    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun reminderNow(id: Long): Reminder?
    @Upsert suspend fun upsertReminder(r: Reminder): Long
    @Delete suspend fun deleteReminder(r: Reminder)

    /* ---- финансы ---- */
    @Query("SELECT * FROM accounts WHERE archived = 0 ORDER BY sort, id")
    fun accounts(): Flow<List<Account>>
    @Query("SELECT * FROM accounts ORDER BY sort, id")
    suspend fun accountsNow(): List<Account>
    @Upsert suspend fun upsertAccount(a: Account): Long
    @Delete suspend fun deleteAccount(a: Account)

    @Query("SELECT * FROM categories ORDER BY income, sort, id")
    fun categories(): Flow<List<Category>>
    @Query("SELECT COUNT(*) FROM categories")
    suspend fun categoryCount(): Int
    @Upsert suspend fun upsertCategory(c: Category): Long
    @Delete suspend fun deleteCategory(c: Category)
    @Query("UPDATE txns SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun detachCategory(categoryId: Long)

    @Query("SELECT * FROM txns ORDER BY day DESC, createdAt DESC")
    fun txns(): Flow<List<Txn>>
    @Upsert suspend fun upsertTxn(t: Txn): Long
    @Delete suspend fun deleteTxn(t: Txn)
    @Query("DELETE FROM txns WHERE accountId = :accountId OR toAccountId = :accountId")
    suspend fun deleteTxnsOfAccount(accountId: Long)

    @Query("SELECT * FROM budgets")
    fun budgets(): Flow<List<Budget>>
    @Upsert suspend fun upsertBudget(b: Budget)
    @Query("DELETE FROM budgets WHERE categoryId = :categoryId")
    suspend fun deleteBudget(categoryId: Long)

    @Query("SELECT * FROM recurring ORDER BY nextDay")
    fun recurring(): Flow<List<Recurring>>
    @Query("SELECT * FROM recurring WHERE active = 1")
    suspend fun activeRecurring(): List<Recurring>
    @Upsert suspend fun upsertRecurring(r: Recurring): Long
    @Delete suspend fun deleteRecurring(r: Recurring)

    /* ---- здоровье ---- */
    @Query("SELECT * FROM body_profile WHERE id = 1")
    fun profile(): Flow<BodyProfile?>
    @Query("SELECT * FROM body_profile WHERE id = 1")
    suspend fun profileNow(): BodyProfile?
    @Upsert suspend fun upsertProfile(p: BodyProfile)

    @Query("SELECT * FROM weights ORDER BY day")
    fun weights(): Flow<List<WeightEntry>>
    @Upsert suspend fun upsertWeight(w: WeightEntry)
    @Delete suspend fun deleteWeight(w: WeightEntry)

    @Query("SELECT * FROM measurements ORDER BY day")
    fun measurements(): Flow<List<Measurement>>
    @Upsert suspend fun upsertMeasurement(m: Measurement)
    @Delete suspend fun deleteMeasurement(m: Measurement)

    @Query("SELECT * FROM food ORDER BY day DESC, meal, id")
    fun food(): Flow<List<FoodEntry>>
    @Upsert suspend fun upsertFood(f: FoodEntry): Long
    @Delete suspend fun deleteFood(f: FoodEntry)

    @Query("SELECT * FROM day_logs ORDER BY day")
    fun dayLogs(): Flow<List<DayLog>>
    @Query("SELECT * FROM day_logs WHERE day = :day")
    suspend fun dayLogNow(day: Long): DayLog?
    @Upsert suspend fun upsertDayLog(d: DayLog)

    @Query("SELECT * FROM workouts ORDER BY day DESC, id DESC")
    fun workouts(): Flow<List<Workout>>
    @Upsert suspend fun upsertWorkout(w: Workout): Long
    @Delete suspend fun deleteWorkout(w: Workout)

    @Query("SELECT * FROM sleep ORDER BY day")
    fun sleep(): Flow<List<SleepEntry>>
    @Upsert suspend fun upsertSleep(s: SleepEntry)
    @Delete suspend fun deleteSleep(s: SleepEntry)

    @Query("SELECT * FROM moods ORDER BY at DESC")
    fun moods(): Flow<List<MoodEntry>>
    @Upsert suspend fun upsertMood(m: MoodEntry): Long
    @Delete suspend fun deleteMood(m: MoodEntry)

    @Query("SELECT * FROM progress_photos ORDER BY day DESC")
    fun photos(): Flow<List<ProgressPhoto>>
    @Insert suspend fun insertPhoto(p: ProgressPhoto): Long
    @Delete suspend fun deletePhoto(p: ProgressPhoto)

    @Query("SELECT * FROM balance_wheel ORDER BY day DESC, id DESC")
    fun wheels(): Flow<List<BalanceWheel>>
    @Upsert suspend fun upsertWheel(w: BalanceWheel): Long
    @Delete suspend fun deleteWheel(w: BalanceWheel)

    /* ---- заметки и топы ---- */
    @Query("SELECT * FROM notes ORDER BY pinned DESC, updatedAt DESC")
    fun notes(): Flow<List<Note>>
    @Upsert suspend fun upsertNote(n: Note): Long
    @Delete suspend fun deleteNote(n: Note)

    @Query("SELECT * FROM top_lists ORDER BY sort, id")
    fun topLists(): Flow<List<TopList>>
    @Upsert suspend fun upsertTopList(l: TopList): Long
    @Delete suspend fun deleteTopList(l: TopList)
    @Query("SELECT * FROM top_items ORDER BY sort, id")
    fun topItems(): Flow<List<TopItem>>
    @Upsert suspend fun upsertTopItem(i: TopItem): Long
    @Upsert suspend fun upsertTopItems(i: List<TopItem>)
    @Delete suspend fun deleteTopItem(i: TopItem)
    @Query("DELETE FROM top_items WHERE listId = :listId")
    suspend fun deleteTopItemsOf(listId: Long)

    /* ---- гардероб ---- */
    @Query("SELECT * FROM wardrobe_items WHERE hidden = 0")
    fun wardrobe(): Flow<List<WardrobeItem>>
    @Query("SELECT id FROM wardrobe_items")
    suspend fun wardrobeIds(): List<String>
    @Upsert suspend fun upsertWardrobeItem(i: WardrobeItem)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertWardrobeItems(i: List<WardrobeItem>)
    @Delete suspend fun deleteWardrobeItem(i: WardrobeItem)

    @Query("SELECT * FROM item_fits")
    fun fits(): Flow<List<ItemFit>>
    @Query("SELECT * FROM item_fits")
    suspend fun fitsNow(): List<ItemFit>
    @Upsert suspend fun upsertFit(f: ItemFit)
    @Query("DELETE FROM item_fits WHERE itemId = :itemId")
    suspend fun deleteFit(itemId: String)
    @Query("DELETE FROM item_fits")
    suspend fun deleteAllFits()

    @Query("SELECT * FROM outfits ORDER BY createdAt DESC")
    fun outfits(): Flow<List<Outfit>>
    @Upsert suspend fun upsertOutfit(o: Outfit): Long
    @Delete suspend fun deleteOutfit(o: Outfit)
}
