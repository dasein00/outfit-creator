package com.dasein.poryadok.logic

/**
 * Без интернета: по названию, продуктам и тексту рецепта (подпись поста, речь из видео) угадывает остальные пункты
 * карточки — категорию, приёмы пищи, сложность, время подготовки и готовки, теги (кухня, «быстро», «вегетарианские»…),
 * советы и замены. ИИ, если он есть, отвечает на это сам; эти правила подстраховывают и работают офлайн.
 */
object RecipeClassify {
    data class Guess(
        val title: String?,
        val category: String,
        val meals: List<Int>,
        val difficulty: Int,
        val prepMin: Int,
        val cookMin: Int,
        val tags: List<String>,
        val tips: String,
        val swaps: String,
    )

    private fun norm(s: String) = s.lowercase().replace('ё', 'е')

    /** Категории по словам в названии и начале текста. Порядок — приоритет при равенстве. */
    private val CATEGORY_WORDS: List<Pair<String, List<String>>> = listOf(
        "Заготовки" to listOf("на зиму", "заготовк", "варенье", "джем", "конфитюр", "засолк", "соленья", "маринованн", "консервир", "закатк", "аджика", "лечо"),
        "Напитки" to listOf("смузи", "коктейл", "лимонад", "компот", "морс", "какао", "латте", "капучино", "кофе", "милкшейк", "напиток", "кисель", "квас", "чай "),
        "Супы" to listOf("суп", "борщ", "щи ", "солянк", "бульон", "уха", "рассольник", "окрошк", "харчо", "гаспачо", "рамен", "том ям", "лагман", "шурпа", "свекольник", "минестроне"),
        "Салаты" to listOf("салат", "винегрет", "оливье", "цезарь", "капрезе"),
        "Десерты" to listOf(
            "десерт", "торт", "пирожн", "чизкейк", "тирамису", "мусс", "пудинг", "панна-кот", "панакот", "мороженое", "конфет", "трюфел",
            "брауни", "суфле", "зефир", "пастила", "крем-брюле", "мармелад", "безе", "меренг", "эклер", "макарун", "сладост",
        ),
        "Выпечка" to listOf(
            "пирог", "пирожк", "булочк", "булки", "хлеб", "печенье", "кекс", "маффин", "капкейк", "круассан", "слойк", "штрудель", "пицц",
            "хачапури", "лепешк", "фокачч", "багет", "ватрушк", "шарлотк", "кулич", "чиабатт", "самса", "киш ", "выпечк",
        ),
        "Завтраки" to listOf(
            "завтрак", "омлет", "яичниц", "скрэмбл", "шакшук", "сырник", "каша", "овсянк", "гранол", "мюсли", "блин", "оладь", "панкейк",
            "тост", "сэндвич", "бутерброд", "брускетт", "запеканк", "пашот", "вафл",
        ),
        "Перекусы" to listOf("перекус", "закуск", "снек", "батончик", "хумус", "чипс", "канапе", "тарталетк", "намазк", "паштет", "гуакамоле", "попкорн", "наггетс", "нагетс"),
        "Гарниры" to listOf("гарнир", "пюре", "булгур", "кускус", "киноа", "гречк", "отварной рис", "рис на гарнир", "овощи на гриле", "запеченные овощи", "запеченный картофель", "картофель по-деревенски"),
    )

