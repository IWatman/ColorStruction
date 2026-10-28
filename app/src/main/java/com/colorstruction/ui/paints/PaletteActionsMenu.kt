package com.colorstruction.ui.paints

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.colorstruction.R
import com.colorstruction.data.Palette
import com.colorstruction.mixing.PaletteType

/** Actions offered by the palette overflow menu. */
internal sealed interface PaletteAction {
    data object New : PaletteAction
    data object Rename : PaletteAction
    data object Delete : PaletteAction
    data object Share : PaletteAction
    data object SaveToFile : PaletteAction
    data object Import : PaletteAction
    data class SetType(val type: PaletteType) : PaletteAction
}

/** Overflow menu with the palette actions; the current palette type is checked. */
@Composable
internal fun PaletteActionsMenu(
    expanded: Boolean,
    active: Palette?,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onAction: (PaletteAction) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.new_palette)) },
            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
            onClick = { onAction(PaletteAction.New) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.rename_palette)) },
            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
            enabled = active != null,
            onClick = { onAction(PaletteAction.Rename) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.palette_type)) },
            enabled = false,
            onClick = {},
        )
        PaletteType.entries.forEach { type ->
            DropdownMenuItem(
                text = { Text(paletteTypeLabel(type)) },
                enabled = active != null,
                trailingIcon = {
                    if (active?.type == type) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                    }
                },
                onClick = { onAction(PaletteAction.SetType(type)) },
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.share_palette)) },
            leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
            enabled = active != null,
            onClick = { onAction(PaletteAction.Share) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.save_palette_to_file)) },
            leadingIcon = { Icon(Icons.Outlined.Save, contentDescription = null) },
            enabled = active != null,
            onClick = { onAction(PaletteAction.SaveToFile) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.import_palette)) },
            leadingIcon = { Icon(Icons.Outlined.FileOpen, contentDescription = null) },
            onClick = { onAction(PaletteAction.Import) },
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.delete_palette)) },
            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            enabled = active != null && canDelete,
            onClick = { onAction(PaletteAction.Delete) },
        )
    }
}

/** Read-only chip that shows the type of the active palette. */
@Composable
internal fun PaletteTypeChip(type: PaletteType) {
    AssistChip(
        onClick = {},
        label = { Text(paletteTypeLabel(type)) },
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}
