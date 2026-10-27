package com.colorstruction.mixing

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * Converts solver weights into human-readable proportions: percents (integers summing to 100)
 * or paint "parts" (multiples of ½, like `3 : 1 : ½`).
 */
object Proportions {

    /** Largest total allowed for [toParts], so that a recipe stays practical to measure. */
    private const val MAX_TOTAL_PARTS = 20.0

    /** Granularity of part values: parts are always multiples of ½. */
    private const val PART_STEP = 0.5

    /**
     * How much worse than the unrounded recipe a rounded one may become before a simpler
     * candidate is rejected; see [chooseParts].
     */
    private const val ROUNDING_SLACK = 0.5

    /** Upper bound on the candidates [recipeUi] evaluates, so rounding stays cheap. */
    const val MAX_CANDIDATES: Int = 40

    /**
     * Converts weights into percentages (integers) that sum to exactly 100.
     *
     * Uses the largest remainder (Hare–Niemeyer) method on top of a floor allocation, and
     * guarantees that every strictly positive weight receives at least 1%. Zero (or invalid)
     * weights receive 0%. If more than 100 weights are positive the minimum rule cannot be
     * honoured and the plain largest remainder result is returned.
     *
     * @param weights component weights; only ratios matter, the sum does not need to be 1.
     * @return one integer per weight, summing to 100.
     */
    fun toPercents(weights: List<Double>): List<Int> {
        if (weights.isEmpty()) return emptyList()
        val n = weights.size
        val sanitized = sanitize(weights)
        val total = sanitized.sum()
        val allZero = total <= 0.0
        // With no usable weights fall back to an even split; every slot counts as positive.
        val ideal = if (allZero) {
            List(n) { 100.0 / n }
        } else {
            sanitized.map { it / total * 100.0 }
        }
        val floors = ideal.map { floor(it + EPSILON).toInt() }
        val isPositive = { index: Int -> allZero || sanitized[index] > 0.0 }

        val result = IntArray(n) { index -> if (isPositive(index)) max(1, floors[index]) else 0 }
        var rest = 100 - result.sum()

        // Hand out the leftover units by largest fractional remainder, ties broken by index.
        val byRemainder = (0 until n).sortedWith(
            compareByDescending<Int> { index -> ideal[index] - floor(ideal[index] + EPSILON) }
                .thenBy { it },
        )
        var cursor = 0
        while (rest > 0 && byRemainder.isNotEmpty()) {
            result[byRemainder[cursor % n]]++
            cursor++
            rest--
        }

        // Too many components (or the 1% rule overflowed): take units back from the largest.
        val bySize = (0 until n).sortedWith(compareByDescending<Int> { result[it] }.thenBy { it })
        while (rest < 0) {
            var shrunk = false
            for (index in bySize) {
                if (rest == 0) break
                val lowerBound = if (isPositive(index)) 1 else 0
                if (result[index] > lowerBound) {
                    result[index]--
                    rest++
                    shrunk = true
                }
            }
            if (!shrunk) break
        }
        return result.toList()
    }

    /**
     * Converts weights into paint parts: multiples of ½, at most 20 parts in total, with the
     * smallest non-zero component expressed as 1 (or ½ when that is not accurate enough).
     *
     * Candidate totals from ½ to 20 parts are enumerated, each is rounded to ½ steps and the
     * candidate with the smallest ratio error is returned; ties prefer simpler (smaller) totals.
     *
     * Example: `[0.6, 0.2, 0.2] -> [3, 1, 1]`, `[1.0] -> [1.0]`, `[0.5, 0.5] -> [1.0, 1.0]`.
     *
     * @param weights component weights; only ratios matter.
     * @return one part value per weight; non-positive weights get 0.
     */
    fun toParts(weights: List<Double>): List<Double> {
        if (weights.isEmpty()) return emptyList()
        val sanitized = sanitize(weights)
        val total = sanitized.sum()
        if (total <= 0.0) return List(weights.size) { 0.0 }

        val proportions = sanitized.map { it / total }

        var best: Candidate? = null
        val lastTotalUnits = (MAX_TOTAL_PARTS / PART_STEP).toInt()
        for (totalUnits in 1..lastTotalUnits) {
            val candidate = evaluate(roundCandidate(proportions, totalUnits / 2.0), proportions)
            // Candidates that cannot be squeezed into MAX_TOTAL_PARTS are ignored.
            if (candidate.parts.sum() / 2.0 > MAX_TOTAL_PARTS + EPSILON) continue
            val current = best
            if (current == null || candidate.isBetterThan(current)) best = candidate
        }

        val chosen = best ?: return List(weights.size) { 0.0 }
        return chosen.parts.map { it / 2.0 }
    }

