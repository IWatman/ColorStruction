package com.colorstruction.ui.mix

import androidx.lifecycle.SavedStateHandle
import com.colorstruction.color.DeltaE
import com.colorstruction.color.Rgb
import com.colorstruction.color.rgbMix
import com.colorstruction.data.AppSettings
import com.colorstruction.data.Paint
import com.colorstruction.data.Palette
import com.colorstruction.data.RatioFormat
import com.colorstruction.mixing.Amounts
import com.colorstruction.mixing.PaletteType
import com.colorstruction.ui.paints.FakeFavoriteRepository
import com.colorstruction.ui.paints.FakePaintRepository
import com.colorstruction.ui.paints.FakeSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Tests for [MixViewModel]: recipe solving wired to the active palette and the settings. */
@OptIn(ExperimentalCoroutinesApi::class)
class MixViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val palette = Palette(1L, "Opaque", PaletteType.OPAQUE, 1L)

    private val red = Rgb(0.8, 0.1, 0.1)
    private val yellow = Rgb(0.9, 0.8, 0.1)
    private val blue = Rgb(0.1, 0.2, 0.7)
    private val oliveTarget = Rgb(0.55, 0.45, 0.25)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun paints(): List<Paint> = listOf(
        Paint(id = 10L, paletteId = 1L, name = "Red", color = red, sortOrder = 0),
        Paint(id = 11L, paletteId = 1L, name = "Yellow", color = yellow, sortOrder = 1),
        Paint(id = 12L, paletteId = 1L, name = "Blue", color = blue, sortOrder = 2),
    )

    private fun filledRepository(): FakePaintRepository =
        FakePaintRepository(palettes = listOf(palette), paints = paints())

    private fun createViewModel(
        repository: FakePaintRepository,
        settings: FakeSettingsStore = FakeSettingsStore(AppSettings(activePaletteId = 1L)),
        favorites: FakeFavoriteRepository = FakeFavoriteRepository(),
        amountLabel: (Double) -> String = { Amounts.formatAmount(it) },
    ): MixViewModel = MixViewModel(
        savedStateHandle = SavedStateHandle(),
        paintRepository = repository,
        settings = settings,
        favoriteRepository = favorites,
        solverDispatcher = dispatcher,
        amountLabel = amountLabel,
    )

    /** Collects the state in the background, the way the screen does. */
    private fun TestScope.observe(viewModel: MixViewModel): MixViewModel {
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun freshStartResumesWithTheLastRecentColor() = runTest(dispatcher) {
        val settings = FakeSettingsStore(
            AppSettings(activePaletteId = 1L, recentColors = listOf(blue.toArgbInt(), red.toArgbInt())),
        )
        val viewModel = observe(createViewModel(filledRepository(), settings))

        assertEquals(blue.toArgbInt(), viewModel.uiState.value.target.toArgbInt())
    }

    @Test
    fun targetEqualToPaintGivesSinglePaintRecipe() = runTest(dispatcher) {
        val viewModel = observe(createViewModel(filledRepository()))
        viewModel.setTarget(blue)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(3, state.paintCount)
        assertFalse(state.isSolving)
        val first = state.recipes.first()
        assertEquals(1, first.recipe.paintCount)
        assertEquals("Blue", first.recipe.components.single().paint?.name)
        assertTrue(first.roundedDeltaE < 0.5)
    }

    @Test
    fun changingTargetResolves() = runTest(dispatcher) {
        val viewModel = observe(createViewModel(filledRepository()))
        viewModel.setTarget(blue)
        advanceUntilIdle()
        assertEquals("Blue", viewModel.uiState.value.recipes.first().singlePaintName())

        viewModel.setTarget(yellow)
        advanceUntilIdle()
        assertEquals("Yellow", viewModel.uiState.value.recipes.first().singlePaintName())
    }

    @Test
    fun emptyPaletteGivesNoRecipes() = runTest(dispatcher) {
        val viewModel = observe(createViewModel(FakePaintRepository(palettes = listOf(palette))))

        val state = viewModel.uiState.value
        assertEquals(1L, state.palette?.id)
        assertEquals(0, state.paintCount)
        assertTrue(state.recipes.isEmpty())
        assertFalse(state.isSolving)
    }

    @Test
    fun ratioFormatSwitchChangesProportionsText() = runTest(dispatcher) {
        val viewModel = observe(createViewModel(filledRepository()))
        viewModel.setTarget(oliveTarget)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.recipes.first().proportionsText.contains("%"))

        viewModel.setRatioFormat(RatioFormat.PERCENT)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(RatioFormat.PERCENT, state.ratioFormat)
        assertTrue(state.recipes.all { it.proportionsText.contains("%") })
    }

    @Test
    fun maxComponentsIsPassedToSolver() = runTest(dispatcher) {
        val settings = FakeSettingsStore(AppSettings(activePaletteId = 1L, maxComponents = 3))
        val viewModel = observe(createViewModel(filledRepository(), settings))
        viewModel.setTarget(oliveTarget)
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.maxComponents)

        viewModel.setMaxComponents(1)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.maxComponents)
        assertTrue(state.recipes.isNotEmpty())
        assertTrue(state.recipes.all { it.recipe.paintCount == 1 })
    }

    @Test
    fun setTargetRecordsRecentColors() = runTest(dispatcher) {
        val settings = FakeSettingsStore(AppSettings(activePaletteId = 1L))
        val viewModel = observe(createViewModel(filledRepository(), settings))

        viewModel.setTarget(blue)
        advanceUntilIdle()
        viewModel.setTarget(yellow)
        advanceUntilIdle()
        // Re-selecting an existing color moves it to the front without duplicating it.
        viewModel.setTarget(blue)
        advanceUntilIdle()

        assertEquals(
            listOf(blue.toArgbInt(), yellow.toArgbInt()),
            viewModel.uiState.value.recentColors,
        )
    }

    @Test
    fun saveFavoriteStoresDisplayedWeightsAndMarksTheRecipeSaved() = runTest(dispatcher) {
        val favorites = FakeFavoriteRepository()
        val viewModel = observe(createViewModel(filledRepository(), favorites = favorites))
        viewModel.setTarget(oliveTarget)
        advanceUntilIdle()

        val recipe = viewModel.uiState.value.recipes.first()
        viewModel.saveFavorite(recipe)
        advanceUntilIdle()

        val saved = favorites.favorites().first().single()
        assertEquals(oliveTarget.toArgbInt(), saved.target.toArgbInt())
        assertEquals("Opaque", saved.paletteName)
        val total = recipe.displayWeights.sum().let { if (it > 0.0) it else 1.0 }
        assertEquals(recipe.displayWeights.size, saved.components.size)
        recipe.displayWeights.forEachIndexed { index, weight ->
            assertEquals(weight / total, saved.components[index].weight, 1e-9)
        }
        assertEquals(recipe.roundedDeltaE, saved.deltaE, 1e-9)
        assertEquals(recipe.favoriteKey, saved.favoriteKey())
        assertTrue(recipe.favoriteKey in viewModel.uiState.value.savedRecipeKeys)

        // Saving the same recipe twice does not create a duplicate.
        viewModel.saveFavorite(recipe)
        advanceUntilIdle()
        assertEquals(1, favorites.favorites().first().size)
    }

    @Test
    fun amountModeLabelsSumToTheTotal() = runTest(dispatcher) {
        val settings = FakeSettingsStore(AppSettings(activePaletteId = 1L, totalAmount = 50))
        val viewModel = observe(
            createViewModel(filledRepository(), settings, amountLabel = { Amounts.formatAmount(it) + " ml" }),
        )
        viewModel.setTarget(oliveTarget)
        advanceUntilIdle()

        viewModel.setRatioFormat(RatioFormat.AMOUNT)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(RatioFormat.AMOUNT, state.ratioFormat)
        assertEquals(50, state.totalAmount)
        val recipe = state.recipes.first()
        assertEquals(recipe.recipe.components.size, recipe.componentLabels.size)
        assertTrue(recipe.componentLabels.all { it.endsWith(" ml") })
        val sum = recipe.componentLabels.sumOf { it.removeSuffix(" ml").toDouble() }
        assertEquals(state.totalAmount.toDouble(), sum, 1e-9)
        recipe.componentLabels.forEach { label ->
            // 50 ml is measured in ½ ml steps.
            assertEquals(0.0, (label.removeSuffix(" ml").toDouble() * 2.0) % 1.0, 1e-9)
        }
    }

    @Test
    fun setTotalAmountClampsToTheAllowedRange() = runTest(dispatcher) {
        val settings = FakeSettingsStore(AppSettings(activePaletteId = 1L))
        val viewModel = observe(createViewModel(filledRepository(), settings))

        viewModel.setTotalAmount(250)
        advanceUntilIdle()
        assertEquals(250, viewModel.uiState.value.totalAmount)

        viewModel.setTotalAmount(0)
        advanceUntilIdle()
        assertEquals(AppSettings.MIN_TOTAL_AMOUNT, viewModel.uiState.value.totalAmount)

        viewModel.setTotalAmount(99_999)
        advanceUntilIdle()
        assertEquals(AppSettings.MAX_TOTAL_AMOUNT, viewModel.uiState.value.totalAmount)
    }

    @Test
    fun rgbComparisonIsOffByDefault() = runTest(dispatcher) {
        val viewModel = observe(createViewModel(filledRepository()))
        viewModel.setTarget(oliveTarget)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.showRgbComparison)
        assertTrue(state.recipes.all { it.rgbResult == null && it.rgbDeltaE == null })
    }

    @Test
    fun rgbComparisonFillsRecipesWhenEnabled() = runTest(dispatcher) {
        val settings = FakeSettingsStore(
            AppSettings(activePaletteId = 1L, showRgbComparison = true),
        )
        val viewModel = observe(createViewModel(filledRepository(), settings))
        viewModel.setTarget(oliveTarget)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.showRgbComparison)
        assertTrue(state.recipes.isNotEmpty())
        assertTrue(state.recipes.all { it.rgbResult != null })

        val recipe = state.recipes.first()
        val rgbResult = requireNotNull(recipe.rgbResult)
        val expected = rgbMix(
            recipe.recipe.components.mapIndexed { index, component ->
                (component.paint?.color ?: Rgb.WHITE) to recipe.displayWeights[index]
            },
        )
        assertEquals(expected.r, rgbResult.r, 1e-9)
        assertEquals(expected.g, rgbResult.g, 1e-9)
        assertEquals(expected.b, rgbResult.b, 1e-9)
        val deltaE = requireNotNull(recipe.rgbDeltaE)
        // The view model stores the target as ARGB, so compare against the rounded target.
        val displayedTarget = Rgb.fromArgbInt(oliveTarget.toArgbInt())
        assertEquals(
            DeltaE.ciede2000(rgbResult.toLab(), displayedTarget.toLab()),
            deltaE,
            1e-9,
        )
        assertTrue(deltaE >= 0.0)
    }

    private fun RecipeUi.singlePaintName(): String? =
        recipe.components.single().paint?.name
}
