package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderDialects
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

/**
 * The language's single `psiStructureViewFactory` (M16 C27): files whose content routes to the
 * 2.0 front end (`.dss`/`.dsi` and V2-marked `.dsh`) get the 2.0 model, everything else keeps
 * the frozen 1.x model. The 2.0 factory class stays unregistered on purpose — two language-level
 * structure-view EPs would couple behavior to platform extension ordering.
 */
class DreamShaderStructureViewFactory : PsiStructureViewFactory {
    override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder? {
        if (psiFile !is DreamShaderPsiFile) return null
        if (DreamShaderDialects.routesToV2(psiFile.text)) {
            return DreamShader2StructureViewFactory().getStructureViewBuilder(psiFile)
        }
        return object : TreeBasedStructureViewBuilder() {
            override fun createStructureViewModel(editor: Editor?): StructureViewModel {
                return DreamShaderStructureViewModel(psiFile, editor)
            }
        }
    }
}
