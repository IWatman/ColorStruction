package com.colorstruction.ui.eyedropper

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.colorstruction.R
import com.colorstruction.color.Rgb

private val SAMPLE_LABELS = listOf(R.string.sample_1px, R.string.sample_3, R.string.sample_5)

/**
 * Bottom panel of the eyedropper: the sampled color with its HEX value and the sample size.
 *
 * @param argb currently sampled (opaque) color
 * @param selectedIndex index into the 1 px / 3×3 / 5×5 choices
 * @param whiteBalanceOn whether a white reference is set for this photo
 * @param canSetWhite whether the spot under the crosshair is light enough to be the white
 * @param onSetWhite stores the spot under the crosshair as the white reference
 * @param onResetWhite drops the white reference
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EyedropperBottomBar(
    argb: Int,
    selectedIndex: Int,
    onSelectSampleSize: (Int) -> Unit,
    whiteBalanceOn: Boolean,
    canSetWhite: Boolean,
    onSetWhite: () -> Unit,
    onResetWhite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxWidth(), tonalElevation = 3.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(argb))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(8.dp),
                        ),
                )
                Text(
                    text = Rgb.fromArgbInt(argb).toHex(),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Text(
                text = stringResource(R.string.sample_size),
                style = MaterialTheme.typography.labelMedium,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SAMPLE_LABELS.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = index == selectedIndex,
                        onClick = { onSelectSampleSize(index) },
                        shape = SegmentedButtonDefaults.itemShape(index, SAMPLE_LABELS.size),
                    ) {
                        Text(text = stringResource(label), textAlign = TextAlign.Center)
                    }
                }
            }
            WhiteBalanceRow(whiteBalanceOn, canSetWhite, onSetWhite, onResetWhite)
        }
    }
}

/** White balance status with the "this is white" / reset action and a short hint. */
@Composable
private fun WhiteBalanceRow(
    whiteBalanceOn: Boolean,
    canSetWhite: Boolean,
    onSetWhite: () -> Unit,
    onResetWhite: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.white_balance),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = stringResource(
                    if (whiteBalanceOn) R.string.white_balance_on else R.string.white_balance_hint,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (whiteBalanceOn) {
            TextButton(onClick = onResetWhite) { Text(stringResource(R.string.white_balance_reset)) }
        } else {
            OutlinedButton(onClick = onSetWhite, enabled = canSetWhite) {
                Text(stringResource(R.string.white_balance_set))
            }
        }
    }
}
