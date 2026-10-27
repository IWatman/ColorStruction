package com.colorstruction.ui.paints

import com.colorstruction.color.Rgb
import com.colorstruction.data.AppSettings
import com.colorstruction.data.Paint
import com.colorstruction.data.Palette
import com.colorstruction.mixing.PaletteType
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
import org.junit.Before
import org.junit.Test

/** Tests for [PaintsViewModel] backed by in-memory fakes. */
@OptIn(ExperimentalCoroutinesApi::class)
class PaintsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val opaquePalette = Palette(1L, "Opaque", PaletteType.OPAQUE, 1L)
    private val waterPalette = Palette(2L, "Water", PaletteType.WATERCOLOR, 2L)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun paint(id: Long, paletteId: Long, name: String, sortOrder: Int) = Paint(
        id = id,
        paletteId = paletteId,
        name = name,
        color = Rgb(r = 0.2, g = 0.4, b = 0.6),
        tintingStrength = 1.5,
        note = "note",
        sortOrder = sortOrder,
    )

    /** Collects the state in the background, the way the screen does. */
    private fun TestScope.observe(viewModel: PaintsViewModel): PaintsViewModel {
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun showsPaintsOfActivePalette() = runTest(dispatcher) {
        val repository = FakePaintRepository(
            palettes = listOf(opaquePalette, waterPalette),
            paints = listOf(
                paint(10L, 1L, "Red", 0),
                paint(11L, 2L, "Blue", 0),
                paint(12L, 2L, "Green", 1),
            ),
        )
        val viewModel = observe(
            PaintsViewModel(repository, FakeSettingsStore(AppSettings(activePaletteId = 2L))),
        )

        val state = viewModel.uiState.value
        assertEquals(2, state.palettes.size)
        assertEquals(2L, state.activePalette?.id)
        assertEquals(listOf("Blue", "Green"), state.paints.map { it.name })
    }

    @Test
    fun createPaletteMakesItActive() = runTest(dispatcher) {
        val settings = FakeSettingsStore(AppSettings(activePaletteId = 1L))
        val repository = FakePaintRepository(palettes = listOf(opaquePalette))
        val viewModel = observe(PaintsViewModel(repository, settings))

        viewModel.createPalette("Ink", PaletteType.WATERCOLOR)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.palettes.size)
        assertEquals("Ink", state.activePalette?.name)
        assertEquals(PaletteType.WATERCOLOR, state.activePalette?.type)
        assertEquals(state.activePalette?.id, settings.settings.first().activePaletteId)
    }

    @Test
    fun deletePaintThenUndoRestoresPaintAtSamePosition() = runTest(dispatcher) {
        val repository = FakePaintRepository(
            palettes = listOf(opaquePalette),
            paints = listOf(
                paint(10L, 1L, "Red", 0),
                paint(11L, 1L, "Blue", 1),
                paint(12L, 1L, "Green", 2),
            ),
        )
        val viewModel = observe(
            PaintsViewModel(repository, FakeSettingsStore(AppSettings(activePaletteId = 1L))),
        )

        viewModel.deletePaint(viewModel.uiState.value.paints[1])
        advanceUntilIdle()
        assertEquals(listOf("Red", "Green"), viewModel.uiState.value.paints.map { it.name })

        viewModel.undoDeletePaint()
        advanceUntilIdle()

        val paints = viewModel.uiState.value.paints
        assertEquals(listOf("Red", "Blue", "Green"), paints.map { it.name })
        assertEquals(listOf(0, 1, 2), paints.map { it.sortOrder })
        assertEquals("note", paints[1].note)
        assertEquals(1.5, paints[1].tintingStrength, 0.0001)
    }

    @Test
    fun deletePaletteFallsBackToAnotherPalette() = runTest(dispatcher) {
        val settings = FakeSettingsStore(AppSettings(activePaletteId = 1L))
        val repository = FakePaintRepository(
            palettes = listOf(opaquePalette, waterPalette),
            paints = listOf(paint(10L, 1L, "Red", 0), paint(11L, 2L, "Blue", 0)),
        )
        val viewModel = observe(PaintsViewModel(repository, settings))
        assertEquals(1L, viewModel.uiState.value.activePalette?.id)

        viewModel.deletePalette(1L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(2L), state.palettes.map { it.id })
        assertEquals(2L, state.activePalette?.id)
        assertEquals(listOf("Blue"), state.paints.map { it.name })
        assertEquals(2L, settings.settings.first().activePaletteId)
    }
}
