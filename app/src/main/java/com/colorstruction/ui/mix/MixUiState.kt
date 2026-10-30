package com.colorstruction.ui.mix

import com.colorstruction.color.DeltaE
import com.colorstruction.color.Rgb
import com.colorstruction.color.rgbMix
import com.colorstruction.data.Palette
import com.colorstruction.data.RatioFormat
import com.colorstruction.data.SettingsRepository
import com.colorstruction.mixing.Accuracy
import com.colorstruction.mixing.Amounts
import com.colorstruction.mixing.Proportions
import com.colorstruction.mixing.Recipe
import com.colorstruction.mixing.RecipeSolver
import com.colorstruction.mixing.accuracyOf

/**
 * One recipe of the mix screen.
 *
 * @property displayWeights the painted shares shown to the user (parts, percents or parts
 *   behind the amounts).
 * @property componentLabels those shares as ready-to-show text (`1½`, `35%`, `12.5 ml`).
 * @property proportionsText those shares as text, in component order.
 * @property roundedResult color of the mix with the rounded shares.
 * @property roundedDeltaE color difference of [roundedResult] to the target.
 * @property rgbResult color that simply averaging the paint RGB values would give, or `null`
 *   when the learning mode is off.
 * @property rgbDeltaE color difference of [rgbResult] to the target, or `null` when off.
 * @property favoriteKey identity used to tell whether this exact recipe is already saved.
 */
data class RecipeUi(
    val recipe: Recipe,
    val displayWeights: List<Double>,
    val componentLabels: List<String>,
    val proportionsText: String,
    val roundedResult: Rgb,
    val roundedDeltaE: Double,
    val accuracy: Accuracy,
    val favoriteKey: String,
    val rgbResult: Rgb? = null,
    val rgbDeltaE: Double? = null,
)

/** Snapshot of the mix screen: target, palette summary and the solved recipes. */
data class MixUiState(
    val target: Rgb,
    val palette: Palette? = null,
    val paintCount: Int = 0,
    val maxComponents: Int = SettingsRepository.DEFAULT_MAX_COMPONENTS,
    val ratioFormat: RatioFormat = RatioFormat.PARTS,
    val totalAmount: Int = SettingsRepository.DEFAULT_TOTAL_AMOUNT,
    val isSolving: Boolean = false,
    val showRgbComparison: Boolean = false,
    val recipes: List<RecipeUi> = emptyList(),
    val recentColors: List<Int> = emptyList(),
    val savedRecipeKeys: Set<String> = emptySet(),
)

/**
 * Rounds [recipe]'s weights into display proportions and re-evaluates the mix with them.
 *
 * @param totalAmount total volume in millilitres, used by [RatioFormat.AMOUNT].
 * @param showRgbComparison whether to compute the naive RGB mixing comparison.
 * @param amountLabel formats one amount with its unit, for example `12.5 ml`.
 */
fun recipeUi(
    recipe: Recipe,
    target: Rgb,
    ratioFormat: RatioFormat,
    solver: RecipeSolver,
    totalAmount: Int = SettingsRepository.DEFAULT_TOTAL_AMOUNT,
    showRgbComparison: Boolean = false,
    amountLabel: (Double) -> String = { Amounts.formatAmount(it) },
): RecipeUi {
    val weights = recipe.components.map { it.weight }
    // Rounding to parts can cost accuracy, so prefer the simplest rounding that stays within
    // half a ΔE of the unrounded recipe and fall back to the most accurate candidate.
    val parts = Proportions
        .partsCandidates(weights)
        .take(Proportions.MAX_CANDIDATES)
        .let { candidates ->
            Proportions.chooseParts(candidates, recipe.deltaE) { candidate ->
                solver.evaluate(target, recipe, candidate).deltaE
            }
        } ?: Proportions.toParts(weights)
    // Every format shows the same recipe: amounts and percents are those parts re-expressed,
    // so switching the format never changes what the user is told to mix.
    val displayWeights: List<Double> = when (ratioFormat) {
        RatioFormat.PARTS, RatioFormat.AMOUNT -> parts
        RatioFormat.PERCENT -> Proportions.toPercents(parts).map { it.toDouble() }
    }
    val proportionsText = when (ratioFormat) {
        RatioFormat.PARTS, RatioFormat.AMOUNT -> Proportions.formatParts(displayWeights)
        RatioFormat.PERCENT -> displayWeights.joinToString(" / ") { "${it.toInt()}%" }
    }
    val componentLabels = when (ratioFormat) {
        RatioFormat.PARTS -> displayWeights.map { Proportions.formatPart(it) }
        RatioFormat.PERCENT -> displayWeights.map { "${it.toInt()}%" }
        RatioFormat.AMOUNT -> {
            val total = totalAmount.toDouble()
            Amounts.amounts(displayWeights, total, Amounts.amountStep(total)).map(amountLabel)
        }
    }
    val rounded = solver.evaluate(target, recipe, displayWeights)
    // Naive RGB mixing: average the paint colors (water counts as white) with the displayed
    // shares, so the user can see why screen colors do not mix like paint.
    val rgbResult = if (showRgbComparison) {
        rgbMix(
            recipe.components.mapIndexed { index, component ->
                (component.paint?.color ?: Rgb.WHITE) to displayWeights.getOrElse(index) { 0.0 }
            },
        )
    } else {
        null
    }
    return RecipeUi(
        recipe = recipe,
        displayWeights = displayWeights,
        componentLabels = componentLabels,
        proportionsText = proportionsText,
        roundedResult = rounded.result,
        roundedDeltaE = rounded.deltaE,
        accuracy = accuracyOf(rounded.deltaE),
        favoriteKey = favoriteKey(
            targetArgb = target.toArgbInt(),
            paintNames = recipe.components.map { it.paint?.name.orEmpty() },
            weights = displayWeights,
        ),
        rgbResult = rgbResult,
        rgbDeltaE = rgbResult?.let { DeltaE.ciede2000(it.toLab(), target.toLab()) },
    )
}
