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
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.law.app.util.Constants

/**
 * 法规详情页 - 传统 Activity + WebView 实现
 *
 * 直接加载国家法律法规数据库详情页，
 * 网站会自动加载 OFD 阅读器渲染 PDF 内容。
 */
class LawDetailActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var loadingText: TextView
    private lateinit var loadingContainer: FrameLayout
    private var lawId: String = ""
    private var lawTitle: String = "法规详情"

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
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                textZoom = 100
                cacheMode = WebSettings.LOAD_DEFAULT
                userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    progressBar.visibility = View.VISIBLE
                    loadingContainer.visibility = View.GONE
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    progressBar.visibility = View.GONE
                    // 等待 OFD 加载完成后再注入 JS，避免白屏
                    // 轮询检测 id 为 previewIframe 的 iframe 是否存在
                    waitForOfdAndInject(view, 0)
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

        // 启用 Cookie（webView 已初始化后调用）
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
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

        // 直接加载网站详情页
        if (lawId.isNotBlank()) {
            // 使用正确的 URL 参数：title 和 id（不是 bbbs）
            val encodedTitle = java.net.URLEncoder.encode(lawTitle, "UTF-8")
            val url = "${Constants.OFFICIAL_URL}detail?title=$encodedTitle&id=$lawId"
            webView.loadUrl(url)
        } else {
            Toast.makeText(this, "无效的法规ID", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    /**
     * 等待 OFD 加载完成后再注入 JS，避免白屏
     * 轮询检测 id 为 previewIframe 的 iframe 是否存在
     */
    private fun waitForOfdAndInject(view: WebView?, retryCount: Int) {
        if (view == null) return
        
        // 最多轮询 30 次（每次 500ms，总共 15 秒）
        if (retryCount >= 30) {
            // 超时后仍然注入 JS（即使 OFD 没加载完）
            injectReaderOnlyMode(view)
            return
        }
        
        // 检测 previewIframe 是否存在
        val checkJs = """
            (function() {
                var iframe = document.getElementById('previewIframe');
                if (iframe && iframe.src && iframe.src.length > 0) {
                    return 'ready';
                }
                return 'not-ready';
            })()
        """.trimIndent()
        
        view.evaluateJavascript(checkJs) { result ->
            if (result == "\"ready\"") {
                // OFD iframe 已存在，再等待 1 秒确保内容加载完成，然后注入 JS
                view.postDelayed({
                    injectReaderOnlyMode(view)
                }, 1000)
            } else {
                // OFD iframe 还没加载，500ms 后重试
                view.postDelayed({
                    waitForOfdAndInject(view, retryCount + 1)
                }, 500)
            }
        }
    }
    
    /**
     * 注入 JS，隐藏网站多余元素，只显示 WPS 内容区域
     *
     * 智能定位 WPS 内容区域：
     * 1. 优先找 iframe 和 canvas（WPS 在线预览）
     * 2. 其次找包含法规特征文字的最大元素（"第X条"、"目录"、页码等）
     * 3. 最后找页面中面积最大的 div
     */
    private fun injectReaderOnlyMode(view: WebView) {
        val js = """
            (function() {
                try {
                    window.__tryReaderOnlyMode = function() {
                        try {
                            var reader = null;
                            var readerType = '';
                            
                            // 0. 优先通过 id 找到 OFD 阅读器 iframe（已知 id 为 previewIframe）
                            var previewIframe = document.getElementById('previewIframe');
                            if (previewIframe) {
                                reader = previewIframe;
                                readerType = 'previewIframe';
                            }
                            
                            // 1. 优先找 iframe（WPS 在线预览通常是 iframe）
                            if (!reader) {
                            var iframes = document.querySelectorAll('iframe');
                            if (iframes.length > 0) {
                                var maxArea = 0;
                                for (var i = 0; i < iframes.length; i++) {
                                    var area = iframes[i].offsetWidth * iframes[i].offsetHeight;
                                    if (area > maxArea && area > 5000) {
                                        maxArea = area;
                                        reader = iframes[i];
                                        readerType = 'iframe';
                                    }
                                }
                            }
                            }
                            
                            // 2. 其次找 canvas（OFD 阅读器通常用 canvas）
                            if (!reader) {
                                var canvases = document.querySelectorAll('canvas');
                                if (canvases.length > 0) {
                                    var maxArea2 = 0;
                                    for (var j = 0; j < canvases.length; j++) {
                                        var area2 = canvases[j].offsetWidth * canvases[j].offsetHeight;
                                        if (area2 > maxArea2 && area2 > 5000) {
                                            maxArea2 = area2;
                                            reader = canvases[j];
                                            readerType = 'canvas';
                                        }
                                    }
                                }
                            }
                            
                            // 3. 找包含法规特征文字的最大元素
                            if (!reader) {
                                var allElements = document.querySelectorAll('div, section, article');
                                var maxArea3 = 0;
                                for (var k = 0; k < allElements.length; k++) {
                                    var el = allElements[k];
                                    var area3 = el.offsetWidth * el.offsetHeight;
                                    var text = el.textContent || '';
                                    // 检查是否包含法规特征：第X条、第X章、目录、页码格式(数字/数字)
                                    var hasLawFeature = /第[一二三四五六七八九十百千0-9]+[条章节篇编]/.test(text) || 
                                                        text.indexOf('目录') >= 0 || 
                                                        /\d+\s*\/\s*\d+/.test(text);
                                    if (area3 > maxArea3 && area3 > window.innerWidth * window.innerHeight * 0.15 && hasLawFeature) {
                                        maxArea3 = area3;
                                        reader = el;
                                        readerType = 'law-text';
                                    }
                                }
                            }
                            
                            // 4. 最后找页面中面积最大的 div
                            if (!reader) {
                                var allDivs = document.querySelectorAll('div');
                                var maxArea4 = 0;
                                for (var l = 0; l < allDivs.length; l++) {
                                    var el2 = allDivs[l];
                                    var area4 = el2.offsetWidth * el2.offsetHeight;
                                    if (area4 > maxArea4 && area4 > window.innerWidth * window.innerHeight * 0.3) {
                                        maxArea4 = area4;
                                        reader = el2;
                                        readerType = 'largest-div';
                                    }
                                }
                            }
                            
                            if (!reader) {
                                // 没有找到，1秒后重试（最多重试20次）
                                if (!window.__readerRetryCount) window.__readerRetryCount = 0;
                                if (window.__readerRetryCount < 20) {
                                    window.__readerRetryCount++;
                                    setTimeout(window.__tryReaderOnlyMode, 1000);
                                }
                                return;
                            }
                            
                            // 已经处理过，不重复处理
                            if (window.__readerOnlyModeApplied) return;
                            window.__readerOnlyModeApplied = true;
                            
                            // 找到从 body 到 reader 的路径
                            var path = [];
                            var current = reader;
                            while (current && current !== document.body) {
                                path.unshift(current);
                                current = current.parentElement;
                            }
                            
                            // 从 body 开始，只保留路径上的元素，隐藏其他兄弟元素
                            function keepOnlyPath(parent, pathIndex) {
                                if (pathIndex >= path.length) return;
                                var target = path[pathIndex];
                                var children = parent.children;
                                for (var i = 0; i < children.length; i++) {
                                    var child = children[i];
                                    if (child === target) {
                                        keepOnlyPath(child, pathIndex + 1);
                                    } else {
                                        child.style.display = 'none';
                                    }
                                }
                            }
                            keepOnlyPath(document.body, 0);
                            
                            // 设置 body 和 html 为全屏
                            document.documentElement.style.margin = '0';
                            document.documentElement.style.padding = '0';
                            document.documentElement.style.overflow = 'hidden';
                            document.body.style.margin = '0';
                            document.body.style.padding = '0';
                            document.body.style.overflow = 'hidden';
                            document.body.style.background = '#fff';
                            
                            // 设置路径上所有元素为全屏宽度
                            for (var m = 0; m < path.length; m++) {
                                var el = path[m];
                                el.style.width = '100%';
                                el.style.maxWidth = '100%';
                                el.style.margin = '0';
                                el.style.padding = '0';
                                el.style.boxSizing = 'border-box';
                                el.style.overflow = 'auto';
                            }
                            
                            // 设置 reader 为全屏
                            reader.style.width = '100%';
                            reader.style.height = '100vh';
                            reader.style.maxWidth = '100%';
                            reader.style.display = 'block';
                            reader.style.border = 'none';
                            
                            // 适配手机宽度：如果是 iframe，直接设置全屏；否则遍历内部元素缩放
                            setTimeout(function() {
                                try {
                                    if (reader.tagName === 'IFRAME') {
                                        // iframe 是跨域的 OFD 阅读器，用 CSS transform 缩放父容器
                                        // 缩放比例：OFD内容约1000px宽，手机屏幕约360-412px，缩放约0.4
                                        var scale = 0.4;
                                        
                                        // 找到 iframe 的直接父容器
                                        var iframeParent = reader.parentElement;
                                        
                                        if (iframeParent) {
                                            // 设置父容器为相对定位，便于缩放
                                            iframeParent.style.position = 'relative';
                                            iframeParent.style.width = '100%';
                                            iframeParent.style.overflow = 'hidden';
                                            
                                            // 用 CSS transform 缩放父容器（包括内部的 iframe）
                                            iframeParent.style.transform = 'scale(' + scale + ')';
                                            iframeParent.style.transformOrigin = 'top left';
                                            
                                            // 计算缩放后的高度，设置父容器的高度
                                            var originalHeight = iframeParent.offsetHeight;
                                            var scaledHeight = originalHeight * scale;
                                            iframeParent.style.height = scaledHeight + 'px';
                                            
                                            // 给父容器添加一个占位兄弟元素，撑起缩放后的高度
                                            if (!iframeParent.nextElementSibling || !iframeParent.nextElementSibling.classList.contains('scale-placeholder')) {
                                                var placeholder = document.createElement('div');
                                                placeholder.className = 'scale-placeholder';
                                                placeholder.style.height = scaledHeight + 'px';
                                                placeholder.style.width = '100%';
                                                placeholder.style.pointerEvents = 'none';
                                                iframeParent.parentNode.insertBefore(placeholder, iframeParent.nextSibling);
                                            } else {
                                                iframeParent.nextElementSibling.style.height = scaledHeight + 'px';
                                            }
                                        }
                                        
                                        // 设置 iframe 本身的样式
                                        reader.style.width = '100%';
                                        reader.style.height = '100vh';
                                        reader.style.minHeight = '100vh';
                                        reader.style.border = 'none';
                                        reader.style.display = 'block';
                                        reader.style.margin = '0';
                                        reader.style.padding = '0';
                                        
                                        // 确保 iframe 的所有祖先元素也是全屏宽度
                                        var ancestor = reader.parentElement;
                                        while (ancestor && ancestor !== document.body) {
                                            ancestor.style.width = '100%';
                                            ancestor.style.margin = '0';
                                            ancestor.style.padding = '0';
                                            ancestor = ancestor.parentElement;
                                        }
                                    } else {
                                        // 非 iframe 情况：遍历 reader 内所有元素，找到宽度大于屏幕的元素并缩放
                                        var allElements = reader.querySelectorAll('*');
                                        var scaledCount = 0;
                                        
                                        for (var i = 0; i < allElements.length; i++) {
                                            var el = allElements[i];
                                            // 只处理有实际宽度且大于屏幕宽度的元素
                                            if (el.offsetWidth > window.innerWidth && el.offsetHeight > 50) {
                                                // 计算缩放比例（留10px边距）
                                                var scale = (window.innerWidth - 10) / el.offsetWidth;
                                                
                                                // 用 transform 缩放
                                                el.style.transform = 'scale(' + scale + ')';
                                                el.style.transformOrigin = 'top center';
                                                el.style.margin = '0 auto';
                                                el.style.display = 'block';
                                                
                                                // 计算缩放后的高度
                                                var scaledHeight = el.offsetHeight * scale;
                                                
                                                // 给元素添加一个占位兄弟元素，撑起缩放后的高度
                                                if (!el.nextElementSibling || !el.nextElementSibling.classList.contains('scale-placeholder')) {
                                                    var placeholder = document.createElement('div');
                                                    placeholder.className = 'scale-placeholder';
                                                    placeholder.style.height = scaledHeight + 'px';
                                                    placeholder.style.width = '100%';
                                                    placeholder.style.pointerEvents = 'none';
                                                    el.parentNode.insertBefore(placeholder, el.nextSibling);
                                                } else {
                                                    el.nextElementSibling.style.height = scaledHeight + 'px';
                                                }
                                                
                                                scaledCount++;
                                            }
                                        }
                                    }
                                    
                                    // 确保 reader 可以滚动
                                    reader.style.overflow = 'auto';
                                    reader.style.webkitOverflowScrolling = 'touch';
                                    
                                    // 触发 resize 事件
                                    window.dispatchEvent(new Event('resize'));
                                    
                                } catch(e) {
                                    console.log('adapt mobile width error:', e);
                                }
                            }, 2000);
                            
                            // 添加 viewport meta 标签
                            var viewport = document.querySelector('meta[name="viewport"]');
                            if (!viewport) {
                                viewport = document.createElement('meta');
                                viewport.setAttribute('name', 'viewport');
                                document.head.appendChild(viewport);
                            }
                            viewport.setAttribute('content', 'width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes');
                            
                            // 延迟触发 resize 事件
                            setTimeout(function() {
                                window.dispatchEvent(new Event('resize'));
                            }, 500);
                            
                        } catch(e) {
                            console.log('readerOnlyMode error:', e);
                        }
                    };
                    
                    window.__tryReaderOnlyMode();
                    
                } catch(e) {
                    console.log('injectReaderOnlyMode error:', e);
                }
            })();
        """.trimIndent()

        view.evaluateJavascript(js, null)
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
