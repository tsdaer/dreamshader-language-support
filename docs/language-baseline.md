# DreamShaderLang Baseline

Stable language-reference notes used by this Rider plugin. Refresh this file when upstream DreamShader language behavior changes.

## DreamShaderLang Syntax Baseline

Primary language reference (upstream):
- https://github.com/TypeDreamMoon/DreamShader/blob/main/Docs/LanguageReference.md

Primary examples reference (upstream):
- https://github.com/TypeDreamMoon/DreamShader/blob/main/Docs/Examples.md

Reference snapshot used for this README alignment:
- Checked on `2026-05-29`
- Upstream doc title: `DreamShaderLang 语法参考`
- Upstream plugin version noted in doc: `1.3.8`

Examples conformance snapshot:
- Checked on `2026-05-29`
- Upstream doc title: `DreamShaderLang 示例与模式`
- Test mapping: `DreamShaderUpstreamExamplesTest.testUpstreamExamplesMarkdownCodeBlocksAreParsable()`

0.0.4 upstream sync snapshot:
- Checked on `2026-06-11`
- Upstream `TypeDreamMoon/DreamShader` main: `406cefb960e81ff2c0b9d0138a7a05c656085944`
- Relevant upstream changes: Substrate material generation (`caa00e8`), `.dsf` layer functions (`c8e65f9`), and `VolumeTexture` (`fef5feb`)
- Synced docs: `Docs/LanguageReference.md`, `Docs/Examples.md`, `Docs/Packages.md`

This section summarizes the language rules that this Rider plugin should follow.

### 1. File Roles and Constraints

- `.dsm`: material-oriented source; usually contains `Shader(...)` and may include shared helpers/imports.
- `.dsf`: function/layer asset source; may contain `ShaderFunction(...)`, `ShaderLayer(...)`, `ShaderLayerBlend(...)`, `Function`, `GraphFunction`, `VirtualFunction`, imports.
- `.dsh`: shared header source; usually shared `Function`/`GraphFunction`/`Namespace`/`VirtualFunction`.

Key constraints to enforce:
- `.dsf` top-level declarations are restricted to `ShaderFunction(...)`, `ShaderLayer(...)`, `ShaderLayerBlend(...)`, `VirtualFunction(...)`, `Function(...)`, `GraphFunction(...)`.
- `.dsm` must not declare top-level `ShaderFunction(...)`, `ShaderLayer(...)`, or `ShaderLayerBlend(...)` (function/layer assets belong in `.dsf`).
- `.dsh` should not be used for asset-generating declarations such as `Shader(...)`, `ShaderFunction(...)`, `ShaderLayer(...)`, `ShaderLayerBlend(...)`.

### 2. Top-Level Declarations

Expected declaration families:
- Asset declarations: `Shader`, `ShaderFunction`, `ShaderLayer`, `ShaderLayerBlend`.
- External asset signature declaration: `VirtualFunction`.
- Shared code declarations: `Function`, `GraphFunction`, `Namespace`.
- Import declaration: `import "..."`.

Important semantics to preserve:
- `Root="..."` path semantics on asset declarations (e.g. `Game`, `Plugin.<Name>`, optional subfolders).
- `ShaderLayer` / `ShaderLayerBlend` input/output-shape requirements:
  - `ShaderLayer` may have at most one input, and if present it must be `MaterialAttributes`.
  - `ShaderLayerBlend` must have exactly two inputs, and both must be `MaterialAttributes`.
  - Both declaration kinds must declare exactly one `MaterialAttributes` output.
- `VirtualFunction` participates in call signatures but does not generate/overwrite assets.

### 3. Section Model

Canonical sections:
- `Properties`
- `Inputs`
- `Outputs`
- `Settings`
- `Options`
- `Graph`

Expected behavior highlights:
- `Inputs` supports optional inputs (`opt`) and default-value usage conventions.
- `Outputs` supports direct initialization and material output binding patterns.
- `Settings` / `Options` map to Unreal material/function configuration fields.
- Section schema differs by declaration kind (`Shader` vs `ShaderFunction` vs `VirtualFunction`, etc.).

