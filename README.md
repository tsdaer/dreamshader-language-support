# dreamshader-language-support (Rider)

<!-- plugin-metadata:start -->
name: Dreamshader Language Extension
description: Comprehensive DreamShaderLang support for JetBrains Rider, including `.dsm`/`.dsf`/`.dsh` file type registration, syntax highlighting, parsing and PSI foundations, context-aware completion, navigation and references, diagnostics and bridge integration, semantic tokens and inlay hints, package store and package lifecycle tooling, template generation, and a unified DreamShader Hub workflow entry.
<!-- plugin-metadata:end -->

JetBrains Rider plugin for DreamShaderLang (`.dsm`, `.dsf`, `.dsh`).
Built on IntelliJ Platform SDK with platform module dependency (`com.intellij.modules.platform`).

Target reference (VS Code extension):
https://github.com/TypeDreamMoon/dreamshader-language-support

## Quick File Links

Core plugin config:
- [`src/main/resources/META-INF/plugin.xml`](src/main/resources/META-INF/plugin.xml)
- [`build.gradle.kts`](build.gradle.kts)
- [`settings.gradle.kts`](settings.gradle.kts)
- [`gradle.properties`](gradle.properties)

Language registration:
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/core/DreamShaderLanguage.kt`](src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/core/DreamShaderLanguage.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/core/DreamShaderFileType.kt`](src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/core/DreamShaderFileType.kt)
- [`src/main/resources/META-INF/plugin.xml`](src/main/resources/META-INF/plugin.xml)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/core/DreamShaderIcons.kt`](src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/core/DreamShaderIcons.kt)

Lexing layer:
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/lexer/DreamShaderTokenType.kt`](src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/lexer/DreamShaderTokenType.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/lexer/DreamShaderTokenSets.kt`](src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/lexer/DreamShaderTokenSets.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/lexer/DreamShaderLanguageKeywords.kt`](src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/lexer/DreamShaderLanguageKeywords.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/lexer/DreamShaderLexer.kt`](src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/lexer/DreamShaderLexer.kt)

Parser and PSI infra:
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/parser/DreamShaderParserDefinition.kt`](src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/parser/DreamShaderParserDefinition.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/parser/DreamShaderPsiParser.kt`](src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/parser/DreamShaderPsiParser.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/core/DreamShaderElementType.kt`](src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/core/DreamShaderElementType.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/core/DreamShaderPsiFile.kt`](src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/core/DreamShaderPsiFile.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/core/DreamShaderPsiElement.kt`](src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/core/DreamShaderPsiElement.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/psi/DreamShaderPsiElementFactory.kt`](src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/psi/DreamShaderPsiElementFactory.kt)

Typed PSI nodes:
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/psi/DreamShaderDeclaration.kt`](src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/psi/DreamShaderDeclaration.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/psi/impl/DreamShaderDeclarationImpl.kt`](src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/psi/impl/DreamShaderDeclarationImpl.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/psi/DreamShaderSection.kt`](src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/psi/DreamShaderSection.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/psi/impl/DreamShaderSectionImpl.kt`](src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/psi/impl/DreamShaderSectionImpl.kt)

Symbol model:
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/symbols/DreamShaderSymbolKind.kt`](src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/symbols/DreamShaderSymbolKind.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/symbols/DreamShaderSymbol.kt`](src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/symbols/DreamShaderSymbol.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/symbols/DreamShaderSymbolModel.kt`](src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/symbols/DreamShaderSymbolModel.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/symbols/DreamShaderSymbolModelBuilder.kt`](src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/symbols/DreamShaderSymbolModelBuilder.kt)

Completion and editor features:
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/editor/DreamShaderCompletionContributor.kt`](src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/editor/DreamShaderCompletionContributor.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderSemanticTokenClassifier.kt`](src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderSemanticTokenClassifier.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderSyntaxHighlighter.kt`](src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderSyntaxHighlighter.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderSyntaxHighlighterFactory.kt`](src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderSyntaxHighlighterFactory.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderTextAttributes.kt`](src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderTextAttributes.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderColorSettingsPage.kt`](src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderColorSettingsPage.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/editor/DreamShaderCommenter.kt`](src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/editor/DreamShaderCommenter.kt)
- [`src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/editor/DreamShaderBraceMatcher.kt`](src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/editor/DreamShaderBraceMatcher.kt)

Icons/resources:
- [`src/main/resources/META-INF/pluginIcon.svg`](src/main/resources/META-INF/pluginIcon.svg)
- [`src/main/resources/icons/dreamshaderFile.svg`](src/main/resources/icons/dreamshaderFile.svg)

Tests:
- [`src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderLexerSyntaxHighlighterTest.kt`](src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderLexerSyntaxHighlighterTest.kt)
- [`src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/integration/DreamShaderLargeFilePerformanceSmokeTest.kt`](src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/integration/DreamShaderLargeFilePerformanceSmokeTest.kt)
- [`src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderSemanticTokenClassifierTest.kt`](src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderSemanticTokenClassifierTest.kt)
- [`src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderSemanticTokensTest.kt`](src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderSemanticTokensTest.kt)
- [`src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/editor/DreamShaderCompletionContextAnalyzerTest.kt`](src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/editor/DreamShaderCompletionContextAnalyzerTest.kt)
- [`src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/editor/DreamShaderCompletionSuggesterTest.kt`](src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/editor/DreamShaderCompletionSuggesterTest.kt)
- [`src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/editor/DreamShaderFoldingBuilderTest.kt`](src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/editor/DreamShaderFoldingBuilderTest.kt)
- [`src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/parser/DreamShaderPsiParserTest.kt`](src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/parser/DreamShaderPsiParserTest.kt)
- [`src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderBundleLocalizationTest.kt`](src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderBundleLocalizationTest.kt)
- [`src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/symbols/DreamShaderSymbolModelBuilderTest.kt`](src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/symbols/DreamShaderSymbolModelBuilderTest.kt)
- [`src/test/testData/rename/foo.xml`](src/test/testData/rename/foo.xml)
- [`src/test/testData/rename/foo_after.xml`](src/test/testData/rename/foo_after.xml)

## Architecture and Data Flow

This plugin currently follows a layered architecture:

1. Lexer and parser foundation
- `DreamShaderLexer` tokenizes source text.
- `DreamShaderPsiParser` builds a permissive AST for declarations and sections.
- `DreamShaderParserDefinition` wires lexer/parser/PSI construction into IntelliJ.

2. PSI and symbol model
- Typed PSI (`DreamShaderDeclaration`, `DreamShaderSection`) is the canonical structure for editor features.
- `DreamShaderSymbolModelBuilder` derives declaration/section symbols for structure/navigation use.

3. Editor intelligence
- Completion (`DreamShaderCompletionContributor`) uses PSI-first context analysis with lexer fallback.
- Signature help and inlay hints consume call-signature parsing utilities (current implementation is parameter inlay hints, not IntelliJ Code Vision lenses).
- Navigation/references rely on declaration symbol extraction and identifier matching.

4. Diagnostics pipeline
- `DreamShaderSemanticAnnotator` is the central diagnostic entry and aggregates:
- Syntax diagnostics
- Section-shape diagnostics
- Semantic diagnostics
- Bridge diagnostics mapped back to source ranges

5. Bridge integration
- `DreamShaderBridgePathResolver` resolves project root and Bridge folder with explicit-setting-first fallback.
- `DreamShaderBridgeDiagnosticsRepository` loads and normalizes Bridge diagnostics snapshots.
- `DreamShaderMaterialExpressionManifest` merges expression classes from explicit path, Bridge manifest, and bundled fallback.

6. Package index data layer (M5 completed)
- `DreamShaderPackageIndexLoader` resolves package index sources (multi-source, legacy single-source, default upstream).
- Index loader accepts both JSON shapes: array root and `{ "packages": [...] }`.
- Entry `path` is resolved relative to local index location; unresolved paths degrade to `repository`.

7. Project-level persistent settings
- `DreamShaderProjectSettings` stores project-scoped configuration for Bridge and package tooling.
- Current keys: `projectRoot`, `materialExpressionManifestPath`, `showStatusBar`, `enableCodeLens`, `packageStoreIndexUrls`, `packageStoreIndexUrl`, `packageStoreGitHubToken`, `bridgeRecompileCurrentCommand`, `bridgeRecompileAllCommand`, `bridgeCleanGeneratedShadersCommand`.

Detailed architecture doc:
- [`docs/architecture.md`](docs/architecture.md)

## Goal

Build a Rider plugin with feature parity to the VS Code DreamShaderLang extension, in phases:
- language core first
- editing and navigation second
- diagnostics and tool integration third
- package tooling and UX polish last

## DreamShaderLang Syntax Baseline

Primary language reference (upstream):
- https://github.com/TypeDreamMoon/DreamShader/blob/main/Docs/LanguageReference.md

Reference snapshot used for this README alignment:
- Checked on `2026-05-21`
- Upstream doc title: `DreamShaderLang 语法参考`
- Upstream plugin version noted in doc: `1.3.7`

This section summarizes the language rules that this Rider plugin should follow.

### 1. File Roles and Constraints

- `.dsm`: material-oriented source; usually contains `Shader(...)` and may include shared helpers/imports.
- `.dsf`: function asset source; may contain `ShaderFunction(...)`, `Function`, `GraphFunction`, `VirtualFunction`, imports.
- `.dsh`: shared header source; usually shared `Function`/`GraphFunction`/`Namespace`/`VirtualFunction`.

Key constraints to enforce:
- `.dsf` top-level declarations are restricted to `ShaderFunction(...)`, `ShaderLayer(...)`, `ShaderLayerBlend(...)`, `VirtualFunction(...)`, `Function(...)`, `GraphFunction(...)`.
- `.dsm` must not declare top-level `ShaderFunction(...)`, `ShaderLayer(...)`, or `ShaderLayerBlend(...)` (function/layer assets belong in `.dsf`).
- `.dsh` should not be used for asset-generating declarations such as `Shader(...)`, `ShaderFunction(...)`, `ShaderLayer(...)`, `ShaderLayerBlend(...)`.

### 2. Top-Level Declarations

Expected declaration families:
- Asset declarations: `Shader`, `ShaderFunction`, `ShaderLayer`, `ShaderLayerBlend`.
- External asset signature declaration: `VirtualFunction`.
- Shared code declarations: `Function`, `GraphFunction`, `Namespace`.
- Import declaration: `import "..."`.

Important semantics to preserve:
- `Root="..."` path semantics on asset declarations (e.g. `Game`, `Plugin.<Name>`, optional subfolders).
- `ShaderLayer` / `ShaderLayerBlend` output-shape requirements (material attributes output constraints).
- `VirtualFunction` participates in call signatures but does not generate/overwrite assets.

### 3. Section Model

Canonical sections:
- `Properties`
- `Inputs`
- `Outputs`
- `Settings`
- `Options`
- `Graph`

Expected behavior highlights:
- `Inputs` supports optional inputs (`opt`) and default-value usage conventions.
- `Outputs` supports direct initialization and material output binding patterns.
- `Settings` / `Options` map to Unreal material/function configuration fields.
- Section schema differs by declaration kind (`Shader` vs `ShaderFunction` vs `VirtualFunction`, etc.).

### 4. Graph / Function Semantics

Graph-level constructs expected by reference:
- Variable declarations, assignments, constructors, brace initializers.
- `if` / `else` flow in graph DSL.
- Calls to `Function(...)`, `Namespace::Function(...)`.
- Calls to `GraphFunction(...)` and `Namespace::GraphFunction(...)`.
- Calls to `ShaderFunction(...)` / `VirtualFunction(...)`.
- `UE.*` builtins for Unreal material node creation.

Function-level semantics:
- `Function` supports `in`/`out` style parameters; `out` arguments are explicit at call sites.
- `GraphFunction` compiles as custom-node style reusable graph helper and can consume `UE.*` sources.

