package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2ElementTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderElementTypes
import com.intellij.lang.surroundWith.SurroundDescriptor
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiUtilCore

/**
 * Surround With (D2):
 * - 2.0 dialect: wrap the selection in a `{ }` block, an `if (...) { }` or a `for (...) { }`;
 * - 1.x dialect: wrap the selection in a named section (`Name { }`, the `Inputs { }` shape).
 *
 * All surrounders perform one deterministic document replacement and return the caret
 * range for the platform to re-select (the `if`/`for` condition slot), so the behavior
 * is unit-testable without the surrounding popup.
 */
class DreamShaderSurroundDescriptor : SurroundDescriptor {
    override fun getElementsToSurround(file: PsiFile, startOffset: Int, endOffset: Int): Array<PsiElement> {
        if (file.language != DreamShaderLanguage) return PsiElement.EMPTY_ARRAY
        val start = minOf(startOffset, endOffset)
        val end = maxOf(startOffset, endOffset)
        val seed = file.findElementAt(start) ?: return PsiElement.EMPTY_ARRAY
        val first = DreamShaderStatementLevelElements.enclosing(seed) ?: return PsiElement.EMPTY_ARRAY

        val selected = mutableListOf(first)
        if (first.textRange.endOffset < end) {
            var candidate = first.nextSibling
            while (candidate != null && candidate.textRange.startOffset < end) {
                val owner = DreamShaderStatementLevelElements.enclosing(candidate)
                if (owner != null && owner !in selected && owner.textRange.startOffset >= first.textRange.startOffset) {
                    // Skip containers that merely span the already-selected statements
                    // (e.g. the enclosing function declaration reached across a whitespace run).
                    if (selected.none { owner.textRange.contains(it.textRange) }) selected.add(owner)
                }
                candidate = candidate.nextSibling
            }
        }
        return selected.toTypedArray()
    }

    override fun getSurrounders(): Array<Surrounder> = arrayOf(
        DreamShaderWithBracesSurrounder(),
        DreamShaderWithIfSurrounder(),
        DreamShaderWithForSurrounder(),
        DreamShaderWithSectionSurrounder(),
    )

    override fun isExclusive(): Boolean = true
}

/**
 * Statement-level element types shared by Surround With (D2) and the statement
 * up/down mover (D4): 1.x declarations/sections and the 2.0 declarations/statements.
 */
internal object DreamShaderStatementLevelElements {
    val TYPES: Set<com.intellij.psi.tree.IElementType> = setOf(
        DreamShaderElementTypes.DECLARATION,
        DreamShaderElementTypes.SECTION,
        DreamShader2ElementTypes.FUNCTION_DECLARATION,
        DreamShader2ElementTypes.STRUCT_DECLARATION,
        DreamShader2ElementTypes.UNIFORM_DECLARATION,
        DreamShader2ElementTypes.GLOBAL_DECLARATION,
        DreamShader2ElementTypes.VARIABLE_DECLARATION_STATEMENT,
        DreamShader2ElementTypes.EXPRESSION_STATEMENT,
        DreamShader2ElementTypes.IF_STATEMENT,
        DreamShader2ElementTypes.FOR_STATEMENT,
        DreamShader2ElementTypes.WHILE_STATEMENT,
        DreamShader2ElementTypes.DO_WHILE_STATEMENT,
        DreamShader2ElementTypes.RETURN_STATEMENT,
        DreamShader2ElementTypes.JUMP_STATEMENT,
    )

    fun enclosing(element: PsiElement): PsiElement? {
        var current: PsiElement? = element
        while (current != null && current !is PsiFile) {
            if (PsiUtilCore.getElementType(current) in TYPES) return current
            current = current.parent
        }
        return null
    }

    /** The nearest movable sibling of [element] in the given direction, if any. */
    fun adjacentSibling(element: PsiElement, down: Boolean): PsiElement? {
        var candidate = if (down) element.nextSibling else element.prevSibling
        while (candidate != null) {
            if (PsiUtilCore.getElementType(candidate) in TYPES) return candidate
            candidate = if (down) candidate.nextSibling else candidate.prevSibling
        }
        return null
    }
}

