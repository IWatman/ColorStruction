package com.colorstruction.ui.mix

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.colorstruction.R
import com.colorstruction.color.Rgb
import kotlinx.coroutines.flow.MutableStateFlow

/** SavedStateHandle key through which the picker returns the confirmed color. */
const val PICKED_COLOR_KEY = "picked_color"

/**
 * Mixing overview: shows the target color and opens the color picker.
 *
 * @param onPickColor invoked with the current target color when the pick button is pressed
 * @param savedStateHandle handle of this destination, used to receive the picked color
 */
@Composable
fun MixScreen(
    onPickColor: (Rgb) -> Unit,
    savedStateHandle: SavedStateHandle?,
    modifier: Modifier = Modifier,
    viewModel: MixViewModel = viewModel(),
) {
    val target by viewModel.target.collectAsStateWithLifecycle()

    val pickedFlow = remember(savedStateHandle) {
        savedStateHandle?.getStateFlow<Int?>(PICKED_COLOR_KEY, null) ?: MutableStateFlow<Int?>(null)
    }
    val pickedArgb by pickedFlow.collectAsStateWithLifecycle()
    LaunchedEffect(pickedArgb) {
        val argb = pickedArgb
        if (argb != null) {
            viewModel.setTarget(Rgb.fromArgbInt(argb))
            savedStateHandle?.set<Int?>(PICKED_COLOR_KEY, null)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.target_color),
            style = MaterialTheme.typography.titleLarge,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(target.toArgbInt()))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(16.dp),
                ),
        )
        Text(
            text = Rgb.formatHex(target),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.align(Alignment.Start),
        )
        Button(onClick = { onPickColor(target) }) {
            Icon(imageVector = Icons.Filled.Colorize, contentDescription = null)
            Text(
                text = stringResource(R.string.pick_color),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
