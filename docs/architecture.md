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
  - reuses `DreamShaderImportClosureResolver.resolveDirectImports(...)` to build forward import edges, then augments with reverse importer edges to preserve connected-closure reference search semantics
- `DreamShaderFindUsagesProvider`
  - Find Usages scanner and display metadata

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

### 7. Package Index Data Layer (M5 Base)

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

### 8. Persistent Settings

- `DreamShaderProjectSettings` (project-level service)
- current keys:
  - `projectRoot`
  - `materialExpressionManifestPath`
  - `showStatusBar`
  - `enableCodeLens`
  - `packageStoreIndexUrls`
  - `packageStoreIndexUrl` (legacy)

## High-Level Runtime Flow

1. User edits file -> lexer/parser produce PSI.
2. Completion/signature/inlay consume PSI/text context.
3. Annotator computes diagnostics and overlays Bridge diagnostics.
4. Navigation/references map identifiers/imports to declarations/files.
5. Package source settings feed package index loader when store features run.

## Extension Guidelines

1. Keep parsing permissive unless grammar hardening is explicitly required.
2. Add new domain checks in `DreamShaderSemanticAnnotator` first, then tests.
3. Keep UI handlers thin; place business logic in analyzers/loaders.
4. Preserve fallback chains (PSI -> parsed text -> lexer, explicit setting -> project -> bundled).
5. Add stable tests for every new rule/loader behavior before wiring UI actions.

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
- `DreamShaderMaterialExpressionCatalog` merges explicit manifests, Bridge manifests, scanned cache data, bundled fallback data, and migration fallback built-ins for shared `UE.*` / `Substrate.*` editor intelligence.

6. Package index data layer (M5 completed)
- `DreamShaderPackageIndexLoader` resolves package index sources (multi-source, legacy single-source, default upstream).
- Index loader accepts both JSON shapes: array root and `{ "packages": [...] }`.
- Entry `path` is resolved relative to local index location; unresolved paths degrade to `repository`.

7. Project-level persistent settings
- `DreamShaderProjectSettings` stores project-scoped configuration for Bridge and package tooling.
- Current keys: `projectRoot`, `materialExpressionManifestPath`, `unrealEngineSourceRoot`, `materialExpressionScanEnabled`, `materialExpressionScanCachePath`, `showStatusBar`, `enableCodeLens`, `outArgumentPlaceholderSuffix`, `preferredImportExtension`, `autoUpdatePreferredImportExtension`, `packageStoreIndexUrls`, `packageStoreIndexUrl`, `packageStoreGitHubToken`, `bridgeRecompileCurrentCommand`, `bridgeRecompileAllCommand`, `bridgeCleanGeneratedShadersCommand`.

Related docs:
- [`code-map.md`](code-map.md) for source entry points.
- [`roadmap.md`](roadmap.md) for milestone status and implementation planning.