### 4. Graph / Function Semantics

Graph-level constructs expected by reference:
- Variable declarations, assignments, constructors, brace initializers.
- `if` / `else` flow in graph DSL.
- Calls to `Function(...)`, `Namespace::Function(...)`.
- Calls to `GraphFunction(...)` and `Namespace::GraphFunction(...)`.
- Calls to `ShaderFunction(...)` / `VirtualFunction(...)`.
- `UE.*` builtins for Unreal material node creation.
- `Substrate.*` wrappers for Substrate graph construction.

Function-level semantics:
- `Function` supports `in`/`out` style parameters; `out` arguments are explicit at call sites.
- `GraphFunction` compiles as custom-node style reusable graph helper and can consume `UE.*` sources.

Substrate semantics:
- `Substrate` is a first-class type for `ShaderFunction`, `.dsf`, and `VirtualFunction` inputs/outputs.
- `Shader` declarations can bind `Base.FrontMaterial`; upstream generation treats this as Substrate shading.
- `Base.FrontMaterial` and `Base.MaterialAttributes` should not both be bound in the same `Shader`.
- Supported wrapper namespace members include `Substrate.Unlit`, `Substrate.Slab`, `Substrate.VerticalLayer`, `Substrate.ConvertMaterialAttributes`, `Substrate.TransmittanceToMFP`, `Substrate.MetalnessToDiffuseAlbedoF0`, `Substrate.HazinessToSecondaryRoughness`, and `Substrate.ThinFilm`.
- `UE.Expression(Class="MaterialExpressionSubstrateSlabBSDF", OutputType="Substrate", ...)` is a supported Substrate escape hatch.
- `UMaterialExpressionCustom` does not support `OutputType="Substrate"`.
- `Substrate` values should not participate in arithmetic, vector construction, swizzle, or `if` branch merging. Rider diagnostics for these expression-level type rules are deferred until expression type inference is mature enough to avoid false positives.

### 5. Import, Path, and Type System

Import expectations:
- File imports via `import "..."` (`.dsh`, `.dsf`, etc.).
- Package-style imports (including namespaced package paths) are part of upstream behavior.

Path helper expectations:
- `Path(...)` forms and root restrictions should be parsed/validated consistently with upstream behavior.

Type system expectations:
- Scalar/vector/matrix families.
- GLSL-style aliases (`vec*`, etc.) alongside Unreal/HLSL-like types.
- Texture/sampler-related types.
- `Substrate`.
- `VolumeTexture` / `Texture3D`; `const TextureCube`, `const Texture2DArray`, and `const VolumeTexture` require explicit default assets.
- Compatibility handling for removed/legacy aliases where applicable.

### 6. Known Language Limits (From Upstream Reference)

- Graph DSL is intentionally not a fully general-purpose programming language.
- Graph currently supports `if` / `else`, not full loop/control-flow parity.
- Rider plugin currently reports diagnostics for unsupported Graph control-flow statements: `for`, `while`, `do`, `switch`, `case`, `default`, `break`, `continue`, `return`.
- `Function` calls require explicit `out` target passing.
- `Namespace` is for function organization and nested namespace grouping (not arbitrary declaration containers).

### 7. Rider Plugin Coverage Mapping

Already implemented in this plugin:
- File type association (`.dsm`, `.dsf`, `.dsh`).
- Top-level declaration and section tokenization/parsing foundations.
- File-role declaration constraints baseline (`.dsf` uses top-level declaration whitelist; `.dsm` disallow top-level `ShaderFunction`/`ShaderLayer`/`ShaderLayerBlend`; `.dsh` disallow asset-generating top-level declarations).
- Declaration section-shape diagnostics baseline with alias/compat behavior (`Results` compatibility for `ShaderFunction`/`VirtualFunction`, declaration-specific allowed/required section checks, duplicate section detection).
- Context-aware completion for sections, types, settings values, catalog-driven `UE.*` / `Substrate.*`, HLSL intrinsics, imports.
- Navigation/symbols/folding/references/hover/signature help basics, including shared catalog-backed docs/signatures for `UE.*` / `Substrate.*`.

