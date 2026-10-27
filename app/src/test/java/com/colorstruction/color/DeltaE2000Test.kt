package com.colorstruction.color

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Reference pairs and expected values from Sharma, Wu, Dalal (2005),
 * "The CIEDE2000 Color-Difference Formula: Implementation Notes, Supplementary Test Data,
 * and Mathematical Observations".
 */
class DeltaE2000Test {

    private data class ReferencePair(
        val expected: Double,
        val lab1: Lab,
        val lab2: Lab,
    )

    private val referencePairs = listOf(
        ReferencePair(2.0425, Lab(50.0000, 2.6772, -79.7751), Lab(50.0000, 0.0000, -82.7485)),
        ReferencePair(2.8615, Lab(50.0000, 3.1571, -77.2803), Lab(50.0000, 0.0000, -82.7485)),
        ReferencePair(3.4412, Lab(50.0000, 2.8361, -74.0200), Lab(50.0000, 0.0000, -82.7485)),
        ReferencePair(1.0000, Lab(50.0000, -1.3802, -84.2814), Lab(50.0000, 0.0000, -82.7485)),
        ReferencePair(1.0000, Lab(50.0000, -1.1848, -84.8006), Lab(50.0000, 0.0000, -82.7485)),
        ReferencePair(1.0000, Lab(50.0000, -0.9009, -85.5211), Lab(50.0000, 0.0000, -82.7485)),
        ReferencePair(2.3669, Lab(50.0000, 0.0000, 0.0000), Lab(50.0000, -1.0000, 2.0000)),
        ReferencePair(2.3669, Lab(50.0000, -1.0000, 2.0000), Lab(50.0000, 0.0000, 0.0000)),
        ReferencePair(7.1792, Lab(50.0000, 2.4900, -0.0010), Lab(50.0000, -2.4900, 0.0009)),
        ReferencePair(7.1792, Lab(50.0000, 2.4900, -0.0010), Lab(50.0000, -2.4900, 0.0010)),
        ReferencePair(7.2195, Lab(50.0000, 2.4900, -0.0010), Lab(50.0000, -2.4900, 0.0011)),
        ReferencePair(7.2195, Lab(50.0000, 2.4900, -0.0010), Lab(50.0000, -2.4900, 0.0012)),
        ReferencePair(4.8045, Lab(50.0000, -0.0010, 2.4900), Lab(50.0000, 0.0009, -2.4900)),
        ReferencePair(4.8045, Lab(50.0000, -0.0010, 2.4900), Lab(50.0000, 0.0010, -2.4900)),
        ReferencePair(4.7461, Lab(50.0000, -0.0010, 2.4900), Lab(50.0000, 0.0011, -2.4900)),
        ReferencePair(4.3065, Lab(50.0000, 2.5000, 0.0000), Lab(50.0000, 0.0000, -2.5000)),
        ReferencePair(27.1492, Lab(50.0000, 2.5000, 0.0000), Lab(73.0000, 25.0000, -18.0000)),
        ReferencePair(22.8977, Lab(50.0000, 2.5000, 0.0000), Lab(61.0000, -5.0000, 29.0000)),
        ReferencePair(31.9030, Lab(50.0000, 2.5000, 0.0000), Lab(56.0000, -27.0000, -3.0000)),
        ReferencePair(19.4535, Lab(50.0000, 2.5000, 0.0000), Lab(58.0000, 24.0000, 15.0000)),
        ReferencePair(1.0000, Lab(50.0000, 2.5000, 0.0000), Lab(50.0000, 3.1736, 0.5854)),
        ReferencePair(1.0000, Lab(50.0000, 2.5000, 0.0000), Lab(50.0000, 3.2972, 0.0000)),
        ReferencePair(1.0000, Lab(50.0000, 2.5000, 0.0000), Lab(50.0000, 1.8634, 0.5757)),
        ReferencePair(1.0000, Lab(50.0000, 2.5000, 0.0000), Lab(50.0000, 3.2592, 0.3350)),
        ReferencePair(1.2644, Lab(60.2574, -34.0099, 36.2677), Lab(60.4626, -34.1751, 39.4387)),
        ReferencePair(1.2630, Lab(63.0109, -31.0961, -5.8663), Lab(62.8187, -29.7946, -4.0864)),
        ReferencePair(1.8731, Lab(61.2901, 3.7196, -5.3901), Lab(61.4292, 2.2480, -4.9620)),
        ReferencePair(1.8645, Lab(35.0831, -44.1164, 3.7933), Lab(35.0232, -40.0716, 1.5901)),
        ReferencePair(2.0373, Lab(22.7233, 20.0904, -46.6940), Lab(23.0331, 14.9730, -42.5619)),
        ReferencePair(1.4146, Lab(36.4612, 47.8580, 18.3852), Lab(36.2715, 50.5065, 21.2231)),
        ReferencePair(1.4441, Lab(90.8027, -2.0831, 1.4410), Lab(91.1528, -1.6435, 0.0447)),
        ReferencePair(1.5381, Lab(90.9257, -0.5406, -0.9208), Lab(88.6381, -0.8985, -0.7239)),
        ReferencePair(0.6377, Lab(6.7747, -0.2908, -2.4247), Lab(5.8714, -0.0985, -2.2286)),
        ReferencePair(0.9082, Lab(2.0776, 0.0795, -1.1350), Lab(0.9033, -0.0636, -0.5514)),
    )

    @Test
    fun matchesSharmaReferencePairs() {
        assertEquals(34, referencePairs.size)

        referencePairs.forEachIndexed { index, pair ->
            val actual = DeltaE.ciede2000(pair.lab1, pair.lab2)
            assertEquals(
                "pair #${index + 1}: ${pair.lab1} vs ${pair.lab2}",
                pair.expected,
                actual,
                1e-4,
            )
        }
    }

    @Test
    fun isSymmetric() {
        for (pair in referencePairs.take(5)) {
            val forward = DeltaE.ciede2000(pair.lab1, pair.lab2)
            val reverse = DeltaE.ciede2000(pair.lab2, pair.lab1)
            assertEquals(forward, reverse, 1e-9)
        }
    }

    @Test
    fun identicalColorsAreZero() {
        assertEquals(0.0, DeltaE.ciede2000(Lab(50.0, 20.0, -30.0), Lab(50.0, 20.0, -30.0)), 1e-12)
        assertEquals(0.0, DeltaE.ciede2000(Lab(0.0, 0.0, 0.0), Lab(0.0, 0.0, 0.0)), 1e-12)
    }
}
