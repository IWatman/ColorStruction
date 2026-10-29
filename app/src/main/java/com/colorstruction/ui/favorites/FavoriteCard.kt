package com.colorstruction.ui.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.colorstruction.R
import com.colorstruction.color.Rgb
import com.colorstruction.data.FavoriteComponent
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
import java.text.DateFormat
import java.util.Date

/** Colour of the virtual water component: a light blue-gray. */
private val WaterColor = Color(0xFFAFC3CE)

/** Shape shared by the swatches. */
private val SwatchShape = RoundedCornerShape(6.dp)

/**
 * One saved recipe: target swatch, palette, date, shares, components and a delete button.
 *
 * @param favorite the stored snapshot shown by this card.
 * @param onOpen invoked when the card is tapped.
 * @param onDelete invoked when the delete button is pressed.
 */
@Composable
internal fun FavoriteCard(favorite: FavoriteRecipe, onOpen: () -> Unit, onDelete: () -> Unit) {
    val parts = Proportions.exactParts(favorite.components.map { it.weight })
    val onShare = rememberFavoriteShare(favorite, parts)
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(SwatchShape).background(Color(favorite.target.toArgbInt()))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SwatchShape),
                )
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    val muted = MaterialTheme.colorScheme.onSurfaceVariant
                    Text(Rgb.formatHex(favorite.target), style = MaterialTheme.typography.titleMedium)
                    if (favorite.paletteName.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.from_palette, favorite.paletteName),
                            style = MaterialTheme.typography.bodySmall,
                            color = muted,
                        )
                    }
                    Text(formatDate(favorite.createdAt), style = MaterialTheme.typography.labelSmall, color = muted)
                }
                IconButton(onClick = onShare) {
                    Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.share_recipe))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_favorite))
                }
            }
            Text(Proportions.formatParts(parts), style = MaterialTheme.typography.bodyMedium)
            favorite.components.forEachIndexed { index, component ->
                ComponentRow(component, Proportions.formatPart(parts.getOrElse(index) { 0.0 }))
            }
        }
    }
}

/** Swatch, name and share of one stored component. */
@Composable
private fun ComponentRow(component: FavoriteComponent, share: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(24.dp).clip(SwatchShape)
                .background(if (component.isWater) WaterColor else Color(component.color.toArgbInt()))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SwatchShape),
        )
        Text(
            text = if (component.isWater) stringResource(R.string.water) else component.paintName,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        Text(share, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Localized medium date of the save time. */
private fun formatDate(epochMillis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMillis))
