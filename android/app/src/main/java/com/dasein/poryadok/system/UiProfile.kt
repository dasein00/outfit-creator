package com.dasein.poryadok.system

import android.content.Context
import android.net.Uri
import android.util.Log
import com.dasein.poryadok.Graph
import com.dasein.poryadok.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Оформление и настройки интерфейса — без записей и без личных ключей. */
@Serializable
data class UiProfileData(
    val format: Int = 1,
    val createdAt: Long = 0,
    val app: String = "",
    val settings: Settings? = null,
    val widget: Map<String, String> = emptyMap(),
    val ui: Map<String, String> = emptyMap(),
    val weather: Map<String, String> = emptyMap(),
)

/**
 * Файл оформления (zip): как расставлены плашки на главной, названия разделов и кнопок, свои иконки и замены
 * встроенных, тема, цвет, вид виджета и программные настройки. Записей, PIN и ключей ИИ/сервисов в нём нет.
 *
 * Если такой файл положить в приложение (assets/defaults/profile.zip), при первой установке на новом телефоне
 * он применится сам — всё будет готово сразу.
 */
object UiProfile {
    const val BUNDLED = "defaults/profile.zip"
    private const val APPLIED_KEY = "profile_bundled_applied"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    /** Папки с иконками: свои картинки, наборы, замены, правки встроенных, порядок. */
    private val DIRS = listOf("icons", "iconsets")
    /** Служебные отметки этого телефона, которые не переносятся. */
    private val UI_SKIP = setOf("backup_last", APPLIED_KEY, "text_editor_mode", com.dasein.poryadok.data.VacationStore.KEY)
    private val SAFE = Regex("^(icons|iconsets)(/[\\p{L}\\p{N}_. ()+\\-]+){1,4}$")

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

    private fun mapToShared(ctx: Context, name: String, m: Map<String, String>, keep: Set<String> = emptySet()) {
        val sp = ctx.getSharedPreferences(name, Context.MODE_PRIVATE)
        val e = sp.edit()
        sp.all.keys.filter { it !in keep }.forEach { e.remove(it) }
        m.forEach { (k, v) ->
            val body = v.drop(2)
            when (v.take(2)) {
                "s:" -> e.putString(k, fixPaths(ctx, body))
                "b:" -> e.putBoolean(k, body.toBoolean())
                "i:" -> body.toIntOrNull()?.let { e.putInt(k, it) }
                "l:" -> body.toLongOrNull()?.let { e.putLong(k, it) }
                "f:" -> body.toFloatOrNull()?.let { e.putFloat(k, it) }
            }
        }
        e.commit()
    }

    /** Пути к своим картинкам с другого телефона → папки этого приложения. */
    fun fixPaths(ctx: Context, text: String): String {
        val base = ctx.filesDir.absolutePath
        return Regex("(?<![\\w])/[^\"\\s,|]*?/files/(icons|iconsets)/").replace(text) { m -> "$base/${m.groupValues[1]}/" }
    }

    /** Только «оформление» из общих настроек: без PIN, ключей, счётчиков и служебных отметок. */
    private fun designOnly(s: Settings) = Settings(
        theme = s.theme, accent = s.accent, name = s.name, currency = s.currency,
        focusWork = s.focusWork, focusBreak = s.focusBreak, focusLong = s.focusLong,
        summaryHour = s.summaryHour, summaryOn = s.summaryOn, eveningReviewOn = s.eveningReviewOn,
        walkPace = s.walkPace, walkPaceAuto = s.walkPaceAuto, talkSpheres = s.talkSpheres,
    )

    private fun applySettings(cur: Settings, p: Settings) = cur.copy(
        theme = p.theme, accent = p.accent, name = p.name.ifBlank { cur.name }, currency = p.currency,
        focusWork = p.focusWork, focusBreak = p.focusBreak, focusLong = p.focusLong,
        summaryHour = p.summaryHour, summaryOn = p.summaryOn, eveningReviewOn = p.eveningReviewOn,
        walkPace = p.walkPace, walkPaceAuto = p.walkPaceAuto, talkSpheres = p.talkSpheres,
    )

