package com.dasein.poryadok.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherLogicTest {
    private val sample = """
        {"latitude":55.75,"longitude":37.62,"current":{"time":"2026-09-29T14:15","temperature_2m":12.4,"relative_humidity_2m":71,
        "apparent_temperature":9.8,"is_day":1,"precipitation":0.0,"weather_code":2,"cloud_cover":40,"pressure_msl":1018.2,
        "surface_pressure":996.1,"wind_speed_10m":4.2,"wind_direction_10m":225,"wind_gusts_10m":9.1},
        "hourly":{"time":["2026-09-29T13:00","2026-09-29T14:00","2026-09-29T15:00"],"temperature_2m":[11.9,12.4,12.8],
        "precipitation_probability":[5,10,20],"weather_code":[1,2,3],"is_day":[1,1,1]},
        "daily":{"time":["2026-09-29","2026-09-30"],"weather_code":[2,61],"temperature_2m_max":[14.1,11.0],"temperature_2m_min":[6.2,5.0],
        "sunrise":["2026-09-29T06:35","2026-09-30T06:37"],"sunset":["2026-09-29T18:21","2026-09-30T18:18"],"uv_index_max":[2.5,1.1],
        "precipitation_probability_max":[20,80],"precipitation_sum":[0.0,6.4],"wind_speed_10m_max":[5.1,7.3]}}
    """.trimIndent()

    @Test fun parsesForecast() {
        val w = WeatherLogic.parse(sample, "Москва", 1L)
        assertEquals(12.4, w.now.temp, 0.01)
        assertEquals(747, WeatherLogic.mmHg(w.now.pressureHpa))
        assertEquals("ЮЗ", WeatherLogic.windName(w.now.windDir))
        assertEquals(2, w.hours.size) // час 13:00 уже прошёл
        assertEquals(2, w.days.size)
        assertEquals(80, w.days[1].pop)
        assertEquals("Москва", w.place)
    }

    @Test fun codesAndText() {
        assertEquals(Sky.RAIN, WeatherLogic.sky(63))
        assertEquals(Sky.STORM, WeatherLogic.sky(95))
        assertEquals("Дождь", WeatherLogic.describe(63))
        assertEquals("+12°", WeatherLogic.temp(12.4))
        assertEquals("-3°", WeatherLogic.temp(-3.2))
        assertEquals("С", WeatherLogic.windName(350))
        assertEquals("В", WeatherLogic.windName(90))
        assertTrue(WeatherLogic.advice(WeatherNow(temp = -5.0, feels = -9.0, code = 71), null).contains("холодно"))
    }

    @Test fun parsesPlaces() {
        val p = WeatherLogic.parsePlaces("""{"results":[{"name":"Ереван","latitude":40.18,"longitude":44.51,"admin1":"Ереван","country":"Армения"}]}""")
        assertEquals("Ереван", p.single().name)
        assertTrue(WeatherLogic.parsePlaces("{}").isEmpty())
    }
}
