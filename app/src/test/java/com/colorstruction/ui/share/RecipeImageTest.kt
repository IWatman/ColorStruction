package com.colorstruction.ui.share

import com.colorstruction.color.Rgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Tests for the off-screen recipe image renderer. Needs Robolectric for `android.graphics`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecipeImageTest {

    private val target = Rgb.parseHex("#3366CC")!!
    private val red = Rgb.parseHex("#CC2200")!!
    private val blue = Rgb.parseHex("#0033AA")!!

    private fun data(components: Int) = RecipeImageData(
        target = target,
        result = null,
        components = List(components) { index -> Triple("Paint $index", red, "1") },
        deltaE = 0.7,
        paletteName = "Test",
        shares = List(components) { 1.0 },
    )

    private fun texts() = RecipeImageTexts(
        title = "Mixing recipe",
        target = "Target",
        result = "Result",
        deltaE = "ΔE 0.7",
        fromPalette = "From palette: Test",
        footer = "Made with ColorStruction",
    )

    /** X coordinate inside the left (target) half of the big swatch. */
    private val targetSampleX: Int =
        (RECIPE_IMAGE_PADDING + (RECIPE_IMAGE_WIDTH - 2 * RECIPE_IMAGE_PADDING) / 4f).toInt()

    /** Y coordinate inside the big swatch. */
    private val swatchSampleY: Int = (RECIPE_IMAGE_SWATCH_TOP + RECIPE_IMAGE_SWATCH_HEIGHT / 2f).toInt()

    @Test
    fun imageIsFullWidthAndGrowsWithComponents() {
        val small = renderRecipeImage(data(2), texts())
        val large = renderRecipeImage(data(5), texts())
        assertEquals(1080, small.width)
        assertTrue("height must grow: ${small.height} vs ${large.height}", large.height > small.height)
        assertEquals(recipeImageHeight(4), renderRecipeImage(data(4), texts()).height)
    }

    @Test
    fun targetHalfOfTheSwatchHasTheTargetColor() {
        val bitmap = renderRecipeImage(data(3), texts())
        assertEquals(target.toArgbInt(), bitmap.getPixel(targetSampleX, swatchSampleY))
    }

    @Test
    fun resultHalfOfTheSwatchHasTheResultColor() {
        val bitmap = renderRecipeImage(data(3).copy(result = blue), texts())
        val x = (RECIPE_IMAGE_WIDTH - RECIPE_IMAGE_PADDING - 100f).toInt()
        assertEquals(blue.toArgbInt(), bitmap.getPixel(x, swatchSampleY))
    }
}
