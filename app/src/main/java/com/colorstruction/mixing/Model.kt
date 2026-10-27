package com.colorstruction.mixing

import com.colorstruction.color.Rgb

/**
 * A paint as seen by the recipe solver: a display name, its sRGB color and how strongly it
 * tints a mixture relative to other paints.
 *
 * @property id stable identifier (from storage), used to build / recall recipes.
 * @property color the paint's own color, used as the spectral mixing input.
 * @property tintingStrength multiplier applied to the paint's weight; 1.0 = neutral.
 */
data class SolverPaint(
    val id: Long,
    val name: String,
    val color: Rgb,
    val tintingStrength: Double = 1.0,
)

/** The kind of palette, which decides how a mixture can be lightened. */
enum class PaletteType {
    /** Opaque paints: lighter tones are reached with white paint. */
    OPAQUE,

    /** Watercolor: lighter tones are reached by diluting with water instead of white paint. */
    WATERCOLOR,
}

/**
 * One component of a [Recipe].
 *
 * A component is either a paint ([paint] != null, [isWater] = false) or, in watercolor mode,
 * the virtual water component ([isWater] = true, [paint] = null).
 *
 * @property weight share of the component; weights of one recipe sum to 1.0.
 */
data class RecipeComponent(
    val paint: SolverPaint?,
    val isWater: Boolean,
    val weight: Double,
) {
    init {
        require(isWater == (paint == null)) {
            "A component is either a paint or water, never both and never neither"
        }
    }
}

/**
 * A mixing recipe that reproduces (approximately) a target color.
 *
 * @property components paints (plus the optional water component), in display order.
 * @property result the color produced by mixing the components with the spectral model.
 * @property deltaE CIEDE2000 difference between [result] and the target color.
 */
data class Recipe(
    val components: List<RecipeComponent>,
    val result: Rgb,
    val deltaE: Double,
) {
    /** Number of paint components; the virtual water component is not counted. */
    val paintCount: Int get() = components.count { it.paint != null }

    /** Total weight of the paint components (everything except water). */
    val paintWeight: Double get() = components.filter { it.paint != null }.sumOf { it.weight }
}

/** Human-readable bucket for a recipe's color difference. */
enum class Accuracy {
    /** The mix is visually indistinguishable from the target. */
    ALMOST_EXACT,

    /** The mix is a good match. */
    CLOSE,

    /** The mix is the same kind of color but noticeably off. */
    SIMILAR,

    /** The target cannot be reached from the selected paints. */
    NOT_POSSIBLE,
}

/**
 * Maps a recipe's color difference to an [Accuracy] bucket:
 * ΔE < 2 almost exact, ΔE < 5 close, ΔE < 10 similar, otherwise not possible.
 */
fun Recipe.accuracy(): Accuracy = when {
    deltaE < 2.0 -> Accuracy.ALMOST_EXACT
    deltaE < 5.0 -> Accuracy.CLOSE
    deltaE < 10.0 -> Accuracy.SIMILAR
    else -> Accuracy.NOT_POSSIBLE
}
