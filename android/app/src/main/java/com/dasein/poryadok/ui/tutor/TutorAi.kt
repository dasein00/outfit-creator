@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.tutor

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.Tutor
import com.dasein.poryadok.system.Ai
import com.dasein.poryadok.system.Ai.arr
import com.dasein.poryadok.system.Ai.str
import com.dasein.poryadok.system.TutorStore
import com.dasein.poryadok.ui.common.Button
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.OutlinedButton
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.RichText
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.TextButton
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

const val TUTOR_CARDS_ROUTE = "tutorCards"
const val TUTOR_CHAT_ROUTE = "tutorChat"
fun tutorChat(tab: Int) = "$TUTOR_CHAT_ROUTE?tab=$tab"

/** Оценка карточки (как в Anki): «Снова», «Трудно», «Хорошо», «Легко». */
private enum class Grade(val title: String) { AGAIN("Снова"), HARD("Трудно"), GOOD("Хорошо"), EASY("Легко") }

/** Следующий интервал по кривой забывания: коробки Лейтнера с поправкой на оценку. */
private fun grade(p: Tutor.Progress?, g: Grade, today: Long): Tutor.Progress {
    val cur = p ?: Tutor.Progress(added = today)
    val last = Tutor.INTERVALS.size - 1
    return when (g) {
        Grade.AGAIN -> cur.copy(box = 1, due = today, wrong = cur.wrong + 1)
        Grade.HARD -> cur.copy(box = cur.box.coerceAtLeast(1), due = today + maxOf(1, Tutor.INTERVALS[cur.box.coerceIn(1, last)] / 2), right = cur.right + 1)
        Grade.GOOD -> (cur.box + 1).coerceAtMost(last).let { b -> cur.copy(box = b, due = today + Tutor.INTERVALS[b], right = cur.right + 1) }
        Grade.EASY -> (cur.box + 2).coerceAtMost(last).let { b -> cur.copy(box = b, due = today + Tutor.INTERVALS[b], right = cur.right + 1) }
    }
}

private fun intervalText(p: Tutor.Progress?, g: Grade, today: Long): String {
    val d = grade(p, g, today).due - today
    return when { d <= 0L -> "сегодня"; d == 1L -> "1 дн."; d < 30 -> "$d дн."; else -> "${d / 30} мес." }
}

/**
 * Карточки по кривой забывания (как в Anki): слово — вспомнить — открыть ответ — оценить.
 * Чем легче вспомнилось, тем позже карточка вернётся. Слова из «Переводчика» («+ В мои слова») попадают сюда сразу.
 */
@Composable
fun TutorCardsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    val sp = rememberSpeaker()
    val st by TutorStore.flow(ctx).collectAsState()
    val s = st ?: TutorStore.State()
    val today = Dates.today()
    var onlyMine by remember { mutableStateOf(s.custom.isNotEmpty()) }
    val words = remember(s.custom) { TutorStore.words(ctx, s) }
    val mine = remember(s.custom) { s.custom.map { it.en.lowercase() }.toSet() }
    // Колода на этот заход фиксируется, чтобы карточки не прыгали после оценки.
    var deck by remember(onlyMine) { mutableStateOf<List<Tutor.Word>?>(null) }
    LaunchedEffect(onlyMine, words) {
        if (deck == null) deck = Tutor.due(words, s.progress, today).filter { !onlyMine || it.id in mine }.shuffled()
    }
    var pos by remember(onlyMine) { mutableStateOf(0) }
    var open by remember { mutableStateOf(false) }
    val list = deck.orEmpty()
    Screen("Карточки", onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("Мои слова · ${s.custom.size}", onlyMine) { onlyMine = true; deck = null; pos = 0; open = false }
                Pill("Все на повторение", !onlyMine) { onlyMine = false; deck = null; pos = 0; open = false }
            }
            Gap(8.dp)
            val w = list.getOrNull(pos)
            if (w == null) {
                Gap(40.dp)
                Text(if (list.isEmpty()) "На сегодня карточек нет" else "Готово! Все карточки на сегодня пройдены", style = MaterialTheme.typography.titleLarge, modifier = Modifier.align(Alignment.CenterHorizontally))
                Text(
                    "Добавляйте слова из «Переводчика» кнопкой «+ В мои слова» — они появятся здесь. Повторы приходят по кривой забывания: через день, 2, 4, 7, 15 дней и дальше.",
                    fontSize = 13.sp, color = extra.dim, modifier = Modifier.padding(top = 8.dp),
                )
                return@Column
            }
            Text("Карточка ${pos + 1} из ${list.size}", fontSize = 12.sp, color = extra.dim)
            val front = if (s.settings.enFirst) w.en else w.ru
            Column(
                Modifier.fillMaxWidth().weight(1f).padding(vertical = 10.dp).clip(RoundedCornerShape(24.dp)).background(extra.card)
                    .clickable { open = true }.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Text(front, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                if (s.settings.enFirst) SpeakButton(sp, w.en)
                if (open) {
                    Gap(16.dp)
                    Text(if (s.settings.enFirst) w.ru else w.en, fontSize = 22.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    if (!s.settings.enFirst) SpeakButton(sp, w.en)
                    if (w.ex.isNotBlank()) { Gap(10.dp); Text(w.ex, fontSize = 15.sp); if (w.exRu.isNotBlank()) Text(w.exRu, fontSize = 13.sp, color = extra.dim) }
                } else Text("Вспомните перевод и нажмите на карточку", fontSize = 13.sp, color = extra.dim, modifier = Modifier.padding(top = 16.dp))
            }
            if (!open) Button(onClick = { open = true }, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) { Text("Показать ответ") }
            else Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                Grade.entries.forEach { g ->
                    val p = s.progress[w.id]
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                            .background(if (g == Grade.AGAIN) extra.danger.copy(alpha = .18f) else MaterialTheme.colorScheme.primary.copy(alpha = if (g == Grade.GOOD) .25f else .12f))
                            .clickable {
                                scope.launch {
                                    TutorStore.update(ctx) { x ->
                                        val gain = if (g == Grade.AGAIN) 2 else 10
                                        x.copy(progress = x.progress + (w.id to grade(x.progress[w.id], g, today)), xp = x.xp + gain, xpByDay = x.xpByDay + (today to ((x.xpByDay[today] ?: 0) + gain)))
                                    }
                                    // «Снова» — карточка вернётся в конце этого захода.
                                    if (g == Grade.AGAIN) deck = list + w
                                    pos++; open = false
                                }
                            }.padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(g.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(intervalText(p, g, today), fontSize = 11.sp, color = extra.dim)
                    }
                }
            }
        }
    }
}

