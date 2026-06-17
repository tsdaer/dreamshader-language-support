# DreamShader Rider Plugin Architecture

## Scope

This document describes the main framework layers, core data models, and
runtime data flow of `dreamshader-language-support`.

It is intended for maintainers who need to extend language features, diagnostics,
Bridge integration, and package tooling without breaking existing behavior.

## Layered Design

### 1. Language Front-End (Lexer + Parser + PSI)

- `DreamShaderLexer`: tokenization for `.dsm/.dsf/.dsh`.
- `DreamShaderPsiParser`: permissive parser that always tries to recover and
  produce declaration/section nodes.
- `DreamShaderParserDefinition`: IntelliJ wiring for lexer/parser/PSI creation.
- `DreamShaderPsiUtil`: shared PSI traversal helpers for DreamShader files,
  declaration lists, direct child declarations/sections, namespace paths, and
  declaration-adjacent comments.

Design goal:
- Preserve usable PSI under partially invalid code so completion/diagnostics
  still work while users type.

### 2. Structural Model (Symbols)

- `DreamShaderSymbolModelBuilder`: builds top-level declaration + section symbol
  trees from PSI or from raw text parse fallback.

Used by:
- Structure view
- Navigation helpers

### 3. Editor Intelligence

- Completion:
  - `DreamShaderCompletionContextAnalyzer`
  - `DreamShaderCompletionSuggester`
  - `DreamShaderCompletionContributor`
- Signature help / parameter info:
  - `DreamShaderSignatureHelpAnalyzer`
  - `DreamShaderParameterInfoHandler`
- Inlay parameter hints:
  - `DreamShaderInlayParameterHintsProvider`
- Code Vision:
  - `DreamShaderCodeVisionProvider`

Design goal:
- Keep domain logic in analyzers/suggesters and use IntelliJ handlers as thin
  adapters.

### 4. Navigation and References

- Shared import graph helper:
  - `DreamShaderImportClosureResolver`
  - provides direct import-edge resolution and forward recursive import closure for navigation/signature/reference features.
- `DreamShaderGotoDeclarationHandler`
  - import string -> resolved target file
  - identifier -> top-level declaration target
- `DreamShaderReferencesSearchExecutor`
  - namespace-aware reference search for declarations across import-connected files
  - reuses `DreamShaderImportClosureResolver.resolveDirectImports(...)` to build forward import edges, then augments with cached reverse importer edges to preserve connected-closure reference search semantics without walking the full workspace filesystem
  - reverse importer discovery uses IntelliJ string-word indexes plus same-directory import candidates, then verifies each candidate through direct-import resolution
- `DreamShaderFindUsagesProvider`
  - Find Usages scanner and display metadata
- `DreamShaderNamesValidator` / `DreamShaderRenameInputValidator`
  - declaration-level Rename input validation that follows DreamShader identifier and keyword rules, including `Name="Path/Leaf"` declarations whose path prefix is preserved by PSI rename
- `DreamShaderRefactoringSupportProvider`
  - declaration-level Safe Delete availability backed by the shared reference search model
- `DreamShaderPsiUtil`
  - centralizes common PSI tree walking used by navigation, references, structure view, symbol model, diagnostics, and plain-text symbol completion

Current boundary:
- References are lightweight and computed within the import-connected closure (source file + files it imports/importers across recursive chains), then constrained by IDE/user-provided search scope (`GlobalSearchScope` / `LocalSearchScope`), not full workspace/global semantic indexing.

### 5. Diagnostics Pipeline

- Entry point: `DreamShaderSemanticAnnotator`
- Diagnostic groups:
  - syntax-level checks
  - section-shape checks
  - semantic checks
  - Bridge diagnostics mapping

Design goal:
- Keep one central annotator pass as the source of truth for user-visible diagnostics.

### 6. Bridge Integration

- `DreamShaderBridgePathResolver`
  - resolves project root + `Saved/DreamShader/Bridge` path
- `DreamShaderBridgeDiagnosticsRepository`
  - reads/normalizes `diagnostics.json`
  - exposes immutable snapshots
- `DreamShaderBridgeToolWindowFactory`
  - renders the Bridge diagnostics tool window.
- `DreamShaderBridgeActions` / `DreamShaderBridgeCommandExecutor`
  - refresh/open Bridge diagnostics and run configurable recompile/clean commands.
- `DreamShaderBridgeFileWatcher`
  - listens for Bridge output file changes and refreshes diagnostics/preview consumers.
- `DreamShaderMaterialExpressionManifest`
  - parses legacy `classes` payloads and rich `expressions` payloads into catalog entries.
  - derives missing `ueName`, signature, namespace, and neutral descriptions when manifest data is partial.
