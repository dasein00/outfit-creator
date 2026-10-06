@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.tutor

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.Tutor
import com.dasein.poryadok.system.TutorStore
import com.dasein.poryadok.ui.common.Bar
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.HowTo
import com.dasein.poryadok.ui.common.IconAction
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.ProgressRing
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.random.Random

object TutorRoutes {
    const val HOME = "tutor"
    const val WORDS = "tutorWords"
    const val DICT = "tutorDict"
    fun session(mode: String, topic: String = "") = "tutorSession?mode=$mode&topic=$topic"
}

/**
 * Озвучка слов голосом телефона. Предпочитает английский голос, установленный на телефон, —
 * тогда произношение работает без интернета.
 */
class Speaker(ctx: android.content.Context) : TextToSpeech.OnInitListener {
    private var ready = false
    private val tts: TextToSpeech = TextToSpeech(ctx.applicationContext, this)
    /** null — ещё проверяем; true — английский голос есть на телефоне; false — голоса нет или нужен интернет. */
    val offline = mutableStateOf<Boolean?>(null)
    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (!ready) { offline.value = false; return }
        runCatching {
            tts.language = Locale.US
            tts.setSpeechRate(.9f)
            val local = tts.voices.orEmpty().filter { v ->
                v.locale.language == "en" && !v.isNetworkConnectionRequired &&
                    TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in v.features.orEmpty()
            }
            val best = local.sortedWith(compareBy<android.speech.tts.Voice>({ v -> when (v.locale.country) { "US" -> 0; "GB" -> 1; else -> 2 } }, { v -> -v.quality })).firstOrNull()
            if (best != null) tts.voice = best
            offline.value = best != null
        }.onFailure { offline.value = false }
    }
    fun say(text: String) { if (ready) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, text) }
    fun close() { runCatching { tts.stop(); tts.shutdown() } }

    companion object {
        /** Открывает установку голосовых данных (английский для работы без интернета). */
        fun installVoice(ctx: android.content.Context) {
            val intents = listOf(
                android.content.Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA),
                android.content.Intent("com.android.settings.TTS_SETTINGS"),
            )
            intents.firstOrNull { i -> runCatching { ctx.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess }
        }
    }
}

@Composable
fun rememberSpeaker(): Speaker {
    val ctx = LocalContext.current
    val sp = remember { Speaker(ctx) }
    DisposableEffect(Unit) { onDispose { sp.close() } }
    return sp
}

@Composable
internal fun SpeakButton(sp: Speaker, text: String, size: Int = 36) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = .15f)).clickable { sp.say(text) },
        contentAlignment = Alignment.Center,
    ) { Text("🔊", fontSize = (size / 2.3).sp) }
}

