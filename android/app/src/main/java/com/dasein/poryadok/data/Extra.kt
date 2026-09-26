package com.dasein.poryadok.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/*
 * Вторая база: рецепты и меню, список покупок, история готовки, тетрадь финансов и журнал импорта.
 * Отдельный файл позволяет добавлять разделы, не трогая схему основной базы у тех, кто уже пользуется приложением.
 */

/** Продукт из базы: пищевая ценность на 100 г. gramsPerUnit — вес одной штуки, если продукт считают штуками. */
@Serializable
@Entity(tableName = "products", indices = [Index("name")])
data class FoodProduct(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String = "Другое",
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0,
    val fiber: Double = 0.0,
    val gramsPerUnit: Double = 0.0,
    val custom: Boolean = false,
    val seedKey: String? = null,
)

@Serializable
@Entity(tableName = "recipes", indices = [Index("seedKey")])
data class Recipe(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String = "Обеды",
    /** Приёмы пищи через запятую (индексы MealType). */
    val meals: String = "",
    /** Теги через запятую. */
    val tags: String = "",
    val servings: Int = 1,
    val prepMin: Int = 0,
    val cookMin: Int = 0,
    /** 1 — легко, 2 — средне, 3 — сложно. */
    val difficulty: Int = 1,
    val photo: String = "",
    val notes: String = "",
    val tips: String = "",
    val swaps: String = "",
    val favorite: Boolean = false,
    val custom: Boolean = false,
    val seedKey: String? = null,
    val createdAt: Long = 0,
)

/** Ингредиент рецепта. КБЖУ на 100 г копируется из продукта, чтобы рецепт не зависел от правок базы. */
@Serializable
@Entity(tableName = "recipe_ingredients", indices = [Index("recipeId")])
data class RecipeIngredient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    val pos: Int = 0,
    val productId: Long? = null,
    val name: String,
    val amount: Double,
    val unit: String = "г",
    val grams: Double = 0.0,
    val kcal100: Double = 0.0,
    val protein100: Double = 0.0,
    val fat100: Double = 0.0,
    val carbs100: Double = 0.0,
    val fiber100: Double = 0.0,
    val shopCategory: String = "Другое",
)

@Serializable
@Entity(tableName = "recipe_steps", indices = [Index("recipeId")])
data class RecipeStep(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    val pos: Int = 0,
    val text: String,
    val minutes: Int = 0,
)

object PlanStatus {
    const val PLANNED = 0
    const val EATEN = 1
    const val SKIPPED = 2
}

/**
 * Блюдо в меню на конкретный день. Ссылается на рецепт; КБЖУ на порцию хранится снимком,
 * чтобы меню не ломалось, если рецепт удалят.
 */
@Serializable
@Entity(tableName = "meal_plan", indices = [Index("day"), Index(value = ["repeatId", "day"])])
data class MealPlanItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val meal: Int,
    val pos: Int = 0,
    val recipeId: Long? = null,
    val title: String,
    val servings: Double = 1.0,
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0,
    val timeMin: Int? = null,
    val status: Int = PlanStatus.PLANNED,
    val foodEntryId: Long? = null,
    val repeatId: Long? = null,
)

/** Шаблон меню: на один день (days = 1) или на неделю (days = 7). */
@Serializable
@Entity(tableName = "meal_presets")
data class MealPreset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val days: Int = 1,
    val note: String = "",
    val createdAt: Long = 0,
)

@Serializable
@Entity(tableName = "meal_preset_items", indices = [Index("presetId")])
data class MealPresetItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val presetId: Long,
    val dayIndex: Int = 0,
    val meal: Int,
    val recipeId: Long,
    val servings: Double = 1.0,
    val timeMin: Int? = null,
)

/** Повторяющееся блюдо: по дням недели (битовая маска, пн = бит 0) в выбранный приём пищи. */
@Serializable
@Entity(tableName = "meal_repeats")
data class MealRepeat(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    val meal: Int,
    val daysMask: Int = 127,
    val fromDay: Long,
    val toDay: Long? = null,
    val servings: Double = 1.0,
    val timeMin: Int? = null,
)

@Serializable
@Entity(tableName = "shopping")
data class ShoppingItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Double = 0.0,
    val unit: String = "",
    val category: String = "Другое",
    val bought: Boolean = false,
    val fromMenu: Boolean = false,
    val key: String = "",
    val note: String = "",
)

/** Что и когда приготовлено. Значения — снимок на момент готовки. */
@Serializable
@Entity(tableName = "cooking_history", indices = [Index("day")])
data class CookingLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val day: Long,
    val recipeId: Long? = null,
    val recipeName: String,
    val servings: Double = 1.0,
    val eaten: Double = 0.0,
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0,
    val meal: Int = 0,
    val foodEntryId: Long? = null,
)

