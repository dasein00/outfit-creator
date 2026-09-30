package com.dasein.poryadok.ui.talk

import com.dasein.poryadok.ui.common.HowTo
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.Settings as AppSettings
import com.dasein.poryadok.logic.Fact
import com.dasein.poryadok.logic.SmallTalk
import com.dasein.poryadok.ui.common.Bar
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.Hint
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlin.random.Random

/** База Small Talks: факты (включая старую картотеку о здоровье), истории, вопросы и приёмы. */
object TalkRepo {
    class Data(
        val facts: List<Fact>,
        val stories: List<SmallTalk.Story>,
        val questions: List<SmallTalk.Question>,
        val tips: List<SmallTalk.Tip>,
        val phrases: List<SmallTalk.Question>,
        val topics: List<SmallTalk.Topic>,
        val months: Map<Int, String>,
        val opinions: List<String>,
        val exercises: List<SmallTalk.Exercise>,
    )

    @Volatile private var cache: Data? = null

    private fun read(ctx: Context, dir: String, filter: (String) -> Boolean): String = runCatching {
        ctx.assets.list(dir).orEmpty().filter(filter).sorted().joinToString("\n") { f ->
            ctx.assets.open("$dir/$f").bufferedReader().use { it.readText() }
        }
    }.getOrDefault("")

    fun load(ctx: Context): Data = cache ?: run {
        val facts = SmallTalk.parseFacts(read(ctx, "smalltalk") { it.startsWith("facts") && it.endsWith(".txt") }) +
            SmallTalk.parseFacts(read(ctx, "facts") { it.endsWith(".txt") })
        Data(
            facts.distinctBy { it.id },
            SmallTalk.parseStories(read(ctx, "smalltalk") { it == "stories.txt" }),
            SmallTalk.parseQuestions(read(ctx, "smalltalk") { it == "questions.txt" }),
            SmallTalk.parseGuide(read(ctx, "smalltalk") { it == "guide.txt" }),
            SmallTalk.parseQuestions(read(ctx, "smalltalk") { it == "phrases.txt" }),
            SmallTalk.parseTopics(read(ctx, "smalltalk") { it == "topics.txt" }),
            SmallTalk.parseMonths(read(ctx, "smalltalk") { it == "months.txt" }),
            SmallTalk.parseLines(read(ctx, "smalltalk") { it == "opinions.txt" }),
            SmallTalk.parseExercises(read(ctx, "smalltalk") { it == "exercises.txt" }),
        ).also { cache = it }
    }

