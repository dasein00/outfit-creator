package com.dasein.poryadok.logic

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Приёмы пищи. Индексы совпадают с полем meal у записей питания:
 * 0–3 были в приложении раньше, 4 и 5 добавлены для рецептов и меню.
 */
object MealType {
    const val BREAKFAST = 0
    const val LUNCH = 1
    const val DINNER = 2
    const val SNACK = 3
    const val BRUNCH = 4
    const val AFTERNOON = 5

    val names = listOf("Завтрак", "Обед", "Ужин", "Перекус", "Второй завтрак", "Полдник")

    /** Порядок в течение дня. */
    val order = listOf(BREAKFAST, BRUNCH, LUNCH, AFTERNOON, DINNER, SNACK)

    /** Время по умолчанию, минуты от полуночи. */
    val defaultTime = mapOf(BREAKFAST to 8 * 60, BRUNCH to 11 * 60, LUNCH to 13 * 60 + 30, AFTERNOON to 16 * 60 + 30, DINNER to 19 * 60, SNACK to 21 * 60)

    fun name(i: Int): String = names.getOrElse(i) { "Приём пищи" }

    fun parse(csv: String): List<Int> = csv.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in names.indices }
}

val RECIPE_CATEGORIES = listOf("Завтраки", "Обеды", "Ужины", "Перекусы", "Супы", "Салаты", "Гарниры", "Десерты", "Напитки", "Выпечка", "Заготовки")
val SHOP_CATEGORIES = listOf("Овощи", "Фрукты", "Мясо", "Рыба", "Молочные продукты", "Крупы", "Бакалея", "Заморозка", "Другое")
val UNITS = listOf("г", "кг", "мл", "л", "шт", "ст.л.", "ч.л.", "стакан", "щепотка", "по вкусу")
val DIFFICULTY = listOf("Легко", "Средне", "Сложно")

data class Macros(val kcal: Double = 0.0, val protein: Double = 0.0, val fat: Double = 0.0, val carbs: Double = 0.0, val fiber: Double = 0.0) {
    operator fun plus(o: Macros) = Macros(kcal + o.kcal, protein + o.protein, fat + o.fat, carbs + o.carbs, fiber + o.fiber)
    operator fun times(k: Double) = Macros(kcal * k, protein * k, fat * k, carbs * k, fiber * k)
    operator fun div(k: Double) = if (k == 0.0) this else times(1 / k)
}

/** Ингредиент для расчётов — без привязки к базе данных. */
data class Ingr(
    val name: String,
    val amount: Double,
    val unit: String,
    val grams: Double,
    val per100: Macros,
    val shopCategory: String = "Другое",
)

object Cooking {
    /** Сколько граммов в количестве. Для штук нужен вес одной штуки. */
    fun grams(amount: Double, unit: String, gramsPerUnit: Double = 0.0): Double = when (unit) {
        "г", "мл" -> amount
        "кг", "л" -> amount * 1000
        "шт" -> amount * (if (gramsPerUnit > 0) gramsPerUnit else 100.0)
        "ст.л." -> amount * 15
        "ч.л." -> amount * 5
        "стакан" -> amount * 200
        "щепотка" -> amount * 1
        else -> 0.0
    }

    fun macrosOf(i: Ingr): Macros = i.per100 * (i.grams / 100.0)

    fun total(list: List<Ingr>): Macros = list.fold(Macros()) { acc, i -> acc + macrosOf(i) }

    fun perServing(list: List<Ingr>, servings: Int): Macros = total(list) / servings.coerceAtLeast(1).toDouble()

    fun scale(list: List<Ingr>, factor: Double): List<Ingr> =
        list.map { it.copy(amount = if (it.unit == "по вкусу") it.amount else it.amount * factor, grams = it.grams * factor) }

    /** Красиво печатает количество: 1.5 → «1,5», 0.333 → «0,33», 120.0 → «120». */
    fun amount(v: Double): String {
        if (v == 0.0) return "0"
        val r = if (v >= 10) v.roundToInt().toDouble() else (v * 100).roundToInt() / 100.0
        return if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString().trimEnd('0').trimEnd('.').replace('.', ',')
    }

    fun label(i: Ingr): String = when (i.unit) {
        "по вкусу" -> "по вкусу"
        else -> amount(i.amount) + " " + i.unit
    }

    /** Строка в список покупок. */
    data class ShopLine(val key: String, val name: String, val amount: Double, val unit: String, val category: String)

    private fun baseUnit(unit: String): Pair<String, Double> = when (unit) {
        "кг" -> "г" to 1000.0
        "л" -> "мл" to 1000.0
        else -> unit to 1.0
    }

    fun key(name: String, unit: String): String = name.trim().lowercase().replace('ё', 'е') + "|" + baseUnit(unit).first

    /** Объединяет одинаковые ингредиенты из разных блюд: 200 г курицы + 0,3 кг курицы = 500 г. */
    fun mergeShopping(items: List<Ingr>): List<ShopLine> =
        items.filter { it.unit != "по вкусу" && it.amount > 0 }
            .groupBy { key(it.name, it.unit) }
            .map { (k, list) ->
                val (u, _) = baseUnit(list.first().unit)
                val sum = list.sumOf { it.amount * baseUnit(it.unit).second }
                val (unit, amount) = when {
                    u == "г" && sum >= 1000 -> "кг" to sum / 1000
                    u == "мл" && sum >= 1000 -> "л" to sum / 1000
                    else -> u to sum
                }
                ShopLine(k, list.first().name.trim(), amount, unit, list.first().shopCategory)
            }
            .sortedWith(compareBy({ SHOP_CATEGORIES.indexOf(it.category).let { i -> if (i < 0) 99 else i } }, { it.name.lowercase() }))

