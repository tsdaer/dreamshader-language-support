package com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2ElementTypes as E
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShader2Lexer
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes as T
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.*

/** Partial evaluation only: unknown external defines never imply an inactive branch. */
internal class DreamShaderPreprocessorModel private constructor(
    val issues: List<Issue>, private val states: List<Pair<Int, State>>,
    private val definitions: List<Pair<Int, Set<String>>>,
) {
    enum class State { ACTIVE, INACTIVE, UNKNOWN }
    data class Issue(val code: String, val range: TextRange, val message: String)
    fun stateAt(offset: Int): State = states.lastOrNull { it.first <= offset }?.second ?: State.ACTIVE
    fun definitionsAt(offset: Int): Set<String> = definitions.lastOrNull { it.first <= offset }?.second ?: emptySet()

    companion object {
        private val builtins = setOf("DS_ENGINE_MAJOR", "DS_ENGINE_MINOR", "DS_ENGINE_PATCH", "DS_SUBSTRATE", "DS_PLATFORM", "DS_PLUGIN_VERSION")
        private val KEY = Key.create<CachedValue<DreamShaderPreprocessorModel>>("dreamshader.preprocessor")
        fun of(file: DreamShaderPsiFile): DreamShaderPreprocessorModel = CachedValuesManager.getManager(file.project).getCachedValue(file, KEY, {
            val ignored = psiTreeWalk(file).filter { it.node.elementType == E.CUSTOM_BODY ||
                it is com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration && it.keywordText()?.lowercase() in setOf("function", "graphfunction") }.map { it.textRange }.toList()
            CachedValueProvider.Result.create(analyze(file.text, ignored), file)
        }, false)

        private data class Frame(val parent: State, var taken: State, var seenElse: Boolean, val range: TextRange)
        fun analyze(text: String, ignored: List<TextRange> = emptyList()): DreamShaderPreprocessorModel {
            val issues = mutableListOf<Issue>()
            val states = mutableListOf(0 to State.ACTIVE)
            val snapshots = mutableListOf<Pair<Int, Set<String>>>()
            val defines = mutableMapOf<String, String?>()
            val undefined = mutableSetOf<String>()
            val stack = ArrayDeque<Frame>()
            var state = State.ACTIVE
            val lexer = DreamShader2Lexer(); lexer.start(text)
            while (lexer.tokenType != null) {
                if (lexer.tokenType == T.PREPROCESSOR_DIRECTIVE && ignored.none { it.contains(lexer.tokenStart) }) {
                    val range = TextRange(lexer.tokenStart, text.indexOf('\n', lexer.tokenStart).let { if (it < 0) text.length else it })
                    val line = text.substring(range.startOffset, range.endOffset)
                    val directive = Regex("""^#\s*([A-Za-z_]\w*)(.*)$""").find(line)
                    val name = directive?.groupValues?.get(1).orEmpty()
                    val operand = directive?.groupValues?.get(2)?.let(::stripComment)?.trim().orEmpty()
                    fun issue(code: String, message: String) { issues += Issue(code, range, message) }
                    if (name in setOf("else", "endif") && operand.isNotEmpty()) issue("DSH1042", "Directive takes no trailing tokens.")
                    if (name in setOf("ifdef", "ifndef", "undef") && operand.matches(Regex("[A-Za-z_]\\w*\\s+.+"))) issue("DSH1042", "Only one define name is allowed.")
                    when (name) {
                        "if", "ifdef", "ifndef" -> {
                            if (operand.isEmpty()) issue("DSH1036", "Conditional directive requires an expression.")
                            val test = if (name == "if") condition(operand, defines, undefined) else {
                                val defined = when { operand in builtins -> State.ACTIVE
                                    operand in defines -> if (defines[operand] == null) State.UNKNOWN else State.ACTIVE
                                    operand in undefined -> State.INACTIVE; else -> State.UNKNOWN }
                                if (name == "ifndef") not(defined) else defined
                            }
                            stack.addLast(Frame(state, test, false, range))
                            state = and(state, test)
                            if (stack.size > 64) issue("DSH1037", "Conditional nesting exceeds 64 levels.")
                        }
                        "elif", "else" -> {
                            if (name == "elif" && operand.isEmpty()) issue("DSH1036", "Conditional directive requires an expression.")
                            val frame = stack.lastOrNull()
                            if (frame == null) issue("DSH1032", "Directive has no matching #if.")
                            else {
                                if (frame.seenElse) issue("DSH1033", "Directive follows #else.")
                                val test = if (name == "else") State.ACTIVE else condition(operand, defines, undefined)
                                state = and(frame.parent, and(not(frame.taken), test))
                                frame.taken = or(frame.taken, test)
                                frame.seenElse = frame.seenElse || name == "else"
                            }
                        }
                        "endif" -> if (stack.isEmpty()) issue("DSH1031", "#endif has no matching #if.")
                            else state = stack.removeLast().parent
                        "define", "undef" -> {
                            val variable = operand.substringBefore(' ').substringBefore('\t')
                            if (!variable.matches(Regex("[A-Za-z_]\\w*"))) issue("DSH1038", "Directive requires a valid define name.")
                            else if (state != State.INACTIVE) {
                                if (variable.startsWith("DS_")) issue("DSH1039", "DS_ names are reserved by DreamShader.")
                                else if (state == State.UNKNOWN) { defines[variable] = null; undefined.remove(variable) }
                                else if (name == "undef") { defines.remove(variable); undefined += variable }
                                else { defines[variable] = operand.removePrefix(variable).trim().ifEmpty { "1" }; undefined.remove(variable) }
                                snapshots += range.endOffset to defines.keys.toSet()
                            }
                        }
                        "pragma", "include", "region", "endregion", "Region", "EndRegion" -> Unit
                        else -> issue("DSH1035", "Unknown preprocessor directive #$name.")
                    }
                    states += range.endOffset to state
                }
                lexer.advance()
            }
            stack.forEach { issues += Issue("DSH1030", it.range, "Conditional block is missing #endif.") }
            return DreamShaderPreprocessorModel(issues, states, snapshots)
        }

        private fun stripComment(text: String): String {
            var quoted = false; var escaped = false
            for (index in text.indices) {
                val ch = text[index]
                if (escaped) { escaped = false; continue }
                if (quoted && ch == '\\') { escaped = true; continue }
                if (ch == '"') quoted = !quoted
                if (!quoted && text.startsWith("//", index)) return text.take(index)
            }
            return text
        }

        private fun not(state: State) = when (state) { State.ACTIVE -> State.INACTIVE; State.INACTIVE -> State.ACTIVE; else -> State.UNKNOWN }
        private fun and(a: State, b: State) = if (a == State.INACTIVE || b == State.INACTIVE) State.INACTIVE else if (a == State.ACTIVE && b == State.ACTIVE) State.ACTIVE else State.UNKNOWN
        private fun or(a: State, b: State) = if (a == State.ACTIVE || b == State.ACTIVE) State.ACTIVE else if (a == State.INACTIVE && b == State.INACTIVE) State.INACTIVE else State.UNKNOWN

        private fun condition(raw: String, defines: Map<String, String?>, undefined: Set<String>): State {
            val text = raw.trim()
            // Only split at top-level operators; quoted values and nested parentheses are opaque.
            var depth = 0; var quote = false; var escape = false
            for (i in text.indices) {
                val c = text[i]
                if (escape) { escape = false; continue }
                if (quote && c == '\\') { escape = true; continue }
                if (c == '"') { quote = !quote; continue }
                if (quote) continue
                if (c == '(') depth++ else if (c == ')') depth--
                if (depth == 0 && text.startsWith("||", i)) return or(condition(text.take(i), defines, undefined), condition(text.drop(i + 2), defines, undefined))
            }
            depth = 0; quote = false; escape = false
            for (i in text.indices) {
                val c = text[i]
                if (escape) { escape = false; continue }
                if (quote && c == '\\') { escape = true; continue }
                if (c == '"') { quote = !quote; continue }
                if (quote) continue
                if (c == '(') depth++ else if (c == ')') depth--
                if (depth == 0 && text.startsWith("&&", i)) return and(condition(text.take(i), defines, undefined), condition(text.drop(i + 2), defines, undefined))
            }
            if (text.startsWith('(') && text.endsWith(')') && balancedOuter(text)) return condition(text.drop(1).dropLast(1), defines, undefined)
            if (text.startsWith('!') && !text.startsWith("!=")) return not(condition(text.drop(1), defines, undefined))
            val defined = Regex("""defined\s*(?:\(\s*([A-Za-z_]\w*)\s*\)|\s+([A-Za-z_]\w*))""").matchEntire(text)
            if (defined != null) {
                val name = defined.groupValues[1].ifEmpty { defined.groupValues[2] }
                return when { name in builtins -> State.ACTIVE
                    name in defines -> if (defines[name] == null) State.UNKNOWN else State.ACTIVE
                    name in undefined -> State.INACTIVE; else -> State.UNKNOWN }
            }
            fun value(expr: String): String? = expr.trim().let { defines[it] ?: if (it in undefined) "0" else it.takeIf { rawValue ->
                rawValue.matches(Regex("-?\\d+|0[xX][0-9a-fA-F]+|\"[^\"]*\"")) } }
            val compare = Regex("""(.+?)\s*(==|!=|<=|>=|<|>)\s*(.+)""").matchEntire(text)
            if (compare != null) {
                val a = value(compare.groupValues[1]) ?: return State.UNKNOWN
                val b = value(compare.groupValues[3]) ?: return State.UNKNOWN
                val op = compare.groupValues[2]
                val result = if (a.startsWith('"') && b.startsWith('"') && op in setOf("==", "!=")) {
                    if (op == "==") a == b else a != b
                } else {
                    val x = integer(a) ?: return State.UNKNOWN; val y = integer(b) ?: return State.UNKNOWN
                    when (op) { "==" -> x == y; "!=" -> x != y; "<" -> x < y; ">" -> x > y; "<=" -> x <= y; else -> x >= y }
                }
                return if (result) State.ACTIVE else State.INACTIVE
            }
            return integer(value(text) ?: return State.UNKNOWN)?.let { if (it == 0L) State.INACTIVE else State.ACTIVE } ?: State.UNKNOWN
        }
        private fun integer(text: String): Long? = if (text.startsWith("0x", true)) text.drop(2).toLongOrNull(16) else text.toLongOrNull()
        private fun balancedOuter(text: String): Boolean {
            var depth = 0
            text.forEachIndexed { index, ch -> if (ch == '(') depth++ else if (ch == ')') depth--
                if (depth == 0 && index != text.lastIndex) return false }
            return depth == 0
        }
    }
}
