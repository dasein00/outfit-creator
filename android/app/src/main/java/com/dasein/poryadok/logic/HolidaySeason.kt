package com.dasein.poryadok.logic

import java.time.LocalDate

/**
 * Новогодний сезон оформления: с 15 декабря по 15 января включительно, каждый год, по дате телефона.
 * Чистая логика без Android — проверяется модульными тестами.
 */
object HolidaySeason {
    /** Через сколько после ухода из приложения возвращение считается новым входом (снежинки снова). */
    const val NEW_ENTRY_AFTER_MS = 5 * 60 * 1000L

    /** Сколько идёт снегопад при входе. */
    const val SNOW_MS = 5_000L

    fun inSeason(d: LocalDate): Boolean =
        (d.monthValue == 12 && d.dayOfMonth >= 15) || (d.monthValue == 1 && d.dayOfMonth <= 15)

    /**
     * Новогодняя тема действует: автоматический режим включён и сейчас сезон — или включён тестовый режим
     * ([preview]: тема в любой день, чтобы посмотреть её заранее).
     */
    fun themeActive(auto: Boolean, d: LocalDate, preview: Boolean = false): Boolean = preview || (auto && inSeason(d))

    /** Снежинки показываются только при действующей теме; их выключатель тему не трогает. */
    fun snowAllowed(auto: Boolean, snow: Boolean, d: LocalDate, preview: Boolean = false): Boolean = snow && themeActive(auto, d, preview)

    /**
     * Приложение открыли заново: первый показ после запуска ([stoppedAt] = 0) или возврат после
     * [NEW_ENTRY_AFTER_MS] в фоне. Переходы между разделами и поворот экрана входом не считаются.
     */
    fun isNewEntry(stoppedAt: Long, now: Long): Boolean = stoppedAt == 0L || now - stoppedAt >= NEW_ENTRY_AFTER_MS

    /** Ближайший день начала сезона — для подписи в настройках. */
    fun nextStart(d: LocalDate): LocalDate {
        val thisYear = LocalDate.of(d.year, 12, 15)
        return if (d.isAfter(thisYear)) thisYear.plusYears(1) else thisYear
    }

    /** Последний день текущего (или ближайшего) сезона. */
    fun seasonEnd(d: LocalDate): LocalDate = if (d.monthValue == 1) LocalDate.of(d.year, 1, 15) else LocalDate.of(d.year + 1, 1, 15)
}