object NoteKind {
    /** Сумма, написанная в тетради отдельно: «итого», «зарплата», «остаток»… */
    const val WRITTEN_TOTAL = 0

    /** Расход из правой колонки — дата неизвестна. */
    const val UNDATED_EXPENSE = 1

    /** День в таблице без суммы: выходной или нет записи. */
    const val NO_INCOME = 2
}

/** Запись финансовой тетради, которая не является обычной операцией. Исходный текст хранится в raw. */
@Serializable
@Entity(tableName = "finance_notes", indices = [Index(value = ["year", "month"])])
data class FinanceNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val year: Int,
    val month: Int,
    val day: Int? = null,
    val kind: Int,
    val label: String = "",
    val amount: Double? = null,
    val category: String = "",
    val raw: String = "",
    val confidence: String = "",
    val ambiguous: Boolean = false,
    val reason: String = "",
    val source: String = "",
    val importKey: String = "",
)

/** Журнал импорта: один ключ источника — одна запись в приложении. Защищает от дублей. */
@Serializable
@Entity(tableName = "import_records")
data class ImportRecord(
    @PrimaryKey val key: String,
    val kind: String,
    val targetId: Long,
    val at: Long,
)

@Dao
interface ExtraDao {
    // Продукты
    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE") fun products(): Flow<List<FoodProduct>>
    @Query("SELECT * FROM products") suspend fun productsNow(): List<FoodProduct>
    @Query("SELECT COUNT(*) FROM products") suspend fun productCount(): Int
    @Upsert suspend fun upsertProduct(p: FoodProduct): Long
    @Delete suspend fun deleteProduct(p: FoodProduct)

    // Рецепты
    @Query("SELECT * FROM recipes ORDER BY name COLLATE NOCASE") fun recipes(): Flow<List<Recipe>>
    @Query("SELECT * FROM recipes") suspend fun recipesNow(): List<Recipe>
    @Query("SELECT * FROM recipes WHERE id = :id") fun recipe(id: Long): Flow<Recipe?>
    @Query("SELECT * FROM recipes WHERE id = :id") suspend fun recipeNow(id: Long): Recipe?
    @Query("SELECT seedKey FROM recipes WHERE seedKey IS NOT NULL") suspend fun recipeSeedKeys(): List<String>
    @Upsert suspend fun upsertRecipe(r: Recipe): Long
    @Query("DELETE FROM recipes WHERE id = :id") suspend fun deleteRecipe(id: Long)
    @Query("UPDATE recipes SET favorite = :fav WHERE id = :id") suspend fun setFavorite(id: Long, fav: Boolean)

    @Query("SELECT * FROM recipe_ingredients ORDER BY recipeId, pos") fun ingredients(): Flow<List<RecipeIngredient>>
    @Query("SELECT * FROM recipe_ingredients WHERE recipeId = :id ORDER BY pos") fun ingredientsOf(id: Long): Flow<List<RecipeIngredient>>
    @Query("SELECT * FROM recipe_ingredients WHERE recipeId = :id ORDER BY pos") suspend fun ingredientsOfNow(id: Long): List<RecipeIngredient>
    @Query("SELECT * FROM recipe_ingredients") suspend fun ingredientsNow(): List<RecipeIngredient>
    @Insert suspend fun insertIngredients(items: List<RecipeIngredient>)
    @Query("DELETE FROM recipe_ingredients WHERE recipeId = :id") suspend fun deleteIngredientsOf(id: Long)

    @Query("SELECT * FROM recipe_steps WHERE recipeId = :id ORDER BY pos") fun stepsOf(id: Long): Flow<List<RecipeStep>>
    @Query("SELECT * FROM recipe_steps WHERE recipeId = :id ORDER BY pos") suspend fun stepsOfNow(id: Long): List<RecipeStep>
    @Insert suspend fun insertSteps(items: List<RecipeStep>)
    @Query("DELETE FROM recipe_steps WHERE recipeId = :id") suspend fun deleteStepsOf(id: Long)

