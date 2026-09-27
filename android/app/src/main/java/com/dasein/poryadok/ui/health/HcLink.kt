package com.dasein.poryadok.ui.health

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.dasein.poryadok.system.Body
import com.dasein.poryadok.system.Steps
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.Glyphs
import com.dasein.poryadok.ui.common.HGap
import com.dasein.poryadok.ui.common.SystemScreens
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.theme.LocalExtra
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val WHEN = SimpleDateFormat("d MMM, HH:mm", Locale("ru"))

/**
 * Проверка цепочки «весы → OKOK → Google Fit → Health Connect → DASEIN»: где именно данные теряются.
 * [refresh] меняется после синхронизации, чтобы карточка перечитала состояние.
 */
@Composable
fun HcLinkCard(refresh: Int, onGranted: () -> Unit) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val owner = LocalLifecycleOwner.current
    var diag by remember { mutableStateOf<Body.Diag?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(owner, refresh, tick) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { diag = Body.diagnose(ctx) }
    }
    val permLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        tick++
        if (granted.any { it in Body.PERMISSIONS }) onGranted()
    }
    val bgLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { tick++ }
    val d = diag

    @Composable
    fun Check(ok: Boolean?, title: String, sub: String, action: String? = null, onAction: () -> Unit = {}) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (ok) { true -> "✓"; false -> "✕"; null -> "•" }, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                color = when (ok) { true -> extra.ok; false -> extra.warn; null -> extra.dim },
                modifier = Modifier.padding(end = 10.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(sub, fontSize = 12.sp, color = extra.dim)
            }
            if (action != null) TextButton(onClick = onAction) { Text(action) }
        }
    }

    Tile {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph(Glyphs.SCALE, 24.dp)
            HGap(10.dp)
            Column(Modifier.weight(1f)) {
                Text("Связь с весами", fontWeight = FontWeight.SemiBold)
                Text("Весы → OKOK → Google Fit → Health Connect → DASEIN", fontSize = 12.sp, color = extra.dim)
            }
        }
        if (d == null) {
            Text("Проверяю…", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 8.dp))
        } else {
            Check(
                d.status == Steps.HcStatus.AVAILABLE, "Health Connect",
                when (d.status) {
                    Steps.HcStatus.AVAILABLE -> "установлен"
                    Steps.HcStatus.NEEDS_UPDATE -> "нужно обновить"
                    Steps.HcStatus.UNAVAILABLE -> "не установлен"
                },
                if (d.status != Steps.HcStatus.AVAILABLE) "Установить" else null,
            ) { SystemScreens.app(ctx, "com.google.android.apps.healthdata") }
            if (d.status == Steps.HcStatus.AVAILABLE) {
                Check(
                    d.weightAllowed, "Доступ DASEIN к весу и составу тела",
                    if (d.granted == 0) "не выдан" else "разрешено ${d.granted} из ${d.total} показателей",
                    if (d.granted < d.total) "Разрешить" else null,
                ) { permLauncher.launch(Body.PERMISSIONS) }
                if (d.weightAllowed) Check(
                    d.count30 > 0, "Взвешивания в Health Connect за 30 дней",
                    if (d.lastKg != null && d.lastAt != null) "${d.count30} шт. Последнее: " + "%.1f кг".format(d.lastKg).replace('.', ',') +
                        " · ${WHEN.format(Date(d.lastAt))} · из ${d.lastApp}"
                    else "пусто — значит Google Fit ещё не передаёт вес в Health Connect (шаг 2 ниже)",
                )
                Check(
                    if (d.background) true else null, "Обновление в фоне",
                    if (d.background) "разрешено — вес подтянется, даже если DASEIN закрыт"
                    else "не разрешено — вес подтягивается при каждом открытии DASEIN",
                    if (!d.background) "Разрешить" else null,
                ) { bgLauncher.launch(setOf(Body.BACKGROUND)) }
                d.error?.let { Text("Ошибка чтения: $it", fontSize = 12.sp, color = extra.warn, modifier = Modifier.padding(top = 6.dp)) }
            }
        }
        TextButton(onClick = { open = !open }, modifier = Modifier.padding(top = 4.dp)) {
            Text(if (open) "Скрыть инструкцию" else "Как настроить, если вес не приходит")
        }
        if (open) {
            listOf(
                "OKOK International → «Мой» → Google Fit → подключить (вы это уже сделали).",
                "Google Fit → вкладка «Профиль» → шестерёнка → «Синхронизировать Fit с Health Connect» — включить. Без этого Google Fit хранит вес только у себя.",
                "Health Connect → «Разрешения приложений» → Fit → разрешить запись: вес, процент жира, костная и безжировая масса, базальный обмен, вода.",
                "Здесь выше — «Разрешить» доступ DASEIN, если стоит ✕.",
                "Встаньте на весы при открытом OKOK. Через 1–5 минут вес появится в Google Fit, затем в Health Connect — строка «Взвешивания в Health Connect» покажет его, а DASEIN запишет сам.",
            ).forEachIndexed { i, t ->
                Row(Modifier.padding(top = 6.dp)) {
                    Text("${i + 1}.", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(end = 6.dp))
                    Text(t, fontSize = 13.sp)
                }
            }
            Text(
                "Через Health Connect приходят: вес, % жира, костная и безжировая (из неё — мышечная) масса, базальный обмен и вода. " +
                    "Висцеральный жир, белок, метаболический возраст и подкожный жир Google Fit не передаёт — их можно дописать в «+ Взвешивание».",
                fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 8.dp),
            )
            Gap(8.dp)
            Row {
                OutlinedButton(onClick = { SystemScreens.app(ctx, "com.google.android.apps.fitness") }, Modifier.weight(1f)) { Text("Google Fit", fontSize = 13.sp) }
                HGap(8.dp)
                OutlinedButton(onClick = { SystemScreens.healthConnect(ctx) }, Modifier.weight(1f)) { Text("Health Connect", fontSize = 13.sp, maxLines = 1) }
            }
        }
    }
}
