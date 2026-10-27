package com.colorstruction.ui.eyedropper

import kotlin.math.max

/** Largest pan offset per axis that still keeps the image covering the container. */
fun EyedropperState.maxPan(container: SizePx, image: SizePx): PointPx {
    val shown = displayedSize(container, image, zoom)
    return PointPx(
        x = max(0f, (shown.width - container.width) / 2f),
        y = max(0f, (shown.height - container.height) / 2f),
    )
}

/** Limits the pan so the image cannot be dragged fully out of view. */
fun EyedropperState.clampPan(container: SizePx, image: SizePx): EyedropperState {
    val limit = maxPan(container, image)
    return copy(panX = panX.coerceIn(-limit.x, limit.x), panY = panY.coerceIn(-limit.y, limit.y))
}

/** Moves the crosshair by a screen-pixel delta and keeps it inside the image. */
fun EyedropperState.dragCrosshair(
    container: SizePx,
    image: SizePx,
    dx: Float,
    dy: Float,
): EyedropperState {
    val scale = displayScale(container, image, zoom)
    if (scale <= 0f) return this
    return copy(
        crosshairX = crosshairX + dx / scale,
        crosshairY = crosshairY + dy / scale,
    ).clampCrosshair(image)
}

/** Moves the image by a screen-pixel pan delta. */
fun EyedropperState.panBy(container: SizePx, image: SizePx, dx: Float, dy: Float): EyedropperState =
    copy(panX = panX + dx, panY = panY + dy).clampPan(container, image)

/** Sets a new zoom while keeping the image point under ([focusX], [focusY]) in place. */fun EyedropperState.zoomTo(
    container: SizePx,
    image: SizePx,
    newZoom: Float,
    focusX: Float,
    focusY: Float,
): EyedropperState {
    val target = newZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
    val anchor = screenToImage(container, image, focusX, focusY)
    val scale = displayScale(container, image, target)
    return copy(
        zoom = target,
        panX = focusX - container.width / 2f - (anchor.x - image.width / 2f) * scale,
        panY = focusY - container.height / 2f - (anchor.y - image.height / 2f) * scale,
    ).clampPan(container, image)
}

/** Center of the loupe: above the crosshair, kept inside the container. */
fun loupeCenter(crosshair: PointPx, container: SizePx, radius: Float, gap: Float): PointPx {
    val maxX = max(radius, container.width - radius)
    val maxY = max(radius, container.height - radius)
    return PointPx(
        x = crosshair.x.coerceIn(radius, maxX),
        y = (crosshair.y - radius - gap).coerceIn(radius, maxY),
    )
}
