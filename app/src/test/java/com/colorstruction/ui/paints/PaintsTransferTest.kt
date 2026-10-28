package com.colorstruction.ui.paints

import com.colorstruction.color.Rgb
import com.colorstruction.data.AppSettings
import com.colorstruction.data.Paint
import com.colorstruction.data.PaintFileData
import com.colorstruction.data.Palette
import com.colorstruction.data.PaletteFileCodec
import com.colorstruction.data.PaletteFileData
import com.colorstruction.data.PaletteFileException
import com.colorstruction.data.PaletteTransfer
import com.colorstruction.mixing.PaletteType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Tests for the palette export / import of [PaintsViewModel]. Needs Robolectric for `org.json`. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PaintsTransferTest {

    private val dispatcher = StandardTestDispatcher()
    private val opaquePalette = Palette(1L, "Opaque", PaletteType.OPAQUE, 1L)
    private val blue = Rgb.parseHex("#1234AB")!!

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun waterData() = PaletteFileData(
        name = "Water",
        type = PaletteType.WATERCOLOR,
        paints = listOf(
            PaintFileData(name = "Blue", color = blue, tintingStrength = 1.5),
            PaintFileData(name = "Green", color = Rgb.parseHex("#33AA44")!!),
        ),
    )

    private fun viewModel(repository: FakePaintRepository, settings: FakeSettingsStore) =
        PaintsViewModel(repository, settings, PaletteTransfer(repository), dispatcher)

    @Test
    fun importPaletteMakesTheNewPaletteActive() = runTest(dispatcher) {
        val repository = FakePaintRepository(palettes = listOf(opaquePalette))
        val settings = FakeSettingsStore(AppSettings(activePaletteId = 1L))
        val viewModel = viewModel(repository, settings)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.importPalette(PaletteFileCodec.encode(waterData()))
        advanceUntilIdle()

        assertEquals(PaintsEvent.Imported("Water", 2), viewModel.events.first())
        val state = viewModel.uiState.value
        assertEquals("Water", state.activePalette?.name)
        assertEquals(PaletteType.WATERCOLOR, state.activePalette?.type)
        assertEquals(listOf("Blue", "Green"), state.paints.map { it.name })
        assertEquals(state.activePalette?.id, settings.settings.first().activePaletteId)
    }

    @Test
    fun importPaletteReportsForeignFile() = runTest(dispatcher) {
        val repository = FakePaintRepository(palettes = listOf(opaquePalette))
        val settings = FakeSettingsStore(AppSettings(activePaletteId = 1L))
        val viewModel = viewModel(repository, settings)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.importPalette("definitely not a palette")
        advanceUntilIdle()

        assertEquals(
            PaintsEvent.ImportFailed(PaletteFileException.Reason.NOT_A_PALETTE),
            viewModel.events.first(),
        )
        assertEquals(1L, viewModel.uiState.value.activePalette?.id)
        assertEquals(1, viewModel.uiState.value.palettes.size)
    }

    @Test
    fun exportActivePaletteReturnsSuggestedNameAndJson() = runTest(dispatcher) {
        val repository = FakePaintRepository(
            palettes = listOf(opaquePalette),
            paints = listOf(
                Paint(
                    id = 10L,
                    paletteId = 1L,
                    name = "Red",
                    color = blue,
                    tintingStrength = 1.5,
                    note = "note",
                    sortOrder = 0,
                ),
            ),
        )
        val settings = FakeSettingsStore(AppSettings(activePaletteId = 1L))
        val viewModel = viewModel(repository, settings)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val (fileName, json) = viewModel.exportActivePalette()!!

        assertEquals("Opaque.colorstruction.json", fileName)
        val decoded = PaletteFileCodec.decode(json)
        assertEquals("Opaque", decoded.name)
        assertEquals(PaletteType.OPAQUE, decoded.type)
        assertEquals(listOf("Red"), decoded.paints.map { it.name })
        assertEquals("note", decoded.paints.single().note)
    }

    @Test
    fun exportWithoutActivePaletteReturnsNull() = runTest(dispatcher) {
        val repository = FakePaintRepository(palettes = listOf(opaquePalette))
        val viewModel = viewModel(repository, FakeSettingsStore(AppSettings(activePaletteId = null)))

        assertNull(viewModel.exportActivePalette())
    }
}
