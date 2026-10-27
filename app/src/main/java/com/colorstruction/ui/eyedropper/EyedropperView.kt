package com.colorstruction.ui.eyedropper

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

private val CROSSHAIR_RADIUS = 13.dp
private val CROSSHAIR_ARM = 22.dp
private val LOUPE_RADIUS = 55.dp
private val LOUPE_GAP = 26.dp
private val LOUPE_MARKER = 18.dp

/** Magnification used inside the loupe. */
private const val LOUPE_MAGNIFICATION = 6f

/**
 * Zoomable image preview with the sampling crosshair and the magnifier loupe.
 *
 * One finger drag moves the crosshair, two fingers pinch-zoom and pan the image.
 *
 * @param bitmap image to pick from (already downsampled)
 * @param state current viewport and crosshair, in image pixels
 * @param onStateChange invoked whenever a gesture changes [state]
 */
@Composable
fun EyedropperView(
    bitmap: Bitmap,
    state: EyedropperState,
    onStateChange: (EyedropperState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val imageSize = remember(bitmap) { SizePx(bitmap.width.toFloat(), bitmap.height.toFloat()) }
    var container by remember { mutableStateOf(SizePx(0f, 0f)) }
    val tracker = rememberPointerTracker()

    val density = LocalDensity.current
    val crosshairRadius = with(density) { CROSSHAIR_RADIUS.toPx() }
    val crosshairArm = with(density) { CROSSHAIR_ARM.toPx() }
    val loupeRadius = with(density) { LOUPE_RADIUS.toPx() }
    val loupeGap = with(density) { LOUPE_GAP.toPx() }
    val markerSize = with(density) { LOUPE_MARKER.toPx() }

    val current = rememberUpdatedState(state)
    val currentContainer = rememberUpdatedState(container)
    val change = rememberUpdatedState(onStateChange)
    val loupeBackground = MaterialTheme.colorScheme.surface

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { container = SizePx(it.width.toFloat(), it.height.toFloat()) }
            .eyedropperGestures(
                tracker = tracker,
                onDragCrosshair = { dx, dy ->
                    val viewport = currentContainer.value
                    change.value(current.value.dragCrosshair(viewport, imageSize, dx, dy))
                },
                onTransform = { centroid, pan, zoom ->
                    val viewport = currentContainer.value
                    val viewportState = current.value
                    val zoomed = viewportState.zoomTo(
                        container = viewport,
                        image = imageSize,
                        newZoom = viewportState.zoom * zoom,
                        focusX = centroid.x,
                        focusY = centroid.y,
                    )
                    change.value(zoomed.panBy(viewport, imageSize, pan.x, pan.y))
                },
            ),
    ) {
        val shown = displayedSize(container, imageSize, state.zoom)
        if (shown.width <= 0f || shown.height <= 0f) return@Canvas

        val placement = state.imageTopLeft(container, imageSize)
        drawFittedImage(image, placement, shown)

        val crosshair = state.imageToScreen(container, imageSize, state.crosshairX, state.crosshairY)
        val crosshairOffset = Offset(crosshair.x, crosshair.y)
        drawCrosshair(crosshairOffset, crosshairRadius, crosshairArm)

        if (tracker.isPressing) {
            val center = loupeCenter(crosshair, container, loupeRadius, loupeGap)
            drawLoupe(
                image = image,
                placement = placement,
                shown = shown,
                crosshair = crosshairOffset,
                center = Offset(center.x, center.y),
                radius = loupeRadius,
                magnification = LOUPE_MAGNIFICATION,
                background = loupeBackground,
                markerSize = markerSize,
            )
        }
    }
}
