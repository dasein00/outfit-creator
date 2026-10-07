package com.dasein.poryadok.system

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * Сжатие видео, чтобы ролики рецептов и тренировок занимали мало места: короткая сторона — 480 точек,
 * видео H.264 около 1 Мбит/с, звук AAC сохраняется. Обычно файл уменьшается в 5–15 раз.
 */
object VideoCompress {
    const val SHORT_SIDE = 480
    private const val BITRATE = 1_000_000

    /** Размеры кадра с учётом поворота и средний битрейт. */
    private data class Info(val w: Int, val h: Int, val bitrate: Int)

    private fun info(path: String): Info? = runCatching {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(path)
            var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return null
            var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return null
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
            Info(w, h, r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0)
        } finally {
            runCatching { r.release() }
        }
    }.getOrNull()

    /** Стоит ли сжимать: кадр крупнее 480p или битрейт заметно выше целевого. */
    fun worth(path: String): Boolean {
        val i = info(path) ?: return false
        return minOf(i.w, i.h) > SHORT_SIDE + 40 || i.bitrate > BITRATE * 2
    }

    /**
     * Сжимает [input] в новый файл рядом с ним. Возвращает путь к сжатому файлу или null, если не получилось
     * (тогда остаётся исходный). Исходный файл не удаляется — это решает вызывающий.
     */
    @OptIn(UnstableApi::class)
    suspend fun compress(ctx: Context, input: File): File? {
        val i = withContext(Dispatchers.IO) { info(input.absolutePath) } ?: return null
        val out = File(input.parentFile, "v_${System.currentTimeMillis()}.mp4")
        // Высота итогового кадра так, чтобы короткая сторона стала 480 (вертикальное видео — выше).
        val short = minOf(i.w, i.h).coerceAtLeast(1)
        val scale = (SHORT_SIDE.toFloat() / short).coerceAtMost(1f)
        val height = ((i.h * scale) / 2).roundToInt() * 2
        val ok = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val transformer = Transformer.Builder(ctx)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(
                        DefaultEncoderFactory.Builder(ctx.applicationContext)
                            .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(BITRATE).build())
                            .build(),
                    )
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (cont.isActive) cont.resume(true)
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            if (cont.isActive) cont.resume(false)
                        }
                    })
                    .build()
                val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(input)))
                    .setEffects(Effects(emptyList(), if (scale < 1f) listOf(Presentation.createForHeight(height)) else emptyList()))
                    .build()
                cont.invokeOnCancellation { runCatching { transformer.cancel() } }
                runCatching { transformer.start(item, out.absolutePath) }.onFailure { if (cont.isActive) cont.resume(false) }
            }
        }
        // Если сжатый файл не меньше исходного — оставляем исходный.
        return if (ok && out.length() > 0 && out.length() < input.length()) out else { out.delete(); null }
    }

    /** Сжимает файл на месте: при успехе исходный удаляется. Возвращает путь к итоговому файлу. */
    suspend fun replace(ctx: Context, path: String): String {
        val f = File(path)
        if (!f.exists()) return path
        val c = compress(ctx, f) ?: return path
        withContext(Dispatchers.IO) { f.delete() }
        return c.absolutePath
    }

    fun mb(bytes: Long) = if (bytes < 1024 * 1024) "${bytes / 1024} КБ" else String.format(java.util.Locale.US, "%.1f МБ", bytes / 1048576.0)
}