### 5. Import, Path, and Type System

Import expectations:
- File imports via `import "..."` (`.dsh`, `.dsf`, etc.).
- Package-style imports (including namespaced package paths) are part of upstream behavior.

Path helper expectations:
- `Path(...)` forms and root restrictions should be parsed/validated consistently with upstream behavior.

Type system expectations:
- Scalar/vector/matrix families.
- GLSL-style aliases (`vec*`, etc.) alongside Unreal/HLSL-like types.
- Texture/sampler-related types.
- Compatibility handling for removed/legacy aliases where applicable.

### 6. Known Language Limits (From Upstream Reference)

- Graph DSL is intentionally not a fully general-purpose programming language.
- Graph currently supports `if` / `else`, not full loop/control-flow parity.
- Rider plugin currently reports diagnostics for unsupported Graph control-flow statements: `for`, `while`, `do`, `switch`, `case`, `default`, `break`, `continue`, `return`.
- `Function` calls require explicit `out` target passing.
- `Namespace` is for function organization (not arbitrary declaration containers).

### 7. Rider Plugin Coverage Mapping

Already implemented in this plugin:
- File type association (`.dsm`, `.dsf`, `.dsh`).
- Top-level declaration and section tokenization/parsing foundations.
- File-role declaration constraints baseline (`.dsf` uses top-level declaration whitelist; `.dsm` disallow top-level `ShaderFunction`/`ShaderLayer`/`ShaderLayerBlend`; `.dsh` disallow asset-generating top-level declarations).
- Declaration section-shape diagnostics baseline with alias/compat behavior (`Results` compatibility for `ShaderFunction`/`VirtualFunction`, declaration-specific allowed/required section checks, duplicate section detection).
- Context-aware completion for sections, types, settings values, `UE.*`, HLSL intrinsics, imports.
- Navigation/symbols/folding/references/hover/signature help basics.

Not fully implemented yet (tracked in milestones below):
- Full section-shape parity with all upstream declaration edge cases and future syntax revisions.
- Full semantic diagnostics for invalid settings/outputs/types.
- Full Graph grammar validation beyond current permissive parser stage.
- Formatting rules aligned with DreamShader structure conventions.
- Semantic token classification parity with upstream VS Code behavior.
- Inlay hints parity for callable/output authoring contexts.

## DreamShader Package Baseline

Primary package reference (upstream):
- https://github.com/TypeDreamMoon/DreamShader/blob/main/Docs/Packages.md

Reference snapshot used for this README alignment:
- Checked on `2026-05-21`
- Upstream doc title: `DreamShader Package`

This section summarizes package rules and behaviors the Rider plugin should align with.

### 1. Package Layout and Metadata

Recommended package structure:
- Package root includes `dreamshader.package.json`, `README.md`, `LICENSE`.
- Shared library files are typically under `Library/**/*.dsh`.
- Example materials are typically under `Examples/**/*.dsm`.

Required metadata file:
- `dreamshader.package.json` must exist at package root.
- `name` is required; accepted forms include `name` and `@scope/name`.
- `repository` is the canonical source for install/update.
- `dreamshader.entry` is the recommended library entry file shown in docs/store UX.
- `version` should follow SemVer conventions.

### 2. Install Location and Lock File

Install target in project:
- `DShader/Packages/<package-name>/`
- Scoped names (for example `@typedreammoon/dream-noise`) keep their scoped path.

Lock file:
- `DShader/dreamshader.lock.json` must be created/updated on install/update/remove.
- Lock entries should track at least: package name, version, repository, commit, install path.

Example handling:
- `Examples/**/*.dsm` inside installed packages should not be auto-compiled by default.
- Recommended workflow is to copy examples into project-owned material folders when used.

### 3. Package Import Resolution

Package import form:
- `import "@scope/name/Library/Noise.dsh";`
- Extension-less form should also resolve: `import "@scope/name/Library/Noise";`

Expected import resolution order:
1. Current file relative path
2. Project `DShader/`
3. Project `DShader/Packages/`
4. Built-in plugin library path (`Plugins/DreamShader/Library/`)

### 4. Package Store and Index Sources

Store source model:
- Support multiple index URLs/paths via `dreamshader.packageStoreIndexUrls`.
- Keep backward compatibility with legacy single source setting `dreamshader.packageStoreIndexUrl`.

Supported index JSON shapes:
- Array form: `[ { ...package... } ]`
- Object form: `{ "packages": [ ... ] }`

Index item baseline fields:
- `name`, `displayName`, `description`, `repository`
- Optional: `path` (for local index development), `tags`

`path` handling:
- When index is local, relative `path` should be resolved against the index file directory.
- If local `path` cannot be resolved, fallback to `repository`.

Default upstream index:
- `https://raw.githubusercontent.com/TypeDreamMoon/dreamshader-package-index/main/packages.json`

### 5. Package Actions Baseline (Rider parity target)

Rider package tooling should reach parity with VSCode package workflows:
- Install package from GitHub repo input (`owner/repo` or full GitHub URL).
- Browse package store (search/install/open repository/manage sources).
- Update installed packages.
- Remove installed package.
- Open packages folder.
- Add/remove package store index source.
- (Optional later) guided package skeleton creator.

Runtime prerequisite:
- Install/update operations depend on available `git` in local environment.

### 6. Authoring Recommendations (Upstream-aligned)

- Put reusable public helpers in `Library/**/*.dsh`.
- Use `Namespace(Name="...")` to avoid symbol collisions across packages.
- Place demos in `Examples/**/*.dsm`.
- Document import path and usage in package README.
- Add GitHub topic `dreamshader-package` for discoverability.

### 7. Rider Coverage Mapping

Already implemented in this plugin:
- Import completion can suggest project `.dsh` / `.dsf` files.
- Import navigation resolves to local files when paths are valid.

Implemented in M5:
- Optional GitHub package discovery integration (`DreamShaderGitHubPackageSearch`) is available.

## Current Progress

| Area | Status | Notes |
|---|---|---|
| File type association (`.dsm`, `.dsf`, `.dsh`) | Done | Registered in plugin.xml |
| Basic lexer | Done | Handles identifiers, keywords, sections, types, strings, numbers, comments, operators, braces |
| Syntax highlighting | Done | Keyword/section/type/string/number/comment/operator/brace coloring |
| Comment support | Done | `//` and `/* ... */` |
| Brace matching | Done | `()`, `[]`, `{}` |
| Color settings page | Done | DreamShader color entries available in editor color scheme |
| Plugin/file icons | Done | Added plugin icon and DreamShader file icon |
| Token sets/constants | Done | Added token set groups for future completion/diagnostics |
| Context-aware completion | Done | PSI-first context detection + lexer fallback; top-level/section/type/settings/base-output/UE/HLSL/import completion available (including quoted enums + unquoted bool/number settings values with typed-prefix filtering) |
| PSI / Parser foundation | Done | ParserDefinition + PsiParser + typed PSI for declaration/section implemented |
| Folding | Done | `lang.foldingBuilder` added; supports brace blocks and `// region` / `// endregion` |
| Semantic tokens | Done | Semantic classification implemented for declaration keywords/names, section names, callable references, `UE` namespace, namespace qualifiers (`Namespace::`), local symbol declaration/usage split, and `Base.*` material output members |
| Diagnostics | Done | Local parser + section-shape + semantic diagnostics implemented with tests |
| Go to Definition / References | Done | Go to Definition + Find References implemented for top-level declaration symbols |
| Document symbols / structure | Done | Structure view integrated for top-level declarations and sections |
| Inlay hints | Done | Parameter name hints implemented with callable-context filtering and settings toggle (`enableCodeLens` controls this inlay-hints layer) |
| Formatting | Done | Basic formatter implemented (`lang.formatter`): indentation, operator spacing, braces/section layout |
| Bridge diagnostics panel | Done | Tool window added with refresh/list/open-location baseline |
| Bridge actions | Done | Refresh/open-location/open bridge path + configurable recompile/clean command execution |
| Status bar / CodeLens | Done | Status widget is gated by `showStatusBar`; inlay hints and IntelliJ daemon-bound Code Vision are gated by `enableCodeLens` |
| Package commands | Done | install/update/remove/browse/open folder + source add/remove wired |
| Authoring templates / scaffold commands | Done | Material/function/header/package scaffold commands implemented and covered by tests (`DTPL-001` to `DTPL-004`) |

## Detailed TODO

Status legend:
- `[x]` completed
- `[ ]` pending

Priority legend:
- `P0` critical foundation
- `P1` core language features
- `P2` advanced language and bridge features
- `P3` tooling polish and release readiness

### Milestone M0: Foundation (Completed)

- [x] `P0` Replace template plugin scaffolding with Rider language plugin skeleton.
- [x] `P0` Register DreamShaderLang file type and extensions in `plugin.xml`.
- [x] `P0` Implement initial lexer and syntax highlighter.
- [x] `P0` Implement commenter and brace matcher.
- [x] `P0` Clean template sample code/tests not related to DreamShader.

Acceptance criteria:
- `.dsm/.dsf/.dsh` open as DreamShaderLang in Rider.
- Basic tokens are highlighted.
- Toggle line/block comment works.
- Brace pairing works for all three bracket kinds.

### Milestone M1: Language Infrastructure

- [x] `P0` Introduce PSI + parser definition (or a staged lightweight structure model).
- [x] `P0` Add token sets/constants for future completion and diagnostics.
- [x] `P1` Add `ColorSettingsPage` so users can customize DreamShader colors in settings.
- [x] `P1` Add language sample text for preview in color settings.
- [x] `P1` Add plugin icons and file icons.

Acceptance criteria:
- Language has a stable internal model extensible for completion/navigation.
- DreamShader color entries appear in Editor Color Scheme settings.

Current staged scope:
- Typed PSI added for `DECLARATION` / `SECTION`.
- Typed PSI accessors added: declaration keyword/name/body range, section name/body range, function-like declaration marker.
- Completion context analysis now uses PSI tree information for file inputs and parser-based structure analysis for text inputs (lexer kept only as defensive fallback).
- Minimal symbol model added from typed PSI: top-level declarations with section children.

### Milestone M2: Completion (Core Editing)

- [x] `P1` Top-level keyword completion: `import`, `Shader`, `ShaderFunction`, `ShaderLayer`, `ShaderLayerBlend`, `VirtualFunction`, `Function`, `GraphFunction`, `Namespace`.
- [x] `P1` Section-aware completion: `Properties`, `Inputs`, `Outputs`, `Settings`, `Options`, `Graph`.
- [x] `P1` Type completion in declaration/function/graph contexts.
- [x] `P1` Settings key/value completion (Domain/ShadingModel/BlendMode/etc.).
- [x] `P1` Material output completion (`Base.*` members).
- [x] `P1` `UE.*` completion and snippet insertion (including `UE.Expression(...)`).
- [x] `P1` HLSL intrinsic completion in function/graph-like code blocks.
- [x] `P1` Import path completion for `.dsh` and `.dsf`.

Acceptance criteria:
- Completion proposals are context-sensitive and match major VS Code behaviors.
- Snippet placeholders expand correctly in Rider.

Implemented:
- Settings/Options section key completion and key-specific value completion.
- `Options` section key completion now follows declaration semantics for `VirtualFunction` (`Asset`, `Description`) instead of generic material settings keys.
- `VirtualFunction` `Options.Asset` value completion now suggests `Path(...)` templates in unquoted context and object-path templates in quoted context, both with typed-prefix filtering.
- `VirtualFunction` `Settings` section (compatibility alias for option-style authoring) now reuses the same key/value completion behavior for `Asset` and `Description`.
- Settings value completion now supports quoted enum values and unquoted scalar forms (`true/false`, `NumCustomizedUVs` `0..8`) with typed-prefix filtering.
- Material output completion for `Base.*` members with assignment insertion.
- `UE.*` member completion with snippet-like insert texts (including `UE.Expression(...)` caret positioning).
- HLSL intrinsic completion in graph-like/function-like contexts.
- Import path completion for project `.dsh` / `.dsf` files.

