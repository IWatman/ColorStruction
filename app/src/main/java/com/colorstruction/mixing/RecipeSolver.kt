package com.colorstruction.mixing

import com.colorstruction.color.DeltaE
import com.colorstruction.color.Lab
import com.colorstruction.color.Rgb

/**
 * Finds mixing recipes that reproduce a target color from a palette.
 *
 * The search runs in two stages. Combinations of one to three paints are searched exhaustively
 * and each one gets its weights optimized with [WeightOptimizer]. If the component cap allows
 * more paints and the best exhaustive result is still far from the target (ΔE ≥ 2), a beam
 * search extends the best combinations one paint at a time, warm-starting every child from its
 * parent's weights.
 *
 * In [PaletteType.WATERCOLOR] mode a virtual water component (paper white) is an extra optional
 * component of every combination and never counts towards the component cap. Opaque palettes
 * never contain water.
 *
 * The solver is stateless apart from the cached paint spectra, so one instance can serve many
 * targets; [solve] is safe to call from a background thread.
 *
 * @param paints the palette; an empty list makes [solve] return an empty list.
 * @param paletteType decides whether water may be used to lighten a mixture.
 */
class RecipeSolver(
    val paints: List<SolverPaint>,
    val paletteType: PaletteType,
) {
    private val palette = PaletteSpectra(paints, paletteType)

    /**
     * Searches for the best recipes for [target].
     *
     * @param target the color to reproduce.
     * @param maxComponents cap on the number of paints; clamped to 1..5 (water excluded).
     * @param resultCount how many recipes to return at most.
     * @param isCancelled polled between combinations; when it returns `true` the search stops and
     *   the best recipes found so far are ranked and returned, without throwing.
     * @return recipes sorted best first, with distinct paint sets and no recipe whose paints are
     *   a strict superset of an earlier one; empty when the palette is empty.
     */
    fun solve(
        target: Rgb,
        maxComponents: Int = 3,
        resultCount: Int = 5,
        isCancelled: () -> Boolean = { false },
    ): List<Recipe> {
        if (paints.isEmpty() || resultCount <= 0) return emptyList()
        val paintCap = maxComponents.coerceIn(1, MAX_COMPONENTS)
        val targetLab = target.toLab()
        val exhaustiveMax = minOf(EXHAUSTIVE_SIZE, paintCap)
        val perSize = Array(paintCap + 1) { ArrayList<Candidate>() }

        var cancelled = false
        for (size in 1..exhaustiveMax) {
            forEachCombination(paints.size, size) { combo ->
                if (isCancelled()) {
                    cancelled = true
                    false
                } else {
                    perSize[size].add(optimize(combo, targetLab, null))
                    true
                }
            }
            if (cancelled) break
        }

        if (!cancelled && paintCap > EXHAUSTIVE_SIZE) {
            beamSearch(perSize, exhaustiveMax, paintCap, targetLab, isCancelled)
        }

        return rankRecipes(perSize.flatMap { it }, palette, targetLab, resultCount)
    }

    /**
     * Recomputes the result and the color difference of [recipe] with new [weights].
     *
     * Used by the UI after rounding the proportions: the components and their order stay the
     * same, only the shares change. Weights are normalized to sum to 1.
     *
     * @param target the color the recipe is designed for; its Lab is needed for ΔE.
     * @param weights one weight per component, in [Recipe.components] order.
     */
    fun evaluate(target: Rgb, recipe: Recipe, weights: List<Double>): Recipe {
        val components = recipe.components
        require(weights.size == components.size) {
            "Got ${weights.size} weights for ${components.size} components"
        }
        val normalized = normalizeWeights(weights)
        val mix = palette.componentKernel(components)
            .mix(DoubleArray(components.size) { normalized[it] })
        return recipe.copy(
            components = components.mapIndexed { index, component ->
                component.copy(weight = normalized[index])
            },
            result = mix.rgb,
            deltaE = DeltaE.ciede2000(mix.lab, target.toLab()),
        )
    }

    /**
     * Extends the best combinations one paint at a time, keeping the beam width, and records
     * every evaluation in [perSize]. Returns `true` when the caller's cancellation was observed.
     */
    private fun beamSearch(
        perSize: Array<ArrayList<Candidate>>,
        exhaustiveMax: Int,
        paintCap: Int,
        targetLab: Lab,
        isCancelled: () -> Boolean,
    ): Boolean {
        val bestExhaustive = (1..exhaustiveMax).flatMap { perSize[it] }.minOfOrNull { it.deltaE }
        if (bestExhaustive != null && bestExhaustive < BEAM_TRIGGER_DELTA_E) return false

        var frontier = perSize[exhaustiveMax].sortedBy { it.deltaE }.take(BEAM_WIDTH)
        for (size in exhaustiveMax + 1..paintCap) {
            val next = ArrayList<Candidate>()
            for (parent in frontier) {
                for (paint in 0 until paints.size) {
                    if (isCancelled()) return true
                    if (parent.combo.contains(paint)) continue
                    val combo = insertSorted(parent.combo, paint)
                    next.add(optimize(combo, targetLab, warmStart(parent, combo, paint)))
                }
            }
            if (next.isEmpty()) break
            next.sortBy { it.deltaE }
            perSize[size].addAll(next.take(BEAM_WIDTH))
            frontier = next.take(BEAM_WIDTH)
        }
        return false
    }

    /** Optimizes the weights of one paint combination. */
    private fun optimize(combo: IntArray, targetLab: Lab, warmStart: DoubleArray?): Candidate {
        val kernel = palette.paintKernel(combo.toList())
        val weights = WeightOptimizer.optimize(kernel, targetLab, warmStart)
        return Candidate(combo.copyOf(), weights, kernel.deltaE(weights, targetLab))
    }

    /** Parent weights scaled down, the new paint getting its share — a good beam start point. */
    private fun warmStart(parent: Candidate, combo: IntArray, newPaint: Int): DoubleArray {
        val size = combo.size + if (palette.hasWater) 1 else 0
        val newShare = 1.0 / size
        val scale = 1.0 - newShare
        val weights = DoubleArray(size)

        var parentPosition = 0
        for (position in combo.indices) {
            if (combo[position] == newPaint) {
                weights[position] = newShare
                continue
            }
            while (parentPosition < parent.combo.size &&
                parent.combo[parentPosition] != combo[position]
            ) {
                parentPosition++
            }
            if (parentPosition < parent.combo.size) {
                weights[position] = parent.weights[parentPosition] * scale
            }
            parentPosition++
        }
        if (palette.hasWater) weights[combo.size] = parent.weights[parent.combo.size] * scale
        return weights
    }

    /** Inserts [paint] into the ascending [combo], keeping it ascending. */
    private fun insertSorted(combo: IntArray, paint: Int): IntArray {
        val result = IntArray(combo.size + 1)
        var source = 0
        var inserted = false
        for (position in result.indices) {
            result[position] = when {
                source == combo.size -> paint
                !inserted && paint < combo[source] -> {
                    inserted = true
                    paint
                }
                else -> combo[source++]
            }
        }
        return result
    }

    /** Non-negative weights rescaled to sum to 1 (an even split when nothing is positive). */
    private fun normalizeWeights(weights: List<Double>): List<Double> {
        val sanitized = weights.map { if (it.isFinite() && it > 0.0) it else 0.0 }
        val total = sanitized.sum()
        return if (total > 0.0) sanitized.map { it / total } else List(weights.size) { 1.0 / weights.size }
    }

    private companion object {
        /** Hard cap on paints per recipe. */
        const val MAX_COMPONENTS = 5

        /** Combinations up to this size are searched exhaustively. */
        const val EXHAUSTIVE_SIZE = 3

        /** Beam search only runs when the exhaustive search is this far from the target. */
        const val BEAM_TRIGGER_DELTA_E = 2.0

        /** Combinations kept alive per beam level. */
        const val BEAM_WIDTH = 20
    }
}