    /**
     * Lists the part vectors worth showing for [weights], simplest first.
     *
     * One candidate is generated per allowed total (from ½ up to [maxTotal]) by rounding the
     * weights to that total, together with the [toParts] result when it is missing. Every
     * candidate uses ½ steps, gives each non-zero weight at least ½ and sums to at most
     * [maxTotal]; duplicates are dropped and the list is ordered by total, so the shortest
     * recipes come first and a longer, more accurate one follows.
     *
     * @param weights component weights; only ratios matter.
     * @param maxTotal largest allowed sum of parts.
     * @return up to `2 × maxTotal` distinct part vectors; empty for empty or all-zero weights.
     */
    fun partsCandidates(weights: List<Double>, maxTotal: Int = 20): List<List<Double>> {
        if (weights.isEmpty() || maxTotal < 1) return emptyList()
        val sanitized = sanitize(weights)
        val total = sanitized.sum()
        if (total <= 0.0) return emptyList()
        val proportions = sanitized.map { it / total }
        val maxHalves = maxTotal * 2

        val candidates = LinkedHashSet<List<Double>>()
        for (totalHalves in 1..maxHalves) {
            val halves = roundToHalves(proportions, totalHalves, maxHalves)
            if (halves.isEmpty()) continue
            val parts = withWholeSmallest(halves.map { it / 2.0 })
            if (parts.sum() <= maxTotal + EPSILON) candidates.add(parts)
        }
        // Guarantee that the plain rounding of toParts is always among the candidates.
        val plain = withWholeSmallest(toParts(weights))
        if (plain.isNotEmpty() && plain.sum() <= maxTotal + EPSILON &&
            plain.all { it == 0.0 || it >= PART_STEP - EPSILON }
        ) {
            candidates.add(plain)
        }
        return candidates.sortedWith(CANDIDATE_ORDER)
    }

    /**
     * Recovers the exact parts that produced [weights] (e.g. weights saved from a displayed
     * `2½ : 1½` recipe), instead of rounding them again. Falls back to [toParts] when the weights
     * don't come from ½-step parts with a total of at most [maxTotal].
     */
    fun exactParts(weights: List<Double>, maxTotal: Int = 20): List<Double> {
        val total = weights.sum()
        if (weights.isEmpty() || total <= 0.0) return toParts(weights)
        for (halves in 1..maxTotal * 2) {
            val scaled = weights.map { it / total * halves }
            if (scaled.all { abs(it - it.roundToInt()) < 1e-6 }) {
                return withWholeSmallest(scaled.map { it.roundToInt() / 2.0 })
            }
        }
        return toParts(weights)
    }

    /** Doubles [parts] when the smallest non-zero part is ½, so `½ : ½` reads as `1 : 1`. */
    private fun withWholeSmallest(parts: List<Double>): List<Double> {
        val smallest = parts.filter { it > 0.0 }.minOrNull() ?: return parts
        return if (smallest < 1.0 - EPSILON) parts.map { it * 2.0 } else parts
    }

    /**
     * Picks the simplest candidate whose rounding does not visibly hurt the recipe.
     *
     * Candidates are expected simplest first, as [partsCandidates] returns them. The first one
     * whose ΔE is at most [unroundedDeltaE] plus [ROUNDING_SLACK] is returned; when none
     * qualifies, the candidate with the lowest ΔE wins (the first one on ties).
     *
     * @param candidates ordered part vectors, normally from [partsCandidates].
     * @param unroundedDeltaE colour difference of the recipe before rounding.
     * @param evaluate maps a part vector to the ΔE it produces.
     * @return the chosen candidate, or `null` when [candidates] is empty.
     */
    fun chooseParts(
        candidates: List<List<Double>>,
        unroundedDeltaE: Double,
        evaluate: (List<Double>) -> Double,
    ): List<Double>? {
        if (candidates.isEmpty()) return null
        var best: List<Double>? = null
        var bestDeltaE = Double.MAX_VALUE
        for (candidate in candidates) {
            val deltaE = evaluate(candidate)
            if (deltaE < bestDeltaE) {
                bestDeltaE = deltaE
                best = candidate
            }
            if (deltaE <= unroundedDeltaE + ROUNDING_SLACK) return candidate
        }
        return best
    }

    /**
     * Formats parts as a `3 : 1 : ½` string; `0.5` becomes `½` and `1.5` becomes `1½`.
     *
     * @param parts part values, normally produced by [toParts].
     */
    fun formatParts(parts: List<Double>): String = parts.joinToString(" : ") { formatPart(it) }

    /** Formats a single part value: `0.5` becomes `½`, `1.5` becomes `1½`. */
    fun formatPart(value: Double): String {
        val halves = round(value * 2.0).toInt()
        val whole = halves / 2
        val hasHalf = halves % 2 != 0
        return when {
            hasHalf && whole > 0 -> "${whole}½"
            hasHalf -> "½"
            else -> whole.toString()
        }
    }

