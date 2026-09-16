package com.law.app

import com.law.app.data.model.Law
import com.law.app.data.model.LawType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 法规模型单元测试
 */
class LawModelTest {

    @Test
    fun `extractArticles returns empty list for blank content`() {
        val law = Law(id = "1", title = "测试法", content = "")
        assertTrue(law.extractArticles().isEmpty())
    }

    @Test
    fun `extractArticles parses multiple articles correctly`() {
        val content = """
            第一条 为了规范立法活动，健全国家立法制度，根据宪法，制定本法。
            第二条 法律、行政法规、地方性法规、自治条例和单行条例的制定、修改和废止，适用本法。
            第三条 立法应当遵循宪法的基本原则，以经济建设为中心，坚持社会主义道路。
        """.trimIndent()
        val law = Law(id = "2", title = "立法法", content = content)
        val articles = law.extractArticles()
        assertEquals(3, articles.size)
        assertEquals("第一条", articles[0].number)
        assertTrue(articles[0].text.contains("规范立法活动"))
        assertEquals("第三条", articles[2].number)
    }

    @Test
    fun `extractArticles returns full content when no article pattern found`() {
        val content = "这是一段没有条文编号的法规内容。"
        val law = Law(id = "3", title = "测试", content = content)
        val articles = law.extractArticles()
        assertEquals(1, articles.size)
        assertEquals(content, articles[0].text)
    }

    @Test
    fun `LawType fromCode maps correctly`() {
        assertEquals(LawType.LAW, LawType.fromCode("flfg"))
        assertEquals(LawType.ADMIN, LawType.fromCode("xzfg"))
        assertEquals(LawType.JUDICIAL, LawType.fromCode("sfjs"))
        assertEquals(LawType.ALL, LawType.fromCode("unknown"))
    }

    @Test
    fun `hasContent returns false for blank content`() {
        val law = Law(id = "4", title = "测试", content = "   ")
        assertTrue(!law.hasContent)
    }
}
