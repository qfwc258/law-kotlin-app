package com.law.app.ui.webview

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 通用法规 WebView 页面
 *
 * 用于加载国家法律法规数据库网站，适配手机屏幕。
 * 支持：
 * - 页面加载进度显示
 * - 返回键处理（WebView 内回退）
 * - 下拉刷新（通过 SwipeRefresh 可选）
 * - 错误页面处理
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LawWebViewScreen(
    url: String,
    modifier: Modifier = Modifier,
    onPageStarted: (() -> Unit)? = null,
    onPageFinished: ((String) -> Unit)? = null
) {
    var isLoading by remember { mutableStateOf(true) }
    var progress by remember { mutableStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    // 返回键处理：如果 WebView 可以回退，则回退，否则由系统处理
    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    // WebView 设置
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
                        cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                        // 移动端 User-Agent
                        userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36 LawApp/1.0"
                    }

                    // WebViewClient：处理页面加载和导航
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            isLoading = true
                            progress = 0
                            onPageStarted?.invoke()
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                            progress = 100
                            canGoBack = view?.canGoBack() ?: false
                            if (url != null) {
                                onPageFinished?.invoke(url)
                            }
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            // 所有链接都在 WebView 内打开，不跳转到外部浏览器
                            return false
                        }
                    }

                    // WebChromeClient：处理进度和标题
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress
                        }
                    }

                    // 加载初始 URL
                    loadUrl(url)
                    webView = this
                }
            },
            update = { view ->
                // URL 变化时重新加载
                if (view.url != url && !isLoading) {
                    view.loadUrl(url)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 加载进度条
        if (isLoading && progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
            )
        }

        // 初始加载时的加载圈
        if (isLoading && progress == 0) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
            )
        }
    }
}
