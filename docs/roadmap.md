# Roadmap and Progress

Project goals, milestone status, detailed TODOs, and release-process notes. This is the long-form planning document; keep README milestones short and link here for detail.

## Goal

Full-featured DreamShaderLang support for JetBrains Rider:
- language core with lexer, parser, PSI, and symbol model
- rich editing and navigation (completion, refactoring, code insight)
- diagnostics and Bridge tool integration
- package management and template scaffolding
- material preview and UX polish

### 1.0.0 - 2026-07-28

First stable release. Includes:
- Language core: `.dsm` / `.dsf` / `.dsh` file types, lexer/parser/PSI, syntax highlighting, formatter, commenter, brace matching, folding, structure view
- Template declaration keyword (v1.5.1) and Layout section keyword (v1.4.8) syntax support
- Context-aware completion, go-to declaration, references/find usages, hover docs, signature help
- Semantic diagnostics pipeline (syntax, section-shape, semantic + Bridge mapping)
- Package management: store dialog, Git-based install/update/remove, multi-source index loading
- Material preview tool window with Bridge file transport
- File and live templates (material, function, header, texture sample, noise material, package scaffold)
- Application-level settings for package indexes, GitHub token, and Bridge commands
- Unified DreamShader Hub


## Completed Version Archives

- `0.0.4`: catalog-based `UE.*` / `Substrate.*` built-ins completed. Unreal material expression data moved out of scattered hard-coded tables into a shared catalog feeding completion, signature help, hover docs, and diagnostics. The release also added best-effort Unreal source scanning, source-root auto-detect, `OutputType="Substrate"` support, and stricter Substrate / Layer diagnostics.

### 2026-06-16 Optimization Archive

This archive folds the temporary optimization plan into the long-term roadmap.
The temporary plan covered edit-time performance for large `.dsh`, `.dsf`, and
`.dsm` files, Bridge-failure-prevention diagnostics, and editor/navigation
polish not yet captured by the implemented roadmap inventory.

Completed:
- Cached semantic diagnostic lexical inputs: file diagnostics now reuse cached file text, whole-file tokens, declaration contexts, and body-level token slices instead of independently re-lexing unchanged bodies in hot semantic passes.
- Cached import closure resolution: direct imports and import closures share cached results invalidated by PSI modification count and VFS structure changes.
- Optimized cross-file reference search so reverse-importer discovery uses cached IntelliJ word-index candidates and same-directory import candidates instead of a full workspace filesystem walk.
- Added conservative `Substrate` expression diagnostics for arithmetic, swizzles, vector constructors, and ternary branch merges.
- Wired `UE.*` go-to declaration to the Unreal source locator, preferring Unreal header targets when source scanning resolves a match and preserving catalog fallback behavior.
- Added DreamShader live templates and conservative postfix templates.
- Updated live template registration to the current IntelliJ Platform `defaultLiveTemplates` extension-point form and removed the deprecated provider implementation.
- Added Goto Symbol and Goto Class contributors backed by DreamShader declaration search.
- Centralized DreamShader PSI traversal/documentation helpers in `DreamShaderPsiUtil`, now shared by declaration search, qualified names, goto declaration, references, structure view, plain-text symbol completion, symbol model, and semantic diagnostics.
- Integrated spellchecking for comments and `VirtualFunction` `Description` strings while avoiding noisy path/code-like string checks.
- Added declaration-adjacent comment extraction for hover docs.
- Expanded inline color preview coverage for color constructor aliases, hex colors, and conservative 0-255 channel forms.
- Rendered `opt` qualifiers and default values in callable hover/signature output.
- Shared declaration context across diagnostic passes by caching sections, section bodies, typed declarations, child declarations, callable signatures, and reusable body tokens.
- Moved parser-obvious malformed declaration/section diagnostics into recoverable parser error markers and removed duplicate token-level malformed syntax diagnostics.
- Added targeted `Path(...)` object-segment suffix warnings for Bridge-breaking asset suffixes such as `.uasset`, `.umap`, and common image extensions.
- Added the shared `DreamShaderUi` design helper layer and migrated Bridge, package store, material preview, settings, template, hub, and welcome panels to the card-based workflow surface.

Remaining blocked item:
- GLSL legacy-alias compatibility diagnostics are intentionally not implemented yet. The removed/legacy alias list needs upstream confirmation before the plugin can warn safely or offer replacement quick fixes.

Verification used during the optimization pass:
- `./gradlew.bat test --tests com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.DreamShaderSemanticDiagnosticsTest --tests com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.DreamShaderSemanticAnnotatorTest --tests com.github.tsdaer.dreamshaderlanguagesupport.language.integration.DreamShaderLargeFilePerformanceSmokeTest`
- `./gradlew.bat test --tests com.github.tsdaer.dreamshaderlanguagesupport.language.parser.DreamShaderPsiParserTest --tests com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.DreamShaderSyntaxDiagnosticsTest`
- `./gradlew.bat test --tests com.github.tsdaer.dreamshaderlanguagesupport.language.editor.DreamShaderMinorEditorFeaturesTest --tests com.github.tsdaer.dreamshaderlanguagesupport.language.editor.DreamShaderSignatureHelpAnalyzerTest --tests com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.DreamShaderDocumentationProviderTest`
- `./gradlew.bat test --tests com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.DreamShaderGotoContributorsTest`
- `./gradlew.bat test --tests com.github.tsdaer.dreamshaderlanguagesupport.language.editor.DreamShaderSpellcheckingStrategyTest`
- `./gradlew.bat test --tests com.github.tsdaer.dreamshaderlanguagesupport.language.templates.DreamShaderPostfixTemplatesTest --tests com.github.tsdaer.dreamshaderlanguagesupport.language.templates.DreamShaderLiveTemplatesTest --tests com.github.tsdaer.dreamshaderlanguagesupport.language.templates.DreamShaderTemplateCommandsTest`
- `./gradlew.bat test`

### 2026-07-20 v1.6.0 Syntax Pass

Completed v1.6.x language features:

- `Group("Name") { ... }` property grouping scopes: added `group` and `propgroup` lexer keywords, declaration parsing, string-name resolution in `DreamShaderDeclarationImpl`, completion snippets with template placeholders, structure-view integration with child section/declaration display, and section flattening in diagnostics (Group contents promoted to parent declaration scope). Also supports `Properties Group("Name") { ... }` section-header modifier syntax with PSI `groupName()` accessor on sections, structure-view display, and completion after section keywords.
- Single-output return-value functions (`Function float X(...)`): `validateDeclarationHeader` now accepts `TYPE` token after `Function`/`GraphFunction` keyword; `returnType()` accessor added to `DreamShaderDeclaration` interface and impl; signature help pattern captures optional return type and renders `: <type>` suffix; hover docs display return type; `return` keyword diagnostics exclude return-value function bodies (where `return` is valid).

## Current Progress

