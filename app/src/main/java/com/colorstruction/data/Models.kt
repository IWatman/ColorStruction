package com.colorstruction.data

import com.colorstruction.color.Rgb
import com.colorstruction.mixing.PaletteType
import com.colorstruction.mixing.SolverPaint

/**
 * A palette in domain terms: what the UI and the solver work with.
 *
 * @property id database id; `0` for a palette that has not been stored yet.
 * @property type decides how mixtures are lightened (white paint or water).
 * @property createdAt creation timestamp in epoch milliseconds.
 */
data class Palette(
    val id: Long,
    val name: String,
    val type: PaletteType,
    val createdAt: Long,
)

/**
 * A paint in domain terms: what the UI and the solver work with.
 *
 * @property color the paint's own color in sRGB, already decoded from storage.
 * @property tintingStrength how strongly the paint tints a mixture; `1.0` is neutral.
 * @property sortOrder position inside the palette; lower values are shown first.
 */
data class Paint(
    val id: Long,
    val paletteId: Long,
    val name: String,
    val color: Rgb,
    val tintingStrength: Double = 1.0,
    val note: String? = null,
    val sortOrder: Int = 0,
)

/** Converts a stored palette to its domain model, falling back to [PaletteType.OPAQUE]. */
fun PaletteEntity.toDomain(): Palette = Palette(
    id = id,
    name = name,
    type = PaletteType.entries.firstOrNull { it.name == type } ?: PaletteType.OPAQUE,
    createdAt = createdAt,
)

/** Converts a domain palette to the stored representation. */
fun Palette.toEntity(): PaletteEntity = PaletteEntity(
    id = id,
    name = name,
    type = type.name,
    createdAt = createdAt,
)

/** Converts a stored paint to its domain model. */
fun PaintEntity.toDomain(): Paint = Paint(
    id = id,
    paletteId = paletteId,
    name = name,
    color = Rgb.fromArgbInt(colorArgb),
    tintingStrength = tintingStrength,
    note = note,
    sortOrder = sortOrder,
)

/** Converts a domain paint to the stored representation. */
fun Paint.toEntity(): PaintEntity = PaintEntity(
    id = id,
    paletteId = paletteId,
    name = name,
    colorArgb = color.toArgbInt(),
    tintingStrength = tintingStrength,
    note = note,
    sortOrder = sortOrder,
)

/** Converts this paint to the solver's input type, dropping storage-only fields. */
fun Paint.toSolverPaint(): SolverPaint = SolverPaint(
    id = id,
    name = name,
    color = color,
    tintingStrength = tintingStrength,
)
