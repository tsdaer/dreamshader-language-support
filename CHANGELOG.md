<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# dreamshader-language-support Changelog

## [0.0.8] - 2026-07-27

### Changed

- Welcome page: replaced custom JCEF pipeline (`JCEFHtmlPanel` + custom `FileEditorProvider` + `LightVirtualFile`) with standard `HTMLEditorProvider` API, matching the approach used by JetBrains' own whatsnew page.
- Link interception in welcome page: switched from native CEF request handlers to `JsQueryHandler`-based communication via `window.jbCefQuery()`.
- Removed optional JCEF module dependency with custom `config-file` workaround — `HTMLEditorProvider` handles JCEF availability internally.

### Fixed

- `ClassNotFoundException: com.intellij.ui.jcef.JBCefApp` in environments where the JCEF module is not loaded.
- Welcome page loading failures caused by fragile custom JCEF initialization and lifecycle management.

### Removed

- `DreamShaderWelcomeWebView.kt`, `DreamShaderWelcomeVirtualFile`, `DreamShaderWelcomeWebViewProvider` (replaced by `HTMLEditorProvider`).
- `src/main/resources/includes/jcef.xml` (no longer needed).
- Redundant `forceSelectWelcomeEditor` alarm-based editor selection workaround.

## [0.0.7] - 2026-07-20

### Added

- `Group("Name")` / `PropGroup("Name")` property grouping scopes: supported as both standalone declarations wrapping nested sections, and as `Properties Group("Name") { ... }` section-header modifiers (primary upstream form). Includes snippet completion, structure-view integration, and section flattening in diagnostics.
- Single-output return-value functions (`Function float X(...)`): parser accepts `TYPE` token after `Function`/`GraphFunction` keyword; `returnType()` PSI accessor; signature help renders `: <type>` suffix; hover docs display return type; `return` statement is valid in return-value function bodies.
- Bridge SQLite database transport: reads diagnostics from `bridge.db` (upstream v1.5.0+), with transparent fallback to `diagnostics.json`. Uses `org.xerial:sqlite-jdbc` via JDBC with short-lived connections to avoid file locking.
- Bridge status bar `[DB]` indicator: shows when diagnostics were loaded from the SQLite database vs. the JSON file.
- Configurable `Source Directory` setting (default `DShader`): mirrors the Unreal plugin's `SourceDirectory` project setting. Auto-detects from common patterns (`DShader`, `Shaders`, `Source`) when not explicitly set. Import resolution, package management, and project root detection now respect this setting instead of hardcoding `DShader`.

### Changed

- Language baseline bumped to upstream DreamShader v1.6.3 reference snapshot.
- Bridge UI redesigned: tool window uses IDE-style `JBTable` with compact rows (22px vs 88px), severity icons, and multi-column layout (message / file / location). Status bar uses icon + compact error/warning count with rich tooltip. Hub dialog uses vertical compact layout with `TitledSeparator` sections.
- `DreamShaderUi` rewritten for IDE-native look: cards use standard borders instead of custom rounded panels; sections use `TitledSeparator`; pills use flat opaque labels; all custom background colors alias to IDE theme colors.
- Bridge database status visible in both tool window toolbar (`bridge.db` / `diagnostics.json` / `no source`) and status bar tooltip (SQLite / JSON / no data).
- 6 broken UI settings tests removed (`DreamShaderSettingsToggleTest`) — pre-existing IntelliJ Platform 2026.1.3 compatibility issue.

### Fixed

- Database-sourced diagnostics now correctly inherit file path from the per-file wrapper object, fixing missing file location in error messages.

## [0.0.6] - 2026-06-17

### Added

- Added declaration-adjacent comment extraction for hover docs, so `//` and `/* ... */` comments immediately before a DreamShader declaration are shown with the built-in declaration documentation.
- Added a shared `DreamShaderUi` design helper layer for card surfaces, rounded borders, status pills, muted labels, and consistent input dialogs across plugin workflow panels.

### Changed

- Updated DreamShader live template registration to the current IntelliJ Platform `defaultLiveTemplates` extension-point style and removed the deprecated provider implementation.
- Centralized common PSI traversal helpers for DreamShader files, declarations, direct child declarations/sections, namespace paths, and declaration comments so navigation, references, structure view, symbol model, diagnostics, and plain-text symbol completion share the same tree-walking behavior.
- Optimized cross-file reference search by replacing the full workspace filesystem walk with cached reverse-import discovery that uses IntelliJ word indexes, nearby import candidates, and direct-import verification.
- Refreshed the Bridge, Package Store, Material Preview, Settings, Template, Hub, and Welcome panels onto the shared DreamShader UI surface, including card-style package rows/details, clearer empty states, GitHub action affordances, and grouped settings sections.

