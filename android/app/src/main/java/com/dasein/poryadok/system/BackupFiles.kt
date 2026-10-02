package com.dasein.poryadok.system

import android.content.Context
import android.net.Uri
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.BackupData
import com.dasein.poryadok.data.BackupSection
import com.dasein.poryadok.data.Settings
import com.dasein.poryadok.data.exportData
import com.dasein.poryadok.data.exportExtra
import com.dasein.poryadok.data.importData
import com.dasein.poryadok.data.importExtra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Что лежит в копии: для экрана загрузки, чтобы показать разделы с числом записей и файлов. */
@Serializable
data class BackupManifest(
    val format: Int = 2,
    val createdAt: Long = 0,
    val app: String = "",
    val sections: List<String> = emptyList(),
    val records: Map<String, Int> = emptyMap(),
    val files: Map<String, Int> = emptyMap(),
    val bytes: Long = 0,
)

/** Настройки приложения, вид виджета и состояние экранов. */
@Serializable
data class BackupPrefs(
    val settings: Settings? = null,
    val widget: Map<String, String> = emptyMap(),
    val ui: Map<String, String> = emptyMap(),
)

/**
 * Резервная копия — zip-архив:
 *  - manifest.json — какие разделы внутри;
 *  - data.json — записи выбранных разделов;
 *  - prefs.json — настройки, виджет, состояние экранов (раздел «Настройки»);
 *  - папки с файлами: фото, видео и GIF заметок, рецептов, гардероба, постеры, свои иконки.
 * Старые копии (только data.json) загружаются как раньше.
 */
object BackupFiles {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    /** Безопасные пути внутри архива: «папка/файл», без «..». */
    private val SAFE_ENTRY = Regex("^[a-z_]+/[A-Za-z0-9_.\\-]+$")
    private val SKIP_DIRS = setOf("datastore", "profileInstalled")
    private val SHARED = listOf("widget", "ui_state")

    /** Папки с файлами пользователя и их файлы. */
    private fun userDirs(ctx: Context): Map<String, List<File>> =
        ctx.filesDir.listFiles().orEmpty().filter { it.isDirectory && it.name !in SKIP_DIRS && !it.name.startsWith(".") }
            .associate { d -> d.name to d.listFiles().orEmpty().filter { it.isFile } }

    /** Сколько записей и файлов в каждом разделе сейчас на телефоне. */
    suspend fun localSummary(ctx: Context): Map<BackupSection, Pair<Int, Int>> = withContext(Dispatchers.IO) {
        val full = Graph.db.exportData(System.currentTimeMillis()).copy(extra = Graph.extraDb.exportExtra())
        val files = userDirs(ctx).entries.groupBy({ BackupSection.forDir(it.key) }, { it.value.size })
        BackupSection.entries.associateWith { it.count(full) to (files[it]?.sum() ?: 0) }
    }

    private fun sharedToMap(ctx: Context, name: String): Map<String, String> =
        ctx.getSharedPreferences(name, Context.MODE_PRIVATE).all.mapNotNull { (k, v) ->
            when (v) {
                is String -> k to "s:$v"
                is Boolean -> k to "b:$v"
                is Int -> k to "i:$v"
                is Long -> k to "l:$v"
                is Float -> k to "f:$v"
                else -> null
            }
        }.toMap()

    private fun mapToShared(ctx: Context, name: String, m: Map<String, String>) {
        val e = ctx.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
        m.forEach { (k, v) ->
            val body = v.drop(2)
            when (v.take(2)) {
                "s:" -> e.putString(k, body)
                "b:" -> e.putBoolean(k, body.toBoolean())
                "i:" -> body.toIntOrNull()?.let { e.putInt(k, it) }
                "l:" -> body.toLongOrNull()?.let { e.putLong(k, it) }
                "f:" -> body.toFloatOrNull()?.let { e.putFloat(k, it) }
            }
        }
        e.apply()
    }

