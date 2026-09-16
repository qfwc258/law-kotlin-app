package com.law.app.ui.detail

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.webkit.DownloadListener
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.law.app.data.parser.LawWebParser
import com.law.app.util.Constants
import com.law.app.util.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 法规详情页 - 传统 Activity + WebView 实现
 *
 * 用 TVBox 模式获取 OFD 阅读器 URL，然后用 WebView 加载。
 */
class LawDetailActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var loadingText: TextView
    private lateinit var loadingContainer: FrameLayout
    private var lawId: String = ""
    private var lawTitle: String = "法规详情"
    private val scope = CoroutineScope(Dispatchers.Main)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 获取参数
        lawId = intent.getStringExtra(EXTRA_LAW_ID) ?: ""
        lawTitle = intent.getStringExtra(EXTRA_LAW_TITLE) ?: "法规详情"

        // 设置标题栏
        supportActionBar?.apply {
            title = lawTitle
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        // 创建根布局
        val rootLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // 进度条（顶部）
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                12
            ).apply {
                gravity = Gravity.TOP
            }
            max = 100
            visibility = View.GONE
        }

        // 创建 WebView
        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
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
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    progressBar.visibility = View.VISIBLE
                    loadingContainer.visibility = View.GONE
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    progressBar.visibility = View.GONE
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
                    progressBar.progress = newProgress
                }
            }

            // 下载拦截
            setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
                try {
                    val request = DownloadManager.Request(Uri.parse(url))
                    request.setMimeType(mimeType)
                    request.setTitle("法规文件下载")
                    request.setDescription("正在下载…")
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    request.setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        "法规宝典/${lawTitle}.${if (mimeType.contains("pdf")) "pdf" else "docx"}"
                    )
                    val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    dm.enqueue(request)
                    Toast.makeText(this@LawDetailActivity, "开始下载…", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@LawDetailActivity, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }

        // 加载中提示
        loadingContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.WHITE)
        }

        val loadingInner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val loadingCircle = ProgressBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                80,
                80
            )
        }

        loadingText = TextView(this).apply {
            text = "正在加载法规详情…"
            textSize = 16f
            setTextColor(android.graphics.Color.parseColor("#666666"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 24
            }
        }

        loadingInner.addView(loadingCircle)
        loadingInner.addView(loadingText)
        loadingContainer.addView(loadingInner)

        rootLayout.addView(webView)
        rootLayout.addView(progressBar)
        rootLayout.addView(loadingContainer)
        setContentView(rootLayout)

        // 加载详情
        if (lawId.isNotBlank()) {
            loadDetail()
        } else {
            Toast.makeText(this, "无效的法规ID", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    /**
     * 用 TVBox 模式获取 OFD 阅读器 URL，然后加载
     */
    private fun loadDetail() {
        scope.launch {
            try {
                loadingText.text = "正在获取详情数据…"

                // 用 LawWebParser 获取详情
                val parser = LawWebParser.getInstance(this@LawDetailActivity)
                val detailResult = withContext(Dispatchers.IO) {
                    parser.getLawDetail(lawId)
                }

                when (detailResult) {
                    is Result.Success -> {
                        val law = detailResult.data
                        lawTitle = law.title
                        supportActionBar?.title = lawTitle

                        // 获取 OFD 阅读器 URL
                        val ossPdfPath = law.ossPdfPath
                        if (!ossPdfPath.isNullOrEmpty()) {
                            loadingText.text = "正在获取预览链接…"
                            val previewResult = withContext(Dispatchers.IO) {
                                parser.getPreviewUrl(ossPdfPath)
                            }
                            when (previewResult) {
                                is Result.Success -> {
                                    // 加载 OFD 阅读器
                                    webView.loadUrl(previewResult.data)
                                }
                                is Result.Error -> {
                                    // 获取预览链接失败，加载网站详情页作为兜底
                                    loadingText.text = "预览链接获取失败，加载网页版…"
                                    val fallbackUrl = "${Constants.OFFICIAL_URL}detail?bbbs=$lawId"
                                    webView.loadUrl(fallbackUrl)
                                }
                                is Result.Loading -> {}
                            }
                        } else {
                            // 没有 PDF 路径，加载网站详情页
                            loadingText.text = "正在加载网页版…"
                            val fallbackUrl = "${Constants.OFFICIAL_URL}detail?bbbs=$lawId"
                            webView.loadUrl(fallbackUrl)
                        }
                    }
                    is Result.Error -> {
                        // 获取详情失败，加载网站详情页作为兜底
                        loadingText.text = "详情获取失败，加载网页版…"
                        val fallbackUrl = "${Constants.OFFICIAL_URL}detail?bbbs=$lawId"
                        webView.loadUrl(fallbackUrl)
                    }
                    is Result.Loading -> {}
                }
            } catch (e: Exception) {
                Toast.makeText(this@LawDetailActivity, "加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
                // 兜底：加载网站详情页
                val fallbackUrl = "${Constants.OFFICIAL_URL}detail?bbbs=$lawId"
                webView.loadUrl(fallbackUrl)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_LAW_ID = "law_id"
        const val EXTRA_LAW_TITLE = "law_title"

        /**
         * 启动详情页
         */
        fun start(context: Context, lawId: String, lawTitle: String = "法规详情") {
            val intent = Intent(context, LawDetailActivity::class.java).apply {
                putExtra(EXTRA_LAW_ID, lawId)
                putExtra(EXTRA_LAW_TITLE, lawTitle)
            }
            context.startActivity(intent)
        }
    }
}
