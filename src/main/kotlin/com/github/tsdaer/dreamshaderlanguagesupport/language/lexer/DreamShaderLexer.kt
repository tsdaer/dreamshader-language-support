package com.github.tsdaer.dreamshaderlanguagesupport.language.lexer
import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType
import java.util.*

/**
 * Implementation of DreamShaderLexer.
 */
class DreamShaderLexer : LexerBase() {
    private var buffer: CharSequence = ""
    private var startOffset: Int = 0
    private var endOffset: Int = 0
    private var state: Int = 0
    private var tokenStart: Int = 0
    private var tokenEnd: Int = 0
    private var tokenType: IElementType? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.startOffset = startOffset
        this.endOffset = endOffset
        this.state = initialState
        tokenStart = startOffset
        tokenEnd = startOffset
        tokenType = null
        advance()
    }

    override fun getState(): Int = state

    override fun getTokenType(): IElementType? = tokenType

    override fun getTokenStart(): Int = tokenStart

    override fun getTokenEnd(): Int = tokenEnd

    override fun getBufferSequence(): CharSequence = buffer

    override fun getBufferEnd(): Int = endOffset

    override fun advance() {
        if (tokenEnd >= endOffset) {
            tokenType = null
            return
        }

        val i = tokenEnd
        tokenStart = i
        val c = buffer[i]

        if (c.isWhitespace()) {
            var j = i + 1
            while (j < endOffset && buffer[j].isWhitespace()) {
                j++
            }
            tokenEnd = j
            tokenType = DreamShaderTokenTypes.WHITE_SPACE
            return
        }

        if (c == '/' && i + 1 < endOffset) {
            val n = buffer[i + 1]
            if (n == '/') {
                var j = i + 2
                while (j < endOffset && buffer[j] != '\n' && buffer[j] != '\r') {
                    j++
                }
                tokenEnd = j
                tokenType = DreamShaderTokenTypes.LINE_COMMENT
                return
            }
            if (n == '*') {
                var j = i + 2
                while (j + 1 < endOffset && !(buffer[j] == '*' && buffer[j + 1] == '/')) {
                    j++
                }
                tokenEnd = if (j + 1 < endOffset) j + 2 else endOffset
                tokenType = DreamShaderTokenTypes.BLOCK_COMMENT
                return
            }
        }

        if (c == '"') {
            var j = i + 1
            while (j < endOffset) {
                val ch = buffer[j]
                if (ch == '\\' && j + 1 < endOffset) {
                    j += 2
                    continue
                }
                if (ch == '"') {
                    j++
                    break
                }
                j++
            }
            tokenEnd = j
            tokenType = DreamShaderTokenTypes.STRING
            return
        }

        if (c.isDigit() || (c == '.' && i + 1 < endOffset && buffer[i + 1].isDigit())) {
            var j = i
            if (buffer[j] == '0' && j + 2 < endOffset && (buffer[j + 1] == 'x' || buffer[j + 1] == 'X') && buffer[j + 2].isHexDigit()) {
                j += 2
                while (j < endOffset && buffer[j].isHexDigit()) {
                    j++
                }
                tokenEnd = j
                tokenType = DreamShaderTokenTypes.NUMBER
                return
            }
            if (buffer[j] == '.') {
                j++
            }
            while (j < endOffset && buffer[j].isDigit()) {
                j++
            }
            if (j < endOffset && buffer[j] == '.') {
                j++
                while (j < endOffset && buffer[j].isDigit()) {
                    j++
                }
            }
            if (j < endOffset && (buffer[j] == 'f' || buffer[j] == 'F')) {
                j++
            }
            tokenEnd = j
            tokenType = DreamShaderTokenTypes.NUMBER
            return
        }

        if (isIdentifierStart(c)) {
            var j = i + 1
            while (j < endOffset && isIdentifierPart(buffer[j])) {
                j++
            }
            tokenEnd = j
            val word = buffer.subSequence(i, j).toString()
            tokenType = classifyIdentifier(word)
            return
        }

        val braceType = classifyBrace(c)
        if (braceType != null) {
            tokenEnd = i + 1
            tokenType = braceType
            return
        }

        if (isOperatorOrPunctuation(c)) {
            tokenEnd = i + 1
            tokenType = DreamShaderTokenTypes.OPERATOR
            return
        }

        tokenEnd = i + 1
        tokenType = DreamShaderTokenTypes.BAD_CHARACTER
    }

    private fun classifyIdentifier(word: String): IElementType {
        val lowered = word.lowercase(Locale.ROOT)
        if (KEYWORDS.contains(lowered)) return DreamShaderTokenTypes.KEYWORD
        if (SECTIONS.contains(lowered)) return DreamShaderTokenTypes.SECTION
        if (TYPES.contains(lowered)) return DreamShaderTokenTypes.TYPE
        return DreamShaderTokenTypes.IDENTIFIER
    }

    private fun isIdentifierStart(c: Char): Boolean = c == '_' || c.isLetter()

    private fun isIdentifierPart(c: Char): Boolean = c == '_' || c.isLetterOrDigit()

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private fun classifyBrace(c: Char): IElementType? {
        return when (c) {
            '(' -> DreamShaderTokenTypes.LPAREN
            ')' -> DreamShaderTokenTypes.RPAREN
            '[' -> DreamShaderTokenTypes.LBRACKET
            ']' -> DreamShaderTokenTypes.RBRACKET
            '{' -> DreamShaderTokenTypes.LBRACE
            '}' -> DreamShaderTokenTypes.RBRACE
            else -> null
        }
    }

    private fun isOperatorOrPunctuation(c: Char): Boolean {
        return c == '=' || c == '+' || c == '-' || c == '*' || c == '/' || c == '?' ||
            c == '.' || c == ',' || c == ';' || c == ':'
    }

    companion object {
        private val KEYWORDS = setOf(
            "import",
            "shader",
            "namespace",
            "function",
            "graphfunction",
            "shaderfunction",
            "shaderlayer",
            "shaderlayerblend",
            "virtualfunction",
            "selfcontained",
            "inline",
            "if",
            "else",
            "for",
            "while",
            "do",
            "switch",
            "case",
            "default",
            "break",
            "continue",
            "return",
            "const",
            "static",
            "in",
            "out",
            "inout",
            "struct",
            "opt",
            "true",
            "false"
        )

        private val SECTIONS = setOf(
            "properties",
            "inputs",
            "outputs",
            "results",
            "settings",
            "options",
            "graph"
        )

        internal val TYPES = setOf(
            "void",
            "float", "float1", "float2", "float3", "float4", "float2x2", "float3x3", "float4x4",
            "half", "half1", "half2", "half3", "half4",
            "int", "int2", "int3", "int4",
            "uint", "uint2", "uint3", "uint4",
            "bool", "bool2", "bool3", "bool4",
            "vec2", "vec3", "vec4",
            "ivec2", "ivec3", "ivec4",
            "uvec2", "uvec3", "uvec4",
            "bvec2", "bvec3", "bvec4",
            "mat2", "mat3", "mat4",
            "texture2d", "texturecube", "texture2darray", "texture3d", "volumetexture", "samplerstate",
            "materialattributes", "substrate",
            "scalarparameter", "vectorparameter", "doublevectorparameter",
            "staticboolparameter", "staticswitchparameter",
            "textureobjectparameter",
            "texturesampleparameter2d", "texturesampleparameter2darray",
            "texturesampleparametercube", "texturesampleparametercubearray",
            "texturesampleparametervolume", "texturesampleparametersubuv",
            "runtimevirtualtexturesampleparameter",
            "sparsevolumetexturesampleparameter", "sparsevolumetextureobjectparameter",
            "channelmaskparameter", "staticcomponentmaskparameter",
            "texturecollectionparameter", "curveatlasrowparameter",
            "dynamicparameter", "fontsampleparameter", "spritetexturesampler"
        )
    }
}