/** Главный экран репетитора: серия, цель дня, слова на сегодня, повторение, тренировки, прогресс. */
@Composable
fun TutorHomeScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val st by TutorStore.flow(ctx).collectAsState()
    val s = st ?: TutorStore.State()
    val today = Dates.today()
    val words = remember(s.custom) { TutorStore.words(ctx, s) }
    val startedToday = s.progress.count { it.value.added == today }
    val left = (s.settings.perDay - startedToday).coerceAtLeast(0)
    val todayNew = remember(s.progress.size, s.settings, today) { Tutor.dailyNew(words, s.progress, s.settings, today).take(left) }
    val stats = Tutor.stats(words, s.progress, today)
    val streak = Tutor.streak(s.goalDays.toSet(), today)
    var settings by remember { mutableStateOf(!s.onboarded) }
    Screen("Репетитор", onBack = { nav.popBackStack() }, actions = {
        IconAction("tutor/01", "Большой словарь") { nav.navigate(TutorRoutes.DICT) }
        IconAction("tutor/05", "Мои слова") { nav.navigate(TutorRoutes.WORDS) }
        IconAction("ui:sliders", "Настройки") { settings = true }
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("tutor/06", "$streak", "дней подряд", Modifier.weight(1f))
                StatTile("tutor/07", "${stats.learned}", "выучено", Modifier.weight(1f))
                StatTile("tutor/08", "${s.xp}", "очков", Modifier.weight(1f))
            }
            Gap(10.dp)
            Tile {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(startedToday / s.settings.perDay.coerceAtLeast(1).toFloat(), extra.ok, size = 56.dp, stroke = 6.dp) { Glyph("tutor/00", 26.dp, badge = false) }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Слова на сегодня", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        Text(
                            "Новых: $startedToday из ${s.settings.perDay}" + when (s.settings.mode) {
                                0 -> " · тема дня: ${Tutor.TOPICS[Tutor.topicOfDay(s.settings, today, words)]}"
                                1 -> " · случайные слова"
                                else -> " · тема дня + случайные"
                            },
                            fontSize = 12.sp, color = extra.dim,
                        )
                    }
                }
                if (todayNew.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                        todayNew.forEach { w -> Text(w.en, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(extra.cardHigh).padding(horizontal = 8.dp, vertical = 4.dp)) }
                    }
                    Button(onClick = { nav.navigate(TutorRoutes.session("learn")) }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("Учить ${todayNew.size} новых слов") }
                } else Text(
                    if (startedToday >= s.settings.perDay) "Цель на сегодня выполнена! Можно повторить слова или выучить ещё." else "Подходящие новые слова закончились — расширьте уровни или темы в настройках.",
                    fontSize = 13.sp, color = extra.ok, modifier = Modifier.padding(top = 8.dp),
                )
                if (startedToday >= s.settings.perDay) TextButton(onClick = { nav.navigate(TutorRoutes.session("learn", "more")) }) { Text("Ещё ${s.settings.perDay} слов") }
            }
            Gap(8.dp)
            Tile(onClick = if (stats.due > 0) ({ nav.navigate(TutorRoutes.session("review")) }) else null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph("tutor/03", 30.dp, badge = false)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Повторение", fontWeight = FontWeight.SemiBold)
                        Text(if (stats.due > 0) "${stats.due} слов пора повторить — интервалы 1, 2, 4, 7, 15, 30 дней" else "На сегодня повторять нечего", fontSize = 12.sp, color = extra.dim)
                    }
                    if (stats.due > 0) Text("${stats.due}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
            SectionTitle("Тренировки")
            val modes = listOf(
                Triple("cards", "Карточки", "tutor/02") to "Переверни и оцени себя",
                Triple("choice", "Выбор перевода", "tutor/01") to "Слово → 4 варианта",
                Triple("reverse", "Обратный перевод", "tutor/04") to "С русского на английский",
                Triple("write", "Написание", "tutor/12") to "Напиши слово сам",
                Triple("listen", "На слух", "tutor/16") to "Услышь и выбери",
                Triple("cloze", "Вставь слово", "tutor/14") to "Пропуск в примере",
                Triple("sprint", "Спринт", "tutor/06") to "Верно или нет за 60 секунд",
            )
            modes.chunked(2).forEach { row ->
                Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (m, sub) ->
                        Tile(Modifier.weight(1f), onClick = { nav.navigate(TutorRoutes.session(m.first)) }, padding = 10.dp) {
                            Glyph(m.third, 30.dp)
                            Text(m.second, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                            Text(sub, fontSize = 11.sp, color = extra.dim, maxLines = 2)
                        }
                    }
                    if (row.size == 1) Box(Modifier.weight(1f))
                }
            }
            Text("Тренировки берут слова, которые вы учите; если их пока мало — подмешиваются слова вашего уровня.", fontSize = 11.sp, color = extra.dim)
            SectionTitle("Прогресс по уровням")
            Tile {
                Tutor.LEVELS.forEach { l ->
                    val (done, total) = stats.byLevel[l] ?: (0 to 0)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                        Text(l, fontWeight = FontWeight.Bold, modifier = Modifier.size(width = 32.dp, height = 20.dp))
                        Bar(if (total > 0) done / total.toFloat() else 0f, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                        Text("  $done/$total", fontSize = 12.sp, color = extra.dim)
                    }
                }
                Text("В процессе: ${stats.learning} · выучено: ${stats.learned} · всего в словаре: ${stats.total}", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp))
            }
            SectionTitle("Темы")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Tutor.TOPICS.forEach { (k, name) ->
                    val n = words.count { it.topic == k }
                    if (n > 0) Pill("$name · $n", false, glyph = Tutor.TOPIC_GLYPH[k]) { nav.navigate(TutorRoutes.session("learn", k)) }
                }
            }
            Text("Нажмите на тему — выучить слова только из неё.", fontSize = 11.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp))
            HowTo("tutor")
            Gap(40.dp)
        }
    }
    if (settings) SettingsDialog(s) { settings = false }
}

