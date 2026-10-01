package com.dasein.poryadok.ui.talk

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.Settings as AppSettings
import com.dasein.poryadok.logic.SmallTalk
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Hint
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.random.Random

private val MONTHS = listOf("январь", "февраль", "март", "апрель", "май", "июнь", "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь")

/** «Темы»: повод месяца и темы с ассоциациями и вопросами — когда не за что зацепиться. */
internal fun LazyListScope.topicsTab(data: TalkRepo.Data) {
    item {
        val extra = LocalExtra.current
        val month = LocalDate.now().monthValue
        data.months[month]?.let { m ->
            Tile(color = MaterialTheme.colorScheme.primary.copy(alpha = .14f)) {
                Text("Повод месяца · ${MONTHS[month - 1]}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Text(m, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        Text(
            "Нажмите на тему — появятся слова-ассоциации и вопросы. Отсутствие темы — тоже тема: смотрите вокруг.",
            fontSize = 13.sp, color = extra.dim, modifier = Modifier.padding(vertical = 10.dp),
        )
    }
    items(data.topics, key = { "topic:" + it.name }) { t -> TopicCard(t) }
}

@Composable
private fun TopicCard(t: SmallTalk.Topic) {
    val extra = LocalExtra.current
    var open by rememberSaveable(t.name) { mutableStateOf(false) }
    Tile(Modifier.padding(bottom = 8.dp), onClick = { open = !open }) {
        Text(t.name, fontWeight = FontWeight.SemiBold)
        Text(t.words, fontSize = 13.sp, color = extra.dim, maxLines = if (open) 10 else 1, modifier = Modifier.padding(top = 4.dp))
        if (open) t.questions.forEach { q ->
            Text("• $q", fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** «Школа»: приёмы из книг по смолтоку, по разделам. */
@OptIn(ExperimentalLayoutApi::class)
internal fun LazyListScope.guideTab(data: TalkRepo.Data) {
    item {
        val extra = LocalExtra.current
        Text(
            "Главное из книг «Межкультурный Small Talk» (Анастасия Шевченко) и «Смол-ток» (Патрик Кинг), а также классических приёмов Карнеги, Восса, Файн и Флеминг.",
            fontSize = 13.sp, color = extra.dim,
        )
        Gap(8.dp)
    }
    item {
        val sections = remember(data) { data.tips.map { it.section }.distinct() }
        var section by rememberSaveable { mutableStateOf("") }
        com.dasein.poryadok.ui.common.SingleFilter(
            "Раздел", sections.map { sec -> com.dasein.poryadok.ui.common.FilterOption(sec, sec, data.tips.count { it.section == sec }) }, section, allLabel = "Все разделы",
        ) { section = it }
        Gap(10.dp)
        data.tips.filter { section.isEmpty() || it.section == section }.forEach { t -> GuideCard(t) }
    }
}

@Composable
private fun GuideCard(t: SmallTalk.Tip) {
    val extra = LocalExtra.current
    Tile(Modifier.padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text(t.section, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
        }
        Text(t.text, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
        if (t.source.isNotBlank()) Text(t.source, fontSize = 11.sp, fontStyle = FontStyle.Italic, color = extra.dim, modifier = Modifier.padding(top = 6.dp))
    }
}

/** «Фразы»: готовые формулировки для трудных моментов; нажатие копирует фразу. */
@OptIn(ExperimentalLayoutApi::class)
internal fun LazyListScope.phrasesTab(data: TalkRepo.Data) {
    item {
        val ctx = LocalContext.current
        val extra = LocalExtra.current
        val clipboard = LocalClipboardManager.current
        val situations = remember(data) { data.phrases.map { it.situation }.distinct() }
        var situation by rememberSaveable { mutableStateOf("") }
        com.dasein.poryadok.ui.common.SingleFilter(
            "Ситуация", situations.map { t -> com.dasein.poryadok.ui.common.FilterOption(t, t, data.phrases.count { it.situation == t }) }, situation, allLabel = "Все ситуации",
        ) { situation = it }
        Gap(8.dp)
        data.phrases.filter { situation.isEmpty() || it.situation == situation }.groupBy { it.situation }.forEach { (sit, list) ->
            SectionTitle(sit)
            list.forEach { p ->
                Text(
                    "«${p.text}»", fontSize = 15.sp, lineHeight = 21.sp,
                    modifier = Modifier.fillMaxWidth().clickable {
                        clipboard.setText(AnnotatedString(p.text))
                        Toast.makeText(ctx, "Фраза скопирована", Toast.LENGTH_SHORT).show()
                    }.padding(vertical = 6.dp),
                )
            }
        }
    }
}

/** «Тренажёр»: упражнение дня, светофор, мнение за 3 секунды, «чего не хватает», FOOFAAE и конструктор историй. */
internal fun LazyListScope.trainerTab(data: TalkRepo.Data, s: AppSettings) {
    item { ExerciseOfDay(data) }
    item { TrafficLight() }
    item { OpinionTrainer(data) }
    item { Seasoning() }
    item { Foofaae(data) }
    item { StoryBuilder(s) }
}

@Composable
private fun ExerciseOfDay(data: TalkRepo.Data) {
    val ex = remember(data) { SmallTalk.ofDay(data.exercises, LocalDate.now().toEpochDay()) } ?: return
    Tile(color = MaterialTheme.colorScheme.primary.copy(alpha = .14f)) {
        Text("Упражнение дня", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        Text(ex.title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, modifier = Modifier.padding(top = 4.dp))
        Text(ex.text, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

private val Yellow = Color(0xFFD9A441)

@Composable
private fun TrafficLight() {
    val extra = LocalExtra.current
    var running by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(running) {
        while (running) {
            delay(1000)
            seconds++
        }
    }
    val light = SmallTalk.light(seconds)
    val color = when (light) { 0 -> extra.ok; 1 -> Yellow; else -> extra.warn }
    SectionTitle("Светофор реплики")
    Tile {
        Text("Запустите, когда начинаете говорить. До 20 секунд — зелёный, до 40 — жёлтый (заканчивайте мысль), дальше — красный: это уже монолог.", fontSize = 13.sp, color = extra.dim)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(if (running || seconds > 0) color else extra.line), contentAlignment = Alignment.Center) {
                Text("$seconds", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    when {
                        !running && seconds == 0 -> "Готов"
                        light == 0 -> "Говорите"
                        light == 1 -> "Заканчивайте мысль"
                        else -> "Передайте слово!"
                    },
                    fontWeight = FontWeight.SemiBold,
                )
                Row(Modifier.padding(top = 6.dp)) {
                    Button(onClick = { if (running) running = false else { seconds = 0; running = true } }) { Text(if (running) "Стоп" else "Старт") }
                    if (!running && seconds > 0) OutlinedButton(onClick = { seconds = 0 }, Modifier.padding(start = 8.dp)) { Text("Сброс") }
                }
            }
        }
    }
}

@Composable
private fun OpinionTrainer(data: TalkRepo.Data) {
    val extra = LocalExtra.current
    if (data.opinions.isEmpty()) return
    var index by rememberSaveable { mutableIntStateOf(Random.nextInt(data.opinions.size)) }
    var left by remember(index) { mutableIntStateOf(3) }
    var started by remember(index) { mutableStateOf(false) }
    LaunchedEffect(index, started) {
        if (!started) return@LaunchedEffect
        while (left > 0) {
            delay(1000)
            left--
        }
    }
    SectionTitle("Мнение за 3 секунды")
    Tile {
        Text("Отвечайте вслух сразу, не обдумывая. Ответ не обязан быть умным — важно говорить уверенно.", fontSize = 13.sp, color = extra.dim)
        Text(data.opinions[index], fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    !started -> "Нажмите «Старт»"
                    left > 0 -> "$left…"
                    else -> "Время! Что ответили?"
                },
                Modifier.weight(1f), fontSize = 16.sp, color = if (started && left == 0) MaterialTheme.colorScheme.primary else extra.dim,
            )
            if (!started) Button(onClick = { started = true }) { Text("Старт") }
            else FilledIconButton(
                onClick = { var n: Int; do { n = Random.nextInt(data.opinions.size) } while (n == index && data.opinions.size > 1); index = n },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) { Icon(Icons.Filled.Refresh, "Следующий вопрос") }
        }
    }
}

private val SEASONING = listOf(
    Triple("Соль", "Юмор и лёгкость", "Разговор пресный? Добавьте щепотку юмора: забавное наблюдение, самоиронию, игривый тон. Говорите чуть быстрее и светлее, улыбнитесь — улыбку слышно в голосе."),
    Triple("Кислота", "Любопытство", "Скучно и предсказуемо? Задайте неожиданный вопрос или скажите слегка непопулярное мнение на безопасную тему. Интонация с вопросом, паузы, задумчивое «Хм…»."),
    Triple("Жир", "Тепло и эмоции", "Сухо и формально? Добавьте человечности: поделитесь маленьким личным наблюдением, спросите, что человек при этом почувствовал. Говорите медленнее и мягче."),
    Triple("Жар", "Энтузиазм", "Вяло? Расскажите о том, что вас по-настоящему увлекает, — энтузиазм заразителен. Говорите громче, выразительнее, с живыми жестами."),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Seasoning() {
    var pick by rememberSaveable { mutableIntStateOf(-1) }
    SectionTitle("Чего не хватает разговору?")
    Tile {
        Text("Разговор как блюдо (Патрик Кинг): выберите, чего не хватает.", fontSize = 13.sp, color = LocalExtra.current.dim)
        Gap(8.dp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SEASONING.forEachIndexed { i, (name, what, _) -> Pill("$name · $what", pick == i) { pick = if (pick == i) -1 else i } }
        }
        SEASONING.getOrNull(pick)?.let { Text(it.third, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 10.dp)) }
    }
}

@Composable
private fun Foofaae(data: TalkRepo.Data) {
    val extra = LocalExtra.current
    if (data.topics.isEmpty()) return
    var index by rememberSaveable { mutableIntStateOf(Random.nextInt(data.topics.size)) }
    val topic = data.topics[index].name
    SectionTitle("FOOFAAE: семь видов реплик")
    Tile {
        Text("Застряли? Смените вид реплики. Потренируйтесь: скажите по одной фразе каждого вида на тему:", fontSize = 13.sp, color = extra.dim)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Text("«$topic»", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            IconButton(onClick = { index = (index + 1 + Random.nextInt(maxOf(1, data.topics.size - 1))) % data.topics.size }) { Icon(Icons.Filled.Refresh, "Другая тема") }
        }
        listOf(
            "Чувство" to "что вам в этом нравится или волнует",
            "Наблюдение" to "что вы замечаете прямо сейчас",
            "Мнение" to "что вы об этом думаете",
            "Факт" to "что интересного вы знаете",
            "Действие" to "что хотите сделать или предложить",
            "О себе" to "короткий случай из жизни",
            "Событие" to "что недавно было или скоро будет",
        ).forEach { (k, v) ->
            Row(Modifier.padding(top = 6.dp)) {
                Text(k, Modifier.fillMaxWidth(.3f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(v, fontSize = 14.sp, color = extra.dim)
            }
        }
    }
}

@Composable
private fun StoryBuilder(s: AppSettings) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val stories = remember(s.talkStories) { SmallTalk.decodeStories(s.talkStories) }
    var title by rememberSaveable { mutableStateOf("") }
    var start by rememberSaveable { mutableStateOf("") }
    var conflict by rememberSaveable { mutableStateOf("") }
    var end by rememberSaveable { mutableStateOf("") }
    fun save(list: List<SmallTalk.MyStory>) = io { Graph.prefs.update { it.copy(talkStories = SmallTalk.encodeStories(list)) } }
    SectionTitle("Конструктор истории")
    Tile {
        Text("Любой случай становится историей в три шага: как было → что пошло не так → чем кончилось. Соберите свои истории, чтобы они были под рукой.", fontSize = 13.sp, color = extra.dim)
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true, label = { Text("Название") })
        OutlinedTextField(start, { start = it }, Modifier.fillMaxWidth().padding(top = 6.dp), label = { Text("1. Как было (статус-кво)") })
        OutlinedTextField(conflict, { conflict = it }, Modifier.fillMaxWidth().padding(top = 6.dp), label = { Text("2. Но тут… (конфликт)") })
        OutlinedTextField(end, { end = it }, Modifier.fillMaxWidth().padding(top = 6.dp), label = { Text("3. И в итоге… (развязка)") })
        Button(
            onClick = {
                save(listOf(SmallTalk.MyStory(title.ifBlank { "История" }, start, conflict, end)) + stories)
                title = ""; start = ""; conflict = ""; end = ""
                Toast.makeText(ctx, "История сохранена", Toast.LENGTH_SHORT).show()
            },
            enabled = start.isNotBlank() && conflict.isNotBlank() && end.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { Text("Сохранить историю") }
        if (conflict.isBlank() && start.isNotBlank()) Text("Без конфликта история звучит как «ну и что?» — что пошло не по плану?", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 6.dp))
    }
    stories.forEachIndexed { i, st ->
        Tile(Modifier.padding(top = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(st.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { TalkRepo.share(ctx, "${st.title}\n\n${st.start} ${st.conflict} ${st.end}") }) { Icon(Icons.Filled.Share, "Поделиться") }
                IconButton(onClick = { save(stories.filterIndexed { j, _ -> j != i }) }) { Icon(Icons.Filled.Delete, "Удалить") }
            }
            Text("${st.start} ${st.conflict} ${st.end}", fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Start)
        }
    }
    Hint("talk_trainer", "Тренажёр собран по книгам о смолтоке: светофор — правило Марка Гоулстона из книги Анастасии Шевченко, остальное — упражнения Патрика Кинга.", Modifier.padding(top = 12.dp), title = "Откуда упражнения")
}
