import org.jetbrains.intellij.platform.gradle.TestFrameworkType

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

intellijPlatform {
    pluginConfiguration {
        id = providers.gradleProperty("pluginGroup").zip(providers.gradleProperty("pluginName")) { group, name ->
            "$group.$name"
        }.orElse("com.github.tsdaer.dreamshaderlanguagesupport")
        name = providers.gradleProperty("pluginDisplayName").orElse("Dreamshader Language Extension")
        version = providers.gradleProperty("version")
        description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map { readme ->
            val marker = "## Quick File Links"
            val idx = readme.indexOf(marker)
            if (idx > 0) {
                "<p>" + readme.substring(0, idx).trim().replace("\n", "<br/>") + "</p>"
            } else {
                "<p>DreamShaderLang language support for JetBrains Rider.</p>"
            }
        }
        changeNotes = providers.fileContents(layout.projectDirectory.file("CHANGELOG.md")).asText.map { changelog ->
            val lines = changelog.lineSequence().take(30).joinToString("\n")
            "<pre>$lines</pre>"
        }
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild").orElse("252")
            untilBuild = providers.gradleProperty("pluginUntilBuild").orElse("252.*")
        }
        vendor {
            name = providers.gradleProperty("pluginVendorName").orElse("tsdaer")
            email = providers.gradleProperty("pluginVendorEmail").orElse("noreply@example.com")
            url = providers.gradleProperty("pluginRepositoryUrl")
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        channels = providers.gradleProperty("pluginPublishChannels").map { raw ->
            raw.split(',').map { it.trim() }.filter { it.isNotBlank() }
        }.orElse(listOf("default"))
    }
}
