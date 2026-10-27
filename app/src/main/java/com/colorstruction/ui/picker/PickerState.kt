package com.colorstruction.ui.picker

import com.colorstruction.color.Hsv
import com.colorstruction.color.Rgb
import com.colorstruction.color.rgbToHsv

private const val FULL_TURN = 360.0

/** Wraps [hue] in degrees into the range `[0, 360)`. */
fun normalizeHue(hue: Double): Double {
    val wrapped = hue % FULL_TURN
    return if (wrapped < 0.0) wrapped + FULL_TURN else wrapped
}

/**
 * Converts this color to [Hsv] while keeping [previousHue] when the color itself carries no
 * hue information.
 *
 * `rgb -> hsv` reports hue 0 for black, white and every gray (saturation 0), so a picker that
 * derives its hue from the color would jump to red whenever the user drags the saturation or
 * value to zero. Reusing the previous hue keeps the wheel marker stable.
 *
 * @param previousHue hue in degrees to keep for achromatic colors; wrapped into `[0, 360)`
 */
fun Rgb.toHsvKeepingHue(previousHue: Double): Hsv {
    val hsv = rgbToHsv(this)
    return if (hsv.s == 0.0) {
        Hsv(normalizeHue(previousHue), 0.0, hsv.v)
    } else {
        hsv
    }
}
