package com.colorstruction.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.colorstruction.color.Rgb
import com.colorstruction.mixing.PaletteType
import com.colorstruction.mixing.StarterPalettes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** JVM tests for [RoomPaintRepository] against an in-memory Room database. */
@RunWith(RobolectricTestRunner::class)
class PaintRepositoryTest {

    private lateinit var database: ColorStructionDatabase
    private lateinit var repository: RoomPaintRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ColorStructionDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RoomPaintRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun paletteWithPaints(vararg names: String): Long {
        val paletteId = repository.createPalette("Test", PaletteType.OPAQUE)
        names.forEach { repository.addPaint(paletteId, it, Rgb.WHITE) }
        return paletteId
    }

    @Test
    fun createsRenamesAndRetagsPalette() = runBlocking {
        val id = repository.createPalette("First", PaletteType.OPAQUE)
        repository.renamePalette(id, "Renamed")
        repository.setPaletteType(id, PaletteType.WATERCOLOR)

        val palette = repository.observePalettes().first().single()
        assertEquals(id, palette.id)
        assertEquals("Renamed", palette.name)
        assertEquals(PaletteType.WATERCOLOR, palette.type)
    }

    @Test
    fun addPaintAppendsToTheEnd() = runBlocking {
        val paletteId = paletteWithPaints("a", "b", "c")

        val paints = repository.observePaints(paletteId).first()

        assertEquals(listOf("a", "b", "c"), paints.map(Paint::name))
        assertEquals(listOf(0, 1, 2), paints.map(Paint::sortOrder))
    }

    @Test
    fun addPaintKeepsGivenTintingStrengthAndNote() = runBlocking {
        val paletteId = repository.createPalette("Test", PaletteType.OPAQUE)
        repository.addPaint(paletteId, "Strong", Rgb(1.0, 0.0, 0.0), 2.5, "very strong")

        val paint = repository.observePaints(paletteId).first().single()

        assertEquals(2.5, paint.tintingStrength, 0.0)
        assertEquals("very strong", paint.note)
        assertEquals(0xFF0000, paint.color.toArgbInt() and 0xFFFFFF)
    }

    @Test
    fun updatePaintStoresChanges() = runBlocking {
        val paletteId = paletteWithPaints("a")
        val paint = repository.observePaints(paletteId).first().single()

        repository.updatePaint(
            paint.copy(
                name = "b",
                color = Rgb(0.0, 0.0, 1.0),
                tintingStrength = 0.5,
                note = "note",
            ),
        )

        val updated = repository.observePaints(paletteId).first().single()
        assertEquals("b", updated.name)
        assertEquals("note", updated.note)
        assertEquals(0.5, updated.tintingStrength, 0.0)
        assertEquals(0x0000FF, updated.color.toArgbInt() and 0xFFFFFF)
    }

    @Test
    fun deletePaintRemovesOnlyThatPaint() = runBlocking {
        val paletteId = paletteWithPaints("a", "b")
        val first = repository.observePaints(paletteId).first().first()

        repository.deletePaint(first.id)

        assertEquals(listOf("b"), repository.observePaints(paletteId).first().map(Paint::name))
    }

    @Test
    fun reorderPaintsRewritesSortOrders() = runBlocking {
        val paletteId = paletteWithPaints("a", "b", "c")
        val ids = repository.observePaints(paletteId).first().map(Paint::id)

        repository.reorderPaints(paletteId, listOf(ids[2], ids[0], ids[1]))

        val paints = repository.observePaints(paletteId).first()
        assertEquals(listOf("c", "a", "b"), paints.map(Paint::name))
        assertEquals(listOf(0, 1, 2), paints.map(Paint::sortOrder))
    }

    @Test
    fun deletePaletteRemovesItsPaints() = runBlocking {
        val keptId = paletteWithPaints("kept")
        val removedId = paletteWithPaints("removed")

        repository.deletePalette(removedId)

        assertTrue(repository.observePalettes().first().none { it.id == removedId })
        assertTrue(repository.observePaints(removedId).first().isEmpty())
        assertEquals(1, repository.observePaints(keptId).first().size)
    }

    @Test
    fun ensureStarterPaletteCreatesBasicAcrylicOnce() = runBlocking {
        val firstId = repository.ensureStarterPalette()
        val secondId = repository.ensureStarterPalette()

        assertEquals(firstId, secondId)
        val palettes = repository.getPalettes()
        assertEquals(1, palettes.size)
        assertEquals(RoomPaintRepository.STARTER_PALETTE_NAME, palettes.single().name)
        assertEquals(PaletteType.OPAQUE, palettes.single().type)

        val paints = repository.observePaints(firstId).first()
        assertEquals(StarterPalettes.basicAcrylic.size, paints.size)
        assertEquals(12, paints.size)
        assertEquals(StarterPalettes.basicAcrylic.map { it.first }, paints.map(Paint::name))
        assertEquals(listOf(0, 1, 2, 3), paints.take(4).map(Paint::sortOrder))
        assertEquals(
            Rgb.parseHex(StarterPalettes.basicAcrylic.first().second)!!.toArgbInt(),
            paints.first().color.toArgbInt(),
        )
    }

    @Test
    fun ensureStarterPaletteKeepsExistingPalettes() = runBlocking {
        val existingId = repository.createPalette("Mine", PaletteType.WATERCOLOR)

        val id = repository.ensureStarterPalette()

        assertEquals(existingId, id)
        assertEquals(1, repository.getPalettes().size)
        assertNotEquals(RoomPaintRepository.STARTER_PALETTE_NAME, repository.getPalettes().single().name)
    }
}
