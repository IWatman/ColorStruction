package com.colorstruction.color

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColorConversionTest {

    private fun labToRgb(lab: Lab): Rgb = linearRgbToSrgb(xyzToLinearRgb(labToXyz(lab)))

    @Test
    fun srgbToLabRoundTrip() {
        val colors = listOf(
            Rgb(0.0, 0.0, 0.0),
            Rgb(1.0, 1.0, 1.0),
            Rgb(1.0, 0.0, 0.0),
            Rgb(0.0, 1.0, 0.0),
            Rgb(0.0, 0.0, 1.0),
            Rgb(0.5, 0.5, 0.5),
            Rgb(0.2, 0.5, 0.8),
            Rgb(0.9, 0.1, 0.3),
            Rgb(0.25, 0.75, 0.5),
            Rgb(0.11, 0.22, 0.33),
        )

        for (color in colors) {
            val roundTripped = labToRgb(color.toLab())
            assertEquals("r: ${color.toHex()}", color.r, roundTripped.r, 1e-6)
            assertEquals("g: ${color.toHex()}", color.g, roundTripped.g, 1e-6)
            assertEquals("b: ${color.toHex()}", color.b, roundTripped.b, 1e-6)
        }
    }

    @Test
    fun parseHexAcceptsSupportedFormats() {
        assertEquals(Rgb(1.0, 0.0, 0.0), Rgb.parseHex("#FF0000"))
        assertEquals(Rgb(1.0, 0.0, 0.0), Rgb.parseHex("FF0000"))
        assertEquals(Rgb(1.0, 0.0, 0.0), Rgb.parseHex("#f00"))
        assertEquals(Rgb(1.0, 0.0, 0.0), Rgb.parseHex("  #ff0000  "))
        assertEquals(Rgb(0.0, 1.0, 0.0), Rgb.parseHex("#00FF00"))
        assertEquals(Rgb(0.0, 0.0, 1.0), Rgb.parseHex("#0000ff"))
        assertEquals(Rgb(1.0, 1.0, 1.0), Rgb.parseHex("#FFFFFF"))
        assertEquals(Rgb(0.0, 0.0, 0.0), Rgb.parseHex("#000000"))

        val mid = Rgb.parseHex("#123456")!!
        assertEquals(0x12 / 255.0, mid.r, 1e-12)
        assertEquals(0x34 / 255.0, mid.g, 1e-12)
        assertEquals(0x56 / 255.0, mid.b, 1e-12)
    }

    @Test
    fun parseHexRejectsInvalidInput() {
        val invalid = listOf(
            "",
            "#",
            "12345",
            "#12",
            "1234567",
            "#12345678",
            "#GGGGGG",
            "#12 456",
            "red",
            null,
        )

        for (input in invalid) {
            assertNull("input=$input", Rgb.parseHex(input ?: ""))
        }
    }

    @Test
    fun formatHexIsUppercaseAndClamps() {
        assertEquals("#FF0000", Rgb.formatHex(Rgb(1.0, 0.0, 0.0)))
        assertEquals("#000000", Rgb.formatHex(Rgb(0.0, 0.0, 0.0)))
        assertEquals("#FFFFFF", Rgb.formatHex(Rgb(1.0, 1.0, 1.0)))
        assertEquals("#804000", Rgb.formatHex(Rgb(0.5, 0.25, 0.0)))
        assertEquals("#FF0000", Rgb.formatHex(Rgb(1.5, -0.5, 0.0)))
    }

    @Test
    fun formatThenParseRoundTrip() {
        val colors = listOf(
            Rgb(0.0, 0.0, 0.0),
            Rgb(1.0, 1.0, 1.0),
            Rgb(1.0, 0.0, 0.0),
            Rgb(0.0, 1.0, 0.0),
            Rgb(0.0, 0.0, 1.0),
            Rgb(0.5, 0.25, 0.125),
        )

        for (color in colors) {
            val parsed = Rgb.parseHex(color.toHex())!!
            assertEquals(color.r, parsed.r, 1.0 / 255.0)
            assertEquals(color.g, parsed.g, 1.0 / 255.0)
            assertEquals(color.b, parsed.b, 1.0 / 255.0)
        }
    }

    @Test
    fun hsvKnownConversions() {
        assertEquals(Hsv(0.0, 1.0, 1.0), Rgb(1.0, 0.0, 0.0).toHsv())
        assertEquals(Hsv(120.0, 1.0, 1.0), Rgb(0.0, 1.0, 0.0).toHsv())
        assertEquals(Hsv(240.0, 1.0, 1.0), Rgb(0.0, 0.0, 1.0).toHsv())
        assertEquals(Hsv(0.0, 0.0, 1.0), Rgb(1.0, 1.0, 1.0).toHsv())
        assertEquals(Hsv(0.0, 0.0, 0.0), Rgb(0.0, 0.0, 0.0).toHsv())

        val gray = Rgb(0.5, 0.5, 0.5).toHsv()
        assertEquals(0.0, gray.h, 1e-12)
        assertEquals(0.0, gray.s, 1e-12)
        assertEquals(0.5, gray.v, 1e-12)
    }

    @Test
    fun hsvToRgbKnownConversions() {
        assertEquals(Rgb(1.0, 0.0, 0.0), hsvToRgb(Hsv(0.0, 1.0, 1.0)))
        assertEquals(Rgb(0.0, 1.0, 0.0), hsvToRgb(Hsv(120.0, 1.0, 1.0)))
        assertEquals(Rgb(0.0, 0.0, 1.0), hsvToRgb(Hsv(240.0, 1.0, 1.0)))
        assertEquals(Rgb(1.0, 0.0, 0.0), hsvToRgb(Hsv(360.0, 1.0, 1.0)))
        assertEquals(Rgb(0.5, 0.5, 0.5), hsvToRgb(Hsv(0.0, 0.0, 0.5)))
    }

    @Test
    fun hsvRoundTrip() {
        val colors = listOf(
            Rgb(0.0, 0.0, 0.0),
            Rgb(1.0, 1.0, 1.0),
            Rgb(1.0, 0.0, 0.0),
            Rgb(0.0, 1.0, 0.0),
            Rgb(0.0, 0.0, 1.0),
            Rgb(0.5, 0.5, 0.5),
            Rgb(0.2, 0.5, 0.8),
            Rgb(0.9, 0.1, 0.3),
            Rgb(0.25, 0.75, 0.5),
            Rgb(0.11, 0.22, 0.33),
        )

        for (color in colors) {
            val roundTripped = hsvToRgb(color.toHsv())
            assertEquals("r: ${color.toHex()}", color.r, roundTripped.r, 1e-9)
            assertEquals("g: ${color.toHex()}", color.g, roundTripped.g, 1e-9)
            assertEquals("b: ${color.toHex()}", color.b, roundTripped.b, 1e-9)
        }
    }
}
