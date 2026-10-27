package com.colorstruction.color

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpectralTest {

    private fun deltaE(a: Rgb, b: Rgb): Double = DeltaE.ciede2000(a.toLab(), b.toLab())

    @Test
    fun mixWithItselfReturnsSameColor() {
        val colors = listOf(
            Rgb.parseHex("#002185")!!,
            Rgb.parseHex("#FCD200")!!,
            Rgb.parseHex("#FF0000")!!,
            Rgb.parseHex("#00FF00")!!,
            Rgb.parseHex("#3375DA")!!,
            Rgb(0.3, 0.6, 0.2),
        )

        for (color in colors) {
            val mixed = Spectral.mix(listOf(color to 1.0, color to 1.0))
            assertTrue(
                "${color.toHex()} -> ${mixed.toHex()}",
                deltaE(color, mixed) < 0.5,
            )
        }
    }

    @Test
    fun zeroWeightComponentIsIgnored() {
        val first = Rgb.parseHex("#E53166")!!
        val second = Rgb.parseHex("#002185")!!
        val mixed = Spectral.mix(listOf(first to 1.0, second to 0.0))
        assertTrue(
            "${first.toHex()} -> ${mixed.toHex()}",
            deltaE(first, mixed) < 0.5,
        )
    }

    @Test
    fun bluePlusYellowIsGreen() {
        val blue = Rgb.parseHex("#0000FF")!!
        val yellow = Rgb.parseHex("#FCD200")!!
        val mixed = Spectral.mix(listOf(blue to 1.0, yellow to 1.0))

        val hue = mixed.toHsv().h
        assertTrue("hue should be green, was $hue", hue >= 70.0 && hue <= 170.0)

        val average = Rgb(
            r = (blue.r + yellow.r) / 2.0,
            g = (blue.g + yellow.g) / 2.0,
            b = (blue.b + yellow.b) / 2.0,
        )
        assertTrue("should differ from RGB average", deltaE(mixed, average) > 10.0)
    }

    @Test
    fun mixingWithWhiteRaisesLightness() {
        val red = Rgb.parseHex("#FF0000")!!
        val mixed = Spectral.mix(listOf(red to 1.0, Rgb.WHITE to 1.0))
        assertTrue(mixed.toLab().l > red.toLab().l)
    }

    @Test
    fun reflectanceRoundTripIsCloseForTenColors() {
        val colors = listOf(
            "#FF0000", "#00FF00", "#0000FF", "#FFFFFF", "#000000",
            "#002185", "#FCD200", "#3375DA", "#E53166", "#804000",
        ).map { Rgb.parseHex(it)!! }

        for (color in colors) {
            val back = Spectral.reflectanceToRgb(Spectral.reflectance(color))
            assertTrue(
                "${color.toHex()} -> ${back.toHex()}",
                deltaE(color, back) < 1.0,
            )
        }
    }

    @Test
    fun reflectanceHasThirtyEightBands() {
        assertEquals(38, Spectral.reflectance(Rgb.parseHex("#336699")!!).size)
        assertEquals(38, Spectral.ks(DoubleArray(38) { 0.5 }).size)
    }

    // Reference vectors from the spectral.js README (https://github.com/rvanwijnen/spectral.js).

    @Test
    fun spectralJsReadmeBlueYellowVector() {
        val mixed = Spectral.mix(
            listOf(
                Rgb.parseHex("#002185")!! to 0.5,
                Rgb.parseHex("#FCD200")!! to 0.5,
            ),
        )
        assertEquals("#3D933E", mixed.toHex())
    }

    @Test
    fun spectralJsReadmeThreeColorVector() {
        val mixed = Spectral.mix(
            listOf(
                Rgb.parseHex("#FCF046")!! to 1.0,
                Rgb.parseHex("#E53166")!! to 1.0,
                Rgb.parseHex("#3375DA")!! to 1.0,
            ),
        )
        assertEquals("#8D7964", mixed.toHex())
    }

    @Test
    fun spectralJsReadmeTintingStrengthVector() {
        val red = Rgb.parseHex("#ff0000")!!
        val yellow = Rgb.parseHex("#ffff00")!!

        val plain = Spectral.mix(listOf(red to 0.5, yellow to 0.5))
        assertEquals("#FF440F", plain.toHex())

        val tinted = Spectral.mix(
            listOf(
                MixComponent(red, 0.5, tintingStrength = 0.35),
                MixComponent(yellow, 0.5, tintingStrength = 1.0),
            ),
        )
        // spectral.js prints #FF8427 here after OKLab gamut mapping; its raw sRGB is
        // [274, 131, 30]. This port clamps out-of-gamut channels instead, so expect #FF831E.
        assertEquals("#FF831E", tinted.toHex())
    }
}
