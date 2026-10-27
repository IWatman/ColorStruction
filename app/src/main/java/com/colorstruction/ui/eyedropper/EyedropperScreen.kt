package com.colorstruction.ui.eyedropper

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.colorstruction.R
import com.colorstruction.color.Rgb
import kotlin.math.roundToInt

/**
 * Picks a color from an image: zoom/pan, a draggable crosshair, a magnifier and area averaging.
 *
 * @param imageUri image to pick from (Photo Picker or camera result)
 * @param onConfirm invoked with the sampled color when the check action is used
 * @param onCancel invoked on the back arrow
 */
@Composable
fun EyedropperScreen(
    imageUri: Uri,
    onConfirm: (Rgb) -> Unit,
    onCancel: () -> Unit,
) {
    val image = rememberEyedropperImage(imageUri)
    var viewport by remember(imageUri) { mutableStateOf(EyedropperState()) }
    var sizeIndex by remember(imageUri) { mutableIntStateOf(0) }

    // Start sampling from the middle of the image as soon as it is decoded.
    LaunchedEffect(image.pixels) {
        image.pixels?.let {
            viewport = viewport.copy(crosshairX = it.width / 2f, crosshairY = it.height / 2f)
        }
    }

    val radius = SAMPLE_RADII[sizeIndex.coerceIn(SAMPLE_RADII.indices)]
    val sampled = remember(image.pixels, viewport.crosshairX, viewport.crosshairY, radius) {
        image.pixels?.let {
            averageColor(
                pixels = it.pixels,
                width = it.width,
                height = it.height,
                cx = viewport.crosshairX.roundToInt(),
                cy = viewport.crosshairY.roundToInt(),
                radius = radius,
            )
        }
    }

    // The root Scaffold already pads for system bars; don't apply the insets twice.
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            EyedropperTopBar(
                canConfirm = sampled != null,
                onCancel = onCancel,
                onConfirm = { sampled?.let { onConfirm(Rgb.fromArgbInt(it)) } },
            )
        },
        bottomBar = {
            if (sampled != null) {
                EyedropperBottomBar(
                    argb = sampled,
                    selectedIndex = sizeIndex,
                    onSelectSampleSize = { sizeIndex = it },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = image.bitmap
            when {
                image.failed -> EyedropperMessage(stringResource(R.string.eyedropper_error))
                bitmap == null -> EyedropperLoading()
                else -> EyedropperView(
                    bitmap = bitmap,
                    state = viewport,
                    onStateChange = { viewport = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