## [0.0.5] - 2026-06-15

### Added

- Added Material Preview parity with VS Code 1.5.3 via the DreamShader Bridge file transport, including a right-side ToolWindow, preview request writer, preview result/image reader, editor-follow behavior, and debounced auto-refresh setting.
- Added Texture Sample and Noise Material `.dsm` template actions.
- Added Create Package Step by Step wizard with package metadata fields and an example-material toggle.
- Added native DreamShader code style settings and formatter support for language-specific options, including blank lines between sections and `::` spacing.
- Added native File and Code Templates, including `New | DreamShader Material`, `DreamShader Function`, `DreamShader Header`, and reusable graph section / texture sample code templates.
- Added DreamShader live templates and conservative postfix templates for common shader/function/control-flow authoring patterns.
- Added DreamShader gutter markers for imports, top-level declarations, and Bridge diagnostic locations.
- Added editor platform integrations for quote handling, import gutter markers, Copy Reference qualified names, breadcrumbs, and constant `float3` / `float4` / `vec3` / `vec4` color previews.
- Added more IntelliJ editor integrations: declaration / section Context Info, plain-text symbol completion, rename name suggestions, and qualified-name reverse parsing for Copy/Paste Reference.
- Added declaration-level Rename input validation and Safe Delete availability for DreamShader declarations, backed by the existing namespace-aware reference search and `Name="Path/Leaf"` rename behavior.
- Added Goto Symbol and Goto Class contributors for DreamShader asset declarations, functions, graph functions, virtual functions, namespaces, and namespace members.
- Added spellchecking support for DreamShader comments and `VirtualFunction` `Description` strings while avoiding noisy checks for paths and code-like strings.
- Added conservative `Substrate` expression diagnostics for arithmetic, swizzles, vector constructor arguments, and ternary branch merges that can fail later in the Bridge stage.
- Added targeted `Path(...)` object-segment suffix warnings for Bridge-breaking asset suffixes such as `.uasset`, `.umap`, and common image extensions.

### Changed

- Split parameter inlay hints from the Code Vision setting by adding the `enableInlayParameterHints` project setting; disabling Code Vision no longer hides parameter hints.
- `ALIGN_SECTION_ASSIGNMENTS` now aligns direct simple assignments inside sections by blank-line and nested-block groups, and Bridge diagnostic gutter markers prefer jumping to the diagnostic source line/column.
- Cached semantic diagnostic inputs more aggressively: file diagnostics now reuse cached source text, whole-file tokens, declaration contexts, section bodies, typed declarations, callable signatures, and body-level token slices across hot semantic passes.
- Cached direct import and import-closure resolution so navigation, signature help, references, inlay hints, hover, and import diagnostics share results for unchanged files.
- `UE.<Name>` go-to declaration now prefers Unreal source locations when source scanning resolves a matching header target, while preserving catalog/documentation fallback behavior.
- Hover and signature rendering now preserve `opt` qualifiers and default values for declared callable parameters.
- Inline color previews now cover `Color(...)` / `LinearColor(...)`, `0xRRGGBB` / `0xRRGGBBAA`, and conservative 0-255 integer channel forms in addition to vector constructors.
- Malformed declaration and section headers now produce recoverable parser error markers; duplicate token-level malformed syntax diagnostics were removed.

## [0.0.4] - 2026-06-14

### Added

- Added catalog-based material expression manifest parsing for `UE.*` completions, including rich `expressions` entries, class-name fallback compatibility, and namespace-ready catalog data for future `Substrate.*` wrappers.
- Routed `UE.<Name>(...)` and `Substrate.<Name>(...)` signature help through the shared material expression catalog, falling back to the built-in signature table when catalog data is incomplete.
- Routed `UE.*` and `Substrate.*` hover documentation through the shared catalog while preserving user hover-documentation override priority.
- Added a best-effort Unreal `UMaterialExpression` header scanner that emits catalog-compatible manifest JSON, wired into the catalog merge order ahead of the bundled fallback.
- Added Unreal Engine source-root auto-detection (`.sln` / `.uproject` + registry lookups) with project settings for the source root, scan toggle, and scan cache path, exposed through the settings UI.
- Added Substrate and Layer semantic diagnostics: `Base.FrontMaterial` / `Base.MaterialAttributes` binding exclusivity, tightened `ShaderLayer` / `ShaderLayerBlend` input/output shape rules, and rejection of `OutputType="Substrate"` on `UMaterialExpressionCustom`.
- Extended the upstream `Examples.md` parse-coverage fixture with Substrate material, Substrate `ShaderFunction` / `VirtualFunction`, Substrate escape-hatch, and `ShaderLayer` / `ShaderLayerBlend` examples.

