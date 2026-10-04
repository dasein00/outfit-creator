package com.dasein.poryadok.logic

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Финансовая аналитика по операциям: итоги месяца, темп трат, прогноз, норма сбережений,
 * правило 50/30/20, подушка безопасности, «здоровье» финансов и советы. Только чистые функции — легко проверить тестами.
 */
object FinanceStats {
    /** Операция в упрощённом виде: день (epochDay), сумма, доход ли, название категории. */
    data class Op(val day: Long, val amount: Double, val income: Boolean, val category: String)

    data class Month(val income: Double, val expense: Double) {
        val net get() = income - expense
        /** Доля дохода, которая осталась: (доход − расход) / доход. */
        val savingsRate get() = if (income > 0) net / income else 0.0
    }

    fun range(ym: YearMonth) = ym.atDay(1).toEpochDay()..ym.atEndOfMonth().toEpochDay()

    fun month(ops: List<Op>, ym: YearMonth): Month {
        val r = range(ym)
        val m = ops.filter { it.day in r }
        return Month(m.filter { it.income }.sumOf { it.amount }, m.filterNot { it.income }.sumOf { it.amount })
    }

    /** Прошло дней месяца (для текущего — по сегодня включительно, для прошлых — весь месяц). */
    fun daysPassed(ym: YearMonth, today: LocalDate): Int = when {
        YearMonth.from(today) == ym -> today.dayOfMonth
        ym.isBefore(YearMonth.from(today)) -> ym.lengthOfMonth()
        else -> 0
    }

    /** Прогноз расходов на конец месяца при нынешнем темпе. */
    fun forecast(spent: Double, passed: Int, days: Int): Double = if (passed <= 0) 0.0 else spent / passed * days

    /**
     * Темп: сколько потрачено к этому числу по сравнению с тем же числом прошлого месяца.
     * +0,15 — на 15 % больше. null — сравнивать не с чем.
     */
    fun pace(ops: List<Op>, ym: YearMonth, today: LocalDate): Double? {
        val day = daysPassed(ym, today).coerceAtLeast(1)
        val prev = ym.minusMonths(1)
        fun upTo(m: YearMonth, d: Int): Double {
            val from = m.atDay(1).toEpochDay()
            val to = m.atDay(d.coerceAtMost(m.lengthOfMonth())).toEpochDay()
            return ops.filter { !it.income && it.day in from..to }.sumOf { it.amount }
        }
        val before = upTo(prev, day)
        if (before <= 0) return null
        return upTo(ym, day) / before - 1
    }

    /** Средний месячный расход и доход за [n] полных месяцев до [ym] (пустые месяцы не считаются). */
    fun averages(ops: List<Op>, ym: YearMonth, n: Int = 3): Month {
        val ms = (1..n).map { month(ops, ym.minusMonths(it.toLong())) }.filter { it.income > 0 || it.expense > 0 }
        if (ms.isEmpty()) return Month(0.0, 0.0)
        return Month(ms.sumOf { it.income } / ms.size, ms.sumOf { it.expense } / ms.size)
    }

    /** Категории расходов месяца и их отклонение от среднего за 3 прошлых месяца. */
    data class CategoryTrend(val category: String, val amount: Double, val average: Double) {
        val change get() = if (average > 0) amount / average - 1 else null
    }