- `DreamShaderMaterialExpressionCatalog`
  - shared data source for `UE.*` and `Substrate.*` completion, signature help, hover docs, and expression-class diagnostics.
  - supports `namespace + member` lookup and preserves `expressionClassNames()` compatibility for older call sites.
  - material expression data merge order:
    1. explicit settings path
    2. Bridge manifest
    3. scanned Unreal source cache
    4. bundled fallback resource
    5. migration fallback built-ins
- `DreamShaderMaterialExpressionScanner`
  - best-effort Unreal header scanner for `UMaterialExpression*` classes.
  - extracts display names, input-like properties, nearby comments, and optional output type data when easily available.
  - writes manifest-compatible JSON so scanner output, Bridge data, explicit manifests, and bundled data use the same parser path.
- `DreamShaderUnrealSourceLocator`
  - auto-detects Unreal source roots from generated `.sln` references or `.uproject` `EngineAssociation`.
  - prefers narrow material-expression source directories under `Engine/Source` when available.

### 7. Package Tooling and Index Data Layer

- Data models:
  - `DreamShaderPackageIndexEntry`
  - `DreamShaderPackageInstallSource`
  - `DreamShaderPackageIndexLoadResult`
- Loader:
  - `DreamShaderPackageIndexLoader`
  - source resolution order:
    1. `packageStoreIndexUrls`
    2. `packageStoreIndexUrl` (legacy)
    3. default upstream URL
  - supported index payloads:
    - array root
    - object root with `packages` field
  - local `path` fallback policy:
    - if resolvable and exists: use local path
    - otherwise: fallback to repository URL
- Tooling flows:
  - package store dialog with search/filter/details/install/update/remove/source management
  - GitHub package search/install/update/remove actions
  - package wizard and scaffold actions for authoring starter packages

### 8. Material Preview

- `DreamShaderMaterialPreviewToolWindowFactory`
  - creates the right-side Material Preview tool window.
- `DreamShaderMaterialPreviewPanel`
  - follows the active `.dsm` editor file, writes preview requests, reads preview results/images, and debounces edit-triggered refreshes.
  - uses JCEF when available, with a Swing image fallback.
- `DreamShaderPreviewRequestWriter`
  - writes portable file-bridge request JSON under `Saved/DreamShader/Bridge/Requests/`.
- `DreamShaderPreviewResultReader`
  - reads `preview.json` and resolves relative image paths under the Bridge directory.
- `DreamShaderPreviewListener`
  - message-bus topic used by Bridge file watching to refresh preview UI after Bridge output changes.

Current boundary:
- File transport is implemented. `previewTransport`, `previewWebSocketPort`, and `previewLiveFrameRate` are persisted for parity/future use, but WebSocket/live-stream preview transport is not implemented.

### 9. UI Design System and Workflow Surfaces

- `DreamShaderUi`
  - shared Swing helper layer for card panels, sections, rounded borders, pills, muted labels, button rows, and input dialogs.
  - keeps workflow panels visually consistent while still using standard IntelliJ Swing components and theme-aware colors.
- Main consumers:
  - Settings configurable
  - DreamShader Hub action/panel
  - Bridge diagnostics tool window
  - Material Preview tool window
  - Package Store dialog
  - Template and package wizard dialogs
  - Welcome page

Design goal:
- Keep workflow UI composition consistent and readable without duplicating layout, spacing, border, and theme-color rules in each feature package.

### 10. Welcome / What's New Page

- `DreamShaderWelcomeProjectActivity`
  - opens a localized welcome page on first install or plugin update.
- `DreamShaderWelcomeStateService`
  - stores the last shown version and decides whether the page should open.
- `DreamShaderWelcomeFileEditorProvider`
  - renders a custom read-only welcome editor.
- Welcome content is built from the bundled plugin version metadata plus localized changelog resources (`CHANGELOG.md` / `CHANGELOG.zh-CN.md`).

### 11. Persistent Settings

- `DreamShaderProjectSettings` (project-level service)
- current keys:
  - `projectRoot`
  - `materialExpressionManifestPath`
  - `unrealEngineSourceRoot`
  - `materialExpressionScanEnabled`
  - `materialExpressionScanCachePath`
  - `showStatusBar`
  - `enableCodeLens`
  - `enableInlayParameterHints`
  - `outArgumentPlaceholderSuffix`
  - `preferredImportExtension`
  - `autoUpdatePreferredImportExtension`
  - `packageStoreIndexUrls`
  - `packageStoreIndexUrl` (legacy)
  - `packageStoreGitHubToken`
  - `hoverDocumentationOverrides`
  - `bridgeRecompileCurrentCommand`
  - `bridgeRecompileAllCommand`
  - `bridgeCleanGeneratedShadersCommand`
  - `previewTransport`
  - `previewWebSocketPort`
  - `previewLiveFrameRate`
  - `previewAutoRefreshDelayMs`

