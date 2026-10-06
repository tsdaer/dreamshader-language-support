# DreamShaderLang 2.0 Baseline (V2 Dialect)

Coverage notes for the DreamShader 2.0 dialect supported by this Rider plugin. Refresh this file when upstream 2.0 behavior changes. The 1.x baseline lives in `language-baseline.md`; both dialects are supported side by side.

## Reference Snapshot

- Checked on `2026-10-03`
- Upstream: DreamShader `2.0.2` (`TypeDreamMoon/DreamShader`, commit `b3bbf48`)
- Rider plugin version: `1.3.0` (milestone M9; M8 language core unchanged, frozen)
- Upstream references: `Docs/language-v2/index.md`, `Docs/language-v2/substrate.md`, `Docs/language-v2/instances.md`, `Docs/language/source-files.md`, `Resources/Templates/*.dss|*.dsi`, `Tests/Corpus/Lang/**`

## Scope of M8 Support (plugin 1.2.0)

| Area | Status |
|---|---|
| File types `.dss` / `.dsi` | Registered on the shared `DreamShaderLang` file type (`dsm;dsf;dsh;dss;dsi`); distinct project-view icons |
| Dialect model | `DreamShaderDialect` (`V1` / `V2` / `HEADER_MIXED`), detected from extension + content markers with the frozen V1 lexer (comment/string safe; 1.x `#Region` excluded) |
| 2.0 lexer (`DreamShader2Lexer`) | Independent `DOT`/`COMMA`/`SEMICOLON` tokens (B1), `///` doc comments, line-start `#pragma`/`#include`/`#if` directive tokens, multi-character operators, case-sensitive keywords/types, literal forms `.5` `1.f` `1e3` `1.5e-3` `2.5h` `1u` `0x1F` `1L` |
| 2.0 parser (`DreamShader2PsiParser`) | Permissive, error-element-free tree: pragma/include directives, doc blocks, uniform/global/struct/function declarations, parameters, statements (block/var/if/for/while/do/return/jump/empty), flat postfix expressions (calls, named arguments incl. `Pin[0] =`, member/index chains, constructors, casts, initializer lists) |
| `/// @custom` bodies | Brace-balanced verbatim capture (`CUSTOM_BODY`): `$`, `@`, mid-line `#`, `'` and braces inside strings/comments cannot break the capture |
| Mixed `.dsh` | Any V2 marker routes the file to the 2.0 front end; 1.x blocks are captured as opaque `LEGACY_BLOCK`s |
| Dispatch | `DreamShaderDispatchingLexer` + `DreamShaderDialectAwarePsiParser` share one predicate; V1 files keep the frozen V1 pipeline (zero fixture diff, 545-test suite green) |
| Highlighting | `.dss`/`.dsi` use `DreamShaderSyntaxHighlighterV2`; new color keys `DREAMSHADER_DOC_COMMENT`, `DREAMSHADER_PREPROCESSOR` registered on the color settings page |
| Fixtures | Upstream corpus (`Tests/Corpus/Lang`, 72 files) parses: valid files zero error elements, `*.bad.*` files total-parse; the three language-v2 docs' fenced examples and the three upstream templates parse zero-error |

## Scope of M9 Support (plugin 1.3.0) - editor-bridge artifacts

