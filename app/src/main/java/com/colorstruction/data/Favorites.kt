package com.colorstruction.data

import com.colorstruction.color.Rgb
import com.colorstruction.mixing.Recipe

/**
 * A saved recipe in domain terms.
 *
 * It is a snapshot: [paletteName] and the colors/names inside [components] are copies taken when
 * the recipe was saved, so later paint edits or deletions do not change it.
 *
 * @property id database id; `0` for a favorite that has not been stored yet.
 * @property target the color the recipe was searched for.
 * @property deltaE CIEDE2000 difference between the recipe result and [target].
 * @property createdAt save timestamp in epoch milliseconds; newer favorites come first.
 * @property note optional free-form user note.
 */
data class FavoriteRecipe(
    val id: Long = 0L,
    val target: Rgb,
    val paletteName: String,
    val deltaE: Double,
    val createdAt: Long,
    val note: String? = null,
    val components: List<FavoriteComponent> = emptyList(),
)

/**
 * One component of a [FavoriteRecipe], in display order.
 *
 * @property paintName name of the paint at save time; empty for the water component.
 * @property color the paint's color at save time.
 * @property isWater `true` for the virtual water component (watercolor mode only).
 * @property weight share of this component; weights of one recipe sum to 1.0.
 */
data class FavoriteComponent(
    val paintName: String,
    val color: Rgb,
    val isWater: Boolean,
    val weight: Double,
) {
    /** Name to show in the UI; the water component has no paint name. */
    val displayName: String get() = if (isWater) WATER_NAME else paintName

    companion object {
        /** Label shown for the virtual water component. */
        const val WATER_NAME: String = "Water"
    }
}

/** Converts a stored favorite (with components) to its domain model, ordering by position. */
fun FavoriteWithComponents.toDomain(): FavoriteRecipe = FavoriteRecipe(
    id = favorite.id,
    target = Rgb.fromArgbInt(favorite.targetArgb),
    paletteName = favorite.paletteName,
    deltaE = favorite.deltaE,
    createdAt = favorite.createdAt,
    note = favorite.note,
    components = components
        .sortedBy(FavoriteComponentEntity::position)
        .map(FavoriteComponentEntity::toDomain),
)

/** Converts a stored component to its domain model. */
fun FavoriteComponentEntity.toDomain(): FavoriteComponent = FavoriteComponent(
    paintName = paintName,
    color = Rgb.fromArgbInt(colorArgb),
    isWater = isWater,
    weight = weight,
)

/** Converts this favorite to the stored header row. */
fun FavoriteRecipe.toEntity(): FavoriteRecipeEntity = FavoriteRecipeEntity(
    id = id,
    targetArgb = target.toArgbInt(),
    paletteName = paletteName,
    deltaE = deltaE,
    createdAt = createdAt,
    note = note,
)

/** Converts this component to a stored row of the favorite [favoriteId] at [position]. */
fun FavoriteComponent.toEntity(favoriteId: Long, position: Int): FavoriteComponentEntity =
    FavoriteComponentEntity(
        favoriteId = favoriteId,
        position = position,
        paintName = paintName,
        colorArgb = color.toArgbInt(),
        isWater = isWater,
        weight = weight,
    )

/**
 * Turns a solved [Recipe] into a savable snapshot for [target] from the palette [paletteName].
 *
 * Every component keeps its paint's name and color, so the favorite survives later edits; the
 * virtual water component is stored with an empty name and `isWater = true`. The result is not
 * persisted yet, hence [FavoriteRecipe.id] is `0`.
 */
fun Recipe.toFavorite(
    target: Rgb,
    paletteName: String,
    clock: () -> Long = System::currentTimeMillis,
): FavoriteRecipe = FavoriteRecipe(
    target = target,
    paletteName = paletteName,
    deltaE = deltaE,
    createdAt = clock(),
    components = components.map { component ->
        val paint = component.paint
        FavoriteComponent(
            paintName = paint?.name.orEmpty(),
            color = paint?.color ?: Rgb.WHITE,
            isWater = component.isWater,
            weight = component.weight,
        )
    },
)
