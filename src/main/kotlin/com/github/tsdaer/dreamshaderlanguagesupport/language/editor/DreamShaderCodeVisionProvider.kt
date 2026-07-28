package com.github.tsdaer.dreamshaderlanguagesupport.language.editor
import com.github.tsdaer.dreamshaderlanguagesupport.language.bridge.DreamShaderBridgePathResolver
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.DreamShaderDeclarationPresentation
import com.github.tsdaer.dreamshaderlanguagesupport.language.packages.DreamShaderPackageIndexLoader
import com.github.tsdaer.dreamshaderlanguagesupport.language.packages.DreamShaderPackageStoreService
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionProvider
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.codeVision.CodeVisionState
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import java.awt.event.MouseEvent

class DreamShaderCodeVisionProvider : CodeVisionProvider<Void?> {
    override val name: String
        get() = "DreamShader Code Vision"

    override val id: String
        get() = "dreamshader.codeVision.workflows"

    override val relativeOrderings: List<CodeVisionRelativeOrdering>
        get() = listOf(CodeVisionRelativeOrdering.CodeVisionRelativeOrderingFirst)

    override val defaultAnchor: CodeVisionAnchorKind
        get() = CodeVisionAnchorKind.Default

    override fun isAvailableFor(project: Project): Boolean {
        val settings = project.getService(DreamShaderProjectSettings::class.java).state
        return settings.enableCodeLens
    }

    override fun precomputeOnUiThread(editor: Editor): Void? = null

    override fun computeCodeVision(editor: Editor, uiData: Void?): CodeVisionState {
        val project = editor.project ?: return CodeVisionState.Ready(emptyList())
        val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return CodeVisionState.Ready(emptyList())
        if (file.language != DreamShaderLanguage) return CodeVisionState.Ready(emptyList())

        val lenses = ArrayList<Pair<TextRange, CodeVisionEntry>>()
        val traverser = SyntaxTraverser.psiTraverser(file)
        for (element in traverser) {
            if (element !is DreamShaderDeclaration) continue
            val hint = computeHint(element, file) ?: continue
            val range = element.textRange
            val entry = ClickableTextCodeVisionEntry(hint, id, onClick = { _, _ -> })
            lenses.add(range to entry)
        }
        return CodeVisionState.Ready(lenses)
    }

    override fun handleClick(editor: Editor, textRange: TextRange, entry: CodeVisionEntry) {
        val project = editor.project ?: return
        val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return
        val startOffset = textRange.startOffset
        val element = file.findElementAt(startOffset) ?: return
        val declaration = element.parent as? DreamShaderDeclaration ?: return
        handleDeclarationClick(editor, declaration)
    }

    fun handleDeclarationClick(editor: Editor, element: PsiElement) {
        val project = element.project
        val active = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        when (val plan = buildClickPlan(project, element.containingFile.virtualFile, active)) {
            is CodeVisionClickPlan.RefreshPackageStore -> {
                val service = project.getService(DreamShaderPackageStoreService::class.java)
                service.loadStore()
            }
            is CodeVisionClickPlan.OpenBridgeDirectory -> {
                val vf = LocalFileSystem.getInstance().findFileByPath(plan.path.replace('\\', '/'))
                if (vf != null) {
                    FileEditorManager.getInstance(project).openFile(vf, false)
                }
            }
            CodeVisionClickPlan.NoAction -> {
            }
        }
    }

    fun computeHint(element: PsiElement, file: PsiFile): String? {
        val declaration = element as? DreamShaderDeclaration ?: return null
        val name = DreamShaderDeclarationPresentation.displayName(declaration) ?: return null
        val hasBridge = resolveExistingBridgeDirectory(file.project, file.virtualFile) != null
        val packageSources = DreamShaderPackageIndexLoader.resolveIndexSources(file.project).size
        val bridgeLabel = if (hasBridge) {
            DreamShaderBundle.message("codeVision.label.bridgeReady")
        } else {
            DreamShaderBundle.message("codeVision.label.bridgeUnavailable")
        }
        return DreamShaderBundle.message("codeVision.hint.summary", bridgeLabel, packageSources, name)
    }

    internal sealed interface CodeVisionClickPlan {
        data object RefreshPackageStore : CodeVisionClickPlan
        data class OpenBridgeDirectory(val path: String) : CodeVisionClickPlan
        data object NoAction : CodeVisionClickPlan
    }

    internal fun testBuildClickPlan(
        project: Project,
        declarationFile: VirtualFile?,
        activeFile: VirtualFile?
    ): CodeVisionClickPlan = buildClickPlan(project, declarationFile, activeFile)

    internal fun testComputeHint(declaration: PsiElement, file: PsiFile): String? = computeHint(declaration, file)

    private fun resolveExistingBridgeDirectory(project: Project, activeFile: VirtualFile?): String? {
        val bridgeDir = DreamShaderBridgePathResolver.resolveBridgeDirectory(project, activeFile) ?: return null
        val vf = LocalFileSystem.getInstance().findFileByPath(bridgeDir.replace('\\', '/')) ?: return null
        return if (vf.isDirectory) bridgeDir else null
    }

    private fun buildClickPlan(
        project: Project,
        declarationFile: VirtualFile?,
        activeFile: VirtualFile?
    ): CodeVisionClickPlan {
        if (declarationFile != null && activeFile != null && declarationFile == activeFile) {
            return CodeVisionClickPlan.RefreshPackageStore
        }
        val bridgeDir = resolveExistingBridgeDirectory(project, declarationFile)
            ?: return CodeVisionClickPlan.NoAction
        return CodeVisionClickPlan.OpenBridgeDirectory(bridgeDir)
    }
}