@Composable
private fun StatTile(glyph: String, value: String, label: String, modifier: Modifier) {
    Tile(modifier, padding = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph(glyph, 26.dp, badge = false)
            Column(Modifier.padding(start = 6.dp)) {
                com.dasein.poryadok.ui.common.FitText(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(label, fontSize = 10.sp, color = LocalExtra.current.dim, maxLines = 1)
            }
        }
    }
}

/** Настройки: уровень, частотность, темы, сколько слов в день, режим набора. */
@Composable
private fun SettingsDialog(s: TutorStore.State, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    var set by remember { mutableStateOf(s.settings) }
    val words = remember { TutorStore.words(ctx, s) }
    val available = words.count { Tutor.matches(it, set) && it.id !in s.progress }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (s.onboarded) "Настройки обучения" else "Давайте настроим обучение") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Уровень владения", fontWeight = FontWeight.SemiBold)
                Text("Можно выбрать несколько — например, A2 и B1.", fontSize = 12.sp, color = extra.dim)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    Tutor.LEVELS.forEach { l -> Pill(Tutor.LEVEL_NAMES[l] ?: l, l in set.levels) { set = set.copy(levels = (if (l in set.levels) set.levels - l else set.levels + l).ifEmpty { listOf(l) }) } }
                }
                Text("Распространённость слов", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    Tutor.FREQ_NAMES.forEach { (k, n) -> Pill(n, set.freq == k) { set = set.copy(freq = k) } }
                }
                Text("Сферы (пусто — все)", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    Tutor.TOPICS.forEach { (k, n) -> Pill(n, k in set.topics, glyph = Tutor.TOPIC_GLYPH[k]) { set = set.copy(topics = if (k in set.topics) set.topics - k else set.topics + k) } }
                }
                Text("Слов в день", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    listOf(5, 10, 15, 20).forEach { n -> Pill("$n", set.perDay == n) { set = set.copy(perDay = n) } }
                }
                Text("Как подбирать слова", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    listOf(0 to "Тема дня", 1 to "Случайные", 2 to "Смешанно").forEach { (k, n) -> Pill(n, set.mode == k) { set = set.copy(mode = k) } }
                }
                Text("Подходит новых слов: $available", fontSize = 13.sp, color = if (available >= set.perDay) extra.ok else extra.warn)
            }
        },
        confirmButton = { TextButton(onClick = { scope.launch { TutorStore.update(ctx) { it.copy(settings = set, onboarded = true) } }; onDismiss() }) { Text("Сохранить") } },
        dismissButton = { if (s.onboarded) TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

// ---------- Занятие ----------

private enum class Kind { INTRO, CARD, CHOICE_RU, CHOICE_EN, WRITE, LISTEN, CLOZE }

private data class Q(val kind: Kind, val word: Tutor.Word, val options: List<Tutor.Word> = emptyList())

/** Занятие: новые слова (знакомство + упражнения), повторение или отдельная тренировка. */
@Composable
fun TutorSessionScreen(nav: NavHostController, mode: String, topic: String) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    val sp = rememberSpeaker()
    val today = Dates.today()
    if (mode == "sprint") { SprintScreen(nav, sp); return }
    val questions = remember {
        val s = TutorStore.now(ctx)
        val all = TutorStore.words(ctx, s)
        val rnd = Random(System.currentTimeMillis())
        val pool = all.filter { Tutor.matches(it, s.settings) }.ifEmpty { all }
        val learning = all.filter { it.id in s.progress }
        fun mixed(ws: List<Tutor.Word>) = ws.flatMap { w ->
            val cloze = Tutor.cloze(w) != null
            listOf(Q(Kind.CHOICE_RU, w, Tutor.options(w, all, 4, rnd, true)), listOf(
                Q(Kind.CHOICE_EN, w, Tutor.options(w, all, 4, rnd, false)), Q(Kind.WRITE, w),
                if (cloze) Q(Kind.CLOZE, w, Tutor.options(w, all, 4, rnd, false)) else Q(Kind.LISTEN, w, Tutor.options(w, all, 4, rnd, true)),
            ).random(rnd))
        }.shuffled(rnd)
        when (mode) {
            "learn" -> {
                val set = when {
                    topic.isNotBlank() && topic != "more" -> pool.plus(all.filter { it.topic == topic }).distinct().filter { it.topic == topic && it.id !in s.progress }.shuffled(rnd).sortedBy { it.freq }.take(s.settings.perDay)
                    else -> {
                        val started = s.progress.count { it.value.added == today }
                        val n = if (topic == "more") s.settings.perDay else (s.settings.perDay - started).coerceAtLeast(0)
                        Tutor.dailyNew(all, s.progress, s.settings, if (topic == "more") today * 7 + started else today).take(n)
                    }
                }
                set.map { Q(Kind.INTRO, it) } + mixed(set)
            }
            "review" -> mixed(Tutor.due(all, s.progress, today).shuffled(rnd).take(30))
            else -> {
                val base = (learning.shuffled(rnd) + pool.shuffled(rnd)).distinct().take(15)
                base.map { w ->
                    when (mode) {
                        "cards" -> Q(Kind.CARD, w)
                        "reverse" -> Q(Kind.CHOICE_EN, w, Tutor.options(w, all, 4, rnd, false))
                        "write" -> Q(Kind.WRITE, w)
                        "listen" -> Q(Kind.LISTEN, w, Tutor.options(w, all, 4, rnd, true))
                        "cloze" -> if (Tutor.cloze(w) != null) Q(Kind.CLOZE, w, Tutor.options(w, all, 4, rnd, false)) else Q(Kind.CHOICE_EN, w, Tutor.options(w, all, 4, rnd, false))
                        else -> Q(Kind.CHOICE_RU, w, Tutor.options(w, all, 4, rnd, true))
                    }
                }
            }
        }
    }
    var i by rememberSaveable { mutableIntStateOf(0) }
    var correct by rememberSaveable { mutableIntStateOf(0) }
    var wrongWords by remember { mutableStateOf(listOf<Tutor.Word>()) }
    val title = when (mode) { "learn" -> "Новые слова"; "review" -> "Повторение"; "cards" -> "Карточки"; "choice" -> "Выбор перевода"; "reverse" -> "Обратный перевод"; "write" -> "Написание"; "listen" -> "На слух"; "cloze" -> "Вставь слово"; else -> "Тренировка" }
    Screen(title, onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).imePadding().fillMaxSize().padding(horizontal = 16.dp)) {
            if (questions.isEmpty()) {
                Text(if (mode == "review") "Сегодня повторять нечего — отличная работа!" else "Нет подходящих слов. Измените уровень или темы в настройках.", color = extra.dim, modifier = Modifier.padding(top = 20.dp))
                return@Column
            }
            Bar(i / questions.size.toFloat(), MaterialTheme.colorScheme.primary, Modifier.padding(vertical = 8.dp), height = 8.dp)
            if (i >= questions.size) {
                val graded = questions.count { it.kind != Kind.INTRO }
                LaunchedEffect(Unit) {
                    val s = TutorStore.now(ctx)
                    if (s.progress.count { it.value.added == today } >= s.settings.perDay || (s.xpByDay[today] ?: 0) >= 150) TutorStore.goalDone(ctx, today)
                }
                Column(Modifier.fillMaxWidth().padding(top = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Glyph("tutor/07", 72.dp, badge = false)
                    Text("Готово!", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                    if (graded > 0) Text("Верно: $correct из $graded · +${correct * 10 + (graded - correct) * 2} очков", fontSize = 16.sp)
                    if (wrongWords.isNotEmpty()) {
                        Text("Стоит повторить:", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp))
                        wrongWords.distinct().forEach { Text("${it.en} — ${it.ru}", fontSize = 14.sp) }
                        Text("Они вернутся на повторение завтра.", fontSize = 12.sp, color = extra.dim)
                    }
                    Button(onClick = { nav.popBackStack() }, modifier = Modifier.padding(top = 20.dp)) { Text("Закончить") }
                }
                return@Column
            }
            val q = questions[i]
            fun next(ok: Boolean?, w: Tutor.Word) {
                if (ok != null) {
                    if (ok) correct++ else wrongWords = wrongWords + w
                    scope.launch { TutorStore.answer(ctx, w, ok, today) }
                }
                i++
            }
            Text("${i + 1} / ${questions.size}", fontSize = 12.sp, color = extra.dim)
            when (q.kind) {
                Kind.INTRO -> Intro(q.word, sp, onKnown = { scope.launch { TutorStore.known(ctx, q.word, today) }; i++ }) {
                    scope.launch { TutorStore.startLearning(ctx, q.word, today) }; i++
                }
                Kind.CARD -> Card(q.word, sp) { ok -> next(ok, q.word) }
                Kind.WRITE -> Write(q.word, sp) { ok -> next(ok, q.word) }
                else -> Choice(q, sp) { ok -> next(ok, q.word) }
            }
        }
    }
}