### Milestone M3: Navigation and Symbols

- [x] `P1` Document symbols for top-level declarations and sections.
- [x] `P1` Folding for blocks and `// region` markers.
- [x] `P1` Go to Definition for imports and symbol declarations.
- [x] `P1` Find References for local declarations/usages.
- [x] `P2` Semantic token classification for declarations, sections, code symbols, and material outputs.
- [x] `P2` Hover information for symbols/settings/UE builtins.
- [x] `P2` Signature help for function-like calls where feasible.
- [x] `P2` Inlay hints for callable signatures and output-flow authoring context.

Acceptance criteria:
- Structure view/symbol popup is useful on real project files.
- Definition/reference flows work for common authoring operations.
- Semantic token coloring is stable across repeated edits and consistent for declaration/section/code-symbol categories.
- Inlay hints improve call-site readability for function-like declarations without introducing noisy/duplicate hints.

Implemented:
- Added `DreamShaderFoldingBuilder` and plugin registration via `lang.foldingBuilder`.
- Supports multiline `{ ... }` folding and custom `// region ...` / `// endregion` folding.
- Added regression tests in `DreamShaderFoldingBuilderTest`.
- Internal symbol model builder added from typed PSI with tests (`DreamShaderSymbolModelBuilder` / `DreamShaderSymbolModelBuilderTest`).
- Added structure view integration via `lang.psiStructureViewFactory` for top-level declarations and sections.
- Added regression test in `DreamShaderStructureViewModelTest`.
- Added `DreamShaderGotoDeclarationHandler` via `gotoDeclarationHandler` extension.
- Supports definition jump for `import "..."` targets (`.dsh/.dsf/.dsm`) and same-file top-level declaration symbol usages.
- Added regression tests in `DreamShaderGotoDeclarationHandlerTest`.
- Added `DreamShaderReferencesSearchExecutor` via `referencesSearch` extension for declaration usage discovery.
- Added `DreamShaderFindUsagesProvider` and `PsiNameIdentifierOwner` support on `DreamShaderDeclaration`.
- Added regression tests in `DreamShaderFindReferencesTest` and `DreamShaderDeclarationRenameTest`.
- Added `DreamShaderDocumentationProvider` via `lang.documentationProvider` for hover docs on declarations, settings keys/values, and `UE.*` builtins.
- Added `DreamShaderParameterInfoHandler` via `codeInsight.parameterInfo` for signature help on `UE.*` builtins and common HLSL intrinsics.
- Added regression tests in `DreamShaderDocumentationProviderTest` and `DreamShaderSignatureHelpAnalyzerTest`.
- Added semantic token classification in `DreamShaderSemanticAnnotator` for declaration keywords/names, section names, callable references, `UE` namespace, namespace qualifiers (`Namespace::`), local symbol declaration/usage split, and `Base.*` material output members.
- Refactored semantic-token classification logic into `DreamShaderSemanticTokenClassifier` so `DreamShaderSemanticAnnotator` focuses on annotation emission + diagnostics pipeline aggregation.
- Reorganized `DreamShaderSemanticAnnotator` internals into grouped diagnostic sections (`bridge`, `syntax`, `section-shape`, `semantic`, `call/import utilities`) and grouped constant/regex blocks to improve maintainability without behavior changes.
- Extended section-shape diagnostics to declaration-aware schema constraints for `ShaderLayer` / `ShaderLayerBlend` / `Function` / `GraphFunction`, plus declaration-scoped `Results` alias handling (`ShaderFunction` / `VirtualFunction` only).
- Added semantic rules for `VirtualFunction` `Options.Asset`: required option entry + asset-path validation with root constraints (`Game`, `Engine`, `Plugin.<Name>`, `Plugins.<Name>`).
- Added `VirtualFunction` `Options/Settings` `Description` quality diagnostics (warning when missing, non-quoted, or empty string).
- Added quick-fix for missing `VirtualFunction` `Description` (`Add Description option`) to insert a default quoted description entry.
- Added quick-fix actions for `VirtualFunction` `Description` warnings:
  - `Quote Description value` for non-quoted descriptions.
  - `Fill Description with default text` for empty-string descriptions.
- Added semantic rule for asset declaration `Root` restrictions: `Shader`/`ShaderFunction`/`ShaderLayer`/`ShaderLayerBlend` now validate `Root` against `Game`, `Plugin.<Name>`, `Plugins.<Name>`.
- Added settings-key quick-fix action for semantic diagnostics:
  - `Replace with '<SuggestedKey>'` for unknown settings keys with typo suggestions.
- Added settings-value quick-fix actions for semantic diagnostics:
  - `Replace with true` for invalid `TwoSided` value.
  - `Replace with 0` for invalid `NumCustomizedUVs` value.
- Added semantic-suggestion quick-fix actions for diagnostic typos:
  - `Replace with 'Base.<SuggestedMember>'` for unknown `Base.*` output members.
  - `Replace with '<SuggestedType>'` for unknown typed declaration names.
- Extended unresolved import diagnostics with explicit extension guard: imports ending with unsupported extensions (non `.dsh/.dsf/.dsm`) now report a dedicated semantic error.
- Refactored unresolved import quick-fix scaffold generation to reuse `DreamShaderTemplateService` templates (`createHeaderTemplate` / `createFunctionTemplate` / `createMaterialTemplate`) for consistency with template actions.
- Completed `DreamShaderBundle_zh_CN.properties` coverage for newly added diagnostics and quick-fix messages (`VirtualFunction Description` quality diagnostics, settings/semantic suggestion quick fixes).
- Added localization parity regression test (`DreamShaderBundleLocalizationTest`) to ensure `zh_CN` bundle contains all base bundle keys.
- Added regression tests in `DreamShaderSemanticTokensTest` for `DSYM-001` and `DSYM-002`, including nested-call/member-like edge cases, array-index symbol usage, and multi-level namespace qualifier classification.
- Added `DreamShaderSemanticTokenClassifierTest` to validate classifier behavior directly and verify classifier/annotator consistency on representative samples.

### M3 Testable Navigation/Symbol Checklist

Rule format:
- `ID`: stable rule id for navigation/symbol features
- `Priority`: milestone priority
- `Expected`: expected behavior
- `Test`: suggested test name

#### M3 Audit Matrix (Checked on `2026-05-24`)

| ID | Status | Test mapping |
| --- | --- | --- |
| `DSYM-001` | `Implemented` | `DreamShaderSemanticTokensTest.testSemanticTokensForDeclarationAndSectionScopes()` |
| `DSYM-002` | `Implemented` | `DreamShaderSemanticTokensTest.testSemanticTokensInGraphAndFunctionBodies()` + subcases `testSemanticTokensForNestedCallsAndMemberLikeSyntax()` / `testSemanticTokensForArrayIndexAndMultiLevelNamespaceQualifier()` |
| `DSYM-003` | `Implemented` | `DreamShaderInlayParameterHintsProviderTest.testProducesHintsForUeAndIntrinsicCalls()` |
| `DSYM-004` | `Implemented` | `DreamShaderInlayParameterHintsProviderTest.testSkipsNamedArguments()` |

1. `ID`: `DSYM-001`  
`Priority`: `P2`  
`Rule`: semantic tokens classify declaration keywords, declaration names, section names, and code identifiers consistently.  
`Expected`: token categories are stable and do not oscillate after incremental edits  
`Test`: `testSemanticTokensForDeclarationAndSectionScopes()`

2. `ID`: `DSYM-002`  
`Priority`: `P2`  
`Rule`: semantic tokens inside `Graph` and function-like bodies classify known builtins/types/symbols predictably.  
`Expected`: `UE.*`, known types, and local symbols receive deterministic token classes  
`Test`: `testSemanticTokensInGraphAndFunctionBodies()`

`Subcases`:
- `testSemanticTokensForNestedCallsAndMemberLikeSyntax()`: nested call chains classify callable references; named call argument keys (`Index=...`) and swizzle/member suffixes (`.x`, `.xyz`) are not misclassified as local symbols.
- `testSemanticTokensForArrayIndexAndMultiLevelNamespaceQualifier()`: local symbols used in index expressions (`Values[idx]`) classify as symbol usages; `A::B::Blend(...)` classifies `A` and `B` as namespace qualifiers and `Blend` as callable reference.

3. `ID`: `DSYM-003`  
`Priority`: `P2`  
`Rule`: inlay hints show callable signature/output context where invocation is ambiguous.  
`Expected`: hint text reflects callable parameter/output ordering and updates when signature changes  
`Test`: `testProducesHintsForUeAndIntrinsicCalls()`

4. `ID`: `DSYM-004`  
`Priority`: `P2`  
`Rule`: inlay hints are suppressed in non-call contexts and do not duplicate existing editor cues.  
`Expected`: no duplicated hint rows for the same call site; hints disappear outside supported scopes  
`Test`: `testSkipsNamedArguments()`

### Milestone M4: Diagnostics and Formatting

- [x] `P1` Local parser diagnostics (unclosed braces/strings/comments, malformed declarations).
- [x] `P1` Section-shape diagnostics for `.dsf` file rules.
- [x] `P2` Semantic diagnostics for common mistakes (unknown setting keys/types/output fields).
- [x] `P1` Basic formatter (indentation, spacing, braces, section layout).
- [x] `P2` Formatting options aligned with Rider code style where possible.

Acceptance criteria:
- Errors are shown inline and in inspection results with actionable messages.
- Formatter provides stable output on repeated runs.

### M4 Testable Syntax Checklist

Rule format:
- `ID`: stable rule id for diagnostics + tests
- `Priority`: maps to milestone priority
- `Expected`: recommended diagnostic text
- `Test`: suggested test method name

#### M4 Audit Matrix (Checked on `2026-05-24`)

