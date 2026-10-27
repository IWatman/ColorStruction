package com.colorstruction.ui.paints

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.colorstruction.R
import kotlinx.coroutines.launch

/**
 * "My paints" tab: palette selection and the paints of the active palette.
 *
 * @param onAddPaint invoked with the active palette id by the add button
 * @param onEditPaint invoked with the palette id and the tapped paint id
 */
@Composable
fun PaintsScreen(
    modifier: Modifier = Modifier,
    onAddPaint: (Long) -> Unit = {},
    onEditPaint: (Long, Long) -> Unit = { _, _ -> },
    viewModel: PaintsViewModel = viewModel(factory = PaintsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<PaletteDialog?>(null) }
    val active = state.activePalette

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PaletteSelectorRow(
                palettes = state.palettes,
                active = active,
                onSelect = viewModel::selectPalette,
                onAction = { action ->
                    dialog = when (action) {
                        PaletteAction.New -> PaletteDialog.NEW
                        PaletteAction.Rename -> PaletteDialog.RENAME
                        PaletteAction.Delete -> PaletteDialog.DELETE
                        is PaletteAction.SetType -> {
                            active?.let { viewModel.setPaletteType(it.id, action.type) }
                            null
                        }
                    }
                },
            )
            active?.let { PaletteTypeChip(it.type) }
            if (state.paints.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.no_paints),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 88.dp),
                ) {
                    itemsIndexed(state.paints, key = { _, paint -> paint.id }) { index, paint ->
                        PaintRow(
                            paint = paint,
                            isFirst = index == 0,
                            isLast = index == state.paints.lastIndex,
                            onEdit = { onEditPaint(paint.paletteId, paint.id) },
                            onMoveUp = { viewModel.movePaint(index, index - 1) },
                            onMoveDown = { viewModel.movePaint(index, index + 1) },
                            onDelete = {
                                viewModel.deletePaint(paint)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = context.getString(
                                            R.string.paint_deleted,
                                            paint.name,
                                        ),
                                        actionLabel = context.getString(R.string.undo),
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.undoDeletePaint()
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { active?.let { onAddPaint(it.id) } },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_paint))
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp),
        )
    }

    PaletteDialogHost(
        dialog = dialog,
        active = active,
        onCreate = { name, type ->
            viewModel.createPalette(name, type)
            dialog = null
        },
        onRename = { name ->
            active?.let { viewModel.renamePalette(it.id, name) }
            dialog = null
        },
        onDelete = {
            active?.let { viewModel.deletePalette(it.id) }
            dialog = null
        },
        onDismiss = { dialog = null },
    )
}