| Area                                           | Status | Notes                                                                                                                                                                                                                                    |
|------------------------------------------------|--------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| File type association (`.dsm`, `.dsf`, `.dsh`) | Done   | Registered in plugin.xml                                                                                                                                                                                                                 |
| Basic lexer                                    | Done   | Handles identifiers, keywords, sections, types, strings, numbers, comments, operators, braces                                                                                                                                            |
| Syntax highlighting                            | Done   | Keyword/section/type/string/number/comment/operator/brace coloring                                                                                                                                                                       |
| Comment support                                | Done   | `//` and `/* ... */`                                                                                                                                                                                                                     |
| Brace matching                                 | Done   | `()`, `[]`, `{}`                                                                                                                                                                                                                         |
| Color settings page                            | Done   | DreamShader color entries available in editor color scheme                                                                                                                                                                               |
| Plugin/file icons                              | Done   | Added plugin icon and DreamShader file icon                                                                                                                                                                                              |
| Token sets/constants                           | Done   | Added token set groups for future completion/diagnostics                                                                                                                                                                                 |
| Context-aware completion                       | Done   | PSI-first context detection + lexer fallback; top-level/section/type/settings/base-output/UE/HLSL/import completion available (including quoted enums + unquoted bool/number settings values with typed-prefix filtering)                |
| PSI / Parser foundation                        | Done   | ParserDefinition + PsiParser + typed PSI for declaration/section implemented                                                                                                                                                             |
| Folding                                        | Done   | `lang.foldingBuilder` added; supports brace blocks and `// region` / `// endregion`                                                                                                                                                      |
| Semantic tokens                                | Done   | Semantic classification implemented for declaration keywords/names, section names, callable references, `UE` namespace, namespace qualifiers (`Namespace::`), local symbol declaration/usage split, and `Base.*` material output members |
| Diagnostics                                    | Done   | Local parser + section-shape + semantic diagnostics implemented with tests                                                                                                                                                               |
| Go to Definition / References                  | Done   | Go to Definition + Find References implemented for imports, top-level declarations, namespace-qualified members (`A::B::Member`), namespace-scoped unqualified member resolution, import-chain cross-file declaration usages, `Name="..."` alias call targets, and Copy/Paste Reference FQN reverse resolution |
| Document symbols / structure                   | Done   | Structure view integrated for top-level declarations and sections; breadcrumbs and Context Info expose declaration/section hierarchy                                                                                                     |
| Inlay hints                                    | Done   | Parameter name hints implemented with callable-context filtering and independent `enableInlayParameterHints` toggle, including same-file and import-chain declared callable signatures (`Function`/`GraphFunction`/`VirtualFunction`) |
| Formatting                                     | Done   | Formatter implemented (`lang.formatter`): indentation, operator spacing, braces/section layout, section blank-line/`::` style options, and simple section assignment alignment                                                           |
| Bridge diagnostics panel                       | Done   | Tool window added with refresh/list/open-location baseline; Bridge diagnostic gutter markers navigate to source path/line/column when resolvable                                                                                         |
| Bridge actions                                 | Done   | Refresh/open-location/open bridge path + configurable recompile/clean command execution                                                                                                                                                  |
| Status bar / CodeLens                          | Done   | Status widget is gated by `showStatusBar`; IntelliJ daemon-bound Code Vision is gated by `enableCodeLens`                                                                                                                               |
| Package commands                               | Done   | install/update/remove/browse/open folder + source add/remove wired                                                                                                                                                                       |
| Authoring templates / scaffold commands        | Done   | Material/function/header/texture sample/noise material/package scaffold/package wizard commands implemented and covered by tests                                                                                                           |
| Material Preview                               | Done   | File-bridge preview request writer, result reader, right-side ToolWindow, editor-follow, edit debounce, and Bridge preview file watcher integration                                                                                        |
| Workflow UI polish                            | Done   | Shared `DreamShaderUi` card/section/pill helpers are used across settings, package store, Bridge diagnostics, material preview, template workflows, Hub, and welcome surfaces                                                               |
| `Group("Name")` property scopes              | Done   | Lexer + parser + PSI; `propgroup` legacy alias; completion snippets; structure-view children; section flattening in diagnostics                                                                                                              |
| Return-value functions                       | Done   | `Function float X(...)` syntax parsed; signature help `: <type>` suffix; hover docs return type; `return` allowed in return-value function bodies                                                                                          |

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
- `Namespace` is now parsed as a typed top-level `DECLARATION` node (instead of lexer-only handling), aligning parser/symbol/diagnostic flows.
- `Namespace` declaration bodies now parse nested declaration PSI nodes (for example `Function` / `GraphFunction`) to support tree-style navigation.
- Completion context analysis now uses PSI tree information for file inputs and parser-based structure analysis for text inputs (lexer kept only as defensive fallback).
- Minimal symbol model added from typed PSI: top-level declarations with section children, plus namespace child declarations.

### Milestone M2: Completion (Core Editing)

- [x] `P1` Top-level keyword completion: `import`, `Shader`, `ShaderFunction`, `ShaderLayer`, `ShaderLayerBlend`, `VirtualFunction`, `Function`, `GraphFunction`, `Namespace`.
- [x] `P1` Section-aware completion: `Properties`, `Inputs`, `Outputs`, `Settings`, `Options`, `Graph`.
- [x] `P1` Type completion in declaration/function/graph contexts.
- [x] `P1` Settings key/value completion (Domain/ShadingModel/BlendMode/etc.).
- [x] `P1` Material output completion (`Base.*` members).
- [x] `P1` `UE.*` completion and snippet insertion (including `UE.Expression(...)`).
- [x] `P1` HLSL intrinsic completion in function/graph-like code blocks.
- [x] `P1` Import path completion for `.dsh`, `.dsf`, and `.dsm`.

Acceptance criteria:
- Completion proposals are context-sensitive and cover sections, types, settings values, `UE.*`, HLSL, and imports.
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
- Import path completion for project `.dsh` / `.dsf` / `.dsm` files and package files under `DShader/Packages`.
- Import candidate path normalization now converts package physical paths (`DShader/Packages/...`) to import-ready package syntax (`@scope/name/...` or `name/...`) before completion display/insertion.
- Section completion now filters by declaration schema, so completion no longer suggests unsupported sections such as `Inputs` in `Shader` or `Graph` in `VirtualFunction`.
- Declaration-head completion suggests supported `Name`/`Root` arguments and `Root` values (`Game`, `Plugin.<Name>`, `Plugins.<Name>`).
- Namespace-qualified callable completion now supports `Tools::` / `A::B::` member suggestions from the current file plus import closure.
- Local symbol completion is scoped to the current graph/function-like body before the caret, and input/function qualifier completion suggests `in`, `out`, `inout`, `opt`, and `const`.
- Conservative Substrate expression-level diagnostics now cover arithmetic, swizzle, constructor, and branch-merge rules that are likely to fail during Bridge generation; full expression type inference remains a separate long-term hardening target.

### Milestone M3: Navigation and Symbols