| ID | Status | Test mapping |
| --- | --- | --- |
| `DSYN-001` | `Implemented` | `DreamShaderSyntaxDiagnosticsTest.testUnclosedStringLiteral()` |
| `DSYN-002` | `Implemented` | `DreamShaderSyntaxDiagnosticsTest.testUnclosedBlockComment()` |
| `DSYN-003` | `Implemented` | `DreamShaderSyntaxDiagnosticsTest.testUnmatchedBraceInGraph()` |
| `DSYN-004` | `Implemented` | `DreamShaderSyntaxDiagnosticsTest.testMalformedTopLevelDeclaration()` |
| `DSYN-005` | `Implemented` | `DreamShaderSyntaxDiagnosticsTest.testMalformedSectionHeader()` |
| `DSYN-101` | `Implemented` | `DreamShaderSectionShapeDiagnosticsTest.testDsfDisallowsTopLevelShader()` / `testDsfDisallowsTopLevelNamespace()` |
| `DSYN-102` | `Implemented` | `DreamShaderSectionShapeDiagnosticsTest.testVirtualFunctionDisallowsGraphSection()` |
| `DSYN-103` | `Implemented` | `DreamShaderSectionShapeDiagnosticsTest.testNamespaceAllowsOnlyFunctionDeclarations()` |
| `DSYN-104` | `Implemented` | `DreamShaderSectionShapeDiagnosticsTest.testLayerRequiresSingleMaterialAttributesOutput()` |
| `DSYN-105` | `Implemented` | `DreamShaderSectionShapeDiagnosticsTest.testLayerBlendRequiresTwoMaterialAttributesInputs()` |
| `DSYN-106` | `Implemented` | `DreamShaderSectionShapeDiagnosticsTest.testDshDisallowsTopLevelShaderFunction()` |
| `DSYN-107` | `Implemented` | `DreamShaderSectionShapeDiagnosticsTest.testDsmDisallowsTopLevelShaderFunction()` |
| `DSYN-108` | `Implemented` | `DreamShaderSectionShapeDiagnosticsTest.testShaderDisallowsInputsSection()` / `testShaderRequiresGraphSection()` |
| `DSYN-109` | `Implemented` | `DreamShaderSectionShapeDiagnosticsTest.testShaderDuplicateSectionIsReported()` |
| `DSYN-201` | `Implemented` | `DreamShaderSemanticDiagnosticsTest.testUnknownSettingsKey()` + `testUnknownSettingsKeyQuickFixReplacesWithSuggestion()` |
| `DSYN-202` | `Implemented` | `DreamShaderSemanticDiagnosticsTest.testInvalidSettingsEnumValue()` + `testInvalidBooleanSettingsValue()` + `testValidBooleanSettingsValue()` + `testInvalidNumCustomizedUvsValue()` + `testValidNumCustomizedUvsValue()` + `testInvalidBooleanSettingsValueQuickFixReplacesWithTrue()` + `testInvalidNumCustomizedUvsValueQuickFixReplacesWithZero()` |
| `DSYN-203` | `Implemented` | `DreamShaderSemanticDiagnosticsTest.testUnknownBaseOutputMember()` + `testUnknownBaseOutputMemberQuickFixReplacesWithSuggestion()` |
| `DSYN-204` | `Implemented` | `DreamShaderSemanticDiagnosticsTest.testUnknownTypeInInputs()` + `testUnknownTypeQuickFixReplacesWithSuggestion()` |
| `DSYN-205` | `Implemented` | `DreamShaderSemanticDiagnosticsTest.testMissingOutArgumentInFunctionCall()` + `testMissingOutArgumentQuickFixAddsPlaceholderArgument()` + `testMissingOutArgumentQuickFixAddsFirstArgumentForEmptyCall()` + `testMissingOutArgumentQuickFixAddsAllMissingOutArguments()` + `testMissingOutArgumentQuickFixAvoidsNameCollision()` + `testMissingOutArgumentQuickFixUsesConfiguredPlaceholderSuffix()` |
| `DSYN-206` | `Implemented` | `DreamShaderSemanticDiagnosticsTest.testUnresolvedImportPath()` + `testUnresolvedImportPathQuickFixCreatesMissingFile()` + `testUnresolvedImportPathQuickFixCreatesFunctionTemplateForDsf()` + `testUnresolvedImportPathQuickFixCreatesShaderTemplateForDsm()` + `testUnresolvedScopedImportQuickFixCreatesFileUnderPackages()` + `testUnresolvedImportPathUnsupportedExtensionReportsExplicitError()` + `testUnresolvedImportPathUnsupportedExtensionQuickFixChangesToDsh()` |
| `DSYN-207` | `Implemented` | `DreamShaderSemanticDiagnosticsTest.testGraphDisallowsForLoopStatement()` / `testGraphDisallowsWhileLoopStatement()` / `testGraphDisallowsDoLoopStatement()` |
| `DSYN-208` | `Implemented` | `DreamShaderSemanticDiagnosticsTest.testGraphDisallowsSwitchStatement()` / `testGraphDisallowsCaseKeyword()` / `testGraphDisallowsDefaultKeyword()` |
| `DSYN-209` | `Implemented` | `DreamShaderSemanticDiagnosticsTest.testGraphDisallowsBreakStatement()` / `testGraphDisallowsContinueStatement()` |
| `DSYN-210` | `Implemented` | `DreamShaderSemanticDiagnosticsTest.testGraphDisallowsReturnStatement()` |
| `DSYN-211` | `Implemented` | `DreamShaderSemanticDiagnosticsTest.testVirtualFunctionOptionAssetRequiresPath()` + `testVirtualFunctionOptionAssetRejectsBareIdentifier()` + `testVirtualFunctionOptionAssetAcceptsPathCall()` + `testVirtualFunctionOptionAssetAcceptsQuotedObjectPath()` + `testVirtualFunctionOptionAssetAcceptsEngineRootPathCall()` + `testVirtualFunctionOptionAssetRejectsUnknownPathRoot()` + `testVirtualFunctionOptionAssetUnknownRootQuickFixReplacesWithGame()` + `testVirtualFunctionOptionAssetRequiresOptionEntry()` |
| `DSYN-212` | `Implemented` | `DreamShaderSemanticDiagnosticsTest.testShaderRootRejectsEngineRoot()` + `testShaderRootAcceptsPluginRoot()` |
| `DSYN-213` | `Implemented` | `DreamShaderSemanticDiagnosticsTest.testVirtualFunctionOptionDescriptionWarnsWhenNotQuoted()` + `testVirtualFunctionOptionDescriptionNotQuotedQuickFixAddsQuotes()` + `testVirtualFunctionOptionDescriptionWarnsWhenEmpty()` + `testVirtualFunctionOptionDescriptionEmptyQuickFixFillsDefault()` + `testVirtualFunctionOptionDescriptionSettingsAliasAcceptsQuotedText()` + `testVirtualFunctionOptionDescriptionRecommendedWhenMissing()` + `testVirtualFunctionOptionDescriptionRecommendedQuickFixAddsDescription()` |

#### A. Local Parser Diagnostics (`P1`)

1. `ID`: `DSYN-001`  
`Priority`: `P1`  
`Rule`: unclosed string literal must report error.  
`Invalid`:
```c
Shader Main {
    Settings {
        Domain = "Surface
    }
}
```
`Expected`: `Unclosed string literal`  
`Test`: `testUnclosedStringLiteral()`

2. `ID`: `DSYN-002`  
`Priority`: `P1`  
`Rule`: unclosed block comment must report error.  
`Invalid`:
```c
Shader Main {
    /* comment
    Graph = { }
}
```
`Expected`: `Unclosed block comment`  
`Test`: `testUnclosedBlockComment()`

3. `ID`: `DSYN-003`  
`Priority`: `P1`  
`Rule`: unmatched `{` / `}` in declaration or section body must report error.  
`Invalid`:
```c
Shader Main {
    Graph = {
        float x = 1.0;
}
```
`Expected`: `Unmatched brace`  
`Test`: `testUnmatchedBraceInGraph()`

4. `ID`: `DSYN-004`  
`Priority`: `P1`  
`Rule`: malformed top-level declaration head must report error (keyword present but missing required declaration identity/body pattern).  
`Invalid`:
```c
Shader {
    Graph = { }
}
```
`Expected`: `Malformed declaration: expected declaration name or argument list`  
`Test`: `testMalformedTopLevelDeclaration()`

5. `ID`: `DSYN-005`  
`Priority`: `P1`  
`Rule`: section token without valid body/terminator must report error.  
`Invalid`:
```c
Shader Main {
    Outputs
    Graph = { }
}
```
`Expected`: `Malformed section: expected '{'`  
`Test`: `testMalformedSectionHeader()`

#### B. Section Shape and File-Type Rules (`P1`)

6. `ID`: `DSYN-101`  
`Priority`: `P1`  
`Rule`: `.dsf` top-level declarations are restricted to `ShaderFunction`, `ShaderLayer`, `ShaderLayerBlend`, `VirtualFunction`, `Function`, `GraphFunction`.  
`Invalid` (`foo.dsf`):
```c
Shader(Name="M_Invalid") {
    Graph = { }
}
```
`Expected`: `Top-level Shader declaration is not allowed in .dsf files`  
`Test`: `testDsfDisallowsTopLevelShader()`

Additional invalid example:
```c
Namespace Tools {
    Function Helper(in float X, out float Y) { Y = X; }
}
```
`Expected`: `Top-level Namespace declaration is not allowed in .dsf files`  
`Test`: `testDsfDisallowsTopLevelNamespace()`

7. `ID`: `DSYN-102`  
`Priority`: `P1`  
`Rule`: `VirtualFunction` must not contain `Graph` (or `Code`) section.  
`Invalid`:
```c
VirtualFunction(Name="BufferWriter") {
    Graph = { }
}
```
`Expected`: `VirtualFunction does not support Graph/Code sections`  
`Test`: `testVirtualFunctionDisallowsGraphSection()`

8. `ID`: `DSYN-103`  
`Priority`: `P1`  
`Rule`: `Namespace` body may only contain `Function` or `GraphFunction`.  
`Invalid`:
```c
Namespace(Name="Bad") {
    ShaderFunction(Name="X") { }
}
```
`Expected`: `Namespace can only contain Function or GraphFunction declarations`  
`Test`: `testNamespaceAllowsOnlyFunctionDeclarations()`

9. `ID`: `DSYN-104`  
`Priority`: `P1`  
`Rule`: `ShaderLayer` / `ShaderLayerBlend` must declare exactly one `MaterialAttributes` output.  
`Invalid`:
```c
ShaderLayer(Name="LayerA") {
    Outputs = {
        float3 Color;
        MaterialAttributes Attr;
    }
}
```
`Expected`: `ShaderLayer/ShaderLayerBlend must declare exactly one MaterialAttributes output`  
`Test`: `testLayerRequiresSingleMaterialAttributesOutput()`

10. `ID`: `DSYN-105`  
`Priority`: `P1`  
`Rule`: `ShaderLayerBlend` must declare at least two `MaterialAttributes` inputs.  
`Invalid`:
```c
ShaderLayerBlend(Name="BlendA") {
    Inputs = {
        MaterialAttributes A;
    }
    Outputs = {
        MaterialAttributes Out;
    }
}
```
`Expected`: `ShaderLayerBlend requires at least two MaterialAttributes inputs`  
`Test`: `testLayerBlendRequiresTwoMaterialAttributesInputs()`

11. `ID`: `DSYN-106`  
`Priority`: `P1`  
`Rule`: `.dsh` must not declare top-level asset-generating declarations (`Shader`, `ShaderFunction`, `ShaderLayer`, `ShaderLayerBlend`).  
`Invalid` (`foo.dsh`):
```c
ShaderFunction(Name="F_Invalid") {
    Outputs = {
        float Out = 0.0;
    }
}
```
`Expected`: `Top-level ShaderFunction declaration is not allowed in .dsh files`  
`Test`: `testDshDisallowsTopLevelShaderFunction()`

12. `ID`: `DSYN-107`  
`Priority`: `P1`  
`Rule`: `.dsm` must not declare top-level `ShaderFunction(...)`, `ShaderLayer(...)`, or `ShaderLayerBlend(...)`.  
`Invalid` (`foo.dsm`):
```c
ShaderFunction(Name="F_Invalid") {
    Outputs = {
        float Out = 0.0;
    }
}
```
`Expected`: `Top-level ShaderFunction declaration is not allowed in .dsm files`  
`Test`: `testDsmDisallowsTopLevelShaderFunction()`

13. `ID`: `DSYN-108`  
`Priority`: `P1`  
`Rule`: declaration section schema must reject unsupported sections and require key sections for supported declaration kinds.  
`Invalid`:
```c
Shader Main {
    Inputs = {
        float UV;
    }
    Settings = {
        Domain = "Surface";
    }
}
```
`Expected`:
- `Section 'Inputs' is not allowed in Shader declarations`
- `Shader declaration requires Graph section`  
`Test`: `testShaderDisallowsInputsSection()` / `testShaderRequiresGraphSection()`

`Current coverage additions`:
- `Results` is accepted as an `Outputs` compatibility alias for `ShaderFunction` and `VirtualFunction`.
- `Results` remains invalid for declarations without alias support (for example `ShaderLayerBlend`).
- `Function` / `GraphFunction` declaration bodies currently use non-section body grammar in this plugin and reject section blocks by schema diagnostics.

