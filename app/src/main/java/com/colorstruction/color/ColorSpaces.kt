package com.colorstruction.color

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** CIE D65 reference white used for the CIELAB conversion. */
const val LAB_WHITE_X: Double = 0.95047
const val LAB_WHITE_Y: Double = 1.0
const val LAB_WHITE_Z: Double = 1.08883

/**
 * Linear-light sRGB triplet. Values are not constrained to [0, 1] on purpose,
 * so that out-of-gamut colors survive round trips.
 */
data class LinearRgb(val r: Double, val g: Double, val b: Double)

/** CIE 1931 XYZ tristimulus values. */
data class Xyz(val x: Double, val y: Double, val z: Double)

/** CIE 1976 L*a*b* color (D65 reference white). */
data class Lab(val l: Double, val a: Double, val b: Double)

/**
 * HSV color.
 *
 * @property h hue in degrees in the range [0, 360]
 * @property s saturation in the range [0, 1]
 * @property v value (brightness) in the range [0, 1]
 */
data class Hsv(val h: Double, val s: Double, val v: Double)

// Standard sRGB (IEC 61966-2-1) to CIE XYZ D65 matrices.
private val RGB_TO_XYZ = arrayOf(
    doubleArrayOf(0.41239079926595934, 0.357584339383878, 0.1804807884018343),
    doubleArrayOf(0.21263900587151027, 0.715168678767756, 0.07219231536073371),
    doubleArrayOf(0.01933081871559182, 0.11919477979462598, 0.9505321522496607),
)

private val XYZ_TO_RGB = arrayOf(
    doubleArrayOf(3.2409699419045226, -1.537383177570094, -0.4986107602930034),
    doubleArrayOf(-0.9692436362808796, 1.8759675015077202, 0.04155505740717559),
    doubleArrayOf(0.05563007969699366, -0.20397695888897652, 1.0569715142428786),
)

private const val SRGB_ALPHA = 0.055
private const val SRGB_GAMMA = 2.4

private const val LAB_DELTA = 6.0 / 29.0
private val LAB_DELTA_CUBED = LAB_DELTA * LAB_DELTA * LAB_DELTA
private val LAB_F_INV_SLOPE = 3.0 * LAB_DELTA * LAB_DELTA

/**
 * Converts a single sRGB channel to linear light (IEC 61966-2-1 piecewise transfer).
 */
fun srgbToLinearChannel(value: Double): Double =
    if (value <= 0.04045) {
        value / 12.92
    } else {
        ((value + SRGB_ALPHA) / (1.0 + SRGB_ALPHA)).pow(SRGB_GAMMA)
    }

/**
 * Converts a single linear-light channel to sRGB (IEC 61966-2-1 piecewise transfer).
 */
fun linearToSrgbChannel(value: Double): Double =
    if (value <= 0.0031308) {
        value * 12.92
    } else {
        (1.0 + SRGB_ALPHA) * value.pow(1.0 / SRGB_GAMMA) - SRGB_ALPHA
    }

/** Converts sRGB to linear-light RGB. */
fun srgbToLinearRgb(rgb: Rgb): LinearRgb = LinearRgb(
    srgbToLinearChannel(rgb.r),
    srgbToLinearChannel(rgb.g),
    srgbToLinearChannel(rgb.b),
)

/** Converts linear-light RGB to sRGB. Out-of-gamut values are kept as-is. */
fun linearRgbToSrgb(linear: LinearRgb): Rgb = Rgb(
    linearToSrgbChannel(linear.r),
    linearToSrgbChannel(linear.g),
    linearToSrgbChannel(linear.b),
)

/** Converts linear-light RGB to CIE XYZ (D65). */
fun linearRgbToXyz(linear: LinearRgb): Xyz = Xyz(
    RGB_TO_XYZ[0][0] * linear.r + RGB_TO_XYZ[0][1] * linear.g + RGB_TO_XYZ[0][2] * linear.b,
    RGB_TO_XYZ[1][0] * linear.r + RGB_TO_XYZ[1][1] * linear.g + RGB_TO_XYZ[1][2] * linear.b,
    RGB_TO_XYZ[2][0] * linear.r + RGB_TO_XYZ[2][1] * linear.g + RGB_TO_XYZ[2][2] * linear.b,
)