    /** Кухни мира: тег «… кухня». */
    private val CUISINES: List<Pair<String, List<String>>> = listOf(
        "итальянская" to listOf("паста", "спагетти", "карбонар", "болоньез", "лазань", "ризотто", "пицц", "тирамису", "брускетт", "фокачч", "капрезе", "минестроне", "равиол", "ньокки", "панна-кот", "по-итальянски"),
        "грузинская" to listOf("хачапури", "хинкали", "харчо", "чахохбили", "сациви", "пхали", "лобио", "аджапсандал", "чкмерули", "по-грузински"),
        "японская" to listOf("суши", "ролл", "рамен", "мисо", "терияки", "темпур", "онигири", "удон", "якитори", "по-японски"),
        "китайская" to listOf("кисло-сладк", "гунбао", "баоцзы", "дим-сам", "по-китайски", "вок "),
        "тайская" to listOf("том ям", "пад тай", "по-тайски", "тайск"),
        "мексиканская" to listOf("тако", "буррито", "кесадиль", "начос", "гуакамоле", "фахитас", "энчилад", "по-мексикански"),
        "узбекская" to listOf("плов", "лагман", "самса", "манты", "шурпа", "нарын", "по-узбекски"),
        "французская" to listOf("киш ", "рататуй", "крем-брюле", "круассан", "жюльен", "конфи", "бургиньон", "по-французски"),
        "индийская" to listOf("карри", "масала", "тикка", "панир", "бирьяни", "по-индийски"),
        "американская" to listOf("бургер", "панкейк", "брауни", "хот-дог", "барбекю", "мак энд чиз", "коул слоу"),
        "испанская" to listOf("паэлья", "гаспачо", "чуррос", "по-испански"),
        "греческая" to listOf("мусака", "цацики", "гирос", "сувлаки", "греческий салат", "по-гречески"),
        "корейская" to listOf("кимчи", "бибимбап", "пулькоги", "по-корейски"),
        "восточная" to listOf("шаурм", "шаверм", "долма", "кебаб", "люля", "пахлав", "фалафел", "хумус"),
    )

    /** Сказано прямо: «на завтрак», «идеальный ужин». */
    private val MEAL_WORDS = listOf(
        MealType.BREAKFAST to listOf("на завтрак", "к завтраку", "завтрак"),
        MealType.LUNCH to listOf("на обед", "к обеду", "обед"),
        MealType.DINNER to listOf("на ужин", "к ужину", "ужин"),
        MealType.SNACK to listOf("перекус"),
        MealType.AFTERNOON to listOf("полдник", "к чаю", "к кофе"),
    )

    private val DEFAULT_MEALS = mapOf(
        "Завтраки" to listOf(MealType.BREAKFAST, MealType.BRUNCH),
        "Супы" to listOf(MealType.LUNCH),
        "Салаты" to listOf(MealType.LUNCH, MealType.DINNER),
        "Десерты" to listOf(MealType.AFTERNOON, MealType.SNACK),
        "Выпечка" to listOf(MealType.BREAKFAST, MealType.AFTERNOON),
        "Перекусы" to listOf(MealType.SNACK, MealType.AFTERNOON),
        "Напитки" to listOf(MealType.BREAKFAST, MealType.SNACK),
        "Гарниры" to listOf(MealType.LUNCH, MealType.DINNER),
        "Обеды" to listOf(MealType.LUNCH, MealType.DINNER),
        "Ужины" to listOf(MealType.DINNER),
        WORLD_CATEGORY to listOf(MealType.LUNCH, MealType.DINNER),
        "Заготовки" to listOf(MealType.LUNCH, MealType.DINNER),
    )

    private val SWEET = listOf("сахар", "мед", "сироп", "сгущ", "шоколад", "варенье", "джем")
    private val TIP_WORDS = listOf("совет", "лайфхак", "секрет", "важно", "не забуд", "храни", "подавать", "подаем", "подавайте", "по желанию", "чтобы не", "главное")
    private val SWAP_WORDS = listOf("замен", "вместо", "можно взять", "подойдет и", "подойдут и")
    private val TITLE_IN_SPEECH = Regex("""(?:сегодня\s+)?(?:готовим|приготовим|рецепт|делаем|сделаем|покажу,? как приготовить)\s+([\p{L}\- ]{3,40}?)(?=[.,!?:]|\s+(?:на|для|из|с|в|за|по|который|которые|это)\s|$)""", RegexOption.IGNORE_CASE)

    private fun hits(text: String, words: List<String>) = words.count { it in text }

