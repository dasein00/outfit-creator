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
private const val AI_RECIPE_PROMPT = """Ты помощник кулинарного приложения. Текст — это подпись к рецепту из Instagram, заметка, страница сайта или расшифровка речи из видео (тогда в нём могут быть разговорные слова, повторы и ошибки распознавания — пойми смысл). Извлеки рецепт и верни ТОЛЬКО JSON без пояснений:
{"title": "название блюда", "servings": число порций или null,
 "ingredients": [{"name": "продукт в именительном падеже, без количества", "amount": число, "unit": "г|кг|мл|л|шт|ст.л.|ч.л.|стакан|щепотка|по вкусу", "grams_per_piece": вес одной штуки в граммах для unit=шт, иначе null}],
 "steps": [{"text": "подробный этап для новичка", "minutes": минуты этапа или 0}]}
Правила: включи ВСЕ продукты, упомянутые в тексте, даже в советах (например «можно добавить сулугуни» — продукт с unit "по вкусу").
Если в тексте есть блок «Ингредиенты (из поста, точные граммовки)» — бери продукты и количества оттуда в первую очередь, а речь используй для этапов и деталей.
Если для продукта указано несколько количеств («150 г + 20 г на обвалку»), сложи их. Проценты жирности оставь в названии.
Если количество сказано на глаз («горсть», «пара ложек», «кусочек») — переведи в граммы или ложки примерно. Если не сказано совсем — оцени разумное количество на указанные порции.
Этапы пиши подробно и понятно новичку: что сделать, какой огонь или температура духовки, сколько минут, как понять, что готово. Не выдумывай продукты, которых нет в тексте.
Даты, хештеги, рекламу, приветствия и «ещё/меньше» игнорируй."""

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
    // Ингредиенты из поста (в видео их обычно не проговаривают): с граммовками — они важнее того, что сказано в речи.
    var ingrText by remember { mutableStateOf("") }
    /** Всё, что разбирается: речь или описание + ингредиенты из поста. */
    fun source(): String = if (ingrText.isBlank()) text else "Ингредиенты (из поста, точные граммовки):\n" + ingrText.trim() + "\n\nОписание и речь из видео:\n" + text.trim()
    val preview = remember(text, ingrText) { if (text.isBlank() && ingrText.isBlank()) null else RecipeText.parse(source()) }
    val aiOn = remember { com.dasein.poryadok.system.Ai.enabled(ctx) }
    var aiBusy by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }
    var linkBusy by remember { mutableStateOf(false) }
    var postUrl by remember { mutableStateOf("") }
    var postBusy by remember { mutableStateOf(false) }
    var videoBusy by remember { mutableStateOf(false) }
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
            applyParsed(d, p, source(), viaAi)
            // С ИИ — ещё и красивое описание для карточки блюда (его потом можно править вручную).
            if (viaAi) {
                status = "Оформляю описание…"
                runCatching { com.dasein.poryadok.system.RecipeLink.prettyDescription(ctx, source(), d.recipe.name) }
                    .onSuccess { if (it.length > 40) d.recipe = d.recipe.copy(notes = it) }
            }
            aiBusy = false; onDismiss()
        }
    }
    fun fromVideo(uri: android.net.Uri) {
        videoBusy = true; aiError = null
        scope.launch {
            runCatching { com.dasein.poryadok.system.VideoSpeech.transcribe(ctx, uri) { st -> status = st } }
                .onSuccess { speech ->
                    // Текст речи — в описание: его можно поправить, а потом добавить ингредиенты из поста и сгенерировать рецепт.
                    text = speech.trim()
                    d.recipe = d.recipe.copy(notes = text)
                    status = "Речь распознана. Поправьте текст, добавьте ингредиенты из поста и нажмите «Сгенерировать рецепт»."
                    videoBusy = false
                }
                .onFailure { e ->
                    videoBusy = false; status = null
                    aiError = if (e is com.dasein.poryadok.system.VideoSpeech.NoSpeech)
                        "${e.message}. Вставьте сюда описание из Instagram, ссылку на пост или сайт, или свой текст — карточка заполнится по нему."
                    else "Не удалось распознать речь: ${e.message}"
                }
        }
    }
    val videoPicker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) fromVideo(uri)
    }
    // Видео, уже прикреплённые к рецепту (файлы на телефоне).
    val ownVideos = remember(d.recipe.videos) { d.recipe.videos.lines().map { it.trim() }.filter { it.startsWith("/") && java.io.File(it).exists() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(btnLabel("Описание блюда")) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "1) «Распознать речь» — текст из видео появится в описании, его можно поправить. Или вставьте подпись из Instagram, ссылку на пост или сайт, свой текст.\n" +
                        "2) Вставьте ссылку на пост Instagram и нажмите «Запарсить рецепт» — подпись под видео (ингредиенты, граммовки) попадёт в поле «Текст из поста». Или вставьте её вручную.\n3) «Сгенерировать рецепт» — ИИ заполнит ингредиенты, подробные этапы и описание. Всё можно править потом.",
                    fontSize = 13.sp, color = extra.dim, lineHeight = 18.sp,
                )
                TextButton(onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()?.let { text = it }
                }) { Text(btnLabel("Вставить из буфера обмена")) }
                // Видео со звуком: речь распознаётся и попадает в описание.
                OutlinedButton(enabled = !videoBusy && !aiBusy, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), onClick = {
                    videoPicker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.VideoOnly))
                }) { Text(if (videoBusy) (status ?: "Распознаю…") else "Распознать речь") }
                ownVideos.forEachIndexed { i, v ->
                    TextButton(enabled = !videoBusy && !aiBusy, onClick = { fromVideo(android.net.Uri.fromFile(java.io.File(v))) }) {
                        Text("Распознать речь из видео рецепта" + if (ownVideos.size > 1) " №${i + 1}" else "")
                    }
                }
                TextInput(text, { text = it }, "Описание: речь из видео, текст поста или ссылка", singleLine = false, minLines = 5)
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
                                    ingrText = cap.trim()
                                    status = "Описание из поста загружено. Распознайте речь из видео (если ещё не) и нажмите «Сгенерировать рецепт»."
                                }
                                .onFailure { aiError = "Не удалось загрузить пост: ${it.message}. Скопируйте подпись вручную в поле «Текст из поста»." }
                            postBusy = false
                        }
                    },
                ) { Text(if (postBusy) "Загружаю пост…" else btnLabel("Запарсить рецепт")) }
                TextInput(ingrText, { ingrText = it }, "Текст из поста: ингредиенты, граммовки", Modifier.padding(top = 6.dp), singleLine = false, minLines = 3)
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
                aiError?.let { Text(if ("Вставьте сюда" in it) it else it + " Можно нажать «Разобрать» — встроенный разбор работает без интернета.", fontSize = 12.sp, color = extra.danger, modifier = Modifier.padding(top = 6.dp)) }
                if (!aiOn) Text("Точнее разбирает бесплатный ИИ: добавьте ключ OpenRouter в «Настройки» → «ИИ-помощник».", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 6.dp))
                // Главные действия — крупно и во всю ширину, чтобы надписи не сжимались.
                if (aiOn) Button(
                    enabled = (text.isNotBlank() || ingrText.isNotBlank()) && !aiBusy && !videoBusy,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 52.dp),
                    onClick = {
                        aiBusy = true; aiError = null; status = "ИИ пишет подробный рецепт…"
                        scope.launch {
                            runCatching { aiParse(ctx, source()) }
                                .onSuccess { applyResult(it, viaAi = true) }
                                .onFailure { aiError = it.message ?: "ошибка"; aiBusy = false }
                        }
                    },
                ) { com.dasein.poryadok.ui.common.AiLabel("Сгенерировать рецепт", aiBusy) }
                OutlinedButton(
                    enabled = preview != null && !aiBusy,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp),
                    onClick = { preview?.let { applyResult(it, viaAi = false) } },
                ) { Text(btnLabel("Разобрать без ИИ")) }
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
        confirmButton = {},
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
