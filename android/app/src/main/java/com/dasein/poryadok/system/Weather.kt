package com.dasein.poryadok.system

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dasein.poryadok.Graph
import com.dasein.poryadok.logic.Sky
import com.dasein.poryadok.logic.WeatherData
import com.dasein.poryadok.logic.WeatherLogic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.cos
import kotlin.math.sin

/**
 * Погода: прогноз Open-Meteo (бесплатный, без ключа) по координатам телефона или выбранному городу.
 * Последний прогноз хранится в приложении — его показывают «Главное», экран погоды и виджет, в том числе без сети.
 */
object Weather {
    private const val TAG = "Weather"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun sp(ctx: Context) = ctx.getSharedPreferences("weather", Context.MODE_PRIVATE)

    private val _data = MutableStateFlow<WeatherData?>(null)
    /** Последний прогноз; обновляется после каждой загрузки. */
    val data: StateFlow<WeatherData?> get() = _data

    fun cached(ctx: Context): WeatherData? = _data.value ?: runCatching {
        sp(ctx).getString("data", null)?.let { json.decodeFromString(WeatherData.serializer(), it) }
    }.getOrNull()?.also { _data.value = it }

    /** Выбранный вручную город (null — по геолокации). */
    fun manualPlace(ctx: Context): WeatherLogic.Place? = runCatching {
        sp(ctx).getString("place", null)?.let { json.decodeFromString(WeatherLogic.Place.serializer(), it) }
    }.getOrNull()

    fun setManualPlace(ctx: Context, p: WeatherLogic.Place?) {
        sp(ctx).edit().apply { if (p == null) remove("place") else putString("place", json.encodeToString(WeatherLogic.Place.serializer(), p)) }.apply()
    }

    fun hasLocationPermission(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun isStale(d: WeatherData?, minutes: Int = 30) = d == null || System.currentTimeMillis() - d.fetchedAt > minutes * 60_000L

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "DASEIN/1.0 (Android)")
        try {
            if (c.responseCode !in 200..299) error("Сервер погоды ответил ${c.responseCode}")
            return c.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            c.disconnect()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun location(ctx: Context): Location? {
        if (!hasLocationPermission(ctx)) return null
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        val all = runCatching { lm.getProviders(true) }.getOrDefault(emptyList())
        val last = (all + LocationManager.PASSIVE_PROVIDER).distinct()
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 3 * 3600_000L) return last
        // Свежие координаты: слушаем все включённые источники (GPS, сеть, fused) и берём первый ответ.
        val active = all.filter { it != LocationManager.PASSIVE_PROVIDER }
        if (active.isEmpty()) return last
        val fresh = withTimeoutOrNull(20_000) {
            suspendCancellableCoroutine<Location?> { cont ->
                val listener = object : android.location.LocationListener {
                    override fun onLocationChanged(l: Location) {
                        runCatching { lm.removeUpdates(this) }
                        if (cont.isActive) cont.resume(l)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                active.forEach { p -> runCatching { lm.requestLocationUpdates(p, 0L, 0f, listener, android.os.Looper.getMainLooper()) } }
                cont.invokeOnCancellation { runCatching { lm.removeUpdates(listener) } }
            }
        }
        return fresh ?: last
    }

    private fun placeName(ctx: Context, lat: Double, lon: Double): String = runCatching {
        @Suppress("DEPRECATION")
        Geocoder(ctx, Locale("ru")).getFromLocation(lat, lon, 1)?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
    }.getOrNull() ?: "Моё местоположение"

    /** Загрузить свежий прогноз. Возвращает текст ошибки или null при успехе. */
    suspend fun refresh(ctx: Context): String? = withContext(Dispatchers.IO) {
        try {
            val manual = manualPlace(ctx)
            val (lat, lon, name) = if (manual != null) Triple(manual.lat, manual.lon, manual.name) else {
                val loc = location(ctx)
                    ?: cached(ctx)?.takeIf { it.lat != 0.0 || it.lon != 0.0 }?.let { Location("cache").apply { latitude = it.lat; longitude = it.lon } }
                    ?: return@withContext if (hasLocationPermission(ctx)) "Не удалось определить местоположение — включите геолокацию или выберите город"
                    else "Разрешите доступ к местоположению или выберите город"
                Triple(loc.latitude, loc.longitude, placeName(ctx, loc.latitude, loc.longitude))
            }
            val text = get(WeatherLogic.forecastUrl(lat, lon))
            val d = WeatherLogic.parse(text, name, System.currentTimeMillis(), LocalDateTime.now().toString().take(16))
            sp(ctx).edit().putString("data", json.encodeToString(WeatherData.serializer(), d)).apply()
            _data.value = d
            Widgets.refresh(ctx)
            null
        } catch (e: Exception) {
            Log.w(TAG, "refresh", e)
            "Нет связи с сервером погоды"
        }
    }

    suspend fun searchPlaces(query: String): List<WeatherLogic.Place> = withContext(Dispatchers.IO) {
        if (query.isBlank()) emptyList() else runCatching { WeatherLogic.parsePlaces(get(WeatherLogic.geocodeUrl(query))) }.getOrDefault(emptyList())
    }

    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<WeatherWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("weather", ExistingPeriodicWorkPolicy.KEEP, req)
    }
}

class WeatherWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Graph.init(applicationContext)
        return if (Weather.refresh(applicationContext) == null) Result.success() else Result.retry()
    }
}

