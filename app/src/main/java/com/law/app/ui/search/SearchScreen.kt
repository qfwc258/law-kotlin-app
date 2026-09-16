package com.law.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.law.app.data.model.Law
import com.law.app.data.model.LawType
import com.law.app.ui.common.ErrorState
import com.law.app.ui.common.LawCard
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * 搜索页 - 完全原生实现
 *
 * 顶部原生搜索框 + 筛选 Chip + 结果列表（LazyColumn），
 * 支持分页加载、防抖搜索、错误重试。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onLawClick: (String, String) -> Unit = { _, _ -> },
    initialKeyword: String = "",
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // 搜索框文本状态（用于控制光标位置）
    var searchText by remember {
        mutableStateOf(
            TextFieldValue(
                text = initialKeyword,
                selection = TextRange(initialKeyword.length)
            )
        )
    }

    // 如果有初始关键词，自动搜索
    LaunchedEffect(initialKeyword) {
        if (initialKeyword.isNotBlank()) {
            searchText = TextFieldValue(
                text = initialKeyword,
                selection = TextRange(initialKeyword.length)
            )
            viewModel.onKeywordChange(initialKeyword)
        }
    }

    // 监听列表滚动到底部，触发加载更多
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .filter { it != null && it >= uiState.results.size - 3 }
            .distinctUntilChanged()
            .collect {
                if (!uiState.isLoadingMore && !uiState.isLoading) {
                    viewModel.loadMore()
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // 顶部搜索框
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { newValue ->
                    searchText = newValue
                    viewModel.onKeywordChange(newValue.text)
                },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = "搜索法律法规…",
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "搜索",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchText.text.isNotEmpty()) {
                        IconButton(onClick = {
                            searchText = TextFieldValue("")
                            viewModel.onKeywordChange("")
                        }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "清除",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                textStyle = MaterialTheme.typography.bodyLarge
            )

            IconButton(onClick = { viewModel.searchNow() }) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "搜索",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // 筛选行：法规类型（与首页大类一致），使用 FlowRow 自动换行
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                LawType.ALL to "全部",
                LawType.LAW to "宪法",
                LawType.LAW to "法律",
                LawType.ADMIN to "行政法规",
                LawType.SUPERVISION to "监察法规",
                LawType.LOCAL to "地方法规",
                LawType.JUDICIAL to "司法解释"
            ).forEach { (type, label) ->
                val isSelected = uiState.selectedType == type
                AssistChip(
                    onClick = { viewModel.onTypeChange(type) },
                    label = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = if (isSelected)
                        AssistChipDefaults.assistChipBorder(
                            borderColor = MaterialTheme.colorScheme.primary
                        )
                    else
                        AssistChipDefaults.assistChipBorder()
                )
            }
        }

        // 结果区域
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                // 加载中
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "搜索中…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 错误
                uiState.error != null && uiState.results.isEmpty() -> {
                    ErrorState(
                        message = uiState.error ?: "搜索失败",
                        onRetry = { viewModel.searchNow() },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // 未搜索
                !uiState.hasSearched && searchText.text.isBlank() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(64.dp)
                                    .padding(bottom = 16.dp)
                            )
                            Text(
                                text = "输入关键词搜索法律法规",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "热门搜索",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            // 使用 FlowRow 避免标签竖排
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                listOf("民法典", "刑法", "劳动合同法", "宪法", "未成年人保护法").forEach { keyword ->
                                    AssistChip(
                                        onClick = {
                                            searchText = TextFieldValue(
                                                text = keyword,
                                                selection = TextRange(keyword.length)
                                            )
                                            viewModel.onKeywordChange(keyword)
                                        },
                                        label = { Text(keyword) }
                                    )
                                }
                            }
                        }
                    }
                }

                // 搜索结果为空
                uiState.hasSearched && uiState.results.isEmpty() && !uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "未找到相关法律法规",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 显示结果列表
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 16.dp,
                            vertical = 8.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 结果总数
                        item {
                            if (uiState.total > 0) {
                                Text(
                                    text = "共找到 ${uiState.total} 条结果",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }

                        // 结果列表
                        items(uiState.results, key = { it.id }) { law ->
                            LawCard(
                                law = law,
                                onClick = { onLawClick(law.id, law.title) }
                            )
                        }

                        // 加载更多
                        item {
                            if (uiState.isLoadingMore) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }

                        // 底部间距
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}
