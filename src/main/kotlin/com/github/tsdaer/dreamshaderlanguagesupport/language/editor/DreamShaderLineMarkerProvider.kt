package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.github.tsdaer.dreamshaderlanguagesupport.language.bridge.DreamShaderBridgeDiagnosticsRepository
import com.github.tsdaer.dreamshaderlanguagesupport.language.bridge.DreamShaderBridgeDiagnostic
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderIcons
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.packages.DreamShaderImportClosureResolver
import com.github.tsdaer.dreamshaderlanguagesupport.language.packages.DreamShaderImportResolver
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import java.awt.event.MouseEvent
import java.util.Locale

class DreamShaderLineMarkerProvider : RelatedItemLineMarkerProvider() {
    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>
    ) {
        if (element.language != DreamShaderLanguage) return
        collectImportMarker(element, result)
        collectDeclarationMarker(element, result)
        collectBridgeDiagnosticMarker(element, result)
    }

    private fun collectImportMarker(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>
    ) {
        if (element.node?.elementType != DreamShaderTokenTypes.STRING) return
        if (!DreamShaderImportClosureResolver.isImportStringLiteralToken(element)) return
        val importPath = element.text.trim('"')
        if (importPath.isBlank()) return
        val target = DreamShaderImportResolver.resolveImport(element.containingFile, importPath) ?: return
        val psiTarget = PsiManager.getInstance(element.project).findFile(target) ?: return
        val builder = NavigationGutterIconBuilder
            .create(DreamShaderIcons.FILE)
            .setTargets(psiTarget)
            .setTooltipText(DreamShaderBundle.message("lineMarker.import.tooltip", importPath))
        result.add(builder.createLineMarkerInfo(element))
    }

    private fun collectDeclarationMarker(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>
    ) {
        if (element.node?.elementType != DreamShaderTokenTypes.IDENTIFIER) return
        val declaration = PsiTreeUtil.getParentOfType(element, DreamShaderDeclaration::class.java, false) ?: return
        if (declaration.nameIdentifier != element) return
        if (PsiTreeUtil.getParentOfType(declaration, DreamShaderDeclaration::class.java, true) != null) return

        val keyword = declaration.keywordText().orEmpty()
        val normalized = keyword.lowercase(Locale.ROOT)
        if (normalized !in MARKED_DECLARATIONS) return
        val icon = when (normalized) {
            "shader" -> DreamShaderIcons.SHADER
            "shaderfunction", "virtualfunction" -> DreamShaderIcons.FUNCTION
            "shaderlayer", "shaderlayerblend" -> DreamShaderIcons.DECLARATION
            else -> DreamShaderIcons.DECLARATION
        }
        val builder = NavigationGutterIconBuilder
            .create(icon)
            .setTargets(declaration)
            .setTooltipText(
                DreamShaderBundle.message(
                    "lineMarker.declaration.tooltip",
                    keyword,
                    declaration.declarationName().orEmpty().ifBlank { "<anonymous>" }
                )
            )
        result.add(builder.createLineMarkerInfo(element))
    }

    private fun collectBridgeDiagnosticMarker(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>
    ) {
        if (element.node?.elementType != DreamShaderTokenTypes.IDENTIFIER) return
        val file = element.containingFile?.virtualFile ?: return
        val line = element.containingFile.viewProvider.document?.getLineNumber(element.textRange.startOffset)?.plus(1) ?: return
        val diagnostic = element.project.getService(DreamShaderBridgeDiagnosticsRepository::class.java)
            .diagnosticsForFile(file)
            .firstOrNull { it.line == line }
            ?: return
        val icon = if (diagnostic.severity.contains("warn", ignoreCase = true)) {
            AllIcons.General.Warning
        } else {
            AllIcons.General.Error
        }
        val builder = NavigationGutterIconBuilder
            .create(icon)
            .setTargets(element)
            .setTooltipText(bridgeDiagnosticTooltip(diagnostic))
        result.add(
            builder.createLineMarkerInfo(
                element,
                GutterIconNavigationHandler<PsiElement> { _: MouseEvent?, psiElement: PsiElement ->
                    val descriptor = bridgeDiagnosticDescriptor(psiElement.project, diagnostic)
                    if (descriptor != null) {
                        descriptor.navigate(true)
                    } else {
                        (psiElement as? Navigatable)?.navigate(true)
                    }
                }
            )
        )
    }

    internal fun testBridgeDiagnosticDescriptor(project: Project, diagnostic: DreamShaderBridgeDiagnostic): OpenFileDescriptor? {
        return bridgeDiagnosticDescriptor(project, diagnostic)
    }

    internal fun testBridgeDiagnosticTooltip(diagnostic: DreamShaderBridgeDiagnostic): String {
        return bridgeDiagnosticTooltip(diagnostic)
    }

    private fun bridgeDiagnosticDescriptor(project: Project, diagnostic: DreamShaderBridgeDiagnostic): OpenFileDescriptor? {
        val sourcePath = diagnostic.sourcePath.replace('\\', '/')
        val sourceFile = LocalFileSystem.getInstance().findFileByPath(sourcePath)
        if (sourceFile == null || !sourceFile.isValid || sourceFile.isDirectory) return null
        return OpenFileDescriptor(
            project,
            sourceFile,
            (diagnostic.line - 1).coerceAtLeast(0),
            (diagnostic.column - 1).coerceAtLeast(0)
        )
    }

    private fun bridgeDiagnosticTooltip(diagnostic: DreamShaderBridgeDiagnostic): String {
        return DreamShaderBundle.message(
            "lineMarker.bridge.tooltip",
            diagnostic.severity.uppercase(Locale.ROOT),
            diagnostic.sourcePath,
            diagnostic.line,
            diagnostic.column,
            diagnostic.message
        )
    }

    private companion object {
        private val MARKED_DECLARATIONS = setOf(
            "shader",
            "shaderfunction",
            "shaderlayer",
            "shaderlayerblend",
            "virtualfunction"
        )
    }
}
