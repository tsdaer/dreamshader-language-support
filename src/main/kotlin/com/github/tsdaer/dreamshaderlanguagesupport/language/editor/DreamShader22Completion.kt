package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderDialects
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderDshCodeCatalog
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPragmas
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes as T
import com.github.tsdaer.dreamshaderlanguagesupport.language.packages.DreamShaderImportResolver
import com.intellij.psi.PsiElement

internal object DreamShader22Completion {
    private val pragmaName = Regex("""#[ \t]*pragma[ \t]+(\w*)""")
    private val disableList = Regex("""#[ \t]*pragma[ \t]+(disable_warning|disable_error)[ \t]*\(([^)]*)""")
    private val disableComment = Regex("""//[ \t]*(disable_warning|disable_error)[ \t]+([A-Za-z0-9_, \t]*)""")
    private val generateKeys = Regex("""#[ \t]*pragma[ \t]+generate[ \t]*\((.*)""")
    private val legacyHeader = Regex("""(?i)^\s*(Shader|ShaderFunction|ShaderLayerBlend|ShaderLayer|Namespace|Function|GraphFunction)\s*\((.*)""")
    private val keySlot = Regex("""\w*""")
    private val codeWord = Regex("""\w+""")
    private val headerAssetKeys = listOf("Generate", "Library", "Description")

    fun suggest(file: DreamShaderPsiFile, leaf: PsiElement, offset: Int, line: String): List<DreamShaderCompletionItem>? {
        if (leaf.node.elementType == T.BLOCK_COMMENT) return null
        val extension = DreamShaderDialects.extensionOf(file.name)
        val legacy = extension in DreamShaderDialects.V1_EXTENSIONS
        if (leaf.node.elementType == T.LINE_COMMENT) {
            val slot = disableComment.matchEntire(file.text.substring(leaf.textOffset, offset.coerceIn(leaf.textOffset, file.textLength)))
            return slot?.let { codes(it.groupValues[1], it.groupValues[2]) }
        }
        if (leaf.node.elementType == T.STRING) return null
        disableList.matchEntire(line)?.let { return codes(it.groupValues[1], it.groupValues[2]) }
        pragmaName.matchEntire(line)?.let {
            return buildList {
                if (extension != "dsh") add(item(DreamShaderPragmas.NO_GENERATION, type = "file pragma"))
                add(item("disable_warning", "disable_warning()", "diagnostic pragma", "disable_warning(".length))
                add(item("disable_error", "disable_error()", "diagnostic pragma", "disable_error(".length))
                if (isPackageHeader(file)) {
                    add(item("generate", "generate(Functions = \"MF_{Name}\")", "package assets"))
                }
                if (!legacy && extension == "dss") add(item("material", "material()", "material pragma", "material(".length))
                if (extension == "dsi") add(item("instance", "instance(Parent = \"\")", "instance pragma", "instance(Parent = \"".length))
            }
        }
        generateKeys.matchEntire(line)?.let {
            if (!isPackageHeader(file)) return emptyList()
            return keyItems(it.groupValues[1], listOf("Functions", "Library", "Desc"))
        }
        legacyHeader.matchEntire(line)?.let {
            val declaration = it.groupValues[1].lowercase()
            val arguments = it.groupValues[2]
            if (legacy && declaration.startsWith("shader")) {
                if (Regex("""(?i).*?\bNoGeneration\s*=\s*\w*""").matches(arguments)) {
                    return listOf(item("true"), item("false"))
                }
                return keyItems(arguments, listOf("NoGeneration")).takeIf { items -> items.isNotEmpty() }
            }
            if (isPackageHeader(file)) return keyItems(arguments, headerAssetKeys).takeIf { items -> items.isNotEmpty() }
        }
        return null
    }

    fun isPackageHeader(file: DreamShaderPsiFile): Boolean {
        if (DreamShaderDialects.extensionOf(file.name) != "dsh") return false
        val root = DreamShaderImportResolver.sourceRoot(file) ?: return false
        val path = file.virtualFile?.path ?: return false
        val prefix = "$root/Packages/"
        if (!path.startsWith(prefix, ignoreCase = true)) return false
        val segments = path.substring(prefix.length).split('/')
        val packageDepth = if (segments.first().startsWith('@')) 2 else 1
        return segments.size > packageDepth && !segments[packageDepth].equals("Examples", ignoreCase = true)
    }

    private fun codes(pragma: String, value: String): List<DreamShaderCompletionItem> {
        val prefix = codeWord.findAll(value).lastOrNull()?.takeIf { it.range.last == value.lastIndex }?.value.orEmpty()
        val used = codeWord.findAll(value.dropLast(prefix.length)).map { it.value.uppercase() }.toSet()
        val severity = if (pragma == DreamShaderPragmas.DISABLE_WARNING) "warning" else "error"
        return DreamShaderDshCodeCatalog.matching(prefix, severity).filterNot { it.code in used }.map {
            item(it.code, type = severity).copy(detail = it.message)
        }
    }

    private fun keyItems(arguments: String, keys: List<String>): List<DreamShaderCompletionItem> {
        if (!arguments.substringAfterLast(',').trimStart().matches(keySlot)) return emptyList()
        return keys.filterNot { Regex("""(?i)\b$it\s*=""").containsMatchIn(arguments) }
            .map { item(it, "$it = ", "argument") }
    }

    private fun item(label: String, insert: String = label, type: String = "value", caret: Int? = null) =
        DreamShaderCompletionItem(label, insert, typeText = type, caretOffset = caret, priority = 96.0)
}
