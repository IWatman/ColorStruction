package com.colorstruction.ui.paints

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.colorstruction.ColorStructionApp
import com.colorstruction.color.Rgb
import com.colorstruction.data.Paint
import com.colorstruction.data.PaintRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Fallback color of a new paint, a mid grey. */
private val DEFAULT_PAINT_COLOR: Rgb = Rgb.parseHex("#808080") ?: Rgb(0.5, 0.5, 0.5)

/** Snapshot of the add / edit paint screen. */
data class PaintEditorUiState(
    val isNew: Boolean = true,
    val loading: Boolean = false,
    val name: String = "",
    val color: Rgb = DEFAULT_PAINT_COLOR,
    val tintingStrength: Double = 1.0,
    val note: String = "",
) {
    /** Saving is allowed only for a named paint that has finished loading. */
    val canSave: Boolean get() = !loading && name.isNotBlank()
}

/** Lowest tinting strength the slider offers. */
const val MIN_TINTING_STRENGTH: Float = 0.25f

/** Highest tinting strength the slider offers. */
const val MAX_TINTING_STRENGTH: Float = 2.0f

/**
 * State holder of the add / edit paint screen.
 *
 * Reads `paletteId` and `paintId` from the navigation arguments in [savedStateHandle] and
 * loads the edited paint on creation; `paintId` of `-1` means a new paint.
 */
class PaintEditorViewModel(
    private val paintRepository: PaintRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val paletteId: Long = savedStateHandle[PALETTE_ID_ARG] ?: -1L

    /** Paint being edited, `-1` when a new paint is created. */
    val paintId: Long = savedStateHandle[PAINT_ID_ARG] ?: -1L

    private val _uiState = MutableStateFlow(
        PaintEditorUiState(isNew = paintId < 0, loading = paintId >= 0),
    )

    /** Current editor state. */
    val uiState: StateFlow<PaintEditorUiState> = _uiState.asStateFlow()

    private val _saved = MutableStateFlow(false)

    /** Emits `true` once the paint has been stored; the screen then closes itself. */
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    init {
        if (paintId >= 0) load()
    }

    private fun load() {
        viewModelScope.launch {
            val paint: Paint? = paintRepository.getPaint(paintId)
            _uiState.value = if (paint == null) {
                PaintEditorUiState(isNew = paintId < 0)
            } else {
                PaintEditorUiState(
                    isNew = false,
                    loading = false,
                    name = paint.name,
                    color = paint.color,
                    tintingStrength = paint.tintingStrength,
                    note = paint.note.orEmpty(),
                )
            }
        }
    }

    /** Updates the paint name. */
    fun onNameChange(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }

    /** Updates the paint color. */
    fun onColorChange(color: Rgb) {
        _uiState.value = _uiState.value.copy(color = color)
    }

    /** Updates the tinting strength, clamped to the slider range. */
    fun onTintingStrengthChange(tintingStrength: Double) {
        val clamped = tintingStrength.coerceIn(
            MIN_TINTING_STRENGTH.toDouble(),
            MAX_TINTING_STRENGTH.toDouble(),
        )
        _uiState.value = _uiState.value.copy(tintingStrength = clamped)
    }

    /** Updates the optional note. */
    fun onNoteChange(note: String) {
        _uiState.value = _uiState.value.copy(note = note)
    }

    /** Stores the paint as a new one or updates the edited one; ignored for a blank name. */
    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        val name = state.name.trim()
        val note = state.note.trim().ifEmpty { null }
        viewModelScope.launch {
            if (paintId < 0) {
                paintRepository.addPaint(
                    paletteId = paletteId,
                    name = name,
                    color = state.color,
                    tintingStrength = state.tintingStrength,
                    note = note,
                )
            } else {
                val existing = paintRepository.getPaint(paintId)
                paintRepository.updatePaint(
                    Paint(
                        id = paintId,
                        paletteId = existing?.paletteId ?: paletteId,
                        name = name,
                        color = state.color,
                        tintingStrength = state.tintingStrength,
                        note = note,
                        sortOrder = existing?.sortOrder ?: 0,
                    ),
                )
            }
            _saved.value = true
        }
    }

    companion object {
        /** Route argument holding the palette id. */
        const val PALETTE_ID_ARG: String = "paletteId"

        /** Route argument holding the edited paint id; `-1` for a new paint. */
        const val PAINT_ID_ARG: String = "paintId"

        /** Creates the view model with the navigation arguments and the application container. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ColorStructionApp
                PaintEditorViewModel(
                    paintRepository = app.container.paintRepository,
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}
