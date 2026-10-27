package com.colorstruction.ui.eyedropper

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** Load result of the eyedropper image: the bitmap, its pixel buffer, or a failure flag. */
class EyedropperImageState {
    /** Decoded bitmap, `null` while loading or after a failure. */
    var bitmap by mutableStateOf<Bitmap?>(null)

    /** Pixel copy used for sampling, `null` while loading or after a failure. */
    var pixels by mutableStateOf<ImagePixels?>(null)

    /** `true` if the image could not be decoded. */
    var failed by mutableStateOf(false)
}

/**
 * Decodes [uri] off the main thread and remembers the result for this image and [uri].
 *
 * The pixels are copied once so sampling a 5×5 area is a few array reads.
 */
@Composable
fun rememberEyedropperImage(uri: Uri): EyedropperImageState {
    val context = LocalContext.current
    val state = remember(uri) { EyedropperImageState() }
    LaunchedEffect(uri) {
        val decoded = decodeSampledBitmap(context, uri)
        if (decoded == null) {
            state.failed = true
        } else {
            state.bitmap = decoded
            state.pixels = decoded.toImagePixels()
        }
    }
    return state
}
