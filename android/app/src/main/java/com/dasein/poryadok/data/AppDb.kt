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
        Outfit::class, WardrobeSet::class,
    ],
    version = 3,
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

        /** v3: карточка вещи (бренд, цвета, цена…), фон коллажа и подборки гардероба. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("brand", "tags", "colors", "materials", "link", "note").forEach {
                    db.execSQL("ALTER TABLE `wardrobe_items` ADD COLUMN `$it` TEXT NOT NULL DEFAULT ''")
                }
                db.execSQL("ALTER TABLE `wardrobe_items` ADD COLUMN `price` REAL NOT NULL DEFAULT 0")
                listOf("bought", "favorite", "archived", "addedAt").forEach {
                    db.execSQL("ALTER TABLE `wardrobe_items` ADD COLUMN `$it` INTEGER NOT NULL DEFAULT 0")
                }
                db.execSQL("ALTER TABLE `outfits` ADD COLUMN `bg` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `wardrobe_sets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`kind` TEXT NOT NULL, `itemIds` TEXT NOT NULL, `outfitIds` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
            }
        }

        fun create(context: Context): AppDb =
            Room.databaseBuilder(context, AppDb::class.java, "poryadok.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }
}
