package com.law.app.data.model

/**
 * 法规类型（对应国家法律法规数据库的 type 参数）
 */
enum class LawType(val code: String, val displayName: String) {
    ALL("", "全部"),
    LAW("flfg", "法律"),
    ADMIN("xzfg", "行政法规"),
    SUPERVISION("jcfg", "监察法规"),
    JUDICIAL("sfjs", "司法解释"),
    LOCAL("dfxfg", "地方性法规"),
    INTERPRETATION("fljs", "法律解释");

    companion object {
        fun fromCode(code: String): LawType =
            entries.find { it.code == code } ?: ALL
    }
}

/**
 * 搜索方式
 */
enum class SearchMode(val param: String, val displayName: String) {
    FUZZY("title;vague", "模糊搜索"),
    ACCURATE("title;accurate", "精确搜索");
}

/**
 * 排序方式
 */
enum class SortOrder(val param: String, val displayName: String) {
    PUBLISH_DESC("f_bbrq_s;desc", "发布日期↓"),
    PUBLISH_ASC("f_bbrq_s;asc", "发布日期↑"),
    EFFECTIVE_DESC("f_sxrq_s;desc", "施行日期↓"),
    EFFECTIVE_ASC("f_sxrq_s;asc", "施行日期↑");
}
