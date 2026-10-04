package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2ElementTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2FunctionDeclaration
import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore

/**
 * Conditional HLSL injection (D7): the bare-HLSL bodies - 1.x `Function`/`GraphFunction`
 * bodies and the 2.0 `/// @custom` bodies - get native HLSL highlighting when an HLSL
 * language is installed in the IDE (e.g. Rider with Unreal). Without such a language the
 * injector stays silent, and the plugin itself still depends only on `com.intellij.modules.platform`.
 */
class DreamShaderHlslInjector : MultiHostInjector {
    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        val hlsl = Language.findLanguageByID(HLSL_LANGUAGE_ID) ?: return
        when (context) {
            is DreamShaderHlslBodyHost -> {
                val range = context.dreamShaderHlslBodyRange() ?: return
                if (range.isEmpty) return
                registrar.startInjecting(hlsl)
                    .addPlace("", "", context, range)
                    .doneInjecting()
            }
        }
    }

    override fun elementsToInjectIn(): List<Class<out PsiElement>> = listOf(PsiLanguageInjectionHost::class.java)

    private companion object {
        const val HLSL_LANGUAGE_ID = "HLSL"
    }
}

/**
 * Contract implemented by the declaration PSI classes whose body is verbatim HLSL.
 * Keeping the contract in an interface means the 1.x declaration impl only gains the
 * injection capability; its existing methods are untouched.
 */
interface DreamShaderHlslBodyHost : PsiLanguageInjectionHost {
    /** The brace-inside body range relative to this host's own text, or null when absent. */
    fun dreamShaderHlslBodyRange(): TextRange?

    override fun isValidHost(): Boolean = true

    override fun updateText(text: String): PsiLanguageInjectionHost = this

    override fun createLiteralTextEscaper(): LiteralTextEscaper<out PsiLanguageInjectionHost> =
        LiteralTextEscaper.createSimple(this)
}

/** The brace-inside range of the first brace pair in [element]'s own text. */
internal fun braceInsideRange(element: PsiElement): TextRange? {
    var openStart = -1
    var openEnd = -1
    var depth = 0
    var child: PsiElement? = element.firstChild
    while (child != null) {
        when (PsiUtilCore.getElementType(child)) {
            DreamShaderTokenTypes.LBRACE -> {
                if (depth == 0) {
                    openStart = child.textRange.startOffset
                    openEnd = child.textRange.endOffset
                }
                depth++
            }
            DreamShaderTokenTypes.RBRACE -> {
                depth--
                if (depth == 0 && openEnd >= 0) {
                    val hostStart = element.textRange.startOffset
                    return TextRange(
                        openEnd - hostStart,
                        child.textRange.startOffset - hostStart,
                    )
                }
            }
        }
        child = child.nextSibling
    }
    return null
}

/** 1.x bodies: `Function` / `GraphFunction` declarations carry bare HLSL between their braces. */
internal fun isHlslBodiedV1Declaration(declaration: DreamShaderDeclaration): Boolean = declaration.isFunctionLike()

/** 2.0 bodies: the verbatim CUSTOM_BODY capture inside a function declaration. */
internal fun customBodyRange(declaration: DreamShader2FunctionDeclaration): TextRange? {
    val body = PsiTreeUtil.findChildrenOfType(declaration, PsiElement::class.java).firstOrNull {
        PsiUtilCore.getElementType(it) == DreamShader2ElementTypes.CUSTOM_BODY
    } ?: return null
    val inner = braceInsideRange(body) ?: return null
    val hostStart = declaration.textRange.startOffset
    val bodyStart = body.textRange.startOffset
    return TextRange(bodyStart + inner.startOffset - hostStart, bodyStart + inner.endOffset - hostStart)
}
