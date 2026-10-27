package com.colorstruction.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** JVM tests for [PaintDao] against an in-memory Room database. */
@RunWith(RobolectricTestRunner::class)
class PaintDaoTest {

    private lateinit var database: ColorStructionDatabase
    private var paletteId: Long = 0L
    private var otherPaletteId: Long = 0L
    private lateinit var paintDao: PaintDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ColorStructionDatabase::class.java,
        ).allowMainThreadQueries().build()
        paintDao = database.paintDao()
        val paletteDao = database.paletteDao()
        paletteId = runBlocking {
            paletteDao.insert(PaletteEntity(name = "Acrylic", type = "OPAQUE", createdAt = 1L))
        }
        otherPaletteId = runBlocking {
            paletteDao.insert(PaletteEntity(name = "Watercolor", type = "WATERCOLOR", createdAt = 2L))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun insertPaint(
        name: String,
        sortOrder: Int,
        ownerId: Long = paletteId,
        colorArgb: Int = 0xFF112233.toInt(),
    ): Long = paintDao.insert(
        PaintEntity(
            paletteId = ownerId,
            name = name,
            colorArgb = colorArgb,
            sortOrder = sortOrder,
        ),
    )

    @Test
    fun insertsAndReadsPaint() = runBlocking {
        val id = paintDao.insert(
            PaintEntity(
                paletteId = paletteId,
                name = "Ultramarine",
                colorArgb = 0xFF002185.toInt(),
                tintingStrength = 1.5,
                note = "granulating",
                sortOrder = 0,
            ),
        )

        val stored = paintDao.getById(id)
        assertEquals("Ultramarine", stored?.name)
        assertEquals(0xFF002185.toInt(), stored?.colorArgb)
        assertEquals(1.5, stored?.tintingStrength ?: 0.0, 0.0)
        assertEquals("granulating", stored?.note)
    }

    @Test
    fun defaultTintingStrengthIsOne() = runBlocking {
        val id = insertPaint(name = "White", sortOrder = 0)

        assertEquals(1.0, paintDao.getById(id)?.tintingStrength ?: 0.0, 0.0)
        assertNull(paintDao.getById(id)?.note)
    }

    @Test
    fun paintsAreOrderedBySortOrder() = runBlocking {
        insertPaint(name = "third", sortOrder = 2)
        insertPaint(name = "first", sortOrder = 0)
        insertPaint(name = "second", sortOrder = 1)

        val names = paintDao.observeByPalette(paletteId).first().map(PaintEntity::name)

        assertEquals(listOf("first", "second", "third"), names)
    }

    @Test
    fun paintsAreScopedToTheirPalette() = runBlocking {
        insertPaint(name = "mine", sortOrder = 0)
        insertPaint(name = "theirs", sortOrder = 0, ownerId = otherPaletteId)

        assertEquals(listOf("mine"), paintDao.getByPalette(paletteId).map(PaintEntity::name))
        assertEquals(listOf("theirs"), paintDao.getByPalette(otherPaletteId).map(PaintEntity::name))
        assertEquals(1, paintDao.countByPalette(paletteId))
    }

    @Test
    fun maxSortOrderIsNullForEmptyPalette() = runBlocking {
        assertNull(paintDao.maxSortOrder(paletteId))

        insertPaint(name = "only", sortOrder = 4)

        assertEquals(4, paintDao.maxSortOrder(paletteId))
    }

    @Test
    fun updateSortOrdersRewritesTheWholeOrder() = runBlocking {
        val a = insertPaint(name = "a", sortOrder = 0)
        val b = insertPaint(name = "b", sortOrder = 1)
        val c = insertPaint(name = "c", sortOrder = 2)

        paintDao.updateSortOrders(listOf(c to 0, a to 1, b to 2))

        val names = paintDao.observeByPalette(paletteId).first().map(PaintEntity::name)
        assertEquals(listOf("c", "a", "b"), names)
    }

    @Test
    fun updatesAndDeletesPaint() = runBlocking {
        val id = insertPaint(name = "old", sortOrder = 0)

        paintDao.update(
            PaintEntity(
                id = id,
                paletteId = paletteId,
                name = "new",
                colorArgb = 0xFF00FF00.toInt(),
                sortOrder = 0,
            ),
        )
        assertEquals("new", paintDao.getById(id)?.name)

        paintDao.deleteById(id)
        assertNull(paintDao.getById(id))
        assertEquals(0, paintDao.countByPalette(paletteId))
    }

    @Test
    fun deletingPaletteCascadesToItsPaints() = runBlocking {
        insertPaint(name = "a", sortOrder = 0)
        insertPaint(name = "b", sortOrder = 1)
        insertPaint(name = "other", sortOrder = 0, ownerId = otherPaletteId)
        assertEquals(2, paintDao.countByPalette(paletteId))

        database.paletteDao().deleteById(paletteId)

        assertEquals(0, paintDao.countByPalette(paletteId))
        assertEquals(1, paintDao.countByPalette(otherPaletteId))
    }
}