    /** Замены ингредиентов: что можно взять вместо. */
    val SWAPS: Map<String, List<String>> = mapOf(
        "Куриное филе" to listOf("Филе индейки", "Треска", "Тофу"),
        "Филе индейки" to listOf("Куриное филе", "Треска", "Тофу"),
        "Говядина постная" to listOf("Филе индейки", "Куриное филе", "Чечевица красная"),
        "Рис бурый" to listOf("Гречка", "Булгур", "Картофель"),
        "Рис белый" to listOf("Рис бурый", "Гречка", "Булгур"),
        "Гречка" to listOf("Булгур", "Киноа", "Рис бурый"),
        "Булгур" to listOf("Гречка", "Киноа", "Рис бурый"),
        "Паста цельнозерновая" to listOf("Гречневая лапша", "Булгур", "Кабачок"),
        "Сметана 10%" to listOf("Греческий йогурт", "Кефир 1%"),
        "Сливки 10%" to listOf("Молоко 1,5%", "Греческий йогурт"),
        "Творог 5%" to listOf("Творог 2%", "Греческий йогурт", "Тофу"),
        "Сахар" to listOf("Мёд", "Эритрит", "Банан"),
        "Мука пшеничная" to listOf("Мука цельнозерновая", "Овсяная мука", "Мука рисовая"),
        "Картофель" to listOf("Батат", "Цветная капуста", "Гречка"),
        "Лосось" to listOf("Форель", "Скумбрия", "Треска"),
        "Треска" to listOf("Минтай", "Хек", "Куриное филе"),
        "Майонез" to listOf("Греческий йогурт", "Сметана 10%"),
        "Хлеб цельнозерновой" to listOf("Хлебцы цельнозерновые", "Лаваш тонкий"),
        "Молоко 1,5%" to listOf("Миндальное молоко", "Кефир 1%"),
        "Яйцо куриное" to listOf("Яичный белок", "Тофу"),
    )

    /** Кандидат для автоматического меню: одна порция рецепта. */
    data class Candidate(
        val id: Long,
        val meals: List<Int>,
        val perServing: Macros,
        val minutes: Int,
        val category: String,
        val ingredients: Set<String>,
    )

    data class AutoParams(
        val kcal: Int,
        val meals: List<Int>,
        val minProtein: Int = 0,
        val maxMinutes: Int = 0,
        val exclude: Set<String> = emptySet(),
        val prefer: Set<String> = emptySet(),
        val categories: Set<String> = emptySet(),
    )

    /** Доля калорий дня на приём пищи. */
    fun shareOf(meal: Int): Double = when (meal) {
        MealType.BREAKFAST -> 0.25
        MealType.BRUNCH -> 0.10
        MealType.LUNCH -> 0.35
        MealType.AFTERNOON -> 0.10
        MealType.DINNER -> 0.25
        else -> 0.10
    }

    private fun contains(set: Set<String>, words: Set<String>): Boolean =
        words.any { w -> set.any { it.contains(w, ignoreCase = true) } }

    /**
     * Подбирает по одному рецепту на каждый приём пищи так, чтобы калории были близки к цели,
     * а белка — не меньше минимума. [seed] даёт разнообразие между днями.
     * Возвращает пары «приём пищи → id рецепта и число порций».
     */
    fun autofill(all: List<Candidate>, p: AutoParams, seed: Int = 0, avoid: Set<Long> = emptySet()): List<Triple<Int, Long, Double>> {
        val usable = all.filter { c ->
            (p.maxMinutes <= 0 || c.minutes <= p.maxMinutes) &&
                !contains(c.ingredients, p.exclude.map { it.trim() }.filter { it.isNotEmpty() }.toSet()) &&
                (p.categories.isEmpty() || c.category in p.categories) && c.perServing.kcal > 0
        }
        val meals = p.meals.sortedBy { MealType.order.indexOf(it) }
        val shares = meals.map { shareOf(it) }
        val shareSum = shares.sum().takeIf { it > 0 } ?: 1.0
        val proteinPerKcal = if (p.kcal > 0) p.minProtein.toDouble() / p.kcal else 0.0
        val used = mutableSetOf<Long>()
        val out = mutableListOf<Triple<Int, Long, Double>>()
        meals.forEachIndexed { idx, meal ->
            val target = p.kcal * shares[idx] / shareSum
            val pool = usable.filter { meal in it.meals }.ifEmpty { usable }
            if (pool.isEmpty()) return@forEachIndexed
            val scored = pool.map { c ->
                val servings = (target / c.perServing.kcal).let { s -> (s * 2).roundToInt().coerceIn(1, 4) / 2.0 }
                val kcal = c.perServing.kcal * servings
                var score = abs(kcal - target) / target.coerceAtLeast(1.0)
                val needProtein = target * proteinPerKcal
                if (needProtein > 0) score += ((needProtein - c.perServing.protein * servings).coerceAtLeast(0.0) / needProtein) * 0.8
                if (contains(c.ingredients, p.prefer)) score -= 0.15
                if (c.id in used) score += 0.6
                if (c.id in avoid) score += 0.25
                // Небольшой детерминированный разброс, чтобы дни отличались.
                score += (((c.id * 31 + seed * 17 + meal * 7) % 13) / 13.0) * 0.12
                Triple(c, servings, score)
            }.sortedBy { it.third }
            val best = scored.first()
            used += best.first.id
            out += Triple(meal, best.first.id, best.second)
        }
        return out
    }
}
