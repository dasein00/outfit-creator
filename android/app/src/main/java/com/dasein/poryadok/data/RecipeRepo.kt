package com.dasein.poryadok.data

import androidx.room.withTransaction
import com.dasein.poryadok.Graph
import com.dasein.poryadok.logic.Cooking
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.Ingr
import com.dasein.poryadok.logic.Macros
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

@Serializable
private data class SeedProduct(
    val name: String, val category: String, val kcal: Double, val protein: Double, val fat: Double,
    val carbs: Double, val fiber: Double, val gramsPerUnit: Double,
)

@Serializable
private data class SeedIngredient(val product: String, val amount: Double, val unit: String, val grams: Double)

@Serializable
private data class SeedRecipe(
    val key: String, val name: String, val category: String, val meals: List<Int>, val tags: List<String>,
    val servings: Int, val prep: Int, val cook: Int, val difficulty: Int, val ingredients: List<SeedIngredient>,
    val steps: List<String>, val tips: String = "", val notes: String = "",
)

@Serializable
private data class Seed(val version: Int, val products: List<SeedProduct>, val recipes: List<SeedRecipe>)

fun RecipeIngredient.toIngr() = Ingr(name, amount, unit, grams, Macros(kcal100, protein100, fat100, carbs100, fiber100), shopCategory)

fun FoodProduct.per100() = Macros(kcal, protein, fat, carbs, fiber)

/** Рецепты, меню, покупки и связь с дневником питания. */
object RecipeRepo {
    private val x get() = Graph.extra
    private val seedLock = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    /** Загружает базу продуктов и ПП-рецептов. Повторный запуск добавляет только новые рецепты. */
    suspend fun seed() = seedLock.withLock {
        val text = Graph.app.assets.open("recipes/seed.json").bufferedReader().use { it.readText() }
        val seed = json.decodeFromString(Seed.serializer(), text)
        val existing = x.productsNow().associateBy { it.seedKey ?: "" }
        val ids = HashMap<String, FoodProduct>()
        seed.products.forEach { p ->
            val prev = existing[p.name]
            val prod = prev ?: FoodProduct(
                name = p.name, category = p.category, kcal = p.kcal, protein = p.protein, fat = p.fat,
                carbs = p.carbs, fiber = p.fiber, gramsPerUnit = p.gramsPerUnit, seedKey = p.name,
            ).let { it.copy(id = x.upsertProduct(it)) }
            ids[p.name] = prod
        }
        val have = x.recipeSeedKeys().toSet()
        val now = System.currentTimeMillis()
        Graph.extraDb.withTransaction {
            seed.recipes.filter { it.key !in have }.forEach { s ->
                val id = x.upsertRecipe(
                    Recipe(
                        name = s.name, category = s.category, meals = s.meals.joinToString(","), tags = s.tags.joinToString(","),
                        servings = s.servings, prepMin = s.prep, cookMin = s.cook, difficulty = s.difficulty,
                        tips = s.tips, notes = s.notes, seedKey = s.key, createdAt = now,
                    )
                )
                x.insertIngredients(s.ingredients.mapIndexed { i, ing ->
                    val p = ids.getValue(ing.product)
                    RecipeIngredient(
                        recipeId = id, pos = i, productId = p.id, name = p.name, amount = ing.amount, unit = ing.unit,
                        grams = ing.grams, kcal100 = p.kcal, protein100 = p.protein, fat100 = p.fat, carbs100 = p.carbs,
                        fiber100 = p.fiber, shopCategory = p.category,
                    )
                })
                x.insertSteps(s.steps.mapIndexed { i, t -> RecipeStep(recipeId = id, pos = i, text = t) })
            }
        }
    }

    fun macros(ingredients: List<RecipeIngredient>, servings: Int): Macros =
        Cooking.perServing(ingredients.map { it.toIngr() }, servings)

    suspend fun perServing(recipeId: Long): Macros? {
        val r = x.recipeNow(recipeId) ?: return null
        return macros(x.ingredientsOfNow(recipeId), r.servings)
    }

