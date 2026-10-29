package com.colorstruction.ui.paints

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.colorstruction.R
import com.colorstruction.color.Rgb
import com.colorstruction.data.Paint
import com.colorstruction.ui.eyedropper.ImageSourceMenu

/** SavedStateHandle key through which the picker returns the swatch color for calibration. */
const val CALIBRATION_COLOR_KEY: String = "calibration_color"

/**
 * Bottom sheet fitting a paint's tinting strength to a swatch measured on a photo.
 *
 * @param calibration calibration state of the editor
 * @param paintColor color of the paint being calibrated, shown until a swatch is measured
 * @param currentStrength tinting strength currently stored in the editor
 * @param onPickColor opens the color picker for the swatch color
 * @param onPickFromImage opens the eyedropper on a gallery or camera photo
 * @param onApply copies the fitted strength into the editor
 * @param onDismiss hides the sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationSheet(
    calibration: CalibrationUiState,
    paintColor: Rgb,
    currentStrength: Double,
    onPartnerChange: (Paint) -> Unit,
    onPaintPartsChange: (Int) -> Unit,
    onPartnerPartsChange: (Int) -> Unit,
    onPickColor: () -> Unit,
    onPickFromImage: (Uri) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.calibration_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.calibration_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PartnerSelector(
                partners = calibration.partners,
                selected = calibration.partner,
                onSelect = onPartnerChange,
            )
            Text(
                text = stringResource(R.string.calibration_parts),
                style = MaterialTheme.typography.titleSmall,
            )
            PartsRow(
                paintParts = calibration.paintParts,
                partnerParts = calibration.partnerParts,
                onPaintPartsChange = onPaintPartsChange,
                onPartnerPartsChange = onPartnerPartsChange,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ColorDot(color = calibration.measured ?: paintColor, size = 56.dp)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPickColor) {
                        Text(stringResource(R.string.pick_swatch_color))
                    }
                    ImageSourceMenu(onPickFromImage = onPickFromImage)
                }
            }
            val result = calibration.result
            val measured = calibration.measured
            if (result != null && measured != null) {
                CalibrationResultBlock(result, measured, currentStrength)
                Button(onClick = onApply, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.apply))
                }
            }
        }
    }
}
