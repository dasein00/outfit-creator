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
    private const val KEY_BG_STYLE = "holiday_bg_style"
    private const val KEY_CARD_STYLE = "holiday_card_style"
    private const val KEY_SANTA = "holiday_santa"

    /** Фон новогоднего режима: бархатный градиент — центр, середина, края (тёмная тема и светлая). */
    class BgStyle(val name: String, val dark: List<androidx.compose.ui.graphics.Color>, val light: List<androidx.compose.ui.graphics.Color>)

    /** Цвет плашек новогоднего режима — полупрозрачный, сквозь него видно фон (тёмная тема и светлая). */
    class CardStyle(val name: String, val dark: androidx.compose.ui.graphics.Color, val light: androidx.compose.ui.graphics.Color)

    private fun c(v: Long) = androidx.compose.ui.graphics.Color(v)

    val BG_STYLES = listOf(
        BgStyle("Зелёный бархат", listOf(c(0xFF235A41), c(0xFF15352A), c(0xFF0A1E16)), listOf(c(0xFFEFF7F2), c(0xFFDCEDE3), c(0xFFC6E0D0))),
        BgStyle("Красный бархат", listOf(c(0xFF8C1A2B), c(0xFF5E0F1C), c(0xFF330710)), listOf(c(0xFFFBE9E9), c(0xFFF3D3D5), c(0xFFE6B9BD))),
        BgStyle("Синяя ночь", listOf(c(0xFF27447A), c(0xFF162850), c(0xFF0A142B)), listOf(c(0xFFEDF2FB), c(0xFFD9E3F4), c(0xFFC3D2EC))),
        BgStyle("Шоколад", listOf(c(0xFF5E3D29), c(0xFF3B2519), c(0xFF1F130C)), listOf(c(0xFFF8EFE6), c(0xFFEEDFCF), c(0xFFE2CDB7))),
    )

    val CARD_STYLES = listOf(
        CardStyle("Матовое стекло", c(0x8C2E323C), c(0xA6FFFFFF)),
        CardStyle("Бордовый", c(0xA6611626), c(0xB3F7DDE0)),
        CardStyle("Хвойный", c(0xA61B4A33), c(0xB3DDEFE4)),
        CardStyle("Тёмный", c(0xB3121212), c(0xCCFFFFFF)),
        CardStyle("Сливочный", c(0x40FADFAF), c(0xB3FFF6E6)),
    )

    /** Выбранный фон — номер в [BG_STYLES] (по умолчанию зелёный бархат). */
    val bgStyle = androidx.compose.runtime.mutableIntStateOf(0)

    /** Выбранные плашки — номер в [CARD_STYLES]. */
    val cardStyle = androidx.compose.runtime.mutableIntStateOf(0)

    fun bg(): BgStyle = BG_STYLES.getOrElse(bgStyle.intValue) { BG_STYLES[0] }
    fun card(): CardStyle = CARD_STYLES.getOrElse(cardStyle.intValue) { CARD_STYLES[0] }
    fun setBgStyle(ctx: Context, i: Int) { bgStyle.intValue = i; sp(ctx).edit().putInt(KEY_BG_STYLE, i).apply() }
    fun setCardStyle(ctx: Context, i: Int) { cardStyle.intValue = i; sp(ctx).edit().putInt(KEY_CARD_STYLE, i).apply() }

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

    /** Сани Санты пролетают по главной между карточками (по умолчанию включены). */
    val santa = mutableStateOf(true)

    /** Запустить сани сейчас (кнопка в настройках): растёт — сани вылетают без ожидания. */
    val santaNow = mutableIntStateOf(0)

    fun setSanta(ctx: Context, v: Boolean) { santa.value = v; sp(ctx).edit().putBoolean(KEY_SANTA, v).apply() }

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
        santa.value = p.getBoolean(KEY_SANTA, true)
        bgStyle.intValue = p.getInt(KEY_BG_STYLE, 0).coerceIn(0, BG_STYLES.lastIndex)
        cardStyle.intValue = p.getInt(KEY_CARD_STYLE, 0).coerceIn(0, CARD_STYLES.lastIndex)
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
