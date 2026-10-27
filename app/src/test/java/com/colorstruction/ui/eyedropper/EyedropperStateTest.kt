package com.colorstruction.ui.eyedropper

import org.junit.Assert.assertEquals
import org.junit.Test

class EyedropperStateTest {

    private val container = SizePx(1000f, 500f)
    private val image = SizePx(200f, 100f)
    private val tolerance = 1e-3f

    @Test
    fun imageCenterMapsToContainerCenter() {
        val state = EyedropperState()
        val screen = state.imageToScreen(container, image, 100f, 50f)
        assertEquals(500f, screen.x, tolerance)
        assertEquals(250f, screen.y, tolerance)
        assertEquals(5f, fitScale(container, image), tolerance)
    }

    @Test
    fun fitUsesTheTighterAxis() {
        // A portrait image in a landscape container is limited by height.
        val portrait = SizePx(100f, 250f)
        assertEquals(2f, fitScale(container, portrait), tolerance)

        val shown = displayedSize(container, portrait, 3f)
        assertEquals(600f, shown.width, tolerance)
        assertEquals(1500f, shown.height, tolerance)
    }

    @Test
    fun zoomDoublesDistancesFromTheCenter() {
        val point = PointPx(120f, 60f) // 20, 10 px right/below the image center
        val atOne = EyedropperState(zoom = 1f).imageToScreen(container, image, point.x, point.y)
        val atTwo = EyedropperState(zoom = 2f).imageToScreen(container, image, point.x, point.y)
        assertEquals(100f, atOne.x - 500f, tolerance)
        assertEquals(50f, atOne.y - 250f, tolerance)
        assertEquals(2 * (atOne.x - 500f), atTwo.x - 500f, tolerance)
        assertEquals(2 * (atOne.y - 250f), atTwo.y - 250f, tolerance)
    }

    @Test
    fun screenToImageInvertsImageToScreen() {
        val state = EyedropperState(zoom = 3.5f, panX = -40f, panY = 25f)
        for (x in listOf(0f, 37f, 100f, 199f)) {
            for (y in listOf(0f, 12f, 50f, 99f)) {
                val screen = state.imageToScreen(container, image, x, y)
                val back = state.screenToImage(container, image, screen.x, screen.y)
                assertEquals("x $x/$y", x, back.x, tolerance)
                assertEquals("y $x/$y", y, back.y, tolerance)
            }
        }
    }

    @Test
    fun panCannotMoveTheImageAwayAtZoomOne() {
        val panned = EyedropperState().panBy(container, image, 400f, -400f)
        assertEquals(0f, panned.panX, tolerance)
        assertEquals(0f, panned.panY, tolerance)
    }

    @Test
    fun panIsClampedToTheImageEdgesWhenZoomedIn() {
        val zoomed = EyedropperState(zoom = 4f)
        assertEquals(1500f, zoomed.maxPan(container, image).x, tolerance)
        assertEquals(750f, zoomed.maxPan(container, image).y, tolerance)

        val far = zoomed.panBy(container, image, 10_000f, -10_000f)
        assertEquals(1500f, far.panX, tolerance)
        assertEquals(-750f, far.panY, tolerance)

        // At the pan limit the image edge is exactly on the container edge: no gap.
        val leftEdge = far.imageToScreen(container, image, 0f, image.height / 2f)
        assertEquals(0f, leftEdge.x, tolerance)
        val bottomEdge = far.imageToScreen(container, image, image.width / 2f, image.height)
        assertEquals(500f, bottomEdge.y, tolerance)
    }

    @Test
    fun zoomKeepsTheFocusedImagePointInPlace() {
        val state = EyedropperState()
        val focusX = 700f
        val focusY = 300f
        val before = state.screenToImage(container, image, focusX, focusY)
        val zoomed = state.zoomTo(container, image, 3f, focusX, focusY)
        assertEquals(3f, zoomed.zoom, tolerance)
        val after = zoomed.imageToScreen(container, image, before.x, before.y)
        assertEquals(focusX, after.x, tolerance)
        assertEquals(focusY, after.y, tolerance)
    }

    @Test
    fun zoomIsClampedToItsRange() {
        assertEquals(MIN_ZOOM, EyedropperState().zoomTo(container, image, 0.1f, 0f, 0f).zoom, tolerance)
        assertEquals(MAX_ZOOM, EyedropperState().zoomTo(container, image, 99f, 0f, 0f).zoom, tolerance)
    }

    @Test
    fun crosshairDragFollowsTheFingerAndStaysInsideTheImage() {
        val state = EyedropperState(zoom = 2f, crosshairX = 100f, crosshairY = 50f)
        // 10 screen px at scale 10 (fit 5 × zoom 2) = 1 image px.
        val dragged = state.dragCrosshair(container, image, 10f, 20f)
        assertEquals(101f, dragged.crosshairX, tolerance)
        assertEquals(52f, dragged.crosshairY, tolerance)

        val clamped = dragged.dragCrosshair(container, image, 0f, 100_000f)
        assertEquals(99f, clamped.crosshairY, tolerance)
    }
}
