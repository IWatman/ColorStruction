package com.colorstruction.ui.eyedropper

import com.colorstruction.color.linearToSrgbChannel
import com.colorstruction.color.srgbToLinearChannel
import kotlin.math.roundToInt

/** Radii of the square sample areas offered in the UI: 1 px, 3×3 and 5×5. */
val SAMPLE_RADII: List<Int> = listOf(0, 1, 2)

private const val OPAQUE_BLACK: Int = -0x1000000

/** sRGB byte value → linear light, precomputed once. */
private val LINEAR_CHANNEL: DoubleArray =
    DoubleArray(256) { srgbToLinearChannel(it / 255.0) }

/**
 * Averages the pixels of the square of half-size [radius] centered on ([cx], [cy]).
 *
 * The square is clamped to the image bounds, so sampling near an edge uses the pixels that
 * exist. Averaging happens in linear light (sRGB is decoded first and re-encoded at the end),
 * which is what a pigment mix of the sampled colors would look like. Returns an opaque ARGB
 * value.
 *
 * @param pixels ARGB pixels in row-major order, at least `width * height` long
 */
fun averageColor(pixels: IntArray, width: Int, height: Int, cx: Int, cy: Int, radius: Int): Int {
    require(width > 0 && height > 0) { "Image must not be empty" }
    require(pixels.size >= width * height) { "Pixel buffer is smaller than the image" }

    val r = radius.coerceAtLeast(0)
    val left = (cx - r).coerceIn(0, width - 1)
    val right = (cx + r).coerceIn(0, width - 1)
    val top = (cy - r).coerceIn(0, height - 1)
    val bottom = (cy + r).coerceIn(0, height - 1)

    var sumR = 0.0
    var sumG = 0.0
    var sumB = 0.0
    var count = 0
    for (y in top..bottom) {
        val row = y * width
        for (x in left..right) {
            val argb = pixels[row + x]
            sumR += LINEAR_CHANNEL[(argb shr 16) and 0xFF]
            sumG += LINEAR_CHANNEL[(argb shr 8) and 0xFF]
            sumB += LINEAR_CHANNEL[argb and 0xFF]
            count++
        }
    }
    if (count == 0) return OPAQUE_BLACK

    val n = count.toDouble()
    return pack(sumR / n, sumG / n, sumB / n)
}

/** Packs linear-light channels into an opaque ARGB value. */
private fun pack(r: Double, g: Double, b: Double): Int {
    val r8 = channelToByte(r)
    val g8 = channelToByte(g)
    val b8 = channelToByte(b)
    return (0xFF shl 24) or (r8 shl 16) or (g8 shl 8) or b8
}

/** Linear light → sRGB byte. */
private fun channelToByte(linear: Double): Int =
    (linearToSrgbChannel(linear.coerceIn(0.0, 1.0)) * 255.0).roundToInt().coerceIn(0, 255)
