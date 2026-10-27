package com.colorstruction.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.colorstruction.ColorStructionApp
import com.colorstruction.data.FavoriteRecipe
import com.colorstruction.data.FavoriteRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Snapshot of the "Favorites" screen. */
data class FavoritesUiState(val favorites: List<FavoriteRecipe> = emptyList())

/** State holder of the "Favorites" screen: the saved recipes and a one-step undo of deletes. */
class FavoritesViewModel(private val repository: FavoriteRepository) : ViewModel() {

    private var lastDeleted: FavoriteRecipe? = null

    /** Currently stored favorites, newest first. */
    val uiState: StateFlow<FavoritesUiState> = repository.favorites()
        .map(::FavoritesUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), FavoritesUiState())

    /** Deletes [favorite]; [undoDelete] stores the same snapshot again. */
    fun delete(favorite: FavoriteRecipe) {
        lastDeleted = favorite
        viewModelScope.launch { repository.delete(favorite.id) }
    }

    /** Re-adds the snapshot removed by the last [delete] under a new id. */
    fun undoDelete() {
        val deleted = lastDeleted ?: return
        lastDeleted = null
        viewModelScope.launch { repository.add(deleted.copy(id = 0L)) }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        /** Creates the view model from the application container. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ColorStructionApp
                FavoritesViewModel(repository = app.container.favoriteRepository)
            }
        }
    }
}