/** True for 2.0-dialect files (`.dss`/`.dsi`). */
internal fun isV2DialectElement(element: PsiElement): Boolean =
    element.containingFile?.virtualFile?.extension?.lowercase() in setOf("dss", "dsi")

/**
 * One deterministic document edit: replaces the element range with [buildReplacement]'s
 * text and returns the caret range the platform should place the selection at.
 */
internal fun surroundElements(
    project: Project,
    editor: Editor,
    elements: Array<PsiElement>,
    buildReplacement: (body: String) -> Pair<String, Int>,
): TextRange? {
    if (elements.isEmpty()) return null
    val document = editor.document
    val start = elements.first().textRange.startOffset
    val end = elements.last().textRange.endOffset

    PsiDocumentManager.getInstance(project).commitDocument(document)
    val body = document.getText(TextRange(start, end))
    val (replacement, caretOffset) = buildReplacement(body)
    document.replaceString(start, end, replacement)
    PsiDocumentManager.getInstance(project).commitDocument(document)
    return TextRange(start + caretOffset, start + caretOffset)
}

/** Indents every line of [body] after the first by [unit] spaces. */
internal fun indentBody(body: String, unit: String = "    "): String =
    body.split('\n').joinToString("\n") { line -> if (line.isBlank()) line else unit + line }

/** 2.0 `{ }` block. */
internal class DreamShaderWithBracesSurrounder : Surrounder {
    override fun getTemplateDescription(): String = DreamShaderBundle.message("surround.with.block")

    override fun isApplicable(elements: Array<PsiElement>): Boolean = elements.isNotEmpty()

    override fun surroundElements(project: Project, editor: Editor, elements: Array<PsiElement>): TextRange? {
        return surroundElements(project, editor, elements) { body ->
            "{\n${indentBody(body)}\n}" to "{".length
        }
    }
}

/** 2.0 `if (condition) { }` - the condition slot becomes the caret selection. */
internal class DreamShaderWithIfSurrounder : Surrounder {
    override fun getTemplateDescription(): String = DreamShaderBundle.message("surround.with.if")

    override fun isApplicable(elements: Array<PsiElement>): Boolean =
        elements.isNotEmpty() && isV2DialectElement(elements.first())

    override fun surroundElements(project: Project, editor: Editor, elements: Array<PsiElement>): TextRange? {
        return surroundElements(project, editor, elements) { body ->
            "if () {\n${indentBody(body)}\n}" to "if (".length
        }
    }
}

/** 2.0 `for (init; condition; step) { }`. */
internal class DreamShaderWithForSurrounder : Surrounder {
    override fun getTemplateDescription(): String = DreamShaderBundle.message("surround.with.for")

    override fun isApplicable(elements: Array<PsiElement>): Boolean =
        elements.isNotEmpty() && isV2DialectElement(elements.first())

    override fun surroundElements(project: Project, editor: Editor, elements: Array<PsiElement>): TextRange? {
        return surroundElements(project, editor, elements) { body ->
            "for (; ; ) {\n${indentBody(body)}\n}" to "for (".length
        }
    }
}

/** 1.x named section (`SectionName { ... }`, the `Inputs { }` shape). */
internal class DreamShaderWithSectionSurrounder : Surrounder {
    override fun getTemplateDescription(): String = DreamShaderBundle.message("surround.with.section")

    override fun isApplicable(elements: Array<PsiElement>): Boolean =
        elements.isNotEmpty() && !isV2DialectElement(elements.first())

    override fun surroundElements(project: Project, editor: Editor, elements: Array<PsiElement>): TextRange? {
        return surroundElements(project, editor, elements) { body ->
            "SectionName {\n${indentBody(body)}\n}" to "SectionName {".length
        }
    }
}
