package com.law.app.ui.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.law.app.LawApp
import com.law.app.data.model.Law
import com.law.app.data.parser.LawWebParser
import com.law.app.util.Constants
import com.law.app.util.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CategoryListUiState(
    val categoryName: String = "",
    val results: List<Law> = emptyList(),
    val total: Int = 0,
    val currentPage: Int = 1,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null
)

class CategoryListViewModel(
    private val parser: LawWebParser
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoryListUiState())
    val uiState: StateFlow<CategoryListUiState> = _uiState.asStateFlow()

    fun setCategory(categoryName: String) {
        if (_uiState.value.categoryName == categoryName && _uiState.value.results.isNotEmpty()) {
            return
        }
        _uiState.value = _uiState.value.copy(categoryName = categoryName)
        loadList(page = 1)
    }

    fun refresh() {
        loadList(page = 1)
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || state.isLoading) return
        val maxPage = (state.total + Constants.PAGE_SIZE - 1) / Constants.PAGE_SIZE
        if (state.currentPage >= maxPage) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            val nextPage = state.currentPage + 1
            val result = parser.search(
                keyword = state.categoryName,
                page = nextPage,
                pageSize = Constants.PAGE_SIZE
            )
            when (result) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        results = _uiState.value.results + result.data.first,
                        total = result.data.second,
                        currentPage = nextPage,
                        isLoadingMore = false,
                        error = null
                    )
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message,
                        isLoadingMore = false
                    )
                }
                is Result.Loading -> {}
            }
        }
    }

    private fun loadList(page: Int) {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null
            )
            val result = parser.search(
                keyword = state.categoryName,
                page = page,
                pageSize = Constants.PAGE_SIZE
            )
            when (result) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        results = result.data.first,
                        total = result.data.second,
                        currentPage = page,
                        isLoading = false,
                        error = null
                    )
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message,
                        isLoading = false,
                        results = emptyList()
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
                return CategoryListViewModel(
                    LawWebParser.getInstance(LawApp.instance)
                ) as T
            }
        }
    }
}
