package com.dasein.poryadok.logic

import kotlin.random.Random

/**
 * Small Talks: факты для общего развития, короткие истории, вопросы для разговора и приёмы.
 * База — текстовые файлы в assets/smalltalk:
 *  facts*.txt   «Сфера|Текст»
 *  stories.txt  «Сфера|Заголовок|Текст»
 *  questions.txt «Ситуация|Вопрос»
 *  tips.txt     «Заголовок|Текст»
 * id — хэш текста, поэтому пополнение базы не сбивает историю и избранное.
 */
object SmallTalk {
    data class Story(val id: Int, val sphere: String, val title: String, val text: String)
    data class Question(val id: Int, val situation: String, val text: String)
    /** Приём из «Школы»: раздел, название, как применять и откуда он (книга или автор). */
    data class Tip(val id: Int, val section: String, val title: String, val text: String, val source: String)
    data class Topic(val name: String, val words: String, val questions: List<String>)
    data class Exercise(val title: String, val text: String)
    /** Своя история по трём шагам: статус-кво, конфликт, развязка. */
    data class MyStory(val title: String, val start: String, val conflict: String, val end: String)

    /** Сферы в порядке показа и их иконки. */
    val SPHERES = listOf(
        "Кино" to "ui:v_film",
        "Музыка" to "ui:v_spark",
        "Искусство" to "ui:image",
        "Литература" to "ui:v_bookopen",
        "Наука" to "ui:bulb",
        "Космос" to "ui:moon",
        "История" to "ui:book",
        "География" to "ui:globe",
        "Животные" to "ui:leaves",
        "Еда" to "ui:salad",
        "Язык" to "ui:cap",
        "Технологии" to "ui:settings",
        "Психология" to "ui:people",
        "Спорт" to "ui:trophy",
        "Юмор" to "ui:smile",
        "Парадоксы" to "ui:wheel",
        "Философия" to "ui:document",
        "Математика" to "ui:grid",
        "Мифология" to "ui:sun",
        "Экономика" to "ui:coins",
        "Архитектура" to "ui:home",
        "Транспорт" to "ui:map",
        "Мода" to "ui:hanger",
        "Здоровье" to "ui:heart",
    )

    /** Темы старой картотеки о здоровье складываются в одну сферу. */
    private val HEALTH_TAGS = setOf(
        "Питание", "Тело", "Мозг", "Сон", "Движение", "Сердце", "Долголетие", "Чувства", "Микробиом", "Кожа",
        "Иммунитет", "Стресс", "Гормоны", "Дыхание", "Вода",
    )

    fun sphereOf(tag: String): String = when {
        tag in HEALTH_TAGS -> "Здоровье"
        tag == "Спорт" || SPHERES.any { it.first == tag } -> tag
        else -> tag
    }

    fun glyph(sphere: String): String = SPHERES.firstOrNull { it.first == sphere }?.second ?: "ui:bulb"

    /** Подводка, чтобы ввернуть факт в разговор естественно. */
    fun bridge(sphere: String): String = when (sphere) {
        "Кино" -> "Кстати, о кино: недавно узнал(а), что…"
        "Музыка" -> "Раз уж заиграла музыка — знаешь, что…"
        "Искусство" -> "Была история про искусство, которая меня зацепила…"
        "Литература" -> "Про книги есть любопытная деталь…"
        "Наука" -> "Наткнулся(лась) на интересную штуку из науки…"
        "Космос" -> "Если посмотреть на небо — вот что поражает…"
        "История" -> "Забавно, но в истории было так…"
        "География" -> "Кстати, про путешествия — знаешь, что…"
        "Животные" -> "Про животных есть факт, который я не могу забыть…"
        "Еда" -> "Раз уж мы про еду — оказывается…"
        "Язык" -> "Люблю такие вещи про слова: оказывается…"
        "Технологии" -> "Из мира техники: знаешь, что…"
        "Психология" -> "Читал(а) про одно исследование о людях…"
        "Спорт" -> "Про спорт есть классная история…"
        "Юмор" -> "Тебе понравится:"
        "Парадоксы" -> "Есть мысленный эксперимент, который взрывает мозг…"
        "Философия" -> "Один философ задал вопрос, над которым я до сих пор думаю…"
        "Математика" -> "Математика умеет удивлять:"
        "Мифология" -> "В мифах есть история, которая многое объясняет…"
        "Экономика" -> "Про деньги есть любопытная штука…"
        "Архитектура" -> "Посмотри на это здание — а знаешь, что…"
        "Транспорт" -> "Раз уж мы в дороге — знаешь, что…"
        "Мода" -> "Про одежду есть неожиданный факт…"
        else -> "Недавно узнал(а) интересное:"
    }

