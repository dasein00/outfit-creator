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
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
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
    /** Убран в архив: не показывается в каталоге и подборе меню, но не удалён. */
    @androidx.room.ColumnInfo(defaultValue = "0") val archived: Boolean = false,
    /** Видео рецепта: пути к файлам или ссылки, по одному в строке. */
    val videos: String = "",
    /** Вес одной порции, г. Если задан — порции считаются по весу, а не делением на число порций. */
    @androidx.room.ColumnInfo(defaultValue = "0") val portionGrams: Double = 0.0,
    /** Вес готового блюда, г (после варки или жарки). 0 — сумма веса ингредиентов. */
    @androidx.room.ColumnInfo(defaultValue = "0") val dishGrams: Double = 0.0,
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

/** Взвешивание со всеми показателями весов (или введёнными вручную). Проценты — от массы тела. */
@Serializable
@Entity(tableName = "body_metrics", indices = [Index("day"), Index("extId")])
data class BodyMetric(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val day: Long,
    val weight: Double,
    val fatPct: Double? = null,
    val musclePct: Double? = null,
    val muscleKg: Double? = null,
    val waterPct: Double? = null,
    val proteinPct: Double? = null,
    val boneKg: Double? = null,
    val visceral: Double? = null,
    val bmr: Double? = null,
    val metabolicAge: Double? = null,
    val subcutaneousPct: Double? = null,
    val leanKg: Double? = null,
    val source: String = "вручную",
    val extId: String? = null,
    /** Рост и обхваты в момент замера — для научных показателей (ИМТ, талия/рост, жир по ВМС США). */
    val heightCm: Double? = null,
    val waistCm: Double? = null,
    val hipCm: Double? = null,
    val neckCm: Double? = null,
    /** Пульс и сердечный индекс (л/мин/м²) — их показывают весы с ЧСС (Kitfort KT-4057, Fitdays). */
    val heartRate: Double? = null,
    val cardiacIndex: Double? = null,
)

/** Сон, определённый по использованию телефона. day — утро. Исправления пользователя учат алгоритм. */
@Serializable
@Entity(tableName = "sleep_auto")
data class SleepAuto(
    @PrimaryKey val day: Long,
    val lastUseAt: Long,
    val firstUseAt: Long,
    val sleepAt: Long,
    val wakeAt: Long,
    val awakenings: Int = 0,
    val glances: Int = 0,
    val confidence: Int = 0,
    val applied: Boolean = false,
    val userBedMin: Int? = null,
    val userWakeMin: Int? = null,
)

/** Активные калории за день из Health Connect (часы, браслет, Mi Fitness). */
@Serializable
@Entity(tableName = "day_energy")
data class DayEnergy(
    @PrimaryKey val day: Long,
    val activeKcal: Int,
    val source: String = "Health Connect",
)

object MediaKind {
    const val MOVIE = 0
    const val SERIES = 1
    const val BOOK = 2
    val names = listOf("Фильм", "Сериал", "Книга")
    val plural = listOf("Фильмы", "Сериалы", "Книги")
}

object MediaStatus {
    const val PLANNED = 0
    const val IN_PROGRESS = 1
    const val DONE = 2
    const val DROPPED = 3
    fun defaults(kind: Int) = if (kind == MediaKind.BOOK) listOf("Хочу прочитать", "Читаю", "Прочитано", "Бросил")
    else listOf("Хочу посмотреть", "Смотрю", "Просмотрено", "Бросил")

    /** Названия статусов с учётом переименования пользователем (например, «Хочу посмотреть» → «В планах»). */
    fun names(kind: Int) = defaults(kind).mapIndexed { i, d -> CustomNames.get(statusKey(kind, i), d) }
    fun statusKey(kind: Int, i: Int) = "status_${kind}_$i"
}

