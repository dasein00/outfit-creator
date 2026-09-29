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
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= 31) add("fused")
            add(LocationManager.NETWORK_PROVIDER); add(LocationManager.GPS_PROVIDER); add(LocationManager.PASSIVE_PROVIDER)
        }.filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) || it == LocationManager.PASSIVE_PROVIDER }
        val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 3 * 3600_000L) return last
        if (Build.VERSION.SDK_INT >= 30) {
            val p = providers.firstOrNull { it != LocationManager.PASSIVE_PROVIDER } ?: return last
            val fresh = withTimeoutOrNull(12_000) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val signal = android.os.CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    runCatching { lm.getCurrentLocation(p, signal, ctx.mainExecutor) { if (cont.isActive) cont.resume(it) } }.onFailure { if (cont.isActive) cont.resume(null) }
                }
            }
            return fresh ?: last
        }
        return last
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

/** Иконки погоды, нарисованные в стиле приложения: солнце, облака, дождь, снег, гроза, туман. */
object WeatherIcons {
    private const val SUN = 0xFFE8A53C.toInt()
    private const val MOON = 0xFFE9DFC6.toInt()
    private const val CLOUD = 0xFFE7E1D6.toInt()
    private const val CLOUD_DARK = 0xFFA79E90.toInt()
    private const val RAIN = 0xFF5B8DD2.toInt()
    private const val SNOW = 0xFFFFFFFF.toInt()
    private const val BOLT = 0xFFF2C94C.toInt()

    fun render(code: Int, isDay: Boolean, size: Int = 128): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val s = size / 100f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        fun sun(cx: Float, cy: Float, r: Float) {
            if (isDay) {
                p.color = SUN; p.style = Paint.Style.STROKE; p.strokeWidth = 5 * s; p.strokeCap = Paint.Cap.ROUND
                for (k in 0 until 8) {
                    val a = Math.PI / 4 * k
                    c.drawLine(cx + cos(a).toFloat() * r * 1.35f, cy + sin(a).toFloat() * r * 1.35f, cx + cos(a).toFloat() * r * 1.75f, cy + sin(a).toFloat() * r * 1.75f, p)
                }
                p.style = Paint.Style.FILL
                c.drawCircle(cx, cy, r, p)
            } else {
                p.style = Paint.Style.FILL; p.color = MOON
                c.drawCircle(cx, cy, r * 1.1f, p)
                p.color = 0x00000000; p.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
                c.drawCircle(cx + r * 0.55f, cy - r * 0.35f, r * 0.95f, p)
                p.xfermode = null
            }
        }
        fun cloud(x: Float, y: Float, w: Float, color: Int) {
            p.style = Paint.Style.FILL; p.color = color
            val h = w * 0.42f
            c.drawRoundRect(RectF(x, y + h * 0.35f, x + w, y + h), h * 0.35f, h * 0.35f, p)
            c.drawCircle(x + w * 0.33f, y + h * 0.42f, h * 0.38f, p)
            c.drawCircle(x + w * 0.62f, y + h * 0.3f, h * 0.5f, p)
        }
        fun drops(color: Int, snow: Boolean) {
            p.color = color; p.style = Paint.Style.FILL
            for (k in 0 until 3) {
                val x = (30 + k * 20) * s
                val y = (74 + (k % 2) * 8) * s
                if (snow) c.drawCircle(x, y, 4.5f * s, p)
                else {
                    val path = Path().apply { moveTo(x, y - 7 * s); lineTo(x - 4 * s, y + 2 * s); quadTo(x, y + 8 * s, x + 4 * s, y + 2 * s); close() }
                    c.drawPath(path, p)
                }
            }
        }
        when (WeatherLogic.sky(code)) {
            Sky.CLEAR -> sun(50 * s, 50 * s, 20 * s)
            Sky.PARTLY -> { sun(36 * s, 36 * s, 15 * s); cloud(26 * s, 40 * s, 64 * s, CLOUD) }
            Sky.CLOUDY -> { cloud(10 * s, 22 * s, 56 * s, CLOUD_DARK); cloud(26 * s, 38 * s, 64 * s, CLOUD) }
            Sky.FOG -> {
                cloud(18 * s, 16 * s, 64 * s, CLOUD)
                p.color = CLOUD_DARK; p.style = Paint.Style.STROKE; p.strokeWidth = 5 * s; p.strokeCap = Paint.Cap.ROUND
                for (k in 0 until 3) c.drawLine((18 + k * 4) * s, (62 + k * 11) * s, (82 - k * 4) * s, (62 + k * 11) * s, p)
            }
            Sky.DRIZZLE, Sky.RAIN -> { cloud(16 * s, 18 * s, 68 * s, CLOUD); drops(RAIN, false) }
            Sky.SNOW -> { cloud(16 * s, 18 * s, 68 * s, CLOUD); drops(SNOW, true) }
            Sky.STORM -> {
                cloud(16 * s, 14 * s, 68 * s, CLOUD_DARK)
                p.color = BOLT; p.style = Paint.Style.FILL
                c.drawPath(Path().apply { moveTo(52 * s, 50 * s); lineTo(40 * s, 74 * s); lineTo(50 * s, 74 * s); lineTo(44 * s, 94 * s); lineTo(62 * s, 66 * s); lineTo(52 * s, 66 * s); lineTo(58 * s, 50 * s); close() }, p)
            }
        }
        return b
    }
}
