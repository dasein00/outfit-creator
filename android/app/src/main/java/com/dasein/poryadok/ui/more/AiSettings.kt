package com.dasein.poryadok.ui.more

import android.content.Intent
import com.dasein.poryadok.ui.common.btnLabel
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.dasein.poryadok.ui.common.Button
import com.dasein.poryadok.ui.common.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dasein.poryadok.system.Ai
import com.dasein.poryadok.ui.common.Gap
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.SectionTitle
import com.dasein.poryadok.ui.common.TextInput
import com.dasein.poryadok.ui.common.Tile
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.launch

/** «ИИ-помощник»: бесплатный ключ OpenRouter и выбор бесплатной модели (Qwen, Kimi, DeepSeek…). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiSettingsSection() {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf(Ai.key(ctx)) }
    var model by remember { mutableStateOf(Ai.model(ctx)) }
    var models by remember { mutableStateOf(Ai.cachedModels(ctx)) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    SectionTitle("ИИ-помощник (бесплатно)")
    Tile {
        Text(
            "Бесплатные модели Qwen, Kimi, DeepSeek через OpenRouter точнее разбирают рецепты из Instagram. " +
                "Нужен бесплатный ключ: зарегистрируйтесь на openrouter.ai → Keys → Create key, скопируйте и вставьте сюда. " +
                "Платить не нужно — бесплатных запросов хватает на десятки рецептов в день. Ключ хранится только на телефоне.",
            fontSize = 13.sp, color = extra.dim, lineHeight = 18.sp,
        )
        OutlinedButton(onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://openrouter.ai/keys"))) } }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(btnLabel("Открыть openrouter.ai — получить ключ"))
        }
        Gap(6.dp)
        TextInput(key, { key = it; Ai.setKey(ctx, it) }, "Ключ OpenRouter (sk-or-…)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            OutlinedButton(onClick = {
                busy = true
                scope.launch {
                    models = Ai.freeModels(ctx)
                    if (model.isBlank() && models.isNotEmpty()) { model = models.first(); Ai.setModel(ctx, model) }
                    status = if (models.isEmpty()) "Не удалось загрузить список — проверьте интернет" else "Бесплатных моделей: ${models.size}"
                    busy = false
                }
            }, enabled = !busy, modifier = Modifier.weight(1f)) { Text(btnLabel("Обновить модели")) }
            Button(onClick = {
                busy = true; status = null
                scope.launch {
                    status = runCatching { Ai.ask(ctx, "Отвечай одним словом.", "Скажи «работает»") }
                        .fold({ "✓ Связь есть: ${it.take(40)}" }, { "✕ ${it.message}" })
                    busy = false
                }
            }, enabled = !busy && key.isNotBlank(), modifier = Modifier.weight(1f)) { Text(if (busy) "Проверяю…" else "Проверить") }
        }
        status?.let { Text(it, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
        if (models.isNotEmpty()) {
            Text("Модель (если занята — приложение само возьмёт следующую):", fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                models.take(12).forEach { m ->
                    Pill(m.substringAfter('/').removeSuffix(":free"), m == model) { model = m; Ai.setModel(ctx, m) }
                }
            }
        }
    }
}
