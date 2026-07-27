package com.github.tsdaer.dreamshaderlanguagesupport.language.welcome

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderSettingsConfigurable
import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.event.HyperlinkEvent

private const val PLUGIN_ID = "com.github.tsdaer.dreamshaderlanguagesupport"
private const val DREAMSHADER_WELCOME_NOTIFICATION_GROUP_ID = "DreamShader Welcome"
private const val VERSION_PROPERTY = "$PLUGIN_ID.version"
private const val INITIAL_VERSION = "0.0.0"
private const val VERSION_RESOURCE = "dreamshader-plugin.properties"
private const val CHANGELOG_RESOURCE = "CHANGELOG.md"

internal enum class WelcomeReason {
    FIRST_INSTALL,
    UPDATED,
    MANUAL
}

internal data class WelcomeDecision(
    val reason: WelcomeReason,
    val previousVersion: String?,
    val currentVersion: String
)

internal fun showWelcomeDialog(project: Project, forceManual: Boolean = false) {
    if (project.isDisposed) return

    val currentVersion = readResourceVersion() ?: return
    val changeNotes = readResourceChangeNotes(currentVersion)
    val properties = PropertiesComponent.getInstance()
    val lastVersionString = properties.getValue(VERSION_PROPERTY, INITIAL_VERSION)

    val reason = when {
        forceManual -> WelcomeReason.MANUAL
        lastVersionString == INITIAL_VERSION -> WelcomeReason.FIRST_INSTALL
        lastVersionString != currentVersion -> WelcomeReason.UPDATED
        else -> return
    }

    val decision = WelcomeDecision(
        reason = reason,
        previousVersion = lastVersionString.takeIf { it != INITIAL_VERSION },
        currentVersion = currentVersion
    )

    ApplicationManager.getApplication().invokeLater {
        if (project.isDisposed) return@invokeLater

        if (isCefSupported()) {
            val success = openWelcomeWebView(project, changeNotes, decision)
            if (success) {
                properties.setValue(VERSION_PROPERTY, currentVersion)
                return@invokeLater
            }
        }
        showWelcomeNotification(project, changeNotes, decision)
        properties.setValue(VERSION_PROPERTY, currentVersion)
    }
}

internal class DreamShaderWelcomeProjectActivity : ProjectActivity {
    private val firstProjectOpening = AtomicBoolean(true)

    override suspend fun execute(project: Project) {
        if (ApplicationManager.getApplication().isHeadlessEnvironment) return
        if (!firstProjectOpening.compareAndSet(true, false)) return
        showWelcomeDialog(project, forceManual = false)
    }
}

private fun openWelcomeWebView(
    project: Project,
    changeNotes: String,
    decision: WelcomeDecision
): Boolean {
    return try {
        val htmlContent = buildWelcomeHtml(decision, changeNotes)

        val title = DreamShaderBundle.message(
            when (decision.reason) {
                WelcomeReason.FIRST_INSTALL -> "welcome.dialog.title.firstInstall"
                WelcomeReason.UPDATED -> "welcome.dialog.title.updated"
                WelcomeReason.MANUAL -> "welcome.dialog.title.manual"
            },
            decision.currentVersion
        )

        val queryHandler = object : HTMLEditorProvider.JsQueryHandler {
            override suspend fun query(id: Long, request: String): String {
                ApplicationManager.getApplication().invokeLater {
                    handleWelcomeHyperlink(project, request)
                }
                return ""
            }
        }

        val request = HTMLEditorProvider.Request.html(htmlContent, "dreamshader://welcome")
            .withQueryHandler(queryHandler)

        val editor = HTMLEditorProvider.openEditor(project, title, request)
        editor != null
    } catch (t: Throwable) {
        Logger.getInstance("DreamShaderWelcome").warn("Failed to open welcome WebView", t)
        false
    }
}

