package com.github.tsdaer.dreamshaderlanguagesupport.language.refactoring

import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderLanguageKeywords
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderLexer
import com.intellij.lang.refactoring.NamesValidator
import com.intellij.openapi.project.Project
import java.util.Locale

class DreamShaderNamesValidator : NamesValidator {
    override fun isKeyword(name: String, project: Project?): Boolean {
        return name.lowercase(Locale.ROOT) in KEYWORDS
    }

    override fun isIdentifier(name: String, project: Project?): Boolean {
        return IDENTIFIER_REGEX.matches(name) && !isKeyword(name, project)
    }

    companion object {
        private val IDENTIFIER_REGEX = Regex("[A-Za-z_][A-Za-z0-9_]*")

        private val KEYWORDS = (
            DreamShaderLanguageKeywords.TOP_LEVEL_KEYWORDS +
                DreamShaderLanguageKeywords.SECTION_KEYWORDS +
                DreamShaderLanguageKeywords.DECLARATION_KEYWORDS +
                DreamShaderLanguageKeywords.FUNCTION_LIKE_DECLARATION_KEYWORDS +
                listOf(
                    "import",
                    "in",
                    "out",
                    "inout",
                    "const",
                    "static",
                    "opt",
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
                    "true",
                    "false"
                ) +
                DreamShaderLexer.TYPES
            )
            .map { it.lowercase(Locale.ROOT) }
            .toSet()
    }
}
