package com.law.app.data.remote.api

import com.law.app.data.remote.dto.LawDetailResponse
import com.law.app.data.remote.dto.LawSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 国家法律法规数据库 API 接口
 *
 * 基地址: https://flk.npc.gov.cn/api/
 *
 * 查询参数说明:
 * - page: 页码（从1开始）
 * - size: 每页条数（默认10）
 * - type: 法规类型
 *     flfg  = 法律法规
 *     xzfg  = 行政法规
 *     jcfg  = 监察法规
 *     sfjs  = 司法解释
 *     dfxfg = 地方性法规
 *     fljs  = 法律解释
 * - searchType: 搜索方式
 *     title;vague    = 标题模糊搜索
 *     title;accurate = 标题精确搜索
 * - sortTr: 排序字段
 *     f_bbrq_s;desc = 按发布日期降序
 *     f_bbrq_s;asc  = 按发布日期升序
 * - gbrqStart/gbrqEnd: 公布日期范围
 * - sxrqStart/sxrqEnd: 施行日期范围
 * - xlwj: 效力位阶筛选
 * - sort: 是否排序（true/false）
 */
interface LawApiService {

    /**
     * 搜索法规列表
     * @param keyword 搜索关键词（对应 title 参数）
     * @param type 法规类型，默认 flfg
     * @param searchType 搜索方式，默认标题模糊
     * @param page 页码
     * @param size 每页条数
     * @param sortTr 排序
     * @param gbrqStart 公布日期起（yyyy-MM-dd）
     * @param gbrqEnd 公布日期止（yyyy-MM-dd）
     * @param sxrqStart 施行日期起（yyyy-MM-dd）
     * @param sxrqEnd 施行日期止（yyyy-MM-dd）
     * @param xlwj 效力位阶
     */
    @GET(".")
    suspend fun searchLaws(
        @Query("title") keyword: String? = null,
        @Query("type") type: String = "flfg",
        @Query("searchType") searchType: String = "title;vague",
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 10,
        @Query("sortTr") sortTr: String = "f_bbrq_s;desc",
        @Query("gbrqStart") gbrqStart: String? = null,
        @Query("gbrqEnd") gbrqEnd: String? = null,
        @Query("sxrqStart") sxrqStart: String? = null,
        @Query("sxrqEnd") sxrqEnd: String? = null,
        @Query("xlwj") xlwj: String? = null,
        @Query("sort") sort: Boolean = true
    ): LawSearchResponse

    /**
     * 获取法规详情（按 ID）
     */
    @GET(".")
    suspend fun getLawDetail(
        @Query("id") id: String
    ): LawDetailResponse
}
