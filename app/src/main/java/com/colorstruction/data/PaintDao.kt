package com.colorstruction.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Database access for paints.
 *
 * Paints of one palette are always ordered by [PaintEntity.sortOrder] and, for equal orders,
 * by id, so the order is deterministic even before [updateSortOrders] has run.
 */
@Dao
interface PaintDao {

    /** Observes the paints of [paletteId] in display order. */
    @Query("SELECT * FROM paint WHERE paletteId = :paletteId ORDER BY sortOrder ASC, id ASC")
    fun observeByPalette(paletteId: Long): Flow<List<PaintEntity>>

    /** Reads the paints of [paletteId] in display order. */
    @Query("SELECT * FROM paint WHERE paletteId = :paletteId ORDER BY sortOrder ASC, id ASC")
    suspend fun getByPalette(paletteId: Long): List<PaintEntity>

    /** Reads one paint. */
    @Query("SELECT * FROM paint WHERE id = :id")
    suspend fun getById(id: Long): PaintEntity?

    /** Inserts [paint] and returns its generated id. */
    @Insert
    suspend fun insert(paint: PaintEntity): Long

    /** Replaces an existing paint row, matched by id. */
    @Update
    suspend fun update(paint: PaintEntity)

    /** Deletes [paint]. */
    @Delete
    suspend fun delete(paint: PaintEntity)

    /** Deletes the paint with [id]. */
    @Query("DELETE FROM paint WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Highest [PaintEntity.sortOrder] in [paletteId], or `null` when the palette is empty. */
    @Query("SELECT MAX(sortOrder) FROM paint WHERE paletteId = :paletteId")
    suspend fun maxSortOrder(paletteId: Long): Int?

    /** Number of paints in [paletteId]. */
    @Query("SELECT COUNT(*) FROM paint WHERE paletteId = :paletteId")
    suspend fun countByPalette(paletteId: Long): Int

    /** Sets the sort order of a single paint. */
    @Query("UPDATE paint SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int)

    /**
     * Rewrites the sort orders of several paints in one transaction.
     *
     * @param items paint id to its new sort order.
     */
    @Transaction
    suspend fun updateSortOrders(items: List<Pair<Long, Int>>) {
        items.forEach { (id, sortOrder) -> updateSortOrder(id, sortOrder) }
    }
}
