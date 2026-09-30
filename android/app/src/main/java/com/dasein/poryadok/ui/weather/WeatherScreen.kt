package com.dasein.poryadok.ui.weather

import android.Manifest
import com.dasein.poryadok.ui.common.HowTo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.dasein.poryadok.logic.WeatherData
import com.dasein.poryadok.logic.WeatherLogic
import com.dasein.poryadok.system.Weather
import com.dasein.poryadok.system.WeatherIcons
import com.dasein.poryadok.ui.Routes
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.HGap
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun WeatherIcon(code: Int, isDay: Boolean, size: Dp) {
    val bmp = remember(code, isDay) { WeatherIcons.render(code, isDay, 160).asImageBitmap() }
    Image(bmp, WeatherLogic.describe(code), Modifier.size(size))
}

/** Прогноз из кэша и его обновление, если он устарел. */
@Composable
private fun rememberWeather(): Pair<WeatherData?, String?> {
    val ctx = LocalContext.current
    val live by Weather.data.collectAsState()
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val c = Weather.cached(ctx)
        if (Weather.isStale(c)) error = Weather.refresh(ctx)
    }
    return (live ?: Weather.cached(ctx)) to error
}

/** Плашка погоды на «Главном»: температура, небо, ветер и давление. Нажатие — подробный прогноз. */
@Composable
fun WeatherTile(nav: NavHostController) {
    val extra = LocalExtra.current
    val (w, error) = rememberWeather()
    Gap(8.dp)
    Tile(onClick = { nav.navigate(Routes.WEATHER) }, padding = 12.dp) {
        if (w == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WeatherIcon(2, true, 36.dp)
                HGap(10.dp)
                Column(Modifier.weight(1f)) {
                    Text("Погода", fontWeight = FontWeight.SemiBold)
                    Text(error ?: "Загружаем прогноз…", fontSize = 12.sp, color = extra.dim)
                }
                Text("›", fontSize = 22.sp, color = extra.dim)
            }
            return@Tile
        }
        val n = w.now
        val today = w.days.firstOrNull()
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherIcon(n.code, n.isDay, 44.dp)
            HGap(10.dp)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(WeatherLogic.temp(n.temp), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    HGap(8.dp)
                    Text(WeatherLogic.describe(n.code), fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp))
                }
                Text(
                    listOfNotNull(
                        w.place.takeIf { it.isNotBlank() },
                        today?.let { "${WeatherLogic.temp(it.tMin)}…${WeatherLogic.temp(it.tMax)}" },
                        "ощущается ${WeatherLogic.temp(n.feels)}",
                    ).joinToString(" · "),
                    fontSize = 12.sp, color = extra.dim, maxLines = 1,
                )
                Text(
                    "Ветер ${n.windMs.roundToInt()} м/с ${WeatherLogic.windName(n.windDir)} · ${WeatherLogic.mmHg(n.pressureHpa)} мм рт. ст. · влажность ${n.humidity}%",
                    fontSize = 12.sp, color = extra.dim, maxLines = 1,
                )
                val rain = WeatherLogic.precipToday(w.hours, today?.date ?: LocalDate.now().toString())
                if (rain.isNotBlank()) Text(
                    rain, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    color = if (rain.startsWith("Без")) extra.dim else MaterialTheme.colorScheme.primary, maxLines = 2,
                )
            }
            Text("›", fontSize = 22.sp, color = extra.dim)
        }
    }
}

private fun hm(iso: String) = iso.substringAfter('T').take(5)

private fun dayName(iso: String): String {
    val d = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return iso
    val today = LocalDate.now()
    return when (d) {
        today -> "Сегодня"
        today.plusDays(1) -> "Завтра"
        else -> d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ru")).replaceFirstChar { it.uppercase() } + ", " + d.dayOfMonth
    }
}

