package com.colorstruction.ui.paints

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.colorstruction.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** File name suffix of exported palettes; stripped for the share subject. */
private const val FILE_SUFFIX = ".colorstruction.json"

/** The palette transfer actions offered by the palette menu. */
class PaletteTransferActions(
    /** Shares the active palette through the system share sheet. */
    val share: () -> Unit,
    /** Writes the active palette into a file picked by the user. */
    val saveToFile: () -> Unit,
    /** Reads a palette file into a new palette. */
    val importFromFile: () -> Unit,
)

/**
 * Remembers the palette exports / import of the screen, reporting results through
 * [snackbarHostState].
 */
@Composable
fun rememberPaletteTransfer(
    viewModel: PaintsViewModel,
    snackbarHostState: SnackbarHostState,
): PaletteTransferActions {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    var pendingJson by rememberSaveable { mutableStateOf<String?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(JSON_MIME),
    ) { uri ->
        val json = pendingJson
        pendingJson = null
        if (uri != null && json != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) { writeText(appContext, uri, json) }
                snackbarHostState.showSnackbar(
                    appContext.getString(
                        if (saved) R.string.palette_saved else R.string.palette_save_failed,
                    ),
                )
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) { readTextOrNull(appContext, uri) }
                if (text == null) {
                    snackbarHostState.showSnackbar(
                        appContext.getString(R.string.import_error_too_large),
                    )
                } else {
                    viewModel.importPalette(text)
                }
            }
        }
    }

    val share: () -> Unit = {
        scope.launch {
            val exported = viewModel.exportActivePalette() ?: return@launch
            val uri = withContext(Dispatchers.IO) {
                createSharedFileUri(appContext, exported.first, exported.second)
            }
            if (uri == null) {
                snackbarHostState.showSnackbar(
                    appContext.getString(R.string.palette_save_failed),
                )
            } else {
                sharePalette(context, uri, exported.first.removeSuffix(FILE_SUFFIX))
            }
        }
    }

    val saveToFile: () -> Unit = {
        scope.launch {
            val exported = viewModel.exportActivePalette() ?: return@launch
            pendingJson = exported.second
            saveLauncher.launch(exported.first)
        }
    }

    return PaletteTransferActions(
        share = share,
        saveToFile = saveToFile,
        importFromFile = { importLauncher.launch(IMPORT_MIME_TYPES) },
    )
}
