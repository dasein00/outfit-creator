package com.dasein.poryadok.ui.more

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.Settings as AppSettings
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.system.Sber
import com.dasein.poryadok.system.Steps
import com.dasein.poryadok.ui.common.AppIcon
import com.dasein.poryadok.ui.common.BarChart
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.HGap
import com.dasein.poryadok.ui.common.Ic
import com.dasein.poryadok.ui.common.ProgressRing
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.common.observe
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun thousands(n: Int) = String.format(Locale.US, "%,d", n).replace(',', ' ')

private fun ago(ms: Long): String = if (ms <= 0) "ещё не было" else SimpleDateFormat("d MMM, HH:mm", Locale("ru")).format(Date(ms))

/** Перепроверять разрешения, когда пользователь возвращается из системных настроек. */
@Composable
private fun OnResume(block: suspend () -> Unit) {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) { owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { block() } }
}

@Composable
fun StepsScreen(nav: NavHostController, settings: AppSettings) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    val dayLogs by observe(emptyList()) { Graph.dao.dayLogs() }
    val profile by observe(null) { Graph.dao.profile() }
    val goal = profile?.stepsGoal ?: 8000
    val today = Dates.today()
    val todaySteps = dayLogs.firstOrNull { it.day == today }?.steps ?: 0
    var hcStatus by remember { mutableStateOf(Steps.HcStatus.UNAVAILABLE) }
    var hcGranted by remember { mutableStateOf(false) }
    var sensorOk by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }

    OnResume {
        hcStatus = Steps.hcStatus(ctx)
        hcGranted = Steps.hcGranted(ctx)
        sensorOk = Steps.sensorAllowed(ctx)
    }
    fun sync() {
        syncing = true
        scope.launch {
            val v = Steps.sync(ctx)
            syncing = false
            Toast.makeText(ctx, if (v != null) "Шагов сегодня: $v" else "Нет данных — проверьте источники ниже", Toast.LENGTH_SHORT).show()
        }
    }
    val hcLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        hcGranted = granted.containsAll(Steps.HC_PERMISSIONS)
        if (hcGranted) scope.launch { Graph.prefs.update { it.copy(stepsHc = true) }; Steps.ensureScheduled(ctx); sync() }
        else Toast.makeText(ctx, "Разрешение не выдано", Toast.LENGTH_SHORT).show()
    }
    val activityLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        sensorOk = ok
        if (ok) scope.launch { Graph.prefs.update { it.copy(stepsSensor = true) }; Steps.ensureScheduled(ctx); sync() }
    }

    Screen("Шаги", onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Tile {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(todaySteps / goal.coerceAtLeast(1).toFloat(), extra.ok, size = 96.dp, stroke = 9.dp) {
                        AppIcon(Ic.heart, 30.dp, badge = false)
                    }
                    Column(Modifier.padding(start = 16.dp).weight(1f)) {
                        Text(thousands(todaySteps), style = MaterialTheme.typography.headlineMedium)
                        Text("из ${thousands(goal)} шагов сегодня", color = extra.dim, fontSize = 13.sp)
                        Text("Синхронизация: ${ago(settings.stepsSyncedAt)}" + if (settings.stepsSource.isNotBlank()) " · ${settings.stepsSource}" else "", color = extra.dim, fontSize = 12.sp)
                    }
                }
                Gap(8.dp)
                Button(onClick = { sync() }, enabled = !syncing && (settings.stepsHc || settings.stepsSensor), modifier = Modifier.fillMaxWidth()) {
                    Text(if (syncing) "Синхронизирую…" else "Синхронизировать сейчас")
                }
            }
            SectionTitle("Последние 14 дней")
            val days = (13 downTo 0).map { today - it }
            BarChart(
                days.map { d -> (dayLogs.firstOrNull { it.day == d }?.steps ?: 0).toFloat() },
                days.map { Dates.day(it).dayOfMonth.toString() },
                extra.ok, highlight = 13, target = goal.toFloat(),
            )

            SectionTitle("Health Connect — любые фитнес-приложения")
            Tile {
                Text(
                    "Через Health Connect шаги приходят из Mi Fitness (Xiaomi Wear), Zepp Life, Google Fit, Samsung Health, Huawei Health и других. " +
                        "На Poco F7 шаги, которые показывает виджет, считает приложение Xiaomi — включите в нём синхронизацию с Health Connect.",
                    fontSize = 13.sp,
                )
                Gap(8.dp)
                val statusText = when (hcStatus) {
                    Steps.HcStatus.AVAILABLE -> if (hcGranted) "Подключено" else "Доступно, нужно разрешение"
                    Steps.HcStatus.NEEDS_UPDATE -> "Нужно обновить Health Connect"
                    Steps.HcStatus.UNAVAILABLE -> "Health Connect не найден"
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(statusText, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = if (hcGranted) extra.ok else MaterialTheme.colorScheme.onSurface)
                    if (hcGranted) Switch(settings.stepsHc, { on -> io { Graph.prefs.update { it.copy(stepsHc = on) }; Steps.ensureScheduled(ctx) } })
                }
                Gap(6.dp)
                when {
                    hcStatus == Steps.HcStatus.AVAILABLE && !hcGranted ->
                        Button(onClick = { hcLauncher.launch(Steps.HC_PERMISSIONS) }, Modifier.fillMaxWidth()) { Text("Разрешить чтение шагов") }
                    hcStatus != Steps.HcStatus.AVAILABLE ->
                        OutlinedButton(onClick = { openStore(ctx, "com.google.android.apps.healthdata") }, Modifier.fillMaxWidth()) { Text("Установить Health Connect") }
                }
                Text(
                    "Как включить в Mi Fitness: Профиль → значок настроек → «Health Connect» (или «Сторонние сервисы») → разрешить запись шагов. " +
                        "В Google Fit: Профиль → Настройки → Health Connect.",
                    fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 8.dp),
                )
            }

            SectionTitle("Датчик шагов телефона")
            Tile {
                val hasSensor = remember { Steps.hasSensor(ctx) }
                Text(
                    if (hasSensor) "Телефон сам считает шаги. DASEIN снимает показания раз в 15 минут — отдельное приложение не нужно. Считается с момента включения."
                    else "В этом телефоне нет аппаратного счётчика шагов — используйте Health Connect.",
                    fontSize = 13.sp,
                )
                if (hasSensor) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Text("Считать шаги датчиком", Modifier.weight(1f))
                    Switch(settings.stepsSensor && sensorOk, { on ->
                        if (on && !sensorOk && Build.VERSION.SDK_INT >= 29) activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                        else io { Graph.prefs.update { it.copy(stepsSensor = on) }; Steps.ensureScheduled(ctx); if (on) Steps.sync(ctx) }
                    })
                }
            }
            Text(
                "Если включены оба источника, за день берётся большее значение. Шаги также можно ввести вручную в «Питание» раздела «Здоровье».",
                fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(vertical = 12.dp),
            )
            Gap(40.dp)
        }
    }
}

