package com.colorstruction.data

import com.colorstruction.color.Rgb
import com.colorstruction.mixing.PaletteType
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** A whole palette as stored in a palette file: the palette plus its paints in display order. */
data class PaletteFileData(
    val name: String,
    val type: PaletteType,
    val paints: List<PaintFileData>,
)

/** One paint inside a palette file. */
data class PaintFileData(
    val name: String,
    val color: Rgb,
    val tintingStrength: Double = 1.0,
    val note: String? = null,
)

/** Thrown when a palette file cannot be decoded; [reason] says why. */
class PaletteFileException(val reason: Reason) : Exception("Invalid palette file: $reason") {
    /** Why a palette file was rejected. */
    enum class Reason {
        /** Not JSON at all, or JSON without the palette marker. */
        NOT_A_PALETTE,
        /** Written by a newer app version. */
        UNSUPPORTED_VERSION,
        /** A field has an unusable value. */
        INVALID,
        /** More text or more paints than the app accepts. */
        TOO_LARGE,
    }
}

/** Reads and writes the pretty-printed JSON palette format. */
object PaletteFileCodec {
    /** Marker that identifies a palette file. */
    const val FORMAT: String = "colorstruction-palette"
    /** Format version written by this app. */
    const val VERSION: Int = 1

    const val MAX_TEXT_LENGTH: Int = 1_000_000
    const val MAX_PAINTS: Int = 500
    const val MAX_NAME_LENGTH: Int = 100
    const val MAX_NOTE_LENGTH: Int = 500
    const val MIN_TINTING_STRENGTH: Double = 0.1
    const val MAX_TINTING_STRENGTH: Double = 10.0
    private const val INDENT = 2

    /** Serializes [data] to pretty-printed JSON (2-space indent). */
    fun encode(data: PaletteFileData): String {
        val paints = JSONArray()
        data.paints.forEach { paint ->
            val strength = if (paint.tintingStrength.isFinite()) paint.tintingStrength else 1.0
            paints.put(
                JSONObject()
                    .put("name", paint.name)
                    .put("color", paint.color.toHex())
                    .put("tintingStrength", strength)
                    .put("note", paint.note ?: JSONObject.NULL),
            )
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("name", data.name)
            .put("type", data.type.name)
            .put("paints", paints)
            .toString(INDENT)
    }

    /**
     * Parses [text] strictly.
     *
     * Unknown extra fields are ignored and an unknown `type` falls back to [PaletteType.OPAQUE];
     * everything else must be usable, otherwise [PaletteFileException] is thrown.
     */
    fun decode(text: String): PaletteFileData {
        if (text.length > MAX_TEXT_LENGTH) throw reject(PaletteFileException.Reason.TOO_LARGE)
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw reject(PaletteFileException.Reason.NOT_A_PALETTE)
        }
        if (root.opt("format") != FORMAT) throw reject(PaletteFileException.Reason.NOT_A_PALETTE)
        val version = (root.opt("version") as? Number)?.toInt()
            ?: throw reject(PaletteFileException.Reason.INVALID)
        if (version > VERSION) throw reject(PaletteFileException.Reason.UNSUPPORTED_VERSION)
        if (version < 1) throw reject(PaletteFileException.Reason.INVALID)

        val typeName = root.opt("type") as? String
        val paints = root.opt("paints") as? JSONArray
            ?: throw reject(PaletteFileException.Reason.INVALID)
        if (paints.length() > MAX_PAINTS) throw reject(PaletteFileException.Reason.TOO_LARGE)

        return PaletteFileData(
            name = requiredName(root.opt("name")),
            type = PaletteType.entries.firstOrNull { it.name.equals(typeName, ignoreCase = true) }
                ?: PaletteType.OPAQUE,
            paints = (0 until paints.length()).map { index ->
                val paint = paints.opt(index) as? JSONObject
                    ?: throw reject(PaletteFileException.Reason.INVALID)
                decodePaint(paint)
            },
        )
    }

    private fun decodePaint(json: JSONObject): PaintFileData = PaintFileData(
        name = requiredName(json.opt("name")),
        color = (json.opt("color") as? String)?.let(Rgb::parseHex)
            ?: throw reject(PaletteFileException.Reason.INVALID),
        tintingStrength = (json.opt("tintingStrength") as? Number)?.toDouble()
            ?.takeIf { it.isFinite() }
            ?.coerceIn(MIN_TINTING_STRENGTH, MAX_TINTING_STRENGTH)
            ?: 1.0,
        note = (json.opt("note") as? String)?.take(MAX_NOTE_LENGTH)?.takeIf { it.isNotBlank() },
    )

    private fun requiredName(raw: Any?): String {
        val trimmed = (raw as? String)?.trim() ?: throw reject(PaletteFileException.Reason.INVALID)
        if (trimmed.isEmpty()) throw reject(PaletteFileException.Reason.INVALID)
        return trimmed.take(MAX_NAME_LENGTH).trim()
    }

    private fun reject(reason: PaletteFileException.Reason) = PaletteFileException(reason)
}

/**
 * Builds a safe file name for the palette called [name]: letters, digits, spaces, `-` and `_`
 * are kept, everything else becomes `_`. A blank name yields `palette.colorstruction.json`.
 */
fun suggestedFileName(name: String): String {
    val sanitized = name
        .map { if (it.isLetterOrDigit() || it in " -_()") it else '_' }
        .joinToString("")
        .trim()
        .take(60)
        .trim()
    return if (sanitized.isEmpty()) "palette.colorstruction.json" else "$sanitized.colorstruction.json"
}
