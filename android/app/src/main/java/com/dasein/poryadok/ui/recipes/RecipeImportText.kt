package com.dasein.poryadok.ui.recipes

import android.content.ClipboardManager
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
import androidx.compose.material3.Text
import com.dasein.poryadok.ui.common.TextButton
import androidx.compose.runtime.Composable
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

/** Инструкция для ИИ: вернуть рецепт строго в JSON. */
private const val AI_RECIPE_PROMPT = """Ты помощник кулинарного приложения. Из текста рецепта (подпись из Instagram, заметка) извлеки данные и верни ТОЛЬКО JSON без пояснений:
{"title": "название блюда", "servings": число порций или null,
 "ingredients": [{"name": "продукт в именительном падеже, без количества", "amount": число, "unit": "г|кг|мл|л|шт|ст.л.|ч.л.|стакан|щепотка|по вкусу", "grams_per_piece": вес одной штуки в граммах для unit=шт, иначе null}],
 "steps": [{"text": "этап приготовления одним-двумя предложениями", "minutes": минуты этапа или 0}]}
Правила: включи ВСЕ продукты, упомянутые в тексте, даже в советах (например «можно добавить сулугуни» — продукт с unit "по вкусу").
Если для продукта указано несколько количеств («150 г + 20 г на обвалку»), сложи их. Проценты жирности оставь в названии.
Если этапов в тексте нет — придумай короткие логичные этапы по смыслу рецепта. Не выдумывай продукты, которых нет в тексте.
Даты, хештеги, рекламу и «ещё/меньше» игнорируй."""

/** Разбор рецепта ИИ (бесплатные модели OpenRouter). null — не получилось, тогда работает встроенный разбор. */
internal suspend fun aiParse(ctx: Context, text: String): RecipeText.Parsed {
    with(com.dasein.poryadok.system.Ai) {
        val answer = ask(ctx, AI_RECIPE_PROMPT, text.take(6000))
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
        return RecipeText.Parsed(o.str("title"), o.num("servings")?.toInt()?.takeIf { it in 1..40 }, ings, steps, null, RecipeText.parse(text).tags)
    }
}

/**
 * «Описание из Instagram»: вставьте подпись к рецепту — приложение найдёт название, порции, ингредиенты
 * с граммовками и разобьёт приготовление на этапы. Всё попадает в черновик, где можно поправить.
 */
