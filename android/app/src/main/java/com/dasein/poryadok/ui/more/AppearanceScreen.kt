package com.dasein.poryadok.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import com.dasein.poryadok.ui.common.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.Settings
import com.dasein.poryadok.data.UiTexts
import kotlinx.coroutines.launch
import com.dasein.poryadok.ui.common.Button
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.OutlinedButton
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.snowCap
import com.dasein.poryadok.ui.common.fancyBackground
import com.dasein.poryadok.ui.common.cardFill
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloat
import com.dasein.poryadok.ui.common.Segments
import com.dasein.poryadok.ui.common.TextButton
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.btnLabel
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.theme.LocalExtra
import com.dasein.poryadok.ui.theme.Look
import com.dasein.poryadok.ui.theme.LookSettings
import com.dasein.poryadok.ui.theme.Palette
import kotlin.math.roundToInt

const val APPEARANCE_ROUTE = "appearance"

/** Что выбираем в палитре: акцент, фон, текст или цвет иконок. */
private enum class Pick { ACCENT, BG, TEXT, ICON, CARD }

/**
 * «Оформление»: тема, акцент (16 цветов и свой), фон, цвет текста, контрастный режим, иконки, шрифт, размер текста,
 * скругление карточек. Всё меняется сразу, сверху — живой пример.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceScreen(nav: NavHostController, settings: Settings) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val look = Look.state.value
    fun set(f: (LookSettings) -> LookSettings) = Look.save(ctx, f(Look.state.value))
    var pick by remember { mutableStateOf<Pick?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var profileMsg by remember { mutableStateOf<String?>(null) }
    var needRestart by remember { mutableStateOf(false) }
    val profileExport = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            profileMsg = "Сохраняю оформление…"
            profileMsg = com.dasein.poryadok.system.UiProfile.export(ctx, uri).fold(
                { "Файл оформления сохранён (иконок и служебных файлов: $it). Пришлите его в чат с Claude, чтобы сделать это оформление стандартным." },
                { "Не удалось сохранить: ${it.message}" },
            )
        }
    }
    val profileImport = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            profileMsg = "Применяю оформление…"
            profileMsg = com.dasein.poryadok.system.UiProfile.import(ctx, uri).fold(
                { needRestart = true; "Оформление загружено (файлов: $it). Перезапустите приложение, чтобы всё применилось." },
                { "Не удалось загрузить: ${it.message}" },
            )
        }
    }

    Screen("Оформление", onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Preview()

            LookPresetsSection(settings)

            SectionTitle("Тема", action = if (settings.theme != "system") "Как в теме" else null, onAction = { io { Graph.prefs.update { it.copy(theme = "system") } } })
            Segments(listOf("system" to "Как в системе", "light" to "Светлая", "dark" to "Тёмная"), settings.theme, { v -> io { Graph.prefs.update { it.copy(theme = v) } } })
            if (look.bgRgb != 0) Text("Сейчас выбран свой фон — светлая или тёмная тема определяется по нему.", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp))
            MiniScreen(Focus.NONE)

            // Праздничные темы — отдельными подразделами.
            SectionTitle("Праздничные темы")
            SeasonRow("Новогодний режим", "15 декабря — 15 января: гирлянда, снег, сосульки, сани Санты", com.dasein.poryadok.ui.theme.Holiday.active) { nav.navigate(HOLIDAY_ROUTE) }
            SeasonRow("Хэллоуин", "24 октября — 1 ноября: кладбище в тумане, глаза, летучие мыши, пауки, привидение", com.dasein.poryadok.ui.theme.Halloween.active) { nav.navigate(HALLOWEEN_ROUTE) }

            SectionTitle("Цвет акцента", action = if (look.accentRgb != 0 || settings.accent != 0) "Как в теме" else null, onAction = {
                set { it.copy(accentRgb = 0) }; io { Graph.prefs.update { s -> s.copy(accent = 0) } }
            })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Palette.accents.forEachIndexed { i, (label, c) ->
                    Swatch(c, label, look.accentRgb == 0 && settings.accent == i) {
                        set { it.copy(accentRgb = 0) }
                        io { Graph.prefs.update { s -> s.copy(accent = i) } }
                    }
                }
                Swatch(if (look.accentRgb != 0) Look.color(look.accentRgb) else null, "Свой", look.accentRgb != 0) { pick = Pick.ACCENT }
            }
            MiniScreen(Focus.ACCENT)

            val holidayNow = com.dasein.poryadok.ui.theme.Holiday.active && !look.contrast
            SectionTitle("Фон", action = if (look.bgRgb != 0) "Как в теме" else null, onAction = { set { it.copy(bgRgb = 0, bgStyle = -1) } })
            if (holidayNow) HolidayNote()
            Text(btnLabel("Глубокие цвета"), fontSize = 13.sp, color = extra.dim, modifier = Modifier.padding(bottom = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Look.DEEP_BACKGROUNDS.forEachIndexed { i, d ->
                    StyleSwatch(d.name, look.bgStyle == i, androidx.compose.ui.graphics.Brush.radialGradient(d.colors)) {
                        set { it.copy(bgStyle = i, bgRgb = d.colors[1].toArgb() and 0xFFFFFF) }
                    }
                }
            }
            Text(btnLabel("Однотонные"), fontSize = 13.sp, color = extra.dim, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Swatch(null, "По теме", look.bgRgb == 0, auto = true) { set { it.copy(bgRgb = 0, bgStyle = -1) } }
                Look.BACKGROUNDS.forEach { (label, rgb) -> Swatch(Look.color(rgb), label, look.bgStyle < 0 && look.bgRgb == rgb) { set { it.copy(bgRgb = rgb, bgStyle = -1) } } }
                Swatch(null, "Свой", look.bgStyle < 0 && look.bgRgb != 0 && Look.BACKGROUNDS.none { it.second == look.bgRgb }) { pick = Pick.BG }
            }
            MiniScreen(Focus.BG)

            SectionTitle("Стиль фона", action = if (look.bgFx >= 0) "Как в теме" else null, onAction = { set { it.copy(bgFx = -1) } })
            Text(
                "Популярные приёмы современных интерфейсов: бархатный и диагональный градиент, «аврора» и меш из мягких цветных пятен, прожектор, зерно, боке. " +
                    "Пятна берут цвета из сочетания к фону. Работает и с однотонным фоном.",
                fontSize = 12.sp, color = extra.dim, lineHeight = 16.sp, modifier = Modifier.padding(bottom = 6.dp),
            )
            BgFxPicker(look) { fx -> set { it.copy(bgFx = fx) } }
            MiniScreen(Focus.BG)

            SectionTitle("Плашки", action = if (look.cardStyle != -1) "Как в теме" else null, onAction = { set { it.copy(cardStyle = -1, cardRgb = 0) } })
            if (holidayNow) HolidayNote()
            Text(
                "Цвет карточек на всех экранах. Полупрозрачные пропускают фон — особенно красиво с глубокими цветами. Текст на них сам остаётся различимым.",
                fontSize = 12.sp, color = extra.dim, lineHeight = 16.sp, modifier = Modifier.padding(bottom = 6.dp),
            )
            val themeDark = extra.dark
            val under = Look.deep(look)?.get(1) ?: MaterialTheme.colorScheme.background
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StyleSwatch("По теме", look.cardStyle == -1, androidx.compose.ui.graphics.SolidColor(Look.mix(under, MaterialTheme.colorScheme.onBackground, if (themeDark) .07f else .03f))) {
                    set { it.copy(cardStyle = -1) }
                }
                Look.CARDS.forEachIndexed { i, cc ->
                    val c = if (themeDark) cc.dark else cc.light
                    // Плашка показана поверх текущего фона — так видна её полупрозрачность.
                    StyleSwatch(cc.name, look.cardStyle == i, androidx.compose.ui.graphics.SolidColor(Look.mix(under, c.copy(alpha = 1f), c.alpha))) { set { it.copy(cardStyle = i) } }
                }
                StyleSwatch("Свой", look.cardStyle == -2, androidx.compose.ui.graphics.SolidColor(if (look.cardStyle == -2 && look.cardRgb != 0) Look.mix(under, Color(look.cardRgb).copy(alpha = 1f), Color(look.cardRgb).alpha) else extra.line)) { pick = Pick.CARD }
            }
            MiniScreen(Focus.CARD)

            SectionTitle("Стиль плашек", action = if (look.cardFx >= 0) "Как в теме" else null, onAction = { set { it.copy(cardFx = -1) } })
            Text(
                "Стекло — светлый блик и тонкая светящаяся рамка; градиент — перелив в цвет сочетания; контур — прозрачная плашка с цветной рамкой; " +
                    "неон — рамка-градиент; объём — мягкий свет сверху и тень снизу.",
                fontSize = 12.sp, color = extra.dim, lineHeight = 16.sp, modifier = Modifier.padding(bottom = 6.dp),
            )
            CardFxPicker(look) { fx -> set { it.copy(cardFx = fx) } }
            MiniScreen(Focus.CARD)

            SectionTitle("Цвет текста", action = if (look.textRgb != 0) "Как в теме" else null, onAction = { set { it.copy(textRgb = 0) } })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Swatch(null, "Авто", look.textRgb == 0, auto = true) { set { it.copy(textRgb = 0) } }
                Look.TEXTS.forEach { (label, rgb) -> Swatch(Look.color(rgb), label, look.textRgb == rgb) { set { it.copy(textRgb = rgb) } } }
                Swatch(null, "Свой", look.textRgb != 0 && Look.TEXTS.none { it.second == look.textRgb }) { pick = Pick.TEXT }
            }
            MiniScreen(Focus.TEXT)
            val bg = MaterialTheme.colorScheme.background
            val fg = MaterialTheme.colorScheme.onBackground
            val ratio = Look.contrast(bg, fg)
            Text(
                "Контраст текста и фона: ${"%.1f".format(ratio)} : 1 — " + when {
                    ratio >= 7 -> "отлично"
                    ratio >= 4.5 -> "хорошо читается"
                    else -> "плохо читается, выберите цвет темнее или светлее"
                },
                fontSize = 12.sp, color = if (ratio >= 4.5) extra.ok else extra.danger, modifier = Modifier.padding(top = 6.dp),
            )

            SectionTitle("Контрастный режим", action = if (look.contrast) "Как в теме" else null, onAction = { set { it.copy(contrast = false) } })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(btnLabel("Максимальный контраст"), fontWeight = FontWeight.SemiBold)
                    Text(
                        "Чисто белый или чёрный текст, чёткие рамки, яркие подписи. Вместе с «контрастными» иконками — всё хорошо видно на любом фоне.",
                        fontSize = 12.sp, color = extra.dim, lineHeight = 16.sp,
                    )
                }
                Switch(look.contrast, { on -> set { it.copy(contrast = on, icons = if (on && it.icons == 0) 1 else it.icons) } })
            }
            MiniScreen(Focus.NONE)

            SectionTitle("Анимация", action = if (!com.dasein.poryadok.ui.common.TabAnim.enabled.value) "Как в теме" else null, onAction = { com.dasein.poryadok.ui.common.TabAnim.setEnabled(true) })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(btnLabel("Анимация переходов"), fontWeight = FontWeight.SemiBold)
                    Text(
                        "Подразделы плавно сменяют друг друга при свайпе влево-вправо и нажатии на вкладку. Сама выключается в режиме энергосбережения и при заряде ${com.dasein.poryadok.ui.common.TabAnim.LOW_BATTERY} % и меньше.",
                        fontSize = 12.sp, color = extra.dim, lineHeight = 16.sp,
                    )
                }
                Switch(com.dasein.poryadok.ui.common.TabAnim.enabled.value, { com.dasein.poryadok.ui.common.TabAnim.setEnabled(it) })
            }
            AnimPreview(com.dasein.poryadok.ui.common.TabAnim.enabled.value)

            SectionTitle("Иконки", action = if (look.icons != 0 || look.iconPalette != -1) "Как в теме" else null, onAction = { set { it.copy(icons = 0, iconRgb = 0, iconPalette = -1) } })
            Look.ICON_MODES.forEachIndexed { i, label ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { if (i == 3 && look.iconRgb == 0) pick = Pick.ICON; set { it.copy(icons = i) } }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(look.icons == i, { if (i == 3 && look.iconRgb == 0) pick = Pick.ICON; set { it.copy(icons = i) } })
                    Text(label, Modifier.weight(1f))
                    if (i == 3) Box(Modifier.size(22.dp).clip(CircleShape).background(if (look.iconRgb != 0) Look.color(look.iconRgb) else extra.line).clickable { pick = Pick.ICON })
                }
            }
            if (look.icons == 4) IconPalettePicker(look) { i -> set { it.copy(iconPalette = i, icons = 4) } }
            // Иконки на фоне и на плашке — как они будут выглядеть в приложении.
            Column(Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, extra.line, RoundedCornerShape(16.dp)).padding(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    listOf("d01/00", "d12/00", "d01/02", "d06/00", "tutor/18", "ui:home").forEach { Glyph(it, 30.dp, badge = false) }
                }
                Box(Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(look.cardRound.dp)).cardFill(RoundedCornerShape(look.cardRound.dp)).padding(10.dp)) {
                    com.dasein.poryadok.ui.theme.OnBackground(extra.card) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            listOf("d17/03", "d06/00", "d12/00", "d01/02", "ui:home").forEach { Glyph(it, 26.dp, badge = false) }
                        }
                    }
                }
            }

            SectionTitle("Шрифт", action = if (look.font != 5) "Как в теме" else null, onAction = { set { it.copy(font = 5) } })
            Look.FONTS.forEach { (label, id) ->
                val family = when (id) { 5 -> com.dasein.poryadok.ui.theme.Medeo; 1 -> FontFamily.SansSerif; 2 -> FontFamily.Serif; 3 -> FontFamily.Monospace; 4 -> FontFamily.Cursive; else -> FontFamily.Serif }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { set { it.copy(font = id) } }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(look.font == id, { set { it.copy(font = id) } })
                    Column {
                        Text("Доброе утро", fontFamily = family, fontSize = 20.sp)
                        Text(label, fontSize = 12.sp, color = extra.dim)
                    }
                }
            }

            SectionTitle("Размер текста", action = if (look.scale != 1f) "Как в теме" else null, onAction = { set { it.copy(scale = 1f) } })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("A", fontSize = 13.sp)
                Slider(look.scale, { v -> set { it.copy(scale = (v * 20).roundToInt() / 20f) } }, valueRange = .8f..1.5f, steps = 13, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                Text("A", fontSize = 22.sp)
            }
            Text("${(look.scale * 100).roundToInt()}% от системного размера", fontSize = 12.sp, color = extra.dim)
            Tile(Modifier.padding(top = 8.dp)) {
                Text("Заголовок карточки", style = MaterialTheme.typography.titleMedium)
                Text("Обычный текст: так читаются задачи, рецепты и заметки.", fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                Text("Мелкая подпись · 12:30", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp))
            }

            SectionTitle("Скругление карточек", action = if (look.cardRound != 18) "Как в теме" else null, onAction = { set { it.copy(cardRound = 18) } })
            Slider(look.cardRound.toFloat(), { v -> set { it.copy(cardRound = v.roundToInt()) } }, valueRange = 4f..32f)
            Text("${look.cardRound} dp", fontSize = 12.sp, color = extra.dim)
            RoundPreview(look.cardRound)

            SectionTitle("Тексты интерфейса")
            Text(
                "Названия кнопок, плашек, заголовков и подсказок во всех разделах: поиск по всему приложению, разделы и свой вариант для любого текста.",
                fontSize = 12.sp, color = extra.dim, lineHeight = 16.sp,
            )
            Button(onClick = { nav.navigate(UI_TEXTS_ROUTE) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp)) { Text(btnLabel("Редактировать тексты")) }
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(btnLabel("Режим редактора"), fontWeight = FontWeight.SemiBold)
                    Text(
                        "Включается и так: удерживайте палец на экране 3 секунды. В режиме удерживайте любой текст 1 секунду — откроется окно, где можно изменить формулировку. Правки видны в «Редактировать тексты».",
                        fontSize = 12.sp, color = extra.dim, lineHeight = 16.sp,
                    )
                }
                Switch(UiTexts.editorMode.value, { UiTexts.setEditorMode(it) })
            }
            // Плашки, скрытые в режиме редактора кнопкой «✕ Скрыть плашку».
            val hiddenPlates = com.dasein.poryadok.ui.common.UiState.hiddenPlates(ctx)
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(btnLabel("Скрытые плашки") + if (hiddenPlates > 0) ": $hiddenPlates" else "", fontWeight = FontWeight.SemiBold)
                    Text(
                        "В режиме редактора над каждой подсказкой, инструкцией «Как пользоваться» и сворачиваемой карточкой есть «✕ Скрыть плашку», " +
                            "а отдельный текст скрывается удержанием → «Скрыть с экрана». Скрытое сохраняется в «Файл оформления».",
                        fontSize = 12.sp, color = extra.dim, lineHeight = 16.sp,
                    )
                }
                if (hiddenPlates > 0) TextButton(onClick = { com.dasein.poryadok.ui.common.UiState.showAllPlates(ctx) }) { Text(btnLabel("Показать все")) }
            }

            SectionTitle("Файл оформления")
            Text(
                "Всё оформление одним файлом: тема, цвета, шрифт, тексты и названия кнопок, свои иконки, главная и виджет. Записей, PIN и ключей в нём нет. " +
                    "Загрузите файл на другом телефоне или пришлите его в чат с Claude — тогда ваше оформление станет стандартным и появится сразу после установки.",
                fontSize = 12.sp, color = extra.dim, lineHeight = 16.sp,
            )
            Button(
                onClick = {
                    scope.launch {
                        profileMsg = "Готовлю оформление…"
                        profileMsg = shareFile(ctx, "DASEIN-оформление.zip", "Оформление DASEIN") { com.dasein.poryadok.system.UiProfile.export(ctx, it) }
                            .fold({ null }, { "Не удалось: ${it.message}" })
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp),
            ) { Text(btnLabel("Отправить в чат")) }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { profileExport.launch("DASEIN-оформление.zip") }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(btnLabel("Экспорт оформления")) }
                OutlinedButton(onClick = { profileImport.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(btnLabel("Импорт оформления")) }
            }
            profileMsg?.let { Text(it, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
            if (needRestart) OutlinedButton(onClick = { restartApp(ctx) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 48.dp)) { Text(btnLabel("Перезапустить приложение")) }

            Gap(16.dp)
            OutlinedButton(onClick = { set { LookSettings() } }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(btnLabel("Вернуть оформление по умолчанию")) }
            Text(
                "Все эти настройки сохраняются в «Файл оформления» и в резервную копию — на новом телефоне всё будет так же.",
                fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 8.dp),
            )
            Gap(40.dp)
        }
    }

    pick?.let { p ->
        val start = when (p) {
            Pick.ACCENT -> look.accentRgb.takeIf { it != 0 } ?: (MaterialTheme.colorScheme.primary.toArgb() and 0xFFFFFF)
            Pick.BG -> look.bgRgb.takeIf { it != 0 } ?: (MaterialTheme.colorScheme.background.toArgb() and 0xFFFFFF)
            Pick.TEXT -> look.textRgb.takeIf { it != 0 } ?: (MaterialTheme.colorScheme.onBackground.toArgb() and 0xFFFFFF)
            Pick.ICON -> look.iconRgb.takeIf { it != 0 } ?: (MaterialTheme.colorScheme.primary.toArgb() and 0xFFFFFF)
            Pick.CARD -> (look.cardRgb.takeIf { it != 0 } ?: extra.card.toArgb()) and 0xFFFFFF
        }
        ColorPickerDialog(start, onDismiss = { pick = null }) { rgb ->
            // 0 значит «по умолчанию», поэтому чистый чёрный храним как почти чёрный.
            val v = (rgb and 0xFFFFFF).let { if (it == 0) 0x010101 else it }
            set {
                when (p) {
                    Pick.ACCENT -> it.copy(accentRgb = v)
                    Pick.BG -> it.copy(bgRgb = v, bgStyle = -1)
                    Pick.TEXT -> it.copy(textRgb = v)
                    Pick.ICON -> it.copy(iconRgb = v, icons = 3)
                    // Свои плашки — слегка прозрачные, чтобы фон просвечивал.
                    Pick.CARD -> it.copy(cardRgb = v or (0xE0 shl 24), cardStyle = -2)
                }
            }
            pick = null
        }
    }
}

/** Перезапуск после загрузки оформления — иконки, тексты и главная перечитываются с нуля. */
private fun restartApp(ctx: android.content.Context) {
    val i = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
    if (i != null) {
        i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ctx.startActivity(i)
    }
    (ctx as? android.app.Activity)?.finishAffinity()
    Runtime.getRuntime().exit(0)
}

