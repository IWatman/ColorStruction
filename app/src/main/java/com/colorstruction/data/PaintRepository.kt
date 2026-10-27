package com.colorstruction.data

import androidx.room.withTransaction
import com.colorstruction.color.Rgb
import com.colorstruction.mixing.PaletteType
import com.colorstruction.mixing.StarterPalettes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Storage for palettes and paints.
 *
 * All writes are suspending and run on the caller's dispatcher (Room switches to its own IO
 * dispatcher internally). Reads that the UI needs to follow are exposed as [Flow]s that emit
 * again after every change.
 */
interface PaintRepository {

    /** All palettes, oldest first. */
    fun observePalettes(): Flow<List<Palette>>

    /** Paints of one palette in display order. */
    fun observePaints(paletteId: Long): Flow<List<Paint>>

    /** One-off read of all palettes, oldest first. */
    suspend fun getPalettes(): List<Palette>

    /** Creates a palette and returns its id. */
    suspend fun createPalette(name: String, type: PaletteType): Long

    /** Changes the palette's display name; does nothing if the palette is gone. */
    suspend fun renamePalette(id: Long, name: String)

    /** Changes the palette type (opaque / watercolor); does nothing if the palette is gone. */
    suspend fun setPaletteType(id: Long, type: PaletteType)

    /** Deletes the palette together with all of its paints. */
    suspend fun deletePalette(id: Long)

    /** Appends a paint to the end of the palette and returns its id. */
    suspend fun addPaint(
        paletteId: Long,
        name: String,
        color: Rgb,
        tintingStrength: Double = 1.0,
        note: String? = null,
    ): Long

    /** Reads one paint by its [id], or `null` when it no longer exists. */
    suspend fun getPaint(id: Long): Paint?

    /** Stores the given paint's fields, matched by [Paint.id]. */
    suspend fun updatePaint(paint: Paint)

    /**
     * Re-inserts [paint] keeping every field, including [Paint.sortOrder], so a paint that was
     * deleted can be brought back at its original position. Returns the new id.
     */
    suspend fun restorePaint(paint: Paint): Long

    /** Deletes a paint. */
    suspend fun deletePaint(id: Long)

    /**
     * Assigns consecutive sort orders to [orderedIds] (first id becomes `0`), so the paints
     * of [paletteId] are displayed in that order.
     */
    suspend fun reorderPaints(paletteId: Long, orderedIds: List<Long>)

    /**
     * Guarantees that at least one palette exists.
     *
     * When the database has no palettes at all, creates the "Basic Acrylic" starter palette
     * filled with [StarterPalettes.basicAcrylic] and returns its id. Otherwise returns the id
     * of the oldest palette. Calling it repeatedly is safe: at most one starter palette is
     * ever created.
     */
    suspend fun ensureStarterPalette(): Long
}

/** Room-backed [PaintRepository]. */
class RoomPaintRepository(
    private val database: ColorStructionDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) : PaintRepository {

    private val paletteDao = database.paletteDao()
    private val paintDao = database.paintDao()

    override fun observePalettes(): Flow<List<Palette>> =
        paletteDao.observeAll().map { entities -> entities.map(PaletteEntity::toDomain) }

    override fun observePaints(paletteId: Long): Flow<List<Paint>> =
        paintDao.observeByPalette(paletteId).map { entities -> entities.map(PaintEntity::toDomain) }

    override suspend fun getPalettes(): List<Palette> =
        paletteDao.getAll().map(PaletteEntity::toDomain)

    override suspend fun createPalette(name: String, type: PaletteType): Long =
        paletteDao.insert(
            PaletteEntity(name = name, type = type.name, createdAt = clock()),
        )

    override suspend fun renamePalette(id: Long, name: String) {
        val current = paletteDao.getById(id) ?: return
        paletteDao.update(current.copy(name = name))
    }

    override suspend fun setPaletteType(id: Long, type: PaletteType) {
        val current = paletteDao.getById(id) ?: return
        paletteDao.update(current.copy(type = type.name))
    }

    override suspend fun deletePalette(id: Long) {
        paletteDao.deleteById(id)
    }

    override suspend fun addPaint(
        paletteId: Long,
        name: String,
        color: Rgb,
        tintingStrength: Double,
        note: String?,
    ): Long {
        val sortOrder = (paintDao.maxSortOrder(paletteId) ?: -1) + 1
        return paintDao.insert(
            PaintEntity(
                paletteId = paletteId,
                name = name,
                colorArgb = color.toArgbInt(),
                tintingStrength = tintingStrength,
                note = note,
                sortOrder = sortOrder,
            ),
        )
    }

    override suspend fun getPaint(id: Long): Paint? = paintDao.getById(id)?.toDomain()

    override suspend fun updatePaint(paint: Paint) {
        paintDao.update(paint.toEntity())
    }

    override suspend fun restorePaint(paint: Paint): Long =
        paintDao.insert(paint.toEntity().copy(id = 0L))

    override suspend fun deletePaint(id: Long) {
        paintDao.deleteById(id)
    }

    override suspend fun reorderPaints(paletteId: Long, orderedIds: List<Long>) {
        val existing = paintDao.getByPalette(paletteId).map(PaintEntity::id).toSet()
        val assignments = orderedIds
            .filter { it in existing }
            .mapIndexed { index, id -> id to index }
        paintDao.updateSortOrders(assignments)
    }

    override suspend fun ensureStarterPalette(): Long = database.withTransaction {
        paletteDao.getAll().firstOrNull()?.let { return@withTransaction it.id }

        val paletteId = paletteDao.insert(
            PaletteEntity(
                name = STARTER_PALETTE_NAME,
                type = PaletteType.OPAQUE.name,
                createdAt = clock(),
            ),
        )
        StarterPalettes.basicAcrylic.forEachIndexed { index, (paintName, hex) ->
            paintDao.insert(
                PaintEntity(
                    paletteId = paletteId,
                    name = paintName,
                    colorArgb = (Rgb.parseHex(hex) ?: Rgb.BLACK).toArgbInt(),
                    sortOrder = index,
                ),
            )
        }
        paletteId
    }

    companion object {
        /** Display name of the palette that is created on first launch. */
        const val STARTER_PALETTE_NAME: String = "Basic Acrylic"
    }
}
