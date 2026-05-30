package com.github.tsdaer.dreamshaderlanguagesupport.language.editor
import com.github.tsdaer.dreamshaderlanguagesupport.language.bridge.DreamShaderBridgePathResolver
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage
import com.github.tsdaer.dreamshaderlanguagesupport.language.packages.DreamShaderPackageIndexLoader
import com.github.tsdaer.dreamshaderlanguagesupport.language.packages.DreamShaderPackageStoreService
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.hints.codeVision.CodeVisionProviderBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import java.awt.event.MouseEvent

/**
 * Provider implementation for DreamShaderCodeVisionProvider.
 */
class DreamShaderCodeVisionProvider : CodeVisionProviderBase() {
    override fun acceptsFile(file: PsiFile): Boolean {
        if (file.language != DreamShaderLanguage) return false
        val settings = file.project.getService(DreamShaderProjectSettings::class.java).state
        return settings.enableCodeLens
    }

    override fun acceptsElement(element: PsiElement): Boolean {
        return element is DreamShaderDeclaration
    }

    override fun getHint(element: PsiElement, file: PsiFile): String {
        val declaration = element as? DreamShaderDeclaration ?: return ""
        val name = declaration.declarationName() ?: return ""
        val hasBridge = resolveExistingBridgeDirectory(file.project, file.virtualFile) != null
        val packageSources = DreamShaderPackageIndexLoader.resolveIndexSources(file.project).size
        val bridgeLabel = if (hasBridge) {
            DreamShaderBundle.message("codeVision.label.bridgeReady")
        } else {
            DreamShaderBundle.message("codeVision.label.bridgeUnavailable")
        }
        return DreamShaderBundle.message("codeVision.hint.summary", bridgeLabel, packageSources, name)
    }

    override fun handleClick(editor: Editor, element: PsiElement, event: MouseEvent?) {
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

    override val name: String
        get() = "DreamShader Code Vision"

    override val id: String
        get() = "dreamshader.codeVision.workflows"

    override val relativeOrderings: List<CodeVisionRelativeOrdering>
        get() = listOf(CodeVisionRelativeOrdering.CodeVisionRelativeOrderingFirst)

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
