package com.dasein.poryadok.ui.recipes

import android.content.ClipboardManager
import com.dasein.poryadok.ui.common.OutlinedButton
import com.dasein.poryadok.ui.common.Button
import com.dasein.poryadok.ui.common.btnLabel
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import com.dasein.poryadok.ui.common.Text
import com.dasein.poryadok.ui.common.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.FoodProduct
import com.dasein.poryadok.data.RecipeIngredient
import com.dasein.poryadok.data.toIngr
import com.dasein.poryadok.logic.Cooking
import com.dasein.poryadok.logic.RecipeText
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.launch

/** Продукт из базы для ингредиента по названию: точное совпадение, потом по корню первого слова. */
internal fun matchProduct(name: String, products: List<FoodProduct>): FoodProduct? {
    fun n(s: String) = s.lowercase().replace('ё', 'е').trim()
    val q = n(name)
    products.firstOrNull { n(it.name) == q }?.let { return it }
    val words = q.split(' ', ',', '(').filter { it.length >= 3 }
    if (words.isEmpty()) return null
    val stem = words.first().take(maxOf(3, words.first().length - 2))
    val cand = products.filter { p -> n(p.name).split(' ', ',').any { it.startsWith(stem) } }
    // Чем больше слов совпало и чем короче название продукта, тем лучше.
    return cand.maxByOrNull { p ->
        val pn = n(p.name)
        words.count { w -> pn.contains(w.take(maxOf(3, w.length - 2))) } * 10 - pn.length / 10
    }
}

/** Вес «штуки», если продукта нет в базе: яйцо, луковица, зубчик… */
private fun pieceGrams(name: String): Double {
    val n = name.lowercase()
    return when {
        "яйц" in n -> 55.0; "лук" in n -> 90.0; "морков" in n -> 80.0; "картоф" in n -> 120.0; "помидор" in n || "томат" in n -> 120.0
        "огур" in n -> 110.0; "перец" in n -> 150.0; "яблок" in n -> 180.0; "банан" in n -> 120.0; "лимон" in n -> 100.0
        "чеснок" in n -> 5.0; "кабач" in n -> 300.0; "баклажан" in n -> 250.0
        else -> 100.0
    }
}

/**
 * Продукты из базы, которые упомянуты в тексте, но не попали в список («рекомендую взять сулугуни», «добавить укроп»):
 * добавляются «по вкусу», чтобы ничего не потерялось.
 */
internal fun mentionedExtra(text: String, found: List<RecipeText.Ingredient>, products: List<FoodProduct>): List<RecipeText.Ingredient> {
    val tokens = RecipeText.tokens(text)
    val have = found.flatMap { RecipeText.keyWords(it.name) }.toMutableSet()
    val out = ArrayList<RecipeText.Ingredient>()
    products.sortedBy { it.name.length }.forEach { p ->
        val words = RecipeText.keyWords(p.name)
        val hit = words.firstOrNull { w -> RecipeText.mentions(tokens, w) } ?: return@forEach
        if (have.any { h -> h.startsWith(hit.take(maxOf(3, hit.length - 1))) || hit.startsWith(h.take(maxOf(3, h.length - 1))) }) return@forEach
        have += hit
        out += RecipeText.Ingredient(p.name, 0.0, "по вкусу")
    }
    return out
}

/** Разобранный текст → ингредиенты черновика с КБЖУ из базы продуктов. */
internal suspend fun ingredientsFrom(parsed: RecipeText.Parsed, text: String = ""): List<RecipeIngredient> {
    val products = Graph.extra.productsNow()
    val all = parsed.ingredients + if (text.isNotBlank()) mentionedExtra(text, parsed.ingredients, products) else emptyList()
    return all.mapIndexed { i, ing ->
        val p = matchProduct(ing.name, products)
        val per = when {
            ing.gramsPerUnit > 0 -> ing.gramsPerUnit
            (p?.gramsPerUnit ?: 0.0) > 0 -> p!!.gramsPerUnit
            else -> pieceGrams(ing.name)
        }
        RecipeIngredient(
            recipeId = 0, pos = i, productId = p?.id, name = ing.name, amount = ing.amount, unit = ing.unit,
            grams = Cooking.grams(ing.amount, ing.unit, per),
            kcal100 = p?.kcal ?: 0.0, protein100 = p?.protein ?: 0.0, fat100 = p?.fat ?: 0.0, carbs100 = p?.carbs ?: 0.0,
            fiber100 = p?.fiber ?: 0.0, shopCategory = p?.category ?: "Другое",
        )
    }
}

private val AI_UNITS = setOf("г", "кг", "мл", "л", "шт", "ст.л.", "ч.л.", "стакан", "щепотка", "по вкусу")

/**
 * Единый стиль карточек — одинаковый для всех рецептов, как в кулинарном приложении. Дублируется в описании блюда
 * и подробных шагах; что бы ни прислала модель, текст ещё раз выравнивает [com.dasein.poryadok.logic.RecipeStyle].
 */
internal const val RECIPE_STYLE = """СТИЛЬ (одинаковый для всех карточек, как в профессиональном кулинарном приложении):
— Название: 2–6 слов, с заглавной буквы, без эмодзи, кавычек, хештегов и точки; по-русски; главное блюдо и уточнение («Хачапури по-аджарски из творога», «Курица в сливочном соусе»).
— Продукты: обычные магазинные названия в именительном падеже, с заглавной буквы, остальное строчными; без брендов и пояснений в скобках, кроме жирности или сорта («Творог 5%», «Рис басмати»).
— Этапы: 4–10 этапов, каждый начинается с глагола в повелительном наклонении на «вы» («Нарежьте», «Обжарьте», «Запекайте»), 1–3 предложения; обязательно конкретика: огонь (слабый, средний, сильный), температура в °C, время в минутах, признак готовности. Без нумерации и эмодзи внутри текста, в конце точка.
— Советы: 2–4 коротких законченных предложения. Замены: «Продукт — чем заменить».
— Теги: строчными буквами, без «#»."""

