# DreamShader Package Baseline

Package-system behavior and Rider parity notes. Keep package import, install, index, and authoring rules here instead of in the README.

Version-specific package impacts should first be captured in `roadmap.md` or a dedicated feature document, then moved here once they become stable behavior.

## DreamShader Package Baseline

Primary package reference (upstream):
- https://github.com/TypeDreamMoon/DreamShader/blob/main/Docs/Packages.md

Reference snapshot used for this README alignment:
- Checked on `2026-05-29`
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

Upstream update note (`2026-05-29` sync):
- Built-in plugin library import fallback (`Plugins/DreamShader/Library/`) has been removed from upstream docs and should not be used as a resolver source.

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
- Import completion can suggest project `.dsh` / `.dsf` / `.dsm` files and package root imports.
- Import navigation resolves local files, package files, and package metadata `dreamshader.entry` roots when paths are valid.
- Package diagnostics distinguish missing package entry files from invalid/unsafe package entry metadata and map them to dedicated messages/quick-fixes.
- Package install, update, and remove actions update the package install folder and lock file.
- Package store UI supports search, install, update, remove, open repository, refresh, installed/updates filters, and index source management.
- GitHub package discovery integration (`DreamShaderGitHubPackageSearch`) is available.
