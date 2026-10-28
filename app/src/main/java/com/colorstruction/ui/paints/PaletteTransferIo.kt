package com.colorstruction.ui.paints

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.colorstruction.R
import com.colorstruction.data.PaletteFileException
import java.io.ByteArrayOutputStream
import java.io.File

/** MIME type of palette files. */
internal const val JSON_MIME = "application/json"

/** Types accepted by the "import palette" file picker. */
internal val IMPORT_MIME_TYPES = arrayOf(JSON_MIME, "text/plain", "application/octet-stream")

/** Hard cap for an imported file; anything larger is rejected as too large. */
private const val MAX_IMPORT_BYTES = 1_100_000

private const val SHARED_DIR = "shared"
private const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"

/** Snackbar text for a rejected palette file. */
internal fun PaletteFileException.Reason.messageRes(): Int = when (this) {
    PaletteFileException.Reason.NOT_A_PALETTE -> R.string.import_error_not_palette
    PaletteFileException.Reason.UNSUPPORTED_VERSION -> R.string.import_error_newer_version
    PaletteFileException.Reason.TOO_LARGE -> R.string.import_error_too_large
    PaletteFileException.Reason.INVALID -> R.string.import_error_damaged
}

/** Opens the system share sheet with the palette [uri] attached. */
internal fun sharePalette(context: Context, uri: Uri, subject: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = JSON_MIME
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, subject)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, context.getString(R.string.share_palette_chooser))
    try {
        context.startActivity(chooser)
    } catch (_: ActivityNotFoundException) {
        // No app can receive the file: nothing to open.
    }
}

/** Writes [json] into a fresh cache file named [fileName] and returns its content uri. */
internal fun createSharedFileUri(context: Context, fileName: String, json: String): Uri? = runCatching {
    val dir = File(context.cacheDir, SHARED_DIR).apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, fileName).apply { writeText(json) }
    FileProvider.getUriForFile(context, context.packageName + FILE_PROVIDER_AUTHORITY_SUFFIX, file)
}.getOrNull()

/** Writes [text] to [uri]; `false` when the stream cannot be opened or written. */
internal fun writeText(context: Context, uri: Uri, text: String): Boolean = runCatching {
    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
        ?: error("No output stream for $uri")
}.isSuccess

/**
 * Reads [uri] as UTF-8 text; `null` when the file exceeds the size cap, an empty string when it
 * cannot be read at all (the codec then rejects it as not a palette).
 */
internal fun readTextOrNull(context: Context, uri: Uri): String? {
    val stream = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
        ?: return ""
    return stream.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = runCatching { input.read(buffer) }.getOrNull() ?: return ""
            if (read < 0) break
            output.write(buffer, 0, read)
            if (output.size() > MAX_IMPORT_BYTES) return null
        }
        output.toString(Charsets.UTF_8.name())
    }
}
