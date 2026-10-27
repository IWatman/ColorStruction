package com.colorstruction.color

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** JavaScript `Number.EPSILON`, used by spectral.js as the minimum reflectance value. */
private const val SPECTRAL_EPSILON: Double = 2.220446049250313e-16

/**
 * A paint component for spectral Kubelka-Munk mixing.
 *
 * @property color sRGB color of the component
 * @property weight non-negative mixing weight; weights need not sum to 1 (they are normalized)
 * @property tintingStrength optional per-pigment tinting strength (1.0 = neutral, less than
 *   1.0 weakens the pigment, greater than 1.0 strengthens it)
 */
data class MixComponent(
    val color: Rgb,
    val weight: Double,
    val tintingStrength: Double = 1.0,
)

/**
 * A precomputed, cache-friendly paint component for repeated spectral mixing.
 *
 * Precompute one instance per paint with [SpectralColor.of] and pass it to
 * [Spectral.mixPrepared]; this avoids recomputing the reflectance spectrum, K/S spectrum and
 * luminance for every solver iteration.
 */
data class PreparedMixComponent(
    val color: SpectralColor,
    val weight: Double,
    val tintingStrength: Double = 1.0,
)

/**
 * A paint color with its spectral data precomputed once.
 *
 * [reflectance] is the 38-band reflectance spectrum, [ks] the Kubelka-Munk K/S spectrum and
 * [luminance] the CIE Y value (floored at [SPECTRAL_EPSILON], matching spectral.js).
 */
class SpectralColor private constructor(
    val rgb: Rgb,
    val reflectance: DoubleArray,
    val ks: DoubleArray,
    val luminance: Double,
) {
    companion object {
        /** Builds a cached [SpectralColor] for [rgb]. */
        fun of(rgb: Rgb): SpectralColor {
            val reflectance = Spectral.reflectance(rgb)
            val luminance = max(SPECTRAL_EPSILON, Spectral.reflectanceToXyz(reflectance).y)
            return SpectralColor(
                rgb = rgb,
                reflectance = reflectance,
                ks = Spectral.ks(reflectance),
                luminance = luminance,
            )
        }
    }
}

/**
 * Spectral Kubelka-Munk paint mixing, ported from spectral.js by Ronald van Wijnen (MIT).
 *
 * Mixing semantics follow spectral.js faithfully: the concentration of a component is
 * `weight² * tintingStrength² * luminance`, where luminance is the CIE Y of the component.
 * These concentrations form a weighted average of the components' K/S spectra, which is then
 * inverted (`R = 1 + KS - sqrt(KS² + 2·KS)`) and converted back to sRGB.
 *
 * The only intentional deviation is the sRGB output precision: spectral.js rounds to 8-bit
 * integers and can apply an OKLab gamut mapping; this port returns continuous doubles in
 * [0, 1] and clamps out-of-gamut channels.
 */
object Spectral {

    /**
     * Decomposes an sRGB color into a 38-band reflectance spectrum (380-750 nm) using the
     * seven spectral.js basis spectra (white, cyan, magenta, yellow, red, green, blue).
     */
    fun reflectance(rgb: Rgb): DoubleArray {
        val linear = srgbToLinearRgb(rgb)

        val w = min(linear.r, min(linear.g, linear.b))
        val r = linear.r - w
        val g = linear.g - w
        val b = linear.b - w

        val c = min(g, b)
        val m = min(r, b)
        val y = min(r, g)
        val red = max(0.0, min(r - b, r - g))
        val green = max(0.0, min(g - b, g - r))
        val blue = max(0.0, min(b - g, b - r))

        val result = DoubleArray(SPECTRAL_BANDS)
        for (i in 0 until SPECTRAL_BANDS) {
            result[i] = max(
                SPECTRAL_EPSILON,
                w * SPECTRAL_BASE_W[i] +
                    c * SPECTRAL_BASE_C[i] +
                    m * SPECTRAL_BASE_M[i] +
                    y * SPECTRAL_BASE_Y[i] +
                    red * SPECTRAL_BASE_R[i] +
                    green * SPECTRAL_BASE_G[i] +
                    blue * SPECTRAL_BASE_B[i],
            )
        }
        return result
    }

    /**
     * Converts a reflectance spectrum to Kubelka-Munk K/S values: `(1 - R)² / (2·R)`.
     */
    fun ks(reflectance: DoubleArray): DoubleArray = DoubleArray(reflectance.size) { ksBand(reflectance[it]) }

