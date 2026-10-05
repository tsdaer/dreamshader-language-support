package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2ElementTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderDialects
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderIcons
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.psiTreeWalk
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.packages.DreamShaderImportResolver
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2FunctionDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2GlobalDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2IncludeDirective
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2PragmaDirective
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2StructDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2UniformDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderPsiUtil
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.ide.util.treeView.smartTree.Sorter
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import javax.swing.Icon

/**
 * Structure view for the 2.0 dialect (M12 C6): includes, uniforms grouped by their
 * `/// @group`, globals, structs, and functions; a `.dsi` file additionally shows a
 * navigable Parent node from its `#pragma instance(...)` header. Reaching this factory
 * goes through [DreamShaderStructureViewFactory]'s dialect routing (M16 C27); `.dsh`
 * files are allowed and mix in one node per legacy block with the 1.x declarations
 * scanned out of its text.
 */
class DreamShader2StructureViewFactory : PsiStructureViewFactory {
    override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder? {
        if (psiFile !is DreamShaderPsiFile) return null
        val extension = DreamShaderDialects.extensionOf(psiFile.name)
        if (extension != "dss" && extension != "dsi" && extension != "dsh") return null
        return object : TreeBasedStructureViewBuilder() {
            override fun createStructureViewModel(editor: Editor?): StructureViewModel {
                return DreamShader2StructureViewModel(psiFile, editor)
            }
        }
    }
}

class DreamShader2StructureViewModel(
    psiFile: DreamShaderPsiFile,
    editor: Editor?,
) : StructureViewModelBase(psiFile, editor, DreamShader2StructureViewElement(psiFile)),
    StructureViewModel.ElementInfoProvider {

    override fun getSorters(): Array<Sorter> = arrayOf(Sorter.ALPHA_SORTER)

    override fun isAlwaysShowsPlus(element: StructureViewTreeElement): Boolean = element.value is DreamShaderPsiFile

    override fun isAlwaysLeaf(element: StructureViewTreeElement): Boolean = true
}

/** The `.dsi` parent-material node: navigable to the resolved parent file when possible. */
private class ParentNode(
    val pragma: PsiElement,
    val parentName: String,
    val targetFile: PsiFile?,
) {
    fun navigate(requestFocus: Boolean) {
        (targetFile?.navigationElement as? Navigatable)?.navigate(requestFocus)
    }

    fun canNavigate(): Boolean = targetFile != null

    fun canNavigateToSource(): Boolean = canNavigate()
}

private class GroupNode(val label: String, val children: List<PsiElement>)

/** A `LEGACY_BLOCK` of a mixed `.dsh`: one node per block, children from a text reparse (M16 C27). */
private class LegacyBlockNode(val block: PsiElement)

/** One 1.x declaration scanned out of a legacy block's text; navigable at its absolute offset. */
private class LegacyDeclarationNode(
    val keyword: String?,
    val name: String?,
    val hostFile: DreamShaderPsiFile,
    val offset: Int,
) {
    val label: String = buildList {
        keyword?.takeIf { it.isNotBlank() }?.let { add(it) }
        name?.takeIf { it.isNotBlank() }?.let { add(it) }
    }.joinToString(" ").ifBlank { "declaration" }
}

/**
 * 1.x declarations inside every legacy block of [file], keyed by block element. The blocks are
 * opaque token flat nodes in the 2.0 tree, so the declarations come from re-parsing each block's
 * text with the dialect-aware parser (legacy-only text routes to the frozen 1.x front end) and
 * mapping the found ranges back by the block start offset. Cached on the file PSI; a malformed
 * block yields no children — the node stays as a placeholder, never a diagnostic.
 */
private fun legacyDeclarationsOf(block: PsiElement): List<LegacyDeclarationNode> {
    val file = block.containingFile as? DreamShaderPsiFile ?: return emptyList()
    val byBlock = CachedValuesManager.getCachedValue(file) {
        CachedValueProvider.Result.create(computeLegacyDeclarations(file), file)
    }
    return byBlock[block].orEmpty()
}

