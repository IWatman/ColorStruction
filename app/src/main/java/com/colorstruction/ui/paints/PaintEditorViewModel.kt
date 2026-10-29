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
import com.colorstruction.mixing.Calibration
import com.colorstruction.mixing.CalibrationInput
import com.colorstruction.mixing.CalibrationResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
const val MIN_TINTING_STRENGTH: Float = 0.1f

/** Highest tinting strength the slider offers. */
const val MAX_TINTING_STRENGTH: Float = 10f

/** Lowest number of parts a calibration mix accepts. */
const val MIN_CALIBRATION_PARTS: Int = 1

/** Highest number of parts a calibration mix accepts. */
const val MAX_CALIBRATION_PARTS: Int = 20

/**
 * Snapshot of the calibration sheet.
 *
 * @property partners other paints of the palette that can be mixed in
 * @property partner paint mixed with the edited one, usually white
 * @property paintParts parts of the edited paint in the swatch
 * @property partnerParts parts of [partner] in the swatch
 * @property measured color picked from a photo of the painted swatch
 * @property result fit for the current inputs, `null` until a color is measured
 * @property open true while the sheet is shown; kept so it reopens after picking a color
 */
data class CalibrationUiState(
    val partners: List<Paint> = emptyList(),
    val partner: Paint? = null,
    val paintParts: Int = 1,
    val partnerParts: Int = 4,
    val measured: Rgb? = null,
    val result: CalibrationResult? = null,
    val open: Boolean = false,
) {
    /** Calibration needs at least one other paint to mix with. */
    val available: Boolean get() = partners.isNotEmpty()
}

/**
 * State holder of the add / edit paint screen.
 *
 * Reads `paletteId` and `paintId` from the navigation arguments in [savedStateHandle] and
 * loads the edited paint on creation; `paintId` of `-1` means a new paint.
 */
class PaintEditorViewModel(
    private val paintRepository: PaintRepository,
    private val savedStateHandle: SavedStateHandle,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
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

    private val _calibration = MutableStateFlow(CalibrationUiState())

    /** Current calibration sheet state. */
    val calibration: StateFlow<CalibrationUiState> = _calibration.asStateFlow()

    private var fitJob: Job? = null

    init {
        if (paintId >= 0) load()
        loadPartners()
    }

    /** Loads the other paints of the palette and preselects the lightest one. */
    private fun loadPartners() {
        if (paletteId < 0) return
        viewModelScope.launch {
            val partners = paintRepository.getPaints(paletteId).filter { it.id != paintId }
            val lightest = partners.maxByOrNull { it.color.toLab().l }
            _calibration.value = _calibration.value.copy(partners = partners, partner = lightest)
            recompute()
        }
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
            recompute()
        }
    }

    /** Updates the paint name. */
    fun onNameChange(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }

    /** Updates the paint color. */
    fun onColorChange(color: Rgb) {
        _uiState.value = _uiState.value.copy(color = color)
        recompute()
    }

    /** Updates the tinting strength, clamped to the slider range. */
    fun onTintingStrengthChange(tintingStrength: Double) {
        val clamped = tintingStrength.coerceIn(
            MIN_TINTING_STRENGTH.toDouble(),
            MAX_TINTING_STRENGTH.toDouble(),
        )
        _uiState.value = _uiState.value.copy(tintingStrength = clamped)
        recompute()
    }

    /** Updates the optional note. */
    fun onNoteChange(note: String) {
        _uiState.value = _uiState.value.copy(note = note)
    }

    /** Shows or hides the calibration sheet. */
    fun setCalibrationOpen(open: Boolean) {
        _calibration.value = _calibration.value.copy(open = open)
    }

    /** Stores the swatch color read from a photo and refits the strength. */
    fun setMeasured(measured: Rgb) {
        _calibration.value = _calibration.value.copy(measured = measured)
        recompute()
    }

    /** Chooses the paint mixed with the edited one and refits. */
    fun setPartner(partner: Paint) {
        _calibration.value = _calibration.value.copy(partner = partner)
        recompute()
    }

    /** Sets the parts of the edited paint, clamped to [MIN_CALIBRATION_PARTS]..[MAX_CALIBRATION_PARTS]. */
    fun setPaintParts(parts: Int) {
        _calibration.value = _calibration.value.copy(
            paintParts = parts.coerceIn(MIN_CALIBRATION_PARTS, MAX_CALIBRATION_PARTS),
        )
        recompute()
    }

    /** Sets the parts of the partner paint, clamped to [MIN_CALIBRATION_PARTS]..[MAX_CALIBRATION_PARTS]. */
    fun setPartnerParts(parts: Int) {
        _calibration.value = _calibration.value.copy(
            partnerParts = parts.coerceIn(MIN_CALIBRATION_PARTS, MAX_CALIBRATION_PARTS),
        )
        recompute()
    }

    /** Copies the fitted strength into the editor and closes the sheet; Save still stores it. */
    fun applyCalibration() {
        val result = _calibration.value.result ?: return
        _uiState.value = _uiState.value.copy(tintingStrength = result.strength)
        _calibration.value = _calibration.value.copy(open = false)
    }

    /** Fits the strength for the current inputs on [computeDispatcher]. */
    private fun recompute() {
        fitJob?.cancel()
        val calibration = _calibration.value
        val partner = calibration.partner
        val measured = calibration.measured
        if (partner == null || measured == null) {
            _calibration.value = calibration.copy(result = null)
            return
        }
        val state = _uiState.value
        val input = CalibrationInput(
            paint = state.color,
            paintParts = calibration.paintParts.toDouble(),
            partner = partner.color,
            partnerParts = calibration.partnerParts.toDouble(),
            partnerStrength = partner.tintingStrength,
            measured = measured,
            currentStrength = state.tintingStrength,
        )
        fitJob = viewModelScope.launch {
            val result = withContext(computeDispatcher) { Calibration.fit(input) }
            _calibration.value = _calibration.value.copy(result = result)
        }
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
