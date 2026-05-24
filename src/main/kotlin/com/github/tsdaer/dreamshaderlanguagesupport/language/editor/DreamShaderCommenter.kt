package com.github.tsdaer.dreamshaderlanguagesupport.language.editor
import com.intellij.lang.Commenter

/**
 * Implementation of DreamShaderCommenter.
 */
class DreamShaderCommenter : Commenter {
    override fun getLineCommentPrefix(): String = "//"

    override fun getBlockCommentPrefix(): String = "/*"

    override fun getBlockCommentSuffix(): String = "*/"

    override fun getCommentedBlockCommentPrefix(): String? = null

    override fun getCommentedBlockCommentSuffix(): String? = null
}
