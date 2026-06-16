<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# dreamshader-language-support Changelog

## [0.0.5] - 2026-06-15

### Added

- Added Material Preview parity with VS Code 1.5.3 via the DreamShader Bridge file transport, including a right-side ToolWindow, preview request writer, preview result/image reader, editor-follow behavior, and debounced auto-refresh setting.
- Added Texture Sample and Noise Material `.dsm` template actions.
- Added Create Package Step by Step wizard with package metadata fields and an example-material toggle.
- Added native DreamShader code style settings and formatter support for language-specific options, including blank lines between sections and `::` spacing.
- Added native File and Code Templates, including `New | DreamShader Material`, `DreamShader Function`, `DreamShader Header`, and reusable graph section / texture sample code templates.
- Added DreamShader gutter markers for imports, top-level declarations, and Bridge diagnostic locations.
- Added editor platform integrations for quote handling, import gutter markers, Copy Reference qualified names, breadcrumbs, and constant `float3` / `float4` / `vec3` / `vec4` color previews.
- Added more IntelliJ editor integrations: declaration / section Context Info, plain-text symbol completion, rename name suggestions, and qualified-name reverse parsing for Copy/Paste Reference.
- Added declaration-level Rename input validation and Safe Delete availability for DreamShader declarations, backed by the existing namespace-aware reference search and `Name="Path/Leaf"` rename behavior.

### Changed

- Split parameter inlay hints from the Code Vision setting by adding the `enableInlayParameterHints` project setting; disabling Code Vision no longer hides parameter hints.
- `ALIGN_SECTION_ASSIGNMENTS` now aligns direct simple assignments inside sections by blank-line and nested-block groups, and Bridge diagnostic gutter markers prefer jumping to the diagnostic source line/column.

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

[0.0.5]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.4...0.0.5
[0.0.4]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.3...0.0.4
[0.0.3]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.2...0.0.3
[0.0.2]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.1...0.0.2
[0.0.1]: https://github.com/tsdaer/dreamshader-language-support/commits/0.0.1
