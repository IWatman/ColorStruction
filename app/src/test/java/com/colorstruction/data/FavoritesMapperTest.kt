package com.colorstruction.data

import com.colorstruction.color.Rgb
import com.colorstruction.mixing.Recipe
import com.colorstruction.mixing.RecipeComponent
import com.colorstruction.mixing.SolverPaint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for the favorite domain mappers ([Recipe.toFavorite], stored <-> domain). */
class FavoritesMapperTest {

    private val ultramarine = SolverPaint(
        id = 7L,
        name = "Ultramarine",
        color = Rgb.fromArgbInt(0xFF002185.toInt()),
        tintingStrength = 1.5,
    )

    private val target = Rgb.fromArgbInt(0xFF3F7FBF.toInt())

    private val watercolorRecipe = Recipe(
        components = listOf(
            RecipeComponent(paint = ultramarine, isWater = false, weight = 0.65),
            RecipeComponent(paint = null, isWater = true, weight = 0.35),
        ),
        result = Rgb.fromArgbInt(0xFF4080C0.toInt()),
        deltaE = 1.25,
    )

    @Test
    fun toFavoriteSnapshotsPaintsAndWater() {
        val favorite = watercolorRecipe.toFavorite(target, "Watercolor", clock = { 1234L })

        assertEquals(0L, favorite.id)
        assertEquals(target, favorite.target)
        assertEquals("Watercolor", favorite.paletteName)
        assertEquals(1.25, favorite.deltaE, 0.0)
        assertEquals(1234L, favorite.createdAt)
        assertEquals(2, favorite.components.size)

        val paint = favorite.components[0]
        assertEquals("Ultramarine", paint.paintName)
        assertEquals(ultramarine.color, paint.color)
        assertFalse(paint.isWater)
        assertEquals(0.65, paint.weight, 0.0)

        val water = favorite.components[1]
        assertEquals("", water.paintName)
        assertTrue(water.isWater)
        assertEquals(0.35, water.weight, 0.0)
    }

    @Test
    fun favoriteRoundTripsThroughEntities() {
        val favorite = watercolorRecipe.toFavorite(target, "Watercolor", clock = { 1234L })

        val stored = FavoriteWithComponents(
            favorite = favorite.toEntity(),
            components = favorite.components
                .mapIndexed { index, it -> it.toEntity(favoriteId = 0L, position = index) }
                .reversed(),
        )

        assertEquals(favorite, stored.toDomain())
    }

    @Test
    fun entityKeepsSnapshotFields() {
        val favorite = watercolorRecipe.toFavorite(target, "Watercolor", clock = { 99L })
        val entity = favorite.toEntity()

        assertEquals(target.toArgbInt(), entity.targetArgb)
        assertEquals(99L, entity.createdAt)
        assertEquals(favorite.note, entity.note)
    }
}