/** Живой пример: заголовок, текст, кнопки и иконки в текущем оформлении. */
@Composable
private fun Preview() {
    val extra = LocalExtra.current
    Tile {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph("d01/00", 30.dp, badge = false)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text("Доброе утро", style = MaterialTheme.typography.titleLarge)
                Text("Так будет выглядеть приложение", fontSize = 13.sp, color = extra.dim)
            }
        }
        Text(
            "Обычный текст карточки: задачи, рецепты, заметки. Подписи и пояснения — приглушённым цветом.",
            fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp),
        )
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {}) { Text("Кнопка") }
            OutlinedButton(onClick = {}) { Text("Вторая") }
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("d12/00", "d01/02", "d06/00", "d17/03").forEach { Glyph(it, 26.dp, badge = false) }
        }
    }
}

/** Кружок цвета с подписью. [color] null — «авто/свой» (рисуется рамкой). */
@Composable
private fun Swatch(color: Color?, label: String, selected: Boolean, auto: Boolean = false, onClick: () -> Unit) {
    val extra = LocalExtra.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(58.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)) {
        Box(
            Modifier.size(38.dp).clip(CircleShape)
                .background(color ?: Color.Transparent)
                .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else extra.line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (color == null) Text(if (auto) "A" else "+", fontWeight = FontWeight.Bold, color = extra.dim)
        }
        Text(label, fontSize = 10.sp, color = extra.dim, maxLines = 2, lineHeight = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** Свой цвет: тон, насыщенность, яркость и код #RRGGBB. */
@Composable
fun ColorPickerDialog(start: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(start or (0xFF shl 24), it) } }
    var h by remember { mutableStateOf(hsv[0]) }
    var sat by remember { mutableStateOf(hsv[1]) }
    var v by remember { mutableStateOf(hsv[2]) }
    val rgb = android.graphics.Color.HSVToColor(floatArrayOf(h, sat, v)) and 0xFFFFFF
    var hex by remember { mutableStateOf("%06X".format(start and 0xFFFFFF)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(btnLabel("Свой цвет")) },
        text = {
            Column {
                Box(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp)).background(Look.color(rgb)))
                Text(btnLabel("Тон"), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                Box(Modifier.fillMaxWidth().heightIn(min = 10.dp).clip(RoundedCornerShape(5.dp)).background(androidx.compose.ui.graphics.Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) })))
                Slider(h, { h = it; hex = "%06X".format(android.graphics.Color.HSVToColor(floatArrayOf(h, sat, v)) and 0xFFFFFF) }, valueRange = 0f..359f)
                Text(btnLabel("Насыщенность"), fontSize = 12.sp)
                Slider(sat, { sat = it; hex = "%06X".format(android.graphics.Color.HSVToColor(floatArrayOf(h, sat, v)) and 0xFFFFFF) })
                Text(btnLabel("Яркость"), fontSize = 12.sp)
                Slider(v, { v = it; hex = "%06X".format(android.graphics.Color.HSVToColor(floatArrayOf(h, sat, v)) and 0xFFFFFF) })
                TextInput(hex, { t ->
                    hex = t.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                    if (hex.length == 6) hex.toIntOrNull(16)?.let { c ->
                        val f = FloatArray(3); android.graphics.Color.colorToHSV(c or (0xFF shl 24), f); h = f[0]; sat = f[1]; v = f[2]
                    }
                }, "Код цвета #RRGGBB")
            }
        },
        confirmButton = { TextButton(onClick = { onPick(rgb) }) { Text(btnLabel("Выбрать")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) } },
    )
}

