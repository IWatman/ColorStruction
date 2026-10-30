package com.colorstruction.mixing

import com.colorstruction.color.DeltaE
import com.colorstruction.color.MixComponent
import com.colorstruction.color.Rgb
import com.colorstruction.color.Spectral
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos

class MixCorrectionTest {
    private val white: Rgb = requireNotNull(Rgb.parseHex("#F5F3EF"))
    private val yellow: Rgb = requireNotNull(Rgb.parseHex("#FFD700"))
    private val blue: Rgb = requireNotNull(Rgb.parseHex("#002185"))
    private val red: Rgb = requireNotNull(Rgb.parseHex("#C1121F"))
    private val umber: Rgb = requireNotNull(Rgb.parseHex("#5A3A22"))
    private val paper: Rgb = requireNotNull(Rgb.parseHex("#FFFFFF"))
    private val palette = listOf(
        SolverPaint(1, "White", white), SolverPaint(2, "Yellow", yellow),
        SolverPaint(3, "Blue", blue), SolverPaint(4, "Red", red),
        SolverPaint(5, "Burnt Umber", umber),
    )
    private val noWhite = palette.filter { it.id != 1L }

    private fun bluePlusWhite(whiteParts: Double): Rgb = Spectral.mix(
        listOf(MixComponent(blue, 1.0, 1.0), MixComponent(white, whiteParts, 1.0)),
    )
    private val target: Rgb = bluePlusWhite(4.0)
    private val current: Rgb = bluePlusWhite(2.0)
    private fun corrected(maxAdditions: Int = 2): CorrectionOutcome =
        MixCorrection.correct(current, target, palette, PaletteType.OPAQUE, maxAdditions)

    @Test
    fun tooDarkBatchGetsWhite() {
        val outcome = corrected()
        assertTrue("corrections=${outcome.corrections.size}", outcome.corrections.isNotEmpty())
        val best = outcome.corrections.first()
        assertTrue(
            "additions=${best.additions.map { it.paint?.name ?: "water" }}",
            best.additions.any { it.paint?.id == 1L },
        )
        assertTrue(
            "deltaE=${best.deltaE} before=${outcome.deltaEBefore}",
            best.deltaE <= outcome.deltaEBefore - MixCorrection.MIN_IMPROVEMENT,
        )
    }

    @Test
    fun matchingBatchNeedsNothing() {
        val outcome = MixCorrection.correct(current, current, palette, PaletteType.OPAQUE)
        assertTrue("corrections=${outcome.corrections.size}", outcome.corrections.isEmpty())
        assertTrue("deltaEBefore=${outcome.deltaEBefore}", outcome.deltaEBefore < 1e-6)
    }

    @Test
    fun watercolorOffersWaterOpaqueNever() {
        val wet = Spectral.mix(listOf(MixComponent(blue, 1.0, 1.0), MixComponent(yellow, 1.0, 1.0)))
        val pale = Spectral.mix(
            listOf(MixComponent(blue, 1.0, 1.0), MixComponent(yellow, 1.0, 1.0), MixComponent(paper, 2.0, 1.0)),
        )
        val watercolor = MixCorrection.correct(wet, pale, noWhite, PaletteType.WATERCOLOR)
        val wetAdditions = watercolor.corrections.flatMap { it.additions }
        assertTrue("corrections=$watercolor", wetAdditions.any { it.isWater })
        val opaque = MixCorrection.correct(wet, pale, noWhite, PaletteType.OPAQUE)
        assertFalse("corrections=$opaque", opaque.corrections.any { c -> c.additions.any { it.isWater } })
    }

    @Test
    fun amountsAndCountsStayInBounds() {
        for (maxAdditions in listOf(2, 1)) {
            val outcome = corrected(maxAdditions)
            assertTrue("results=${outcome.corrections.size}", outcome.corrections.size <= 3)
            for (correction in outcome.corrections) {
                assertTrue(
                    "additions=${correction.additions.size} max=$maxAdditions",
                    correction.additions.size <= maxAdditions,
                )
                for (addition in correction.additions) {
                    val amount = addition.amount
                    assertTrue(
                        "amount=$amount",
                        amount >= MixCorrection.MIN_AMOUNT && amount <= MixCorrection.MAX_AMOUNT,
                    )
                }
                val total = correction.additions.sumOf { it.amount }
                assertTrue("total=$total", total <= MixCorrection.MAX_AMOUNT + 1e-9)
            }
        }
    }

    @Test
    fun reportedDeltaEMatchesSpectralMix() {
        val outcome = corrected()
        assertTrue("corrections=${outcome.corrections.size}", outcome.corrections.isNotEmpty())
        for (correction in outcome.corrections) {
            val components = listOf(MixComponent(current, 1.0, 1.0)) + correction.additions.map {
                val paint = requireNotNull(it.paint) { "An opaque palette has no water additions" }
                MixComponent(paint.color, it.amount, paint.tintingStrength)
            }
            val mixed = Spectral.mix(components)
            val deltaE = DeltaE.ciede2000(mixed.toLab(), target.toLab())
            assertEquals("deltaE", deltaE, correction.deltaE, 1e-6)
            assertEquals("r", mixed.r, correction.result.r, 1e-9)
            assertEquals("g", mixed.g, correction.result.g, 1e-9)
            assertEquals("b", mixed.b, correction.result.b, 1e-9)
        }
    }

    @Test
    fun roundAmountSteps() {
        val cases = listOf(0.0123 to 0.01, 0.0137 to 0.015, 0.047 to 0.05, 0.23 to 0.25, 0.001 to 0.005, 5.0 to 1.0)
        for ((input, expected) in cases) {
            assertEquals("roundAmount($input)", expected, MixCorrection.roundAmount(input), 1e-9)
        }
    }

    @Test
    fun twentyPaintsAreFast() {
        val paints = (0 until 20).map { index ->
            val angle = index * 2.0 * PI / 20.0
            val color = Rgb(
                0.5 + 0.5 * cos(angle),
                0.5 + 0.5 * cos(angle - 2.0 * PI / 3.0),
                0.5 + 0.5 * cos(angle + 2.0 * PI / 3.0),
            )
            SolverPaint(id = index + 1L, name = "Paint $index", color = color)
        }
        val mix = Spectral.mix(listOf(MixComponent(paints[0].color, 1.0, 1.0)))
        val goal = Spectral.mix(
            listOf(MixComponent(paints[0].color, 1.0, 1.0), MixComponent(paints[10].color, 1.0, 1.0)),
        )
        val start = System.nanoTime()
        val outcome = MixCorrection.correct(mix, goal, paints, PaletteType.OPAQUE)
        val millis = (System.nanoTime() - start) / 1_000_000.0
        assertTrue("took $millis ms", millis < 2000.0)
        assertTrue("deltaEBefore=${outcome.deltaEBefore}", outcome.deltaEBefore.isFinite())
        assertTrue("corrections=${outcome.corrections.size}", outcome.corrections.size <= 3)
    }
}
