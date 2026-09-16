package com.law.app.data.repository

import com.google.gson.Gson
import com.law.app.data.local.LawDao
import com.law.app.data.local.LawEntity
import com.law.app.data.model.Law
import com.law.app.data.model.LawType
import com.law.app.data.model.SearchMode
import com.law.app.data.model.SortOrder
import com.law.app.data.remote.NetworkModule
import com.law.app.data.remote.dto.ContentNode
import com.law.app.data.remote.dto.LawDetailData
import com.law.app.data.remote.dto.LawDetailResponse
import com.law.app.data.remote.dto.LawRecordDto
import com.law.app.data.remote.dto.LawSearchRequest
import com.law.app.data.remote.dto.LawSearchResponse
import com.law.app.util.Constants
import com.law.app.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 法规数据仓库
 *
 * 数据策略：
 * - 搜索/列表：优先远程 API，结果写入本地缓存
 * - 详情：优先远程 API，失败时回退本地缓存
 * - 收藏/历史：纯本地
 * - 离线：搜索回退本地缓存
 *
 * 新版 API 说明:
 * - 搜索: POST /law-search/search/list
 * - 详情: GET /law-search/search/flfgDetails?bbbs=xxx
 * - 详情只返回目录树，不返回条文正文，正文需通过 PDF 查看
 */
class LawRepository(
    private val api: com.law.app.data.remote.api.LawApiService = NetworkModule.lawApiService,
    private val dao: LawDao
) {

    private val gson = Gson()

    // ===== 搜索 =====

    /**
     * 搜索法规（远程 API，新版 POST 接口）
     */
    suspend fun searchLaws(
        keyword: String,
        type: LawType = LawType.ALL,
        searchMode: SearchMode = SearchMode.FUZZY,
        sortOrder: SortOrder = SortOrder.PUBLISH_DESC,
        page: Int = 1,
        size: Int = Constants.PAGE_SIZE,
        gbrqStart: String? = null,
        gbrqEnd: String? = null,
        sxrqStart: String? = null,
        sxrqEnd: String? = null
    ): Result<Pair<List<Law>, Int>> {
        return try {
            val request = LawSearchRequest(
                searchRange = 1, // 1=全部范围
                searchType = if (searchMode == SearchMode.EXACT) 1 else 2, // 1=精确, 2=模糊
                searchContent = keyword,
                pageNum = page,
                pageSize = size
            )
            val response: LawSearchResponse = api.searchLaws(request)
            if (response.isSuccess) {
                val laws = response.rows.map { it.toDomain() }
                // 写入缓存（保留收藏和阅读状态）
                laws.forEach { law ->
                    val cached = dao.getLawById(law.id)
                    dao.insertOrUpdate(
                        law.toEntity(
                            isFavorite = cached?.isFavorite ?: false,
                            lastReadTime = cached?.lastReadTime ?: 0L
                        )
                    )
                }
                Result.success(laws to response.total)
            } else {
                Result.error(response.msg ?: "搜索失败")
            }
        } catch (e: Exception) {
            // 网络失败时回退本地缓存搜索
            Result.error("网络请求失败: ${e.message}", e)
        }
    }

    /**
     * 本地缓存搜索（离线模式）
     */
    fun searchCachedLaws(keyword: String): Flow<List<Law>> =
        dao.searchCachedLaws(keyword).map { list -> list.map { it.toDomain() } }

    // ===== 详情 =====

    /**
     * 获取法规详情
     * 优先远程，失败回退本地缓存
     */
    suspend fun getLawDetail(id: String): Result<Law> {
        // 先尝试远程
        try {
            val response: LawDetailResponse = api.getLawDetail(id)
            if (response.isSuccess && response.data != null) {
                val law = response.data.toDomain(gson)
                val cached = dao.getLawById(id)
                dao.insertOrUpdate(
                    law.toEntity(
                        isFavorite = cached?.isFavorite ?: false,
                        lastReadTime = cached?.lastReadTime ?: 0L
                    )
                )
                // 记录阅读时间
                dao.updateLastReadTime(id, System.currentTimeMillis())
                return Result.success(law.copy(isFavorite = cached?.isFavorite ?: false))
            }
        } catch (e: Exception) {
            // 继续回退本地
        }

        // 回退本地缓存
        val cached = dao.getLawById(id)
        return if (cached != null) {
            dao.updateLastReadTime(id, System.currentTimeMillis())
            Result.success(cached.toDomain())
        } else {
            Result.error("无法获取法规详情，请检查网络连接")
        }
    }

    // ===== 收藏 =====

    fun getFavoriteLaws(): Flow<List<Law>> =
        dao.getFavoriteLaws().map { list -> list.map { it.toDomain() } }

    suspend fun toggleFavorite(id: String, isFavorite: Boolean) {
        val cached = dao.getLawById(id)
        if (cached != null) {
            dao.updateFavorite(id, isFavorite)
        }
    }

    suspend fun isFavorite(id: String): Boolean = dao.isFavorite(id) ?: false

    // ===== 阅读历史 =====

    fun getRecentLaws(limit: Int = Constants.RECENT_LIMIT): Flow<List<Law>> =
        dao.getRecentLaws(limit).map { list -> list.map { it.toDomain() } }

    suspend fun clearReadingHistory() {
        dao.clearReadingHistory()
    }

    // ===== 缓存清理 =====

    suspend fun clearOldCache(days: Int = 7) {
        val cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
        dao.clearOldCache(cutoff)
    }
}

