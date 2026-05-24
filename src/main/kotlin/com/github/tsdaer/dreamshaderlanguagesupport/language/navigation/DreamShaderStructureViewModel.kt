package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.Sorter
import com.intellij.openapi.editor.Editor

/**
 * Model implementation for DreamShaderStructureViewModel.
 */
class DreamShaderStructureViewModel(
    psiFile: DreamShaderPsiFile,
    editor: Editor?
) : StructureViewModelBase(psiFile, editor, DreamShaderStructureViewElement(psiFile)),
    StructureViewModel.ElementInfoProvider {

    init {
        withSuitableClasses(
            com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration::class.java,
            com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderSection::class.java
        )
    }

    override fun getSorters(): Array<Sorter> = arrayOf(Sorter.ALPHA_SORTER)

    override fun isAlwaysShowsPlus(element: StructureViewTreeElement): Boolean = element.value is DreamShaderPsiFile

    override fun isAlwaysLeaf(element: StructureViewTreeElement): Boolean =
        element.value is com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderSection
}

