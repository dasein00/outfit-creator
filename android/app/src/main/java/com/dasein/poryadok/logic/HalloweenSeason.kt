package com.dasein.poryadok.logic

import java.time.LocalDate

/**
 * Хэллоуинский сезон оформления: с 15 октября по 2 ноября включительно, каждый год, по дате телефона.
 * Чистая логика без Android — проверяется модульными тестами.
 */
object HalloweenSeason {
    fun inSeason(d: LocalDate): Boolean = (d.monthValue == 10 && d.dayOfMonth >= 15) || (d.monthValue == 11 && d.dayOfMonth <= 2)

    /** Тема действует: автоматический режим и сезон — или тестовый режим ([preview]) в любой день. */
    fun themeActive(auto: Boolean, d: LocalDate, preview: Boolean = false): Boolean = preview || (auto && inSeason(d))

    /** Ближайший день начала сезона — для подписи в настройках. */
    fun nextStart(d: LocalDate): LocalDate {
        val thisYear = LocalDate.of(d.year, 10, 15)
        return if (d.isAfter(LocalDate.of(d.year, 11, 2))) thisYear.plusYears(1) else thisYear
    }

    /** Последний день сезона, который идёт или начнётся. */
    fun seasonEnd(d: LocalDate): LocalDate =
        if (d.isAfter(LocalDate.of(d.year, 11, 2))) LocalDate.of(d.year + 1, 11, 2) else LocalDate.of(d.year, 11, 2)
}