    /** Сохраняет копию выбранных разделов (по умолчанию — всех). Возвращает число файлов. */
    suspend fun export(ctx: Context, uri: Uri, sections: Set<BackupSection> = BackupSection.entries.toSet()): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val now = System.currentTimeMillis()
            val full = Graph.db.exportData(now).copy(extra = Graph.extraDb.exportExtra())
            val data = BackupSection.only(full, sections)
            val dirs = userDirs(ctx).filterKeys { BackupSection.forDir(it) in sections }
            var files = 0
            var bytes = 0L
            val out = ctx.contentResolver.openOutputStream(uri) ?: error("Не удалось открыть файл")
            ZipOutputStream(out.buffered()).use { zip ->
                val manifest = BackupManifest(
                    createdAt = now, app = appVersion(ctx), sections = sections.map { it.name },
                    records = sections.associate { it.name to it.count(data) },
                    files = dirs.entries.groupBy({ BackupSection.forDir(it.key).name }, { it.value.size }).mapValues { it.value.sum() },
                    bytes = dirs.values.flatten().sumOf { it.length() },
                )
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(json.encodeToString(BackupManifest.serializer(), manifest).toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("data.json"))
                zip.write(json.encodeToString(BackupData.serializer(), data).toByteArray())
                zip.closeEntry()
                if (BackupSection.SETTINGS in sections) {
                    val prefs = BackupPrefs(Graph.prefs.now(), sharedToMap(ctx, "widget"), sharedToMap(ctx, "ui_state"))
                    zip.putNextEntry(ZipEntry("prefs.json"))
                    zip.write(json.encodeToString(BackupPrefs.serializer(), prefs).toByteArray())
                    zip.closeEntry()
                }
                dirs.forEach { (dir, list) ->
                    list.forEach { f ->
                        zip.putNextEntry(ZipEntry("$dir/${f.name}"))
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                        files++; bytes += f.length()
                    }
                }
            }
            UiStateBackup.markDone(ctx, now)
            files
        }
    }

    /** Читает только описание копии: какие разделы, сколько записей и файлов. */
    suspend fun inspect(ctx: Context, uri: Uri): Result<BackupManifest> = withContext(Dispatchers.IO) {
        runCatching {
            var manifest: BackupManifest? = null
            var legacy: BackupData? = null
            val files = HashMap<String, Int>()
            var hasPrefs = false
            val input = ctx.contentResolver.openInputStream(uri) ?: error("Не удалось открыть файл")
            ZipInputStream(input.buffered()).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    when {
                        e.name == "manifest.json" -> manifest = json.decodeFromString(BackupManifest.serializer(), zip.readBytes().decodeToString())
                        e.name == "data.json" && manifest == null -> legacy = json.decodeFromString(BackupData.serializer(), zip.readBytes().decodeToString())
                        e.name == "prefs.json" -> hasPrefs = true
                        !e.isDirectory && SAFE_ENTRY.matches(e.name) -> {
                            val s = BackupSection.forDir(e.name.substringBefore('/')).name
                            files[s] = (files[s] ?: 0) + 1
                        }
                    }
                    e = zip.nextEntry
                }
            }
            manifest ?: legacy?.let { d ->
                // Старая копия без описания: разделы определяем по данным.
                val secs = BackupSection.entries.filter { it.count(d) > 0 || (files[it.name] ?: 0) > 0 || (it == BackupSection.SETTINGS && hasPrefs) }
                BackupManifest(1, d.createdAt, "", secs.map { it.name }, secs.associate { it.name to it.count(d) }, files)
            } ?: error("В архиве нет данных — это не копия DASEIN")
        }
    }

    /**
     * Загружает выбранные разделы из копии. Остальные разделы на телефоне остаются как есть.
     * Файлы (фото, видео, GIF) выбранных разделов кладутся в папки приложения, пути в записях исправляются под этот телефон.
     */
    suspend fun import(ctx: Context, uri: Uri, sections: Set<BackupSection>? = null): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            var dataText: String? = null
            var prefs: BackupPrefs? = null
            var files = 0
            fun wanted(s: BackupSection) = sections == null || s in sections
            val input = ctx.contentResolver.openInputStream(uri) ?: error("Не удалось открыть файл")
            ZipInputStream(input.buffered()).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    when {
                        e.name == "data.json" -> dataText = zip.readBytes().decodeToString()
                        e.name == "prefs.json" -> prefs = json.decodeFromString(BackupPrefs.serializer(), zip.readBytes().decodeToString())
                        !e.isDirectory && SAFE_ENTRY.matches(e.name) && wanted(BackupSection.forDir(e.name.substringBefore('/'))) -> {
                            val target = File(ctx.filesDir, e.name)
                            if (target.canonicalPath.startsWith(ctx.filesDir.canonicalPath)) {
                                target.parentFile?.mkdirs()
                                target.outputStream().use { zip.copyTo(it) }
                                files++
                            }
                        }
                    }
                    e = zip.nextEntry
                }
            }
            val text = dataText ?: error("В архиве нет data.json — это не копия DASEIN")
            // Пути к файлам с другого телефона → папки этого приложения.
            val base = ctx.filesDir.absolutePath
            val fixed = Regex("\"(?!asset:)[^\"]*?/files/([a-z_]+)/").replace(text) { m -> "\"$base/${m.groupValues[1]}/" }
            val file = json.decodeFromString(BackupData.serializer(), fixed)
            val chosen = (sections ?: BackupSection.entries.toSet()) - BackupSection.SETTINGS
            if (chosen.isNotEmpty()) {
                val now = System.currentTimeMillis()
                val current = Graph.db.exportData(now).copy(extra = Graph.extraDb.exportExtra())
                val merged = BackupSection.merge(current, file, chosen)
                Graph.db.importData(merged)
                merged.extra?.let { Graph.extraDb.importExtra(it) }
            }
            if (wanted(BackupSection.SETTINGS)) prefs?.let { p ->
                p.settings?.let { s ->
                    // Счётчик шагов датчика — свой у каждого телефона, его не переносим.
                    Graph.prefs.update { cur -> s.copy(sensorLast = cur.sensorLast, sensorDay = cur.sensorDay, sensorSteps = cur.sensorSteps) }
                }
                if (p.widget.isNotEmpty()) mapToShared(ctx, "widget", p.widget)
                if (p.ui.isNotEmpty()) mapToShared(ctx, "ui_state", p.ui)
            }
            Alarms.rescheduleAll(ctx)
            Widgets.refresh(ctx)
            files
        }
    }

    private fun appVersion(ctx: Context): String =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName.orEmpty() }.getOrDefault("")
}

/** Когда в последний раз сохраняли копию. */
object UiStateBackup {
    fun markDone(ctx: Context, at: Long) = ctx.getSharedPreferences("ui_state", Context.MODE_PRIVATE).edit().putLong("backup_last", at).apply()
    fun last(ctx: Context): Long = ctx.getSharedPreferences("ui_state", Context.MODE_PRIVATE).getLong("backup_last", 0L)
}
