package com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement

/**
 * Implementation of DreamShaderSemanticAnnotator.
 */
class DreamShaderSemanticAnnotator : Annotator, DumbAware {
    private val pipeline = DreamShaderSemanticAnnotationPipeline()

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        pipeline.annotate(element, holder)
    }
}