// ---------- Разговорный тренажёр и проверка текста ----------

private data class ChatMsg(val mine: Boolean, val text: String, val ru: String = "", val fix: String = "", val why: String = "")

private val SCENES = listOf(
    "Свободная тема" to "a friendly casual conversation about the user's day, hobbies and plans",
    "Знакомство" to "meeting a new person at a party",
    "Кафе" to "ordering food and drinks in a cafe (you are the waiter)",
    "Отель" to "checking in at a hotel (you are the receptionist)",
    "Аэропорт" to "airport check-in and security (you are the airline agent)",
    "Магазин" to "shopping for clothes (you are the shop assistant)",
    "Собеседование" to "a job interview (you are the interviewer)",
    "Врач" to "a visit to a doctor (you are the doctor)",
)

/**
 * Разговор на английском с ИИ (как Duolingo Max и Speak): ИИ играет роль в выбранной ситуации, отвечает просто
 * и исправляет ваши ошибки — с объяснением по-русски. Вкладка «Проверка» — разбор любого вашего текста по правилам.
 */
@Composable
fun TutorChatScreen(nav: NavHostController, initialTab: Int) {
    var tab by remember { mutableStateOf(initialTab) }
    Screen("Английский с ИИ", onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                Pill("Разговор", tab == 0) { tab = 0 }
                Pill("Проверка текста", tab == 1) { tab = 1 }
            }
            if (!Ai.enabled(LocalContext.current)) {
                Text("Нужен бесплатный ключ ИИ: «Настройки» → «ИИ-помощник».", color = LocalExtra.current.danger, modifier = Modifier.padding(16.dp))
                return@Column
            }
            if (tab == 0) ChatTab() else CheckTab()
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.ChatTab() {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    val sp = rememberSpeaker()
    var scene by remember { mutableStateOf(0) }
    val msgs = remember { mutableStateListOf<ChatMsg>() }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var rule by remember { mutableStateOf<Pair<String, String?>?>(null) }
    val listState = rememberLazyListState()
    fun system() = "You are a friendly English conversation partner for a Russian-speaking learner (level A2–B1). Scenario: ${SCENES[scene].second}. " +
        "Stay in role, reply in simple natural English in 1–3 short sentences and always end with a question to keep the conversation going. " +
        "Also check the learner's LAST message for grammar, word choice and spelling mistakes. " +
        "Answer strictly as JSON: {\"reply\":\"your English reply\",\"reply_ru\":\"its Russian translation\",\"fix\":\"the learner's last message corrected (empty if it was correct)\",\"why\":\"short explanation of the mistakes in Russian (empty if correct)\"}"
    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() && msgs.isNotEmpty()) return
        if (t.isNotEmpty()) msgs.add(ChatMsg(true, t))
        input = ""; busy = true; error = null
        scope.launch {
            val history = msgs.takeLast(12).joinToString("\n") { (if (it.mine) "Learner: " else "You: ") + it.text }
            val user = if (msgs.isEmpty()) "Start the conversation with a greeting and a first question." else "Conversation so far:\n$history"
            runCatching { Ai.extractJson(Ai.ask(ctx, system(), user)) ?: throw Ai.AiError("ИИ ответил не по формату") }
                .onSuccess { o ->
                    val fix = o.str("fix").orEmpty().trim()
                    val why = o.str("why").orEmpty().trim()
                    // Исправление — к последнему моему сообщению.
                    val last = msgs.indexOfLast { it.mine }
                    if (last >= 0 && fix.isNotEmpty() && !fix.equals(msgs[last].text, true)) msgs[last] = msgs[last].copy(fix = fix, why = why)
                    val reply = o.str("reply").orEmpty().trim()
                    if (reply.isNotEmpty()) { msgs.add(ChatMsg(false, reply, o.str("reply_ru").orEmpty())); sp.say(reply) }
                }
                .onFailure { error = it.message }
            busy = false
            listState.animateScrollToItem((msgs.size - 1).coerceAtLeast(0))
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        SCENES.forEachIndexed { i, (t, _) -> Pill(t, scene == i) { scene = i; msgs.clear(); error = null } }
    }
    LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (msgs.isEmpty()) item {
            Text("Выберите ситуацию и нажмите «Начать» — ИИ заговорит первым. Пишите по-английски как получится: ошибки он исправит и объяснит.", fontSize = 13.sp, color = extra.dim)
            Button(onClick = { send("") }, enabled = !busy, modifier = Modifier.padding(top = 8.dp)) { Text(if (busy) "Подключаюсь…" else "Начать") }
        }
        itemsIndexed(msgs) { _, m ->
            var showRu by remember { mutableStateOf(false) }
            Box(Modifier.fillMaxWidth(), contentAlignment = if (m.mine) Alignment.CenterEnd else Alignment.CenterStart) {
                Column(
                    Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (m.mine) MaterialTheme.colorScheme.primary.copy(alpha = .16f) else extra.card)
                        .clickable { showRu = !showRu }.padding(12.dp),
                ) {
                    Text(m.text, fontSize = 15.sp)
                    if (!m.mine) {
                        if (showRu && m.ru.isNotBlank()) Text(m.ru, fontSize = 13.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp))
                        Row {
                            SpeakButton(sp, m.text, 32)
                            if (m.ru.isNotBlank()) TextButton(onClick = { showRu = !showRu }) { Text(if (showRu) "Скрыть перевод" else "Перевод") }
                        }
                    }
                    if (m.fix.isNotBlank()) {
                        Text("Правильнее: ${m.fix}", fontSize = 13.sp, color = extra.ok, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                        if (m.why.isNotBlank()) Text(m.why, fontSize = 12.sp, color = extra.dim)
                        TextButton(onClick = { rule = (m.text + " → " + m.fix) to null }) { Text("Объяснить правило") }
                    }
                }
            }
        }
        if (busy && msgs.isNotEmpty()) item { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.padding(4.dp), strokeWidth = 2.dp); Text("  печатает…", color = extra.dim) } }
        error?.let { e -> item { Text(e, color = extra.danger, fontSize = 13.sp) } }
    }
    if (msgs.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("Ваш ответ по-английски") }, maxLines = 4, shape = RoundedCornerShape(16.dp))
        Button(onClick = { send(input) }, enabled = input.isNotBlank() && !busy, modifier = Modifier.padding(start = 8.dp)) { Text("↑") }
    }
    rule?.let { (q, _) -> GrammarDialog(q) { rule = null } }
}

