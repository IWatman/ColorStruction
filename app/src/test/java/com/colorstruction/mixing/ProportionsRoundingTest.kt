package com.colorstruction.mixing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/** Tests for the rounding candidates of [Proportions] and the `chooseParts` selection rule. */
class ProportionsRoundingTest {

    private companion object {
        const val DELTA = 1e-9
        const val TOLERATED_EXTRA_DELTA_E = 0.5
    }

    @Test
    fun candidatesRespectShapeAndConstraints() {
        val random = Random(1234L)
        repeat(200) {
            val size = 1 + random.nextInt(5)
            val weights = List(size) { 0.05 + random.nextDouble() * 5.0 }
            val candidates = Proportions.partsCandidates(weights)
            val label = "$weights -> $candidates"

            assertTrue(label, candidates.isNotEmpty())
            assertEquals(label, candidates.size, candidates.distinct().size)
            candidates.forEach { candidate ->
                assertEquals(label, size, candidate.size)
                candidate.forEach { part ->
                    assertEquals(label, 0.0, (part * 2.0) - Math.round(part * 2.0), DELTA)
                    assertTrue(label, part >= 0.0)
                    assertTrue(label, part == 0.0 || part >= 0.5 - DELTA)
                }
                assertTrue(label, candidate.sum() <= 20.0 + DELTA)
            }
        }
    }

    @Test
    fun candidatesAreOrderedSimplestFirst() {
        val candidates = Proportions.partsCandidates(listOf(0.6, 0.25, 0.15))
        val totals = candidates.map { it.sum() }
        assertEquals(totals.sorted(), totals)
        // The simplest candidate is 1 : 1 : 1 (½ : ½ : ½ is doubled to whole parts).
        assertEquals(3.0, candidates.first().sum(), DELTA)
        assertTrue(candidates.all { parts -> parts.filter { it > 0.0 }.min() >= 1.0 })
    }

    @Test
    fun candidatesContainTheToPartsResult() {
        val random = Random(77L)
        repeat(200) {
            val size = 1 + random.nextInt(4)
            val weights = List(size) { 0.1 + random.nextDouble() * 3.0 }
            val candidates = Proportions.partsCandidates(weights)
            val plain = Proportions.toParts(weights)
            assertTrue("$weights -> $candidates must contain $plain", candidates.contains(plain))
        }
        assertTrue(
            Proportions.partsCandidates(listOf(0.6, 0.2, 0.2)).contains(listOf(3.0, 1.0, 1.0)),
        )
    }

    @Test
    fun candidatesHonourCustomMaxTotal() {
        val candidates = Proportions.partsCandidates(listOf(0.7, 0.2, 0.1), maxTotal = 3)
        assertTrue(candidates.isNotEmpty())
        assertTrue(candidates.all { it.sum() <= 3.0 + DELTA })
    }

    @Test
    fun candidatesAreEmptyWithoutUsableWeights() {
        assertEquals(emptyList<List<Double>>(), Proportions.partsCandidates(emptyList()))
        assertEquals(emptyList<List<Double>>(), Proportions.partsCandidates(listOf(0.0, 0.0)))
        assertEquals(emptyList<List<Double>>(), Proportions.partsCandidates(listOf(1.0), maxTotal = 0))
    }

    @Test
    fun choosePartsTakesTheSimplestAcceptableCandidate() {
        val candidates = listOf(listOf(1.0), listOf(2.0), listOf(3.0))
        val deltaEByTotal = mapOf(1.0 to 1.2, 2.0 to 1.1, 3.0 to 1.0)

        val chosen = Proportions.chooseParts(candidates, unroundedDeltaE = 1.0) { parts ->
            deltaEByTotal.getValue(parts.sum())
        }

        assertEquals(listOf(1.0), chosen)
    }

    @Test
    fun choosePartsSkipsCandidatesThatCostTooMuchAccuracy() {
        val candidates = listOf(listOf(1.0), listOf(2.0), listOf(3.0))
        val deltaEByTotal = mapOf(1.0 to 3.5, 2.0 to 1.0 + TOLERATED_EXTRA_DELTA_E + 0.1, 3.0 to 1.2)

        val chosen = Proportions.chooseParts(candidates, unroundedDeltaE = 1.0) { parts ->
            deltaEByTotal.getValue(parts.sum())
        }

        assertEquals(listOf(3.0), chosen)
    }

    @Test
    fun choosePartsAcceptsTheExactSlackBoundary() {
        val candidates = listOf(listOf(1.0), listOf(2.0))
        val chosen = Proportions.chooseParts(candidates, unroundedDeltaE = 1.0) { parts ->
            if (parts.sum() == 1.0) 1.0 + TOLERATED_EXTRA_DELTA_E else 1.0
        }
        assertEquals(listOf(1.0), chosen)
    }

    @Test
    fun choosePartsFallsBackToTheMostAccurateCandidate() {
        val candidates = listOf(listOf(1.0), listOf(2.0), listOf(3.0))
        val deltaEByTotal = mapOf(1.0 to 4.0, 2.0 to 3.0, 3.0 to 3.5)

        val chosen = Proportions.chooseParts(candidates, unroundedDeltaE = 1.0) { parts ->
            deltaEByTotal.getValue(parts.sum())
        }

        assertEquals(listOf(2.0), chosen)
    }

    @Test
    fun choosePartsReturnsNullForNoCandidates() {
        assertNull(Proportions.chooseParts(emptyList(), 1.0) { 0.0 })
    }
}
