package com.github.tsdaer.dreamshaderlanguagesupport.language.parser
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderElementTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderLexer
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderSection
import com.intellij.lang.ASTNode
import com.intellij.lang.impl.PsiBuilderFactoryImpl
import com.intellij.psi.tree.IElementType
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DreamShaderPsiParserTest : BasePlatformTestCase() {
    fun testParserEmitsDeclarationAndSectionNodes() {
        val text = """
            Shader MySurface {
                Inputs {
                    float2 UV;
                }
                Outputs {
                    float3 Color;
                }
            }
        """.trimIndent()

        val parserDefinition = DreamShaderParserDefinition()
        val builder = PsiBuilderFactoryImpl().createBuilder(parserDefinition, DreamShaderLexer(), text)
        val root = DreamShaderPsiParser().parse(DreamShaderElementTypes.FILE, builder)

        val declarationCount = countNodesByType(root, DreamShaderElementTypes.DECLARATION)
        val sectionCount = countNodesByType(root, DreamShaderElementTypes.SECTION)
        val debugTree = buildDebugTree(root)

        assertTrue("Expected at least one declaration node\n$debugTree", declarationCount >= 1)
        assertTrue("Expected at least two section nodes\n$debugTree", sectionCount >= 2)

        val declarationNode = requireNotNull(findFirstNodeByType(root, DreamShaderElementTypes.DECLARATION))
        val declarationPsi = parserDefinition.createElement(declarationNode)
        assertTrue(declarationPsi is DreamShaderDeclaration)
        declarationPsi as DreamShaderDeclaration
        assertEquals("shader", declarationPsi.keywordText())
        assertEquals("MySurface", declarationPsi.declarationName())
        assertTrue(declarationPsi.bodyTextRange() != null)
        assertTrue(!declarationPsi.isFunctionLike())

        val sectionNode = requireNotNull(findFirstNodeByType(root, DreamShaderElementTypes.SECTION))
        val sectionPsi = parserDefinition.createElement(sectionNode)
        assertTrue(sectionPsi is DreamShaderSection)
        sectionPsi as DreamShaderSection
        assertEquals("inputs", sectionPsi.sectionName())
        assertTrue(sectionPsi.bodyTextRange() != null)
    }

    fun testFunctionDeclarationIsFunctionLike() {
        val text = """
            Function Util {
                Graph {
                    float3 x;
                }
            }
        """.trimIndent()

        val parserDefinition = DreamShaderParserDefinition()
        val builder = PsiBuilderFactoryImpl().createBuilder(parserDefinition, DreamShaderLexer(), text)
        val root = DreamShaderPsiParser().parse(DreamShaderElementTypes.FILE, builder)
        val declarationNode = requireNotNull(findFirstNodeByType(root, DreamShaderElementTypes.DECLARATION))
        val declarationPsi = parserDefinition.createElement(declarationNode) as DreamShaderDeclaration

        assertEquals("function", declarationPsi.keywordText())
        assertEquals("Util", declarationPsi.declarationName())
        assertTrue(declarationPsi.isFunctionLike())
    }

    fun testNamespaceParsesAsDeclarationNode() {
        val text = """
            Namespace Tools {
                Function Util(in float x, out float y) {
                    y = x;
                }
            }
        """.trimIndent()

        val parserDefinition = DreamShaderParserDefinition()
        val builder = PsiBuilderFactoryImpl().createBuilder(parserDefinition, DreamShaderLexer(), text)
        val root = DreamShaderPsiParser().parse(DreamShaderElementTypes.FILE, builder)

        val declarationNode = requireNotNull(findFirstNodeByType(root, DreamShaderElementTypes.DECLARATION))
        val declarationPsi = parserDefinition.createElement(declarationNode) as DreamShaderDeclaration

        assertEquals("namespace", declarationPsi.keywordText())
        assertEquals("Tools", declarationPsi.declarationName())
        assertTrue(declarationPsi.bodyTextRange() != null)
        assertTrue(!declarationPsi.isFunctionLike())
    }

    fun testNamespaceContainsNestedDeclarationNodes() {
        val text = """
            Namespace Tools {
                Function Util(in float x, out float y) {
                    y = x;
                }
                GraphFunction Blend(in float a, out float b) {
                    b = a;
                }
            }
        """.trimIndent()

        val parserDefinition = DreamShaderParserDefinition()
        val builder = PsiBuilderFactoryImpl().createBuilder(parserDefinition, DreamShaderLexer(), text)
        val root = DreamShaderPsiParser().parse(DreamShaderElementTypes.FILE, builder)

        val declarationCount = countNodesByType(root, DreamShaderElementTypes.DECLARATION)
        assertTrue("Expected namespace + nested declarations", declarationCount >= 3)
    }

    private fun countNodesByType(node: ASTNode?, elementType: com.intellij.psi.tree.IElementType): Int {
        if (node == null) return 0
        var count = 0
        if (node.elementType == elementType) count++
        var child = node.firstChildNode
        while (child != null) {
            count += countNodesByType(child, elementType)
            child = child.treeNext
        }
        return count
    }

    private fun findFirstNodeByType(node: ASTNode?, elementType: IElementType): ASTNode? {
        if (node == null) return null
        if (node.elementType == elementType) return node
        var child = node.firstChildNode
        while (child != null) {
            val found = findFirstNodeByType(child, elementType)
            if (found != null) return found
            child = child.treeNext
        }
        return null
    }

    private fun buildDebugTree(root: ASTNode): String {
        val sb = StringBuilder()
        appendNode(root, 0, sb)
        return sb.toString()
    }

    private fun appendNode(node: ASTNode, depth: Int, sb: StringBuilder) {
        repeat(depth) { sb.append("  ") }
        sb.append(node.elementType.toReadableName())
        sb.append('\n')
        var child = node.firstChildNode
        while (child != null) {
            appendNode(child, depth + 1, sb)
            child = child.treeNext
        }
    }

    private fun IElementType.toReadableName(): String = toString()
}
