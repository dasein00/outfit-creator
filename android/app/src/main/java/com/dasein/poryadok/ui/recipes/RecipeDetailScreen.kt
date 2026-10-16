@file:OptIn(ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.recipes

import com.dasein.poryadok.ui.common.Hint
import com.dasein.poryadok.ui.common.btnLabel
import com.dasein.poryadok.ui.common.HowTo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import com.dasein.poryadok.ui.common.Button
import androidx.compose.material3.MaterialTheme
import com.dasein.poryadok.ui.common.OutlinedButton
import androidx.compose.material3.Switch
import com.dasein.poryadok.ui.common.Text
import com.dasein.poryadok.ui.common.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.FoodProduct
import com.dasein.poryadok.data.MealRepeat
import com.dasein.poryadok.data.Recipe
import com.dasein.poryadok.data.RecipeRepo
import androidx.compose.foundation.layout.size
import com.dasein.poryadok.data.toIngr
import com.dasein.poryadok.logic.Cooking
import com.dasein.poryadok.logic.DIFFICULTY
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.MealType
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.CheckDot
import com.dasein.poryadok.ui.common.ConfirmDialog
import com.dasein.poryadok.ui.common.DatePickDialog
import com.dasein.poryadok.ui.common.FieldButton
import com.dasein.poryadok.ui.common.FullCenter
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.HGap
import com.dasein.poryadok.ui.common.Ic
import com.dasein.poryadok.ui.common.IconAction
import com.dasein.poryadok.ui.common.NumberField
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Segments
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.num
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.common.plain
import com.dasein.poryadok.ui.common.rememberImage
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.launch
import android.widget.Toast
import kotlin.math.roundToInt

@Composable
fun RecipeDetailScreen(nav: NavHostController, id: Long) {
    val x = Graph.extra
    val recipe by observe(null, id) { x.recipe(id) }
    val baseIngr by observe(emptyList(), id) { x.ingredientsOf(id) }
    val steps by observe(emptyList(), id) { x.stepsOf(id) }
    val products by observe(emptyList()) { x.products() }
    val extra = LocalExtra.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var perServing by rememberSaveable { mutableStateOf(true) }
    var servings by rememberSaveable(id) { mutableStateOf(0) }
    var baseIdx by rememberSaveable(id) { mutableStateOf(-1) }
    var baseHave by rememberSaveable(id) { mutableStateOf("") }
    val done = remember(id) { mutableStateMapOf<Int, Boolean>() }
    // Отмеченные ингредиенты («уже достал») и таймеры этапов: номер этапа → когда закончится.
    val have = remember(id) { mutableStateMapOf<Int, Boolean>() }
    val timers = remember(id) { mutableStateMapOf<Int, Long>() }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timers.size) {
        while (timers.isNotEmpty()) {
            now = System.currentTimeMillis()
            timers.filterValues { it <= now }.keys.forEach { i ->
                timers.remove(i)
                done[i] = true
                Toast.makeText(ctx, "Таймер этапа ${i + 1} — готово!", Toast.LENGTH_LONG).show()
                runCatching {
                    val v = ctx.getSystemService(android.os.Vibrator::class.java)
                    if (android.os.Build.VERSION.SDK_INT >= 26) v?.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 200, 600), -1))
                    android.media.RingtoneManager.getRingtone(ctx, android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION))?.play()
                }
            }
            kotlinx.coroutines.delay(1000)
        }
    }
    // Пока готовите (отмечены этапы или идёт таймер), экран не гаснет.
    val view = androidx.compose.ui.platform.LocalView.current
    val cooking = done.isNotEmpty() || timers.isNotEmpty() || have.isNotEmpty()
    androidx.compose.runtime.DisposableEffect(cooking) {
        view.keepScreenOn = cooking
        onDispose { view.keepScreenOn = false }
    }
    val green = com.dasein.poryadok.ui.common.LikeGreen
    val swaps = remember(id) { mutableStateMapOf<Int, FoodProduct>() }
    var swapFor by remember { mutableStateOf<Int?>(null) }
    var addToMenu by remember { mutableStateOf(false) }
    var cook by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val r = recipe
    if (r == null) {
        Screen("Рецепт", onBack = { nav.popBackStack() }) { FullCenter { Text(btnLabel("Рецепт не найден или удалён"), color = extra.dim) } }
        return
    }
    val serv = if (servings == 0) r.servings else servings
    val ingr = baseIngr.mapIndexed { i, ing ->
        swaps[i]?.let { p -> ing.copy(productId = p.id, name = p.name, kcal100 = p.kcal, protein100 = p.protein, fat100 = p.fat, carbs100 = p.carbs, fiber100 = p.fiber, shopCategory = p.category) } ?: ing
    }
    val calcList = ingr
    val calcBase = ingr.getOrNull(baseIdx)?.toIngr()
    val baseFactor = calcBase?.let { b -> baseHave.num()?.let { Cooking.factorFor(b, it) } }
    // С весом порции «порций» в блюде столько, сколько раз порция помещается в вес блюда.
    val exact = RecipeRepo.exactServings(baseIngr, r)
    val servD: Double = baseFactor?.let { exact * it } ?: (serv * exact / r.servings.coerceAtLeast(1))
    val factor = baseFactor ?: (serv / r.servings.toDouble())
    val scaled = Cooking.scale(ingr.map { it.toIngr() }, factor)
    val total = Cooking.total(scaled)
    val shown = if (perServing) total / servD else total

    Screen(
        title = r.name,
        onBack = { nav.popBackStack() },
        actions = {
            IconAction(Ic.heart, if (r.favorite) "Убрать из избранного" else "В избранное") { io { x.setFavorite(r.id, !r.favorite) } }
            IconAction(Ic.archive, if (r.archived) "Вернуть из архива" else "Убрать в архив") {
                io { x.setArchived(r.id, !r.archived) }
                Toast.makeText(ctx, if (r.archived) "Рецепт возвращён в каталог" else "Рецепт в архиве — вернуть можно в каталоге, категория «Архив»", Toast.LENGTH_LONG).show()
            }
            IconAction(Ic.sliders, "Изменить") { nav.navigate(Routes.recipeEdit(r.id)) }
        },
    ) { pad ->
        val scroll = rememberScrollState()
        var videoY by remember { mutableStateOf(0) }
        Column(Modifier.padding(pad).verticalScroll(scroll).padding(horizontal = 16.dp)) {
            if (r.archived) {
                Tile(color = extra.warn.copy(alpha = .18f)) {
                    Text(btnLabel("Рецепт в архиве"), fontWeight = FontWeight.SemiBold)
                    Text("Он не показывается в каталоге и подборе меню.", fontSize = 13.sp, color = extra.dim)
                    TextButton(onClick = { io { x.setArchived(r.id, false) } }) { Text(btnLabel("Вернуть из архива")) }
                }
                Gap(10.dp)
            }
            val img by rememberImage(r.photo.takeIf { it.isNotBlank() && !it.startsWith("dish/") }, 1200)
            img?.let {
                Image(it, null, Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Crop)
                Gap(10.dp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (img == null) { RecipeThumb(r, if (r.photo.startsWith("dish/")) 96.dp else 56.dp); HGap(12.dp) }
                Column(Modifier.weight(1f)) {
                    Text(r.category + if (r.custom) " · мой рецепт" else "", color = extra.dim, fontSize = 13.sp)
                    Text(
                        listOf(
                            "Подготовка ${minutes(r.prepMin)}", if (r.cookMin > 0) "готовка ${minutes(r.cookMin)}" else "без готовки",
                            DIFFICULTY.getOrElse(r.difficulty - 1) { "" }.lowercase(),
                        ).joinToString(" · "),
                        fontSize = 13.sp,
                    )
                    val meals = MealType.parse(r.meals)
                    if (meals.isNotEmpty()) Text(btnLabel("Подходит: ") + meals.joinToString { MealType.name(it).lowercase() }, fontSize = 12.sp, color = extra.dim)
                }
            }
            val videoCount = RecipeVideos.list(r).size
            Text(
                if (videoCount > 0) "▶ Видео рецепта: $videoCount — смотреть" else "▶ Добавить видео рецепта",
                color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).clickable { scope.launch { scroll.animateScrollTo(videoY) } }.padding(vertical = 4.dp),
            )
            if (r.tagList().isNotEmpty()) {
                Gap(8.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    r.tagList().forEach { Text("#$it", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) }
                }
            }

            Gap(12.dp)
            Tile {
                Segments(listOf(true to "На 1 порцию", false to "На весь рецепт"), perServing, { perServing = it })
                Gap(10.dp)
                MacroLine(shown, showFiber = true)
                if (r.portionGrams > 0) {
                    val dishW = (if (r.dishGrams > 0) r.dishGrams else Cooking.rawGrams(baseIngr.map { it.toIngr() })) * factor
                    Text(
                        if (perServing) "Порция ${Cooking.amount(r.portionGrams)} г" else "Всё блюдо ≈ ${Cooking.amount(Math.round(dishW).toDouble())} г",
                        fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Gap(10.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(btnLabel("Порций"), Modifier.weight(1f))
                    Stepper(serv) { servings = it.coerceIn(1, 40) }
                }
                if (swaps.isNotEmpty()) Text(btnLabel("С учётом замен ингредиентов"), fontSize = 12.sp, color = extra.warn, modifier = Modifier.padding(top = 6.dp))
            }

            SectionTitle("Калькулятор граммовки")
            Tile {
                Hint("recipe_calc", "Сколько у вас основного продукта? Остальное пересчитается в тех же пропорциях, КБЖУ — тоже.", title = "Как работает калькулятор")
                Gap(6.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    calcList.forEachIndexed { i, ing ->
                        if (ing.unit != "по вкусу" && (ing.grams > 0 || ing.amount > 0)) Pill(ing.name, baseIdx == i) { baseIdx = if (baseIdx == i) -1 else i }
                    }
                }
                if (calcBase != null) {
                    Gap(6.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NumberField(baseHave, { baseHave = it }, "У меня есть", Modifier.weight(1f), suffix = if (calcBase.unit == "шт") "шт" else if (calcBase.unit == "мл" || calcBase.unit == "л") "мл" else "г")
                        TextButton(onClick = { baseIdx = -1; baseHave = "" }) { Text(btnLabel("Сброс")) }
                    }
                    Text(
                        "По рецепту: ${Cooking.label(calcBase)}" + if (calcBase.unit != "г" && calcBase.grams > 0) " (${Cooking.amount(calcBase.grams)} г)" else "",
                        fontSize = 12.sp, color = extra.dim,
                    )
                    if (baseFactor != null) Text(
                        "Получится ≈ ${Cooking.amount(servD)} порц. · всего ${Cooking.amount(scaled.sumOf { it.grams })} г · ${total.kcal.roundToInt()} ккал",
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            SectionTitle(if (baseFactor != null) "Ингредиенты на ≈ ${Cooking.amount(servD)} порц." else "Ингредиенты на $serv порц.")
            Tile(padding = 8.dp) {
                scaled.forEachIndexed { i, ing ->
                    val hasSwap = Cooking.SWAPS.containsKey(baseIngr.getOrNull(i)?.name) || swaps.containsKey(i)
                    val got = have[i] == true
                    // Нажатие отмечает ингредиент зелёным — «уже на столе».
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (got) green.copy(alpha = .18f) else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { have[i] = !got }.padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (got) "✓ " else "", color = green, fontWeight = FontWeight.Bold)
                        Text(ing.name, Modifier.weight(1f), color = if (swaps.containsKey(i)) extra.warn else MaterialTheme.colorScheme.onSurface)
                        Text(Cooking.label(ing), color = extra.dim, fontSize = 14.sp)
                        if (hasSwap) Text(
                            "замена", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(8.dp)).clickable { swapFor = i }.padding(4.dp),
                        )
                    }
                }
            }

            // «Описание» — ваши заметки к блюду (сюда же попадает текст рецепта при вставке из Instagram). Пишется прямо здесь.
            Gap(8.dp)
            RecipeDescription(r)

            // Шаги: кратко, как в рецепте, или подробно для новичка (расписывает ИИ, сохраняется на телефоне).
            val ingrLines = remember(baseIngr) { baseIngr.map { listOf(it.name, Cooking.label(it.toIngr())).joinToString(" — ") } }
            val srcSteps = remember(steps) { steps.map { it.text } }
            var detailed by com.dasein.poryadok.ui.common.rememberUiFlag("recipe_detailed", true)
            var detail by remember(id, srcSteps, ingrLines) { mutableStateOf(com.dasein.poryadok.system.RecipeDetail.saved(ctx, id, srcSteps, ingrLines)) }
            var detailBusy by remember(id) { mutableStateOf(false) }
            var detailError by remember(id) { mutableStateOf<String?>(null) }
            val aiOn = remember { com.dasein.poryadok.system.Ai.enabled(ctx) }
            fun makeDetail() {
                if (detailBusy || srcSteps.isEmpty()) return
                detailBusy = true; detailError = null
                scope.launch {
                    val res = runCatching { com.dasein.poryadok.system.RecipeDetail.make(ctx, id, r.name, r.servings, ingrLines, srcSteps) }
                    detailBusy = false
                    res.onSuccess { detail = it; done.clear(); timers.clear() }.onFailure { detailError = it.message ?: "Не получилось" }
                }
            }
            LaunchedEffect(detailed, srcSteps) {
                if (detailed && detail == null && aiOn && srcSteps.isNotEmpty() && com.dasein.poryadok.system.Offline.hasNetwork(ctx)) makeDetail()
            }
            val shown: List<Pair<String, Int>> = if (detailed && detail != null) detail!!.map { it.text to it.minutes }
                else steps.map { it.text to it.minutes }
            val doneCount = shown.indices.count { done[it] == true }
            SectionTitle(
                if (shown.isEmpty()) "Приготовление" else "Приготовление · $doneCount из ${shown.size}",
                action = if (done.isNotEmpty() || have.isNotEmpty()) "Сбросить" else null,
            ) { done.clear(); have.clear(); timers.clear() }
            if (steps.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                    Pill("Подробно для новичка", detailed) { if (!detailed) { detailed = true; done.clear(); timers.clear() } }
                    Pill("Кратко", !detailed) { if (detailed) { detailed = false; done.clear(); timers.clear() } }
                }
                if (detailed && detail == null) Tile(Modifier.padding(bottom = 8.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .08f)) {
                    when {
                        detailBusy -> Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("  ИИ расписывает шаги подробно… обычно 10–30 секунд", fontSize = 13.sp)
                        }
                        !aiOn -> Text(
                            "Подробные шаги расписывает бесплатный ИИ. Добавьте ключ в «Настройки» → «ИИ-помощник». А пока — подсказки новичку под каждым шагом.",
                            fontSize = 13.sp, color = extra.dim,
                        )
                        else -> Column {
                            Text(detailError ?: "Подробные шаги появятся, когда будет интернет. Пока — подсказки новичку под каждым шагом.", fontSize = 13.sp, color = if (detailError != null) extra.danger else extra.dim)
                            TextButton(onClick = { makeDetail() }) { Text(btnLabel("Расписать подробно")) }
                        }
                    }
                }
                Text("Нажмите на этап, когда выполните его, — он загорится зелёным. Пока готовите, экран не гаснет.", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(bottom = 6.dp))
            }
            // Подсказки новичку (без интернета): каждая — один раз, у первого шага, где встречается.
            val hints = remember(shown) {
                val seen = HashSet<String>()
                shown.map { (t, _) -> com.dasein.poryadok.logic.CookingHints.forStep(t, 3).filter { seen.add(it) }.take(2) }
            }
            val showHints = !(detailed && detail != null)
            shown.forEachIndexed { i, (stepText, stepMin) ->
                val isDone = done[i] == true
                val mins = stepMin.takeIf { it > 0 } ?: com.dasein.poryadok.logic.RecipeText.minutesIn(stepText)
                Tile(Modifier.padding(bottom = 8.dp), onClick = { done[i] = !isDone }, color = if (isDone) green.copy(alpha = .22f) else extra.card) {
                    Row {
                        CheckDot(isDone, green, onClick = { done[i] = !isDone }, size = 24.dp)
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Text(if (isDone) "Этап ${i + 1} · готово" else "Этап ${i + 1}", fontSize = 12.sp, color = if (isDone) green else extra.dim, fontWeight = if (isDone) FontWeight.SemiBold else FontWeight.Normal)
                            Text(stepText, color = MaterialTheme.colorScheme.onSurface, lineHeight = 21.sp)
                            if (showHints && !isDone) hints.getOrNull(i).orEmpty().forEach { h ->
                                Text(
                                    "Подсказка: $h", fontSize = 13.sp, lineHeight = 18.sp, color = extra.dim,
                                    modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = .07f)).padding(8.dp),
                                )
                            }
                            if (mins > 0 && !isDone) {
                                val end = timers[i]
                                val label = if (end != null) {
                                    val left = ((end - now) / 1000).coerceAtLeast(0)
                                    "⏱ %d:%02d · стоп".format(left / 60, left % 60)
                                } else "⏱ Таймер $mins мин"
                                Text(
                                    label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = .12f))
                                        .clickable { if (end != null) timers.remove(i) else { timers[i] = System.currentTimeMillis() + mins * 60_000L; now = System.currentTimeMillis() } }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
            if (detailed && detail != null) TextButton(onClick = { com.dasein.poryadok.system.RecipeDetail.forget(ctx, id); detail = null; makeDetail() }, enabled = !detailBusy) {
                Text(if (detailBusy) "Расписываю заново…" else "Расписать заново")
            }
            if (shown.isNotEmpty() && doneCount == shown.size) Text(btnLabel("Готово! Приятного аппетита 🍽"), color = green, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
            if (steps.isEmpty()) Text(btnLabel("Шаги не добавлены"), color = extra.dim)
            Column(Modifier.onGloballyPositioned { videoY = it.positionInParent().y.toInt() }) { RecipeVideosSection(r) }

            RecipeRating(r.id)

            if (r.tips.isNotBlank()) { SectionTitle("Советы"); Text(r.tips) }
            val swapList = baseIngr.mapNotNull { ing -> Cooking.SWAPS[ing.name]?.let { ing.name to it } }
            if (swapList.isNotEmpty() || r.swaps.isNotBlank()) {
                SectionTitle("Замены ингредиентов")
                swapList.forEach { (from, to) -> Text("$from → ${to.joinToString(", ")}", fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp)) }
                if (r.swaps.isNotBlank()) Text(r.swaps, fontSize = 14.sp)
            }
            SectionTitle("Пищевая ценность")
            Tile {
                val per100 = scaled.sumOf { it.grams }.takeIf { it > 0 }?.let { total * (100 / it) }
                Text("Порция (${Cooking.amount(scaled.sumOf { it.grams } / servD)} г)", fontSize = 13.sp, color = extra.dim)
                MacroLine(total / servD, showFiber = true)
                if (per100 != null) {
                    Gap(8.dp)
                    Text(btnLabel("На 100 г"), fontSize = 13.sp, color = extra.dim)
                    MacroLine(per100, showFiber = true)
                }
                Text("Расчёт по базе продуктов, значения приблизительные.", fontSize = 11.sp, color = extra.dim, modifier = Modifier.padding(top = 8.dp))
            }

            Gap(16.dp)
            Row {
                Button(onClick = { addToMenu = true }, Modifier.weight(1f)) { Text(btnLabel("В меню")) }
                HGap(8.dp)
                Button(onClick = { cook = true }, Modifier.weight(1f)) { Text(btnLabel("Приготовить")) }
            }
            Gap(8.dp)
            OutlinedButton(onClick = {
                scope.launch {
                    RecipeRepo.addToShopping(r.id, factor)
                    Toast.makeText(ctx, "Ингредиенты добавлены в список покупок", Toast.LENGTH_SHORT).show()
                }
            }, Modifier.fillMaxWidth()) { Text(btnLabel("Ингредиенты в список покупок")) }
            OutlinedButton(onClick = {
                io { x.setArchived(r.id, !r.archived) }
                Toast.makeText(ctx, if (r.archived) "Рецепт возвращён в каталог" else "Рецепт в архиве — вернуть можно в каталоге, категория «Архив»", Toast.LENGTH_LONG).show()
            }, Modifier.fillMaxWidth()) { Text(if (r.archived) "Вернуть из архива" else "Убрать в архив") }
            Row {
                OutlinedButton(onClick = { io { x.setFavorite(r.id, !r.favorite) } }, Modifier.weight(1f)) { Text(if (r.favorite) "В избранном ✓" else "В избранное") }
                HGap(8.dp)
                OutlinedButton(onClick = { nav.navigate(Routes.recipeEdit(r.id)) }, Modifier.weight(1f)) { Text(btnLabel("Изменить")) }
            }
            Row {
                TextButton(onClick = {
                    scope.launch { RecipeRepo.duplicate(r.id)?.let { nav.navigate(Routes.recipeEdit(it)) } }
                }) { Text(if (swaps.isEmpty()) "Сделать копию" else "Копия") }
                if (swaps.isNotEmpty()) TextButton(onClick = {
                    scope.launch {
                        val newId = RecipeRepo.saveRecipe(
                            r.copy(id = 0, name = r.name + " (с заменами)", custom = true, seedKey = null, favorite = false, createdAt = 0, archived = false),
                            ingr, steps.map { it.text },
                        )
                        nav.navigate(Routes.recipe(newId))
                    }
                }) { Text(btnLabel("Сохранить с заменами")) }
                if (r.custom) TextButton(onClick = { confirmDelete = true }) { Text(btnLabel("Удалить"), color = extra.danger) }
            }
            HowTo("recipe")
            Gap(40.dp)
        }
    }

    swapFor?.let { i ->
        val from = baseIngr.getOrNull(i)
        val options = Cooking.SWAPS[from?.name].orEmpty().mapNotNull { n -> products.firstOrNull { it.name == n } }
        AlertDialog(
            onDismissRequest = { swapFor = null },
            title = { Text("Заменить: ${from?.name}") },
            text = {
                Column {
                    Text("КБЖУ пересчитается. Рецепт не изменится, пока вы не сохраните копию.", fontSize = 13.sp, color = extra.dim)
                    Gap(8.dp)
                    options.forEach { p ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { swaps[i] = p; swapFor = null }.padding(10.dp)) {
                            Text(p.name, Modifier.weight(1f))
                            Text("${p.kcal.roundToInt()} ккал · Б ${p.protein.plain()}", fontSize = 12.sp, color = extra.dim)
                        }
                    }
                    if (options.isEmpty()) Text(btnLabel("Для этого продукта нет готовых замен."), color = extra.dim)
                }
            },
            confirmButton = { if (swaps.containsKey(i)) TextButton(onClick = { swaps.remove(i); swapFor = null }) { Text(btnLabel("Вернуть исходный")) } },
            dismissButton = { TextButton(onClick = { swapFor = null }) { Text(btnLabel("Закрыть")) } },
        )
    }
    if (addToMenu) AddToMenuDialog(r.id, r.meals, onDismiss = { addToMenu = false }) { msg -> Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
    if (cook) CookDialog(r.id, serv, r.meals, onDismiss = { cook = false }) { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() }
    if (confirmDelete) ConfirmDialog("Удалить рецепт?", "Рецепт исчезнет из базы. Уже запланированные блюда останутся в меню.", onDismiss = { confirmDelete = false }) {
        scope.launch { RecipeRepo.deleteRecipe(r.id); nav.popBackStack() }
    }
}