| Area | Status |
|---|---|
| `dreamshader-builtin-catalog.json` | Parsed (`DreamShaderBuiltinCatalogManifest`, schema `dreamshader-builtin-catalog` v1) at the top of the material-expression merge chain (source `BRIDGE_BUILTIN_CATALOG`, above `BRIDGE_MANIFEST`, below user-configured manifests). Input pins → arguments, literal `properties[]` → property-qualified arguments, `positional[]` reorders the signature, `isAbstract` entries skipped, output value types normalized (`Float3`→`float3`, `Substrate` kept) |
| Bundled V2 fallback (B2) | `messages/dreamshader-builtin-catalog-fallback.json` derived from upstream `Docs/builtins/ue.md` @2.0.2: 27 registered `UE.*` builtins + `UE.StaticSwitchParameter` / `UE.CollectionParam` (+alias `CollectionParameter`) / `UE.SceneTexture` / `UE.Expression`; merged with the legacy six-entry manifest so offline installs complete everything the docs register |
| `dreamshader-symbol-index` v1 | Read-only project service `DreamShaderSymbolIndexRepository`: resolves `<root>/Saved/DreamShader/Index/<root>/<rel>.index.json`, validates schema string + version (mismatch → null data, no diagnostics), caches by (path, mtime, size); DTO covers declarations / references / includes / parameters / `.dsi` `instance` (parent, overrides, parent parameter schema) |
| `bridge.db` meta (B6) | `DreamShaderBridgeMetaReader` reads the `key`/`value` table generically; missing table or unreadable database degrade to an empty map |
| `reveal-node` | `DreamShaderRevealNodeAction` (`.dss`/`.dsi` only) → request `{action:"reveal-node", file, line, requestId}` (temp file + atomic rename into `Requests/`), polls `Responses/<requestId>.json`, reads payload `version` / `assetPath` / `instanceAssetPath` / `expressions[]` and `stage:"navigate"` failures (`DSH9050`-`DSH9057`); success pins a gutter marker on the revealed line |
| Editor liveness (A3.5) | `DreamShaderBridgeStatus`: `status.json` parse, `busy`-before-heartbeat-age rule, 30s stale window, missing file = definitively offline; the status bar widget shows editor online/offline (with plugin version) and the preview / reveal-node writers refuse to request into a dead editor |

## Scope of M11 Support (plugin 1.5.0) - semantics on the 2.0 PSI

| Area | Status |
|---|---|
| Stub-backed declarations (C1) | `uniform` / global / function / `struct` / include-edge nodes are PSI stubs through the Java holder interface `DreamShader2StubElementTypes`, registered as `stubElementTypeHolder` (external-id prefix `dreamshader2.`; the platform requires an interface holder). Four `StringStubIndexExtension`s: keys `dreamshader2.uniform` / `.function` / `.struct` / `.include`; the function index sinks `export`/`extern` names only (file-local helpers stay file-local); stub payload = name + type spelling + linkage flags; `DreamShaderElementTypes.FILE` is an `IStubFileElementType` whose `getStubVersion` bumps with the payload shape |
| Workspace symbol merge (C1) | Project service `DreamShader2WorkspaceSymbols`: Bridge `dreamshader-symbol-index` declarations shadow same-name stub entries, the union fills the rest; `instanceSchema` returns the `.dsi` parent parameter schema only when `valid`; include closures resolve via the shared import-path semantics (breadth-first, depth 16, cycle-safe) |
| Type model (C2) | `DreamShader2Type`: scalar/vector/matrix over the FLOAT/HALF/INT/UINT/BOOL families, texture kinds, sampler, `material`, `Substrate`, `void`, user `StructType`, `Unknown`; the spelling parser handles `float`..`float4x4` and the upstream index/catalog spellings (`float1` = scalar, `Float2` -> `float2` via the manifest normalizer) |
| Inference (C2) | `DreamShader2InferenceContext`: constructor/cast spellings; component-wise promotion (scalar⊕vector -> vector, float widens half, differing shapes -> Unknown); swizzle narrowing (xyzw/rgba/stpq, letter count = result size); a small conservative material-attribute table (`FrontMaterial` -> Substrate); `Texture.Sample` -> float4; `UE.*` return types from the material-expression catalog; `Substrate.*` -> Substrate; `lerp`/`saturate`/`clamp`/`abs` -> first-argument type; user functions from the same file and the include closure. Unknown is "no opinion" and never diagnostic |
| Substrate checks (A6) | `DSH5293`: `-`, `/`, Substrate×Substrate `*`, unary `-`, compound assignments, `lerp` mixing Substrate with a number; `DSH5295`: all eleven pin-conflict pairs, call and builder forms; `DSH5296`: `Haziness`/`Roughness` and `Thickness`/`Transmittance` companions in the call form (builder first-use companions not yet implemented); `DSH5297`: builder seal state machine (first use seals, whole assignment seals, `Substrate.Slab()` rebind resets; writes inside loops ignored - engine DSH4383 territory); `DSH5298`: member writes under an `if` the declaration is outside of |
| Instance checks (A6) | `DSH7258` / `DSH7264` / `DSH7259` (static-switch kind needs `/// @static`; `float3` overriding a `float4` parameter keeps the parent alpha) gated on a valid `instance.schema`; `DSH7268` resolved against the parent source's own symbol index (sibling file first, then the project file-name index); missing or invalid schema -> silence |
| Argument order (A6) | `DSH2158`: a positional argument after a named one on any V2 argument list |
| Mismatch hints (C2) | Weak warning when assignment categories differ (numeric/texture/sampler/material/Substrate/struct); bool↔numeric stays exempt; never an error |