/** Карточка фильма, сериала или книги в личной коллекции. */
@Serializable
@Entity(tableName = "media_items", indices = [Index("kind"), Index("externalId")])
data class MediaItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: Int = MediaKind.MOVIE,
    val title: String,
    val originalTitle: String = "",
    val year: Int? = null,
    /** Локальный файл постера или обложки. */
    val poster: String = "",
    val posterUrl: String = "",
    val description: String = "",
    val genres: String = "",
    /** Режиссёры (для книг — авторы), через запятую. */
    val creators: String = "",
    /** Актёры и роли: по строке «Имя — роль». */
    val cast: String = "",
    val countries: String = "",
    /** Длительность, сезоны или страницы — как написано. */
    val length: String = "",
    val status: Int = MediaStatus.DONE,
    val favorite: Boolean = false,
    val myRating: Int = 0,
    val review: String = "",
    val finishedDay: Long? = null,
    val source: String = "вручную",
    val externalId: String = "",
    val externalRating: Double? = null,
    val url: String = "",
    val createdAt: Long = 0,
    /** Сколько серий просмотрено или страниц прочитано. */
    val progress: Int = 0,
    /** Всего серий или страниц (0 — не указано). */
    val progressTotal: Int = 0,
    /** Когда начал смотреть или читать. */
    val startedDay: Long? = null,
    /** Сколько раз пересматривал или перечитывал. */
    val rewatch: Int = 0,
    /** Свои метки через запятую: «в кино», «с семьёй», «посоветовали». */
    val tags: String = "",
)

/** Свой список фильмов, сериалов или книг с любым названием («Новогодние», «Посоветовали друзья»…). kind = −1 — для всех видов. */
@Serializable
@Entity(tableName = "media_lists")
data class MediaList(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val glyph: String = "",
    val kind: Int = -1,
    val createdAt: Long = 0,
)

