package com.colorstruction.color

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * CIEDE2000 color difference (Sharma, Wu, Dalal 2005), with kL = kC = kH = 1.
 */
object DeltaE {

    /**
     * Computes the CIEDE2000 color difference between two CIELAB colors.
     *
     * The result is symmetric and uses the reference conditions (kL = kC = kH = 1).
     */
    fun ciede2000(lab1: Lab, lab2: Lab): Double {
        val l1 = lab1.l
        val a1 = lab1.a
        val b1 = lab1.b
        val l2 = lab2.l
        val a2 = lab2.a
        val b2 = lab2.b

        val c1 = sqrt(a1 * a1 + b1 * b1)
        val c2 = sqrt(a2 * a2 + b2 * b2)
        val cBar = (c1 + c2) / 2.0

        val cBarPow7 = cBar.pow(7)
        val g = 0.5 * (1.0 - sqrt(cBarPow7 / (cBarPow7 + 25.0.pow(7))))

        val a1Prime = (1.0 + g) * a1
        val a2Prime = (1.0 + g) * a2

        val c1Prime = sqrt(a1Prime * a1Prime + b1 * b1)
        val c2Prime = sqrt(a2Prime * a2Prime + b2 * b2)

        val h1Prime = hueDegrees(a1Prime, b1)
        val h2Prime = hueDegrees(a2Prime, b2)

        val dLPrime = l2 - l1
        val dCPrime = c2Prime - c1Prime

        val dhPrime = when {
            c1Prime * c2Prime == 0.0 -> 0.0
            abs(h2Prime - h1Prime) <= 180.0 -> h2Prime - h1Prime
            h2Prime - h1Prime > 180.0 -> h2Prime - h1Prime - 360.0
            else -> h2Prime - h1Prime + 360.0
        }
        val dHPrime = 2.0 * sqrt(c1Prime * c2Prime) * sin(Math.toRadians(dhPrime / 2.0))

        val lBarPrime = (l1 + l2) / 2.0
        val cBarPrime = (c1Prime + c2Prime) / 2.0

        val hBarPrime = when {
            c1Prime * c2Prime == 0.0 -> h1Prime + h2Prime
            abs(h1Prime - h2Prime) <= 180.0 -> (h1Prime + h2Prime) / 2.0
            h1Prime + h2Prime < 360.0 -> (h1Prime + h2Prime + 360.0) / 2.0
            else -> (h1Prime + h2Prime - 360.0) / 2.0
        }

        val t = 1.0 -
            0.17 * cos(Math.toRadians(hBarPrime - 30.0)) +
            0.24 * cos(Math.toRadians(2.0 * hBarPrime)) +
            0.32 * cos(Math.toRadians(3.0 * hBarPrime + 6.0)) -
            0.20 * cos(Math.toRadians(4.0 * hBarPrime - 63.0))

        val dTheta = 30.0 * exp(-((hBarPrime - 275.0) / 25.0).pow(2))

        val cBarPrimePow7 = cBarPrime.pow(7)
        val rc = 2.0 * sqrt(cBarPrimePow7 / (cBarPrimePow7 + 25.0.pow(7)))

        val sl = 1.0 + 0.015 * (lBarPrime - 50.0).pow(2) / sqrt(20.0 + (lBarPrime - 50.0).pow(2))
        val sc = 1.0 + 0.045 * cBarPrime
        val sh = 1.0 + 0.015 * cBarPrime * t

        val rt = -sin(Math.toRadians(2.0 * dTheta)) * rc

        val termL = dLPrime / sl
        val termC = dCPrime / sc
        val termH = dHPrime / sh

        return sqrt(
            termL * termL + termC * termC + termH * termH + rt * termC * termH,
        )
    }

    private fun hueDegrees(a: Double, b: Double): Double {
        if (a == 0.0 && b == 0.0) return 0.0
        val degrees = Math.toDegrees(atan2(b, a))
        return if (degrees < 0.0) degrees + 360.0 else degrees
    }
}
