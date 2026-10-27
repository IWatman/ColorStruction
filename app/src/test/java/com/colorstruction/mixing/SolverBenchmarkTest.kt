package com.colorstruction.mixing

import com.colorstruction.color.Hsv
import com.colorstruction.color.Rgb
import com.colorstruction.color.hsvToRgb
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rough performance guard for the solver: 30 paints, five fixed targets, component caps of 3
 * and 5. The budget is deliberately generous (it has to hold on a slow CI machine), while the
 * printed timings show what the solver really costs.
 */
class SolverBenchmarkTest {

    /** Generous per-solve budget in milliseconds. */
    private val budgetMs = 5000L

    private fun benchmarkPaints(): List<SolverPaint> {
        val paints = ArrayList<SolverPaint>(30)
        StarterPalettes.basicAcrylic.forEachIndexed { index, (name, hex) ->
            paints.add(SolverPaint(index.toLong(), name, Rgb.parseHex(hex)!!))
        }
        // 18 generated paints: nine hues at two value levels each.
        var id = paints.size.toLong()
        for (value in listOf(0.9, 0.45)) {
            for (hue in 0 until 360 step 40) {
                paints.add(
                    SolverPaint(
                        id = id++,
                        name = "Generated $hue/$value",
                        color = hsvToRgb(Hsv(hue.toDouble(), 0.75, value)),
                    ),
                )
            }
        }
        return paints
    }

    private fun benchmarkTargets(): List<Rgb> {
        val random = Random(20240607L)
        return List(5) { Rgb(random.nextDouble(), random.nextDouble(), random.nextDouble()) }
    }

    @Test
    fun thirtyPaintsStayWithinBudget() {
        val paints = benchmarkPaints()
        assertEquals(30, paints.size)
        val solver = RecipeSolver(paints, PaletteType.OPAQUE)
        val targets = benchmarkTargets()

        for (maxComponents in listOf(3, 5)) {
            for ((index, target) in targets.withIndex()) {
                val startedAt = System.nanoTime()
                val recipes = solver.solve(target, maxComponents = maxComponents, resultCount = 5)
                val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

                val best = recipes.firstOrNull()
                println(
                    "solve #$index maxComponents=$maxComponents: $elapsedMs ms, " +
                        "${recipes.size} recipes, best ΔE=${best?.deltaE}, " +
                        "paints=${best?.paintCount}, target=${target.toHex()}",
                )
                assertTrue("no recipes for ${target.toHex()}", recipes.isNotEmpty())
                assertTrue(
                    "solve #$index (maxComponents=$maxComponents) took $elapsedMs ms",
                    elapsedMs < budgetMs,
                )
            }
        }
    }
}
