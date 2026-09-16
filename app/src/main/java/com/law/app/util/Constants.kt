package com.law.app.util

/**
 * 全局常量
 */
object Constants {
    /** 国家法律法规数据库 API 基地址（新版 SPA 站点） */
    const val BASE_URL = "https://flk.npc.gov.cn/"

    /** 官网首页 */
    const val OFFICIAL_URL = "https://flk.npc.gov.cn/"

    /** User-Agent（模拟 Edge 129 浏览器，避免被拦截和下载限制） */
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36 EdgA/129.0.0.0"

    /** Referer（新版 API 需要校验来源） */
    const val REFERER = "https://flk.npc.gov.cn/"

    /** 每页默认条数 */
    const val PAGE_SIZE = 10

    /** 最近阅读最大条数 */
    const val RECENT_LIMIT = 20

    /** 数据库名称 */
    const val DB_NAME = "law_database"

    // 法规类型常量（新版 API 用 flfgCodeId 数字编码）
    const val TYPE_LAW = "flfg"           // 法律法规
    const val TYPE_ADMIN = "xzfg"         // 行政法规
    const val TYPE_SUPERVISION = "jcfg"   // 监察法规
    const val TYPE_JUDICIAL = "sfjs"      // 司法解释
    const val TYPE_LOCAL = "dfxfg"         // 地方性法规
    const val TYPE_INTERPRETATION = "fljs" // 法律解释
}