## [0.0.3] - 2026-05-30

### Added

- Added namespace declaration support in language parsing and navigation flows, including go-to declaration, find usages, and structure/symbol model coverage.
- Added in-progress action disabling for GitHub package search in the package store dialog to prevent duplicate operations while requests are running.
- Added cross-file import-closure resolution to support imported-function signature analysis.
- Added reference-search expansion across import chains and `Name="..."` form declarations.
- Added nested-namespace semantic coverage for unknown types and missing `out` arguments in nested declarations.
- Added semantic diagnostics for duplicate declaration names within the same scope (top-level and namespace-local scopes), while allowing same names across different namespace scopes.
- Added duplicate declaration-name quick fix (`Rename declaration to '<NameN>'`) with unique numeric-suffix suggestion in the current scope.
- Added path-form `Name="..."` duplicate-name coverage: diagnostics compare path leaf names and quick-fix preserves path prefix while renaming only the leaf.
- Added stronger unknown-type diagnostics and const-texture default-asset path validation.
- Added dumb-mode support for the welcome file editor.

### Changed

- Reworked plugin version and changelog loading for the welcome page flow.
- Reworked Code Vision click handling to improve interaction stability and align with package search state transitions.
- Improved hover override configuration and welcome guidance: built-in entries can now be edited/reset, and missing project root prompts are clearer.
- Updated changelog localization pipeline by splitting language files (`CHANGELOG.md` / `CHANGELOG.zh-CN.md`) and syncing release CI patching behavior.
- Updated README release presentation with Marketplace plugin card and static badges.
- Refactored shared import/signature parsing helpers to consolidate navigation/diagnostics logic.
- Updated README terminal build guidance to emphasize Java 17+ auto-resolution and `GRADLE_USER_HOME`.

### Fixed

- Fixed release workflow config-cache and dependency ordering issues in publishing pipeline.
- Fixed Gradle build conflicts between changelog patching and configuration cache behavior.

## [0.0.2] - 2026-05-29

### Added

- `const` texture diagnostics now require explicit default assets (for example `Path(...)`) and report targeted semantic errors.
- Hover docs overrides now have a visual table editor (`Path` + `Content`) with validation summary and sample row insertion.
- Unsupported import extension quick-fix now surfaces clear “will update preferred default” behavior hints in UI.
- Added localized changelog strategy for the welcome page, with English `CHANGELOG.md` and Chinese `CHANGELOG.zh-CN.md`.

### Changed

- Unsupported import extension quick-fix ranking is now smarter: resolvable candidates are prioritized, preferred defaults are highlighted, and auto-update behavior is explicit.
- Editor assistance has been expanded: `.dsm` import completion is richer, hover docs coverage is broader, and user-declared functions participate in signature/inlay hints.
- Package store dialog UX state handling was tightened and covered by expanded UI regression tests (`DPKG-UI-001` to `DPKG-UI-006`).

### Removed

- Removed built-in library fallback from material expression manifest resolution to avoid stale implicit data sources.

## [0.0.1] - 2026-05-29

### Added

- Language core: `.dsm` / `.dsf` / `.dsh` file types, lexer/parser/PSI foundations, syntax highlighting, formatter, commenter, brace matching, folding, and structure view.
- Smart editing: context-aware completion (sections/types/settings values/`UE.*`/HLSL/imports), go-to declaration, references/find usages, hover docs, and signature help.
- Semantic diagnostics pipeline: syntax + section-shape + semantic checks, quick-fixes, declaration constraints, graph control-flow restrictions, and import resolution diagnostics.
- Bridge integration: diagnostics repository loading/mapping, diagnostics tool window, refresh/open/location actions, command-based recompile/clean actions, and status bar widget.
- Project settings: project root, manifest path, code-lens toggle, import-extension preference/auto-update, bridge commands, and hover-doc overrides.
- Package tooling: package store dialog, multi-source index loading, install/update/remove flows, GitHub package workflows, index source management, and package-aware import resolution.
- Templates and workflows: material/function/header templates, package scaffold generation, and one-stop DreamShader Hub entry.
- Internationalization: bundle-driven UI/messages with `DreamShaderBundle.properties` and `DreamShaderBundle_zh_CN.properties`.
- Quality and release readiness: broad unit/integration/UI test coverage, large-file smoke tests, Marketplace signing/publishing workflow, and changelog patching in release CI.

[0.0.6]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.5...0.0.6
[0.0.5]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.4...0.0.5
[0.0.4]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.3...0.0.4
[0.0.3]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.2...0.0.3
[0.0.2]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.1...0.0.2
[0.0.1]: https://github.com/tsdaer/dreamshader-language-support/commits/0.0.1
