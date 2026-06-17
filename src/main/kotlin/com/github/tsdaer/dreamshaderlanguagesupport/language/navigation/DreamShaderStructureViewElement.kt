package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderIcons
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderPsiUtil
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderSection
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import javax.swing.Icon

/**
 * Implementation of DreamShaderStructureViewElement.
 */
class DreamShaderStructureViewElement(
    private val element: PsiElement
) : StructureViewTreeElement {

    override fun getValue(): Any = element

    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText(): String? = when (element) {
            is DreamShaderPsiFile -> element.name
            is DreamShaderDeclaration -> {
                val keyword = element.keywordText().orEmpty()
                val name = element.declarationName().orEmpty().ifBlank { "<anonymous>" }
                "$keyword $name"
            }
            is DreamShaderSection -> element.sectionName()
            else -> element.text
        }

        override fun getLocationString(): String? = null

        override fun getIcon(unused: Boolean): Icon? = when (element) {
            is DreamShaderPsiFile -> DreamShaderIcons.FILE
            is DreamShaderDeclaration -> when {
                element.keywordText() == "shader" -> DreamShaderIcons.SHADER
                element.isFunctionLike() -> DreamShaderIcons.FUNCTION
                else -> DreamShaderIcons.DECLARATION
            }
            is DreamShaderSection -> when (element.sectionName()) {
                "settings" -> DreamShaderIcons.SECTION_SETTINGS
                "inputs" -> DreamShaderIcons.SECTION_INPUTS
                "outputs" -> DreamShaderIcons.SECTION_OUTPUTS
                "graph" -> DreamShaderIcons.SECTION_GRAPH
                else -> DreamShaderIcons.SECTION
            }
            else -> null
        }
    }

    override fun getChildren(): Array<TreeElement> {
        return when (element) {
            is DreamShaderPsiFile -> {
                DreamShaderPsiUtil.topLevelDeclarations(element)
                    .map { DreamShaderStructureViewElement(it) }
                    .toTypedArray()
            }
            is DreamShaderDeclaration -> {
                if (element.keywordText() == "namespace") {
                    val childDeclarations = DreamShaderPsiUtil.directChildDeclarations(element)
                    if (childDeclarations.isNotEmpty()) {
                        childDeclarations
                            .map { DreamShaderStructureViewElement(it) }
                            .toTypedArray()
                    } else {
                        DreamShaderPsiUtil.directSectionsOf(element)
                            .map { DreamShaderStructureViewElement(it) }
                            .toTypedArray()
                    }
                } else {
                    DreamShaderPsiUtil.directSectionsOf(element)
                        .map { DreamShaderStructureViewElement(it) }
                        .toTypedArray()
                }
            }
            else -> emptyArray()
        }
    }

    override fun navigate(requestFocus: Boolean) {
        (element as? Navigatable)?.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = (element as? Navigatable)?.canNavigate() == true

    override fun canNavigateToSource(): Boolean = (element as? Navigatable)?.canNavigateToSource() == true
}
