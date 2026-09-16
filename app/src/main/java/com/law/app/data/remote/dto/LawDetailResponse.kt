package com.law.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * 法规详情接口响应
 */
data class LawDetailResponse(
    @SerializedName("code") val code: Int = 0,
    @SerializedName("msg") val msg: String? = null,
    @SerializedName("data") val data: LawDetailData? = null
) {
    val isSuccess: Boolean get() = code == 200
}

data class LawDetailData(
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
    @SerializedName("pdfUrl") val pdfUrl: String? = null,
    @SerializedName("wpsUrl") val wpsUrl: String? = null
)