14. `ID`: `DSYN-109`  
`Priority`: `P1`  
`Rule`: duplicate sections in one declaration must report diagnostics.  
`Invalid`:
```c
Shader Main {
    Graph = { }
    Graph = { }
}
```
`Expected`: `Duplicate section 'Graph' in Shader declaration`  
`Test`: `testShaderDuplicateSectionIsReported()`

25. `ID`: `DSYN-211`  
`Priority`: `P2`  
`Rule`: `VirtualFunction` `Options.Asset` must use asset-path form (`Path(...)` or quoted object path).  
`Rule+`: `Asset` is required when `Options` section exists; path root must be one of `Game`, `Engine`, `Plugin.<Name>`, `Plugins.<Name>`.  
`Invalid`:
```c
VirtualFunction(Name="VF_InvalidAsset") {
    Options = {
        Asset = Path(Project, Materials/M_VFAsset);
    }
}
```
`Expected`:
- `VirtualFunction Options.Asset must be an asset path (quoted object path or Path(...))`
- `VirtualFunction Options.Asset path root 'Project' is not allowed. Use Game, Engine, Plugin.<Name>, or Plugins.<Name>`
- `VirtualFunction requires Asset option in Options (Settings alias is also accepted)`  
`QuickFix`: `Replace Path root with Game` rewrites `Path(<UnknownRoot>, ...)` to `Path(Game, ...)`  
`Test`: `testVirtualFunctionOptionAssetRequiresPath()` / `testVirtualFunctionOptionAssetRejectsBareIdentifier()` / `testVirtualFunctionOptionAssetAcceptsPathCall()` / `testVirtualFunctionOptionAssetAcceptsQuotedObjectPath()` / `testVirtualFunctionOptionAssetAcceptsEngineRootPathCall()` / `testVirtualFunctionOptionAssetRejectsUnknownPathRoot()` / `testVirtualFunctionOptionAssetUnknownRootQuickFixReplacesWithGame()` / `testVirtualFunctionOptionAssetRequiresOptionEntry()`

26. `ID`: `DSYN-212`  
`Priority`: `P2`  
`Rule`: asset declarations (`Shader` / `ShaderFunction` / `ShaderLayer` / `ShaderLayerBlend`) must reject unsupported `Root` values.  
`Invalid`:
```c
Shader Root = "Engine", Name="Materials/M_InvalidRoot" {
    Graph = { }
}
```
`Expected`: `Shader Root value 'Engine' is not allowed. Use Game, Plugin.<Name>, or Plugins.<Name>`  
`Test`: `testShaderRootRejectsEngineRoot()` / `testShaderRootAcceptsPluginRoot()`

27. `ID`: `DSYN-213`  
`Priority`: `P2`  
`Rule`: `VirtualFunction` `Options.Description` (including `Settings` alias) is recommended and should be a non-empty quoted string.  
`Invalid`:
```c
VirtualFunction(Name="VF_InvalidDescription") {
    Options = {
        Description = BridgeCompatible;
    }
}
```
`Expected`: `VirtualFunction Options.Description should be a quoted string literal`  
`Additional invalid`:
```c
VirtualFunction(Name="VF_EmptyDescription") {
    Settings = {
        Description = "";
    }
}
```
`Expected`: `VirtualFunction Options.Description should not be empty`  
`Additional recommended warning`:
```c
VirtualFunction(Name="VF_NoDescription") {
    Options = {
        Asset = Path(Game, Materials/M_VFAsset);
    }
}
```
`Expected`: `VirtualFunction should provide Options.Description (Settings alias is also accepted)`  
`QuickFix`:
- `Quote Description value` wraps non-quoted values in quotes
- `Fill Description with default text` replaces empty-string with default description text
- `Add Description option` inserts `Description = "Bridge-compatible virtual function";` when missing  
`Test`: `testVirtualFunctionOptionDescriptionWarnsWhenNotQuoted()` / `testVirtualFunctionOptionDescriptionNotQuotedQuickFixAddsQuotes()` / `testVirtualFunctionOptionDescriptionWarnsWhenEmpty()` / `testVirtualFunctionOptionDescriptionEmptyQuickFixFillsDefault()` / `testVirtualFunctionOptionDescriptionSettingsAliasAcceptsQuotedText()` / `testVirtualFunctionOptionDescriptionRecommendedWhenMissing()` / `testVirtualFunctionOptionDescriptionRecommendedQuickFixAddsDescription()`

#### C. Semantic Diagnostics (`P2`)

15. `ID`: `DSYN-201`  
`Priority`: `P2`  
`Rule`: unknown `Settings` key should report warning/error with suggestion.  
`Invalid`:
```c
Shader Main {
    Settings = {
        DomainX = "Surface";
    }
}
```
`Expected`: `Unknown settings key 'DomainX'. Did you mean 'Domain'?`  
`QuickFix`: `Replace with 'Domain'`  
`Test`: `testUnknownSettingsKey()` / `testUnknownSettingsKeyQuickFixReplacesWithSuggestion()`

16. `ID`: `DSYN-202`  
`Priority`: `P2`  
`Rule`: invalid `Settings` value should report diagnostic (enum-like + scalar bool/int validation).  
`Invalid`:
```c
Shader Main {
    Settings = {
        BlendMode = "OpaqueX";
    }
}
```
`Additional invalid examples`:
```c
Shader Main {
    Settings = {
        TwoSided = "enabled";
        NumCustomizedUVs = 9;
    }
}
```
`Expected`:
- `Invalid value 'OpaqueX' for setting 'BlendMode'`
- `Invalid value 'enabled' for setting 'TwoSided'`
- `Invalid value '9' for setting 'NumCustomizedUVs'`  
`QuickFix`:
- `Replace with true` for invalid `TwoSided` values
- `Replace with 0` for invalid `NumCustomizedUVs` values  
`Test`: `testInvalidSettingsEnumValue()` / `testInvalidBooleanSettingsValue()` / `testValidBooleanSettingsValue()` / `testInvalidNumCustomizedUvsValue()` / `testValidNumCustomizedUvsValue()` / `testInvalidBooleanSettingsValueQuickFixReplacesWithTrue()` / `testInvalidNumCustomizedUvsValueQuickFixReplacesWithZero()`

17. `ID`: `DSYN-203`  
`Priority`: `P2`  
`Rule`: unknown `Base.*` output member in `Outputs`/`Graph` should report diagnostic.  
`Invalid`:
```c
Shader Main {
    Outputs = {
        Base.ColorX = float3(1.0, 1.0, 1.0);
    }
}
```
`Expected`: `Unknown material output member 'Base.ColorX'. Did you mean 'Base.BaseColor'?`  
`QuickFix`: `Replace with 'Base.BaseColor'`  
`Test`: `testUnknownBaseOutputMember()` / `testUnknownBaseOutputMemberQuickFixReplacesWithSuggestion()`

18. `ID`: `DSYN-204`  
`Priority`: `P2`  
`Rule`: unknown type token in typed declaration context should report diagnostic.  
`Invalid`:
```c
Shader Main {
    Inputs = {
        float9 BadType;
    }
}
```
`Expected`: `Unknown type 'float9'. Did you mean 'float'?`  
`QuickFix`: `Replace with 'float'`  
`Test`: `testUnknownTypeInInputs()` / `testUnknownTypeQuickFixReplacesWithSuggestion()`

19. `ID`: `DSYN-205`  
`Priority`: `P2`  
`Rule`: calling a `Function`/multi-output `GraphFunction` without required `out` target should report diagnostic.  
`Invalid`:
```c
Function ApplyTint(in vec3 color, in vec3 tint, out vec3 result) {
    result = color * tint;
}

Shader Main {
    Graph = {
        ApplyTint(float3(1,1,1), float3(1,0,0));
    }
}
```
`Expected`: `Missing out argument for parameter 'result'`  
`QuickFix`: `Add missing out arguments` inserts suggested placeholder targets for all missing out params at call site (appends `, <param>Out` list or fills empty arg list with `<param>Out` list), auto-avoids identifier collisions by incrementing suffixes (`<param>Out2`, ...), and uses configurable suffix from settings (`Out Placeholder Suffix`).  
`Test`: `testMissingOutArgumentInFunctionCall()` / `testMissingOutArgumentQuickFixAddsPlaceholderArgument()` / `testMissingOutArgumentQuickFixAddsFirstArgumentForEmptyCall()` / `testMissingOutArgumentQuickFixAddsAllMissingOutArguments()` / `testMissingOutArgumentQuickFixAvoidsNameCollision()` / `testMissingOutArgumentQuickFixUsesConfiguredPlaceholderSuffix()`

20. `ID`: `DSYN-206`  
`Priority`: `P2`  
`Rule`: unresolved `import` path should report diagnostic on string literal.  
`Invalid`:
```c
import "NotFound/Nope.dsh";
```
`Expected`: `Cannot resolve import 'NotFound/Nope.dsh'`  
`Additional invalid`:
```c
import "Scripts/Auto.usf";
```
`Expected`: `Unsupported import file extension .usf. Only .dsh, .dsf, and .dsm are supported.`  
`QuickFix`: `Create missing import file: <path>` creates a missing file under project-safe relative path, defaults to `.dsh` when extension is omitted, uses `DreamShaderTemplateService` templates for generated content (`.dsh` header template, `.dsf` function template, `.dsm` material template), opens the created file in editor, and supports scoped package imports by creating under `DShader/Packages/@scope/name/...`; unsupported extensions also provide `Change extension to .dsh`.  
`Test`: `testUnresolvedImportPath()` / `testUnresolvedImportPathQuickFixCreatesMissingFile()` / `testUnresolvedImportPathQuickFixCreatesFunctionTemplateForDsf()` / `testUnresolvedImportPathQuickFixCreatesShaderTemplateForDsm()` / `testUnresolvedScopedImportQuickFixCreatesFileUnderPackages()` / `testUnresolvedImportPathUnsupportedExtensionReportsExplicitError()` / `testUnresolvedImportPathUnsupportedExtensionQuickFixChangesToDsh()`

21. `ID`: `DSYN-207`  
`Priority`: `P2`  
`Rule`: Graph section must reject loop statements `for` / `while` / `do`.  
`Expected`: `Graph section does not support loop statement '<keyword>'`  
`Test`: `testGraphDisallowsForLoopStatement()` / `testGraphDisallowsWhileLoopStatement()` / `testGraphDisallowsDoLoopStatement()`

22. `ID`: `DSYN-208`  
`Priority`: `P2`  
`Rule`: Graph section must reject `switch` family tokens `switch` / `case` / `default`.  
`Expected`: `Graph section does not support switch statement '<keyword>'`  
`Test`: `testGraphDisallowsSwitchStatement()` / `testGraphDisallowsCaseKeyword()` / `testGraphDisallowsDefaultKeyword()`

23. `ID`: `DSYN-209`  
`Priority`: `P2`  
`Rule`: Graph section must reject `break` and `continue` control statements.  
`Expected`: `Graph section does not support control statement '<keyword>'`  
`Test`: `testGraphDisallowsBreakStatement()` / `testGraphDisallowsContinueStatement()`

24. `ID`: `DSYN-210`  
`Priority`: `P2`  
`Rule`: Graph section must reject `return` statement.  
`Expected`: `Graph section does not support return statement`  
`Test`: `testGraphDisallowsReturnStatement()`

#### D. Test Harness Mapping