    /**
     * Rounds [proportions] to the smallest half-part vector whose total is close to [targetTotal]
     * and normalises it so the smallest non-zero part is as close to 1 (or ½) as possible.
     */
    private fun roundCandidate(proportions: List<Double>, targetTotal: Double): IntArray {
        val halves = IntArray(proportions.size) { index ->
            val raw = proportions[index] * targetTotal / PART_STEP
            if (proportions[index] > 0.0) max(1, round(raw).toInt()) else 0
        }
        val divisor = halves.filter { it > 0 }.fold(0) { acc, value -> gcd(acc, value) }
        val reduced = if (divisor > 1) IntArray(halves.size) { halves[it] / divisor } else halves
        val smallest = reduced.filter { it > 0 }.minOrNull() ?: return halves
        // A smallest half-part of 1 reads as "½"; double it back so the base part is 1.
        if (smallest == 1 && reduced.sum() * 2 <= MAX_TOTAL_PARTS / PART_STEP) {
            return IntArray(reduced.size) { reduced[it] * 2 }
        }
        return reduced
    }

    private fun evaluate(halves: IntArray, proportions: List<Double>): Candidate {
        val totalHalves = halves.sum()
        if (totalHalves <= 0) return Candidate(halves, Double.MAX_VALUE, MINIMUM_UNIT)
        var error = 0.0
        var smallest = Int.MAX_VALUE
        for (index in halves.indices) {
            val share = halves[index].toDouble() / totalHalves
            error += abs(share - proportions[index])
            if (halves[index] > 0) smallest = minOf(smallest, halves[index])
        }
        val rank = when (smallest) {
            2 -> MINIMUM_WHOLE // one whole part is the smallest component
            1 -> MINIMUM_HALF // only ½ is the smallest component
            else -> MINIMUM_UNIT // e.g. 1½, happens for ratios like 3 : 4
        }
        return Candidate(halves, error, rank)
    }

    /**
     * Rounds [proportions] to ½ units so that they sum to about [totalHalves] halves, keeping
     * every non-zero share at ½ at least. Returns an empty array when the result cannot be
     * squeezed into [maxHalves].
     */
    private fun roundToHalves(
        proportions: List<Double>,
        totalHalves: Int,
        maxHalves: Int,
    ): IntArray {
        val halves = IntArray(proportions.size) { index ->
            if (proportions[index] > 0.0) max(1, round(proportions[index] * totalHalves).toInt()) else 0
        }
        var sum = halves.sum()
        // Rounding several small shares up can overshoot the budget: shrink the biggest part.
        while (sum > maxHalves) {
            val largest = halves.indices.filter { halves[it] > 1 }.maxByOrNull { halves[it] }
                ?: return IntArray(0)
            halves[largest]--
            sum--
        }
        return halves
    }

    /** Candidates by total, then element by element, so equally simple ones stay deterministic. */
    private val CANDIDATE_ORDER: Comparator<List<Double>> = Comparator { first, second ->
        val byTotal = first.sum().compareTo(second.sum())
        if (byTotal != 0) return@Comparator byTotal
        for (index in 0 until minOf(first.size, second.size)) {
            val byValue = first[index].compareTo(second[index])
            if (byValue != 0) return@Comparator byValue
        }
        first.size.compareTo(second.size)
    }

    private fun gcd(a: Int, b: Int): Int {
        var x = a
        var y = b
        while (y != 0) {
            val t = x % y
            x = y
            y = t
        }
        return x
    }

    private fun sanitize(weights: List<Double>): List<Double> =
        weights.map { if (it.isFinite() && it > 0.0) it else 0.0 }

    private const val EPSILON = 1e-9

    /** Rank of a candidate whose smallest component is one whole part. */
    private const val MINIMUM_WHOLE = 0

    /** Rank of a candidate whose smallest component is ½. */
    private const val MINIMUM_HALF = 1

    /** Rank of a candidate whose smallest component is something else (e.g. 1½). */
    private const val MINIMUM_UNIT = 2

    /** A rounded part vector (in ½ units) together with its ratio error. */
    private class Candidate(
        val parts: IntArray,
        val error: Double,
        val minimumRank: Int,
    ) {
        /**
         * Prefers a readable minimum component (1 before ½), then a smaller ratio error,
         * then a smaller total (a simpler recipe).
         */
        fun isBetterThan(other: Candidate): Boolean = when {
            minimumRank != other.minimumRank -> minimumRank < other.minimumRank
            error < other.error - EPSILON -> true
            abs(error - other.error) > EPSILON -> false
            else -> parts.sum() < other.parts.sum()
        }
    }
}