    fun parseFacts(text: String): List<Fact> = text.lineSequence().mapNotNull { line ->
        val i = line.indexOf('|')
        if (i <= 0 || line.startsWith("#")) return@mapNotNull null
        val body = line.substring(i + 1).trim()
        if (body.isEmpty()) null else Fact(body.hashCode(), sphereOf(line.substring(0, i).trim()), body)
    }.distinctBy { it.id }.toList()

    fun parseStories(text: String): List<Story> = text.lineSequence().mapNotNull { line ->
        val p = line.split('|', limit = 3)
        if (p.size < 3 || line.startsWith("#") || p[2].isBlank()) null
        else Story(("s:" + p[2].trim()).hashCode(), p[0].trim(), p[1].trim(), p[2].trim())
    }.distinctBy { it.id }.toList()

    fun parseQuestions(text: String): List<Question> = text.lineSequence().mapNotNull { line ->
        val p = line.split('|', limit = 2)
        if (p.size < 2 || line.startsWith("#") || p[1].isBlank()) null
        else Question(("q:" + p[1].trim()).hashCode(), p[0].trim(), p[1].trim())
    }.distinctBy { it.id }.toList()

    /** «Раздел|Приём|Как применять|Источник». */
    fun parseGuide(text: String): List<Tip> = text.lineSequence().mapNotNull { line ->
        val p = line.split('|', limit = 4)
        if (p.size < 3 || line.startsWith("#") || p[2].isBlank()) null
        else Tip(("t:" + p[1].trim()).hashCode(), p[0].trim(), p[1].trim(), p[2].trim(), p.getOrNull(3)?.trim().orEmpty())
    }.distinctBy { it.id }.toList()

    /** «Тема|О чём говорить|вопрос;вопрос». */
    fun parseTopics(text: String): List<Topic> = text.lineSequence().mapNotNull { line ->
        val p = line.split('|', limit = 3)
        if (p.size < 3 || line.startsWith("#")) null
        else Topic(p[0].trim(), p[1].trim(), p[2].split(';').map { it.trim() }.filter { it.isNotEmpty() })
    }.toList()

    /** «Номер месяца|Темы». */
    fun parseMonths(text: String): Map<Int, String> = text.lineSequence().mapNotNull { line ->
        val p = line.split('|', limit = 2)
        val m = p[0].trim().toIntOrNull()
        if (p.size < 2 || m == null || m !in 1..12) null else m to p[1].trim()
    }.toMap()

    fun parseLines(text: String): List<String> = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList()

    fun parseExercises(text: String): List<Exercise> = text.lineSequence().mapNotNull { line ->
        val p = line.split('|', limit = 2)
        if (p.size < 2 || line.startsWith("#") || p[1].isBlank()) null else Exercise(p[0].trim(), p[1].trim())
    }.toList()

    /** Упражнение дня: одно и то же весь день, каждый день новое. */
    fun <T> ofDay(list: List<T>, epochDay: Long): T? = if (list.isEmpty()) null else list[Math.floorMod(epochDay, list.size.toLong()).toInt()]

    /** Светофор реплики: 0 — зелёный (до 20 с), 1 — жёлтый (до 40 с), 2 — красный. */
    fun light(seconds: Int): Int = when {
        seconds < 20 -> 0
        seconds < 40 -> 1
        else -> 2
    }

    private const val SEP_FIELD = '\u001F'
    private const val SEP_STORY = '\u001E'

    fun encodeStories(list: List<MyStory>): String = list.joinToString(SEP_STORY.toString()) { st ->
        listOf(st.title, st.start, st.conflict, st.end).joinToString(SEP_FIELD.toString()) { it.replace(SEP_FIELD, ' ').replace(SEP_STORY, ' ') }
    }

    fun decodeStories(s: String): List<MyStory> = s.split(SEP_STORY).mapNotNull { rec ->
        val p = rec.split(SEP_FIELD)
        if (p.size < 4 || p.all { it.isBlank() }) null else MyStory(p[0], p[1], p[2], p[3])
    }

    fun decodeSet(s: String): Set<String> = s.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    fun encodeSet(s: Collection<String>): String = s.joinToString(",")

