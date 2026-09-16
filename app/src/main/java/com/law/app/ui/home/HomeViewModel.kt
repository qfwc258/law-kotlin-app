package com.law.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.law.app.LawApp
import com.law.app.data.model.Law
import com.law.app.data.model.LawType
import com.law.app.data.repository.LawRepository
import com.law.app.util.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = false,
    val recentLaws: List<Law> = emptyList(),
    val newLaws: List<Law> = emptyList(),
    val error: String? = null
)

class HomeViewModel(
    private val repository: LawRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadHomeData()
    }

    fun loadHomeData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // 加载最近阅读
            repository.getRecentLaws(limit = 5).collect { recent ->
                _uiState.value = _uiState.value.copy(recentLaws = recent)
            }
        }

        viewModelScope.launch {
            // 加载新法速递（搜索最新发布）
            val result = repository.searchLaws(
                keyword = "",
                type = LawType.LAW,
                page = 1,
                size = 10
            )
            when (result) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        newLaws = result.data.first,
                        isLoading = false
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
                return HomeViewModel(LawApp.instance.repository) as T
            }
        }
    }
}
