package com.colorstruction.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Database access for saved recipes.
 *
 * Favorites are read as [FavoriteWithComponents]; the component list is unordered and must be
 * sorted by [FavoriteComponentEntity.position] by the caller.
 */
@Dao
interface FavoriteDao {

    /** Observes all favorites, newest first, with their components. */
    @Transaction
    @Query("SELECT * FROM favorite_recipe ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<FavoriteWithComponents>>

    /** Reads all favorites, newest first, with their components. */
    @Transaction
    @Query("SELECT * FROM favorite_recipe ORDER BY createdAt DESC, id DESC")
    suspend fun getAll(): List<FavoriteWithComponents>

    /** Reads one favorite with its components, or `null` when it does not exist. */
    @Transaction
    @Query("SELECT * FROM favorite_recipe WHERE id = :id")
    suspend fun getById(id: Long): FavoriteWithComponents?

    /** Inserts [favorite] and returns its generated id. */
    @Insert
    suspend fun insertFavorite(favorite: FavoriteRecipeEntity): Long

    /** Inserts all [components]; their ids are generated. */
    @Insert
    suspend fun insertComponents(components: List<FavoriteComponentEntity>)

    /** Number of component rows of [favoriteId]. */
    @Query("SELECT COUNT(*) FROM favorite_component WHERE favoriteId = :favoriteId")
    suspend fun componentCount(favoriteId: Long): Int

    /** Number of saved favorites. */
    @Query("SELECT COUNT(*) FROM favorite_recipe")
    suspend fun count(): Int

    /** Deletes the favorite with [id]; its components go with it (ON DELETE CASCADE). */
    @Query("DELETE FROM favorite_recipe WHERE id = :id")
    suspend fun delete(id: Long)

    /**
     * Stores [favorite] and its [components] in one transaction and returns the new favorite id.
     *
     * Component ids and favorite ids are ignored; [FavoriteComponentEntity.position] is kept, so
     * callers control the display order.
     */
    @Transaction
    suspend fun insertWithComponents(
        favorite: FavoriteRecipeEntity,
        components: List<FavoriteComponentEntity>,
    ): Long {
        val favoriteId = insertFavorite(favorite.copy(id = 0L))
        insertComponents(components.map { it.copy(id = 0L, favoriteId = favoriteId) })
        return favoriteId
    }
}
