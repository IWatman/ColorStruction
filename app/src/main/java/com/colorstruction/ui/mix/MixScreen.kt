package com.colorstruction.ui.mix

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.colorstruction.R
import com.colorstruction.color.Rgb
import com.colorstruction.ui.eyedropper.ImageSourceMenu
import kotlinx.coroutines.flow.MutableStateFlow

/** SavedStateHandle key through which the picker returns the confirmed color. */
const val PICKED_COLOR_KEY = "picked_color"

/**
 * Mixing overview: target color, display controls and the recipe cards.
 *
 * @param onPickColor invoked with the current target color when the pick button is pressed
 * @param onPickFromImage invoked with a gallery/camera image to open the eyedropper on
 * @param savedStateHandle handle of this destination, used to receive the picked color
 */
@Composable
fun MixScreen(
    onPickColor: (Rgb) -> Unit,
    onPickFromImage: (Uri) -> Unit,
    savedStateHandle: SavedStateHandle?,
    modifier: Modifier = Modifier,
    viewModel: MixViewModel = viewModel(factory = MixViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val target = uiState.target

    val pickedFlow = remember(savedStateHandle) {
        savedStateHandle?.getStateFlow<Int?>(PICKED_COLOR_KEY, null) ?: MutableStateFlow<Int?>(null)
    }
    val pickedArgb by pickedFlow.collectAsStateWithLifecycle()
    LaunchedEffect(pickedArgb) {
        val argb = pickedArgb
        if (argb != null) {
            viewModel.setTarget(Rgb.fromArgbInt(argb))
            savedStateHandle?.set<Int?>(PICKED_COLOR_KEY, null)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { TargetSection(target, onPickColor, onPickFromImage) }
        if (uiState.recentColors.isNotEmpty()) {
            item {
                RecentColors(
                    colors = uiState.recentColors,
                    onSelect = { argb -> viewModel.setTarget(Rgb.fromArgbInt(argb)) },
                )
            }
        }
        item {
            MixControls(
                ratioFormat = uiState.ratioFormat,
                maxComponents = uiState.maxComponents,
                totalAmount = uiState.totalAmount,
                onRatioFormatChange = viewModel::setRatioFormat,
                onMaxComponentsChange = viewModel::setMaxComponents,
                onTotalAmountChange = viewModel::setTotalAmount,
            )
        }
        item { RecipesHeader(uiState.palette?.name) }
        if (uiState.isSolving) {
            item {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.solving), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
        items(uiState.recipes) { recipe ->
            RecipeCard(
                recipe = recipe,
                target = target,
                ratioFormat = uiState.ratioFormat,
                totalAmount = uiState.totalAmount,
                isSaved = recipe.favoriteKey in uiState.savedRecipeKeys,
                onSave = { viewModel.saveFavorite(recipe) },
                onShare = rememberRecipeShare(
                    recipe = recipe,
                    target = target,
                    ratioFormat = uiState.ratioFormat,
                    paletteName = uiState.palette?.name.orEmpty(),
                ),
            )
        }
        if (uiState.paintCount == 0) {
            item {
                Text(stringResource(R.string.no_paints_in_palette), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Target swatch next to its hex value and the pick buttons. */
@Composable
private fun TargetSection(
    target: Rgb,
    onPickColor: (Rgb) -> Unit,
    onPickFromImage: (Uri) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(target.toArgbInt()))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.target_color), style = MaterialTheme.typography.titleMedium)
            Text(Rgb.formatHex(target), style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { onPickColor(target) }) {
                Icon(imageVector = Icons.Filled.Colorize, contentDescription = null)
                Text(stringResource(R.string.pick_color), Modifier.padding(start = 8.dp))
            }
            ImageSourceMenu(onPickFromImage = onPickFromImage)
        }
    }
}

/** Recently used target colors as tappable swatches. */
@Composable
private fun RecentColors(colors: List<Int>, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.recent_colors), style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(colors) { argb ->
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(argb))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .clickable { onSelect(argb) },
                )
            }
        }
    }
}

/** "Recipes" heading with the name of the palette the recipes come from. */
@Composable
private fun RecipesHeader(paletteName: String?) {
    Column {
        Text(stringResource(R.string.recipes), style = MaterialTheme.typography.titleMedium)
        if (!paletteName.isNullOrEmpty()) {
            Text(
                text = stringResource(R.string.from_palette, paletteName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
