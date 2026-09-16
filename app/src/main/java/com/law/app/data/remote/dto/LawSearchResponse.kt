package com.law.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * 国家法律法规数据库搜索接口响应
 * 基地址: https://flk.npc.gov.cn/api/
 */
data class LawSearchResponse(
    @SerializedName("code") val code: Int = 0,
    @SerializedName("msg") val msg: String? = null,
    @SerializedName("data") val data: LawSearchData? = null
) {
    val isSuccess: Boolean get() = code == 200
}

data class LawSearchData(
    @SerializedName("total") val total: Int = 0,
    @SerializedName("list") val list: List<LawRecordDto> = emptyList()
)

/**
 * 单条法规记录（搜索列表项）
 */
data class LawRecordDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("xlwj") val xlwj: String? = null,
    @SerializedName("f_bbrq_s") val publishDate: String? = null,
    @SerializedName("f_sxrq_s") val effectiveDate: String? = null,
    @SerializedName("f_bmgj_s") val issuingAuthority: String? = null,
    @SerializedName("f_yj_s") val summary: String? = null,
    @SerializedName("f_nr_s") val content: String? = null,
    @SerializedName("f_wjbh_s") val documentNumber: String? = null,
    @SerializedName("f_xlsj_s") val revisionDate: String? = null,
    @SerializedName("url") val detailUrl: String? = null,
    @SerializedName("pdfUrl") val pdfUrl: String? = null,
    @SerializedName("wpsUrl") val wpsUrl: String? = null
)
