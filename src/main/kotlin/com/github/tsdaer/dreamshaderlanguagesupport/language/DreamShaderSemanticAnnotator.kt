package com.github.tsdaer.dreamshaderlanguagesupport.language

import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderSection
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import java.util.Locale

class DreamShaderSemanticAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element.node.elementType != DreamShaderTokenTypes.IDENTIFIER) return
        if (element.text.isBlank()) return

        val declaration = PsiTreeUtil.getParentOfType(element, DreamShaderDeclaration::class.java, false)
        if (declaration != null && element == declaration.nameIdentifier) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(element)
                .textAttributes(DreamShaderTextAttributes.DECLARATION_NAME)
                .create()
            return
        }

        val text = element.text
        if (!looksLikeCallableReference(text, element)) return
        if (isDeclarationHeadIdentifier(element)) return
        if (!isInsideDeclarationBody(element)) return

        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(element)
            .textAttributes(DreamShaderTextAttributes.CALLABLE_REFERENCE)
            .create()
    }

    private fun looksLikeCallableReference(text: String, element: PsiElement): Boolean {
        if (text.equals("ue", ignoreCase = true)) return false
        val next = skipWhitespaceForward(element)
        return next == "(" || next == "."
    }

    private fun skipWhitespaceForward(element: PsiElement): String? {
        val text = element.containingFile.text
        val start = element.textRange.endOffset
        var i = start
        while (i < text.length && text[i].isWhitespace()) i++
        if (i >= text.length) return null
        return text[i].toString()
    }

    private fun isDeclarationHeadIdentifier(element: PsiElement): Boolean {
        val declaration = PsiTreeUtil.getParentOfType(element, DreamShaderDeclaration::class.java, false) ?: return false
        if (element == declaration.nameIdentifier) return true

        val declarationRange = declaration.textRange
        val bodyRange = declaration.bodyTextRange() ?: return false
        val elementRange = element.textRange
        return declarationRange.contains(elementRange) && !bodyRange.contains(elementRange)
    }

    private fun isInsideDeclarationBody(element: PsiElement): Boolean {
        val declaration = PsiTreeUtil.getParentOfType(element, DreamShaderDeclaration::class.java, false) ?: return false
        val bodyRange = declaration.bodyTextRange() ?: return false
        if (!bodyRange.contains(element.textRange)) return false

        val section = PsiTreeUtil.getParentOfType(element, DreamShaderSection::class.java, false)
        if (section != null) {
            val sectionName = section.sectionName()?.lowercase(Locale.ROOT)
            if (sectionName == "settings" || sectionName == "options" || sectionName == "properties") {
                return false
            }
        }

        return true
    }
}