@Composable
private fun WordHeader(w: Tutor.Word, sp: Speaker, showEn: Boolean = true) {
    val extra = LocalExtra.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(if (showEn) w.en else w.ru, fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
            Text("${w.level} · ${Tutor.TOPICS[w.topic] ?: ""}", fontSize = 12.sp, color = extra.dim)
        }
        if (showEn) SpeakButton(sp, w.en, 44)
    }
}

@Composable
private fun Intro(w: Tutor.Word, sp: Speaker, onKnown: () -> Unit, onNext: () -> Unit) {
    val extra = LocalExtra.current
    LaunchedEffect(w) { delay(300); sp.say(w.en) }
    Text("Новое слово", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
    WordHeader(w, sp)
    Text(w.ru, fontSize = 22.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
    Tile(Modifier.padding(top = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(w.ex, fontSize = 16.sp, modifier = Modifier.weight(1f))
            SpeakButton(sp, w.ex, 32)
        }
        Text(w.exRu, fontSize = 14.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp))
    }
    Text("Совет: произнесите слово вслух и придумайте с ним свою фразу — так запоминается быстрее.", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 10.dp))
    Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onKnown, modifier = Modifier.weight(1f)) { Text("Уже знаю") }
        Button(onClick = onNext, modifier = Modifier.weight(1f)) { Text("Запомнил →") }
    }
}

