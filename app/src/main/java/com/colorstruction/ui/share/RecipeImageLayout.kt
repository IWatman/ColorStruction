package com.colorstruction.ui.share

import android.graphics.Paint

// Image layout in pixels: title, one big target/result swatch, one 72 px row per component,
// a proportion bar, the palette line and the footer.
internal const val RECIPE_IMAGE_WIDTH = 1080
internal const val RECIPE_IMAGE_PADDING = 48f
internal const val RECIPE_IMAGE_TITLE_BASELINE = RECIPE_IMAGE_PADDING + 44f
internal const val RECIPE_IMAGE_SWATCH_TOP = RECIPE_IMAGE_TITLE_BASELINE + 24f
internal const val RECIPE_IMAGE_SWATCH_HEIGHT = 240f
internal const val RECIPE_IMAGE_LABEL_BASELINE = RECIPE_IMAGE_SWATCH_TOP + RECIPE_IMAGE_SWATCH_HEIGHT + 30f
internal const val RECIPE_IMAGE_FIRST_ROW_TOP = RECIPE_IMAGE_LABEL_BASELINE + 20f
internal const val RECIPE_IMAGE_ROW_HEIGHT = 72f
internal const val RECIPE_IMAGE_ROW_SWATCH = 48f
internal const val RECIPE_IMAGE_BAR_HEIGHT = 24f
internal const val RECIPE_IMAGE_GAP = 24f
internal const val RECIPE_IMAGE_TITLE_SIZE = 56f
internal const val RECIPE_IMAGE_BODY_SIZE = 42f
internal const val RECIPE_IMAGE_SMALL_SIZE = 30f
internal const val RECIPE_IMAGE_TEXT_COLOR = 0xFF1B1B1F.toInt()
internal const val RECIPE_IMAGE_MUTED_COLOR = 0xFF5A5A5A.toInt()
internal const val RECIPE_IMAGE_OUTLINE_COLOR = 0xFFBDBDBD.toInt()

/** Top edge of the proportion bar for [rows] component rows. */
internal fun recipeImageBarTop(rows: Int): Float =
    RECIPE_IMAGE_FIRST_ROW_TOP + rows * RECIPE_IMAGE_ROW_HEIGHT + 20f

/** Baseline of the palette line for [rows] component rows. */
internal fun recipeImagePaletteBaseline(rows: Int): Float =
    recipeImageBarTop(rows) + RECIPE_IMAGE_BAR_HEIGHT + RECIPE_IMAGE_GAP + RECIPE_IMAGE_SMALL_SIZE

/** Total image height for [componentCount] component rows. */
internal fun recipeImageHeight(componentCount: Int): Int =
    (recipeImagePaletteBaseline(componentCount) + RECIPE_IMAGE_GAP + RECIPE_IMAGE_SMALL_SIZE + RECIPE_IMAGE_PADDING).toInt()

/** Cuts [value] down to [maxWidth] with an ellipsis; empty when there is no room at all. */
internal fun ellipsize(value: String, paint: Paint, maxWidth: Float): String {
    if (maxWidth <= 0f) return ""
    if (paint.measureText(value) <= maxWidth) return value
    var end = value.length
    while (end > 0 && paint.measureText(value.substring(0, end) + "…") > maxWidth) end--
    return value.substring(0, end) + "…"
}
