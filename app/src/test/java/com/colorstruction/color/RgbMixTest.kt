package com.colorstruction.color

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests for [rgbMix], the naive channel average used by the learning mode. */
class RgbMixTest {

    private val blue = Rgb.parseHex("#0000FF")!!
    private val yellow = Rgb.parseHex("#FFFF00")!!

    @Test
    fun blueAndYellowAverageToGray() {
        val mixed = rgbMix(listOf(blue to 0.5, yellow to 0.5))

        assertEquals(0.5, mixed.r, 0.01)
        assertEquals(0.5, mixed.g, 0.01)
        assertEquals(0.5, mixed.b, 0.01)
    }

    @Test
    fun singleComponentReturnsItself() {
        val mixed = rgbMix(listOf(blue to 1.0))

        assertEquals(blue.r, mixed.r, 1e-9)
        assertEquals(blue.g, mixed.g, 1e-9)
        assertEquals(blue.b, mixed.b, 1e-9)
    }

    @Test
    fun weightsAreNormalized() {
        val normalized = rgbMix(listOf(blue to 1.0, yellow to 1.0))
        val scaled = rgbMix(listOf(blue to 3.0, yellow to 3.0))

        assertEquals(normalized.r, scaled.r, 1e-9)
        assertEquals(normalized.g, scaled.g, 1e-9)
        assertEquals(normalized.b, scaled.b, 1e-9)

        // 3 : 1 in favor of blue.
        val leaningBlue = rgbMix(listOf(blue to 3.0, yellow to 1.0))
        assertEquals(0.25, leaningBlue.r, 0.01)
        assertEquals(0.25, leaningBlue.g, 0.01)
        assertEquals(0.75, leaningBlue.b, 0.01)
    }

    @Test
    fun emptyOrZeroWeightListIsBlack() {
        assertEquals(Rgb.BLACK, rgbMix(emptyList()))
        assertEquals(Rgb.BLACK, rgbMix(listOf(blue to 0.0, yellow to 0.0)))
    }
}
