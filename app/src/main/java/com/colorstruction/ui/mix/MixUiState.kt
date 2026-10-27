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
 * @property favoriteKey identity used to tell whether this exact recipe is already saved.
 */
data class RecipeUi(
    val recipe: Recipe,
    val displayWeights: List<Double>,
    val proportionsText: String,
    val roundedResult: Rgb,
    val roundedDeltaE: Double,
    val accuracy: Accuracy,
    val favoriteKey: String,
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
    val recentColors: List<Int> = emptyList(),
    val savedRecipeKeys: Set<String> = emptySet(),
)

/** Rounds [recipe]'s weights into display proportions and re-evaluates the mix with them. */
fun recipeUi(
    recipe: Recipe,
    target: Rgb,
    ratioFormat: RatioFormat,
    solver: RecipeSolver,
): RecipeUi {
    val weights = recipe.components.map { it.weight }
    // Rounding to parts can cost accuracy, so prefer the simplest rounding that stays within
    // half a ΔE of the unrounded recipe and fall back to the most accurate candidate.
    val displayWeights: List<Double> = when (ratioFormat) {
        RatioFormat.PARTS -> Proportions
            .partsCandidates(weights)
            .take(Proportions.MAX_CANDIDATES)
            .let { candidates ->
                Proportions.chooseParts(candidates, recipe.deltaE) { parts ->
                    solver.evaluate(target, recipe, parts).deltaE
                }
            } ?: Proportions.toParts(weights)
        RatioFormat.PERCENT -> Proportions.toPercents(weights).map { it.toDouble() }
    }
    val proportionsText = when (ratioFormat) {
        RatioFormat.PARTS -> Proportions.formatParts(displayWeights)
        RatioFormat.PERCENT -> displayWeights.joinToString(" / ") { "${it.toInt()}%" }
    }
    val rounded = solver.evaluate(target, recipe, displayWeights)
    return RecipeUi(
        recipe = recipe,
        displayWeights = displayWeights,
        proportionsText = proportionsText,
        roundedResult = rounded.result,
        roundedDeltaE = rounded.deltaE,
        accuracy = accuracyOf(rounded.deltaE),
        favoriteKey = favoriteKey(
            targetArgb = target.toArgbInt(),
            paintNames = recipe.components.map { it.paint?.name.orEmpty() },
            weights = displayWeights,
        ),
    )
}