    /** Сохраняет рецепт целиком и обновляет КБЖУ в ещё не съеденных блюдах меню. */
    suspend fun saveRecipe(r: Recipe, ingredients: List<RecipeIngredient>, steps: List<String>): Long {
        var id = r.id
        Graph.extraDb.withTransaction {
            id = x.upsertRecipe(if (r.createdAt == 0L) r.copy(createdAt = System.currentTimeMillis()) else r).let { if (r.id != 0L) r.id else it }
            x.deleteIngredientsOf(id)
            x.insertIngredients(ingredients.mapIndexed { i, ing -> ing.copy(id = 0, recipeId = id, pos = i) })
            x.deleteStepsOf(id)
            x.insertSteps(steps.filter { it.isNotBlank() }.mapIndexed { i, t -> RecipeStep(recipeId = id, pos = i, text = t.trim()) })
            val m = macros(ingredients, r.servings)
            x.plannedOf(id).forEach { p ->
                x.upsertPlan(p.copy(title = r.name, kcal = m.kcal, protein = m.protein, fat = m.fat, carbs = m.carbs))
            }
        }
        return id
    }

    suspend fun duplicate(id: Long): Long? {
        val r = x.recipeNow(id) ?: return null
        val ing = x.ingredientsOfNow(id)
        val steps = x.stepsOfNow(id).map { it.text }
        return saveRecipe(r.copy(id = 0, name = r.name + " (копия)", custom = true, seedKey = null, favorite = false, createdAt = 0), ing, steps)
    }

    suspend fun deleteRecipe(id: Long) {
        Graph.extraDb.withTransaction {
            x.deleteIngredientsOf(id)
            x.deleteStepsOf(id)
            x.repeatsNow().filter { it.recipeId == id }.forEach { rep ->
                x.deleteRepeatFuture(rep.id, Dates.today())
                x.deleteRepeat(rep.id)
            }
            x.deleteRecipe(id)
        }
    }

    // ---------- Меню ----------

    suspend fun addToPlan(day: Long, meal: Int, recipeId: Long, servings: Double = 1.0, timeMin: Int? = null, repeatId: Long? = null): Long? {
        val r = x.recipeNow(recipeId) ?: return null
        val m = macros(x.ingredientsOfNow(recipeId), r.servings)
        return x.upsertPlan(
            MealPlanItem(
                day = day, meal = meal, recipeId = recipeId, title = r.name, servings = servings,
                kcal = m.kcal, protein = m.protein, fat = m.fat, carbs = m.carbs, timeMin = timeMin, repeatId = repeatId,
                pos = (System.currentTimeMillis() % 100_000).toInt(),
            )
        )
    }

    /** «Съедено»: блюдо из плана переходит в дневник питания. */
    suspend fun markEaten(p: MealPlanItem, eatenServings: Double = p.servings) {
        val k = eatenServings
        val foodId = Graph.dao.upsertFood(
            FoodEntry(
                day = p.day, meal = p.meal, name = p.title + if (k != 1.0) " ×${Cooking.amount(k)}" else "",
                kcal = (p.kcal * k).roundToInt(), protein = round1(p.protein * k), fat = round1(p.fat * k), carbs = round1(p.carbs * k),
            )
        )
        x.upsertPlan(p.copy(status = PlanStatus.EATEN, foodEntryId = foodId))
    }

    /** Отменить «съедено»: запись питания удаляется, блюдо снова в плане. */
    suspend fun unmarkEaten(p: MealPlanItem) {
        p.foodEntryId?.let { Graph.dao.deleteFoodById(it) }
        x.upsertPlan(p.copy(status = PlanStatus.PLANNED, foodEntryId = null))
    }

    suspend fun skip(p: MealPlanItem) = x.upsertPlan(p.copy(status = PlanStatus.SKIPPED))

    /** Удалить из меню. Повторяющиеся блюда помечаются пропущенными, чтобы не вернулись. */
    suspend fun removeFromPlan(p: MealPlanItem) {
        if (p.foodEntryId != null && p.status == PlanStatus.EATEN) Graph.dao.deleteFoodById(p.foodEntryId)
        if (p.repeatId != null) x.upsertPlan(p.copy(status = PlanStatus.SKIPPED, foodEntryId = null)) else x.deletePlan(p)
    }

