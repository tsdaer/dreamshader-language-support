# Temporary Optimization Plan

Temporary implementation plan based on the 2026-06-16 audit of `README.md`,
`docs/language-baseline.md`, `docs/roadmap.md`, and the current Kotlin source
tree. This list is intentionally deduplicated against the implemented items in
`docs/roadmap.md`.

Use this document to guide short-term optimization work. Once items are
implemented, either fold them into `roadmap.md` / release notes or remove this
temporary plan.

## Scope

- Improve edit-time performance for large `.dsh`, `.dsf`, and `.dsm` files.
- Close behavior gaps that can currently pass Rider diagnostics but fail in the
  Bridge stage.
- Add targeted editor-experience features that are absent from the roadmap's
  implemented inventory.
- Keep changes small enough to land independently, with focused tests per item.

## Priority Legend

- `P0`: High-impact user pain or performance hotspot.
- `P1`: Editing/navigation experience gap.
- `P2`: Robustness, parser health, or long-tail compatibility.

## Progress

Last updated: 2026-06-16

| Item | Priority | Status | Notes |
| --- | --- | --- | --- |
| 1. Cache semantic diagnostic lexical inputs | P0 | In progress | File-level diagnostic inputs are cached: source text, whole-file tokens, and top-level declarations. Some internal body-range lexing still remains for later incremental cleanup. |
| 2. Cache import closure resolution | P0 | Done | `resolveDirectImports` and `resolveImportClosure` now use cached values invalidated by PSI modification count and VFS structure changes. |
| 3. Add conservative Substrate expression diagnostics | P0 | Not started | Next P0 Bridge-failure-prevention item. |
| 4. Wire `UE.*` goto to Unreal source locator | P0 | Done | `UE.<Name>` goto now prefers Unreal header source targets when source scanning is enabled and falls back when disabled or unresolved. |
| 5. Add Live Templates and Postfix Templates | P1 | Not started | Pending independent editor-experience patch. |
| 6. Add Goto Symbol / Goto Class contributors | P1 | Not started | Pending independent navigation patch. |
| 7. Integrate spellchecking | P1 | Not started | Pending independent editor-experience patch. |
| 8. Expand color preview coverage | P1 | Not started | Pending independent editor-experience patch. |
| 9. Show `opt` and default values in hover signatures | P1 | Not started | Pending independent documentation/signature rendering patch. |
| 10. Share declaration context across diagnostic passes | P2 | Not started | Best handled after cache boundaries stabilize. |
| 11. Let parser emit recoverable error markers | P2 | Not started | Pending parser recovery cleanup. |
| 12. Validate `Path(...)` object-segment suffixes | P2 | Not started | Pending diagnostics patch. |
| 13. Add GLSL legacy-alias compatibility diagnostics | P2 | Blocked | Requires upstream confirmation of the alias list. |

Current verification:

- `./gradlew test --tests com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.DreamShaderSemanticDiagnosticsTest --tests com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.DreamShaderSemanticAnnotatorTest --tests com.github.tsdaer.dreamshaderlanguagesupport.language.packages.DreamShaderImportClosureResolverTest --tests com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.DreamShaderGotoDeclarationHandlerTest`

## P0: Performance and Bridge-Failure Prevention

### 1. Cache semantic diagnostic lexical inputs

Problem:
`diagnostics/DreamShaderSemanticAnnotationPipeline.kt` currently lexes the
whole file on each annotation pass via `lexTokens(0, file.textLength)`. Several
internal passes also lex declaration bodies again. The language module does not
currently use `CachedValuesManager`.

Implementation notes:
- Cache file-level tokens and top-level declaration summaries with
  `CachedValuesManager.getCachedValue(file)`.
- Invalidate on `PsiModificationTracker.MODIFICATION_COUNT`.
- Prefer reusing parser/PSI-derived structure where available, and keep the
  standalone `DreamShaderLexer()` path only as a defensive fallback.
- Avoid changing diagnostic behavior in the same patch unless required by the
  cache shape.

Acceptance:
- Repeated annotations of an unchanged file reuse cached lexical data.
- Existing semantic diagnostics tests still pass.
- Add or update a focused test around cache invalidation after PSI changes if
  practical.

Effort: M

### 2. Cache import closure resolution

Problem:
`DreamShaderImportClosureResolver.resolveImportClosure` and
`resolveDirectImports` repeatedly scan PSI string tokens. The resolver is called
from references, goto, signature help, inlay hints, hover, call-signature, and
unresolved-import diagnostics.

