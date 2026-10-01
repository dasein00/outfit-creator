package com.dasein.poryadok.logic

import com.dasein.poryadok.data.MediaItem
import com.dasein.poryadok.data.MediaKind
import com.dasein.poryadok.data.MediaStatus

/**
 * Каталог фильмов, сериалов и книг как в агрегаторах: фасеты (жанр, десятилетие, страна, автор),
 * длительность из свободного текста, прогресс и статистика коллекции.
 */
object MediaCatalog {
    /** «драма, комедия / триллер» → [драма, комедия, триллер] с заглавной буквы. */
    fun split(s: String): List<String> =
        s.split(',', ';', '/', '·', '|').map { it.trim().trim('.') }.filter { it.isNotBlank() && it.length < 60 }
            .map { it.replaceFirstChar(Char::uppercase) }.distinct()

    fun decade(year: Int?): String? = year?.takeIf { it in 1850..2100 }?.let { "${it / 10 * 10}-е" }

    /** Минуты из «2 ч 15 мин», «135 мин», «1:55», «115». */
    fun minutes(length: String): Int? {
        val t = length.lowercase()
        Regex("""(\d+)\s*:\s*(\d{2})""").find(t)?.let { return it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt() }
        val h = Regex("""(\d+)\s*(ч|час)""").find(t)?.groupValues?.get(1)?.toIntOrNull()
        val m = Regex("""(\d+)\s*(мин|m)""").find(t)?.groupValues?.get(1)?.toIntOrNull()
        if (h != null || m != null) return (h ?: 0) * 60 + (m ?: 0)
        return t.trim().toIntOrNull()?.takeIf { it in 1..600 }
    }

    /** Число страниц или серий из текста («320 стр.», «3 сезона, 24 серии» → 24). */
    fun count(length: String, kind: Int): Int? {
        val t = length.lowercase()
        if (kind == MediaKind.SERIES) {
            Regex("""(\d+)\s*(сер|эп)""").find(t)?.let { return it.groupValues[1].toInt() }
            return null
        }
        if (kind == MediaKind.BOOK) return Regex("""(\d+)""").find(t)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..20000 }
        return null
    }

    /** Сколько всего серий или страниц: своё значение или из описания длины. */
    fun total(m: MediaItem): Int = m.progressTotal.takeIf { it > 0 } ?: count(m.length, m.kind) ?: 0

    fun progressText(m: MediaItem): String? {
        if (m.kind == MediaKind.MOVIE) return null
        val total = total(m)
        if (m.progress <= 0 && total <= 0) return null
        val unit = if (m.kind == MediaKind.BOOK) "стр." else "сер."
        return if (total > 0) "${m.progress} из $total $unit" else "${m.progress} $unit"
    }

    fun progressShare(m: MediaItem): Float? = total(m).takeIf { it > 0 }?.let { (m.progress / it.toFloat()).coerceIn(0f, 1f) }

    /** Группы личной оценки. */
    val RATING_OPTIONS = listOf("10" to "10 — шедевр", "8" to "8–9", "6" to "6–7", "1" to "1–5", "0" to "Без оценки")

    fun ratingMatches(key: String, r: Int): Boolean = when (key) {
        "" -> true
        "10" -> r == 10
        "8" -> r in 8..9
        "6" -> r in 6..7
        "1" -> r in 1..5
        "0" -> r == 0
        else -> true
    }

    val EXTERNAL_OPTIONS = listOf("8" to "от 8,0", "7" to "от 7,0", "6" to "от 6,0")

    val LENGTH_OPTIONS = listOf("90" to "До 1,5 часа", "120" to "До 2 часов", "150" to "До 2,5 часа", "999" to "Длиннее 2,5 часа")

    fun lengthMatches(key: String, m: MediaItem): Boolean {
        if (key.isEmpty()) return true
        val min = minutes(m.length) ?: return false
        return if (key == "999") min > 150 else min <= key.toInt()
    }

    data class Stats(
        val total: Int,
        val byStatus: Map<Int, Int>,
        val favorites: Int,
        val rated: Int,
        val avgRating: Double,
        /** Сколько оценок каждого балла 1..10. */
        val ratingBars: List<Int>,
        val topGenres: List<Pair<String, Int>>,
        val topCreators: List<Pair<String, Int>>,
        val topCountries: List<Pair<String, Int>>,
        /** Завершено по годам: год → штук. */
        val byYear: List<Pair<Int, Int>>,
        val doneMinutes: Int,
        val donePages: Int,
        val doneEpisodes: Int,
    )

    fun stats(items: List<MediaItem>): Stats {
        val done = items.filter { it.status == MediaStatus.DONE }
        val rated = items.filter { it.myRating > 0 }
        fun top(sel: (MediaItem) -> String) = items.flatMap { split(sel(it)) }.groupingBy { it }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).take(8).map { it.key to it.value }
        return Stats(
            total = items.size,
            byStatus = items.groupingBy { it.status }.eachCount(),
            favorites = items.count { it.favorite },
            rated = rated.size,
            avgRating = if (rated.isEmpty()) 0.0 else rated.sumOf { it.myRating }.toDouble() / rated.size,
            ratingBars = (1..10).map { r -> rated.count { it.myRating == r } },
            topGenres = top { it.genres },
            topCreators = top { it.creators },
            topCountries = top { it.countries },
            byYear = done.mapNotNull { it.finishedDay }.map { Dates.day(it).year }.groupingBy { it }.eachCount().entries.sortedByDescending { it.key }.map { it.key to it.value },
            doneMinutes = done.filter { it.kind == MediaKind.MOVIE }.sumOf { (minutes(it.length) ?: 0) * (1 + it.rewatch) },
            donePages = items.filter { it.kind == MediaKind.BOOK }.sumOf { if (it.status == MediaStatus.DONE) total(it).coerceAtLeast(it.progress) else it.progress },
            doneEpisodes = items.filter { it.kind == MediaKind.SERIES }.sumOf { if (it.status == MediaStatus.DONE) total(it).coerceAtLeast(it.progress) else it.progress },
        )
    }

    /** Похожее из своей коллекции: общие жанры и авторы, сначала с высокой оценкой. */
    fun similar(m: MediaItem, all: List<MediaItem>, n: Int = 8): List<MediaItem> {
        val genres = split(m.genres).toSet()
        val creators = split(m.creators).toSet()
        if (genres.isEmpty() && creators.isEmpty()) return emptyList()
        return all.asSequence().filter { it.id != m.id }.map { o ->
            val g = split(o.genres).count { it in genres }
            val c = split(o.creators).count { it in creators }
            o to (g * 2 + c * 5)
        }.filter { it.second >= 2 }.sortedWith(compareByDescending<Pair<MediaItem, Int>> { it.second }.thenByDescending { it.first.myRating })
            .take(n).map { it.first }.toList()
    }
}
