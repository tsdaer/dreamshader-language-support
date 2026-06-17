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
private const val DREAMSHADER_PLUGIN_INFO_RESOURCE = "dreamshader-plugin.properties"
private const val DREAMSHADER_CHANGELOG_RESOURCE = "CHANGELOG.md"
private const val DREAMSHADER_CHANGELOG_ZH_RESOURCE = "CHANGELOG.zh-CN.md"

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
    val changes = extractChangeNotesHtml(decision.currentVersion)
    val features = DreamShaderBundle.message("welcome.section.features.html")
    val howTo = DreamShaderBundle.message("welcome.section.howTo.html")
    val setup = DreamShaderBundle.message("welcome.section.setup.html")
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
              color-scheme: light dark;
              --bg1: #edf3f8;
              --bg2: #f8fbfd;
              --ink: #ffffff;
              --panel: rgba(255, 255, 255, .86);
              --text: #172030;
              --muted: #4e5b73;
              --line: #d7deef;
              --accent: #1d6fc7;
              --accent-2: #20a07c;
              --accent-soft: #e5efff;
              --shadow: rgba(28, 42, 64, .13);
            }
            @media (prefers-color-scheme: dark) {
              :root {
                --bg1: #151922;
                --bg2: #252b35;
                --ink: #10141b;
                --panel: rgba(35, 41, 54, .88);
                --text: #e8edf7;
                --muted: #b1bdd2;
                --line: #374054;
                --accent: #77a6ff;
                --accent-2: #6ed6b3;
                --accent-soft: #263450;
                --shadow: rgba(0, 0, 0, .34);
              }
            }
            html, body {
              margin: 0;
              padding: 0;
              font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei UI", sans-serif;
              color: var(--text);
              background:
                radial-gradient(circle at 12% 8%, rgba(29, 111, 199, .20), transparent 30%),
                radial-gradient(circle at 88% 14%, rgba(32, 160, 124, .16), transparent 28%),
                linear-gradient(145deg, var(--bg1), var(--bg2));
            }
            .wrap {
              max-width: 1120px;
              margin: 0 auto;
              padding: 30px 28px 44px;
            }
            .hero {
              background: var(--panel);
              border: 1px solid var(--line);
              border-radius: 24px;
              padding: 28px 30px;
              box-shadow: 0 20px 60px var(--shadow);
              display: flex;
              justify-content: space-between;
              gap: 22px;
              align-items: flex-start;
              position: relative;
              overflow: hidden;
            }
            .hero:after {
              content: "";
              position: absolute;
              width: 280px;
              height: 280px;
              right: -90px;
              top: -120px;
              border-radius: 999px;
              background: linear-gradient(135deg, rgba(29,111,199,.18), rgba(32,160,124,.12));
              pointer-events: none;
            }
            .title {
              font-size: 34px;
              line-height: 1.2;
              margin: 0 0 8px;
              font-weight: 800;
              letter-spacing: -0.4px;
            }
            .subtitle {
              margin: 0;
              color: var(--muted);
              font-size: 16px;
              max-width: 760px;
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
              position: relative;
              z-index: 1;
            }
            .quick {
              display: flex;
              flex-wrap: wrap;
              gap: 8px;
              margin-top: 18px;
            }
            .chip {
              border: 1px solid var(--line);
              border-radius: 999px;
              padding: 6px 10px;
              color: var(--muted);
              background: color-mix(in srgb, var(--panel) 78%, var(--ink));
              font-size: 12px;
              font-weight: 650;
            }
            .grid {
              margin-top: 18px;
              display: grid;
              grid-template-columns: 1fr 1fr 1fr;
              gap: 18px;
            }
            .card {
              background: var(--panel);
              border: 1px solid var(--line);
              border-radius: 18px;
              padding: 18px 20px;
              box-shadow: 0 8px 28px var(--shadow);
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
            .card li::marker {
              color: var(--accent);
            }
            .changes {
              margin-top: 18px;
              background: var(--panel);
              border: 1px solid var(--line);
              border-radius: 18px;
              padding: 18px 20px;
              box-shadow: 0 8px 28px var(--shadow);
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
              padding: 16px;
              border: 1px dashed var(--line);
              border-radius: 14px;
              background: color-mix(in srgb, var(--panel) 78%, var(--ink));
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
              font-weight: 650;
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
                <div class="quick">
                  <span class="chip">.dsm</span>
                  <span class="chip">.dsf</span>
                  <span class="chip">.dsh</span>
                  <span class="chip">Bridge</span>
                  <span class="chip">Packages</span>
                </div>
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
              <article class="card">
                <h2>${escapeHtml(DreamShaderBundle.message("welcome.section.setup.title"))}</h2>
                $setup
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

private fun extractChangeNotesHtml(currentVersion: String): String {
    val notes = extractCurrentChangelogSection(readBundledChangelogByLocale(), currentVersion).trim()
    if (notes.isNotBlank()) {
        return "<pre>${escapeHtml(notes)}</pre>"
    }
    return DreamShaderBundle.message("welcome.section.changes.fallback")
}

private fun extractCurrentChangelogSection(changelog: String, currentVersion: String): String {
    val lines = changelog.lines()
    val exactHeading = Regex("^## \\[${Regex.escape(currentVersion)}\\].*$")
    val versionHeading = Regex("^## \\[\\d+\\.\\d+\\.\\d+(?:[-+][^]]+)?\\].*$")
    val anySectionHeading = Regex("^## \\[[^]]+].*$")
    val linkReference = Regex("^\\[[^]]+]:\\s+.*$")
    val exactStart = lines.indexOfFirst { exactHeading.matches(it.trim()) }
    val start = exactStart.takeIf { it >= 0 }
        ?: lines.indexOfFirst { versionHeading.matches(it.trim()) }
    if (start < 0) return changelog.trim()

    return lines.asSequence()
        .drop(start + 1)
        .takeWhile { line ->
            val trimmed = line.trim()
            !anySectionHeading.matches(trimmed) && !linkReference.matches(trimmed)
        }
        .joinToString("\n")
        .trim()
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

private fun readBundledChangelogByLocale(): String {
    val isChinese = Locale.getDefault().language.equals("zh", ignoreCase = true)
    val resource = if (isChinese) DREAMSHADER_CHANGELOG_ZH_RESOURCE else DREAMSHADER_CHANGELOG_RESOURCE
    return readBundledTextResource(resource).ifBlank {
        if (resource == DREAMSHADER_CHANGELOG_ZH_RESOURCE) {
            readBundledTextResource(DREAMSHADER_CHANGELOG_RESOURCE)
        } else {
            ""
        }
    }
}

private fun readBundledTextResource(resource: String): String {
    val loader = DreamShaderWelcomeProjectActivity::class.java.classLoader ?: return ""
    return runCatching {
        loader.getResourceAsStream(resource)?.use { input ->
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
