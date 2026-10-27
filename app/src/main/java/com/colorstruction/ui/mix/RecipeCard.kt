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
import androidx.compose.material3.ElevatedCard
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
import com.colorstruction.mixing.Proportions
import com.colorstruction.mixing.RecipeComponent
import kotlin.math.roundToInt

/** Colour of the virtual water component: a light blue-gray. */
private val WaterColor = Color(0xFFAFC3CE)

/** Shape shared by the component swatches. */
private val SwatchShape = RoundedCornerShape(6.dp)

/**
 * One recipe: target/result swatches, accuracy badge, components and a proportion bar.
 *
 * @param recipe the recipe together with the rounded shares that are displayed.
 * @param target the color the recipe was solved for, shown next to the result.
 * @param ratioFormat how the per-component share is rendered (parts or percent).
 */
@Composable
fun RecipeCard(
    recipe: RecipeUi,
    target: Rgb,
    ratioFormat: RatioFormat,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Header(recipe, target)
            recipe.recipe.components.forEachIndexed { index, component ->
                ComponentRow(component, recipe.displayWeights.getOrElse(index) { 0.0 }, ratioFormat)
            }
            ProportionBar(recipe)
        }
    }
}

/** Side-by-side target and result swatches with the accuracy badge and the ΔE value. */
@Composable
private fun Header(recipe: RecipeUi, target: Rgb) {
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
private fun ComponentRow(component: RecipeComponent, weight: Double, ratioFormat: RatioFormat) {
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
        Text(proportionText(weight, ratioFormat), style = MaterialTheme.typography.bodyMedium)
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

/** Per-component share text: `1½` for parts, `35%` for percents. */
private fun proportionText(weight: Double, ratioFormat: RatioFormat): String =
    if (ratioFormat == RatioFormat.PERCENT) "${weight.roundToInt()}%" else Proportions.formatPart(weight)