@Composable
private fun Card(w: Tutor.Word, sp: Speaker, onAnswer: (Boolean) -> Unit) {
    val extra = LocalExtra.current
    var flipped by remember(w) { mutableStateOf(false) }
    Box(
        Modifier.fillMaxWidth().height(260.dp).padding(top = 12.dp).clip(RoundedCornerShape(20.dp)).background(extra.card).clickable { flipped = !flipped; if (!flipped) sp.say(w.en) },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
            if (!flipped) {
                Text(w.en, fontSize = 32.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("нажмите, чтобы перевернуть", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 8.dp))
            } else {
                Text(w.ru, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary)
                Text(w.ex, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
                Text(w.exRu, fontSize = 13.sp, color = extra.dim, textAlign = TextAlign.Center)
            }
        }
    }
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) { SpeakButton(sp, w.en); Text("  ${w.level} · ${Tutor.TOPICS[w.topic]}", fontSize = 12.sp, color = extra.dim) }
    if (flipped) Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onAnswer(false) }, modifier = Modifier.weight(1f)) { Text("Не вспомнил") }
        Button(onClick = { onAnswer(true) }, modifier = Modifier.weight(1f)) { Text("Знаю") }
    }
}

@Composable
private fun Choice(q: Q, sp: Speaker, onAnswer: (Boolean) -> Unit) {
    val extra = LocalExtra.current
    var picked by remember(q) { mutableStateOf<Tutor.Word?>(null) }
    val w = q.word
    val ruAnswers = q.kind == Kind.CHOICE_RU || q.kind == Kind.LISTEN
    LaunchedEffect(q) { if (q.kind == Kind.LISTEN || q.kind == Kind.CHOICE_RU) { delay(250); sp.say(w.en) } }
    when (q.kind) {
        Kind.CHOICE_RU -> { Text("Выберите перевод", fontSize = 13.sp, color = extra.dim, modifier = Modifier.padding(top = 8.dp)); WordHeader(w, sp) }
        Kind.CHOICE_EN -> { Text("Как это по-английски?", fontSize = 13.sp, color = extra.dim, modifier = Modifier.padding(top = 8.dp)); WordHeader(w, sp, showEn = false) }
        Kind.LISTEN -> {
            Text("Прослушайте и выберите перевод", fontSize = 13.sp, color = extra.dim, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.padding(vertical = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.Center) { SpeakButton(sp, w.en, 80) }
        }
        else -> {
            Text("Вставьте пропущенное слово", fontSize = 13.sp, color = extra.dim, modifier = Modifier.padding(top = 8.dp))
            Tile(Modifier.padding(top = 10.dp)) {
                Text(Tutor.cloze(w) ?: w.ex, fontSize = 18.sp, lineHeight = 24.sp)
                Text(w.exRu, fontSize = 13.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
    Gap(12.dp)
    q.options.forEach { o ->
        val isRight = o.id == w.id
        val col = when {
            picked == null -> extra.card
            isRight -> extra.ok.copy(alpha = .25f)
            picked == o -> extra.danger.copy(alpha = .25f)
            else -> extra.card
        }
        Text(
            if (ruAnswers) o.ru else o.en, fontSize = 17.sp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(col)
                .border(1.dp, if (picked != null && isRight) extra.ok else Color.Transparent, RoundedCornerShape(14.dp))
                .clickable(enabled = picked == null) { picked = o; if (!ruAnswers) sp.say(w.en) }.padding(14.dp),
        )
    }
    picked?.let { p ->
        val ok = p.id == w.id
        LaunchedEffect(p) { if (ok) { delay(900); onAnswer(true) } }
        Tile(Modifier.padding(top = 10.dp)) {
            Text(if (ok) "Верно!" else "Правильно: ${w.en} — ${w.ru}", fontWeight = FontWeight.SemiBold, color = if (ok) extra.ok else extra.danger)
            Text(w.ex, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            Text(w.exRu, fontSize = 13.sp, color = extra.dim)
        }
        if (!ok) Button(onClick = { onAnswer(false) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Дальше") }
    }
}

@Composable
private fun Write(w: Tutor.Word, sp: Speaker, onAnswer: (Boolean) -> Unit) {
    val extra = LocalExtra.current
    var text by remember(w) { mutableStateOf("") }
    var result by remember(w) { mutableStateOf<Boolean?>(null) }
    var hint by remember(w) { mutableIntStateOf(0) }
    Text("Напишите по-английски", fontSize = 13.sp, color = extra.dim, modifier = Modifier.padding(top = 8.dp))
    WordHeader(w, sp, showEn = false)
    if (hint > 0) Text("Подсказка: " + w.en.take(hint) + "…" + " (${w.en.length} букв)", fontSize = 13.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp))
    fun check() { if (result == null && text.isNotBlank()) { result = Tutor.typedOk(text, w); sp.say(w.en) } }
    OutlinedTextField(
        text, { if (result == null) text = it }, singleLine = true, label = { Text("Слово") },
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp), shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false), keyboardActions = KeyboardActions(onDone = { check() }),
    )
    if (result == null) Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { hint = (hint + 1).coerceAtMost(w.en.length) }, modifier = Modifier.weight(1f)) { Text("Подсказка") }
        Button(onClick = { check() }, enabled = text.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Проверить") }
    } else {
        val ok = result == true
        Tile(Modifier.padding(top = 10.dp)) {
            Text(if (ok) (if (text.trim().equals(w.en, true)) "Верно!" else "Верно (с опечаткой): ${w.en}") else "Правильно: ${w.en}", fontWeight = FontWeight.SemiBold, color = if (ok) extra.ok else extra.danger)
            Text(w.ex, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp)); Text(w.exRu, fontSize = 13.sp, color = extra.dim)
        }
        Button(onClick = { onAnswer(ok && hint == 0 || ok && hint <= 1) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Дальше") }
    }
}

/** Спринт: 60 секунд, верна ли пара «слово — перевод». */
@Composable
private fun SprintScreen(nav: NavHostController, sp: Speaker) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    val today = Dates.today()
    val all = remember { TutorStore.words(ctx).let { ws -> val s = TutorStore.now(ctx); ws.filter { Tutor.matches(it, s.settings) }.ifEmpty { ws } } }
    var seconds by remember { mutableIntStateOf(60) }
    var score by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var started by remember { mutableStateOf(false) }
    fun pair(): Pair<Tutor.Word, Tutor.Word> { val w = all.random(); return w to if (Random.nextBoolean()) w else all.random() }
    var cur by remember { mutableStateOf(pair()) }
    var flash by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(started) { if (started) while (seconds > 0) { delay(1000); seconds-- } }
    LaunchedEffect(seconds) { if (started && seconds == 0) scope.launch { TutorStore.update(ctx) { s -> s.copy(xp = s.xp + score * 3, xpByDay = s.xpByDay + (today to ((s.xpByDay[today] ?: 0) + score * 3))) } } }
    Screen("Спринт", onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (!started) {
                Glyph("tutor/06", 72.dp, badge = false)
                Text("60 секунд: верен ли перевод?", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
                Text("Отвечайте быстро — каждый верный ответ +3 очка.", color = extra.dim, modifier = Modifier.padding(top = 4.dp))
                Button(onClick = { started = true }, modifier = Modifier.padding(top = 20.dp)) { Text("Старт") }
                return@Column
            }
            Text("$seconds с", fontSize = 18.sp, color = if (seconds <= 10) extra.danger else extra.dim)
            Text("Счёт: $score из $total", fontSize = 15.sp)
            if (seconds == 0) {
                Text("Время вышло!", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 30.dp))
                Text("Верно $score из $total · +${score * 3} очков", modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { nav.popBackStack() }) { Text("Закончить") }
                    Button(onClick = { seconds = 60; score = 0; total = 0; cur = pair() }) { Text("Ещё раз") }
                }
                return@Column
            }
            val (w, t) = cur
            Box(
                Modifier.fillMaxWidth().padding(top = 30.dp).clip(RoundedCornerShape(20.dp))
                    .background(when (flash) { true -> extra.ok.copy(alpha = .2f); false -> extra.danger.copy(alpha = .2f); null -> extra.card }).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(w.en, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(t.ru, fontSize = 22.sp, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                }
            }
            fun answer(saysTrue: Boolean) {
                val ok = (w.id == t.id) == saysTrue
                total++; if (ok) score++
                flash = ok; cur = pair()
            }
            LaunchedEffect(flash, total) { delay(250); flash = null }
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { answer(false) }, modifier = Modifier.weight(1f).height(56.dp)) { Text("Неверно", fontSize = 17.sp) }
                Button(onClick = { answer(true) }, modifier = Modifier.weight(1f).height(56.dp)) { Text("Верно", fontSize = 17.sp) }
            }
        }
    }
}

// ---------- Словарь ----------

@Composable
fun TutorWordsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    val sp = rememberSpeaker()
    val st by TutorStore.flow(ctx).collectAsState()
    val s = st ?: TutorStore.State()
    val today = Dates.today()
    val words = remember(s.custom) { TutorStore.words(ctx, s) }
    var q by rememberSaveable { mutableStateOf("") }
    var topic by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    val shown = words.filter { w ->
        (topic.isBlank() || w.topic == topic) && (level.isBlank() || w.level == level) &&
            (q.isBlank() || q.trim().lowercase().let { it in w.en.lowercase() || it in w.ru.lowercase() })
    }
    Screen("Словарь", onBack = { nav.popBackStack() }, actions = { IconAction("ui:check", "Добавить слово") { adding = true } }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp)) {
            item {
                TextInput(q, { q = it }, "Поиск по-английски или по-русски")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    Pill("Все уровни", level.isBlank()) { level = "" }
                    Tutor.LEVELS.forEach { l -> Pill(l, level == l) { level = if (level == l) "" else l } }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp, bottom = 6.dp)) {
                    Pill("Все темы", topic.isBlank()) { topic = "" }
                    Tutor.TOPICS.forEach { (k, n) -> Pill(n, topic == k) { topic = if (topic == k) "" else k } }
                }
                Text("Слов: ${shown.size} · ● выучено  ◐ учу  ○ новое", fontSize = 12.sp, color = extra.dim)
                OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) { Text("+ Добавить своё слово") }
            }
            items(shown, key = { it.id + it.topic }) { w ->
                val p = s.progress[w.id]
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp)).background(extra.card).padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(when { p == null -> "○"; p.box >= Tutor.LEARNED_BOX -> "●"; else -> "◐" }, fontSize = 16.sp, color = if (p != null && p.box >= Tutor.LEARNED_BOX) extra.ok else MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(w.en, fontWeight = FontWeight.SemiBold)
                        Text(w.ru, fontSize = 13.sp, color = extra.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(w.level, fontSize = 11.sp, color = extra.dim)
                    SpeakButton(sp, w.en, 30)
                    if (p == null) TextButton(onClick = { scope.launch { TutorStore.startLearning(ctx, w, today) } }) { Text("Учить", fontSize = 12.sp) }
                }
            }
        }
    }
    if (adding) {
        var en by remember { mutableStateOf("") }; var ru by remember { mutableStateOf("") }; var ex by remember { mutableStateOf("") }; var exRu by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Своё слово") },
            text = {
                Column {
                    TextInput(en, { en = it }, "Слово по-английски"); Gap(6.dp)
                    TextInput(ru, { ru = it }, "Перевод"); Gap(6.dp)
                    TextInput(ex, { ex = it }, "Пример (необязательно)", singleLine = false); Gap(6.dp)
                    TextInput(exRu, { exRu = it }, "Перевод примера", singleLine = false)
                }
            },
            confirmButton = {
                TextButton(enabled = en.isNotBlank() && ru.isNotBlank(), onClick = {
                    val w = TutorStore.MyWord(en.trim(), ru.trim(), ex.trim(), exRu.trim())
                    scope.launch {
                        TutorStore.update(ctx) { it.copy(custom = it.custom.filter { c -> !c.en.equals(w.en, true) } + w) }
                        TutorStore.startLearning(ctx, Tutor.Word(w.en, w.ru, w.ex, w.exRu, "B1", 1, "my", true), today)
                    }
                    adding = false
                }) { Text("Добавить и учить") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Отмена") } },
        )
    }
}

