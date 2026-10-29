package com.colorstruction.ui.paints

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.colorstruction.R
import com.colorstruction.color.Rgb
import com.colorstruction.mixing.CalibrationResult

/**
 * Fitted strength together with the measured and the predicted swatches.
 *
 * @param currentStrength strength currently stored in the editor
 * @param measured color picked from the photo of the swatch
 */
@Composable
internal fun CalibrationResultBlock(
    result: CalibrationResult,
    measured: Rgb,
    currentStrength: Double,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LabeledSwatch(R.string.calibration_swatch, measured)
            LabeledSwatch(R.string.calibration_before, result.predictedBefore)
            LabeledSwatch(R.string.calibration_after, result.predictedAfter)
        }
        Text(
            text = stringResource(
                R.string.calibration_strength_change,
                currentStrength,
                result.strength,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(
                R.string.calibration_delta_e_change,
                result.deltaEBefore,
                result.deltaEAfter,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (result.atLimit) {
            Text(
                text = stringResource(R.string.calibration_at_limit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Swatch with a caption underneath. */
@Composable
private fun LabeledSwatch(labelRes: Int, color: Rgb) {
    Column(
        modifier = Modifier.width(72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ColorDot(color = color, size = 56.dp)
        Text(text = stringResource(labelRes), style = MaterialTheme.typography.labelSmall)
    }
}
