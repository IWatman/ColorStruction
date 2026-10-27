package com.colorstruction.data

import com.colorstruction.color.Rgb
import com.colorstruction.mixing.PaletteType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** JVM tests for the entity / domain mappers. */
class MapperTest {

    private val oneByte = 1.0 / 255.0

    @Test
    fun paletteRoundTripKeepsAllFields() {
        val entity = PaletteEntity(id = 7L, name = "Watercolors", type = "WATERCOLOR", createdAt = 42L)

        val domain = entity.toDomain()

        assertEquals(7L, domain.id)
        assertEquals("Watercolors", domain.name)
        assertEquals(PaletteType.WATERCOLOR, domain.type)
        assertEquals(42L, domain.createdAt)
        assertEquals(entity, domain.toEntity())
    }

    @Test
    fun unknownPaletteTypeFallsBackToOpaque() {
        val entity = PaletteEntity(id = 1L, name = "Legacy", type = "SOMETHING_NEW", createdAt = 0L)

        assertEquals(PaletteType.OPAQUE, entity.toDomain().type)
    }

    @Test
    fun paintRoundTripKeepsAllFields() {
        val color = Rgb(0x00 / 255.0, 0x21 / 255.0, 0x85 / 255.0)
        val entity = PaintEntity(
            id = 3L,
            paletteId = 1L,
            name = "Ultramarine Blue",
            colorArgb = 0xFF002185.toInt(),
            tintingStrength = 1.25,
            note = "cool",
            sortOrder = 5,
        )

        val domain = entity.toDomain()

        assertEquals(3L, domain.id)
        assertEquals(1L, domain.paletteId)
        assertEquals("Ultramarine Blue", domain.name)
        assertEquals(color.r, domain.color.r, oneByte)
        assertEquals(color.g, domain.color.g, oneByte)
        assertEquals(color.b, domain.color.b, oneByte)
        assertEquals(1.25, domain.tintingStrength, 0.0)
        assertEquals("cool", domain.note)
        assertEquals(5, domain.sortOrder)
        assertEquals(entity, domain.toEntity())
    }

    @Test
    fun colorSurvivesArgbRoundTrip() {
        val argb = 0xFFC3262E.toInt()

        val entity = PaintEntity(
            id = 0L,
            paletteId = 1L,
            name = "Cadmium Red",
            colorArgb = argb,
            sortOrder = 0,
        )

        val paint = entity.toDomain()
        assertEquals(argb, paint.color.toArgbInt())
        assertEquals(argb, paint.toEntity().colorArgb)
    }

    @Test
    fun nullNoteIsPreserved() {
        val paint = PaintEntity(
            id = 1L,
            paletteId = 1L,
            name = "White",
            colorArgb = 0xFFFFFFFF.toInt(),
            sortOrder = 0,
        ).toDomain()

        assertNull(paint.note)
        assertNull(paint.toEntity().note)
    }

    @Test
    fun paintMapsToSolverPaint() {
        val paint = Paint(
            id = 9L,
            paletteId = 2L,
            name = "Yellow Ochre",
            color = Rgb.parseHex("#C99B3F")!!,
            tintingStrength = 0.8,
            note = "earthy",
            sortOrder = 1,
        )

        val solverPaint = paint.toSolverPaint()

        assertEquals(9L, solverPaint.id)
        assertEquals("Yellow Ochre", solverPaint.name)
        assertEquals(paint.color, solverPaint.color)
        assertEquals(0.8, solverPaint.tintingStrength, 0.0)
    }
}