## M12 Editing Experience (plugin 2.0.0, upstream `2.0.2` @ `b3bbf48`)

| Capability | Behavior |
|---|---|
| Context completion (C3) | PSI-driven contributor for `.dss`/`.dsi`: catalog-driven named arguments (`inputs[]` order; filled names and positionally consumed pins excluded), Substrate builder members on Substrate-typed receivers (C2 inference; already-written members excluded; documented offline pin fallback), `.dsi` parent parameters from the Bridge symbol index (silent without a valid schema), and `#pragma material(...)` keys/values (Bridge settings.json first, offline fallback from the upstream settings page) |
| Semantic colors (C4) | Three-level identifier colors (uniform parameter / function parameter / locals) via AST-layer name-slot detection; dedicated value color for explicitly `Substrate`/`material`-typed variables (per-file cached declared names; nothing inferred); `/// @key` attribute names and `.dsi` override names colored in the file-level pass; five new configurable color keys |
| Workspace usages (C5) | A second references-search executor serves the stub-backed 2.0 declarations over the word index; derived files (`Decompiled/`, `Intermediate/DreamShader/`) are excluded; `.dsi` override declarations surface as renameable usages so a rename can reach them |
| Rename safety net (C5) | `DreamShaderRenameImpactChecker` lists `.dsi` override files for a uniform rename and same-name export/call-site files for an export rename |
| Structure view (C6) | V2 factory/model/element: includes, uniforms grouped by `/// @group`, globals, structs, functions; `.dsi` shows a Parent node from the `#pragma instance` header; 1.x files keep the legacy structure view |
| Code Vision (C6) | Usage counts above export functions; a clickable "Parent: X" hint above a `.dsi` instance pragma that opens the resolved parent file |
| Navigation closure (C7) | `DreamShaderSourceSpanNavigator.openAtLine` backs the "Open at line" action on reveal-node failure notifications (navigate-stage diagnostics carry file/line/column); a successful reveal also moves the caret to the revealed line |
| WebSocket preview (A7) | Opt-in `previewTransport = "websocket"`: the `[u32 LE length][tag][payload]` envelope, tag-3 24-byte RGBA8 header, png pairing rule, plain-JSON `previewMaterial`/`previewControl`/`setProbe`/`clearProbe` out, frames in; probes attach at the active editor's caret line; the file transport stays the default fallback |
| Platform features (D1-D7) | In-file usage highlighting, Surround With (2.0 `{ }`/`if`/`for`, 1.x named section), rename-only suggested refactorings (V2 function PSI now implements `PsiNameIdentifierOwner`), comment-line Join Lines, list element moving, statement up/down, comma-list split/join, code block support, language-aware Copy Path (`/Game/...` inside the source root), conditional HLSL injection (silent without an HLSL language) |

## M13 Completion Intelligence (plugin 2.1.0, upstream `2.0.2` @ `b3bbf48`)

The completion coverage map per context; both contributors share one priority scale (`DreamShaderCompletionPriorities`) and one lookup-element factory, and dispatch per dialect (`DreamShaderCompletionDialects`).

