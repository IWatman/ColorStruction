package com.colorstruction.data

import com.colorstruction.color.Rgb
import com.colorstruction.mixing.PaletteType
import com.colorstruction.ui.paints.FakePaintRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** JVM tests for [PaletteTransfer] against the in-memory fake repository. */
class PaletteTransferTest {

    private val white = Rgb.parseHex("#FFFFFF")!!
    private val red = Rgb.parseHex("#CC0000")!!
    private val blue = Rgb.parseHex("#0000CC")!!

    private fun fakeRepository(): FakePaintRepository = FakePaintRepository(
        palettes = listOf(
            Palette(id = 1, name = "Basic Acrylic", type = PaletteType.OPAQUE, createdAt = 1),
        ),
        paints = listOf(
            Paint(id = 11, paletteId = 1, name = "Third", color = blue, sortOrder = 2),
            Paint(id = 12, paletteId = 1, name = "First", color = white, sortOrder = 0),
            Paint(id = 13, paletteId = 1, name = "Second", color = red, note = "warm", sortOrder = 1),
        ),
    )

    @Test
    fun exportReadsPaletteAndPaintsInDisplayOrder() = runBlocking {
        val data = PaletteTransfer(fakeRepository()).export(1)

        assertEquals("Basic Acrylic", data.name)
        assertEquals(PaletteType.OPAQUE, data.type)
        assertEquals(listOf("First", "Second", "Third"), data.paints.map { it.name })
        assertEquals(red, data.paints[1].color)
        assertEquals("warm", data.paints[1].note)
    }

    @Test(expected = IllegalArgumentException::class)
    fun exportOfMissingPaletteFails(): Unit = runBlocking {
        PaletteTransfer(fakeRepository()).export(99)
    }

    @Test
    fun importCreatesUniquelyNamedPaletteWithPaintsInOrder() = runBlocking {
        val repository = fakeRepository()
        val transfer = PaletteTransfer(repository)
        val data = PaletteFileData(
            name = "Basic Acrylic",
            type = PaletteType.WATERCOLOR,
            paints = listOf(
                PaintFileData("Third", blue, 1.5, null),
                PaintFileData("First", white, 0.5, "note"),
            ),
        )

        val first = transfer.import(data)
        val second = transfer.import(data)
        val palettes = repository.getPalettes()

        assertNotEquals(1L, first)
        assertNotEquals(first, second)
        assertEquals("Basic Acrylic (2)", palettes.first { it.id == first }.name)
        assertEquals("Basic Acrylic (3)", palettes.first { it.id == second }.name)
        assertEquals(PaletteType.WATERCOLOR, palettes.first { it.id == first }.type)

        val paints = repository.getPaints(first)
        assertEquals(listOf("Third", "First"), paints.map { it.name })
        assertEquals(listOf(1.5, 0.5), paints.map { it.tintingStrength })
        assertEquals("note", paints[1].note)
        assertEquals(blue, paints[0].color)
    }

    @Test
    fun importLeavesExistingPaletteAndItsPaintsAlone() = runBlocking {
        val repository = fakeRepository()
        PaletteTransfer(repository).import(
            PaletteFileData("Basic Acrylic", PaletteType.OPAQUE, listOf(PaintFileData("New", red))),
        )

        assertEquals(2, repository.getPalettes().size)
        assertEquals(3, repository.getPaints(1).size)
        assertEquals(listOf("First", "Second", "Third"), repository.getPaints(1).map { it.name })
    }
}
