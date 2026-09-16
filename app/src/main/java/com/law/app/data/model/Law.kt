package com.law.app.data.model

/**
 * 法规领域模型（UI 层使用）
 */
data class Law(
    val id: String,
    val title: String,
    val type: LawType = LawType.LAW,
    val typeName: String = "",
    val lawLevel: String = "",
    val issuingAuthority: String = "",
    val publishDate: String = "",
    val effectiveDate: String = "",
    val documentNumber: String = "",
    val summary: String = "",
    val content: String = "",
    val pdfUrl: String? = null,
    val wpsUrl: String? = null,
    val detailUrl: String? = null,
    // 本地属性
    val isFavorite: Boolean = false,
    val lastReadTime: Long = 0L
) {
    /** 是否有正文内容 */
    val hasContent: Boolean get() = content.isNotBlank()

    /** 从正文中按"第X条"切分条文列表 */
    fun extractArticles(): List<Article> {
        if (content.isBlank()) return emptyList()
        val articleRegex = Regex("""第[一二三四五六七八九十百千零\d]+条""")
        val matches = articleRegex.findAll(content).toList()
        if (matches.isEmpty()) return listOf(Article(number = "", text = content.trim()))

        return matches.mapIndexed { index, match ->
            val start = match.range.first
            val end = if (index + 1 < matches.size) matches[index + 1].range.first else content.length
            val fullText = content.substring(start, end).trim()
            val number = match.value
            val text = fullText.removePrefix(number).trim()
            Article(number = number, text = text)
        }
    }
}

/**
 * 单条法条
 */
data class Article(
    val number: String,
    val text: String
)
