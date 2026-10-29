package com.colorstruction.mixing

import com.colorstruction.color.MixComponent
import com.colorstruction.color.Rgb
import com.colorstruction.color.Spectral
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationTest {

    private companion object {
        private val BLUE: Rgb = requireNotNull(Rgb.parseHex("#002185"))
        private val WHITE: Rgb = requireNotNull(Rgb.parseHex("#F5F3EF"))
        private const val PAINT_PARTS = 1.0
        private const val PARTNER_PARTS = 4.0

        /** Spectral prediction of blue + white with [strength] on the blue. */
        fun predict(strength: Double, partnerStrength: Double = 1.0): Rgb = Spectral.mix(
            listOf(
                MixComponent(BLUE, PAINT_PARTS, strength),
                MixComponent(WHITE, PARTNER_PARTS, partnerStrength),
            ),
        )

        fun input(
            measured: Rgb,
            currentStrength: Double = 1.0,
            paintParts: Double = PAINT_PARTS,
            partnerParts: Double = PARTNER_PARTS,
        ) = CalibrationInput(
            paint = BLUE,
            paintParts = paintParts,
            partner = WHITE,
            partnerParts = partnerParts,
            partnerStrength = 1.0,
            measured = measured,
            currentStrength = currentStrength,
        )
    }

    @Test
    fun fitRecoversWeakenedPigment() {
        val result = Calibration.fit(input(predict(0.6), currentStrength = 1.0))

        assertEquals(0.6, result.strength, 0.02)
        assertTrue("deltaEAfter=${result.deltaEAfter}", result.deltaEAfter < 0.5)
        assertTrue(
            "before=${result.deltaEBefore} after=${result.deltaEAfter}",
            result.deltaEBefore > result.deltaEAfter,
        )
        assertEquals(false, result.atLimit)
    }

    @Test
    fun fitRecoversStrengthenedPigment() {
        val result = Calibration.fit(input(predict(1.5), currentStrength = 1.0))

        assertEquals(1.5, result.strength, 0.02)
        assertTrue("deltaEAfter=${result.deltaEAfter}", result.deltaEAfter < 0.5)
        assertTrue(
            "before=${result.deltaEBefore} after=${result.deltaEAfter}",
            result.deltaEBefore > result.deltaEAfter,
        )
        assertEquals(false, result.atLimit)
    }

    @Test
    fun fitKeepsCurrentStrengthWhenSwatchMatchesPrediction() {
        val current = 1.0
        val result = Calibration.fit(input(predict(current), currentStrength = current))

        assertEquals(current, result.strength, 0.02)
        assertEquals(result.deltaEBefore, result.deltaEAfter, 1e-6)
        assertTrue("deltaEBefore=${result.deltaEBefore}", result.deltaEBefore < 0.05)
        assertEquals(result.predictedBefore, result.predictedAfter)
    }

    @Test
    fun unreachableSwatchStopsAtLimitWithoutThrowing() {
        val result = Calibration.fit(input(Rgb(1.0, 0.0, 0.0), currentStrength = 1.0))

        assertTrue(
            "strength=${result.strength} deltaEAfter=${result.deltaEAfter}",
            result.atLimit || result.deltaEAfter > 5.0,
        )
        assertTrue(result.strength >= Calibration.MIN_STRENGTH)
        assertTrue(result.strength <= Calibration.MAX_STRENGTH)
        assertTrue(result.deltaEAfter.isFinite())
    }

    @Test
    fun invalidPartsThrow() {
        assertThrows(IllegalArgumentException::class.java) {
            Calibration.fit(input(predict(1.0), paintParts = 0.0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Calibration.fit(input(predict(1.0), paintParts = -1.0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Calibration.fit(input(predict(1.0), partnerParts = 0.0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Calibration.fit(input(predict(1.0), partnerParts = -4.0))
        }
    }
}