/** Инструкция для ИИ: вернуть рецепт строго в JSON. */
private const val AI_RECIPE_PROMPT = """Ты помощник кулинарного приложения. На входе до двух источников об одном блюде:
«ТЕКСТ ПОСТА» — подпись под видео в Instagram или текст сайта (обычно ингредиенты с граммовками, иногда шаги и советы);
«РЕЧЬ ИЗ ВИДЕО» — расшифровка того, что говорит автор (обычно как готовить, нюансы, огонь и время; бывают разговорные слова, повторы и ошибки распознавания — пойми смысл).
Источники ДОПОЛНЯЮТ друг друга: собери один полный рецепт из обоих. Продукты — все из обоих источников без повторов; количества бери из поста, если там есть, иначе из речи, иначе оцени. Этапы — объедини: порядок и детали из речи, точные цифры (граммы, градусы, минуты) из поста. Советы и замены — из обоих.
Верни ТОЛЬКО JSON без пояснений:
{"title": "название блюда, придуманное по самому рецепту: главный продукт или блюдо + способ приготовления или подача (например «Хачапури по-аджарски из творога»). НЕ копируй первую строку поста, если это не название («итак, ингредиенты», «рецепт дня», «сохраняй»)",
 "about": "1–2 предложения для описания карточки: что за блюдо, вкус и текстура, чем хорошо (без воды и превосходных степеней)",
 "servings": число порций или null,
 "category": "одна из: Завтраки|Обеды|Ужины|Перекусы|Кухни мира|Супы|Салаты|Гарниры|Десерты|Напитки|Выпечка|Заготовки",
 "meals": ["подходящие приёмы пищи из: Завтрак|Второй завтрак|Обед|Полдник|Ужин|Перекус"],
 "difficulty": 1 (легко) | 2 (средне) | 3 (сложно),
 "prep_minutes": минуты подготовки (нарезка, замес), "cook_minutes": минуты готовки (варка, жарка, духовка, ожидание),
 "tags": ["до 6 коротких тегов: кухня в виде «итальянская кухня», быстро, высокобелковые, низкоуглеводные, вегетарианские, постное, без сахара, заготовка, на компанию…"],
 "ingredients": [{"name": "продукт в именительном падеже, без количества", "amount": число, "unit": "г|кг|мл|л|шт|ст.л.|ч.л.|стакан|щепотка|по вкусу", "grams_per_piece": вес одной штуки в граммах для unit=шт, иначе null}],
 "steps": [{"text": "подробный этап для новичка", "minutes": минуты этапа или 0}],
 "tips": ["советы автора и полезные советы по подаче и хранению"], "swaps": ["чем можно заменить продукт"]}
Правила: включи ВСЕ продукты, упомянутые в любом источнике, даже в советах (например «можно добавить сулугуни» — продукт с unit "по вкусу").
Если для продукта указано несколько количеств («150 г + 20 г на обвалку»), сложи их. Проценты жирности оставь в названии.
Если количество сказано на глаз («горсть», «пара ложек», «кусочек») — переведи в граммы или ложки примерно. Если не сказано совсем — оцени разумное количество на указанные порции.
Этапы пиши подробно и понятно новичку: что сделать, какой огонь или температура духовки, сколько минут, как понять, что готово. Не выдумывай продукты, которых нет в источниках.
Даты, хештеги, рекламу, приветствия, просьбы подписаться и «ещё/меньше» игнорируй.
Если источники на английском или другом языке — пиши ВСЁ по-русски (переведи названия продуктов, этапы, советы) и переведи меры:
1 cup ≈ 240 мл (мука ≈ 130 г, сахар ≈ 200 г, рис ≈ 190 г), tbsp = ст.л., tsp = ч.л., oz = 28 г, lb = 450 г, stick масла = 113 г, °F → °C.
""" + RECIPE_STYLE

/** Текст для разбора из двух источников с подписями — ИИ сводит их, встроенный разбор берёт продукты из поста, этапы из речи. */
internal fun recipeSource(post: String, speech: String): String = when {
    post.isBlank() -> speech.trim()
    speech.isBlank() -> post.trim()
    else -> "ТЕКСТ ПОСТА:\n" + post.trim() + "\n\nРЕЧЬ ИЗ ВИДЕО:\n" + speech.trim()
}

/** Текст на английском (или другом языке) — перевод на русский с метрическими мерами; без ИИ или при ошибке — как есть. */
internal suspend fun translateIfForeign(ctx: Context, text: String): String {
    if (!com.dasein.poryadok.logic.RecipeStyle.isForeign(text) || !com.dasein.poryadok.system.Ai.enabled(ctx)) return text
    return runCatching {
        com.dasein.poryadok.system.Ai.ask(
            ctx,
            "Переведи текст рецепта на русский язык — точно и естественно, как в русской кулинарной книге. Сохрани строки и порядок, " +
                "подписи «ТЕКСТ ПОСТА:» и «РЕЧЬ ИЗ ВИДЕО:» оставь как есть. Меры переведи в метрические: 1 cup ≈ 240 мл (мука ≈ 130 г, сахар ≈ 200 г), " +
                "tbsp = ст.л., tsp = ч.л., oz = 28 г, lb = 450 г, °F → °C. Хештеги и рекламу убери. Верни только перевод.",
            text.take(9000),
        ).trim().takeIf { it.length > text.length / 4 }
    }.getOrNull() ?: text
}

