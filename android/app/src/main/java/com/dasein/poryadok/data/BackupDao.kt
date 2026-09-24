package com.dasein.poryadok.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Выгрузка и загрузка всех таблиц целиком для резервной копии. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM projects") suspend fun allProject(): List<Project>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putProject(items: List<Project>)
    @Query("DELETE FROM projects") suspend fun wipeProject()
    @Query("SELECT * FROM tasks") suspend fun allTaskItem(): List<TaskItem>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTaskItem(items: List<TaskItem>)
    @Query("DELETE FROM tasks") suspend fun wipeTaskItem()
    @Query("SELECT * FROM subtasks") suspend fun allSubtask(): List<Subtask>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSubtask(items: List<Subtask>)
    @Query("DELETE FROM subtasks") suspend fun wipeSubtask()
    @Query("SELECT * FROM goals") suspend fun allGoal(): List<Goal>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putGoal(items: List<Goal>)
    @Query("DELETE FROM goals") suspend fun wipeGoal()
    @Query("SELECT * FROM milestones") suspend fun allMilestone(): List<Milestone>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putMilestone(items: List<Milestone>)
    @Query("DELETE FROM milestones") suspend fun wipeMilestone()
    @Query("SELECT * FROM focus_sessions") suspend fun allFocusSession(): List<FocusSession>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putFocusSession(items: List<FocusSession>)
    @Query("DELETE FROM focus_sessions") suspend fun wipeFocusSession()
    @Query("SELECT * FROM habits") suspend fun allHabit(): List<Habit>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putHabit(items: List<Habit>)
    @Query("DELETE FROM habits") suspend fun wipeHabit()
    @Query("SELECT * FROM habit_logs") suspend fun allHabitLog(): List<HabitLog>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putHabitLog(items: List<HabitLog>)
    @Query("DELETE FROM habit_logs") suspend fun wipeHabitLog()
    @Query("SELECT * FROM events") suspend fun allEventItem(): List<EventItem>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putEventItem(items: List<EventItem>)
    @Query("DELETE FROM events") suspend fun wipeEventItem()
    @Query("SELECT * FROM reminders") suspend fun allReminder(): List<Reminder>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putReminder(items: List<Reminder>)
    @Query("DELETE FROM reminders") suspend fun wipeReminder()
    @Query("SELECT * FROM accounts") suspend fun allAccount(): List<Account>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putAccount(items: List<Account>)
    @Query("DELETE FROM accounts") suspend fun wipeAccount()
    @Query("SELECT * FROM categories") suspend fun allCategory(): List<Category>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putCategory(items: List<Category>)
    @Query("DELETE FROM categories") suspend fun wipeCategory()
    @Query("SELECT * FROM txns") suspend fun allTxn(): List<Txn>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTxn(items: List<Txn>)
    @Query("DELETE FROM txns") suspend fun wipeTxn()
    @Query("SELECT * FROM budgets") suspend fun allBudget(): List<Budget>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putBudget(items: List<Budget>)
    @Query("DELETE FROM budgets") suspend fun wipeBudget()
    @Query("SELECT * FROM recurring") suspend fun allRecurring(): List<Recurring>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putRecurring(items: List<Recurring>)
    @Query("DELETE FROM recurring") suspend fun wipeRecurring()
    @Query("SELECT * FROM body_profile") suspend fun allBodyProfile(): List<BodyProfile>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putBodyProfile(items: List<BodyProfile>)
    @Query("DELETE FROM body_profile") suspend fun wipeBodyProfile()
    @Query("SELECT * FROM weights") suspend fun allWeightEntry(): List<WeightEntry>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putWeightEntry(items: List<WeightEntry>)
    @Query("DELETE FROM weights") suspend fun wipeWeightEntry()
    @Query("SELECT * FROM measurements") suspend fun allMeasurement(): List<Measurement>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putMeasurement(items: List<Measurement>)
    @Query("DELETE FROM measurements") suspend fun wipeMeasurement()
    @Query("SELECT * FROM food") suspend fun allFoodEntry(): List<FoodEntry>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putFoodEntry(items: List<FoodEntry>)
    @Query("DELETE FROM food") suspend fun wipeFoodEntry()
    @Query("SELECT * FROM day_logs") suspend fun allDayLog(): List<DayLog>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putDayLog(items: List<DayLog>)
    @Query("DELETE FROM day_logs") suspend fun wipeDayLog()
    @Query("SELECT * FROM workouts") suspend fun allWorkout(): List<Workout>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putWorkout(items: List<Workout>)
    @Query("DELETE FROM workouts") suspend fun wipeWorkout()
    @Query("SELECT * FROM sleep") suspend fun allSleepEntry(): List<SleepEntry>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSleepEntry(items: List<SleepEntry>)
    @Query("DELETE FROM sleep") suspend fun wipeSleepEntry()
    @Query("SELECT * FROM moods") suspend fun allMoodEntry(): List<MoodEntry>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putMoodEntry(items: List<MoodEntry>)
    @Query("DELETE FROM moods") suspend fun wipeMoodEntry()
    @Query("SELECT * FROM progress_photos") suspend fun allProgressPhoto(): List<ProgressPhoto>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putProgressPhoto(items: List<ProgressPhoto>)
    @Query("DELETE FROM progress_photos") suspend fun wipeProgressPhoto()
    @Query("SELECT * FROM balance_wheel") suspend fun allBalanceWheel(): List<BalanceWheel>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putBalanceWheel(items: List<BalanceWheel>)
    @Query("DELETE FROM balance_wheel") suspend fun wipeBalanceWheel()
    @Query("SELECT * FROM notes") suspend fun allNote(): List<Note>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putNote(items: List<Note>)
    @Query("DELETE FROM notes") suspend fun wipeNote()
    @Query("SELECT * FROM top_lists") suspend fun allTopList(): List<TopList>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTopList(items: List<TopList>)
    @Query("DELETE FROM top_lists") suspend fun wipeTopList()
    @Query("SELECT * FROM top_items") suspend fun allTopItem(): List<TopItem>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTopItem(items: List<TopItem>)
    @Query("DELETE FROM top_items") suspend fun wipeTopItem()
    @Query("SELECT * FROM wardrobe_items") suspend fun allWardrobeItem(): List<WardrobeItem>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putWardrobeItem(items: List<WardrobeItem>)
    @Query("DELETE FROM wardrobe_items") suspend fun wipeWardrobeItem()
    @Query("SELECT * FROM item_fits") suspend fun allItemFit(): List<ItemFit>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putItemFit(items: List<ItemFit>)
    @Query("DELETE FROM item_fits") suspend fun wipeItemFit()
    @Query("SELECT * FROM outfits") suspend fun allOutfit(): List<Outfit>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putOutfit(items: List<Outfit>)
    @Query("DELETE FROM outfits") suspend fun wipeOutfit()
}
