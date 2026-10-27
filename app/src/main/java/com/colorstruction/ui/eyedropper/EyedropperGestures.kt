package com.colorstruction.ui.eyedropper

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/** Live pointer state of the eyedropper canvas: drag routing and loupe visibility. */
class PointerTracker {
    /** Number of pointers currently down. */
    var pressed by mutableIntStateOf(0)

    /** Whether any pointer is down; the loupe is drawn while this is true. */
    var isPressing by mutableStateOf(false)
}

/** Remembers a [PointerTracker] for the current composition. */
@Composable
fun rememberPointerTracker(): PointerTracker = remember { PointerTracker() }

/**
 * Adds the eyedropper gestures: one finger moves the crosshair, two fingers zoom and pan.
 *
 * The pointer count is observed on the initial pass and nothing is consumed there, so the
 * transform detector below still sees a pristine gesture stream; it reports both single and
 * multi finger drags and we route them by pointer count.
 */
fun Modifier.eyedropperGestures(
    tracker: PointerTracker,
    onDragCrosshair: (dx: Float, dy: Float) -> Unit,
    onTransform: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
): Modifier = this
    .pointerInput(tracker) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val down = event.changes.count { it.pressed }
                tracker.pressed = down
                tracker.isPressing = down > 0
            }
        }
    }
    .pointerInput(tracker) {
        detectTransformGestures { centroid, pan, zoom, _ ->
            if (tracker.pressed >= 2) {
                onTransform(centroid, pan, zoom)
            } else {
                onDragCrosshair(pan.x, pan.y)
            }
        }
    }
