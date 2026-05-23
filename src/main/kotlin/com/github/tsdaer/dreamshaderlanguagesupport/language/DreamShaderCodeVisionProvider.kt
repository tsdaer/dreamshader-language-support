package com.github.tsdaer.dreamshaderlanguagesupport.language

import com.github.tsdaer.dreamshaderlanguagesupport.language.bridge.DreamShaderBridgePathResolver
import com.github.tsdaer.dreamshaderlanguagesupport.language.packages.DreamShaderPackageIndexLoader
import com.github.tsdaer.dreamshaderlanguagesupport.language.packages.DreamShaderPackageStoreService
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.hints.codeVision.CodeVisionProviderBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import java.awt.event.MouseEvent

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
        val bridgeDir = DreamShaderBridgePathResolver.resolveBridgeDirectory(file.project, file.virtualFile)
        val hasBridge = !bridgeDir.isNullOrBlank()
        val packageSources = DreamShaderPackageIndexLoader.resolveIndexSources(file.project).size
        val bridgeLabel = if (hasBridge) "Bridge" else "NoBridge"
        return "$bridgeLabel | Pkg:$packageSources | $name"
    }

    override fun handleClick(editor: Editor, element: PsiElement, event: MouseEvent?) {
        val project = element.project
        val active = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        if (active != null && active == element.containingFile.virtualFile) {
            val service = project.getService(DreamShaderPackageStoreService::class.java)
            service.loadStore()
            return
        }

        val bridgeDir = DreamShaderBridgePathResolver.resolveBridgeDirectory(project, element.containingFile.virtualFile)
        if (!bridgeDir.isNullOrBlank()) {
            val vf = LocalFileSystem.getInstance().findFileByPath(bridgeDir.replace('\\', '/'))
            if (vf != null) {
                FileEditorManager.getInstance(project).openFile(vf, false)
            }
        }
    }

    override val name: String
        get() = "DreamShader Code Vision"

    override val id: String
        get() = "dreamshader.codeVision.workflows"

    override val relativeOrderings: List<CodeVisionRelativeOrdering>
        get() = listOf(CodeVisionRelativeOrdering.CodeVisionRelativeOrderingFirst)
}
