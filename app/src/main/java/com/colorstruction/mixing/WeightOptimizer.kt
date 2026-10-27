package com.colorstruction.mixing

import com.colorstruction.color.Lab

/**
 * Minimizes CIEDE2000 between a spectral mix and a target color over the weight simplex
 * (`w ≥ 0`, `Σw = 1`).
 *
 * Two phases, both cheap enough for thousands of combinations:
 *  1. a coarse grid over the simplex — step 0.1 for up to three components, 0.25 above that
 *     (or a caller supplied warm start taken from the parent combination);
 *  2. pairwise weight transfer: move δ from one component to another and keep the move when
 *     the color difference improves, with δ halving from 0.05 down to 0.002.
 */
internal object WeightOptimizer {

    /** Coarse grid step for combinations of up to [LARGE_COMBINATION] components. */
    private const val SMALL_GRID_STEP = 0.1

    /** Coarse grid step for bigger combinations, where the simplex grows too fast. */
    private const val LARGE_GRID_STEP = 0.25

    /** Component count above which the coarse grid switches to [LARGE_GRID_STEP]. */
    private const val LARGE_COMBINATION = 4

    /** First, largest transfer step. */
    private const val INITIAL_STEP = 0.05

    /** Smallest transfer step; the refinement stops once the step would go below it. */
    private const val MIN_STEP = 0.002

    /** Upper bound on refinement sweeps per step size, so a pathological case still terminates. */
    private const val MAX_SWEEPS = 24

    /** Improvement smaller than this is treated as noise, which prevents endless micro-moves. */
    private const val IMPROVEMENT_EPSILON = 1e-9

    /**
     * Finds weights minimizing CIEDE2000 between `kernel`'s mix and [targetLab].
     *
     * @param kernel components to mix; the returned array has [MixKernel.componentCount] entries.
     * @param targetLab the color to reach.
     * @param warmStart optional starting weights (for example the parent combination's best
     *   weights with the new component folded in); the coarse grid is skipped when given and
     *   usable. Weights need not sum to 1 and must have one entry per component.
     * @return non-negative weights summing to 1.0.
     */
    fun optimize(kernel: MixKernel, targetLab: Lab, warmStart: DoubleArray? = null): DoubleArray {
        val count = kernel.componentCount
        if (count <= 0) return DoubleArray(0)
        if (count == 1) return DoubleArray(1) { 1.0 }

        val start = normalize(warmStart, count)
            ?: coarseGrid(kernel, count, targetLab)

        var weights = start
        var step = INITIAL_STEP
        while (step >= MIN_STEP) {
            weights = refine(kernel, targetLab, weights, step)
            step /= 2.0
        }
        return normalize(weights, count) ?: weights
    }

    /** Best point of the coarse simplex grid. */
    private fun coarseGrid(kernel: MixKernel, count: Int, targetLab: Lab): DoubleArray {
        val step = if (count < LARGE_COMBINATION) SMALL_GRID_STEP else LARGE_GRID_STEP
        val units = Math.round(1.0 / step).toInt()
        val candidate = DoubleArray(count)
        val best = DoubleArray(count)
        var bestDeltaE = Double.MAX_VALUE

        fun enumerate(index: Int, remaining: Int) {
            if (index == count - 1) {
                candidate[index] = remaining.toDouble() / units
                val deltaE = kernel.deltaE(candidate, targetLab)
                if (deltaE < bestDeltaE) {
                    bestDeltaE = deltaE
                    candidate.copyInto(best)
                }
                return
            }
            for (unit in 0..remaining) {
                candidate[index] = unit.toDouble() / units
                enumerate(index + 1, remaining - unit)
            }
        }

        enumerate(0, units)
        return best
    }

    /**
     * Repeatedly moves [step] of weight from one component to another while that improves the
     * color difference, until a full sweep finds nothing to improve.
     */
    private fun refine(
        kernel: MixKernel,
        targetLab: Lab,
        start: DoubleArray,
        step: Double,
    ): DoubleArray {
        val count = start.size
        val current = start.copyOf()
        var currentDeltaE = kernel.deltaE(current, targetLab)
        val candidate = DoubleArray(count)

        var sweep = 0
        var improved = true
        while (improved && sweep < MAX_SWEEPS) {
            improved = false
            sweep++
            for (from in 0 until count) {
                for (to in 0 until count) {
                    if (from == to) continue
                    // A tolerance keeps floating point residue from blocking a legal move.
                    if (current[from] < step - 1e-12) continue
                    current.copyInto(candidate)
                    candidate[from] -= step
                    candidate[to] += step
                    val deltaE = kernel.deltaE(candidate, targetLab)
                    if (deltaE < currentDeltaE - IMPROVEMENT_EPSILON) {
                        currentDeltaE = deltaE
                        candidate.copyInto(current)
                        improved = true
                    }
                }
            }
        }
        return current
    }

    /** Copies [weights] into a normalized array, or returns `null` if they are unusable. */
    private fun normalize(weights: DoubleArray?, count: Int): DoubleArray? {
        if (weights == null || weights.size != count) return null
        val sanitized = DoubleArray(count) { index ->
            val value = weights[index]
            if (value.isFinite() && value > 0.0) value else 0.0
        }
        val total = sanitized.sum()
        if (total <= 0.0) return null
        for (index in sanitized.indices) sanitized[index] /= total
        return sanitized
    }
}
