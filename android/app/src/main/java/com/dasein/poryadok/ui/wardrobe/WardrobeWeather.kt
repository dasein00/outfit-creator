package com.dasein.poryadok.ui.wardrobe

import com.dasein.poryadok.data.WardrobeItem
import com.dasein.poryadok.logic.WeatherDay
import kotlin.math.roundToInt

/** Что надеть по прогнозу дня: подсказка словами и типы вещей для подбора наряда из гардероба. */
data class DressHint(
    val text: String,
    val outer: List<String>,
    val top: List<String>,
    val bottom: List<String>,
    val hat: Boolean,
)

object WardrobeWeather {
    /** «Дневная» температура: ближе к максимуму — днём человек большую часть времени на улице. */
    fun feel(d: WeatherDay) = d.tMax * .65 + d.tMin * .35

    fun hint(d: WeatherDay): DressHint {
        val t = feel(d)
        val wet = d.pop >= 50 || d.precipMm >= 1.0
        val base = when {
            t < -5 -> DressHint("Мороз: парка или тёплое пальто, свитер или худи, плотные брюки и шапка", listOf("parka", "coat"), listOf("sweater", "hoodie", "sweatshirt"), listOf("jeans", "trousers", "joggers"), true)
            t < 5 -> DressHint("Холодно: пальто, парка или куртка, свитер или свитшот, брюки; шапка не помешает", listOf("coat", "parka", "jacket", "trench"), listOf("sweater", "sweatshirt", "hoodie"), listOf("jeans", "trousers", "joggers"), true)
            t < 12 -> DressHint("Прохладно: куртка или пиджак поверх свитшота, худи или рубашки, брюки", listOf("jacket", "trench", "blazer", "coat"), listOf("sweatshirt", "hoodie", "longsleeve", "shirt", "sweater"), listOf("jeans", "trousers", "joggers"), false)
            t < 18 -> DressHint("Свежо: рубашка, рубашка-куртка или свитшот, лёгкая куртка с собой, брюки", listOf("jacket", "blazer"), listOf("shirt", "overshirt", "sweatshirt", "longsleeve"), listOf("jeans", "trousers", "joggers"), false)
            t < 24 -> DressHint("Тепло: рубашка, поло или футболка, лёгкие брюки или джоггеры", emptyList(), listOf("shirt", "polo", "tee"), listOf("jeans", "trousers", "joggers"), false)
            else -> DressHint("Жарко: футболка или поло, шорты, светлые ткани", emptyList(), listOf("tee", "polo"), listOf("shorts"), false)
        }
        val extra = buildList {
            if (wet) add("возьмите зонт, обувь — непромокаемую")
            if (d.windMs >= 10) add("ветрено, до ${d.windMs.roundToInt()} м/с — нужен слой от ветра")
            if (d.uv >= 6) add("сильное солнце — кепка и солнцезащитный крем")
        }
        // Дождь и ветер в тёплый день — всё равно лёгкая куртка.
        val outer = if ((wet || d.windMs >= 10) && base.outer.isEmpty() && t < 26) listOf("jacket") else base.outer
        return base.copy(text = (listOf(base.text) + extra).joinToString("; "), outer = outer)
    }

    /** Случайный наряд из гардероба под подсказку: верхняя одежда (если нужна), верх, низ, обувь и шапка в холод. */
    fun pick(h: DressHint, items: List<WardrobeItem>): List<String> {
        val pool = items.filter { !it.archived }
        fun of(cat: String, kinds: List<String>) = pool.filter { it.category == cat && it.kind() in kinds }.randomOrNull()
            ?: pool.filter { it.category == cat }.takeIf { kinds.isEmpty() }?.randomOrNull()
        return listOfNotNull(
            if (h.outer.isNotEmpty()) of("верхняя одежда", h.outer) else null,
            of("верх", h.top) ?: pool.filter { it.category == "верх" }.randomOrNull(),
            of("низ", h.bottom) ?: pool.filter { it.category == "низ" }.randomOrNull(),
            // В холод и дождь — ботинки, в тепло — кроссовки.
            (if (h.hat || h.text.contains("зонт")) pool.filter { it.category == "обувь" && it.kind() == "boots" }.randomOrNull() else pool.filter { it.category == "обувь" && it.kind() == "sneakers" }.randomOrNull())
                ?: pool.filter { it.category == "обувь" }.randomOrNull(),
            if (h.hat) pool.filter { it.category == "аксессуары" && it.kind() == "hat" }.randomOrNull() else null,
        ).map { it.id }
    }
}
