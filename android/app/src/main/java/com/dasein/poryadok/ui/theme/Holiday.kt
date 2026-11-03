package com.dasein.poryadok.ui.theme

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.dasein.poryadok.logic.HolidaySeason
import java.time.LocalDate

/**
 * Новогодний режим оформления (с 15 декабря по 15 января): праздничные цвета иконок и снегопад при входе.
 * Настройки — в ui_state, как остальное оформление: сохраняются между запусками и попадают в файл оформления.
 * Всё — состояние Compose, поэтому тема включается и выключается сама, без перезапуска.
 */
object Holiday {
    private const val KEY_AUTO = "holiday_auto"
    private const val KEY_SNOW = "holiday_snow"

    /** Автоматический новогодний режим (по умолчанию включён). */
    val auto = mutableStateOf(true)

    /** Падающие снежинки при входе (по умолчанию включены). Не влияет на цвета. */
    val snow = mutableStateOf(true)

    /** Сегодняшняя дата телефона — обновляется при входе и раз в минуту, пока приложение открыто. */
    val today = mutableStateOf(LocalDate.now())

    /** Номер входа в приложение: растёт при запуске и возвращении после долгого перерыва. */
    val entry = mutableIntStateOf(0)

    /** До какого входа снег уже показан — чтобы перерисовки и пересоздание экрана не запускали его снова. */
    var snowShownFor = 0

    private var stoppedAt = 0L
    private var loaded = false

    private fun sp(ctx: Context) = ctx.getSharedPreferences("ui_state", Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        if (loaded) return
        val p = sp(ctx)
        auto.value = p.getBoolean(KEY_AUTO, true)
        snow.value = p.getBoolean(KEY_SNOW, true)
        loaded = true
    }

    /** Перечитать после загрузки файла оформления или резервной копии. */
    fun reload(ctx: Context) { loaded = false; load(ctx) }

    fun setAuto(ctx: Context, v: Boolean) { auto.value = v; sp(ctx).edit().putBoolean(KEY_AUTO, v).apply() }
    fun setSnow(ctx: Context, v: Boolean) { snow.value = v; sp(ctx).edit().putBoolean(KEY_SNOW, v).apply() }

    /** Новогодние цвета сейчас включены. */
    val active: Boolean get() = HolidaySeason.themeActive(auto.value, today.value)

    /** Снегопад при входе сейчас разрешён. */
    val snowNow: Boolean get() = HolidaySeason.snowAllowed(auto.value, snow.value, today.value)

    fun refreshDate() {
        val d = LocalDate.now()
        if (d != today.value) today.value = d
    }

    /** Приложение на экране (весь процесс, а не отдельный экран): новый вход — снова снег. */
    fun onStart() {
        refreshDate()
        if (HolidaySeason.isNewEntry(stoppedAt, System.currentTimeMillis())) entry.intValue++
    }

    fun onStop() { stoppedAt = System.currentTimeMillis() }
}
