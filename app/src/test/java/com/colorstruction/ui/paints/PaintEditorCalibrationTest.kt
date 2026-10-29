package com.colorstruction.ui.paints

import androidx.lifecycle.SavedStateHandle
import com.colorstruction.color.Rgb
import com.colorstruction.data.Paint
import com.colorstruction.data.Palette
import com.colorstruction.mixing.Calibration
import com.colorstruction.mixing.PaletteType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Tests for the calibration part of [PaintEditorViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
class PaintEditorCalibrationTest {

    private val dispatcher = StandardTestDispatcher()

    private val palette = Palette(1L, "Opaque", PaletteType.OPAQUE, 1L)

    private val red = Paint(
        id = 10L,
        paletteId = 1L,
        name = "Red",
        color = Rgb(r = 0.8, g = 0.1, b = 0.1),
        tintingStrength = 1.0,
        note = null,
        sortOrder = 0,
    )

    private val white = Paint(
        id = 11L,
        paletteId = 1L,
        name = "White",
        color = Rgb(r = 1.0, g = 1.0, b = 1.0),
        tintingStrength = 1.0,
        note = null,
        sortOrder = 1,
    )

    private val black = Paint(
        id = 12L,
        paletteId = 1L,
        name = "Black",
        color = Rgb(r = 0.05, g = 0.05, b = 0.05),
        tintingStrength = 1.0,
        note = null,
        sortOrder = 2,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): PaintEditorViewModel = PaintEditorViewModel(
        paintRepository = FakePaintRepository(
            palettes = listOf(palette),
            paints = listOf(red, white, black),
        ),
        savedStateHandle = SavedStateHandle(
            mapOf(
                PaintEditorViewModel.PALETTE_ID_ARG to 1L,
                PaintEditorViewModel.PAINT_ID_ARG to 10L,
            ),
        ),
        computeDispatcher = dispatcher,
    )

    @Test
    fun defaultPartnerIsTheLightestOtherPaint() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        val calibration = viewModel.calibration.value
        assertEquals(listOf(11L, 12L), calibration.partners.map { it.id })
        assertEquals(11L, calibration.partner?.id)
        assertTrue(calibration.available)
        assertEquals(1, calibration.paintParts)
        assertEquals(4, calibration.partnerParts)
    }

    @Test
    fun measuredSwatchProducesAResult() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        assertNull(viewModel.calibration.value.result)

        viewModel.setMeasured(Rgb(r = 0.95, g = 0.75, b = 0.75))
        advanceUntilIdle()

        val result = viewModel.calibration.value.result
        assertNotNull(result)
        val strength = result?.strength ?: 0.0
        assertTrue(strength in Calibration.MIN_STRENGTH..Calibration.MAX_STRENGTH)
    }

    @Test
    fun applyCalibrationCopiesTheFittedStrength() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.setMeasured(Rgb(r = 0.95, g = 0.75, b = 0.75))
        advanceUntilIdle()

        val fitted = viewModel.calibration.value.result?.strength
        assertNotNull(fitted)
        viewModel.applyCalibration()

        assertEquals(fitted ?: 0.0, viewModel.uiState.value.tintingStrength, 0.0001)
    }

    @Test
    fun partsAreClampedToTheAllowedRange() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.setPaintParts(0)
        viewModel.setPartnerParts(99)
        assertEquals(MIN_CALIBRATION_PARTS, viewModel.calibration.value.paintParts)
        assertEquals(MAX_CALIBRATION_PARTS, viewModel.calibration.value.partnerParts)

        viewModel.setPaintParts(7)
        viewModel.setPartnerParts(3)
        assertEquals(7, viewModel.calibration.value.paintParts)
        assertEquals(3, viewModel.calibration.value.partnerParts)
    }
}
