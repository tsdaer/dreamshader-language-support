package com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile
import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager

/** 未知类型规则的问题数据（D8 共享内核，由管线产出、inspection 渲染）。 */
internal data class DreamShaderUnknownTypeProblem(
    val range: TextRange,
    val type: String,
    val message: String,
    val suggestion: String?
)

/**
 * D8 首批迁移：section 输入/输出/属性体中的未知类型。
 *
 * 原本由语义 annotator 即时报告，现迁为 `LocalInspectionTool`：获得 Inspect Code 批量
 * 扫描、用户可调级别/开关（Settings → Editor → Inspections → DreamShader）。annotator
 * 不再报告该规则，二者不重复。规则本身不变——类型集合与建议算法仍来自共享管线内核，
 * Bridge/`dsc check` 依旧是语义权威。
 */
class DreamShaderUnknownTypeInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                val psiFile = file as? DreamShaderPsiFile ?: return
                DreamShaderSemanticAnnotationPipeline().collectUnknownTypeProblems(psiFile).forEach { problem ->
                    val fixes = problem.suggestion
                        ?.let { arrayOf<LocalQuickFix>(DreamShaderReplaceTypeQuickFix(file, problem.range, it)) }
                        ?: emptyArray()
                    holder.registerProblem(file, problem.range, problem.message, *fixes)
                }
            }
        }
    }
}

/** D8 修复动作：把未知类型替换为建议的拼写（annotator 时代同名的语义快修）。 */
internal class DreamShaderReplaceTypeQuickFix(
    file: PsiFile,
    range: TextRange,
    private val replacement: String
) : LocalQuickFix {

    private val filePointer = SmartPointerManager.createPointer(file)
    private val relativeRange = TextRange(range.startOffset, range.endOffset)

    override fun getFamilyName(): String = DreamShaderBundle.message("quickfix.family.semantic")

    override fun getName(): String = DreamShaderBundle.message("quickfix.typeReplaceWithSuggestion", replacement)

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val targetFile = filePointer.element ?: return
        if (!FileModificationService.getInstance().preparePsiElementForWrite(targetFile)) return
        WriteCommandAction.runWriteCommandAction(project) {
            val document = PsiDocumentManager.getInstance(project).getDocument(targetFile) ?: return@runWriteCommandAction
            document.replaceString(relativeRange.startOffset, relativeRange.endOffset, replacement)
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }
    }
}
