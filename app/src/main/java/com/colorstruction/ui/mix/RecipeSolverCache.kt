package com.colorstruction.ui.mix

import com.colorstruction.data.Paint
import com.colorstruction.data.toSolverPaint
import com.colorstruction.mixing.PaletteType
import com.colorstruction.mixing.RecipeSolver
import com.colorstruction.mixing.SolverPaint

/**
 * Keeps one [RecipeSolver] alive while the paints and the palette type stay the same.
 *
 * Building a solver pre-computes the paint spectra, so reusing it keeps re-solves cheap.
 */
internal class RecipeSolverCache {

    private var solver: RecipeSolver? = null
    private var paints: List<SolverPaint>? = null
    private var type: PaletteType? = null

    /** Returns a solver for [palettePaints] of [paletteType], reusing the previous one if valid. */
    fun get(palettePaints: List<Paint>, paletteType: PaletteType): RecipeSolver {
        val solverPaints = palettePaints.map { it.toSolverPaint() }
        val existing = solver
        if (existing != null && type == paletteType && paints == solverPaints) return existing
        return RecipeSolver(solverPaints, paletteType).also {
            solver = it
            paints = solverPaints
            type = paletteType
        }
    }
}