    fun categoryTrends(ops: List<Op>, ym: YearMonth): List<CategoryTrend> {
        val cur = ops.filter { !it.income && it.day in range(ym) }.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
        val prevMonths = (1..3).map { ym.minusMonths(it.toLong()) }.filter { m -> ops.any { it.day in range(m) } }
        val avg = if (prevMonths.isEmpty()) emptyMap() else prevMonths.flatMap { m -> ops.filter { !it.income && it.day in range(m) } }
            .groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } / prevMonths.size }
        return cur.map { (c, v) -> CategoryTrend(c, v, avg[c] ?: 0.0) }.sortedByDescending { it.amount }
    }

    /** Траты по дням недели (1 = понедельник) — средняя сумма за такой день. */
    fun byWeekday(ops: List<Op>, from: Long, to: Long): Map<DayOfWeek, Double> {
        val spent = ops.filter { !it.income && it.day in from..to }.groupBy { LocalDate.ofEpochDay(it.day).dayOfWeek }
            .mapValues { e -> e.value.sumOf { it.amount } }
        val counts = (from..to).groupingBy { LocalDate.ofEpochDay(it).dayOfWeek }.eachCount()
        return DayOfWeek.entries.associateWith { d -> (spent[d] ?: 0.0) / (counts[d] ?: 1).coerceAtLeast(1) }
    }

    // ---------- 50/30/20 ----------

    enum class Need(val title: String, val target: Double) { NEEDS("Обязательное", 0.5), WANTS("Желания", 0.3), SAVE("Сбережения", 0.2) }

    private val NEEDS = listOf("продукт", "жиль", "жкх", "коммун", "транспорт", "здоров", "связь", "интернет", "кредит", "образован", "дет", "налог", "страхов", "аренд", "ипотек")

    /** Обязательная трата или «желание» — по названию категории. */
    fun needOf(category: String): Need = if (NEEDS.any { category.lowercase().contains(it) }) Need.NEEDS else Need.WANTS

    /** Доли дохода: обязательное, желания, сбережения (что осталось). Без дохода — доли от расходов. */
    fun split503020(ops: List<Op>, ym: YearMonth): Map<Need, Double> {
        val m = ops.filter { it.day in range(ym) }
        val income = m.filter { it.income }.sumOf { it.amount }
        val exp = m.filterNot { it.income }
        val needs = exp.filter { needOf(it.category) == Need.NEEDS }.sumOf { it.amount }
        val wants = exp.filter { needOf(it.category) == Need.WANTS }.sumOf { it.amount }
        val base = if (income > 0) income else (needs + wants).coerceAtLeast(1.0)
        return mapOf(Need.NEEDS to needs / base, Need.WANTS to wants / base, Need.SAVE to ((income - needs - wants) / base).coerceAtLeast(0.0))
    }

    /** На сколько месяцев хватит денег на счетах при среднем расходе. */
    fun runwayMonths(balance: Double, avgMonthlyExpense: Double): Double? = if (avgMonthlyExpense <= 0) null else balance / avgMonthlyExpense

    // ---------- Оценка и советы ----------

    data class Health(val score: Int, val parts: List<Pair<String, Int>>)

    /**
     * Оценка 0–100 из четырёх частей по 25: норма сбережений (20 %+ — максимум), подушка (6 мес.+),
     * бюджет (уложились — максимум) и динамика трат (не растут быстрее, чем в среднем).
     */
    fun health(savingsRate: Double, runway: Double?, budgetUse: Double?, pace: Double?): Health {
        val s = (savingsRate / 0.2 * 25).coerceIn(0.0, 25.0)
        val r = ((runway ?: 0.0) / 6 * 25).coerceIn(0.0, 25.0)
        val b = when {
            budgetUse == null -> 15.0
            budgetUse <= 1.0 -> 25.0
            else -> (25 - (budgetUse - 1) * 100).coerceIn(0.0, 25.0)
        }
        val p = when {
            pace == null -> 15.0
            pace <= 0 -> 25.0
            else -> (25 - pace * 50).coerceIn(0.0, 25.0)
        }
        val parts = listOf("Сбережения" to s.roundToInt(), "Подушка" to r.roundToInt(), "Бюджет" to b.roundToInt(), "Темп трат" to p.roundToInt())
        return Health(parts.sumOf { it.second }, parts)
    }

    data class Tip(val title: String, val text: String, val warn: Boolean = false)

    /** Советы по вашим цифрам — от самых важных. */
    fun tips(
        m: Month, avg: Month, runway: Double?, pace: Double?, trends: List<CategoryTrend>, split: Map<Need, Double>,
        hasBudget: Boolean, fmt: (Double) -> String,
    ): List<Tip> {
        val out = mutableListOf<Tip>()
        if (m.income > 0 && m.net < 0) out += Tip("Расходы больше доходов", "В этом месяце траты превысили доход на ${fmt(-m.net)}. Посмотрите самые крупные категории и отложите необязательные покупки до следующего месяца.", true)
        if (pace != null && pace > 0.15) out += Tip("Тратите быстрее обычного", "К этому числу потрачено на ${(pace * 100).roundToInt()} % больше, чем месяц назад. Если так продолжится, месяц закончится с перерасходом.", true)
        trends.filter { (it.change ?: 0.0) > 0.4 && it.amount - it.average > 1000 }.take(2).forEach {
            out += Tip("«${it.category}» выросли", "${fmt(it.amount)} против ${fmt(it.average)} в среднем — на ${((it.change ?: 0.0) * 100).roundToInt()} % больше. Проверьте, разовая ли это покупка.", true)
        }
        when {
            runway == null -> {}
            runway < 1 -> out += Tip("Создайте подушку безопасности", "Денег на счетах меньше, чем на месяц жизни. Цель — 3–6 месячных расходов (≈ ${fmt(avg.expense.coerceAtLeast(m.expense) * 3)}). Начните с 10 % от каждого дохода.", true)
            runway < 3 -> out += Tip("Подушка: ${"%.1f".format(runway)} мес.", "Хорошее начало. Доведите запас до 3–6 месяцев расходов, держите его на отдельном счёте или вкладе с возможностью снятия.")
            runway >= 6 -> out += Tip("Подушка собрана", "Запаса хватит на ${runway.roundToInt()} мес. Сверх 6 месяцев деньги лучше вложить: вклад, ОФЗ, ИИС — так их не съест инфляция.")
        }
        if (m.income > 0) {
            val rate = m.savingsRate
            out += when {
                rate >= 0.2 -> Tip("Откладываете ${(rate * 100).roundToInt()} % дохода", "Это выше рекомендуемых 20 %. Отличная привычка — направьте излишек на цели или вложения.")
                rate > 0 -> Tip("Норма сбережений ${(rate * 100).roundToInt()} %", "Ориентир — 10–20 % дохода. Правило «сначала заплати себе»: в день зарплаты сразу переводите часть на накопительный счёт.")
                else -> Tip("Ничего не откладывается", "Попробуйте автоматический перевод 5–10 % в день дохода — небольшая сумма, которую не замечаешь, за год превращается в заметный запас.")
            }
        }
        val wants = split[Need.WANTS] ?: 0.0
        if (m.income > 0 && wants > 0.35) out += Tip("Много трат «на желания»", "Кафе, развлечения, покупки — ${(wants * 100).roundToInt()} % дохода при ориентире 30 %. Правило 24 часов: перед незапланированной покупкой подождите сутки.")
        if (!hasBudget && m.expense > 0) out += Tip("Задайте бюджет", "С месячным лимитом приложение покажет, сколько можно тратить в день, и предупредит заранее. Возьмите средний расход за 3 месяца и уменьшите на 5–10 %.")
        out += EVERGREEN[(LocalDate.now().dayOfYear) % EVERGREEN.size]
        return out
    }

    /** Общие правила финансовой грамотности — по одному в день. */
    val EVERGREEN = listOf(
        Tip("Правило 50/30/20", "50 % дохода — на обязательное (еда, жильё, транспорт), 30 % — на желания, 20 % — на сбережения и досрочное погашение долгов."),
        Tip("Сначала дорогие долги", "Гасите досрочно прежде всего кредитки и займы с самой высокой ставкой: это гарантированная «доходность», которой не даст ни один вклад."),
        Tip("Инфляция съедает наличные", "При инфляции 8 % в год через 5 лет на ту же сумму можно купить на треть меньше. Свободные деньги держите на вкладе или накопительном счёте."),
        Tip("Мелкие траты складываются", "Кофе за 250 ₽ каждый будний день — это 65 000 ₽ в год. Посмотрите в «Аналитике», на какие мелочи уходит больше всего."),
        Tip("Список перед магазином", "Покупки по списку и не на голодный желудок сокращают траты на продукты на 10–20 %."),
        Tip("Проверяйте подписки", "Раз в квартал пересматривайте регулярные платежи: часть подписок обычно уже не нужна."),
        Tip("Налоговый вычет", "За лечение, обучение, покупку жилья и взносы на ИИС можно вернуть 13 % расходов — до десятков тысяч рублей в год."),
        Tip("Цель с датой", "«Накопить 100 000 ₽ к июню» работает лучше, чем «копить». Калькулятор «Накопить на цель» покажет сумму в месяц."),
        Tip("Сложный процент", "10 000 ₽ в месяц под 12 % годовых за 10 лет превращаются в ≈ 2,3 млн ₽, из них около 1,1 млн — проценты. Время важнее суммы."),
        Tip("Крупная покупка — по правилу 30 дней", "Хотите вещь дороже 10 % месячного дохода — запишите и вернитесь через 30 дней. Часто желание проходит само."),
    )

    // ---------- Калькуляторы ----------

    /** Вклад с ежемесячной капитализацией и пополнением. Возвращает (итог, внесено, проценты). */
    fun deposit(start: Double, monthly: Double, ratePct: Double, months: Int): Triple<Double, Double, Double> {
        val r = ratePct / 100 / 12
        var b = start
        repeat(months.coerceAtLeast(0)) { b = b * (1 + r) + monthly }
        val put = start + monthly * months.coerceAtLeast(0)
        return Triple(b, put, b - put)
    }

    /** Аннуитетный кредит: (ежемесячный платёж, всего выплат, переплата). */
    fun loan(amount: Double, ratePct: Double, months: Int): Triple<Double, Double, Double> {
        if (months <= 0 || amount <= 0) return Triple(0.0, 0.0, 0.0)
        val r = ratePct / 100 / 12
        val pay = if (r == 0.0) amount / months else amount * r / (1 - (1 + r).pow(-months))
        return Triple(pay, pay * months, pay * months - amount)
    }

    /** Сколько откладывать в месяц, чтобы к сроку накопить [target] (с доходностью [ratePct] годовых). */
    fun saveFor(target: Double, have: Double, ratePct: Double, months: Int): Double {
        if (months <= 0) return (target - have).coerceAtLeast(0.0)
        val r = ratePct / 100 / 12
        val grown = have * (1 + r).pow(months)
        val left = (target - grown).coerceAtLeast(0.0)
        return if (r == 0.0) left / months else left * r / ((1 + r).pow(months) - 1)
    }

    /** Покупательная способность суммы через [years] лет при инфляции [inflPct]. */
    fun inflation(amount: Double, inflPct: Double, years: Double): Double = amount / (1 + inflPct / 100).pow(years)

    /** Сколько месяцев гасить долг платежом [payment]. null — платёж не покрывает проценты. */
    fun payoffMonths(debt: Double, ratePct: Double, payment: Double): Int? {
        val r = ratePct / 100 / 12
        if (payment <= debt * r) return null
        var b = debt
        var n = 0
        while (b > 0.005 && n < 1200) { b = b * (1 + r) - payment; n++ }
        return n
    }
}