- [x] `P1` Document symbols for top-level declarations and sections.
- [x] `P1` Folding for blocks and `// region` markers.
- [x] `P1` Go to Definition for imports and symbol declarations.
- [x] `P1` Find References for local declarations/usages.
- [x] `P2` Extend Find References to import-chain cross-file declaration usages.
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
- Added structure view integration via `lang.psiStructureViewFactory` for top-level declarations and sections, with namespace nodes expanding nested declarations.
- Added regression test in `DreamShaderStructureViewModelTest`.
- Added `DreamShaderGotoDeclarationHandler` via `gotoDeclarationHandler` extension.
- Supports definition jump for `import "..."` targets (`.dsh/.dsf/.dsm`), same-file top-level declaration symbol usages, `Namespace::Member(...)` calls (both qualifier and member target), and namespace-scoped unqualified member calls.
- Namespace navigation supports multi-level qualifier chains (`A::B::Member`) with full qualifier-path resolution: goto on `Member` resolves against the full namespace path (`A::B`), and goto on intermediate qualifier (`B`) resolves to the nested namespace declaration.
- Qualified unresolved member calls (for example `Tools::Missing`) no longer fall back to top-level same-name declarations.
- Added regression tests in `DreamShaderGotoDeclarationHandlerTest` (including unresolved qualified-member no-fallback and namespace-scope unqualified resolution cases).
- Updated nested-qualifier goto regression test to use editor caret offset (`<caret>`) instead of manual string index arithmetic, preventing brittle offset drift in future fixture edits.
- Added `DreamShaderReferencesSearchExecutor` via `referencesSearch` extension for declaration usage discovery.
- References search is namespace-aware for `Namespace::Member` patterns, so same-name top-level and namespace members no longer cross-match.
- References search now excludes declaration-name identifier tokens from usage results, so Find References/Rename operate on real usages only (declaration heads are no longer double-counted as references).
- Namespace-aware references matching now uses full qualifier-path matching in multi-level chains (`A::B::Member`), so same-name members under different namespace paths (for example `A::B` vs `C::B`) do not cross-match.
- Unqualified reference matching inside namespaces now follows enclosing namespace path scope (nearest scope first, then parent namespace path), aligning Find References/Rename with goto behavior.
- References search now traverses import-connected files (including recursive import chains), so cross-file call sites are included in Find References/Rename results.
- References search now respects IDE/user-provided search scope constraints (`GlobalSearchScope`/`LocalSearchScope`) while traversing import-connected files, so scoped Find References/Rename queries do not leak outside requested files/elements.
- References search import graph construction now reuses shared import resolution (`DreamShaderImportClosureResolver.resolveDirectImports`) while keeping the existing importers+imports connected-closure behavior unchanged.
- Architecture quick note: see `docs/architecture.md` -> `4. Navigation and References` -> `Current boundary` for the canonical reference-search boundary definition.
- References search now aligns symbol-name extraction with goto behavior for `Name="..."` declarations (`VirtualFunction` / `ShaderFunction` / `ShaderLayer` / `ShaderLayerBlend`): both full path and path-leaf alias names participate in Find References/Rename matching.
- Namespace declarations now also align with the same `Name="..."` symbol extraction path (`Namespace(Name="...")`): declaration naming, nested-namespace qualifier resolution (`A::B::Member`), Find References, and Rename all treat attribute-form namespaces equivalently to identifier-form namespaces.
- Rename now supports `Name="..."` declaration identifiers directly (`VirtualFunction` / `ShaderFunction` / `ShaderLayer` / `ShaderLayerBlend`): refactor updates the quoted `Name` value itself and propagates to call sites; path-form names preserve prefix and only replace leaf segment (for example `Functions/F_PulseTint` -> `Functions/F_OutputTint`).
- `DreamShaderDeclaration.declarationName()` now normalizes `Name="..."` declarations to user-facing callable aliases (string unquoted; path-form returns leaf segment), so structure/navigation/find-usages naming is consistent with call-site symbols.
- Added `DreamShaderFindUsagesProvider` and `PsiNameIdentifierOwner` support on `DreamShaderDeclaration`.
- Find Usages presentation type now reflects declaration keyword kind (for example `dreamshader shaderfunction declaration`) instead of a single generic declaration label, improving usage-panel scanability in mixed files.
- Added regression tests in `DreamShaderFindReferencesTest` and `DreamShaderDeclarationRenameTest`.
- Stabilized cross-file rename regression for path-form `Name="..."` declarations (`ShaderFunction` etc.): rename assertions now commit pending PSI/doc changes and validate call-site updates with whitespace-tolerant matching, preventing false negatives from formatter/layout noise.
- Standardized rename regression assertions to semantic regex matching (instead of indentation/newline-sensitive string slices) and centralized post-rename PSI document commit in test helper flow, reducing flakiness under full-suite runs.
- Added cross-file references regression coverage in `DreamShaderFindReferencesTest`:
  - `testReferencesSearchFindsUsagesAcrossImportedFiles()`
  - `testReferencesSearchFindsUsagesThroughRecursiveImportChain()`
  - `testReferencesSearchRespectsGlobalFileScope()`
  - `testReferencesSearchRespectsLocalSearchScopeFileBoundary()`
- Added `Name="..."` references regression coverage in `DreamShaderFindReferencesTest`:
  - `testReferencesSearchFindsVirtualFunctionUsagesByNameAttributeLeaf()`
  - `testReferencesSearchFindsShaderFunctionUsagesByNameAttributePathLeafAcrossImportedFiles()`
- Added `Name="..."` rename regression coverage in `DreamShaderDeclarationRenameTest`:
  - `testRenameVirtualFunctionNameAttributeUpdatesStringAndUsages()`
  - `testRenameShaderFunctionNameAttributePathLeafKeepsPrefixAndUpdatesCrossFileUsages()`
- Added declaration-name normalization regression coverage in `DreamShaderDeclarationRenameTest`:
  - `testDeclarationNameUsesVirtualFunctionNameAttributeLeaf()`
  - `testDeclarationNameUsesShaderFunctionNameAttributePathLeaf()`
- Hardened `Name="..."` rename regression assertions to accept formatter whitespace normalization (`Name="X"` vs `Name = "X"`), removing full-suite order-dependent flakiness while keeping semantic rename guarantees.
- Added Find Usages provider presentation regression coverage in `DreamShaderFindUsagesProviderTest`:
  - `testGetTypeUsesDeclarationKeyword()`
  - `testGetTypeFallsBackWhenKeywordMissing()`
- Added rename regression hardening for namespace collisions: renaming namespace members/top-level declarations no longer cross-renames same-name declarations in the other scope, including nested same-name members under different namespace paths.
- Added `DreamShaderDocumentationProvider` via `lang.documentationProvider` for hover docs on declarations, settings keys/values, `UE.*` builtins, function call signatures, and local variables (name/type/scope).
- Hover resolution now prefers caret/original token context before resolved declaration target, so call-site and local-variable hovers are not shadowed by declaration fallback in IDE mouse hover flows.
- Refactored hover documentation data storage/lookup to dot-path form (`path.path`), centralized in `DreamShaderDocumentationData` (for example `settings.domain.description`, `ueBuiltins.texcoord.signature`) and consumed through path-based accessors.
- Added `DreamShaderParameterInfoHandler` via `codeInsight.parameterInfo` for signature help on `UE.*` builtins, common HLSL intrinsics, and declared callable signatures from current file plus import-recursive closure (`Function`/`GraphFunction`/`VirtualFunction`).
- Extended inlay-parameter-hint signature resolution to reuse declared callable signatures from current file plus import-recursive closure and suppress declaration-head false positives (avoid duplicate hints on declaration parameter lists).
- Function-call hover signature rendering now resolves declared callable signatures from current file plus import-recursive closure.
- Added `DreamShaderImportClosureResolver` as a shared import-recursive file-closure utility, reused by signature/inlay/hover call-signature flows and goto imported-file traversal.
- `DreamShaderReferencesSearchExecutor` now also reuses `DreamShaderImportClosureResolver` for direct import-edge extraction before building bidirectional (imports + importers) reference-search closure.
- `DreamShaderGotoDeclarationHandler` import-string detection now also reuses `DreamShaderImportClosureResolver.isImportStringLiteralToken(...)` to keep import-literal classification rules centralized.
- Added `DreamShaderCallSignatureResolver` to centralize declaration-first call-signature resolution (same file -> import-recursive closure -> built-in signatures), and to avoid import-closure scanning when same-file declarations already satisfy the call.
- Added dedicated import-closure regression coverage in `DreamShaderImportClosureResolverTest`:
  - `testResolveDirectImportsReturnsImmediateImportedFilesOnly()`
  - `testResolveImportClosureIncludesRecursiveImportsInBfsOrder()`
  - `testResolveImportClosureHandlesImportCycleWithoutDuplicates()`
  - `testIsImportStringLiteralTokenRecognizesOnlyImportStrings()`
