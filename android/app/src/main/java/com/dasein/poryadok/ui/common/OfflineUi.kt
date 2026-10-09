package com.dasein.poryadok.ui.common

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.dasein.poryadok.system.Offline
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.util.Locale

private val UNTIL = DateTimeFormatter.ofPattern("d MMMM", Locale("ru"))

/** «Сохранено для чтения без интернета: 31 из 31 дня, до 8 ноября». */
@Composable
fun OfflineStatusLine(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val st by Offline.status.collectAsState()
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { Offline.refreshStatus(ctx) } }
    val s = st
    val until = s.until
    val text = when {
        s.running -> "Загружаю новости на месяц вперёд для чтения без интернета… ${s.stored} из ${s.total}"
        s.stored >= s.total && until != null -> "Без интернета сохранено на месяц вперёд — до ${until.format(UNTIL)}."
        until != null -> "Без интернета сохранено до ${until.format(UNTIL)} (${s.stored} из ${s.total} дней). Остальное догрузится при подключении к сети."
        else -> "Новости на месяц вперёд загрузятся сами, как только телефон подключится к Wi-Fi или мобильной сети."
    }
    Text(text, fontSize = 11.sp, color = LocalExtra.current.dim, lineHeight = 15.sp, modifier = modifier)
}