/** Converts CIE XYZ (D65) to linear-light RGB. */
fun xyzToLinearRgb(xyz: Xyz): LinearRgb = LinearRgb(
    XYZ_TO_RGB[0][0] * xyz.x + XYZ_TO_RGB[0][1] * xyz.y + XYZ_TO_RGB[0][2] * xyz.z,
    XYZ_TO_RGB[1][0] * xyz.x + XYZ_TO_RGB[1][1] * xyz.y + XYZ_TO_RGB[1][2] * xyz.z,
    XYZ_TO_RGB[2][0] * xyz.x + XYZ_TO_RGB[2][1] * xyz.y + XYZ_TO_RGB[2][2] * xyz.z,
)

private fun labF(t: Double): Double =
    if (t > LAB_DELTA_CUBED) cbrt(t) else t / LAB_F_INV_SLOPE + 4.0 / 29.0

private fun labFInv(f: Double): Double {
    val f3 = f * f * f
    return if (f3 > LAB_DELTA_CUBED) f3 else (f - 4.0 / 29.0) * LAB_F_INV_SLOPE
}

/** Converts CIE XYZ to CIELAB (D65 reference white). */
fun xyzToLab(xyz: Xyz): Lab {
    val fx = labF(xyz.x / LAB_WHITE_X)
    val fy = labF(xyz.y / LAB_WHITE_Y)
    val fz = labF(xyz.z / LAB_WHITE_Z)
    return Lab(
        l = 116.0 * fy - 16.0,
        a = 500.0 * (fx - fy),
        b = 200.0 * (fy - fz),
    )
}

/** Converts CIELAB (D65 reference white) to CIE XYZ. */
fun labToXyz(lab: Lab): Xyz {
    val fy = (lab.l + 16.0) / 116.0
    val fx = fy + lab.a / 500.0
    val fz = fy - lab.b / 200.0
    return Xyz(
        x = labFInv(fx) * LAB_WHITE_X,
        y = labFInv(fy) * LAB_WHITE_Y,
        z = labFInv(fz) * LAB_WHITE_Z,
    )
}

/** Converts sRGB to HSV. */
fun rgbToHsv(rgb: Rgb): Hsv {
    val maxC = max(rgb.r, max(rgb.g, rgb.b))
    val minC = min(rgb.r, min(rgb.g, rgb.b))
    val delta = maxC - minC

    val v = maxC
    val s = if (maxC == 0.0) 0.0 else delta / maxC

    var h = when {
        delta == 0.0 -> 0.0
        maxC == rgb.r -> 60.0 * (((rgb.g - rgb.b) / delta) % 6.0)
        maxC == rgb.g -> 60.0 * ((rgb.b - rgb.r) / delta + 2.0)
        else -> 60.0 * ((rgb.r - rgb.g) / delta + 4.0)
    }
    if (h < 0.0) h += 360.0

    return Hsv(h, s, v)
}

/** Converts HSV to sRGB. */
fun hsvToRgb(hsv: Hsv): Rgb {
    val h = ((hsv.h % 360.0) + 360.0) % 360.0
    val s = hsv.s.coerceIn(0.0, 1.0)
    val v = hsv.v.coerceIn(0.0, 1.0)

    val c = v * s
    val hp = h / 60.0
    val x = c * (1.0 - abs(hp % 2.0 - 1.0))

    val (r1, g1, b1) = when (floor(hp).toInt().mod(6)) {
        0 -> Triple(c, x, 0.0)
        1 -> Triple(x, c, 0.0)
        2 -> Triple(0.0, c, x)
        3 -> Triple(0.0, x, c)
        4 -> Triple(x, 0.0, c)
        else -> Triple(c, 0.0, x)
    }

    val m = v - c
    return Rgb(r1 + m, g1 + m, b1 + m)
}
