package com.colorstruction.ui.eyedropper

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/** Draws [image] at [placement] with [shown] size using the given [filterQuality]. */
fun DrawScope.drawFittedImage(
    image: ImageBitmap,
    placement: PointPx,
    shown: SizePx,
    filterQuality: FilterQuality = FilterQuality.Low,
) {
    val dstWidth = shown.width.roundToInt().coerceAtLeast(1)
    val dstHeight = shown.height.roundToInt().coerceAtLeast(1)
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(placement.x.roundToInt(), placement.y.roundToInt()),
        dstSize = IntSize(dstWidth, dstHeight),
        filterQuality = filterQuality,
    )
}

/** Draws the sampling crosshair: a ring plus four arms, dark underneath and white on top. */
fun DrawScope.drawCrosshair(
    center: Offset,
    radius: Float,
    arm: Float,
    dark: Color = Color.Black,
    light: Color = Color.White,
) {
    val darkWidth = radius * 0.3f
    val lightWidth = radius * 0.14f
    drawCircle(dark, radius = radius, center = center, style = Stroke(width = darkWidth))
    drawCircle(light, radius = radius, center = center, style = Stroke(width = lightWidth))
    for (direction in listOf(Offset(1f, 0f), Offset(-1f, 0f), Offset(0f, 1f), Offset(0f, -1f))) {
        val inner = center + direction * (radius * 0.55f)
        val outer = center + direction * (radius + arm)
        drawLine(dark, inner, outer, strokeWidth = darkWidth)
        drawLine(light, inner, outer, strokeWidth = lightWidth)
    }
}

/**
 * Draws the magnifier: the image around the crosshair scaled by [magnification] with
 * nearest-neighbour filtering, clipped to a circle centered on [center].
 */
fun DrawScope.drawLoupe(
    image: ImageBitmap,
    placement: PointPx,
    shown: SizePx,
    crosshair: Offset,
    center: Offset,
    radius: Float,
    magnification: Float,
    background: Color,
    markerSize: Float,
    dark: Color = Color.Black,
    light: Color = Color.White,
) {
    val bounds = Rect(
        left = center.x - radius,
        top = center.y - radius,
        right = center.x + radius,
        bottom = center.y + radius,
    )
    drawCircle(background, radius = radius, center = center)
    clipPath(Path().apply { addOval(bounds) }) {
        withTransform({
            translate(center.x, center.y)
            scale(magnification, magnification, pivot = Offset.Zero)
            translate(-crosshair.x, -crosshair.y)
        }) {
            drawFittedImage(image, placement, shown, FilterQuality.None)
        }
    }
    val half = markerSize / 2f
    val markerTopLeft = Offset(center.x - half, center.y - half)
    val markerArea = Size(markerSize, markerSize)
    drawRect(dark, markerTopLeft, markerArea, style = Stroke(width = markerSize * 0.22f))
    drawRect(light, markerTopLeft, markerArea, style = Stroke(width = markerSize * 0.1f))
    drawCircle(dark, radius = radius, center = center, style = Stroke(width = radius * 0.1f))
    drawCircle(light, radius = radius, center = center, style = Stroke(width = radius * 0.04f))
}
