package com.dasein.poryadok.logic

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.math.roundToInt

@Serializable
data class WeatherNow(
    val temp: Double = 0.0,
    val feels: Double = 0.0,
    val humidity: Int = 0,
    /** Давление на уровне станции, гПа. */
    val pressureHpa: Double = 0.0,
    val windMs: Double = 0.0,
    val windDir: Int = 0,
    val gustMs: Double = 0.0,
    val code: Int = 0,
    val isDay: Boolean = true,
    val precipMm: Double = 0.0,
    val cloud: Int = 0,
)

@Serializable
data class WeatherHour(val time: String, val temp: Double, val pop: Int, val code: Int, val isDay: Boolean = true)

@Serializable
data class WeatherDay(
    val date: String,
    val code: Int,
    val tMax: Double,
    val tMin: Double,
    val sunrise: String = "",
    val sunset: String = "",
    val uv: Double = 0.0,
    val pop: Int = 0,
    val precipMm: Double = 0.0,
    val windMs: Double = 0.0,
)

/** Прогноз с Open-Meteo, сохранённый в приложении: текущая погода, по часам и по дням. */
@Serializable
data class WeatherData(
    val place: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val fetchedAt: Long = 0,
    val now: WeatherNow = WeatherNow(),
    val hours: List<WeatherHour> = emptyList(),
    val days: List<WeatherDay> = emptyList(),
)

/** Вид погоды — для иконки. */
enum class Sky { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM }

object WeatherLogic {
    private val json = Json { ignoreUnknownKeys = true }

    /** Коды погоды ВМО (WMO 4677, как их отдаёт Open-Meteo). */
    fun describe(code: Int): String = when (code) {
        0 -> "Ясно"
        1 -> "Преимущественно ясно"
        2 -> "Переменная облачность"
        3 -> "Пасмурно"
        45 -> "Туман"
        48 -> "Туман с изморозью"
        51 -> "Слабая морось"
        53 -> "Морось"
        55 -> "Сильная морось"
        56, 57 -> "Ледяная морось"
        61 -> "Небольшой дождь"
        63 -> "Дождь"
        65 -> "Сильный дождь"
        66, 67 -> "Ледяной дождь"
        71 -> "Небольшой снег"
        73 -> "Снег"
        75 -> "Сильный снег"
        77 -> "Снежная крупа"
        80 -> "Небольшой ливень"
        81 -> "Ливень"
        82 -> "Сильный ливень"
        85 -> "Снегопад"
        86 -> "Сильный снегопад"
        95 -> "Гроза"
        96, 99 -> "Гроза с градом"
        else -> "—"
    }

    fun sky(code: Int): Sky = when (code) {
        0, 1 -> Sky.CLEAR
        2 -> Sky.PARTLY
        3 -> Sky.CLOUDY
        45, 48 -> Sky.FOG
        51, 53, 55, 56, 57 -> Sky.DRIZZLE
        61, 63, 65, 66, 67, 80, 81, 82 -> Sky.RAIN
        71, 73, 75, 77, 85, 86 -> Sky.SNOW
        95, 96, 99 -> Sky.STORM
        else -> Sky.CLOUDY
    }

    /** Гектопаскали → миллиметры ртутного столба. */
    fun mmHg(hPa: Double): Int = (hPa * 0.750062).roundToInt()

    /** Откуда дует ветер: «С», «СВ», … */
    fun windName(deg: Int): String = listOf("С", "СВ", "В", "ЮВ", "Ю", "ЮЗ", "З", "СЗ")[(((deg % 360) + 360) % 360 + 22) / 45 % 8]

    fun windFull(deg: Int): String = listOf("северный", "северо-восточный", "восточный", "юго-восточный", "южный", "юго-западный", "западный", "северо-западный")[(((deg % 360) + 360) % 360 + 22) / 45 % 8]

    fun temp(t: Double): String = t.roundToInt().let { if (it > 0) "+$it°" else "$it°" }

    /** Совет на день по погоде. */
    fun advice(now: WeatherNow, today: WeatherDay?): String {
        val parts = mutableListOf<String>()
        val pop = today?.pop ?: 0
        when {
            sky(now.code) == Sky.STORM -> parts += "Гроза — лучше переждать в помещении"
            pop >= 60 || sky(now.code) == Sky.RAIN -> parts += "Возьмите зонт"
            sky(now.code) == Sky.SNOW -> parts += "Снег — обувь с хорошей подошвой"
        }
        when {
            now.feels <= -15 -> parts += "очень холодно, одевайтесь многослойно"
            now.feels <= 0 -> parts += "холодно — шапка и перчатки"
            now.feels >= 28 -> parts += "жарко — пейте больше воды"
        }
        if (now.gustMs >= 15) parts += "сильный ветер, порывы до ${now.gustMs.roundToInt()} м/с"
        if ((today?.uv ?: 0.0) >= 6) parts += "высокий УФ-индекс — нужен солнцезащитный крем"
        return parts.joinToString(", ").replaceFirstChar { it.uppercase() }
    }

