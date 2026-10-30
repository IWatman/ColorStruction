package com.colorstruction.mixing

import com.colorstruction.color.DeltaE
import com.colorstruction.color.Lab
import com.colorstruction.color.Rgb
import com.colorstruction.color.SpectralColor
import kotlin.math.round

/**
 * One paint to add; [paint] is null only for water. [amount] = parts per 1 part of the existing
 * mix (0.05 = 5 % of the batch).
 */
data class CorrectionAddition(val paint: SolverPaint?, val isWater: Boolean, val amount: Double)

/**
 * One suggestion: 1 or 2 additions sorted by amount descending, the predicted color and its
 * CIEDE2000 to the target.
 */
data class Correction(val additions: List<CorrectionAddition>, val result: Rgb, val deltaE: Double)

/** [deltaEBefore] = CIEDE2000(current, target); [corrections] best first. */
data class CorrectionOutcome(val deltaEBefore: Double, val corrections: List<Correction>)

/** Stable key of the water component; paint ids are positive, so this never collides. */
private const val WATER_KEY: Long = Long.MIN_VALUE

/** A possible addition: a paint, or the virtual water component of a watercolor palette. */
private class CorrectionCandidate(val paint: SolverPaint?, val spectrum: SpectralColor) {
    val key: Long = paint?.id ?: WATER_KEY
    val isWater: Boolean get() = paint == null
    val tintingStrength: Double = paint?.tintingStrength ?: PaletteSpectra.WATER_TINTING_STRENGTH
}

/** A tuned mix: the batch plus [candidates] at [amounts], and how well it hits the target. */
private class CorrectionMix(
    val candidates: List<CorrectionCandidate>,
    val amounts: List<Double>,
    val deltaE: Double,
    val result: Rgb,
) {
    /** Smaller is better: ΔE, and at equal ΔE one addition fewer. */
    val score: Double get() = deltaE + (candidates.size - 1)
}

/**
 * Suggests which paints to add to an existing batch so it moves towards a target color.
 *
 * The batch is one component (its current color, neutral tinting, weight 1.0) and every addition
 * is another component with a weight equal to its amount, so the prediction matches what the
 * spectral model does when the painter actually stirs the paint in. Water is only a candidate in
 * watercolor palettes, where it lightens without tinting.
 */
object MixCorrection {

    /** Largest amount, in parts per 1 part of the batch, that a suggestion may contain. */
    const val MAX_AMOUNT = 1.0

    /** Smallest amount a suggestion may contain. */
    const val MIN_AMOUNT = 0.005

    /** A suggestion is only worth showing when it improves ΔE by at least this much. */
    const val MIN_IMPROVEMENT = 1.0

    private const val BATCH_WEIGHT = 1.0
    private const val BATCH_TINTING = 1.0

    /** A pair is dropped when one of its additions is worth less than this much ΔE on its own. */
    private const val PAIR_MARGIN = 0.3

    private const val FINE_LIMIT = 0.02
    private const val MEDIUM_LIMIT = 0.10
    private const val FINE_STEP = 0.005
    private const val MEDIUM_STEP = 0.01
    private const val COARSE_STEP = 0.05

    /**
     * Suggests up to [resultCount] corrections for the batch [current] towards [target].
     *
     * @param paints the paints the painter has on the palette.
     * @param paletteType decides whether water may be used as a lightener.
     * @param maxAdditions how many additions per suggestion, coerced to 1..2.
     * @return the current color difference and the suggestions, best first.
     */
    fun correct(
        current: Rgb,
        target: Rgb,
        paints: List<SolverPaint>,
        paletteType: PaletteType,
        maxAdditions: Int = 2,
        resultCount: Int = 3,
    ): CorrectionOutcome {
        val targetLab = target.toLab()
        val deltaEBefore = DeltaE.ciede2000(current.toLab(), targetLab)
        val candidates = candidatesOf(paints, paletteType)
        if (candidates.isEmpty() || resultCount <= 0) return CorrectionOutcome(deltaEBefore, emptyList())

        val batch = SpectralColor.of(current)
        val mixes = ArrayList<CorrectionMix>()
        for (candidate in candidates) mixes += bestSingle(batch, candidate, targetLab)
        if (maxAdditions.coerceIn(1, 2) >= 2) {
            for (i in candidates.indices) {
                for (j in i + 1 until candidates.size) {
                    mixes += bestPair(batch, candidates[i], candidates[j], targetLab)
                }
            }
        }

        val kept = mixes
            .map { roundAmounts(batch, it, targetLab) }
            .filter { it.candidates.size == 1 || keepsBoth(batch, it, targetLab) }
            .filter { it.deltaE <= deltaEBefore - MIN_IMPROVEMENT }
            .sortedBy { it.score }

        return CorrectionOutcome(deltaEBefore, keepUndominated(kept).take(resultCount).map(::toCorrection))
    }

    /**
     * Rounds [amount] to a readable step: < 0.02 to a multiple of 0.005, < 0.10 to a multiple of
     * 0.01, otherwise to a multiple of 0.05; the result is coerced to [MIN_AMOUNT], [MAX_AMOUNT].
     */
    fun roundAmount(amount: Double): Double {
        if (!amount.isFinite()) return MIN_AMOUNT
        val step = when {
            amount < FINE_LIMIT -> FINE_STEP
            amount < MEDIUM_LIMIT -> MEDIUM_STEP
            else -> COARSE_STEP
        }
        return (round(amount / step) * step).coerceIn(MIN_AMOUNT, MAX_AMOUNT)
    }

