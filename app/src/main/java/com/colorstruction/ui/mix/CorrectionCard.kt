package com.colorstruction.ui.mix

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.colorstruction.R
import com.colorstruction.color.Rgb
import com.colorstruction.mixing.Correction
import com.colorstruction.mixing.CorrectionAddition
import com.colorstruction.mixing.CorrectionOutcome
import java.text.NumberFormat

/** Colour of the virtual water component: a light blue-gray. */
private val WaterColor = Color(0xFFAFC3CE)

/** Shape shared by the swatches. */
private val SwatchShape = RoundedCornerShape(6.dp)

/**
 * State of the "fix a mix" sheet.
 *
 * @property mix the measured color of the existing batch, or `null` before it is picked
 * @property isComputing whether suggestions are being searched for
 * @property outcome the suggestions for [mix], or `null` while unknown
 */
data class CorrectionUiState(
    val mix: Rgb? = null,
    val isComputing: Boolean = false,
    val outcome: CorrectionOutcome? = null,
)

/** One suggestion: what to add, then the predicted color with its ΔE. */
@Composable
internal fun CorrectionCard(correction: Correction) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            correction.additions.forEach { AdditionRow(it) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Swatch(Color(correction.result.toArgbInt()), 24)
                Text(
                    text = stringResource(R.string.correction_result, correction.deltaE),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/** "+5 %" with the paint swatch and name. */
@Composable
private fun AdditionRow(addition: CorrectionAddition) {
    val locale = LocalConfiguration.current.locales[0]
    val percent = NumberFormat.getNumberInstance(locale)
        .apply { maximumFractionDigits = 1 }
        .format(addition.amount * 100.0)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Swatch(addition.paint?.let { Color(it.color.toArgbInt()) } ?: WaterColor, 32)
        Text(
            text = addition.paint?.name ?: stringResource(R.string.water),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        Text(
            text = stringResource(R.string.correction_add_amount, percent),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun Swatch(color: Color, sizeDp: Int) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(SwatchShape)
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SwatchShape),
    )
}
