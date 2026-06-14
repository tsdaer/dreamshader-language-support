package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.impl.ConstantNode

/**
 * 解析 LSP/TextMate 风格的 snippet（`${N:default}` / `$N` / `$0`）并装配到 IntelliJ
 * 活动模板，使 `Substrate.*`、`UE.*` 等补全插入后可通过 Tab 在占位符间跳转。
 *
 * 解析逻辑保持纯 Kotlin（不依赖 IDE），便于单元测试；[fill] 是面向 [Template] 的薄适配层。
 */
internal object DreamShaderSnippetParser {
    /** snippet 解析后的片段。 */
    internal sealed interface Seg

    /** 字面量文本片段。 */
    internal data class Literal(val text: String) : Seg

    /** 占位停靠点；[index] 为 0 表示模板结束停靠点（`$0`）。 */
    internal data class Stop(val index: Int, val default: String) : Seg

    /**
     * 将 snippet 解析为字面量与停靠点片段序列。
     *
     * - `${N:default}`：读取数字 N 与默认值，默认值按花括号深度匹配，正确处理嵌套
     *   `${...}` 以及默认值中出现的 `(`、`)`、`,`、`.`（如 `float3(0.04, 0.04, 0.04)`）。
     * - `$N` / `$0`：无默认值的停靠点。
     * - 其余字符累积为 [Literal]。
     */
    internal fun parse(snippet: String): List<Seg> {
        val segments = mutableListOf<Seg>()
        val literal = StringBuilder()
        var i = 0
        val n = snippet.length

        fun flushLiteral() {
            if (literal.isNotEmpty()) {
                segments.add(Literal(literal.toString()))
                literal.setLength(0)
            }
        }

        while (i < n) {
            val ch = snippet[i]
            if (ch == '$' && i + 1 < n) {
                val next = snippet[i + 1]
                when {
                    next == '{' -> {
                        val parsed = parseBraced(snippet, i + 2)
                        if (parsed != null) {
                            flushLiteral()
                            segments.add(parsed.first)
                            i = parsed.second
                            continue
                        }
                    }
                    next.isDigit() -> {
                        var j = i + 1
                        while (j < n && snippet[j].isDigit()) j++
                        val index = snippet.substring(i + 1, j).toIntOrNull()
                        if (index != null) {
                            flushLiteral()
                            segments.add(Stop(index, ""))
                            i = j
                            continue
                        }
                    }
                }
            }
            literal.append(ch)
            i++
        }
        flushLiteral()
        return segments
    }

    /**
     * 解析 `${` 之后的内容（[start] 指向数字首字符）。成功返回 [Stop] 与结束 `}` 之后的下标，
     * 语法不符（无数字 / 括号不闭合）时返回 null，由调用方退化为字面量。
     */
    private fun parseBraced(snippet: String, start: Int): Pair<Stop, Int>? {
        val n = snippet.length
        var j = start
        while (j < n && snippet[j].isDigit()) j++
        if (j == start) return null
        val index = snippet.substring(start, j).toIntOrNull() ?: return null

        if (j < n && snippet[j] == '}') {
            return Stop(index, "") to (j + 1)
        }
        if (j >= n || snippet[j] != ':') return null

        // 读取默认值：按花括号深度匹配到对应 `}`。
        var depth = 1
        val default = StringBuilder()
        var k = j + 1
        while (k < n) {
            val c = snippet[k]
            when (c) {
                '{' -> { depth++; default.append(c) }
                '}' -> {
                    depth--
                    if (depth == 0) return Stop(index, default.toString()) to (k + 1)
                    default.append(c)
                }
                else -> default.append(c)
            }
            k++
        }
        return null
    }

    /**
     * 将 snippet 装配到 [template]：字面量转文本段；`$0` 转结束停靠点；其余转带默认值的变量。
     * 若 snippet 中不含 `$0`，模板自然以最后一段结尾作为最终光标位。
     */
    fun fill(template: Template, snippet: String) {
        for (segment in parse(snippet)) {
            when (segment) {
                is Literal -> template.addTextSegment(segment.text)
                is Stop -> if (segment.index == 0) {
                    template.addEndVariable()
                } else {
                    template.addVariable(
                        "VAR${segment.index}",
                        ConstantNode(segment.default),
                        ConstantNode(segment.default),
                        true
                    )
                }
            }
        }
    }
}
