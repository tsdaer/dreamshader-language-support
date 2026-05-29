package com.github.tsdaer.dreamshaderlanguagesupport.language.welcome

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Key
import com.intellij.util.Alarm
import com.intellij.testFramework.LightVirtualFile
import java.util.Properties
import java.util.Locale

private const val DREAMSHADER_WELCOME_FILE_NAME = "DreamShader Welcome"
private const val DREAMSHADER_WELCOME_EDITOR_TYPE_ID = "dreamshader-welcome-editor"
private val DREAMSHADER_WELCOME_FILE_KEY = Key.create<DreamShaderWelcomeVirtualFile>("dreamshader.welcome.virtualFile")
private val DREAMSHADER_CHANGELOG_CHINESE_HEADER = Regex("""^\s*###\s+中文\s*$""", setOf(RegexOption.IGNORE_CASE))
private const val DREAMSHADER_PLUGIN_INFO_RESOURCE = "dreamshader-plugin.properties"
private const val DREAMSHADER_CHANGELOG_RESOURCE = "CHANGELOG.md"

internal fun showWelcomeDialog(project: Project, forceManual: Boolean = false) {
    val currentVersion = readPluginVersion().takeUnless { it.isNullOrBlank() }
        ?: DreamShaderBundle.message("common.unknown")

    val stateService = ApplicationManager.getApplication().getService(DreamShaderWelcomeStateService::class.java)
    val decision = if (forceManual) {
        DreamShaderWelcomeStateService.WelcomeDecision(
            reason = DreamShaderWelcomeStateService.WelcomeReason.MANUAL,
            previousVersion = stateService.currentRecordedVersion(),
            currentVersion = currentVersion
        )
    } else {
        stateService.decideAndMark(currentVersion)
    } ?: return

    ApplicationManager.getApplication().invokeLater {
        if (project.isDisposed) return@invokeLater
        openWelcomePageInEditor(project, decision)
    }
}

internal class DreamShaderWelcomeProjectActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        showWelcomeDialog(project, forceManual = false)
    }
}

internal class DreamShaderWelcomeVirtualFile(
    val htmlContent: String
) : LightVirtualFile(DREAMSHADER_WELCOME_FILE_NAME) {
    init {
        setWritable(false)
    }
}

internal fun openWelcomePageInEditor(
    project: Project,
    decision: DreamShaderWelcomeStateService.WelcomeDecision
) {
    val manager = FileEditorManager.getInstance(project)
    val previous = project.getUserData(DREAMSHADER_WELCOME_FILE_KEY)
    if (previous != null) {
        manager.closeFile(previous)
    }

    val file = DreamShaderWelcomeVirtualFile(buildWhatsNewLikeHtml(decision))
    project.putUserData(DREAMSHADER_WELCOME_FILE_KEY, file)
    manager.openFile(file, true)
    forceSelectWelcomeEditor(project, file)
}

private fun forceSelectWelcomeEditor(project: Project, file: DreamShaderWelcomeVirtualFile) {
    val manager = FileEditorManager.getInstance(project)
    manager.setSelectedEditor(file, DREAMSHADER_WELCOME_EDITOR_TYPE_ID)

    val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, project)
    val delaysMs = intArrayOf(60, 140, 260, 420, 700, 1100)
    for (delay in delaysMs) {
        alarm.addRequest({
            if (project.isDisposed || !file.isValid) return@addRequest
            manager.setSelectedEditor(file, DREAMSHADER_WELCOME_EDITOR_TYPE_ID)
        }, delay)
    }
}

