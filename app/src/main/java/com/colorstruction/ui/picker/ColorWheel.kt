package com.colorstruction.ui.picker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.colorstruction.color.Hsv
import com.colorstruction.color.hsvToRgb

private val OutlineColor = Color(0x33FFFFFF)
private val SquareOutlineColor = Color(0x22000000)
private val MarkerDark = Color(0xFF1A1A1A)
private const val HUE_STOP_COUNT = 24

/** Sweep gradient stops: Compose sweeps clockwise from 3 o'clock, hue 0 is at the top. */
private val HUE_RING_STOPS: Array<Pair<Float, Color>> = Array(HUE_STOP_COUNT + 1) { index ->
    val angle = 360.0 * index / HUE_STOP_COUNT
    val hue = (angle + 90.0) % 360.0
    (index.toFloat() / HUE_STOP_COUNT) to Color(hsvToRgb(Hsv(hue, 1.0, 1.0)).toArgbInt())
}

/** Renders the fully saturated color of [hue] as a Compose [Color]. */
private fun pureHue(hue: Double): Color = Color(hsvToRgb(Hsv(hue, 1.0, 1.0)).toArgbInt())

/** Converts an [Offset] (Compose) into a [WheelPoint] (geometry). */
private fun Offset.toWheelPoint(): WheelPoint = WheelPoint(x, y)

/**
 * HSV color wheel: a hue ring around a saturation/value square.
 *
 * The zone where a press starts decides what the whole drag controls: pressing the ring
 * edits the hue only, pressing the square edits saturation/value only. Tapping outside the
 * wheel is ignored.
 *
 * @param hsv current color, hue in degrees and saturation/value in [0, 1]
 * @param onHsvChange invoked with the edited color while dragging
 * @param modifier applied to the square canvas
 */
@Composable
fun ColorWheel(
    hsv: Hsv,
    onHsvChange: (Hsv) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentHsv by rememberUpdatedState(hsv)
    val onHsvChangeLatest by rememberUpdatedState(onHsvChange)

    fun emit(geometry: WheelGeometry, zone: WheelZone, position: Offset) {
        val base = currentHsv
        when (zone) {
            WheelZone.RING -> onHsvChangeLatest(base.copy(h = geometry.hueAt(position.toWheelPoint()).toDouble()))
            WheelZone.SQUARE -> {
                val sv = geometry.svAt(position.toWheelPoint())
                onHsvChangeLatest(base.copy(s = sv.s.toDouble(), v = sv.v.toDouble()))
            }
            WheelZone.NONE -> Unit
        }
    }

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val geometry = WheelGeometry(minOf(size.width, size.height).toFloat())
                    val zone = geometry.zoneOf(down.position.toWheelPoint())
                    if (zone == WheelZone.NONE) return@awaitEachGesture
                    down.consume()
                    emit(geometry, zone, down.position)
                    val pointerId = down.id
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!change.pressed) break
                        emit(geometry, zone, change.position)
                        change.consume()
                    }
                }
            },
    ) {
        val geometry = WheelGeometry(size.minDimension)
        val center = Offset(geometry.centerX, geometry.centerY)

        // Hue ring.
        drawCircle(
            brush = Brush.sweepGradient(*HUE_RING_STOPS),
            radius = geometry.ringRadius,
            center = center,
            style = Stroke(width = geometry.ringThickness),
        )
        val outlineWidth = 1.dp.toPx()
        val outline = Stroke(width = outlineWidth)
        drawCircle(OutlineColor, geometry.outerRadius - outlineWidth / 2f, center, style = outline)
        drawCircle(OutlineColor, geometry.innerRadius + outlineWidth / 2f, center, style = outline)

        // Saturation / value square: white -> pure hue horizontally, transparent -> black vertically.
        val squareTopLeft = Offset(geometry.squareLeft, geometry.squareTop)
        val squareSize = Size(geometry.squareSize, geometry.squareSize)
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.White, pureHue(hsv.h)),
                startX = geometry.squareLeft,
                endX = geometry.squareRight,
            ),
            topLeft = squareTopLeft,
            size = squareSize,
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black),
                startY = geometry.squareTop,
                endY = geometry.squareBottom,
            ),
            topLeft = squareTopLeft,
            size = squareSize,
        )
        drawRect(
            color = SquareOutlineColor,
            topLeft = squareTopLeft,
            size = squareSize,
            style = outline,
        )

        // Markers.
        drawMarker(geometry.hueMarker(hsv.h.toFloat()), 9.dp.toPx())
        drawMarker(geometry.svMarker(hsv.s.toFloat(), hsv.v.toFloat()), 7.dp.toPx())
    }
}

/** Draws a marker: a dark outer stroke with a white inner stroke. */
private fun DrawScope.drawMarker(point: WheelPoint, radius: Float) {
    val center = Offset(point.x, point.y)
    val darkWidth = 2.dp.toPx()
    val whiteWidth = 1.dp.toPx()
    drawCircle(
        color = MarkerDark,
        radius = radius,
        center = center,
        style = Stroke(width = darkWidth),
    )
    drawCircle(
        color = Color.White,
        radius = radius - darkWidth,
        center = center,
        style = Stroke(width = whiteWidth),
    )
}
