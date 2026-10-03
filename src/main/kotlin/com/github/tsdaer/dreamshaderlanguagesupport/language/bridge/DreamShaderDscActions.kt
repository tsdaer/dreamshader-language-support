package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import java.io.File
import java.util.concurrent.TimeUnit

private val DSC_EXTENSIONS = setOf("dsm", "dsf", "dsh", "dss", "dsi")

/** One finished `dsc` invocation. */
data class DreamShaderDscResult(
    val exitCode: Int,
    val output: String
) {
    val success: Boolean get() = exitCode == 0
}

/**
 * `dsc` 动作测试钩子（A8）：替换进程执行与迁移确认对话框。
 */
internal object DreamShaderDscActionTestHooks {
    @Volatile
    var commandRunner: ((command: List<String>, workingDir: String?) -> DreamShaderDscResult)? = null

    /** 迁移预演通过后的确认；返回 null 走真实对话框。 */
    @Volatile
    var migrateConfirmer: ((project: Project, filePath: String) -> Boolean)? = null

    fun reset() {
        commandRunner = null
        migrateConfirmer = null
    }
}

/**
 * `dsc` 命令执行器（A8）。
 *
 * 路径可以是 `dsc.exe` 一类可执行文件，也可以是上游的 `dsc.ps1`（自动经 PowerShell 包装）。
 * 输出截断到通知可读的长度。
 */
internal object DreamShaderDscCommandRunner {
    private const val TIMEOUT_SECONDS = 120L
    private const val MAX_OUTPUT_CHARS = 4000

    fun commandFor(dscPath: String, args: List<String>): List<String> {
        val normalized = dscPath.trim()
        return if (normalized.endsWith(".ps1", ignoreCase = true)) {
            listOf("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", normalized) + args
        } else {
            listOf(normalized) + args
        }
    }

    fun run(project: Project, dscPath: String, args: List<String>): DreamShaderDscResult {
        val command = commandFor(dscPath, args)
        val hooked = DreamShaderDscActionTestHooks.commandRunner
        if (hooked != null) {
            return hooked(command, project.basePath)
        }
        return runCatching {
            val workingDir = project.basePath?.let { File(it) }?.takeIf { it.isDirectory }
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .apply { workingDir?.let { directory(it) } }
                .start()
            val finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                DreamShaderDscResult(-1, DreamShaderBundle.message("dsc.notification.timedOut", TIMEOUT_SECONDS))
            } else {
                val output = process.inputStream.bufferedReader().readText()
                DreamShaderDscResult(process.exitValue(), StringUtil.shortenTextWithEllipsis(output, MAX_OUTPUT_CHARS, 0))
            }
        }.getOrElse {
            DreamShaderDscResult(
                -1,
                DreamShaderBundle.message("dsc.notification.launchFailed", it.message ?: "")
            )
        }
    }
}

/** 四个 `dsc` 动作共用的骨架：解析路径 → 组参数 → 执行 → 通知。 */
abstract class DreamShaderDscActionBase(
    private val textKey: String,
    private val descriptionKey: String
) : DumbAwareAction() {

    init {
        templatePresentation.text = DreamShaderBundle.message(textKey)
        templatePresentation.description = DreamShaderBundle.message(descriptionKey)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val configured = project != null && project
            .getService(DreamShaderProjectSettings::class.java)
            .resolvedDscExecutablePath()
            .isNotBlank()
        e.presentation.isEnabled = configured
        e.presentation.description = if (configured) {
            DreamShaderBundle.message(descriptionKey)
        } else {
            DreamShaderBundle.message("dsc.action.notConfigured.description")
        }
    }

    protected abstract fun buildArgs(project: Project, e: AnActionEvent): List<String>?

    protected open fun onSuccess(project: Project, args: List<String>, result: DreamShaderDscResult) {
        notify(project, NotificationType.INFORMATION, result.output.ifBlank { DreamShaderBundle.message("dsc.notification.succeeded") })
    }

    protected open fun onFailure(project: Project, args: List<String>, result: DreamShaderDscResult) {
        notify(project, NotificationType.ERROR, result.output.ifBlank { DreamShaderBundle.message("dsc.notification.failed", result.exitCode) })
    }

    protected fun notify(project: Project, type: NotificationType, content: String) {
        DreamShaderBridgeActionTestHooks.notificationSink?.invoke(
            project,
            type,
            DreamShaderBundle.message("dsc.notification.title"),
            content
        ) ?: com.intellij.notification.NotificationGroupManager.getInstance()
            .getNotificationGroup("DreamShader Notifications")
            .createNotification(DreamShaderBundle.message("dsc.notification.title"), content, type)
            .notify(project)
    }

    protected fun dscPath(project: Project): String =
        project.getService(DreamShaderProjectSettings::class.java).resolvedDscExecutablePath().trim()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val dsc = dscPath(project)
        if (dsc.isBlank()) {
            notify(project, NotificationType.WARNING, DreamShaderBundle.message("dsc.action.notConfigured.description"))
            return
        }
        val args = buildArgs(project, e)
        if (args == null) {
            notify(project, NotificationType.WARNING, DreamShaderBundle.message("dsc.notification.noActiveFile"))
            return
        }
        val result = DreamShaderDscCommandRunner.run(project, dsc, args)
        if (result.success) {
            onSuccess(project, args, result)
        } else {
            onFailure(project, args, result)
        }
    }

    protected fun activeDreamShaderFile(e: AnActionEvent): String? {
        val contextFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val candidate = contextFile
            ?: FileEditorManager.getInstance(e.project ?: return null).selectedFiles.firstOrNull()
            ?: EditorFactory.getInstance().allEditors.firstOrNull { it.project == e.project }
                ?.let { com.intellij.psi.PsiDocumentManager.getInstance(it.project!!).getPsiFile(it.document)?.virtualFile }
        return candidate?.takeIf { it.extension?.lowercase() in DSC_EXTENSIONS }?.path?.replace('\\', '/')
    }
}

