package com.dasein.poryadok.logic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * «В этот день в культуре»: дни рождения звёзд (кино, музыка, литература, искусство) и культурные события —
 * премьеры фильмов и спектаклей, выход альбомов и книг, премии, фестивали. Россия, США и весь мир.
 * Источники: встроенная подборка (работает без интернета) и раздел «В этот день» русской Википедии.
 */
object CultureDay {
    const val BIRTH = "birth"
    const val EVENT = "event"
    val REGIONS = listOf("Россия", "США", "Мир")

    data class Item(
        val kind: String,
        val year: Int,
        /** Имя человека или название события. */
        val name: String,
        /** Кто это или что произошло. */
        val text: String,
        val region: String,
        val context: String = "",
        val image: String = "",
        val url: String = "",
        val source: String = "DASEIN",
    ) {
        val yearLabel get() = if (year < 0) "${-year} до н. э." else "$year"
        /** Сколько лет прошло (юбилей). */
        fun yearsAgo(now: Int) = now - year
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.obj(k: String) = this[k] as? JsonObject
    private fun JsonObject.arr(k: String) = this[k] as? JsonArray

    /** Профессии из мира культуры (по началу слова). */
    private val PEOPLE = listOf(
        "актёр", "актер", "актрис", "киноактёр", "киноактрис", "певец", "певиц", "музыкант", "композитор", "режиссёр", "режиссер",
        "кинорежиссёр", "кинорежиссер", "сценарист", "продюсер", "рэпер", "гитарист", "барабанщик", "басист", "вокалист", "пианист",
        "скрипач", "виолончелист", "дирижёр", "дирижер", "танцовщ", "балерин", "хореограф", "писател", "поэт", "драматург", "прозаик",
        "художни", "живописец", "скульптор", "фотограф", "фотомодел", "модель", "модельер", "дизайнер", "телеведущ", "шоумен", "комик",
        "юморист", "мультипликатор", "аниматор", "иллюстратор", "блогер", "оперн", "исполнител", "автор песен", "автор-исполнител",
        "диджей", "кинооператор", "клоун", "иллюзионист", "сказочни", "бард", "солист", "рок-музыкант",
    )

    /** Слова, по которым событие относится к культуре. */
    private val EVENT_WORDS = listOf(
        "фильм", "кинофильм", "мультфильм", "мультсериал", "сериал", "премьер", "альбом", "сингл", "песн", "концерт", "фестивал",
        "оскар", "грэмми", "эмми", "золотой глобус", "каннск", "венецианск", "берлинале", "кинофестивал", "спектакл", "театр", "опер",
        "балет", "мюзикл", "выставк", "музей", "галере", "роман", "книг", "повест", "поэм", "рассказ", "журнал", "комикс",
        "телепередач", "телесериал", "телешоу", "телеканал", "рок-групп", "музыкальная группа", "the beatles", "битлз",
        "видеоигр", "компьютерная игра", "евровидени", "голливуд", "кинотеатр", "киностуди", "мультипликац", "симфони",
        "консерватор", "филармони", "цирк", "эстрад", "дисней", "pixar", "marvel",
    )

    private fun lc(s: String) = s.lowercase().replace('ё', 'е')

    fun isCulturePerson(desc: String): Boolean = lc(desc).let { d -> PEOPLE.any { w -> Regex("(^|[\\s,(«-])" + Regex.escape(lc(w))).containsMatchIn(d) } }

    fun isCultureEvent(text: String): Boolean = lc(text).let { d -> EVENT_WORDS.any { lc(it) in d } }

    /** Страна по описанию: российский/советский → Россия, американский → США, иначе — мир. */
    fun regionOf(text: String): String {
        val d = lc(text)
        return when {
            listOf("российск", "советск", "русск", "в москве", "в санкт-петербург", "в ленинград", "в ссср", "в россии").any { it in d } -> "Россия"
            listOf("американск", "в сша", "голливуд", "в нью-йорк", "в лос-андже", "бродвей").any { it in d } -> "США"
            else -> "Мир"
        }
    }

    /** Встроенная подборка: {"items":[{"md":"01-08","year":1935,"kind":"birth","name":…,"text":…,"region":…}]}. */
    fun parseBuiltIn(text: String): Map<String, List<Item>> = runCatching {
        (json.parseToJsonElement(text) as JsonObject).arr("items").orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val md = o.str("md") ?: return@mapNotNull null
            md to Item(
                o.str("kind") ?: BIRTH, (o["year"] as? JsonPrimitive)?.intOrNull ?: 0, o.str("name").orEmpty(), o.str("text").orEmpty(),
                o.str("region") ?: "Мир", context = o.str("context").orEmpty(),
            )
        }.groupBy({ it.first }, { it.second })
    }.getOrDefault(emptyMap())

    private fun pageOf(o: JsonObject): JsonObject? = o.arr("pages").orEmpty().mapNotNull { it as? JsonObject }
        .firstOrNull { p -> !p.str("extract").isNullOrBlank() && !Regex("^\\d{1,4}( год)?$").matches((p.obj("titles")?.str("normalized") ?: p.str("title").orEmpty()).replace('_', ' ')) }

    private fun titleOf(p: JsonObject?) = (p?.obj("titles")?.str("normalized") ?: p?.str("normalizedtitle") ?: p?.str("title"))?.replace('_', ' ').orEmpty()
    private fun urlOf(p: JsonObject?) = p?.obj("content_urls")?.obj("mobile")?.str("page") ?: p?.obj("content_urls")?.obj("desktop")?.str("page").orEmpty()

    /** Из ответа Википедии onthisday/all: рождения людей культуры и культурные события. */
    fun parseWiki(text: String): List<Item> = runCatching {
        val root = json.parseToJsonElement(text) as JsonObject
        val births = root.arr("births").orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val year = (o["year"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
            val t = o.str("text")?.trim().orEmpty()
            val page = pageOf(o)
            val desc = t.substringAfter(", ", page?.str("description").orEmpty()).ifBlank { page?.str("description").orEmpty() }
            if (!isCulturePerson(desc)) return@mapNotNull null
            Item(
                BIRTH, year, t.substringBefore(", ").ifBlank { titleOf(page) }, desc.replaceFirstChar { it.uppercase() },
                regionOf(desc + " " + page?.str("description").orEmpty()), page?.str("extract").orEmpty(),
                page?.obj("thumbnail")?.str("source").orEmpty(), urlOf(page), "Википедия",
            )
        }
        val events = (root.arr("selected").orEmpty() + root.arr("events").orEmpty()).mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val year = (o["year"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
            val t = o.str("text")?.trim().orEmpty()
            if (t.isBlank() || !isCultureEvent(t)) return@mapNotNull null
            val page = pageOf(o)
            Item(
                EVENT, year, titleOf(page).ifBlank { t.take(60) }, t.replaceFirstChar { it.uppercase() }, regionOf(t),
                page?.str("extract").orEmpty(), page?.obj("thumbnail")?.str("source").orEmpty(), urlOf(page), "Википедия",
            )
        }
        (births + events).distinctBy { it.kind to it.year to it.name }
    }.getOrDefault(emptyList())

    /** Сначала встроенная подборка, потом Википедия (с фото и подробностями — выше). Повторы по имени отбрасываются. */
    fun merge(builtIn: List<Item>, wiki: List<Item>): List<Item> {
        val names = builtIn.map { lc(it.name) }.toSet()
        val w = wiki.filter { lc(it.name) !in names }
            .sortedWith(compareByDescending<Item> { it.image.isNotBlank() }.thenByDescending { it.context.length > 150 }.thenBy { it.year })
        return builtIn + w
    }
}
