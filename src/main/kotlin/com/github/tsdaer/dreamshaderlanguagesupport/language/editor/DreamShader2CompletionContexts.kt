package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2ElementTypes as E
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.symbols.DreamShader2InstanceModel
import com.github.tsdaer.dreamshaderlanguagesupport.language.symbols.DreamShader2WorkspaceSymbols
import com.intellij.psi.PsiElement

/** Exclusive contexts precede the broad expression candidates. */
internal object DreamShader2CompletionContexts {
    private val types = com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShader2Lexer.TYPES.sorted()
    private fun item(name: String, insert: String = name, type: String = "syntax", caret: Int? = null) =
        DreamShaderCompletionItem(name, insert, typeText = type, caretOffset = caret, priority = 96.0)

    fun preprocessorSuggestions(file: DreamShaderPsiFile, leaf: PsiElement, offset: Int): List<DreamShaderCompletionItem>? {
        val line = file.text.take(offset).substringAfterLast('\n').trimStart()
        if (!line.startsWith('#')) return null
        return suggest(file, leaf, offset)
    }

    fun suggest(file: DreamShaderPsiFile, leaf: PsiElement, offset: Int): List<DreamShaderCompletionItem>? {
        if (nearestAncestorOfType(leaf, E.CUSTOM_BODY) != null) return emptyList()
        val before = file.text.take(offset.coerceIn(0, file.textLength))
        val line = before.substringAfterLast('\n').trimStart()
        val instance = file.name.endsWith(".dsi")
        if (line.startsWith("///")) {
            val slot = Regex("""^///\s*@([A-Za-z_]*)(?:\s+(.*))?$""").find(line) ?: return emptyList()
            val key = slot.groupValues[1]
            if (slot.groups[2] == null) return listOf("name", "default", "sampler", "static", "asset", "page", "slider", "group", "sort", "desc", "library", "layer", "layerblend", "param", "custom")
                .map { item(it, "$it ", "metadata") }
            return when (key) {
                "sampler" -> listOf("Color", "LinearColor", "Normal", "Masks", "Grayscale", "Alpha", "DistanceFieldFont", "External").map { item(it, type = "sampler") }
                "name" -> if (instance) {
                    val schema = file.project.getService(DreamShader2WorkspaceSymbols::class.java).instanceSchema(file.virtualFile)
                    (schema?.parameters?.mapNotNull { it.name } ?: DreamShader2InstanceModel.sourceParameters(file).map { it.first }).map { item(it, type = "material parameter name") }
                } else emptyList()
                "static" -> emptyList()
                "page", "sort" -> listOf(item("0", type = "integer"))
                "slider" -> listOf(item("0 1", type = "range"))
                "default" -> listOf(item("None", type = "resource"))
                else -> emptyList()
            }
        }
        if (line.startsWith('#')) {
            if (line.matches(Regex("""#\s*\w*"""))) {
                val names = listOf("if", "ifdef", "ifndef", "elif", "else", "endif", "define", "undef", "pragma") + if (instance) emptyList() else listOf("include")
                return names.map { item(it, "$it ", "directive") }
            }
            if (Regex("""#(?:if|elif|ifdef|ifndef|undef)\b""").containsMatchIn(line)) {
                val known = DreamShaderPreprocessorModel.of(file).definitionsAt(offset)
                return (listOf("DS_ENGINE_MAJOR", "DS_ENGINE_MINOR", "DS_ENGINE_PATCH", "DS_SUBSTRATE", "DS_PLATFORM", "DS_PLUGIN_VERSION") + known)
                    .distinct().map { item(it, type = if (it in listOf("DS_PLATFORM", "DS_PLUGIN_VERSION")) "string define" else "define") } +
                    listOf(item("defined", "defined()", "condition", 8))
            }
            if (instance && Regex("""#pragma\s+material\b|#include\b""").containsMatchIn(line)) return emptyList()
            if (Regex("""#pragma\s+instance\s*\(\s*Parent\s*=\s*"[^"]*$""").containsMatchIn(line)) {
                return DreamShader2AutoImport.parentNames(file).map { item(it, type = "parent material / instance") }
            }
            return null
        }
        if (!instance && line.matches(Regex("[A-Za-z_]*")) &&
            com.intellij.psi.util.PsiTreeUtil.getParentOfType(leaf, com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2FunctionDeclaration::class.java) == null) {
            return types.map { item(it, type = "type") } + listOf("export", "extern", "uniform", "struct").map { item(it, "$it ", "declaration") } +
                DreamShader2FileAnalysis.of(file).structs.mapNotNull { it.declarationName()?.let { name -> item(name, type = "struct type") } } +
                listOf(item("#pragma", "#pragma material()", "directive", "#pragma material(".length), item("#include", "#include \"\"", "directive", "#include \"".length))
        }
        if (Regex("""^(?:uniform|static\s+const|const|static)\s+[\w]*$""").matches(line)) {
            return types.filter { !instance || !it.matches(Regex(".*[1-4]x[1-4]$")) && it !in setOf("material", "MaterialAttributes", "Substrate", "void", "SamplerState") }
                .map { item(it, type = "type") }
        }
        if (instance) {
            if (line.startsWith("uniform ")) {
                if ('=' in line) return null
                if (Regex("""^uniform\s+\w+\s+\w*$""").matches(line)) return DreamShader2CompletionSuggester.instanceNameItems(file)
                return emptyList()
            }
            return listOf(
                item("uniform", "uniform ", "override"),
                item("#pragma", "#pragma instance(Parent = \"\")", "instance", "#pragma instance(Parent = \"".length),
                item("StaticSwitch", "/// @static\nuniform bool UseDetail = false;", "override template"),
                item("StaticMask", "/// @static\nuniform bool4 Mask = bool4(true, true, false, false);", "override template"),
                item("Texture", "/// @default None\nuniform Texture2D Albedo;", "override template"),
                item("Font", "/// @default None\n/// @page 0\nuniform Font Glyphs;", "override template")
            ) + listOf("RuntimeVirtualTexture", "SparseVolumeTexture", "TextureCollection", "ParameterCollection")
                .map { item(it, "/// @default None\nuniform $it Resource;", "override template") }
        }
        return null
    }