    fun decodeIds(s: String): List<Int> = s.split(',').mapNotNull { it.trim().toIntOrNull() }
    fun encodeIds(ids: List<Int>): String = ids.joinToString(",")

    /** Факты выбранных сфер; пустой выбор — все. */
    fun filter(facts: List<Fact>, spheres: Set<String>): List<Fact> =
        if (spheres.isEmpty()) facts else facts.filter { it.tag in spheres }.ifEmpty { facts }

    /** Переключает избранное: новый список id. */
    fun toggle(ids: List<Int>, id: Int): List<Int> = if (id in ids) ids - id else listOf(id) + ids

    /**
     * Колода: общая история прочитанного (id) и позиция в ней. Фильтр по сферам историю не стирает:
     * «вперёд» и «назад» просто перескакивают через факты других сфер.
     */
    data class Deck(val history: List<Int> = emptyList(), val pos: Int = 0)

    private const val MAX_HISTORY = 2500

    fun current(facts: List<Fact>, d: Deck): Fact? {
        val byId = facts.associateBy { it.id }
        return d.history.getOrNull(d.pos)?.let { byId[it] }
    }

    /** Если текущий факт не из выбранных сфер (или истории нет) — встаёт на подходящий. */
    fun ensure(facts: List<Fact>, d: Deck, rnd: Random): Deck {
        if (facts.isEmpty()) return d
        if (current(facts, d) != null) return d
        val ids = facts.map { it.id }.toSet()
        val back = (d.pos.coerceAtMost(d.history.lastIndex) downTo 0).firstOrNull { d.history[it] in ids }
        if (back != null) return d.copy(pos = back)
        return append(facts, d, rnd)
    }

    fun next(facts: List<Fact>, d0: Deck, rnd: Random): Deck {
        if (facts.isEmpty()) return d0
        val d = ensure(facts, d0, rnd)
        val ids = facts.map { it.id }.toSet()
        val ahead = (d.pos + 1..d.history.lastIndex).firstOrNull { d.history[it] in ids }
        return if (ahead != null) d.copy(pos = ahead) else append(facts, d, rnd)
    }

    fun prev(facts: List<Fact>, d0: Deck, rnd: Random): Deck {
        val d = ensure(facts, d0, rnd)
        val ids = facts.map { it.id }.toSet()
        val back = (d.pos - 1 downTo 0).firstOrNull { d.history[it] in ids } ?: return d
        return d.copy(pos = back)
    }

    fun hasPrev(facts: List<Fact>, d: Deck): Boolean {
        val ids = facts.map { it.id }.toSet()
        return (0 until d.pos.coerceAtMost(d.history.size)).any { d.history[it] in ids }
    }

    /** Случайный факт из выбранных сфер (без повторов, пока есть непрочитанные). */
    fun random(facts: List<Fact>, d: Deck, rnd: Random): Deck = if (facts.isEmpty()) d else append(facts, d, rnd)

    /** Показать конкретный факт (например, из избранного). */
    fun jump(d: Deck, id: Int): Deck {
        val h = (d.history + id).takeLast(MAX_HISTORY)
        return Deck(h, h.lastIndex)
    }

    /** Сколько фактов из выбранных сфер уже прочитано. */
    fun read(facts: List<Fact>, d: Deck): Int {
        val seen = d.history.toSet()
        return facts.count { it.id in seen }
    }

    private fun append(facts: List<Fact>, d: Deck, rnd: Random): Deck {
        val seen = d.history.toSet()
        val cur = d.history.getOrNull(d.pos)
        var pool = facts.filter { it.id !in seen }
        if (pool.isEmpty()) pool = facts.filter { it.id != cur }.ifEmpty { facts }
        val id = pool[rnd.nextInt(pool.size)].id
        // Всё прочитано — начинаем новый круг по этим сферам: убираем их из истории, чтобы снова считались новыми.
        val base = if (facts.all { it.id in seen }) {
            val ids = facts.map { it.id }.toSet()
            d.history.filter { it !in ids }
        } else d.history
        val h = (base + id).takeLast(MAX_HISTORY)
        return Deck(h, h.lastIndex)
    }

    /** Поиск по фактам и историям: все слова запроса должны встретиться. */
    fun matches(text: String, query: String): Boolean {
        val words = query.lowercase().replace('ё', 'е').split(' ').filter { it.isNotBlank() }
        val t = text.lowercase().replace('ё', 'е')
        return words.all { it in t }
    }
}