/**
 * Разбор рецепта ИИ. Английский текст сначала переводится. Если ответ слабый (нет продуктов или этапов, название —
 * фраза из подписи), ИИ получает замечания и переделывает один раз. Ошибка — тогда работает встроенный разбор.
 */
internal suspend fun aiParse(ctx: Context, text: String): RecipeText.Parsed {
    val src = translateIfForeign(ctx, text)
    val first = aiParseOnce(ctx, src, null)
    val problems = buildList {
        if (first.ingredients.isEmpty()) add("нет списка продуктов")
        if (first.steps.size < 2) add("этапов меньше двух — распиши приготовление по шагам")
        if (com.dasein.poryadok.logic.RecipeStyle.isJunkTitle(first.title)) add("название «${first.title.orEmpty()}» не подходит — придумай название блюда по рецепту")
        if (first.steps.any { com.dasein.poryadok.logic.RecipeStyle.isForeign(it.text) }) add("этапы должны быть по-русски")
    }
    if (problems.isEmpty()) return first
    val second = runCatching { aiParseOnce(ctx, src, problems.joinToString("; ")) }.getOrNull() ?: return first
    fun score(p: RecipeText.Parsed) = p.ingredients.size.coerceAtMost(8) + p.steps.size.coerceAtMost(8) * 2 +
        (if (com.dasein.poryadok.logic.RecipeStyle.isJunkTitle(p.title)) 0 else 6)
    val best = if (score(second) >= score(first)) second else first
    // Название всё ещё не годится — отдельный короткий запрос только про название.
    if (!com.dasein.poryadok.logic.RecipeStyle.isJunkTitle(best.title)) return best
    val t = runCatching { aiTitle(ctx, best) }.getOrNull()
    return best.copy(title = t?.takeUnless { com.dasein.poryadok.logic.RecipeStyle.isJunkTitle(it) })
}

/** Название блюда по продуктам и этапам: 2–6 слов, главный продукт и способ приготовления. */
internal suspend fun aiTitle(ctx: Context, p: RecipeText.Parsed): String? {
    val text = "Продукты: " + p.ingredients.joinToString { it.name } + "\nЭтапы: " + p.steps.joinToString(" ") { it.text }.take(1500)
    return com.dasein.poryadok.system.Ai.ask(
        ctx,
        "Ты редактор кулинарного приложения. Придумай точное название блюда по его рецепту: 2–6 слов, по-русски, с заглавной буквы, " +
            "главное блюдо или продукт и способ приготовления или подача (например «Курица в сливочном соусе», «Сырники из творога с изюмом»). " +
            "Без эмодзи, кавычек и точки. Ответь только названием.",
        text,
    ).lines().firstOrNull { it.isNotBlank() }?.let { com.dasein.poryadok.logic.RecipeStyle.title(it) }
}

private suspend fun aiParseOnce(ctx: Context, text: String, fix: String?): RecipeText.Parsed {
    with(com.dasein.poryadok.system.Ai) {
        val user = text.take(9000) + if (fix != null) "\n\nПРЕДЫДУЩИЙ ОТВЕТ НЕ ПОДОШЁЛ: $fix. Исправь и верни полный JSON целиком." else ""
        val answer = ask(ctx, AI_RECIPE_PROMPT, user)
        val o = extractJson(answer) ?: throw com.dasein.poryadok.system.Ai.AiError("ИИ ответил не в том формате")
        val ings = o.arr("ingredients").orEmpty().mapNotNull { it as? kotlinx.serialization.json.JsonObject }.mapNotNull { i ->
            val name = i.str("name")?.trim()?.replaceFirstChar { it.uppercase() } ?: return@mapNotNull null
            val unit = i.str("unit")?.trim()?.takeIf { it in AI_UNITS } ?: "по вкусу"
            RecipeText.Ingredient(name, if (unit == "по вкусу") 0.0 else (i.num("amount") ?: 0.0), unit, i.num("grams_per_piece") ?: 0.0)
        }
        val steps = o.arr("steps").orEmpty().mapNotNull { it as? kotlinx.serialization.json.JsonObject }.mapNotNull { st ->
            val t = st.str("text")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            RecipeText.Step(t, (st.num("minutes") ?: 0.0).toInt().takeIf { it > 0 } ?: RecipeText.minutesIn(t))
        }
        fun strings(key: String) = o.arr(key).orEmpty().mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { p -> p.isString }?.content?.trim() }.filter { it.isNotEmpty() }
        val meals = strings("meals").mapNotNull { m -> com.dasein.poryadok.logic.MealType.names.indexOfFirst { it.equals(m, ignoreCase = true) }.takeIf { it >= 0 } }
        val prep = o.num("prep_minutes")?.toInt()?.takeIf { it in 0..600 }
        val cook = o.num("cook_minutes")?.toInt()?.takeIf { it in 0..1440 }
        val tagsAi = strings("tags").map { it.lowercase().removePrefix("#") }.take(6)
        return RecipeText.Parsed(
            o.str("title"), o.num("servings")?.toInt()?.takeIf { it in 1..40 }, ings, steps, cook,
            tagsAi.ifEmpty { RecipeText.parse(text).tags },
            category = o.str("category")?.trim()?.takeIf { it in com.dasein.poryadok.logic.RECIPE_CATEGORIES },
            meals = meals.ifEmpty { null },
            difficulty = o.num("difficulty")?.toInt()?.takeIf { it in 1..3 },
            prepMinutes = prep,
            tips = strings("tips").take(6).joinToString("\n") { "• $it" }.ifBlank { null },
            swaps = strings("swaps").take(6).joinToString("\n") { "• $it" }.ifBlank { null },
            about = o.str("about")?.trim()?.takeIf { it.length > 10 },
        )
    }
}

