package com.colorstruction.color

/**
 * Naive "screen mixing": the weighted average of the sRGB channels of [components].
 *
 * This is what averaging the channel values of the paints would give, which is not how
 * pigments behave — blue and yellow average to gray instead of green. The weights are
 * normalized, so only their ratios matter. Returns [Rgb.BLACK] for an empty list or when
 * the weights sum to zero.
 *
 * @param components colors paired with their non-negative weights.
 */
fun rgbMix(components: List<Pair<Rgb, Double>>): Rgb {
    val total = components.sumOf { it.second }
    if (components.isEmpty() || total <= 0.0) return Rgb.BLACK
    var r = 0.0
    var g = 0.0
    var b = 0.0
    components.forEach { (color, weight) ->
        val share = weight / total
        r += color.r * share
        g += color.g * share
        b += color.b * share
    }
    return Rgb(r, g, b)
}