/** Подробное объяснение правила по конкретной ошибке. */
@Composable
private fun GrammarDialog(mistake: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    var text by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(mistake) {
        text = runCatching { explainGrammar(ctx, mistake) }.getOrElse { "Не получилось: ${it.message}" }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Правило") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Text(mistake, fontSize = 13.sp, color = extra.dim)
                Gap(8.dp)
                text?.let { RichText(it) } ?: Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(strokeWidth = 2.dp); Text("  ИИ объясняет…") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Понятно") } },
    )
}

private suspend fun explainGrammar(ctx: Context, mistake: String): String = Ai.ask(
    ctx,
    "Ты преподаватель английского для русскоязычных. По ошибке ученика объясни правило простым языком на русском в Markdown: " +
        "«## Правило» — суть в 2–4 предложениях, «## Как правильно» — формула или схема, «## Примеры» — 4 примера (англ. — рус.), " +
        "«## Частая ловушка» — чем отличается от похожего случая в русском. Коротко, без воды.",
    "Ошибка: $mistake",
).trim()

private data class Mistake(val wrong: String, val right: String, val rule: String)

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.CheckTab() {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var corrected by remember { mutableStateOf<String?>(null) }
    var mistakes by remember { mutableStateOf<List<Mistake>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var rule by remember { mutableStateOf<String?>(null) }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Напишите по-английски что угодно — письмо, пост, рассказ о дне. ИИ исправит ошибки и объяснит каждую по правилу.", fontSize = 13.sp, color = extra.dim)
        OutlinedTextField(input, { input = it }, Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 4, shape = RoundedCornerShape(14.dp), placeholder = { Text("Yesterday I go to the cinema with my friends…") })
        Button(enabled = input.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), onClick = {
            busy = true; error = null
            scope.launch {
                runCatching {
                    val o: JsonObject = Ai.extractJson(
                        Ai.ask(
                            ctx,
                            "You are an English teacher for Russian speakers. Correct the learner's text. Answer strictly JSON: " +
                                "{\"corrected\":\"the whole corrected text\",\"mistakes\":[{\"wrong\":\"fragment with mistake\",\"right\":\"corrected fragment\",\"rule\":\"short explanation of the rule in Russian\"}]}. " +
                                "If there are no mistakes, return an empty list. Also fix unnatural word choice.",
                            input,
                        ),
                    ) ?: throw Ai.AiError("ИИ ответил не по формату")
                    corrected = o.str("corrected")
                    mistakes = o.arr("mistakes").orEmpty().mapNotNull { e ->
                        val m = e as? JsonObject ?: return@mapNotNull null
                        Mistake(m.str("wrong").orEmpty(), m.str("right").orEmpty(), m.str("rule").orEmpty())
                    }
                }.onFailure { error = it.message }
                busy = false
            }
        }) { Text(if (busy) "Проверяю…" else "Проверить") }
        error?.let { Text(it, color = extra.danger, fontSize = 13.sp) }
        corrected?.let { c ->
            SectionTitle(if (mistakes.isEmpty()) "Ошибок нет — отлично!" else "Исправленный текст")
            if (mistakes.isNotEmpty()) Text(c, fontSize = 15.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(extra.card).padding(12.dp))
            mistakes.forEachIndexed { i, m ->
                Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(extra.card).padding(12.dp)) {
                    Text("${i + 1}. ${m.wrong}  →  ${m.right}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(m.rule, fontSize = 13.sp, color = extra.dim, modifier = Modifier.padding(top = 2.dp))
                    TextButton(onClick = { rule = "${m.wrong} → ${m.right}" }) { Text("Объяснить правило подробнее") }
                }
            }
        }
    }
    rule?.let { GrammarDialog(it) { rule = null } }
}

