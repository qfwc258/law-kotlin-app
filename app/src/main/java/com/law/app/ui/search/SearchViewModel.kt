package com.law.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.law.app.LawApp
import com.law.app.data.model.Law
import com.law.app.data.model.LawType
import com.law.app.data.model.SearchMode
import com.law.app.data.model.SortOrder
import com.law.app.data.repository.LawRepository
import com.law.app.util.Constants
import com.law.app.util.Result
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val keyword: String = "",
    val selectedType: LawType = LawType.ALL,
    val searchMode: SearchMode = SearchMode.FUZZY,
    val sortOrder: SortOrder = SortOrder.PUBLISH_DESC,
    val results: List<Law> = emptyList(),
    val total: Int = 0,
    val currentPage: Int = 1,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val hasSearched: Boolean = false
)

class SearchViewModel(
    private val repository: LawRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    /** 更新关键词并防抖搜索 */
    fun onKeywordChange(keyword: String) {
        _uiState.value = _uiState.value.copy(keyword = keyword)
        searchJob?.cancel()
        if (keyword.isBlank()) {
            _uiState.value = _uiState.value.copy(
                results = emptyList(),
                total = 0,
                hasSearched = false,
                error = null
            )
            return
        }
        searchJob = viewModelScope.launch {
            delay(300) // 防抖
            performSearch(page = 1)
        }
    }

    fun onTypeChange(type: LawType) {
        _uiState.value = _uiState.value.copy(selectedType = type)
        if (_uiState.value.keyword.isNotBlank()) {
            performSearch(page = 1)
        }
    }

    fun onSearchModeChange(mode: SearchMode) {
        _uiState.value = _uiState.value.copy(searchMode = mode)
        if (_uiState.value.keyword.isNotBlank()) {
            performSearch(page = 1)
        }
    }

    fun onSortOrderChange(order: SortOrder) {
        _uiState.value = _uiState.value.copy(sortOrder = order)
        if (_uiState.value.keyword.isNotBlank()) {
            performSearch(page = 1)
        }
    }

    /** 立即搜索（点击搜索按钮） */
    fun searchNow() {
        if (_uiState.value.keyword.isNotBlank()) {
            performSearch(page = 1)
        }
    }

    /** 加载更多 */
    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || state.isLoading) return
        val maxPage = (state.total + Constants.PAGE_SIZE - 1) / Constants.PAGE_SIZE
        if (state.currentPage >= maxPage) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            val nextPage = state.currentPage + 1
            val result = repository.searchLaws(
                keyword = state.keyword,
                type = state.selectedType,
                searchMode = state.searchMode,
                sortOrder = state.sortOrder,
                page = nextPage,
                size = Constants.PAGE_SIZE
            )
            when (result) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        results = _uiState.value.results + result.data.first,
                        total = result.data.second,
                        currentPage = nextPage,
                        isLoadingMore = false
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

    private fun performSearch(page: Int) {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                hasSearched = true
            )
            val result = repository.searchLaws(
                keyword = state.keyword,
                type = state.selectedType,
                searchMode = state.searchMode,
                sortOrder = state.sortOrder,
                page = page,
                size = Constants.PAGE_SIZE
            )
            when (result) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        results = result.data.first,
                        total = result.data.second,
                        currentPage = page,
                        isLoading = false
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
                return SearchViewModel(LawApp.instance.repository) as T
            }
        }
    }
}
