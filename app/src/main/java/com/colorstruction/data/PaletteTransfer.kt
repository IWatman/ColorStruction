package com.colorstruction.data

/**
 * Moves one palette in and out of the app as a [PaletteFileData] snapshot.
 *
 * Exports read the palette through [PaintRepository]; imports always create a new palette and
 * never touch existing ones.
 */
class PaletteTransfer(private val repository: PaintRepository) {

    /** Reads palette [paletteId] together with its paints in display order. */
    suspend fun export(paletteId: Long): PaletteFileData {
        val palette = repository.getPalettes().firstOrNull { it.id == paletteId }
            ?: throw IllegalArgumentException("No palette with id $paletteId")
        return PaletteFileData(
            name = palette.name,
            type = palette.type,
            paints = repository.getPaints(paletteId).map { paint ->
                PaintFileData(
                    name = paint.name,
                    color = paint.color,
                    tintingStrength = paint.tintingStrength,
                    note = paint.note,
                )
            },
        )
    }

    /**
     * Creates a new palette from [data] and returns its id.
     *
     * The new palette is named "Name", "Name (2)", "Name (3)"… — whichever is still free — and
     * the paints are added in file order. Nothing else in the database is modified.
     */
    suspend fun import(data: PaletteFileData): Long {
        val name = uniqueName(data.name, repository.getPalettes().map { it.name })
        val paletteId = repository.createPalette(name, data.type)
        data.paints.forEach { paint ->
            repository.addPaint(
                paletteId = paletteId,
                name = paint.name,
                color = paint.color,
                tintingStrength = paint.tintingStrength,
                note = paint.note,
            )
        }
        return paletteId
    }

    /** [desired] itself, or the first free "desired (n)" variant; names are compared ignoring case. */
    private fun uniqueName(desired: String, existing: List<String>): String {
        val taken = existing.mapTo(mutableSetOf()) { it.trim().lowercase() }
        val base = desired.trim().take(PaletteFileCodec.MAX_NAME_LENGTH).ifBlank { FALLBACK_NAME }
        if (base.lowercase() !in taken) return base
        var counter = 2
        while (true) {
            val candidate = "$base ($counter)"
            if (candidate.lowercase() !in taken) return candidate
            counter++
        }
    }

    companion object {
        /** Name used when the imported palette has no usable name. */
        const val FALLBACK_NAME: String = "Palette"
    }
}
