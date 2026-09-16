package com.law.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.law.app.LawApp
import com.law.app.data.model.Law
import com.law.app.data.parser.HomeData
import com.law.app.data.parser.LawCategory
import com.law.app.data.parser.LawWebParser
import com.law.app.util.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val categories: List<LawCategory> = emptyList(),
    val newLaws: List<Law> = emptyList(),
    val popularSearches: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class HomeViewModel(
    private val parser: LawWebParser
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadHomeData()
    }

    fun loadHomeData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = parser.getHomeData()
            when (result) {
                is Result.Success -> {
                    val data = result.data
                    _uiState.value = _uiState.value.copy(
                        categories = data.categories,
                        newLaws = data.newLaws,
                        popularSearches = data.popularSearches,
                        isLoading = false,
                        error = null
                    )
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message,
                        isLoading = false
                    )
                }
                is Result.Loading -> {}
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(
                    LawWebParser.getInstance(LawApp.instance)
                ) as T
            }
        }
    }
}
