package com.colorstruction.ui.eyedropper

import kotlin.math.max
import kotlin.math.min

/** Zoom bounds of the eyedropper canvas. */
const val MIN_ZOOM: Float = 1f
const val MAX_ZOOM: Float = 8f

/** A size in pixels (screen or image). */
data class SizePx(val width: Float, val height: Float)

/** A point in pixels (screen or image). */
data class PointPx(val x: Float, val y: Float)

/**
 * Viewport of the eyedropper canvas.
 *
 * [panX]/[panY] are screen-pixel offsets of the image center from the container center;
 * [crosshairX]/[crosshairY] are in image pixels.
 */
data class EyedropperState(
    val zoom: Float = MIN_ZOOM,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val crosshairX: Float = 0f,
    val crosshairY: Float = 0f,
)

/** Scale that fits the whole image into the container (`ContentScale.Fit`), before zoom. */
fun fitScale(container: SizePx, image: SizePx): Float {
    if (container.width <= 0f || container.height <= 0f) return 0f
    if (image.width <= 0f || image.height <= 0f) return 0f
    return min(container.width / image.width, container.height / image.height)
}

/** Combined image → screen scale at [zoom]. */
fun displayScale(container: SizePx, image: SizePx, zoom: Float): Float =
    fitScale(container, image) * zoom

/** Size the image covers on screen at [zoom]. */
fun displayedSize(container: SizePx, image: SizePx, zoom: Float): SizePx {
    val scale = displayScale(container, image, zoom)
    return SizePx(image.width * scale, image.height * scale)
}

/** Top-left corner of the image in screen coordinates. */
fun EyedropperState.imageTopLeft(container: SizePx, image: SizePx): PointPx {
    val shown = displayedSize(container, image, zoom)
    return PointPx(
        x = container.width / 2f + panX - shown.width / 2f,
        y = container.height / 2f + panY - shown.height / 2f,
    )
}

/** Maps an image pixel to a container-local screen position. */
fun EyedropperState.imageToScreen(
    container: SizePx,
    image: SizePx,
    imageX: Float,
    imageY: Float,
): PointPx {
    val scale = displayScale(container, image, zoom)
    return PointPx(
        x = container.width / 2f + panX + (imageX - image.width / 2f) * scale,
        y = container.height / 2f + panY + (imageY - image.height / 2f) * scale,
    )
}

/** Inverse of [imageToScreen]. */
fun EyedropperState.screenToImage(
    container: SizePx,
    image: SizePx,
    screenX: Float,
    screenY: Float,
): PointPx {
    val scale = displayScale(container, image, zoom)
    if (scale <= 0f) return PointPx(image.width / 2f, image.height / 2f)
    return PointPx(
        x = image.width / 2f + (screenX - container.width / 2f - panX) / scale,
        y = image.height / 2f + (screenY - container.height / 2f - panY) / scale,
    )
}

/** Keeps the crosshair inside the image. */
fun EyedropperState.clampCrosshair(image: SizePx): EyedropperState = copy(
    crosshairX = crosshairX.coerceIn(0f, max(0f, image.width - 1f)),
    crosshairY = crosshairY.coerceIn(0f, max(0f, image.height - 1f)),
)
