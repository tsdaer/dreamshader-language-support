import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.changelog.tasks.PatchChangelogTask
import java.nio.file.Files
import java.nio.file.Path
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        // Keep IU for reliable headless test execution.
        intellijIdea("2025.2.6.2")
        testFramework(TestFrameworkType.Platform)
    }
}

tasks.processResources {
    val patchChangelogTask = tasks.named<PatchChangelogTask>("patchChangelog")
    val pluginVersion = providers.gradleProperty("version")
    inputs.property("pluginVersion", pluginVersion)
    // Keep an explicit producer-consumer link for Gradle 9 task output validation.
    dependsOn(patchChangelogTask)
    from(patchChangelogTask.flatMap { it.outputFile })
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

    fun parsePluginMetadataValue(block: String, key: String): String? {
        val prefix = "$key:"
        return block.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith(prefix) }
            ?.substring(prefix.length)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    val pluginNameFromReadme = pluginMetadata.map { block ->
        parsePluginMetadataValue(block, "name")
    }
    val pluginDescriptionFromReadme = pluginMetadata.map { block ->
        parsePluginMetadataValue(block, "description")
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

    pluginConfiguration {
        id = providers.gradleProperty("pluginGroup").zip(providers.gradleProperty("pluginName")) { group, name ->
            "$group.$name"
        }.orElse("com.github.tsdaer.dreamshaderlanguagesupport")
        name = pluginNameFromReadme.orElse("Dreamshader Language Extension")
        version = providers.gradleProperty("version")
        description = pluginDescriptionFromReadme.map { metadataDescription ->
            "<p>${metadataDescription ?: "DreamShaderLang language support for JetBrains Rider."}</p>"
        }
        changeNotes = providers.fileContents(layout.projectDirectory.file("CHANGELOG.md")).asText.map { changelog ->
            "<pre>${changelog.trim()}</pre>"
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
