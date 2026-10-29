package com.colorstruction.ui.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

/** Renders [data] with [texts] onto a fresh 1080 px wide bitmap using a plain Canvas. */
fun renderRecipeImage(data: RecipeImageData, texts: RecipeImageTexts): Bitmap {
    val rows = data.components.size
    val bitmap = Bitmap.createBitmap(RECIPE_IMAGE_WIDTH, recipeImageHeight(rows), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)

    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = RECIPE_IMAGE_OUTLINE_COLOR
    }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textSize = RECIPE_IMAGE_BODY_SIZE
        color = RECIPE_IMAGE_TEXT_COLOR
    }
    val small = Paint(text).apply { textSize = RECIPE_IMAGE_SMALL_SIZE; color = RECIPE_IMAGE_MUTED_COLOR }
    val title = Paint(text).apply {
        textSize = RECIPE_IMAGE_TITLE_SIZE
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
    canvas.drawText(texts.title, RECIPE_IMAGE_PADDING, RECIPE_IMAGE_TITLE_BASELINE, title)

    val contentWidth = RECIPE_IMAGE_WIDTH - 2 * RECIPE_IMAGE_PADDING
    val right = RECIPE_IMAGE_PADDING + contentWidth
    val bottom = RECIPE_IMAGE_SWATCH_TOP + RECIPE_IMAGE_SWATCH_HEIGHT
    val middle = RECIPE_IMAGE_PADDING + contentWidth / 2f
    val result = data.result
    val targetWidth = if (result == null) right else middle
    fill.color = data.target.toArgbInt()
    canvas.drawRect(RectF(RECIPE_IMAGE_PADDING, RECIPE_IMAGE_SWATCH_TOP, targetWidth, bottom), fill)
    if (result != null) {
        fill.color = result.toArgbInt()
        canvas.drawRect(RectF(middle, RECIPE_IMAGE_SWATCH_TOP, right, bottom), fill)
    }
    canvas.drawRect(RectF(RECIPE_IMAGE_PADDING, RECIPE_IMAGE_SWATCH_TOP, right, bottom), stroke)

    canvas.drawText(texts.target, RECIPE_IMAGE_PADDING, RECIPE_IMAGE_LABEL_BASELINE, small)
    if (result != null) canvas.drawText(texts.result, middle, RECIPE_IMAGE_LABEL_BASELINE, small)
    texts.deltaE?.let { line ->
        small.textAlign = Paint.Align.RIGHT
        canvas.drawText(line, right, RECIPE_IMAGE_LABEL_BASELINE, small)
        small.textAlign = Paint.Align.LEFT
    }

    data.components.forEachIndexed { index, (name, color, label) ->
        val top = RECIPE_IMAGE_FIRST_ROW_TOP + index * RECIPE_IMAGE_ROW_HEIGHT
        val swatch = RectF(
            RECIPE_IMAGE_PADDING,
            top + (RECIPE_IMAGE_ROW_HEIGHT - RECIPE_IMAGE_ROW_SWATCH) / 2f,
            RECIPE_IMAGE_PADDING + RECIPE_IMAGE_ROW_SWATCH,
            top + (RECIPE_IMAGE_ROW_HEIGHT + RECIPE_IMAGE_ROW_SWATCH) / 2f,
        )
        fill.color = color.toArgbInt()
        canvas.drawRoundRect(swatch, 8f, 8f, fill)
        canvas.drawRoundRect(swatch, 8f, 8f, stroke)
        val baseline = top + RECIPE_IMAGE_ROW_HEIGHT / 2f - (text.ascent() + text.descent()) / 2f
        val labelLeft = right - text.measureText(label)
        val nameLeft = swatch.right + 16f
        canvas.drawText(ellipsize(name, text, labelLeft - 16f - nameLeft), nameLeft, baseline, text)
        canvas.drawText(label, labelLeft, baseline, text)
    }

    drawProportionBar(canvas, fill, data, contentWidth)
    if (texts.fromPalette.isNotEmpty()) {
        val line = ellipsize(texts.fromPalette, small, contentWidth)
        canvas.drawText(line, RECIPE_IMAGE_PADDING, recipeImagePaletteBaseline(rows), small)
    }
    val footerBaseline = recipeImagePaletteBaseline(rows) + RECIPE_IMAGE_GAP + RECIPE_IMAGE_SMALL_SIZE
    canvas.drawText(texts.footer, RECIPE_IMAGE_PADDING, footerBaseline, small)
    return bitmap
}

/** Stacked bar whose segment widths follow the numeric [RecipeImageData.shares]. */
private fun drawProportionBar(canvas: Canvas, fill: Paint, data: RecipeImageData, contentWidth: Float) {
    val fallback = List(data.components.size) { 1.0 }
    val shares = data.shares.take(data.components.size).let { if (it.sum() > 0.0) it else fallback }
    val total = shares.sum().let { if (it > 0.0) it else 1.0 }
    val top = recipeImageBarTop(data.components.size)
    var left = RECIPE_IMAGE_PADDING
    data.components.forEachIndexed { index, (_, color, _) ->
        val share = (shares.getOrElse(index) { 0.0 } / total).toFloat().coerceAtLeast(0.002f)
        val right = if (index == data.components.lastIndex) RECIPE_IMAGE_PADDING + contentWidth else left + contentWidth * share
        fill.color = color.toArgbInt()
        canvas.drawRect(RectF(left, top, right, top + RECIPE_IMAGE_BAR_HEIGHT), fill)
        left = right
    }
}