Current test files:
- `src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/diagnostics/DreamShaderSyntaxDiagnosticsTest.kt` for `DSYN-001` to `DSYN-005`.
- `src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/diagnostics/DreamShaderSectionShapeDiagnosticsTest.kt` for `DSYN-101` to `DSYN-109` plus declaration-schema coverage (`Results` alias scope and function/graphfunction section constraints).
- `src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/diagnostics/DreamShaderSemanticDiagnosticsTest.kt` for `DSYN-201` to `DSYN-210` plus `DSYN-211`/`DSYN-212`/`DSYN-213` (`VirtualFunction Options.Asset` required + asset-path root validation + unknown-root quick-fix, asset declaration `Root` validation, `VirtualFunction` description quality/recommended warnings + quick-fix actions), with extended semantic quick-fix coverage:
  - `DSYN-201` settings-key suggestion replacement (`Replace with '<SuggestedKey>'`)
  - `DSYN-202` scalar settings validation (`TwoSided` bool, `NumCustomizedUVs` range) + value quick-fix actions (`Replace with true`, `Replace with 0`)
  - `DSYN-203` base output suggestion replacement (`Replace with 'Base.<SuggestedMember>'`)
  - `DSYN-204` type suggestion replacement (`Replace with '<SuggestedType>'`)
  - `DSYN-205` missing out-argument insertion (`Add missing out arguments`, including name-collision avoidance and configurable `Out Placeholder Suffix`)
  - `DSYN-206` unresolved import scaffold creation (`Create missing import file: <path>`, default `.dsh` extension when omitted, template content sourced from `DreamShaderTemplateService`, open created file, scoped imports routed to `DShader/Packages/@scope/name/...`) + unsupported-extension semantic guard (`.dsh/.dsf/.dsm` only) + extension quick-fix (`Change extension to .dsh`)

Recommendation:
- Use the `M4 Audit Matrix` as the source of truth for release checks and regression triage.

### Milestone M5: Bridge Integration and Package Tooling

- [x] `P2` Detect project root and bridge directory resolution.
- [x] `P2` Read Unreal bridge diagnostic files and project manifests.
- [x] `P2` Tool window/panel for bridge diagnostics.
- [x] `P2` Actions: recompile current/all, clean generated shaders, refresh diagnostics, open diagnostic location.
- [x] `P2` Bridge-manifest-aware `UE.Expression(...)` completion (`materialExpressionManifestPath` + project bridge manifest + bundled fallback manifest).
- [x] `P2` Settings parity for bridge/tooling (`projectRoot`, `materialExpressionManifestPath`, `showStatusBar`, `enableCodeLens`).
- [x] `P2` Status bar + CodeLens actions parity for DreamShader workflows (official IntelliJ daemon-bound Code Vision provider + inlay hints toggle parity).
- [x] `P2` Package store index support (multiple sources + deprecated single source compatibility).
- [x] `P2` Actions: install/update/remove/browse/open packages folder.
- [x] `P3` Add/remove package store index source commands and source-management UX parity.
- [x] `P3` Authoring/template commands parity (create package/material/function/header/sample files).
- [x] `P3` Optional GitHub package search integration.

Acceptance criteria:
- Bridge diagnostics appear in Rider and map back to source locations.
- Package lifecycle actions complete successfully and report clear errors.
- Bridge actions are discoverable and usable from Rider UI actions without requiring manual file-system steps.
- Bridge/manifest-related settings produce deterministic behavior with clear fallback order.
- Template/scaffold commands produce valid starter files that pass local parse/completion baseline.

### M5 Testable Bridge and Tooling Checklist

Rule format:
- `ID`: stable rule id for bridge/tooling behaviors
- `Priority`: milestone priority
- `Expected`: expected outcome or error text
- `Test`: suggested test name

#### M5 Bridge/Tooling Audit Matrix (Checked on `2026-05-24`)

| ID | Status | Test mapping |
| --- | --- | --- |
| `DBRG-001` | `Implemented` | `DreamShaderBridgePathResolverTest.testAutoDetectsProjectRootFromActiveFile()` |
| `DBRG-002` | `Implemented` | `DreamShaderBridgePathResolverTest.testProjectRootSettingOverridesAutoDetect()` |
| `DBRG-003` | `Implemented` | `DreamShaderBridgeDiagnosticsTest.testBridgeDiagnosticNavigationToExactLocation()` |
| `DBRG-004` | `Implemented` | `DreamShaderBridgeDiagnosticsTest.testRefreshBridgeDiagnosticsSyncsPanelAndEditor()` |
| `DBRG-005` | `Implemented` | `DreamShaderBridgeActionsTest.testBridgeActionCommandSetAvailability()` and `testBridgeActionCommandSetReturnsExpectedSuccessAndErrorMessages()` |
| `DBRG-101` | `Implemented` | `DreamShaderManifestCompletionTest.testUeExpressionClassCompletionWithManifestAndFallback()` + `DreamShaderSemanticDiagnosticsTest` (`testUnknownExpressionClassInUeExpressionCall`, `testUeExpressionClassRequired`, `testUeExpressionOutputTypeRequired`) |
| `DBRG-102` | `Implemented` | `DreamShaderManifestCompletionTest.testUsesConfiguredMaterialExpressionManifestPath()` |
| `DBRG-103` | `Implemented` | `DreamShaderManifestCompletionTest.testInvalidManifestPathFallsBackGracefully()` |
| `DBRG-104` | `Implemented` | `DreamShaderStatusBarVisibilityTest.testStatusBarVisibilitySetting()` |
| `DBRG-105` | `Implemented` | `DreamShaderSettingsToggleTest.testEnableCodeLensToggleControlsInlayHintsProviderOutput()` |
| `DTPL-001` | `Implemented` | `DreamShaderTemplateCommandsTest.testCreateMaterialTemplateProducesValidDsm()` |
| `DTPL-002` | `Implemented` | `DreamShaderTemplateCommandsTest.testCreateFunctionFileTemplateProducesValidDsf()` |
| `DTPL-003` | `Implemented` | `DreamShaderTemplateCommandsTest.testCreateHeaderTemplateProducesValidDsh()` |
| `DTPL-004` | `Implemented` | `DreamShaderTemplateCommandsTest.testCreatePackageScaffoldLayout()` |

#### A. Project Root / Bridge Diagnostics

1. `ID`: `DBRG-001`  
`Priority`: `P2`  
`Rule`: project root is auto-detected from active DreamShader file path when setting is empty.  
`Expected`: bridge directory resolves to `<project>/Saved/DreamShader/Bridge`  
`Test`: `testAutoDetectsProjectRootFromActiveFile()`

2. `ID`: `DBRG-002`  
`Priority`: `P2`  
`Rule`: explicit `projectRoot` setting overrides auto-detection.  
`Expected`: bridge read path follows configured root even when active file is elsewhere  
`Test`: `testProjectRootSettingOverridesAutoDetect()`

3. `ID`: `DBRG-003`  
`Priority`: `P2`  
`Rule`: bridge diagnostics file entries map to exact source file/line/column in editor navigation.  
`Expected`: open-diagnostic action lands on precise location in source file  
`Test`: `testBridgeDiagnosticNavigationToExactLocation()`

4. `ID`: `DBRG-004`  
`Priority`: `P2`  
`Rule`: refresh bridge diagnostics updates panel tree and inline diagnostics in one pass.  
`Expected`: counts/items in panel and editor diagnostics stay consistent after refresh  
`Test`: `testRefreshBridgeDiagnosticsSyncsPanelAndEditor()`

5. `ID`: `DBRG-005`  
`Priority`: `P2`  
`Rule`: bridge action set supports recompile current/all, clean generated shaders, refresh diagnostics, open location.  
`Expected`: each action is invokable and returns explicit success/error feedback  
`Test`: `testBridgeActionCommandSetAvailability()`

#### B. Manifest-Aware Completion and Settings

6. `ID`: `DBRG-101`  
`Priority`: `P2`  
`Rule`: `UE.Expression(Class="...")` completion and semantic validation use bridge-manifest classes and bundled fallback entries.  
`Expected`: reflected classes appear when manifest exists; baseline completions remain available without manifest; missing/invalid `Class` and missing/invalid `OutputType` surface actionable diagnostics and quick-fix suggestions  
`Test`: `testUeExpressionClassCompletionWithManifestAndFallback()`, plus semantic tests in `DreamShaderSemanticDiagnosticsTest`

7. `ID`: `DBRG-102`  
`Priority`: `P2`  
`Rule`: `materialExpressionManifestPath` setting uses explicit file path when valid.  
`Expected`: completion source switches to configured manifest path  
`Test`: `testUsesConfiguredMaterialExpressionManifestPath()`

8. `ID`: `DBRG-103`  
`Priority`: `P2`  
`Rule`: invalid explicit manifest path degrades gracefully to project/bundled sources.  
`Expected`: no crash; completion remains functional with fallback data  
`Test`: `testInvalidManifestPathFallsBackGracefully()`

9. `ID`: `DBRG-104`  
`Priority`: `P2`  
`Rule`: `showStatusBar` toggles DreamShader status UI element for active DreamShader documents.  
`Expected`: status entry appears/disappears immediately after setting change  
`Test`: `testStatusBarVisibilitySetting()`

10. `ID`: `DBRG-105`  
`Priority`: `P2`  
`Rule`: `enableCodeLens` currently toggles DreamShader inlay-parameter-hints output (CodeLens-like UX), not IntelliJ Code Vision actions.  
`Expected`: inlay hints appear/disappear without restart when setting changes  
`Test`: `testEnableCodeLensToggleControlsInlayHintsProviderOutput()`

#### C. Authoring Template / Scaffold Commands

11. `ID`: `DTPL-001`  
`Priority`: `P3`  
`Rule`: create-material command generates syntactically valid `.dsm` starter with canonical sections.  
`Expected`: generated file parses and offers standard completion contexts immediately  
`Test`: `testCreateMaterialTemplateProducesValidDsm()`

12. `ID`: `DTPL-002`  
`Priority`: `P3`  
`Rule`: create-function-file command generates valid `.dsf` starter with supported top-level declaration.  
`Expected`: generated file passes file-shape baseline for `.dsf`  
`Test`: `testCreateFunctionFileTemplateProducesValidDsf()`

13. `ID`: `DTPL-003`  
`Priority`: `P3`  
`Rule`: create-header command generates valid `.dsh` starter constrained to header-allowed declaration forms.  
`Expected`: generated file passes header file-shape baseline  
`Test`: `testCreateHeaderTemplateProducesValidDsh()`

14. `ID`: `DTPL-004`  
`Priority`: `P3`  
`Rule`: package scaffold command generates `dreamshader.package.json` plus recommended folder layout.  
`Expected`: scaffold output includes metadata file and `Library/` baseline structure  
`Test`: `testCreatePackageScaffoldLayout()`

### M5 Testable Package Checklist

Rule format:
- `ID`: stable rule id for package tooling/tests
- `Priority`: milestone priority
- `Expected`: expected outcome or error text
- `Test`: suggested test name

#### M5 Package Audit Matrix (Checked on `2026-05-24`)

