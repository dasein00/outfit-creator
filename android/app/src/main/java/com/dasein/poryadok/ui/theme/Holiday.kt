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
    private const val KEY_PREVIEW = "holiday_preview"
    private const val KEY_LIGHTS = "holiday_lights"
    private const val KEY_LIGHTS_MODE = "holiday_lights_mode"
    private const val KEY_BACKDROP = "holiday_backdrop"

    /** Режимы гирлянды: переливы цветами, бегущие огни, мерцание. */
    val LIGHT_MODES = listOf("Переливы", "Бегущие огни", "Мерцание")

    /** Автоматический новогодний режим (по умолчанию включён). */
    val auto = mutableStateOf(true)

    /** Падающие снежинки при входе (по умолчанию включены). Не влияет на цвета. */
    val snow = mutableStateOf(true)

    /** Тестовый режим: новогодняя тема в любой день, чтобы посмотреть её заранее. */
    val preview = mutableStateOf(false)

    /** Гирлянда горит (нажатие на неё включает и выключает). */
    val lights = mutableStateOf(true)

    /** Режим огоньков — номер в [LIGHT_MODES] (удержание переключает). */
    val lightsMode = androidx.compose.runtime.mutableIntStateOf(0)

    /** Полупрозрачный снег на фоне под карточками (по умолчанию включён). */
    val backdrop = mutableStateOf(true)

    /** Общие часы украшений, секунды: по ним мигает гирлянда и идёт снег на фоне (один таймер на всё приложение). */
    val clock = androidx.compose.runtime.mutableFloatStateOf(0f)

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
        preview.value = p.getBoolean(KEY_PREVIEW, false)
        lights.value = p.getBoolean(KEY_LIGHTS, true)
        backdrop.value = p.getBoolean(KEY_BACKDROP, true)
        lightsMode.intValue = p.getInt(KEY_LIGHTS_MODE, 0).coerceIn(0, LIGHT_MODES.lastIndex)
        loaded = true
    }

    /** Перечитать после загрузки файла оформления или резервной копии. */
    fun reload(ctx: Context) { loaded = false; load(ctx) }

    fun setAuto(ctx: Context, v: Boolean) { auto.value = v; sp(ctx).edit().putBoolean(KEY_AUTO, v).apply() }
    fun setSnow(ctx: Context, v: Boolean) { snow.value = v; sp(ctx).edit().putBoolean(KEY_SNOW, v).apply() }

    /** Включили тестовый режим — сразу и снег, чтобы было видно, как выглядит вход. */
    fun setPreview(ctx: Context, v: Boolean) {
        preview.value = v; sp(ctx).edit().putBoolean(KEY_PREVIEW, v).apply()
        if (v) letItSnow()
    }

    fun setBackdrop(ctx: Context, v: Boolean) { backdrop.value = v; sp(ctx).edit().putBoolean(KEY_BACKDROP, v).apply() }

    fun setLights(ctx: Context, v: Boolean) { lights.value = v; sp(ctx).edit().putBoolean(KEY_LIGHTS, v).apply() }

    /** Следующий режим огоньков; выключенная гирлянда при этом загорается. */
    fun nextLightsMode(ctx: Context) {
        lightsMode.intValue = (lightsMode.intValue + 1) % LIGHT_MODES.size
        sp(ctx).edit().putInt(KEY_LIGHTS_MODE, lightsMode.intValue).apply()
        if (!lights.value) setLights(ctx, true)
    }

    /** Показать снегопад сейчас (кнопка в настройках) — как при новом входе. */
    fun letItSnow() { entry.intValue++ }

    /** Новогодние цвета сейчас включены. */
    val active: Boolean get() = HolidaySeason.themeActive(auto.value, today.value, preview.value)

    /** Снегопад при входе сейчас разрешён. */
    val snowNow: Boolean get() = HolidaySeason.snowAllowed(auto.value, snow.value, today.value, preview.value)

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
