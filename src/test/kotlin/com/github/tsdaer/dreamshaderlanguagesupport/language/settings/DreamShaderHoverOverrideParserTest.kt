package com.github.tsdaer.dreamshaderlanguagesupport.language.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DreamShaderHoverOverrideParserTest : BasePlatformTestCase() {
    fun testParserAcceptsValidEntriesAndComments() {
        val parsed = DreamShaderHoverOverrideParser.parse(
            """
            # comment
            declaration.function.description=Function docs
            settings.domain.description=Domain docs
            """.trimIndent()
        )

        assertEquals(2, parsed.entries.size)
        assertEquals("Function docs", parsed.entries["declaration.function.description"])
        assertEquals("Domain docs", parsed.entries["settings.domain.description"])
        assertEquals(1, parsed.ignoredLineCount)
        assertEquals(0, parsed.duplicateKeyCount)
        assertTrue(parsed.issues.isEmpty())
    }

    fun testParserKeepsLastValueForDuplicateKeys() {
        val parsed = DreamShaderHoverOverrideParser.parse(
            """
            declaration.function.description=first
            declaration.function.description=second
            """.trimIndent()
        )

        assertEquals(1, parsed.entries.size)
        assertEquals("second", parsed.entries["declaration.function.description"])
        assertEquals(1, parsed.duplicateKeyCount)
        assertTrue(parsed.issues.isEmpty())
    }

    fun testParserCollectsFormatIssues() {
        val parsed = DreamShaderHoverOverrideParser.parse(
            """
            invalid line
            =value
            declaration.function.description=
            """.trimIndent()
        )

        assertEquals(0, parsed.entries.size)
        assertEquals(3, parsed.issues.size)
        assertEquals(DreamShaderHoverOverrideIssueType.MISSING_EQUALS, parsed.issues[0].type)
        assertEquals(1, parsed.issues[0].lineNumber)
        assertEquals(DreamShaderHoverOverrideIssueType.EMPTY_KEY, parsed.issues[1].type)
        assertEquals(2, parsed.issues[1].lineNumber)
        assertEquals(DreamShaderHoverOverrideIssueType.EMPTY_VALUE, parsed.issues[2].type)
        assertEquals(3, parsed.issues[2].lineNumber)
    }
}
