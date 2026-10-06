<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# dreamshader-language-support Changelog

## [2.4.0] - 2026-10-06

### Added

- Refactoring milestone M16 (plan: `docs/m16-plan.md`):
  - **Inline Variable for the 2.0 dialect** (C25): the Refactoring-menu action (`DreamShader.InlineVariable`) replaces every read usage of a single-declarator local with the declaration's parenthesized initializer and removes the declaration together with its `///` doc comment. The precheck runs fully before the write and refuses - with a hint and an untouched document - when the initializer is missing, the declaration has several declarators, the initializer reads a parameter, local, or global (drift risk), contains assignments/compound assignments/`++`/`--`, calls a user-declared or unknown callable, when a usage is itself a write target, or when the target sits inside a `/// @custom` body; usages outside the declaration's lexical scope do not belong to it and stay untouched.
  - **Extract Function single return value** (C26): when exactly one value is written by the selection, never read there, used afterwards, its final write is the last selected statement (`name = expr;`), and the name is a bare same-block declaration with no uses between itself and the selection, the helper returns the value instead of taking an `out` parameter - `Type Name(inParams) { …; return name; }`, the call site becomes the receiving declaration `Type name = Name(inParams);`, the bare declaration line goes away, and the linked in-place rename covers the new function name, the call-site name, and the receiving variable together. Parameter targets, initialized or outer declarations, and any intermediate use keep the `out` matrix.
  - **Structure view dialect routing with mixed-`.dsh` segments** (C27): the structure view factory routes by dialect - pure V2 files (`.dss`/`.dsi`, V2-only `.dsh`) get the 2.0 model where the factory previously fell through to the 1.x model and produced an empty view (the 2.0 factory had never been registered); pure legacy files keep the 1.x model byte-for-byte; a mixed `.dsh` shows one Legacy node per legacy block (labeled from the block's first 1.x declaration) whose children come from a cached text-level 1.x re-scan, navigable at their absolute offsets.
  - **Inlay parameter hints for 2.0 functions** (C28): V2 user functions declared in the file or its include closure now supply parameter names where the 1.x resolver draws a blank; mixed `.dsh` resolves per caret region (legacy regions keep the 1.x path, `@custom` bodies stay empty), and named arguments keep suppressing the hint.

### Changed

- **Extract Function `inout` correction** (C26): compound assignments and `++`/`--` read their own old value, so their targets now come back as `inout` parameters instead of `out` (which would read an undefined value in HLSL semantics).
- **Schema-driven `.dsi` override values** (C29): when the Bridge symbol index carries a valid parent schema with a parameter matching the override's name, the schema type re-drives the value templates, a `StaticSwitch` kind forces `true`/`false` even when the override spells `int`/`float`, and a `Texture` kind stays silent; without a schema (or without a matching parameter) the declared-spelling path is unchanged.
- **Flow-sensitive write-back narrowing** (C30): after a call passes a plain variable to a declared `out`/`inout` parameter with a confident type, the variable's inferred type narrows to the formal's spelling from the call's end onward - swizzle completion and type-mismatch checks see the written shape (named arguments match by name; a nearer re-declaration of the name beats the write-back; unparsable formal spellings stay silent).

## [2.3.0] - 2026-10-06

### Added

- Refactoring milestone M15 (plan: `docs/m15-plan.md`):
  - **Extract Function for the 2.0 dialect** (C21): selecting complete statements inside a `.dss` function body inserts a file-local helper function after the current one and replaces the selection with a call. The signature is synthesized from the selection's data flow - names read from outside become value parameters with their declared type spelling, names written in the selection and used afterwards come back as `out` parameters (`inout` when the selection also reads them, e.g. `x = x + 1`), and the new function name starts a linked in-place rename template with the call site. Extraction refuses - with a hint and an untouched document - on `return`/`break`/`continue`, partial selections, assignments through members or indexes, declarations that would change scope, and names whose type cannot be resolved; names that resolve to functions or namespace roots stay as-is instead of becoming parameters.
  - **Inline Uniform for the 2.0 dialect** (C22): a `Refactoring`-menu action (`DreamShader.InlineUniform`) replaces every usage of a single-declarator uniform in the current `.dss` with its parenthesized initializer and removes the declaration together with its `///` doc block. Every precondition is checked before the write: `.dsi` material instances overriding the uniform (via the rename safety net), usages in other files, other initializers reading the uniform, missing initializers, and multi-declarator declarations all refuse with a hint and an untouched document.

### Changed

- **Mixed-`.dsh` completion now dispatches per caret region** (C23): a HEADER_MIXED `.dsh` is parsed by the 2.0 front end, so the caret's enclosing node decides the serving dialect - legacy blocks are answered by the 1.x contributor, real V2 declarations (uniform/global/struct/function/pragma/include/doc blocks) by the 2.0 one, and the top-level gaps between declarations host both dialects' keyword sets. Pure files keep their file-level answer at every offset, and the typed `.`/`(`/`=` auto-popup applies the V2 lexical rules only inside V2 regions.
- **Type inference slices** (C24): local name resolution is lexically scoped - a declaration's scope owner (its nearest block, or the `for` header for loop-init declarations) must also contain the use site, so an earlier sibling block's same-name local no longer lends a false confident type, and shadowing resolves innermost-first; the operand-typed intrinsic set grows by `normalize`/`min`/`max`/`floor`/`ceil`/`round`/`frac` plus `dot` (scalar of the wider family, equal shapes) and `cross` (same-family 3-vectors); call arguments of user-declared functions are checked against the callee's declared parameter types as weak warnings (file + include closure resolution, named arguments matched by name, confident pairs only).

## [2.2.0] - 2026-10-05

### Added

- Expression intelligence milestone M14 (plan: `docs/m14-plan.md`):
  - **Extract Variable for the 2.0 dialect** (C20): selecting (or placing the caret after) an expression inside a `.dss`/`.dsi` function body inserts a local declaration before the enclosing statement with the inferred type spelling and replaces the occurrence with a suggested, de-duplicated name; the declaration and the use site start a linked in-place rename template. Extraction refuses - with a hint and an untouched document - when the type is not knowable, and inside `if` arms or loop bodies where hoisting would change evaluation order. 1.x files keep the platform default.
  - **Postfix templates for the 2.0 dialect** (C18): the postfix expression selector now serves `.dss`/`.dsi` - the template candidate is the value after the last top-level assignment (`=`, `+=`, ...) instead of the whole line, and the wrapped `$expr$` is the expression-shaped PSI node rather than a leaf - so `if`/`ifel` work in 2.0 files for the first time; four expression templates join: `lerp(expr, y, s)`, `saturate(expr)`, `expr.Sample()`, and `expr.<components>`.

### Changed

- **Expression type inference hardening** (C17, first slice of the long-term inference target): ternary expressions fold to the branch types (the condition type is discarded, arms merge with scalar/vector promotion, right-nested chains close innermost-first) instead of reporting the condition's type; compound assignments keep the left-hand shape instead of absorbing the right-hand vector shape; user function calls resolve declared return types through the include closure (non-export helpers included) before the workspace export view; the assignment category-mismatch warning now covers compound assignments too, staying silent when DSH5293 already flags the Substrate operand.
- **Signature help with named arguments** (C19): the parameter-info caret position maps a named argument to its parameter by name (case-insensitive, string-literal value carets included), so `Destination = "Wo` highlights `Destination` instead of the comma-count slot; the positional index only decides in gaps between arguments and for unmatched names. Catalog signatures now appear together with the documented built-in spelling of the same callable as distinct overload rows (catalog first, presentations de-duplicated).

## [2.1.0] - 2026-10-05

### Added

- Completion intelligence second batch (M13 close-out, plan: `docs/completion-plan.md`):
  - **Catalog enum values at named-argument value slots** (C11.2): `Source = ` / `Source = "Wo` on `Enum`-annotated catalog parameters suggests the documented basis names - vector transforms (`UE.TransformVector`) take `Tangent`-style bases, position transforms (`UE.TransformPosition`) take `PeriodicWorld`/`TranslatedWorld`-style bases, from upstream `Docs/builtins/transform.md`. A bare slot inserts the value quoted, an open quote takes a bare value, and non-enum parameters or closed value slots fall through to identifier completion.

### Changed

- **Completion hot-path hardening** (C15): the 1.x completion context is now analyzed from the committed `originalFile` PSI (lexer pass as the only fallback) instead of re-parsing the whole file on every popup; the two callable-candidate collectors merged into one cached closure + signature pass per PSI revision; the project-wide FilenameIndex scan is cached per project revision (shared by `import "` and `#include "` completion) and import candidates are collected lazily - contexts outside import strings touch no index.
- **Quote-aware insertion matrix** (D9 close-out): real-completion tests cover the "opening quote typed / not typed" x "value selected" matrix for `#pragma material` values, locking the no-double-quote insertion; the 1.x settings value path keeps its bare-insert semantics.

## [2.0.0] - 2026-10-04

### Added

- Milestone M13 completion intelligence (plan: `docs/completion-plan.md`), eliminating the "messy suggestions after dot" complaints:
  - **Dialect-aware completion dispatch** (C8): the 1.x contributor stays silent in pure 2.0 files (`.dss`/`.dsi` and V2-marked `.dsh`, detected with the parser's own dispatch rules, cached on the file PSI) - pressing `.` in a `.dss` no longer lists 1.x top-level keywords; mixed `.dsh` headers keep legacy service.
  - **Receiver-typed 2.0 member completion** (C9): after `.` the 2.0 contributor dispatches on the C2 inferred receiver type - swizzles for vectors (`xyzw`/`rgba` sets with result-size tails), matrix `_mRC` cells, struct fields via the symbol model, texture `sample`, `material` attributes, `Substrate` builder pins (catalog types plus the offline fallback, already-written pins excluded), `UE.`/`Substrate.` catalog members, and an unknown receiver degrades to namespace roots plus callables instead of keywords.
  - **Unified completion priorities** (C10): a shared ordering scale (`DreamShaderCompletionPriorities`) feeds both contributors through one lookup-element factory - 2.0 entries now carry priorities and icons instead of riding at priority 0, and the 1.x suggester's priority literals map onto the same constants.
  - **Slot-checked named arguments** (C11): `Name = ` suggestions appear only at the parameter-name slot (after `(` or a top-level comma); value positions and nested member accesses fall through to identifier completion.
  - **Dialect-aware auto popup** (C12): typing `.` / `(` / `=` in 2.0 files auto-popups completion through lexical rules (member access, call arguments, `#pragma material` value slots) without the 1.x analyzer re-parse.
  - **2.0 bare-identifier completion** (C13): bare Ctrl+Space in `.dss`/`.dsi` suggests locals declared before the caret, function parameters, uniforms/globals with types, structs, file and workspace-exported callables, type constructors, HLSL intrinsics, and dialect keywords.
  - **`#include` path completion** (C14): `#include "` in 2.0 files suggests project `.dsh`/`.dss`/`.dsi` sources with in-string replacement.
  - **`.dsi` override value completion** (A10): at an override's value slot the declared type spelling drives the suggestions - `bool` overrides get `true`/`false`, scalars get placeholders, vectors/matrices get constructor templates; resource-typed overrides stay silent. Suggestions match the parent schema type by construction and work without Bridge data.
  - **1.x dot tightening** (C16): a non-namespace receiver in graph contexts suggests only the scope's callables and locals - no namespace roots, intrinsics, or constructors.
  - **Pragma value quoting fix** (D9 partial): `#pragma material` values insert bare inside an opened string (no more `""` doubling) and quoted at bare slots.

- Milestone M12 editing experience (upstream `2.0.2` @ `b3bbf48`), aligning the plugin version with the upstream 2.0 line:
  - **Context-aware 2.0 completion** (C3): a new PSI-driven contributor for `.dss`/`.dsi` files - named-argument completion driven by the builtin catalog's `inputs[]` (filled names and positionally consumed pins are excluded), `Substrate` builder member completion on C2-typed receivers with a documented offline pin fallback, `.dsi` parent-parameter completion from the Bridge symbol index, and `#pragma material(...)` keys/values with Bridge settings.json first and an offline fallback table from the upstream settings page.
  - **Semantic highlighting for the 2.0 dialect** (C4): three-level identifier colors (uniform parameter / function parameter / locals, structural and AST-layer based), a dedicated value color for explicitly `Substrate`/`material`-typed variables at usage sites, `/// @key` attribute names and `.dsi` override names colored in the file-level pass, and five new configurable color keys.
  - **Workspace usages and the rename safety net** (C5): a second references-search executor serves the stub-backed 2.0 declarations over the word index (derived files excluded, `.dsi` override declarations surface as renameable usages), and the rename impact checker spells out `.dsi` overrides for uniforms and same-name export/call-site files before a rename.
  - **Structure view and Code Vision for 2.0** (C6): includes, uniforms grouped by `/// @group`, globals, structs and functions; a navigable Parent node in `.dsi` files; usage counts above export functions and a clickable "Parent: X" hint above the instance pragma. 1.x files keep the legacy structure view untouched.
  - **Two-way navigation closure** (C7): a shared source-span navigator backs the new "Open at line" action on reveal-node failure notifications (navigate-stage diagnostics carry file/line/column), and a successful reveal also lands the caret on the revealed line. Reveal gutter-marker rendering is covered by tests.
  - **WebSocket live preview** (A7): the transport B protocol - the `[u32 LE length][tag][payload]` envelope, the 24-byte RGBA8 raw-frame header, the png-encoding pairing rule - implemented as an opt-in loopback client with `setProbe`/`clearProbe` wired to the active editor's caret line; the file transport stays the default fallback, and a mock loopback WebSocket server covers the round-trip.
  - **Editor experience platform features** (D1-D7): in-file usage highlighting, Surround With (`{ }` / `if` / `for` for 2.0, named sections for 1.x), rename-only suggested refactorings for 2.0 functions (with `PsiNameIdentifierOwner` on the V2 function PSI), Join Lines for stacked comments, Alt+Shift+Left/Right inside 2.0 lists, statement up/down for 1.x declarations/sections and 2.0 statements, split/join of 2.0 comma lists, code block support over the structural braces, a language-aware Copy Path offering the `/Game/...` asset path inside the source root, and conditional HLSL injection for the bare-HLSL bodies (1.x functions and 2.0 `/// @custom`), silent when no HLSL language is installed.

## [1.5.0] - 2026-10-03

### Added

- Milestone M11 semantics (upstream `2.0.2` @ `b3bbf48`):
  - **Cross-file symbol model** (C1): the 2.0 declarations - `uniform`, globals, `export`/`extern` functions (workspace-visible linkage only), `struct`, and include edges - are PSI stubs served through four platform stub indexes (registered via a `stubElementTypeHolder` interface). `DreamShader2WorkspaceSymbols` merges the Bridge `dreamshader-symbol-index` v1 artifact (authoritative) with the offline stub-index fallback per name: a Bridge entry shadows a same-name stub entry, and missing or schema-incompatible Bridge data degrades silently to stub-only. Include closures resolve through the shared import-path semantics for downstream consumers. A 500-file project indexes and queries within budget.
  - **Conservative type inference for the 2.0 dialect** (C2): a value-type model over the 2.0 PSI - numeric scalars/vectors/matrices, texture kinds, `SamplerState`, `material`, `Substrate`, `void`, and user structs. Covers constructor/cast spellings, HLSL component-wise promotion (scalar + vector stays a vector, `float` widens `half`, differing shapes stay Unknown), swizzle narrowing (`float4.rgb` -> `float3`), material-attribute typing (`FrontMaterial` -> `Substrate`), texture `Sample` -> `float4`, catalog-driven `UE.*` return types (offline fallback included), `Substrate.*` -> `Substrate`, operand-typed `lerp`/`saturate`/`clamp`/`abs`, and include-closure structs/functions. Everything uninferred is Unknown and never produces a diagnostic.
  - **Substrate sugar checks keyed to upstream codes** (A6): operators outside the sugar report `DSH5293` (`-`, `/`, Substrate x Substrate, unary `-`, compound assignment, and a `lerp` mixing a Substrate value with a number); conflicting `Substrate.Slab` arguments report `DSH5295` (all eleven pin-conflict pairs, call and builder forms); `Haziness` without `Roughness` and `Thickness` without `Transmittance` report `DSH5296` in the call form; builder values report `DSH5297` for member writes after the first use or a whole assignment (a `Substrate.Slab()` rebind resets the builder) and `DSH5298` for member writes under an `if` the declaration is outside of (loop bodies stay out of the engine's DSH4383 territory). A positional argument after a named one reports `DSH2158`. The checks read parse-tree facts or confidently inferred types and never need Bridge data.
  - **`.dsi` override checks against the parent's parameter schema** (A6): when the Bridge symbol index carries a valid `instance.schema`, overrides are checked for unknown names (`DSH7258`), case-only matches (`DSH7264`), and type mismatches (`DSH7259`, including the static-switch-needs-`@static` rule and the float3-keeps-the-parent-alpha rule); a name that exists on the parent only as a layer or blend parameter reports `DSH7268` (resolved against the parent source's own symbol index, sibling-first). A missing or invalid schema means zero diagnostics - silent degradation, never a false positive.
  - **Assignment type-mismatch hints** (C2 consumer): a weak warning when an assignment's inferred value category differs from the target's declared category (numeric vs material/Substrate/texture/sampler/struct). Warning-level only - the engine check stays the authority - and bool/numeric coercions stay exempt.

### Changed

- Language baseline: `docs/language-v2-baseline.md` extended with the M11 semantics map (stub indexes, workspace merge, type model, A6 checks).

## [1.4.0] - 2026-10-03

### Added

- Diagnostics and tooling for milestone M10 (upstream `2.0.2` @ `b3bbf48`):
  - **DSH diagnostic-code catalog** (A5): a machine-readable catalog of all 639 upstream `DSHnnnn` codes (`messages/dreamshader-dsh-codes.json`), generated from the upstream `Docs/diagnostics/README.md` table by the re-runnable `scripts/generate_dsh_codes.py` (stable diff; curated Chinese summaries in `scripts/dsh-codes-zh.json`). Bridge diagnostics carrying a DSH code show the code meaning (Chinese when curated) in the tooltip and gain an **Open DSH documentation** Alt+Enter intention that opens the code's page in the upstream docs.
  - **Preventive checks keyed to upstream codes** (A5): 2.0 syntax (`uniform` / `export` / `extern` / `#pragma` / `#include`) in a `.dsm`/`.dsf` file is reported against `DSH2248`; the `#include <...>` angle-bracket spelling is reported against `DSH3203` with a quote-the-path quick fix; the existing `.dsh` asset-block shape check now cites `DSH2249`. All checks skip comments and string literals and never fire on ambiguous patterns.
  - **Legacy GLSL alias hints** (B3): in 1.x files, GLSL spellings (`mix` / `fract` / `mod` functions and the `vec3`-family / `mat`-family type aliases from the engine's legacy alias tables) get a weak warning citing `DSH5277` plus a one-click replace with the HLSL name. Files declaring a same-named function, comments, and string literals are skipped; `.dss`/`.dsi` files are left to the engine's own `DSH4250` error.
  - **`.dss` / `.dsi` file templates** (A4): the upstream `NewMaterial.dss` / `NewFunction.dss` / `NewInstance.dsi` template bodies with the upstream `{FILENAME}` / `{ASSETPATH}` / `{STEM}` / `{PARENT}` placeholders, in both the **Tools → DreamShader → Templates** actions (with a parent-material dialog for instances) and the **New → DreamShader** menu (2.0 section first, 1.x blocks below, matching the upstream New menu). Rendered products parse with zero errors; a file created under `DShader/Materials/` templates to `/Game/Materials/...`.
  - **`dsc` CLI toolchain integration** (A8): a configurable `dsc` executable path (project and application settings; `.ps1` paths are wrapped in PowerShell automatically) enables **Tools → DreamShader → DreamShader Bridge**: *dsc Check Current File*, *dsc Format Current File*, *dsc Migrate Current File* (runs `migrate -Check` first, then asks for confirmation before writing - upstream moves the 1.x source to `Saved/DreamShader/Migrated/`), and *dsc Refresh Symbol Index*. Without a configured path the actions are disabled with a hint.
  - **Decompiled-output support** (A9): sources under `Decompiled/` or `Intermediate/DreamShader/` are recognized as derived files - the `problemHighlightFilter` extension suppresses platform error highlighting on them (no false positives, batch Inspect Code skips them, manual editing remains possible), and the `readerModeMatcher` extension opens them in reading mode by default.
  - **Unknown-type inspection** (D8, first LocalInspectionTool migration): the Inputs/Outputs/Properties unknown-type rule moved from the semantic annotator into `DreamShaderUnknownTypeInspection` under **Settings → Editor → Inspections → DreamShader** - configurable level/switch, included in Inspect Code batch scans, with the same suggestion quick fix. The annotator no longer reports the rule, so nothing is reported twice.

### Changed

- Bridge tool window (B7): the Message column renders the DSH code as a link-styled prefix (with a cell tooltip carrying the code meaning), a toolbar button opens the selected diagnostic's DSH documentation, the active-file probe now recognizes `.dss`/`.dsi` files, and the summary line always appends the diagnostics source tag (a string-concatenation precedence bug dropped it whenever an info-level diagnostic was present).
- Settings UI gains the `dsc` executable path field (A8).

## [1.3.0] - 2026-10-03

### Added

- DreamShader editor-bridge 2.0 artifacts (milestone M9, upstream `2.0.2` @ `b3bbf48`):
  - `dreamshader-builtin-catalog.json` (the 2.0 binder's node catalog, also written by `dsc export-catalog`) is consumed at the top of the material-expression merge chain, above the 1.x `material-expressions.json`: classes, input pins, literal properties, aliases and positional-parameter order drive `UE.*` / `Substrate.*` completion and signature help. Schema/version mismatches degrade silently to the next data source.
  - `dreamshader-symbol-index` v1 read-only repository: parses `Saved/DreamShader/Index/<root>/<source>.index.json` (declarations, references, includes, parameter schema, `.dsi` instance schema) with schema validation and stamp-based rereads - the data foundation for the upcoming cross-file symbol model.
  - `reveal-node` support: the new **Reveal Node in Unreal Material Editor** action (`.dss` / `.dsi` files) checks the editor heartbeat, writes the request through the temp-file + atomic-rename queue, polls `Responses/<requestId>.json`, marks the revealed line with a gutter icon, and surfaces `DSH9050`-`DSH9057` navigate failures.
  - `bridge.db` `meta` table is now read and exposed as key/value pairs (generation timestamps, and `protocol` / `pluginVersion` / `engineDir` rows when the editor writes them).
  - `status.json` heartbeat and `owner.lock` liveness model: the status bar widget shows editor online/offline (with the editor's plugin version), and the preview and reveal-node request writers refuse to write requests into a dead editor (upstream discards them).

### Changed

- Bundled offline fallback upgraded (B2): a full V2 catalog (`dreamshader-builtin-catalog-fallback.json`) derived from upstream `Docs/builtins/ue.md` @2.0.2 - the 27 registered `UE.*` builtins plus `UE.StaticSwitchParameter`, `UE.CollectionParam`, `UE.SceneTexture`, and `UE.Expression` - is merged with the legacy six-entry manifest, so offline installs still complete all registered calls.
- Language baseline: `docs/language-v2-baseline.md` extended with the bridge-artifact consumption map (catalog / symbol index / meta / reveal-node / status).

## [1.2.0] - 2026-10-03

### Added

- DreamShader 2.0 dialect support (milestone M8, upstream `2.0.2` @ `b3bbf48`): `.dss` compilation units and `.dsi` material instances are now registered file types with dedicated project icons, joining `.dsm`/`.dsf`/`.dsh` on the shared DreamShader file type.
- Dialect model (`DreamShaderDialect`: `V1` / `V2` / `HEADER_MIXED`) detecting the dialect from extension plus content markers. Marker scanning runs the frozen 1.x lexer, so comments and string literals cannot trigger misdetection, and the 1.x `#Region` layout directive stays a 1.x marker.
- DreamShader 2.0 lexer: independent `.` / `,` / `;` punctuation tokens, `///` doc-comment tokens, line-start `#pragma` / `#include` / `#if`-style directive tokens, multi-character operators (`==`, `<=`, `+=`, `<<=`, ...), case-sensitive keyword/type classification (`export`, `uniform`, `Texture2D`, `Substrate`, ...), and exact-lexeme literals (`.5`, `1.f`, `1e3`, `1.5e-3`, `2.5h`, `1u`, `0x1F`, `1L`).
- DreamShader 2.0 parser with a permissive, error-element-free PSI: pragma/include directives, `///` doc blocks, uniform/global/struct/function declarations, parameters, the full statement set (block / variable / expression / `if` / `for` / `while` / `do` / `return` / `break` / `continue` / `discard` / empty), calls, named arguments (including `Pin[0] = ...` pin-index form), member/index chains, constructors, casts, and `{...}` initializer lists. `/// @custom` function bodies are captured verbatim (brace-balanced, opaque to `$`, `@`, mid-line `#`, and braces inside strings or comments).
- Mixed-dialect headers: a file with any 2.0 marker parses under the 2.0 front end while legacy 1.x blocks are captured as opaque blocks; 1.x files keep the frozen 1.x pipeline unchanged.
- DreamShader 2.0 syntax highlighting with new configurable color settings (doc comment, preprocessor directive, punctuation).
- Upstream 2.0 conformance fixtures: the upstream `Tests/Corpus/Lang` corpus (72 files), the three `language-v2` documentation example sets, and the upstream `.dss`/`.dsi` templates are all parsed in tests - valid examples with zero error elements.

### Changed

- Language baseline split into a dual baseline: `docs/language-baseline.md` (1.x, frozen) and new `docs/language-v2-baseline.md` (2.0 coverage map and test mapping).

## [1.1.0] - 2026-09-01

### Added

- DreamShader 1.8 source-root-aware imports. Project and plugin `DShader` trees are isolated by default; explicit `Project:`, `Plugin.<Name>:` and compatible `Plugins/<Name>:` qualifiers cross roots.
- Bridge diagnostic metadata support for upstream `code`, `stage`, `detail`, `assetPath`, `source`, shader platform, and quality level fields. Diagnostic codes are displayed in editor annotations.
- Coverage for plugin-owned imports, explicit cross-root imports, and containment enforcement.

### Changed

- Language and integration baseline updated from DreamShaderLang 1.6.3 to DreamShader 1.8.0/current main as checked on 2026-09-01.
- Preview request files are written to a temporary file and atomically moved into the Bridge request queue, preventing Unreal from consuming partial JSON.

### Fixed

- Import resolution no longer allows an owned DreamShader source to escape its source/packages root through `..` or an absolute path.
- Plugin sources no longer accidentally resolve an unqualified import from the project `DShader` tree when a same-named dependency belongs to another root.
- Imported declarations resolve correctly in IntelliJ temporary/light virtual files, restoring imported hover docs and parameter hints.
- Texture-sample and noise material generators no longer emit unresolved `Builtin/Texture.dsh` / `Builtin/Noise.dsh` imports; they now generate self-contained DreamShader 1.8 syntax.

## [1.0.0] - 2026-07-28

### Added

- `Template` declaration keyword and `Layout` section keyword for DreamShaderLang 1.5.1+ and 1.4.8+ syntax compatibility (lexer, parser, language rules, completion).
- `enableGitHubPackageSearch` project and application-level setting for toggling GitHub package search.
- Application-level `DreamShaderApplicationSettings` providing IDE-wide fallback for package index URLs, GitHub token, Bridge commands, and GitHub package search toggle.

### Fixed

- Template service now reads `sourceDirectory` from project settings instead of hardcoding `"DShader"`.
- Diagnostic per-file cache now invalidates on file change instead of on every project-wide PSI modification.
- Bridge SQLite driver/connection errors are now logged at WARN level instead of silently swallowed.
- Hub dialog now closes after any action button click instead of staying open.
- Build: `pluginSinceBuild` fallback unified to `261` (was `252`).

### Removed

- Empty unused packages `services/`, `startup/`, `toolWindow/`.
- Upstream VS Code extension reference from README; documentation now stands alone.

## [0.0.10] - 2026-07-27

### Fixed

- Plugin verifier internal API warning: replaced `PluginManagerCore.getPlugin()` with resource-based version and changelog reading (`dreamshader-plugin.properties` + `CHANGELOG.md`).

## [0.0.9] - 2026-07-27

### Changed

- Plugin XML: removed optional `com.intellij.modules.jcef` dependency — no longer needed since welcome page uses `HTMLEditorProvider`.
- Suppressed remaining unavoidable deprecated API usages inherited from IntelliJ Platform base classes.

### Fixed

- Plugin verifier internal API warning for `PluginManagerCore.getPlugin(PluginId)`.

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

[2.3.0]: https://github.com/tsdaer/dreamshader-language-support/compare/2.2.0...2.3.0
[2.2.0]: https://github.com/tsdaer/dreamshader-language-support/compare/2.1.0...2.2.0
[2.1.0]: https://github.com/tsdaer/dreamshader-language-support/compare/2.0.0...2.1.0
[2.0.0]: https://github.com/tsdaer/dreamshader-language-support/compare/1.5.0...2.0.0
[1.5.0]: https://github.com/tsdaer/dreamshader-language-support/compare/1.4.0...1.5.0
[1.4.0]: https://github.com/tsdaer/dreamshader-language-support/compare/1.3.0...1.4.0
[1.3.0]: https://github.com/tsdaer/dreamshader-language-support/compare/1.2.0...1.3.0
[1.2.0]: https://github.com/tsdaer/dreamshader-language-support/compare/1.1.0...1.2.0
[1.1.0]: https://github.com/tsdaer/dreamshader-language-support/compare/1.0.0...1.1.0
[1.0.0]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.10...1.0.0
[0.0.10]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.9...0.0.10
[0.0.9]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.8...0.0.9
[0.0.8]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.7...0.0.8
[0.0.7]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.6...0.0.7
[0.0.6]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.5...0.0.6
[0.0.5]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.4...0.0.5
[0.0.4]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.3...0.0.4
[0.0.3]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.2...0.0.3
[0.0.2]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.1...0.0.2
[0.0.1]: https://github.com/tsdaer/dreamshader-language-support/commits/0.0.1
