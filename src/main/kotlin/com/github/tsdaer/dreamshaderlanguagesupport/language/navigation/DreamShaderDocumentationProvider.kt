package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation
import com.github.tsdaer.dreamshaderlanguagesupport.language.bridge.DreamShaderBridgeSettingsRepository
import com.github.tsdaer.dreamshaderlanguagesupport.language.bridge.DreamShaderSettingMapping
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.DreamShaderCallSignatureResolver
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.DreamShaderMaterialExpressionInfo
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.DreamShaderMaterialExpressionManifest
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.DreamShaderSignatureHelpAnalyzer
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderLexer
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderSection
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import java.util.*

/**
 * Provider implementation for DreamShaderDocumentationProvider.
 */
class DreamShaderDocumentationProvider : AbstractDocumentationProvider() {
    override fun getCustomDocumentationElement(
        editor: Editor,
        file: PsiFile,
        contextElement: PsiElement?,
        targetOffset: Int
    ): PsiElement? {
        if (file.language != DreamShaderLanguage && contextElement?.language != DreamShaderLanguage) return null
        if (file.textLength <= 0) return contextElement

        val safeOffset = targetOffset.coerceIn(0, file.textLength - 1)
        var leaf = file.findElementAt(safeOffset)
        if (leaf == null && safeOffset > 0) {
            leaf = file.findElementAt(safeOffset - 1)
        }
        if (leaf?.node?.elementType == DreamShaderTokenTypes.WHITE_SPACE) {
            leaf = PsiTreeUtil.prevVisibleLeaf(leaf) ?: PsiTreeUtil.nextVisibleLeaf(leaf)
        }
        val selected = leaf ?: contextElement
        if (selected != null && selected.node?.elementType == DreamShaderTokenTypes.IDENTIFIER) {
            val declarationTarget = DreamShaderGotoDeclarationHandler()
                .getGotoDeclarationTargets(selected, selected.textRange.startOffset, editor)
                ?.firstOrNull()
            if (declarationTarget != null) return declarationTarget
        }
        return selected
    }

    override fun getQuickNavigateInfo(element: PsiElement, originalElement: PsiElement?): String? {
        return generateDoc(element, originalElement)
    }

    override fun generateHoverDoc(element: PsiElement, originalElement: PsiElement?): String? {
        return generateDoc(element, originalElement)
    }

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        val resolved = element
        val source = originalElement ?: element ?: return null
        if (source.language != DreamShaderLanguage && resolved?.language != DreamShaderLanguage) return null

        val tokenElement = source
        val token = normalizeTokenText(tokenElement.text)
        if (token.isNotBlank()) {
            val sectionDoc = sectionDocumentation(tokenElement)
            if (sectionDoc != null) return sectionDoc

            val settingsKeyDoc = settingsKeyDocumentation(tokenElement, token)
            if (settingsKeyDoc != null) return settingsKeyDoc

            val settingsValueDoc = settingsValueDocumentation(tokenElement, token)
            if (settingsValueDoc != null) return settingsValueDoc

            val catalogDoc = catalogExpressionDocumentation(tokenElement, token)
            if (catalogDoc != null) return catalogDoc

            val functionCallDoc = functionCallDocumentation(tokenElement, token)
            if (functionCallDoc != null) return functionCallDoc

            val localVariableDoc = localVariableDocumentation(tokenElement, token)
            if (localVariableDoc != null) return localVariableDoc
        }

        val declarationDoc = declarationDocumentation(source)
            ?: declarationDocumentation(resolved ?: source)
        if (declarationDoc != null) return declarationDoc

