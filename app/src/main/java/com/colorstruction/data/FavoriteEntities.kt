package com.colorstruction.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * A saved ("favorite") recipe as stored in the database.
 *
 * A favorite is a snapshot: it copies the palette name and the paint data of the moment it was
 * saved, so later edits to or deletions of the original paints do not change it.
 *
 * @property id auto-generated primary key, `0` means "not inserted yet".
 * @property targetArgb the target color of the search as an opaque `0xFFRRGGBB` integer.
 * @property paletteName name of the palette the recipe was computed from.
 * @property deltaE CIEDE2000 difference between the recipe result and the target.
 * @property createdAt creation timestamp in epoch milliseconds, used for ordering.
 * @property note optional free-form user note.
 */
@Entity(tableName = "favorite_recipe")
data class FavoriteRecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val targetArgb: Int,
    val paletteName: String,
    val deltaE: Double,
    val createdAt: Long,
    val note: String? = null,
)

/**
 * One component of a saved recipe.
 *
 * @property id auto-generated primary key, `0` means "not inserted yet".
 * @property favoriteId owning favorite; deleting the favorite deletes its components.
 * @property position display order inside the favorite; lower values come first.
 * @property paintName snapshot of the paint name; empty for the virtual water component.
 * @property colorArgb snapshot of the paint color as an opaque `0xFFRRGGBB` integer.
 * @property isWater `true` for the virtual water component (watercolor mode only).
 * @property weight share of this component; weights of one recipe sum to 1.0.
 */
@Entity(
    tableName = "favorite_component",
    foreignKeys = [
        ForeignKey(
            entity = FavoriteRecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["favoriteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("favoriteId")],
)
data class FavoriteComponentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val favoriteId: Long,
    val position: Int,
    val paintName: String,
    val colorArgb: Int,
    val isWater: Boolean,
    val weight: Double,
)

/**
 * A favorite together with its components, as loaded through a Room relation.
 *
 * [components] is sorted by [FavoriteComponentEntity.position] in the mapper, because
 * `@Relation` does not guarantee any order.
 */
data class FavoriteWithComponents(
    @Embedded val favorite: FavoriteRecipeEntity,
    @Relation(parentColumn = "id", entityColumn = "favoriteId")
    val components: List<FavoriteComponentEntity>,
)
