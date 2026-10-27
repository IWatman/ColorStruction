package com.colorstruction.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A palette as stored in the database.
 *
 * @property id auto-generated primary key, `0` means "not inserted yet".
 * @property type the [com.colorstruction.mixing.PaletteType] name (`OPAQUE` / `WATERCOLOR`).
 * @property createdAt creation timestamp in epoch milliseconds, used for ordering.
 */
@Entity(tableName = "palette")
data class PaletteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val type: String,
    val createdAt: Long,
)

/**
 * A paint as stored in the database.
 *
 * @property id auto-generated primary key, `0` means "not inserted yet".
 * @property paletteId owning palette; deleting the palette deletes its paints.
 * @property colorArgb the paint color as an opaque `0xFFRRGGBB` integer.
 * @property tintingStrength how strongly the paint tints a mixture; `1.0` is neutral.
 * @property note optional free-form user note.
 * @property sortOrder position inside the palette; lower values are shown first.
 */
@Entity(
    tableName = "paint",
    foreignKeys = [
        ForeignKey(
            entity = PaletteEntity::class,
            parentColumns = ["id"],
            childColumns = ["paletteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("paletteId")],
)
data class PaintEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val paletteId: Long,
    val name: String,
    val colorArgb: Int,
    val tintingStrength: Double = 1.0,
    val note: String? = null,
    val sortOrder: Int,
)
