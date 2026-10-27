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

/** JVM tests for [PaletteDao] against an in-memory Room database. */
@RunWith(RobolectricTestRunner::class)
class PaletteDaoTest {

    private lateinit var database: ColorStructionDatabase
    private lateinit var paletteDao: PaletteDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ColorStructionDatabase::class.java,
        ).allowMainThreadQueries().build()
        paletteDao = database.paletteDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertsAndReadsPalette() = runBlocking {
        val id = paletteDao.insert(PaletteEntity(name = "Studio", type = "OPAQUE", createdAt = 1L))

        val stored = paletteDao.getById(id)

        assertEquals("Studio", stored?.name)
        assertEquals("OPAQUE", stored?.type)
        assertEquals(1L, stored?.createdAt)
    }

    @Test
    fun readsAllPalettesInCreationOrder() = runBlocking {
        val later = paletteDao.insert(PaletteEntity(name = "Later", type = "OPAQUE", createdAt = 200L))
        val earlier = paletteDao.insert(PaletteEntity(name = "Earlier", type = "WATERCOLOR", createdAt = 100L))

        val ids = paletteDao.getAll().map(PaletteEntity::id)

        assertEquals(listOf(earlier, later), ids)
    }

    @Test
    fun observesPaletteChanges() = runBlocking {
        val id = paletteDao.insert(PaletteEntity(name = "First", type = "OPAQUE", createdAt = 1L))
        assertEquals(listOf("First"), paletteDao.observeAll().first().map(PaletteEntity::name))

        paletteDao.update(PaletteEntity(id = id, name = "Second", type = "WATERCOLOR", createdAt = 1L))

        val observed = paletteDao.observeAll().first().single()
        assertEquals("Second", observed.name)
        assertEquals("WATERCOLOR", observed.type)
        assertEquals("Second", paletteDao.observeById(id).first()?.name)
    }

    @Test
    fun deletesPalette() = runBlocking {
        val id = paletteDao.insert(PaletteEntity(name = "Gone", type = "OPAQUE", createdAt = 1L))
        assertEquals(1, paletteDao.count())

        paletteDao.deleteById(id)

        assertEquals(0, paletteDao.count())
        assertNull(paletteDao.getById(id))
    }
}