private fun buildWelcomeHtml(decision: WelcomeDecision, changeNotes: String): String {
    val badge = DreamShaderBundle.message(
        when (decision.reason) {
            WelcomeReason.FIRST_INSTALL -> "welcome.badge.firstInstall"
            WelcomeReason.UPDATED -> "welcome.badge.updated"
            WelcomeReason.MANUAL -> "welcome.badge.manual"
        }
    )
    val titleText = DreamShaderBundle.message("welcome.header.title", decision.currentVersion)
    val subtitleText = buildSubtitleText(decision)
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
      background: color-mix(in srgb, var(--panel) 78%, transparent);
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
      background: color-mix(in srgb, var(--panel) 78%, transparent);
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
      <div class="note">$changeNotes</div>
    </section>
  </div>
  <script>
    document.addEventListener('click', function (e) {
      var el = e.target.closest('a');
      if (!el) return;
      var href = el.getAttribute('href');
      if (!href) return;
      if (href.startsWith('dreamshader://') || href.startsWith('http://') || href.startsWith('https://')) {
        e.preventDefault();
        if (window.jbCefQuery) {
          window.jbCefQuery({ request: href, onSuccess: function () {}, onFailure: function () {} });
        }
      }
    });
  </script>
</body>
</html>
    """.trimIndent()
}

private fun showWelcomeNotification(
    project: Project,
    changeNotes: String,
    decision: WelcomeDecision
) {
    val title = DreamShaderBundle.message(
        when (decision.reason) {
            WelcomeReason.FIRST_INSTALL -> "welcome.dialog.title.firstInstall"
            WelcomeReason.UPDATED -> "welcome.dialog.title.updated"
            WelcomeReason.MANUAL -> "welcome.dialog.title.manual"
        },
        decision.currentVersion
    )

    val subtitleText = buildSubtitleText(decision)
    val features = DreamShaderBundle.message("welcome.section.features.html")
    val howTo = DreamShaderBundle.message("welcome.section.howTo.html")
    val setup = DreamShaderBundle.message("welcome.section.setup.html")
    val changesTitle = DreamShaderBundle.message("welcome.section.changes.title")
    val versionLine = DreamShaderBundle.message(
        "welcome.section.changes.versionLine",
        decision.previousVersion ?: DreamShaderBundle.message("common.unknown"),
        decision.currentVersion
    )
    val changeNotesContent = changeNotes
        .takeIf { it.isNotEmpty() }
        ?: DreamShaderBundle.message("welcome.section.changes.fallback")

    val content = buildString {
        append("<p>").append(escapeHtml(subtitleText)).append("</p>")
        append("<br>")
        append("<h3>").append(escapeHtml(DreamShaderBundle.message("welcome.section.features.title"))).append("</h3>")
        append(features)
        append("<h3>").append(escapeHtml(DreamShaderBundle.message("welcome.section.howTo.title"))).append("</h3>")
        append(howTo)
        append("<h3>").append(escapeHtml(DreamShaderBundle.message("welcome.section.setup.title"))).append("</h3>")
        append(setup)
        append("<h3>").append(escapeHtml(changesTitle)).append("</h3>")
        append("<p><i>").append(escapeHtml(versionLine)).append("</i></p>")
        append(changeNotesContent)
    }

    val notification = NotificationGroupManager.getInstance()
        .getNotificationGroup(DREAMSHADER_WELCOME_NOTIFICATION_GROUP_ID)
        .createNotification(content, NotificationType.INFORMATION)
        .setTitle(title)
        .setImportant(true)

    @Suppress("DEPRECATION")
    notification.setListener { _, event ->
        if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) {
            val url = event.description ?: return@setListener
            handleWelcomeHyperlink(project, url)
        }
    }

    notification.addAction(
        NotificationAction.create(
            DreamShaderBundle.message("welcome.button.openSettings"),
            { _, notification ->
                notification.hideBalloon()
                ShowSettingsUtil.getInstance().showSettingsDialog(project, DreamShaderSettingsConfigurable::class.java)
            }
        )
    )

    notification.addAction(
        NotificationAction.create(
            DreamShaderBundle.message("welcome.button.viewChangelog"),
            { _, notification ->
                notification.hideBalloon()
                BrowserUtil.browse("https://github.com/tsdaer/dreamshader-language-support/blob/main/CHANGELOG.md")
            }
        )
    )

    if (decision.reason == WelcomeReason.FIRST_INSTALL) {
        notification.addAction(
            NotificationAction.create(
                DreamShaderBundle.message("welcome.button.start"),
                { _, notification ->
                    notification.hideBalloon()
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, DreamShaderSettingsConfigurable::class.java)
                }
            )
        )
    }

    notification.whenExpired {
        ApplicationManager.getApplication().messageBus
            .syncPublisher(DreamShaderUpdateListener.TOPIC)
            .onPostUpdate(true)
    }

    notification.notify(project)
}

internal fun handleWelcomeHyperlink(project: Project, url: String) {
    if (url.startsWith("dreamshader://open-settings")) {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, DreamShaderSettingsConfigurable::class.java)
    } else {
        try {
            BrowserUtil.browse(url)
        } catch (_: Exception) {
        }
    }
}

private fun readResourceVersion(): String? {
    return try {
        val props = java.util.Properties()
        DreamShaderWelcomeProjectActivity::class.java
            .classLoader
            .getResourceAsStream(VERSION_RESOURCE)
            ?.use { props.load(it) }
        props.getProperty("version")
    } catch (_: Exception) {
        null
    }
}

private fun readResourceChangeNotes(currentVersion: String): String {
    return try {
        val changelog = DreamShaderWelcomeProjectActivity::class.java
            .classLoader
            .getResourceAsStream(CHANGELOG_RESOURCE)
            ?.bufferedReader()
            ?.readText()
            ?: return ""
        extractChangelogSection(changelog, currentVersion)
    } catch (_: Exception) {
        ""
    }
}

private fun extractChangelogSection(changelog: String, version: String): String {
    val lines = changelog.lines()
    val headingRegex = Regex("""^## \[${Regex.escape(version)}\].*$""")
    val anySectionRegex = Regex("""^## \[[^]]+\].*$""")
    val startIndex = lines.indexOfFirst { headingRegex.matches(it.trim()) }
    if (startIndex < 0) return ""
    return lines.drop(startIndex + 1)
        .takeWhile { !anySectionRegex.matches(it.trim()) }
        .joinToString("\n")
        .trim()
}

private fun buildSubtitleText(decision: WelcomeDecision): String {
    return when (decision.reason) {
        WelcomeReason.FIRST_INSTALL ->
            DreamShaderBundle.message("welcome.header.subtitle.firstInstall")
        WelcomeReason.UPDATED ->
            DreamShaderBundle.message(
                "welcome.header.subtitle.updated",
                decision.previousVersion ?: DreamShaderBundle.message("common.unknown")
            )
        WelcomeReason.MANUAL ->
            DreamShaderBundle.message("welcome.header.subtitle.manual")
    }
}

private fun escapeHtml(input: String): String {
    return input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}
