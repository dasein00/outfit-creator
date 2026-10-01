package com.dasein.poryadok.logic

/**
 * «Из холодильника»: подбор блюд по продуктам, которые есть дома.
 *
 *  - основной ингредиент — блюдо обязательно должно его содержать (если выбрано несколько — все);
 *  - «есть» — продукты в холодильнике: чем больше их в блюде, тем выше оно в списке;
 *  - «нет» — продуктов точно нет: блюда с ними отсеиваются;
 *  - «только из того, что есть» — показывать лишь блюда, для которых всё уже дома.
 *
 * Соль, вода, сахар, масло для жарки и сухие специи считаются всегда имеющимися —
 * иначе почти любое блюдо «не получится» из-за щепотки перца.
 */
object Fridge {
    data class Spec(
        val main: Set<String> = emptySet(),
        val have: Set<String> = emptySet(),
        val missing: Set<String> = emptySet(),
        val onlyHave: Boolean = false,
    ) {
        val isEmpty get() = main.isEmpty() && have.isEmpty() && missing.isEmpty()
        val count get() = main.size + have.size + missing.size
    }

    /** Насколько блюдо подходит: чего не хватает и сколько продуктов из холодильника в нём. */
    data class Fit(val ok: Boolean, val lacking: List<String>, val used: Int)

    private val STAPLE_KEYS = listOf(
        "соль", "вода", "сахар", "перец черный", "перец чёрный", "перец молот", "масло растительное", "масло подсолнечное",
        "масло оливковое", "лавров", "паприка", "куркума", "зира", "кориандр молот", "специ", "приправ", "сода", "уксус",
        "корица", "мускатн", "орегано", "базилик сушен", "прованск", "хмели-сунели", "чеснок сушен", "ванилин",
    )

    fun norm(s: String): String = s.trim().lowercase().replace('ё', 'е')

    /** Продукты, которые есть почти в каждой кухне. */
    fun isStaple(name: String): Boolean {
        val n = norm(name)
        return STAPLE_KEYS.any { n.startsWith(norm(it)) || n == norm(it) }
    }

    fun fit(ingredients: List<String>, spec: Spec): Fit {
        val names = ingredients.map(::norm).distinct()
        val main = spec.main.map(::norm).toSet()
        val have = spec.have.map(::norm).toSet()
        val missing = spec.missing.map(::norm).toSet()
        val hasMain = main.all { it in names }
        val blocked = names.any { it in missing }
        val lacking = names.filter { it !in have && it !in main && !isStaple(it) }
        val used = names.count { it in have || it in main }
        val ok = hasMain && !blocked && (!spec.onlyHave || lacking.isEmpty()) && (main.isEmpty() && have.isEmpty() || used > 0)
        return Fit(ok, ingredients.filter { norm(it) in lacking }.distinct(), used)
    }

    /** Порядок выдачи: сначала то, для чего всё есть, затем по числу недостающего и по числу своих продуктов. */
    fun rank(fit: Fit): Int = fit.lacking.size * 100 - fit.used

    private const val SEP = "\u001F"

    fun encode(spec: Spec): String = listOf(
        spec.main.joinToString(SEP), spec.have.joinToString(SEP), spec.missing.joinToString(SEP), if (spec.onlyHave) "1" else "0",
    ).joinToString("\n")

    fun decode(raw: String): Spec {
        if (raw.isBlank()) return Spec()
        val p = raw.split("\n")
        fun set(i: Int) = p.getOrNull(i).orEmpty().split(SEP).filter { it.isNotBlank() }.toSet()
        return Spec(set(0), set(1), set(2), p.getOrNull(3) == "1")
    }
}
