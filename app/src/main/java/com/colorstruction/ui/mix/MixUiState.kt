package com.colorstruction.ui.mix

import com.colorstruction.color.Rgb
import com.colorstruction.data.Palette
import com.colorstruction.data.RatioFormat
import com.colorstruction.data.SettingsRepository
import com.colorstruction.mixing.Accuracy
import com.colorstruction.mixing.Proportions
import com.colorstruction.mixing.Recipe
import com.colorstruction.mixing.RecipeSolver
import com.colorstruction.mixing.accuracyOf

/**
 * One recipe of the mix screen.
 *
 * @property displayWeights the painted shares shown to the user (parts or percents).
 * @property proportionsText those shares as text, in component order.
 * @property roundedResult color of the mix with the rounded shares.
 * @property roundedDeltaE color difference of [roundedResult] to the target.
 */
data class RecipeUi(
    val recipe: Recipe,
    val displayWeights: List<Double>,
    val proportionsText: String,
    val roundedResult: Rgb,
    val roundedDeltaE: Double,
    val accuracy: Accuracy,
)

/** Snapshot of the mix screen: target, palette summary and the solved recipes. */
data class MixUiState(
    val target: Rgb,
    val palette: Palette? = null,
    val paintCount: Int = 0,
    val maxComponents: Int = SettingsRepository.DEFAULT_MAX_COMPONENTS,
    val ratioFormat: RatioFormat = RatioFormat.PARTS,
    val isSolving: Boolean = false,
    val recipes: List<RecipeUi> = emptyList(),
)

/** Rounds [recipe]'s weights into display proportions and re-evaluates the mix with them. */
fun recipeUi(
    recipe: Recipe,
    target: Rgb,
    ratioFormat: RatioFormat,
    solver: RecipeSolver,
): RecipeUi {
    val weights = recipe.components.map { it.weight }
    val (displayWeights, proportionsText) = when (ratioFormat) {
        RatioFormat.PARTS -> Proportions.toParts(weights)
            .let { parts -> parts to Proportions.formatParts(parts) }
        RatioFormat.PERCENT -> Proportions.toPercents(weights)
            .let { percents -> percents.map { it.toDouble() } to percents.joinToString(" / ") { "$it%" } }
    }
    val rounded = solver.evaluate(target, recipe, displayWeights)
    return RecipeUi(
        recipe = recipe,
        displayWeights = displayWeights,
        proportionsText = proportionsText,
        roundedResult = rounded.result,
        roundedDeltaE = rounded.deltaE,
        accuracy = accuracyOf(rounded.deltaE),
    )
}
