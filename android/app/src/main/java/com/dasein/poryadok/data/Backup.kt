package com.dasein.poryadok.data

import androidx.room.withTransaction
import kotlinx.serialization.Serializable

@Serializable
data class BackupData(
    val version: Int = 1,
    val createdAt: Long = 0,
    val projects: List<Project> = emptyList(),
    val taskItems: List<TaskItem> = emptyList(),
    val subtasks: List<Subtask> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val milestones: List<Milestone> = emptyList(),
    val focusSessions: List<FocusSession> = emptyList(),
    val habits: List<Habit> = emptyList(),
    val habitLogs: List<HabitLog> = emptyList(),
    val eventItems: List<EventItem> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categorys: List<Category> = emptyList(),
    val txns: List<Txn> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val recurrings: List<Recurring> = emptyList(),
    val bodyProfiles: List<BodyProfile> = emptyList(),
    val weightEntrys: List<WeightEntry> = emptyList(),
    val measurements: List<Measurement> = emptyList(),
    val foodEntrys: List<FoodEntry> = emptyList(),
    val dayLogs: List<DayLog> = emptyList(),
    val workouts: List<Workout> = emptyList(),
    val sleepEntrys: List<SleepEntry> = emptyList(),
    val moodEntrys: List<MoodEntry> = emptyList(),
    val progressPhotos: List<ProgressPhoto> = emptyList(),
    val balanceWheels: List<BalanceWheel> = emptyList(),
    val notes: List<Note> = emptyList(),
    val topLists: List<TopList> = emptyList(),
    val topItems: List<TopItem> = emptyList(),
    val wardrobeItems: List<WardrobeItem> = emptyList(),
    val itemFits: List<ItemFit> = emptyList(),
    val outfits: List<Outfit> = emptyList(),
)

suspend fun AppDb.exportData(now: Long): BackupData {
    val d = backupDao()
    return BackupData(
        createdAt = now,
        projects = d.allProject(),
        taskItems = d.allTaskItem(),
        subtasks = d.allSubtask(),
        goals = d.allGoal(),
        milestones = d.allMilestone(),
        focusSessions = d.allFocusSession(),
        habits = d.allHabit(),
        habitLogs = d.allHabitLog(),
        eventItems = d.allEventItem(),
        reminders = d.allReminder(),
        accounts = d.allAccount(),
        categorys = d.allCategory(),
        txns = d.allTxn(),
        budgets = d.allBudget(),
        recurrings = d.allRecurring(),
        bodyProfiles = d.allBodyProfile(),
        weightEntrys = d.allWeightEntry(),
        measurements = d.allMeasurement(),
        foodEntrys = d.allFoodEntry(),
        dayLogs = d.allDayLog(),
        workouts = d.allWorkout(),
        sleepEntrys = d.allSleepEntry(),
        moodEntrys = d.allMoodEntry(),
        progressPhotos = d.allProgressPhoto(),
        balanceWheels = d.allBalanceWheel(),
        notes = d.allNote(),
        topLists = d.allTopList(),
        topItems = d.allTopItem(),
        wardrobeItems = d.allWardrobeItem(),
        itemFits = d.allItemFit(),
        outfits = d.allOutfit(),
    )
}

suspend fun AppDb.importData(b: BackupData) {
    val d = backupDao()
    withTransaction {
        d.wipeProject(); d.putProject(b.projects)
        d.wipeTaskItem(); d.putTaskItem(b.taskItems)
        d.wipeSubtask(); d.putSubtask(b.subtasks)
        d.wipeGoal(); d.putGoal(b.goals)
        d.wipeMilestone(); d.putMilestone(b.milestones)
        d.wipeFocusSession(); d.putFocusSession(b.focusSessions)
        d.wipeHabit(); d.putHabit(b.habits)
        d.wipeHabitLog(); d.putHabitLog(b.habitLogs)
        d.wipeEventItem(); d.putEventItem(b.eventItems)
        d.wipeReminder(); d.putReminder(b.reminders)
        d.wipeAccount(); d.putAccount(b.accounts)
        d.wipeCategory(); d.putCategory(b.categorys)
        d.wipeTxn(); d.putTxn(b.txns)
        d.wipeBudget(); d.putBudget(b.budgets)
        d.wipeRecurring(); d.putRecurring(b.recurrings)
        d.wipeBodyProfile(); d.putBodyProfile(b.bodyProfiles)
        d.wipeWeightEntry(); d.putWeightEntry(b.weightEntrys)
        d.wipeMeasurement(); d.putMeasurement(b.measurements)
        d.wipeFoodEntry(); d.putFoodEntry(b.foodEntrys)
        d.wipeDayLog(); d.putDayLog(b.dayLogs)
        d.wipeWorkout(); d.putWorkout(b.workouts)
        d.wipeSleepEntry(); d.putSleepEntry(b.sleepEntrys)
        d.wipeMoodEntry(); d.putMoodEntry(b.moodEntrys)
        d.wipeProgressPhoto(); d.putProgressPhoto(b.progressPhotos)
        d.wipeBalanceWheel(); d.putBalanceWheel(b.balanceWheels)
        d.wipeNote(); d.putNote(b.notes)
        d.wipeTopList(); d.putTopList(b.topLists)
        d.wipeTopItem(); d.putTopItem(b.topItems)
        d.wipeWardrobeItem(); d.putWardrobeItem(b.wardrobeItems)
        d.wipeItemFit(); d.putItemFit(b.itemFits)
        d.wipeOutfit(); d.putOutfit(b.outfits)
    }
}
