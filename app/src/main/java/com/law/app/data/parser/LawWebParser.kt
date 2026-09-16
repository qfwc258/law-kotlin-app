package com.law.app.data.parser

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.law.app.data.model.Law
import com.law.app.data.model.LawType
import com.law.app.util.Constants
import com.law.app.util.Result
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * TVBox 模式 - 后台 WebView 数据提取器
 *
 * 原理：用隐藏的 WebView 加载网站初始化环境（Cookie、localStorage 等），
 * 然后通过 evaluateJavascript 执行 fetch 调用搜索 API，
 * WebView 自动处理请求头、Cookie、CORS 等，模拟真实浏览器环境。
 *
 * 优势：
 * - 不依赖 Retrofit 的请求头配置
 * - WebView 自动处理 Cookie、令牌、签名等
 * - 即使 API 有防爬机制，也能通过 WebView 绕过
 * - 直接获取 API 完整 JSON 数据（包括 bbbs）
 */
class LawWebParser private constructor(context: Context) {

    private val webView: WebView
    private var isInitialized = false
    private val initLock = Any()

    init {
        webView = WebView(context.applicationContext).apply {
            layoutParams = ViewGroup.LayoutParams(0, 0)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36 LawApp/1.0"
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    synchronized(initLock) {
                        isInitialized = true
                        (initLock as Object).notifyAll()
                    }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    return false
                }
            }
            webChromeClient = WebChromeClient()
        }
    }

    /**
     * 初始化 WebView 环境（加载网站首页）
     */
    suspend fun ensureInitialized(): Boolean = withContext(Dispatchers.Main) {
        if (isInitialized) return@withContext true

        val deferred = CompletableDeferred<Boolean>()
        synchronized(initLock) {
            if (isInitialized) {
                deferred.complete(true)
                return@withContext true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                synchronized(initLock) {
                    isInitialized = true
                }
                deferred.complete(true)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    deferred.complete(false)
                }
            }
        }

        webView.loadUrl(Constants.OFFICIAL_URL)

        // 等待初始化完成，最多 10 秒
        withTimeoutOrNull(10000) {
            deferred.await()
        } ?: false
    }

    /**
     * 搜索法规
     *
     * @param keyword 搜索关键词
     * @param page 页码（从 1 开始）
     * @param pageSize 每页数量
     * @return Result<Pair<List<Law>, Int>> 列表和总数
     */
    suspend fun search(
        keyword: String,
        page: Int = 1,
        pageSize: Int = 10
    ): Result<Pair<List<Law>, Int>> = withContext(Dispatchers.Main) {
        // 确保 WebView 已初始化
        if (!ensureInitialized()) {
            return@withContext Result.Error("网络初始化失败，请检查网络连接")
        }

        // 转义关键词中的特殊字符
        val escapedKeyword = keyword.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")

        // 构造 fetch 脚本
        val script = """
            (function() {
                window.__searchResult = null;
                window.__searchError = null;
                fetch('/law-search/search/list', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'Accept': 'application/json'
                    },
                    body: JSON.stringify({
                        searchRange: 1,
                        searchType: 2,
                        searchContent: "$escapedKeyword",
                        pageNum: $page,
                        pageSize: $pageSize
                    })
                })
                .then(function(response) {
                    if (!response.ok) {
                        throw new Error('HTTP ' + response.status);
                    }
                    return response.json();
                })
                .then(function(data) {
                    window.__searchResult = JSON.stringify(data);
                })
                .catch(function(error) {
                    window.__searchError = error.message || 'Unknown error';
                });
                return 'started';
            })()
        """.trimIndent()

        // 执行 fetch
        webView.evaluateJavascript(script, null)

        // 轮询等待结果
        val result = pollForResult(timeoutMs = 15000)
        if (result != null) {
            return@withContext parseSearchResult(result)
        }

        // 检查是否有错误
        val error = getJavascriptValue("window.__searchError")
        if (error != null && error != "null") {
            return@withContext Result.Error("搜索失败: $error")
        }

        Result.Error("搜索超时，请稍后重试")
    }

    /**
     * 轮询等待 JS 执行结果
     */
    private suspend fun pollForResult(timeoutMs: Long): String? {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val value = getJavascriptValue("window.__searchResult")
            if (value != null && value != "null" && value.isNotEmpty()) {
                return value
            }
            kotlinx.coroutines.delay(200)
        }
        return null
    }

    /**
     * 获取 JS 变量值
     */
    private suspend fun getJavascriptValue(expression: String): String? {
        val deferred = CompletableDeferred<String?>()
        webView.evaluateJavascript(expression) { result ->
            deferred.complete(result)
        }
        return withTimeoutOrNull(3000) {
            deferred.await()
        }
    }

    /**
     * 解析搜索结果 JSON
     */
    private fun parseSearchResult(jsonString: String): Result<Pair<List<Law>, Int>> {
        return try {
            // 移除 JSON 字符串两端的引号
            val cleanedJson = jsonString.removeSurrounding("\"")
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\\\", "\\")

            val json = JSONObject(cleanedJson)
            val code = json.optInt("code", -1)
            val msg = json.optString("msg", "")

            if (code != 200) {
                return Result.Error("搜索失败: $msg")
            }

            val total = json.optInt("total", 0)
            val rows = json.optJSONArray("rows") ?: JSONArray()

            val laws = mutableListOf<Law>()
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val law = parseLawRow(row)
                if (law != null) {
                    laws.add(law)
                }
            }

            Result.Success(Pair(laws, total))
        } catch (e: Exception) {
            Result.Error("解析搜索结果失败: ${e.message}")
        }
    }

    /**
     * 解析单条法规记录
     */
    private fun parseLawRow(row: JSONObject): Law? {
        return try {
            val bbbs = row.optString("bbbs", "")
            if (bbbs.isEmpty()) return null

            // 清理标题中的 HTML 高亮标签
            val rawTitle = row.optString("title", "")
            val title = rawTitle
                .replace("<em class='highlight'>", "")
                .replace("</em>", "")
                .replace("<em class=\"highlight\">", "")
                .trim()

            val gbrq = row.optString("gbrq", "")
            val sxrq = row.optString("sxrq", "")
            val sxx = row.optInt("sxx", 0)
            val zdjgName = row.optString("zdjgName", "")
            val flxz = row.optString("flxz", "")

            // 根据 flxz 判断法规类型
            val type = when {
                flxz.contains("法律") -> LawType.LAW
                flxz.contains("行政法规") -> LawType.ADMIN
                flxz.contains("司法解释") -> LawType.JUDICIAL
                flxz.contains("地方性法规") || flxz.contains("地方") -> LawType.LOCAL
                flxz.contains("监察") -> LawType.SUPERVISION
                else -> LawType.ALL
            }

            Law(
                id = bbbs,
                title = title,
                summary = "",
                issuingAuthority = zdjgName,
                publishDate = gbrq,
                effectiveDate = sxrq,
                type = type,
                status = if (sxx > 0) sxx else null,
                isFavorite = false,
                lastReadTime = 0,
                contentTreeJson = null
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取法规详情
     *
     * @param bbbs 法规唯一标识
     * @return Result<Law> 法规详情（含目录树、OSS 文件路径）
     */
    suspend fun getLawDetail(bbbs: String): Result<Law> = withContext(Dispatchers.Main) {
        if (!ensureInitialized()) {
            return@withContext Result.Error("网络初始化失败，请检查网络连接")
        }

        val script = """
            (function() {
                window.__detailResult = null;
                window.__detailError = null;
                fetch('/law-search/search/flfgDetails?bbbs=$bbbs', {
                    method: 'GET',
                    headers: {'Accept': 'application/json'}
                })
                .then(function(response) {
                    if (!response.ok) throw new Error('HTTP ' + response.status);
                    return response.json();
                })
                .then(function(data) {
                    window.__detailResult = JSON.stringify(data);
                })
                .catch(function(error) {
                    window.__detailError = error.message || 'Unknown error';
                });
                return 'started';
            })()
        """.trimIndent()

        webView.evaluateJavascript(script, null)

        val result = pollForDetailResult(timeoutMs = 15000)
        if (result != null) {
            return@withContext parseDetailResult(result)
        }

        val error = getJavascriptValue("window.__detailError")
        if (error != null && error != "null") {
            return@withContext Result.Error("获取详情失败: $error")
        }

        Result.Error("获取详情超时，请稍后重试")
    }

    /**
     * 轮询等待详情结果
     */
    private suspend fun pollForDetailResult(timeoutMs: Long): String? {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val value = getJavascriptValue("window.__detailResult")
            if (value != null && value != "null" && value.isNotEmpty()) {
                return value
            }
            kotlinx.coroutines.delay(200)
        }
        return null
    }

    /**
     * 解析详情结果
     */
    private fun parseDetailResult(jsonString: String): Result<Law> {
        return try {
            val cleanedJson = jsonString.removeSurrounding("\"")
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\\\", "\\")

            val json = JSONObject(cleanedJson)
            val code = json.optInt("code", -1)
            if (code != 200) {
                return Result.Error("获取详情失败: ${json.optString("msg", "")}")
            }

            val data = json.optJSONObject("data") ?: return Result.Error("详情数据为空")

            val bbbs = data.optString("bbbs", "")
            val title = data.optString("title", "")
            val gbrq = data.optString("gbrq", "")
            val sxrq = data.optString("sxrq", "")
            val sxx = data.optInt("sxx", 0)
            val zdjgName = data.optString("zdjgName", "")
            val flxz = data.optString("flxz", "")

            // OSS 文件路径
            val ossFile = data.optJSONObject("ossFile")
            val ossPdfPath = ossFile?.optString("ossPdfPath", null)
            val ossWordPath = ossFile?.optString("ossWordPath", null)

            // 目录树（序列化为 JSON 字符串）
            val content = data.optJSONObject("content")
            val contentTreeJson = content?.toString()

            val type = when {
                flxz.contains("法律") -> LawType.LAW
                flxz.contains("行政法规") -> LawType.ADMIN
                flxz.contains("司法解释") -> LawType.JUDICIAL
                flxz.contains("地方性法规") || flxz.contains("地方") -> LawType.LOCAL
                flxz.contains("监察") -> LawType.SUPERVISION
                else -> LawType.ALL
            }

            val law = Law(
                id = bbbs,
                title = title,
                summary = "",
                issuingAuthority = zdjgName,
                publishDate = gbrq,
                effectiveDate = sxrq,
                type = type,
                status = if (sxx > 0) sxx else null,
                isFavorite = false,
                lastReadTime = 0,
                contentTreeJson = contentTreeJson,
                ossPdfPath = ossPdfPath,
                ossWordPath = ossWordPath
            )

            Result.Success(law)
        } catch (e: Exception) {
            Result.Error("解析详情失败: ${e.message}")
        }
    }

    /**
     * 获取首页数据（大类 + 新法速递 + 热门搜索）
     */
    suspend fun getHomeData(): Result<HomeData> = withContext(Dispatchers.Main) {
        if (!ensureInitialized()) {
            return@withContext Result.Error("网络初始化失败，请检查网络连接")
        }

        val script = """
            (function() {
                window.__homeResult = null;
                window.__homeError = null;
                fetch('/law-search/index/aggregateData', {
                    method: 'GET',
                    headers: {'Accept': 'application/json'}
                })
                .then(function(response) {
                    if (!response.ok) throw new Error('HTTP ' + response.status);
                    return response.json();
                })
                .then(function(data) {
                    window.__homeResult = JSON.stringify(data);
                })
                .catch(function(error) {
                    window.__homeError = error.message || 'Unknown error';
                });
                return 'started';
            })()
        """.trimIndent()

        webView.evaluateJavascript(script, null)

        val result = pollForHomeResult(timeoutMs = 15000)
        if (result != null) {
            return@withContext parseHomeResult(result)
        }

        val error = getJavascriptValue("window.__homeError")
        if (error != null && error != "null") {
            return@withContext Result.Error("获取首页数据失败: $error")
        }

        Result.Error("获取首页数据超时，请稍后重试")
    }

    /**
     * 轮询等待首页结果
     */
    private suspend fun pollForHomeResult(timeoutMs: Long): String? {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val value = getJavascriptValue("window.__homeResult")
            if (value != null && value != "null" && value.isNotEmpty()) {
                return value
            }
            kotlinx.coroutines.delay(200)
        }
        return null
    }

    /**
     * 解析首页结果
     */
    private fun parseHomeResult(jsonString: String): Result<HomeData> {
        return try {
            val cleanedJson = jsonString.removeSurrounding("\"")
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\\\", "\\")

            val json = JSONObject(cleanedJson)
            val code = json.optInt("code", -1)
            if (code != 200) {
                return Result.Error("获取首页数据失败: ${json.optString("msg", "")}")
            }

            val data = json.optJSONObject("data") ?: return Result.Error("首页数据为空")

            // 大类列表
            val categories = mutableListOf<LawCategory>()
            val flflCount = data.optJSONArray("flflCount")
            if (flflCount != null) {
                for (i in 0 until flflCount.length()) {
                    val item = flflCount.getJSONObject(i)
                    categories.add(
                        LawCategory(
                            id = item.optInt("id", 0),
                            name = item.optString("key", ""),
                            count = item.optInt("count", 0)
                        )
                    )
                }
            }

            // 新法速递
            val newLaws = mutableListOf<Law>()
            val xfsd = data.optJSONArray("xfsd")
            if (xfsd != null) {
                for (i in 0 until xfsd.length()) {
                    val item = xfsd.getJSONObject(i)
                    val bbbs = item.optString("bbbs", "")
                    if (bbbs.isNotEmpty()) {
                        val flxz = item.optString("flxz", "")
                        val type = when {
                            flxz.contains("法律") -> LawType.LAW
                            flxz.contains("行政法规") -> LawType.ADMIN
                            flxz.contains("司法解释") -> LawType.JUDICIAL
                            else -> LawType.ALL
                        }
                        newLaws.add(
                            Law(
                                id = bbbs,
                                title = item.optString("title", ""),
                                summary = "",
                                issuingAuthority = "",
                                publishDate = item.optString("gbrq", ""),
                                effectiveDate = "",
                                type = type,
                                status = null,
                                isFavorite = false,
                                lastReadTime = 0,
                                contentTreeJson = null
                            )
                        )
                    }
                }
            }

            // 热门搜索
            val popularSearches = mutableListOf<String>()
            val popularSearch = data.optJSONArray("popularSearch")
            if (popularSearch != null) {
                for (i in 0 until popularSearch.length()) {
                    val item = popularSearch.getJSONObject(i)
                    val title = item.optString("title", "")
                    if (title.isNotEmpty()) {
                        popularSearches.add(title)
                    }
                }
            }

            Result.Success(HomeData(categories, newLaws, popularSearches))
        } catch (e: Exception) {
            Result.Error("解析首页数据失败: ${e.message}")
        }
    }

    /**
     * 获取 OFD 阅读器预览 URL
     *
     * @param ossPdfPath OSS PDF 文件路径
     * @return Result<String> OFD 阅读器 URL
     */
    suspend fun getPreviewUrl(ossPdfPath: String): Result<String> = withContext(Dispatchers.Main) {
        if (!ensureInitialized()) {
            return@withContext Result.Error("网络初始化失败，请检查网络连接")
        }

        val script = """
            (function() {
                window.__previewResult = null;
                window.__previewError = null;
                fetch('/law-search/amazonFile/previewLink?filePath=$ossPdfPath&fileType=pdf', {
                    method: 'GET',
                    headers: {'Accept': 'application/json'}
                })
                .then(function(response) {
                    if (!response.ok) throw new Error('HTTP ' + response.status);
                    return response.json();
                })
                .then(function(data) {
                    window.__previewResult = JSON.stringify(data);
                })
                .catch(function(error) {
                    window.__previewError = error.message || 'Unknown error';
                });
                return 'started';
            })()
        """.trimIndent()

        webView.evaluateJavascript(script, null)

        val result = pollForPreviewResult(timeoutMs = 15000)
        if (result != null) {
            return@withContext parsePreviewResult(result)
        }

        val error = getJavascriptValue("window.__previewError")
        if (error != null && error != "null") {
            return@withContext Result.Error("获取预览链接失败: $error")
        }

        Result.Error("获取预览链接超时，请稍后重试")
    }

    /**
     * 轮询等待预览结果
     */
    private suspend fun pollForPreviewResult(timeoutMs: Long): String? {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val value = getJavascriptValue("window.__previewResult")
            if (value != null && value != "null" && value.isNotEmpty()) {
                return value
            }
            kotlinx.coroutines.delay(200)
        }
        return null
    }

    /**
     * 解析预览结果
     */
    private fun parsePreviewResult(jsonString: String): Result<String> {
        return try {
            val cleanedJson = jsonString.removeSurrounding("\"")
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\\\", "\\")
            val json = org.json.JSONObject(cleanedJson)
            val code = json.optInt("code", -1)
            if (code != 200) {
                return Result.Error("获取预览链接失败: ${json.optString("msg", "")}")
            }
            val data = json.optJSONObject("data")
            val url = data?.optString("url", "") ?: ""
            if (url.isEmpty()) {
                Result.Error("预览链接为空")
            } else {
                // 修复 OFD 阅读器 URL：对 file 参数进行 URL 编码，并添加 fileName 参数
                val fixedUrl = fixOFDReaderUrl(url)
                Result.Success(fixedUrl)
            }
        } catch (e: Exception) {
            Result.Error("解析预览链接失败: ${e.message}")
        }
    }

    /**
     * 修复 OFD 阅读器 URL
     *
     * 问题：ofdGenerateLink API 返回的文件名没有格式后缀，
     * 导致 OFD 阅读器报错"回调信息中文件名没有带格式后缀"
     *
     * 修复：在 file 参数中的 API URL 末尾添加 fileName=law.pdf 参数，
     * 让 API 返回带后缀的文件名
     */
    private fun fixOFDReaderUrl(url: String): String {
        return try {
            // 找到 file= 参数的位置
            val fileParamIndex = url.indexOf("file=")
            if (fileParamIndex == -1) {
                return url
            }

            // 提取 file 参数的值（从 file= 到下一个 & 或字符串末尾）
            val fileValueStart = fileParamIndex + 5
            val fileValueEnd = url.indexOf("&", fileValueStart).let {
                if (it == -1) url.length else it
            }
            var fileValue = url.substring(fileValueStart, fileValueEnd)

            // 在 file 参数中的 API URL 末尾添加 fileName=law.pdf 参数
            if (!fileValue.contains("fileName=")) {
                fileValue = if (fileValue.contains("?")) {
                    "$fileValue&fileName=law.pdf"
                } else {
                    "$fileValue?fileName=law.pdf"
                }
            }

            // 构建新的 URL
            val prefix = url.substring(0, fileParamIndex)
            val suffix = if (fileValueEnd < url.length) url.substring(fileValueEnd) else ""

            "$prefix$fileValue$suffix"
        } catch (e: Exception) {
            url // 修复失败，返回原始 URL
        }
    }

    /**
     * 获取 PDF 下载直链（带签名，有效期1小时）
     *
     * @param ossPdfPath OSS PDF 文件路径
     * @return Result<String> PDF 下载 URL
     */
    suspend fun getDownloadUrl(ossPdfPath: String): Result<String> = withContext(Dispatchers.Main) {
        if (!ensureInitialized()) {
            return@withContext Result.Error("网络初始化失败，请检查网络连接")
        }

        val script = """
            (function() {
                window.__downloadResult = null;
                window.__downloadError = null;
                fetch('/law-search/amazonFile/ofdGenerateLink?filePath=$ossPdfPath&fileType=pdf', {
                    method: 'GET',
                    headers: {'Accept': 'application/json'}
                })
                .then(function(response) {
                    if (!response.ok) throw new Error('HTTP ' + response.status);
                    return response.json();
                })
                .then(function(data) {
                    window.__downloadResult = JSON.stringify(data);
                })
                .catch(function(error) {
                    window.__downloadError = error.message || 'Unknown error';
                });
                return 'started';
            })()
        """.trimIndent()

        webView.evaluateJavascript(script, null)

        val result = pollForDownloadResult(timeoutMs = 15000)
        if (result != null) {
            return@withContext parseDownloadResult(result)
        }

        val error = getJavascriptValue("window.__downloadError")
        if (error != null && error != "null") {
            return@withContext Result.Error("获取下载链接失败: $error")
        }

        Result.Error("获取下载链接超时，请稍后重试")
    }

    /**
     * 轮询等待下载结果
     */
    private suspend fun pollForDownloadResult(timeoutMs: Long): String? {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val value = getJavascriptValue("window.__downloadResult")
            if (value != null && value != "null" && value.isNotEmpty()) {
                return value
            }
            kotlinx.coroutines.delay(200)
        }
        return null
    }

    /**
     * 解析下载结果
     */
    private fun parseDownloadResult(jsonString: String): Result<String> {
        return try {
            val cleanedJson = jsonString.removeSurrounding("\"")
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\\\", "\\")
            val json = org.json.JSONObject(cleanedJson)
            val file = json.optJSONObject("file")
            val downloadUrl = file?.optString("download_url", "") ?: ""
            if (downloadUrl.isEmpty()) {
                Result.Error("下载链接为空")
            } else {
                Result.Success(downloadUrl)
            }
        } catch (e: Exception) {
            Result.Error("解析下载链接失败: ${e.message}")
        }
    }

    /**
     * 释放资源
     */
    fun destroy() {
        webView.stopLoading()
        webView.destroy()
        isInitialized = false
    }

    companion object {
        @Volatile
        private var instance: LawWebParser? = null

        fun getInstance(context: Context): LawWebParser {
            return instance ?: synchronized(this) {
                instance ?: LawWebParser(context).also { instance = it }
            }
        }
    }
}

/**
 * 首页数据
 */
data class HomeData(
    val categories: List<LawCategory> = emptyList(),
    val newLaws: List<Law> = emptyList(),
    val popularSearches: List<String> = emptyList()
)

/**
 * 法规大类
 */
data class LawCategory(
    val id: Int,
    val name: String,
    val count: Int
)