    /** Приготовлено: запись в историю и по желанию — в дневник питания. */
    suspend fun cook(recipeId: Long, servings: Double, eaten: Double, meal: Int, addToFood: Boolean, day: Long = Dates.today()) {
        val r = x.recipeNow(recipeId) ?: return
        val m = macros(x.ingredientsOfNow(recipeId), r.servings) * eaten
        val foodId = if (addToFood && eaten > 0) Graph.dao.upsertFood(
            FoodEntry(day = day, meal = meal, name = r.name, kcal = m.kcal.roundToInt(), protein = round1(m.protein), fat = round1(m.fat), carbs = round1(m.carbs))
        ) else null
        x.upsertHistory(
            CookingLog(
                at = System.currentTimeMillis(), day = day, recipeId = recipeId, recipeName = r.name, servings = servings, eaten = eaten,
                kcal = m.kcal, protein = m.protein, fat = m.fat, carbs = m.carbs, meal = meal, foodEntryId = foodId,
            )
        )
    }

    suspend fun historyToFood(h: CookingLog) {
        if (h.foodEntryId != null) return
        val id = Graph.dao.upsertFood(
            FoodEntry(day = h.day, meal = h.meal, name = h.recipeName, kcal = h.kcal.roundToInt(), protein = round1(h.protein), fat = round1(h.fat), carbs = round1(h.carbs))
        )
        x.upsertHistory(h.copy(foodEntryId = id))
    }

    /** Создаёт недостающие блюда по правилам повтора на [horizon] дней вперёд. */
    suspend fun materializeRepeats(horizon: Int = 35) {
        val today = Dates.today()
        x.repeatsNow().forEach { rep ->
            val from = maxOf(rep.fromDay, today)
            val to = minOf(rep.toDay ?: Long.MAX_VALUE, today + horizon)
            var d = from
            while (d <= to) {
                val dow = Dates.day(d).dayOfWeek.value - 1
                if (rep.daysMask and (1 shl dow) != 0 && x.repeatCount(rep.id, d) == 0) {
                    addToPlan(d, rep.meal, rep.recipeId, rep.servings, rep.timeMin, rep.id)
                }
                d++
            }
        }
    }

    suspend fun saveRepeat(rep: MealRepeat) {
        val id = x.upsertRepeat(rep).let { if (rep.id != 0L) rep.id else it }
        x.deleteRepeatFuture(id, Dates.today())
        materializeRepeats()
    }

    suspend fun deleteRepeat(id: Long) {
        x.deleteRepeatFuture(id, Dates.today())
        x.deleteRepeat(id)
    }

    // ---------- Шаблоны ----------

    suspend fun presetFromDays(name: String, from: Long, days: Int): Long {
        val items = x.planNow(from, from + days - 1).filter { it.recipeId != null && it.status != PlanStatus.SKIPPED }
        val id = x.upsertPreset(MealPreset(name = name, days = days, createdAt = System.currentTimeMillis()))
        x.insertPresetItems(items.map {
            MealPresetItem(presetId = id, dayIndex = if (days == 7) Dates.day(it.day).dayOfWeek.value - 1 else 0, meal = it.meal, recipeId = it.recipeId!!, servings = it.servings, timeMin = it.timeMin)
        })
        return id
    }

    suspend fun duplicatePreset(p: MealPreset) {
        val id = x.upsertPreset(p.copy(id = 0, name = p.name + " (копия)", createdAt = System.currentTimeMillis()))
        x.insertPresetItems(x.presetItemsOf(p.id).map { it.copy(id = 0, presetId = id) })
    }

    suspend fun deletePreset(id: Long) {
        x.deletePresetItemsOf(id)
        x.deletePreset(id)
    }