// ---------- Примеры употребления ----------

private data class Example(val en: String, val ru: String, val note: String)

/**
 * Живые примеры употребления слова или фразы (как Reverso Context): 5–6 предложений из разных ситуаций
 * с переводом и пометкой, где так говорят. Работает с ключом ИИ, по кнопке.
 */
@Composable
internal fun AiExamples(word: String, sp: Speaker) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    if (!Ai.enabled(ctx)) return
    var list by remember(word) { mutableStateOf<List<Example>?>(null) }
    var busy by remember(word) { mutableStateOf(false) }
    var error by remember(word) { mutableStateOf<String?>(null) }
    if (list == null) {
        OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
            busy = true; error = null
            scope.launch {
                runCatching {
                    val o = Ai.extractJson(
                        Ai.ask(
                            ctx,
                            "You help Russian speakers learn English. Give 6 natural example sentences with the given English word or phrase as native speakers really use it, " +
                                "in different meanings and situations (everyday talk, work, news, informal). Answer strictly JSON: " +
                                "{\"examples\":[{\"en\":\"sentence\",\"ru\":\"natural Russian translation\",\"note\":\"where/how it's used, in Russian, 2-4 words\"}]}",
                            word,
                        ),
                    ) ?: throw Ai.AiError("ИИ ответил не по формату")
                    list = o.arr("examples").orEmpty().mapNotNull { e ->
                        val m = e as? JsonObject ?: return@mapNotNull null
                        Example(m.str("en").orEmpty(), m.str("ru").orEmpty(), m.str("note").orEmpty()).takeIf { it.en.isNotBlank() }
                    }
                }.onFailure { error = it.message }
                busy = false
            }
        }) { Text(if (busy) "ИИ подбирает примеры…" else "Примеры из живой речи (ИИ)") }
        error?.let { Text(it, color = extra.danger, fontSize = 12.sp) }
        return
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(extra.card).padding(12.dp)) {
        Text("Как говорят: «$word»", fontWeight = FontWeight.SemiBold)
        list.orEmpty().forEach { ex ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(ex.en, fontSize = 15.sp)
                    Text(ex.ru, fontSize = 13.sp, color = extra.dim)
                    if (ex.note.isNotBlank()) Text(ex.note, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
                SpeakButton(sp, ex.en, 32)
            }
        }
    }
}
