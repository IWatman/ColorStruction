package com.colorstruction.ui.mix

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.colorstruction.R
import com.colorstruction.color.Rgb
import com.colorstruction.data.RatioFormat
import com.colorstruction.mixing.Accuracy
import com.colorstruction.mixing.RecipeComponent

/** Colour of the virtual water component: a light blue-gray. */
private val WaterColor = Color(0xFFAFC3CE)

/** Shape shared by the component swatches. */
private val SwatchShape = RoundedCornerShape(6.dp)

/**
 * One recipe: target/result swatches, accuracy badge, components and a proportion bar.
 *
 * @param recipe the recipe together with the rounded shares that are displayed.
 * @param target the color the recipe was solved for, shown next to the result.
 * @param ratioFormat how the per-component share is rendered (parts, percent or amount).
 * @param totalAmount total volume of the recipe in millilitres, shown in the amount mode.
 * @param isSaved whether this exact recipe is already stored as a favorite.
 * @param onSave invoked when the star is tapped on a recipe that is not saved yet.
 * @param onShare invoked when the share button is tapped.
 */
@Composable
fun RecipeCard(
    recipe: RecipeUi,
    target: Rgb,
    ratioFormat: RatioFormat,
    totalAmount: Int,
    isSaved: Boolean,
    onSave: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Header(recipe, target, isSaved, onSave, onShare)
            recipe.rgbResult?.let { rgb -> RgbComparisonRow(rgb, recipe.rgbDeltaE) }
            recipe.recipe.components.forEachIndexed { index, component ->
                val label = recipe.componentLabels.getOrElse(index) { "" }
                // Amount labels arrive as bare numbers; the unit is localized here, at render
                // time, so it follows a language change immediately.
                val shown = if (ratioFormat == RatioFormat.AMOUNT && label.isNotEmpty()) {
                    stringResource(R.string.amount_label, label)
                } else {
                    label
                }
                ComponentRow(component, shown)
            }
            ProportionBar(recipe)
            if (ratioFormat == RatioFormat.AMOUNT) {
                Text(
                    text = stringResource(R.string.total_amount_line, totalAmount.toString()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Side-by-side target and result swatches, the accuracy badge, ΔE and the share/save buttons. */
@Composable
private fun Header(
    recipe: RecipeUi,
    target: Rgb,
    isSaved: Boolean,
    onSave: () -> Unit,
    onShare: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(8.dp))) {
                Box(Modifier.weight(1f).fillMaxHeight().background(Color(target.toArgbInt())))
                Box(Modifier.weight(1f).fillMaxHeight().background(Color(recipe.roundedResult.toArgbInt())))
            }
            val style = MaterialTheme.typography.labelSmall
            val color = MaterialTheme.colorScheme.onSurfaceVariant
            Row(Modifier.padding(top = 2.dp)) {
                Text(stringResource(R.string.target), style = style, color = color, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.result), style = style, color = color)
            }
        }
        Column(Modifier.padding(start = 8.dp), horizontalAlignment = Alignment.End) {
            AccuracyBadge(recipe.accuracy)
            val style = MaterialTheme.typography.labelSmall
            val color = MaterialTheme.colorScheme.onSurfaceVariant
            Text(stringResource(R.string.delta_e, recipe.roundedDeltaE), style = style, color = color)
        }
        IconButton(onClick = onShare) {
            Icon(
                imageVector = Icons.Outlined.Share,
                contentDescription = stringResource(R.string.share_recipe),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onSave, enabled = !isSaved) {
            Icon(
                imageVector = if (isSaved) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = stringResource(
                    if (isSaved) R.string.saved_favorite else R.string.save_favorite,
                ),
                tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Compact row under the header: the naive RGB average as a small swatch plus its ΔE.
 *
 * @param rgb the color plain channel averaging would produce.
 * @param rgbDeltaE its color difference to the target, when known.
 */
@Composable
private fun RgbComparisonRow(rgb: Rgb, rgbDeltaE: Double?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(16.dp)
                .clip(SwatchShape)
                .background(Color(rgb.toArgbInt()))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SwatchShape),
        )
        Text(
            text = stringResource(R.string.rgb_comparison, rgbDeltaE ?: 0.0),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** Small coloured label for an [Accuracy] bucket. */
@Composable
private fun AccuracyBadge(accuracy: Accuracy) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (accuracy) {
        Accuracy.ALMOST_EXACT -> colors.secondaryContainer to colors.onSecondaryContainer
        Accuracy.CLOSE -> colors.primaryContainer to colors.onPrimaryContainer
        Accuracy.SIMILAR -> colors.surfaceVariant to colors.onSurfaceVariant
        Accuracy.NOT_POSSIBLE -> colors.errorContainer to colors.onErrorContainer
    }
    val label = when (accuracy) {
        Accuracy.ALMOST_EXACT -> stringResource(R.string.accuracy_almost_exact)
        Accuracy.CLOSE -> stringResource(R.string.accuracy_close)
        Accuracy.SIMILAR -> stringResource(R.string.accuracy_similar)
        Accuracy.NOT_POSSIBLE -> stringResource(R.string.accuracy_not_possible)
    }
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50)) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

/** Swatch, name and share of one component. */
@Composable
private fun ComponentRow(component: RecipeComponent, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(24.dp)
                .clip(SwatchShape)
                .background(swatchColor(component))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SwatchShape),
        )
        Text(
            text = component.paint?.name ?: stringResource(R.string.water),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Thin stacked bar whose segment widths follow the displayed shares. */
@Composable
private fun ProportionBar(recipe: RecipeUi) {
    val weights = recipe.displayWeights
    val total = weights.sum().let { if (it > 0.0) it else 1.0 }
    Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))) {
        recipe.recipe.components.forEachIndexed { index, component ->
            val share = (weights.getOrElse(index) { 0.0 } / total).toFloat().coerceAtLeast(0.002f)
            Box(Modifier.weight(share).fillMaxHeight().background(swatchColor(component)))
        }
    }
}

/** Colour of a component: the paint's own color or the water stand-in. */
private fun swatchColor(component: RecipeComponent): Color =
    component.paint?.let { Color(it.color.toArgbInt()) } ?: WaterColor
