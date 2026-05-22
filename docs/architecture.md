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

- `DreamShaderGotoDeclarationHandler`
  - import string -> resolved target file
  - identifier -> top-level declaration target
- `DreamShaderReferencesSearchExecutor`
  - file-local reference search for top-level declarations
- `DreamShaderFindUsagesProvider`
  - Find Usages scanner and display metadata

Current boundary:
- References are intentionally lightweight and file-local.

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
  - completion data merge order:
    1. explicit settings path
    2. Bridge manifest
    3. bundled fallback resource

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