@Serializable
@Entity(tableName = "media_list_items", primaryKeys = ["listId", "mediaId"], indices = [Index("mediaId")])
data class MediaListItem(
    val listId: Long,
    val mediaId: Long,
    val position: Int = 0,
    val addedAt: Long = 0,
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
    @Query("UPDATE recipes SET archived = :archived WHERE id = :id") suspend fun setArchived(id: Long, archived: Boolean)
    @Query("UPDATE recipes SET videos = :videos WHERE id = :id") suspend fun setVideos(id: Long, videos: String)

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

    // Состав тела
    @Query("SELECT * FROM body_metrics ORDER BY at") fun bodyMetrics(): Flow<List<BodyMetric>>
    @Query("SELECT * FROM body_metrics ORDER BY at") suspend fun bodyMetricsNow(): List<BodyMetric>
    @Query("SELECT COUNT(*) FROM body_metrics WHERE extId = :extId") suspend fun bodyExtCount(extId: String): Int
    @Query("SELECT * FROM body_metrics WHERE extId = :extId LIMIT 1") suspend fun bodyByExt(extId: String): BodyMetric?
    @Upsert suspend fun upsertBodyMetric(m: BodyMetric): Long
    @Delete suspend fun deleteBodyMetric(m: BodyMetric)

    // Автоопределение сна
    @Query("SELECT * FROM sleep_auto ORDER BY day DESC") fun sleepAuto(): Flow<List<SleepAuto>>
    @Query("SELECT * FROM sleep_auto ORDER BY day DESC") suspend fun sleepAutoNow(): List<SleepAuto>
    @Query("SELECT * FROM sleep_auto WHERE day = :day") suspend fun sleepAutoOf(day: Long): SleepAuto?
    @Upsert suspend fun upsertSleepAuto(s: SleepAuto)

    // Энергия за день
    @Query("SELECT * FROM day_energy ORDER BY day") fun dayEnergy(): Flow<List<DayEnergy>>
    @Query("SELECT * FROM day_energy WHERE day = :day") suspend fun dayEnergyOf(day: Long): DayEnergy?
    @Upsert suspend fun upsertDayEnergy(e: DayEnergy)

    // Фильмы, сериалы, книги
    @Query("SELECT * FROM media_items ORDER BY createdAt DESC") fun media(): Flow<List<MediaItem>>
    @Query("SELECT * FROM media_items WHERE id = :id") fun mediaItem(id: Long): Flow<MediaItem?>
    @Query("SELECT * FROM media_items WHERE id = :id") suspend fun mediaItemNow(id: Long): MediaItem?
    @Query("SELECT * FROM media_items WHERE externalId = :ext AND externalId != '' LIMIT 1") suspend fun mediaByExternal(ext: String): MediaItem?
    @Upsert suspend fun upsertMedia(m: MediaItem): Long
    @Delete suspend fun deleteMedia(m: MediaItem)

    // Свои списки
    @Query("SELECT * FROM media_lists ORDER BY createdAt") fun mediaLists(): Flow<List<MediaList>>
    @Query("SELECT * FROM media_list_items") fun mediaListItems(): Flow<List<MediaListItem>>
    @Upsert suspend fun upsertMediaList(l: MediaList): Long
    @Query("DELETE FROM media_lists WHERE id = :id") suspend fun deleteMediaListRow(id: Long)
    @Query("DELETE FROM media_list_items WHERE listId = :id") suspend fun clearMediaList(id: Long)
    @Query("DELETE FROM media_list_items WHERE mediaId = :mediaId") suspend fun removeMediaFromLists(mediaId: Long)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addToMediaList(i: MediaListItem)
    @Query("DELETE FROM media_list_items WHERE listId = :listId AND mediaId = :mediaId") suspend fun removeFromMediaList(listId: Long, mediaId: Long)
    @Query("SELECT * FROM media_lists") suspend fun allMediaLists(): List<MediaList>
    @Query("SELECT * FROM media_list_items") suspend fun allMediaListItems(): List<MediaListItem>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putMediaLists(items: List<MediaList>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putMediaListItems(items: List<MediaListItem>)
    @Query("DELETE FROM media_lists") suspend fun wipeMediaLists()
    @Query("DELETE FROM media_list_items") suspend fun wipeMediaListItems()

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
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putBodyMetrics(items: List<BodyMetric>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSleepAuto(items: List<SleepAuto>)
    @Query("DELETE FROM body_metrics") suspend fun wipeBodyMetrics()
    @Query("DELETE FROM sleep_auto") suspend fun wipeSleepAuto()
    @Query("SELECT * FROM day_energy") suspend fun allDayEnergy(): List<DayEnergy>
    @Query("SELECT * FROM media_items") suspend fun allMedia(): List<MediaItem>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putDayEnergy(items: List<DayEnergy>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putMedia(items: List<MediaItem>)
    @Query("DELETE FROM day_energy") suspend fun wipeDayEnergy()
    @Query("DELETE FROM media_items") suspend fun wipeMedia()
}

@Database(
    entities = [
        FoodProduct::class, Recipe::class, RecipeIngredient::class, RecipeStep::class, MealPlanItem::class,
        MealPreset::class, MealPresetItem::class, MealRepeat::class, ShoppingItem::class, CookingLog::class,
        FinanceNote::class, ImportRecord::class, BodyMetric::class, SleepAuto::class, DayEnergy::class, MediaItem::class,
        MediaList::class, MediaListItem::class, Page::class, PageBlock::class, Exercise::class, WorkoutPlan::class,
        PlanExercise::class, WorkoutSession::class, SetLog::class, CustomDay::class, HolidayMark::class,
    ],
    version = 10,
    exportSchema = false,
)
abstract class ExtraDb : RoomDatabase() {
    abstract fun dao(): ExtraDao
    abstract fun pages(): PageDao
    abstract fun training(): TrainingDao
    abstract fun days(): DaysDao

    companion object {
        /** v2: состав тела и автоопределение сна. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `body_metrics` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `at` INTEGER NOT NULL, " +
                        "`day` INTEGER NOT NULL, `weight` REAL NOT NULL, `fatPct` REAL, `musclePct` REAL, `muscleKg` REAL, `waterPct` REAL, " +
                        "`proteinPct` REAL, `boneKg` REAL, `visceral` REAL, `bmr` REAL, `metabolicAge` REAL, `subcutaneousPct` REAL, " +
                        "`leanKg` REAL, `source` TEXT NOT NULL, `extId` TEXT)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_body_metrics_day` ON `body_metrics` (`day`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_body_metrics_extId` ON `body_metrics` (`extId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sleep_auto` (`day` INTEGER NOT NULL, `lastUseAt` INTEGER NOT NULL, `firstUseAt` INTEGER NOT NULL, " +
                        "`sleepAt` INTEGER NOT NULL, `wakeAt` INTEGER NOT NULL, `awakenings` INTEGER NOT NULL, `glances` INTEGER NOT NULL, " +
                        "`confidence` INTEGER NOT NULL, `applied` INTEGER NOT NULL, `userBedMin` INTEGER, `userWakeMin` INTEGER, PRIMARY KEY(`day`))"
                )
            }
        }

        /** v3: энергия за день и коллекция фильмов, сериалов и книг. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `day_energy` (`day` INTEGER NOT NULL, `activeKcal` INTEGER NOT NULL, `source` TEXT NOT NULL, PRIMARY KEY(`day`))")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `media_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `kind` INTEGER NOT NULL, " +
                        "`title` TEXT NOT NULL, `originalTitle` TEXT NOT NULL, `year` INTEGER, `poster` TEXT NOT NULL, `posterUrl` TEXT NOT NULL, " +
                        "`description` TEXT NOT NULL, `genres` TEXT NOT NULL, `creators` TEXT NOT NULL, `cast` TEXT NOT NULL, `countries` TEXT NOT NULL, " +
                        "`length` TEXT NOT NULL, `status` INTEGER NOT NULL, `favorite` INTEGER NOT NULL, `myRating` INTEGER NOT NULL, " +
                        "`review` TEXT NOT NULL, `finishedDay` INTEGER, `source` TEXT NOT NULL, `externalId` TEXT NOT NULL, " +
                        "`externalRating` REAL, `url` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_items_kind` ON `media_items` (`kind`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_items_externalId` ON `media_items` (`externalId`)")
            }
        }

        /** v4: свои списки фильмов, сериалов и книг. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `media_lists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`glyph` TEXT NOT NULL, `kind` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `media_list_items` (`listId` INTEGER NOT NULL, `mediaId` INTEGER NOT NULL, " +
                        "`position` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`listId`, `mediaId`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_list_items_mediaId` ON `media_list_items` (`mediaId`)")
            }
        }

        /** v5: рост и обхваты в замерах, страницы заметок, тренировки с подходами. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("heightCm", "waistCm", "hipCm", "neckCm").forEach { db.execSQL("ALTER TABLE `body_metrics` ADD COLUMN `$it` REAL") }
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `parentId` INTEGER, `title` TEXT NOT NULL, " +
                        "`icon` TEXT NOT NULL, `cover` TEXT NOT NULL, `favorite` INTEGER NOT NULL, `archived` INTEGER NOT NULL, `sort` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pages_parentId` ON `pages` (`parentId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `page_blocks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `pageId` INTEGER NOT NULL, `pos` INTEGER NOT NULL, " +
                        "`type` TEXT NOT NULL, `text` TEXT NOT NULL, `checked` INTEGER NOT NULL, `media` TEXT NOT NULL, `indent` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_page_blocks_pageId` ON `page_blocks` (`pageId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `exercises` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `muscle` TEXT NOT NULL, " +
                        "`equipment` TEXT NOT NULL, `description` TEXT NOT NULL, `media` TEXT NOT NULL, `kind` INTEGER NOT NULL, `glyph` TEXT NOT NULL, " +
                        "`custom` INTEGER NOT NULL, `seedKey` TEXT, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercises_seedKey` ON `exercises` (`seedKey`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `workout_plans` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, " +
                        "`glyph` TEXT NOT NULL, `cover` TEXT NOT NULL, `daysMask` INTEGER NOT NULL, `sort` INTEGER NOT NULL, `archived` INTEGER NOT NULL, " +
                        "`seedKey` TEXT, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `plan_exercises` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `planId` INTEGER NOT NULL, `exerciseId` INTEGER NOT NULL, " +
                        "`pos` INTEGER NOT NULL, `sets` INTEGER NOT NULL, `reps` INTEGER NOT NULL, `weight` REAL NOT NULL, `seconds` INTEGER NOT NULL, " +
                        "`restSec` INTEGER NOT NULL, `repMin` INTEGER NOT NULL, `repMax` INTEGER NOT NULL, `weightStep` REAL NOT NULL, `maxSets` INTEGER NOT NULL, " +
                        "`note` TEXT NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_plan_exercises_planId` ON `plan_exercises` (`planId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `workout_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `planId` INTEGER, `title` TEXT NOT NULL, " +
                        "`day` INTEGER NOT NULL, `startedAt` INTEGER NOT NULL, `finishedAt` INTEGER, `note` TEXT NOT NULL, `feel` INTEGER NOT NULL, `workoutId` INTEGER)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_sessions_day` ON `workout_sessions` (`day`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `set_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, `exerciseId` INTEGER NOT NULL, " +
                        "`planExerciseId` INTEGER, `setIndex` INTEGER NOT NULL, `reps` INTEGER NOT NULL, `weight` REAL NOT NULL, `seconds` INTEGER NOT NULL, " +
                        "`done` INTEGER NOT NULL, `at` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_set_logs_sessionId` ON `set_logs` (`sessionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_set_logs_exerciseId` ON `set_logs` (`exerciseId`)")
            }
        }

        /** v6: архив рецептов. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `recipes` ADD COLUMN `archived` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v7: видео рецептов, свои праздники и дни, цвета праздников. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `recipes` ADD COLUMN `videos` TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `custom_days` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `month` INTEGER NOT NULL, " +
                        "`day` INTEGER NOT NULL, `year` INTEGER, `color` INTEGER NOT NULL, `note` TEXT NOT NULL, `kind` TEXT NOT NULL, `yearly` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE TABLE IF NOT EXISTS `holiday_marks` (`holidayId` TEXT NOT NULL, `color` INTEGER NOT NULL, PRIMARY KEY(`holidayId`))")
            }
        }

        /** v8: вес порции и готового блюда в рецептах. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `recipes` ADD COLUMN `portionGrams` REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `recipes` ADD COLUMN `dishGrams` REAL NOT NULL DEFAULT 0")
            }
        }

        /** v9: прогресс, дата начала, пересмотры и свои метки у фильмов, сериалов и книг. */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `media_items` ADD COLUMN `progress` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `media_items` ADD COLUMN `progressTotal` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `media_items` ADD COLUMN `startedDay` INTEGER")
                db.execSQL("ALTER TABLE `media_items` ADD COLUMN `rewatch` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `media_items` ADD COLUMN `tags` TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v10: пульс и сердечный индекс во взвешивании. */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `body_metrics` ADD COLUMN `heartRate` REAL")
                db.execSQL("ALTER TABLE `body_metrics` ADD COLUMN `cardiacIndex` REAL")
            }
        }

        fun create(context: Context): ExtraDb =
            Room.databaseBuilder(context, ExtraDb::class.java, "dasein_extra.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10).build()
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
    val bodyMetrics: List<BodyMetric> = emptyList(),
    val sleepAuto: List<SleepAuto> = emptyList(),
    val dayEnergy: List<DayEnergy> = emptyList(),
    val media: List<MediaItem> = emptyList(),
    val mediaLists: List<MediaList> = emptyList(),
    val mediaListItems: List<MediaListItem> = emptyList(),
    val pages: List<Page> = emptyList(),
    val pageBlocks: List<PageBlock> = emptyList(),
    val exercises: List<Exercise> = emptyList(),
    val workoutPlans: List<WorkoutPlan> = emptyList(),
    val planExercises: List<PlanExercise> = emptyList(),
    val workoutSessions: List<WorkoutSession> = emptyList(),
    val setLogs: List<SetLog> = emptyList(),
    val customDays: List<CustomDay> = emptyList(),
    val holidayMarks: List<HolidayMark> = emptyList(),
)

suspend fun ExtraDb.exportExtra(): ExtraBackup {
    val d = dao()
    val p = pages()
    val t = training()
    return ExtraBackup(
        d.allProducts(), d.allRecipes(), d.allIngredients(), d.allSteps(), d.allPlan(), d.allPresets(),
        d.allPresetItems(), d.allRepeats(), d.allShopping(), d.allHistory(), d.financeNotesNow(), d.allImportRecords(),
        d.bodyMetricsNow(), d.sleepAutoNow(), d.allDayEnergy(), d.allMedia(), d.allMediaLists(), d.allMediaListItems(),
        p.pagesNow(), p.allBlocks(), t.exercisesNow(), t.plansNow(), t.allPlanExercises(), t.sessionsNow(), t.allSets(),
        days().customDaysNow(), days().marksNow(),
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
        d.wipeBodyMetrics(); d.putBodyMetrics(b.bodyMetrics)
        d.wipeSleepAuto(); d.putSleepAuto(b.sleepAuto)
        d.wipeDayEnergy(); d.putDayEnergy(b.dayEnergy)
        d.wipeMedia(); d.putMedia(b.media)
        d.wipeMediaLists(); d.putMediaLists(b.mediaLists)
        d.wipeMediaListItems(); d.putMediaListItems(b.mediaListItems)
        // Старые копии без заметок и тренировок не стирают то, что уже есть.
        val p = pages()
        val t = training()
        if (b.pages.isNotEmpty() || b.pageBlocks.isNotEmpty()) { p.wipePages(); p.putPages(b.pages); p.wipeBlocks(); p.putBlocks(b.pageBlocks) }
        if (b.exercises.isNotEmpty() || b.workoutPlans.isNotEmpty()) {
            t.wipeExercises(); t.putExercises(b.exercises)
            t.wipePlans(); t.putPlans(b.workoutPlans)
            t.wipePlanExercises(); t.putPlanExercises(b.planExercises)
            t.wipeSessions(); t.putSessions(b.workoutSessions)
            t.wipeSets(); t.putSets(b.setLogs)
        }
        if (b.customDays.isNotEmpty() || b.holidayMarks.isNotEmpty()) {
            val dd = days()
            dd.wipeCustomDays(); dd.putCustomDays(b.customDays)
            dd.wipeMarks(); dd.putMarks(b.holidayMarks)
        }
    }
}
