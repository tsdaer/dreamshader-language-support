package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DreamShaderSpellcheckingStrategyTest : BasePlatformTestCase() {
    fun testCommentsUseSpellcheckingTokenizer() {
        val file = myFixture.configureByText(
            "spell_comments.dsm",
            """
            // commment with typo
            Shader Main {
                /* block commment */
                Graph {
                }
            }
            """.trimIndent()
        )
        val strategy = DreamShaderSpellcheckingStrategy()
        val lineComment = findTokenElementByText(file, "// commment with typo", DreamShaderTokenTypes.LINE_COMMENT)
        val blockComment = findTokenElementByText(file, "/* block commment */", DreamShaderTokenTypes.BLOCK_COMMENT)

        assertNotSame(SpellcheckingStrategy.EMPTY_TOKENIZER, strategy.getTokenizer(lineComment))
        assertNotSame(SpellcheckingStrategy.EMPTY_TOKENIZER, strategy.getTokenizer(blockComment))
    }

    fun testVirtualFunctionDescriptionStringsAreSpellchecked() {
        val file = myFixture.configureByText(
            "spell_description.dsf",
            """
            VirtualFunction(Name="/Game/Functions/VF_Pulse") {
                Options {
                    Description = "Bridge compatiblity mesage";
                    Asset = Path(Game, Materials/M_VFAsset);
                }
            }
            """.trimIndent()
        )
        val strategy = DreamShaderSpellcheckingStrategy()
        val descriptionString = findTokenElementByText(file, "\"Bridge compatiblity mesage\"", DreamShaderTokenTypes.STRING)
        val assetString = findTokenElementByText(file, "\"/Game/Functions/VF_Pulse\"", DreamShaderTokenTypes.STRING)

        assertTrue(strategy.isSpellcheckableDescriptionString(descriptionString))
        assertNotSame(SpellcheckingStrategy.EMPTY_TOKENIZER, strategy.getTokenizer(descriptionString))
        assertFalse(strategy.isSpellcheckableDescriptionString(assetString))
        assertSame(SpellcheckingStrategy.EMPTY_TOKENIZER, strategy.getTokenizer(assetString))
    }

    fun testNonDescriptionStringsStayOutOfSpellchecking() {
        val file = myFixture.configureByText(
            "spell_non_description.dsf",
            """
            VirtualFunction(Name="/Game/Functions/VF_Pulse") {
                Options {
                    Asset = Path(Game, Materials/M_VFAsset);
                }
            }
            Function Helper {
                string Label = "CodeLike_Value";
            }
            """.trimIndent()
        )
        val strategy = DreamShaderSpellcheckingStrategy()
        val nameString = findTokenElementByText(file, "\"/Game/Functions/VF_Pulse\"", DreamShaderTokenTypes.STRING)
        val codeLikeString = findTokenElementByText(file, "\"CodeLike_Value\"", DreamShaderTokenTypes.STRING)

        assertFalse(strategy.isSpellcheckableDescriptionString(nameString))
        assertSame(SpellcheckingStrategy.EMPTY_TOKENIZER, strategy.getTokenizer(nameString))
        assertFalse(strategy.isSpellcheckableDescriptionString(codeLikeString))
        assertSame(SpellcheckingStrategy.EMPTY_TOKENIZER, strategy.getTokenizer(codeLikeString))
    }

    private fun findTokenElementByText(file: PsiElement, text: String, expectedType: IElementType): PsiElement {
        val match = findAstNode(file.node, expectedType, text)
        assertNotNull("Expected token $expectedType for text: $text", match)
        return match!!.psi
    }

    private fun findAstNode(node: ASTNode?, expectedType: IElementType, text: String): ASTNode? {
        if (node == null) return null
        if (node.elementType == expectedType && node.text == text) return node
        var child = node.firstChildNode
        while (child != null) {
            val match = findAstNode(child, expectedType, text)
            if (match != null) return match
            child = child.treeNext
        }
        return null
    }
}
