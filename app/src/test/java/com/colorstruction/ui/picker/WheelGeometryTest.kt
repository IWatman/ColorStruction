package com.colorstruction.ui.picker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WheelGeometryTest {

    private val geometry = WheelGeometry(1000f)
    private val tolerance = 1e-2f
    private val angleTolerance = 0.01f

    private fun around(dist: Float, degrees: Double): WheelPoint {
        val rad = Math.toRadians(degrees)
        return WheelPoint(
            x = geometry.centerX + dist * Math.cos(rad).toFloat(),
            y = geometry.centerY + dist * Math.sin(rad).toFloat(),
        )
    }

    @Test
    fun hueIsZeroAtTopAndIncreasesClockwise() {
        val radius = geometry.ringRadius
        assertEquals(0f, geometry.hueAt(around(radius, -90.0)), angleTolerance)
        assertEquals(90f, geometry.hueAt(around(radius, 0.0)), angleTolerance)
        assertEquals(180f, geometry.hueAt(around(radius, 90.0)), angleTolerance)
        assertEquals(270f, geometry.hueAt(around(radius, 180.0)), angleTolerance)
    }

    @Test
    fun hueMarkerRoundTripsThroughHueAt() {
        for (hue in listOf(0f, 15f, 45f, 90f, 180f, 270f, 359f)) {
            val marker = geometry.hueMarker(hue)
            val dx = marker.x - geometry.centerX
            val dy = marker.y - geometry.centerY
            assertEquals(geometry.ringRadius, Math.hypot(dx.toDouble(), dy.toDouble()).toFloat(), tolerance)
            val back = geometry.hueAt(marker)
            val difference = Math.abs(((back - hue) + 540f) % 360f - 180f)
            assertEquals("hue $hue", 0f, difference, angleTolerance)
        }
    }

    @Test
    fun hueMarkerZeroIsAtTheTop() {
        val marker = geometry.hueMarker(0f)
        assertEquals(geometry.centerX, marker.x, tolerance)
        assertEquals(geometry.centerY - geometry.ringRadius, marker.y, tolerance)
    }

    @Test
    fun zoneClassification() {
        val center = WheelPoint(geometry.centerX, geometry.centerY)
        assertEquals(WheelZone.SQUARE, geometry.zoneOf(center))

        val onRing = WheelPoint(geometry.centerX, geometry.centerY - geometry.ringRadius)
        assertEquals(WheelZone.RING, geometry.zoneOf(onRing))

        val outerEdge = WheelPoint(geometry.centerX - geometry.outerRadius, geometry.centerY)
        assertEquals(WheelZone.RING, geometry.zoneOf(outerEdge))

        assertEquals(WheelZone.NONE, geometry.zoneOf(WheelPoint(0f, 0f)))
        assertEquals(WheelZone.NONE, geometry.zoneOf(WheelPoint(1000f, 1000f)))
    }

    @Test
    fun ringAndSquareDoNotOverlap() {
        assertEquals(geometry.size * 0.12f, geometry.ringThickness, tolerance)
        assertEquals(geometry.outerRadius - geometry.ringThickness, geometry.innerRadius, tolerance)
        assertTrue(geometry.squareHalfSize < geometry.innerRadius)
    }

    @Test
    fun svAtCornersOfTheSquare() {
        val topLeft = geometry.svAt(WheelPoint(geometry.squareLeft, geometry.squareTop))
        assertEquals(0f, topLeft.s, tolerance)
        assertEquals(1f, topLeft.v, tolerance)

        val topRight = geometry.svAt(WheelPoint(geometry.squareRight, geometry.squareTop))
        assertEquals(1f, topRight.s, tolerance)
        assertEquals(1f, topRight.v, tolerance)

        val bottomLeft = geometry.svAt(WheelPoint(geometry.squareLeft, geometry.squareBottom))
        assertEquals(0f, bottomLeft.s, tolerance)
        assertEquals(0f, bottomLeft.v, tolerance)

        val bottomRight = geometry.svAt(WheelPoint(geometry.squareRight, geometry.squareBottom))
        assertEquals(1f, bottomRight.s, tolerance)
        assertEquals(0f, bottomRight.v, tolerance)
    }

    @Test
    fun svAtClampsOutsideTheSquare() {
        val outsideRight = geometry.svAt(WheelPoint(geometry.squareRight + 500f, geometry.centerY))
        assertEquals(1f, outsideRight.s, tolerance)

        val outsideLeft = geometry.svAt(WheelPoint(geometry.squareLeft - 500f, geometry.centerY))
        assertEquals(0f, outsideLeft.s, tolerance)

        val aboveTop = geometry.svAt(WheelPoint(geometry.centerX, geometry.squareTop - 500f))
        assertEquals(1f, aboveTop.v, tolerance)

        val belowBottom = geometry.svAt(WheelPoint(geometry.centerX, geometry.squareBottom + 500f))
        assertEquals(0f, belowBottom.v, tolerance)
    }

    @Test
    fun svMarkerRoundTripsThroughSvAt() {
        for (s in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            for (v in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
                val back = geometry.svAt(geometry.svMarker(s, v))
                assertEquals("s for ($s, $v)", s, back.s, tolerance)
                assertEquals("v for ($s, $v)", v, back.v, tolerance)
            }
        }
    }

    @Test
    fun markersStayInsideTheCanvas() {
        val marker = geometry.svMarker(0f, 1f)
        assertEquals(geometry.squareLeft, marker.x, tolerance)
        assertEquals(geometry.squareTop, marker.y, tolerance)
    }
}