/**
 * Описание карточки — одно и то же оформление у всех рецептов: заголовок, пара строк о блюде, ингредиенты,
 * приготовление по шагам, советы и замены. Собирается из уже выровненных полей карточки, а не пишется ИИ заново.
 */
internal fun recipeDescription(d: RecipeDraft, about: String?): String = buildString {
    val r = d.recipe
    append("# ").append(r.name.ifBlank { "Рецепт" }).append("\n\n")
    about?.let { append(com.dasein.poryadok.logic.RecipeStyle.step(it)).append("\n\n") }
    if (d.ingredients.isNotEmpty()) {
        append("## Ингредиенты\n")
        d.ingredients.forEach { i ->
            val q = com.dasein.poryadok.logic.Cooking.label(i.toIngr())
            append("- ").append(i.name).append(if (q.isNotBlank()) " — $q" else "").append('\n')
        }
        append('\n')
    }
    val steps = d.steps.filter { it.isNotBlank() }
    if (steps.isNotEmpty()) {
        append("## Приготовление\n")
        steps.forEachIndexed { n, s -> append(n + 1).append(". ").append(s).append('\n') }
        append('\n')
    }
    if (r.tips.isNotBlank()) append("## Советы\n").append(r.tips.trim()).append("\n\n")
    if (r.swaps.isNotBlank()) append("## Замены\n").append(r.swaps.trim()).append('\n')
}.trim()

/**
 * Теги карточки от ИИ по названию, продуктам и этапам: кухня, «быстро», «высокобелковые», «постное» и т. п.
 * Без ИИ — встроенные правила [com.dasein.poryadok.logic.RecipeClassify].
 */
internal suspend fun aiTags(ctx: Context, d: RecipeDraft): List<String> {
    val r = d.recipe
    val text = buildString {
        appendLine("Блюдо: ${r.name}. Категория: ${r.category}. Время: ${r.prepMin + r.cookMin} мин.")
        appendLine("Продукты: " + d.ingredients.joinToString { "${it.name} ${it.grams.toInt()} г" })
        appendLine("Этапы: " + d.steps.filter { it.isNotBlank() }.joinToString(" "))
    }
    val offline = {
        val p = RecipeText.Parsed(r.name.ifBlank { null }, r.servings, d.ingredients.map { RecipeText.Ingredient(it.name, it.amount, it.unit, 0.0) }, d.steps.map { RecipeText.Step(it, 0) }, r.cookMin, emptyList())
        com.dasein.poryadok.logic.RecipeClassify.guess(p, text).tags
    }
    if (!com.dasein.poryadok.system.Ai.enabled(ctx)) return offline()
    return runCatching {
        with(com.dasein.poryadok.system.Ai) {
            val answer = ask(
                ctx,
                "Ты помощник кулинарного приложения. По описанию блюда подбери до 6 коротких тегов на русском в нижнем регистре: кухня мира («итальянская кухня»), " +
                    "быстро (до 30 мин), высокобелковые, низкоуглеводные, вегетарианские, постное, без сахара, без глютена, заготовка, на компанию, детское, праздничное, бюджетное, острое. " +
                    "Только подходящие. Верни ТОЛЬКО JSON: {\"tags\": [\"…\"]}",
                text.take(4000),
            )
            val o = extractJson(answer) ?: return@runCatching offline()
            o.arr("tags").orEmpty().mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.trim()?.lowercase()?.removePrefix("#") }.filter { it.isNotEmpty() }.take(6)
        }
    }.getOrElse { offline() }.ifEmpty { offline() }
}

/** Это уже оформленное описание или прошлый разбор, а не подпись поста — в поле поста его не переносим. */
private fun isOwnDraft(t: String) = t.trimStart().startsWith("#") || "## Ингредиенты" in t || t.trimStart().startsWith("ТЕКСТ ПОСТА:")

/**
 * «Рецепт из видео, Instagram или по ссылке». Два источника, которые дополняют друг друга:
 *  • текст поста (подпись под видео в Instagram, сайт) — обычно продукты с граммовками;
 *  • речь из видео — как готовить. Видео рецепта распознаётся один раз (и сразу после добавления, в фоне),
 *    выбранное здесь видео само прикрепляется к рецепту — загружать его второй раз не нужно.
 * С ИИ рецепт собирается из обоих источников целиком; без интернета — встроенный разбор заполняет всё сам.
 */
