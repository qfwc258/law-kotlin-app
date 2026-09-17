package com.law.app.util

/**
 * 法条文本转 Markdown 转换器
 *
 * 识别法条层级（编、章、节、条、款、项），自动转换为 Markdown 标题结构
 */
object LawMarkdownConverter {

    /**
     * 将纯文本法条转换为 Markdown
     */
    fun convertToMarkdown(title: String, text: String): String {
        if (text.isBlank()) return "# $title\n\n暂无内容"

        val lines = text.lines()
        val markdown = StringBuilder()

        // 标题
        markdown.append("# ").append(title).append("\n\n")

        var currentChapter = ""
        var currentSection = ""

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                markdown.append("\n")
                continue
            }

            // 识别层级
            when {
                // 编
                Regex("""^第[一二三四五六七八九十百千零\d]+编""").containsMatchIn(trimmed) -> {
                    currentChapter = trimmed
                    markdown.append("\n## ").append(trimmed).append("\n\n")
                }
                // 章
                Regex("""^第[一二三四五六七八九十百千零\d]+章""").containsMatchIn(trimmed) -> {
                    currentChapter = trimmed
                    markdown.append("\n### ").append(trimmed).append("\n\n")
                }
                // 节
                Regex("""^第[一二三四五六七八九十百千零\d]+节""").containsMatchIn(trimmed) -> {
                    currentSection = trimmed
                    markdown.append("\n#### ").append(trimmed).append("\n\n")
                }
                // 条
                Regex("""^第[一二三四五六七八九十百千零\d]+条""").containsMatchIn(trimmed) -> {
                    markdown.append("\n**").append(trimmed).append("**\n\n")
                }
                // 款（以数字开头，如"一、""二、"或"1.""2."）
                Regex("""^[一二三四五六七八九十]+[、．.]""").containsMatchIn(trimmed) ||
                Regex("""^\d+[、．.]""").containsMatchIn(trimmed) -> {
                    markdown.append("- ").append(trimmed).append("\n")
                }
                // 项（以"（一）""（二）"开头）
                Regex("""^[（(][一二三四五六七八九十\d]+[）)]""").containsMatchIn(trimmed) -> {
                    markdown.append("  - ").append(trimmed).append("\n")
                }
                // 目录
                trimmed == "目 录" || trimmed == "目录" -> {
                    markdown.append("\n---\n\n## 目录\n\n")
                }
                // 普通文本
                else -> {
                    markdown.append(trimmed).append("\n\n")
                }
            }
        }

        return markdown.toString()
    }

    /**
     * 从 OFD 解析后的文本块列表中提取纯文本
     */
    fun extractTextFromBlocks(blocks: List<TextBlock>): String {
        if (blocks.isEmpty()) return ""

        // 按 Y 坐标排序（从上到下），同一行按 X 坐标排序（从左到右）
        val sorted = blocks.sortedWith(compareBy<TextBlock> { it.y }.thenBy { it.x })

        val text = StringBuilder()
        var lastY = -1.0
        val lineThreshold = 5.0 // 同一行的 Y 坐标阈值

        for (block in sorted) {
            if (lastY >= 0 && Math.abs(block.y - lastY) > lineThreshold) {
                text.append("\n")
            }
            text.append(block.text)
            lastY = block.y
        }

        return text.toString()
    }

    /**
     * 文本块（OFD 解析后的文本单元）
     */
    data class TextBlock(
        val text: String,
        val x: Double,
        val y: Double,
        val width: Double = 0.0,
        val height: Double = 0.0
    )
}
