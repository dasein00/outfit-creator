package com.dasein.poryadok.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Project::class, TaskItem::class, Subtask::class, Goal::class, Milestone::class, FocusSession::class,
        Habit::class, HabitLog::class, EventItem::class, Reminder::class, Account::class, Category::class,
        Txn::class, Budget::class, Recurring::class, BodyProfile::class, WeightEntry::class, Measurement::class,
        FoodEntry::class, DayLog::class, Workout::class, SleepEntry::class, MoodEntry::class, ProgressPhoto::class,
        BalanceWheel::class, Note::class, TopList::class, TopItem::class, WardrobeItem::class, ItemFit::class,
        Outfit::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): LifeDao
    abstract fun backupDao(): BackupDao

    companion object {
        fun create(context: Context): AppDb =
            Room.databaseBuilder(context, AppDb::class.java, "poryadok.db").build()
    }
}