// ===== Mapper 扩展函数 =====

/** 清理标题中的 HTML 高亮标签 */
private fun String?.cleanHighlight(): String {
    if (this == null) return ""
    return this.replace(Regex("""<em[^>]*>"""), "")
        .replace(Regex("""</em>"""), "")
        .trim()
}

/**
 * 搜索记录 -> 领域模型
 */
private fun LawRecordDto.toDomain(): Law = Law(
    id = bbbs ?: title?.hashCode()?.toString() ?: System.currentTimeMillis().toString(),
    title = title.cleanHighlight(),
    type = LawType.fromCode("flfg"), // 新版 API 搜索结果默认为法律法规
    typeName = flxz ?: "",
    lawLevel = flxz ?: "",
    issuingAuthority = zdjgName ?: "",
    publishDate = gbrq ?: "",
    effectiveDate = sxrq ?: "",
    documentNumber = "",
    summary = "",
    content = "",
    pdfUrl = null,
    wpsUrl = null,
    detailUrl = null,
    ossPdfPath = null,
    ossWordPath = null,
    contentTreeJson = null,
    status = sxx
)

/**
 * 详情数据 -> 领域模型
 */
private fun LawDetailData.toDomain(gson: Gson): Law {
    // 序列化目录树为 JSON 字符串存储
    val treeJson = content?.let { gson.toJson(it) }

    // 从目录树生成文本摘要（编章节列表）
    val contentText = content?.let { node ->
        buildString {
            node.getAllChapters().forEach { (depth, n) ->
                if (depth > 0) {
                    append("  ".repeat(depth - 1))
                    append(n.title)
                    append("\n")
                }
            }
            val articles = node.getAllArticles()
            if (articles.isNotEmpty()) {
                append("\n共 ${articles.size} 条条文\n")
                append("（正文请查看 PDF 原文）\n")
            }
        }
    } ?: ""

    return Law(
        id = bbbs ?: "",
        title = title ?: "",
        type = LawType.fromCode("flfg"),
        typeName = flxz ?: "",
        lawLevel = flxz ?: "",
        issuingAuthority = zdjgName ?: "",
        publishDate = gbrq ?: "",
        effectiveDate = sxrq ?: "",
        documentNumber = "",
        summary = "",
        content = contentText,
        pdfUrl = null,
        wpsUrl = null,
        detailUrl = null,
        ossPdfPath = ossFile?.ossPdfPath,
        ossWordPath = ossFile?.ossWordPath,
        contentTreeJson = treeJson,
        status = sxx
    )
}

private fun LawEntity.toDomain(): Law = Law(
    id = id,
    title = title,
    type = LawType.fromCode(type),
    typeName = typeName,
    lawLevel = lawLevel,
    issuingAuthority = issuingAuthority,
    publishDate = publishDate,
    effectiveDate = effectiveDate,
    documentNumber = documentNumber,
    summary = summary,
    content = content,
    pdfUrl = pdfUrl,
    wpsUrl = wpsUrl,
    detailUrl = detailUrl,
    ossPdfPath = ossPdfPath,
    ossWordPath = ossWordPath,
    contentTreeJson = contentTreeJson,
    status = status,
    isFavorite = isFavorite,
    lastReadTime = lastReadTime
)

private fun Law.toEntity(
    isFavorite: Boolean = this.isFavorite,
    lastReadTime: Long = this.lastReadTime
): LawEntity = LawEntity(
    id = id,
    title = title,
    type = type.code,
    typeName = typeName,
    lawLevel = lawLevel,
    issuingAuthority = issuingAuthority,
    publishDate = publishDate,
    effectiveDate = effectiveDate,
    documentNumber = documentNumber,
    summary = summary,
    content = content,
    pdfUrl = pdfUrl,
    wpsUrl = wpsUrl,
    detailUrl = detailUrl,
    ossPdfPath = ossPdfPath,
    ossWordPath = ossWordPath,
    contentTreeJson = contentTreeJson,
    status = status,
    isFavorite = isFavorite,
    lastReadTime = lastReadTime
)
