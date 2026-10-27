package com.colorstruction.data

import com.colorstruction.color.Rgb
import com.colorstruction.mixing.Recipe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Storage for saved favorite recipes.
 *
 * Favorites are snapshots: saving one copies the palette name and the paint names/colors, so
 * later paint edits or deletions never change a stored favorite.
 */
interface FavoriteRepository {

    /** All favorites, newest first, each with its components in display order. */
    fun favorites(): Flow<List<FavoriteRecipe>>

    /** Stores [favorite] as a new snapshot and returns its id. */
    suspend fun add(favorite: FavoriteRecipe): Long

    /** Deletes the favorite with [id]; its components are deleted with it. */
    suspend fun delete(id: Long)

    /** Number of stored favorites. */
    suspend fun count(): Int
}

/** Room-backed [FavoriteRepository]. */
class RoomFavoriteRepository(
    private val database: ColorStructionDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) : FavoriteRepository {

    private val favoriteDao = database.favoriteDao()

    override fun favorites(): Flow<List<FavoriteRecipe>> =
        favoriteDao.observeAll().map { rows -> rows.map(FavoriteWithComponents::toDomain) }

    override suspend fun add(favorite: FavoriteRecipe): Long = favoriteDao.insertWithComponents(
        favorite = favorite.toEntity(),
        components = favorite.components.mapIndexed { index, component ->
            component.toEntity(favoriteId = 0L, position = index)
        },
    )

    override suspend fun delete(id: Long) {
        favoriteDao.delete(id)
    }

    override suspend fun count(): Int = favoriteDao.count()

    /** Convenience: snapshots [recipe] for [target] and stores it in one call. */
    suspend fun addRecipe(recipe: Recipe, target: Rgb, paletteName: String): Long =
        add(recipe.toFavorite(target = target, paletteName = paletteName, clock = clock))
}
