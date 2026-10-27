package com.colorstruction.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** JVM tests for [FavoriteDao] against an in-memory Room database. */
@RunWith(RobolectricTestRunner::class)
class FavoriteDaoTest {

    private lateinit var database: ColorStructionDatabase
    private lateinit var favoriteDao: FavoriteDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ColorStructionDatabase::class.java,
        ).allowMainThreadQueries().build()
        favoriteDao = database.favoriteDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun favorite(createdAt: Long = 1L, note: String? = null) = FavoriteRecipeEntity(
        targetArgb = 0xFF3366CC.toInt(),
        paletteName = "Acrylic",
        deltaE = 1.5,
        createdAt = createdAt,
        note = note,
    )

    private fun component(
        position: Int,
        name: String,
        isWater: Boolean = false,
        weight: Double = 0.5,
    ) = FavoriteComponentEntity(
        favoriteId = 0L,
        position = position,
        paintName = name,
        colorArgb = if (isWater) 0xFFFFFFFF.toInt() else 0xFF112233.toInt(),
        isWater = isWater,
        weight = weight,
    )

    @Test
    fun insertsFavoriteWithComponents() = runBlocking {
        val id = favoriteDao.insertWithComponents(
            favorite = favorite(note = "sunset"),
            components = listOf(component(0, "Ultramarine"), component(1, "", isWater = true)),
        )

        val stored = favoriteDao.getById(id)
        assertEquals(0xFF3366CC.toInt(), stored?.favorite?.targetArgb)
        assertEquals("Acrylic", stored?.favorite?.paletteName)
        assertEquals("sunset", stored?.favorite?.note)
        assertEquals(1, favoriteDao.count())
        assertEquals(2, favoriteDao.componentCount(id))
    }

    @Test
    fun componentsComeBackInPositionOrder() = runBlocking {
        val id = favoriteDao.insertWithComponents(
            favorite = favorite(),
            // Deliberately out of order: position is what defines the display order.
            components = listOf(
                component(position = 2, name = "third"),
                component(position = 0, name = "first"),
                component(position = 1, name = "second"),
            ),
        )

        val names = favoriteDao.getById(id)?.toDomain()?.components?.map { it.paintName }
        assertEquals(listOf("first", "second", "third"), names)
    }

    @Test
    fun deletingFavoriteCascadesToItsComponents() = runBlocking {
        val id = favoriteDao.insertWithComponents(
            favorite = favorite(),
            components = listOf(component(0, "a"), component(1, "b")),
        )
        val otherId = favoriteDao.insertWithComponents(
            favorite = favorite(createdAt = 2L),
            components = listOf(component(0, "kept")),
        )

        favoriteDao.delete(id)

        assertEquals(0, favoriteDao.componentCount(id))
        assertEquals(1, favoriteDao.componentCount(otherId))
        assertNull(favoriteDao.getById(id))
    }

    @Test
    fun observeAllOrdersNewestFirst() = runBlocking {
        favoriteDao.insertWithComponents(favorite(createdAt = 100L), emptyList())
        favoriteDao.insertWithComponents(favorite(createdAt = 300L), emptyList())
        favoriteDao.insertWithComponents(favorite(createdAt = 200L), emptyList())

        val createdAt = favoriteDao.observeAll().first().map { it.favorite.createdAt }

        assertEquals(listOf(300L, 200L, 100L), createdAt)
    }
}
