package com.law.app.util

/**
 * 全局常量
 */
object Constants {
    /** 国家法律法规数据库 API 基地址 */
    const val BASE_URL = "https://flk.npc.gov.cn/api/"

    /** 官网首页 */
    const val OFFICIAL_URL = "https://flk.npc.gov.cn/"

    /** User-Agent */
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) LawKotlinApp/1.0.0"

    /** 每页默认条数 */
    const val PAGE_SIZE = 10

    /** 最近阅读最大条数 */
    const val RECENT_LIMIT = 20

    /** 数据库名称 */
    const val DB_NAME = "law_database"

    // 法规类型常量
    const val TYPE_LAW = "flfg"           // 法律法规
    const val TYPE_ADMIN = "xzfg"         // 行政法规
    const val TYPE_SUPERVISION = "jcfg"   // 监察法规
    const val TYPE_JUDICIAL = "sfjs"      // 司法解释
    const val TYPE_LOCAL = "dfxfg"         // 地方性法规
    const val TYPE_INTERPRETATION = "fljs" // 法律解释
}