/**
 * «Новогодний режим» — часть выбора темы: автоматическая праздничная тема с 15 декабря по 15 января
 * и снежинки при входе. Снежинки — отдельный выключатель: без них тема остаётся, без темы их не бывает.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HolidaySettings() {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val h = com.dasein.poryadok.ui.theme.Holiday
    val season = com.dasein.poryadok.logic.HolidaySeason
    val auto = h.auto.value
    val today = h.today.value
    val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMMM", java.util.Locale("ru"))
    SectionTitle("Новогодний режим")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(btnLabel("Автоматический новогодний режим"), fontWeight = FontWeight.SemiBold)
            Text(
                "Каждый год с 15 декабря по 15 января иконки в праздничных цветах: красный, белый, зелёный и немного золота. " +
                    "Потом всё само возвращается к обычным цветам. " + when {
                    !auto -> "Сейчас выключен — праздничная тема не включится."
                    season.inSeason(today) -> "Сейчас действует — до ${season.seasonEnd(today).format(fmt)}."
                    else -> "Включится ${season.nextStart(today).format(fmt)}."
                },
                fontSize = 12.sp, color = extra.dim, lineHeight = 16.sp,
            )
        }
        Switch(auto, { h.setAuto(ctx, it) })
    }
    val preview = h.preview.value
    val canSnow = auto || preview
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(btnLabel("Падающие снежинки"), fontWeight = FontWeight.SemiBold, color = if (canSnow) Color.Unspecified else extra.dim)
            Text(
                if (canSnow) "При входе в приложение 5 секунд идёт снег, потом снежинки улетают за нижний край экрана. Не мешает нажимать и листать."
                else "Снежинки идут вместе с новогодним режимом — включите его, и выбор сохранится.",
                fontSize = 12.sp, color = extra.dim, lineHeight = 16.sp,
            )
        }
        Switch(h.snow.value, { h.setSnow(ctx, it) }, enabled = canSnow)
    }
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(btnLabel("Снег на фоне"), fontWeight = FontWeight.SemiBold, color = if (canSnow) Color.Unspecified else extra.dim)
            Text("Полупрозрачные снежинки медленно падают под карточками, пока открыто приложение.", fontSize = 12.sp, color = extra.dim, lineHeight = 16.sp)
        }
        Switch(h.backdrop.value, { h.setBackdrop(ctx, it) }, enabled = canSnow)
    }
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(btnLabel("Гирлянда"), fontWeight = FontWeight.SemiBold, color = if (canSnow) Color.Unspecified else extra.dim)
            Text(
                "Огоньки вдоль верха экрана и еловые ветки, на карточках — снежные шапки. Нажмите на любую лампочку — гирлянда погаснет или загорится, " +
                    "удержите лампочку — сменится режим.",
                fontSize = 12.sp, color = extra.dim, lineHeight = 16.sp,
            )
        }
        Switch(h.lights.value, { h.setLights(ctx, it) }, enabled = canSnow)
    }
    if (canSnow) androidx.compose.foundation.layout.FlowRow(
        Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        h.LIGHT_MODES.forEachIndexed { i, m ->
            com.dasein.poryadok.ui.common.Pill(m, h.lights.value && h.lightsMode.intValue == i) {
                while (h.lightsMode.intValue != i) h.nextLightsMode(ctx)
                if (!h.lights.value) h.setLights(ctx, true)
            }
        }
    }
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(btnLabel("Сани Санты"), fontWeight = FontWeight.SemiBold, color = if (canSnow) Color.Unspecified else extra.dim)
            Text(
                "Санта на санях с тремя оленями (впереди — красноносый) время от времени пролетает на главной по полосе кнопок — " +
                    "то слева направо, то обратно — и улетает за край экрана. Нажимать кнопки не мешает.",
                fontSize = 12.sp, color = extra.dim, lineHeight = 16.sp,
            )
        }
        Switch(h.santa.value, { h.setSanta(ctx, it) }, enabled = canSnow)
    }
    if (h.active && h.santa.value) TextButton(onClick = { h.santaNow.intValue++; android.widget.Toast.makeText(ctx, "Сани вылетели — откройте главную", android.widget.Toast.LENGTH_SHORT).show() }) {
        Text(btnLabel("Запустить сани сейчас"))
    }
    HolidayLookPicker()
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(btnLabel("Тестовый режим"), fontWeight = FontWeight.SemiBold)
            Text(
                "Включает новогоднее оформление прямо сейчас, в любой день, — чтобы посмотреть его заранее. " +
                    "Работает, пока не выключите, даже вне сезона и при выключенном автоматическом режиме.",
                fontSize = 12.sp, color = extra.dim, lineHeight = 16.sp,
            )
        }
        Switch(preview, { h.setPreview(ctx, it) })
    }
    if (h.active) OutlinedButton(
        onClick = { h.letItSnow() }, enabled = h.snow.value,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) { Text(btnLabel(if (h.snow.value) "Показать снегопад сейчас" else "Снегопад выключен")) }
}

/**
 * Выбор фона и цвета плашек для новогоднего режима — с живым превью: бархатный фон с хвойной каймой
 * и пример плашки со снежной шапкой и сосульками. Превью видно и вне сезона.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HolidayLookPicker() {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val h = com.dasein.poryadok.ui.theme.Holiday
    val dark = extra.dark
    val assets = com.dasein.poryadok.ui.common.HolidayAssets
    LaunchedEffect(Unit) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { assets.load(ctx.applicationContext) } }
    val bg = h.bg()
    val card = h.card().let { if (dark) it.dark else it.light }
    Text(btnLabel("Фон и плашки"), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
    // Превью.
    Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(170.dp).clip(RoundedCornerShape(18.dp))) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val v = if (dark) bg.dark else bg.light
            drawRect(androidx.compose.ui.graphics.Brush.radialGradient(v, androidx.compose.ui.geometry.Offset(size.width * .5f, size.height * .3f), maxOf(size.width, size.height) * .9f))
            if (assets.ready.value) assets.borderBottom?.let { img ->
                val hh = size.width * img.height / img.width
                drawImage(img, srcOffset = androidx.compose.ui.unit.IntOffset.Zero, srcSize = androidx.compose.ui.unit.IntSize(img.width, img.height),
                    dstOffset = androidx.compose.ui.unit.IntOffset(0, (size.height - hh).toInt()), dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), hh.toInt()), alpha = .9f)
            }
        }
        Box(
            Modifier.align(Alignment.TopCenter).padding(horizontal = 18.dp, vertical = 18.dp).fillMaxWidth().height(86.dp)
                .clip(RoundedCornerShape(com.dasein.poryadok.ui.theme.LocalLook.current.cardRound.dp)).background(card).snowCap(2026, force = true),
        ) {
            com.dasein.poryadok.ui.theme.OnBackground(card) {
                Column(Modifier.padding(start = 14.dp, top = 24.dp)) {
                    Text(btnLabel("Пример плашки"), fontWeight = FontWeight.SemiBold)
                    Text("Так будут выглядеть карточки", fontSize = 12.sp, color = extra.dim)
                }
            }
        }
    }
    Text(btnLabel("Фон"), fontSize = 13.sp, color = extra.dim, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        h.BG_STYLES.forEachIndexed { i, st ->
            val v = if (dark) st.dark else st.light
            StyleSwatch(st.name, h.bgStyle.intValue == i, androidx.compose.ui.graphics.Brush.radialGradient(v)) { h.setBgStyle(ctx, i) }
        }
    }
    Text(btnLabel("Плашки"), fontSize = 13.sp, color = extra.dim, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        h.CARD_STYLES.forEachIndexed { i, st ->
            val c = if (dark) st.dark else st.light
            // Плашка показана поверх выбранного фона — так видна её полупрозрачность.
            val base = (if (dark) bg.dark else bg.light)[1]
            StyleSwatch(st.name, h.cardStyle.intValue == i, androidx.compose.ui.graphics.SolidColor(com.dasein.poryadok.ui.theme.Look.mix(base, c.copy(alpha = 1f), c.alpha))) { h.setCardStyle(ctx, i) }
        }
    }
}

/** Кружок-образец с подписью; выбранный — с рамкой акцента. */
@Composable
internal fun StyleSwatch(name: String, selected: Boolean, brush: androidx.compose.ui.graphics.Brush, onClick: () -> Unit) {
    val extra = LocalExtra.current
    Column(Modifier.width(76.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(brush)
                .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else extra.line, CircleShape),
        )
        Text(name, fontSize = 11.sp, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 13.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Сейчас действует новогодний режим: его фон и плашки главнее, обычные вернутся после праздников. */
@Composable
private fun HolidayNote() {
    Text(
        "Сейчас действует новогодний режим — его фон и плашки настраиваются выше. Выбранное здесь сохранится и вернётся после праздников.",
        fontSize = 12.sp, color = LocalExtra.current.warn, lineHeight = 16.sp, modifier = Modifier.padding(bottom = 6.dp),
    )
}

/** Что подсвечено в мини-экране: редактируемый объект обведён бегущим пунктиром. */
private enum class Focus { NONE, BG, CARD, TEXT, ACCENT }

/** Бегущий пунктир вокруг редактируемого объекта. */
private fun Modifier.focusMark(on: Boolean, color: Color, radius: androidx.compose.ui.unit.Dp, phase: () -> Float): Modifier =
    if (!on) this else drawWithContent {
        drawContent()
        val w = 2.dp.toPx()
        drawRoundRect(
            color, topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
            size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(w, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 7f), phase())),
        )
    }

