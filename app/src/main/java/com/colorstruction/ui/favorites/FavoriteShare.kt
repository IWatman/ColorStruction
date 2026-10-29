package com.colorstruction.ui.favorites

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.colorstruction.R
import com.colorstruction.data.FavoriteRecipe
import com.colorstruction.mixing.Proportions
import com.colorstruction.ui.share.RecipeImageData
import com.colorstruction.ui.share.RecipeImageTexts
import com.colorstruction.ui.share.WATER_SWATCH
import com.colorstruction.ui.share.renderRecipeImage
import com.colorstruction.ui.share.shareRecipeImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Share action of a saved [favorite]: renders its recipe image as a PNG and opens the share sheet.
 *
 * @param parts exact parts of the components, used for the labels and the proportion bar.
 */
@Composable
internal fun rememberFavoriteShare(favorite: FavoriteRecipe, parts: List<Double>): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chooserTitle = stringResource(R.string.share_recipe_chooser)
    val waterName = stringResource(R.string.water)
    val paletteName = favorite.paletteName
    val data = RecipeImageData(
        target = favorite.target,
        result = null,
        components = favorite.components.mapIndexed { index, component ->
            Triple(
                if (component.isWater) waterName else component.paintName,
                if (component.isWater) WATER_SWATCH else component.color,
                Proportions.formatPart(parts.getOrElse(index) { 0.0 }),
            )
        },
        deltaE = favorite.deltaE,
        paletteName = paletteName,
        shares = parts,
    )
    val texts = RecipeImageTexts(
        title = stringResource(R.string.recipe_image_title),
        target = stringResource(R.string.target),
        result = stringResource(R.string.result),
        deltaE = stringResource(R.string.delta_e, favorite.deltaE),
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
