package com.dasein.poryadok.ui.more

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import com.dasein.poryadok.BuildConfigInfo
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.Settings
import com.dasein.poryadok.logic.Dates
import com.dasein.poryadok.system.Alarms
import com.dasein.poryadok.system.BackupFiles
import com.dasein.poryadok.ui.Pin
import com.dasein.poryadok.ui.PinPad
import com.dasein.poryadok.ui.common.ConfirmDialog
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.Screen
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.Segments
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.common.io
import com.dasein.poryadok.ui.theme.LocalExtra
import com.dasein.poryadok.ui.theme.Palette
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(nav: NavHostController, s: Settings) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var pinStep by remember { mutableStateOf(0) }
    var firstPin by remember { mutableStateOf("") }
    var pinAttempt by remember { mutableStateOf(0) }
    var confirmImport by remember { mutableStateOf<Uri?>(null) }
    var name by remember { mutableStateOf(s.name) }
    fun upd(f: (Settings) -> Settings) = io { Graph.prefs.update(f); Alarms.rescheduleAll(ctx) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            message = BackupFiles.export(ctx, uri).fold({ "Копия сохранена (фото: $it)" }, { "Ошибка: ${it.message}" })
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) confirmImport = uri }

    Screen(title = "Настройки", onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionTitle("Профиль")
            TextInput(name, { name = it; upd { st -> st.copy(name = it.trim()) } }, "Как к вам обращаться")
            Gap(10.dp)
            Text("Валюта", fontSize = 13.sp, color = extra.dim)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                listOf("₽", "$", "€", "₸", "₴", "Br").forEach { c -> Pill(c, s.currency == c) { upd { it.copy(currency = c) } } }
            }

            SectionTitle("Оформление")
            Segments(listOf("system" to "Как в системе", "light" to "Светлая", "dark" to "Тёмная"), s.theme, { v -> upd { it.copy(theme = v) } })
            Gap(12.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Palette.accents.forEachIndexed { i, (label, c) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { upd { it.copy(accent = i) } }) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(c)
                                .border(if (s.accent == i) 3.dp else 0.dp, extra.dim, CircleShape)
                        )
                        Text(label, fontSize = 10.sp, color = extra.dim)
                    }
                }
            }

            SectionTitle("Защита")
            Tile {
                if (s.pinHash.isEmpty()) {
                    Text("Приложение открывается без PIN")
                    Gap(8.dp)
                    OutlinedButton(onClick = { pinStep = 1 }) { Text("🔒 Установить PIN-код") }
                } else {
                    Text("PIN-код установлен. Приложение блокируется, если свернуть его дольше чем на 30 секунд.", fontSize = 13.sp)
                    val bioAvailable = remember { (ctx as? FragmentActivity)?.let { Pin.canUseBiometric(it) } ?: false }
                    if (bioAvailable) Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Вход по отпечатку / лицу", Modifier.weight(1f))
                        Switch(s.biometric, { v -> upd { it.copy(biometric = v) } })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedButton(onClick = { pinStep = 1 }) { Text("Сменить PIN") }
                        OutlinedButton(onClick = { upd { it.copy(pinHash = "", biometric = false) } }) { Text("Убрать PIN", color = extra.danger) }
                    }
                }
            }

            SectionTitle("Уведомления")
            Tile {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Утренняя сводка")
                        Text("План на день в ${s.summaryHour}:00", fontSize = 12.sp, color = extra.dim)
                    }
                    Switch(s.summaryOn, { v -> upd { it.copy(summaryOn = v) } })
                }
                if (s.summaryOn) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    listOf(6, 7, 8, 9, 10).forEach { h -> Pill("$h:00", s.summaryHour == h) { upd { it.copy(summaryHour = h) } } }
                }
                Gap(10.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Вечерний итог")
                        Text("В 21:00 — отметить настроение и привычки", fontSize = 12.sp, color = extra.dim)
                    }
                    Switch(s.eveningReviewOn, { v -> upd { it.copy(eveningReviewOn = v) } })
                }
                Gap(10.dp)
                val am = ctx.getSystemService(AlarmManager::class.java)
                if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                    Text("Чтобы напоминания приходили минута в минуту, разрешите точные будильники.", fontSize = 12.sp, color = extra.warn)
                    OutlinedButton(onClick = {
                        ctx.startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName)))
                    }) { Text("Разрешить точные будильники") }
                }
                OutlinedButton(onClick = {
                    ctx.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName))
                }) { Text("Настройки уведомлений") }
            }

            SectionTitle("Данные")
            Tile {
                Text("Все данные хранятся только на телефоне. Сохраните копию в файл — её можно перенести на новый телефон.", fontSize = 13.sp)
                Gap(8.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exportLauncher.launch("poryadok-${Dates.day(Dates.today())}.zip") }, modifier = Modifier.weight(1f)) { Text("Сохранить копию") }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, modifier = Modifier.weight(1f)) { Text("Восстановить") }
                }
                message?.let { Text(it, fontSize = 13.sp, color = extra.ok, modifier = Modifier.padding(top = 8.dp)) }
            }
            SectionTitle("О приложении")
            Text("Порядок ${BuildConfigInfo.version(ctx)}", color = extra.dim)
            Text("Задачи, привычки, цели, фокус, календарь, финансы, здоровье, заметки, топы и гардероб — в одном месте.", fontSize = 12.sp, color = extra.dim)
            Gap(40.dp)
        }
    }

    if (pinStep > 0) Dialog(onDismissRequest = { pinStep = 0 }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        PinPad(
            title = if (pinStep == 1) "Новый PIN" else "Повторите PIN",
            subtitle = if (pinAttempt > 0) "PIN не совпал, попробуйте снова" else "4 цифры",
            error = pinAttempt > 0,
            resetKey = pinStep * 100 + pinAttempt,
        ) { p ->
            if (pinStep == 1) { firstPin = p; pinStep = 2 }
            else if (p == firstPin) {
                upd { it.copy(pinHash = Pin.hash(p)) }
                com.dasein.poryadok.Lock.unlocked.value = true
                pinStep = 0; pinAttempt = 0
                message = "PIN установлен"
            } else { pinAttempt++; pinStep = 1 }
        }
    }
    confirmImport?.let { uri ->
        ConfirmDialog(
            "Восстановить из копии?", "Текущие данные будут заменены данными из файла.", confirm = "Восстановить",
            onDismiss = { confirmImport = null },
        ) {
            scope.launch {
                message = BackupFiles.import(ctx, uri).fold({ "Данные восстановлены" }, { "Ошибка: ${it.message}" })
            }
        }
    }
}