/**
 * Мини-экран в текущем оформлении: фон (однотонный, глубокий или новогодний), заголовок, плашка с текстом,
 * кнопкой и полосой прогресса, нижняя панель. Редактируемая часть ([focus]) обведена бегущим пунктиром.
 */
@Composable
private fun MiniScreen(focus: Focus) {
    val extra = LocalExtra.current
    val look = com.dasein.poryadok.ui.theme.LocalLook.current
    val cs = MaterialTheme.colorScheme
    val h = com.dasein.poryadok.ui.theme.Holiday
    val holiday = h.active && !look.contrast
    val grad = if (holiday) h.bg().let { if (extra.dark) it.dark else it.light } else Look.gradient(look, cs.background)
    val pal = com.dasein.poryadok.ui.theme.currentPalette()
    val mark = Look.readable(if (focus == Focus.ACCENT) cs.onBackground else cs.primary, cs.background, 3f)
    val phase = if (focus == Focus.NONE) null else androidx.compose.animation.core.rememberInfiniteTransition(label = "mark").animateFloat(
        0f, 34f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.LinearEasing)), label = "phase",
    )
    val ph = { -(phase?.value ?: 0f) }
    val outer = RoundedCornerShape(18.dp)
    val cardR = (look.cardRound * .8f).dp
    Box(
        Modifier.padding(top = 10.dp).fillMaxWidth().height(196.dp).clip(outer).border(1.dp, extra.line, outer)
            .focusMark(focus == Focus.BG, mark, 18.dp, ph),
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            if (grad != null) fancyBackground(grad, if (holiday) 0 else Look.fx(look), pal, extra.dark) else drawRect(cs.background)
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Сегодня", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = cs.onBackground, modifier = Modifier.weight(1f))
                Glyph("ui:home", 18.dp, badge = false)
            }
            Text("Четверг, 5 ноября", fontSize = 11.sp, color = extra.dim)
            Column(
                Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(cardR)).cardFill(RoundedCornerShape(cardR))
                    .focusMark(focus == Focus.CARD, mark, cardR, ph).padding(10.dp),
            ) {
                com.dasein.poryadok.ui.theme.OnBackground(extra.card) {
                    Column(Modifier.focusMark(focus == Focus.TEXT, mark, 6.dp, ph).padding(horizontal = 4.dp, vertical = 2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Glyph("d01/00", 18.dp, badge = false)
                            Text("Пример плашки", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp))
                        }
                        Text("Основной текст карточки", fontSize = 12.sp)
                        Text("Подпись приглушённым цветом", fontSize = 11.sp, color = extra.dim)
                    }
                    Row(
                        Modifier.padding(top = 6.dp).focusMark(focus == Focus.ACCENT, mark, 12.dp, ph).padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.clip(RoundedCornerShape(50)).background(cs.primary).padding(horizontal = 12.dp, vertical = 4.dp)) {
                            Text("Кнопка", fontSize = 12.sp, color = cs.onPrimary, fontWeight = FontWeight.SemiBold)
                        }
                        Box(Modifier.padding(start = 10.dp).weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(extra.line)) {
                            Box(Modifier.fillMaxWidth(.62f).fillMaxSize().background(cs.primary))
                        }
                        Text("62%", fontSize = 11.sp, color = cs.primary, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

/** Как сменяются подразделы: две мини-вкладки сами переключаются — плавно или сразу, как в приложении. */
@Composable
private fun AnimPreview(enabled: Boolean) {
    val extra = LocalExtra.current
    val cs = MaterialTheme.colorScheme
    val r = com.dasein.poryadok.ui.theme.LocalLook.current.cardRound.dp
    var tab by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(1600); tab = 1 - tab } }
    val tabs = listOf("Задачи", "Привычки")
    Column(Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, extra.line, RoundedCornerShape(16.dp)).padding(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tabs.forEachIndexed { i, t ->
                Box(Modifier.clip(RoundedCornerShape(50)).background(if (tab == i) cs.primary else extra.card).padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Text(t, fontSize = 12.sp, color = if (tab == i) cs.onPrimary else cs.onBackground)
                }
            }
        }
        androidx.compose.animation.AnimatedContent(
            targetState = tab,
            transitionSpec = {
                if (!enabled) androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                else {
                    val dir = if (targetState > initialState) 1 else -1
                    (androidx.compose.animation.slideInHorizontally { it * dir } + androidx.compose.animation.fadeIn()) togetherWith
                        (androidx.compose.animation.slideOutHorizontally { -it * dir } + androidx.compose.animation.fadeOut())
                }
            },
            label = "tabs",
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
        ) { t ->
            Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(r)).cardFill(RoundedCornerShape(r)).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                com.dasein.poryadok.ui.theme.OnBackground(extra.card) {
                    Text(if (t == 0) "Купить продукты · Позвонить маме" else "Вода · Зарядка · Чтение", fontSize = 12.sp)
                }
            }
        }
        Text(if (enabled) "Сейчас: плавная смена" else "Сейчас: мгновенно, без анимации", fontSize = 11.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Карточки с текущим скруглением: большая, средняя и кнопка-плашка. */
@Composable
private fun RoundPreview(round: Int) {
    val extra = LocalExtra.current
    val r = round.dp
    Row(Modifier.padding(top = 8.dp).fillMaxWidth().height(84.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1.4f).fillMaxSize().clip(RoundedCornerShape(r)).cardFill(RoundedCornerShape(r)).padding(10.dp)) {
            com.dasein.poryadok.ui.theme.OnBackground(extra.card) { Text("Карточка", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(2) {
                Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(r)).cardFill(RoundedCornerShape(r)))
            }
        }
    }
}
