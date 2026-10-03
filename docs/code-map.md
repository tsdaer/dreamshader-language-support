
# Code Map

Quick links to the main implementation files and tests. Keep this document focused on source navigation; move conceptual notes to `architecture.md` and milestone notes to `roadmap.md`.

## Quick File Links

Core plugin config:
- [`src/main/resources/META-INF/plugin.xml`](../src/main/resources/META-INF/plugin.xml)
- [`build.gradle.kts`](../build.gradle.kts)
- [`settings.gradle.kts`](../settings.gradle.kts)
- [`gradle.properties`](../gradle.properties)

Language registration:
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/core/DreamShaderLanguage.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/core/DreamShaderLanguage.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/core/DreamShaderFileType.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/core/DreamShaderFileType.kt)
- [`src/main/resources/META-INF/plugin.xml`](../src/main/resources/META-INF/plugin.xml)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/core/DreamShaderIcons.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/core/DreamShaderIcons.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/core/DreamShaderDialect.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/core/DreamShaderDialect.kt) (dialect model)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/core/DreamShader2ElementType.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/core/DreamShader2ElementType.kt) (2.0 element types)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/core/DreamShaderFileIconProvider.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/core/DreamShaderFileIconProvider.kt)

Lexing layer:
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/lexer/DreamShaderTokenType.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/lexer/DreamShaderTokenType.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/lexer/DreamShaderTokenSets.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/lexer/DreamShaderTokenSets.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/lexer/DreamShaderLanguageKeywords.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/lexer/DreamShaderLanguageKeywords.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/lexer/DreamShaderLexer.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/lexer/DreamShaderLexer.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/lexer/DreamShader2Lexer.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/lexer/DreamShader2Lexer.kt) (2.0 dialect)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/lexer/DreamShaderDispatchingLexer.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/lexer/DreamShaderDispatchingLexer.kt) (dialect routing)

Parser and PSI infra:
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/parser/DreamShaderParserDefinition.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/parser/DreamShaderParserDefinition.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/parser/DreamShaderPsiParser.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/parser/DreamShaderPsiParser.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/parser/DreamShader2PsiParser.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/parser/DreamShader2PsiParser.kt) (2.0 dialect)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/parser/DreamShaderDialectAwarePsiParser.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/parser/DreamShaderDialectAwarePsiParser.kt) (dialect routing)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/core/DreamShaderElementType.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/core/DreamShaderElementType.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/core/DreamShaderPsiFile.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/core/DreamShaderPsiFile.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/core/DreamShaderPsiElement.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/core/DreamShaderPsiElement.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/psi/DreamShaderPsiUtil.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/psi/DreamShaderPsiUtil.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/psi/DreamShaderPsiElementFactory.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/psi/DreamShaderPsiElementFactory.kt)

Typed PSI nodes:
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/psi/DreamShaderDeclaration.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/psi/DreamShaderDeclaration.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/psi/impl/DreamShaderDeclarationImpl.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/psi/impl/DreamShaderDeclarationImpl.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/psi/DreamShaderSection.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/psi/DreamShaderSection.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/psi/impl/DreamShaderSectionImpl.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/psi/impl/DreamShaderSectionImpl.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/psi/DreamShader2Declaration.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/psi/DreamShader2Declaration.kt) (2.0 typed PSI)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/psi/impl/DreamShader2DeclarationImpl.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/psi/impl/DreamShader2DeclarationImpl.kt)

Symbol model:
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/symbols/DreamShaderSymbolKind.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/symbols/DreamShaderSymbolKind.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/symbols/DreamShaderSymbol.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/symbols/DreamShaderSymbol.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/symbols/DreamShaderSymbolModel.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/symbols/DreamShaderSymbolModel.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/symbols/DreamShaderSymbolModelBuilder.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/symbols/DreamShaderSymbolModelBuilder.kt)

Navigation and refactoring:
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/navigation/DreamShaderGotoDeclarationHandler.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/navigation/DreamShaderGotoDeclarationHandler.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/navigation/DreamShaderReferencesSearchExecutor.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/navigation/DreamShaderReferencesSearchExecutor.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/navigation/DreamShaderDeclarationSearch.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/navigation/DreamShaderDeclarationSearch.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/refactoring/DreamShaderRenameInputValidator.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/refactoring/DreamShaderRenameInputValidator.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/refactoring/DreamShaderRefactoringSupportProvider.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/refactoring/DreamShaderRefactoringSupportProvider.kt)

Completion and editor features:
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/editor/DreamShaderCompletionContributor.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/editor/DreamShaderCompletionContributor.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderSemanticTokenClassifier.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderSemanticTokenClassifier.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderSyntaxHighlighter.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderSyntaxHighlighter.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderSyntaxHighlighterFactory.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderSyntaxHighlighterFactory.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderTextAttributes.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderTextAttributes.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderColorSettingsPage.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderColorSettingsPage.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/editor/DreamShaderCommenter.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/editor/DreamShaderCommenter.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/editor/DreamShaderBraceMatcher.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/editor/DreamShaderBraceMatcher.kt)

