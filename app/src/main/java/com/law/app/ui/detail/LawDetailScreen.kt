package com.law.app.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.law.app.ui.webview.LawWebViewScreen
import com.law.app.util.Constants

/**
 * 法规详情页 - WebView 加载国家法律法规数据库详情页
 *
 * 网站详情页包含：
 * - 法规基本信息（发布机关、公布日期、施行日期等）
 * - 完整法条正文
 * - PDF/Word 下载功能
 * - 目录导航
 *
 * 所有内容由网站渲染，已适配手机屏幕。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LawDetailScreen(
    lawId: String,
    onBack: () -> Unit
) {
    var pageTitle by remember { mutableStateOf("法规详情") }

    // 构造详情页 URL
    val detailUrl = "${Constants.OFFICIAL_URL}detail?bbbs=$lawId"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = pageTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
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
            LawWebViewScreen(
                url = detailUrl,
                modifier = Modifier.fillMaxSize(),
                onPageFinished = { url ->
                    // 页面加载完成后，可以通过 JavaScript 获取标题
                    // 这里保持默认标题，WebView 内会显示网站自己的标题
                }
            )
        }
    }
}
