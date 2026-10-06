package com.dasein.poryadok.logic

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.math.abs
import kotlin.random.Random

/**
 * «Репетитор» английского: слова с переводом и примерами, уровни CEFR (A1–C1), частотность, темы,
 * ежедневный набор слов и интервальное повторение по коробкам Лейтнера (как в Anki, Lingualeo, ReWord).
 */
object Tutor {
    data class Word(val en: String, val ru: String, val ex: String, val exRu: String, val level: String, val freq: Int, val topic: String, val custom: Boolean = false) {
        val id get() = en.lowercase()
    }

    val LEVELS = listOf("A1", "A2", "B1", "B2", "C1")
    val LEVEL_NAMES = mapOf("A1" to "A1 · начальный", "A2" to "A2 · элементарный", "B1" to "B1 · средний", "B2" to "B2 · выше среднего", "C1" to "C1 · продвинутый")
    val TOPICS = linkedMapOf(
        "basics" to "Базовые слова", "people" to "Люди и семья", "food" to "Еда и напитки", "home" to "Дом и быт",
        "travel" to "Путешествия", "city" to "Город и транспорт", "work" to "Работа и офис", "business" to "Деньги и бизнес",
        "it" to "IT и технологии", "health" to "Здоровье", "feelings" to "Эмоции и характер", "nature" to "Природа и погода",
        "study" to "Учёба и наука", "shopping" to "Покупки", "sport" to "Спорт и хобби", "phrasal" to "Фразовые глаголы",
        "idioms" to "Идиомы", "my" to "Мои слова",
    )
    val TOPIC_GLYPH = mapOf(
        "basics" to "tutor/00", "people" to "tutor/09", "food" to "food/02", "home" to "tutor/10", "travel" to "tutor/04",
        "city" to "tutor/11", "work" to "tutor/12", "business" to "ui:wallet", "it" to "tutor/13", "health" to "pressure/01",
        "feelings" to "ui:smile", "nature" to "tutor/15", "study" to "tutor/01", "shopping" to "ui:cart", "sport" to "sport/00",
        "phrasal" to "tutor/14", "idioms" to "tutor/17", "my" to "tutor/05",
    )
    val FREQ_NAMES = mapOf(1 to "Самые частые", 2 to "Частые и средние", 3 to "Все, включая редкие")

    private val json = Json { ignoreUnknownKeys = true }

    /** Встроенный словарь: {"words":[[en, ru, example, exampleRu, level, freq, topic], …]}. */
    fun parse(text: String): List<Word> = runCatching {
        ((json.parseToJsonElement(text) as JsonObject)["words"] as JsonArray).mapNotNull { e ->
            val a = e as? JsonArray ?: return@mapNotNull null
            fun s(i: Int) = (a.getOrNull(i) as? JsonPrimitive)?.contentOrNull.orEmpty()
            Word(s(0), s(1), s(2), s(3), s(4), (a.getOrNull(5) as? JsonPrimitive)?.intOrNull ?: 2, s(6))
        }
    }.getOrDefault(emptyList())

    // ---------- Настройки и прогресс ----------

    @Serializable
    data class Settings(
        val levels: List<String> = listOf("A2", "B1"),
        /** 1 — только самые частые, 2 — частые и средние, 3 — все. */
        val freq: Int = 2,
        /** Пусто — все темы. */
        val topics: List<String> = emptyList(),
        val perDay: Int = 10,
        /** 0 — тема дня, 1 — случайные слова, 2 — смешанно. */
        val mode: Int = 0,
        /** Сначала показывать английское слово (true) или русское. */
        val enFirst: Boolean = true,
    )

    @Serializable
    data class Progress(val box: Int = 0, val due: Long = 0, val right: Int = 0, val wrong: Int = 0, val added: Long = 0)

    /** Коробки Лейтнера: через сколько дней повторить слово из коробки 1…7. В 7-й — выучено. */
    val INTERVALS = listOf(0, 1, 2, 4, 7, 15, 30, 60)
    const val LEARNED_BOX = 5

    fun answer(p: Progress?, correct: Boolean, today: Long): Progress {
        val cur = p ?: Progress(added = today)
        return if (correct) {
            val b = (cur.box + 1).coerceAtMost(INTERVALS.size - 1)
            cur.copy(box = b, due = today + INTERVALS[b], right = cur.right + 1)
        } else cur.copy(box = 1, due = today + 1, wrong = cur.wrong + 1)
    }

    /** «Уже знаю» — сразу в выученные, но с контрольным повтором через месяц. */
    fun known(p: Progress?, today: Long) = (p ?: Progress(added = today)).copy(box = 6, due = today + 30)

    fun matches(w: Word, s: Settings): Boolean =
        (w.custom || w.level in s.levels) && (w.custom || w.freq <= s.freq) && (s.topics.isEmpty() || w.topic in s.topics || (w.custom && "my" in s.topics))