Current boundary and long-term parity notes:
- `Partial`: section-shape diagnostics cover current declaration-aware baseline, but full parity for all upstream edge cases/future syntax revisions is an ongoing alignment target.
- `Partial`: semantic diagnostics cover major high-signal rules; exhaustive parity for all invalid settings/outputs/types remains a long-term hardening target.
  - `2026-05-30 update`: declaration parameter types in `Function(...)` / `GraphFunction(...)` now participate in unknown-type diagnostics with suggestion quick-fixes (same rule family as section typed declarations).
  - `2026-05-30 update`: local typed declarations inside `Graph`/`Function`/`GraphFunction` bodies now participate in unknown-type diagnostics with suggestion quick-fixes.
  - `2026-05-30 update`: unknown-type diagnostics now also cover nested declarations under `Namespace` (including `Namespace` child `Function(...)` / `GraphFunction(...)` parameter types and local typed declarations in graph-constrained bodies).
  - `2026-05-30 update`: missing-`out`-argument diagnostics/quick-fix now also cover nested declarations under `Namespace` (for example `Tools::ApplyTint(...)` resolves namespace child signatures and offers the same placeholder insertion behavior).
  - `2026-05-30 update`: missing-`out`-argument call-signature resolution is now namespace-aware for unqualified same-name calls (nearest enclosing namespace scope first, then parent scopes, then top-level), preventing false positives/negatives when top-level and namespace child declarations share names.
  - `2026-05-30 update`: `const TextureCube/Texture2DArray/Texture3D/VolumeTexture` default-asset diagnostics now validate initializer path shape and root (`quoted object path`/`Path(...)`, root in `Game`/`Engine`/`Plugin.*`/`Plugins.*`), not only initializer presence.
  - `2026-05-30 update`: const-texture default-asset unknown-root diagnostics now provide `Replace Path root with Game` quick-fix and highlight the initializer value range directly.
  - `2026-05-30 update`: asset-root validation/quick-fix now consistently handles both `Path(...)` and quoted object-path forms (for both `VirtualFunction Options.Asset` and const-texture default assets).
  - `2026-05-30 update`: leading-slash quoted paths now extract root by first segment (`"/Project/..."` -> `Project`), preventing false `Game` classification and reporting correct unknown-root diagnostics.
  - `2026-05-30 update`: `Path(...)` asset-path validation now requires a non-empty object segment argument (for example rejects `Path(Game)` and requires at least root + object path).
  - `2026-05-30 update`: when `Path(...)` is missing object segment (`Path(rootOnly)`), diagnostics now provide `Complete Path(...) with object segment` quick-fix (auto-completes to `Path(<Root>, Textures/T_AutoAsset)` baseline).
  - `2026-06-11 update`: `Base.FrontMaterial` is a known material output member and conflicts with `Base.MaterialAttributes` when both are bound in one declaration.
  - `2026-06-11 update`: `ShaderLayer` and `ShaderLayerBlend` input-shape diagnostics follow upstream's stricter `MaterialAttributes` rules.
  - `2026-06-11 update`: `UE.Expression(..., OutputType="Substrate")` rejects `Custom` / `MaterialExpressionCustom` / `UMaterialExpressionCustom`.
- `Partial`: parser is intentionally permissive for IDE resilience; full strict Graph grammar validation is not the current parser mode.
- `Implemented` (baseline): formatter handles indentation/spacing/braces/section layout; fine-grained parity with all DreamShader authoring conventions may continue to evolve.
- `Implemented` (baseline): semantic token classification and inlay hints are available and tested; exact upstream VS Code parity is treated as iterative polish, not a release blocker.