## High-Level Runtime Flow

1. User edits file -> lexer/parser produce PSI.
2. Completion/signature/inlay consume PSI/text context.
3. Annotator computes diagnostics and overlays Bridge diagnostics.
4. Navigation/references map identifiers/imports to declarations/files.
5. Package source settings feed package index loader when store features run.
6. Workflow panels compose standard IntelliJ controls through `DreamShaderUi` surfaces for consistent settings, package, preview, Bridge, template, hub, and welcome UX.
7. Welcome state compares the bundled plugin version with the last shown version and opens localized changelog content when needed.

## Extension Guidelines

1. Keep parsing permissive unless grammar hardening is explicitly required.
2. Add new domain checks in `DreamShaderSemanticAnnotator` first, then tests.
3. Keep UI handlers thin; place business logic in analyzers/loaders.
4. Preserve fallback chains (PSI -> parsed text -> lexer, explicit setting -> project -> bundled).
5. Add stable tests for every new rule/loader behavior before wiring UI actions.
6. Reuse `DreamShaderUi` for new workflow panels instead of introducing one-off Swing spacing, border, and color rules.

## README Architecture Snapshot

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
- Signature help, parameter info, and inlay hints consume call-signature parsing utilities; Code Vision is implemented separately through `DreamShaderCodeVisionProvider`.
- Hover docs include built-in declaration docs, catalog-backed `UE.*` / `Substrate.*` docs, local variable info, and adjacent declaration comments.
- Navigation/references rely on declaration symbol extraction, shared PSI traversal helpers, and identifier matching.
- Cross-file reference search expands through import-connected files using cached direct imports and cached reverse-importer discovery instead of a full filesystem scan.

4. Diagnostics pipeline
- `DreamShaderSemanticAnnotator` is the central diagnostic entry and aggregates:
- Syntax diagnostics
- Section-shape diagnostics
- Semantic diagnostics
- Bridge diagnostics mapped back to source ranges

5. Bridge integration
- `DreamShaderBridgePathResolver` resolves project root and Bridge folder with explicit-setting-first fallback.
- `DreamShaderBridgeDiagnosticsRepository` loads and normalizes Bridge diagnostics snapshots.
- `DreamShaderMaterialExpressionCatalog` merges explicit manifests, Bridge manifests, scanned cache data, bundled fallback data, and migration fallback built-ins for shared `UE.*` / `Substrate.*` editor intelligence.

6. Package tooling and index data layer
- `DreamShaderPackageIndexLoader` resolves package index sources (multi-source, legacy single-source, default upstream).
- Index loader accepts both JSON shapes: array root and `{ "packages": [...] }`.
- Entry `path` is resolved relative to local index location; unresolved paths degrade to `repository`.
- Package store and package authoring actions cover browse/search/install/update/remove/source management plus guided package wizard/scaffold flows.

7. Material Preview
- `DreamShaderMaterialPreviewPanel` writes Bridge file-transport preview requests for the active `.dsm`, reads `preview.json` / `Preview/*.png`, and refreshes on editor or Bridge-output changes.

8. Project-level persistent settings
- `DreamShaderProjectSettings` stores project-scoped configuration for Bridge and package tooling.
- Current keys: `projectRoot`, `materialExpressionManifestPath`, `unrealEngineSourceRoot`, `materialExpressionScanEnabled`, `materialExpressionScanCachePath`, `showStatusBar`, `enableCodeLens`, `enableInlayParameterHints`, `outArgumentPlaceholderSuffix`, `preferredImportExtension`, `autoUpdatePreferredImportExtension`, `packageStoreIndexUrls`, `packageStoreIndexUrl`, `packageStoreGitHubToken`, `hoverDocumentationOverrides`, `bridgeRecompileCurrentCommand`, `bridgeRecompileAllCommand`, `bridgeCleanGeneratedShadersCommand`, `previewTransport`, `previewWebSocketPort`, `previewLiveFrameRate`, `previewAutoRefreshDelayMs`.

9. Shared workflow UI
- `DreamShaderUi` provides theme-aware card, section, pill, rounded-border, input-dialog, and surface helpers used by the package store, settings, Bridge, preview, template, hub, and welcome panels.

10. Welcome / What's New
- First install, update, and manual Hub flows can open the localized welcome editor using bundled plugin version/changelog resources.

Related docs:
- [`code-map.md`](code-map.md) for source entry points.
- [`roadmap.md`](roadmap.md) for milestone status and implementation planning.
