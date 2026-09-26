package com.dasein.poryadok.system

import android.content.Context
import android.net.Uri
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.BackupData
import com.dasein.poryadok.data.exportData
import com.dasein.poryadok.data.exportExtra
import com.dasein.poryadok.data.importExtra
import com.dasein.poryadok.data.importData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Резервная копия: zip с data.json и папками фото. */
object BackupFiles {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val DIRS = listOf("progress", "wardrobe", "outfits", "recipes")
    private val SAFE_ENTRY = Regex("^(progress|wardrobe|outfits|recipes)/[A-Za-z0-9_.\\-]+$")

    suspend fun export(ctx: Context, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val data = Graph.db.exportData(System.currentTimeMillis()).copy(extra = Graph.extraDb.exportExtra())
            var files = 0
            val out = ctx.contentResolver.openOutputStream(uri) ?: error("Не удалось открыть файл")
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("data.json"))
                zip.write(json.encodeToString(BackupData.serializer(), data).toByteArray())
                zip.closeEntry()
                DIRS.forEach { dir ->
                    File(ctx.filesDir, dir).listFiles()?.filter { it.isFile }?.forEach { f ->
                        zip.putNextEntry(ZipEntry("$dir/${f.name}"))
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                        files++
                    }
                }
            }
            files
        }
    }

    suspend fun import(ctx: Context, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            var data: BackupData? = null
            val input = ctx.contentResolver.openInputStream(uri) ?: error("Не удалось открыть файл")
            ZipInputStream(input).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    when {
                        e.name == "data.json" -> data = json.decodeFromString(BackupData.serializer(), zip.readBytes().decodeToString())
                        !e.isDirectory && SAFE_ENTRY.matches(e.name) -> {
                            val target = File(ctx.filesDir, e.name)
                            target.parentFile?.mkdirs()
                            target.outputStream().use { zip.copyTo(it) }
                        }
                    }
                    e = zip.nextEntry
                }
            }
            val d = data ?: error("В архиве нет data.json — это не копия DASEIN")
            fun fix(p: String): String {
                if (p.isBlank() || p.startsWith("asset:")) return p
                val i = p.indexOf("/files/")
                return if (i >= 0) File(ctx.filesDir, p.substring(i + 7)).absolutePath else p
            }
            Graph.db.importData(
                d.copy(
                    progressPhotos = d.progressPhotos.map { it.copy(path = fix(it.path)) },
                    wardrobeItems = d.wardrobeItems.map { it.copy(src = fix(it.src)) },
                    outfits = d.outfits.map { it.copy(preview = fix(it.preview)) },
                )
            )
            d.extra?.let { x -> Graph.extraDb.importExtra(x.copy(recipes = x.recipes.map { it.copy(photo = fix(it.photo)) })) }
            Alarms.rescheduleAll(ctx)
            Widgets.refresh(ctx)
        }
    }
}
