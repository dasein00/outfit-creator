package com.dasein.poryadok.ui.health

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.BodyMetric
import com.dasein.poryadok.logic.BodyFacts
import com.dasein.poryadok.logic.BodyScience
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.logic.Evidence
import com.dasein.poryadok.system.Body
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.HGap
import com.dasein.poryadok.ui.common.InfoBox
import com.dasein.poryadok.ui.common.NumberField
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.num
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.theme.LocalExtra

private fun s(v: Double?) = v?.let { if (it == Math.floor(it)) it.toLong().toString() else "%.1f".format(it).replace('.', ',') } ?: ""

/** Последнее известное значение поля среди замеров (от новых к старым). */
private fun <T> List<BodyMetric>.lastOf(f: (BodyMetric) -> T?): T? = asReversed().firstNotNullOfOrNull(f)

/**
 * Научный анализ по росту, весу, возрасту, полу и обхватам. Все формулы и пороги — из клинических руководств
 * и рецензируемых работ; у каждой карточки указан источник.
 */
@Composable
fun BodyScienceScreen(nav: NavHostController) {
    val data = rememberBodyData()
    val extra = LocalExtra.current
    val measurements by observe(emptyList()) { Graph.dao.measurements() }
    val p = data.profile
    val readings = data.readings
    val last = readings.lastOrNull()
    val lastMeasure = measurements.maxByOrNull { it.day }

    var height by remember { mutableStateOf("") }
    var waist by remember { mutableStateOf("") }
    var hip by remember { mutableStateOf("") }
    var neck by remember { mutableStateOf("") }
    var filled by remember { mutableStateOf(false) }
    LaunchedEffect(readings, p, lastMeasure) {
        if (filled || (readings.isEmpty() && p.heightCm == 0.0)) return@LaunchedEffect
        height = s(readings.lastOf { it.heightCm } ?: p.heightCm)
        waist = s(readings.lastOf { it.waistCm } ?: lastMeasure?.waist)
        hip = s(readings.lastOf { it.hipCm } ?: lastMeasure?.hips)
        neck = s(readings.lastOf { it.neckCm })
        filled = true
    }

    val weight = last?.weight ?: p.startWeight
    val h = height.num()?.takeIf { it in 100.0..250.0 } ?: p.heightCm
    val facts = BodyFacts(
        male = p.male, age = p.age, heightCm = h, weightKg = weight,
        waistCm = waist.num()?.takeIf { it in 40.0..250.0 }, hipCm = hip.num()?.takeIf { it in 40.0..250.0 },
        neckCm = neck.num()?.takeIf { it in 20.0..70.0 }, scaleFatPct = last?.fatPct, activity = p.activity,
    )
    val list = remember(facts) { BodyScience.analyze(facts) }

    fun save() {
        io {
            val now = System.currentTimeMillis()
            val base = last?.takeIf { it.source != LEGACY && it.day == Dates.today() }
                ?: BodyMetric(at = now, day = Dates.today(), weight = weight, source = "вручную")
            Body.save(base.copy(heightCm = facts.heightCm, waistCm = facts.waistCm, hipCm = facts.hipCm, neckCm = facts.neckCm))
            Graph.dao.upsertProfile(p.copy(heightCm = facts.heightCm))
        }
    }

    Screen("Научный анализ тела", onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Tile {
                Text("Замеры", fontWeight = FontWeight.SemiBold)
                Text(
                    "Вес ${s(weight)} кг — из последнего взвешивания. Пол: ${if (p.male) "мужской" else "женский"}, возраст ${p.age} — из профиля.",
                    fontSize = 12.sp, color = extra.dim,
                )
                Gap(8.dp)
                Row {
                    NumberField(height, { height = it }, "Рост", Modifier.weight(1f), suffix = "см")
                    HGap(8.dp)
                    NumberField(waist, { waist = it }, "Талия", Modifier.weight(1f), suffix = "см")
                }
                Gap(6.dp)
                Row {
                    NumberField(hip, { hip = it }, "Бёдра", Modifier.weight(1f), suffix = "см")
                    HGap(8.dp)
                    NumberField(neck, { neck = it }, "Шея", Modifier.weight(1f), suffix = "см")
                }
                Gap(8.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { save() }) { Text("Сохранить в замер") }
                    HGap(8.dp)
                    androidx.compose.material3.TextButton(onClick = { nav.navigate(Routes.health(0)) }) { Text("Профиль") }
                }
            }
            Gap(8.dp)
            InfoBox("science_how", "Как измерять") {
                Text(
                    "Рост — стоя у стены, босиком, пятки вместе. Талия — на середине между нижним ребром и тазовой костью, на спокойном выдохе. " +
                        "Бёдра — по самой широкой части ягодиц. Шея — сразу под кадыком, лента чуть наклонена вниз. Лента горизонтальна и не сдавливает кожу.",
                    fontSize = 13.sp, color = extra.dim, lineHeight = 18.sp,
                )
            }
            SectionTitle("Показатели")
            list.forEach { EvidenceCard(it); Gap(8.dp) }
            Text(
                "Расчёты носят справочный характер и не заменяют консультацию врача.",
                fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun EvidenceCard(e: Evidence) {
    val extra = LocalExtra.current
    Tile {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(e.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            e.label?.let { StatusChip(it, e.tone) }
        }
        if (e.value != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(fmt(e.value, e.decimals), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = toneColor(e.tone).takeIf { e.tone != null } ?: MaterialTheme.colorScheme.onSurface)
                if (e.unit.isNotBlank()) Text(" " + e.unit, color = extra.dim, modifier = Modifier.padding(bottom = 5.dp))
            }
        }
        if (e.needs != null && e.value == null) Text(e.needs, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
        if (e.scale != null && e.value != null) { Gap(4.dp); ScaleBar(e.scale, e.value, if (e.decimals >= 2) 2 else 1) }
        if (e.verdict.isNotBlank()) Text(e.verdict, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp), lineHeight = 19.sp)
        if (e.needs != null && e.value != null) Text(e.needs, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        Gap(6.dp)
        InfoBox("science_${e.key}", "Формула и источник", glyph = "ui:book") {
            Text(e.about, fontSize = 13.sp, color = extra.dim, lineHeight = 18.sp)
            Text("Формула: " + e.formula, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            Text("Источник: " + e.source, fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp), lineHeight = 16.sp)
        }
    }
}
