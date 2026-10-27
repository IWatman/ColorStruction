package com.colorstruction.ui.mix

import com.colorstruction.data.FavoriteRecipe
import kotlin.math.roundToInt

/** Scale used to turn share ratios into stable integers. */
private const val KEY_SCALE = 1_000_000.0

/**
 * Stable identity of a recipe: target ARGB, the sorted paint names and the share ratios.
 *
 * The weights are normalised to their sum first, so the same recipe gives the same key no matter
 * whether the shares are stored as parts or as raw weights.
 */
fun favoriteKey(targetArgb: Int, paintNames: List<String>, weights: List<Double>): String {
    val total = weights.sum().let { if (it > 0.0) it else 1.0 }
    val shares = weights.joinToString(",") { ((it / total) * KEY_SCALE).roundToInt().toString() }
    val names = paintNames.filter { it.isNotEmpty() }.sorted().joinToString("+")
    return "$targetArgb|$names|$shares"
}

/** Key of a stored favorite; see [favoriteKey]. */
fun FavoriteRecipe.favoriteKey(): String = favoriteKey(
    targetArgb = target.toArgbInt(),
    paintNames = components.map { it.paintName },
    weights = components.map { it.weight },
)