Bridge, preview, packages, and workflows:
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/bridge/DreamShaderBridgeActions.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/bridge/DreamShaderBridgeActions.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/bridge/DreamShaderBridgeDiagnosticsRepository.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/bridge/DreamShaderBridgeDiagnosticsRepository.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/editor/DreamShaderBuiltinCatalogManifest.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/editor/DreamShaderBuiltinCatalogManifest.kt) (2.0 binder catalog parser + bundled fallback)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/bridge/DreamShaderSymbolIndexRepository.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/bridge/DreamShaderSymbolIndexRepository.kt) (symbol-index v1 read-only repository)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/bridge/DreamShaderBridgeStatus.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/bridge/DreamShaderBridgeStatus.kt) (bridge.db meta reader, status.json/owner.lock liveness)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/bridge/DreamShaderRevealNodeAction.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/bridge/DreamShaderRevealNodeAction.kt) (reveal-node action + gutter marker)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/bridge/DreamShaderRevealNodeSupport.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/bridge/DreamShaderRevealNodeSupport.kt) (reveal-node request writer / response reader)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/preview/DreamShaderMaterialPreviewPanel.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/preview/DreamShaderMaterialPreviewPanel.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/preview/DreamShaderPreviewRequestWriter.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/preview/DreamShaderPreviewRequestWriter.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/packages/DreamShaderPackageStoreDialog.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/packages/DreamShaderPackageStoreDialog.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/templates/DreamShaderTemplateActions.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/templates/DreamShaderTemplateActions.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/welcome/DreamShaderWelcome.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/welcome/DreamShaderWelcome.kt)

Icons/resources:
- [`src/main/resources/META-INF/pluginIcon.svg`](../src/main/resources/META-INF/pluginIcon.svg)
- [`src/main/resources/icons/dreamshaderFile.svg`](../src/main/resources/icons/dreamshaderFile.svg)

Shared UI design system:
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/ui/DreamShaderUi.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/ui/DreamShaderUi.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/settings/DreamShaderSettingsConfigurable.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/settings/DreamShaderSettingsConfigurable.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/packages/DreamShaderPackageStoreDialog.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/packages/DreamShaderPackageStoreDialog.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/preview/DreamShaderMaterialPreviewPanel.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/preview/DreamShaderMaterialPreviewPanel.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/bridge/DreamShaderBridgeToolWindowFactory.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/bridge/DreamShaderBridgeToolWindowFactory.kt)

Tests:
- [`src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderLexerSyntaxHighlighterTest.kt`](../src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderLexerSyntaxHighlighterTest.kt)
- [`src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/integration/DreamShaderLargeFilePerformanceSmokeTest.kt`](../src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/integration/DreamShaderLargeFilePerformanceSmokeTest.kt)
- [`src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/integration/DreamShaderUpstreamExamplesTest.kt`](../src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/integration/DreamShaderUpstreamExamplesTest.kt)
- [`src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderSemanticTokenClassifierTest.kt`](../src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderSemanticTokenClassifierTest.kt)
- [`src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderSemanticTokensTest.kt`](../src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderSemanticTokensTest.kt)
- [`src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/editor/DreamShaderCompletionContextAnalyzerTest.kt`](../src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/editor/DreamShaderCompletionContextAnalyzerTest.kt)
- [`src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/editor/DreamShaderCompletionSuggesterTest.kt`](../src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/editor/DreamShaderCompletionSuggesterTest.kt)
- [`src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/editor/DreamShaderImportPathNormalizationTest.kt`](../src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/editor/DreamShaderImportPathNormalizationTest.kt)
- [`src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/editor/DreamShaderFoldingBuilderTest.kt`](../src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/editor/DreamShaderFoldingBuilderTest.kt)
- [`src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/parser/DreamShaderPsiParserTest.kt`](../src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/parser/DreamShaderPsiParserTest.kt)
- [`src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/navigation/DreamShaderDeclarationRenameTest.kt`](../src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/navigation/DreamShaderDeclarationRenameTest.kt)
- [`src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderBundleLocalizationTest.kt`](../src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderBundleLocalizationTest.kt)
- [`src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/symbols/DreamShaderSymbolModelBuilderTest.kt`](../src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/symbols/DreamShaderSymbolModelBuilderTest.kt)
- [`src/test/resources/upstream/Examples.md`](../src/test/resources/upstream/Examples.md)
- [`src/test/testData/rename/foo.xml`](../src/test/testData/rename/foo.xml)
- [`src/test/testData/rename/foo_after.xml`](../src/test/testData/rename/foo_after.xml)
