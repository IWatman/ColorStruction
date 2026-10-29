package com.colorstruction.ui.share

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.colorstruction.color.Rgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Cache directory holding images that are handed to the share sheet. */
private const val SHARED_IMAGE_DIR = "shared_images"

/** Name of the shared recipe image; a single reuse-and-overwrite file is enough. */
private const val SHARED_IMAGE_NAME = "recipe.png"

private const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"
private const val PNG_MIME = "image/png"

/** Colour of the virtual water component: a light blue-gray. */
internal val WATER_SWATCH: Rgb = Rgb.fromArgbInt(0xFFAFC3CE.toInt())

/**
 * Writes [bitmap] as a PNG into the cache and opens the system share sheet for it.
 *
 * Encoding happens on [Dispatchers.IO]; nothing happens when no app can receive the image.
 */
suspend fun shareRecipeImage(context: Context, bitmap: Bitmap, chooserTitle: String) {
    val uri = withContext(Dispatchers.IO) { writeSharedImage(context, bitmap) } ?: return
    val send = Intent(Intent.ACTION_SEND).apply {
        type = PNG_MIME
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, chooserTitle)
    try {
        context.startActivity(chooser)
    } catch (_: ActivityNotFoundException) {
        // No app can receive the image: nothing to open.
    }
}

/** Overwrites `cacheDir/shared_images/recipe.png` with [bitmap] and returns its content uri. */
private fun writeSharedImage(context: Context, bitmap: Bitmap): Uri? = runCatching {
    val dir = File(context.cacheDir, SHARED_IMAGE_DIR).apply { mkdirs() }
    val file = File(dir, SHARED_IMAGE_NAME)
    file.outputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) }
    FileProvider.getUriForFile(context, context.packageName + FILE_PROVIDER_AUTHORITY_SUFFIX, file)
}.getOrNull()
