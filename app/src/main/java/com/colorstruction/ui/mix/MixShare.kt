package com.colorstruction.ui.mix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.colorstruction.R
import com.colorstruction.color.Rgb
import com.colorstruction.data.RatioFormat
import com.colorstruction.ui.share.RecipeImageData
import com.colorstruction.ui.share.RecipeImageTexts
import com.colorstruction.ui.share.WATER_SWATCH
import com.colorstruction.ui.share.renderRecipeImage
import com.colorstruction.ui.share.shareRecipeImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Share action of one solved [recipe]: renders its image as a PNG and opens the share sheet.
 *
 * @param paletteName name of the palette the recipe comes from, shown under the components.
 */
@Composable
internal fun rememberRecipeShare(
    recipe: RecipeUi,
    target: Rgb,
    ratioFormat: RatioFormat,
    paletteName: String,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chooserTitle = stringResource(R.string.share_recipe_chooser)
    val waterName = stringResource(R.string.water)
    val data = RecipeImageData(
        target = target,
        result = recipe.roundedResult,
        components = recipe.recipe.components.mapIndexed { index, component ->
            val label = recipe.componentLabels.getOrElse(index) { "" }
            // Amount labels arrive as bare numbers; the unit is localized here like on the card.
            val shown = if (ratioFormat == RatioFormat.AMOUNT && label.isNotEmpty()) {
                context.getString(R.string.amount_label, label)
            } else {
                label
            }
            Triple(component.paint?.name ?: waterName, component.paint?.color ?: WATER_SWATCH, shown)
        },
        deltaE = recipe.roundedDeltaE,
        paletteName = paletteName,
        shares = recipe.displayWeights,
    )
    val texts = RecipeImageTexts(
        title = stringResource(R.string.recipe_image_title),
        target = stringResource(R.string.target),
        result = stringResource(R.string.result),
        deltaE = stringResource(R.string.delta_e, recipe.roundedDeltaE),
        fromPalette = if (paletteName.isEmpty()) "" else stringResource(R.string.from_palette, paletteName),
        footer = stringResource(R.string.recipe_image_footer),
    )
    return {
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { renderRecipeImage(data, texts) }
            shareRecipeImage(context, bitmap, chooserTitle)
        }
    }
}
