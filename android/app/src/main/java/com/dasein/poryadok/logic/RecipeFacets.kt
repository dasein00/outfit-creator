package com.dasein.poryadok.logic

/**
 * Группы фильтров каталога рецептов, как в магазинах: нажимаете «Мясо» — выбираете «Курица».
 * Всё определяется по названиям ингредиентов, белку и тегам, без ручной разметки.
 */
object RecipeFacets {
    /** Основа блюда: ключ, подпись и слова в названиях ингредиентов. */
    data class Base(val key: String, val label: String, val words: List<String>)

    val BASES = listOf(
        Base("chicken", "Курица", listOf("куриц", "курин", "цыпл", "бройлер")),
        Base("turkey", "Индейка", listOf("индейк", "индюш")),
        Base("pork", "Свинина", listOf("свин", "бекон", "грудинк", "корейк", "сало")),
        Base("beef", "Говядина", listOf("говяд", "телят")),
        Base("lamb", "Баранина", listOf("баран", "ягнят")),
        Base("mince", "Фарш", listOf("фарш")),
        Base("sausage", "Колбаса и сосиски", listOf("колбас", "сосиск", "сардельк", "ветчин")),
        Base("liver", "Субпродукты", listOf("печень", "печён", "сердечк", "желудк", "язык")),
        Base("fish", "Рыба", listOf("рыб", "минтай", "горбуш", "лосос", "сёмг", "семг", "треск", "хек", "скумбри", "сельд", "тунец", "форел", "пикш", "карп", "судак", "килек", "шпрот", "сардин", "мойв", "кета")),
        Base("seafood", "Морепродукты", listOf("кревет", "кальмар", "мидии", "мидий", "краб", "осьминог", "морепродукт")),
        Base("eggs", "Яйца", listOf("яйц", "яйцо")),
        Base("curd", "Творог и сыр", listOf("творог", "сыр", "брынз", "моцарел", "рикотт")),
        Base("mushroom", "Грибы", listOf("гриб", "шампиньон", "вешенк", "опят", "лисичк")),
        Base("legumes", "Бобовые", listOf("фасол", "нут", "чечевиц", "горох", "соя ", "тофу", "бобы")),
    )

    private val MEAT = listOf("chicken", "turkey", "pork", "beef", "lamb", "mince", "sausage", "liver")
    private val ANIMAL_EXTRA = listOf("молок", "сливк", "сметан", "кефир", "йогурт", "масло сливоч", "мёд", "мед ", "ряженк", "майонез", "желатин")

    fun norm(s: String) = s.lowercase().replace('ё', 'е')

    private fun has(names: List<String>, b: Base) = names.any { n -> b.words.any { norm(it) in n } }

    /** Основы, которые есть в блюде. */
    fun bases(ingredients: List<String>): Set<String> {
        val names = ingredients.map { norm(it) + " " }
        return BASES.filter { has(names, it) }.map { it.key }.toSet()
    }

    fun isVegetarian(ingredients: List<String>): Boolean {
        val b = bases(ingredients)
        return b.none { it in MEAT || it == "fish" || it == "seafood" }
    }

    fun isVegan(ingredients: List<String>): Boolean {
        if (!isVegetarian(ingredients)) return false
        val b = bases(ingredients)
        if ("eggs" in b || "curd" in b) return false
        val names = ingredients.map { norm(it) + " " }
        return names.none { n -> ANIMAL_EXTRA.any { norm(it) in n } }
    }

    /** Варианты фильтра «Мясо и основа». */
    val BASE_OPTIONS: List<Pair<String, String>> =
        listOf("meat" to "Любое мясо") + BASES.map { it.key to it.label } + listOf("veg" to "Вегетарианское", "vegan" to "Веганское")

    fun matchesBase(key: String, ingredients: List<String>): Boolean = when (key) {
        "" -> true
        "meat" -> bases(ingredients).any { it in MEAT }
        "veg" -> isVegetarian(ingredients)
        "vegan" -> isVegan(ingredients)
        else -> key in bases(ingredients)
    }

    /** Белок на порцию. */
    val PROTEIN_OPTIONS = listOf("high" to "Высокобелковые (от 25 г)", "mid" to "Средний белок (10–25 г)", "low" to "Низкобелковые (до 10 г)")

    fun matchesProtein(key: String, protein: Double): Boolean = when (key) {
        "high" -> protein >= 25
        "mid" -> protein >= 10 && protein < 25
        "low" -> protein < 10
        else -> true
    }

    val DIFFICULTY_OPTIONS = listOf("1" to "Легко", "2" to "Средне", "3" to "Сложно")

    fun matchesDifficulty(key: String, difficulty: Int): Boolean =
        key.isEmpty() || difficulty.coerceIn(1, 3) == key.toInt()
}
