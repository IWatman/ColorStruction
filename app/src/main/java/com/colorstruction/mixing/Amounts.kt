package com.colorstruction.mixing

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.round

/**
 * Splits a total volume of a mix into per-paint amounts in millilitres.
 *
 * Amounts are rounded to a readable step (½ ml for small batches, 1 ml and 5 ml for larger
 * ones) and still add up to the requested total, so the recipe can be measured with a syringe
 * or a small beaker.
 */
object Amounts {

    /** Largest total, in millilitres, that is still measured in ½ ml steps. */
    private const val FINE_MAX_TOTAL = 100.0

    /** Largest total, in millilitres, that is measured in whole millilitres. */
    private const val MEDIUM_MAX_TOTAL = 1000.0

    /** Step used above [MEDIUM_MAX_TOTAL]. */
    private const val COARSE_STEP = 5.0

    private const val EPSILON = 1e-9

    /**
     * Rounding step for a batch of [total] millilitres: ½ ml up to 100 ml, 1 ml up to 1000 ml
     * and 5 ml above that.
     */
    fun amountStep(total: Double): Double = when {
        total <= FINE_MAX_TOTAL -> 0.5
        total <= MEDIUM_MAX_TOTAL -> 1.0
        else -> COARSE_STEP
    }

    /**
     * Splits [total] millilitres between the paints in proportion to [weights].
     *
     * Every non-zero weight gets at least one [step] while the step budget allows it, and the
     * result sums to exactly [total] whenever [total] is a multiple of [step]: the steps are
     * handed out with the largest remainder method. Weights that are not positive get 0; an
     * all-zero or empty weight list splits the total evenly.
     *
     * @param weights component weights; only ratios matter.
     * @param total total volume in millilitres.
     * @param step rounding step in millilitres, normally from [amountStep].
     * @return one amount per weight, in millilitres.
     */
    fun amounts(weights: List<Double>, total: Double, step: Double): List<Double> {
        if (weights.isEmpty()) return emptyList()
        val safeStep = if (step.isFinite() && step > 0.0) step else 1.0
        val units = if (total.isFinite() && total > 0.0) {
            round(total / safeStep).toInt()
        } else {
            0
        }
        if (units <= 0) return List(weights.size) { 0.0 }

        val sanitized = weights.map { if (it.isFinite() && it > 0.0) it else 0.0 }
        val weightSum = sanitized.sum()
        // No usable weights: an even split is the best guess and still sums to the total.
        val effective = if (weightSum > 0.0) sanitized else List(weights.size) { 1.0 }
        val positive = effective.map { it > 0.0 }
        val shareTotal = effective.sum()
        val ideal = effective.map { it / shareTotal * units }
        val floors = ideal.map { floor(it + EPSILON).toInt() }
        val forced = positive.count { it } <= units

        val result = IntArray(weights.size) { index ->
            if (forced && positive[index]) max(1, floors[index]) else floors[index]
        }
        var rest = units - result.sum()

        // Leftover steps go to the largest fractional remainders, ties broken by index.
        val byRemainder = (0 until weights.size).sortedWith(
            compareByDescending<Int> { index -> ideal[index] - floor(ideal[index] + EPSILON) }
                .thenBy { it },
        )
        var cursor = 0
        while (rest > 0 && byRemainder.isNotEmpty()) {
            result[byRemainder[cursor % weights.size]]++
            cursor++
            rest--
        }

        // The minimum rule can overshoot on tiny totals: take steps back from the biggest.
        val bySize = (0 until weights.size).sortedWith(compareByDescending<Int> { result[it] }.thenBy { it })
        while (rest < 0) {
            var shrunk = false
            for (index in bySize) {
                if (rest == 0) break
                val lowerBound = if (forced && positive[index]) 1 else 0
                if (result[index] > lowerBound) {
                    result[index]--
                    rest++
                    shrunk = true
                }
            }
            if (!shrunk) break
        }
        return result.map { it * safeStep }
    }

    /** Formats an amount without a trailing `.0`: `12.5`, `3`, `0.5`. */
    fun formatAmount(value: Double): String {
        if (!value.isFinite()) return "0"
        val tenths = round(value * 10.0).toLong()
        val whole = tenths / 10L
        val fraction = abs(tenths % 10L)
        return if (fraction == 0L) whole.toString() else "$whole.$fraction"
    }
}