    fun share(ctx: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        runCatching { ctx.startActivity(Intent.createChooser(send, "Поделиться").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/** Колода и избранное хранятся в настройках; эти функции читают и пишут их. */
private fun deckOf(s: AppSettings) = SmallTalk.Deck(SmallTalk.decodeIds(s.factsHistory), s.factsPos)

private fun saveDeck(d: SmallTalk.Deck) = io {
    Graph.prefs.update { it.copy(factsHistory = SmallTalk.encodeIds(d.history), factsPos = d.pos) }
}

private fun toggleFavorite(id: Int) = io {
    Graph.prefs.update { it.copy(talkFavorites = SmallTalk.encodeIds(SmallTalk.toggle(SmallTalk.decodeIds(it.talkFavorites), id))) }
}

/**
 * Плашка «Интересный факт» на главной: ← и → листают факты выбранных в Small Talks сфер,
 * нажатие на сам факт открывает раздел Small Talks.
 */
@Composable
fun TalkFactCard(onOpen: () -> Unit) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val data = remember { TalkRepo.load(ctx) }
    val settings by observe(null) { Graph.prefs.settings }
    val s = settings ?: return
    val spheres = SmallTalk.decodeSet(s.talkSpheres)
    val facts = remember(data, s.talkSpheres) { SmallTalk.filter(data.facts, spheres) }
    if (facts.isEmpty()) return
    val deck = remember(s.factsHistory, s.factsPos, facts) { SmallTalk.ensure(facts, deckOf(s), Random.Default) }
    // Первый показ: запоминаем факт, чтобы он не менялся при каждом открытии главной.
    LaunchedEffect(deck) { if (deck != deckOf(s)) saveDeck(deck) }
    val fact = SmallTalk.current(facts, deck) ?: return
    val read = SmallTalk.read(facts, deck)

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(extra.card).padding(14.dp)) {
        Row(Modifier.clickable(onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
            Glyph(SmallTalk.glyph(fact.tag), 22.dp)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("Интересный факт", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("${fact.tag} · Small Talks", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
            Text("$read из ${facts.size}", fontSize = 12.sp, color = extra.dim)
        }
        AnimatedContent(fact, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "fact") { f ->
            Text(
                f.text, fontSize = 15.sp, lineHeight = 21.sp,
                modifier = Modifier.fillMaxWidth().heightIn(min = 84.dp).clickable(onClick = onOpen).padding(top = 10.dp),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedIconButton(onClick = { saveDeck(SmallTalk.prev(facts, deck, Random.Default)) }, enabled = SmallTalk.hasPrev(facts, deck)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Предыдущий факт")
            }
            Box(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Bar(read / facts.size.toFloat(), MaterialTheme.colorScheme.primary, height = 4.dp)
            }
            FilledIconButton(
                onClick = {
                    val n = SmallTalk.next(facts, deck, Random.Default)
                    if (read >= facts.size && n.pos == n.history.lastIndex) Toast.makeText(ctx, "Вы прочитали все ${facts.size} фактов! Начинаем новый круг.", Toast.LENGTH_LONG).show()
                    saveDeck(n)
                },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Следующий факт") }
        }
    }
}

private val TABS = listOf("Факты", "Истории", "Вопросы", "Темы", "Школа", "Тренажёр", "Фразы", "Избранное")
private const val FAV_TAB = 7
private val TAB_GLYPHS = listOf("ui:bulb", "ui:book", "ui:search", "ui:globe", "ui:cap", "ui:target", "ui:notebook", "ui:heart")
private val TAB_HINTS = listOf(
    "Интересные факты по сферам — те же, что на главной. Листайте стрелками, ★ — в избранное.",
    "Короткие истории, которые можно пересказать за минуту. Нажмите, чтобы раскрыть.",
    "Вопросы для разговора по ситуациям: знакомство, работа, свидание, нетворкинг и другие.",
    "Повод месяца и темы с ассоциациями — когда не за что зацепиться.",
    "Приёмы из книг о смолтоке: как начать, поддержать, слушать и красиво закончить разговор.",
    "Упражнения: упражнение дня, светофор реплики, мнение за 3 секунды, конструктор историй.",
    "Готовые фразы для трудных моментов. Нажмите на фразу — она скопируется.",
    "Сохранённые факты и истории — освежите их перед встречей.",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmallTalkScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val data = remember { TalkRepo.load(ctx) }
    val settings by observe(null) { Graph.prefs.settings }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Screen("Small Talks", onBack = { nav.popBackStack() }) { pad ->
        val s = settings ?: return@Screen
        val favorites = SmallTalk.decodeIds(s.talkFavorites)
        Column(Modifier.padding(pad)) {
        // Вкладки раздела — отдельной полосой сверху, чтобы их не путали с фильтрами сфер.
        ScrollableTabRow(
            selectedTabIndex = tab, edgePadding = 12.dp,
            containerColor = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.primary,
        ) {
            TABS.forEachIndexed { i, t ->
                Tab(
                    selected = tab == i, onClick = { tab = i },
                    icon = { Glyph(TAB_GLYPHS[i], 22.dp) },
                    text = { Text(if (i == FAV_TAB && favorites.isNotEmpty()) "$t · ${favorites.size}" else t, maxLines = 1) },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = LocalExtra.current.dim,
                )
            }
        }
        androidx.compose.runtime.key(tab) {
        LazyColumn(Modifier.padding(horizontal = 16.dp)) {
            item {
                Text(TAB_HINTS[tab], fontSize = 13.sp, color = LocalExtra.current.dim, modifier = Modifier.padding(top = 10.dp, bottom = 10.dp))
            }
            when (tab) {
                0 -> factsTab(data, s, favorites)
                1 -> storiesTab(data, s, favorites)
                2 -> questionsTab(data)
                3 -> topicsTab(data)
                4 -> guideTab(data)
                5 -> trainerTab(data, s)
                6 -> phrasesTab(data)
                else -> favoritesTab(data, favorites) { tab = it }
            }
            item { HowTo("small_talk") }
            item { Gap(32.dp) }
        }
        }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SphereChips(counts: Map<String, Int>, selected: Set<String>, onChange: (Set<String>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Pill("Все", selected.isEmpty()) { onChange(emptySet()) }
        SmallTalk.SPHERES.filter { (counts[it.first] ?: 0) > 0 }.forEach { (name, glyph) ->
            Pill(name, name in selected, glyph) { onChange(if (name in selected) selected - name else selected + name) }
        }
    }
}

private fun saveSpheres(set: Set<String>) = io { Graph.prefs.update { it.copy(talkSpheres = SmallTalk.encodeSet(set)) } }

private fun androidx.compose.foundation.lazy.LazyListScope.factsTab(data: TalkRepo.Data, s: AppSettings, favorites: List<Int>) {
    item {
        val ctx = LocalContext.current
        val extra = LocalExtra.current
        val spheres = SmallTalk.decodeSet(s.talkSpheres)
        val counts = remember(data) { data.facts.groupingBy { it.tag }.eachCount() }
        val facts = remember(data, s.talkSpheres) { SmallTalk.filter(data.facts, spheres) }
        val deck = remember(s.factsHistory, s.factsPos, facts) { SmallTalk.ensure(facts, deckOf(s), Random.Default) }
        LaunchedEffect(deck) { if (deck != deckOf(s)) saveDeck(deck) }
        val fact = SmallTalk.current(facts, deck)
        val read = SmallTalk.read(facts, deck)

        Text("Сферы — они же показываются на главной", fontSize = 12.sp, color = extra.dim)
        Gap(6.dp)
        SphereChips(counts, spheres) { saveSpheres(it) }
        Gap(12.dp)
        if (fact != null) Tile {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph(SmallTalk.glyph(fact.tag), 26.dp)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(fact.tag, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Text("Прочитано $read из ${facts.size}", fontSize = 12.sp, color = extra.dim)
                }
                IconButton(onClick = { toggleFavorite(fact.id) }) {
                    if (fact.id in favorites) Icon(Icons.Filled.Star, "Убрать из избранного", tint = MaterialTheme.colorScheme.primary)
                    else Icon(Icons.Outlined.StarOutline, "В избранное")
                }
                IconButton(onClick = { TalkRepo.share(ctx, fact.text) }) { Icon(Icons.Filled.Share, "Поделиться") }
            }
            AnimatedContent(fact, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "talkFact") { f ->
                Column(Modifier.fillMaxWidth().heightIn(min = 130.dp).padding(top = 10.dp)) {
                    Text(f.text, fontSize = 17.sp, lineHeight = 24.sp)
                    Gap(10.dp)
                    Text("Как начать: «${SmallTalk.bridge(f.tag)}»", fontSize = 13.sp, fontStyle = FontStyle.Italic, color = extra.dim)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedIconButton(onClick = { saveDeck(SmallTalk.prev(facts, deck, Random.Default)) }, enabled = SmallTalk.hasPrev(facts, deck)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Предыдущий")
                }
                Box(Modifier.weight(1f).padding(horizontal = 12.dp)) { Bar(read / facts.size.toFloat(), MaterialTheme.colorScheme.primary, height = 4.dp) }
                OutlinedIconButton(onClick = { saveDeck(SmallTalk.random(facts, deck, Random.Default)) }) { Icon(Icons.Filled.Refresh, "Случайный") }
                Box(Modifier.padding(start = 8.dp)) {
                    FilledIconButton(
                        onClick = { saveDeck(SmallTalk.next(facts, deck, Random.Default)) },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Следующий") }
                }
            }
        }
        FactSearch(data.facts, favorites) { id -> saveDeck(SmallTalk.jump(deckOf(s), id)) }
        Hint(
            "talk_facts", "Выберите одну или несколько сфер — факты на главной будут только из них. ★ сохраняет факт в «Избранное», " +
                "кнопка ↻ — случайный факт. Под фактом — фраза, с которой его удобно начать в разговоре.",
            Modifier.padding(top = 12.dp), title = "Как пользоваться",
        )
    }
}

@Composable
private fun FactSearch(facts: List<Fact>, favorites: List<Int>, onPick: (Int) -> Unit) {
    val extra = LocalExtra.current
    var q by rememberSaveable { mutableStateOf("") }
    SectionTitle("Поиск по фактам")
    OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Например: Ван Гог, пчёлы, Луна") })
    if (q.trim().length >= 2) {
        val found = remember(q, facts) { facts.filter { SmallTalk.matches(it.text + " " + it.tag, q) }.take(30) }
        if (found.isEmpty()) Text("Ничего не нашлось.", color = extra.dim, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        found.forEach { f ->
            Column(Modifier.fillMaxWidth().clickable { onPick(f.id); q = "" }.padding(vertical = 8.dp)) {
                Text((if (f.id in favorites) "★ " else "") + f.tag, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Text(f.text, fontSize = 14.sp, maxLines = 3)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.storiesTab(data: TalkRepo.Data, s: AppSettings, favorites: List<Int>) {
    item {
        val extra = LocalExtra.current
        val spheres = SmallTalk.decodeSet(s.talkSpheres)
        val counts = remember(data) { data.stories.groupingBy { it.sphere }.eachCount() }
        SphereChips(counts, spheres) { saveSpheres(it) }
        Gap(10.dp)
    }
    val spheres = SmallTalk.decodeSet(s.talkSpheres)
    val list = data.stories.filter { spheres.isEmpty() || it.sphere in spheres }.ifEmpty { data.stories }
    items(list, key = { it.id }) { st -> StoryCard(st, st.id in favorites) }
}

@Composable
private fun StoryCard(st: SmallTalk.Story, favorite: Boolean) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    var open by rememberSaveable(st.id) { mutableStateOf(false) }
    Tile(Modifier.padding(bottom = 8.dp), onClick = { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph(SmallTalk.glyph(st.sphere), 22.dp)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(st.title, fontWeight = FontWeight.SemiBold)
                Text(st.sphere, fontSize = 12.sp, color = extra.dim)
            }
            IconButton(onClick = { toggleFavorite(st.id) }) {
                if (favorite) Icon(Icons.Filled.Star, "Убрать из избранного", tint = MaterialTheme.colorScheme.primary)
                else Icon(Icons.Outlined.StarOutline, "В избранное")
            }
        }
        if (open) {
            Text(st.text, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { TalkRepo.share(ctx, "${st.title}\n\n${st.text}") }) { Icon(Icons.Filled.Share, "Поделиться") }
            }
        } else Text(st.text, fontSize = 13.sp, color = extra.dim, maxLines = 2, modifier = Modifier.padding(top = 6.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.questionsTab(data: TalkRepo.Data) {
    item {
        val ctx = LocalContext.current
        val extra = LocalExtra.current
        val situations = remember(data) { data.questions.map { it.situation }.distinct() }
        var situation by rememberSaveable { mutableStateOf("") }
        val pool = data.questions.filter { situation.isEmpty() || it.situation == situation }
        var pick by rememberSaveable(situation) { mutableIntStateOf(if (pool.isEmpty()) -1 else Random.nextInt(pool.size)) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("Все", situation.isEmpty()) { situation = "" }
            situations.forEach { t -> Pill(t, t == situation) { situation = t } }
        }
        Gap(10.dp)
        pool.getOrNull(pick)?.let { q ->
            Tile(color = MaterialTheme.colorScheme.primary.copy(alpha = .14f)) {
                Text(q.situation, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Text(q.text, fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Спросите и слушайте: уточняйте детали из ответа.", Modifier.weight(1f), fontSize = 12.sp, color = extra.dim)
                    IconButton(onClick = { TalkRepo.share(ctx, q.text) }) { Icon(Icons.Filled.Share, "Поделиться") }
                    FilledIconButton(
                        onClick = { if (pool.size > 1) { var n: Int; do { n = Random.nextInt(pool.size) } while (n == pick); pick = n } },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) { Icon(Icons.Filled.Refresh, "Другой вопрос") }
                }
            }
        }
        SectionTitle(if (situation.isEmpty()) "Все вопросы · ${pool.size}" else "$situation · ${pool.size}")
        pool.forEach { q ->
            Text("• ${q.text}", fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(vertical = 5.dp))
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.favoritesTab(data: TalkRepo.Data, favorites: List<Int>, goTab: (Int) -> Unit) {
    val facts = data.facts.associateBy { it.id }
    val stories = data.stories.associateBy { it.id }
    val favFacts = favorites.mapNotNull { facts[it] }
    val favStories = favorites.mapNotNull { stories[it] }
    if (favFacts.isEmpty() && favStories.isEmpty()) item {
        Text("Пока пусто. Нажмите ☆ у факта или истории — они появятся здесь, чтобы освежить их перед встречей.", color = LocalExtra.current.dim, fontSize = 14.sp)
    }
    if (favFacts.isNotEmpty()) item { SectionTitle("Факты · ${favFacts.size}") }
    items(favFacts, key = { "f${it.id}" }) { f ->
        val ctx = LocalContext.current
        Tile(Modifier.padding(bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph(SmallTalk.glyph(f.tag), 20.dp)
                Text(f.tag, Modifier.weight(1f).padding(start = 8.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                IconButton(onClick = { TalkRepo.share(ctx, f.text) }) { Icon(Icons.Filled.Share, "Поделиться") }
                IconButton(onClick = { toggleFavorite(f.id) }) { Icon(Icons.Filled.Star, "Убрать из избранного", tint = MaterialTheme.colorScheme.primary) }
            }
            Text(f.text, fontSize = 15.sp, lineHeight = 21.sp)
        }
    }
    if (favStories.isNotEmpty()) item { SectionTitle("Истории · ${favStories.size}") }
    items(favStories, key = { "s${it.id}" }) { st -> StoryCard(st, true) }
}
