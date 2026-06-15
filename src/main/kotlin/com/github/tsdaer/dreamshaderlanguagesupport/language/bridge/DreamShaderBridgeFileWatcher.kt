package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

import com.github.tsdaer.dreamshaderlanguagesupport.language.preview.DreamShaderPreviewListener
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.Alarm

/**
 * Bridge 文件监听器。
 *
 * 监听 `Saved/DreamShader/Bridge/` 下四个已知 Bridge 文件的 VFS 变化，
 * 经防抖后让 Bridge 仓库缓存失效、重读诊断、重跑高亮并刷新状态栏，
 * 实现 UE 重新生成 Bridge 产物时插件功能自动适应，无需用户手动操作。
 */
internal class DreamShaderBridgeFileWatcher : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (project.isDisposed) return
        // 单测模式下高亮由测试 fixture 同步驱动，后台监听触发的 daemon restart 会与之冲突；
        // 该自动刷新行为仅在真实 IDE 运行时启用。
        if (ApplicationManager.getApplication().isUnitTestMode) return
        val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, project)
        val connection = project.messageBus.connect()
        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                if (project.isDisposed) return
                if (events.none { isBridgeFileEvent(it) }) return
                alarm.cancelAllRequests()
                alarm.addRequest({ refreshBridge(project) }, DEBOUNCE_MS)
            }
        })
    }

    private fun isBridgeFileEvent(event: VFileEvent): Boolean {
        val path = event.path.replace('\\', '/')
        if (!path.contains("/$BRIDGE_RELATIVE_DIR/")) return false
        val fileName = path.substringAfterLast('/')
        if (path.contains("/$BRIDGE_RELATIVE_DIR/Preview/") && fileName.endsWith(".png", ignoreCase = true)) {
            return true
        }
        return fileName in BRIDGE_FILE_NAMES
    }

    private fun refreshBridge(project: Project) {
        if (project.isDisposed) return
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            val activeFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
            project.getService(DreamShaderBridgeSettingsRepository::class.java)?.invalidate()
            project.getService(DreamShaderBridgeDiagnosticsRepository::class.java)?.refresh(activeFile)
            DaemonCodeAnalyzer.getInstance(project).restart()
            WindowManager.getInstance().getStatusBar(project)?.updateWidget(WIDGET_ID)
            project.messageBus.syncPublisher(DreamShaderPreviewListener.TOPIC).previewBridgeChanged()
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 250
        const val BRIDGE_RELATIVE_DIR = "Saved/DreamShader/Bridge"
        val BRIDGE_FILE_NAMES = setOf(
            "diagnostics.json",
            "settings.json",
            "material-expressions.json",
            "substrate-builtins.json",
            "preview.json"
        )
    }
}