- Added regression tests in `DreamShaderDocumentationProviderTest`, `DreamShaderSignatureHelpAnalyzerTest`, and `DreamShaderInlayParameterHintsProviderTest`.
- Added semantic token classification in `DreamShaderSemanticAnnotator` for declaration keywords/names, section names, callable references, `UE` namespace, namespace qualifiers (`Namespace::`), local symbol declaration/usage split, and `Base.*` material output members.
- Refactored semantic-token classification logic into `DreamShaderSemanticTokenClassifier` so `DreamShaderSemanticAnnotator` focuses on annotation emission + diagnostics pipeline aggregation.
- Reorganized `DreamShaderSemanticAnnotator` internals into grouped diagnostic sections (`bridge`, `syntax`, `section-shape`, `semantic`, `call/import utilities`) and grouped constant/regex blocks to improve maintainability without behavior changes.
- Extended section-shape diagnostics to declaration-aware schema constraints for `ShaderLayer` / `ShaderLayerBlend` / `Function` / `GraphFunction`, plus declaration-scoped `Results` alias handling (`ShaderFunction` / `VirtualFunction` only).
- Added semantic rules for `VirtualFunction` `Options.Asset`: required option entry + asset-path validation with root constraints (`Game`, `Engine`, `Plugin.<Name>`, `Plugins.<Name>`).
- Added `VirtualFunction` `Options/Settings` `Description` quality diagnostics (warning when missing, non-quoted, or empty string).
- Extended `VirtualFunction` option diagnostics so missing `Asset`/`Description` are still reported when neither `Options` nor `Settings` section exists.
- Extended unsupported Graph control-flow diagnostics to also apply inside `Function` and `GraphFunction` declaration bodies.
- Added formatter spacing guard for namespace qualifiers so `Namespace::Member` always stays compact around `::` (no injected spaces before/after the double colon).
- Stabilized formatter/rename regression tests against code-style setting leakage by resetting temporary code-style settings per formatter test lifecycle.
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
- Unsupported import extension quick-fix now offers all supported replacement extensions (`.dsh`, `.dsf`, `.dsm`), prioritizes already-resolvable targets in the current workspace, and marks quick-fix labels with `(resolves existing file)` / `(preferred default)` / `(will update preferred default)` hints.
- Optional setting `autoUpdatePreferredImportExtension` lets users persist the selected quick-fix extension as the new preferred default when applying unsupported-extension quick-fixes.
- Import-extension quick-fix hint suffix composition is centralized in a dedicated formatter to keep ordering stable (`resolves existing` -> `preferred default` -> `will update preferred default`) and simplify future hint expansion.
- Added same-scope duplicate declaration-name diagnostics for semantic analysis (top-level and namespace-local scope), including `Name="..."` path-form declarations matched by path leaf (for example `Functions/F_Blend` -> `F_Blend`), while allowing identical names across different namespace scopes.
- Added duplicate declaration-name quick fix: `Rename declaration to '<NameN>'`, which suggests a unique numeric-suffix name in the current scope and preserves path prefix for `Name="..."` path-form declarations.
- Added a lightweight settings-UI test base for recursive component lookup by stable component name, and covered `Preferred Import Extension` preview live updates (combo selection + auto-update checkbox interaction).
- Extended the same UI-testing approach to `DreamShader Package Store` dialog with stable component names and a regression test that verifies install/update/remove button enablement reacts correctly to selection changes and installed-state transitions.
- Added Package Store filter regression coverage to verify `Installed only` / `Updates possible only` toggles refresh list contents and preserve correct action-button disablement in empty-list states.
- Added Package Store search regression coverage to verify `queryField` + `Search` button refresh list contents based on keyword and restore full list after clearing the query.
- Added Package Store GitHub-search UI regression coverage by exposing a testable search execution path and validating both empty-query status handling and successful search-result list replacement.
- Extended GitHub-search UI regression coverage with explicit `ERROR` status behavior checks to ensure failed searches do not mutate the current Package Store list.
- Added GitHub-search `no results` UI regression checks to lock current behavior (APPLIED with empty list + action buttons disabled) and refactored Package Store dialog UI tests with a shared harness helper to reduce repeated setup code.
- Added sequential GitHub-search resilience coverage (`APPLIED` then `ERROR`) to ensure error-path searches do not overwrite or clear the last successfully applied GitHub result list.
- Refactored unresolved import quick-fix scaffold generation to reuse `DreamShaderTemplateService` templates (`createHeaderTemplate` / `createFunctionTemplate` / `createMaterialTemplate`) for consistency with template actions.
- Completed `DreamShaderBundle_zh_CN.properties` coverage for newly added diagnostics and quick-fix messages (`VirtualFunction Description` quality diagnostics, settings/semantic suggestion quick fixes).
- Added localization parity regression test (`DreamShaderBundleLocalizationTest`) to ensure `zh_CN` bundle contains all base bundle keys.
- Added regression tests in `DreamShaderSemanticTokensTest` for `DSYM-001` and `DSYM-002`, including nested-call/member-like edge cases, array-index symbol usage, and multi-level namespace qualifier classification.
- Added `DreamShaderSemanticTokenClassifierTest` to validate classifier behavior directly and verify classifier/annotator consistency on representative samples.
- Refactored `DreamShaderCodeVisionProvider` click handling into an explicit action-plan layer (`RefreshPackageStore` / `OpenBridgeDirectory` / `NoAction`) to make workflow behavior deterministic and unit-testable.
- Added regression tests in `DreamShaderCodeVisionProviderTest` for code-vision click planning branches (active-file refresh, bridge-directory open plan, and no-action fallback when bridge path is unresolved).

### M3 Testable Navigation/Symbol Checklist

Rule format:
- `ID`: stable rule id for navigation/symbol features
- `Priority`: milestone priority
- `Expected`: expected behavior
- `Test`: suggested test name

#### M3 Audit Matrix (Checked on `2026-05-30`)

| ID         | Status        | Test mapping                                                                                                                                                                                                         |
|------------|---------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `DSYM-001` | `Implemented` | `DreamShaderSemanticTokensTest.testSemanticTokensForDeclarationAndSectionScopes()`                                                                                                                                   |
| `DSYM-002` | `Implemented` | `DreamShaderSemanticTokensTest.testSemanticTokensInGraphAndFunctionBodies()` + subcases `testSemanticTokensForNestedCallsAndMemberLikeSyntax()` / `testSemanticTokensForArrayIndexAndMultiLevelNamespaceQualifier()` |
| `DSYM-003` | `Implemented` | `DreamShaderInlayParameterHintsProviderTest.testProducesHintsForUeAndIntrinsicCalls()` + `DreamShaderInlayParameterHintsProviderTest.testProducesHintsForUserDeclaredFunctionCall()`                                                  |
| `DSYM-004` | `Implemented` | `DreamShaderInlayParameterHintsProviderTest.testSkipsNamedArguments()`                                                                                                                                               |
| `DSYM-005` | `Implemented` | `DreamShaderCodeVisionProviderTest.testClickPlanRefreshesStoreWhenDeclarationFileIsActiveFile()` + `DreamShaderCodeVisionProviderTest.testClickPlanOpensBridgeDirectoryWhenNotActiveAndBridgeExists()` + `DreamShaderCodeVisionProviderTest.testClickPlanReturnsNoActionWhenNotActiveAndBridgeMissing()` |

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
`Test`: `testProducesHintsForUeAndIntrinsicCalls()` + `testProducesHintsForUserDeclaredFunctionCall()`

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

