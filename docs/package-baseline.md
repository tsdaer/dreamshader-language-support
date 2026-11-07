# DreamShader Package Baseline

Package-system behavior and implementation notes. Keep package import, install, index, and authoring rules here instead of in the README.

Version-specific package impacts should first be captured in `roadmap.md` or a dedicated feature document, then moved here once they become stable behavior.

## DreamShader Package Baseline

Primary package reference (upstream):
- https://github.com/TypeDreamMoon/DreamShader/blob/v2.2.0/Docs/tools/packages.md

DreamShader 2.2.0 builds supported package sources under `Assets/` and package headers that opt into generation. Examples are excluded. Rider offers header generation completions; Unreal generates the assets and reports their paths.

Reference snapshot used for this README alignment:
- Checked on `2026-09-01`
- Upstream doc title: `Packages`

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

Current import baseline: upstream [v2.1.1 import rules](https://github.com/TypeDreamMoon/DreamShader/blob/v2.1.1/Docs/language/import.md), checked 2026-10-08.

For a relative path, resolve only within the current file's owning source root:
1. Current file directory
2. Owning source root
3. That root's `Packages/` directory

Omitting the extension only appends `.dsh`; explicit `.dsh` / `.dss` headers are legal. `.dsi` and legacy material/function asset files are excluded. Use `/` (including `/Game/`) to anchor cross-root resolution; an own-root match takes precedence over other source roots. Automatic import validates the final target so a shadowing file cannot redirect the selected symbol.

`Project:` / `Plugin:` qualifiers do not resolve. `dreamshader.entry` remains useful package metadata and an explicit-path suggestion; the resolver does not automatically expand an import of the package directory into that entry. No plugin library fallback is added.

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

### 5. Package Actions

Implementation coverage:

- Install package from GitHub repo input (`owner/repo` or full GitHub URL).
- Browse package store (search/install/open repository/manage sources).
- Update installed packages.
- Remove installed package.
- Open packages folder.
- Add/remove package store index source.
- Guided package creation/scaffold workflows for package metadata and starter examples.

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
- Import completion suggests resolvable `.dsh` / `.dss` header paths in source roots and installed packages.
- Import navigation resolves legal explicit header paths; a package root import requires an explicit entry-path correction.
- Package diagnostics distinguish missing package entry files from invalid/unsafe package entry metadata and map them to dedicated messages/quick-fixes.
- Package install, update, and remove actions update the package install folder and lock file.
- Package store UI supports card-style search results/details, install, update, remove, open repository, refresh, installed/updates filters, GitHub search, empty states, and index source management.
- GitHub package discovery integration (`DreamShaderGitHubPackageSearch`) is available.
- Package authoring tools include a step-by-step package wizard and a direct package scaffold action.