    /** Тема дня: по кругу из выбранных тем (или всех), меняется каждый день. */
    fun topicOfDay(s: Settings, day: Long, words: List<Word>): String {
        val ts = (s.topics.ifEmpty { TOPICS.keys.filter { it != "my" } }).filter { t -> words.any { it.topic == t && matches(it, s) } }
            .ifEmpty { TOPICS.keys.filter { it != "my" } }
        return ts[(abs(day) % ts.size).toInt()]
    }

    /**
     * Новые слова на сегодня: ещё не начатые, подходящие под уровень, частотность и темы.
     * Тема дня — все из одной темы; случайные — из всех; смешанно — половина темы дня, половина случайных.
     * Сначала более частые слова. Набор на день повторяется при повторном открытии (зерно = день).
     */
    fun dailyNew(words: List<Word>, progress: Map<String, Progress>, s: Settings, day: Long): List<Word> {
        val fresh = words.filter { matches(it, s) && it.id !in progress }
        val rnd = Random(day * 31 + s.levels.hashCode())
        val topic = topicOfDay(s, day, words)
        fun pick(src: List<Word>, n: Int) = src.shuffled(rnd).sortedBy { it.freq }.take(n)
        val n = s.perDay
        val list = when (s.mode) {
            0 -> pick(fresh.filter { it.topic == topic }, n).let { it + pick(fresh - it.toSet(), n - it.size) }
            1 -> pick(fresh, n)
            else -> {
                val a = pick(fresh.filter { it.topic == topic }, n / 2)
                a + pick(fresh - a.toSet(), n - a.size)
            }
        }
        return list.take(n)
    }

    fun due(words: List<Word>, progress: Map<String, Progress>, today: Long): List<Word> =
        words.filter { w -> progress[w.id]?.let { it.due <= today } == true }

    // ---------- Упражнения ----------

    /** Варианты ответа: правильный + похожие (та же тема и уровень), без повторов перевода. */
    fun options(w: Word, all: List<Word>, n: Int, rnd: Random, ruAnswers: Boolean): List<Word> {
        val pool = all.filter { it.id != w.id && (if (ruAnswers) it.ru != w.ru else it.en.lowercase() != w.en.lowercase()) }
        val near = pool.filter { it.topic == w.topic }.shuffled(rnd).take(n)
        val rest = pool.filter { it !in near && it.level == w.level }.shuffled(rnd)
        val wrong = (near + rest + pool.shuffled(rnd)).distinctBy { if (ruAnswers) it.ru else it.en.lowercase() }.take(n - 1)
        return (wrong + w).shuffled(rnd)
    }

    /** Ответ при написании: регистр и пробелы не важны, одна опечатка прощается в словах от 5 букв. */
    fun typedOk(answer: String, w: Word): Boolean {
        val a = norm(answer); val b = norm(w.en)
        if (a == b) return true
        return b.length >= 5 && lev(a, b) <= 1
    }

    private fun norm(s: String) = s.trim().lowercase().replace('’', '\'').replace(Regex("\\s+"), " ").removePrefix("to ")

    fun lev(a: String, b: String): Int {
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]; d[0] = i
            for (j in 1..b.length) {
                val t = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = t
            }
        }
        return d[b.length]
    }

    /** Пример с пропуском: слово заменено на «___» (для упражнения «Вставь слово»). */
    fun cloze(w: Word): String? {
        val exact = Regex("(?i)\\b" + Regex.escape(w.en) + "\\w*")
        if (exact.containsMatchIn(w.ex)) return w.ex.replace(exact, "___")
        val stem = w.en.split(' ').first().take(4)
        if (stem.length < 3) return null
        val r = Regex("(?i)\\b" + Regex.escape(stem) + "\\w*")
        return if (r.containsMatchIn(w.ex)) w.ex.replaceFirst(r, "___") else null
    }

    // ---------- Статистика ----------

    data class Stats(val learned: Int, val learning: Int, val due: Int, val total: Int, val byLevel: Map<String, Pair<Int, Int>>)

    fun stats(words: List<Word>, progress: Map<String, Progress>, today: Long): Stats {
        val learned = progress.count { it.value.box >= LEARNED_BOX }
        val learning = progress.count { it.value.box in 0 until LEARNED_BOX }
        val byLevel = LEVELS.associateWith { l ->
            val ws = words.filter { it.level == l }
            ws.count { (progress[it.id]?.box ?: 0) >= LEARNED_BOX } to ws.size
        }
        return Stats(learned, learning, due(words, progress, today).size, words.size, byLevel)
    }

    /** Серия дней подряд, когда цель дня была выполнена (сегодня можно ещё не успеть). */
    fun streak(days: Set<Long>, today: Long): Int {
        var d = if (today in days) today else today - 1
        var n = 0
        while (d in days) { n++; d-- }
        return n
    }
}
