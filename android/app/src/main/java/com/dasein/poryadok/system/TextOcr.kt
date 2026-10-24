package com.dasein.poryadok.system

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.dasein.poryadok.logic.CyrOcr
import com.dasein.poryadok.logic.RecipeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Текст со скриншотов (подпись к посту, рецепт). Распознавание на телефоне (ML Kit) читает только латиницу и
 * превращает русский текст в «MHrpegveHTbI», поэтому:
 *  1. есть ключ ИИ и интернет — скриншот читает ИИ, который понимает картинки (точно, с кириллицей);
 *  2. иначе — ML Kit, а похожие латинские буквы переводятся в кириллицу; с ИИ без картинок текст ещё и выправляется.
 */
object TextOcr {
    private const val READ_PROMPT = "Перепиши дословно весь текст со скриншота (это пост или рецепт, обычно на русском). " +
        "Сохрани строки, списки, числа и единицы измерения как на картинке. Без пояснений, без своих слов, без форматирования Markdown — только текст. " +
        "Интерфейс приложения (кнопки, время, имя аккаунта, лайки, «ещё») не переписывай."

    private const val FIX_PROMPT = "Это текст рецепта на русском, распознанный со скриншота с ошибками: часть русских букв прочитана как похожие латинские " +
        "(«MHrpegveHTbI» = «ингредиенты», «WT» = «шт», «r» = «г»). Восстанови исходный русский текст. Сохрани строки, числа и единицы. " +
        "Верни только исправленный текст, без пояснений."

    suspend fun read(ctx: Context, uris: List<Uri>, onStage: (String) -> Unit = {}): String {
        val online = Ai.enabled(ctx) && Offline.hasNetwork(ctx)
        if (online) {
            onStage("ИИ читает скриншот…")
            val images = withContext(Dispatchers.IO) { uris.mapNotNull { jpeg(ctx, it) } }
            if (images.isNotEmpty()) runCatching { Ai.askImages(ctx, READ_PROMPT, images) }.getOrNull()
                ?.let { clean(it) }?.takeIf { it.length > 10 }?.let { return it }
        }
        onStage("Распознаю текст…")
        val raw = RecipeText.cleanOcr(uris.flatMap { ScaleOcr.lines(ctx, it) })
        if (raw.isBlank() || !CyrOcr.looksGarbled(raw)) return raw
        val fixed = CyrOcr.fix(raw)
        if (!online) return fixed
        onStage("ИИ исправляет распознанный текст…")
        return runCatching { Ai.ask(ctx, FIX_PROMPT, raw.take(6000)) }.getOrNull()?.let { clean(it) }?.takeIf { it.length > raw.length / 3 } ?: fixed
    }

    /** Ответ модели без ```-обёртки и пустых краёв. */
    private fun clean(s: String) = s.trim().removePrefix("```text").removePrefix("```").removeSuffix("```").trim()

    /** Скриншот → JPEG до 2000 px по длинной стороне (меньше трафика, текст ещё читается). */
    private fun jpeg(ctx: Context, uri: Uri): ByteArray? = runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        val long = maxOf(opts.outWidth, opts.outHeight).takeIf { it > 0 } ?: return null
        var sample = 1
        while (long / (sample * 2) >= 2000) sample *= 2
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val scale = 2000f / maxOf(bmp.width, bmp.height)
        val fit = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        java.io.ByteArrayOutputStream().also { fit.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
    }.getOrNull()
}
