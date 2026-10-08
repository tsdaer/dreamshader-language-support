package com.github.tsdaer.dreamshaderlanguagesupport.language.symbols

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2ElementTypes as E
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.DreamShader2FileAnalysis
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.psiTreeWalk
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes as T
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.impl.dreamShader2MeaningfulChildren
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.util.*

/** Live lexical bindings. Names are case sensitive; completion matching is the UI's concern. */
internal object DreamShader2SemanticResolver {
    data class Symbol(val name: String, val type: String?, val identifier: PsiElement, val owner: PsiElement)
    private val KEY = Key.create<CachedValue<List<Symbol>>>("dreamshader2.bindings")

    private fun bindings(file: DreamShaderPsiFile): List<Symbol> =
        CachedValuesManager.getManager(file.project).getCachedValue(file, KEY, {
            val symbols = psiTreeWalk(file).flatMap { element ->
                ProgressManager.checkCanceled()
                when (element) {
                    is DreamShader2Parameter -> listOfNotNull(element.parameterName()?.let { name ->
                        (element as? PsiNameIdentifierOwner)?.nameIdentifier?.let { Symbol(name, element.typeName(), it, element) }
                    })
                    is DreamShader2FunctionDeclaration -> named(element, element.returnTypeText())
                    is DreamShader2StructDeclaration -> named(element, element.declarationName())
                    is DreamShader2UniformDeclaration -> declarators(element, element.typeName())
                    is DreamShader2GlobalDeclaration -> declarators(element, element.typeName())
                    else -> if (element.node.elementType == E.VARIABLE_DECLARATION_STATEMENT) {
                        declarators(element, com.github.tsdaer.dreamshaderlanguagesupport.language.psi.impl.dreamShader2TypeSpellingOf(element))
                    } else emptyList()
                }.asSequence()
            }.toList()
            CachedValueProvider.Result.create(symbols, file)
        }, false)

    private fun named(owner: DreamShader2NamedDeclaration, type: String?): List<Symbol> {
        val name = owner.declarationName() ?: return emptyList()
        val id = (owner as? PsiNameIdentifierOwner)?.nameIdentifier
            ?: dreamShader2MeaningfulChildren(owner).firstOrNull { it.elementType == T.IDENTIFIER && it.text == name }?.psi ?: return emptyList()
        return listOf(Symbol(name, type, id, owner))
    }

    private fun declarators(owner: PsiElement, type: String?): List<Symbol> {
        val result = mutableListOf<Symbol>()
        var sawType = false
        var expectName = false
        // Direct children only: commas inside an initializer never declare names.
        dreamShader2MeaningfulChildren(owner).forEach { node ->
            when (node.elementType) {
                T.TYPE -> { sawType = true; expectName = true }
                T.IDENTIFIER -> if (!sawType) { sawType = true; expectName = true }
                    else if (expectName) { result += Symbol(node.text, type, node.psi, owner); expectName = false }
                E.LOCAL_DECLARATOR_NAME -> if (expectName) {
                    val name = node.psi as? DreamShader2NamedDeclaration
                    val id = (name as? PsiNameIdentifierOwner)?.nameIdentifier
                    if (name != null && id != null) result += Symbol(id.text, type, id, name)
                    expectName = false
                }
                T.COMMA -> expectName = true
            }
        }
        return result
    }

    fun isLocal(owner: PsiElement): Boolean = owner.node.elementType == E.VARIABLE_DECLARATION_STATEMENT ||
        owner.node.elementType == E.LOCAL_DECLARATOR_NAME && owner.parent?.node?.elementType == E.VARIABLE_DECLARATION_STATEMENT

    private fun isIncludedDeclaration(owner: PsiElement): Boolean {
        val declaration = if (owner.node.elementType == E.LOCAL_DECLARATOR_NAME) owner.parent else owner
        return declaration is DreamShader2UniformDeclaration || declaration is DreamShader2GlobalDeclaration ||
            declaration is DreamShader2FunctionDeclaration || declaration is DreamShader2StructDeclaration
    }

    fun scopeOwner(element: PsiElement): PsiElement? = generateSequence(element.parent) { it.parent }
        .firstOrNull { it.node?.elementType in setOf(E.BLOCK, E.FOR_STATEMENT, E.FUNCTION_DECLARATION) }

