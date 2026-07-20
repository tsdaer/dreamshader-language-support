
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel
import java.nio.file.Files
import java.nio.file.Path

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.intellij.platform")
}

dependencies {
    // Explicitly align stdlib with the Kotlin compiler version for 2026.1 Platform tests.
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation(platform("org.junit:junit-bom:6.1.0"))
    // Keep JUnit 4 APIs on the test compile classpath for IntelliJ Platform test compatibility.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        // Keep IU for reliable headless test execution.
        intellijIdea("2026.1.3")
        bundledModule("intellij.spellchecker")
        bundledModule("com.intellij.database")
        testFramework(TestFrameworkType.Platform)
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    val pluginVersion = providers.gradleProperty("version")
    inputs.property("pluginVersion", pluginVersion)
    from(layout.projectDirectory.file("CHANGELOG.md"))
    from(layout.projectDirectory.file("CHANGELOG.zh-CN.md"))
    filesMatching("dreamshader-plugin.properties") {
        val resolvedVersion = pluginVersion.orNull ?: "0.0.0"
        expand(
            mapOf(
                "pluginVersion" to resolvedVersion
            )
        )
    }
}

intellijPlatform {
    val pluginMetadata = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map { readme ->
        val startMarker = "<!-- plugin-metadata:start -->"
        val endMarker = "<!-- plugin-metadata:end -->"
        val start = readme.indexOf(startMarker)
        val end = readme.indexOf(endMarker)
        if (start in 0..<end) {
            readme.substring(start + startMarker.length, end)
        } else {
            ""
        }
    }

    // Extract a metadata value by key. Supports two shapes:
    //   1) single line:  key: value
    //   2) YAML-style block scalar:  key: |   (followed by more-indented lines)
    // Block scalars keep blank lines (paragraph breaks) and strip the common indent.
    fun extractPluginMetadataValue(block: String, key: String): String? {
        val lines = block.lines()
        val prefix = "$key:"
        for (i in lines.indices) {
            val line = lines[i]
            if (!line.trim().startsWith(prefix)) continue
            val keyIndent = line.takeWhile { it == ' ' }.length
            val after = line.trim().substring(prefix.length).trim()
            if (after != "|" && after != "|-" && after != ">") {
                return after.takeIf { it.isNotEmpty() }
            }
            // Block scalar: collect following blank or more-indented lines.
            val collected = mutableListOf<String>()
            var blockIndent = -1
            var j = i + 1
            while (j < lines.size) {
                val l = lines[j]
                if (l.isBlank()) {
                    collected.add("")
                } else {
                    val indent = l.takeWhile { it == ' ' }.length
                    if (indent <= keyIndent) break
                    if (blockIndent < 0) blockIndent = indent
                    collected.add(l.substring(minOf(blockIndent, indent)))
                }
                j++
            }
            while (collected.isNotEmpty() && collected.first().isBlank()) collected.removeAt(0)
            while (collected.isNotEmpty() && collected.last().isBlank()) collected.removeAt(collected.size - 1)
            return collected.joinToString("\n").takeIf { it.isNotEmpty() }
        }
        return null
    }

    // Render multi-line plain text into the HTML subset accepted by plugin.xml description:
    //   blank-line-separated blocks -> <p> (or <ul> when every line starts with "- "),
    //   single newlines -> <br>, `code` -> <code>, **bold** -> <b>.
    fun renderPluginDescriptionHtml(raw: String): String {
        fun escape(text: String): String = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        fun inline(text: String): String {
            var rendered = escape(text)
            rendered = Regex("`([^`]+)`").replace(rendered) { "<code>${it.groupValues[1]}</code>" }
            rendered = Regex("\\*\\*([^*]+)\\*\\*").replace(rendered) { "<b>${it.groupValues[1]}</b>" }
            return rendered
        }

        val html = StringBuilder()
        for (paragraph in raw.trim().split(Regex("\\n[ \\t]*\\n"))) {
            val nonBlank = paragraph.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (nonBlank.isEmpty()) continue
            // Within a block, group consecutive "- " lines into a <ul> and the rest into <p>,
            // so a heading line followed by list items renders as <p>..</p><ul>..</ul>.
            var idx = 0
            while (idx < nonBlank.size) {
                if (nonBlank[idx].startsWith("- ")) {
                    html.append("<ul>")
                    while (idx < nonBlank.size && nonBlank[idx].startsWith("- ")) {
                        html.append("<li>${inline(nonBlank[idx].removePrefix("- ").trim())}</li>")
                        idx++
                    }
                    html.append("</ul>")
                } else {
                    val textLines = mutableListOf<String>()
                    while (idx < nonBlank.size && !nonBlank[idx].startsWith("- ")) {
                        textLines.add(nonBlank[idx])
                        idx++
                    }
                    html.append("<p>${textLines.joinToString("<br>") { inline(it) }}</p>")
                }
            }
        }
        return html.toString()
    }

    fun escapePluginHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    fun extractCurrentChangelogSection(changelog: String, version: String): String {
        val lines = changelog.lines()
        val exactHeading = Regex("^## \\[${Regex.escape(version)}\\].*$")
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

    val pluginNameFromReadme = pluginMetadata.map { block ->
        extractPluginMetadataValue(block, "name")
            ?.lineSequence()
            ?.firstOrNull { it.isNotBlank() }
            ?.trim()
    }
    val pluginDescriptionHtmlFromReadme = pluginMetadata.map { block ->
        val englishHtml = extractPluginMetadataValue(block, "description")
            ?.let { renderPluginDescriptionHtml(it) }
        val chineseHtml = extractPluginMetadataValue(block, "description_zh")
            ?.let { renderPluginDescriptionHtml(it) }
        // JetBrains plugin description has no native i18n, so render bilingual content
        // as English + a separator + Chinese in a single field.
        when {
            englishHtml != null && chineseHtml != null -> "$englishHtml<hr/>$chineseHtml"
            else -> englishHtml ?: chineseHtml
        }
    }

    val secretsDirPath = providers.gradleProperty("jetbrainsSecretsDir")
        .orElse(".secrets")

    val defaultCertificateChainFilePath = secretsDirPath.map { "$it/jetbrains-chain.crt" }
    val defaultPrivateKeyFilePath = secretsDirPath.map { "$it/jetbrains-private.pem" }
    val defaultPrivateKeyPasswordFilePath = secretsDirPath.map { "$it/jetbrains-private-key-password.txt" }
    val defaultPublishTokenFilePath = secretsDirPath.map { "$it/jetbrains-publish-token.txt" }

    val certificateChainFromFile = providers.environmentVariable("JETBRAINS_CERTIFICATE_CHAIN_FILE")
        .orElse(providers.environmentVariable("CERTIFICATE_CHAIN_FILE"))
        .orElse(providers.gradleProperty("jetbrainsCertificateChainFile"))
        .orElse(defaultCertificateChainFilePath)
        .map { path -> Files.readString(Path.of(path.trim())) }

    val privateKeyFromFile = providers.environmentVariable("JETBRAINS_PRIVATE_KEY_FILE")
        .orElse(providers.environmentVariable("PRIVATE_KEY_FILE"))
        .orElse(providers.gradleProperty("jetbrainsPrivateKeyFile"))
        .orElse(defaultPrivateKeyFilePath)
        .map { path -> Files.readString(Path.of(path.trim())) }

    val certificateChainFromEnv = providers.environmentVariable("JETBRAINS_CERTIFICATE_CHAIN")
        .orElse(providers.environmentVariable("CERTIFICATE_CHAIN"))

    val privateKeyFromEnv = providers.environmentVariable("JETBRAINS_PRIVATE_KEY")
        .orElse(providers.environmentVariable("PRIVATE_KEY"))

    val privateKeyPasswordFromFile = providers.environmentVariable("JETBRAINS_PRIVATE_KEY_PASSWORD_FILE")
        .orElse(providers.environmentVariable("PRIVATE_KEY_PASSWORD_FILE"))
        .orElse(providers.gradleProperty("jetbrainsPrivateKeyPasswordFile"))
        .orElse(defaultPrivateKeyPasswordFilePath)
        .map { path -> Files.readString(Path.of(path.trim())).trim() }

    val publishTokenFromFile = providers.environmentVariable("JETBRAINS_PUBLISH_TOKEN_FILE")
        .orElse(providers.environmentVariable("PUBLISH_TOKEN_FILE"))
        .orElse(providers.gradleProperty("jetbrainsPublishTokenFile"))
        .orElse(defaultPublishTokenFilePath)
        .map { path -> Files.readString(Path.of(path.trim())).trim() }

    pluginVerification {
        failureLevel.set(
            listOf(
                FailureLevel.COMPATIBILITY_PROBLEMS,
                FailureLevel.OVERRIDE_ONLY_API_USAGES
            )
        )
    }

    pluginConfiguration {
        id = providers.gradleProperty("pluginGroup").zip(providers.gradleProperty("pluginName")) { group, name ->
            "$group.$name"
        }.orElse("com.github.tsdaer.dreamshaderlanguagesupport")
        name = pluginNameFromReadme.orElse("Dreamshader Language Extension")
        version = providers.gradleProperty("version")
        description = pluginDescriptionHtmlFromReadme.map { html ->
            html ?: "<p>DreamShaderLang language support for JetBrains Rider.</p>"
        }
        changeNotes = providers.fileContents(layout.projectDirectory.file("CHANGELOG.md")).asText
            .zip(providers.gradleProperty("version")) { changelog, version ->
                "<pre>${escapePluginHtml(extractCurrentChangelogSection(changelog, version))}</pre>"
            }
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild").orElse("252")
            val untilBuildProperty = providers.gradleProperty("pluginUntilBuild")
            if (untilBuildProperty.isPresent) {
                untilBuild = untilBuildProperty.get()
            }
        }
        vendor {
            name = providers.gradleProperty("pluginVendorName").orElse("tsdaer")
            email = providers.gradleProperty("pluginVendorEmail").orElse("noreply@example.com")
            url = providers.gradleProperty("pluginRepositoryUrl")
        }
    }

    signing {
        certificateChain.set(
            certificateChainFromFile.orElse(
                certificateChainFromEnv
            )
        )
        privateKey.set(
            privateKeyFromFile.orElse(
                privateKeyFromEnv
            )
        )
        password.set(
            providers.environmentVariable("JETBRAINS_PRIVATE_KEY_PASSWORD")
                .orElse(providers.environmentVariable("PRIVATE_KEY_PASSWORD"))
                .orElse(providers.gradleProperty("jetbrainsPrivateKeyPassword"))
                .orElse(privateKeyPasswordFromFile)
        )
    }

    publishing {
        token = providers.environmentVariable("JETBRAINS_PUBLISH_TOKEN")
            .orElse(providers.environmentVariable("PUBLISH_TOKEN"))
            .orElse(providers.gradleProperty("jetbrainsPublishToken"))
            .orElse(publishTokenFromFile)
        channels = providers.gradleProperty("pluginPublishChannels").map { raw ->
            raw.split(',').map { it.trim() }.filter { it.isNotBlank() }
        }.orElse(listOf("default"))
    }
}
