package com.colorstruction.ui.paints

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.colorstruction.R
import com.colorstruction.data.Palette
import com.colorstruction.mixing.PaletteType

/** Palette dialog currently shown by the screen. */
internal enum class PaletteDialog { NEW, RENAME, DELETE }

/**
 * Shows the palette dialog requested by [dialog]; renders nothing for `null`.
 *
 * @param active the active palette, needed as the rename / delete target
 */
@Composable
internal fun PaletteDialogHost(
    dialog: PaletteDialog?,
    active: Palette?,
    onCreate: (String, PaletteType) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        PaletteDialog.NEW -> NamePaletteDialog(
            title = stringResource(R.string.new_palette),
            showTypeSelector = true,
            onConfirm = onCreate,
            onDismiss = onDismiss,
        )
        PaletteDialog.RENAME -> NamePaletteDialog(
            title = stringResource(R.string.rename_palette),
            initialName = active?.name.orEmpty(),
            confirmLabelRes = R.string.rename,
            onConfirm = { name, _ -> onRename(name) },
            onDismiss = onDismiss,
        )
        PaletteDialog.DELETE -> active?.let { palette ->
            DeletePaletteDialog(
                paletteName = palette.name,
                onConfirm = onDelete,
                onDismiss = onDismiss,
            )
        }
        null -> Unit
    }
}