/**
 * Иконки погоды в стиле иконок DASEIN: кремовые облака с мягкой нижней тенью, оранжевое солнце,
 * кремовая луна со звёздами, оранжевые капли, кремовые снежинки, тёплый туман. Плоская заливка, без обводок.
 * На экране перекрашиваются под цвет оформления, как остальные иконки.
 */
object WeatherIcons {
    private const val ORANGE = 0xFFFF9527.toInt()
    private const val ORANGE_LIGHT = 0xFFFFB65C.toInt()
    private const val CREAM = 0xFFFFF1D6.toInt()
    private const val CREAM_SHADE = 0xFFF2DDB8.toInt()
    private const val TAN = 0xFFD69A55.toInt()
    private const val TAN_SHADE = 0xFFB97F42.toInt()
    private const val BROWN = 0xFF5A4636.toInt()

    fun render(code: Int, isDay: Boolean, size: Int = 128): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val s = size / 100f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        fun fill(col: Int) { p.style = Paint.Style.FILL; p.color = col; p.xfermode = null }

        fun sun(cx: Float, cy: Float, r: Float) {
            fill(ORANGE)
            // Лучи — скруглённые капсулы.
            for (k in 0 until 8) {
                val a = Math.PI / 4 * k
                val x1 = cx + cos(a).toFloat() * r * 1.38f; val y1 = cy + sin(a).toFloat() * r * 1.38f
                val x2 = cx + cos(a).toFloat() * r * 1.78f; val y2 = cy + sin(a).toFloat() * r * 1.78f
                p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.26f; p.strokeCap = Paint.Cap.ROUND
                c.drawLine(x1, y1, x2, y2, p)
            }
            fill(ORANGE); c.drawCircle(cx, cy, r, p)
            fill(ORANGE_LIGHT); c.drawCircle(cx - r * 0.18f, cy - r * 0.18f, r * 0.62f, p)
        }
        fun star(x: Float, y: Float, r: Float) {
            c.drawPath(Path().apply {
                moveTo(x, y - r); quadTo(x, y, x + r, y); quadTo(x, y, x, y + r); quadTo(x, y, x - r, y); quadTo(x, y, x, y - r); close()
            }, p)
        }
        fun moon(cx: Float, cy: Float, r: Float) {
            val layer = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val lc = Canvas(layer)
            val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CREAM }
            lc.drawCircle(cx, cy, r, lp)
            lp.color = CREAM_SHADE; lc.drawCircle(cx + r * 0.12f, cy + r * 0.12f, r * 0.8f, lp)
            lp.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
            lc.drawCircle(cx + r * 0.62f, cy - r * 0.38f, r * 0.9f, lp)
            c.drawBitmap(layer, 0f, 0f, null)
            fill(ORANGE)
            star(cx + r * 1.15f, cy + r * 0.35f, r * 0.22f)
            star(cx + r * 0.65f, cy + r * 1.05f, r * 0.15f)
        }
        fun cloud(x: Float, y: Float, w: Float, top: Int, shade: Int) {
            val h = w * 0.5f
            // Тень снизу — тем же силуэтом, чуть смещённым.
            fun shape(dy: Float, col: Int) {
                fill(col)
                c.drawRoundRect(RectF(x, y + h * 0.42f + dy, x + w, y + h + dy), h * 0.3f, h * 0.3f, p)
                c.drawCircle(x + w * 0.3f, y + h * 0.5f + dy, h * 0.33f, p)
                c.drawCircle(x + w * 0.58f, y + h * 0.36f + dy, h * 0.46f, p)
                c.drawCircle(x + w * 0.8f, y + h * 0.6f + dy, h * 0.3f, p)
            }
            shape(h * 0.1f, shade)
            shape(0f, top)
        }
        fun drop(x: Float, y: Float, k: Float, col: Int) {
            fill(col)
            c.drawPath(Path().apply {
                moveTo(x, y - 7 * k * s); cubicTo(x - 1 * k * s, y - 3 * k * s, x - 5 * k * s, y + 1 * k * s, x - 5 * k * s, y + 4 * k * s)
                cubicTo(x - 5 * k * s, y + 8 * k * s, x + 5 * k * s, y + 8 * k * s, x + 5 * k * s, y + 4 * k * s)
                cubicTo(x + 5 * k * s, y + 1 * k * s, x + 1 * k * s, y - 3 * k * s, x, y - 7 * k * s); close()
            }, p)
        }
        fun flake(x: Float, y: Float, r: Float) {
            p.style = Paint.Style.STROKE; p.color = CREAM; p.strokeWidth = r * 0.34f; p.strokeCap = Paint.Cap.ROUND; p.xfermode = null
            for (k in 0 until 3) {
                val a = Math.PI / 3 * k
                c.drawLine(x - cos(a).toFloat() * r, y - sin(a).toFloat() * r, x + cos(a).toFloat() * r, y + sin(a).toFloat() * r, p)
            }
            fill(ORANGE); c.drawCircle(x, y, r * 0.28f, p)
        }
        fun bolt(x: Float, y: Float) {
            fill(ORANGE)
            c.drawPath(Path().apply {
                moveTo(x + 4 * s, y); lineTo(x - 8 * s, y + 22 * s); lineTo(x + 1 * s, y + 22 * s); lineTo(x - 5 * s, y + 40 * s)
                lineTo(x + 14 * s, y + 14 * s); lineTo(x + 5 * s, y + 14 * s); lineTo(x + 11 * s, y); close()
            }, p)
            fill(ORANGE_LIGHT)
            c.drawPath(Path().apply { moveTo(x + 4 * s, y + 2 * s); lineTo(x - 4 * s, y + 18 * s); lineTo(x + 1 * s, y + 18 * s); close() }, p)
        }
        val heavy = code in setOf(55, 65, 67, 75, 82, 86, 99)
        val freezing = code in setOf(56, 57, 66, 67)
        val light = code in setOf(51, 61, 71, 80, 85)
        when (WeatherLogic.sky(code)) {
            Sky.CLEAR -> if (isDay) {
                if (code == 1) { sun(46 * s, 46 * s, 20 * s); cloud(48 * s, 58 * s, 40 * s, CREAM, CREAM_SHADE) } else sun(50 * s, 50 * s, 22 * s)
            } else {
                moon(46 * s, 50 * s, 24 * s)
                if (code == 1) cloud(48 * s, 60 * s, 40 * s, CREAM, CREAM_SHADE)
            }
            Sky.PARTLY -> {
                if (isDay) sun(36 * s, 36 * s, 15 * s) else moon(36 * s, 36 * s, 16 * s)
                cloud(22 * s, 40 * s, 68 * s, CREAM, CREAM_SHADE)
            }
            Sky.CLOUDY -> { cloud(8 * s, 18 * s, 56 * s, TAN, TAN_SHADE); cloud(26 * s, 36 * s, 66 * s, CREAM, CREAM_SHADE) }
            Sky.FOG -> {
                cloud(16 * s, 10 * s, 68 * s, CREAM, CREAM_SHADE)
                p.style = Paint.Style.STROKE; p.strokeWidth = 6 * s; p.strokeCap = Paint.Cap.ROUND; p.xfermode = null
                p.color = TAN; c.drawLine(16 * s, 64 * s, 70 * s, 64 * s, p); c.drawLine(78 * s, 64 * s, 84 * s, 64 * s, p)
                p.color = CREAM_SHADE; c.drawLine(24 * s, 76 * s, 84 * s, 76 * s, p)
                p.color = TAN; c.drawLine(16 * s, 88 * s, 34 * s, 88 * s, p); c.drawLine(42 * s, 88 * s, 76 * s, 88 * s, p)
            }
            Sky.DRIZZLE -> {
                cloud(14 * s, 12 * s, 72 * s, CREAM, CREAM_SHADE)
                listOf(30f to 72f, 50f to 80f, 70f to 72f, 40f to 90f, 60f to 90f).forEach { (x, y) -> drop(x * s, y * s, 0.55f, if (freezing) CREAM else ORANGE) }
            }
            Sky.RAIN -> {
                if (code in 80..82 && isDay) sun(70 * s, 22 * s, 13 * s)
                cloud(12 * s, 14 * s, 74 * s, if (heavy) TAN else CREAM, if (heavy) TAN_SHADE else CREAM_SHADE)
                val xs = if (heavy) listOf(26f, 42f, 58f, 74f) else if (light) listOf(38f, 62f) else listOf(30f, 50f, 70f)
                xs.forEachIndexed { i, x -> drop(x * s, (76 + (i % 2) * 10) * s, if (heavy) 0.95f else 0.85f, if (freezing) CREAM else ORANGE) }
                if (freezing) flake(50 * s, 92 * s, 5 * s)
            }
            Sky.SNOW -> {
                cloud(12 * s, 12 * s, 74 * s, CREAM, CREAM_SHADE)
                val pts = if (heavy) listOf(26f to 74f, 46f to 82f, 66f to 74f, 36f to 93f, 58f to 93f) else if (light) listOf(38f to 80f, 62f to 84f) else listOf(30f to 76f, 52f to 86f, 72f to 76f)
                pts.forEach { (x, y) -> flake(x * s, y * s, 7 * s) }
            }
            Sky.STORM -> {
                cloud(12 * s, 10 * s, 76 * s, TAN, TAN_SHADE)
                bolt(46 * s, 54 * s)
                if (code != 95) listOf(26f, 74f).forEach { x -> fill(CREAM); c.drawCircle(x * s, 80 * s, 4 * s, p) }
                else listOf(26f, 74f).forEach { x -> drop(x * s, 78 * s, 0.7f, ORANGE) }
            }
        }
        return b
    }
}