    fun rankByExpectedType(file: DreamShaderPsiFile, leaf: PsiElement, offset: Int, items: List<DreamShaderCompletionItem>): List<DreamShaderCompletionItem> {
        val inference = DreamShader2InferenceContext.forFile(file, file.project.getService(DreamShader2WorkspaceSymbols::class.java))
        val declaration = generateSequence(leaf) { it.parent }.firstOrNull { it.node?.elementType in setOf(E.VARIABLE_DECLARATION_STATEMENT, E.GLOBAL_DECLARATION, E.UNIFORM_DECLARATION) }
        val line = file.text.take(offset).substringAfterLast('\n')
        val call = DreamShaderSignatureHelpAnalyzer.findEnclosingCall(file.text, offset, allowStringInterior = true)
        val parameterTypes = call?.let { shape -> DreamShaderCallSignatureResolver.resolveSignatures(shape.functionName, file).mapNotNull { signature ->
            val index = DreamShaderSignatureHelpAnalyzer.parameterIndex(file.text, shape, signature, offset)
            signature.parameterTypes.getOrNull(index)
        }.distinct() }.orEmpty()
        val returnStatement = nearestAncestorOfType(leaf, E.RETURN_STATEMENT)
        val expected = parameterTypes.singleOrNull()?.let(DreamShader2Type::fromSpelling)
            ?: declaration?.let { DreamShader2Type.fromSpelling(dreamShader2DeclaredTypeSpelling(it)) }
            ?: returnStatement?.let { com.intellij.psi.util.PsiTreeUtil.getParentOfType(it, com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2FunctionDeclaration::class.java)?.returnTypeText()?.let(DreamShader2Type::fromSpelling) }
            ?: Regex("""\b([A-Za-z_]\w*)\s*=\s*\w*$""").find(line)?.groupValues?.get(1)?.let { inference.typeOfName(it, leaf) }
            ?: return items
        if (expected == DreamShader2Type.Unknown) return items
        return items.map { item ->
            val spelling = item.tailText?.substringAfter(" : ")?.takeIf { it != item.tailText } ?: item.typeText
            val type = DreamShader2Type.fromSpelling(spelling)
            val compatible = type == expected || (type is DreamShader2Type.Scalar && expected.isNumeric && type.isNumeric)
            item.copy(priority = item.priority + if (compatible) 100.0 else 0.0)
        }
    }
}
