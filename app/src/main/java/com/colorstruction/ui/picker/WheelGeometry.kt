package com.colorstruction.ui.picker

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A point in canvas pixel coordinates, origin at the top-left corner. */
internal data class WheelPoint(val x: Float, val y: Float)

/** A saturation/value pair, both clamped to [0, 1]. */
internal data class Sv(val s: Float, val v: Float)

/** Region of the wheel in which a touch started. */
internal enum class WheelZone { RING, SQUARE, NONE }

private const val RING_THICKNESS_FRACTION = 0.12f
private const val SQUARE_GAP_FRACTION = 0.02f
private const val FULL_TURN = 360.0
private val SQRT2: Float = sqrt(2f)

/**
 * Pure geometry of the color wheel for a square canvas of [size] pixels.
 *
 * The hue ring occupies the outer 12% of the diameter; hue 0 (red) is at the top and hue
 * increases clockwise. The saturation/value square is the largest axis-aligned square that
 * fits inside the inner radius, with a small gap. Inside the square saturation grows
 * left to right (0..1) and value grows bottom to top (0..1).
 */
internal class WheelGeometry(val size: Float) {

    val centerX: Float = size / 2f
    val centerY: Float = size / 2f

    /** Radius of the outer edge of the hue ring. */
    val outerRadius: Float = size / 2f

    /** Thickness of the hue ring. */
    val ringThickness: Float = size * RING_THICKNESS_FRACTION

    /** Radius of the inner edge of the hue ring. */
    val innerRadius: Float = outerRadius - ringThickness

    /** Radius through the middle of the hue ring, where the hue marker sits. */
    val ringRadius: Float = (outerRadius + innerRadius) / 2f

    /** Half side length of the saturation/value square. */
    val squareHalfSize: Float = (innerRadius - size * SQUARE_GAP_FRACTION) / SQRT2

    /** Side length of the saturation/value square. */
    val squareSize: Float = squareHalfSize * 2f

    val squareLeft: Float = centerX - squareHalfSize
    val squareTop: Float = centerY - squareHalfSize
    val squareRight: Float = centerX + squareHalfSize
    val squareBottom: Float = centerY + squareHalfSize

    /** Classifies [point] as belonging to the ring, the square, or neither. */
    fun zoneOf(point: WheelPoint): WheelZone {
        val dx = point.x - centerX
        val dy = point.y - centerY
        val distance = sqrt(dx * dx + dy * dy)
        return when {
            distance >= innerRadius && distance <= outerRadius -> WheelZone.RING
            point.x >= squareLeft && point.x <= squareRight &&
                point.y >= squareTop && point.y <= squareBottom -> WheelZone.SQUARE
            else -> WheelZone.NONE
        }
    }

    /** Hue in degrees in `[0, 360)` for [point]; 0 is at the top and grows clockwise. */
    fun hueAt(point: WheelPoint): Float {
        val degrees = Math.toDegrees(atan2((point.y - centerY).toDouble(), (point.x - centerX).toDouble()))
        val hue = (degrees + 90.0) % FULL_TURN
        return (if (hue < 0.0) hue + FULL_TURN else hue).toFloat()
    }

    /** Center point of the hue ring marker for [hue]. */
    fun hueMarker(hue: Float): WheelPoint {
        val radians = Math.toRadians((hue - 90f).toDouble())
        return WheelPoint(
            x = centerX + ringRadius * cos(radians).toFloat(),
            y = centerY + ringRadius * sin(radians).toFloat(),
        )
    }

    /** Saturation/value of [point], clamped to the square. Unclamped points clamp to its edges. */
    fun svAt(point: WheelPoint): Sv = Sv(
        s = ((point.x - squareLeft) / squareSize).coerceIn(0f, 1f),
        v = (1f - (point.y - squareTop) / squareSize).coerceIn(0f, 1f),
    )

    /** Point inside the square for saturation [s] and value [v] (both clamped to [0, 1]). */
    fun svMarker(s: Float, v: Float): WheelPoint = WheelPoint(
        x = squareLeft + s.coerceIn(0f, 1f) * squareSize,
        y = squareTop + (1f - v.coerceIn(0f, 1f)) * squareSize,
    )
}