/** `dsc check <file>`：引擎侧权威诊断刷新。 */
class DreamShaderDscCheckAction : DreamShaderDscActionBase(
    "action.DreamShader.Dsc.Check.text",
    "action.DreamShader.Dsc.Check.description"
) {
    override fun buildArgs(project: Project, e: AnActionEvent): List<String>? {
        val file = activeDreamShaderFile(e) ?: return null
        return listOf("check", file)
    }

    override fun onSuccess(project: Project, args: List<String>, result: DreamShaderDscResult) {
        super.onSuccess(project, args, result)
        // 引擎刚重写过诊断工件；让仓库立即重读当前文件。
        val activeFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        project.getService(DreamShaderBridgeDiagnosticsRepository::class.java).refresh(activeFile)
    }
}

/** `dsc fmt <file>`：上游格式化（refusing-with-diagnostic 语义见 DSH9042-9044）。 */
class DreamShaderDscFormatAction : DreamShaderDscActionBase(
    "action.DreamShader.Dsc.Format.text",
    "action.DreamShader.Dsc.Format.description"
) {
    override fun buildArgs(project: Project, e: AnActionEvent): List<String>? {
        val file = activeDreamShaderFile(e) ?: return null
        return listOf("fmt", file)
    }
}

/** `dsc migrate <file>`：先 `-Check` 预演，确认后真正迁移（备份至 Saved/DreamShader/Migrated/）。 */
class DreamShaderDscMigrateAction : DreamShaderDscActionBase(
    "action.DreamShader.Dsc.Migrate.text",
    "action.DreamShader.Dsc.Migrate.description"
) {
    override fun buildArgs(project: Project, e: AnActionEvent): List<String>? {
        val file = activeDreamShaderFile(e) ?: return null
        return listOf("migrate", file)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val dsc = dscPath(project)
        if (dsc.isBlank()) {
            notify(project, NotificationType.WARNING, DreamShaderBundle.message("dsc.action.notConfigured.description"))
            return
        }
        val file = activeDreamShaderFile(e)
        if (file == null) {
            notify(project, NotificationType.WARNING, DreamShaderBundle.message("dsc.notification.noActiveFile"))
            return
        }
        val extension = file.substringAfterLast('/').substringAfterLast('.')
        if (extension !in setOf("dsm", "dsf", "dsh")) {
            notify(project, NotificationType.WARNING, DreamShaderBundle.message("dsc.notification.migrateRefusedExtension", extension))
            return
        }

        // 第一步：预演。`-Check` 只证明迁移可行，不写任何文件。
        val checkResult = DreamShaderDscCommandRunner.run(project, dsc, listOf("migrate", file, "-Check"))
        if (!checkResult.success) {
            notify(
                project,
                NotificationType.ERROR,
                DreamShaderBundle.message("dsc.notification.migrateCheckFailed") + "\n" +
                    checkResult.output.ifBlank { DreamShaderBundle.message("dsc.notification.failed", checkResult.exitCode) }
            )
            return
        }

        val hookConfirmed = DreamShaderDscActionTestHooks.migrateConfirmer?.invoke(project, file)
        val confirmed = hookConfirmed ?: run {
            if (ApplicationManager.getApplication().isUnitTestMode) {
                true
            } else {
                Messages.showYesNoDialog(
                    project,
                    DreamShaderBundle.message("dsc.migrate.confirm.message", file),
                    DreamShaderBundle.message("dsc.migrate.confirm.title"),
                    Messages.getQuestionIcon()
                ) == Messages.YES
            }
        }
        if (!confirmed) {
            notify(project, NotificationType.INFORMATION, DreamShaderBundle.message("dsc.migrate.cancelled"))
            return
        }

        val result = DreamShaderDscCommandRunner.run(project, dsc, listOf("migrate", file))
        if (result.success) {
            notify(
                project,
                NotificationType.INFORMATION,
                DreamShaderBundle.message("dsc.migrate.succeeded", file) + "\n" + result.output
            )
        } else {
            notify(project, NotificationType.ERROR, result.output.ifBlank { DreamShaderBundle.message("dsc.notification.failed", result.exitCode) })
        }
    }
}

/** `dsc index`：手动刷新符号索引工件。 */
class DreamShaderDscIndexAction : DreamShaderDscActionBase(
    "action.DreamShader.Dsc.Index.text",
    "action.DreamShader.Dsc.Index.description"
) {
    override fun buildArgs(project: Project, e: AnActionEvent): List<String> = listOf("index")
}