    /** Применяет шаблон к периоду. Шаблон недели раскладывается по дням недели, шаблон дня — на каждый день. */
    suspend fun applyPreset(presetId: Long, from: Long, to: Long, replace: Boolean) {
        val preset = x.presetItemsOf(presetId)
        val days = x.allPresets().firstOrNull { it.id == presetId }?.days ?: 1
        if (replace) x.clearPlanned(from, to)
        var d = from
        while (d <= to) {
            val dow = Dates.day(d).dayOfWeek.value - 1
            preset.filter { days == 1 || it.dayIndex == dow }.forEach { addToPlan(d, it.meal, it.recipeId, it.servings, it.timeMin) }
            d++
        }
    }

    // ---------- Автоподбор ----------

    suspend fun autofill(from: Long, to: Long, params: Cooking.AutoParams, replace: Boolean): Int {
        val recipes = x.recipesNow()
        val ingr = x.ingredientsNow().groupBy { it.recipeId }
        val cands = recipes.map { r ->
            val list = ingr[r.id].orEmpty()
            Cooking.Candidate(
                r.id, com.dasein.poryadok.logic.MealType.parse(r.meals), macros(list, r.servings), r.prepMin + r.cookMin, r.category,
                list.map { it.name }.toSet(),
            )
        }
        if (replace) x.clearPlanned(from, to)
        var added = 0
        var prev = emptySet<Long>()
        var d = from
        while (d <= to) {
            val existingMeals = if (replace) emptySet() else x.planNow(d, d).filter { it.status != PlanStatus.SKIPPED }.map { it.meal }.toSet()
            val picks = Cooking.autofill(cands, params.copy(meals = params.meals.filter { it !in existingMeals }), seed = d.toInt(), avoid = prev)
            picks.forEach { (meal, rid, serv) -> addToPlan(d, meal, rid, serv); added++ }
            prev = picks.map { it.second }.toSet()
            d++
        }
        return added
    }

    // ---------- Покупки ----------

    /** Пересобирает покупки из меню за период. Ручные позиции и отметки «куплено» сохраняются. */
    suspend fun shoppingFromMenu(from: Long, to: Long): Int {
        val plan = x.planNow(from, to).filter { it.status == PlanStatus.PLANNED && it.recipeId != null }
        val recipes = x.recipesNow().associateBy { it.id }
        val all = mutableListOf<Ingr>()
        plan.forEach { p ->
            val r = recipes[p.recipeId] ?: return@forEach
            val factor = p.servings / r.servings.coerceAtLeast(1)
            all += Cooking.scale(x.ingredientsOfNow(r.id).map { it.toIngr() }, factor)
        }
        val lines = Cooking.mergeShopping(all)
        val bought = x.shoppingNow().filter { it.fromMenu && it.bought }.map { it.key }.toSet()
        Graph.extraDb.withTransaction {
            x.clearMenuShopping()
            lines.filter { it.key !in bought }.forEach {
                x.upsertShopping(ShoppingItem(name = it.name, amount = it.amount, unit = it.unit, category = it.category, fromMenu = true, key = it.key))
            }
        }
        return lines.size
    }

    /** Добавляет ингредиенты одного рецепта; одинаковые позиции складываются. */
    suspend fun addToShopping(recipeId: Long, factor: Double) {
        val r = x.recipeNow(recipeId) ?: return
        val lines = Cooking.mergeShopping(Cooking.scale(x.ingredientsOfNow(recipeId).map { it.toIngr() }, factor))
        val existing = x.shoppingNow().filter { !it.bought }
        lines.forEach { l ->
            val same = existing.firstOrNull { it.key == l.key && !it.fromMenu }
            if (same != null) {
                val merged = Cooking.mergeShopping(
                    listOf(
                        Ingr(same.name, same.amount, same.unit, 0.0, Macros(), same.category),
                        Ingr(l.name, l.amount, l.unit, 0.0, Macros(), l.category),
                    )
                ).first()
                x.upsertShopping(same.copy(amount = merged.amount, unit = merged.unit))
            } else {
                x.upsertShopping(ShoppingItem(name = l.name, amount = l.amount, unit = l.unit, category = l.category, key = l.key, note = r.name))
            }
        }
    }

    private fun round1(v: Double) = (v * 10).roundToInt() / 10.0
}
