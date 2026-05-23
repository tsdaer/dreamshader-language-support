<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# dreamshader-language-support Changelog

## [Unreleased]
### Added
- Initial scaffold created from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)
- DreamShaderLang language support foundation for `.dsm`, `.dsf`, `.dsh`: file type registration, lexer, parser definition, typed PSI, syntax highlighting, commenter, brace matching, and color settings page.
- Folding support for brace blocks and `// region` / `// endregion`.
- Context-aware completion for top-level declarations, sections, types, settings/options keys and values, `Base.*` outputs, `UE.*`, HLSL intrinsics, and import paths.
- Navigation and symbol capabilities: structure view, go-to declaration (imports and declarations), find usages/references, rename support for declarations, documentation provider (hover), and parameter info/signature help.
- Bridge integration baseline: project/bridge path resolution, diagnostics repository loading, diagnostics tool window, and bridge tool actions (refresh, open diagnostics/location, open bridge directory, recompile current/all, clean generated).
- Status bar widget for DreamShader bridge diagnostics with visibility toggle.
- Project settings page at `Settings | Tools | DreamShader` for bridge/material-manifest paths, status widget toggle, code-lens toggle, and bridge command templates (`%file%`, `%projectRoot%`, `%bridgeDir%`).
- Package tooling under `Tools | DreamShader Packages`: browse package store, install from GitHub, update/remove installed packages, open packages folder, and add/remove package index sources.
- Package store UI features: search, installed/update-possible filters, details pane, repository display, action enablement by state, double-click install, and cancellable background lifecycle tasks.
- Multi-source package index support with legacy single-source compatibility and default upstream index fallback.
- Internationalization infrastructure with `messages.DreamShaderBundle` and localized UI/message strings for plugin actions, settings, bridge tooling, and package tooling.
- Chinese translation bundle `DreamShaderBundle_zh_CN.properties` in readable UTF-8 form.
- Basic DreamShader formatter registered via `lang.formatter` with indentation, operator spacing, and section/brace layout normalization.
- DreamShader daemon-bound Code Vision provider for declaration-level workflow hints in editor.
- Optional GitHub package search integration in package store (with optional token setting for API rate limits).
- Marketplace publishing metadata/signing/publishing Gradle configuration wired for release workflow (`pluginConfiguration`, `signing`, `publishing`).

### Changed
- Plugin descriptor now declares `<resource-bundle>messages.DreamShaderBundle</resource-bundle>` and uses resource keys for configurable display name.
- Action presentation strings for bridge/package actions are now provided through bundle-backed `templatePresentation` values instead of hardcoded literals.
- User-facing notifications, dialog labels, and package/bridge operation messages were migrated to resource-bundle lookups.

### Fixed
- Fixed `buildSearchableOptions` plugin descriptor issue by providing a proper configurable display-name key in `plugin.xml`.
