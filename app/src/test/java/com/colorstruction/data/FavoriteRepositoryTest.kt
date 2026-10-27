package com.colorstruction.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.colorstruction.color.Rgb
import com.colorstruction.mixing.PaletteType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** JVM tests for [RoomFavoriteRepository] against an in-memory Room database. */
@RunWith(RobolectricTestRunner::class)
class FavoriteRepositoryTest {

    private lateinit var database: ColorStructionDatabase
    private lateinit var repository: FavoriteRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ColorStructionDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RoomFavoriteRepository(database, clock = { 1000L })
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun favorite(createdAt: Long, name: String) = FavoriteRecipe(
        target = MID_BLUE,
        paletteName = "Acrylic",
        deltaE = 2.0,
        createdAt = createdAt,
        components = listOf(
            FavoriteComponent(paintName = name, color = PAINT_BLUE, isWater = false, weight = 1.0),
        ),
    )

    @Test
    fun addAndObserveFavorites() = runBlocking {
        repository.add(favorite(createdAt = 10L, name = "old"))
        repository.add(favorite(createdAt = 20L, name = "new"))

        val stored = repository.favorites().first()

        assertEquals(2, repository.count())
        assertEquals(listOf("new", "old"), stored.map { it.components.single().paintName })
        assertEquals(MID_BLUE, stored.first().target)
        assertEquals("Acrylic", stored.first().paletteName)
    }

    @Test
    fun deleteRemovesFavorite() = runBlocking {
        val id = repository.add(favorite(createdAt = 10L, name = "gone"))

        repository.delete(id)

        assertEquals(0, repository.count())
        assertEquals(emptyList<FavoriteRecipe>(), repository.favorites().first())
    }

    @Test
    fun favoriteIsIndependentOfLaterPaintDeletion() = runBlocking {
        val paintRepository = RoomPaintRepository(database)
        val paletteId = paintRepository.createPalette("Acrylic", PaletteType.OPAQUE)
        val paintId = paintRepository.addPaint(paletteId, "Ultramarine", PAINT_BLUE)
        val paint = requireNotNull(paintRepository.getPaint(paintId))
        repository.add(
            FavoriteRecipe(
                target = MID_BLUE,
                paletteName = "Acrylic",
                deltaE = 0.5,
                createdAt = 5L,
                components = listOf(
                    FavoriteComponent(paint.name, paint.color, isWater = false, weight = 1.0),
                ),
            ),
        )

        paintRepository.deletePaint(paintId)
        paintRepository.deletePalette(paletteId)

        val stored = repository.favorites().first().single().components.single()
        assertEquals("Ultramarine", stored.paintName)
        assertEquals(PAINT_BLUE, stored.color)
    }

    private companion object {
        /** A mid-blue target that survives the ARGB round trip. */
        val MID_BLUE: Rgb = Rgb.fromArgbInt(0xFF3366CC.toInt())

        /** Color of the snapshot paint; also ARGB-safe. */
        val PAINT_BLUE: Rgb = Rgb.fromArgbInt(0xFF112233.toInt())
    }
}
