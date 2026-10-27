package com.colorstruction.ui.mix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.colorstruction.R
import com.colorstruction.data.AppSettings
import com.colorstruction.data.RatioFormat

/**
 * Display controls of the mix screen: parts vs percents and the paint cap.
 *
 * @param ratioFormat currently selected proportion format.
 * @param maxComponents currently selected paint cap per recipe.
 * @param onRatioFormatChange invoked when the user switches the proportion format.
 * @param onMaxComponentsChange invoked with the new paint cap (1..5).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MixControls(
    ratioFormat: RatioFormat,
    maxComponents: Int,
    onRatioFormatChange: (RatioFormat) -> Unit,
    onMaxComponentsChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val formats = RatioFormat.entries
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            formats.forEachIndexed { index, format ->
                SegmentedButton(
                    selected = format == ratioFormat,
                    onClick = { onRatioFormatChange(format) },
                    shape = SegmentedButtonDefaults.itemShape(index, formats.size),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = format.label())
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.max_paints),
                style = MaterialTheme.typography.labelLarge,
            )
            for (count in AppSettings.MIN_MAX_COMPONENTS..AppSettings.MAX_MAX_COMPONENTS) {
                FilterChip(
                    selected = count == maxComponents,
                    onClick = { onMaxComponentsChange(count) },
                    label = { Text(text = count.toString()) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Localized short label of a [RatioFormat] segment. */
@Composable
private fun RatioFormat.label(): String = when (this) {
    RatioFormat.PARTS -> stringResource(R.string.parts)
    RatioFormat.PERCENT -> stringResource(R.string.percent)
}
