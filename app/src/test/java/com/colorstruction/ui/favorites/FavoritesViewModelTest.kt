package com.colorstruction.ui.favorites

import com.colorstruction.color.Rgb
import com.colorstruction.data.FavoriteComponent
import com.colorstruction.data.FavoriteRecipe
import com.colorstruction.ui.paints.FakeFavoriteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Tests for [FavoritesViewModel]: listing the stored recipes and undoing a delete. */
@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private fun favorite(id: Long, name: String = "Mix $id"): FavoriteRecipe = FavoriteRecipe(
        id = id,
        target = Rgb(0.5, 0.4, 0.2),
        paletteName = "Opaque",
        deltaE = 1.5,
        createdAt = id,
        components = listOf(
            FavoriteComponent(paintName = name, color = Rgb(0.8, 0.1, 0.1), isWater = false, weight = 0.5),
            FavoriteComponent(paintName = "White", color = Rgb.WHITE, isWater = false, weight = 0.5),
        ),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Collects the state in the background, the way the screen does. */
    private fun TestScope.observe(viewModel: FavoritesViewModel): FavoritesViewModel {
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun listsStoredFavorites() = runTest(dispatcher) {
        val repository = FakeFavoriteRepository(listOf(favorite(1L), favorite(2L)))
        val viewModel = observe(FavoritesViewModel(repository))

        assertEquals(listOf(1L, 2L), viewModel.uiState.value.favorites.map { it.id })
    }

    @Test
    fun deleteRemovesTheFavoriteAndUndoRestoresIt() = runTest(dispatcher) {
        val repository = FakeFavoriteRepository(listOf(favorite(1L), favorite(2L)))
        val viewModel = observe(FavoritesViewModel(repository))

        val deleted = viewModel.uiState.value.favorites.first { it.id == 1L }
        viewModel.delete(deleted)
        advanceUntilIdle()
        assertEquals(listOf(2L), viewModel.uiState.value.favorites.map { it.id })

        viewModel.undoDelete()
        advanceUntilIdle()

        val restored = viewModel.uiState.value.favorites
        assertEquals(2, restored.size)
        val copy = restored.first { it.id != 2L }
        assertEquals(deleted.target.toArgbInt(), copy.target.toArgbInt())
        assertEquals(deleted.paletteName, copy.paletteName)
        assertEquals(deleted.createdAt, copy.createdAt)
        assertEquals(deleted.components, copy.components)
        assertTrue(copy.id != deleted.id)
    }

    @Test
    fun undoWithoutADeleteDoesNothing() = runTest(dispatcher) {
        val repository = FakeFavoriteRepository(listOf(favorite(1L)))
        val viewModel = observe(FavoritesViewModel(repository))

        viewModel.undoDelete()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.favorites.size)
    }
}
