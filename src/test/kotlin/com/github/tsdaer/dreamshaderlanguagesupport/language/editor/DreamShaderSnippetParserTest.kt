package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DreamShaderSnippetParserTest {
    @Test
    fun parsesSubstrateSlabSnippetWithNestedParenDefaults() {
        val snippet =
            "Substrate.Slab(DiffuseAlbedo=\${1:Color}, F0=\${2:float3(0.04, 0.04, 0.04)}, " +
                "Roughness=\${3:0.45}, Normal=\${4:Normal})"
        val segments = DreamShaderSnippetParser.parse(snippet)

        val stops = segments.filterIsInstance<DreamShaderSnippetParser.Stop>()
        assertEquals(listOf(1, 2, 3, 4), stops.map { it.index })
        assertEquals("Color", stops[0].default)
        // 含括号与逗号的嵌套默认值必须完整保留。
        assertEquals("float3(0.04, 0.04, 0.04)", stops[1].default)
        assertEquals("0.45", stops[2].default)
        assertEquals("Normal", stops[3].default)

        // 重新拼接字面量与默认值应还原原始 snippet。
        val rebuilt = buildString {
            segments.forEach {
                when (it) {
                    is DreamShaderSnippetParser.Literal -> append(it.text)
                    is DreamShaderSnippetParser.Stop -> append("\${${it.index}:${it.default}}")
                }
            }
        }
        assertEquals(snippet, rebuilt)
    }

    @Test
    fun parsesEndStopAndBareIndex() {
        val segments = DreamShaderSnippetParser.parse("UE.Add(A=\${1:Value}, B=\$2)\$0")
        val stops = segments.filterIsInstance<DreamShaderSnippetParser.Stop>()
        assertEquals(listOf(1, 2, 0), stops.map { it.index })
        assertEquals("Value", stops[0].default)
        assertEquals("", stops[1].default)
        assertEquals("", stops[2].default)
    }

    @Test
    fun treatsMalformedDollarAsLiteral() {
        // `${oops}` 无数字、结尾孤立 `$` 均非合法占位，应原样作为字面量保留。
        val segments = DreamShaderSnippetParser.parse("see \${oops} and trailing\$")
        assertTrue(segments.all { it is DreamShaderSnippetParser.Literal })
        val text = segments.filterIsInstance<DreamShaderSnippetParser.Literal>().joinToString("") { it.text }
        assertEquals("see \${oops} and trailing\$", text)
    }
}
