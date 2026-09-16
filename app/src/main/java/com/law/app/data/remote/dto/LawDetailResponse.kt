package com.law.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * 法规详情接口响应（新版 API）
 *
 * 端点: GET /law-search/search/flfgDetails?bbbs=xxx
 * 基地址: https://flk.npc.gov.cn/
 *
 * 响应示例:
 * {
 *   "code": 200,
 *   "msg": "操作成功",
 *   "data": {
 *     "bbbs": "xxx",
 *     "title": "中华人民共和国民法典",
 *     "gbrq": "2020-05-28",
 *     "sxrq": "2021-01-01",
 *     "sxx": 3,
 *     "zdjgName": "全国人民代表大会",
 *     "flxz": "法律",
 *     "ossFile": { ... },
 *     "content": { ... 目录树 ... }
 *   }
 * }
 */
data class LawDetailResponse(
    @SerializedName("code") val code: Int = 0,
    @SerializedName("msg") val msg: String? = null,
    @SerializedName("data") val data: LawDetailData? = null
) {
    val isSuccess: Boolean get() = code == 200
}

/**
 * 法规详情数据
 */
data class LawDetailData(
    @SerializedName("bbbs") val bbbs: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("gbrq") val gbrq: String? = null,
    @SerializedName("sxrq") val sxrq: String? = null,
    @SerializedName("sxx") val sxx: Int? = null,
    @SerializedName("zdjgName") val zdjgName: String? = null,
    @SerializedName("flxz") val flxz: String? = null,
    @SerializedName("zdjgCodeId") val zdjgCodeId: Int? = null,
    @SerializedName("flfgCodeId") val flfgCodeId: Int? = null,
    @SerializedName("ossFile") val ossFile: OssFile? = null,
    @SerializedName("content") val content: ContentNode? = null,
    @SerializedName("xgwj") val xgwj: List<Any>? = null,
    @SerializedName("xgzl") val xgzl: List<Any>? = null
)

/**
 * OSS 文件信息（用于下载 PDF/Word）
 */
data class OssFile(
    @SerializedName("ossWordPath") val ossWordPath: String? = null,
    @SerializedName("ossWordOfdPath") val ossWordOfdPath: String? = null,
    @SerializedName("ossWordOfdSize") val ossWordOfdSize: Long? = null,
    @SerializedName("ossPdfPath") val ossPdfPath: String? = null,
    @SerializedName("ossPdfOfdPath") val ossPdfOfdPath: String? = null,
    @SerializedName("ossPdfOfdSize") val ossPdfOfdSize: Long? = null
)

/**
 * 目录树节点（编 -> 章 -> 节 -> 条）
 *
 * 注意: 新版 API 只返回目录结构，不返回条文正文内容。
 * 正文内容需通过 PDF/OFD 文件查看。
 */
data class ContentNode(
    @SerializedName("id") val id: String? = null,
    @SerializedName("parentId") val parentId: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("index") val index: Int? = null,
    @SerializedName("children") val children: List<ContentNode>? = null
) {
    /** 是否为叶子节点（条文） */
    val isLeaf: Boolean get() = children.isNullOrEmpty()

    /** 递归获取所有条文（叶子节点） */
    fun getAllArticles(): List<ContentNode> {
        val result = mutableListOf<ContentNode>()
        fun traverse(node: ContentNode) {
            if (node.isLeaf && node.title?.startsWith("第") == true) {
                result.add(node)
            }
            node.children?.forEach { traverse(it) }
        }
        traverse(this)
        return result
    }

    /** 递归获取所有章节（非叶子节点，带层级） */
    fun getAllChapters(): List<Pair<Int, ContentNode>> {
        val result = mutableListOf<Pair<Int, ContentNode>>()
        fun traverse(node: ContentNode, depth: Int) {
            if (!node.isLeaf) {
                result.add(depth to node)
                node.children?.forEach { traverse(it, depth + 1) }
            }
        }
        traverse(this, 0)
        return result
    }
}

/**
 * 预览链接响应
 */
data class PreviewLinkResponse(
    @SerializedName("code") val code: Int = 0,
    @SerializedName("msg") val msg: String? = null,
    @SerializedName("data") val data: PreviewLinkData? = null
) {
    val isSuccess: Boolean get() = code == 200
}

data class PreviewLinkData(
    @SerializedName("url") val url: String? = null,
    @SerializedName("urlIn") val urlIn: String? = null
)
