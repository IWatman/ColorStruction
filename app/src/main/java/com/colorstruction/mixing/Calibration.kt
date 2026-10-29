package com.colorstruction.mixing

import com.colorstruction.color.DeltaE
import com.colorstruction.color.MixComponent
import com.colorstruction.color.Rgb
import com.colorstruction.color.Spectral
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.round

/**
 * One swatch measurement used to calibrate a paint's tinting strength.
 *
 * @property paint the paint under calibration
 * @property paintParts parts of [paint] in the mixed swatch (must be positive)
 * @property partner the partner paint, usually white
 * @property partnerParts parts of [partner] in the mixed swatch (must be positive)
 * @property partnerStrength tinting strength of [partner], already known
 * @property measured color picked from a photo of the painted swatch
 * @property currentStrength tinting strength currently stored for [paint]
 */
data class CalibrationInput(
    val paint: Rgb,
    val paintParts: Double,
    val partner: Rgb,
    val partnerParts: Double,
    val partnerStrength: Double = 1.0,
    val measured: Rgb,
    val currentStrength: Double,
)

/**
 * Outcome of [Calibration.fit].
 *
 * @property strength fitted tinting strength, rounded to two decimals
 * @property deltaEBefore CIEDE2000 with [CalibrationInput.currentStrength]
 * @property deltaEAfter CIEDE2000 with [strength]
 * @property predictedBefore spectral mix with the current strength
 * @property predictedAfter spectral mix with the fitted strength
 * @property atLimit true when the fit ended on [Calibration.MIN_STRENGTH] or [Calibration.MAX_STRENGTH]
 */
data class CalibrationResult(
    val strength: Double,
    val deltaEBefore: Double,
    val deltaEAfter: Double,
    val predictedBefore: Rgb,
    val predictedAfter: Rgb,
    val atLimit: Boolean,
)

/**
 * Fits a paint's tinting strength so the spectral model reproduces a measured swatch.
 *
 * The swatch is modelled as `paintParts` of [CalibrationInput.paint] with the unknown strength
 * plus `partnerParts` of [CalibrationInput.partner] with a known strength. The strength minimizing
 * the CIEDE2000 difference to the measured color is found by a coarse log-space scan followed by
 * golden-section refinement.
 */
object Calibration {

    /** Lowest tinting strength the fit may return. */
    const val MIN_STRENGTH = 0.1

    /** Highest tinting strength the fit may return. */
    const val MAX_STRENGTH = 10.0

    private const val SCAN_POINTS = 40
    private const val TOLERANCE = 1e-4
    private const val ROUND_FACTOR = 100.0
    private const val INV_PHI = 0.6180339887498949
    private const val LIMIT_EPS = 1e-9

    /**
     * Fits the tinting strength of [CalibrationInput.paint].
     *
     * @throws IllegalArgumentException if a part count or a strength is not positive.
     */
    fun fit(input: CalibrationInput): CalibrationResult {
        require(input.paintParts > 0.0) { "paintParts must be positive, was ${input.paintParts}" }
        require(input.partnerParts > 0.0) { "partnerParts must be positive, was ${input.partnerParts}" }
        require(input.partnerStrength > 0.0) { "partnerStrength must be positive, was ${input.partnerStrength}" }
        require(input.currentStrength > 0.0) { "currentStrength must be positive, was ${input.currentStrength}" }

        val measuredLab = input.measured.toLab()
        fun cost(strength: Double): Double =
            DeltaE.ciede2000(predict(input, strength).toLab(), measuredLab)

        val best = refine(input, ::cost)

        val fitted = (round(best * ROUND_FACTOR) / ROUND_FACTOR).coerceIn(MIN_STRENGTH, MAX_STRENGTH)
        val predictedBefore = predict(input, input.currentStrength)
        val predictedAfter = predict(input, fitted)

        return CalibrationResult(
            strength = fitted,
            deltaEBefore = DeltaE.ciede2000(predictedBefore.toLab(), measuredLab),
            deltaEAfter = DeltaE.ciede2000(predictedAfter.toLab(), measuredLab),
            predictedBefore = predictedBefore,
            predictedAfter = predictedAfter,
            atLimit = fitted <= MIN_STRENGTH + LIMIT_EPS || fitted >= MAX_STRENGTH - LIMIT_EPS,
        )
    }

    /** Coarse log-space scan over the strength range, then golden-section refinement. */
    private fun refine(input: CalibrationInput, cost: (Double) -> Double): Double {
        val logMin = ln(MIN_STRENGTH)
        val logMax = ln(MAX_STRENGTH)
        val step = (logMax - logMin) / (SCAN_POINTS - 1)

        var bestIndex = 0
        var bestCost = Double.MAX_VALUE
        for (i in 0 until SCAN_POINTS) {
            val value = cost(exp(logMin + step * i))
            if (value < bestCost) {
                bestCost = value
                bestIndex = i
            }
        }

        var lo = logMin + step * (bestIndex - 1).coerceAtLeast(0)
        var hi = logMin + step * (bestIndex + 1).coerceAtMost(SCAN_POINTS - 1)

        var c = hi - INV_PHI * (hi - lo)
        var d = lo + INV_PHI * (hi - lo)
        var fc = cost(exp(c))
        var fd = cost(exp(d))

        while (exp(hi) - exp(lo) > TOLERANCE) {
            if (fc <= fd) {
                hi = d
                d = c
                fd = fc
                c = hi - INV_PHI * (hi - lo)
                fc = cost(exp(c))
            } else {
                lo = c
                c = d
                fc = fd
                d = lo + INV_PHI * (hi - lo)
                fd = cost(exp(d))
            }
        }
        return exp(if (fc <= fd) c else d).coerceIn(MIN_STRENGTH, MAX_STRENGTH)
    }

    /** Spectral prediction for [input]'s recipe using [strength] for the calibrated paint. */
    private fun predict(input: CalibrationInput, strength: Double): Rgb = Spectral.mix(
        listOf(
            MixComponent(input.paint, input.paintParts, strength),
            MixComponent(input.partner, input.partnerParts, input.partnerStrength),
        ),
    )
}
