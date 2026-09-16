package com.law.app.ui.detail

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.DownloadListener
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.law.app.ui.common.ErrorState
import com.law.app.ui.common.LoadingState
import com.law.app.ui.common.StatusBadge
import com.law.app.ui.common.TypeBadge
import com.law.app.ui.theme.Favorite
import com.law.app.util.Constants
import com.law.app.util.DownloadState

/**
 * 法规详情页 - 原生头部 + WebView 正文（移动端 CSS 优化）
 *
 * 顶部原生 AppBar + 基本信息卡片 + 操作栏，
 * 下方 WebView 加载法规详情页并注入移动端 CSS，
 * 支持下载、复制全文、目录导航。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LawDetailScreen(
    lawId: String,
    onBack: () -> Unit,
    viewModel: LawDetailViewModel = viewModel(factory = LawDetailViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var isContentLoading by remember { mutableStateOf(true) }
    var contentProgress by remember { mutableStateOf(0) }
    var showCatalogSheet by remember { mutableStateOf(false) }
    val catalogSheetState = rememberModalBottomSheetState()

    // 目录项（在顶层计算，避免在 if 块内使用 remember 导致崩溃）
    val catalogItems = remember(uiState.law?.contentTreeJson) {
        uiState.law?.contentTreeJson?.let { parseCatalogItems(it) } ?: emptyList()
    }

    LaunchedEffect(lawId) {
        viewModel.loadLaw(lawId)
    }

    // 监听预览 URL 变化，加载 OFD 阅读器（在顶层执行，避免在条件分支内导致问题）
    LaunchedEffect(uiState.previewUrl) {
        val url = uiState.previewUrl
        if (!url.isNullOrEmpty()) {
            webView?.loadUrl(url)
        }
    }

    // WebView 生命周期管理
    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.law?.title ?: "法规详情",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                    IconButton(onClick = {
                        // 复制链接
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("法规链接", "${Constants.OFFICIAL_URL}detail?bbbs=$lawId")
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "链接已复制", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "复制链接")
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
                    uiState.law?.let { law ->
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 基本信息卡片
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = law.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TypeBadge(type = law.type)
                                    StatusBadge(status = law.status)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                if (law.issuingAuthority.isNotBlank()) {
                                    MetaInfoRow("发布机关", law.issuingAuthority)
                                }
                                if (law.publishDate.isNotBlank()) {
                                    MetaInfoRow("公布日期", law.publishDate)
                                }
                                if (law.effectiveDate.isNotBlank()) {
                                    MetaInfoRow("施行日期", law.effectiveDate)
                                }
                            }
                        }

                        // 操作栏
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 下载 PDF
                            AssistChip(
                                onClick = {
                                    if (uiState.pdfDownloadState is DownloadState.Completed) {
                                        viewModel.openPdf()
                                    } else {
                                        viewModel.downloadPdf()
                                    }
                                },
                                label = {
                                    Text(
                                        text = when (uiState.pdfDownloadState) {
                                            is DownloadState.Downloading -> "下载中 ${(uiState.pdfDownloadState as DownloadState.Downloading).progress}%"
                                            is DownloadState.Completed -> "打开 PDF"
                                            is DownloadState.Failed -> "重试下载"
                                            else -> "下载 PDF"
                                        },
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (uiState.pdfDownloadState is DownloadState.Completed)
                                            Icons.Default.PictureAsPdf else Icons.Default.Download,
                                        contentDescription = null,
                                        tint = if (uiState.pdfDownloadState is DownloadState.Completed)
                                            MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (uiState.pdfDownloadState is DownloadState.Completed)
                                        MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            // 复制全文（OFD 预览暂不支持文本提取，提示下载后复制）
                            AssistChip(
                                onClick = {
                                    Toast.makeText(
                                        context,
                                        "OFD 预览暂不支持复制全文，请下载 PDF 后复制",
                                        Toast.LENGTH_LONG
                                    ).show()
                                },
                                label = { Text("复制全文", style = MaterialTheme.typography.labelLarge) },
                                leadingIcon = {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                                },
                                modifier = Modifier.weight(1f)
                            )

                            // 目录
                            AssistChip(
                                onClick = { showCatalogSheet = true },
                                label = { Text("目录", style = MaterialTheme.typography.labelLarge) },
                                leadingIcon = {
                                    Icon(Icons.Default.List, contentDescription = null)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // 下载进度条
                        if (uiState.pdfDownloadState is DownloadState.Downloading) {
                            LinearProgressIndicator(
                                progress = { (uiState.pdfDownloadState as DownloadState.Downloading).progress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        // WebView 正文区域
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                        settings.apply {
                                            javaScriptEnabled = true
                                            domStorageEnabled = true
                                            databaseEnabled = true
                                            loadWithOverviewMode = true
                                            useWideViewPort = true
                                            setSupportZoom(true)
                                            builtInZoomControls = true
                                            displayZoomControls = false
                                            mediaPlaybackRequiresUserGesture = false
                                            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                            textZoom = 100
                                            userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36 LawApp/1.0"
                                        }

                                        webViewClient = object : WebViewClient() {
                                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                                isContentLoading = true
                                                contentProgress = 0
                                            }

                                            override fun onPageFinished(view: WebView?, url: String?) {
                                                isContentLoading = false
                                            }

                                            override fun shouldOverrideUrlLoading(
                                                view: WebView?,
                                                request: WebResourceRequest?
                                            ): Boolean {
                                                return false
                                            }
                                        }

                                        webChromeClient = object : WebChromeClient() {
                                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                                contentProgress = newProgress
                                            }
                                        }

                                        // 下载拦截
                                        setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
                                            // 用系统下载管理器下载
                                            val request = android.app.DownloadManager.Request(android.net.Uri.parse(url))
                                            request.setMimeType(mimeType)
                                            request.setTitle("法规文件下载")
                                            request.setDescription("正在下载…")
                                            request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                            request.setDestinationInExternalPublicDir(
                                                android.os.Environment.DIRECTORY_DOWNLOADS,
                                                "法规宝典/${law.title}.pdf"
                                            )
                                            val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                            dm.enqueue(request)
                                            Toast.makeText(ctx, "开始下载…", Toast.LENGTH_SHORT).show()
                                        })

                                        webView = this

                                        // WebView 创建后，如果预览 URL 已存在，立即加载
                                        post {
                                            val url = uiState.previewUrl
                                            if (!url.isNullOrEmpty()) {
                                                loadUrl(url)
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            // 预览加载中
                            if (uiState.isPreviewLoading && uiState.previewUrl == null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .align(Alignment.Center),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator()
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "正在加载法规预览…",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // 内容加载进度条
                            if (isContentLoading && contentProgress in 1..99) {
                                LinearProgressIndicator(
                                    progress = { contentProgress / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.TopCenter),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            // 初始加载圈
                            if (isContentLoading && contentProgress == 0) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .padding(16.dp)
                                )
                            }
                        }
                    }
                    } // end of let
                }
            }
        }
    }

    // 目录抽屉
    if (showCatalogSheet && catalogItems.isNotEmpty()) {
        ModalBottomSheet(
            onDismissRequest = { showCatalogSheet = false },
            sheetState = catalogSheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "目录",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(catalogItems, key = { it.title + it.depth }) { item ->
                        Text(
                            text = item.title,
                            style = if (item.isArticle) {
                                MaterialTheme.typography.bodyMedium
                            } else {
                                MaterialTheme.typography.titleSmall
                            },
                            color = if (item.isArticle) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            fontWeight = if (item.isArticle) FontWeight.Normal else FontWeight.SemiBold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = (item.depth * 16).dp, top = 8.dp, bottom = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 目录项数据
 */
private data class CatalogItemData(
    val title: String,
    val depth: Int,
    val isArticle: Boolean
)

/**
 * 从目录树 JSON 解析出扁平化的目录列表（使用 org.json，避免 Gson 问题）
 */
private fun parseCatalogItems(json: String): List<CatalogItemData> {
    return try {
        val root = org.json.JSONObject(json)
        val result = mutableListOf<CatalogItemData>()
        fun traverse(node: org.json.JSONObject, depth: Int) {
            val title = node.optString("title", "")
            if (depth > 0 && title.isNotEmpty()) {
                val isArticle = title.startsWith("第") && title.contains("条")
                result.add(CatalogItemData(title, depth, isArticle))
            }
            val children = node.optJSONArray("children")
            if (children != null) {
                for (i in 0 until children.length()) {
                    traverse(children.getJSONObject(i), depth + 1)
                }
            }
        }
        traverse(root, 0)
        result
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * 基本信息行
 */
@Composable
private fun MetaInfoRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}