| ID         | Status        | Test mapping                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
|------------|---------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `DSYN-001` | `Implemented` | `DreamShaderSyntaxDiagnosticsTest.testUnclosedStringLiteral()`                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| `DSYN-002` | `Implemented` | `DreamShaderSyntaxDiagnosticsTest.testUnclosedBlockComment()`                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| `DSYN-003` | `Implemented` | `DreamShaderSyntaxDiagnosticsTest.testUnmatchedBraceInGraph()`                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| `DSYN-004` | `Implemented` | `DreamShaderSyntaxDiagnosticsTest.testMalformedTopLevelDeclaration()`                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| `DSYN-005` | `Implemented` | `DreamShaderSyntaxDiagnosticsTest.testMalformedSectionHeader()`                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| `DSYN-101` | `Implemented` | `DreamShaderSectionShapeDiagnosticsTest.testDsfDisallowsTopLevelShader()` / `testDsfDisallowsTopLevelNamespace()`                                                                                                                                                                                                                                                                                                                                                                                                       |
| `DSYN-102` | `Implemented` | `DreamShaderSectionShapeDiagnosticsTest.testVirtualFunctionDisallowsGraphSection()`                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| `DSYN-103` | `Implemented` | `DreamShaderSectionShapeDiagnosticsTest.testNamespaceAllowsOnlyFunctionDeclarations()`                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `DSYN-104` | `Implemented` | `DreamShaderSectionShapeDiagnosticsTest.testLayerRequiresSingleMaterialAttributesOutput()`                                                                                                                                                                                                                                                                                                                                                                                                                              |
| `DSYN-105` | `Implemented` | `DreamShaderSectionShapeDiagnosticsTest.testLayerBlendRequiresTwoMaterialAttributesInputs()`                                                                                                                                                                                                                                                                                                                                                                                                                            |
| `DSYN-106` | `Implemented` | `DreamShaderSectionShapeDiagnosticsTest.testDshDisallowsTopLevelShaderFunction()`                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| `DSYN-107` | `Implemented` | `DreamShaderSectionShapeDiagnosticsTest.testDsmDisallowsTopLevelShaderFunction()`                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| `DSYN-108` | `Implemented` | `DreamShaderSectionShapeDiagnosticsTest.testShaderDisallowsInputsSection()` / `testShaderRequiresGraphSection()`                                                                                                                                                                                                                                                                                                                                                                                                        |
| `DSYN-109` | `Implemented` | `DreamShaderSectionShapeDiagnosticsTest.testShaderDuplicateSectionIsReported()`                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| `DSYN-201` | `Implemented` | `DreamShaderSemanticDiagnosticsTest.testUnknownSettingsKey()` + `testUnknownSettingsKeyQuickFixReplacesWithSuggestion()`                                                                                                                                                                                                                                                                                                                                                                                                |
| `DSYN-202` | `Implemented` | `DreamShaderSemanticDiagnosticsTest.testInvalidSettingsEnumValue()` + `testInvalidBooleanSettingsValue()` + `testValidBooleanSettingsValue()` + `testInvalidNumCustomizedUvsValue()` + `testValidNumCustomizedUvsValue()` + `testInvalidBooleanSettingsValueQuickFixReplacesWithTrue()` + `testInvalidNumCustomizedUvsValueQuickFixReplacesWithZero()`                                                                                                                                                                  |
| `DSYN-203` | `Implemented` | `DreamShaderSemanticDiagnosticsTest.testUnknownBaseOutputMember()` + `testUnknownBaseOutputMemberQuickFixReplacesWithSuggestion()`                                                                                                                                                                                                                                                                                                                                                                                      |
| `DSYN-204` | `Implemented` | `DreamShaderSemanticDiagnosticsTest.testUnknownTypeInInputs()` + `testUnknownTypeQuickFixReplacesWithSuggestion()` + `testUnknownTypeInFunctionParameter()` + `testUnknownTypeInGraphFunctionParameter()` + `testUnknownTypeInNamespaceFunctionParameter()` + `testUnknownTypeInGraphLocalVariable()` + `testUnknownTypeInNamespaceFunctionLocalVariable()`                                                                                                                                                                                                         |
| `DSYN-205` | `Implemented` | `DreamShaderSemanticDiagnosticsTest.testMissingOutArgumentInFunctionCall()` + `testMissingOutArgumentQuickFixAddsPlaceholderArgument()` + `testMissingOutArgumentQuickFixAddsFirstArgumentForEmptyCall()` + `testMissingOutArgumentQuickFixAddsAllMissingOutArguments()` + `testMissingOutArgumentQuickFixAvoidsNameCollision()` + `testMissingOutArgumentQuickFixUsesConfiguredPlaceholderSuffix()` + `testMissingOutArgumentInNamespaceFunctionCall()` + `testMissingOutArgumentInNamespaceFunctionCallQuickFixAddsPlaceholderArgument()` + `testMissingOutArgumentResolvesUnqualifiedCallToNearestNamespaceScope()` + `testMissingOutArgumentDoesNotReportForTopLevelOverloadWhenNamespaceCallIsComplete()` |
| `DSYN-206` | `Implemented` | `DreamShaderSemanticDiagnosticsTest.testUnresolvedImportPath()` + `testUnresolvedImportPathQuickFixCreatesMissingFile()` + `testUnresolvedImportPathQuickFixCreatesFunctionTemplateForDsf()` + `testUnresolvedImportPathQuickFixCreatesShaderTemplateForDsm()` + `testUnresolvedScopedImportQuickFixCreatesFileUnderPackages()` + `testUnresolvedImportPathUnsupportedExtensionReportsExplicitError()` + `testUnresolvedImportPathUnsupportedExtensionQuickFixChangesToDsh()` + `testUnresolvedImportPathUnsupportedExtensionQuickFixOffersAllSupportedExtensions()` + `testUnresolvedImportPathUnsupportedExtensionQuickFixPrioritizesResolvableExtension()` + `testUnresolvedImportPathUnsupportedExtensionQuickFixMarksPreferredDefault()` + `testUnresolvedImportPathUnsupportedExtensionQuickFixAutoUpdatesPreferredExtensionWhenEnabled()` + `testUnresolvedImportPathUnsupportedExtensionQuickFixDoesNotAutoUpdatePreferredExtensionWhenDisabled()` + `testUnresolvedImportPathUnsupportedExtensionQuickFixWillUpdateHintHiddenWhenAlreadyPreferred()` + `testUnresolvedImportPathUnsupportedExtensionQuickFixHintOrderIsStable()` |
| `DSYN-207` | `Implemented` | `DreamShaderSemanticDiagnosticsTest.testGraphDisallowsForLoopStatement()` / `testGraphDisallowsWhileLoopStatement()` / `testGraphDisallowsDoLoopStatement()`                                                                                                                                                                                                                                                                                                                                                            |
| `DSYN-208` | `Implemented` | `DreamShaderSemanticDiagnosticsTest.testGraphDisallowsSwitchStatement()` / `testGraphDisallowsCaseKeyword()` / `testGraphDisallowsDefaultKeyword()`                                                                                                                                                                                                                                                                                                                                                                     |
| `DSYN-209` | `Implemented` | `DreamShaderSemanticDiagnosticsTest.testGraphDisallowsBreakStatement()` / `testGraphDisallowsContinueStatement()`                                                                                                                                                                                                                                                                                                                                                                                                       |
| `DSYN-210` | `Implemented` | `DreamShaderSemanticDiagnosticsTest.testGraphDisallowsReturnStatement()`                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| `DSYN-211` | `Implemented` | `DreamShaderSemanticDiagnosticsTest.testVirtualFunctionOptionAssetRequiresPath()` + `testVirtualFunctionOptionAssetRejectsBareIdentifier()` + `testVirtualFunctionOptionAssetAcceptsPathCall()` + `testVirtualFunctionOptionAssetAcceptsQuotedObjectPath()` + `testVirtualFunctionOptionAssetAcceptsEngineRootPathCall()` + `testVirtualFunctionOptionAssetRejectsUnknownPathRoot()` + `testVirtualFunctionOptionAssetUnknownRootQuickFixReplacesWithGame()` + `testVirtualFunctionOptionAssetRequiresOptionEntry()`    |
| `DSYN-212` | `Implemented` | `DreamShaderSemanticDiagnosticsTest.testShaderRootRejectsEngineRoot()` + `testShaderRootAcceptsPluginRoot()`                                                                                                                                                                                                                                                                                                                                                                                                            |
| `DSYN-213` | `Implemented` | `DreamShaderSemanticDiagnosticsTest.testVirtualFunctionOptionDescriptionWarnsWhenNotQuoted()` + `testVirtualFunctionOptionDescriptionNotQuotedQuickFixAddsQuotes()` + `testVirtualFunctionOptionDescriptionWarnsWhenEmpty()` + `testVirtualFunctionOptionDescriptionEmptyQuickFixFillsDefault()` + `testVirtualFunctionOptionDescriptionSettingsAliasAcceptsQuotedText()` + `testVirtualFunctionOptionDescriptionRecommendedWhenMissing()` + `testVirtualFunctionOptionDescriptionRecommendedQuickFixAddsDescription()` |
| `DSYN-214` | `Implemented` | `DreamShaderSemanticDiagnosticsTest.testDuplicateTopLevelDeclarationNameIsReported()` + `testDuplicateNamespaceChildDeclarationNameIsReported()` + `testSameNameInDifferentNamespacesIsAllowed()` + `testDuplicateTopLevelDeclarationNameQuickFixRenamesToUniqueName()` + `testDuplicateNamespaceChildDeclarationNameQuickFixRenamesToUniqueName()` + `testDuplicateNameAttributePathLeafIsReported()` + `testDuplicateNameAttributePathLeafQuickFixKeepsPrefixAndRenamesLeaf()` + `testSameNameAttributeLeafInDifferentNamespacesIsAllowed()`                                                                                           |
| `DSYN-215` | `Implemented` | `DreamShaderSemanticDiagnosticsTest.testOptionalInputRequiresDefaultInInputsSection()` + `testOptionalInputWithDefaultDoesNotWarn()` + `testOptionalInputMalformedDefaultWarns()` + `testOptionalFunctionParameterRequiresDefault()`                                                                                                                                                                                                                                                                                    |

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
`Rule`: `Namespace` body may only contain `Function`, `GraphFunction`, or nested `Namespace`.  
`Invalid`:
```c
Namespace(Name="Bad") {
    ShaderFunction(Name="X") { }
}
```
`Expected`: `Namespace can only contain Function, GraphFunction, or Namespace declarations`  
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

