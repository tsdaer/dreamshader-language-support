
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

Lexing layer:
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/lexer/DreamShaderTokenType.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/lexer/DreamShaderTokenType.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/lexer/DreamShaderTokenSets.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/lexer/DreamShaderTokenSets.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/lexer/DreamShaderLanguageKeywords.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/lexer/DreamShaderLanguageKeywords.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/lexer/DreamShaderLexer.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/lexer/DreamShaderLexer.kt)

Parser and PSI infra:
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/parser/DreamShaderParserDefinition.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/parser/DreamShaderParserDefinition.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/parser/DreamShaderPsiParser.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/parser/DreamShaderPsiParser.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/core/DreamShaderElementType.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/core/DreamShaderElementType.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/core/DreamShaderPsiFile.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/core/DreamShaderPsiFile.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/core/DreamShaderPsiElement.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/core/DreamShaderPsiElement.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/psi/DreamShaderPsiElementFactory.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/psi/DreamShaderPsiElementFactory.kt)

Typed PSI nodes:
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/psi/DreamShaderDeclaration.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/psi/DreamShaderDeclaration.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/psi/impl/DreamShaderDeclarationImpl.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/psi/impl/DreamShaderDeclarationImpl.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/psi/DreamShaderSection.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/psi/DreamShaderSection.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/psi/impl/DreamShaderSectionImpl.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/psi/impl/DreamShaderSectionImpl.kt)

Symbol model:
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/symbols/DreamShaderSymbolKind.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/symbols/DreamShaderSymbolKind.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/symbols/DreamShaderSymbol.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/symbols/DreamShaderSymbol.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/symbols/DreamShaderSymbolModel.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/symbols/DreamShaderSymbolModel.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/symbols/DreamShaderSymbolModelBuilder.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/symbols/DreamShaderSymbolModelBuilder.kt)

Completion and editor features:
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/editor/DreamShaderCompletionContributor.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/editor/DreamShaderCompletionContributor.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderSemanticTokenClassifier.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderSemanticTokenClassifier.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderSyntaxHighlighter.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderSyntaxHighlighter.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderSyntaxHighlighterFactory.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderSyntaxHighlighterFactory.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderTextAttributes.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderTextAttributes.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderColorSettingsPage.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderColorSettingsPage.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/editor/DreamShaderCommenter.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/editor/DreamShaderCommenter.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/editor/DreamShaderBraceMatcher.kt`](../src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/editor/DreamShaderBraceMatcher.kt)

Icons/resources:
- [`src/main/resources/META-INF/pluginIcon.svg`](../src/main/resources/META-INF/pluginIcon.svg)
- [`src/main/resources/icons/dreamshaderFile.svg`](../src/main/resources/icons/dreamshaderFile.svg)

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