    /** Категория блюда по названию (весомее) и началу текста. */
    fun category(title: String?, text: String, ingredients: List<String>): String {
        val t = " " + norm(title.orEmpty()) + " "
        val intro = " " + norm(text.take(400)) + " "
        val scored = CATEGORY_WORDS.map { (c, w) -> c to hits(t, w) * 3 + hits(intro, w) }
        val best = scored.filter { it.second > 0 }.maxByOrNull { it.second }
        if (best != null) return best.first
        val all = " " + norm(text) + " "
        // Блюдо без явной категории: второе. Кухня мира — в свою категорию.
        if (cuisine(title, text) != null) return WORLD_CATEGORY
        if ("на ужин" in all || "к ужину" in all) return "Ужины"
        if ("на завтрак" in all) return "Завтраки"
        if ("перекус" in all) return "Перекусы"
        val names = ingredients.map { norm(it) }
        val heavy = RecipeFacets.bases(ingredients).any { it in setOf("chicken", "turkey", "pork", "beef", "lamb", "mince", "liver", "sausage") }
        return if (heavy || names.size >= 6) "Обеды" else "Ужины"
    }

    fun cuisine(title: String?, text: String): String? {
        val t = " " + norm(title.orEmpty()) + " " + norm(text.take(600)) + " "
        return CUISINES.map { (c, w) -> c to hits(t, w) }.filter { it.second > 0 }.maxByOrNull { it.second }?.first
    }

    fun meals(category: String, text: String): List<Int> {
        val all = norm(text)
        val said = MEAL_WORDS.filter { (_, w) -> w.any { it in all } }.map { it.first }
        val base = DEFAULT_MEALS[category].orEmpty()
        // Сказанное прямо — главнее; категория добавляет обычные приёмы пищи.
        return MealType.order.filter { it in said || (said.isEmpty() && it in base) || (category == "Завтраки" && it in base) }
            .ifEmpty { base }
    }

    fun difficulty(steps: Int, minutes: Int): Int = when {
        steps <= 4 && minutes <= 30 -> 1
        steps <= 8 && minutes <= 90 -> 2
        else -> 3
    }

    /** Предложения с советами или заменами. */
    private fun sentences(text: String, words: List<String>): String {
        val parts = text.replace("\r", "").split(Regex("""(?<=[.!?])\s+|\n+""")).map { it.trim().trim('•', '-', '—', '*', ' ') }
        return parts.filter { p -> p.length in 15..220 && words.any { it in norm(p) } }
            .distinct().take(5).joinToString("\n") { "• " + it.replaceFirstChar { c -> c.uppercase() } }
    }

    fun guess(p: RecipeText.Parsed, text: String): Guess {
        val names = p.ingredients.map { it.name }
        val title = p.title ?: TITLE_IN_SPEECH.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.split(' ').size <= 6 }?.replaceFirstChar { it.uppercase() }
        val cat = category(title, text, names)
        val stepMin = p.steps.sumOf { it.minutes }.coerceAtMost(600)
        val prepEst = (5 + names.size * 2).coerceIn(5, 30)
        val total = p.totalMinutes
        val (prep, cook) = if (total != null && total > 0) minOf(prepEst, total / 3).let { it to (total - it) } else prepEst to stepMin
        val tags = buildList {
            cuisine(title, text)?.let { add("$it кухня") }
            if (prep + cook in 1..30) add("быстро")
            if (names.isNotEmpty() && RecipeFacets.isVegan(names)) add("постное")
            else if (names.isNotEmpty() && RecipeFacets.isVegetarian(names)) add("вегетарианские")
            if (cat in setOf("Десерты", "Выпечка", "Напитки") && names.isNotEmpty() && names.none { n -> SWEET.any { it in norm(n) } }) add("без сахара")
            if (cat == "Заготовки") add("заготовка")
        }
        return Guess(
            title = title,
            category = cat,
            meals = meals(cat, (title.orEmpty() + " " + text)),
            difficulty = difficulty(p.steps.size, prep + cook),
            prepMin = prep,
            cookMin = cook,
            tags = tags,
            tips = sentences(text, TIP_WORDS),
            swaps = sentences(text, SWAP_WORDS),
        )
    }
}
