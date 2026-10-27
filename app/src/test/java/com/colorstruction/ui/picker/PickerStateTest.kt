package com.colorstruction.ui.picker

import com.colorstruction.color.Hsv
import com.colorstruction.color.Rgb
import com.colorstruction.color.hsvToRgb
import org.junit.Assert.assertEquals
import org.junit.Test

private const val EPS = 1e-9

class PickerStateTest {

    private fun assertRgbEquals(expected: Rgb, actual: Rgb, tolerance: Double = 1e-9) {
        assertEquals("r", expected.r, actual.r, tolerance)
        assertEquals("g", expected.g, actual.g, tolerance)
        assertEquals("b", expected.b, actual.b, tolerance)
    }

    @Test
    fun `black keeps previous hue`() {
        val hsv = Rgb.BLACK.toHsvKeepingHue(210.0)
        assertEquals(210.0, hsv.h, EPS)
        assertEquals(0.0, hsv.s, EPS)
        assertEquals(0.0, hsv.v, EPS)
    }

    @Test
    fun `white keeps previous hue`() {
        val hsv = Rgb.WHITE.toHsvKeepingHue(42.5)
        assertEquals(42.5, hsv.h, EPS)
        assertEquals(0.0, hsv.s, EPS)
        assertEquals(1.0, hsv.v, EPS)
    }

    @Test
    fun `gray keeps previous hue`() {
        val gray = Rgb(0.5, 0.5, 0.5)
        val hsv = gray.toHsvKeepingHue(300.0)
        assertEquals(300.0, hsv.h, EPS)
        assertEquals(0.0, hsv.s, EPS)
        assertEquals(0.5, hsv.v, EPS)
    }

    @Test
    fun `previous hue is wrapped into a single turn`() {
        val hsv = Rgb.BLACK.toHsvKeepingHue(450.0)
        assertEquals(90.0, hsv.h, EPS)
        val negative = Rgb.BLACK.toHsvKeepingHue(-30.0)
        assertEquals(330.0, negative.h, EPS)
    }

    @Test
    fun `chromatic colors report their own hue`() {
        val red = Rgb(1.0, 0.0, 0.0).toHsvKeepingHue(180.0)
        assertEquals(0.0, red.h, EPS)
        assertEquals(1.0, red.s, EPS)
        assertEquals(1.0, red.v, EPS)

        val green = Rgb(0.0, 1.0, 0.0).toHsvKeepingHue(180.0)
        assertEquals(120.0, green.h, 1e-9)

        val blue = Rgb(0.0, 0.0, 1.0).toHsvKeepingHue(180.0)
        assertEquals(240.0, blue.h, 1e-9)
    }

    @Test
    fun `hsv converts back to the same color`() {
        val colors = listOf(
            Rgb(1.0, 0.0, 0.0),
            Rgb(0.24, 0.58, 0.24),
            Rgb(0.1, 0.9, 0.4),
            Rgb(0.75, 0.75, 0.2),
            Rgb.BLACK,
            Rgb.WHITE,
            Rgb(0.5, 0.5, 0.5),
        )
        for (color in colors) {
            val hsv: Hsv = color.toHsvKeepingHue(123.0)
            assertRgbEquals(color, hsvToRgb(hsv), 1e-9)
        }
    }

    @Test
    fun `round trip keeps the hue of a chromatic color`() {
        val color = Rgb.fromArgbInt(0xFF3D933E.toInt())
        val hsv = color.toHsvKeepingHue(0.0)
        val again = hsvToRgb(hsv).toHsvKeepingHue(hsv.h)
        assertEquals(hsv.h, again.h, 1e-6)
        assertEquals(hsv.s, again.s, 1e-6)
        assertEquals(hsv.v, again.v, 1e-6)
        assertEquals(Rgb.formatHex(color), Rgb.formatHex(hsvToRgb(hsv)))
    }

    @Test
    fun `zero saturation keeps hue through a value change`() {
        val desaturated = hsvToRgb(Hsv(h = 275.0, s = 0.0, v = 0.8))
        val hsv = desaturated.toHsvKeepingHue(275.0)
        assertEquals(275.0, hsv.h, EPS)
        assertEquals(0.8, hsv.v, 1e-9)
    }
}
