package com.dasein.poryadok.ui.health

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import com.dasein.poryadok.ui.common.HowTo
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
import kotlinx.coroutines.flow.first

private fun s(v: Double?) = v?.let { if (it == Math.floor(it)) it.toLong().toString() else "%.1f".format(it).replace('.', ',') } ?: ""

/** Научный анализ теперь внутри «Детали» → «Аналитика»; старый адрес открывает её. */
@Composable
fun BodyScienceScreen(nav: NavHostController) = BodyDetailScreen(nav, 0L, initialTab = 1)

/**
 * Научный анализ по росту, весу, возрасту, полу и обхватам. Все формулы и пороги — из клинических руководств
 * и рецензируемых работ; у каждой карточки указан источник. Каждую карточку можно свернуть.
 */
@Composable
internal fun BodyScienceSection(nav: NavHostController, last: BodyMetric?) {
    val extra = LocalExtra.current
    // Ждём настоящие данные из базы, чтобы не показать на мгновение значения по умолчанию.
    val profile by observe<com.dasein.poryadok.data.BodyProfile?>(null) { Graph.dao.profile() }
    val measurements by observe<List<com.dasein.poryadok.data.Measurement>?>(null) { Graph.dao.measurements() }
    val p = profile ?: return
    val ms = measurements ?: return
    val latest = ms.maxByOrNull { it.day }
    val weight = last?.weight ?: p.startWeight
    val facts = BodyFacts(
        male = p.male, age = p.age, heightCm = p.heightCm, weightKg = weight,
        waistCm = latest?.waist, hipCm = latest?.hips, neckCm = latest?.neck, scaleFatPct = last?.fatPct, activity = p.activity,
    )
    // Показываем только то, что удалось вычислить из введённых данных.
    val list = remember(facts) { BodyScience.analyze(facts).filter { it.value != null || it.key == "pace" } }
    SectionTitle("Научный анализ тела")
    com.dasein.poryadok.ui.common.FoldTile("science_input", "Исходные данные", summary = "${s(p.heightCm)} см · ${s(weight)} кг") {
        Text(
            listOfNotNull(
                if (p.male) "мужчина" else "женщина", "${p.age} лет", "рост ${s(p.heightCm)} см", "вес ${s(weight)} кг",
                latest?.waist?.let { "талия ${s(it)}" }, latest?.hips?.let { "бёдра ${s(it)}" }, latest?.neck?.let { "шея ${s(it)}" },
                last?.fatPct?.let { "жир с весов ${s(it)}%" },
            ).joinToString(", "),
            fontSize = 13.sp,
        )
        Text(
            "Изменить в «Замерах» →", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp).clickable { nav.navigate(Routes.health(2)) },
        )
        if (latest?.waist == null) Text(
            "Добавьте в «Замерах» талию (а также бёдра и шею) — появятся индексы талия/рост, талия/бёдра и точнее станет процент жира.",
            fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp),
        )
    }
    list.forEach { Gap(8.dp); EvidenceCard(it.copy(needs = null)) }
    Text(
        "Расчёты носят справочный характер и не заменяют консультацию врача.",
        fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(vertical = 10.dp),
    )
}

@Composable
private fun EvidenceCard(e: Evidence) {
    val extra = LocalExtra.current
    val summary = listOfNotNull(e.value?.let { fmt(it, e.decimals) + if (e.unit.isNotBlank()) " " + e.unit else "" }, e.label).joinToString(" · ").ifBlank { null }
    com.dasein.poryadok.ui.common.FoldTile("science_card_${e.key}", e.title, summary = summary, summaryColor = e.tone?.let { toneColor(it) }) {
        e.label?.let { StatusChip(it, e.tone) }
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
