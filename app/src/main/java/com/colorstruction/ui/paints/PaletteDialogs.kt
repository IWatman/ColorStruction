package com.colorstruction.ui.paints

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.colorstruction.R
import com.colorstruction.mixing.PaletteType

/** Localized label for a palette type. */
@Composable
internal fun paletteTypeLabel(type: PaletteType): String = stringResource(
    when (type) {
        PaletteType.OPAQUE -> R.string.palette_type_opaque
        PaletteType.WATERCOLOR -> R.string.palette_type_watercolor
    },
)

/**
 * Asks for a palette name, and for the palette type when [showTypeSelector] is set.
 *
 * @param onConfirm receives the trimmed name and the chosen type
 */
@Composable
fun NamePaletteDialog(
    title: String,
    onConfirm: (name: String, type: PaletteType) -> Unit,
    onDismiss: () -> Unit,
    initialName: String = "",
    showTypeSelector: Boolean = false,
    initialType: PaletteType = PaletteType.OPAQUE,
    confirmLabelRes: Int = R.string.create,
) {
    var name by remember { mutableStateOf(initialName) }
    var type by remember { mutableStateOf(initialType) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.palette_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (showTypeSelector) {
                    Column(modifier = Modifier.selectableGroup()) {
                        PaletteType.entries.forEach { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = option == type,
                                        role = Role.RadioButton,
                                        onClick = { type = option },
                                    )
                                    .padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = option == type, onClick = null)
                                Text(
                                    text = paletteTypeLabel(option),
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), type) },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(confirmLabelRes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** Confirms deletion of the palette [paletteName]. */
@Composable
fun DeletePaletteDialog(
    paletteName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_palette)) },
        text = { Text(stringResource(R.string.delete_palette_confirm, paletteName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
