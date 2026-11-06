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
        /** Режим «Что приготовить»: любой из продуктов, все обязательно или только из них. */
        val mode: Mode = Mode.ANY,
    ) {
        val isEmpty get() = main.isEmpty() && have.isEmpty() && missing.isEmpty()
        val count get() = main.size + have.size + missing.size
    }

    /**
     * Как подбирать блюда по продуктам «есть дома» (как у SuperCook, Allrecipes, Yummly):
     *  - [ANY] — блюда хотя бы с одним из продуктов; выше те, где их больше;
     *  - [ALL] — в блюде обязательно все выбранные продукты, другие допускаются;
     *  - [ONLY] — блюдо целиком из выбранных продуктов (плюс соль, масло, специи).
     */
    enum class Mode { ANY, ALL, ONLY }

    /** Подбор: [matched] — какие из выбранных продуктов есть в блюде, [lacking] — чего не хватает. */
    data class Pick(val ok: Boolean, val matched: List<String>, val lacking: List<String>, val used: Int)

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

    private fun stem(w: String) = w.take(maxOf(3, minOf(w.length - 1, 4)))

    /** Основы, у которых разные продукты: сливки и сливы, масло и маслины, горох и горчица. */
    private val AMBIGUOUS = setOf("слив", "масл", "горо", "горч", "пере", "сыро", "капу", "карт")
    private fun head(s: String) = norm(s).split(' ', ',', '(', '-').firstOrNull { it.length >= 2 }.orEmpty().filter { it.isLetter() }

    /**
     * Один и тот же продукт под разными названиями: «Курица» — «Куриное филе», «Помидоры» — «Помидор черри»,
     * «Творог» — «Творог 5%», «Яйца» — «Яйцо куриное». Сравниваются целиком и по основе первого слова
     * («Яйцо куриное» с «Курицей» не совпадает).
     */
    fun same(a: String, b: String): Boolean {
        val x = norm(a); val y = norm(b)
        if (x == y || x.startsWith("$y ") || y.startsWith("$x ")) return true
        val ha = head(a); val hb = head(b)
        return ha.length >= 3 && hb.length >= 3 && stem(ha) == stem(hb) && (stem(ha) !in AMBIGUOUS || ha == hb)
    }

    fun fit(ingredients: List<String>, spec: Spec): Fit {
        val names = ingredients.distinctBy(::norm)
        fun inAny(n: String, set: Set<String>) = set.any { same(it, n) }
        val hasMain = spec.main.all { m -> names.any { same(m, it) } }
        val blocked = names.any { inAny(it, spec.missing) }
        val lacking = names.filter { !inAny(it, spec.have) && !inAny(it, spec.main) && !isStaple(it) }
        val used = names.count { inAny(it, spec.have) || inAny(it, spec.main) }
        val ok = hasMain && !blocked && (!spec.onlyHave || lacking.isEmpty()) && (spec.main.isEmpty() && spec.have.isEmpty() || used > 0)
        return Fit(ok, lacking, used)
    }

    /** Подбор для «Что приготовить»: продукты [have], без [exclude], в режиме [mode]. */
    fun pick(ingredients: List<String>, have: Set<String>, exclude: Set<String>, mode: Mode): Pick {
        val names = ingredients.distinctBy(::norm)
        val matched = have.filter { p -> names.any { same(p, it) } }.sorted()
        val covered = names.filter { n -> have.any { same(it, n) } }
        val lacking = names.filter { n -> n !in covered && !isStaple(n) }
        val blocked = names.any { n -> exclude.any { same(it, n) } }
        val ok = !blocked && matched.isNotEmpty() && when (mode) {
            Mode.ANY -> true
            Mode.ALL -> matched.size == have.size
            Mode.ONLY -> lacking.isEmpty()
        }
        return Pick(ok, matched, lacking, covered.size)
    }

    /** Порядок выдачи: сначала то, для чего всё есть, затем по числу недостающего и по числу своих продуктов. */
    fun rank(fit: Fit): Int = fit.lacking.size * 100 - fit.used

    private const val SEP = "\u001F"

    fun encode(spec: Spec): String = listOf(
        spec.main.joinToString(SEP), spec.have.joinToString(SEP), spec.missing.joinToString(SEP), if (spec.onlyHave) "1" else "0", spec.mode.name,
    ).joinToString("\n")

    fun decode(raw: String): Spec {
        if (raw.isBlank()) return Spec()
        val p = raw.split("\n")
        fun set(i: Int) = p.getOrNull(i).orEmpty().split(SEP).filter { it.isNotBlank() }.toSet()
        val mode = Mode.entries.firstOrNull { it.name == p.getOrNull(4) } ?: Mode.ANY
        return Spec(set(0), set(1), set(2), p.getOrNull(3) == "1", mode)
    }
}
