package com.dasein.poryadok.logic

import java.text.NumberFormat
import kotlin.math.abs

object Money {
    private val nf = NumberFormat.getNumberInstance(RU).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 0
    }

    fun format(v: Double, currency: String = "₽"): String = nf.format(v).replace(' ', ' ') + " " + currency
    fun signed(v: Double, income: Boolean, currency: String = "₽"): String = (if (income) "+" else "−") + format(abs(v), currency)

    fun parse(s: String): Double? = s.replace(" ", "").replace(' '.toString(), "").replace(',', '.').toDoubleOrNull()

    enum class BudgetLevel { OK, NEAR, OVER }

    fun budgetLevel(spent: Double, limit: Double): BudgetLevel = when {
        limit <= 0 -> BudgetLevel.OK
        spent > limit -> BudgetLevel.OVER
        spent >= limit * 0.8 -> BudgetLevel.NEAR
        else -> BudgetLevel.OK
    }

    /** Сколько можно тратить в день до конца месяца, чтобы уложиться в лимит. */
    fun dailyAllowance(limit: Double, spent: Double, daysLeftInclToday: Int): Double =
        if (daysLeftInclToday <= 0) 0.0 else ((limit - spent) / daysLeftInclToday).coerceAtLeast(0.0)
}
