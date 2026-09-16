package com.law.app.data.remote.api

import com.law.app.data.remote.dto.LawDetailResponse
import com.law.app.data.remote.dto.LawSearchRequest
import com.law.app.data.remote.dto.LawSearchResponse
import com.law.app.data.remote.dto.PreviewLinkResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * 国家法律法规数据库 API 接口（新版 SPA 站点）
 *
 * 基地址: https://flk.npc.gov.cn/
 *
 * 注意: 新版网站已重构为 Vue/React SPA，旧版 /api/ 端点已失效。
 * 所有请求需携带 User-Agent 和 Referer 头，否则可能被拦截。
 */
interface LawApiService {

    /**
     * 搜索法规列表（新版 API）
     *
     * 端点: POST /law-search/search/list
     *
     * 请求体:
     * - searchRange: 搜索范围 (1=全部, 2=标题, 3=正文)
     * - searchType: 搜索类型 (1=精确, 2=模糊)
     * - searchContent: 搜索关键词
     * - pageNum: 页码（从1开始）
     * - pageSize: 每页条数
     *
     * 响应: { total, rows, code, msg }
     */
    @POST("law-search/search/list")
    suspend fun searchLaws(
        @Body request: LawSearchRequest
    ): LawSearchResponse

    /**
     * 获取法规详情（新版 API）
     *
     * 端点: GET /law-search/search/flfgDetails
     *
     * @param bbbs 法规唯一标识（从搜索结果的 bbbs 字段获取）
     *
     * 响应: { code, msg, data: { bbbs, title, gbrq, sxrq, sxx, zdjgName, flxz, ossFile, content } }
     * 注意: content 是目录树（编->章->条），不包含条文正文，正文需查看 PDF。
     */
    @GET("law-search/search/flfgDetails")
    suspend fun getLawDetail(
        @Query("bbbs") bbbs: String
    ): LawDetailResponse

    /**
     * 获取文件预览链接（用于 PDF/OFD 在线预览）
     *
     * 端点: GET /law-search/amazonFile/previewLink
     *
     * @param filePath OSS 文件路径（从详情的 ossFile.ossPdfPath 获取）
     * @param fileType 文件类型 (pdf/word)
     */
    @GET("law-search/amazonFile/previewLink")
    suspend fun getPreviewLink(
        @Query("filePath") filePath: String,
        @Query("fileType") fileType: String = "pdf"
    ): PreviewLinkResponse
}