internal fun buildWhatsNewLikeHtml(decision: DreamShaderWelcomeStateService.WelcomeDecision): String {
    val badge = DreamShaderBundle.message(
        when (decision.reason) {
            DreamShaderWelcomeStateService.WelcomeReason.FIRST_INSTALL -> "welcome.badge.firstInstall"
            DreamShaderWelcomeStateService.WelcomeReason.UPDATED -> "welcome.badge.updated"
            DreamShaderWelcomeStateService.WelcomeReason.MANUAL -> "welcome.badge.manual"
        }
    )
    val titleText = DreamShaderBundle.message("welcome.header.title", decision.currentVersion)
    val subtitleText = buildSubtitleText(decision)
    val changes = extractChangeNotesHtml()
    val features = DreamShaderBundle.message("welcome.section.features.html")
    val howTo = DreamShaderBundle.message("welcome.section.howTo.html")
    val versionLine = DreamShaderBundle.message(
        "welcome.section.changes.versionLine",
        decision.previousVersion ?: DreamShaderBundle.message("common.unknown"),
        decision.currentVersion
    )

    return """
        <!doctype html>
        <html>
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1" />
          <style>
            :root {
              --bg1: #eef4ff;
              --bg2: #dbe6ff;
              --panel: #ffffffee;
              --text: #172030;
              --muted: #4e5b73;
              --line: #d7deef;
              --accent: #2f6ce5;
              --accent-soft: #e5efff;
            }
            @media (prefers-color-scheme: dark) {
              :root {
                --bg1: #1f2531;
                --bg2: #141923;
                --panel: #232938f0;
                --text: #e8edf7;
                --muted: #b1bdd2;
                --line: #374054;
                --accent: #77a6ff;
                --accent-soft: #263450;
              }
            }
            html, body {
              margin: 0;
              padding: 0;
              font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei UI", sans-serif;
              color: var(--text);
              background: linear-gradient(145deg, var(--bg1), var(--bg2));
            }
            .wrap {
              max-width: 1100px;
              margin: 0 auto;
              padding: 26px 26px 40px;
            }
            .hero {
              background: var(--panel);
              border: 1px solid var(--line);
              border-radius: 16px;
              padding: 22px 24px;
              box-shadow: 0 10px 30px rgba(20, 40, 80, 0.08);
              display: flex;
              justify-content: space-between;
              gap: 16px;
              align-items: flex-start;
            }
            .title {
              font-size: 30px;
              line-height: 1.2;
              margin: 0 0 8px;
              font-weight: 780;
              letter-spacing: 0.2px;
            }
            .subtitle {
              margin: 0;
              color: var(--muted);
              font-size: 15px;
            }
            .badge {
              white-space: nowrap;
              border: 1px solid var(--accent);
              background: var(--accent-soft);
              color: var(--accent);
              font-size: 12px;
              letter-spacing: 0.3px;
              text-transform: uppercase;
              border-radius: 999px;
              padding: 7px 12px;
              font-weight: 700;
            }
            .grid {
              margin-top: 16px;
              display: grid;
              grid-template-columns: 1fr 1fr;
              gap: 16px;
            }
            .card {
              background: var(--panel);
              border: 1px solid var(--line);
              border-radius: 14px;
              padding: 16px 18px;
              box-shadow: 0 4px 18px rgba(24, 42, 72, 0.06);
            }
            .card h2 {
              margin: 0 0 10px;
              font-size: 18px;
            }
            .card p {
              margin: 0 0 10px;
              color: var(--muted);
            }
            .card ul {
              margin: 0;
              padding-left: 20px;
            }
            .card li {
              margin: 0 0 8px;
            }
            .changes {
              margin-top: 16px;
              background: var(--panel);
              border: 1px solid var(--line);
              border-radius: 14px;
              padding: 16px 18px;
            }
            .changes-head {
              display: flex;
              justify-content: space-between;
              align-items: baseline;
              gap: 12px;
              margin-bottom: 8px;
            }
            .changes h2 {
              margin: 0;
              font-size: 18px;
            }
            .version {
              color: var(--muted);
              font-size: 13px;
            }
            .note {
              margin-top: 10px;
              padding: 14px;
              border: 1px dashed var(--line);
              border-radius: 10px;
              background: color-mix(in srgb, var(--panel) 82%, #0000);
            }
            pre {
              white-space: pre-wrap;
              word-break: break-word;
            }
            code {
              background: rgba(120, 136, 168, 0.18);
              border-radius: 6px;
              padding: 1px 5px;
            }
            a {
              color: var(--accent);
              text-decoration: none;
            }
            a:hover { text-decoration: underline; }
            @media (max-width: 900px) {
              .grid { grid-template-columns: 1fr; }
              .hero { flex-direction: column; }
            }
          </style>
        </head>
        <body>
          <div class="wrap">
            <section class="hero">
              <div>
                <h1 class="title">${escapeHtml(titleText)}</h1>
                <p class="subtitle">${escapeHtml(subtitleText)}</p>
              </div>
              <span class="badge">${escapeHtml(badge)}</span>
            </section>

            <section class="grid">
              <article class="card">
                <h2>${escapeHtml(DreamShaderBundle.message("welcome.section.features.title"))}</h2>
                $features
              </article>
              <article class="card">
                <h2>${escapeHtml(DreamShaderBundle.message("welcome.section.howTo.title"))}</h2>
                $howTo
              </article>
            </section>

            <section class="changes">
              <div class="changes-head">
                <h2>${escapeHtml(DreamShaderBundle.message("welcome.section.changes.title"))}</h2>
                <span class="version">${escapeHtml(versionLine)}</span>
              </div>
              <div class="note">$changes</div>
            </section>
          </div>
        </body>
        </html>
    """.trimIndent()
}

