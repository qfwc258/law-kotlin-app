package com.law.app.ui.detail

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.law.app.data.model.Article
import com.law.app.ui.common.ErrorState
import com.law.app.ui.common.LoadingState
import com.law.app.ui.common.TypeBadge
import com.law.app.ui.theme.Favorite
import com.law.app.util.DownloadState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LawDetailScreen(
    lawId: String,
    onBack: () -> Unit,
    viewModel: LawDetailViewModel = viewModel(factory = LawDetailViewModel.Factory)
) {
    val uiState by viewModel.uiState

    LaunchedEffect(lawId) {
        viewModel.loadLaw(lawId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.law?.title ?: "法规详情",
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFavorite() }) {
                        Icon(
                            imageVector = if (uiState.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "收藏",
                            tint = if (uiState.isFavorite) Favorite else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { /* 分享 */ }) {
                        Icon(Icons.Default.Share, contentDescription = "分享")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoading -> {
                    LoadingState(modifier = Modifier.align(Alignment.Center))
                }
                uiState.error != null && uiState.law == null -> {
                    ErrorState(
                        message = uiState.error ?: "加载失败",
                        onRetry = { viewModel.loadLaw(lawId) },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.law != null -> {
                    val law = uiState.law!!
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
                    ) {
                        // 标题区
                        item {
                            Text(
                                text = law.title,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TypeBadge(type = law.type)
                                if (law.lawLevel.isNotBlank()) {
                                    Text(
                                        text = law.lawLevel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // 元信息
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            MetaInfoRow(label = "发布机关", value = law.issuingAuthority)
                            if (law.documentNumber.isNotBlank()) {
                                MetaInfoRow(label = "文号", value = law.documentNumber)
                            }
                            if (law.publishDate.isNotBlank()) {
                                MetaInfoRow(label = "公布日期", value = law.publishDate)
                            }
                            if (law.effectiveDate.isNotBlank()) {
                                MetaInfoRow(label = "施行日期", value = law.effectiveDate)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            androidx.compose.material3.Divider()
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // 下载原文
                        item {
                            Text(
                                text = "下载原文",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                DownloadChip(
                                    label = "PDF",
                                    state = uiState.pdfDownloadState,
                                    onClick = {
                                        if (uiState.pdfDownloadState is DownloadState.Completed) {
                                            viewModel.openPdf()
                                        } else {
                                            viewModel.downloadPdf()
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                DownloadChip(
                                    label = "WPS",
                                    state = uiState.wpsDownloadState,
                                    onClick = {
                                        if (uiState.wpsDownloadState is DownloadState.Completed) {
                                            viewModel.openWps()
                                        } else {
                                            viewModel.downloadWps()
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "文件保存至：下载/法规宝典/",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            androidx.compose.material3.Divider()
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // 摘要
                        if (law.summary.isNotBlank()) {
                            item {
                                Text(
                                    text = "摘要",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = law.summary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                androidx.compose.material3.Divider()
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        // 正文标题
                        item {
                            Text(
                                text = "正文",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // 法条列表
                        if (uiState.articles.isNotEmpty()) {
                            items(uiState.articles, key = { it.number + it.text.hashCode() }) { article ->
                                ArticleItem(article = article)
                            }
                        } else if (law.content.isNotBlank()) {
                            // 无法切分条文时显示全文
                            item {
                                Text(
                                    text = law.content,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        } else {
                            item {
                                Text(
                                    text = "正文内容暂不可用，可访问国家法律法规数据库查看原文。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(32.dp))
                            Text(
                                text = "数据来源：国家法律法规数据库 (flk.npc.gov.cn)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaInfoRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ArticleItem(article: Article) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        if (article.number.isNotBlank()) {
            Text(
                text = article.number,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        Text(
            text = article.text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = androidx.compose.ui.unit.TextUnit(28f, androidx.compose.ui.unit.TextUnitType.Sp)
        )
    }
}

/**
 * 下载按钮 Chip：根据下载状态显示不同 UI
 * - Idle：显示"下载 PDF/WPS"
 * - Downloading/Paused：显示进度条
 * - Completed：显示"打开"
 * - Failed：显示错误信息，可重试
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadChip(
    label: String,
    state: DownloadState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        AssistChip(
            onClick = onClick,
            label = {
                Text(
                    text = when (state) {
                        is DownloadState.Idle -> "下载 $label"
                        is DownloadState.Downloading -> "下载中 ${state.progress}%"
                        is DownloadState.Paused -> "已暂停 ${state.progress}%"
                        is DownloadState.Completed -> "打开 $label"
                        is DownloadState.Failed -> "重试下载"
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = when (state) {
                        is DownloadState.Completed -> Icons.Default.DownloadDone
                        else -> Icons.Default.Download
                    },
                    contentDescription = null,
                    tint = when (state) {
                        is DownloadState.Completed -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = when (state) {
                    is DownloadState.Completed -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // 进度条
        when (state) {
            is DownloadState.Downloading -> {
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            is DownloadState.Paused -> {
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is DownloadState.Failed -> {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2
                )
            }
            else -> {}
        }
    }
}