28. `ID`: `DSYN-214`  
`Priority`: `P2`  
`Rule`: duplicate declaration names are not allowed within the same declaration scope (top-level scope and each `Namespace` local scope are checked independently); path-form `Name="Folder/Leaf"` declarations participate by leaf name.  
`Expected`: `Duplicate declaration name 'BuildNoise' in the same scope`  
`Note`: same declaration name is allowed across different namespace scopes.  
`QuickFix`: `Rename declaration to 'BuildNoise2'` (suggests a unique numeric-suffix name within current scope; for path-form `Name`, only leaf is replaced and prefix is preserved, for example `Functions/F_Blend` -> `Functions/F_Blend2`)  
`Test`: `testDuplicateTopLevelDeclarationNameIsReported()` / `testDuplicateNamespaceChildDeclarationNameIsReported()` / `testSameNameInDifferentNamespacesIsAllowed()` / `testDuplicateNameAttributePathLeafIsReported()` / `testDuplicateNameAttributePathLeafQuickFixKeepsPrefixAndRenamesLeaf()` / `testSameNameAttributeLeafInDifferentNamespacesIsAllowed()`

29. `ID`: `DSYN-215`  
`Priority`: `P2`  
`Rule`: `opt` inputs should provide a default value in `Inputs` sections and `Function` / `GraphFunction` signatures; obviously malformed defaults should warn while the parser remains permissive.  
`Invalid`:
```c
ShaderFunction MyFunction {
    Inputs = {
        opt float Strength;
    }
    Outputs = {
        float Out;
    }
    Graph = {
        Out = Strength;
    }
}
```
`Expected`: `Optional input 'Strength' should provide a default value`  
`Additional invalid`:
```c
Function ApplyTint(in float3 Color, opt float Strength) {
}
```
`Expected`: `Optional input 'Strength' should provide a default value`  
`Malformed default examples`: empty initializer, unclosed string, or unmatched `(`/`[`/`{` delimiters.  
`Test`: `testOptionalInputRequiresDefaultInInputsSection()` / `testOptionalInputWithDefaultDoesNotWarn()` / `testOptionalInputMalformedDefaultWarns()` / `testOptionalFunctionParameterRequiresDefault()`

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
`Test`: `testUnknownTypeInInputs()` / `testUnknownTypeQuickFixReplacesWithSuggestion()` / `testUnknownTypeInFunctionParameter()` / `testUnknownTypeInGraphFunctionParameter()` / `testUnknownTypeInNamespaceFunctionParameter()` / `testUnknownTypeInGraphLocalVariable()` / `testUnknownTypeInNamespaceFunctionLocalVariable()`

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
`Test`: `testMissingOutArgumentInFunctionCall()` / `testMissingOutArgumentQuickFixAddsPlaceholderArgument()` / `testMissingOutArgumentQuickFixAddsFirstArgumentForEmptyCall()` / `testMissingOutArgumentQuickFixAddsAllMissingOutArguments()` / `testMissingOutArgumentQuickFixAvoidsNameCollision()` / `testMissingOutArgumentQuickFixUsesConfiguredPlaceholderSuffix()` / `testMissingOutArgumentInNamespaceFunctionCall()` / `testMissingOutArgumentInNamespaceFunctionCallQuickFixAddsPlaceholderArgument()` / `testMissingOutArgumentResolvesUnqualifiedCallToNearestNamespaceScope()` / `testMissingOutArgumentDoesNotReportForTopLevelOverloadWhenNamespaceCallIsComplete()`

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
`QuickFix`: `Create missing import file: <path>` creates a missing file under project-safe relative path, defaults to `.dsh` when extension is omitted, uses `DreamShaderTemplateService` templates for generated content (`.dsh` header template, `.dsf` function template, `.dsm` material template), opens the created file in editor, and supports scoped package imports by creating under `DShader/Packages/@scope/name/...`; unsupported extensions provide `Change extension to .dsh/.dsf/.dsm`, with ordering rules `resolves existing target > preferred default extension setting > stable fallback order`, and label hints `(resolves existing file)` / `(preferred default)` / `(will update preferred default)` where applicable; `(will update preferred default)` is shown only when `autoUpdatePreferredImportExtension=true` and selected extension differs from current preferred default.  
`Test`: `testUnresolvedImportPath()` / `testUnresolvedImportPathQuickFixCreatesMissingFile()` / `testUnresolvedImportPathQuickFixCreatesFunctionTemplateForDsf()` / `testUnresolvedImportPathQuickFixCreatesShaderTemplateForDsm()` / `testUnresolvedScopedImportQuickFixCreatesFileUnderPackages()` / `testUnresolvedImportPathUnsupportedExtensionReportsExplicitError()` / `testUnresolvedImportPathUnsupportedExtensionQuickFixChangesToDsh()` / `testUnresolvedImportPathUnsupportedExtensionQuickFixOffersAllSupportedExtensions()` / `testUnresolvedImportPathUnsupportedExtensionQuickFixPrioritizesResolvableExtension()` / `testUnresolvedImportPathUnsupportedExtensionQuickFixMarksPreferredDefault()` / `testUnresolvedImportPathUnsupportedExtensionQuickFixAutoUpdatesPreferredExtensionWhenEnabled()` / `testUnresolvedImportPathUnsupportedExtensionQuickFixDoesNotAutoUpdatePreferredExtensionWhenDisabled()` / `testUnresolvedImportPathUnsupportedExtensionQuickFixWillUpdateHintHiddenWhenAlreadyPreferred()` / `testUnresolvedImportPathUnsupportedExtensionQuickFixHintOrderIsStable()`

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

### Milestone M5: Bridge Integration and Package Tooling

- [x] `P2` Detect project root and bridge directory resolution.
- [x] `P2` Read Unreal bridge diagnostic files and project manifests.
- [x] `P2` Tool window/panel for bridge diagnostics.
- [x] `P2` Actions: recompile current/all, clean generated shaders, refresh diagnostics, open diagnostic location.
- [x] `P2` Bridge-manifest-aware `UE.Expression(...)` completion (`materialExpressionManifestPath` + project bridge manifest + bundled fallback manifest).
- [x] `P2` Bridge/tooling settings (`projectRoot`, `materialExpressionManifestPath`, `showStatusBar`, `enableCodeLens`).
- [x] `P2` Status bar + CodeLens actions for DreamShader workflows (official IntelliJ daemon-bound Code Vision provider + inlay hints toggle).
- [x] `P2` Package store index support (multiple sources + deprecated single source compatibility).
- [x] `P2` Actions: install/update/remove/browse/open packages folder.
- [x] `P3` Add/remove package store index source commands.
- [x] `P3` Authoring/template commands (create package/material/function/header/sample files).
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

