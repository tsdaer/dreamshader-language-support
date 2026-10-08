# DreamShader 2.1.1 intelligence

Implementation baseline: [DreamShader v2.1.1](https://github.com/TypeDreamMoon/DreamShader/tree/v2.1.1), commit `dbdcbede47f84b7bf4b9f671138c1f87fd3e5010`. Plugin version remains 2.5.0; checked 2026-10-08. The compiler-supported 1.x dialect remains available. `.dsp` support is deferred.

## Editor behavior

| Area | Current behavior |
|---|---|
| Scope and references | Cached lexical bindings handle nested blocks, declaration order, loop scope, parameters and local shadowing. Completion, inference, hover docs, goto and Rename use the same bindings. Additional names in grouped local/uniform/global declarations have separate Rename targets. User struct names remain distinct from function/parameter/declarator names, including identical type/parameter spellings; type slots carry independent PSI references for Rename at the caret. |
| Call information | User functions and catalog nodes share signatures containing parameter order, type, qualifier, return type and documentation. Parameter information and inlay hints handle named arguments, nested calls and quoted strings. |
| Completion | BASIC first shows visible symbols; SMART ranks known expected types. The second manual invocation expands to legal header functions, structs, uniforms and globals; accepting a result adds include/import in the same undo operation. Strings, directives, metadata, members and named argument names suppress unrelated auto-import entries. |
| Insertions | Existing parentheses, quotes and argument separators are reused. Calls with parameters place the caret inside the argument list. Mapped instance names that contain spaces insert a legal source identifier plus `/// @name`. |
| Catalog | Schema v1 remains compatible. Optional enum/default values, aliases, const-property flags, full outputs, material attributes and dynamic-pin flags are read without changing upstream schemas. Docs show enums/defaults/output names. Engine-generated metadata remains authoritative over the bundled documented fallback. |
| Outputs and swizzles | Named output selection precedes numeric swizzle. Multi-output nodes remain Unknown until a selected output is known. `xyzw` / `rgba` accept case differences and repetitions, including scalar `.xx` / `.rrr`; mixed alphabets and out-of-bounds channels are rejected consistently. |
| Substrate | Builder members come from Slab pins, excluding earlier definite writes to the same binding; reads, future writes, other variables and uncertain branches do not suppress members. |
| Instances | Parent candidates stay in the instance's owning source root. Bridge schema drives unoverridden mapped names and typed values. Source parent/include analysis supplies fallback suggestions only. Static switch/mask, texture, Font and other resource templates follow `.dsi` syntax. |
| Metadata/preprocessor | `@name`, `@default`, `@sampler`, `@static`, `@asset`, `@page`, `@slider` and existing extension keys remain available. Eight conditional/definition directives, `defined`, file-local defines and six `DS_*` variables are suggested. Conditional structure and reserved prefix modifications report upstream codes. |
| Conservative checks | Source-proven invalid swizzles, graph matrix construction, clearly unbounded loops and `@custom` lifted calls reading HLSL locals receive upstream diagnostics. Dynamic-pin omissions, unknown receiver types and missing instance schemas do not produce invented hard errors. |

## Imports and compilation environment

[Upstream import rules](https://github.com/TypeDreamMoon/DreamShader/blob/v2.1.1/Docs/language/import.md): omitted extensions only append `.dsh`; explicit `.dsh` / `.dss` headers are legal. Relative lookup uses the file directory, owning source root and that root's `Packages/`. `/` anchors cross-root lookup. `Project:` / `Plugin:` qualifiers and package entry auto-expansion are removed. Selected auto-import paths are resolved again to prevent shadowing from redirecting an insertion.

[Swizzle rules](https://github.com/TypeDreamMoon/DreamShader/blob/v2.1.1/Docs/graph/swizzle.md), [instance rules](https://github.com/TypeDreamMoon/DreamShader/blob/v2.1.1/Docs/language-v2/instances.md), [preprocessor rules](https://github.com/TypeDreamMoon/DreamShader/blob/v2.1.1/Docs/language/preprocessor.md) and the [IR catalog](https://github.com/TypeDreamMoon/DreamShader/blob/v2.1.1/Source/DreamShaderLang/Private/IR/IRCatalog.cpp) supply the remaining fixed-version references.

The local preprocessor deliberately performs partial evaluation: known literals, comparisons, definitions and boolean short-circuiting can prove branch state; external values and unsupported arithmetic remain Unknown. Definitions never leak between files. Known builtins exist, but their configured values are not guessed. Unknown branches remain available to editor features; conflicting types of same-scope declarations remain Unknown, as do user function return types with ambiguous signatures. The plugin does not reproduce the compiler's complete graph lowering or environment evaluation; Bridge diagnostics retain their reported severity.

## Caching and regression fixtures

File analysis/bindings and preprocessor models cache against source changes. Include closures additionally track PSI/VFS changes and source-root configuration. Catalog files and Bridge index data use separate bounded/stamp caches. Instance parents and asset candidate lists track PSI/VFS, configuration and indexing state. Second-invocation queries use stub indexes; regular completion performs no network requests, Unreal scan or repeated full-project file enumeration. Cancellation propagates; project index requests degrade quietly while indexes are unavailable.

- `DreamShader211IntelligenceTest`: editor fixtures for scope, references/Rename, SMART ranking, call/named argument resolution, rich catalog, output selection, swizzles, Substrate writes, insertion reuse, auto-import plus one-step Undo, legal header filtering, instance parent/name mapping, include cycles/diamonds/cache invalidation, graph checks and cancellation.
- `DreamShaderPreprocessorModelTest`: file isolation, reserved prefix/structure, unknown environments, short-circuiting, builtin presence and quoted URL values.
- `DreamShaderUpstreamV2DocsTest`: 11 tagged examples pinned in `src/test/resources/upstream/v2.1.1/documented-examples.json`; declaration checks ensure V2 snippets actually route to V2 PSI. The older 2.0.2 corpus/document fixtures remain as compatibility tests.
- Existing source-root/package import, navigation, refactoring, diagnostics, settings and hot-path fixtures were updated where their expectations contradicted the current import rules.

The performance fixture creates 10,000 actual source lines and a chain of 100 headers, warms ten times, then measures 30 completion-suggester invocations. P95 is sample 29 after sorting. This measures cached semantic candidate generation, not total GUI rendering or cold indexing. The acceptance threshold is 150 ms; the full-suite run before final packaging measured **19.32 ms** on this host. Results may vary by hardware and IDE load.

## Reproduce verification

```powershell
.\gradlew.bat test --no-configuration-cache
.\gradlew.bat build buildPlugin --no-configuration-cache
python scripts/generate_dsh_codes.py --upstream build/upstream-2.1.1 --source "DreamShader 2.1.1 @dbdcbede47f84b7bf4b9f671138c1f87fd3e5010"
```

Run the [JetBrains Plugin Verifier](https://github.com/JetBrains/intellij-plugin-verifier) against locally installed/cached IDEs (the verifier and plugin dependencies must already be cached for `-offline`):

```powershell
$verifierJar = 'path/to/verifier-cli-all.jar'
$ideDirectories = @('path/to/IDE-261', 'path/to/IDE-262', 'path/to/IDE-263')
java -Xmx3g -jar $verifierJar check-plugin build/distributions/dreamshader-language-support-2.5.0.zip @ideDirectories -offline -verification-reports-dir build/reports/pluginVerifier211-verified
```

The diagnostic generator produces 832 codes and is deterministic for the pinned input. Plugin verification uses the cached JetBrains Plugin Verifier against the local IntelliJ SDK. Final test/build/verifier results are recorded below. No publishing is performed.

## Final validation (2026-10-08)

- `build buildPlugin --no-configuration-cache`: successful; 920 tests, zero failures/errors/skips.
- Performance: 10,000 lines / 100 includes / 30 warm samples, semantic completion P95 **19.3203 ms** (threshold 150 ms). Actual GUI rendering and cold indexing are outside this metric.
- `scripts/check_i18n_keys.ps1`: passed. `git diff --check`: passed.
- Regenerating the 832-code catalog from the pinned upstream input produces identical SHA-256 output.
- Distribution: `build/distributions/dreamshader-language-support-2.5.0.zip` (SHA-256 `25e61632e95336f2122a4d9b1b91d549a0fe2598fe8f6f90b8dbccc4d37955bb`).
- JetBrains Plugin Verifier 1.410, offline, final ZIP: **Compatible** on `IU-261.25134.95` (2026.1.3), `IU-262.10968.63` (2026.2.3) and `IU-263.6259.32` (EAP). No compatibility problems, invalid-plugin reports or override-only API violations.
- The verifier retains API advisories: two scheduled-for-removal uses; 14/15 deprecated uses, 56 experimental uses, and 65 internal uses on the 261/262 builds. These are recorded for future maintenance and do not change the Compatible verdict. Reports: `build/reports/pluginVerifier211-verified/`.
- Verified ZIP SHA-256 matches the distribution above after verification.

No release, external AI integration or upstream schema modification was performed. Runtime engine asset/schema validation still depends on the actual project's Bridge output; these local fixtures do not substitute for an Unreal integration session.
