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

## Deliberate Limitations (downstream milestones)

- **The plugin is not the compiler.** The parser emits no error elements and no semantic checks for V2 yet; the engine bridge stays the authority (plan A5/A6 bring code-table and heuristic checks in M10/M11).
- **Routing is content-based.** The parser dispatch sees only the buffer, so a `.dss` with no V2 markers at all (a bare helper) loses V2 tree structure while remaining editable. The public `DreamShaderDialects.detect` API is extension-aware.
- **Expression precedence is flat.** Calls/arguments/chains get nodes; binary/ternary/assignment operators stay flat inside `EXPRESSION` until C2 (type inference) rebuilds the precedence tree.
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
| `DreamShaderRevealNodeTest` | Request body fields vs upstream contract, line clamp, envelope + added fields, navigate failure code, foreign-id/missing-file degradation, no temp residue |
