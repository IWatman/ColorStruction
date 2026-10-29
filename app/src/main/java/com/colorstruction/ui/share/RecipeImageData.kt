package com.colorstruction.ui.share

import com.colorstruction.color.Rgb

/**
 * Everything printed on a shareable recipe image.
 *
 * @property components name, swatch color and preformatted share label of each component.
 * @property shares numeric shares that size the proportion bar segments.
 */
data class RecipeImageData(
    val target: Rgb,
    val result: Rgb?,
    val components: List<Triple<String, Rgb, String>>,
    val deltaE: Double?,
    val paletteName: String,
    val shares: List<Double> = emptyList(),
)

/**
 * Localized captions of a shareable recipe image; all of them are ready to draw.
 *
 * @property deltaE preformatted accuracy line such as `ΔE 0.7`, or `null` to omit it.
 * @property fromPalette already formatted palette line, empty to omit it.
 */
data class RecipeImageTexts(
    val title: String,
    val target: String,
    val result: String,
    val deltaE: String?,
    val fromPalette: String,
    val footer: String,
)