| ID | Status | Test mapping |
| --- | --- | --- |
| `DPKG-001` | `Implemented` | `DreamShaderPackageIndexTest.testLoadsAndMergesMultipleIndexSources()` |
| `DPKG-002` | `Implemented` | `DreamShaderPackageIndexTest.testLegacySingleSourceIndexCompatibility()` |
| `DPKG-003` | `Implemented` | `DreamShaderPackageIndexTest.testParsesArrayAndObjectIndexShapes()` |
| `DPKG-004` | `Implemented` | `DreamShaderPackageIndexTest.testMalformedIndexDoesNotBreakOtherSources()` |
| `DPKG-005` | `Implemented` | `DreamShaderPackageIndexTest.testResolvesLocalPathRelativeToIndexFile()` |
| `DPKG-006` | `Implemented` | `DreamShaderPackageIndexTest.testFallsBackToRepositoryWhenLocalPathMissing()` |
| `DPKG-101` | `Implemented` | `DreamShaderPackageLifecycleTest.testInstallFromGithubShorthand()` |
| `DPKG-102` | `Implemented` | `DreamShaderPackageLifecycleTest.testInstallFromGithubUrl()` |
| `DPKG-103` | `Implemented` | `DreamShaderPackageLifecycleTest.testInstallFailsWhenMetadataMissing()` |
| `DPKG-104` | `Implemented` | `DreamShaderPackageLifecycleTest.testInstallFailsWhenNameMissing()` |
| `DPKG-105` | `Implemented` | `DreamShaderPackageLifecycleTest.testInstallWritesLockFileEntry()` |
| `DPKG-106` | `Implemented` | `DreamShaderPackageLifecycleTest.testUpdateRefreshesLockFileEntry()` |
| `DPKG-107` | `Implemented` | `DreamShaderPackageLifecycleTest.testRemovePackageAndPruneLockEntry()` |
| `DPKG-108` | `Implemented` | `DreamShaderPackageLifecycleTest.testRemoveNonInstalledPackageFailsClearly()` |
| `DPKG-109` | `Implemented` | `DreamShaderPackageLifecycleTest.testOpenPackagesFolderCreatesWhenMissing()` |
| `DPKG-110` | `Implemented` | `DreamShaderPackageLifecycleTest.testInstallFailsWhenGitUnavailable()` |
| `DPKG-201` | `Implemented` | `DreamShaderPackageImportResolutionTest.testResolvesScopedImportWithExtension()` |
| `DPKG-202` | `Implemented` | `DreamShaderPackageImportResolutionTest.testResolvesScopedImportWithoutExtension()` |
| `DPKG-203` | `Implemented` | `DreamShaderPackageImportResolutionTest.testImportResolutionPrecedenceForPackagePaths()` |
| `DPKG-301` | `Implemented` | `DreamShaderPackageStoreUiModelTest.testAddIndexSourceDeduplicates()` |
| `DPKG-302` | `Implemented` | `DreamShaderPackageStoreUiModelTest.testRemoveIndexSourceRefreshesStoreData()` |
| `DPKG-303` | `Implemented` | `DreamShaderPackageStoreUiModelTest.testStoreSearchMatchesNameDescriptionAndTags()` |
| `DPKG-401` | `Implemented` | `DreamShaderPackageBridgeInteropTest.testBridgeDiagnosticsMapToInstalledPackageSource()` |

#### A. Package Index Source and Parsing

1. `ID`: `DPKG-001`  
`Priority`: `P2`  
`Rule`: multiple index sources from `packageStoreIndexUrls` are loaded and merged.  
`Input`:
- Source A contains `@scope/a`
- Source B contains `@scope/b`
`Expected`: store list contains both packages (`@scope/a`, `@scope/b`) without dropping either source  
`Test`: `testLoadsAndMergesMultipleIndexSources()`

2. `ID`: `DPKG-002`  
`Priority`: `P2`  
`Rule`: legacy single source `packageStoreIndexUrl` remains compatible when multi-source is absent.  
`Input`:
- `packageStoreIndexUrls` unset
- `packageStoreIndexUrl` set to valid JSON index
`Expected`: index loads successfully from legacy setting  
`Test`: `testLegacySingleSourceIndexCompatibility()`

3. `ID`: `DPKG-003`  
`Priority`: `P2`  
`Rule`: both supported JSON shapes parse correctly (`[]` and `{ "packages": [] }`).  
`Input`:
- Index file A: array shape
- Index file B: object-with-packages shape
`Expected`: both produce equivalent package entries  
`Test`: `testParsesArrayAndObjectIndexShapes()`

4. `ID`: `DPKG-004`  
`Priority`: `P2`  
`Rule`: malformed index payload reports actionable source-specific error and does not crash loader.  
`Invalid`:
```json
{ "packages": "broken" }
```
`Expected`: `Failed to parse package index: <source>` and other valid sources still load  
`Test`: `testMalformedIndexDoesNotBreakOtherSources()`

5. `ID`: `DPKG-005`  
`Priority`: `P2`  
`Rule`: local index `path` resolves relative to index file directory.  
`Input`:
- Local index at `X:/idx/packages.json`
- Entry `path: "../repos/dream-noise"`
`Expected`: resolved absolute path points to `X:/repos/dream-noise`  
`Test`: `testResolvesLocalPathRelativeToIndexFile()`

6. `ID`: `DPKG-006`  
`Priority`: `P2`  
`Rule`: when local `path` does not exist, installer falls back to `repository`.  
`Input`:
- Missing local `path`
- Valid `repository`
`Expected`: install source selected from `repository` with warning log  
`Test`: `testFallsBackToRepositoryWhenLocalPathMissing()`

#### B. Install / Update / Remove Lifecycle

7. `ID`: `DPKG-101`  
`Priority`: `P2`  
`Rule`: install from GitHub shorthand (`owner/repo`) clones into `DShader/Packages/<name>`.  
`Input`: `TypeDreamMoon/dream-noise`  
`Expected`: package directory created with root `dreamshader.package.json`  
`Test`: `testInstallFromGithubShorthand()`

8. `ID`: `DPKG-102`  
`Priority`: `P2`  
`Rule`: install from full GitHub URL is supported.  
`Input`: `https://github.com/TypeDreamMoon/dream-noise`  
`Expected`: same behavior as shorthand install  
`Test`: `testInstallFromGithubUrl()`

9. `ID`: `DPKG-103`  
`Priority`: `P2`  
`Rule`: package install validates required metadata file.  
`Invalid`: repo missing `dreamshader.package.json`  
`Expected`: `Invalid package: missing dreamshader.package.json`  
`Test`: `testInstallFailsWhenMetadataMissing()`

10. `ID`: `DPKG-104`  
`Priority`: `P2`  
`Rule`: package install validates required fields (`name` at minimum).  
`Invalid`:
```json
{ "version": "1.0.0" }
```
`Expected`: `Invalid package metadata: missing field 'name'`  
`Test`: `testInstallFailsWhenNameMissing()`

11. `ID`: `DPKG-105`  
`Priority`: `P2`  
`Rule`: successful install writes/updates lock file entry.  
`Expected`: `DShader/dreamshader.lock.json` contains package `name`, `version`, `repository`, `commit`, `installPath`  
`Test`: `testInstallWritesLockFileEntry()`

12. `ID`: `DPKG-106`  
`Priority`: `P2`  
`Rule`: update refreshes package content and lock file commit/version fields.  
`Expected`: updated lock entry reflects new commit (and version when changed)  
`Test`: `testUpdateRefreshesLockFileEntry()`

13. `ID`: `DPKG-107`  
`Priority`: `P2`  
`Rule`: remove deletes package directory and removes lock file entry only for target package.  
`Expected`: target package removed; other lock entries preserved  
`Test`: `testRemovePackageAndPruneLockEntry()`

14. `ID`: `DPKG-108`  
`Priority`: `P2`  
`Rule`: remove non-installed package returns explicit error.  
`Expected`: `Package is not installed: <name>`  
`Test`: `testRemoveNonInstalledPackageFailsClearly()`

15. `ID`: `DPKG-109`  
`Priority`: `P2`  
`Rule`: open packages folder action resolves to project `DShader/Packages` and creates directory if absent.  
`Expected`: folder path exists and is opened  
`Test`: `testOpenPackagesFolderCreatesWhenMissing()`

16. `ID`: `DPKG-110`  
`Priority`: `P2`  
`Rule`: when `git` is unavailable, install/update fail with explicit prerequisite error.  
`Expected`: `git is required for package install/update`  
`Test`: `testInstallFailsWhenGitUnavailable()`

#### C. Package Import Resolution (Package-aware)

17. `ID`: `DPKG-201`  
`Priority`: `P2`  
`Rule`: scoped import path resolves to installed package file with extension.  
`Input`: `import "@typedreammoon/dream-noise/Library/Noise.dsh";`  
`Expected`: goto/import resolution points to installed package file  
`Test`: `testResolvesScopedImportWithExtension()`

18. `ID`: `DPKG-202`  
`Priority`: `P2`  
`Rule`: extension-less package import resolves `.dsh` entry.  
`Input`: `import "@typedreammoon/dream-noise/Library/Noise";`  
`Expected`: resolves to `Noise.dsh`  
`Test`: `testResolvesScopedImportWithoutExtension()`

19. `ID`: `DPKG-203`  
`Priority`: `P2`  
`Rule`: import resolution follows expected precedence (relative -> `DShader` -> `DShader/Packages` -> built-in library).  
`Expected`: nearest higher-priority match is selected deterministically  
`Test`: `testImportResolutionPrecedenceForPackagePaths()`

#### D. Package Store UX and Source Management

20. `ID`: `DPKG-301`  
`Priority`: `P2`  
`Rule`: add index source deduplicates normalized path/URL and persists list setting.  
`Expected`: duplicate source is ignored with info message  
`Test`: `testAddIndexSourceDeduplicates()`

21. `ID`: `DPKG-302`  
`Priority`: `P2`  
`Rule`: remove index source updates persisted settings and refreshes store view.  
`Expected`: removed source packages no longer appear after refresh  
`Test`: `testRemoveIndexSourceRefreshesStoreData()`

22. `ID`: `DPKG-303`  
`Priority`: `P2`  
`Rule`: browse store supports search over `name`, `displayName`, `description`, `tags`.  
`Expected`: search query matches across all supported fields  
`Test`: `testStoreSearchMatchesNameDescriptionAndTags()`

#### E. Bridge/Project Diagnostics Interop

23. `ID`: `DPKG-401`  
`Priority`: `P2`  
`Rule`: package-related bridge diagnostics map to originating source path under `DShader/Packages`.  
`Expected`: diagnostic navigation opens exact file/line in installed package  
`Test`: `testBridgeDiagnosticsMapToInstalledPackageSource()`

#### F. Test Harness Mapping

Current test files:
- `src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderSemanticTokensTest.kt` for `DSYM-001` to `DSYM-002`.
- `src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/editor/DreamShaderInlayParameterHintsProviderTest.kt` for `DSYM-003` to `DSYM-004`.
- `src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/bridge/DreamShaderBridgePathResolverTest.kt` for `DBRG-001` to `DBRG-002`.
- `src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/bridge/DreamShaderBridgeDiagnosticsTest.kt` for `DBRG-003` to `DBRG-004`.
- `src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/bridge/DreamShaderBridgeActionsTest.kt` for `DBRG-005`.
- `src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/editor/DreamShaderManifestCompletionTest.kt` for `DBRG-101` to `DBRG-103`.
- `src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/bridge/DreamShaderStatusBarVisibilityTest.kt` for `DBRG-104`.
- `src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/settings/DreamShaderSettingsToggleTest.kt` for `DBRG-105`.
- `src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/templates/DreamShaderTemplateCommandsTest.kt` for `DTPL-001` to `DTPL-004`.
- `src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/packages/DreamShaderPackageIndexTest.kt` for `DPKG-001` to `DPKG-006`.
- `src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/packages/DreamShaderPackageLifecycleTest.kt` for `DPKG-101` to `DPKG-110`.
- `src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/packages/DreamShaderPackageImportResolutionTest.kt` for `DPKG-201` to `DPKG-203`.
- `src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/packages/DreamShaderPackageStoreUiModelTest.kt` for `DPKG-301` to `DPKG-303`.
- `src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/packages/DreamShaderGitHubPackageSearchTest.kt` for GitHub discovery integration.
- `src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/packages/DreamShaderPackageBridgeInteropTest.kt` for `DPKG-401`.

Additional implemented tests:
- Extended `DBRG-005` from registration/invocation safety to explicit success/error message assertions (`DreamShaderBridgeActionsTest.testBridgeActionCommandSetReturnsExpectedSuccessAndErrorMessages()`).
- Added package GitHub search parser unit tests (`DreamShaderGitHubPackageSearchTest`).

Recommendation:
- Use `M5 Bridge/Tooling Audit Matrix` and `M5 Package Audit Matrix` as release gates for Bridge/package functionality.

#### G. M3 Test Harness Mapping

