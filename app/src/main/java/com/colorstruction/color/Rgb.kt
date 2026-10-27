package com.colorstruction.color

import java.util.Locale
import kotlin.math.round

/**
 * An sRGB color whose channels are doubles in the nominal range [0, 1].
 *
 * Out-of-gamut values (for example from spectral reconstruction) are allowed and are
 * only clamped when converting to an [Int] ARGB value or to a hex string.
 */
data class Rgb(val r: Double, val g: Double, val b: Double) {

    /**
     * Converts this color to an opaque ARGB [Int] (0xFFRRGGBB). Channels are clamped to
     * [0, 1] and rounded to the nearest 8-bit value.
     */
    fun toArgbInt(): Int {
        val r8 = channelToByte(r)
        val g8 = channelToByte(g)
        val b8 = channelToByte(b)
        return (0xFF shl 24) or (r8 shl 16) or (g8 shl 8) or b8
    }

    /** Converts to linear-light RGB. */
    fun toLinearRgb(): LinearRgb = srgbToLinearRgb(this)

    /** Converts to CIE XYZ (D65). */
    fun toXyz(): Xyz = linearRgbToXyz(toLinearRgb())

    /** Converts to CIELAB (D65). */
    fun toLab(): Lab = xyzToLab(toXyz())

    /** Converts to HSV. */
    fun toHsv(): Hsv = rgbToHsv(this)

    /** Formats as an uppercase `#RRGGBB` string. Out-of-gamut channels are clamped. */
    fun toHex(): String = formatHex(this)

    companion object {
        val BLACK = Rgb(0.0, 0.0, 0.0)
        val WHITE = Rgb(1.0, 1.0, 1.0)

        /**
         * Creates a color from an opaque ARGB [Int]. The alpha byte is ignored.
         */
        fun fromArgbInt(argb: Int): Rgb = Rgb(
            r = ((argb shr 16) and 0xFF) / 255.0,
            g = ((argb shr 8) and 0xFF) / 255.0,
            b = (argb and 0xFF) / 255.0,
        )

        /**
         * Parses a hex color string.
         *
         * Accepts `#RRGGBB`, `RRGGBB` and `#RGB` (case-insensitive, optional leading `#`,
         * surrounding whitespace is ignored). Returns `null` for anything else.
         */
        fun parseHex(hex: String): Rgb? {
            val trimmed = hex.trim()
            if (trimmed.isEmpty()) return null

            val body = if (trimmed.startsWith("#")) trimmed.substring(1) else trimmed
            val expanded = when (body.length) {
                3 -> "${body[0]}${body[0]}${body[1]}${body[1]}${body[2]}${body[2]}"
                6 -> body
                else -> return null
            }
            if (!HEX_6.matches(expanded)) return null

            return Rgb(
                r = expanded.substring(0, 2).toInt(16) / 255.0,
                g = expanded.substring(2, 4).toInt(16) / 255.0,
                b = expanded.substring(4, 6).toInt(16) / 255.0,
            )
        }

        /** Formats [rgb] as an uppercase `#RRGGBB` string, clamping out-of-gamut channels. */
        fun formatHex(rgb: Rgb): String = String.format(
            Locale.ROOT,
            "#%02X%02X%02X",
            channelToByte(rgb.r),
            channelToByte(rgb.g),
            channelToByte(rgb.b),
        )
    }
}

private val HEX_6 = Regex("^[0-9a-fA-F]{6}$")

private fun channelToByte(value: Double): Int =
    round(value.coerceIn(0.0, 1.0) * 255.0).toInt()
