package com.colorstruction.mixing

import com.colorstruction.color.Rgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTest {

    private fun paint(id: Long, hex: String = "#3366CC") = SolverPaint(
        id = id,
        name = "Paint $id",
        color = Rgb.parseHex(hex)!!,
    )

    private fun recipeOf(deltaE: Double) = Recipe(
        components = listOf(RecipeComponent(paint(1), isWater = false, weight = 1.0)),
        result = Rgb.WHITE,
        deltaE = deltaE,
    )

    @Test
    fun paintComponentCarriesPaintAndWeight() {
        val component = RecipeComponent(paint(7), isWater = false, weight = 0.4)
        assertEquals(7L, component.paint!!.id)
        assertEquals(0.4, component.weight, 1e-9)
        assertTrue(!component.isWater)
    }

    @Test(expected = IllegalArgumentException::class)
    fun componentCannotBeBothPaintAndWater() {
        RecipeComponent(paint(1), isWater = true, weight = 0.5)
    }

    @Test(expected = IllegalArgumentException::class)
    fun componentCannotBeNeitherPaintNorWater() {
        RecipeComponent(null, isWater = false, weight = 0.5)
    }

    @Test
    fun waterComponentHasNoPaint() {
        val water = RecipeComponent(null, isWater = true, weight = 0.5)
        assertNull(water.paint)
        assertTrue(water.isWater)
    }

    @Test
    fun paintCountExcludesWater() {
        val recipe = Recipe(
            components = listOf(
                RecipeComponent(paint(1), isWater = false, weight = 0.3),
                RecipeComponent(paint(2), isWater = false, weight = 0.2),
                RecipeComponent(null, isWater = true, weight = 0.5),
            ),
            result = Rgb.WHITE,
            deltaE = 3.0,
        )
        assertEquals(3, recipe.components.size)
        assertEquals(2, recipe.paintCount)
        assertEquals(0.5, recipe.paintWeight, 1e-9)
    }

    @Test
    fun paintCountWithoutWaterCountsEverything() {
        val recipe = Recipe(
            components = listOf(
                RecipeComponent(paint(1), isWater = false, weight = 0.5),
                RecipeComponent(paint(2), isWater = false, weight = 0.5),
            ),
            result = Rgb.WHITE,
            deltaE = 0.1,
        )
        assertEquals(2, recipe.paintCount)
        assertEquals(1.0, recipe.paintWeight, 1e-9)
    }

    @Test
    fun accuracyBucketsFollowThresholds() {
        assertEquals(Accuracy.ALMOST_EXACT, recipeOf(0.0).accuracy())
        assertEquals(Accuracy.ALMOST_EXACT, recipeOf(1.99).accuracy())
        assertEquals(Accuracy.CLOSE, recipeOf(2.0).accuracy())
        assertEquals(Accuracy.CLOSE, recipeOf(4.99).accuracy())
        assertEquals(Accuracy.SIMILAR, recipeOf(5.0).accuracy())
        assertEquals(Accuracy.SIMILAR, recipeOf(9.99).accuracy())
        assertEquals(Accuracy.NOT_POSSIBLE, recipeOf(10.0).accuracy())
        assertEquals(Accuracy.NOT_POSSIBLE, recipeOf(42.0).accuracy())
    }

    @Test
    fun starterPaletteHasTwelveColors() {
        assertEquals(12, StarterPalettes.basicAcrylic.size)
        assertEquals(12, StarterPalettes.basicAcrylic.map { it.first }.toSet().size)
    }

    @Test
    fun starterPaletteEntriesAreValidHex() {
        for ((name, hex) in StarterPalettes.basicAcrylic) {
            assertTrue("name of $hex is blank", name.isNotBlank())
            assertTrue("$name is not #RRGGBB: $hex", Regex("^#[0-9A-Fa-f]{6}$").matches(hex))
            val color = Rgb.parseHex(hex)
            assertNotNull("$name does not parse as a color: $hex", color)
        }
    }

    @Test
    fun starterPaletteCanBeTurnedIntoPaints() {
        val paints = StarterPalettes.basicAcrylic.mapIndexed { index, (name, hex) ->
            SolverPaint(id = index.toLong(), name = name, color = Rgb.parseHex(hex)!!)
        }
        assertEquals(12, paints.size)
        assertTrue(paints.all { it.tintingStrength == 1.0 })
        val white = paints.first { it.name == "Titanium White" }
        assertTrue(white.color.toLab().l > 90.0)
        val black = paints.first { it.name == "Ivory Black" }
        assertTrue(black.color.toLab().l < 20.0)
    }

    @Test
    fun paletteTypesCoverBothModes() {
        assertEquals(2, PaletteType.entries.size)
        assertEquals(
            listOf(PaletteType.OPAQUE, PaletteType.WATERCOLOR),
            PaletteType.entries.toList(),
        )
    }
}