private fun computeLegacyDeclarations(
    file: DreamShaderPsiFile,
): Map<PsiElement, List<LegacyDeclarationNode>> {
    val result = HashMap<PsiElement, MutableList<LegacyDeclarationNode>>()
    psiTreeWalk(file).filter { it.node?.elementType == DreamShader2ElementTypes.LEGACY_BLOCK }.forEach { block ->
        val text = block.text
        if (text.isBlank()) return@forEach
        val parsed = PsiFileFactory.getInstance(file.project)
            .createFileFromText("legacy.dsh", DreamShaderLanguage, text) ?: return@forEach
        val blockStart = block.textRange.startOffset
        DreamShaderPsiUtil.declarationsIn(parsed).forEach { declaration ->
            // The raw keyword spelling (1.x keywordText() normalizes case; the tree should
            // echo how the file was written).
            val keyword = declaration.node.findChildByType(DreamShaderTokenTypes.KEYWORD)?.text
            val name = declaration.declarationName()
            val offset = blockStart + declaration.textRange.startOffset
            result.getOrPut(block) { mutableListOf() }
                .add(LegacyDeclarationNode(keyword, name, file, offset))
        }
    }
    return result
}

private fun legacyBlockLabel(block: PsiElement): String {
    val first = legacyDeclarationsOf(block).firstOrNull() ?: return "legacy block"
    return first.label
}

private fun DreamShaderPsiFile.parentInstanceNode(): ParentNode? {
    val pragma = PsiTreeUtil.collectElementsOfType(this, DreamShader2PragmaDirective::class.java)
        .firstOrNull { it.pragmaName().equals("instance", ignoreCase = true) } ?: return null
    val parentName = PARENT_PATTERN.find(pragma.text)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
        ?: return null
    val target = DreamShaderImportResolver.resolveImport(this, parentName)
        ?.let { PsiManager.getInstance(project).findFile(it) }
    return ParentNode(pragma, parentName, target)
}