| Context (press `.` / Ctrl+Space) | Suggestions |
|---|---|
| Dialect gating (C8) | The 1.x contributor stays silent in pure 2.0 files (`.dss`/`.dsi` and V2-marked `.dsh`); mixed `.dsh` headers keep legacy service; the 2.0 contributor serves only pure 2.0 files |
| Vector receiver (C9) | All in-set swizzles (`xyzw`/`rgba`, lengths 1..4) with result-shape tails |
| Scalar receiver (C9) | `x`/`r` with the family tail |
| Matrix receiver (C9) | `_mRC` cells for the declared rows x cols |
| Struct receiver (C9) | Fields from the symbol model (file + include closure) with declared types |
| Texture receiver (C9) | `sample()` with the `float4` result tail |
| `material` receiver (C9) | Material attributes (`FrontMaterial`, `BaseColor`, ...) mirroring the inference's attribute table |
| `Substrate` receiver (C9) | Builder pins from the catalog types plus the documented offline fallback; already-written pins excluded (scan limited to the enclosing function body) |
| Namespace prefix (`UE.` / `Substrate.` / catalog) (C9) | Catalog members with signatures, snippets, and output types; the two builtin namespaces stay suggestable offline |
| Unknown receiver (C9/C16) | 2.0: namespace roots plus callables; 1.x graph: scope callables plus locals - never cross-dialect keywords or intrinsics/constructor dumps |
| Named-argument name slot (C11) | Remaining catalog parameters (`Name = `, filled names and positionally consumed pins excluded), only after `(` or a top-level comma |
| Named-argument value slot (C11.2) | Documented enum values for `Enum`-annotated catalog parameters (transform vector/position bases from upstream `Docs/builtins/transform.md`); bare slot inserts quoted, open quote takes a bare value; non-enum parameters fall through to identifiers |
| Auto popup (C12) | `.` / `(` / `=` popup per dialect - 2.0 through lexical rules (member access, call args, pragma value slots), 1.x through the PSI-backed context analyzer |
| Bare Ctrl+Space in 2.0 (C13) | Locals before the caret, parameters, uniforms/globals with types, structs, file + workspace callables, constructors, HLSL intrinsics, dialect keywords/directives |
| `#include "` (C14) | Project `.dsh`/`.dss`/`.dsi` paths with in-string replacement, sharing the import candidate collector |
| `.dsi` override value slot (A10) | Declared-type-driven templates: `bool` -> `true`/`false`, scalars -> placeholders, vectors/matrices -> constructors; resource types stay silent |
| Hot path (C15) | Context analysis reads the committed originalFile PSI (lexer fallback only - no per-popup re-parse); callable candidates from one cached closure+signature pass; FilenameIndex scan cached per project revision and import candidates collected lazily |

## M14 Expression Intelligence (plugin 2.2.0, upstream `2.0.2` @ `b3bbf48`)

The first slice of the long-term "full expression type inference" target plus editor-facing consumers.

| Capability (task) | Behavior |
|---|---|
| Ternary typing (C17) | `a ? b : c` folds to the branch types - condition type discarded, arms merge with scalar/vector promotion, right-nested chains (`a ? b : c ? d : e`) close innermost-first; an Unknown arm yields the other arm |
| Compound assignments (C17) | `+=`/`-=`/`*=`/`/=`/... keep the left-hand type instead of absorbing the right-hand vector shape |
| Include-closure calls (C17) | User function calls resolve declared return types through the include closure (non-export helpers included) before the workspace export view; nearest include first, Unknown as fallback |
| Compound-assignment mismatch (C17) | The weak-warning category check covers compound assignments and stays silent when `DSH5293` already flags the Substrate operand |
| Signature help named arguments (C19) | The parameter-info highlight maps a named argument to its parameter by name (case-insensitive, string-literal value carets included); positional comma count decides only in gaps and for unmatched names |
| Signature overload union (C19) | Catalog signatures and the documented built-in spelling of the same callable appear as distinct rows (catalog first, presentations de-duplicated) |
| Postfix templates (C18) | The selector serves `.dss`/`.dsi`: the candidate is the value after the last top-level assignment (`==`/comparisons excluded, compound forms included), and `$expr$` wraps the expression-shaped PSI node; templates `if` / `ifel` / `lerp(expr, y, s)` / `saturate(expr)` / `expr.Sample()` / `expr.$swz$` |
| Extract Variable (C20) | Selection or caret gesture inside a 2.0 function body inserts `Type name = expr;` before the enclosing statement (inferred spelling, de-duplicated suggested name) and starts a linked in-place rename over declaration + use site; refuses with a hint when the type is unknowable or the statement sits in an `if` arm / loop body; 1.x files keep the platform default |

