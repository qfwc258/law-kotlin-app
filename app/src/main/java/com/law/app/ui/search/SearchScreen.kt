package com.law.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.law.app.data.model.LawType
import com.law.app.data.model.SearchMode
import com.law.app.data.model.SortOrder
import com.law.app.ui.common.EmptyState
import com.law.app.ui.common.ErrorState
import com.law.app.ui.common.LawCard
import com.law.app.ui.common.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onLawClick: (String) -> Unit,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory)
) {
    val uiState by viewModel.uiState
    val listState = rememberLazyListState()

    // 滚动到底部加载更多
    LaunchedEffect(listState.canScrollForward) {
        if (!listState.canScrollForward && uiState.results.isNotEmpty()) {
            viewModel.loadMore()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 搜索栏
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            OutlinedTextField(
                value = uiState.keyword,
                onValueChange = viewModel::onKeywordChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("输入法规名称或关键词…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "搜索") },
                trailingIcon = {
                    if (uiState.keyword.isNotBlank()) {
                        IconButton(onClick = { viewModel.onKeywordChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "清除")
                        }
                    }
                },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 筛选行：类型 + 搜索方式 + 排序
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 法规类型下拉
                TypeDropdown(
                    selected = uiState.selectedType,
                    onSelect = viewModel::onTypeChange,
                    modifier = Modifier.weight(1f)
                )
                // 搜索方式
                FilterChip(
                    selected = uiState.searchMode == SearchMode.FUZZY,
                    onClick = {
                        viewModel.onSearchModeChange(
                            if (uiState.searchMode == SearchMode.FUZZY)
                                SearchMode.ACCURATE else SearchMode.FUZZY
                        )
                    },
                    label = { Text(uiState.searchMode.displayName) }
                )
                // 排序
                SortDropdown(
                    selected = uiState.sortOrder,
                    onSelect = viewModel::onSortOrderChange
                )
            }
        }

        // 结果计数
        if (uiState.hasSearched && uiState.results.isNotEmpty()) {
            Text(
                text = "共找到 ${uiState.total} 条结果",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 结果列表
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading && uiState.results.isEmpty() -> {
                    LoadingState(modifier = Modifier.align(Alignment.Center))
                }
                uiState.error != null && uiState.results.isEmpty() -> {
                    ErrorState(
                        message = uiState.error ?: "搜索失败",
                        onRetry = { viewModel.searchNow() },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.hasSearched && uiState.results.isEmpty() -> {
                    EmptyState(
                        message = "未找到相关法规，请尝试其他关键词",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                !uiState.hasSearched -> {
                    EmptyState(
                        message = "输入关键词开始搜索法律法规",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 16.dp,
                            vertical = 8.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.results, key = { it.id }) { law ->
                            LawCard(
                                law = law,
                                onClick = { onLawClick(law.id) }
                            )
                        }
                        if (uiState.isLoadingMore) {
                            item {
                                LoadingState()
                            }
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeDropdown(
    selected: LawType,
    onSelect: (LawType) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            textStyle = MaterialTheme.typography.bodySmall
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            LawType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.displayName) },
                    onClick = {
                        onSelect(type)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortDropdown(
    selected: SortOrder,
    onSelect: (SortOrder) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(),
            textStyle = MaterialTheme.typography.bodySmall
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            SortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(order.displayName) },
                    onClick = {
                        onSelect(order)
                        expanded = false
                    }
                )
            }
        }
    }
}
