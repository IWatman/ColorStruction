package com.colorstruction.mixing

import com.colorstruction.color.DeltaE
import com.colorstruction.color.Lab
import com.colorstruction.color.PreparedMixComponent
import com.colorstruction.color.Rgb
import com.colorstruction.color.SPECTRAL_BANDS
import com.colorstruction.color.SPECTRAL_CIE_CMF
import com.colorstruction.color.SPECTRAL_XYZ_TO_RGB
import com.colorstruction.color.SpectralColor
import com.colorstruction.color.linearToSrgbChannel
import kotlin.math.sqrt

/** Lab of pure white, returned when a mix has no contributing component (no pigment at all). */
private val WHITE_LAB: Lab = Rgb.WHITE.toLab()

/**
 * The outcome of one [MixKernel] evaluation: the resulting color in both representations, so a
 * caller that needs both never mixes twice.
 */
internal class MixResult(val rgb: Rgb, val lab: Lab)

/**
 * A hot-path mixer for a fixed set of components.
 *
 * The kernel is the solver's inner loop: it reproduces [com.colorstruction.color.Spectral.mixPrepared]
 * operation for operation (same concentration formula, same summation order, same sRGB
 * clamping), but keeps its reflectance buffer and intermediate results in fields instead of
 * allocating them on every call.
 *
 * @param colors precomputed spectra of the components, in mixing order.
 * @param tintingStrengths per-component tinting strength (1.0 = neutral), same order.
 */
internal class MixKernel(
    private val colors: Array<SpectralColor>,
    private val tintingStrengths: DoubleArray,
) {
    init {
        require(colors.size == tintingStrengths.size) {
            "Got ${colors.size} colors but ${tintingStrengths.size} tinting strengths"
        }
    }

    /** Number of components this kernel mixes; weight arrays must have at least this length. */
    val componentCount: Int get() = colors.size

    private val luminances = DoubleArray(colors.size) { colors[it].luminance }
    private val reflectance = DoubleArray(SPECTRAL_BANDS)

    private var cachedRgb: Rgb = Rgb.WHITE
    private var cachedLab: Lab = WHITE_LAB

    /**
     * Mixes [weights] once and returns both the resulting sRGB color and its CIELAB value —
     * the same color [com.colorstruction.color.Spectral.mixPrepared] returns, converted with
     * [Rgb.toLab].
     *
     * Weights may be any non-negative numbers and need not sum to 1; they are normalized over
     * the components that actually contribute (positive weight and positive tinting strength).
     * Entries beyond [componentCount] are ignored.
     */
    fun mix(weights: DoubleArray): MixResult {
        compute(weights)
        return MixResult(cachedRgb, cachedLab)
    }

    /** Mixes [weights] and returns the resulting sRGB color. */
    fun rgb(weights: DoubleArray): Rgb {
        compute(weights)
        return cachedRgb
    }

    /** Mixes [weights] and returns the CIELAB color of the result, see [rgb]. */
    fun lab(weights: DoubleArray): Lab {
        compute(weights)
        return cachedLab
    }

    /** CIEDE2000 difference between the mix of [weights] and [targetLab]. */
    fun deltaE(weights: DoubleArray, targetLab: Lab): Double =
        DeltaE.ciede2000(lab(weights), targetLab)

    private fun compute(weights: DoubleArray) {
        var totalWeight = 0.0
        for (index in 0 until componentCount) {
            if (weights[index] > 0.0 && tintingStrengths[index] > 0.0) totalWeight += weights[index]
        }
        if (totalWeight <= 0.0) {
            // Matches Spectral.mixPrepared: no pigment on a white substrate.
            cachedRgb = Rgb.WHITE
            cachedLab = WHITE_LAB
            return
        }

        for (band in 0 until SPECTRAL_BANDS) {
            var ksMix = 0.0
            var totalConcentration = 0.0
            for (index in 0 until componentCount) {
                if (weights[index] <= 0.0 || tintingStrengths[index] <= 0.0) continue
                val weight = weights[index] / totalWeight
                val tintingStrength = tintingStrengths[index]
                val concentration =
                    weight * weight * tintingStrength * tintingStrength * luminances[index]
                totalConcentration += concentration
                ksMix += colors[index].ks[band] * concentration
            }
            val ks = ksMix / totalConcentration
            reflectance[band] = 1.0 + ks - sqrt(ks * ks + 2.0 * ks)
        }

        // Reflectance -> XYZ, then XYZ -> linear sRGB -> clamped sRGB (same as the reference path).
        var x = 0.0
        var y = 0.0
        var z = 0.0
        for (band in 0 until SPECTRAL_BANDS) {
            val value = reflectance[band]
            x += SPECTRAL_CIE_CMF[0][band] * value
            y += SPECTRAL_CIE_CMF[1][band] * value
            z += SPECTRAL_CIE_CMF[2][band] * value
        }

        val matrix = SPECTRAL_XYZ_TO_RGB
        val linearR = matrix[0][0] * x + matrix[0][1] * y + matrix[0][2] * z
        val linearG = matrix[1][0] * x + matrix[1][1] * y + matrix[1][2] * z
        val linearB = matrix[2][0] * x + matrix[2][1] * y + matrix[2][2] * z

        val rgb = Rgb(
            linearToSrgbChannel(linearR).coerceIn(0.0, 1.0),
            linearToSrgbChannel(linearG).coerceIn(0.0, 1.0),
            linearToSrgbChannel(linearB).coerceIn(0.0, 1.0),
        )
        cachedRgb = rgb
        cachedLab = rgb.toLab()
    }

    companion object {
        /** Builds a kernel from precomputed mix components, keeping their order. */
        fun of(components: List<PreparedMixComponent>): MixKernel = MixKernel(
            colors = Array(components.size) { components[it].color },
            tintingStrengths = DoubleArray(components.size) { components[it].tintingStrength },
        )

        /** Builds a kernel from spectra and tinting strengths. */
        fun of(colors: Array<SpectralColor>, tintingStrengths: DoubleArray): MixKernel =
            MixKernel(colors, tintingStrengths)
    }
}
