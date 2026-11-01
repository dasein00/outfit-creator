package com.dasein.poryadok.ui.recipes

import com.dasein.poryadok.Graph
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Иконка блюда по названию — из банка иконок нового стиля (у каждого встроенного рецепта своя иллюстрация).
 * Ищется рецепт с самым похожим названием; если такого блюда нет — похожее внешне или смежное
 * (котлета из курицы → котлета из свинины, борщ → щи, оладьи → блины…), затем — по составу, в конце — по категории.
 */
object DishIcons {
    private class Entry(val name: String, val image: String, val category: String, val title: Set<String>, val head: String, val ings: Set<String>)

    private val entries: List<Entry> by lazy {
        runCatching {
            val root = Json.parseToJsonElement(Graph.app.assets.open("recipes/seed.json").bufferedReader().use { it.readText() }).jsonObject
            (root["recipes"] as? JsonArray).orEmpty().mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val name = (o["name"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                val image = (o["image"] as? JsonPrimitive)?.content?.takeIf { it.startsWith("dish/") } ?: return@mapNotNull null
                val cat = (o["category"] as? JsonPrimitive)?.content.orEmpty()
                val ings = (o["ingredients"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.get("product")?.let { p -> (p as? JsonPrimitive)?.content } }
                val t = stems(name)
                Entry(name, image, cat, t.toSet(), t.firstOrNull().orEmpty(), ings.flatMap { stems(it).take(1) }.toSet())
            }
        }.getOrDefault(emptyList())
    }

    private val STOP = setOf("с", "со", "и", "в", "во", "на", "из", "по", "для", "без", "а", "к", "от", "под", "или", "блюдо", "домашн", "просто", "быстр", "вкусн", "легк", "лёгк")

    /** Корни слов: строчные, «ё» → «е», служебные слова убраны, окончание обрезано (первые 3–6 букв). */
    private fun stems(s: String): List<String> = s.lowercase().replace('ё', 'е').split(Regex("[^а-яa-z]+"))
        .filter { it.length >= 2 && it !in STOP }
        .map { w -> if (w.length <= 4) w else w.take((w.length - 2).coerceIn(4, 6)) }
        .filter { it !in STOP }

    /** Семьи похожих блюд: если точного нет, берём блюдо из той же семьи. */
    private val FAMILIES: List<Set<String>> = listOf(
        setOf("котле", "тефте", "фрика", "биточ", "зрази", "бургер", "люля", "кебаб", "шницел", "отбив"),
        setOf("суп", "борщ", "щи", "солян", "харчо", "рассол", "уха", "бульо", "окрош", "гаспач", "крем-с", "лапша"),
        setOf("блин", "блинч", "олад", "панке", "сырни", "драни", "вафл"),
        setOf("салат", "винег", "оливье", "цезар", "боул"),
        setOf("паста", "спаге", "макар", "лапша", "фетту", "пенне", "лазан", "ризот", "удон", "рамен"),
        setOf("пирог", "пирож", "пицц", "киш", "тарт", "хачап", "самса", "чебур", "кулеб", "пиро"),
        setOf("каша", "овсян", "гречк", "рисов", "манн", "пшен", "кино", "гранол", "мюсли"),
        setOf("запек", "гратен", "мусак", "суфле"),
        setOf("омлет", "яичн", "фритт", "шакшу", "скрэм", "яйцо", "яйца"),
        setOf("торт", "пирож", "чизке", "тирам", "кекс", "маффи", "брауни", "печен", "десер", "мусс", "пудин"),
        setOf("смузи", "коктей", "лимон", "морс", "компо", "чай", "кофе", "какао", "напит", "сок"),
        setOf("ролл", "суши", "онигир", "поке"),
        setOf("шаурм", "буррит", "тако", "ролл", "лаваш", "сэндв", "бутер", "тост", "брускет", "кесад"),
        setOf("плов", "ризот", "паэль", "рис"),
        setOf("рагу", "гуляш", "жарк", "тушен", "строга", "азу", "чили"),
        setOf("стейк", "шашлы", "барбек", "гриль", "запеч"),
        setOf("пельм", "варен", "манты", "хинка", "дамп", "равио", "гедза"),
        setOf("улитк", "рулет", "ролл", "нем", "спринг"),
    )
    /** Мясо и рыба взаимозаменяемы для картинки: курица ↔ индейка ↔ свинина ↔ говядина… */
    private val PROTEINS = setOf("кури", "курин", "цыпл", "индей", "свин", "говя", "телят", "баран", "фарш", "мясн", "мяс", "утк", "кроли")
    private val FISH = setOf("рыб", "лосос", "семг", "форел", "тунц", "треск", "минта", "сельд", "кревет", "мидии", "кальм", "краб", "икр")

    private fun family(stem: String): Int = FAMILIES.indexOfFirst { f -> f.any { stem.startsWith(it) || it.startsWith(stem) && stem.length >= 3 } }
    private fun same(a: String, b: String) = a == b || (a.length >= 4 && b.length >= 4 && (a.startsWith(b) || b.startsWith(a)))
    private fun group(s: String, g: Set<String>) = g.any { s.startsWith(it) }

    private val cache = HashMap<String, String>()

    /** Иконка блюда для [title] ([category] и [ingredients] помогают, если по названию совпадений нет). */
    fun match(title: String, category: String = "", ingredients: List<String> = emptyList()): String {
        val key = title.lowercase() + "|" + category + "|" + ingredients.take(4).joinToString(",")
        cache[key]?.let { return it }
        val t = stems(title)
        val head = t.firstOrNull().orEmpty()
        val ing = ingredients.flatMap { stems(it).take(1) }.toSet()
        var best: Entry? = null
        var bestScore = 0.0
        for (e in entries) {
            var s = 0.0
            t.forEachIndexed { i, w ->
                val wgt = if (i == 0) 4.0 else 2.0
                when {
                    e.title.any { same(it, w) } -> s += wgt
                    group(w, PROTEINS) && e.title.any { group(it, PROTEINS) } -> s += wgt * .5
                    group(w, FISH) && e.title.any { group(it, FISH) } -> s += wgt * .5
                }
            }
            // Тип блюда (первое слово) из той же семьи — «котлеты» ↔ «тефтели», «борщ» ↔ «щи».
            val fh = family(head)
            if (fh >= 0 && e.title.any { family(it) == fh }) s += 3.0
            if (head.isNotEmpty() && same(head, e.head)) s += 2.0
            // Состав.
            if (ing.isNotEmpty()) s += ing.count { w -> e.ings.any { same(it, w) } } * .6
            if (category.isNotBlank() && e.category == category) s += .8
            // При равенстве — блюдо с более коротким названием (меньше лишних деталей).
            s -= e.name.length / 1000.0
            if (s > bestScore) { bestScore = s; best = e }
        }
        val r = best?.takeIf { bestScore >= 1.5 }?.image ?: byCategory(category) ?: best?.image ?: categoryIcon(category)
        cache[key] = r
        return r
    }

    /** Первая иконка нового стиля в категории. */
    private fun byCategory(category: String): String? = entries.firstOrNull { it.category == category }?.image

    /** Название встроенного рецепта, похожего на текст (для названия без ИИ): только при сильном совпадении состава. */
    fun similarName(ingredients: List<String>): String? {
        val ing = ingredients.flatMap { stems(it).take(1) }.toSet()
        if (ing.size < 3) return null
        val best = entries.maxByOrNull { e -> ing.count { w -> e.ings.any { same(it, w) } }.toDouble() / (e.ings.size + ing.size) } ?: return null
        val common = ing.count { w -> best.ings.any { same(it, w) } }
        return best.name.takeIf { common >= 4 && common * 2 >= best.ings.size }
    }
}
