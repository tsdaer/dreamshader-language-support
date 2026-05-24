package com.github.tsdaer.dreamshaderlanguagesupport.language.symbols
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.*

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderElementTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderLexer
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.DreamShaderParserDefinition
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.DreamShaderPsiParser
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderSection
import com.intellij.lang.ASTNode
import com.intellij.lang.impl.PsiBuilderFactoryImpl
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

/**
 * Builds a lightweight declaration/section symbol model used by structure and
 * navigation features.
 *
 * Supports both PSI-based builds (open editor files) and text-only builds
 * (tests and parser fallback paths).
 */
object DreamShaderSymbolModelBuilder {
    fun build(file: PsiFile): DreamShaderSymbolModel {
        val declarations = PsiTreeUtil.findChildrenOfType(file, DreamShaderDeclaration::class.java)
            .filter { PsiTreeUtil.getParentOfType(it, DreamShaderDeclaration::class.java, true) == null }
            .mapNotNull { declaration -> buildDeclarationSymbol(declaration) }
            .toList()

        return DreamShaderSymbolModel(topLevelSymbols = declarations)
    }

    fun buildFromText(text: String): DreamShaderSymbolModel {
        val parserDefinition = DreamShaderParserDefinition()
        val builder = PsiBuilderFactoryImpl().createBuilder(parserDefinition, DreamShaderLexer(), text)
        val root = DreamShaderPsiParser().parse(DreamShaderElementTypes.FILE, builder)
        return buildFromParsedRoot(root, parserDefinition)
    }

    private fun buildFromParsedRoot(root: ASTNode, parserDefinition: DreamShaderParserDefinition): DreamShaderSymbolModel {
        val declarationNodes = collectNodes(root, DreamShaderElementTypes.DECLARATION)
            .filter { node -> findAncestorOfType(node, DreamShaderElementTypes.DECLARATION) == null }

        val declarationSymbols = declarationNodes.mapNotNull { declarationNode ->
            val declarationPsi = parserDefinition.createElement(declarationNode) as? DreamShaderDeclaration ?: return@mapNotNull null
            val keyword = declarationPsi.keywordText() ?: return@mapNotNull null
            val name = declarationPsi.declarationName().orEmpty().ifBlank { "<anonymous>" }
            val displayName = "$keyword $name"
            val range = declarationNode.textRange ?: return@mapNotNull null
            val sectionSymbols = collectNodes(declarationNode, DreamShaderElementTypes.SECTION)
                .filter { sectionNode ->
                    val sectionAncestor = findAncestorOfType(sectionNode, DreamShaderElementTypes.SECTION)
                    val declarationAncestor = findAncestorOfType(sectionNode, DreamShaderElementTypes.DECLARATION)
                    sectionAncestor == null && declarationAncestor == declarationNode
                }
                .mapNotNull { sectionNode ->
                    val sectionPsi = parserDefinition.createElement(sectionNode) as? DreamShaderSection ?: return@mapNotNull null
                    val sectionName = sectionPsi.sectionName() ?: return@mapNotNull null
                    val sectionRange = sectionNode.textRange ?: return@mapNotNull null
                    DreamShaderSymbol(
                        name = sectionName,
                        kind = DreamShaderSymbolKind.SECTION,
                        range = normalizeRange(sectionRange)
                    )
                }

            DreamShaderSymbol(
                name = displayName,
                kind = DreamShaderSymbolKind.DECLARATION,
                range = normalizeRange(range),
                children = sectionSymbols
            )
        }

        return DreamShaderSymbolModel(topLevelSymbols = declarationSymbols)
    }

    private fun buildDeclarationSymbol(declaration: DreamShaderDeclaration): DreamShaderSymbol? {
        val keyword = declaration.keywordText() ?: return null
        val name = declaration.declarationName().orEmpty().ifBlank { "<anonymous>" }
        val displayName = "$keyword $name"
        val range = declaration.textRange ?: return null

        val sections = PsiTreeUtil.findChildrenOfType(declaration, DreamShaderSection::class.java)
            .filter { section -> PsiTreeUtil.getParentOfType(section, DreamShaderSection::class.java, true) == null }
            .mapNotNull { section -> buildSectionSymbol(section) }
            .toList()

        return DreamShaderSymbol(
            name = displayName,
            kind = DreamShaderSymbolKind.DECLARATION,
            range = normalizeRange(range),
            children = sections
        )
    }

    private fun buildSectionSymbol(section: DreamShaderSection): DreamShaderSymbol? {
        val sectionName = section.sectionName() ?: return null
        val range = section.textRange ?: return null

        return DreamShaderSymbol(
            name = sectionName,
            kind = DreamShaderSymbolKind.SECTION,
            range = normalizeRange(range)
        )
    }

    private fun normalizeRange(range: TextRange): TextRange {
        if (range.startOffset <= range.endOffset) return range
        return TextRange(range.endOffset, range.startOffset)
    }

    private fun collectNodes(root: ASTNode, elementType: com.intellij.psi.tree.IElementType): List<ASTNode> {
        val result = mutableListOf<ASTNode>()
        fun walk(node: ASTNode?) {
            if (node == null) return
            if (node.elementType == elementType) {
                result.add(node)
            }
            var child = node.firstChildNode
            while (child != null) {
                walk(child)
                child = child.treeNext
            }
        }
        walk(root)
        return result
    }

    private fun findAncestorOfType(node: ASTNode, elementType: com.intellij.psi.tree.IElementType): ASTNode? {
        var current = node.treeParent
        while (current != null) {
            if (current.elementType == elementType) {
                return current
            }
            current = current.treeParent
        }
        return null
    }
}