Implementation notes:
- Cache direct imports and import closure results per file/project context.
- Invalidate on `PsiModificationTracker.MODIFICATION_COUNT`.
- Include project-root/package settings in the cache dependency or split cached
  data so setting changes cannot leave stale import paths.
- Keep cycle handling behavior unchanged.

Acceptance:
- Multiple resolver callers for the same unchanged file share import results.
- Import diagnostics and cross-file navigation remain correct after editing an
  import string.
- Existing import-chain tests pass.

Effort: S

### 3. Add conservative Substrate expression diagnostics

Problem:
The language baseline marks several Substrate expression rules as deferred:
Substrate values should not participate in arithmetic, vector constructors,
swizzles, or branch merging. Today these patterns can avoid IDE diagnostics and
fail later in the Bridge stage.

Implementation notes:
- Add a narrow semantic pass to `annotateSemanticDiagnostics`.
- Track only local symbols whose declared type is literally `Substrate`.
- Diagnose direct arithmetic operators, swizzles such as `.x` / `.yzw`, vector
  constructor arguments, and ternary/branch merge patterns when the Substrate
  value is known.
- Prefer under-reporting to avoid false positives until broader type inference
  exists.

Acceptance:
- Known illegal `Substrate` operations receive IDE diagnostics.
- Non-Substrate expressions and unknown/inferred symbols are not flagged.
- Tests cover at least arithmetic, swizzle, constructor, and ternary cases.

Effort: M

### 4. Wire `UE.*` goto to Unreal source locator

Problem:
`DreamShaderUnrealSourceLocator` exists, but `DreamShaderGotoDeclarationHandler`
does not call it. `UE.Texcoord` and similar symbols can only jump to catalog
documentation, despite the 0.0.4 source-scanning feature.

Implementation notes:
- Add a `UE.<Name>` branch in the goto handler.
- When Unreal source scanning is enabled and a source location exists, prefer
  the source target.
- Fall back to the current catalog/documentation behavior when source lookup is
  disabled or unresolved.

Acceptance:
- F12 / Go to Declaration on a resolvable `UE.*` expression opens the Unreal
  source location.
- Existing catalog fallback remains available.
- Tests cover enabled, disabled, and unresolved-source paths where feasible.

Effort: S

## P1: Editing and Navigation Polish

### 5. Add Live Templates and Postfix Templates

Problem:
`plugin.xml` registers file templates but no `defaultLiveTemplatesProvider` or
`postfixTemplateProvider`.

Implementation notes:
- Add `resources/liveTemplates/DreamShader.xml`.
- Initial live templates: `shader`, `sfun`, `slayer`, `slblend`, `gfn`, `iff`,
  `ifel`, and `uee`.
- `uee` should insert a `UE.Expression` form with `Class` and `OutputType`
  placeholders.
- Add postfix templates only if they map cleanly onto existing PSI contexts.

Acceptance:
- DreamShader live templates appear in Rider template settings.
- Templates expand in `.dsm`, `.dsf`, and `.dsh` files where appropriate.

Effort: S

### 6. Add Goto Symbol / Goto Class contributors

Problem:
No `gotoSymbolContributor` or `gotoClassContributor` is registered, so
DreamShader declarations do not appear in Ctrl+N / Ctrl+Alt+Shift+N flows.

Implementation notes:
- Implement `ChooseByNameContributor`.
- Treat asset declarations as class-like entries.
- Treat namespace members and functions as symbol entries.
- Reuse the existing symbol/declaration model rather than creating a parallel
  scanner if possible.

Acceptance:
- Asset declarations are searchable via class navigation.
- `Function`, `GraphFunction`, `VirtualFunction`, and namespace members are
  searchable via symbol navigation.
- Results navigate to the declaration PSI element.

Effort: M

### 7. Integrate spellchecking

Problem:
No `SpellcheckingStrategy` is registered. Text in comments and
`VirtualFunction Description` strings can leak spelling mistakes into Unreal UI.

Implementation notes:
- Use `CommentSplitter` for `LINE_COMMENT` and `BLOCK_COMMENT`.
- Use `PlainTextSplitter` for string contents assigned to `Description`.
- Restrict string spellchecking to description-like contexts to avoid noisy
  diagnostics for paths, enum values, and code-like strings.

Acceptance:
- Comments are spellchecked.
- `Description="..."` contents are spellchecked.
- Paths/imports/settings values are not broadly spellchecked.

Effort: S

### 8. Expand color preview coverage

Problem:
`DreamShaderColorProvider.kt` only recognizes `float3`, `vec3`, `float4`, and
`vec4` forms. Hex colors, `Color(...)`, and 0-255 integer forms do not show
preview swatches.

