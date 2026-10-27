package com.colorstruction.ui.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.colorstruction.R
import com.colorstruction.color.Hsv
import com.colorstruction.color.Rgb
import com.colorstruction.color.hsvToRgb

private val MAX_WHEEL_WIDTH = 360.dp

/** Renders [this] as a Compose (opaque) color. */
private fun Rgb.toComposeColor(): Color = Color(toArgbInt())

/**
 * Full screen color picker.
 *
 * The wheel, the hex field and the RGB sliders all edit one hoisted [Hsv] state; the hue is
 * kept in that state so it survives saturation or value becoming zero.
 *
 * @param initialColor color shown when the screen opens
 * @param onConfirm invoked with the edited color when the check action is used
 * @param onCancel invoked on the back arrow
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerScreen(
    initialColor: Rgb,
    onConfirm: (Rgb) -> Unit,
    onCancel: () -> Unit,
) {
    var hsv by remember(initialColor) { mutableStateOf(initialColor.toHsvKeepingHue(0.0)) }
    val color = remember(hsv) { hsvToRgb(hsv) }
    val onColorChange: (Rgb) -> Unit = { hsv = it.toHsvKeepingHue(hsv.h) }

    // The root Scaffold already pads for system bars; don't apply the insets twice.
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text(stringResource(R.string.picker_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onConfirm(color) }) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = stringResource(R.string.confirm),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val landscape = maxWidth > maxHeight
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PreviewSwatch(color = color, initialColor = initialColor)
                if (landscape) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        WheelSection(hsv, { hsv = it }, Modifier.weight(1f))
                        ControlsSection(color, onColorChange, Modifier.weight(1f))
                    }
                } else {
                    WheelSection(hsv, { hsv = it }, Modifier.fillMaxWidth())
                    ControlsSection(color, onColorChange, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** The edited color as a large rounded swatch with the initial color right beside it. */
@Composable
private fun PreviewSwatch(color: Rgb, initialColor: Rgb, modifier: Modifier = Modifier) {
    val initialLabel = stringResource(R.string.initial_color)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(12.dp))
                .background(color.toComposeColor())
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(12.dp),
                ),
        )
        Box(
            modifier = Modifier
                .width(56.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(12.dp))
                .background(initialColor.toComposeColor())
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(12.dp),
                )
                .semantics { contentDescription = initialLabel },
        )
    }
}

/** The color wheel, centered and capped at [MAX_WHEEL_WIDTH]. */
@Composable
private fun WheelSection(hsv: Hsv, onHsvChange: (Hsv) -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.color_wheel_description)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        ColorWheel(
            hsv = hsv,
            onHsvChange = onHsvChange,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = MAX_WHEEL_WIDTH)
                .semantics { contentDescription = description },
        )
    }
}

/** Hex field plus RGB sliders. */
@Composable
private fun ControlsSection(
    color: Rgb,
    onColorChange: (Rgb) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HexField(color = color, onColorChange = onColorChange)
        RgbSliders(color = color, onColorChange = onColorChange)
    }
}
