package com.colorstruction.mixing

import com.colorstruction.color.DeltaE
import com.colorstruction.color.Rgb
import com.colorstruction.color.Spectral
import com.colorstruction.color.SpectralColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeSolverTest {

    private fun palette(includeWhite: Boolean = true): List<SolverPaint> =
        StarterPalettes.basicAcrylic
            .filter { includeWhite || it.first != "Titanium White" }
            .mapIndexed { index, (name, hex) -> paint(index.toLong(), name, hex) }

    private fun paint(id: Long, name: String, hex: String) =
        SolverPaint(id = id, name = name, color = Rgb.parseHex(hex)!!)

    private fun paintNamed(paints: List<SolverPaint>, name: String): SolverPaint =
        paints.first { it.name == name }

    private fun weightOf(recipe: Recipe, paintId: Long): Double =
        recipe.components.first { it.paint?.id == paintId }.weight

    private fun paintIds(recipe: Recipe): List<Long> = recipe.paintIds()

    // --- target is one of the palette paints -------------------------------------------------

    @Test
    fun targetEqualToPalettePaintGivesSinglePaintRecipe() {
        val paints = palette()
        val target = paintNamed(paints, "Ultramarine Blue").color
        val recipes = RecipeSolver(paints, PaletteType.OPAQUE).solve(target, maxComponents = 3)

        val best = recipes.first()
        assertEquals(1, best.paintCount)
        assertEquals("Ultramarine Blue", best.components.single().paint?.name)
        assertTrue("ΔE was ${best.deltaE}", best.deltaE < 0.5)
    }

    // --- known two paint mix -----------------------------------------------------------------

    @Test
    fun findsKnownTwoPaintMix() {
        val paints = palette()
        val yellow = paintNamed(paints, "Cadmium Yellow Light")
        val blue = paintNamed(paints, "Ultramarine Blue")
        val target = Spectral.mix(listOf(yellow.color to 0.3, blue.color to 0.7))

        val recipes = RecipeSolver(paints, PaletteType.OPAQUE).solve(target, maxComponents = 3)
        val best = recipes.first()

        assertEquals(2, best.paintCount)
        assertEquals(setOf(yellow.id, blue.id), paintIds(best).toSet())
        assertEquals(0.3, weightOf(best, yellow.id), 0.05)
        assertEquals(0.7, weightOf(best, blue.id), 0.05)
        assertTrue("ΔE was ${best.deltaE}", best.deltaE < 1.0)
    }

    // --- ranking, weights and diversity ------------------------------------------------------

    @Test
    fun fewerPaintsArePreferredWithinOneDeltaE() {
        val paints = palette()
        val target = Spectral.mix(
            listOf(
                paintNamed(paints, "Phthalo Blue").color to 0.4,
                paintNamed(paints, "Burnt Sienna").color to 0.6,
            ),
        )
        val recipes = RecipeSolver(paints, PaletteType.OPAQUE).solve(target, maxComponents = 5)

        for (earlier in recipes.indices) {
            for (later in earlier + 1 until recipes.size) {
                val fewer = recipes[later]
                val more = recipes[earlier]
                val violates = fewer.paintCount < more.paintCount &&
                    fewer.deltaE <= more.deltaE + 1.0
                assertFalse(
                    "recipe $later (${fewer.paintCount} paints, ΔE=${fewer.deltaE}) should rank " +
                        "before $earlier (${more.paintCount} paints, ΔE=${more.deltaE})",
                    violates,
                )
            }
        }
    }

    @Test
    fun weightsAreNormalizedAndAboveThreshold() {
        val targets = listOf("#7A8B9C", "#C3A15E", "#203040").map { Rgb.parseHex(it)!! }
        val solvers = listOf(
            RecipeSolver(palette(), PaletteType.OPAQUE),
            RecipeSolver(palette(includeWhite = false), PaletteType.WATERCOLOR),
        )
        for (solver in solvers) {
            for (target in targets) {
                val recipes = solver.solve(target, maxComponents = 3, resultCount = 5)
                assertTrue("no recipes for ${target.toHex()}", recipes.isNotEmpty())
                for (recipe in recipes) {
                    val sum = recipe.components.sumOf { it.weight }
                    assertEquals("weights of ${target.toHex()}", 1.0, sum, 1e-9)
                    for (component in recipe.components) {
                        assertTrue("weight ${component.weight} < 0.01", component.weight >= 0.01)
                    }
                }
            }
        }
    }

    @Test
    fun returnedRecipesHaveDistinctPaintSetsAndNoStrictSupersets() {
        val paints = palette()
        val target = Rgb.parseHex("#6E7F4E")!!
        val recipes = RecipeSolver(paints, PaletteType.OPAQUE).solve(target, maxComponents = 5)

        val seen = ArrayList<List<Long>>()
        for (recipe in recipes) {
            val ids = paintIds(recipe)
            assertFalse("duplicate paint set $ids", seen.any { it == ids })
            assertFalse(
                "paint set $ids is a strict superset of an earlier recipe",
                seen.any { it.size < ids.size && ids.containsAll(it) },
            )
            seen.add(ids)
        }
    }

    // --- watercolor mode ---------------------------------------------------------------------

    @Test
    fun watercolorUsesWaterInsteadOfWhitePaint() {
        val paints = palette(includeWhite = false)
        val yellow = paintNamed(paints, "Cadmium Yellow Light")
        val target = Spectral.mix(listOf(yellow.color to 0.2, Rgb.WHITE to 0.8))

        val recipe = RecipeSolver(paints, PaletteType.WATERCOLOR)
            .solve(target, maxComponents = 3)
            .first()

        assertEquals(1, recipe.paintCount)
        assertEquals(yellow.id, recipe.components.first { it.paint != null }.paint?.id)
        val water = recipe.components.first { it.isWater }
        assertEquals(0.8, water.weight, 0.05)
        assertTrue("ΔE was ${recipe.deltaE}", recipe.deltaE < 1.0)
    }

    @Test
    fun watercolorAllowsWaterWithSingleComponentCap() {
        val paints = palette(includeWhite = false)
        val red = paintNamed(paints, "Cadmium Red Medium")
        val target = Spectral.mix(listOf(red.color to 0.25, Rgb.WHITE to 0.75))

        val recipe = RecipeSolver(paints, PaletteType.WATERCOLOR)
            .solve(target, maxComponents = 1)
            .first()

        assertEquals(1, recipe.paintCount)
        assertTrue("water should be present", recipe.components.any { it.isWater })
        assertTrue("ΔE was ${recipe.deltaE}", recipe.deltaE < 1.5)
    }

    @Test
    fun opaqueModeNeverContainsWater() {
        val paints = palette()
        val targets = listOf("#F0E8D0", "#2B2B33", "#4C7A9E").map { Rgb.parseHex(it)!! }
        for (target in targets) {
            val recipes = RecipeSolver(paints, PaletteType.OPAQUE).solve(target, maxComponents = 3)
            assertTrue(recipes.isNotEmpty())
            for (recipe in recipes) {
                assertFalse(recipe.components.any { it.isWater })
            }
        }
    }

    // --- component cap -----------------------------------------------------------------------

    @Test
    fun componentCapRestrictsPaintCount() {
        val solver = RecipeSolver(palette(), PaletteType.OPAQUE)
        for (target in listOf("#908060", "#3C5A78").map { Rgb.parseHex(it)!! }) {
            val recipes = solver.solve(target, maxComponents = 1)
            assertTrue(recipes.isNotEmpty())
            assertTrue(recipes.all { it.paintCount == 1 })
        }
    }

    @Test
    fun componentCapIsClampedLowAndHigh() {
        val paints = palette()
        val solver = RecipeSolver(paints, PaletteType.OPAQUE)
        val target = Rgb.parseHex("#7A8B9C")!!

        fun signature(recipes: List<Recipe>) =
            recipes.map { it.paintIds() to it.deltaE }

        assertTrue(solver.solve(target, maxComponents = 0).all { it.paintCount == 1 })
        assertEquals(
            signature(solver.solve(target, maxComponents = 1)),
            signature(solver.solve(target, maxComponents = 0)),
        )
        assertEquals(
            signature(solver.solve(target, maxComponents = 5)),
            signature(solver.solve(target, maxComponents = 9)),
        )
        assertTrue(solver.solve(target, maxComponents = 9).all { it.paintCount <= 5 })
    }

    // --- cancellation, empty palette ---------------------------------------------------------

    @Test
    fun cancelledSearchReturnsQuicklyWithoutException() {
        val solver = RecipeSolver(palette(), PaletteType.OPAQUE)
        val target = Rgb.parseHex("#7A8B9C")!!
        val startedAt = System.nanoTime()
        val recipes = solver.solve(target, maxComponents = 5, isCancelled = { true })
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

        assertTrue("cancelled search took $elapsedMs ms", elapsedMs < 2000)
        assertTrue(recipes.size <= 5)
    }

    @Test
    fun emptyPaletteGivesEmptyResult() {
        assertTrue(RecipeSolver(emptyList(), PaletteType.OPAQUE).solve(Rgb.parseHex("#123456")!!).isEmpty())
        assertTrue(
            RecipeSolver(emptyList(), PaletteType.WATERCOLOR)
                .solve(Rgb.parseHex("#123456")!!, maxComponents = 5)
                .isEmpty(),
        )
    }

    // --- MixKernel and evaluate --------------------------------------------------------------

    @Test
    fun mixKernelMatchesSpectralMixPrepared() {
        val paints = palette()
        val colors = listOf("Cadmium Yellow Light", "Ultramarine Blue", "Titanium White")
            .map { paintNamed(paints, it).color }
        val tints = listOf(1.0, 0.35, 1.4)
        val weights = doubleArrayOf(0.55, 0.3, 0.15)

        val prepared = colors.indices.map {
            PreparedComponent(SpectralColor.of(colors[it]), weights[it], tints[it])
        }
        val expected = Spectral.mixPrepared(prepared.map { it.toMixComponent() })
        val kernel = MixKernel(
            colors = Array(colors.size) { prepared[it].color },
            tintingStrengths = DoubleArray(colors.size) { prepared[it].tintingStrength },
        )
        val actual = kernel.rgb(weights)

        assertEquals(expected.r, actual.r, 1e-12)
        assertEquals(expected.g, actual.g, 1e-12)
        assertEquals(expected.b, actual.b, 1e-12)
        assertEquals(DeltaE.ciede2000(expected.toLab(), kernel.lab(weights)), 0.0, 1e-9)
    }

    @Test
    fun mixKernelIgnoresZeroWeightsLikeTheReference() {
        val paints = palette()
        val colors = listOf("Phthalo Green", "Ivory Black")
            .map { SpectralColor.of(paintNamed(paints, it).color) }
        val kernel = MixKernel(Array(2) { colors[it] }, doubleArrayOf(1.0, 1.0))

        val actual = kernel.rgb(doubleArrayOf(0.4, 0.0))
        val expected = Spectral.mixPrepared(
            listOf(PreparedComponent(colors[0], 1.0, 1.0).toMixComponent()),
        )

        assertEquals(expected.r, actual.r, 1e-12)
        assertEquals(expected.g, actual.g, 1e-12)
        assertEquals(expected.b, actual.b, 1e-12)
        // No pigment at all means no paint on the paper: pure white.
        assertEquals(Rgb.WHITE, kernel.rgb(doubleArrayOf(0.0, 0.0)))
    }

    @Test
    fun evaluateWithOwnWeightsReproducesTheRecipe() {
        val paints = palette()
        val solver = RecipeSolver(paints, PaletteType.OPAQUE)
        val target = Rgb.parseHex("#4C7A9E")!!
        val recipe = solver.solve(target, maxComponents = 3).first()

        val reevaluated = solver.evaluate(target, recipe, recipe.components.map { it.weight })

        assertEquals(recipe.deltaE, reevaluated.deltaE, 1e-9)
        assertEquals(recipe.result.r, reevaluated.result.r, 1e-12)
        assertEquals(recipe.result.g, reevaluated.result.g, 1e-12)
        assertEquals(recipe.result.b, reevaluated.result.b, 1e-12)
        assertEquals(
            DeltaE.ciede2000(recipe.result.toLab(), target.toLab()),
            recipe.deltaE,
            1e-9,
        )
    }

    /** Small helper so tests can build reference [com.colorstruction.color.PreparedMixComponent]s. */
    private class PreparedComponent(
        val color: SpectralColor,
        val weight: Double,
        val tintingStrength: Double,
    ) {
        fun toMixComponent() =
            com.colorstruction.color.PreparedMixComponent(color, weight, tintingStrength)
    }
}
