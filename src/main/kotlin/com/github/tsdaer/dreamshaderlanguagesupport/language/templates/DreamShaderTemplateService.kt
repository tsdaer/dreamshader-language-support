package com.github.tsdaer.dreamshaderlanguagesupport.language.templates

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderJson
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.intellij.openapi.project.Project
import kotlinx.serialization.Serializable
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.*

/** `dreamshader.package.json` 脚手架元数据的序列化模型。 */
@Serializable
private data class PackageScaffoldMetadata(
    val name: String,
    val version: String,
    val displayName: String? = null,
    val description: String? = null,
    val author: String? = null,
    val repository: String,
    val dreamshader: DreamShaderEntry
) {
    @Serializable
    data class DreamShaderEntry(val entry: String)
}

internal data class DreamShaderPackageScaffoldRequest(
    val name: String,
    val displayName: String = "",
    val description: String = "",
    val namespaceName: String = "",
    val author: String = "",
    val repository: String = "",
    val includeExample: Boolean = true
)

/**
 * Service implementation for DreamShaderTemplateService.
 */
internal class DreamShaderTemplateService(
    private val project: Project
) {
    fun createMaterialTemplate(targetPathInput: String): DreamShaderTemplateOperationResult {
        val target = resolveTargetFile(targetPathInput, "dsm")
            ?: return DreamShaderTemplateOperationResult(
                false,
                DreamShaderBundle.message("templates.error.invalidTargetPath")
            )
        if (target.exists()) {
            return DreamShaderTemplateOperationResult(
                false,
                DreamShaderBundle.message("templates.error.targetExists", target.invariantSeparatorsPathString)
            )
        }

        val stem = fileStem(target.name)
        val symbol = toIdentifier(stem, "NewMaterial")
        val content = """
            Shader(Name="Materials/$symbol") {
                Properties = {
                    float Roughness = 0.5;
                }

                Outputs = {
                    float3 Color;
                }

                Settings = {
                    Domain = Surface;
                    ShadingModel = DefaultLit;
                    BlendMode = Opaque;
                }

                Graph = {
                    Color = float3(1.0, 1.0, 1.0);
                    Base.BaseColor = Color;
                }
            }
        """.trimIndent() + "\n"

        return writeFile(target, content, DreamShaderBundle.message("templates.success.materialCreated", target.invariantSeparatorsPathString))
    }

    fun createTextureSampleTemplate(targetPathInput: String): DreamShaderTemplateOperationResult {
        val target = resolveTargetFile(targetPathInput, "dsm")
            ?: return DreamShaderTemplateOperationResult(
                false,
                DreamShaderBundle.message("templates.error.invalidTargetPath")
            )
        if (target.exists()) {
            return DreamShaderTemplateOperationResult(
                false,
                DreamShaderBundle.message("templates.error.targetExists", target.invariantSeparatorsPathString)
            )
        }

        val stem = fileStem(target.name)
        val symbol = toIdentifier(stem, "M_TextureSample")
        val content = """
            import "Builtin/Texture.dsh";

            Shader(Name="Materials/$symbol") {
                Properties = {
                    const Texture2D AlbedoTexture = Path(Game, Textures, T_Default);
                    float2 UVScale = float2(1.0, 1.0);
                    float3 Tint = float3(1.0, 1.0, 1.0);
                }

                Outputs = {
                    float3 Color;
                }

                Settings = {
                    Domain = Surface;
                    ShadingModel = DefaultLit;
                    BlendMode = Opaque;
                }

                Graph = {
                    float2 UV = TexCoord0 * UVScale;
                    Color = Texture::Sample2DRGB(AlbedoTexture, UV) * Tint;
                    Base.BaseColor = Color;
                }
            }
        """.trimIndent() + "\n"

        return writeFile(target, content, DreamShaderBundle.message("templates.success.textureSampleCreated", target.invariantSeparatorsPathString))
    }

    fun createNoiseMaterialTemplate(targetPathInput: String): DreamShaderTemplateOperationResult {
        val target = resolveTargetFile(targetPathInput, "dsm")
            ?: return DreamShaderTemplateOperationResult(
                false,
                DreamShaderBundle.message("templates.error.invalidTargetPath")
            )
        if (target.exists()) {
            return DreamShaderTemplateOperationResult(
                false,
                DreamShaderBundle.message("templates.error.targetExists", target.invariantSeparatorsPathString)
            )
        }

        val stem = fileStem(target.name)
        val symbol = toIdentifier(stem, "M_NoiseMaterial")
        val content = """
            import "Builtin/Noise.dsh";

            Shader(Name="Materials/$symbol") {
                Properties = {
                    float Scale = 8.0;
                    float Contrast = 1.0;
                    float3 ColorA = float3(0.04, 0.08, 0.12);
                    float3 ColorB = float3(0.7, 0.9, 1.0);
                }

                Outputs = {
                    float3 Color;
                }

                Settings = {
                    Domain = Surface;
                    ShadingModel = DefaultLit;
                    BlendMode = Opaque;
                }

                Graph = {
                    float2 UV = TexCoord0 * Scale;
                    float Mask = saturate(Noise::FBM2D(UV) * Contrast);
                    Color = lerp(ColorA, ColorB, Mask);
                    Base.BaseColor = Color;
                    Base.Roughness = 0.65;
                }
            }
        """.trimIndent() + "\n"

        return writeFile(target, content, DreamShaderBundle.message("templates.success.noiseMaterialCreated", target.invariantSeparatorsPathString))
    }

    fun createFunctionTemplate(targetPathInput: String): DreamShaderTemplateOperationResult {
        val target = resolveTargetFile(targetPathInput, "dsf")
            ?: return DreamShaderTemplateOperationResult(
                false,
                DreamShaderBundle.message("templates.error.invalidTargetPath")
            )
        if (target.exists()) {
            return DreamShaderTemplateOperationResult(
                false,
                DreamShaderBundle.message("templates.error.targetExists", target.invariantSeparatorsPathString)
            )
        }

        val stem = fileStem(target.name)
        val symbol = toIdentifier(stem, "NewFunction")
        val content = """
            ShaderFunction(Name="Functions/$symbol") {
                Inputs = {
                    float3 InColor;
                    float Strength = 1.0;
                }

                Outputs = {
                    float3 OutColor;
                }

                Graph = {
                    OutColor = InColor * Strength;
                }
            }
        """.trimIndent() + "\n"

        return writeFile(target, content, DreamShaderBundle.message("templates.success.functionCreated", target.invariantSeparatorsPathString))
    }

    fun createHeaderTemplate(targetPathInput: String): DreamShaderTemplateOperationResult {
        val target = resolveTargetFile(targetPathInput, "dsh")
            ?: return DreamShaderTemplateOperationResult(
                false,
                DreamShaderBundle.message("templates.error.invalidTargetPath")
            )
        if (target.exists()) {
            return DreamShaderTemplateOperationResult(
                false,
                DreamShaderBundle.message("templates.error.targetExists", target.invariantSeparatorsPathString)
            )
        }

        val stem = fileStem(target.name)
        val namespaceName = toIdentifier(stem, "Shared")
        val content = """
            Namespace $namespaceName {
                Function ApplyTint(in float3 InColor, in float Strength, out float3 OutColor) {
                    OutColor = InColor * Strength;
                }
            }
        """.trimIndent() + "\n"

        return writeFile(target, content, DreamShaderBundle.message("templates.success.headerCreated", target.invariantSeparatorsPathString))
    }

    fun createPackageScaffold(packageNameInput: String): DreamShaderTemplateOperationResult {
        return createPackageScaffold(DreamShaderPackageScaffoldRequest(name = packageNameInput))
    }

    fun createPackageScaffold(request: DreamShaderPackageScaffoldRequest): DreamShaderTemplateOperationResult {
        val normalizedName = normalizePackageName(request.name)
            ?: return DreamShaderTemplateOperationResult(
                false,
                DreamShaderBundle.message("templates.error.invalidPackageName", request.name)
            )
        val packageRoot = packageRootForName(normalizedName)

        if (packageRoot.exists()) {
            val hasContent = if (packageRoot.isDirectory()) {
                Files.list(packageRoot).use { it.findAny().isPresent }
            } else {
                true
            }
            if (hasContent) {
                return DreamShaderTemplateOperationResult(
                    false,
                    DreamShaderBundle.message("templates.error.targetExists", packageRoot.invariantSeparatorsPathString)
                )
            }
        }

        val packageId = normalizedName.substringAfterLast('/')
        val entryFileName = "${toIdentifier(packageId, "Main")}Lib.dsh"
        val namespaceName = toIdentifier(request.namespaceName.ifBlank { packageId }, "Library")
        val repository = request.repository.ifBlank { "https://github.com/owner/repository" }
        val description = request.description.ifBlank { "DreamShader package scaffold." }

        val metadataContent = DreamShaderJson.encodePretty(
            PackageScaffoldMetadata(
                name = normalizedName,
                version = "0.1.0",
                displayName = request.displayName.trim().takeIf { it.isNotBlank() },
                description = request.description.trim().takeIf { it.isNotBlank() },
                author = request.author.trim().takeIf { it.isNotBlank() },
                repository = repository,
                dreamshader = PackageScaffoldMetadata.DreamShaderEntry(
                    entry = "Library/$entryFileName"
                )
            )
        ) + "\n"
        val readmeContent = """
            # $normalizedName

            $description
        """.trimIndent() + "\n"
        val licenseContent = "MIT\n"
        val libraryContent = """
            Namespace $namespaceName {
                Function ${namespaceName}_Demo(in float3 InColor, out float3 OutColor) {
                    OutColor = InColor;
                }
            }
        """.trimIndent() + "\n"
        val exampleContent = """
            Shader(Name="Examples/M_${toIdentifier(packageId, "Sample")}") {
                Outputs = {
                    float3 Color;
                }

                Graph = {
                    Color = float3(1.0, 1.0, 1.0);
                    Base.BaseColor = Color;
                }
            }
        """.trimIndent() + "\n"

        return runCatching {
            ensureUnderProject(packageRoot)
            Files.createDirectories(packageRoot.resolve("Library"))
            if (request.includeExample) {
                Files.createDirectories(packageRoot.resolve("Examples"))
            }
            Files.writeString(packageRoot.resolve("dreamshader.package.json"), metadataContent, StandardCharsets.UTF_8)
            Files.writeString(packageRoot.resolve("README.md"), readmeContent, StandardCharsets.UTF_8)
            Files.writeString(packageRoot.resolve("LICENSE"), licenseContent, StandardCharsets.UTF_8)
            Files.writeString(packageRoot.resolve("Library").resolve(entryFileName), libraryContent, StandardCharsets.UTF_8)
            if (request.includeExample) {
                Files.writeString(packageRoot.resolve("Examples").resolve("Sample.dsm"), exampleContent, StandardCharsets.UTF_8)
            }
            DreamShaderTemplateOperationResult(
                true,
                DreamShaderBundle.message("templates.success.packageCreated", normalizedName, packageRoot.invariantSeparatorsPathString),
                packageRoot
            )
        }.getOrElse { error ->
            DreamShaderTemplateOperationResult(
                false,
                DreamShaderBundle.message(
                    "templates.error.createFailed",
                    error.message ?: DreamShaderBundle.message("common.unknown")
                )
            )
        }
    }

    internal fun readFileText(path: Path): String = path.readText(StandardCharsets.UTF_8)

    private fun writeFile(
        target: Path,
        content: String,
        successMessage: String
    ): DreamShaderTemplateOperationResult {
        return runCatching {
            ensureUnderProject(target)
            Files.createDirectories(target.parent)
            Files.writeString(target, content, StandardCharsets.UTF_8)
            DreamShaderTemplateOperationResult(true, successMessage, target)
        }.getOrElse { error ->
            DreamShaderTemplateOperationResult(
                false,
                DreamShaderBundle.message(
                    "templates.error.createFailed",
                    error.message ?: DreamShaderBundle.message("common.unknown")
                )
            )
        }
    }

    private fun resolveTargetFile(pathInput: String, expectedExtension: String): Path? {
        val normalizedInput = pathInput.trim().replace('\\', '/')
        if (normalizedInput.isBlank()) return null
        val candidate = runCatching { Path.of(normalizedInput) }.getOrNull() ?: return null
        val resolved = if (candidate.isAbsolute) candidate else projectBasePath().resolve(candidate).normalize()
        val extension = resolved.fileName?.toString()?.substringAfterLast('.', "")?.lowercase()
        if (extension != expectedExtension) return null
        return resolved
    }

    internal fun normalizePackageName(raw: String): String? {
        val trimmed = raw.trim().replace('\\', '/').trim('/')
        if (trimmed.isBlank()) return null
        val scopedPattern = Regex("^@[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
        val simplePattern = Regex("^[A-Za-z0-9_.-]+$")
        if (scopedPattern.matches(trimmed) || simplePattern.matches(trimmed)) {
            return trimmed.lowercase()
        }
        return null
    }

    private fun packageRootForName(name: String): Path {
        val parts = name.split('/').filter { it.isNotBlank() }
        val settings = project.getService(DreamShaderProjectSettings::class.java)?.state
        val sourceDir = settings?.sourceDirectory?.trim().orEmpty().ifBlank { "DShader" }.trimStart('/').trimEnd('/')
        val root = projectBasePath().resolve(sourceDir).resolve("Packages")
        var current = root
        parts.forEach { part -> current = current.resolve(part) }
        return current
    }

    private fun projectBasePath(): Path {
        val basePath = project.basePath ?: error("project base path is null")
        return try {
            Path.of(basePath).toAbsolutePath().normalize()
        } catch (_: InvalidPathException) {
            error("invalid project base path")
        }
    }

    private fun ensureUnderProject(target: Path) {
        val canonicalProject = projectBasePath().toFile().canonicalFile.toPath().normalize()
        val canonicalTarget = target.toAbsolutePath().normalize().toFile().canonicalFile.toPath().normalize()
        require(canonicalTarget.startsWith(canonicalProject)) {
            "Refusing to write outside project: $canonicalTarget"
        }
    }

    private fun fileStem(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        return if (dot <= 0) fileName else fileName.substring(0, dot)
    }

    private fun toIdentifier(input: String, fallback: String): String {
        val cleaned = buildString(input.length) {
            input.forEach { ch ->
                when {
                    ch.isLetterOrDigit() || ch == '_' -> append(ch)
                    ch == '-' || ch == ' ' || ch == '.' -> append('_')
                }
            }
        }.trim('_')
        val value = cleaned.ifBlank { fallback }
        return if (value.first().isDigit()) "_$value" else value
    }
}

/**
 * Data model for DreamShaderTemplateOperationResult.
 */
internal data class DreamShaderTemplateOperationResult(
    val success: Boolean,
    val message: String,
    val targetPath: Path? = null
)
