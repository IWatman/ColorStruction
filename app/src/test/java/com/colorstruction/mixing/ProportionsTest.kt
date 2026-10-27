package com.colorstruction.mixing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class ProportionsTest {

    private companion object {
        const val DELTA = 1e-9
    }

    @Test
    fun percentsSumToHundredForSingleWeight() {
        assertEquals(listOf(100), Proportions.toPercents(listOf(1.0)))
        assertEquals(listOf(100), Proportions.toPercents(listOf(42.0)))
    }

    @Test
    fun percentsSplitEvenlyWhenAllWeightsAreZero() {
        val percents = Proportions.toPercents(listOf(0.0, 0.0, 0.0, 0.0))
        assertEquals(100, percents.sum())
        assertEquals(listOf(25, 25, 25, 25), percents)
    }

    @Test
    fun percentsKeepZeroWeightsAtZero() {
        val percents = Proportions.toPercents(listOf(1.0, 0.0, 3.0))
        assertEquals(100, percents.sum())
        assertEquals(0, percents[1])
        assertEquals(75, percents[2])
    }

    @Test
    fun percentsGiveEveryPositiveWeightAtLeastOne() {
        val weights = listOf(0.9999, 0.00005, 0.00004, 0.00001)
        val percents = Proportions.toPercents(weights)
        assertEquals(100, percents.sum())
        assertTrue(percents.toString(), percents.all { it >= 1 })
        assertTrue(percents.toString(), percents[0] >= 96)
    }

    @Test
    fun percentsBreakTiesTowardsEarlierComponents() {
        assertEquals(listOf(34, 33, 33), Proportions.toPercents(listOf(1.0, 1.0, 1.0)))
    }

    @Test
    fun percentsAreStableForManyRandomWeightVectors() {
        val random = Random(20240521L)
        repeat(500) {
            val size = 1 + random.nextInt(8)
            val weights = List(size) { random.nextDouble() * 100.0 }
            val percents = Proportions.toPercents(weights)

            assertEquals("size for $weights", size, percents.size)
            assertEquals("sum for $weights -> $percents", 100, percents.sum())
            assertTrue("non-negative for $weights", percents.all { it >= 0 })
            weights.forEachIndexed { index, weight ->
                if (weight > 0.0) {
                    assertTrue("minimum for $weights -> $percents", percents[index] >= 1)
                } else {
                    assertEquals("zero for $weights -> $percents", 0, percents[index])
                }
            }
        }
    }

    @Test
    fun percentsSumToHundredWithZeroesAndDuplicates() {
        val random = Random(7L)
        repeat(200) {
            val size = 1 + random.nextInt(10)
            val weights = List(size) { if (random.nextInt(4) == 0) 0.0 else random.nextDouble() }
            val percents = Proportions.toPercents(weights)
            val allZero = weights.all { it == 0.0 }
            assertEquals("sum for $weights -> $percents", 100, percents.sum())
            weights.forEachIndexed { index, weight ->
                when {
                    weight > 0.0 -> assertTrue("minimum for $weights -> $percents", percents[index] >= 1)
                    // With no usable weight at all the percentages are split evenly instead.
                    allZero -> assertTrue("even split for $weights -> $percents", percents[index] >= 1)
                    else -> assertEquals("zero for $weights -> $percents", 0, percents[index])
                }
            }
        }
    }

    @Test
    fun percentsHandleEmptyInput() {
        assertEquals(emptyList<Int>(), Proportions.toPercents(emptyList()))
    }

    @Test
    fun toPartsMatchesSpecifiedExamples() {
        assertEquals(listOf(3.0, 1.0, 1.0), Proportions.toParts(listOf(0.6, 0.2, 0.2)))
        assertEquals(listOf(3.0, 2.0, 1.0), Proportions.toParts(listOf(0.5, 0.333, 0.167)))
        assertEquals(listOf(1.0), Proportions.toParts(listOf(1.0)))
        assertEquals(listOf(1.0, 1.0), Proportions.toParts(listOf(0.5, 0.5)))
    }

    @Test
    fun toPartsIgnoresAbsoluteScaleOfWeights() {
        assertEquals(
            Proportions.toParts(listOf(0.6, 0.2, 0.2)),
            Proportions.toParts(listOf(60.0, 20.0, 20.0)),
        )
    }

    @Test
    fun toPartsHandlesEmptyAndZeroWeights() {
        assertEquals(emptyList<Double>(), Proportions.toParts(emptyList()))
        assertEquals(listOf(0.0, 0.0, 0.0), Proportions.toParts(listOf(0.0, 0.0, 0.0)))
        val parts = Proportions.toParts(listOf(1.0, 0.0))
        assertEquals(0.0, parts[1], DELTA)
        assertEquals(1.0, parts[0], DELTA)
    }

    @Test
    fun toPartsKeepsHalfStepsSmallTotalAndUnitMinimum() {
        val random = Random(99L)
        repeat(500) {
            val size = 1 + random.nextInt(5)
            val weights = List(size) { 0.05 + random.nextDouble() * 10.0 }
            val parts = Proportions.toParts(weights)
            val label = "$weights -> $parts"

            assertEquals(label, size, parts.size)
            parts.forEach { part ->
                assertEquals(label, 0.0, (part * 2.0) - Math.round(part * 2.0).toDouble(), 1e-9)
                assertTrue(label, part >= 0.0)
            }
            assertTrue(label, parts.sum() <= 20.0 + DELTA)
            val smallest = parts.filter { it > 0.0 }.min()
            assertTrue(label, smallest == 0.5 || smallest == 1.0)
        }
    }

    @Test
    fun toPartsKeepsProportionsWithinReason() {
        val parts = Proportions.toParts(listOf(0.5, 0.25, 0.25))
        assertEquals(listOf(2.0, 1.0, 1.0), parts)
        val shares = parts.map { it / parts.sum() }
        assertEquals(0.5, shares[0], 1e-9)
        assertEquals(0.25, shares[1], 1e-9)
    }

    @Test
    fun formatPartsProducesFractionGlyphs() {
        assertEquals("3 : 1 : ½", Proportions.formatParts(listOf(3.0, 1.0, 0.5)))
        assertEquals("1½", Proportions.formatParts(listOf(1.5)))
        assertEquals("1", Proportions.formatParts(listOf(1.0)))
        assertEquals("2½ : ½", Proportions.formatParts(listOf(2.5, 0.5)))
        assertEquals("", Proportions.formatParts(emptyList()))
    }
}