/** Экран погоды: сейчас, по часам, на неделю, где вы находитесь или в выбранном городе. */
@Composable
fun WeatherScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    val live by Weather.data.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pickCity by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(Weather.manualPlace(ctx)) }
    fun reload() {
        busy = true
        scope.launch { error = Weather.refresh(ctx); busy = false }
    }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { reload() }
    LaunchedEffect(Unit) {
        if (manual == null && !Weather.hasLocationPermission(ctx)) {
            askLocation.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
        } else if (Weather.isStale(Weather.cached(ctx), 10)) reload()
    }
    val w = live ?: Weather.cached(ctx)

    Screen("Погода", onBack = { nav.popBackStack(); Unit }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(w?.place?.ifBlank { null } ?: "Погода", style = MaterialTheme.typography.titleLarge)
                    Text(
                        (if (manual != null) "Выбранный город" else "По местоположению телефона") +
                            (w?.let { " · обновлено " + java.text.SimpleDateFormat("HH:mm", Locale("ru")).format(java.util.Date(it.fetchedAt)) } ?: ""),
                        fontSize = 12.sp, color = extra.dim,
                    )
                }
                TextButton(onClick = { reload() }, enabled = !busy) { Text(if (busy) "Обновляем…" else "Обновить") }
            }
            error?.let { Text(it, color = extra.warn, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                OutlinedButton(onClick = {
                    Weather.setManualPlace(ctx, null); manual = null
                    if (Weather.hasLocationPermission(ctx)) reload()
                    else askLocation.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                }) { Text("Где я сейчас") }
                OutlinedButton(onClick = { pickCity = true }) { Text("Выбрать город") }
            }
            if (w == null) {
                Tile { Text(if (busy) "Загружаем прогноз…" else "Прогноза пока нет. Разрешите доступ к местоположению или выберите город.", color = extra.dim) }
                return@Column
            }
            val n = w.now
            val today = w.days.firstOrNull()
            Tile {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WeatherIcon(n.code, n.isDay, 84.dp)
                    HGap(12.dp)
                    Column {
                        Text(WeatherLogic.temp(n.temp), fontSize = 48.sp, fontWeight = FontWeight.Bold)
                        Text(WeatherLogic.describe(n.code), fontSize = 16.sp)
                        Text("Ощущается как ${WeatherLogic.temp(n.feels)}", fontSize = 13.sp, color = extra.dim)
                    }
                }
                today?.let {
                    Text("Днём до ${WeatherLogic.temp(it.tMax)}, ночью до ${WeatherLogic.temp(it.tMin)}", fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                }
                val rain = WeatherLogic.precipToday(w.hours, today?.date ?: LocalDate.now().toString())
                if (rain.isNotBlank()) Text(rain, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
                val advice = WeatherLogic.advice(n, today)
                if (advice.isNotBlank()) Text(advice, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
            }
            SectionTitle("Условия")
            Tile {
                Param("Ветер", "${n.windMs.roundToInt()} м/с, ${WeatherLogic.windFull(n.windDir)}", "порывы до ${n.gustMs.roundToInt()} м/с")
                Param("Давление", "${WeatherLogic.mmHg(n.pressureHpa)} мм рт. ст.", "${n.pressureHpa.roundToInt()} гПа · норма около 760 на уровне моря")
                Param("Влажность", "${n.humidity}%", null)
                Param("Облачность", "${n.cloud}%", null)
                today?.let {
                    Param("Осадки сегодня", "${it.pop}%", if (it.precipMm > 0) "до ${"%.1f".format(it.precipMm)} мм" else null)
                    Param("УФ-индекс", "%.1f".format(it.uv), when { it.uv >= 8 -> "очень высокий"; it.uv >= 6 -> "высокий"; it.uv >= 3 -> "умеренный"; else -> "низкий" })
                    Param("Восход и закат", "${hm(it.sunrise)} — ${hm(it.sunset)}", null)
                }
            }
            if (w.hours.isNotEmpty()) {
                SectionTitle("По часам")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    w.hours.forEach { h ->
                        Tile(Modifier.width(66.dp), padding = 8.dp) {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(hm(h.time), fontSize = 12.sp, color = extra.dim)
                                WeatherIcon(h.code, h.isDay, 32.dp)
                                Text(WeatherLogic.temp(h.temp), fontWeight = FontWeight.SemiBold)
                                Text(if (h.pop > 0) "${h.pop}%" else " ", fontSize = 11.sp, color = extra.dim)
                            }
                        }
                    }
                }
            }
            SectionTitle("На неделю")
            Tile(padding = 8.dp) {
                w.days.forEach { d ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(dayName(d.date), Modifier.width(92.dp), fontWeight = FontWeight.Medium)
                        WeatherIcon(d.code, true, 30.dp)
                        HGap(8.dp)
                        Column(Modifier.weight(1f)) {
                            Text(WeatherLogic.describe(d.code), fontSize = 13.sp, maxLines = 1)
                            Text("ветер до ${d.windMs.roundToInt()} м/с" + if (d.pop > 0) " · осадки ${d.pop}%" else "", fontSize = 11.sp, color = extra.dim)
                        }
                        Text("${WeatherLogic.temp(d.tMin)} … ${WeatherLogic.temp(d.tMax)}", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Text(
                "Данные: Open-Meteo (open-meteo.com) — бесплатный открытый сервис прогнозов на основе моделей национальных метеослужб. " +
                    "Прогноз обновляется раз в час в фоне и показывается на виджете.",
                fontSize = 11.sp, color = extra.dim, modifier = Modifier.padding(vertical = 16.dp),
            )
            HowTo("weather")
        }
    }

    if (pickCity) CityDialog(onDismiss = { pickCity = false }) { p ->
        Weather.setManualPlace(ctx, p); manual = p; pickCity = false; reload()
    }
}

@Composable
private fun Param(title: String, value: String, sub: String?) {
    val extra = LocalExtra.current
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = extra.dim)
        Column(horizontalAlignment = Alignment.End) {
            Text(value, fontWeight = FontWeight.SemiBold)
            if (sub != null) Text(sub, fontSize = 11.sp, color = extra.dim)
        }
    }
}

@Composable
private fun CityDialog(onDismiss: () -> Unit, onPick: (WeatherLogic.Place) -> Unit) {
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<WeatherLogic.Place>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Город") },
        text = {
            Column {
                TextInput(q, { q = it }, "Название города")
                Gap(6.dp)
                Button(onClick = { searching = true; scope.launch { results = Weather.searchPlaces(q); searching = false } }, enabled = q.isNotBlank()) {
                    Text(if (searching) "Ищем…" else "Найти")
                }
                results.forEach { p ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(p) }.padding(vertical = 8.dp, horizontal = 4.dp)) {
                        Text(p.name, fontWeight = FontWeight.Medium)
                        Text(listOf(p.region, p.country).filter { it.isNotBlank() }.joinToString(", "), fontSize = 12.sp, color = extra.dim)
                    }
                }
                if (!searching && results.isEmpty() && q.isNotBlank()) Text("Введите название и нажмите «Найти»", fontSize = 12.sp, color = extra.dim)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}