    // Меню
    @Query("SELECT * FROM meal_plan ORDER BY day, meal, pos") fun plan(): Flow<List<MealPlanItem>>
    @Query("SELECT * FROM meal_plan WHERE day BETWEEN :from AND :to ORDER BY day, meal, pos") suspend fun planNow(from: Long, to: Long): List<MealPlanItem>
    @Query("SELECT * FROM meal_plan WHERE recipeId = :id AND status = 0") suspend fun plannedOf(id: Long): List<MealPlanItem>
    @Query("SELECT COUNT(*) FROM meal_plan WHERE repeatId = :repeatId AND day = :day") suspend fun repeatCount(repeatId: Long, day: Long): Int
    @Upsert suspend fun upsertPlan(p: MealPlanItem): Long
    @Delete suspend fun deletePlan(p: MealPlanItem)
    @Query("DELETE FROM meal_plan WHERE day BETWEEN :from AND :to AND status = 0 AND repeatId IS NULL") suspend fun clearPlanned(from: Long, to: Long)
    @Query("DELETE FROM meal_plan WHERE repeatId = :repeatId AND status = 0 AND day >= :from") suspend fun deleteRepeatFuture(repeatId: Long, from: Long)

    @Query("SELECT * FROM meal_presets ORDER BY name COLLATE NOCASE") fun presets(): Flow<List<MealPreset>>
    @Query("SELECT * FROM meal_preset_items") fun presetItems(): Flow<List<MealPresetItem>>
    @Query("SELECT * FROM meal_preset_items WHERE presetId = :id") suspend fun presetItemsOf(id: Long): List<MealPresetItem>
    @Upsert suspend fun upsertPreset(p: MealPreset): Long
    @Query("DELETE FROM meal_presets WHERE id = :id") suspend fun deletePreset(id: Long)
    @Insert suspend fun insertPresetItems(items: List<MealPresetItem>)
    @Upsert suspend fun upsertPresetItem(item: MealPresetItem): Long
    @Delete suspend fun deletePresetItem(item: MealPresetItem)
    @Query("DELETE FROM meal_preset_items WHERE presetId = :id") suspend fun deletePresetItemsOf(id: Long)

    @Query("SELECT * FROM meal_repeats") fun repeats(): Flow<List<MealRepeat>>
    @Query("SELECT * FROM meal_repeats") suspend fun repeatsNow(): List<MealRepeat>
    @Upsert suspend fun upsertRepeat(r: MealRepeat): Long
    @Query("DELETE FROM meal_repeats WHERE id = :id") suspend fun deleteRepeat(id: Long)

    // Покупки
    @Query("SELECT * FROM shopping ORDER BY bought, category, name COLLATE NOCASE") fun shopping(): Flow<List<ShoppingItem>>
    @Query("SELECT * FROM shopping") suspend fun shoppingNow(): List<ShoppingItem>
    @Upsert suspend fun upsertShopping(s: ShoppingItem): Long
    @Delete suspend fun deleteShopping(s: ShoppingItem)
    @Query("DELETE FROM shopping WHERE bought = 1") suspend fun clearBought()
    @Query("DELETE FROM shopping WHERE fromMenu = 1 AND bought = 0") suspend fun clearMenuShopping()

    // История готовки
    @Query("SELECT * FROM cooking_history ORDER BY at DESC") fun history(): Flow<List<CookingLog>>
    @Upsert suspend fun upsertHistory(h: CookingLog): Long
    @Delete suspend fun deleteHistory(h: CookingLog)

    // Тетрадь финансов
    @Query("SELECT * FROM finance_notes ORDER BY year, month, kind, day, id") fun financeNotes(): Flow<List<FinanceNote>>
    @Query("SELECT * FROM finance_notes") suspend fun financeNotesNow(): List<FinanceNote>
    @Upsert suspend fun upsertFinanceNote(n: FinanceNote): Long
    @Delete suspend fun deleteFinanceNote(n: FinanceNote)

    @Query("SELECT * FROM import_records WHERE `key` = :key") suspend fun importRecord(key: String): ImportRecord?
    @Query("SELECT * FROM import_records WHERE kind = :kind") suspend fun importRecordsOf(kind: String): List<ImportRecord>
    @Query("SELECT * FROM import_records WHERE kind LIKE :prefix || '%'") fun importRecordsLike(prefix: String): Flow<List<ImportRecord>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putImportRecord(r: ImportRecord)
    @Query("DELETE FROM import_records WHERE `key` = :key") suspend fun deleteImportRecord(key: String)