@Composable
internal fun RecipeTextDialog(d: RecipeDraft, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    /** Речь из видео или свой текст. */
    var text by remember { mutableStateOf(d.recipe.notes) }
    /** Текст поста: ингредиенты, граммовки (в видео их обычно не проговаривают). */
    var ingrText by remember { mutableStateOf("") }
    fun source(): String = recipeSource(ingrText, text)
    // Без интернета: продукты — из поста (и те, что прозвучали в речи), этапы — из поста или из речи.
    val preview = remember(text, ingrText) {
        when {
            text.isBlank() && ingrText.isBlank() -> null
            text.isNotBlank() && ingrText.isNotBlank() -> RecipeText.merge(ingrText, text)
            else -> RecipeText.parse(source())
        }
    }
    val aiOn = remember { com.dasein.poryadok.system.Ai.enabled(ctx) }
    var aiBusy by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }
    var linkBusy by remember { mutableStateOf(false) }
    // Ссылка на пост подставляется сама: из прикреплённых к рецепту ссылок или из вставленного текста.
    var postUrl by remember {
        mutableStateOf(
            RecipeVideos.source(d.recipe) ?: (d.recipe.videos.lines() + d.recipe.notes.lines()).map { it.trim() }
                .firstOrNull { it.startsWith("http") && "instagram.com" in it }.orEmpty()
        )
    }
    var bothBusy by remember { mutableStateOf(false) }
    var postBusy by remember { mutableStateOf(false) }
    var videoBusy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    // Instagram требует вход — показываем кнопку «Войти в Instagram», после входа пост загружается сам.
    var needLogin by remember { mutableStateOf(false) }
    var showLogin by remember { mutableStateOf(false) }
    var shotBusy by remember { mutableStateOf(false) }
    val shots = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(6)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        shotBusy = true; aiError = null
        scope.launch {
            val got = com.dasein.poryadok.system.TextOcr.read(ctx, uris) { st -> status = st }
            if (got.isBlank()) aiError = "На скриншоте не нашлось текста. Откройте пост с раскрытым описанием («ещё») и сделайте скриншот."
            else {
                ingrText = listOf(ingrText.trim(), got).filter { it.isNotBlank() }.joinToString("\n")
                status = "Текст со скриншота распознан. Проверьте его и нажмите «Сгенерировать рецепт» или «Разобрать без ИИ»."
            }
            if (got.isBlank()) status = null
            shotBusy = false
        }
    }
    /** Ссылка на пост, из которого взято описание, — кнопка «Источник» под видео в карточке. */
    fun keepSource(url: String) { if (url.startsWith("http")) d.recipe = RecipeVideos.withSource(d.recipe, url) }
    /**
     * Новая карточка: заполняется в фоне по очереди — окно закрывается, карточку можно покинуть и делать следующую.
     * Очередь сама дождётся подписи по ссылке и распознавания речи из видео, потом ИИ напишет рецепт; о начале и конце
     * приходит уведомление, готовая карточка ждёт в «Черновиках». У правки существующего рецепта всё делается здесь же.
     */
    fun queueAi(): Boolean {
        val did = d.draftId ?: return false
        keepSource(postUrl)
        RecipeAiQueue.enqueue(ctx, RecipeDrafts.from(d, did), ingrText.trim(), text.trim(), postUrl.takeIf { it.startsWith("http") }.orEmpty())
        android.widget.Toast.makeText(
            ctx, "Карточка в очереди — рецепт создастся, когда загрузятся пост и речь из видео. Можно выйти и делать следующую.",
            android.widget.Toast.LENGTH_LONG,
        ).show()
        onDismiss()
        return true
    }
    fun loginNeeded(e: Throwable) { if (e is com.dasein.poryadok.system.RecipeLink.InstagramLogin) needLogin = true }
    // Видео, уже прикреплённые к рецепту (файлы на телефоне).
    val ownVideos = remember(d.recipe.videos) { d.recipe.videos.lines().map { it.trim() }.filter { it.startsWith("/") && java.io.File(it).exists() } }
    val link = remember(text) { com.dasein.poryadok.system.RecipeLink.findUrl(text)?.takeIf { text.trim().length < it.length + 40 } }

    /** Новая речь не затирает подпись поста: вставленный раньше текст переезжает в поле поста. */
    fun putSpeech(speech: String) {
        if (ingrText.isBlank() && text.isNotBlank() && text.trim() != speech.trim() && !isOwnDraft(text) && com.dasein.poryadok.system.RecipeLink.findUrl(text) != text.trim()) ingrText = text.trim()
        text = speech.trim()
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        // Поделились постом (пришла одна ссылка): пост Instagram — в поле поста, сайт — в описание.
        link?.let { l ->
            linkBusy = true
            runCatching { com.dasein.poryadok.system.RecipeLink.fetch(l) }
                .onSuccess { keepSource(l); if ("instagram.com" in l) { postUrl = l; ingrText = it.trim(); text = "" } else text = it.trim() }
                .onFailure { aiError = "Не удалось загрузить: ${it.message}."; loginNeeded(it) }
            linkBusy = false
        }
        // Речь из видео рецепта уже распознана (сразу после добавления) — подставляем, второй раз ничего выбирать не нужно.
        ownVideos.firstNotNullOfOrNull { com.dasein.poryadok.system.VideoSpeech.cached(ctx, it) }?.let { sp ->
            if (text.isBlank() || isOwnDraft(text) || text.trim() == d.recipe.notes.trim() && ingrText.isBlank()) {
                if (text.trim() != sp.trim()) putSpeech(sp)
                status = "Речь из видео рецепта уже распознана."
            }
        }
    }
    // Речь первого видео ещё распознаётся (в фоне после добавления) — дожидаемся и сами вставляем её в поле речи.
    androidx.compose.runtime.LaunchedEffect(ownVideos.firstOrNull()) {
        val v = ownVideos.firstOrNull() ?: return@LaunchedEffect
        if (com.dasein.poryadok.system.VideoSpeech.cached(ctx, v) != null) return@LaunchedEffect
        runCatching { com.dasein.poryadok.system.VideoSpeech.transcribeFile(ctx, v) }.onSuccess { sp ->
            if (text.isBlank() || isOwnDraft(text) || text.trim() == d.recipe.notes.trim() && ingrText.isBlank()) {
                if (text.trim() != sp.trim()) putSpeech(sp)
                status = "Речь из видео распознана и вставлена."
            }
        }
    }
    fun applyResult(p: RecipeText.Parsed, viaAi: Boolean) {
        scope.launch {
            applyParsed(d, p, source(), viaAi)
            keepSource(postUrl)
            // С ИИ — ещё и красивое описание для карточки блюда (его потом можно править вручную).
            // С ИИ — описание карточки в едином оформлении (заголовок, о блюде, ингредиенты, шаги, советы).
            if (viaAi) d.recipe = d.recipe.copy(notes = recipeDescription(d, p.about))
            aiBusy = false; onDismiss()
        }
    }
    fun speechError(e: Throwable): String = if (e is com.dasein.poryadok.system.VideoSpeech.NoSpeech)
        "${e.message}. Вставьте описание из Instagram, ссылку на пост или сайт, или свой текст — карточка заполнится по нему."
    else "Не удалось распознать речь: ${e.message}"

    /** Речь из файла видео рецепта: из памяти, если уже распознана, иначе распознаётся (один раз). */
    suspend fun speechOf(path: String): Result<String> =
        runCatching { com.dasein.poryadok.system.VideoSpeech.transcribeFile(ctx, path) { st -> status = st } }

    fun fromFile(path: String) {
        videoBusy = true; aiError = null
        scope.launch {
            speechOf(path)
                .onSuccess { sp ->
                    putSpeech(sp)
                    status = "Речь распознана. Поправьте текст при необходимости и нажмите «Сгенерировать рецепт» или «Разобрать без ИИ»."
                }
                .onFailure { e -> status = null; aiError = speechError(e) }
            videoBusy = false
        }
    }
    /** Видео добавлено здесь же (после монтажа и сжатия): прикрепляется к рецепту, и сразу распознаётся речь. */
    fun attachAndRecognize(path: String) {
        d.recipe = d.recipe.copy(videos = (RecipeVideos.list(d.recipe) + path).distinct().joinToString("\n"))
        fromFile(path)
    }
    var addVideo by remember { mutableStateOf(false) }
    /**
     * Всё за один раз: подпись из поста Instagram + речь из видео рецепта → рецепт.
     * С ИИ — он сводит оба текста; без интернета или ключа — встроенный разбор (продукты из поста, этапы из речи, остальное по правилам).
     */
    fun fromPostAndVideo() {
        bothBusy = true; aiError = null
        scope.launch {
            if (postUrl.startsWith("http") && ingrText.isBlank()) {
                status = "Загружаю описание из поста…"
                runCatching { com.dasein.poryadok.system.RecipeLink.fetch(postUrl) }
                    .onSuccess { ingrText = it.trim(); keepSource(postUrl) }
                    .onFailure { aiError = "Пост не загрузился (${it.message}) — беру только речь из видео."; loginNeeded(it) }
            }
            ownVideos.firstOrNull()?.let { v ->
                speechOf(v).onSuccess { putSpeech(it) }.onFailure { if (ingrText.isBlank() && text.isBlank()) aiError = speechError(it) }
            }
            if (text.isBlank() && ingrText.isBlank()) {
                status = null; bothBusy = false
                if (aiError == null) aiError = "Нет текста для рецепта. Добавьте видео, ссылку на пост, скриншот описания или свой текст."
                return@launch
            }
            val online = aiOn && com.dasein.poryadok.system.Offline.hasNetwork(ctx)
            val offline = { if (text.isNotBlank() && ingrText.isNotBlank()) RecipeText.merge(ingrText, text) else RecipeText.parse(source()) }
            if (online && queueAi()) { bothBusy = false; return@launch }
            if (online) {
                status = "ИИ сводит описание поста и речь из видео…"
                aiBusy = true
                runCatching { aiParse(ctx, source()) }
                    .onSuccess { bothBusy = false; applyResult(it, viaAi = true) }
                    .onFailure { aiBusy = false; bothBusy = false; applyResult(offline(), viaAi = false) }
            } else {
                bothBusy = false
                applyResult(offline(), viaAi = false)
            }
        }
    }
    // Фоновое распознавание только что добавленного видео — показываем, на каком оно этапе.
    val bgStage by com.dasein.poryadok.system.VideoSpeech.stage.collectAsState()
    val bgText = ownVideos.firstNotNullOfOrNull { bgStage[it] }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(btnLabel("Описание блюда")) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "Достаточно видео и ссылки на пост: речь из видео рецепта распознаётся сама, подпись под постом загружается по ссылке. " +
                        "Оба текста дополняют друг друга: продукты и граммовки — из поста, как готовить — из речи. " +
                        "ИИ или встроенный разбор (без интернета) заполнят название, категорию, приём пищи, порции, время, сложность, ингредиенты, этапы, теги и советы. Всё можно поправить потом.",
                    fontSize = 13.sp, color = extra.dim, lineHeight = 18.sp,
                )
                bgText?.let { Text("Видео рецепта: $it", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp)) }
                TextButton(onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()?.let { text = it }
                }) { Text(btnLabel("Вставить из буфера обмена")) }
                // Видео загружается один раз — здесь (монтаж, сжатие) или в карточке рецепта. «Распознать речь» берёт его само.
                if (ownVideos.isEmpty() || addVideo) {
                    Text(
                        if (ownVideos.isEmpty()) "Добавьте видео рецепта: откроется монтаж (обрезка, куски, скорость), потом видео сожмётся и прикрепится к рецепту, а речь распознается сама."
                        else "Новое видео тоже прикрепится к рецепту.",
                        fontSize = 12.sp, color = extra.dim, lineHeight = 16.sp, modifier = Modifier.padding(top = 8.dp),
                    )
                    com.dasein.poryadok.ui.common.VideoAddButtons("recipes/video") { path -> addVideo = false; attachAndRecognize(path) }
                }
                if (ownVideos.isNotEmpty()) {
                    // Речь первого видео распознаётся сама и входит в «Заполнить…» — отдельная кнопка для него не нужна.
                    val ready = ownVideos.associateWith { com.dasein.poryadok.system.VideoSpeech.cached(ctx, it) != null }
                    if (videoBusy) Text(status ?: "Распознаю речь…", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 6.dp))
                    ownVideos.drop(1).forEachIndexed { i, v ->
                        TextButton(enabled = !videoBusy && !aiBusy, onClick = { fromFile(v) }) { Text("Распознать речь из видео №${i + 2}" + if (ready[v] == true) " (готово)" else "") }
                    }
                    if (!addVideo) TextButton(enabled = !videoBusy, onClick = { addVideo = true }) { Text(btnLabel("+ Добавить другое видео")) }
                }
                TextInput(text, { text = it }, "Речь из видео или свой текст", singleLine = false, minLines = 5)
                // Подпись под видео в Instagram: ссылка → «Запарсить рецепт» → текст поста попадает в поле ниже.
                TextInput(postUrl, { postUrl = it.trim() }, "Ссылка на пост Instagram", Modifier.padding(top = 6.dp))
                Button(
                    enabled = postUrl.startsWith("http") && !postBusy,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 48.dp),
                    onClick = {
                        postBusy = true; aiError = null
                        scope.launch {
                            runCatching { com.dasein.poryadok.system.RecipeLink.fetch(postUrl) }
                                .onSuccess { cap ->
                                    ingrText = cap.trim(); keepSource(postUrl)
                                    status = "Описание из поста загружено. Нажмите «Сгенерировать рецепт» или «Разобрать без ИИ»."
                                }
                                .onFailure {
                                    aiError = if (it is com.dasein.poryadok.system.RecipeLink.InstagramLogin) "${it.message}."
                                    else "Не удалось загрузить пост: ${it.message}. Скопируйте подпись вручную в поле «Текст из поста»."
                                    loginNeeded(it)
                                }
                            postBusy = false
                        }
                    },
                ) { Text(if (postBusy) "Загружаю пост…" else btnLabel("Запарсить рецепт")) }
                if (needLogin) OutlinedButton(
                    onClick = { showLogin = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 48.dp),
                ) { Text(btnLabel("Войти в Instagram")) }
                // Подпись со скриншота: текст распознаётся на телефоне (без интернета и без входа в Instagram).
                OutlinedButton(
                    enabled = !shotBusy,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 48.dp),
                    onClick = { shots.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                ) { Text(if (shotBusy) "Распознаю текст…" else btnLabel("📷 Текст описания со скриншота")) }
                TextInput(ingrText, { ingrText = it }, "Текст из поста: ингредиенты, граммовки", Modifier.padding(top = 6.dp), singleLine = false, minLines = 3)
                if (showLogin) com.dasein.poryadok.ui.common.InstaLoginDialog {
                    showLogin = false
                    if (com.dasein.poryadok.system.InstaWeb.loggedIn()) {
                        needLogin = false
                        // Вход есть — сразу загружаем пост ещё раз.
                        if (postUrl.startsWith("http")) {
                            postBusy = true; aiError = null; status = "Загружаю пост…"
                            scope.launch {
                                runCatching { com.dasein.poryadok.system.RecipeLink.fetch(postUrl) }
                                    .onSuccess { cap -> ingrText = cap.trim(); keepSource(postUrl); status = "Описание из поста загружено. Нажмите «Сгенерировать рецепт» или «Разобрать без ИИ»." }
                                    .onFailure { status = null; aiError = "Не удалось загрузить пост: ${it.message}." }
                                postBusy = false
                            }
                        }
                    }
                }
                if (link != null && !linkBusy && text.trim().startsWith("http")) Button(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), onClick = {
                    linkBusy = true; aiError = null
                    scope.launch {
                        runCatching { com.dasein.poryadok.system.RecipeLink.fetch(link) }
                            .onSuccess { keepSource(link); if ("instagram.com" in link) { postUrl = link; ingrText = it.trim(); text = "" } else text = it.trim() }
                            .onFailure { aiError = "Не удалось загрузить: ${it.message}."; loginNeeded(it) }
                        linkBusy = false
                    }
                }) { Text(btnLabel("Загрузить рецепт по ссылке")) }
                if (linkBusy) Text(btnLabel("Загружаю рецепт по ссылке…"), fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 6.dp))
                status?.let { Text(it, fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 6.dp)) }
                aiError?.let { Text(if ("Вставьте" in it) it else it + " Можно нажать «Разобрать без ИИ» — встроенный разбор работает без интернета.", fontSize = 12.sp, color = extra.danger, modifier = Modifier.padding(top = 6.dp)) }
                if (!aiOn) Text("Точнее разбирает бесплатный ИИ: добавьте ключ OpenRouter в «Настройки» → «ИИ-помощник».", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 6.dp))
                // Главные действия — крупно и во всю ширину, чтобы надписи не сжимались.
                // Всегда активна: новая карточка уходит в фоновую очередь (она сама дождётся поста и речи из видео),
                // у правки рецепта пост и речь собираются и разбираются здесь же.
                Button(
                    enabled = !aiBusy && !bothBusy,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 52.dp),
                    onClick = { aiError = null; if (!queueAi()) fromPostAndVideo() },
                ) { com.dasein.poryadok.ui.common.AiLabel(if (aiOn) "Сгенерировать рецепт" else "Создать рецепт", aiBusy || bothBusy, status ?: "ИИ думает…") }
                OutlinedButton(
                    enabled = preview != null && !aiBusy,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp),
                    onClick = { preview?.let { applyResult(it, viaAi = false) } },
                ) { Text(btnLabel("Разобрать без ИИ")) }
                preview?.let { p ->
                    val g = remember(p, text, ingrText) { com.dasein.poryadok.logic.RecipeClassify.guess(p, source()) }
                    Text(
                        listOfNotNull(
                            (p.title ?: g.title)?.let { "Название: $it" },
                            "Категория: ${g.category}",
                            "Ингредиентов: ${p.ingredients.size}" + " (+ упомянутые в тексте)",
                            "Этапов: ${p.steps.size}",
                            p.servings?.let { "Порций: $it" } ?: "Порции посчитаю по весу",
                        ).joinToString(" · "),
                        fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) } },
    )
}

