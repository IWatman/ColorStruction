package com.colorstruction.ui.mix

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
import com.colorstruction.data.*
import com.colorstruction.mixing.RecipeSolver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Default target color, a mid green. */
private const val DEFAULT_TARGET_ARGB: Int = 0xFF3D933E.toInt()

/** Key under which the target color is stored in the [SavedStateHandle]. */
private const val KEY_TARGET_ARGB = "mix_target_argb"

/**
 * Holds the mix screen state: the target color and the recipes for the active palette.
 *
 * The target is persisted in the [SavedStateHandle], solving runs on [solverDispatcher], and a
 * new target or a palette change cancels the previous solve.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MixViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val paintRepository: PaintRepository,
    private val settings: SettingsStore,
    private val solverDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val solvers = RecipeSolverCache()

    /** The settings stream, re-emitted after every change. */
    private val appSettings: Flow<AppSettings> = settings.settings

    private val _target = MutableStateFlow(
        Rgb.fromArgbInt(savedStateHandle.get<Int>(KEY_TARGET_ARGB) ?: DEFAULT_TARGET_ARGB),
    )

    init {
        // A fresh start (not a restored screen) resumes with the last color the user worked on.
        if (savedStateHandle.get<Int>(KEY_TARGET_ARGB) == null) {
            viewModelScope.launch {
                val last = appSettings.first().recentColors.firstOrNull() ?: return@launch
                if (savedStateHandle.get<Int>(KEY_TARGET_ARGB) == null) {
                    savedStateHandle[KEY_TARGET_ARGB] = last
                    _target.value = Rgb.fromArgbInt(last)
                }
            }
        }
    }

    /** A palette with its paints; [paints] is empty when there is no palette. */
    private data class PalettePaints(val palette: Palette?, val paints: List<Paint>)

    /** Everything one solve depends on. */
    private data class Request(
        val target: Rgb,
        val palette: Palette?,
        val paints: List<Paint>,
        val maxComponents: Int,
        val ratioFormat: RatioFormat,
    )

    private val activePalette: Flow<PalettePaints> = appSettings
        .map { it.activePaletteId }
        .distinctUntilChanged()
        .flatMapLatest { activeId ->
            paintRepository.observePalettes()
                .map { resolveActivePalette(it, AppSettings(activePaletteId = activeId)) }
                .distinctUntilChanged()
                .flatMapLatest { palette ->
                    if (palette == null) {
                        flowOf(PalettePaints(null, emptyList()))
                    } else {
                        paintRepository.observePaints(palette.id)
                            .map { paints -> PalettePaints(palette, paints) }
                    }
                }
        }

    private val solved: Flow<MixUiState> = combine(
        _target,
        activePalette,
        appSettings.map { it.maxComponents }.distinctUntilChanged(),
        appSettings.map { it.ratioFormat }.distinctUntilChanged(),
    ) { target, (palette, paints), maxComponents, ratioFormat ->
        Request(target, palette, paints, maxComponents, ratioFormat)
    }.debounce(DEBOUNCE_MILLIS).flatMapLatest { request ->
        flow {
            emit(request.toState(isSolving = true, recipes = emptyList()))
            val recipes = if (request.paints.isEmpty()) {
                emptyList()
            } else {
                val context = currentCoroutineContext()
                val type = requireNotNull(request.palette).type
                solve(request, solvers.get(request.paints, type)) { !context.isActive }
            }
            emit(request.toState(isSolving = false, recipes))
        }.flowOn(solverDispatcher)
    }

    /** Recently used target colors, most recent first. */
    private val recentColors: Flow<List<Int>> = appSettings
        .map { it.recentColors }
        .distinctUntilChanged()

    /** Current screen state, refreshed after every change that affects the recipes. */
    val uiState: StateFlow<MixUiState> = combine(solved, recentColors) { state, recents ->
        state.copy(recentColors = recents)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        MixUiState(_target.value),
    )

    /** Sets and persists [rgb] as the target color and records it among recent colors. */
    fun setTarget(rgb: Rgb) {
        val argb = rgb.toArgbInt()
        savedStateHandle[KEY_TARGET_ARGB] = argb
        _target.value = Rgb.fromArgbInt(argb)
        viewModelScope.launch { settings.addRecentColor(argb) }
    }

    /** Sets how many paints one recipe may use. */
    fun setMaxComponents(value: Int) {
        viewModelScope.launch { settings.setMaxComponents(value) }
    }

    /** Sets how proportions are displayed. */
    fun setRatioFormat(format: RatioFormat) {
        viewModelScope.launch { settings.setRatioFormat(format) }
    }

    private fun solve(
        request: Request,
        solver: RecipeSolver,
        isCancelled: () -> Boolean,
    ): List<RecipeUi> = solver
        .solve(request.target, request.maxComponents, RESULT_COUNT, isCancelled)
        .map { recipe -> recipeUi(recipe, request.target, request.ratioFormat, solver) }
        // Same rule as the solver, now on the rounded numbers: one extra paint costs 1.0 ΔE.
        .sortedBy { it.roundedDeltaE + it.recipe.paintCount }

    private fun Request.toState(isSolving: Boolean, recipes: List<RecipeUi>): MixUiState =
        MixUiState(target, palette, paints.size, maxComponents, ratioFormat, isSolving, recipes)

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val DEBOUNCE_MILLIS = 150L
        private const val RESULT_COUNT = 5

        /** Creates the view model from the application container. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ColorStructionApp
                MixViewModel(
                    savedStateHandle = createSavedStateHandle(),
                    paintRepository = app.container.paintRepository,
                    settings = app.container.settingsRepository,
                )
            }
        }
    }
}