@Composable
internal fun RecipeTextDialog(d: RecipeDraft, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(d.recipe.notes) }
    val preview = remember(text) { if (text.isBlank()) null else RecipeText.parse(text) }
    val aiOn = remember { com.dasein.poryadok.system.Ai.enabled(ctx) }
    var aiBusy by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }
    var linkBusy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val link = remember(text) { com.dasein.poryadok.system.RecipeLink.findUrl(text)?.takeIf { text.trim().length < it.length + 40 } }
    // Пришла одна ссылка (поделились постом) — сразу загружаем рецепт.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val l = link ?: return@LaunchedEffect
        linkBusy = true
        runCatching { com.dasein.poryadok.system.RecipeLink.fetch(l) }.onSuccess { text = it.trim() }.onFailure { aiError = "Не удалось загрузить: ${it.message}." }
        linkBusy = false
    }
    fun applyResult(p: RecipeText.Parsed, viaAi: Boolean) {
        scope.launch {
            applyParsed(d, p, text, viaAi)
            // С ИИ — ещё и красивое описание для карточки блюда (его потом можно править вручную).
            if (viaAi) {
                status = "Оформляю описание…"
                runCatching { com.dasein.poryadok.system.RecipeLink.prettyDescription(ctx, text, d.recipe.name) }
                    .onSuccess { if (it.length > 40) d.recipe = d.recipe.copy(notes = it) }
            }
            aiBusy = false; onDismiss()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(btnLabel("Описание блюда")) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Text("Вставьте подпись к рецепту из Instagram или любой текст: ингредиенты с граммовками, порции и этапы приготовления определятся сами.", fontSize = 13.sp, color = extra.dim)
                TextButton(onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()?.let { text = it }
                }) { Text(btnLabel("Вставить из буфера обмена")) }
                TextInput(text, { text = it }, "Описание или ссылка (Instagram, сайт с рецептом)", singleLine = false, minLines = 6)
                if (link != null) Button(enabled = !linkBusy, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), onClick = {
                    linkBusy = true; aiError = null
                    scope.launch {
                        runCatching { com.dasein.poryadok.system.RecipeLink.fetch(link) }
                            .onSuccess { text = it.trim() }
                            .onFailure { aiError = "Не удалось загрузить: ${it.message}." }
                        linkBusy = false
                    }
                }) { Text(if (linkBusy) "Загружаю рецепт…" else "Загрузить рецепт по ссылке") }
                status?.let { Text(it, fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 6.dp)) }
                aiError?.let { Text(it + " Можно нажать «Разобрать» — встроенный разбор работает без интернета.", fontSize = 12.sp, color = extra.danger, modifier = Modifier.padding(top = 6.dp)) }
                if (!aiOn) Text("Точнее разбирает бесплатный ИИ: добавьте ключ OpenRouter в «Настройки» → «ИИ-помощник».", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 6.dp))
                preview?.let { p ->
                    Text(
                        listOfNotNull(
                            p.title?.let { "Название: $it" },
                            "Ингредиентов: ${p.ingredients.size}" + " (+ упомянутые в тексте)",
                            "Этапов: ${p.steps.size}",
                            p.servings?.let { "Порций: $it" } ?: "Порции посчитаю по весу",
                        ).joinToString(" · "),
                        fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        },
        confirmButton = {
            Row {
            if (aiOn) TextButton(enabled = text.isNotBlank() && !aiBusy, onClick = {
                aiBusy = true; aiError = null
                scope.launch {
                    runCatching { aiParse(ctx, text) }
                        .onSuccess { applyResult(it, viaAi = true) }
                        .onFailure { aiError = it.message ?: "ошибка"; aiBusy = false }
                }
            }) { Text(if (aiBusy) (status ?: "ИИ думает…") else "✨ С ИИ") }
            TextButton(enabled = preview != null && !aiBusy, onClick = { preview?.let { applyResult(it, viaAi = false) } }) { Text(btnLabel("Разобрать")) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) } },
    )
}

/** Применить разбор к черновику: ингредиенты с КБЖУ из базы, порции, этапы. */
private suspend fun applyParsed(d: RecipeDraft, p: RecipeText.Parsed, text: String, viaAi: Boolean) {
                    // ИИ сам перечисляет упомянутые продукты; встроенный разбор добирает их по базе.
                    val ings = ingredientsFrom(p, if (viaAi) "" else text)
                    var r = d.recipe.copy(notes = text.trim())
                    if (r.name.isBlank() && p.title != null) r = r.copy(name = p.title)
                    val total = ings.sumOf { it.grams }
                    val servings = p.servings ?: RecipeText.guessServings(total)
                    // Порции из текста задают вес порции: блюдо делится на столько тарелок.
                    r = r.copy(servings = servings, portionGrams = if (p.servings != null && total > 0) (total / servings).coerceAtLeast(50.0) else r.portionGrams)
                    p.totalMinutes?.let { r = r.copy(cookMin = it) }
                    if (p.tags.isNotEmpty() && r.tags.isBlank()) r = r.copy(tags = p.tags.take(6).joinToString(", "))
                    d.recipe = r
                    if (ings.isNotEmpty()) { d.ingredients.clear(); d.ingredients.addAll(ings) }
                    if (p.steps.isNotEmpty()) { d.steps.clear(); d.steps.addAll(p.steps.map { it.text }) }
                    // Сразу показать, что получилось: ингредиенты можно поправить.
                    d.step = 1
}
