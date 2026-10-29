package com.colorstruction.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** How recipe proportions are displayed. */
enum class RatioFormat {
    /** Integer-ish parts, for example `3 : 1 : ½`. */
    PARTS,

    /** Percentages that sum to 100. */
    PERCENT,

    /** Volumes in millilitres that sum to [AppSettings.totalAmount]. */
    AMOUNT,
}

/**
 * User settings that live outside the database.
 *
 * @property activePaletteId id of the palette recipes are computed from, `null` if unset.
 * @property maxComponents cap on paints per recipe, always within
 *   [AppSettings.MIN_MAX_COMPONENTS]..[AppSettings.MAX_MAX_COMPONENTS].
 * @property ratioFormat how proportions are rendered.
 * @property totalAmount total volume of a recipe in millilitres, used by [RatioFormat.AMOUNT],
 *   always within [AppSettings.MIN_TOTAL_AMOUNT]..[AppSettings.MAX_TOTAL_AMOUNT].
 * @property themeMode how the app chooses between light and dark schemes.
 * @property recentColors last distinct target colors as ARGB ints, most recent first.
 */
data class AppSettings(
    val activePaletteId: Long? = null,
    val maxComponents: Int = SettingsRepository.DEFAULT_MAX_COMPONENTS,
    val ratioFormat: RatioFormat = RatioFormat.PARTS,
    val totalAmount: Int = SettingsRepository.DEFAULT_TOTAL_AMOUNT,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val recentColors: List<Int> = emptyList(),
) {
    companion object {
        /** Inclusive lower bound of [maxComponents]. */
        const val MIN_MAX_COMPONENTS: Int = 1

        /** Inclusive upper bound of [maxComponents]. */
        const val MAX_MAX_COMPONENTS: Int = 5

        /** Inclusive lower bound of [totalAmount] in millilitres. */
        const val MIN_TOTAL_AMOUNT: Int = 1

        /** Inclusive upper bound of [totalAmount] in millilitres. */
        const val MAX_TOTAL_AMOUNT: Int = 5000
    }
}

/**
 * The part of settings storage the UI depends on: a settings stream and the active palette.
 *
 * View models and tests use this interface so they do not need a real DataStore.
 */
interface SettingsStore {

    /** The current settings, re-emitted after every change. */
    val settings: Flow<AppSettings>

    /** Makes [paletteId] the active palette, or clears the selection with `null`. */
    suspend fun setActivePalette(paletteId: Long?)

    /**
     * Stores the paint cap per recipe, clamped to
     * [AppSettings.MIN_MAX_COMPONENTS]..[AppSettings.MAX_MAX_COMPONENTS].
     */
    suspend fun setMaxComponents(value: Int)

    /** Stores the proportions format. */
    suspend fun setRatioFormat(format: RatioFormat)

    /**
     * Stores the total volume of a recipe in millilitres, clamped to
     * [AppSettings.MIN_TOTAL_AMOUNT]..[AppSettings.MAX_TOTAL_AMOUNT].
     */
    suspend fun setTotalAmount(value: Int)

    /** Stores how the app chooses between light and dark schemes. */
    suspend fun setThemeMode(mode: ThemeMode)

    /**
     * Records [argb] as the most recent target color, moving it to the front and
     * keeping at most [SettingsRepository.MAX_RECENT_COLORS] distinct colors.
     */
    suspend fun addRecentColor(argb: Int)
}

/**
 * DataStore-backed settings storage.
 *
 * Reads are exposed as a [Flow] that emits the current values immediately and again after
 * every write. Values are normalized on read, so a corrupted or outdated preferences file
 * still produces a valid [AppSettings].
 *
 * @param dataStore the preferences store to read and write; tests pass a temporary file.
 */
class SettingsRepository(private val dataStore: DataStore<Preferences>) : SettingsStore {

    /** The current settings, re-emitted after every change. */
    override val settings: Flow<AppSettings> = dataStore.data.map { it.toAppSettings() }

    /** Makes [paletteId] the active palette, or clears the selection with `null`. */
    override suspend fun setActivePalette(paletteId: Long?) {
        dataStore.edit { preferences ->
            if (paletteId == null) {
                preferences.remove(KEY_ACTIVE_PALETTE_ID)
            } else {
                preferences[KEY_ACTIVE_PALETTE_ID] = paletteId
            }
        }
    }

