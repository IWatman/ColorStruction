package com.colorstruction.mixing

/**
 * Built-in palettes offered when the user has no paints yet.
 *
 * The colors are approximate swatches of real world paints, good enough to start mixing with;
 * the user is expected to adjust them to the paints they actually own.
 */
object StarterPalettes {

    /**
     * The default "Basic Acrylic" palette: 12 student-grade acrylic colors that cover a
     * reasonable mixing gamut (white, black, a warm and a cool of each primary, two earths).
     *
     * Each entry is `English paint name to #RRGGBB swatch`.
     */
    val basicAcrylic: List<Pair<String, String>> = listOf(
        "Titanium White" to "#F5F3EF",
        "Ivory Black" to "#231F20",
        "Cadmium Yellow Light" to "#FFD500",
        "Yellow Ochre" to "#C99B3F",
        "Cadmium Red Medium" to "#C3262E",
        "Alizarin Crimson Hue" to "#8F1D21",
        "Ultramarine Blue" to "#002185",
        "Phthalo Blue" to "#0F3E6E",
        "Phthalo Green" to "#0B3B33",
        "Burnt Sienna" to "#8A3324",
        "Burnt Umber" to "#5A3A22",
        "Dioxazine Purple" to "#3F0F52",
    )
}
