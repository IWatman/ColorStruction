package com.colorstruction.ui.picker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.colorstruction.R
import com.colorstruction.color.Rgb
import kotlin.math.round

private val RedTint = Color(0xFFD32F2F)
private val GreenTint = Color(0xFF388E3C)
private val BlueTint = Color(0xFF1976D2)
private const val MAX_CHANNEL = 255.0

/**
 * Three sliders editing the red, green and blue channels of [color] as 8-bit values.
 *
 * Each slider shows its current integer value and is tinted with the channel color.
 *
 * @param color color currently shown in the picker
 * @param onColorChange invoked with the updated color while a slider moves
 */
@Composable
fun RgbSliders(
    color: Rgb,
    onColorChange: (Rgb) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ChannelSlider(
            label = stringResource(R.string.color_red),
            value = color.r,
            tint = RedTint,
            onValueChange = { onColorChange(color.copy(r = it)) },
        )
        ChannelSlider(
            label = stringResource(R.string.color_green),
            value = color.g,
            tint = GreenTint,
            onValueChange = { onColorChange(color.copy(g = it)) },
        )
        ChannelSlider(
            label = stringResource(R.string.color_blue),
            value = color.b,
            tint = BlueTint,
            onValueChange = { onColorChange(color.copy(b = it)) },
        )
    }
}

/** One labelled, tinted 0..255 channel slider. [value] is a channel in `[0, 1]`. */
@Composable
private fun ChannelSlider(
    label: String,
    value: Double,
    tint: Color,
    onValueChange: (Double) -> Unit,
) {
    val byte = round(value.coerceIn(0.0, 1.0) * MAX_CHANNEL).toInt()
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$label   $byte",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 4.dp),
        )
        Slider(
            value = byte.toFloat(),
            onValueChange = { onValueChange(it / MAX_CHANNEL) },
            valueRange = 0f..MAX_CHANNEL.toFloat(),
            steps = MAX_CHANNEL.toInt() - 1,
            colors = SliderDefaults.colors(
                thumbColor = tint,
                activeTrackColor = tint,
                inactiveTrackColor = tint.copy(alpha = 0.24f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
