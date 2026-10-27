package com.colorstruction.ui.paints

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.colorstruction.R
import com.colorstruction.color.Rgb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/** Formats [value] as e.g. `1.00×`. */
private fun formatStrength(value: Double): String = String.format(Locale.ROOT, "%.2f×", value)

/**
 * Add / edit paint screen.
 *
 * @param onBack invoked on the back arrow
 * @param onSaved invoked after the paint has been stored
 * @param onPickColor invoked with the current color when "Change color" is pressed
 * @param pickedColor color returned by the picker through the saved state handle
 * @param onClearPickedColor called after the picked color has been applied
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaintEditorScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onPickColor: (Rgb) -> Unit,
    pickedColor: StateFlow<Int?>? = null,
    onClearPickedColor: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: PaintEditorViewModel = viewModel(factory = PaintEditorViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val pickedFlow = remember(pickedColor) { pickedColor ?: MutableStateFlow<Int?>(null) }
    val pickedArgb by pickedFlow.collectAsStateWithLifecycle()

    LaunchedEffect(saved) {
        if (saved) onSaved()
    }
    LaunchedEffect(pickedArgb) {
        val argb = pickedArgb
        if (argb != null) {
            viewModel.onColorChange(Rgb.fromArgbInt(argb))
            onClearPickedColor()
        }
    }

    // The root Scaffold already pads for system bars; don't apply the insets twice.
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = {
                    Text(
                        stringResource(
                            if (state.isNew) R.string.new_paint else R.string.edit_paint,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::save, enabled = state.canSave) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = stringResource(R.string.save),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.paint_name)) },
                singleLine = true,
            )
            ColorSection(
                color = state.color,
                onChangeColor = { onPickColor(state.color) },
            )
            StrengthSection(
                value = state.tintingStrength,
                onChange = viewModel::onTintingStrengthChange,
            )
            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::onNoteChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.note)) },
                minLines = 2,
            )
        }
    }
}

/** Large color swatch, its HEX value and the button that opens the picker. */
@Composable
private fun ColorSection(color: Rgb, onChangeColor: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(color.toArgbInt()))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(12.dp),
                ),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = Rgb.formatHex(color),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedButton(onClick = onChangeColor) {
                Text(stringResource(R.string.change_color))
            }
        }
    }
}

/** Tinting strength slider with the current value and a one-line hint. */
@Composable
private fun StrengthSection(value: Double, onChange: (Double) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.tinting_strength),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = formatStrength(value),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Slider(
            value = value.toFloat().coerceIn(MIN_TINTING_STRENGTH, MAX_TINTING_STRENGTH),
            onValueChange = { onChange(it.toDouble()) },
            valueRange = MIN_TINTING_STRENGTH..MAX_TINTING_STRENGTH,
        )
        Text(
            text = stringResource(R.string.tinting_strength_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