private val PARENT_PATTERN = Regex("""Parent\s*=\s*"([^"]+)"""")

private val DOC_GROUP_PATTERN = Regex("""@group\s+([A-Za-z0-9_]+)""")

class DreamShader2StructureViewElement(private val element: Any) : StructureViewTreeElement {

    override fun getValue(): Any = element

    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText(): String? = when (element) {
            is DreamShaderPsiFile -> element.name
            is ParentNode -> "Parent: ${element.parentName}"
            is GroupNode -> element.label
            is LegacyBlockNode -> legacyBlockLabel(element.block)
            is LegacyDeclarationNode -> element.label
            is DreamShader2UniformDeclaration -> uniformLabel(element)
            is DreamShader2FunctionDeclaration -> functionLabel(element)
            is DreamShader2StructDeclaration -> "struct ${element.declarationName().orEmpty()}"
            is DreamShader2GlobalDeclaration -> "global ${element.declarationName().orEmpty()}"
            is DreamShader2IncludeDirective -> "include ${element.pathText().orEmpty()}"
            else -> (element as? PsiElement)?.text
        }

        override fun getLocationString(): String? = when (element) {
            is DreamShader2UniformDeclaration -> element.typeName()?.let { " : $it" }
            is DreamShader2FunctionDeclaration -> element.returnTypeText()?.let { " : $it" }
            else -> null
        }

        override fun getIcon(unused: Boolean): Icon? = when (element) {
            is DreamShaderPsiFile -> DreamShaderIcons.FILE
            is ParentNode -> DreamShaderIcons.DECLARATION
            is GroupNode -> DreamShaderIcons.SECTION
            is LegacyBlockNode -> DreamShaderIcons.SECTION
            is LegacyDeclarationNode -> DreamShaderIcons.DECLARATION
            is DreamShader2UniformDeclaration -> DreamShaderIcons.DECLARATION
            is DreamShader2FunctionDeclaration -> DreamShaderIcons.FUNCTION
            is DreamShader2StructDeclaration -> DreamShaderIcons.DECLARATION
            is DreamShader2IncludeDirective -> DreamShaderIcons.DECLARATION
            else -> null
        }
    }

    override fun getChildren(): Array<TreeElement> {
        val children: List<Any> = when (element) {
            is DreamShaderPsiFile -> topLevelChildren(element)
            is GroupNode -> element.children
            is LegacyBlockNode -> legacyDeclarationsOf(element.block)
            else -> emptyList()
        }
        return children.map { DreamShader2StructureViewElement(it) }.toTypedArray()
    }

    private fun topLevelChildren(file: DreamShaderPsiFile): List<Any> {
        val nodes = mutableListOf<Any>()
        nodes.addAll(includes(file))
        file.parentInstanceNode()?.let { nodes.add(it) }

        val uniforms = PsiTreeUtil.collectElementsOfType(file, DreamShader2UniformDeclaration::class.java)
        if (uniforms.isNotEmpty()) {
            uniforms
                .groupBy { uniform -> uniformGroupOf(uniform) }
                .forEach { entry ->
                    nodes.add(GroupNode(entry.key, entry.value))
                }
        }

        PsiTreeUtil.collectElementsOfType(file, DreamShader2GlobalDeclaration::class.java).forEach { nodes.add(it) }
        PsiTreeUtil.collectElementsOfType(file, DreamShader2StructDeclaration::class.java).forEach { nodes.add(it) }
        PsiTreeUtil.collectElementsOfType(file, DreamShader2FunctionDeclaration::class.java).forEach { nodes.add(it) }
        psiTreeWalk(file)
            .filter { it.node?.elementType == DreamShader2ElementTypes.LEGACY_BLOCK }
            .forEach { nodes.add(LegacyBlockNode(it)) }
        return nodes
    }

    private fun includes(file: DreamShaderPsiFile): List<Any> {
        return PsiTreeUtil.collectElementsOfType(file, DreamShader2IncludeDirective::class.java)
            .map { it as PsiElement }
    }

    private fun uniformGroupOf(uniform: DreamShader2UniformDeclaration): String {
        val docBlock = uniform.node.findChildByType(DreamShader2ElementTypes.DOC_BLOCK)?.text ?: return UNGROUPED
        return DOC_GROUP_PATTERN.find(docBlock)?.groupValues?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
            ?: UNGROUPED
    }

    override fun navigate(requestFocus: Boolean) {
        when (element) {
            is ParentNode -> element.navigate(requestFocus)
            is LegacyDeclarationNode -> element.hostFile.virtualFile?.let {
                OpenFileDescriptor(element.hostFile.project, it, element.offset).navigate(requestFocus)
            }
            is PsiElement -> ((element.navigationElement ?: element) as? Navigatable)?.navigate(requestFocus)
            else -> (element as? Navigatable)?.navigate(requestFocus)
        }
    }

    override fun canNavigate(): Boolean = when (element) {
        is ParentNode -> element.canNavigate()
        is LegacyDeclarationNode -> element.hostFile.virtualFile != null
        is PsiElement -> ((element.navigationElement ?: element) as? Navigatable)?.canNavigate() == true
        else -> (element as? Navigatable)?.canNavigate() == true
    }

    override fun canNavigateToSource(): Boolean = when (element) {
        is ParentNode -> element.canNavigateToSource()
        is PsiElement -> (element.navigationElement as? Navigatable)?.canNavigateToSource() == true
        else -> (element as? Navigatable)?.canNavigateToSource() == true
    }

    companion object {
        const val UNGROUPED = "Ungrouped"
    }
}

private fun uniformLabel(uniform: DreamShader2UniformDeclaration): String =
    "uniform ${uniform.declarationName().orEmpty().ifBlank { "<anonymous>" }}"

private fun functionLabel(function: DreamShader2FunctionDeclaration): String {
    val qualifier = when {
        function.isExport() -> "export "
        function.isExtern() -> "extern "
        else -> ""
    }
    return "${qualifier}function ${function.declarationName().orEmpty().ifBlank { "<anonymous>" }}"
}
