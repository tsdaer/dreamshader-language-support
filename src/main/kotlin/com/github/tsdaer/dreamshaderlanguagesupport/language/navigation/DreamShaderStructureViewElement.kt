package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderSection
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import javax.swing.Icon

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

    override fun getChildren(): Array<TreeElement> = when (element) {
        is DreamShaderPsiFile -> {
            PsiTreeUtil.findChildrenOfType(element, DreamShaderDeclaration::class.java)
                .filter { declaration ->
                    PsiTreeUtil.getParentOfType(declaration, DreamShaderDeclaration::class.java, true) == null
                }
                .map { DreamShaderStructureViewElement(it) }
                .toTypedArray()
        }
        is DreamShaderDeclaration -> {
            PsiTreeUtil.findChildrenOfType(element, DreamShaderSection::class.java)
                .filter { section ->
                    PsiTreeUtil.getParentOfType(section, DreamShaderSection::class.java, true) == null
                }
                .map { DreamShaderStructureViewElement(it) }
                .toTypedArray()
        }
        else -> emptyArray()
    }

    override fun navigate(requestFocus: Boolean) {
        (element as? Navigatable)?.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = (element as? Navigatable)?.canNavigate() == true

    override fun canNavigateToSource(): Boolean = (element as? Navigatable)?.canNavigateToSource() == true
}
