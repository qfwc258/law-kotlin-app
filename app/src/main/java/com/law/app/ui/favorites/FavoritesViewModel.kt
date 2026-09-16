package com.law.app.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.law.app.LawApp
import com.law.app.data.model.Law
import com.law.app.data.repository.LawRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FavoritesUiState(
    val favorites: List<Law> = emptyList(),
    val isLoading: Boolean = true
)

class FavoritesViewModel(
    private val repository: LawRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    init {
        loadFavorites()
    }

    fun loadFavorites() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.getFavoriteLaws().collect { laws ->
                _uiState.value = _uiState.value.copy(
                    favorites = laws,
                    isLoading = false
                )
            }
        }
    }

    fun removeFavorite(lawId: String) {
        viewModelScope.launch {
            repository.toggleFavorite(lawId, false)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return FavoritesViewModel(LawApp.instance.repository) as T
            }
        }
    }
}
