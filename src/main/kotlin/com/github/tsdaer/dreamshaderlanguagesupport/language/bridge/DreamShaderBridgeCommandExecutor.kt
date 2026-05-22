package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

import com.github.tsdaer.dreamshaderlanguagesupport.language.DreamShaderBundle
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import java.io.File

internal data class DreamShaderBridgeCommandResult(
    val success: Boolean,
    val message: String,
    val exitCode: Int? = null
)

internal object DreamShaderBridgeCommandExecutor {
    private const val PLACEHOLDER_FILE = "%file%"
    private const val PLACEHOLDER_PROJECT = "%projectRoot%"
    private const val PLACEHOLDER_BRIDGE = "%bridgeDir%"

    fun execute(
        project: Project,
        commandTemplate: String,
        activeFilePath: String?,
        bridgeDirectoryPath: String?
    ): DreamShaderBridgeCommandResult {
        val command = commandTemplate.trim()
        if (command.isBlank()) {
            return DreamShaderBridgeCommandResult(false, DreamShaderBundle.message("bridge.command.empty"))
        }

        val projectRoot = project.basePath?.replace('\\', '/').orEmpty()
        val substituted = command
            .replace(PLACEHOLDER_FILE, activeFilePath.orEmpty())
            .replace(PLACEHOLDER_PROJECT, projectRoot)
            .replace(PLACEHOLDER_BRIDGE, bridgeDirectoryPath.orEmpty())

        return runCatching {
            val cmd = if (SystemInfo.isWindows) {
                GeneralCommandLine("cmd", "/c", substituted)
            } else {
                GeneralCommandLine("sh", "-lc", substituted)
            }
            val workDir = project.basePath?.let { File(it) }
            if (workDir != null && workDir.exists() && workDir.isDirectory) {
                cmd.withWorkDirectory(workDir)
            }
            val output = CapturingProcessHandler(cmd).runProcess(5 * 60 * 1000)
            if (output.isTimeout) {
                DreamShaderBridgeCommandResult(false, DreamShaderBundle.message("bridge.command.timeout"), null)
            } else if (output.exitCode == 0) {
                DreamShaderBridgeCommandResult(
                    true,
                    output.stdout.trim().ifBlank { DreamShaderBundle.message("bridge.command.successDefault") },
                    output.exitCode
                )
            } else {
                val stderr = output.stderr.trim()
                val stdout = output.stdout.trim()
                DreamShaderBridgeCommandResult(
                    false,
                    if (stderr.isNotBlank()) stderr else stdout.ifBlank { DreamShaderBundle.message("bridge.command.failedDefault") },
                    output.exitCode
                )
            }
        }.getOrElse {
            DreamShaderBridgeCommandResult(
                false,
                it.message ?: DreamShaderBundle.message("bridge.command.executeFailed"),
                null
            )
        }
    }
}
