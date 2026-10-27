package com.colorstruction.mixing

import com.colorstruction.color.Rgb
import com.colorstruction.color.SpectralColor

/**
 * Spectral data of one palette: the precomputed paint spectra plus, for watercolor palettes, the
 * virtual water component (paper white, neutral tinting strength).
 *
 * Water is an *extra optional* component: it never counts towards the component cap and opaque
 * palettes never have it at all.
 */
internal class PaletteSpectra(
    val paints: List<SolverPaint>,
    val paletteType: PaletteType,
) {
    val paintSpectra: Array<SpectralColor> =
        Array(paints.size) { SpectralColor.of(paints[it].color) }

    val paintTinting: DoubleArray = DoubleArray(paints.size) { paints[it].tintingStrength }

    val waterSpectrum: SpectralColor? =
        if (paletteType == PaletteType.WATERCOLOR) SpectralColor.of(WATER_COLOR) else null

    /** Whether this palette may dilute a mixture with water. */
    val hasWater: Boolean get() = waterSpectrum != null

    private val spectrumById: Map<Long, SpectralColor> =
        HashMap<Long, SpectralColor>(paints.size).apply {
            paints.forEachIndexed { index, paint -> put(paint.id, paintSpectra[index]) }
        }

    /**
     * Kernel over the paints at [paintIndices], in that order, with water appended last unless
     * [withWater] is `false`.
     */
    fun paintKernel(paintIndices: List<Int>, withWater: Boolean = hasWater): MixKernel {
        val water = waterSpectrum
        val size = paintIndices.size + if (withWater) 1 else 0
        return MixKernel(
            colors = Array(size) { position ->
                if (position < paintIndices.size) {
                    paintSpectra[paintIndices[position]]
                } else {
                    requireNotNull(water) { "Only watercolor palettes have a water component" }
                }
            },
            tintingStrengths = DoubleArray(size) { position ->
                if (position < paintIndices.size) {
                    paintTinting[paintIndices[position]]
                } else {
                    WATER_TINTING_STRENGTH
                }
            },
        )
    }

    /** Kernel over recipe components in their own order (used by [RecipeSolver.evaluate]). */
    fun componentKernel(components: List<RecipeComponent>): MixKernel = MixKernel(
        colors = Array(components.size) { spectrumOf(components[it]) },
        tintingStrengths = DoubleArray(components.size) { tintingOf(components[it]) },
    )

    /** Spectrum of a component: the paint's cached spectrum, or the paper white for water. */
    fun spectrumOf(component: RecipeComponent): SpectralColor {
        val paint = component.paint ?: return waterSpectrum ?: SpectralColor.of(WATER_COLOR)
        return spectrumById[paint.id] ?: SpectralColor.of(paint.color)
    }

    /** Tinting strength of a component; water has neutral strength. */
    fun tintingOf(component: RecipeComponent): Double =
        component.paint?.tintingStrength ?: WATER_TINTING_STRENGTH

    companion object {
        /** Water is paper white: it lightens without tinting the color. */
        val WATER_COLOR: Rgb = Rgb.WHITE

        /** Water has neutral tinting strength. */
        const val WATER_TINTING_STRENGTH: Double = 1.0
    }
}
