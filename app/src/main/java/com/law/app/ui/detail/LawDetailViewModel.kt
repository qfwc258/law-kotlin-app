package com.law.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.law.app.LawApp
import com.law.app.data.model.Article
import com.law.app.data.model.Law
import com.law.app.data.repository.LawRepository
import com.law.app.util.DownloadState
import com.law.app.util.LawDownloadManager
import com.law.app.util.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DetailUiState(
    val law: Law? = null,
    val articles: List<Article> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isFavorite: Boolean = false,
    val pdfDownloadState: DownloadState = DownloadState.Idle,
    val wpsDownloadState: DownloadState = DownloadState.Idle
)

class LawDetailViewModel(
    private val repository: LawRepository,
    private val downloadManager: LawDownloadManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    init {
        // 观察下载状态变化
        viewModelScope.launch {
            downloadManager.downloadStates.collect { states ->
                val law = _uiState.value.law ?: return@collect
                val pdfState = states[law.id + "_pdf"] ?: DownloadState.Idle
                val wpsState = states[law.id + "_wps"] ?: DownloadState.Idle
                _uiState.value = _uiState.value.copy(
                    pdfDownloadState = pdfState,
                    wpsDownloadState = wpsState
                )
            }
        }
    }

    fun loadLaw(lawId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // 先查本地收藏状态
            val favorite = repository.isFavorite(lawId)
            _uiState.value = _uiState.value.copy(isFavorite = favorite)

            val result = repository.getLawDetail(lawId)
            when (result) {
                is Result.Success -> {
                    val law = result.data
                    val articles = law.extractArticles()
                    _uiState.value = _uiState.value.copy(
                        law = law,
                        articles = articles,
                        isLoading = false,
                        isFavorite = law.isFavorite
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

    fun toggleFavorite() {
        val law = _uiState.value.law ?: return
        val newFavorite = !_uiState.value.isFavorite
        viewModelScope.launch {
            repository.toggleFavorite(law.id, newFavorite)
            _uiState.value = _uiState.value.copy(isFavorite = newFavorite)
        }
    }

    /** 下载 PDF */
    fun downloadPdf() {
        val law = _uiState.value.law ?: return
        downloadManager.download(law, "pdf")
    }

    /** 下载 WPS */
    fun downloadWps() {
        val law = _uiState.value.law ?: return
        downloadManager.download(law, "wps")
    }

    /** 打开已下载的 PDF */
    fun openPdf() {
        val law = _uiState.value.law ?: return
        downloadManager.openDownloadedFile(law, "pdf")
    }

    /** 打开已下载的 WPS */
    fun openWps() {
        val law = _uiState.value.law ?: return
        downloadManager.openDownloadedFile(law, "wps")
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LawDetailViewModel(
                    LawApp.instance.repository,
                    LawApp.instance.downloadManager
                ) as T
            }
        }
    }
}