Implementation notes:
- Extend `COLOR_CALLS` with supported color constructor aliases.
- Add a hex literal branch for `0xRRGGBB` and, if desired, `0xRRGGBBAA`.
- Normalize integer forms to 0-1 channel values only when all channels are in
  the 0-255 range.
- Keep current float-vector behavior unchanged.

Acceptance:
- Inline color previews appear for supported hex and `Color(...)` forms.
- Invalid or ambiguous numeric tuples do not show misleading previews.
- Existing color-provider tests remain green and new forms are covered.

Effort: S

### 9. Show `opt` and default values in hover signatures

Problem:
DSYN-215 validates missing defaults, but
`DreamShaderDocumentationProvider` does not render `opt` modifiers or resolved
default expressions in hover signatures.

Implementation notes:
- Extend signature rendering to include optional markers and defaults, e.g.
  `opt float Strength = 1.0`.
- Share parsing logic with the diagnostic path if a helper already exists.
- Keep signature help and hover output consistent if both render the same
  callable model.

Acceptance:
- Hover on a call target shows optional parameters and their defaults.
- Existing documentation rendering remains stable for non-optional inputs.

Effort: S

## P2: Robustness and Long-Tail Compatibility

### 10. Share declaration context across diagnostic passes

Problem:
Multiple diagnostic passes repeatedly traverse PSI with `findChildrenOfType`.
The annotation pipeline currently has many independent passes that reconstruct
similar declaration/section/input/output context.

Implementation notes:
- Introduce a lightweight `DeclarationContext` or equivalent index.
- Store sections, inputs, outputs, settings/options, local symbols, and body
  ranges needed by existing passes.
- Build it once per declaration from cached file-level data.
- Migrate passes incrementally to reduce review risk.

Acceptance:
- No diagnostic behavior regression.
- Repeated PSI traversal in the hot annotation path is reduced.

Effort: M

### 11. Let parser emit recoverable error markers

Problem:
`parser/DreamShaderPsiParser.kt` keeps malformed declarations permissive without
calling `marker.error(...)`. Annotators then re-lex to reconstruct malformed
declaration diagnostics.

Implementation notes:
- Add recoverable parser errors for malformed declarations where the expected
  shape is known.
- Keep recovery permissive enough that later declarations still parse.
- Move only parser-obvious syntax errors into parser markers; semantic errors
  should remain in annotators.

Acceptance:
- Malformed declarations produce parser error markers.
- Parser recovery still preserves structure for following declarations.
- Annotator fallback lexing can be reduced or simplified.

Effort: S

### 12. Validate `Path(...)` object-segment suffixes

Problem:
Root validation is in place, including leading-slash and root-only checks, but
object segments can still contain Bridge-breaking suffixes such as `.uasset`,
`.umap`, or image extensions.

Implementation notes:
- Add object-segment suffix validation near existing path extraction helpers.
- Warn on known invalid asset/object suffixes.
- Avoid rejecting legitimate folder names that happen to contain dots unless
  they match the object segment being validated.

Acceptance:
- `Path(...)` values with invalid object suffixes receive a targeted warning.
- Existing valid root and object path cases remain accepted.

Effort: S

### 13. Add GLSL legacy-alias compatibility diagnostics

Problem:
The baseline mentions compatibility handling for removed or legacy GLSL aliases,
but the actual alias list needs upstream confirmation before implementation.

Implementation notes:
- Confirm the removed/legacy alias set with upstream first.
- Add warnings in `annotateUnknownTypeDiagnostics`.
- Provide replacement quick-fixes for aliases with clear modern equivalents.

Acceptance:
- Confirmed legacy aliases produce warnings, not hard errors.
- Quick-fixes replace known aliases with the supported type names.
- No unconfirmed alias is diagnosed.

Effort: S, blocked on upstream alias list

## Suggested Execution Order

1. Land import-closure caching and `UE.*` goto source wiring first. They are
   small, user-visible, and low risk.
2. Land file-level diagnostic caching before broader diagnostic refactors.
3. Add conservative Substrate diagnostics to catch known Bridge failures.
4. Add P1 editor features in independent patches.
5. Use parser error markers and shared declaration context as cleanup after the
   cache boundaries are stable.

## Test Focus

- Keep performance-oriented tests focused on cache invalidation and repeated
  caller behavior rather than wall-clock timing.
- Add fixture-style diagnostics tests for each new Substrate and `Path(...)`
  rule.
- Cover navigation contributors with light integration tests that assert returned
  names and target PSI offsets.
- Keep template and spellcheck tests scoped to registration/context behavior.