## M15 Refactoring & Dialect Refinement (plugin 2.3.0, upstream `2.0.2` @ `b3bbf48`)

The Introduce/Inline refactoring family grows over the C20 foundation, mixed `.dsh` completion
splits per caret region, and three inference slices land.

| Capability (task) | Behavior |
|---|---|
| Extract Function (C21) | A selection of complete statements inside a 2.0 function body becomes a file-local `void` helper inserted after the current function; the selection is replaced with a call. Free names read from outside become value parameters (declared spelling, first-appearance order); names written in the selection and used afterwards return as `out` parameters, `inout` when the selection also reads them. Declared spellings only - names resolving to functions or namespace roots stay as-is. Linked in-place rename over the new declaration and the call site |
| Extract Function refusals (C21) | `return`/`break`/`continue` in the selection, selections not covering whole sibling statements, writes through member/index chains, declarations used after the selection (scope change), and out-values with unresolvable types all refuse with a hint and an untouched document; 1.x files keep the platform default |
| Inline Uniform (C22) | The `DreamShader.InlineUniform` Refactoring-menu action replaces every in-file usage of a single-declarator uniform with its parenthesized initializer and deletes the declaration with its `///` doc block. Pre-checks refuse (hint, document untouched) on `.dsi` overrides (rename safety net), cross-file usages, other initializers reading the uniform, missing initializers, multi-declarator uniforms, and non-`.dss` files |
| Mixed-`.dsh` region dispatch (C23) | `servesV1At`/`servesV2At`/`regionAt` map the caret to a region over the 2.0-parsed PSI: `LEGACY_BLOCK` ranges serve the 1.x contributor, V2 declaration ranges (uniform/global/struct/function/pragma/include/doc) serve the 2.0 one, top-level gaps serve both (a mixed header legally holds both dialects at the top level). Pure files answer file-level at every offset; the typed-handler auto-popup applies the V2 lexical rules only inside V2 regions |
| Lexical scope resolution (C24.1) | A local declaration lends its type only when its scope owner (nearest BLOCK, or the `for` header for loop-init declarations) also contains the use site - earlier sibling blocks no longer produce false confident types, and shadowing resolves nearest-declaration-first |
| Operand-typed intrinsics (C24.2) | `normalize`/`min`/`max`/`floor`/`ceil`/`round`/`frac` carry the first argument's type; `dot` of equal-shape numeric operands yields a scalar of the wider family; `cross` of two same-family 3-vectors yields that 3-vector. Unlisted intrinsics stay Unknown |
| Call-argument mismatch (C24.3) | Arguments of user-declared calls (file + include closure; the workspace export layer has no parameter shapes) are checked against the callee's declared parameter types as weak warnings on the same category scale as assignments; named arguments match by name case-insensitively, and any Unknown side stays silent |

## M16 Inlining & Dialect Segmentation (plugin 2.4.0, upstream `2.0.2` @ `b3bbf48`)

The Introduce family gains Inline Variable and Extract Function's single return-value upgrade,
navigation surfaces segment by dialect, and two conservative inference/completion slices land.

