package com.colorstruction.mixing

import com.colorstruction.color.DeltaE
import com.colorstruction.color.Lab

/**
 * One evaluated paint combination: which paints make it up, the optimized weights and the
 * resulting color difference.
 *
 * Weights are ordered like [combo] — the paint weights follow the paint indices and the
 * optional water weight (watercolor palettes only) comes last.
 */
internal class Candidate(
    val combo: IntArray,
    val weights: DoubleArray,
    val deltaE: Double,
)

/** Components with a smaller weight are dropped from a recipe. */
private const val MIN_WEIGHT = 0.01

/**
 * Enumerates every combination of [size] indices taken from `0 until n`, passing them to
 * [action] in ascending order.
 *
 * [action] receives a scratch array that is reused between calls — it must copy it when the
 * combination has to outlive the call. Returning `false` from [action] stops the enumeration.
 */
internal fun forEachCombination(n: Int, size: Int, action: (IntArray) -> Boolean) {
    if (size <= 0 || size > n) return
    val combo = IntArray(size)
    var stopped = false

    fun recurse(depth: Int, start: Int) {
        if (stopped) return
        if (depth == size) {
            if (!action(combo)) stopped = true
            return
        }
        // Keep enough indices for the remaining slots.
        for (index in start..n - (size - depth)) {
            combo[depth] = index
            recurse(depth + 1, index + 1)
            if (stopped) return
        }
    }

    recurse(0, 0)
}

/** Ids of the paint components of a recipe, ascending; the water component is not included. */
internal fun Recipe.paintIds(): List<Long> =
    components.mapNotNull { it.paint?.id }.sorted()

/**
 * Ranking used for the recipe list, best first.
 *
 * The primary key is `ΔE + paintCount`, which expresses the "fewer paints is better" rule
 * exactly: a recipe with fewer paints always comes first when its ΔE is at most 1.0 worse, and
 * the key stays a consistent total order (so sorting cannot misbehave).
 * Ties are broken by ΔE and then by the number of components.
 */
internal val RECIPE_ORDER: Comparator<Recipe> = Comparator { first, second ->
    val firstKey = first.deltaE + first.paintCount
    val secondKey = second.deltaE + second.paintCount
    when {
        firstKey < secondKey -> -1
        firstKey > secondKey -> 1
        first.deltaE < second.deltaE -> -1
        first.deltaE > second.deltaE -> 1
        else -> first.components.size.compareTo(second.components.size)
    }
}

/**
 * Turns optimized [candidates] into the recipe list the solver returns.
 *
 * Components below [MIN_WEIGHT] are dropped (then the rest is renormalized and the color and ΔE
 * are recomputed), the best candidate per paint set is kept, and the list is ranked with
 * [RECIPE_ORDER]. A recipe whose paints are a strict superset of an already returned recipe is
 * skipped, so the result stays diverse.
 */
internal fun rankRecipes(
    candidates: List<Candidate>,
    palette: PaletteSpectra,
    targetLab: Lab,
    resultCount: Int,
): List<Recipe> {
    val bestPerPaintSet = LinkedHashMap<List<Long>, Recipe>()
    for (candidate in candidates) {
        val recipe = buildRecipe(candidate, palette, targetLab) ?: continue
        val key = recipe.paintIds()
        val existing = bestPerPaintSet[key]
        if (existing == null || recipe.deltaE < existing.deltaE) bestPerPaintSet[key] = recipe
    }

    val ranked = bestPerPaintSet.values.sortedWith(RECIPE_ORDER)
    val result = ArrayList<Recipe>(resultCount)
    val returnedPaintSets = ArrayList<List<Long>>()
    for (recipe in ranked) {
        if (result.size >= resultCount) break
        val ids = recipe.paintIds()
        if (returnedPaintSets.any { it.size < ids.size && ids.containsAll(it) }) continue
        result.add(recipe)
        returnedPaintSets.add(ids)
    }
    return result
}

/**
 * Builds a recipe from optimized weights, dropping components below [MIN_WEIGHT], renormalizing
 * and recomputing the result color and ΔE. Returns `null` when nothing but water is left.
 */
private fun buildRecipe(
    candidate: Candidate,
    palette: PaletteSpectra,
    targetLab: Lab,
): Recipe? {
    val combo = candidate.combo
    val weights = candidate.weights
    val keptPaints = ArrayList<Int>(combo.size)
    val keptWeights = ArrayList<Double>(combo.size + 1)
    for (position in combo.indices) {
        if (weights[position] >= MIN_WEIGHT) {
            keptPaints.add(combo[position])
            keptWeights.add(weights[position])
        }
    }
    val waterPosition = if (palette.hasWater) combo.size else -1
    val waterWeight = if (waterPosition >= 0 && weights[waterPosition] >= MIN_WEIGHT) {
        weights[waterPosition]
    } else {
        0.0
    }
    // Water alone (no paint at all) is not a recipe.
    if (keptPaints.isEmpty()) return null

    val total = keptWeights.sum() + waterWeight
    val hasWater = waterWeight > 0.0
    val normalized = DoubleArray(keptPaints.size + if (hasWater) 1 else 0) { position ->
        if (position < keptPaints.size) keptWeights[position] / total else waterWeight / total
    }
    val mix = palette.paintKernel(keptPaints, hasWater).mix(normalized)

    val paintComponents = keptPaints.indices
        .map {
            RecipeComponent(
                paint = palette.paints[keptPaints[it]],
                isWater = false,
                weight = normalized[it],
            )
        }
        .sortedByDescending { it.weight }
    val components = if (hasWater) {
        // Water always goes last, whatever its share.
        paintComponents + RecipeComponent(null, isWater = true, weight = normalized.last())
    } else {
        paintComponents
    }
    return Recipe(
        components = components,
        result = mix.rgb,
        deltaE = DeltaE.ciede2000(mix.lab, targetLab),
    )
}
