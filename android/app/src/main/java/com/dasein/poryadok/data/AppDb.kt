package com.dasein.poryadok.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Project::class, TaskItem::class, Subtask::class, Goal::class, Milestone::class, FocusSession::class,
        Habit::class, HabitLog::class, EventItem::class, Reminder::class, Account::class, Category::class,
        Txn::class, Budget::class, Recurring::class, BodyProfile::class, WeightEntry::class, Measurement::class,
        FoodEntry::class, DayLog::class, Workout::class, SleepEntry::class, MoodEntry::class, ProgressPhoto::class,
        BalanceWheel::class, Note::class, TopList::class, TopItem::class, WardrobeItem::class, ItemFit::class,
        Outfit::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): LifeDao
    abstract fun backupDao(): BackupDao

    companion object {
        /** v2: обхват шеи в замерах — нужен для процента жира по формуле ВМС США. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `measurements` ADD COLUMN `neck` REAL")
            }
        }

        fun create(context: Context): AppDb =
            Room.databaseBuilder(context, AppDb::class.java, "poryadok.db").addMigrations(MIGRATION_1_2).build()
    }
}
