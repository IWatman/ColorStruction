package com.colorstruction.mixing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for [Amounts]: step choice, splitting a total and formatting. */
class AmountsTest {

    @Test
    fun stepDependsOnTotalSize() {
        assertEquals(0.5, Amounts.amountStep(50.0), 1e-9)
        assertEquals(0.5, Amounts.amountStep(100.0), 1e-9)
        assertEquals(1.0, Amounts.amountStep(101.0), 1e-9)
        assertEquals(1.0, Amounts.amountStep(1000.0), 1e-9)
        assertEquals(5.0, Amounts.amountStep(1001.0), 1e-9)
        assertEquals(5.0, Amounts.amountStep(5000.0), 1e-9)
    }

    @Test
    fun amountsSumToTheTotal() {
        val total = 50.0
        val result = Amounts.amounts(listOf(3.0, 1.0), total, Amounts.amountStep(total))

        assertEquals(listOf(37.5, 12.5), result)
        assertEquals(total, result.sum(), 1e-9)
    }

    @Test
    fun amountsSplitThreeWaysToTheTotal() {
        val total = 50.0
        val result = Amounts.amounts(listOf(1.0, 1.0, 1.0), total, Amounts.amountStep(total))

        assertEquals(total, result.sum(), 1e-9)
        assertTrue(result.all { it >= 0.5 })
    }

    @Test
    fun everyNonZeroWeightGetsAtLeastOneStep() {
        val total = 50.0
        val weights = listOf(0.98, 0.019, 0.001, 0.0)
        val result = Amounts.amounts(weights, total, Amounts.amountStep(total))

        assertEquals(0.0, result[3], 1e-9)
        assertEquals(0.5, result[1], 1e-9)
        assertEquals(0.5, result[2], 1e-9)
        assertEquals(total, result.sum(), 1e-9)
    }

    @Test
    fun coarseTotalUsesFiveMillilitreSteps() {
        val total = 2000.0
        val step = Amounts.amountStep(total)
        val result = Amounts.amounts(listOf(2.0, 1.0), total, step)

        assertEquals(5.0, step, 1e-9)
        assertEquals(total, result.sum(), 1e-9)
        assertTrue(result.all { it > 0.0 && it % 5.0 == 0.0 })
    }

    @Test
    fun evenSplitWhenAllWeightsAreZero() {
        val result = Amounts.amounts(listOf(0.0, 0.0), 50.0, 0.5)

        assertEquals(50.0, result.sum(), 1e-9)
    }

    @Test
    fun emptyWeightsOrEmptyTotalGiveNothing() {
        assertTrue(Amounts.amounts(emptyList(), 50.0, 0.5).isEmpty())
        assertEquals(listOf(0.0, 0.0), Amounts.amounts(listOf(1.0, 1.0), 0.0, 0.5))
    }

    @Test
    fun formatAmountDropsTrailingZero() {
        assertEquals("12.5", Amounts.formatAmount(12.5))
        assertEquals("3", Amounts.formatAmount(3.0))
        assertEquals("3", Amounts.formatAmount(3.0000001))
        assertEquals("0.5", Amounts.formatAmount(0.5))
        assertEquals("100", Amounts.formatAmount(100.0))
    }
}