@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { onChange(value - 1) }, Modifier.width(48.dp), contentPadding = PaddingValues(0.dp)) { Text(btnLabel("−")) }
        Text("$value", Modifier.width(40.dp), fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        OutlinedButton(onClick = { onChange(value + 1) }, Modifier.width(48.dp), contentPadding = PaddingValues(0.dp)) { Text(btnLabel("+")) }
    }
}

private const val DAYS_ALL = 127

/** Добавить блюдо в меню: день, приём пищи, порции и при желании — повтор по дням недели. */
@Composable
fun AddToMenuDialog(recipeId: Long, mealsCsv: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val suggested = MealType.parse(mealsCsv).firstOrNull() ?: MealType.LUNCH
    var day by remember { mutableStateOf(Dates.today()) }
    var meal by remember { mutableStateOf(suggested) }
    var servings by remember { mutableStateOf("1") }
    var repeat by remember { mutableStateOf(false) }
    var mask by remember { mutableStateOf(DAYS_ALL) }
    var pickDate by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(btnLabel("Добавить в меню")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FieldButton(if (repeat) "Начиная с" else "День", "${Dates.weekdayShort(day)}, ${Dates.label(day)}", Modifier.fillMaxWidth()) { pickDate = true }
                Gap(8.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MealType.order.forEach { m -> Pill(MealType.name(m), m == meal) { meal = m } }
                }
                Gap(8.dp)
                NumberField(servings, { servings = it }, "Порций")
                Gap(8.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(btnLabel("Повторять по дням недели"), Modifier.weight(1f))
                    Switch(repeat, { repeat = it })
                }
                if (repeat) WeekdayMask(mask) { mask = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val s = servings.num()?.takeIf { it > 0 } ?: 1.0
                scope.launch {
                    if (repeat) {
                        RecipeRepo.saveRepeat(MealRepeat(recipeId = recipeId, meal = meal, daysMask = mask, fromDay = day, servings = s))
                        onDone("Блюдо будет в меню по выбранным дням")
                    } else {
                        RecipeRepo.addToPlan(day, meal, recipeId, s)
                        onDone("Добавлено: ${MealType.name(meal).lowercase()}, ${Dates.label(day)}")
                    }
                    onDismiss()
                }
            }) { Text(btnLabel("Добавить")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) } },
    )
    if (pickDate) DatePickDialog(day, { pickDate = false }, { it?.let { d -> day = d } }, allowClear = false)
}

