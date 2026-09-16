package com.law.app.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.law.app.data.model.Law
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * 法规文件下载管理器
 *
 * 使用系统 DownloadManager 进行下载：
 * - 通知栏显示下载进度
 * - 支持断点续传
 * - 下载到公共 Downloads 目录
 * - 下载完成后可直接打开
 */
class LawDownloadManager(private val context: Context) {

    private val downloadManager: DownloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    /** 下载状态：stateKey（lawId_format） -> DownloadState */
    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    /** 正在下载的任务：downloadId -> stateKey（lawId_format） */
    private val activeDownloads = mutableMapOf<Long, String>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val downloadId = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: return
            if (downloadId == -1L) return
            val stateKey = activeDownloads[downloadId] ?: return
            checkDownloadStatus(downloadId, stateKey)
        }
    }

    init {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }

    fun destroy() {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {
        }
    }

    /**
     * 下载法规文件
     * @param law 法规对象
     * @param format 下载格式："pdf" 或 "wps"
     */
    fun download(law: Law, format: String = "pdf") {
        val stateKey = "${law.id}_${format.lowercase()}"
        // 新版 API 优先使用 ossPdfPath/ossWordPath 构建下载 URL
        val url = when (format.lowercase()) {
            "pdf" -> buildDownloadUrl(law.ossPdfPath, law.pdfUrl)
            "wps", "doc", "docx" -> buildDownloadUrl(law.ossWordPath, law.wpsUrl)
            else -> null
        }

        if (url.isNullOrBlank()) {
            updateState(stateKey, DownloadState.Failed("该格式下载链接不可用"))
            return
        }

        // 清理文件名中的非法字符
        val safeTitle = law.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val fileName = "${safeTitle}.${format.lowercase()}"
        val mimeType = getMimeType(format)

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("${law.title}（$format）")
            .setDescription("正在下载法规文件…")
            .setMimeType(mimeType)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "法律宝典/$fileName")

        try {
            val downloadId = downloadManager.enqueue(request)
            activeDownloads[downloadId] = stateKey
            updateState(stateKey, DownloadState.Downloading(downloadId, 0))
            // 启动进度轮询
            startProgressPolling(downloadId, stateKey)
        } catch (e: Exception) {
            updateState(stateKey, DownloadState.Failed("下载失败: ${e.message}"))
        }
    }

    /**
     * 从直链下载文件
     * @param url 下载直链
     * @param title 通知栏标题
     * @param notificationTitle 通知栏描述
     * @param mimeType MIME 类型
     */
    fun downloadFromUrl(
        url: String,
        title: String,
        notificationTitle: String = "正在下载…",
        mimeType: String = "application/pdf"
    ) {
        val stateKey = "url_${System.currentTimeMillis()}"
        val safeTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_")

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(safeTitle)
            .setDescription(notificationTitle)
            .setMimeType(mimeType)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "法律宝典/$safeTitle")

        try {
            val downloadId = downloadManager.enqueue(request)
            activeDownloads[downloadId] = stateKey
            updateState(stateKey, DownloadState.Downloading(downloadId, 0))
            startProgressPolling(downloadId, stateKey)
        } catch (e: Exception) {
            updateState(stateKey, DownloadState.Failed("下载失败: ${e.message}"))
        }
    }

    /**
     * 打开已下载的文件
     */
    fun openDownloadedFile(law: Law, format: String = "pdf") {
        val safeTitle = law.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val fileName = "${safeTitle}.${format.lowercase()}"
        val file = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "法律宝典/$fileName"
        )

        if (!file.exists()) {
            // 尝试在 Download 目录根查找
            val altFile = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName
            )
            if (altFile.exists()) {
                openFile(altFile, format)
                return
            }
            updateState(law.id, DownloadState.Failed("文件不存在，请先下载"))
            return
        }
        openFile(file, format)
    }

    private fun openFile(file: File, format: String) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val mimeType = getMimeType(format)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // 没有可打开的应用时，用浏览器打开
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.fromFile(file))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (_: Exception) {
            }
        }
    }

    /** 取消下载 */
    fun cancel(downloadId: Long) {
        downloadManager.remove(downloadId)
        activeDownloads.remove(downloadId)
    }

    /** 查询某法规某格式的下载状态 */
    fun getState(stateKey: String): DownloadState? = _downloadStates.value[stateKey]

    // ===== 内部方法 =====

    private fun startProgressPolling(downloadId: Long, stateKey: String) {
        Thread {
            var finished = false
            while (!finished) {
                try {
                    Thread.sleep(500)
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor: Cursor = downloadManager.query(query)
                    if (cursor.moveToFirst()) {
                        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        val bytesDownloaded = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val bytesTotal = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        val progress = if (bytesTotal > 0) (bytesDownloaded * 100 / bytesTotal) else 0

                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                updateState(stateKey, DownloadState.Completed(downloadId, 100))
                                activeDownloads.remove(downloadId)
                                finished = true
                            }
                            DownloadManager.STATUS_FAILED -> {
                                val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                                updateState(stateKey, DownloadState.Failed("下载失败（错误码: $reason）"))
                                activeDownloads.remove(downloadId)
                                finished = true
                            }
                            DownloadManager.STATUS_PAUSED -> {
                                updateState(stateKey, DownloadState.Paused(downloadId, progress))
                            }
                            DownloadManager.STATUS_RUNNING -> {
                                updateState(stateKey, DownloadState.Downloading(downloadId, progress))
                            }
                        }
                    }
                    cursor.close()
                } catch (e: Exception) {
                    finished = true
                }
            }
        }.start()
    }

    private fun checkDownloadStatus(downloadId: Long, stateKey: String) {
        try {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor: Cursor = downloadManager.query(query)
            if (cursor.moveToFirst()) {
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        updateState(stateKey, DownloadState.Completed(downloadId, 100))
                    }
                    DownloadManager.STATUS_FAILED -> {
                        val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        updateState(stateKey, DownloadState.Failed("下载失败（错误码: $reason）"))
                    }
                }
            }
            cursor.close()
        } catch (_: Exception) {
        }
        activeDownloads.remove(downloadId)
    }

    private fun updateState(stateKey: String, state: DownloadState) {
        val current = _downloadStates.value.toMutableMap()
        current[stateKey] = state
        _downloadStates.value = current
    }

    private fun getMimeType(format: String): String = when (format.lowercase()) {
        "pdf" -> "application/pdf"
        "wps", "doc", "docx" -> "application/msword"
        else -> "application/octet-stream"
    }

    /**
     * 构建下载 URL
     * 新版 API 使用 OSS 内部路径，需通过 ofdGenerateLink 转换为可下载 URL
     * 旧版 API 直接使用完整 URL
     */
    private fun buildDownloadUrl(ossPath: String?, fallbackUrl: String?): String? {
        if (!ossPath.isNullOrBlank()) {
            // 新版 API: 通过 ofdGenerateLink 获取可访问的文件 URL
            return "https://flk.npc.gov.cn/law-search/amazonFile/ofdGenerateLink?filePath=${java.net.URLEncoder.encode(ossPath, "UTF-8")}"
        }
        return fallbackUrl
    }
}

/**
 * 下载状态密封类
 */
sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val downloadId: Long, val progress: Int) : DownloadState()
    data class Paused(val downloadId: Long, val progress: Int) : DownloadState()
    data class Completed(val downloadId: Long, val progress: Int) : DownloadState()
    data class Failed(val message: String) : DownloadState()
}