Current test files:
- `src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/highlighting/DreamShaderSemanticTokensTest.kt` for `DSYM-001` to `DSYM-002`.
- `src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language/editor/DreamShaderInlayParameterHintsProviderTest.kt` for `DSYM-003` to `DSYM-004`.

Recommendation:
- Use `M3 Audit Matrix` as the canonical gate for semantic-token and inlay-hint regressions.

### Milestone M6: Quality, Tests, and Release

- [x] `P1` Add lexer/highlighter unit tests with representative DreamShader fixtures.
- [x] `P1` Add completion regression tests for key contexts.
- [x] `P1` Add navigation/diagnostic tests for common workflows.
- [x] `P2` Add performance smoke tests for large files.
- [x] `P3` Prepare Marketplace metadata, signing, and publishing pipeline.
- [x] `P3` Maintain changelog aligned with release tags.

Acceptance criteria:
- CI covers core language behaviors and prevents regressions.
- Plugin is publishable to JetBrains Marketplace.

#### M6 Audit Matrix (Checked on `2026-05-24`)

| Item | Status | Evidence |
| --- | --- | --- |
| `P1` lexer/highlighter tests | `Implemented` | `DreamShaderLexerSyntaxHighlighterTest` |
| `P1` completion regression tests | `Implemented` | `DreamShaderCompletionContextAnalyzerTest`, `DreamShaderCompletionSuggesterTest` |
| `P1` navigation/diagnostic tests | `Implemented` | `DreamShaderGotoDeclarationHandlerTest`, `DreamShaderFindReferencesTest`, `DreamShaderDocumentationProviderTest`, `DreamShaderSignatureHelpAnalyzerTest`, `DreamShaderSyntaxDiagnosticsTest`, `DreamShaderSectionShapeDiagnosticsTest`, `DreamShaderSemanticDiagnosticsTest` |
| `P1` localization parity guard | `Implemented` | `DreamShaderBundleLocalizationTest` (base bundle keys must exist in `DreamShaderBundle_zh_CN.properties`) |
| `P2` large-file performance smoke tests | `Implemented` | `DreamShaderLargeFilePerformanceSmokeTest` |
| `P3` Marketplace metadata/signing/publishing pipeline | `Implemented` | `build.gradle.kts` (`pluginConfiguration`, `signing`, `publishing`, file-based signing env/properties + legacy env fallback), `.github/workflows/build.yml`, `.github/workflows/release.yml` (writes cert/key secrets to temp files and publishes with file-path env) |
| `P3` changelog aligned with release tags | `Implemented` | `CHANGELOG.md` + release workflow `patchChangelog` step in `.github/workflows/release.yml` |

## Development

Requirements:
- JDK 17+ (recommended 21)

IDEA/IntelliJ Gradle configuration (project-verified):
- Gradle user home: `J:/Gradle`
- Gradle distribution: `Wrapper`
- Gradle JVM: `Oracle OpenJDK 21.0.2`

### Terminal Build Prerequisite (Important)

This project **must** run Gradle with Java 17+.
On this machine, system `java` may point to Java 11, so AI/terminal commands should bootstrap JDK 21 explicitly.
All terminal Gradle operations should also pin Gradle user home to `J:/Gradle`.

Use this before Gradle commands (AI/terminal only):
```powershell
$env:JAVA_HOME="C:\Users\Bunny\.jbr\jbr-21.0.2-windows-x64-b375.1"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:GRADLE_USER_HOME="J:/Gradle"
java -version
.\gradlew.bat -version
```

Expected checks:
- `java -version` shows `21.x`
- `gradlew -version` reports JVM `21.x`
- `GRADLE_USER_HOME` points to `J:/Gradle`

Observed failure when using Java 11:
```text
Gradle requires JVM 17 or later to run. Your build is currently configured to use JVM 11.
```

Quick verification command:
```powershell
$env:GRADLE_USER_HOME="J:/Gradle"
.\gradlew.bat -version
```

Expected check:
- `gradlew -version` reports JVM `21.x`

### Build Commands

Build:
```powershell
$env:GRADLE_USER_HOME="J:/Gradle"; .\gradlew.bat build --no-configuration-cache
```

Test:
```powershell
$env:GRADLE_USER_HOME="J:/Gradle"; .\gradlew.bat test --no-configuration-cache
```

Run plugin in sandbox:
```powershell
$env:GRADLE_USER_HOME="J:/Gradle"; .\gradlew.bat runIde --no-configuration-cache
```

### Marketplace Signing and Publishing (File-Based)

Default local secret directory (project-relative):
- `.secrets/`
- This folder is git-ignored and intended for local signing/publishing credentials only.

Default files read from `.secrets/`:
- `.secrets/jetbrains-chain.crt`
- `.secrets/jetbrains-private.pem`
- `.secrets/jetbrains-private-key-password.txt`
- `.secrets/jetbrains-publish-token.txt`

You can override secret directory with Gradle property:
```powershell
$env:GRADLE_USER_HOME="J:/Gradle"; .\gradlew.bat publishPlugin -PjetbrainsSecretsDir=.private
```

`build.gradle.kts` supports multiple signing input modes, with file mode preferred:
- `JETBRAINS_CERTIFICATE_CHAIN_FILE` / `jetbrainsCertificateChainFile`
- `JETBRAINS_PRIVATE_KEY_FILE` / `jetbrainsPrivateKeyFile`
- `JETBRAINS_PRIVATE_KEY_PASSWORD` / `jetbrainsPrivateKeyPassword`
- `JETBRAINS_PUBLISH_TOKEN` / `jetbrainsPublishToken`
- `JETBRAINS_PRIVATE_KEY_PASSWORD_FILE` / `jetbrainsPrivateKeyPasswordFile`
- `JETBRAINS_PUBLISH_TOKEN_FILE` / `jetbrainsPublishTokenFile`

Compatibility fallback is also enabled:
- `CERTIFICATE_CHAIN_FILE`, `PRIVATE_KEY_FILE`
- `CERTIFICATE_CHAIN`, `PRIVATE_KEY`
- `PRIVATE_KEY_PASSWORD`, `PUBLISH_TOKEN`
- `PRIVATE_KEY_PASSWORD_FILE`, `PUBLISH_TOKEN_FILE`

Local PowerShell example (no `openssl` required):
```powershell
$env:JAVA_HOME="C:\Users\Bunny\.jbr\jbr-21.0.2-windows-x64-b375.1"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:GRADLE_USER_HOME="J:/Gradle"

# Option A: Use project-local .secrets defaults (no extra env vars needed)
.\gradlew.bat signPlugin
.\gradlew.bat publishPlugin

# Option B: Explicit file paths via env vars
$env:JETBRAINS_CERTIFICATE_CHAIN_FILE="C:\secrets\jetbrains-chain.crt"
$env:JETBRAINS_PRIVATE_KEY_FILE="C:\secrets\jetbrains-private.pem"
$env:JETBRAINS_PRIVATE_KEY_PASSWORD_FILE="C:\secrets\jetbrains-private-key-password.txt"
$env:JETBRAINS_PUBLISH_TOKEN_FILE="C:\secrets\jetbrains-publish-token.txt"
.\gradlew.bat signPlugin
.\gradlew.bat publishPlugin
```

If you only have text secrets, save them directly as files (no OpenSSL conversion step):
```powershell
@'
-----BEGIN CERTIFICATE-----
...
-----END CERTIFICATE-----
'@ | Set-Content -Path C:\secrets\jetbrains-chain.crt -NoNewline

@'
-----BEGIN PRIVATE KEY-----
...
-----END PRIVATE KEY-----
'@ | Set-Content -Path C:\secrets\jetbrains-private.pem -NoNewline
```

CI release workflow (`.github/workflows/release.yml`) already uses file mode:
- Reads `CERTIFICATE_CHAIN` and `PRIVATE_KEY` from GitHub Secrets
- Writes them to `$RUNNER_TEMP/*.crt|*.pem`
- Exports `JETBRAINS_CERTIFICATE_CHAIN_FILE` and `JETBRAINS_PRIVATE_KEY_FILE`
- Executes `./gradlew publishPlugin`

### Package Tools Actions (Rider)

Location:
- `Tools` -> `DreamShader` -> `DreamShader Packages`

Available actions:
- Browse Package Store
- Install Package From GitHub
- Update Installed Package
- Remove Installed Package
- Open Packages Folder
- Add Package Index Source
- Remove Package Index Source

### Template Tools Actions (Rider)

Location:
- `Tools` -> `DreamShader` -> `DreamShader Templates`

Available actions:
- Create Material Template
- Create Function Template
- Create Header Template
- Create Package Scaffold

### DreamShader Hub (Rider)

Location:
- `Tools` -> `DreamShader` -> `Open DreamShader Hub`

`DreamShader Hub` provides a one-stop entry for common plugin workflows:
- Open DreamShader settings
- Open Bridge diagnostics panel
- Refresh Bridge diagnostics
- Recompile current/all and clean generated shaders
- Browse package store and install from GitHub
- Open packages folder and add package index source
- Create material/function/header templates and package scaffold

`Browse Package Store` now opens an interactive dialog with:
- search field (`name` / `displayName` / `description` / `tags`)
- package list + detail pane
- package list summary shows install marker, version, and tag preview
- refresh/add source/remove source
- install/update/remove selected package
- install/update/remove run as cancellable background tasks with progress
- installed state marker in list and details
- double-click package to install
- dynamic action enablement by install state
- `Installed only` filter
- `Updates possible only` filter (installed + git available + repository present)
- auto-select and highlight package after install/update

### DreamShader Settings (Rider)

Location:
- `Settings` -> `Tools` -> `DreamShader`

Configurable fields:
- `Project Root`
- `Material Manifest Path`
- `Show DreamShader status bar widget`
- `Enable DreamShader in-editor code lens hints` (currently controls inlay-parameter-hints behavior, not IntelliJ Code Vision)
- `Out Placeholder Suffix` (used by missing `out` argument quick-fix placeholder generation; sanitized to identifier-safe form)
- `Bridge Recompile Current Command`
- `Bridge Recompile All Command`
- `Bridge Clean Generated Command`

Bridge command placeholders:
- `%file%` = current DreamShader file absolute path
- `%projectRoot%` = project base path
- `%bridgeDir%` = resolved bridge directory path

### One-Liner Commands

AI/terminal recommended one-liners (with JDK 21 bootstrap):
```powershell
$env:JAVA_HOME="C:\Users\Bunny\.jbr\jbr-21.0.2-windows-x64-b375.1"; $env:Path="$env:JAVA_HOME\bin;$env:Path"; $env:GRADLE_USER_HOME="J:/Gradle"; .\gradlew.bat build --no-configuration-cache
```

```powershell
$env:JAVA_HOME="C:\Users\Bunny\.jbr\jbr-21.0.2-windows-x64-b375.1"; $env:Path="$env:JAVA_HOME\bin;$env:Path"; $env:GRADLE_USER_HOME="J:/Gradle"; .\gradlew.bat test --no-configuration-cache
```

```powershell
$env:JAVA_HOME="C:\Users\Bunny\.jbr\jbr-21.0.2-windows-x64-b375.1"; $env:Path="$env:JAVA_HOME\bin;$env:Path"; $env:GRADLE_USER_HOME="J:/Gradle"; .\gradlew.bat runIde --no-configuration-cache
```

### Quick Troubleshooting

- If build still reports JVM 11:
```powershell
$env:GRADLE_USER_HOME="J:/Gradle"
.\gradlew.bat -version
Get-Command java | Format-List Source
java -version
```
Re-run the JDK 21 bootstrap commands above in the same shell session.

- If Rider IDE can build but terminal cannot:
Rider uses configured Gradle JVM.
Terminal uses system `java` from `PATH`.
Fix terminal by setting `JAVA_HOME`, prepending `$env:JAVA_HOME\bin`, and keeping `$env:GRADLE_USER_HOME="J:/Gradle"`.
