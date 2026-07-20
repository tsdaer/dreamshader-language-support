# Upstream Parity Development Plan

Parity baseline: VS Code DreamShaderLang extension v1.5.3 → target v1.6.3

## Gap Summary

| # | Feature | Upstream Version | Rider Status |
|---|---------|-----------------|--------------|
| 1 | `Group("Name") { ... }` property scopes | 1.6.0 | Missing |
| 2 | Single-output return-value functions (`Function float Luma(...)`) | 1.6.0 | Missing |
| 3 | Optional `=` between section name and body | 1.6.0 | Already done |
| 4 | Bare `"/Game/..."` asset paths in parameters | 1.6.0 | Already done |
| 5 | Graph control-flow as valid syntax (`if`/`else`/`for`/`while`) | 1.6.1 | Partial (lexed but flagged as error) |
| 6 | Syntax highlighting: Function/GraphFunction bodies, Group scopes, Base.*, Substrate.* | 1.6.1 | Partial (Group missing) |
| 7 | SignatureHelp for return-type functions & document outline for Function params | 1.6.1 | Partial (needs return-type support) |
| 8 | `Group` completion label (currently only `propgroup`) | 1.6.3 | Missing (no Group at all) |
| 9 | Allman-style formatting syntax highlighting | 1.6.3 | Missing |
| 10 | WebSocket/live-stream preview transport | 1.5.3 | Not implemented (reserved for future) |
| 11 | GLSL legacy-alias compatibility diagnostics | — | Blocked (needs upstream confirmation) |

---

## Phase 1: Core Syntax Upgrade (v1.6.0 syntax) — Priority: High

### 1.1 Group("Name") { ... } property scopes

Files to create/modify:
- `lexer/DreamShaderLexer.kt` — add `GROUP` token type, add `group` to keyword recognition
- `parser/DreamShaderPsiParser.kt` — parse `Group("Name") { ... }` as a typed section-like node; flatten nested declarations into parent scope
- `psi/` — new/updated PSI node types for Group
- `highlighting/DreamShaderSyntaxHighlighter.kt` — highlight Group keyword
- `highlighting/DreamShaderSemanticTokenClassifier.kt` — semantic token classification
- `editor/DreamShaderCompletionContributor.kt` — `Group` completion with snippet `Group("Name") { ... }`; also keep `propgroup` alias
- `editor/DreamShaderFormattingModelBuilder.kt` — formatting support
- `navigation/DreamShaderStructureViewFactory.kt` — structure view integration
- `diagnostics/DreamShaderSemanticAnnotationPipeline.kt` — diagnostics for Group shape validation
- Tests: lexer, parser, completion, highlighting, structure view, diagnostics

### 1.2 Single-output return-value functions

Files to create/modify:
- `lexer/DreamShaderLexer.kt` — `return` is already lexed but diagnosed as error
- `parser/DreamShaderPsiParser.kt` — update `validateDeclarationHeader` to accept `TYPE` token after `Function`/`GraphFunction` keyword (e.g., `Function float Luma(...)`)
- `psi/DreamShaderDeclaration.kt` — add `returnType` accessor
- `diagnostics/DreamShaderSemanticAnnotationPipeline.kt` — remove `return` keyword error for function bodies; add return-type validation; stop requiring `out` parameter for return-value functions
- `editor/DreamShaderCompletionContributor.kt` — completion for return-type functions
- `editor/DreamShaderSignatureHelp.kt` — update `USER_FUNCTION_DECLARATION_HEAD_PATTERN` to accept return type slot; render `: <type>` return suffix
- `editor/DreamShaderInlayParameterHintsProvider.kt` — update hints for return-type functions
- `navigation/DreamShaderStructureViewFactory.kt` — add Function/GraphFunction params to document outline
- `navigation/DreamShaderDocumentationProvider.kt` — hover docs for return-type functions
- `highlighting/DreamShaderSyntaxHighlighter.kt` — highlight return type
- Tests: parser, completion, signature help, documentation, diagnostics, inlay hints

---

## Phase 2: Graph Control-Flow & Highlighting Fixes (v1.6.1) — Priority: Medium

### 2.1 Graph control-flow as valid syntax

Current state: `for`/`while`/`do`/`switch`/`break`/`continue`/`return` are all flagged as errors in Graph sections and function bodies. Upstream v1.6.1 makes `if`/`else`/`for`/`while` valid.

Files to modify:
- `parser/DreamShaderPsiParser.kt` — add structural parsing for `if`/`else`/`for`/`while` blocks as self-terminating statements
- `diagnostics/DreamShaderSemanticAnnotationPipeline.kt` — update diagnostics:
  - `if`/`else` already valid (remove error flags where incorrectly applied)
  - `for`/`while`/`do` — change from error to valid syntax
  - `switch`/`case`/`default`/`break`/`continue` — may need to keep as unsupported or change per upstream
  - `return` — allow in return-value `Function` bodies (from Phase 1.2)
  - Variables inside branches resolve in scope (completion + diagnostics)
- `editor/DreamShaderCompletionContributor.kt` — completions inside if/for/while branches
- Tests: parser control-flow tests, diagnostics update, completion inside branches

### 2.2 Syntax highlighting parity

Files to modify:
- `highlighting/DreamShaderSyntaxHighlighter.kt` — ensure Function/GraphFunction bodies, `Base.<Attribute>`, and `Substrate.<Node>()` are correctly scoped
- Review TextMate-like scoping for declarations after `Group("X") { ... }` (ensure `}` doesn't end section highlight)

---

## Phase 3: Editor Quality (v1.6.3) — Priority: Low

### 3.1 Allman-style formatting

Files to modify:
- `lexer/DreamShaderLexer.kt` — ensure opening `{` on its own line is correctly tokenized in section/body contexts
- `highlighting/DreamShaderSyntaxHighlighter.kt` — verify highlighting doesn't break when `{` is on next line
- `editor/DreamShaderFormattingModelBuilder.kt` — optionally add brace-placement code style option
- `editor/DreamShaderSmartEnterProcessor.kt` — smart enter handling for Allman-style
- Tests: highlighting tests with Allman-style fixtures

---

## Phase 4: WebSocket Preview Transport — Priority: Low / Future

- `preview/DreamShaderPreviewRequestWriter.kt` — add WebSocket transport path
- `preview/DreamShaderPreviewResultReader.kt` — add WebSocket frame reading
- `preview/DreamShaderMaterialPreviewPanel.kt` — live frame rendering at configured frame rate
- Settings: wire existing `previewTransport`, `previewWebSocketPort`, `previewLiveFrameRate` settings

---

## Phase 5: GLSL Legacy-Alias Diagnostics — Priority: Blocked

Awaiting upstream confirmation of removed/legacy alias list before implementation.

---

## Implementation Order

```
Phase 1.1  ──  Group("Name") { ... }     ← Biggest missing feature, touches many subsystems
Phase 1.2  ──  Return-value functions      ← Unblocks Phase 2.2 SignatureHelp
Phase 2.1  ──  Graph control-flow          ← Unblocks Phase 2.2 highlighting
Phase 2.2  ──  Syntax highlighting fixes   ← Depends on 1.1 + 2.1
Phase 3.1  ──  Allman-style highlighting   ← Independent, small scope
Phase 4    ──  WebSocket preview           ← Independent, reserved for future
```