private val WEEKDAYS = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

@Composable
fun WeekdayMask(mask: Int, onChange: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        WEEKDAYS.forEachIndexed { i, d ->
            val on = mask and (1 shl i) != 0
            Pill(d, on) { onChange(mask xor (1 shl i)) }
        }
    }
}

fun maskLabel(mask: Int): String = when (mask) {
    127 -> "каждый день"
    31 -> "по будням"
    96 -> "по выходным"
    else -> WEEKDAYS.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.joinToString(", ")
}

/**
 * «Приготовить»: сначала навеска основного продукта (например, курицы) — остальные ингредиенты
 * пересчитываются в той же пропорции; всё, что меньше чайной ложки, показывается в граммах.
 * Затем — сколько порций получилось и сколько съедено; запись в историю и по желанию в дневник питания.
 */
@Composable
fun CookDialog(recipeId: Long, servings: Int, mealsCsv: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val extra = LocalExtra.current
    val x = Graph.extra
    val recipe by observe(null, recipeId) { x.recipe(recipeId) }
    val ingredients by observe(emptyList(), recipeId) { x.ingredientsOf(recipeId) }
    val list = remember(ingredients) { ingredients.map { it.toIngr() } }
    var baseIdx by remember { mutableStateOf(-2) }
    var have by remember { mutableStateOf("") }
    var cooked by remember { mutableStateOf(servings.toString()) }
    var cookedTouched by remember { mutableStateOf(false) }
    var eaten by remember { mutableStateOf("1") }
    var meal by remember { mutableStateOf(MealType.parse(mealsCsv).firstOrNull() ?: MealType.LUNCH) }
    var toFood by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val r = recipe
    // Основной продукт и навеска по умолчанию — как в рецепте на выбранное число порций.
    LaunchedEffect(list, r) {
        if (baseIdx == -2 && list.isNotEmpty() && r != null) {
            baseIdx = Cooking.mainIndex(list)
            list.getOrNull(baseIdx)?.let { b -> have = Cooking.amount(b.grams * servings / r.servings.coerceAtLeast(1)) }
        }
    }
    val base = list.getOrNull(baseIdx)
    val factor = base?.let { b -> have.num()?.let { Cooking.factorFor(b.copy(unit = "г", amount = b.grams), it) } }
    val autoServ = if (factor != null && r != null) r.servings * factor else null
    LaunchedEffect(autoServ) { if (!cookedTouched && autoServ != null) cooked = Cooking.amount((autoServ * 10).roundToInt() / 10.0) }
    val scaled = factor?.let { Cooking.scale(list, it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(btnLabel("Приготовить")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(btnLabel("Основной продукт"), fontSize = 12.sp, color = extra.dim)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    list.forEachIndexed { i, ing ->
                        if (ing.unit != "по вкусу" && ing.grams > 0) Pill(ing.name, i == baseIdx) {
                            baseIdx = i
                            r?.let { rr -> have = Cooking.amount(ing.grams * servings / rr.servings.coerceAtLeast(1)) }
                        }
                    }
                }
                if (base != null) {
                    NumberField(have, { have = it }, "Навеска: ${base.name}", suffix = "г")
                    Text(
                        "По рецепту ${Cooking.amount(base.grams)} г на ${r?.servings ?: 1} порц. Взвесьте продукт — остальное пересчитается.",
                        fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (scaled != null) {
                    Gap(8.dp)
                    Text(btnLabel("Сколько положить"), fontWeight = FontWeight.SemiBold)
                    scaled.forEachIndexed { i, ing ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(ing.name, Modifier.weight(1f), fontSize = 14.sp, fontWeight = if (i == baseIdx) FontWeight.SemiBold else FontWeight.Normal)
                            Text(Cooking.weighLabel(ing), fontSize = 14.sp, color = if (i == baseIdx) MaterialTheme.colorScheme.primary else extra.dim)
                        }
                    }
                    val total = scaled.sumOf { it.grams }
                    Text(
                        "Всего ≈ ${Cooking.amount(total)} г сырых продуктов · ${Cooking.total(scaled).kcal.roundToInt()} ккал",
                        fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp),
                    )
                    Text("Меньше чайной ложки — в граммах, для кухонных весов.", fontSize = 11.sp, color = extra.dim)
                }
                Gap(10.dp)
                Row {
                    NumberField(cooked, { cooked = it; cookedTouched = true }, "Получилось порций", Modifier.weight(1f))
                    HGap(8.dp)
                    NumberField(eaten, { eaten = it }, "Съедено порций", Modifier.weight(1f))
                }
                if (scaled != null) cooked.num()?.takeIf { it > 0 }?.let { c ->
                    Text(
                        "Порция ≈ ${Cooking.amount(scaled.sumOf { it.grams } / c)} г · ${(Cooking.total(scaled).kcal / c).roundToInt()} ккал",
                        fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Gap(8.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MealType.order.forEach { m -> Pill(MealType.name(m), m == meal) { meal = m } }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text(btnLabel("Добавить в питание за сегодня"), Modifier.weight(1f))
                    Switch(toFood, { toFood = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    RecipeRepo.cook(recipeId, cooked.num() ?: servings.toDouble(), eaten.num() ?: 0.0, meal, toFood, factor = factor)
                    onDone(if (toFood) "Записано в историю и в питание" else "Записано в историю готовки")
                    onDismiss()
                }
            }) { Text(btnLabel("Сохранить")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) } },
    )
}


/** Своя оценка рецепта: 1–5 звёзд (хранится на телефоне). */
@Composable
private fun RecipeRating(id: Long) {
    val ctx = LocalContext.current
    val sp = remember { ctx.getSharedPreferences("ui_state", android.content.Context.MODE_PRIVATE) }
    var rate by remember(id) { mutableStateOf(sp.getInt("recipe_rate_$id", 0)) }
    SectionTitle("Моя оценка")
    Row(verticalAlignment = Alignment.CenterVertically) {
        (1..5).forEach { k ->
            Text(
                if (k <= rate) "★" else "☆", fontSize = 30.sp, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                    rate = if (rate == k) 0 else k
                    sp.edit().putInt("recipe_rate_$id", rate).apply()
                }.padding(horizontal = 4.dp),
            )
        }
        if (rate > 0) Text(btnLabel("  ") + listOf("", "не понравилось", "так себе", "нормально", "вкусно", "шедевр")[rate], fontSize = 13.sp, color = LocalExtra.current.dim)
    }
}


/**
 * «Описание» в карточке блюда: красиво оформленный текст (заголовки, списки, жирный). «Изменить» — правка
 * обычным текстом, сохраняется сама. «Оформить с ИИ» — ИИ делает из текста рецепта аккуратное описание.
 */
@Composable
private fun RecipeDescription(r: Recipe) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    var text by remember(r.id) { mutableStateOf(r.notes) }
    var editing by remember(r.id) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val latest by androidx.compose.runtime.rememberUpdatedState(r)
    // Сохраняем через полсекунды после набора.
    LaunchedEffect(text) {
        if (text == latest.notes) return@LaunchedEffect
        kotlinx.coroutines.delay(600)
        Graph.extra.upsertRecipe(latest.copy(notes = text))
    }
    com.dasein.poryadok.ui.common.FoldTile(
        "recipe_desc", "Описание",
        summary = text.lineSequence().map { it.trim().trimStart('#', ' ') }.firstOrNull { it.isNotBlank() }?.take(60) ?: "ваши заметки к блюду",
    ) {
        if (editing || text.isBlank()) {
            androidx.compose.material3.OutlinedTextField(
                text, { text = it }, Modifier.fillMaxWidth().padding(bottom = 6.dp), minLines = 4,
                placeholder = { Text("Ваши заметки: что изменить в следующий раз, как получилось, с чем подать… Можно оформлять: «## Раздел», «- пункт», **жирный**.", color = extra.dim) },
                shape = RoundedCornerShape(14.dp),
            )
        } else com.dasein.poryadok.ui.common.RichText(text, Modifier.padding(bottom = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            if (text.isNotBlank()) TextButton(onClick = { editing = !editing }) { Text(if (editing) "Готово" else "✎ Изменить") }
            if (com.dasein.poryadok.system.Ai.enabled(ctx)) TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    val src = text.ifBlank {
                        val ing = Graph.extra.ingredientsOfNow(r.id).joinToString("\n") { "- ${it.name} ${Cooking.label(it.toIngr())}" }
                        val st = Graph.extra.stepsOfNow(r.id).mapIndexed { i, s -> "${i + 1}. ${s.text}" }.joinToString("\n")
                        "Ингредиенты:\n$ing\nПриготовление:\n$st"
                    }
                    runCatching { com.dasein.poryadok.system.RecipeLink.prettyDescription(ctx, src, r.name) }
                        .onSuccess { if (it.length > 40) { text = it; editing = false } }
                        .onFailure { android.widget.Toast.makeText(ctx, it.message ?: "Не получилось", android.widget.Toast.LENGTH_LONG).show() }
                    busy = false
                }
            }) { com.dasein.poryadok.ui.common.AiLabel("Оформить с ИИ", busy, "ИИ оформляет…") }
        }
    }
}
