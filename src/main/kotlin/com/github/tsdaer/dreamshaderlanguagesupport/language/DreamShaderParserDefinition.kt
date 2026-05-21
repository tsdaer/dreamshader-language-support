package com.github.tsdaer.dreamshaderlanguagesupport.language

import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderPsiElementFactory
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

class DreamShaderParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?): Lexer = DreamShaderLexer()

    override fun createParser(project: Project?): PsiParser = DreamShaderPsiParser()

    override fun getFileNodeType(): IFileElementType = DreamShaderElementTypes.FILE

    override fun getCommentTokens(): TokenSet = DreamShaderTokenSets.COMMENTS

    override fun getStringLiteralElements(): TokenSet = DreamShaderTokenSets.STRINGS

    override fun createElement(node: ASTNode): PsiElement {
        return DreamShaderPsiElementFactory.createElement(node)
    }

    override fun createFile(viewProvider: FileViewProvider): PsiFile = DreamShaderPsiFile(viewProvider)

    override fun spaceExistenceTypeBetweenTokens(left: ASTNode, right: ASTNode): ParserDefinition.SpaceRequirements {
        if (left.elementType == TokenType.WHITE_SPACE || right.elementType == TokenType.WHITE_SPACE) {
            return ParserDefinition.SpaceRequirements.MAY
        }
        return ParserDefinition.SpaceRequirements.MAY
    }
}