| ID         | Status        | Test mapping                                                                                                                                                                                                                                                |
|------------|---------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `DBRG-001` | `Implemented` | `DreamShaderBridgePathResolverTest.testAutoDetectsProjectRootFromActiveFile()`                                                                                                                                                                              |
| `DBRG-002` | `Implemented` | `DreamShaderBridgePathResolverTest.testProjectRootSettingOverridesAutoDetect()`                                                                                                                                                                             |
| `DBRG-003` | `Implemented` | `DreamShaderBridgeDiagnosticsTest.testBridgeDiagnosticNavigationToExactLocation()`                                                                                                                                                                          |
| `DBRG-004` | `Implemented` | `DreamShaderBridgeDiagnosticsTest.testRefreshBridgeDiagnosticsSyncsPanelAndEditor()`                                                                                                                                                                        |
| `DBRG-005` | `Implemented` | `DreamShaderBridgeActionsTest.testBridgeActionCommandSetAvailability()` and `testBridgeActionCommandSetReturnsExpectedSuccessAndErrorMessages()`                                                                                                            |
| `DBRG-101` | `Implemented` | `DreamShaderManifestCompletionTest.testUeExpressionClassCompletionWithManifestAndFallback()` + `DreamShaderSemanticDiagnosticsTest` (`testUnknownExpressionClassInUeExpressionCall`, `testUeExpressionClassRequired`, `testUeExpressionOutputTypeRequired`) |
| `DBRG-102` | `Implemented` | `DreamShaderManifestCompletionTest.testUsesConfiguredMaterialExpressionManifestPath()`                                                                                                                                                                      |
| `DBRG-103` | `Implemented` | `DreamShaderManifestCompletionTest.testInvalidManifestPathFallsBackGracefully()`                                                                                                                                                                            |
| `DBRG-104` | `Implemented` | `DreamShaderStatusBarVisibilityTest.testStatusBarVisibilitySetting()`                                                                                                                                                                                       |
| `DBRG-105` | `Implemented` | `DreamShaderSettingsToggleTest.testEnableCodeLensToggleControlsInlayHintsProviderOutput()` + `testCodeVisionRespectsEnableCodeLensSetting()`                                                                                                              |
| `DTPL-001` | `Implemented` | `DreamShaderTemplateCommandsTest.testCreateMaterialTemplateProducesValidDsm()`                                                                                                                                                                              |
| `DTPL-002` | `Implemented` | `DreamShaderTemplateCommandsTest.testCreateFunctionFileTemplateProducesValidDsf()`                                                                                                                                                                          |
| `DTPL-003` | `Implemented` | `DreamShaderTemplateCommandsTest.testCreateHeaderTemplateProducesValidDsh()`                                                                                                                                                                                |
| `DTPL-004` | `Implemented` | `DreamShaderTemplateCommandsTest.testCreatePackageScaffoldLayout()`                                                                                                                                                                                         |

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
`Rule`: `enableCodeLens` controls IntelliJ Code Vision hints, while `enableInlayParameterHints` controls DreamShader parameter-name inlay hints.
`Expected`: inlay hints and Code Vision hints appear/disappear independently without restart when their respective settings change
`Test`: `testEnableCodeLensToggleControlsInlayHintsProviderOutput()`, `testCodeVisionRespectsEnableCodeLensSetting()`

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

| ID         | Status        | Test mapping                                                                             |
|------------|---------------|------------------------------------------------------------------------------------------|
| `DPKG-001` | `Implemented` | `DreamShaderPackageIndexTest.testLoadsAndMergesMultipleIndexSources()`                   |
| `DPKG-002` | `Implemented` | `DreamShaderPackageIndexTest.testLegacySingleSourceIndexCompatibility()`                 |
| `DPKG-003` | `Implemented` | `DreamShaderPackageIndexTest.testParsesArrayAndObjectIndexShapes()`                      |
| `DPKG-004` | `Implemented` | `DreamShaderPackageIndexTest.testMalformedIndexDoesNotBreakOtherSources()`               |
| `DPKG-005` | `Implemented` | `DreamShaderPackageIndexTest.testResolvesLocalPathRelativeToIndexFile()`                 |
| `DPKG-006` | `Implemented` | `DreamShaderPackageIndexTest.testFallsBackToRepositoryWhenLocalPathMissing()`            |
| `DPKG-101` | `Implemented` | `DreamShaderPackageLifecycleTest.testInstallFromGithubShorthand()`                       |
| `DPKG-102` | `Implemented` | `DreamShaderPackageLifecycleTest.testInstallFromGithubUrl()`                             |
| `DPKG-103` | `Implemented` | `DreamShaderPackageLifecycleTest.testInstallFailsWhenMetadataMissing()`                  |
| `DPKG-104` | `Implemented` | `DreamShaderPackageLifecycleTest.testInstallFailsWhenNameMissing()`                      |
| `DPKG-105` | `Implemented` | `DreamShaderPackageLifecycleTest.testInstallWritesLockFileEntry()`                       |
| `DPKG-106` | `Implemented` | `DreamShaderPackageLifecycleTest.testUpdateRefreshesLockFileEntry()`                     |
| `DPKG-107` | `Implemented` | `DreamShaderPackageLifecycleTest.testRemovePackageAndPruneLockEntry()`                   |
| `DPKG-108` | `Implemented` | `DreamShaderPackageLifecycleTest.testRemoveNonInstalledPackageFailsClearly()`            |
| `DPKG-109` | `Implemented` | `DreamShaderPackageLifecycleTest.testOpenPackagesFolderCreatesWhenMissing()`             |
| `DPKG-110` | `Implemented` | `DreamShaderPackageLifecycleTest.testInstallFailsWhenGitUnavailable()`                   |
| `DPKG-201` | `Implemented` | `DreamShaderPackageImportResolutionTest.testResolvesScopedImportWithExtension()`         |
| `DPKG-202` | `Implemented` | `DreamShaderPackageImportResolutionTest.testResolvesScopedImportWithoutExtension()`      |
| `DPKG-203` | `Implemented` | `DreamShaderPackageImportResolutionTest.testImportResolutionPrecedenceForPackagePaths()` |
| `DPKG-301` | `Implemented` | `DreamShaderPackageStoreUiModelTest.testAddIndexSourceDeduplicates()`                    |
| `DPKG-302` | `Implemented` | `DreamShaderPackageStoreUiModelTest.testRemoveIndexSourceRefreshesStoreData()`           |
| `DPKG-303` | `Implemented` | `DreamShaderPackageStoreUiModelTest.testStoreSearchMatchesNameDescriptionAndTags()`      |
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

#### D-UI. Package Store Dialog UI Regression Matrix

24. `ID`: `DPKG-UI-001`  
`Priority`: `P2`  
`Rule`: action buttons in `Browse Package Store` react to selection and install state.  
`Expected`: installed entry disables install and enables remove/update (when updatable); uninstalled entry enables install and disables remove; no selection disables install/update/remove.  
`Test`: `DreamShaderPackageStoreDialogUiTest.testActionButtonsToggleBySelectionAndInstallState()`

25. `ID`: `DPKG-UI-002`  
`Priority`: `P2`  
`Rule`: `Installed only` and `Updates possible only` filters refresh list and keep button state coherent.  
`Expected`: filter toggles adjust list size deterministically; empty filtered list disables install/update/remove.  
`Test`: `DreamShaderPackageStoreDialogUiTest.testFiltersRefreshListAndActionButtons()`

26. `ID`: `DPKG-UI-003`  
`Priority`: `P2`  
`Rule`: search input + `Search` button refreshes list by query and can restore full list when query clears.  
`Expected`: keyword narrows results to matching entry; clearing query restores all entries from sources.  
`Test`: `DreamShaderPackageStoreDialogUiTest.testSearchFieldAndButtonRefreshListByQuery()`

27. `ID`: `DPKG-UI-004`  
`Priority`: `P2`  
`Rule`: GitHub search empty-query path should be non-mutating.  
`Expected`: search status `EMPTY_QUERY`; current list remains unchanged.  
`Test`: `DreamShaderPackageStoreDialogUiTest.testGitHubSearchEmptyQueryReturnsEmptyStatusAndDoesNotMutateList()`

