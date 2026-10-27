package com.colorstruction.ui.paints

import androidx.lifecycle.SavedStateHandle
import com.colorstruction.color.Rgb
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Tests for [PaintEditorViewModel] backed by in-memory fakes. */
@OptIn(ExperimentalCoroutinesApi::class)
class PaintEditorViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val palette = Palette(1L, "Opaque", PaletteType.OPAQUE, 1L)

    private val existingPaint = Paint(
        id = 10L,
        paletteId = 1L,
        name = "Red",
        color = Rgb(r = 0.8, g = 0.1, b = 0.1),
        tintingStrength = 1.4,
        note = "warm",
        sortOrder = 0,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun handle(paletteId: Long = 1L, paintId: Long = -1L) = SavedStateHandle(
        mapOf(
            PaintEditorViewModel.PALETTE_ID_ARG to paletteId,
            PaintEditorViewModel.PAINT_ID_ARG to paintId,
        ),
    )

    /** Collects the state in the background, the way the screen does. */
    private fun TestScope.observe(viewModel: PaintEditorViewModel): PaintEditorViewModel {
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun newPaintStartsBlankWithDefaults() = runTest(dispatcher) {
        val viewModel = observe(
            PaintEditorViewModel(FakePaintRepository(palettes = listOf(palette)), handle()),
        )

        val state = viewModel.uiState.value
        assertTrue(state.isNew)
        assertEquals("", state.name)
        assertEquals(Rgb.parseHex("#808080"), state.color)
        assertEquals(1.0, state.tintingStrength, 0.0001)
        assertFalse(state.canSave)
    }

    @Test
    fun saveNewPaintAddsItWithEditedFields() = runTest(dispatcher) {
        val repository = FakePaintRepository(palettes = listOf(palette))
        val viewModel = observe(PaintEditorViewModel(repository, handle()))

        viewModel.onNameChange("Ultramarine")
        viewModel.onColorChange(Rgb(r = 0.1, g = 0.2, b = 0.7))
        viewModel.onTintingStrengthChange(1.75)
        viewModel.onNoteChange("blue")
        viewModel.save()
        advanceUntilIdle()

        val paints = repository.observePaints(1L).first()
        assertEquals(1, paints.size)
        assertEquals("Ultramarine", paints[0].name)
        assertEquals(Rgb(r = 0.1, g = 0.2, b = 0.7), paints[0].color)
        assertEquals(1.75, paints[0].tintingStrength, 0.0001)
        assertEquals("blue", paints[0].note)
        assertTrue(viewModel.saved.first())
    }

    @Test
    fun editingLoadsPaintAndSavesChanges() = runTest(dispatcher) {
        val repository = FakePaintRepository(palettes = listOf(palette), paints = listOf(existingPaint))
        val viewModel = observe(PaintEditorViewModel(repository, handle(paintId = 10L)))

        val loaded = viewModel.uiState.value
        assertFalse(loaded.isNew)
        assertEquals("Red", loaded.name)
        assertEquals(existingPaint.color, loaded.color)
        assertEquals(1.4, loaded.tintingStrength, 0.0001)
        assertEquals("warm", loaded.note)

        viewModel.onNameChange("Crimson")
        viewModel.onTintingStrengthChange(0.5)
        viewModel.onNoteChange("")
        viewModel.save()
        advanceUntilIdle()

        val stored = repository.getPaint(10L)
        assertEquals("Crimson", stored?.name)
        assertEquals(0.5, stored?.tintingStrength ?: 0.0, 0.0001)
        assertNull(stored?.note)
        assertEquals(existingPaint.color, stored?.color)
        assertEquals(1, repository.observePaints(1L).first().size)
    }

    @Test
    fun blankNameCannotBeSaved() = runTest(dispatcher) {
        val repository = FakePaintRepository(palettes = listOf(palette))
        val viewModel = observe(PaintEditorViewModel(repository, handle()))

        viewModel.onNameChange("   ")
        assertFalse(viewModel.uiState.value.canSave)
        viewModel.save()
        advanceUntilIdle()

        assertTrue(repository.observePaints(1L).first().isEmpty())
        assertFalse(viewModel.saved.first())
    }
}
