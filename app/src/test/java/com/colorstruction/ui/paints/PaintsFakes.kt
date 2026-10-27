package com.colorstruction.ui.paints

import com.colorstruction.color.Rgb
import com.colorstruction.data.AppSettings
import com.colorstruction.data.FavoriteRecipe
import com.colorstruction.data.FavoriteRepository
import com.colorstruction.data.Paint
import com.colorstruction.data.PaintRepository
import com.colorstruction.data.Palette
import com.colorstruction.data.RatioFormat
import com.colorstruction.data.SettingsRepository
import com.colorstruction.data.SettingsStore
import com.colorstruction.data.ThemeMode
import com.colorstruction.mixing.PaletteType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [PaintRepository] for view model tests. */
internal class FakePaintRepository(
    palettes: List<Palette> = emptyList(),
    paints: List<Paint> = emptyList(),
) : PaintRepository {

    private val palettesState = MutableStateFlow(palettes)
    private val paintsState = MutableStateFlow(paints)
    private var nextPaletteId = (palettes.maxOfOrNull { it.id } ?: 0L) + 1
    private var nextPaintId = (paints.maxOfOrNull { it.id } ?: 0L) + 1

    override fun observePalettes(): Flow<List<Palette>> = palettesState

    override fun observePaints(paletteId: Long): Flow<List<Paint>> = paintsState.map { all ->
        all.filter { it.paletteId == paletteId }.sortedBy { it.sortOrder }
    }

    override suspend fun getPalettes(): List<Palette> = palettesState.value

    override suspend fun getPaints(paletteId: Long): List<Paint> =
        paintsState.value.filter { it.paletteId == paletteId }.sortedBy { it.sortOrder }

    override suspend fun createPalette(name: String, type: PaletteType): Long {
        val id = nextPaletteId++
        palettesState.value += Palette(id = id, name = name, type = type, createdAt = id)
        return id
    }

    override suspend fun renamePalette(id: Long, name: String) {
        palettesState.value = palettesState.value.map { if (it.id == id) it.copy(name = name) else it }
    }

    override suspend fun setPaletteType(id: Long, type: PaletteType) {
        palettesState.value = palettesState.value.map { if (it.id == id) it.copy(type = type) else it }
    }

    override suspend fun deletePalette(id: Long) {
        palettesState.value = palettesState.value.filterNot { it.id == id }
        paintsState.value = paintsState.value.filterNot { it.paletteId == id }
    }

    override suspend fun addPaint(
        paletteId: Long,
        name: String,
        color: Rgb,
        tintingStrength: Double,
        note: String?,
    ): Long {
        val id = nextPaintId++
        val sortOrder = paintsState.value
            .filter { it.paletteId == paletteId }
            .maxOfOrNull { it.sortOrder + 1 }
            ?: 0
        paintsState.value += Paint(
            id = id,
            paletteId = paletteId,
            name = name,
            color = color,
            tintingStrength = tintingStrength,
            note = note,
            sortOrder = sortOrder,
        )
        return id
    }

    override suspend fun getPaint(id: Long): Paint? = paintsState.value.firstOrNull { it.id == id }

    override suspend fun updatePaint(paint: Paint) {
        paintsState.value = paintsState.value.map { if (it.id == paint.id) paint else it }
    }

    override suspend fun restorePaint(paint: Paint): Long {
        val id = nextPaintId++
        paintsState.value += paint.copy(id = id)
        return id
    }

    override suspend fun deletePaint(id: Long) {
        paintsState.value = paintsState.value.filterNot { it.id == id }
    }

    override suspend fun reorderPaints(paletteId: Long, orderedIds: List<Long>) {
        val orders = orderedIds.withIndex().associate { (index, id) -> id to index }
        paintsState.value = paintsState.value.map { paint ->
            val order = orders[paint.id]
            if (paint.paletteId == paletteId && order != null) {
                paint.copy(sortOrder = order)
            } else {
                paint
            }
        }
    }

    override suspend fun ensureStarterPalette(): Long =
        palettesState.value.firstOrNull()?.id
            ?: createPalette("Starter", PaletteType.OPAQUE)
}

/** In-memory [FavoriteRepository] for view model tests. */
internal class FakeFavoriteRepository(initial: List<FavoriteRecipe> = emptyList()) : FavoriteRepository {

    private val state = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    override fun favorites(): Flow<List<FavoriteRecipe>> = state

    override suspend fun add(favorite: FavoriteRecipe): Long {
        val id = nextId++
        state.value = listOf(favorite.copy(id = id)) + state.value
        return id
    }

    override suspend fun delete(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun count(): Int = state.value.size
}

/** In-memory [SettingsStore] for view model tests. */internal class FakeSettingsStore(initial: AppSettings = AppSettings()) : SettingsStore {

    private val state = MutableStateFlow(initial)

    override val settings: Flow<AppSettings> = state

    override suspend fun setActivePalette(paletteId: Long?) {
        state.value = state.value.copy(activePaletteId = paletteId)
    }

    override suspend fun setMaxComponents(value: Int) {
        state.value = state.value.copy(
            maxComponents = value.coerceIn(
                AppSettings.MIN_MAX_COMPONENTS,
                AppSettings.MAX_MAX_COMPONENTS,
            ),
        )
    }

    override suspend fun setRatioFormat(format: RatioFormat) {
        state.value = state.value.copy(ratioFormat = format)
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        state.value = state.value.copy(themeMode = mode)
    }

    override suspend fun addRecentColor(argb: Int) {
        val updated = (listOf(argb) + state.value.recentColors.filterNot { it == argb })
            .take(SettingsRepository.MAX_RECENT_COLORS)
        state.value = state.value.copy(recentColors = updated)
    }
}