    /** Это файл оформления (а не полная копия): внутри profile.json и нет manifest.json. */
    fun isProfile(ctx: Context, uri: Uri): Boolean = runCatching {
        var profile = false; var manifest = false
        ctx.contentResolver.openInputStream(uri)?.use { ins ->
            ZipInputStream(ins.buffered()).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (e.name == "profile.json") profile = true
                    if (e.name == "manifest.json") manifest = true
                }
            }
        }
        profile && !manifest
    }.getOrDefault(false)

    /** Сохраняет файл оформления. Возвращает число файлов-иконок внутри. */
    suspend fun export(ctx: Context, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val data = UiProfileData(
                createdAt = System.currentTimeMillis(),
                app = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName.orEmpty() }.getOrDefault(""),
                settings = designOnly(Graph.prefs.now()),
                widget = sharedToMap(ctx, "widget"),
                ui = sharedToMap(ctx, "ui_state").filterKeys { it !in UI_SKIP },
                weather = sharedToMap(ctx, "weather").filterKeys { it == "place" },
            )
            var files = 0
            val out = ctx.contentResolver.openOutputStream(uri) ?: error("Не удалось открыть файл")
            ZipOutputStream(out.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("profile.json"))
                zip.write(json.encodeToString(UiProfileData.serializer(), data).toByteArray())
                zip.closeEntry()
                DIRS.forEach { dir ->
                    val root = File(ctx.filesDir, dir)
                    root.walkTopDown().maxDepth(4).filter { it.isFile }.forEach { f ->
                        val name = "$dir/" + f.relativeTo(root).invariantSeparatorsPath
                        if (SAFE.matches(name)) {
                            zip.putNextEntry(ZipEntry(name))
                            f.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                            files++
                        }
                    }
                }
            }
            files
        }
    }

    /** Загружает файл оформления, выбранный пользователем. */
    suspend fun import(ctx: Context, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val input = ctx.contentResolver.openInputStream(uri) ?: error("Не удалось открыть файл")
            val (data, files) = read(ctx, input)
            Graph.prefs.update { cur -> data.settings?.let { applySettings(cur, it) } ?: cur }
            com.dasein.poryadok.ui.theme.Look.reload(ctx)
            com.dasein.poryadok.ui.training.Muscles.reload()
            Widgets.refresh(ctx)
            files
        }
    }

    /** Разбирает архив: раскладывает иконки и применяет настройки экранов. Общие настройки возвращает вызывающему. */
    private fun read(ctx: Context, input: InputStream): Pair<UiProfileData, Int> {
        var data: UiProfileData? = null
        var files = 0
        val fresh = mutableSetOf<String>()
        ZipInputStream(input.buffered()).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                when {
                    e.name == "profile.json" -> data = json.decodeFromString(UiProfileData.serializer(), zip.readBytes().decodeToString())
                    !e.isDirectory && SAFE.matches(e.name) && e.name.split('/').none { it == ".." } -> {
                        val dir = e.name.substringBefore('/')
                        if (fresh.add(dir)) File(ctx.filesDir, dir).deleteRecursively()
                        val target = File(ctx.filesDir, e.name)
                        if (target.canonicalPath.startsWith(ctx.filesDir.canonicalPath)) {
                            target.parentFile?.mkdirs()
                            if (target.extension == "json") target.writeText(fixPaths(ctx, zip.readBytes().decodeToString()))
                            else target.outputStream().use { zip.copyTo(it) }
                            files++
                        }
                    }
                }
                e = zip.nextEntry
            }
        }
        val d = data ?: error("Это не файл оформления DASEIN (нет profile.json)")
        if (d.widget.isNotEmpty()) mapToShared(ctx, "widget", d.widget)
        // Служебные отметки этого телефона (когда была копия) сохраняем.
        if (d.ui.isNotEmpty()) mapToShared(ctx, "ui_state", d.ui, keep = UI_SKIP)
        if (d.weather.isNotEmpty()) ctx.getSharedPreferences("weather", Context.MODE_PRIVATE).edit().apply {
            d.weather.forEach { (k, v) -> if (v.startsWith("s:")) putString(k, v.drop(2)) }
        }.commit()
        return d to files
    }

    /**
     * Встроенный в приложение файл оформления: применяется один раз при первой установке,
     * пока на телефоне ещё ничего не настроено. Вызывается при запуске до первого экрана.
     */
    fun applyBundled(ctx: Context) {
        runCatching {
            val ui = ctx.getSharedPreferences("ui_state", Context.MODE_PRIVATE)
            if (ui.getBoolean(APPLIED_KEY, false)) return
            val names = runCatching { ctx.assets.list("defaults")?.toList() }.getOrNull().orEmpty()
            if ("profile.zip" !in names) return
            val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            // Только на «чистой» установке: обновление не трогает уже настроенное приложение.
            val freshInstall = info.firstInstallTime == info.lastUpdateTime || ui.all.isEmpty()
            if (freshInstall) {
                val (data, _) = ctx.assets.open(BUNDLED).use { read(ctx, it) }
                data.settings?.let { s -> runBlocking { Graph.prefs.update { cur -> applySettings(cur, s) } } }
            }
            ctx.getSharedPreferences("ui_state", Context.MODE_PRIVATE).edit().putBoolean(APPLIED_KEY, true).commit()
        }.onFailure { Log.w("DASEIN", "bundled profile", it) }
    }
}
