package com.colorstruction.ui.eyedropper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PixelSamplingTest {

    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val red = 0xFFFF0000.toInt()

    private fun red8(argb: Int) = (argb shr 16) and 0xFF
    private fun green8(argb: Int) = (argb shr 8) and 0xFF
    private fun blue8(argb: Int) = argb and 0xFF

    /** Assertion that tolerates ±1 from the 8-bit rounding round trip. */
    private fun assertArgbbClose(expected: Int, actual: Int, tolerance: Int = 1) {
        assertEquals("alpha of ${actual.toUInt().toString(16)}", 0xFF, (actual ushr 24) and 0xFF)
        for (shift in intArrayOf(16, 8, 0)) {
            val e = (expected shr shift) and 0xFF
            val a = (actual shr shift) and 0xFF
            assertTrue("channel $shift: expected $e, got $a", abs(e - a) <= tolerance)
        }
    }

    @Test
    fun radiusZeroReturnsThePixelItself() {
        val pixels = intArrayOf(red, 0xFF336699.toInt(), black, white)
        assertEquals(red, averageColor(pixels, 2, 2, 0, 0, 0))
        assertArgbbClose(0xFF336699.toInt(), averageColor(pixels, 2, 2, 1, 0, 0))
        assertEquals(black, averageColor(pixels, 2, 2, 0, 1, 0))
        assertEquals(white, averageColor(pixels, 2, 2, 1, 1, 0))

        // Same expectation when the sample sits in the middle of a uniform image.
        val uniform = IntArray(25) { 0xFF336699.toInt() }
        assertArgbbClose(0xFF336699.toInt(), averageColor(uniform, 5, 5, 2, 2, 2))
    }

    @Test
    fun uniformAreaKeepsTheColor() {
        val uniform = IntArray(9 * 9) { 0xFF102030.toInt() }
        assertArgbbClose(0xFF102030.toInt(), averageColor(uniform, 9, 9, 4, 4, 1))
        assertArgbbClose(0xFF102030.toInt(), averageColor(uniform, 9, 9, 0, 0, 2))
    }

    @Test
    fun averageStaysOpaqueEvenWhenPixelsAreTransparent() {
        val translucent = IntArray(4) { 0x00112233 }
        assertEquals(0xFF, (averageColor(translucent, 2, 2, 0, 0, 1) ushr 24) and 0xFF)
    }

    @Test
    fun samplingNearACornerUsesOnlyExistingPixels() {
        // 4×4 black image with a single red pixel in the top-left corner.
        val pixels = IntArray(16) { black }
        pixels[0] = red
        val sampled = averageColor(pixels, 4, 4, 0, 0, 1)
        // One red and three black pixels: linear red = 0.25 → sRGB ≈ 137.
        assertTrue("red ${red8(sampled)}", abs(137 - red8(sampled)) <= 1)
        assertEquals(0, green8(sampled))
        assertEquals(0, blue8(sampled))

        // 5×5 area at the corner covers 9 pixels: 1/9 linear → sRGB ≈ 94.
        val big = averageColor(pixels, 4, 4, 0, 0, 2)
        assertTrue("red ${red8(big)}", abs(94 - red8(big)) <= 2)
    }

    @Test
    fun centerOutsideTheImageIsClamped() {
        val pixels = IntArray(4) { white }
        pixels[3] = red
        assertEquals(red, averageColor(pixels, 2, 2, 99, 99, 0))
        assertEquals(white, averageColor(pixels, 2, 2, -5, -5, 0))
    }

    @Test
    fun blackAndWhiteAverageInLinearLight() {
        val pixels = intArrayOf(black, white)
        val mixed = averageColor(pixels, 2, 1, 0, 0, 1)

        // Linear averaging gives ~188, sRGB averaging would give 127/128.
        assertTrue("expected ~188, got ${red8(mixed)}", red8(mixed) in 186..190)
        assertNotEquals(127, red8(mixed))
        assertNotEquals(128, red8(mixed))
        assertEquals(255, (mixed ushr 24) and 0xFF)
    }

    @Test
    fun averageOfADarkAndALightPixelIsBrighterThanTheArithmeticMean() {
        val dark = 0xFF404040.toInt()
        val light = 0xFFC0C0C0.toInt()
        val mixed = averageColor(intArrayOf(dark, light), 2, 1, 1, 0, 1)
        val arithmetic = (0x40 + 0xC0) / 2
        assertTrue("linear average should be brighter than $arithmetic", red8(mixed) > arithmetic)
    }

    @Test
    fun whiteBalanceTurnsThePaperIntoPureWhite() {
        val paper = 0xFFD8C8A8.toInt() // warm, slightly dark paper under lamp light
        val gains = requireNotNull(whiteBalanceGains(paper))
        val balanced = averageColor(intArrayOf(paper), 1, 1, 0, 0, 0, gains)
        assertArgbbClose(white, balanced)
    }

    @Test
    fun whiteBalanceRemovesTheColorCastFromAGray() {
        // A neutral gray photographed under the same warm light gets the same cast as the paper.
        val paper = 0xFFE0D0B0.toInt()
        val gains = requireNotNull(whiteBalanceGains(paper))
        val castGray = 0xFF706858.toInt()
        val balanced = averageColor(intArrayOf(castGray), 1, 1, 0, 0, 0, gains)
        val spread = maxOf(red8(balanced), green8(balanced), blue8(balanced)) -
            minOf(red8(balanced), green8(balanced), blue8(balanced))
        assertTrue("balanced gray should be neutral, spread $spread", spread <= 4)
    }

    @Test
    fun tooDarkSpotIsNotAcceptedAsWhite() {
        assertEquals(null, whiteBalanceGains(0xFF202020.toInt()))
        assertEquals(null, whiteBalanceGains(0xFFFFFF10.toInt()))
    }

    @Test
    fun noGainsKeepsTheOldBehaviour() {
        val pixel = 0xFF336699.toInt()
        assertEquals(averageColor(intArrayOf(pixel), 1, 1, 0, 0, 0), averageColor(intArrayOf(pixel), 1, 1, 0, 0, 0, null))
    }
}
