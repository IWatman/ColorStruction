package com.colorstruction.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Database access for palettes.
 *
 * Every returned [PaletteEntity] is observed / read in creation order, so the list is stable
 * and new palettes appear at the end.
 */
@Dao
interface PaletteDao {

    /** Observes all palettes, oldest first. Emits again on every change. */
    @Query("SELECT * FROM palette ORDER BY createdAt ASC, id ASC")
    fun observeAll(): Flow<List<PaletteEntity>>

    /** Observes one palette, emitting `null` when it does not exist (any more). */
    @Query("SELECT * FROM palette WHERE id = :id")
    fun observeById(id: Long): Flow<PaletteEntity?>

    /** Reads one palette, oldest first. */
    @Query("SELECT * FROM palette WHERE id = :id")
    suspend fun getById(id: Long): PaletteEntity?

    /** Reads all palettes, ordered by creation time. */
    @Query("SELECT * FROM palette ORDER BY createdAt ASC, id ASC")
    suspend fun getAll(): List<PaletteEntity>

    /** Number of stored palettes. */
    @Query("SELECT COUNT(*) FROM palette")
    suspend fun count(): Int

    /** Inserts [palette] and returns its generated id. */
    @Insert
    suspend fun insert(palette: PaletteEntity): Long

    /** Replaces an existing palette row, matched by id. */
    @Update
    suspend fun update(palette: PaletteEntity)

    /** Deletes [palette]; its paints are removed by the foreign key cascade. */
    @Delete
    suspend fun delete(palette: PaletteEntity)

    /** Deletes the palette with [id]; its paints are removed by the foreign key cascade. */
    @Query("DELETE FROM palette WHERE id = :id")
    suspend fun deleteById(id: Long)
}
