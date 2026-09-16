package com.law.app.ui.detail

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.MenuItem
import android.view.View
import android.webkit.DownloadListener
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.law.app.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 法规详情页 - 传统 Activity + WebView 实现
 *
 * 先调用 previewLink API 获取 OFD 阅读器 URL，
 * 然后用 WebView 直接加载 OFD 阅读器（跳过网站其他元素）。
 */
class LawDetailActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
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

        // 创建布局
        val rootLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // 进度条
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            max = 100
            visibility = View.GONE
        }

        // 创建 WebView
        webView = WebView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
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

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    if (!title.isNullOrBlank() && title != "about:blank") {
                        supportActionBar?.title = title
                    }
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

        rootLayout.addView(progressBar)
        rootLayout.addView(webView)
        setContentView(rootLayout)

        // 加载 OFD 阅读器
        if (lawId.isNotBlank()) {
            loadOFDReader()
        } else {
            Toast.makeText(this, "无效的法规ID", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    /**
     * 调用 previewLink API 获取 OFD 阅读器 URL，然后加载
     */
    private fun loadOFDReader() {
        scope.launch {
            try {
                // 先加载网站详情页作为兜底
                val fallbackUrl = "${Constants.OFFICIAL_URL}detail?bbbs=$lawId"
                webView.loadUrl(fallbackUrl)

                // 异步获取 OFD 阅读器 URL
                val ofdUrl = withContext(Dispatchers.IO) {
                    fetchOFDReaderUrl(lawId)
                }

                if (ofdUrl != null) {
                    // 加载 OFD 阅读器
                    webView.post {
                        webView.loadUrl(ofdUrl)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@LawDetailActivity, "加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 调用 previewLink API 获取 OFD 阅读器 URL
     */
    private fun fetchOFDReaderUrl(bbbs: String): String? {
        return try {
            // 先获取详情，拿到 ossPdfPath
            val detailUrl = "${Constants.OFFICIAL_URL}law-search/search/flfgDetails?bbbs=$bbbs"
            val detailJson = fetchJson(detailUrl) ?: return null
            val detailObj = JSONObject(detailJson)
            val data = detailObj.optJSONObject("data") ?: return null
            val ossFile = data.optJSONObject("ossFile") ?: return null
            val ossPdfPath = ossFile.optString("ossPdfPath", "")
            if (ossPdfPath.isEmpty()) return null

            // 调用 previewLink API 获取 OFD 阅读器 URL
            val previewUrl = "${Constants.OFFICIAL_URL}law-search/amazonFile/previewLink?filePath=$ossPdfPath&fileType=pdf"
            val previewJson = fetchJson(previewUrl) ?: return null
            val previewObj = JSONObject(previewJson)
            val code = previewObj.optInt("code", -1)
            if (code != 200) return null
            val previewData = previewObj.optJSONObject("data") ?: return null
            previewData.optString("url", "")
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 发送 GET 请求获取 JSON
     */
    private fun fetchJson(urlString: String): String? {
        return try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
            conn.setRequestProperty("Referer", Constants.OFFICIAL_URL)
            conn.setRequestProperty("Origin", Constants.OFFICIAL_URL)
            conn.setRequestProperty("Accept", "application/json")

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                conn.disconnect()
                return null
            }

            val inputStream = conn.inputStream
            val response = inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            response
        } catch (e: Exception) {
            null
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