    /** The paints followed (in watercolor palettes) by water. */
    private fun candidatesOf(paints: List<SolverPaint>, paletteType: PaletteType): List<CorrectionCandidate> {
        val result = ArrayList<CorrectionCandidate>(paints.size + 1)
        for (paint in paints) result += CorrectionCandidate(paint, SpectralColor.of(paint.color))
        if (paletteType == PaletteType.WATERCOLOR) {
            result += CorrectionCandidate(null, SpectralColor.of(PaletteSpectra.WATER_COLOR))
        }
        return result
    }

    /** Kernel over the batch (index 0) and [candidates]. */
    private fun kernelFor(batch: SpectralColor, candidates: List<CorrectionCandidate>): MixKernel = MixKernel(
        colors = Array(candidates.size + 1) { if (it == 0) batch else candidates[it - 1].spectrum },
        tintingStrengths = DoubleArray(candidates.size + 1) {
            if (it == 0) BATCH_TINTING else candidates[it - 1].tintingStrength
        },
    )

    /** Best amount of one paint added to the batch. */
    private fun bestSingle(batch: SpectralColor, candidate: CorrectionCandidate, targetLab: Lab): CorrectionMix {
        val kernel = kernelFor(batch, listOf(candidate))
        val weights = DoubleArray(2)
        weights[0] = BATCH_WEIGHT
        val amount = minimize1d { value ->
            weights[1] = value
            kernel.deltaE(weights, targetLab)
        }
        return mixOf(kernel, listOf(candidate), listOf(amount), targetLab)
    }

    /** Best amounts of two paints added to the batch. */
    private fun bestPair(batch: SpectralColor, first: CorrectionCandidate, second: CorrectionCandidate, targetLab: Lab): CorrectionMix {
        val candidates = listOf(first, second)
        val kernel = kernelFor(batch, candidates)
        val weights = DoubleArray(3)
        weights[0] = BATCH_WEIGHT
        val (firstAmount, secondAmount) = minimize2d { a, b ->
            weights[1] = a
            weights[2] = b
            kernel.deltaE(weights, targetLab)
        }
        return mixOf(kernel, candidates, listOf(firstAmount, secondAmount), targetLab)
    }

    /** Mixes [amounts] of [candidates] into the batch with [kernel]. */
    private fun mixOf(
        kernel: MixKernel,
        candidates: List<CorrectionCandidate>,
        amounts: List<Double>,
        targetLab: Lab,
    ): CorrectionMix {
        val weights = DoubleArray(candidates.size + 1)
        weights[0] = BATCH_WEIGHT
        for (index in amounts.indices) weights[index + 1] = amounts[index]
        val mixed = kernel.mix(weights)
        return CorrectionMix(candidates, amounts, DeltaE.ciede2000(mixed.lab, targetLab), mixed.rgb)
    }

    /** Mixes [amounts] of [candidates] into [batch] through a fresh kernel. */
    private fun evaluate(
        batch: SpectralColor,
        candidates: List<CorrectionCandidate>,
        amounts: List<Double>,
        targetLab: Lab,
    ): CorrectionMix = mixOf(kernelFor(batch, candidates), candidates, amounts, targetLab)

    /** Rounds the amounts of [mix] (shrinking a too-big pair) and re-evaluates it. */
    private fun roundAmounts(batch: SpectralColor, mix: CorrectionMix, targetLab: Lab): CorrectionMix {
        var amounts = mix.amounts.map(::roundAmount)
        val total = amounts.sum()
        if (amounts.size > 1 && total > MAX_AMOUNT) {
            val scale = MAX_AMOUNT / total
            amounts = amounts.map { roundAmount(it * scale) }
        }
        return evaluate(batch, mix.candidates, amounts, targetLab)
    }

    /** False when dropping one addition of the pair costs less than [PAIR_MARGIN] ΔE. */
    private fun keepsBoth(batch: SpectralColor, mix: CorrectionMix, targetLab: Lab): Boolean =
        (0..1).all { dropped ->
            val other = 1 - dropped
            val alone = evaluate(batch, listOf(mix.candidates[other]), listOf(mix.amounts[other]), targetLab)
            alone.deltaE - mix.deltaE >= PAIR_MARGIN
        }

    /** Drops pairs whose paint is already suggested on its own with a better score. */
    private fun keepUndominated(sorted: List<CorrectionMix>): List<CorrectionMix> {
        val result = ArrayList<CorrectionMix>(sorted.size)
        for (mix in sorted) {
            val covered = mix.candidates.size > 1 && mix.candidates.any { candidate ->
                result.any { chosen ->
                    chosen.candidates.size == 1 && chosen.candidates[0].key == candidate.key &&
                        chosen.score <= mix.score
                }
            }
            if (!covered) result += mix
        }
        return result
    }

    /** Converts an internal [CorrectionMix] into a public [Correction], additions largest first. */
    private fun toCorrection(mix: CorrectionMix): Correction = Correction(
        additions = mix.candidates.indices
            .map { index ->
                CorrectionAddition(
                    paint = mix.candidates[index].paint,
                    isWater = mix.candidates[index].isWater,
                    amount = mix.amounts[index],
                )
            }
            .sortedByDescending { it.amount },
        result = mix.result,
        deltaE = mix.deltaE,
    )
}
