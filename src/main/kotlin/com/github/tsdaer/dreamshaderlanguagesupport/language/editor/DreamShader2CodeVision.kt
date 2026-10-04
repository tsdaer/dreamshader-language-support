package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderDialects
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile
import com.github.tsdaer.dreamshaderlanguagesupport.language.packages.DreamShaderImportResolver
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2FunctionDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2PragmaDirective
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil

/**
 * Code Vision for the 2.0 dialect (M12 C6): export functions carry a usage count above
 * their declaration, and a `.dsi` file shows a navigable "Parent: X" hint above its
 * `#pragma instance(...)` header.
 */
internal object DreamShader2CodeVision {
    private const val PARENT_HINT_PREFIX = "Parent: "

    fun compute(file: PsiFile, providerId: String): List<Pair<TextRange, CodeVisionEntry>> {
        if (file !is DreamShaderPsiFile) return emptyList()
        val extension = DreamShaderDialects.extensionOf(file.name)
        if (extension != "dss" && extension != "dsi") return emptyList()

        val lenses = mutableListOf<Pair<TextRange, CodeVisionEntry>>()

        PsiTreeUtil.collectElementsOfType(file, DreamShader2FunctionDeclaration::class.java)
            .filter { it.isExport() }
            .forEach { function ->
                val name = function.declarationName() ?: return@forEach
                val usages = ReferencesSearch.search(function).findAll().size
                val range = functionNameRange(function) ?: return@forEach
                lenses.add(
                    range to ClickableTextCodeVisionEntry(
                        "$name - $usages usages",
                        providerId,
                        onClick = { _, _ -> },
                    ),
                )
            }

        if (extension == "dsi") {
            PsiTreeUtil.collectElementsOfType(file, DreamShader2PragmaDirective::class.java)
                .firstOrNull { it.pragmaName().equals("instance", ignoreCase = true) }
                ?.let { pragma ->
                    val parentName = PARENT_PATTERN.find(pragma.text)?.groupValues?.getOrNull(1) ?: return@let
                    val range = TextRange(pragma.textRange.startOffset, pragma.textRange.startOffset + 1)
                    lenses.add(
                        range to ClickableTextCodeVisionEntry(
                            "$PARENT_HINT_PREFIX$parentName",
                            providerId,
                            onClick = { _, editor -> openParent(editor, file, parentName) },
                        ),
                    )
                }
        }

        return lenses
    }

    private fun functionNameRange(function: DreamShader2FunctionDeclaration): TextRange? {
        val name = function.declarationName() ?: return null
        val leaf = PsiTreeUtil.collectElements(function) {
            it.node?.elementType == com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes.IDENTIFIER &&
                it.text == name
        }.firstOrNull() ?: return null
        return leaf.textRange
    }

    private fun openParent(editor: com.intellij.openapi.editor.Editor, file: PsiFile, parentName: String) {
        val project = editor.project ?: return
        val target = DreamShaderImportResolver.resolveImport(file, parentName) ?: return
        FileEditorManager.getInstance(project).openFile(target, true)
    }

    internal val PARENT_HINT = PARENT_HINT_PREFIX

    private val PARENT_PATTERN = Regex("""Parent\s*=\s*"([^"]+)""")
}