/**
 * Применить разбор к черновику: ингредиенты с КБЖУ из базы, порции, этапы — и все остальные пункты карточки:
 * категория, приёмы пищи, сложность, время, теги, советы и замены (от ИИ, а без него — [com.dasein.poryadok.logic.RecipeClassify]).
 */
internal suspend fun applyParsed(d: RecipeDraft, p: RecipeText.Parsed, text: String, viaAi: Boolean) {
    // ИИ сам перечисляет упомянутые продукты; встроенный разбор добирает их по базе.
    val ings = ingredientsFrom(p, if (viaAi) "" else text).map { it.copy(name = com.dasein.poryadok.logic.RecipeStyle.ingredient(it.name)) }
    val g = com.dasein.poryadok.logic.RecipeClassify.guess(p, text)
    var r = d.recipe.copy(notes = text.trim())
    // Название — по самому рецепту: фразы из подписи («итак, ингредиенты») не берём; без ИИ — главный продукт.
    val st = com.dasein.poryadok.logic.RecipeStyle
    val title = p.title?.takeUnless { st.isJunkTitle(it) } ?: g.title?.takeUnless { st.isJunkTitle(it) }
        ?: ings.firstOrNull { it.unit != "по вкусу" }?.let { "Блюдо: " + it.name.lowercase() }
    if ((r.name.isBlank() || st.isJunkTitle(r.name)) && title != null) r = r.copy(name = st.title(title))
    val total = ings.sumOf { it.grams }
    val servings = p.servings ?: RecipeText.guessServings(total)
    // Порции из текста задают вес порции: блюдо делится на столько тарелок.
    r = r.copy(servings = servings, portionGrams = if (p.servings != null && total > 0) (total / servings).coerceAtLeast(50.0) else r.portionGrams)
    // Теги по КБЖУ порции: высокобелковые (от 25 г белка), низкоуглеводные (до 15 г углеводов).
    val protein = ings.sumOf { it.grams * it.protein100 / 100 } / servings
    val carbs = ings.sumOf { it.grams * it.carbs100 / 100 } / servings
    val kcal = ings.sumOf { it.grams * it.kcal100 / 100 }
    val kbjuTags = buildList {
        if (protein >= 25) add("высокобелковые")
        if (kcal > 0 && carbs <= 15) add("низкоуглеводные")
    }
    val tags = (r.tagList() + p.tags.take(if (viaAi) 6 else 3) + g.tags + kbjuTags).map { com.dasein.poryadok.logic.RecipeStyle.tag(it) }.filter { it.isNotEmpty() }.distinct().take(10)
    val meals = (p.meals ?: g.meals).toSet()
    r = r.copy(
        category = p.category ?: g.category,
        meals = com.dasein.poryadok.logic.MealType.order.filter { it in meals }.joinToString(","),
        difficulty = p.difficulty ?: g.difficulty,
        prepMin = p.prepMinutes ?: g.prepMin,
        // У ИИ «totalMinutes» — время готовки; у встроенного разбора время делит RecipeClassify.
        cookMin = if (p.prepMinutes != null) (p.totalMinutes ?: g.cookMin) else g.cookMin,
        tags = tags.joinToString(", "),
        tips = r.tips.ifBlank { (p.tips ?: g.tips).let { com.dasein.poryadok.logic.RecipeStyle.bullets(it) } },
        swaps = r.swaps.ifBlank { (p.swaps ?: g.swaps).let { com.dasein.poryadok.logic.RecipeStyle.bullets(it) } },
    )
    d.recipe = r
    if (ings.isNotEmpty()) { d.ingredients.clear(); d.ingredients.addAll(ings) }
    if (p.steps.isNotEmpty()) { d.steps.clear(); d.steps.addAll(p.steps.map { com.dasein.poryadok.logic.RecipeStyle.step(it.text) }.filter { it.isNotBlank() }) }
    // Сразу показать, что получилось: ингредиенты можно поправить.
    d.step = 1
}
