package com.colorstruction.ui.paints

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.colorstruction.ColorStructionApp
import com.colorstruction.data.Paint
import com.colorstruction.data.PaintRepository
import com.colorstruction.data.Palette
import com.colorstruction.data.SettingsStore
import com.colorstruction.data.resolveActivePalette
import com.colorstruction.mixing.PaletteType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Snapshot of the "My paints" screen. */
data class PaintsUiState(
    val palettes: List<Palette> = emptyList(),
    val activePalette: Palette? = null,
    val paints: List<Paint> = emptyList(),
)

/** State holder of the "My paints" screen: palettes, the active palette and its paints. */
@OptIn(ExperimentalCoroutinesApi::class)
class PaintsViewModel(
    private val paintRepository: PaintRepository,
    private val settingsRepository: SettingsStore,
) : ViewModel() {

    private data class DeletedPaint(val paint: Paint, val order: List<Long>)

    private var lastDeleted: DeletedPaint? = null

    /** Current screen state, refreshed after every change in the repositories. */
    val uiState: StateFlow<PaintsUiState> = combine(
        paintRepository.observePalettes(),
        settingsRepository.settings,
    ) { palettes, settings ->
        palettes to resolveActivePalette(palettes, settings)
    }.flatMapLatest { (palettes, active) ->
        if (active == null) {
            flowOf(PaintsUiState(palettes = palettes))
        } else {
            paintRepository.observePaints(active.id).map { paints ->
                PaintsUiState(palettes = palettes, activePalette = active, paints = paints)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PaintsUiState())
    /** Makes [id] the active palette. */
    fun selectPalette(id: Long) {
        viewModelScope.launch { settingsRepository.setActivePalette(id) }
    }

    /** Creates an empty palette with [name] and makes it active; blank names are ignored. */
    fun createPalette(name: String, type: PaletteType) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val id = paintRepository.createPalette(trimmed, type)
            settingsRepository.setActivePalette(id)
        }
    }

    /** Renames the palette [id]; blank names are ignored. */
    fun renamePalette(id: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { paintRepository.renamePalette(id, trimmed) }
    }

    /** Changes how mixtures of the palette [id] are lightened. */
    fun setPaletteType(id: Long, type: PaletteType) {
        viewModelScope.launch { paintRepository.setPaletteType(id, type) }
    }

    /** Deletes the palette [id] with all its paints, falling back to another palette. */
    fun deletePalette(id: Long) {
        viewModelScope.launch {
            val wasActive = uiState.value.activePalette?.id == id
            paintRepository.deletePalette(id)
            if (wasActive) {
                settingsRepository.setActivePalette(paintRepository.getPalettes().firstOrNull()?.id)
            }
        }
    }

    /** Deletes [paint]; [undoDeletePaint] brings it back with the same fields. */
    fun deletePaint(paint: Paint) {
        viewModelScope.launch {
            val order = uiState.value.paints.map { it.id }
            paintRepository.deletePaint(paint.id)
            lastDeleted = DeletedPaint(paint, order)
        }
    }

    /** Re-adds the paint removed by the last [deletePaint] at its previous position. */
    fun undoDeletePaint() {
        val deleted = lastDeleted ?: return
        lastDeleted = null
        viewModelScope.launch {
            val paint = deleted.paint
            val newId = paintRepository.restorePaint(paint)
            val restored = deleted.order.map { if (it == paint.id) newId else it }
            paintRepository.reorderPaints(paint.paletteId, restored)
        }
    }

    /** Moves the paint at [fromIndex] to [toIndex] inside the active palette. */
    fun movePaint(fromIndex: Int, toIndex: Int) {
        val palette = uiState.value.activePalette ?: return
        val ids = uiState.value.paints.map { it.id }.toMutableList()
        if (fromIndex !in ids.indices || toIndex !in ids.indices || fromIndex == toIndex) return
        ids.add(toIndex, ids.removeAt(fromIndex))
        viewModelScope.launch { paintRepository.reorderPaints(palette.id, ids) }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        /** Creates the view model from the application container. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ColorStructionApp
                PaintsViewModel(
                    paintRepository = app.container.paintRepository,
                    settingsRepository = app.container.settingsRepository,
                )
            }
        }
    }
}