    // Резервная копия
    @Query("SELECT * FROM products") suspend fun allProducts(): List<FoodProduct>
    @Query("SELECT * FROM recipes") suspend fun allRecipes(): List<Recipe>
    @Query("SELECT * FROM recipe_ingredients") suspend fun allIngredients(): List<RecipeIngredient>
    @Query("SELECT * FROM recipe_steps") suspend fun allSteps(): List<RecipeStep>
    @Query("SELECT * FROM meal_plan") suspend fun allPlan(): List<MealPlanItem>
    @Query("SELECT * FROM meal_presets") suspend fun allPresets(): List<MealPreset>
    @Query("SELECT * FROM meal_preset_items") suspend fun allPresetItems(): List<MealPresetItem>
    @Query("SELECT * FROM meal_repeats") suspend fun allRepeats(): List<MealRepeat>
    @Query("SELECT * FROM shopping") suspend fun allShopping(): List<ShoppingItem>
    @Query("SELECT * FROM cooking_history") suspend fun allHistory(): List<CookingLog>
    @Query("SELECT * FROM import_records") suspend fun allImportRecords(): List<ImportRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putProducts(items: List<FoodProduct>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putRecipes(items: List<Recipe>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putIngredients(items: List<RecipeIngredient>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSteps(items: List<RecipeStep>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putPlan(items: List<MealPlanItem>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putPresets(items: List<MealPreset>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putPresetItems(items: List<MealPresetItem>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putRepeats(items: List<MealRepeat>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putShopping(items: List<ShoppingItem>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putHistory(items: List<CookingLog>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putFinanceNotes(items: List<FinanceNote>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putImportRecords(items: List<ImportRecord>)

    @Query("DELETE FROM products") suspend fun wipeProducts()
    @Query("DELETE FROM recipes") suspend fun wipeRecipes()
    @Query("DELETE FROM recipe_ingredients") suspend fun wipeIngredients()
    @Query("DELETE FROM recipe_steps") suspend fun wipeSteps()
    @Query("DELETE FROM meal_plan") suspend fun wipePlan()
    @Query("DELETE FROM meal_presets") suspend fun wipePresets()
    @Query("DELETE FROM meal_preset_items") suspend fun wipePresetItems()
    @Query("DELETE FROM meal_repeats") suspend fun wipeRepeats()
    @Query("DELETE FROM shopping") suspend fun wipeShopping()
    @Query("DELETE FROM cooking_history") suspend fun wipeHistory()
    @Query("DELETE FROM finance_notes") suspend fun wipeFinanceNotes()
    @Query("DELETE FROM import_records") suspend fun wipeImportRecords()
}

@Database(
    entities = [
        FoodProduct::class, Recipe::class, RecipeIngredient::class, RecipeStep::class, MealPlanItem::class,
        MealPreset::class, MealPresetItem::class, MealRepeat::class, ShoppingItem::class, CookingLog::class,
        FinanceNote::class, ImportRecord::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class ExtraDb : RoomDatabase() {
    abstract fun dao(): ExtraDao

    companion object {
        fun create(context: Context): ExtraDb =
            Room.databaseBuilder(context, ExtraDb::class.java, "dasein_extra.db").build()
    }
}

@Serializable
data class ExtraBackup(
    val products: List<FoodProduct> = emptyList(),
    val recipes: List<Recipe> = emptyList(),
    val ingredients: List<RecipeIngredient> = emptyList(),
    val steps: List<RecipeStep> = emptyList(),
    val plan: List<MealPlanItem> = emptyList(),
    val presets: List<MealPreset> = emptyList(),
    val presetItems: List<MealPresetItem> = emptyList(),
    val repeats: List<MealRepeat> = emptyList(),
    val shopping: List<ShoppingItem> = emptyList(),
    val history: List<CookingLog> = emptyList(),
    val financeNotes: List<FinanceNote> = emptyList(),
    val importRecords: List<ImportRecord> = emptyList(),
)

suspend fun ExtraDb.exportExtra(): ExtraBackup {
    val d = dao()
    return ExtraBackup(
        d.allProducts(), d.allRecipes(), d.allIngredients(), d.allSteps(), d.allPlan(), d.allPresets(),
        d.allPresetItems(), d.allRepeats(), d.allShopping(), d.allHistory(), d.financeNotesNow(), d.allImportRecords(),
    )
}

suspend fun ExtraDb.importExtra(b: ExtraBackup) {
    val d = dao()
    withTransaction {
        d.wipeProducts(); d.putProducts(b.products)
        d.wipeRecipes(); d.putRecipes(b.recipes)
        d.wipeIngredients(); d.putIngredients(b.ingredients)
        d.wipeSteps(); d.putSteps(b.steps)
        d.wipePlan(); d.putPlan(b.plan)
        d.wipePresets(); d.putPresets(b.presets)
        d.wipePresetItems(); d.putPresetItems(b.presetItems)
        d.wipeRepeats(); d.putRepeats(b.repeats)
        d.wipeShopping(); d.putShopping(b.shopping)
        d.wipeHistory(); d.putHistory(b.history)
        d.wipeFinanceNotes(); d.putFinanceNotes(b.financeNotes)
        d.wipeImportRecords(); d.putImportRecords(b.importRecords)
    }
}