28. `ID`: `DPKG-UI-005`  
`Priority`: `P2`  
`Rule`: GitHub search success and failure states mutate list only when appropriate.  
`Expected`: `APPLIED` replaces list with GitHub entries; `ERROR` keeps previous list.  
`Test`: `DreamShaderPackageStoreDialogUiTest.testGitHubSearchAppliedStatusRefreshesListEntries()` + `DreamShaderPackageStoreDialogUiTest.testGitHubSearchErrorStatusKeepsExistingList()`

29. `ID`: `DPKG-UI-006`  
`Priority`: `P2`  
`Rule`: sequential GitHub search state transitions preserve last good state on failure and support explicit no-results replacement behavior.  
`Expected`: `APPLIED -> ERROR` keeps last applied results; `APPLIED` with empty entries transitions to empty-list state with lifecycle buttons disabled.  
`Test`: `DreamShaderPackageStoreDialogUiTest.testGitHubSearchErrorAfterAppliedKeepsLastAppliedResults()` + `DreamShaderPackageStoreDialogUiTest.testGitHubSearchNoResultsReplacesListWithEmptyState()`

#### D-UI Audit Matrix (Checked on `2026-05-29`)

| ID             | Status        | Test mapping                                                                                                                                                                                                                                                           |
|----------------|---------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `DPKG-UI-001`  | `Implemented` | `DreamShaderPackageStoreDialogUiTest.testActionButtonsToggleBySelectionAndInstallState()`                                                                                                                                                                            |
| `DPKG-UI-002`  | `Implemented` | `DreamShaderPackageStoreDialogUiTest.testFiltersRefreshListAndActionButtons()`                                                                                                                                                                                       |
| `DPKG-UI-003`  | `Implemented` | `DreamShaderPackageStoreDialogUiTest.testSearchFieldAndButtonRefreshListByQuery()`                                                                                                                                                                                   |
| `DPKG-UI-004`  | `Implemented` | `DreamShaderPackageStoreDialogUiTest.testGitHubSearchEmptyQueryReturnsEmptyStatusAndDoesNotMutateList()`                                                                                                                                                            |
| `DPKG-UI-005`  | `Implemented` | `DreamShaderPackageStoreDialogUiTest.testGitHubSearchAppliedStatusRefreshesListEntries()` + `DreamShaderPackageStoreDialogUiTest.testGitHubSearchErrorStatusKeepsExistingList()`                                                                                     |
| `DPKG-UI-006`  | `Implemented` | `DreamShaderPackageStoreDialogUiTest.testGitHubSearchNoResultsReplacesListWithEmptyState()` + `DreamShaderPackageStoreDialogUiTest.testGitHubSearchErrorAfterAppliedKeepsLastAppliedResults()`                                                                        |
| `DPKG-UI-007`  | `Implemented` | `DreamShaderPackageStoreDialogUiTest.testGitHubSearchInProgressDisablesActionControls()`                                                                                                                                                                             |

#### E. Bridge/Project Diagnostics Interop

30. `ID`: `DPKG-401`  
`Priority`: `P2`  
`Rule`: package-related bridge diagnostics map to originating source path under `DShader/Packages`.  
`Expected`: diagnostic navigation opens exact file/line in installed package  
`Test`: `testBridgeDiagnosticsMapToInstalledPackageSource()`

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

#### M6 Audit Matrix (Checked on `2026-05-30`)

| Item                                                  | Status        | Evidence                                                                                                                                                                                                                                                                     |
|-------------------------------------------------------|---------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `P1` lexer/highlighter tests                          | `Implemented` | `DreamShaderLexerSyntaxHighlighterTest`                                                                                                                                                                                                                                      |
| `P1` completion regression tests                      | `Implemented` | `DreamShaderCompletionContextAnalyzerTest`, `DreamShaderCompletionSuggesterTest`                                                                                                                                                                                             |
| `P1` navigation/diagnostic tests                      | `Implemented` | `DreamShaderGotoDeclarationHandlerTest`, `DreamShaderFindReferencesTest`, `DreamShaderDeclarationRenameTest`, `DreamShaderFindUsagesProviderTest`, `DreamShaderDocumentationProviderTest`, `DreamShaderSignatureHelpAnalyzerTest`, `DreamShaderImportClosureResolverTest`, `DreamShaderSyntaxDiagnosticsTest`, `DreamShaderSectionShapeDiagnosticsTest`, `DreamShaderSemanticDiagnosticsTest` (including function/graph-function declaration-parameter unknown-type diagnostics, namespace-child declaration-parameter unknown-type diagnostics, graph/function-body local unknown-type diagnostics, namespace-child function-body local unknown-type diagnostics, namespace-child missing-`out`-argument diagnostics and quick-fix coverage, unqualified same-name call namespace-scope precedence for missing-`out` diagnostics, const-texture default-asset path/root validation, unknown-root `Replace Path root with Game` quick-fix for both `Path(...)` and quoted paths, leading-slash quoted-root extraction regression coverage, `Path(rootOnly)` rejection coverage, `Complete Path(...) with object segment` quick-fix coverage, and quick-fix regression coverage) |
| `P1` package store UI regression tests                | `Implemented` | `DreamShaderPackageStoreDialogUiTest` (`DPKG-UI-001`..`DPKG-UI-007`: action-button state, filter toggles, query search, GitHub search `EMPTY_QUERY`/`APPLIED`/`ERROR`, no-results state, `APPLIED -> ERROR` sequence retention, and in-progress action-disablement)        |
| `P1` localization coverage guard                       | `Implemented` | `DreamShaderBundleLocalizationTest` (base bundle keys must exist in `DreamShaderBundle_zh_CN.properties`)                                                                                                                                                                    |
| `P2` large-file performance smoke tests               | `Implemented` | `DreamShaderLargeFilePerformanceSmokeTest`                                                                                                                                                                                                                                   |
| `P3` Marketplace metadata/signing/publishing pipeline | `Implemented` | `build.gradle.kts` (`pluginConfiguration`, `signing`, `publishing`, file-based signing env/properties + legacy env fallback), `.github/workflows/build.yml`, `.github/workflows/release.yml` (writes cert/key secrets to temp files and publishes with file-path env)        |
| `P3` changelog aligned with release tags              | `Implemented` | `CHANGELOG.md` / `CHANGELOG.zh-CN.md` + current-version release note extraction in `.github/workflows/build.yml`                                                                                                                                                              |

### Milestone M7: Material Preview and Authoring

- [x] `P1` Add Material Preview action and right-side ToolWindow.
- [x] `P1` Use the portable file-bridge contract: write `Saved/DreamShader/Bridge/Requests/request-*.json`, read `preview.json`, and render `Bridge/Preview/*.png`.
- [x] `P1` Add preview auto-refresh delay setting. `previewTransport`, `previewWebSocketPort`, and `previewLiveFrameRate` are stored for future use; WebSocket streaming is intentionally not implemented in this milestone.
- [x] `P2` Add Texture Sample and Noise Material authoring templates.
- [x] `P2` Add Create Package Step by Step wizard that writes package metadata and can omit examples.

Acceptance criteria:
- `Tools | DreamShader | Show Material Preview` opens the preview panel and writes a valid preview request for the active `.dsm`.
- Bridge writes to `preview.json` / `Preview/*.png` refresh the panel without manual reload.
- Template and package wizard commands generate valid on-disk scaffolds and are covered by tests.

### Changelog Localization

- Changelog is split into two files:
  - English: `CHANGELOG.md`
  - Chinese: `CHANGELOG.zh-CN.md`
- Plugin welcome page ("What Changed In This Version") auto-selects changelog language by IDE/runtime locale:
  - `zh*` locale -> `CHANGELOG.zh-CN.md` (fallback to English if unavailable)
  - other locales -> `CHANGELOG.md`

### Changelog Versioning Policy

- Between version bumps, record new updates under the current `gradle.properties` version section (for example, keep adding to `0.0.5` until `0.0.6` is actually created).
- Do not keep an `Unreleased` section. The first versioned changelog section is the active development log until the next version bump.
- Use Git commit history as the source of truth for detailed update chronology before each version bump.