    /**
     * Stores the paint cap per recipe, clamped to
     * [AppSettings.MIN_MAX_COMPONENTS]..[AppSettings.MAX_MAX_COMPONENTS].
     */
    override suspend fun setMaxComponents(value: Int) {
        val clamped = value.coerceIn(AppSettings.MIN_MAX_COMPONENTS, AppSettings.MAX_MAX_COMPONENTS)
        dataStore.edit { preferences -> preferences[KEY_MAX_COMPONENTS] = clamped }
    }

    /** Stores the proportions format. */
    override suspend fun setRatioFormat(format: RatioFormat) {
        dataStore.edit { preferences -> preferences[KEY_RATIO_FORMAT] = format.name }
    }

    /**
     * Stores the total volume of a recipe in millilitres, clamped to
     * [AppSettings.MIN_TOTAL_AMOUNT]..[AppSettings.MAX_TOTAL_AMOUNT].
     */
    override suspend fun setTotalAmount(value: Int) {
        val clamped = value.coerceIn(AppSettings.MIN_TOTAL_AMOUNT, AppSettings.MAX_TOTAL_AMOUNT)
        dataStore.edit { preferences -> preferences[KEY_TOTAL_AMOUNT] = clamped }
    }

    /** Stores how the app chooses between light and dark schemes. */
    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences -> preferences[KEY_THEME_MODE] = mode.name }
    }

    /**
     * Records [argb] as the most recent target color, moving it to the front and
     * keeping at most [SettingsRepository.MAX_RECENT_COLORS] distinct colors.
     */
    override suspend fun addRecentColor(argb: Int) {
        dataStore.edit { preferences ->
            val current = parseRecentColors(preferences[KEY_RECENT_COLORS])
            val updated = (listOf(argb) + current.filterNot { it == argb })
                .take(MAX_RECENT_COLORS)
            preferences[KEY_RECENT_COLORS] = updated.joinToString(",")
        }
    }

    companion object {
        /** Default paint cap per recipe. */
        const val DEFAULT_MAX_COMPONENTS: Int = 3

        /** Default total volume of a recipe in millilitres. */
        const val DEFAULT_TOTAL_AMOUNT: Int = 50

        /** Maximum number of recent target colors kept. */
        const val MAX_RECENT_COLORS: Int = 12

        /** Settings stored in the app's private storage, shared by the whole process. */
        fun fromContext(context: Context): SettingsRepository =
            SettingsRepository(context.applicationContext.settingsDataStore)
    }
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
)

private val KEY_ACTIVE_PALETTE_ID = longPreferencesKey("active_palette_id")
private val KEY_MAX_COMPONENTS = intPreferencesKey("max_components")
private val KEY_RATIO_FORMAT = stringPreferencesKey("ratio_format")
private val KEY_TOTAL_AMOUNT = intPreferencesKey("total_amount")
private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
private val KEY_RECENT_COLORS = stringPreferencesKey("recent_colors")

private fun Preferences.toAppSettings(): AppSettings = AppSettings(
    activePaletteId = this[KEY_ACTIVE_PALETTE_ID],
    maxComponents = (this[KEY_MAX_COMPONENTS] ?: SettingsRepository.DEFAULT_MAX_COMPONENTS)
        .coerceIn(AppSettings.MIN_MAX_COMPONENTS, AppSettings.MAX_MAX_COMPONENTS),
    ratioFormat = RatioFormat.entries.firstOrNull { it.name == this[KEY_RATIO_FORMAT] }
        ?: RatioFormat.PARTS,
    totalAmount = (this[KEY_TOTAL_AMOUNT] ?: SettingsRepository.DEFAULT_TOTAL_AMOUNT)
        .coerceIn(AppSettings.MIN_TOTAL_AMOUNT, AppSettings.MAX_TOTAL_AMOUNT),
    themeMode = ThemeMode.entries.firstOrNull { it.name == this[KEY_THEME_MODE] }
        ?: ThemeMode.SYSTEM,
    recentColors = parseRecentColors(this[KEY_RECENT_COLORS]),
)

/** Parses the comma-separated recent colors, dropping garbage and capping the size. */
private fun parseRecentColors(raw: String?): List<Int> =
    raw?.split(',')
        ?.mapNotNull { it.trim().toIntOrNull() }
        ?.take(SettingsRepository.MAX_RECENT_COLORS)
        ?: emptyList()

/**
 * Returns the palette the app should currently work with.
 *
 * The stored [AppSettings.activePaletteId] wins when that palette still exists; otherwise the
 * first palette (oldest) is returned, and `null` only when there are no palettes at all.
 */
fun resolveActivePalette(palettes: List<Palette>, settings: AppSettings): Palette? =
    palettes.firstOrNull { it.id == settings.activePaletteId } ?: palettes.firstOrNull()
