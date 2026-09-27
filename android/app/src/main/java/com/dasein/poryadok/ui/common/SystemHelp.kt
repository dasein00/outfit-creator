package com.dasein.poryadok.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dasein.poryadok.ui.theme.LocalExtra

/** Переходы в системные настройки. Каждый — с запасным вариантом, если у производителя экран называется иначе. */
object SystemScreens {
    private fun tryStart(ctx: Context, vararg intents: Intent): Boolean {
        for (i in intents) {
            try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true } catch (_: Exception) {}
        }
        return false
    }

    fun appDetails(ctx: Context) = tryStart(
        ctx,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)),
        Intent(Settings.ACTION_SETTINGS),
    )

    fun usageAccess(ctx: Context) = tryStart(
        ctx,
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:" + ctx.packageName)),
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )

    fun healthConnect(ctx: Context) = tryStart(
        ctx,
        *listOfNotNull(
            if (Build.VERSION.SDK_INT >= 34) Intent("android.health.connect.action.HEALTH_HOME_SETTINGS") else null,
            Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"),
            ctx.packageManager.getLaunchIntentForPackage("com.google.android.apps.healthdata"),
        ).toTypedArray(),
    )

    fun app(ctx: Context, pkg: String) = tryStart(
        ctx,
        *listOfNotNull(
            ctx.packageManager.getLaunchIntentForPackage(pkg),
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")),
        ).toTypedArray(),
    )
}

/**
 * Android 13+ блокирует «особые» доступы (статистика использования, чтение уведомлений) для приложений,
 * установленных из файла, и показывает «Доступ для приложения запрещен». Снимается один раз в настройках приложения.
 */
@Composable
fun RestrictedSettingsHelp(access: String, onOpenAccess: () -> Unit) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    InfoBox("restricted_$access", "Пишет «Доступ для приложения запрещен»?", glyph = "ui:settings") {
        Text(
            "Это защита Android для приложений, установленных из файла, а не из Google Play. Снимается один раз:",
            fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 2.dp),
        )
        listOf(
            "Сначала попробуйте включить доступ — пусть Android покажет это предупреждение (без этого нужный пункт не появится).",
            "Нажмите «Настройки DASEIN» ниже.",
            "В правом верхнем углу нажмите ⋮ (три точки) → «Разрешить ограниченные настройки» и подтвердите отпечатком или PIN-кодом.",
            "Вернитесь и нажмите «$access» → DASEIN → включите переключатель.",
        ).forEachIndexed { i, t ->
            Row(Modifier.padding(top = 6.dp)) {
                Text("${i + 1}.", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(end = 6.dp))
                Text(t, fontSize = 13.sp)
            }
        }
        Text(
            "Если трёх точек нет, прокрутите экран приложения вниз: у некоторых производителей пункт называется «Разрешить ограниченные параметры».",
            fontSize = 11.sp, color = extra.dim, modifier = Modifier.padding(top = 6.dp),
        )
        OutlinedButton(onClick = { SystemScreens.appDetails(ctx) }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Настройки DASEIN") }
        OutlinedButton(onClick = onOpenAccess, Modifier.fillMaxWidth()) { Text(access) }
    }
}