    /** Converts a 38-band reflectance spectrum to CIE XYZ using the CIE 1931 D65 CMF. */
    fun reflectanceToXyz(reflectance: DoubleArray): Xyz {
        require(reflectance.size == SPECTRAL_BANDS) {
            "Expected $SPECTRAL_BANDS reflectance bands, got ${reflectance.size}"
        }
        var x = 0.0
        var y = 0.0
        var z = 0.0
        for (i in 0 until SPECTRAL_BANDS) {
            x += SPECTRAL_CIE_CMF[0][i] * reflectance[i]
            y += SPECTRAL_CIE_CMF[1][i] * reflectance[i]
            z += SPECTRAL_CIE_CMF[2][i] * reflectance[i]
        }
        return Xyz(x, y, z)
    }

    /** Converts a 38-band reflectance spectrum back to sRGB, clamping out-of-gamut channels. */
    fun reflectanceToRgb(reflectance: DoubleArray): Rgb {
        val xyz = reflectanceToXyz(reflectance)
        val linear = xyzToLinearRgbSpectral(xyz)
        return Rgb(
            linearToSrgbChannel(linear.r).coerceIn(0.0, 1.0),
            linearToSrgbChannel(linear.g).coerceIn(0.0, 1.0),
            linearToSrgbChannel(linear.b).coerceIn(0.0, 1.0),
        )
    }

    /**
     * Mixes colors with neutral tinting strength (1.0).
     *
     * Each [Pair] is `(color, weight)`; weights must be non-negative and need not sum to 1.
     * Components with a non-positive weight are ignored.
     */
    fun mix(components: List<Pair<Rgb, Double>>): Rgb =
        mix(components.map { MixComponent(it.first, it.second) })

    /**
     * Mixes colors with per-component tinting strength.
     *
     * Weights must be non-negative and need not sum to 1; they are normalized. Components with
     * a non-positive weight or tinting strength are ignored. If nothing contributes, white is
     * returned (no pigment on a white substrate).
     */
    @JvmName("mixWithTinting")
    fun mix(components: List<MixComponent>): Rgb {
        val contributing = components.filter { it.weight > 0.0 && it.tintingStrength > 0.0 }
        if (contributing.isEmpty()) return Rgb.WHITE

        val totalWeight = contributing.sumOf { it.weight }
        return mixPrepared(
            contributing.map {
                PreparedMixComponent(
                    color = SpectralColor.of(it.color),
                    weight = it.weight / totalWeight,
                    tintingStrength = it.tintingStrength,
                )
            },
        )
    }

    /**
     * Mixes precomputed [SpectralColor] components. Identical semantics to [mix] but avoids
     * recomputing spectral data; intended for solver hot paths.
     */
    fun mixPrepared(components: List<PreparedMixComponent>): Rgb {
        val contributing = components.filter { it.weight > 0.0 && it.tintingStrength > 0.0 }
        if (contributing.isEmpty()) return Rgb.WHITE

        val totalWeight = contributing.sumOf { it.weight }
        val reflectance = DoubleArray(SPECTRAL_BANDS)

        for (band in 0 until SPECTRAL_BANDS) {
            var ksMix = 0.0
            var totalConcentration = 0.0

            for (component in contributing) {
                val weight = component.weight / totalWeight
                val concentration = weight * weight *
                    component.tintingStrength * component.tintingStrength *
                    component.color.luminance
                totalConcentration += concentration
                ksMix += component.color.ks[band] * concentration
            }

            reflectance[band] = km(ksMix / totalConcentration)
        }

        return reflectanceToRgb(reflectance)
    }

    private fun ksBand(reflectance: Double): Double {
        val oneMinus = 1.0 - reflectance
        return oneMinus * oneMinus / (2.0 * reflectance)
    }

    private fun km(ks: Double): Double = 1.0 + ks - sqrt(ks * ks + 2.0 * ks)

    private fun xyzToLinearRgbSpectral(xyz: Xyz): LinearRgb = LinearRgb(
        r = SPECTRAL_XYZ_TO_RGB[0][0] * xyz.x + SPECTRAL_XYZ_TO_RGB[0][1] * xyz.y + SPECTRAL_XYZ_TO_RGB[0][2] * xyz.z,
        g = SPECTRAL_XYZ_TO_RGB[1][0] * xyz.x + SPECTRAL_XYZ_TO_RGB[1][1] * xyz.y + SPECTRAL_XYZ_TO_RGB[1][2] * xyz.z,
        b = SPECTRAL_XYZ_TO_RGB[2][0] * xyz.x + SPECTRAL_XYZ_TO_RGB[2][1] * xyz.y + SPECTRAL_XYZ_TO_RGB[2][2] * xyz.z,
    )
}
