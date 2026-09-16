package com.law.app.ui.detail

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
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
import java.io.File
import java.io.FileOutputStream

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

    /**
     * JavaScript 接口：用于接收 blob URL 转换后的 base64 数据并保存为文件
     */
    inner class BlobDownloadInterface {
        @JavascriptInterface
        fun onBlobData(base64Data: String, fileName: String, mimeType: String) {
            try {
                // 解码 base64 数据
                val data = Base64.decode(base64Data, Base64.DEFAULT)

                // 创建下载目录
                val downloadDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "法律宝典"
                )
                if (!downloadDir.exists()) {
                    downloadDir.mkdirs()
                }

                // 保存文件
                val file = File(downloadDir, fileName)
                FileOutputStream(file).use { it.write(data) }

                runOnUiThread {
                    Toast.makeText(
                        this@LawDetailActivity,
                        "下载完成: ${file.absolutePath}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this@LawDetailActivity,
                        "下载失败: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

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

            // 添加 JavaScript 接口，用于处理 blob URL 下载
            addJavascriptInterface(BlobDownloadInterface(), "BlobDownloader")

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
                    // 检测是否是 blob URL
                    if (url.startsWith("blob:")) {
                        // blob URL 需要通过 JavaScript 转换成 base64 再下载
                        // 从 contentDisposition 中提取文件名
                        var fileName = "$lawTitle.docx"
                        if (contentDisposition != null && contentDisposition.contains("filename")) {
                            val match = Regex("filename\\*?=([^;]+)").find(contentDisposition)
                            if (match != null) {
                                var name = match.groupValues[1].trim().removeSurrounding("\"")
                                // 处理 URL 编码的文件名
                                if (name.startsWith("UTF-8''")) {
                                    name = java.net.URLDecoder.decode(name.removePrefix("UTF-8''"), "UTF-8")
                                }
                                fileName = name
                            }
                        }

                        // 通过 JavaScript 把 blob 转换成 base64
                        val jsCode = """
                            (function() {
                                try {
                                    fetch('$url')
                                        .then(response => response.blob())
                                        .then(blob => {
                                            var reader = new FileReader();
                                            reader.onloadend = function() {
                                                var base64 = reader.result.split(',')[1];
                                                BlobDownloader.onBlobData(base64, '$fileName', '$mimeType');
                                            };
                                            reader.readAsDataURL(blob);
                                        })
                                        .catch(err => {
                                            console.error('Blob download error:', err);
                                        });
                                } catch(e) {
                                    console.error('Blob download exception:', e);
                                }
                            })();
                        """.trimIndent()

                        webView.post {
                            webView.evaluateJavascript(jsCode, null)
                        }

                        Toast.makeText(this@LawDetailActivity, "开始下载…", Toast.LENGTH_SHORT).show()
                    } else {
                        // 普通 HTTP/HTTPS URL，直接用 DownloadManager 下载
                        val request = DownloadManager.Request(Uri.parse(url))
                        request.setMimeType(mimeType)
                        request.setTitle("法规文件下载")
                        request.setDescription("正在下载…")
                        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        request.setDestinationInExternalPublicDir(
                            Environment.DIRECTORY_DOWNLOADS,
                            "法律宝典/${lawTitle}.${if (mimeType.contains("pdf")) "pdf" else "docx"}"
                        )
                        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        dm.enqueue(request)
                        Toast.makeText(this@LawDetailActivity, "开始下载…", Toast.LENGTH_SHORT).show()
                    }
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
                            // 但保留下载按键（class 包含 "download" 的元素）
                            function keepOnlyPath(parent, pathIndex) {
                                if (pathIndex >= path.length) return;
                                var target = path[pathIndex];
                                var children = parent.children;
                                for (var i = 0; i < children.length; i++) {
                                    var child = children[i];
                                    if (child === target) {
                                        keepOnlyPath(child, pathIndex + 1);
                                    } else {
                                        // 隐藏所有非路径上的元素（包括原网页的下载按钮，因为我们用 ActionBar 的下载按钮）
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
                                        // iframe 是跨域的 OFD 阅读器
                                        // 目标：显示宽度=手机宽度，显示高度=手机高度（拉满至底部）
                                        // 方案：设置 iframe 原始宽度为 750px，用 transform 缩放到手机宽度
                                        // OFD 内容实际宽度约 700px，设置 750px 让内容拉满手机宽度
                                        var originalWidth = 750; // OFD 内容原始宽度
                                        var scale = window.innerWidth / originalWidth;
                                        
                                        // 找到 iframe 的直接父容器
                                        var iframeParent = reader.parentElement;
                                        
                                        if (iframeParent) {
                                            // 设置父容器为全屏，overflow hidden
                                            iframeParent.style.position = 'relative';
                                            iframeParent.style.width = '100%';
                                            iframeParent.style.height = '100vh';
                                            iframeParent.style.minHeight = '100vh';
                                            iframeParent.style.overflow = 'hidden';
                                            iframeParent.style.margin = '0';
                                            iframeParent.style.padding = '0';
                                        }
                                        
                                        // 设置 iframe 原始尺寸（缩放前）
                                        reader.style.width = originalWidth + 'px';
                                        reader.style.height = (window.innerHeight / scale) + 'px';
                                        reader.style.minHeight = (window.innerHeight / scale) + 'px';
                                        reader.style.border = 'none';
                                        reader.style.display = 'block';
                                        reader.style.margin = '0';
                                        reader.style.padding = '0';
                                        
                                        // 用 CSS transform 缩放 iframe，使其显示宽度=手机宽度
                                        reader.style.transform = 'scale(' + scale + ')';
                                        reader.style.transformOrigin = 'top left';
                                        
                                        // 确保 iframe 的所有祖先元素也是全屏
                                        var ancestor = reader.parentElement;
                                        while (ancestor && ancestor !== document.body) {
                                            ancestor.style.width = '100%';
                                            ancestor.style.height = '100%';
                                            ancestor.style.minHeight = '100vh';
                                            ancestor.style.margin = '0';
                                            ancestor.style.padding = '0';
                                            ancestor.style.overflow = 'hidden';
                                            ancestor = ancestor.parentElement;
                                        }
                                        
                                        // 设置 body 和 html 为全屏
                                        document.body.style.width = '100%';
                                        document.body.style.height = '100%';
                                        document.body.style.minHeight = '100vh';
                                        document.body.style.overflow = 'hidden';
                                        document.documentElement.style.width = '100%';
                                        document.documentElement.style.height = '100%';
                                        document.documentElement.style.overflow = 'hidden';
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

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_detail, menu)
        return true
    }

    /**
     * 通过 JavaScript 触发下载：
     * 1. 点击原网页的下载按钮，显示弹出框
     * 2. 等待 500ms 后，点击弹出框里的"下载"按钮，触发下载
     */
    private fun triggerDownload() {
        Toast.makeText(this, "正在准备下载…", Toast.LENGTH_SHORT).show()

        val jsCode = """
            (function() {
                try {
                    // 找到原网页的下载按钮并点击
                    var downloadBtn = document.querySelector('.download');
                    if (downloadBtn) {
                        downloadBtn.click();
                        
                        // 等待 500ms 后，点击弹出框里的"下载"按钮
                        setTimeout(function() {
                            try {
                                // 找到弹出框里的下载按钮（包含"下载"文字的按钮）
                                var popups = document.querySelectorAll('.el-tooltip__popper, [class*="tooltip"], [class*="popover"]');
                                var clicked = false;
                                for (var i = 0; i < popups.length; i++) {
                                    var popup = popups[i];
                                    if (popup.style.display !== 'none' && popup.offsetParent !== null) {
                                        // 找到弹出框里所有按钮
                                        var buttons = popup.querySelectorAll('button, [role="button"], span, a');
                                        for (var j = 0; j < buttons.length; j++) {
                                            var btn = buttons[j];
                                            var text = (btn.textContent || '').trim();
                                            // 点击包含"下载"但不包含"WPS"的按钮
                                            if (text.indexOf('下载') >= 0 && text.indexOf('WPS') < 0 && text.indexOf('扫码') < 0) {
                                                btn.click();
                                                clicked = true;
                                                break;
                                            }
                                        }
                                        if (clicked) break;
                                    }
                                }
                                
                                // 如果没找到弹出框里的按钮，直接找页面上所有包含"下载"的按钮
                                if (!clicked) {
                                    var allButtons = document.querySelectorAll('button, [role="button"], span, a');
                                    for (var k = 0; k < allButtons.length; k++) {
                                        var btn2 = allButtons[k];
                                        var text2 = (btn2.textContent || '').trim();
                                        if (text2 === '下载' || (text2.indexOf('下载') >= 0 && text2.indexOf('WPS') < 0 && text2.indexOf('扫码') < 0 && btn2.offsetParent !== null)) {
                                            btn2.click();
                                            break;
                                        }
                                    }
                                }
                            } catch(e) {
                                console.error('Click download in popup error:', e);
                            }
                        }, 500);
                    } else {
                        console.log('Download button not found');
                    }
                } catch(e) {
                    console.error('Trigger download error:', e);
                }
            })();
        """.trimIndent()

        webView.post {
            webView.evaluateJavascript(jsCode, null)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            R.id.action_download -> {
                triggerDownload()
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
