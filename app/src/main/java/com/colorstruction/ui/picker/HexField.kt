package com.colorstruction.ui.picker

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.colorstruction.R
import com.colorstruction.color.Rgb

private const val MAX_LENGTH = 7
private val HEX_BODY = Regex("^[0-9a-fA-F]*$")
private val HEX_WITH_SHARP = Regex("^#[0-9a-fA-F]*$")

/**
 * Text field for a `#RRGGBB` color.
 *
 * Input may be typed with or without the leading `#`; [onColorChange] is invoked only while the
 * text parses to a color, otherwise the field shows an error and a supporting hint. When [color]
 * changes from the outside (wheel or sliders) the text follows it, unless the user is editing.
 *
 * @param color color currently shown in the picker
 * @param onColorChange invoked when the typed text is a valid color
 */
@Composable
fun HexField(
    color: Rgb,
    onColorChange: (Rgb) -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatted = Rgb.formatHex(color)
    var text by remember { mutableStateOf(formatted) }
    var focused by remember { mutableStateOf(false) }
    val isValid = Rgb.parseHex(text) != null

    LaunchedEffect(formatted) {
        if (!focused) text = formatted
    }

    val label = stringResource(R.string.hex_label)
    val errorText = stringResource(R.string.hex_error)

    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val cleaned = raw.filter { it != ' ' }.take(MAX_LENGTH)
            if (HEX_WITH_SHARP.matches(cleaned) || HEX_BODY.matches(cleaned)) {
                text = cleaned
                Rgb.parseHex(cleaned)?.let(onColorChange)
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { state ->
                focused = state.isFocused
                if (!state.isFocused && Rgb.parseHex(text) == null) text = formatted
            },
        label = { Text(label) },
        placeholder = { Text("#RRGGBB") },
        isError = !isValid,
        supportingText = { if (!isValid) Text(errorText) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            keyboardType = KeyboardType.Ascii,
        ),
    )
}