    fun visibleSymbols(file: DreamShaderPsiFile, use: PsiElement): List<Symbol> {
        ProgressManager.checkCanceled()
        val scopes = generateSequence(use) { it.parent }.toSet()
        val function = PsiTreeUtil.getParentOfType(use, DreamShader2FunctionDeclaration::class.java, false)
        val branches = com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.DreamShaderPreprocessorModel.of(file)
        val own = bindings(file).filter { symbol ->
            if (branches.stateAt(symbol.identifier.textOffset) == com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.DreamShaderPreprocessorModel.State.INACTIVE) return@filter false
            when {
                symbol.owner is DreamShader2Parameter -> symbol.owner.parent?.parent == function
                isLocal(symbol.owner) ->
                    symbol.identifier.textOffset < use.textOffset && scopeOwner(symbol.owner) in scopes
                else -> true
            }
        }.sortedWith(compareBy<Symbol> {
            when { isLocal(it.owner) -> 0
                it.owner is DreamShader2Parameter -> 1; else -> 2 }
        }.thenByDescending { it.identifier.textOffset })
        val included = file.project.getService(DreamShader2WorkspaceSymbols::class.java).includeClosurePsi(file)
            .filterIsInstance<DreamShaderPsiFile>().flatMap { bindings(it) }
            .filter { isIncludedDeclaration(it.owner) && active(it.owner) }
        return (own + included).groupBy { it.name }.values.map { candidates ->
            val selected = candidates.first()
            if (candidates.size == 1) return@map selected
            val alternatives = candidates.filter {
                it.owner.containingFile == selected.owner.containingFile && scopeOwner(it.owner) == scopeOwner(selected.owner) &&
                    isLocal(it.owner) == isLocal(selected.owner) && (it.owner is DreamShader2Parameter) == (selected.owner is DreamShader2Parameter)
            }
            val uncertain = alternatives.any {
                com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.DreamShaderPreprocessorModel.of(it.owner.containingFile as DreamShaderPsiFile)
                    .stateAt(it.identifier.textOffset) == com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.DreamShaderPreprocessorModel.State.UNKNOWN
            }
            if (uncertain && alternatives.map { it.type }.distinct().size > 1) selected.copy(type = null) else selected
        }
    }

    fun resolve(name: String, use: PsiElement): Symbol? {
        val file = use.containingFile as? DreamShaderPsiFile ?: return null
        return visibleSymbols(file, use).firstOrNull { it.name == name }
    }

    fun resolveIdentifier(identifier: PsiElement): Symbol? {
        val file = identifier.containingFile as? DreamShaderPsiFile ?: return null
        bindings(file).firstOrNull { it.identifier == identifier }?.let { return it }
        // Members, named arguments, and metadata are different namespaces.
        val parent = identifier.parent ?: return null
        if (parent.node.elementType == E.MEMBER_ACCESS_EXPRESSION &&
            dreamShader2MeaningfulChildren(parent).lastOrNull()?.psi == identifier) return null
        if (parent is DreamShader2NamedArgument && parent.argumentName() == identifier.text) return null
        if (parent.node.elementType in setOf(E.FUNCTION_DECLARATION, E.PARAMETER, E.STRUCT_FIELD,
                E.UNIFORM_DECLARATION, E.GLOBAL_DECLARATION, E.VARIABLE_DECLARATION_STATEMENT)) {
            val type = dreamShader2MeaningfulChildren(parent).firstOrNull { it.elementType == T.TYPE || it.elementType == T.IDENTIFIER }
            if (type?.psi == identifier) {
                val own = DreamShader2FileAnalysis.of(file).structs.firstOrNull { it.declarationName() == identifier.text && active(it) }
                val included = own ?: file.project.getService(DreamShader2WorkspaceSymbols::class.java).includeClosurePsi(file)
                    .filterIsInstance<DreamShaderPsiFile>().asSequence().flatMap { DreamShader2FileAnalysis.of(it).structs }
                    .firstOrNull { it.declarationName() == identifier.text && active(it) }
                return included?.let { named(it, it.declarationName()).singleOrNull() }
            }
        }
        if (parent.node.elementType !in setOf(E.REFERENCE_EXPRESSION, E.CALL_EXPRESSION, E.PARAMETER,
                E.VARIABLE_DECLARATION_STATEMENT, E.UNIFORM_DECLARATION, E.GLOBAL_DECLARATION)) return null
        return resolve(identifier.text, identifier)
    }

    private fun active(element: PsiElement) = com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.DreamShaderPreprocessorModel.of(element.containingFile as DreamShaderPsiFile).stateAt(element.textOffset) !=
        com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.DreamShaderPreprocessorModel.State.INACTIVE

    fun functions(file: DreamShaderPsiFile, name: String): List<DreamShader2FunctionDeclaration> {
        val local = DreamShader2FileAnalysis.of(file).functions.filter { it.declarationName() == name && active(it) }
        if (local.isNotEmpty()) return local
        return file.project.getService(DreamShader2WorkspaceSymbols::class.java).includeClosurePsi(file)
            .filterIsInstance<DreamShaderPsiFile>().flatMap { DreamShader2FileAnalysis.of(it).functions }
            .filter { it.declarationName() == name && active(it) }
    }
}