| Capability (task) | Behavior |
|---|---|
| Structure view dialect routing (C27) | The structure view factory routes by dialect: files `DreamShaderDialects.routesToV2` (`dss`/`dsi`, V2-only `dsh`) build the 2.0 model - previously the 2.0 factory was never registered and those files fell through to an empty 1.x view; pure legacy files keep the 1.x model unchanged. A mixed `.dsh` shows one Legacy node per `LEGACY_BLOCK` (labeled from the block's first 1.x declaration) whose children come from a file-level cached text re-parse through the dialect-aware parser, navigable at absolute offsets; a malformed legacy block degrades to a childless node |
| V2 inlay parameter hints (C28) | When the 1.x resolver (`DreamShaderCallSignatureResolver`) finds no signature and the caret sits in a V2-served region (`servesV2At`), V2 user functions from the file analysis and the include closure supply parameter names; legacy regions keep the 1.x path, `@custom` bodies stay empty, named arguments keep suppressing the hint |
| Inline Variable (C25) | The `DreamShader.InlineVariable` Refactoring-menu action replaces every read usage of a single-declarator local with its parenthesized initializer and deletes the declaration with its `///` doc comment (body comments above a local are lexer trivia, so the doc lines are swallowed textually). Full precheck before the write: missing initializer, multi-declarator, impure initializer (references to parameters/locals/globals - uniforms are the only allowed reads; assignments/compound/`++`/`--`; user-declared or unknown calls), write-target usages, and `@custom` bodies refuse with a hint and an untouched document; same-name references outside the declaration's scope do not belong to it and stay untouched |
| Extract Function single return value (C26) | When exactly one written-then-used value never reads itself (`out`), the last selected statement is its final simple assignment (`name = expr;`), and the name resolves to a bare single-declarator no-initializer declaration in the selection's own block with no references between them, the helper becomes `Type Name(inParams) { …; return name; }` (a single write folds the last statement into `Type name = expr;`, several writes get a leading bare declaration) and the call site becomes `Type name = Name(inParams);` with the bare declaration line removed; the linked rename covers function name, call-site name, and receiving variable. Parameters, initialized/outer declarations, and intermediate uses keep the `out` matrix |
| Extract Function `inout` correction (C26) | Compound assignments and `++`/`--` targets count as reads (they consume their own old value), so their qualifier is `inout` instead of `out` |
| Schema-driven `.dsi` override values (C29) | A valid parent-schema parameter matching the override's name is the authority: the schema `type` spelling re-drives the templates, `kind == StaticSwitch` forces `true`/`false` even over an `int`/`float` declaration, `kind == Texture` stays silent; a name miss or missing/invalid schema keeps the declared-spelling path (A10) unchanged |
| out/inout write-back narrowing (C30) | A lazily built per-function write-back table records `(call, variable → formal type)` for plain reference arguments of `out`/`inout` formals with confident spellings (positional pairing, named arguments by name, more arguments than parameters stays silent); `typeOfName` returns the latest record whose call ends before the use site, so swizzle members and mismatch checks see the written shape. A nearer re-declaration beats the record; unparsable formal spellings and calls whose argument is not a plain reference never narrow |

## Deliberate Limitations (downstream milestones)

- **The plugin is not the compiler.** The parser emits no error elements; the engine bridge and `dsc check` stay the semantic authority. M10/M11 add only conservative, upstream-keyed heuristics that go silent when their deciding data (a confident inferred type, a valid Bridge schema) is absent.
- **Routing is content-based.** The parser dispatch sees only the buffer, so a `.dss` with no V2 markers at all (a bare helper) loses V2 tree structure while remaining editable. The public `DreamShaderDialects.detect` API is extension-aware.
- **Expression precedence is flat.** Calls/arguments/chains get nodes; binary/ternary/assignment operators stay flat inside `EXPRESSION`. C2's inference folds operands left-to-right by design - exact enough for category checks, deliberately not a precedence-faithful evaluator.
- **Lexer opacity inside `@custom`.** Upstream reports nothing inside a captured body, and neither do we - but bad-character tokens may still drive editor squiggles inside `@custom` bodies until a raw-slice lexer state lands.
- **Case sensitivity vs legacy spellings.** The 2.0 lexer keeps V1 keyword/type spellings recognized (lowercase match) for mixed-header highlighting, except `substrate`, which stays an identifier because the 2.0 type is case-sensitively `Substrate`.

## Test Mapping

| Test | Covers |
|---|---|
| `DreamShaderDialectTest` | Extension x content marker detection matrix; comment/string immunity; `#Region` stays V1 |
| `DreamShader2LexerTest` | B1 punctuation tokens, doc comments, directives, maximal-munch operators, literal lexemes, case sensitivity |
| `DreamShader2PsiParserTest` | Node shapes: doc blocks, uniforms (grouped declarators), export/extern functions, named arguments, `@custom` verbatim capture, structs with array dims, mixed `.dsh` legacy capture, statements |
| `DreamShaderUpstreamV2CorpusTest` | Upstream corpus zero-error (valid) / total-parse (`.bad.`), template rendering |
| `DreamShaderUpstreamV2DocsTest` | `index.md` / `substrate.md` / `instances.md` fenced examples parse zero-error; `.dsi` example pins 5 overrides |
| `DreamShaderBuiltinCatalogManifestTest` | Catalog DTO parsing (inputs/properties/positional ordering, aliases, abstract skip, output-type normalization), schema/version rejection, bundled fallback >= 27 `UE.*` calls |
| `DreamShaderBuiltinCatalogMergeTest` | Merge priority: builtin-catalog > bridge manifest, configured manifest > builtin-catalog, bundled = V2 + legacy merge |
| `DreamShaderSymbolIndexRepositoryTest` | Index path layout, upstream-shape parsing, `.dsi` instance section, schema rejection, missing-file degrade, rewrite reread |
| `DreamShaderBridgeMetaReaderTest` | meta table read; missing table / missing file / blank path degrade |
| `DreamShaderBridgeStatusTest` | status identity fields, busy-over-heartbeat rule, 30s staleness, owner.lock parse, ISO-8601 heartbeat |
| `DreamShader2StubIndexTest` | Stub population (uniform/function/struct/include), linkage-only function index, rebuild idempotency after rename, 500-file scale budget |
| `DreamShader2WorkspaceSymbolsTest` | Bridge-shadows-stub merge per name, degrade-to-stub without Bridge data, structs/uniforms merge, instance-schema validity gating, include closure resolution |
| `DreamShader2TypeInferenceTest` | Spelling parsing (`float1`, matrices), file-scope/parameter types, upstream expression examples (swizzle, promotion, catalog, Sample), silent-Unknown contract, constructor/Substrate typing; C17: ternary branch typing (plain/promoted/right-nested), compound-assignment left-hand shape, include-closure helper returns; C24: lexical-scope resolution (if-arm/for-init/shadowing), operand-typed intrinsics (`dot`/`cross`/`normalize`, shape mismatches, unlisted silence); C30: out/inout write-back narrowing (pre-call stays declared, post-call narrows, named arguments, Unknown formals silent, nearer re-declaration wins, chained inference) |
| `DreamShader2SubstrateDiagnosticsTest` | `DSH5293`/`DSH5295`/`DSH5296`/`DSH5297`/`DSH5298` and `DSH2158` positive+negative pairs; C2 mismatch is weak-warning-only; C17: compound-assignment mismatch warns, stays silent on Substrate operands and between numerics; C24: call-argument mismatch warns positionally and by name, silent for same-category and Unknown sides |
| `DreamShaderSignatureHelpAnalyzerTest` | Call-context resolution, parameter indices, declared/catalog/built-in signature tiers; C19: named-argument caret mapping (string interiors included), gap and unmatched-name fallbacks, overload union order and dedup |
| `DreamShaderPostfixTemplatesTest` | Template registration and template strings; 1.x line rules (accept bare expressions, reject assignment lines); C18: V2 declaration/caret contexts accepted, empty/statement contexts rejected, compound/comparison `=` handling |
| `DreamShader2ExtractVariableTest` | C20: call/member/literal extraction with inferred spellings and document diffs, caret gesture, `IntroduceVariable` action routing, unknown-type and loop-body refusal with untouched documents, 1.x silence |
| `DreamShader2ExtractFunctionTest` | C21: single/multi-statement extraction with in/out/inout parameter synthesis, call-site replacement, insertion position, `ExtractMethod` action routing; refusal matrix (return, partial selection, member write, declared-inside-used-after) with untouched documents, 1.x silence; C26: compound/increment targets are `inout`, single-return-value upgrade (bare declaration removed, receiving declaration at the call site, multi-write body form) with out-matrix fallbacks (initialized declaration, parameter target, intermediate uses, two out values, conditional last statement) |
| `DreamShader2InlineUniformTest` | C22: initializer inlining into all usages with declaration + doc-block removal, `DreamShader.InlineUniform` action routing; refusals (no initializer, `.dsi` override, another initializer reads the uniform, cross-file usages) with untouched documents, 1.x silence |
| `DreamShader2InlineVariableTest` | C25: inlining into every read usage with declaration + doc-comment removal, `DreamShader.InlineVariable` action routing; refusal matrix (no initializer, multi-declarator, initializer reads parameter/local/global, initializer side effects, user-function call, write-target usage, `@custom` body) with untouched documents, out-of-scope same-name usages untouched, 1.x silence |
| `DreamShader2InstanceOverridePassTest` | `DSH7258`/`DSH7264`/`DSH7259`/`DSH7268` with a valid schema; silence without Bridge data or with an invalid schema |
| `DreamShaderRevealNodeTest` | Request body fields vs upstream contract, line clamp, envelope + added fields, navigate failure code, foreign-id/missing-file degradation, no temp residue |
| `DreamShader2CompletionTest` | The M13 completion matrix (36 cases): dialect gating plus C23 mixed-`.dsh` region matrix (legacy/V2/BOTH regions, pure-file whole-file answers), receiver-typed members (swizzle/matrix/struct/texture/material/Substrate/namespaces/degraded), named-argument slot checks and catalog enum values, bare identifiers, `#include` paths, `.dsi` parent parameters and override values, pragma keys/values, and the D9 quote-aware insertion matrix via real lookup selection; C29: schema-driven override values (schema type agreement, StaticSwitch over `int`, Texture silence, name-miss fallback) over injected index fixtures |
| `DreamShaderCompletionHotPathTest` | C15: PSI-backed vs text-backed suggest parity across contexts, lazy import-candidate evaluation, callable-candidate cache with PSI-revision invalidation, cached FilenameIndex scan visibility |
| `DreamShaderCompletionSuggesterTest` | 1.x suggester contexts (settings keys/values, declaration heads, outputs `Base.*`, graph keywords/callables) and the C16 non-namespace dot tightening |
| `DreamShader2SemanticClassifierTest` | Uniform/function parameter name slots, Substrate variable usage color, V1 files skip the V2 branches |
| `DreamShader2RenameImpactTest` | Export uniform references, `.dsi` override impacts, same-name export impacts with derived exclusion, light-reference rename |
| `DreamShader2StructureViewTest` | V2-only builder routing, `@group` grouping, includes/structs/globals, `.dsi` Parent node; C27: factory routing (`dss`/`dsi` to the 2.0 model, legacy files unchanged), mixed-`.dsh` Legacy nodes with 1.x declaration children and navigation offsets |
| `DreamShaderInlayParameterHintsProviderTest` | Call-context signature tiers, 1.x declared/catalog/built-in hints; C28: V2 user-function hints from the file and the include closure, mixed-`.dsh` per-region resolution (legacy regions keep 1.x), named-argument and declaration-head suppression |
| `DreamShader2CodeVisionTest` | Export usage counts, helper silence, `.dsi` Parent hint, legacy-file silence |
| `DreamShaderSourceSpanNavigatorTest` | Hook-injected navigation, missing-file report, gutter marker rendering (range/tooltip/equality), out-of-range line rejection |
| `DreamShaderPreviewWsTest` | Frame envelope decode (JSON/PNG/raw), raw header fields, image painting, truncated-header rejection, client message contract, mock-server round-trip |
