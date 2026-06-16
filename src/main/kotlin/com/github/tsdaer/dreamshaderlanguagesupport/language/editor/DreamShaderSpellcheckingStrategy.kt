package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderSection
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.spellchecker.inspections.CommentSplitter
import com.intellij.spellchecker.inspections.PlainTextSplitter
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy
import com.intellij.spellchecker.tokenizer.TokenConsumer
import com.intellij.spellchecker.tokenizer.Tokenizer

class DreamShaderSpellcheckingStrategy : SpellcheckingStrategy() {
    override fun getTokenizer(element: PsiElement?): Tokenizer<out PsiElement> {
        if (element == null) return EMPTY_TOKENIZER
        return when {
            isCommentToken(element) -> myCommentTokenizer
            element.node?.elementType == DreamShaderTokenTypes.STRING && isSpellcheckableDescriptionString(element) ->
                descriptionTokenizer
            else -> EMPTY_TOKENIZER
        }
    }

    internal fun isSpellcheckableDescriptionString(element: PsiElement): Boolean {
        if (element.node?.elementType != DreamShaderTokenTypes.STRING) return false
        val section = PsiTreeUtil.getParentOfType(element, DreamShaderSection::class.java, false) ?: return false
        if (section.sectionName() !in DESCRIPTION_SECTIONS) return false

        val declaration = PsiTreeUtil.getParentOfType(section, DreamShaderDeclaration::class.java, true) ?: return false
        if (declaration.keywordText() != "virtualfunction") return false

        var cursor = element.prevSibling
        while (cursor != null) {
            val tokenType = cursor.node?.elementType
            when (tokenType) {
                DreamShaderTokenTypes.IDENTIFIER -> return cursor.text.equals("Description", ignoreCase = true)
                DreamShaderTokenTypes.OPERATOR -> {
                    if (cursor.text == ";") return false
                }
            }
            cursor = cursor.prevSibling
        }
        return false
    }

    private fun isCommentToken(element: PsiElement): Boolean {
        return when (element.node?.elementType) {
            DreamShaderTokenTypes.LINE_COMMENT,
            DreamShaderTokenTypes.BLOCK_COMMENT -> true
            else -> false
        }
    }

    private companion object {
        private val DESCRIPTION_SECTIONS = setOf("options", "settings")
        private val commentTokenizer = object : Tokenizer<PsiElement>() {
            override fun tokenize(element: PsiElement, consumer: TokenConsumer) {
                consumer.consumeToken(element, false, CommentSplitter.getInstance())
            }
        }

        private val descriptionTokenizer = object : Tokenizer<PsiElement>() {
            override fun tokenize(element: PsiElement, consumer: TokenConsumer) {
                val endOffset = (element.textLength - 1).coerceAtLeast(1)
                consumer.consumeToken(
                    element,
                    element.text,
                    false,
                    0,
                    TextRange(1, endOffset),
                    PlainTextSplitter.getInstance()
                )
            }
        }
    }
}
