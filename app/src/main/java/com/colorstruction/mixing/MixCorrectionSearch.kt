package com.colorstruction.mixing

import kotlin.math.exp
import kotlin.math.ln

/** Smallest addition the correction search may propose, in parts per 1 part of the batch. */
internal const val SEARCH_MIN = 0.005

/** Largest addition the correction search may propose, in parts per 1 part of the batch. */
internal const val SEARCH_MAX = 1.0

/** Bracket width (in amount, not in log space) at which the golden-section refinement stops. */
private const val SEARCH_TOLERANCE = 1e-4

/** Grid points per axis of the coarse two-dimensional scan. */
private const val GRID_POINTS = 10

/** Alternating refinement rounds of [minimize2d]. */
private const val REFINE_ROUNDS = 3

/** Grid points per axis used by [minimize1d] inside the [minimize2d] refinement. */
private const val REFINE_POINTS = 8

/** 1/phi, the golden-section shrinking factor. */
private const val INV_PHI = 0.6180339887498949

/**
 * Minimizes `cost(a)` for `a` in `[lo, hi]`.
 *
 * A coarse scan over [points] amounts spaced evenly in log space picks the best sample; the
 * golden-section search then refines, in log space, the bracket formed by the neighbours of that
 * sample until the bracket is narrower than [SEARCH_TOLERANCE] in `a`.
 *
 * @return the amount with the lowest cost found, within `[lo, hi]`.
 */
internal fun minimize1d(
    lo: Double = SEARCH_MIN,
    hi: Double = SEARCH_MAX,
    points: Int = 24,
    cost: (Double) -> Double,
): Double {
    val low = lo.coerceIn(SEARCH_MIN, SEARCH_MAX)
    val high = hi.coerceIn(SEARCH_MIN, SEARCH_MAX)
    if (high <= low) return low

    val count = points.coerceAtLeast(2)
    val logLo = ln(low)
    val step = (ln(high) - logLo) / (count - 1)

    var bestIndex = 0
    var bestCost = Double.MAX_VALUE
    for (index in 0 until count) {
        val value = cost(exp(logLo + step * index))
        if (value < bestCost) {
            bestCost = value
            bestIndex = index
        }
    }

    // Bracket: the samples around the best one, in log space.
    var left = logLo + step * (bestIndex - 1).coerceAtLeast(0)
    var right = logLo + step * (bestIndex + 1).coerceAtMost(count - 1)

    var c = right - INV_PHI * (right - left)
    var d = left + INV_PHI * (right - left)
    var costC = cost(exp(c))
    var costD = cost(exp(d))
    while (exp(right) - exp(left) > SEARCH_TOLERANCE) {
        if (costC <= costD) {
            right = d
            d = c
            costD = costC
            c = right - INV_PHI * (right - left)
            costC = cost(exp(c))
        } else {
            left = c
            c = d
            costC = costD
            d = left + INV_PHI * (right - left)
            costD = cost(exp(d))
        }
    }
    return exp(if (costC <= costD) c else d).coerceIn(SEARCH_MIN, SEARCH_MAX)
}

/**
 * Minimizes `cost(a, b)` over `[SEARCH_MIN, SEARCH_MAX]^2`.
 *
 * A [GRID_POINTS] x [GRID_POINTS] log-spaced grid (pairs with `a + b > SEARCH_MAX` are skipped)
 * finds a starting point; [REFINE_ROUNDS] rounds then alternate [minimize1d] on each coordinate
 * while the other is held fixed, with a bracket around the current best value that shrinks by
 * half (in log space) every round.
 *
 * @return the pair with the lowest cost found; scaled down proportionally when it still sums to
 *   more than [SEARCH_MAX].
 */
internal fun minimize2d(cost: (Double, Double) -> Double): Pair<Double, Double> {
    val logLo = ln(SEARCH_MIN)
    val step = (ln(SEARCH_MAX) - logLo) / (GRID_POINTS - 1)

    var bestA = SEARCH_MIN
    var bestB = SEARCH_MIN
    var bestCost = Double.MAX_VALUE
    for (i in 0 until GRID_POINTS) {
        val a = exp(logLo + step * i)
        for (j in 0 until GRID_POINTS) {
            val b = exp(logLo + step * j)
            val value = if (a + b > SEARCH_MAX) Double.POSITIVE_INFINITY else cost(a, b)
            if (value < bestCost) {
                bestCost = value
                bestA = a
                bestB = b
            }
        }
    }

    var halfWidth = step / 2.0
    repeat(REFINE_ROUNDS) {
        val (loA, hiA) = bracket(bestA, halfWidth)
        bestA = minimize1d(loA, hiA, REFINE_POINTS) { a -> cost(a, bestB) }
        val (loB, hiB) = bracket(bestB, halfWidth)
        bestB = minimize1d(loB, hiB, REFINE_POINTS) { b -> cost(bestA, b) }
        halfWidth /= 2.0
    }

    val total = bestA + bestB
    if (total > SEARCH_MAX) {
        val scale = SEARCH_MAX / total
        bestA = (bestA * scale).coerceIn(SEARCH_MIN, SEARCH_MAX)
        bestB = (bestB * scale).coerceIn(SEARCH_MIN, SEARCH_MAX)
    }
    return bestA to bestB
}

/** `[value / 2^halfWidth, value * 2^halfWidth]`, clamped to the search range. */
private fun bracket(value: Double, halfWidth: Double): Pair<Double, Double> {
    val lo = (value * exp(-halfWidth)).coerceIn(SEARCH_MIN, SEARCH_MAX)
    val hi = (value * exp(halfWidth)).coerceIn(SEARCH_MIN, SEARCH_MAX)
    return lo to hi
}
