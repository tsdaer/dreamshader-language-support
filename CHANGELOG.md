<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# dreamshader-language-support Changelog

## [Unreleased]

## [0.0.2] - 2026-05-29

### Added

- `const` texture diagnostics now require explicit default assets (for example `Path(...)`) and report targeted semantic errors.
- Hover docs overrides now have a visual table editor (`Path` + `Content`) with validation summary and sample row insertion.
- Unsupported import extension quick-fix now surfaces clear “will update preferred default” behavior hints in UI.

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

### 中文

- 暂无。

[Unreleased]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.2...HEAD
[0.0.2]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.1...0.0.2
[0.0.1]: https://github.com/tsdaer/dreamshader-language-support/commits/0.0.1
