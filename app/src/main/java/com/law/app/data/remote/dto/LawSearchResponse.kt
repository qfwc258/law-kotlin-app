package com.law.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * 国家法律法规数据库 - 搜索接口响应（新版 API）
 *
 * 端点: POST /law-search/search/list
 * 基地址: https://flk.npc.gov.cn/
 *
 * 请求体示例:
 * {
 *   "searchRange": 1,
 *   "searchType": 2,
 *   "searchContent": "民法典",
 *   "pageNum": 1,
 *   "pageSize": 10
 * }
 *
 * 响应示例:
 * {
 *   "total": 880,
 *   "rows": [...],
 *   "code": 200,
 *   "msg": "查询成功"
 * }
 */
data class LawSearchResponse(
    @SerializedName("code") val code: Int = 0,
    @SerializedName("msg") val msg: String? = null,
    @SerializedName("total") val total: Int = 0,
    @SerializedName("rows") val rows: List<LawRecordDto> = emptyList()
) {
    val isSuccess: Boolean get() = code == 200
}

/**
 * 搜索请求体（简化版，只传必要字段，避免空 List 导致 Gson 序列化问题）
 */
data class LawSearchRequest(
    @SerializedName("searchRange") val searchRange: Int = 1,
    @SerializedName("searchType") val searchType: Int = 2,
    @SerializedName("searchContent") val searchContent: String = "",
    @SerializedName("pageNum") val pageNum: Int = 1,
    @SerializedName("pageSize") val pageSize: Int = 10
)

/**
 * 单条法规记录（搜索列表项，新版 API）
 *
 * 字段说明:
 * - bbbs: 唯一标识（详情页用此 ID 查询）
 * - title: 标题（含 <em class='highlight'> 高亮标签，需清理）
 * - gbrq: 公布日期 (yyyy-MM-dd)
 * - sxrq: 施行日期 (yyyy-MM-dd)
 * - sxx: 效力层级 (1=已修改, 2=已废止, 3=现行有效, null=其他)
 * - zdjgName: 制定机关名称
 * - flxz: 效力位阶（法律、行政法规等）
 * - zdjgCodeId: 制定机关编码
 * - flfgCodeId: 法律法规编码
 * - score: 搜索相关度分数
 */
data class LawRecordDto(
    @SerializedName("bbbs") val bbbs: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("gbrq") val gbrq: String? = null,
    @SerializedName("sxrq") val sxrq: String? = null,
    @SerializedName("sxx") val sxx: Int? = null,
    @SerializedName("zdjgName") val zdjgName: String? = null,
    @SerializedName("flxz") val flxz: String? = null,
    @SerializedName("zdjgCodeId") val zdjgCodeId: Int? = null,
    @SerializedName("flfgCodeId") val flfgCodeId: Int? = null,
    @SerializedName("score") val score: Double? = null
)