        return null
    }

    private fun sectionDocumentation(element: PsiElement): String? {
        if (element.node?.elementType != DreamShaderTokenTypes.SECTION) return null

        val sectionName = element.text.trim().lowercase(Locale.ROOT)
        if (sectionName.isBlank()) return null

        val description = DreamShaderDocumentationData.sectionDescription(sectionName) ?: return null
        val title = sectionName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        return "<b>$title</b><br/>$description"
    }

    private fun declarationDocumentation(element: PsiElement): String? {
        val declaration = when (element) {
            is DreamShaderDeclaration -> element
            else -> PsiTreeUtil.getParentOfType(element, DreamShaderDeclaration::class.java, false)
        } ?: return null
        if (!isDeclarationDocumentationAnchor(element, declaration)) return null

        val keyword = declaration.keywordText() ?: return null
        val name = declarationDisplayName(declaration).orEmpty().ifBlank { "<anonymous>" }
        val overrideKey = "declaration.$keyword.description"
        val kind = overrideDoc(element, overrideKey)
            ?: DreamShaderDocumentationData.declarationDescription(keyword)
            ?: DreamShaderBundle.message("docs.declaration.default")

        return buildString {
            append("<b>")
            append(keyword.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() })
            append(' ')
            append(name)
            append("</b><br/>")
            append(kind)
        }
    }

    private fun settingsKeyDocumentation(element: PsiElement, token: String): String? {
        if (!isInSettingsOrOptionsSection(element)) return null
        val info = DreamShaderDocumentationData.settingInfo(token) ?: return null
        val overrideKey = "settings.${info.key.lowercase(Locale.ROOT)}.description"
        val description = overrideDoc(element, overrideKey) ?: info.description

        val bridgeMappings = bridgeMappingsForSettingKey(element, info.key)
        val commonValues = bridgeMappings
            .map { it.alias }
            .filter { it.isNotBlank() }
            .distinct()
            .ifEmpty { info.commonValues }

        return buildString {
            append("<b>")
            append(DreamShaderBundle.message("docs.label.settingsKey"))
            append(": ")
            append(info.key)
            append("</b><br/>")
            append(description)
            if (commonValues.isNotEmpty()) {
                append("<br/>")
                append(DreamShaderBundle.message("docs.label.commonValues"))
                append(": ")
                append(commonValues.joinToString(", "))
            }
        }
    }

    private fun settingsValueDocumentation(element: PsiElement, token: String): String? {
        if (!isInSettingsOrOptionsSection(element)) return null
        val value = token.trim('"')
        if (value.isBlank()) return null

        bridgeMappingForValue(element, value)?.let { (key, mapping) ->
            return buildString {
                append("<b>")
                append(DreamShaderBundle.message("docs.label.settingValue"))
                append(": ")
                append(mapping.alias)
                append("</b><br/>")
                mapping.displayName?.takeIf { it.isNotBlank() }?.let {
                    append(DreamShaderBundle.message("docs.label.displayName"))
                    append(": ")
                    append(it)
                    append("<br/>")
                }
                mapping.name?.takeIf { it.isNotBlank() }?.let {
                    append(DreamShaderBundle.message("docs.label.ueName"))
                    append(": ")
                    append(it)
                    append("<br/>")
                }
                mapping.value?.let {
                    append(DreamShaderBundle.message("docs.label.enumValue"))
                    append(": ")
                    append(it)
                    append("<br/>")
                }
                append(DreamShaderBundle.message("docs.label.usedBy"))
                append(": ")
                append(key)
            }
        }

        val owners = DreamShaderDocumentationData.valueOwners(value)
        if (owners.isEmpty()) return null

        return buildString {
            append("<b>")
            append(DreamShaderBundle.message("docs.label.settingValue"))
            append(": ")
            append(value)
            append("</b><br/>")
            append(DreamShaderBundle.message("docs.label.usedBy"))
            append(": ")
            append(owners.joinToString(", "))
        }
    }

    /**
     * 将文档设置键（如 `Domain`/`MaterialDomain`/`RenderType`）归一为 Bridge `settings.json`
     * 使用的小写键（`materialdomain`/`shadingmodel`/`blendmode`）。
     */
    private fun bridgeKeyFor(settingKey: String): String? = when (settingKey.lowercase(Locale.ROOT)) {
        "domain", "materialdomain" -> "materialdomain"
        "shadingmodel" -> "shadingmodel"
        "blendmode", "rendertype" -> "blendmode"
        else -> null
    }

    /** 返回某设置键在 Bridge 中的映射列表（不可用/缺失时为空）。 */
    private fun bridgeMappingsForSettingKey(element: PsiElement, settingKey: String): List<DreamShaderSettingMapping> {
        val bridgeKey = bridgeKeyFor(settingKey) ?: return emptyList()
        val repository = element.project.getService(DreamShaderBridgeSettingsRepository::class.java)
            ?: return emptyList()
        return repository.mappingsForKey(bridgeKey)
    }

    /**
     * 解析悬浮的设置值所属的 Bridge 映射：优先按同行键定位，键缺失时回退到「包含该别名的任一键」。
     * 命中返回 (设置键, 映射)，否则 null（由调用方退化为硬编码 `valueOwners`）。
     */
    private fun bridgeMappingForValue(element: PsiElement, value: String): Pair<String, DreamShaderSettingMapping>? {
        val repository = element.project.getService(DreamShaderBridgeSettingsRepository::class.java)
            ?: return null
        val ownerKey = settingKeyForValue(element)
        if (ownerKey != null) {
            val bridgeKey = bridgeKeyFor(ownerKey)
            if (bridgeKey != null) {
                repository.mappingForValue(bridgeKey, value)?.let { return ownerKey to it }
            }
        }
        val fallbackKey = repository.keysContainingAlias(value).firstOrNull() ?: return null
        val mapping = repository.mappingForValue(fallbackKey, value) ?: return null
        return fallbackKey to mapping
    }

    /** 由悬浮的值元素向左扫描到 `=`，再取其前一个标识符 leaf 作为所属设置键。 */
    private fun settingKeyForValue(element: PsiElement): String? {
        var leaf: PsiElement? = PsiTreeUtil.prevVisibleLeaf(element)
        while (leaf != null && leaf.text.trim() != "=") {
            leaf = PsiTreeUtil.prevVisibleLeaf(leaf)
        }
        if (leaf == null) return null
        val keyLeaf = PsiTreeUtil.prevVisibleLeaf(leaf) ?: return null
        return keyLeaf.text.trim().takeIf { it.isNotBlank() }
    }

    private fun catalogExpressionDocumentation(element: PsiElement, token: String): String? {
        if (!isGraphLikeContext(element)) return null
        if (element.node?.elementType != DreamShaderTokenTypes.IDENTIFIER) return null

        val namespace = namespaceQualifierOf(element) ?: return null
        if (namespace.isBlank()) return null

        val entries = collectCatalogEntries(element)
        val qualifiedName = "$namespace.$token"
        val entry = entries.firstOrNull { it.qualifiedName.equals(qualifiedName, ignoreCase = true) }
            ?: return null

        val overrideKey = "ueBuiltins.${entry.ueName.lowercase(Locale.ROOT)}.description"
        val description = overrideDoc(element, overrideKey)
            ?: entry.description
            ?: DreamShaderBundle.message("docs.declaration.default")
        val signature = entry.signature?.takeIf { it.isNotBlank() } ?: "${entry.qualifiedName}(...)"

        return buildString {
            append("<b>")
            append(signature)
            append("</b><br/>")
            append(description)
            entry.outputType?.takeIf { it.isNotBlank() }?.let { outputType ->
                append("<br/>")
                append(DreamShaderBundle.message("docs.label.type"))
                append(": ")
                append(outputType)
            }
            if (entry.parameters.isNotEmpty()) {
                append("<br/>")
                append(DreamShaderBundle.message("docs.label.parameters"))
                append(":<ul>")
                entry.parameters.forEach { parameter ->
                    append("<li>")
                    parameter.qualifier?.takeIf { it.isNotBlank() }?.let { append(it).append(' ') }
                    append(parameter.name)
                    parameter.type?.takeIf { it.isNotBlank() }?.let { append(": ").append(it) }
                    parameter.placeholder?.takeIf { it.isNotBlank() }?.let { append(" = ").append(it) }
                    append("</li>")
                }
                append("</ul>")
            }
        }
    }

    /**
     * Resolves the namespace qualifier that immediately precedes [element] in a
     * `Namespace.Member` access, e.g. `UE` for the `Sine` token in `UE.Sine`.
     */
    private fun namespaceQualifierOf(element: PsiElement): String? {
        val dot = previousNonTriviaTokenText(element) ?: return null
        if (dot != ".") return null
        var leaf = PsiTreeUtil.prevVisibleLeaf(element) ?: return null
        leaf = PsiTreeUtil.prevVisibleLeaf(leaf) ?: return null
        val type = leaf.node?.elementType
        if (type != DreamShaderTokenTypes.IDENTIFIER && type != DreamShaderTokenTypes.TYPE) return null
        return leaf.text.trim().takeIf { it.isNotBlank() }
    }

    private fun collectCatalogEntries(element: PsiElement): List<DreamShaderMaterialExpressionInfo> {
        val project = element.project
        val settings = project.getService(
            com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings::class.java
        )
        return DreamShaderMaterialExpressionManifest.catalogEntries(
            project = project,
            explicitManifestPath = settings?.state?.materialExpressionManifestPath
        )
    }

    private fun functionCallDocumentation(element: PsiElement, token: String): String? {
        if (!isGraphLikeContext(element)) return null
        if (!isCallableReference(element)) return null

        val signatures = DreamShaderCallSignatureResolver.resolveSignatures(token, element.containingFile)
        if (signatures.isEmpty()) return null
        val signature = signatures.first()

        return buildString {
            append("<b>")
            append(DreamShaderBundle.message("docs.label.functionCall"))
            append(": ")
            append(token)
            append("</b><br/>")
            append("<code>")
            append(signature.presentableText)
            append("</code><br/>")
            append(DreamShaderBundle.message("docs.functionCall.signatureHint"))
        }
    }

    private fun localVariableDocumentation(element: PsiElement, token: String): String? {
        if (!isGraphLikeContext(element)) return null
        if (!isIdentifierLike(element, token)) return null
        if (isDeclarationNameIdentifier(element)) return null
        if (isCallableReference(element)) return null
        if (isNamedCallArgumentKey(element)) return null
        if (isNamespaceQualifier(element)) return null
        if (isMemberAccessComponent(element)) return null
        if (isBuiltInIdentifier(token)) return null

        val localVar = findNearestLocalVariableDeclaration(element, token) ?: return null
        return buildString {
            append("<b>")
            append(DreamShaderBundle.message("docs.label.localVariable"))
            append(": ")
            append(localVar.name)
            append("</b><br/>")
            append(DreamShaderBundle.message("docs.label.type"))
            append(": ")
            append(localVar.typeName)
            append("<br/>")
            append(DreamShaderBundle.message("docs.label.scope"))
            append(": ")
            append(localVar.scopeLabel)
        }
    }

    private fun isGraphLikeContext(element: PsiElement): Boolean {
        val declaration = PsiTreeUtil.getParentOfType(element, DreamShaderDeclaration::class.java, false)
        if (declaration?.isFunctionLike() == true) return true

        val section = PsiTreeUtil.getParentOfType(element, DreamShaderSection::class.java, false) ?: return false
        val name = section.sectionName() ?: return false
        return name == "graph" || name == "outputs" || name == "inputs" || name == "results"
    }

    private fun isInSettingsOrOptionsSection(element: PsiElement): Boolean {
        val section = PsiTreeUtil.getParentOfType(element, DreamShaderSection::class.java, false) ?: return false
        val sectionName = section.sectionName() ?: return false
        return sectionName == "settings" || sectionName == "options"
    }

    private fun normalizeTokenText(text: String): String {
        return text.trim().trim('"')
    }

    private fun isCallableReference(element: PsiElement): Boolean {
        if (element.node?.elementType != DreamShaderTokenTypes.IDENTIFIER) return false
        val next = nextNonTriviaTokenText(element) ?: return false
        return next == "("
    }

    private fun isIdentifierLike(element: PsiElement, token: String): Boolean {
        if (token.isBlank()) return false
        if (element.node?.elementType != DreamShaderTokenTypes.IDENTIFIER) return false
        return token == element.text
    }

    private fun isDeclarationDocumentationAnchor(element: PsiElement, declaration: DreamShaderDeclaration): Boolean {
        if (element == declaration) return true
        if (declaration.nameIdentifier == element) return true
        return isDeclarationKeywordElement(element, declaration)
    }

    private fun isDeclarationKeywordElement(element: PsiElement, declaration: DreamShaderDeclaration): Boolean {
        if (element.node?.elementType != DreamShaderTokenTypes.KEYWORD) return false
        var child = declaration.node.firstChildNode
        while (child != null) {
            if (child.elementType == DreamShaderTokenTypes.KEYWORD) {
                return child.psi == element
            }
            child = child.treeNext
        }
        return false
    }

    private fun declarationDisplayName(declaration: DreamShaderDeclaration): String? {
        val rawName = declaration.declarationName()
        val bodyStart = declaration.bodyTextRange()?.startOffset ?: declaration.text.length
        val head = declaration.text.substring(0, bodyStart.coerceIn(0, declaration.text.length))
        val keyValueName = NAME_ATTRIBUTE_REGEX.find(head)?.groupValues?.getOrNull(1)?.trim()
        if (!keyValueName.isNullOrBlank()) return keyValueName
        return rawName
    }

    private fun isDeclarationNameIdentifier(element: PsiElement): Boolean {
        val declaration = PsiTreeUtil.getParentOfType(element, DreamShaderDeclaration::class.java, false) ?: return false
        return declaration.nameIdentifier == element
    }

    private fun isBuiltInIdentifier(token: String): Boolean {
        if (token.equals("UE", ignoreCase = true)) return true
        if (token.equals("Base", ignoreCase = true)) return true
        return false
    }

    private fun isMemberAccessComponent(element: PsiElement): Boolean {
        val prev = previousNonTriviaTokenText(element) ?: return false
        return prev == "."
    }

    private fun isNamespaceQualifier(element: PsiElement): Boolean {
        val text = element.containingFile.text
        var i = element.textRange.endOffset
        while (i < text.length && text[i].isWhitespace()) i++
        if (i + 1 >= text.length) return false
        return text[i] == ':' && text[i + 1] == ':'
    }

    private fun isNamedCallArgumentKey(element: PsiElement): Boolean {
        val next = nextNonTriviaTokenText(element) ?: return false
        if (next != "=") return false

        val prevLeaf = PsiTreeUtil.prevVisibleLeaf(element) ?: return false
        val prevText = prevLeaf.text
        return prevText == "(" || prevText == ","
    }

    private fun nextNonTriviaTokenText(element: PsiElement): String? {
        var leaf = PsiTreeUtil.nextVisibleLeaf(element)
        while (leaf != null) {
            val type = leaf.node?.elementType
            if (type != DreamShaderTokenTypes.WHITE_SPACE &&
                type != DreamShaderTokenTypes.LINE_COMMENT &&
                type != DreamShaderTokenTypes.BLOCK_COMMENT
            ) {
                return leaf.text
            }
            leaf = PsiTreeUtil.nextVisibleLeaf(leaf)
        }
        return null
    }

    private fun previousNonTriviaTokenText(element: PsiElement): String? {
        var leaf = PsiTreeUtil.prevVisibleLeaf(element)
        while (leaf != null) {
            val type = leaf.node?.elementType
            if (type != DreamShaderTokenTypes.WHITE_SPACE &&
                type != DreamShaderTokenTypes.LINE_COMMENT &&
                type != DreamShaderTokenTypes.BLOCK_COMMENT
            ) {
                return leaf.text
            }
            leaf = PsiTreeUtil.prevVisibleLeaf(leaf)
        }
        return null
    }

    private fun findNearestLocalVariableDeclaration(element: PsiElement, variableName: String): LocalVariableInfo? {
        val declaration = PsiTreeUtil.getParentOfType(element, DreamShaderDeclaration::class.java, false) ?: return null
        val bodyRange = declaration.bodyTextRange() ?: return null
        val searchEnd = element.textRange.startOffset
        if (searchEnd <= bodyRange.startOffset) return null

        val text = element.containingFile.text
        val searchStart = bodyRange.startOffset
        val searchText = text.substring(searchStart, searchEnd)
        if (searchText.isBlank()) return null

        val tokens = lexWithOffsets(searchText, searchStart)
        for (index in tokens.indices.reversed()) {
            val token = tokens[index]
            if (token.type != DreamShaderTokenTypes.IDENTIFIER) continue
            if (!token.text.equals(variableName, ignoreCase = false)) continue
            val prev = previousSignificantToken(tokens, index) ?: continue
            if (prev.type != DreamShaderTokenTypes.TYPE) continue

            val scope = enclosingScopeLabel(declaration, element)
            return LocalVariableInfo(name = token.text, typeName = prev.text, scopeLabel = scope)
        }
        return null
    }

    private fun lexWithOffsets(text: String, baseOffset: Int): List<LexedToken> {
        val lexer = DreamShaderLexer()
        lexer.start(text)
        val out = mutableListOf<LexedToken>()
        while (lexer.tokenType != null) {
            val type = lexer.tokenType ?: break
            val start = lexer.tokenStart
            val end = lexer.tokenEnd
            out.add(
                LexedToken(
                    type = type,
                    text = text.substring(start, end),
                    range = TextRange(baseOffset + start, baseOffset + end)
                )
            )
            lexer.advance()
        }
        return out
    }

    private fun previousSignificantToken(tokens: List<LexedToken>, index: Int): LexedToken? {
        var i = index - 1
        while (i >= 0) {
            val token = tokens[i]
            if (token.type != DreamShaderTokenTypes.WHITE_SPACE &&
                token.type != DreamShaderTokenTypes.LINE_COMMENT &&
                token.type != DreamShaderTokenTypes.BLOCK_COMMENT
            ) {
                return token
            }
            i--
        }
        return null
    }

    private fun enclosingScopeLabel(declaration: DreamShaderDeclaration, element: PsiElement): String {
        val section = PsiTreeUtil.getParentOfType(element, DreamShaderSection::class.java, false)
        val sectionName = section?.sectionName()
        if (!sectionName.isNullOrBlank()) {
            return sectionName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
        val keyword = declaration.keywordText().orEmpty()
        return keyword.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
    }

    private fun overrideDoc(element: PsiElement, key: String): String? {
        return DreamShaderHoverOverrideService.resolve(element.project, key)
    }

    private data class LexedToken(
        val type: com.intellij.psi.tree.IElementType,
        val text: String,
        val range: TextRange
    )

    private data class LocalVariableInfo(
        val name: String,
        val typeName: String,
        val scopeLabel: String
    )

    companion object {
        private val NAME_ATTRIBUTE_REGEX = Regex("\\bName\\s*=\\s*\"([^\"]+)\"")
    }
}
