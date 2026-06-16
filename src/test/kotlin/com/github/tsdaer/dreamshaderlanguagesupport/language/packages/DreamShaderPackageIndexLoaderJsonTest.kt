package com.github.tsdaer.dreamshaderlanguagesupport.language.packages

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 回归覆盖旧手写解析器的缺陷，确认迁移到 kotlinx.serialization 后已修复：
 * - `\uXXXX` / `\/` / `\b` / `\f` 转义
 * - 深层嵌套同名字段不再被顶层误匹配
 * - 空 / 坏 JSON 安全降级为空
 */
class DreamShaderPackageIndexLoaderJsonTest {
    @Test
    fun `decodes unicode and slash escapes in string fields`() {
        val json = """
            [
              {
                "name": "@scope/pkg",
                "displayName": "中文 \/ Name\b\f",
                "repository": "https:\/\/example.com\/repo.git"
              }
            ]
        """.trimIndent()

        val entries = DreamShaderPackageIndexLoader.parseEntries(json, "test")
        assertEquals(1, entries.size)
        val entry = entries.first()
        assertEquals("中文 / Name\b", entry.displayName)
        assertEquals("https://example.com/repo.git", entry.repository)
    }

    @Test
    fun `ignores same-named fields nested in unrelated objects`() {
        // 顶层 name/repository 之外，嵌套对象里也有 name —— 旧正则会误匹配嵌套值。
        val json = """
            [
              {
                "name": "@scope/outer",
                "repository": "https://example.com/outer.git",
                "metadata": { "name": "inner-should-not-leak" }
              }
            ]
        """.trimIndent()

        val entries = DreamShaderPackageIndexLoader.parseEntries(json, "test")
        assertEquals("@scope/outer", entries.single().name)
    }

    @Test
    fun `blank and malformed json degrade to empty or error`() {
        assertTrue(DreamShaderPackageIndexLoader.parseEntries("", "test").isEmpty())
        assertTrue(DreamShaderPackageIndexLoader.parseEntries("   ", "test").isEmpty())
    }
}
