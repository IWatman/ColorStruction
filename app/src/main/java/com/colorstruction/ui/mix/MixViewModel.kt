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
import com.colorstruction.R
import com.colorstruction.color.Rgb
import com.colorstruction.data.*
import com.colorstruction.mixing.Amounts
import com.colorstruction.mixing.MixCorrection
import com.colorstruction.mixing.RecipeSolver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Default target color, a mid green. */
private const val DEFAULT_TARGET_ARGB: Int = 0xFF3D933E.toInt()

/** Key under which the target color is stored in the [SavedStateHandle]. */
private const val KEY_TARGET_ARGB = "mix_target_argb"

/** Key under which the measured batch color of the "fix a mix" sheet is stored. */
private const val KEY_CORRECTION_ARGB = "mix_correction_argb"

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
    private val favoriteRepository: FavoriteRepository,
    private val solverDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** Formats one amount with its unit for [RatioFormat.AMOUNT]; set from resources by [Factory]. */
    private val amountLabel: (Double) -> String = { Amounts.formatAmount(it) },
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
        val totalAmount: Int,
        val showRgbComparison: Boolean,
    )

    /** Palette and paint display settings that change how a recipe is rendered. */
    private data class Display(
        val ratioFormat: RatioFormat,
        val totalAmount: Int,
        val showRgbComparison: Boolean,
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
        appSettings.map { Display(it.ratioFormat, it.totalAmount, it.showRgbComparison) }
            .distinctUntilChanged(),
    ) { target, (palette, paints), maxComponents, display ->
        Request(
            target = target,
            palette = palette,
            paints = paints,
            maxComponents = maxComponents,
            ratioFormat = display.ratioFormat,
            totalAmount = display.totalAmount,
            showRgbComparison = display.showRgbComparison,
        )
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

    private val _correctionMix = MutableStateFlow(
        savedStateHandle.get<Int>(KEY_CORRECTION_ARGB)?.let(Rgb::fromArgbInt),
    )

    /** Suggestions for fixing the measured batch, re-searched when the target or palette changes. */
    val correction: StateFlow<CorrectionUiState> = combine(
        _target,
        _correctionMix,
        activePalette,
    ) { target, mix, palettePaints -> Triple(target, mix, palettePaints) }
        .flatMapLatest { (target, mix, palettePaints) ->
            val palette = palettePaints.palette
            if (mix == null || palette == null) {
                flowOf(CorrectionUiState(mix))
            } else {
                flow {
                    emit(CorrectionUiState(mix, isComputing = true))
                    val outcome = MixCorrection.correct(
                        current = mix,
                        target = target,
                        paints = palettePaints.paints.map { it.toSolverPaint() },
                        paletteType = palette.type,
                    )
                    emit(CorrectionUiState(mix, outcome = outcome))
                }.flowOn(solverDispatcher)
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            CorrectionUiState(_correctionMix.value),
        )

    /** Sets the measured color of the existing batch for the "fix a mix" sheet. */
    fun setCorrectionMix(rgb: Rgb) {
        savedStateHandle[KEY_CORRECTION_ARGB] = rgb.toArgbInt()
        _correctionMix.value = rgb
    }

    /** Recently used target colors, most recent first. */
    private val recentColors: Flow<List<Int>> = appSettings
        .map { it.recentColors }
        .distinctUntilChanged()

    /** Keys of the recipes that are already stored as favorites. */
    private val savedRecipeKeys: Flow<Set<String>> = favoriteRepository.favorites()
        .map { favorites -> favorites.map { it.favoriteKey() }.toSet() }
        .distinctUntilChanged()

    /** Current screen state, refreshed after every change that affects the recipes. */
    val uiState: StateFlow<MixUiState> = combine(
        solved,
        recentColors,
        savedRecipeKeys,
    ) { state, recents, keys ->
        state.copy(recentColors = recents, savedRecipeKeys = keys)
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

    /** Sets the total volume of a recipe in millilitres. */
    fun setTotalAmount(value: Int) {
        viewModelScope.launch { settings.setTotalAmount(value) }
    }

    /**
     * Stores [recipe] as a favorite with the shares that are displayed, for the current target.
     * Saving the same recipe twice is a no-op.
     */
    fun saveFavorite(recipe: RecipeUi) {
        if (recipe.favoriteKey in uiState.value.savedRecipeKeys) return
        val target = _target.value
        val paletteName = uiState.value.palette?.name.orEmpty()
        val total = recipe.displayWeights.sum().let { if (it > 0.0) it else 1.0 }
        val snapshot = recipe.recipe.copy(
            components = recipe.recipe.components.mapIndexed { index, component ->
                component.copy(weight = recipe.displayWeights.getOrElse(index) { 0.0 } / total)
            },
            result = recipe.roundedResult,
            deltaE = recipe.roundedDeltaE,
        ).toFavorite(target = target, paletteName = paletteName)
        viewModelScope.launch { favoriteRepository.add(snapshot) }
    }

    private fun solve(
        request: Request,
        solver: RecipeSolver,
        isCancelled: () -> Boolean,
    ): List<RecipeUi> = solver
        .solve(request.target, request.maxComponents, RESULT_COUNT, isCancelled)
        .map { recipe ->
            recipeUi(
                recipe = recipe,
                target = request.target,
                ratioFormat = request.ratioFormat,
                solver = solver,
                totalAmount = request.totalAmount,
                showRgbComparison = request.showRgbComparison,
                amountLabel = amountLabel,
            )
        }
        // Same rule as the solver, now on the rounded numbers: one extra paint costs 1.0 ΔE.
        .sortedBy { it.roundedDeltaE + it.recipe.paintCount }

    private fun Request.toState(isSolving: Boolean, recipes: List<RecipeUi>): MixUiState =
        MixUiState(
            target = target,
            palette = palette,
            paintCount = paints.size,
            maxComponents = maxComponents,
            ratioFormat = ratioFormat,
            totalAmount = totalAmount,
            isSolving = isSolving,
            showRgbComparison = showRgbComparison,
            recipes = recipes,
        )

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
                    favoriteRepository = app.container.favoriteRepository,
                )
            }
        }
    }
}
