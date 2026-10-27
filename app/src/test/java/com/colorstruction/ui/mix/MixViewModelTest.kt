package com.colorstruction.ui.mix

import androidx.lifecycle.SavedStateHandle
import com.colorstruction.color.Rgb
import com.colorstruction.data.AppSettings
import com.colorstruction.data.Paint
import com.colorstruction.data.Palette
import com.colorstruction.data.RatioFormat
import com.colorstruction.mixing.PaletteType
import com.colorstruction.ui.paints.FakePaintRepository
import com.colorstruction.ui.paints.FakeSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
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
    ): MixViewModel = MixViewModel(
        savedStateHandle = SavedStateHandle(),
        paintRepository = repository,
        settings = settings,
        solverDispatcher = dispatcher,
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

    private fun RecipeUi.singlePaintName(): String? =
        recipe.components.single().paint?.name
}