private fun openStore(ctx: android.content.Context, pkg: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
fun SberScreen(nav: NavHostController, settings: AppSettings) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    var listener by remember { mutableStateOf(false) }
    var sms by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    val txns by observe(emptyList()) { Graph.dao.txns() }
    val accounts by observe(emptyList()) { Graph.dao.accounts() }
    val sberAcc = accounts.firstOrNull { it.name == Sber.ACCOUNT }
    val sberTxns = txns.filter { it.accountId == sberAcc?.id }.take(10)
    OnResume {
        listener = Sber.listenerEnabled(ctx)
        sms = Sber.smsAllowed(ctx)
    }
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        sms = ok
        if (!ok) Toast.makeText(ctx, "Без разрешения импорт SMS недоступен", Toast.LENGTH_SHORT).show()
    }

    Screen("Сбербанк", onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(Ic.bank, 34.dp)
                HGap(12.dp)
                Text(
                    "У Сбера нет открытого API для личных финансов, поэтому DASEIN читает уведомления «СберБанк Онлайн» и SMS с номера 900 и сам записывает покупки, переводы и зачисления на счёт «Сбер».",
                    fontSize = 13.sp,
                )
            }
            SectionTitle("1. Уведомления — новые операции")
            Tile {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (listener) "Доступ к уведомлениям есть" else "Нужен доступ к уведомлениям", fontWeight = FontWeight.SemiBold, color = if (listener) extra.ok else MaterialTheme.colorScheme.onSurface)
                        Text("Читаются только уведомления Сбера и SMS от 900. Ничего не отправляется в интернет.", fontSize = 12.sp, color = extra.dim)
                    }
                }
                Gap(6.dp)
                if (!listener) Button(onClick = {
                    try { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (_: ActivityNotFoundException) {
                        ctx.startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                }, Modifier.fillMaxWidth()) { Text("Открыть настройки доступа") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Записывать операции автоматически", Modifier.weight(1f))
                    Switch(settings.sberOn, { on -> io { Graph.prefs.update { it.copy(sberOn = on) } } }, enabled = listener)
                }
                Text(
                    "На Xiaomi/Poco: разрешите DASEIN автозапуск и отключите для него экономию батареи — иначе система может выгружать службу.",
                    fontSize = 12.sp, color = extra.dim,
                )
            }
            SectionTitle("2. История из SMS 900")
            Tile {
                Text("Разово загрузить операции из уже полученных SMS за последние 90 дней. Повторный запуск дублей не создаёт.", fontSize = 13.sp)
                Gap(6.dp)
                if (!sms) OutlinedButton(onClick = { smsLauncher.launch(Manifest.permission.READ_SMS) }, Modifier.fillMaxWidth()) { Text("Разрешить чтение SMS") }
                else Button(onClick = {
                    importing = true
                    scope.launch {
                        val (seen, added) = Sber.importSms(ctx)
                        importing = false
                        Toast.makeText(ctx, "SMS от Сбера: $seen, новых операций: $added", Toast.LENGTH_LONG).show()
                    }
                }, enabled = !importing, modifier = Modifier.fillMaxWidth()) { Text(if (importing) "Загружаю…" else "Загрузить из SMS") }
            }
            SectionTitle("Последние операции «Сбер»")
            Text("Всего записано: ${settings.sberImported}. Последняя: ${ago(settings.sberLastAt)}", fontSize = 12.sp, color = extra.dim)
            Gap(6.dp)
            if (sberTxns.isEmpty()) Text("Пока нет операций.", color = extra.dim)
            sberTxns.forEach { t ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${Dates.short(t.day)}  ${t.note.removePrefix("Сбер: ")}", Modifier.weight(1f), fontSize = 13.sp, maxLines = 1)
                    Text((if (t.type == 1) "+" else "−") + com.dasein.poryadok.logic.Money.format(t.amount, settings.currency), fontSize = 13.sp, color = if (t.type == 1) extra.ok else MaterialTheme.colorScheme.onSurface)
                }
            }
            Text(
                "Категория подбирается по названию магазина; её можно поменять в операции. Если какое-то уведомление не распозналось — добавьте операцию вручную.",
                fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(vertical = 12.dp),
            )
            Gap(40.dp)
        }
    }
}
