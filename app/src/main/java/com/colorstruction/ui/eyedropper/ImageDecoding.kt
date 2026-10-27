package com.colorstruction.ui.eyedropper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Decodes [uri] into a software ARGB_8888 bitmap whose longest side is at most [maxSide].
 *
 * Sub-sampling happens while decoding, so a 50 MP photo never lands in memory at full size.
 * API 28+ goes through [ImageDecoder], which applies the EXIF orientation itself; older
 * versions use [BitmapFactory] plus [ExifInterface].
 *
 * @return the bitmap, or `null` if the image cannot be read
 */
suspend fun decodeSampledBitmap(
    context: Context,
    uri: Uri,
    maxSide: Int = 2048,
): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val decoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Some providers cannot serve ImageDecoder; fall back to BitmapFactory then.
            runCatching { decodeWithImageDecoder(context, uri, maxSide) }
                .getOrElse { decodeWithBitmapFactory(context, uri, maxSide) }
        } else {
            decodeWithBitmapFactory(context, uri, maxSide)
        }
        decoded?.let(::toSoftwareArgb8888)
    }.getOrNull()
}

/** ARGB pixels of a decoded image plus its size, ready for sampling. */
class ImagePixels(val pixels: IntArray, val width: Int, val height: Int)

/** Copies this bitmap's pixels into an [ImagePixels] buffer (read once, sampled many times). */
fun Bitmap.toImagePixels(): ImagePixels {
    val buffer = IntArray(width * height)
    getPixels(buffer, 0, width, 0, 0, width, height)
    return ImagePixels(buffer, width, height)
}

/** Largest power-of-two divisor that keeps both sides within [maxSide]. */
internal fun inSampleSize(width: Int, height: Int, maxSide: Int): Int {
    if (maxSide <= 0 || width <= 0 || height <= 0) return 1
    val longest = max(width, height)
    var sample = 1
    while (longest / sample > maxSide && sample * 2 > 0) sample *= 2
    return sample
}

@RequiresApi(Build.VERSION_CODES.P)
private fun decodeWithImageDecoder(context: Context, uri: Uri, maxSide: Int): Bitmap {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.setTargetSampleSize(inSampleSize(info.size.width, info.size.height, maxSide))
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
}

@Suppress("DEPRECATION")
private fun decodeWithBitmapFactory(context: Context, uri: Uri, maxSide: Int): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching {
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = inSampleSize(bounds.outWidth, bounds.outHeight, maxSide)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = runCatching {
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull() ?: return null

    return rotateByExif(decoded, exifRotationDegrees(context, uri))
}

/** EXIF orientation of [uri] as a clockwise rotation in degrees (0 if unknown). */
private fun exifRotationDegrees(context: Context, uri: Uri): Int = runCatching {
    context.contentResolver.openInputStream(uri)?.use { input ->
        val exif = ExifInterface(input)
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } ?: 0
}.getOrDefault(0)

private fun rotateByExif(bitmap: Bitmap, degrees: Int): Bitmap {
    if (degrees == 0) return bitmap
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return runCatching {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            .also { if (it !== bitmap) bitmap.recycle() }
    }.getOrDefault(bitmap)
}

/** Copies [bitmap] into an ARGB_8888 software bitmap so its pixels can be read. */
private fun toSoftwareArgb8888(bitmap: Bitmap): Bitmap {
    if (bitmap.config == Bitmap.Config.ARGB_8888) return bitmap
    return runCatching { bitmap.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull() ?: bitmap
}