    private fun JsonObject.d(k: String) = (this[k] as? JsonPrimitive)?.doubleOrNull ?: 0.0
    private fun JsonObject.i(k: String) = (this[k] as? JsonPrimitive)?.let { it.intOrNull ?: it.doubleOrNull?.roundToInt() } ?: 0
    private fun JsonObject.arr(k: String): JsonArray = (this[k] as? JsonArray) ?: JsonArray(emptyList())
    private fun JsonArray.d(i: Int) = (getOrNull(i) as? JsonPrimitive)?.doubleOrNull ?: 0.0
    private fun JsonArray.i(i: Int) = (getOrNull(i) as? JsonPrimitive)?.let { it.intOrNull ?: it.doubleOrNull?.roundToInt() } ?: 0
    private fun JsonArray.s(i: Int) = (getOrNull(i) as? JsonPrimitive)?.content ?: ""

    /** Разбор ответа Open-Meteo /v1/forecast. [nowIso] — текущее местное время «2026-09-29T14:00» для отбора часов. */
    fun parse(text: String, place: String, fetchedAt: Long, nowIso: String? = null): WeatherData {
        val root = json.parseToJsonElement(text).jsonObject
        val c = root["current"]?.jsonObject ?: JsonObject(emptyMap())
        val now = WeatherNow(
            temp = c.d("temperature_2m"), feels = c.d("apparent_temperature"), humidity = c.i("relative_humidity_2m"),
            pressureHpa = c.d("surface_pressure").takeIf { it > 0 } ?: c.d("pressure_msl"),
            windMs = c.d("wind_speed_10m"), windDir = c.i("wind_direction_10m"), gustMs = c.d("wind_gusts_10m"),
            code = c.i("weather_code"), isDay = c.i("is_day") == 1, precipMm = c.d("precipitation"), cloud = c.i("cloud_cover"),
        )
        val h = root["hourly"]?.jsonObject ?: JsonObject(emptyMap())
        val ht = h.arr("time")
        val start = (c["time"] as? JsonPrimitive)?.content ?: nowIso
        val hours = ht.indices.map { i ->
            WeatherHour(ht.s(i), h.arr("temperature_2m").d(i), h.arr("precipitation_probability").i(i), h.arr("weather_code").i(i), h.arr("is_day").i(i) != 0)
        }.filter { start == null || it.time >= start.take(13) }.take(24)
        val d = root["daily"]?.jsonObject ?: JsonObject(emptyMap())
        val dt = d.arr("time")
        val days = dt.indices.map { i ->
            WeatherDay(
                dt.s(i), d.arr("weather_code").i(i), d.arr("temperature_2m_max").d(i), d.arr("temperature_2m_min").d(i),
                d.arr("sunrise").s(i), d.arr("sunset").s(i), d.arr("uv_index_max").d(i), d.arr("precipitation_probability_max").i(i),
                d.arr("precipitation_sum").d(i), d.arr("wind_speed_10m_max").d(i),
            )
        }
        return WeatherData(place, root.d("latitude"), root.d("longitude"), fetchedAt, now, hours, days)
    }

    fun forecastUrl(lat: Double, lon: Double): String =
        "https://api.open-meteo.com/v1/forecast?latitude=${"%.4f".format(java.util.Locale.US, lat)}&longitude=${"%.4f".format(java.util.Locale.US, lon)}" +
            "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,cloud_cover,pressure_msl,surface_pressure,wind_speed_10m,wind_direction_10m,wind_gusts_10m" +
            "&hourly=temperature_2m,precipitation_probability,weather_code,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max,precipitation_probability_max,precipitation_sum,wind_speed_10m_max" +
            "&timezone=auto&forecast_days=7&wind_speed_unit=ms"

    @Serializable
    data class Place(val name: String, val lat: Double, val lon: Double, val region: String = "", val country: String = "")

    fun geocodeUrl(query: String): String =
        "https://geocoding-api.open-meteo.com/v1/search?count=8&language=ru&format=json&name=" + java.net.URLEncoder.encode(query.trim(), "UTF-8")

    fun parsePlaces(text: String): List<Place> {
        val root = json.parseToJsonElement(text).jsonObject
        return (root["results"] as? JsonArray ?: return emptyList()).mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            Place(
                (o["name"] as? JsonPrimitive)?.content ?: return@mapNotNull null, o.d("latitude"), o.d("longitude"),
                (o["admin1"] as? JsonPrimitive)?.content ?: "", (o["country"] as? JsonPrimitive)?.content ?: "",
            )
        }
    }
}
