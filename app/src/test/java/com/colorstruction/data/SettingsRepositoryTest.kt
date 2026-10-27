package com.colorstruction.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.colorstruction.mixing.PaletteType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** JVM tests for [SettingsRepository] backed by a temporary DataStore file. */
class SettingsRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var storeScope: CoroutineScope
    private lateinit var settingsFile: File
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        settingsFile = File(temporaryFolder.root, "settings.preferences_pb")
        repository = SettingsRepository(
            PreferenceDataStoreFactory.create(scope = storeScope) { settingsFile },
        )
    }

    @After
    fun tearDown() {
        storeScope.cancel()
    }

    @Test
    fun returnsDefaultsWhenNothingIsStored() = runTest {
        val settings = repository.settings.first()

        assertNull(settings.activePaletteId)
        assertEquals(3, settings.maxComponents)
        assertEquals(RatioFormat.PARTS, settings.ratioFormat)
    }

    @Test
    fun storesAndClearsActivePalette() = runTest {
        repository.setActivePalette(42L)
        assertEquals(42L, repository.settings.first().activePaletteId)

        repository.setActivePalette(null)
        assertNull(repository.settings.first().activePaletteId)
    }

    @Test
    fun clampsMaxComponentsToSupportedRange() = runTest {
        repository.setMaxComponents(0)
        assertEquals(1, repository.settings.first().maxComponents)

        repository.setMaxComponents(-7)
        assertEquals(1, repository.settings.first().maxComponents)

        repository.setMaxComponents(9)
        assertEquals(5, repository.settings.first().maxComponents)

        repository.setMaxComponents(4)
        assertEquals(4, repository.settings.first().maxComponents)
    }

    @Test
    fun storesRatioFormat() = runTest {
        repository.setRatioFormat(RatioFormat.PERCENT)
        assertEquals(RatioFormat.PERCENT, repository.settings.first().ratioFormat)

        repository.setRatioFormat(RatioFormat.PARTS)
        assertEquals(RatioFormat.PARTS, repository.settings.first().ratioFormat)
    }

    @Test
    fun keepsValuesAcrossRepositoryInstances() = runTest {
        repository.setActivePalette(7L)
        repository.setMaxComponents(5)
        repository.setRatioFormat(RatioFormat.PERCENT)
        // DataStore releases the file only once its scope has fully stopped; a plain cancel()
        // races with opening the second store ("multiple DataStores active for the same file").
        storeScope.coroutineContext.job.cancelAndJoin()

        storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val reopened = SettingsRepository(
            PreferenceDataStoreFactory.create(scope = storeScope) { settingsFile },
        )

        val settings = reopened.settings.first()
        assertEquals(7L, settings.activePaletteId)
        assertEquals(5, settings.maxComponents)
        assertEquals(RatioFormat.PERCENT, settings.ratioFormat)
    }

    @Test
    fun resolveActivePalettePrefersStoredId() {
        val palettes = listOf(
            Palette(id = 1L, name = "First", type = PaletteType.OPAQUE, createdAt = 1L),
            Palette(id = 2L, name = "Second", type = PaletteType.WATERCOLOR, createdAt = 2L),
        )

        assertEquals(2L, resolveActivePalette(palettes, AppSettings(activePaletteId = 2L))?.id)
        assertEquals(1L, resolveActivePalette(palettes, AppSettings(activePaletteId = null))?.id)
    }

    @Test
    fun resolveActivePaletteFallsBackWhenActivePaletteWasDeleted() {
        val palettes = listOf(
            Palette(id = 1L, name = "First", type = PaletteType.OPAQUE, createdAt = 1L),
        )

        val resolved = resolveActivePalette(palettes, AppSettings(activePaletteId = 99L))

        assertEquals(1L, resolved?.id)
        assertNull(resolveActivePalette(emptyList(), AppSettings(activePaletteId = 1L)))
    }
}