private fun buildSubtitleText(decision: DreamShaderWelcomeStateService.WelcomeDecision): String {
    return when (decision.reason) {
        DreamShaderWelcomeStateService.WelcomeReason.FIRST_INSTALL -> {
            DreamShaderBundle.message("welcome.header.subtitle.firstInstall")
        }
        DreamShaderWelcomeStateService.WelcomeReason.UPDATED -> {
            DreamShaderBundle.message(
                "welcome.header.subtitle.updated",
                decision.previousVersion ?: DreamShaderBundle.message("common.unknown")
            )
        }
        DreamShaderWelcomeStateService.WelcomeReason.MANUAL -> {
            DreamShaderBundle.message("welcome.header.subtitle.manual")
        }
    }
}

private fun extractChangeNotesHtml(): String {
    val notes = readBundledChangelog().trim()
    if (notes.isNotBlank()) {
        return selectLocalizedChangeNotesMarkdown(notes)
    }
    return DreamShaderBundle.message("welcome.section.changes.fallback")
}

private fun selectLocalizedChangeNotesMarkdown(notes: String): String {
    val raw = notes.trim()
    val lines = raw.lineSequence().toList()
    val chineseIndex = lines.indexOfFirst { line -> DREAMSHADER_CHANGELOG_CHINESE_HEADER.matches(line) }
    if (chineseIndex < 0) {
        return "<pre>$raw</pre>"
    }

    val isChinese = Locale.getDefault().language.equals("zh", ignoreCase = true)
    val localized = if (isChinese) {
        lines.drop(chineseIndex + 1).joinToString("\n").trim()
    } else {
        lines.take(chineseIndex).joinToString("\n").trim()
    }
    return "<pre>$localized</pre>"
}

private fun readPluginVersion(): String? {
    val loader = DreamShaderWelcomeProjectActivity::class.java.classLoader ?: return null
    return runCatching {
        loader.getResourceAsStream(DREAMSHADER_PLUGIN_INFO_RESOURCE)?.use { input ->
            val properties = Properties()
            properties.load(input)
            properties.getProperty("version")?.trim()
        }
    }.getOrNull()
}

private fun readBundledChangelog(): String {
    val loader = DreamShaderWelcomeProjectActivity::class.java.classLoader ?: return ""
    return runCatching {
        loader.getResourceAsStream(DREAMSHADER_CHANGELOG_RESOURCE)?.use { input ->
            input.bufferedReader(Charsets.UTF_8).readText()
        }.orEmpty()
    }.getOrDefault("")
}

private fun escapeHtml(input: String): String {
    return input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}
