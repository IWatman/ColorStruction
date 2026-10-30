package com.colorstruction.ui.mix

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.colorstruction.R
import com.colorstruction.color.Rgb
import com.colorstruction.ui.eyedropper.ImageSourceMenu

/** SavedStateHandle key through which the picker and eyedropper return the batch color. */
const val CORRECTION_COLOR_KEY: String = "correction_color"

/** Below this color difference the batch already matches the target well enough. */
private const val ALREADY_CLOSE_DELTA_E = 2.0

/** Shape shared by the swatches. */
private val SwatchShape = RoundedCornerShape(8.dp)

/**
 * Bottom sheet that suggests what to add to an existing batch to bring it closer to the target.
 *
 * @param target the color the batch should have
 * @param state the measured batch color and the suggestions for it
 * @param onPickColor opens the color picker for the batch color
 * @param onPickFromImage opens the eyedropper on a photo of the batch
 * @param onDismiss hides the sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorrectionSheet(
    target: Rgb,
    state: CorrectionUiState,
    onPickColor: (Rgb) -> Unit,
    onPickFromImage: (Uri) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.correction_title), style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(R.string.correction_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LabeledSwatch(target, stringResource(R.string.target))
                LabeledSwatch(state.mix, stringResource(R.string.correction_your_mix))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onPickColor(state.mix ?: target) }) {
                        Text(stringResource(R.string.correction_pick_color))
                    }
                    ImageSourceMenu(onPickFromImage = onPickFromImage)
                }
            }
            CorrectionResults(state)
        }
    }
}

/** Progress, the "already close" / "nothing helps" notes, or the suggestion cards. */
@Composable
private fun CorrectionResults(state: CorrectionUiState) {
    val outcome = state.outcome
    when {
        state.mix == null -> Unit
        state.isComputing -> LinearProgressIndicator(Modifier.fillMaxWidth())
        outcome == null -> Unit
        else -> {
            Text(
                text = stringResource(R.string.correction_now, outcome.deltaEBefore),
                style = MaterialTheme.typography.titleSmall,
            )
            when {
                outcome.deltaEBefore < ALREADY_CLOSE_DELTA_E ->
                    Note(stringResource(R.string.correction_already_close))
                outcome.corrections.isEmpty() ->
                    Note(stringResource(R.string.correction_nothing_helps))
                else -> {
                    outcome.corrections.forEach { CorrectionCard(it) }
                    Note(stringResource(R.string.correction_footer))
                }
            }
        }
    }
}

/** A swatch with a caption under it; an empty outlined box when [color] is unknown. */
@Composable
private fun LabeledSwatch(color: Rgb?, label: String, size: Dp = 56.dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(size)
                .clip(SwatchShape)
                .background(color?.let { Color(it.toArgbInt()) } ?: Color.Transparent)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SwatchShape),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Secondary explanatory text. */
@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
