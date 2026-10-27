package com.colorstruction.ui.eyedropper

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.colorstruction.R
import java.io.File

private const val CAMERA_DIR = "camera"
private const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"

/** The two ways of feeding an image into the eyedropper. */
class ImageSourceLauncher(
    /** Opens the system photo picker (no storage permission required). */
    val pickFromGallery: () -> Unit,
    /** Opens the system camera app, writing the shot into the app cache. */
    val takePhoto: () -> Unit,
)

/**
 * Remembers a [ImageSourceLauncher] and delivers the chosen image to [onImage].
 *
 * The pending camera output uri survives configuration changes so the result can still be
 * matched after the camera activity returns.
 */
@Composable
fun rememberImageSourceLauncher(onImage: (Uri) -> Unit): ImageSourceLauncher {
    val context = LocalContext.current
    val appContext = context.applicationContext
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) onImage(uri)
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { saved ->
        val uriString = pendingCameraUri
        pendingCameraUri = null
        if (saved && uriString != null) onImage(Uri.parse(uriString))
    }

    val pickFromGallery = remember(galleryLauncher) {
        {
            galleryLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }
    }
    val takePhoto = remember(cameraLauncher) {
        {
            val target = createCameraTargetUri(appContext)
            if (target == null) {
                Toast.makeText(context, R.string.no_camera_app, Toast.LENGTH_SHORT).show()
            } else {
                pendingCameraUri = target.toString()
                try {
                    cameraLauncher.launch(target)
                } catch (_: ActivityNotFoundException) {
                    pendingCameraUri = null
                    Toast.makeText(context, R.string.no_camera_app, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    return remember(pickFromGallery, takePhoto) { ImageSourceLauncher(pickFromGallery, takePhoto) }
}

/** Fresh cache file for a photo, with previous shots removed to keep the cache small. */
private fun createCameraTargetUri(context: Context): Uri? = runCatching {
    val dir = File(context.cacheDir, CAMERA_DIR).apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
    FileProvider.getUriForFile(
        context,
        context.packageName + FILE_PROVIDER_AUTHORITY_SUFFIX,
        file,
    )
}.getOrNull()
