@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.logic.HalloweenSeason
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.HalloweenAssets
import com.dasein.poryadok.ui.common.HalloweenBackdrop
import com.dasein.poryadok.ui.common.OutlinedButton
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Text
import com.dasein.poryadok.ui.common.btnLabel
import com.dasein.poryadok.ui.common.halloweenCap
import com.dasein.poryadok.ui.theme.Halloween
import com.dasein.poryadok.ui.theme.Holiday
import com.dasein.poryadok.ui.theme.LocalExtra
import com.dasein.poryadok.ui.theme.LocalLook
import com.dasein.poryadok.ui.theme.Look
import com.dasein.poryadok.ui.theme.OnBackground

const val HOLIDAY_ROUTE = "appearance/holiday"
const val HALLOWEEN_ROUTE = "appearance/halloween"

/** Строка-переход к подразделу праздничной темы: название, даты, «сейчас включена». */
@Composable
internal fun SeasonRow(title: String, desc: String, on: Boolean, onClick: () -> Unit) {
    val extra = LocalExtra.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(extra.card).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(btnLabel(title), fontWeight = FontWeight.SemiBold)
            Text(desc + if (on) " · сейчас включена" else "", fontSize = 12.sp, color = extra.dim, lineHeight = 16.sp)
        }
        Text("›", fontSize = 22.sp, color = extra.dim, modifier = Modifier.padding(start = 8.dp))
    }
}

/** «Оформление → Новогодний режим». */
@Composable
fun HolidaySettingsScreen(nav: NavHostController) {
    Screen("Новогодний режим", onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            HolidaySettings()
            Gap(40.dp)
        }
    }
}

@Composable
private fun SwitchRow(title: String, desc: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val extra = LocalExtra.current
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(btnLabel(title), fontWeight = FontWeight.SemiBold, color = if (enabled) Color.Unspecified else extra.dim)
            Text(desc, fontSize = 12.sp, color = extra.dim, lineHeight = 16.sp)
        }
        Switch(checked, onChange, enabled = enabled)
    }
}

/**
 * «Оформление → Хэллоуин»: автоматическое включение с 15 октября по 2 ноября, тестовый режим, каждое украшение
 * отдельным выключателем, фон и плашки — с живым превью сцены.
 */
@Composable
fun HalloweenSettingsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val h = Halloween
    val today = Holiday.today.value
    val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMMM", java.util.Locale("ru"))
    LaunchedEffect(Unit) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { HalloweenAssets.load(ctx.applicationContext) } }
    // Вне сезона общие часы украшений стоят — для живого превью они идут, пока открыт этот экран.
    LaunchedEffect(h.active, Holiday.active) {
        if (h.active || Holiday.active) return@LaunchedEffect
        while (true) { Holiday.clock.floatValue += .033f; kotlinx.coroutines.delay(33) }
    }
    Screen("Хэллоуин", onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            // Живое превью: сцена целиком и плашка с норами и паучком.
            Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(300.dp).clip(RoundedCornerShape(18.dp))) {
                HalloweenBackdrop(Modifier.fillMaxSize())
                val card = h.card().let { if (extra.dark) it.dark else it.light }
                Box(
                    Modifier.align(Alignment.Center).padding(horizontal = 22.dp).fillMaxWidth().height(96.dp)
                        .halloweenCap(2027, force = true).clip(RoundedCornerShape(LocalLook.current.cardRound.dp)).background(card),
                ) {
                    OnBackground(card) {
                        Column(Modifier.padding(start = 14.dp, top = 24.dp)) {
                            Text(btnLabel("Пример плашки"), fontWeight = FontWeight.SemiBold)
                            Text("Из норки выползает паучок", fontSize = 12.sp, color = extra.dim)
                        }
                    }
                }
            }
            if (!h.active) Text(
                "Превью видно и вне сезона. Чтобы включить Хэллоуин во всём приложении прямо сейчас — «Тестовый режим» ниже.",
                fontSize = 12.sp, color = extra.dim, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp),
            )

            SectionTitle("Включение")
            SwitchRow(
                "Автоматический Хэллоуин",
                "Каждый год с 15 октября по 2 ноября — ночное кладбище в тумане, тыквенно-лаймовые иконки. Потом всё само возвращается. " + when {
                    !h.auto.value -> "Сейчас выключен."
                    HalloweenSeason.inSeason(today) -> "Сейчас действует — до ${HalloweenSeason.seasonEnd(today).format(fmt)}."
                    else -> "Включится ${HalloweenSeason.nextStart(today).format(fmt)}."
                },
                h.auto.value,
            ) { h.set(ctx, "auto", it) }
            SwitchRow(
                "Тестовый режим",
                "Хэллоуин прямо сейчас, в любой день, — чтобы посмотреть заранее. Новогодний тестовый режим при этом выключается.",
                h.preview.value,
            ) { h.setPreview(ctx, it) }

            SectionTitle("Украшения")
            SwitchRow("Туман", "Плывёт между дальним и ближним планом кладбища и слегка — под плашками.", h.fog.value) { h.set(ctx, "fog", it) }
            SwitchRow("Красные глаза", "Моргают в кустах кладбища и в кронах деревьев, иногда исчезают и появляются снова.", h.eyes.value) { h.set(ctx, "eyes", it) }
            SwitchRow("Рука из могилы", "Время от времени вырывается из земли, машет и уходит обратно.", h.hand.value) { h.set(ctx, "hand", it) }
            SwitchRow("Летучие мыши", "Висят на ветках сверху, иногда взлетают и пересаживаются на другую ветку.", h.bats.value) { h.set(ctx, "bats", it) }
            SwitchRow(
                "Пауки на плашках",
                "На каждой плашке земляная шапка с норками. Из норки выползает паучок: при двух норах перебегает в другую, при одной — гуляет по плашке и возвращается.",
                h.spiders.value,
            ) { h.set(ctx, "spiders", it) }
            SwitchRow("Привидение", "Вместо саней Санты по полосе кнопок на главной пролетает привидение с двумя маленькими спутниками.", h.ghost.value) { h.set(ctx, "ghost", it) }
            SwitchRow("Листопад при входе", "5 секунд кружатся листья, пролетают летучие мыши и мерцают звёздочки — потом улетают за край экрана.", h.leaves.value) { h.set(ctx, "leaves", it) }
            if (h.active) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    Holiday.santaNow.intValue++
                    android.widget.Toast.makeText(ctx, "Привидение вылетело — откройте главную", android.widget.Toast.LENGTH_SHORT).show()
                }, enabled = h.ghost.value, modifier = Modifier.weight(1f)) { Text(btnLabel("Запустить привидение")) }
                OutlinedButton(onClick = { Holiday.letItSnow() }, enabled = h.leaves.value, modifier = Modifier.weight(1f)) { Text(btnLabel("Листопад")) }
            }

            SectionTitle("Фон")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                h.BG_STYLES.forEachIndexed { i, st ->
                    StyleSwatch(st.name, h.bgStyle.intValue == i, Brush.radialGradient(if (extra.dark) st.dark else st.light)) { h.setBgStyle(ctx, i) }
                }
            }
            SectionTitle("Плашки")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val base = h.bg().let { if (extra.dark) it.dark else it.light }[1]
                h.CARD_STYLES.forEachIndexed { i, st ->
                    val c = if (extra.dark) st.dark else st.light
                    StyleSwatch(st.name, h.cardStyle.intValue == i, SolidColor(Look.mix(base, c.copy(alpha = 1f), c.alpha))) { h.setCardStyle(ctx, i) }
                }
            }
            Gap(40.dp)
        }
    }
}