// ---------- Плашка «Слово дня» ----------

/** Плашка на главном: случайное слово вашего уровня, перевод, пример с переводом, озвучка, «Другое», «Учить». */
@Composable
fun HomeWordCard(nav: NavHostController) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    val sp = rememberSpeaker()
    val st by TutorStore.flow(ctx).collectAsState()
    val s = st ?: TutorStore.State()
    val today = Dates.today()
    val pool = remember(s.settings, s.custom) { TutorStore.words(ctx, s).let { ws -> ws.filter { Tutor.matches(it, s.settings) }.ifEmpty { ws } } }
    if (pool.isEmpty()) return
    var seed by rememberSaveable { mutableIntStateOf(0) }
    val w = pool[Random(today * 1000 + seed).nextInt(pool.size)]
    val learning = w.id in s.progress
    Tile(onClick = { nav.navigate(TutorRoutes.HOME) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph("tutor/00", 22.dp, badge = false)
            Text("  Слово дня · английский", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(w.level, fontSize = 12.sp, color = extra.dim)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Column(Modifier.weight(1f)) {
                Text(w.en, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(w.ru, fontSize = 17.sp, color = MaterialTheme.colorScheme.primary)
            }
            SpeakButton(sp, w.en, 40)
        }
        if (w.ex.isNotBlank()) Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(extra.cardHigh).padding(10.dp)) {
            Text(w.ex, fontSize = 14.sp)
            Text(w.exRu, fontSize = 13.sp, color = extra.dim, modifier = Modifier.padding(top = 2.dp))
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { seed++ }) { Text("Другое") }
            if (!learning) Button(onClick = { scope.launch { TutorStore.startLearning(ctx, w, today) } }) { Text("Учить") }
            else Text("✓ в изучении", fontSize = 13.sp, color = extra.ok)
            Text(Tutor.TOPICS[w.topic] ?: "", fontSize = 11.sp, color = extra.dim, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.weight(1f))
        }
    }
}
