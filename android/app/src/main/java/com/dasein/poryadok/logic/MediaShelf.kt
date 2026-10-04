package com.dasein.poryadok.logic

import com.dasein.poryadok.data.MediaItem
import com.dasein.poryadok.data.MediaStatus

/** Умные папки и группировка фильмотеки. Чистые функции — проверяются тестами. */
object MediaShelf {
    /** Страны, по которым карточка считается дорамой или азиатским кино. */
    val ASIA = listOf("корея", "китай", "япония", "тайвань", "таиланд", "гонконг", "филиппины", "вьетнам", "сингапур", "индонезия", "малайзия")

    fun isAsian(m: MediaItem): Boolean {
        val c = m.countries.lowercase()
        return ASIA.any { c.contains(it) } || m.tags.lowercase().contains("дорам") || m.genres.lowercase().contains("дорам")
    }

    /** Умная папка: ключ, название, иконка и условие. Обновляется сама. */
    data class Smart(val key: String, val title: String, val glyph: String, val test: (MediaItem, Long) -> Boolean)

    val SMART = listOf(
        Smart("smart:fav", "Любимое", "habit/28") { m, _ -> m.favorite },
        Smart("smart:asia", "Дорамы и Азия", "habit/03") { m, _ -> isAsian(m) },
        Smart("smart:recent", "Добавлено за месяц", "ui:calendar") { m, now -> now - m.createdAt <= 30L * 86_400_000 },
        Smart("smart:unrated", "Без моей оценки", "ui:target") { m, _ -> m.status == MediaStatus.DONE && m.myRating == 0 },
        Smart("smart:top", "Мои 9–10", "ui:check") { m, _ -> m.myRating >= 9 },
    )

    fun smart(key: String): Smart? = SMART.firstOrNull { it.key == key }

    /** Как группировать коллекцию. */
    enum class Group(val label: String) {
        NONE("Без группировки"), STATUS("По статусу"), DECADE("По десятилетиям"), GENRE("По жанру"),
        COUNTRY("По стране"), RATING("По моей оценке"), FOLDER("По папкам"), ADDED("По месяцу добавления"),
    }

    private val MONTHS = listOf("январь", "февраль", "март", "апрель", "май", "июнь", "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь")

    /**
     * Разбивает уже отсортированный список на группы с заголовками, порядок групп — логичный для каждого вида.
     * [folders] — названия папок карточки (для группировки по папкам; карточка может попасть в несколько).
     */
    fun group(list: List<MediaItem>, g: Group, statusNames: List<String>, folders: (MediaItem) -> List<String> = { emptyList() }): List<Pair<String, List<MediaItem>>> {
        if (g == Group.NONE) return listOf("" to list)
        fun keysOf(m: MediaItem): List<String> = when (g) {
            Group.STATUS -> listOf(statusNames.getOrElse(m.status) { "Другое" })
            Group.DECADE -> listOf(MediaCatalog.decade(m.year) ?: "Год не указан")
            Group.GENRE -> listOf(MediaCatalog.split(m.genres).firstOrNull() ?: "Без жанра")
            Group.COUNTRY -> listOf(MediaCatalog.split(m.countries).firstOrNull() ?: "Страна не указана")
            Group.RATING -> listOf(if (m.myRating > 0) "★ ${m.myRating}" else "Без оценки")
            Group.FOLDER -> folders(m).ifEmpty { listOf("Вне папок") }
            Group.ADDED -> listOf(
                if (m.createdAt <= 0) "Давно" else java.time.Instant.ofEpochMilli(m.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    .let { "${MONTHS[it.monthValue - 1].replaceFirstChar(Char::uppercase)} ${it.year}" },
            )
            Group.NONE -> listOf("")
        }
        val map = LinkedHashMap<String, MutableList<MediaItem>>()
        list.forEach { m -> keysOf(m).forEach { k -> map.getOrPut(k) { mutableListOf() } += m } }
        val entries = map.entries.map { it.key to it.value.toList() }
        return when (g) {
            Group.STATUS -> entries.sortedBy { (k, _) -> statusNames.indexOf(k).let { if (it < 0) 99 else listOf(1, 0, 2, 3).indexOf(it) } }
            Group.DECADE -> entries.sortedByDescending { (k, _) -> k.take(4).toIntOrNull() ?: 0 }
            Group.RATING -> entries.sortedByDescending { (k, _) -> k.removePrefix("★ ").toIntOrNull() ?: 0 }
            Group.GENRE, Group.COUNTRY, Group.FOLDER -> entries.sortedWith(compareByDescending<Pair<String, List<MediaItem>>> { it.second.size }.thenBy { it.first })
            else -> entries
        }
    }

    /**
     * Что поменять в топе «Что посмотреть»: какие карточки добавить и какие пункты убрать.
     * [planned] — id запланированных карточек, [links] — пункт топа → карточка.
     */
    fun watchDiff(planned: List<Long>, links: Map<Long, Long>): Pair<List<Long>, List<Long>> {
        val set = planned.toSet()
        val have = links.values.toSet()
        return planned.filter { it !in have } to links.filter { it.value !in set }.keys.toList()
    }
}
