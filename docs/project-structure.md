# Project Structure

This repository now uses a role-oriented file layout under `src/main/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language`.

## Main Source Layout

- `core/`: language identity and base PSI types
  - `DreamShaderLanguage`, `DreamShaderFileType`, `DreamShaderIcons`, `DreamShaderBundle`, `DreamShaderElementType`, `DreamShaderPsiElement`, `DreamShaderPsiFile`
- `lexer/`: lexical tokens and keyword catalogs
- `parser/`: parser definition and PSI parser implementation
- `highlighting/`: syntax highlighting, semantic token classification, color settings
- `editor/`: completion, formatter, folding, commenter, brace matcher, parameter info, inlay hints, manifest-aware editor helpers
- `navigation/`: goto declaration, references, find usages, docs, structure view
- `diagnostics/`: semantic annotator and diagnostics pipeline/passes/models
- `refactoring/`: DreamShader names validator, rename input validation, and Safe Delete support
- `settings/`: project settings, settings configurable, hub entry action
- `bridge/`: Bridge integration and diagnostics mapping
- `packages/`: package store/index/install/import tooling
- `preview/`: Material Preview tool window, file-bridge request/result handling, and preview refresh events
- `ui/`: shared Swing UI helpers for DreamShader cards, sections, pills, rounded borders, and workflow dialogs
- `psi/`: typed PSI nodes, PSI factories, and shared PSI traversal/documentation utilities
- `symbols/`: symbol model and builder
- `templates/`: file/package template actions and services
- `welcome/`: localized What's New/welcome editor shown on first install, update, or manual Hub action

## Test Source Layout

Tests under `src/test/kotlin/com/github/tsdaer/dreamshaderlanguagesupport/language` are grouped by responsibility:

- `highlighting/`
- `editor/`
- `navigation/`
- `diagnostics/`
- `parser/`
- `preview/`
- `integration/`
- `settings/`
- feature-specific existing groups remain (`bridge/`, `packages/`, `symbols/`, `templates/`)

## Rules

- Keep Kotlin package declarations aligned with the physical directory (for example `language/editor` -> `...language.editor`).
- Prefer placing new files by runtime responsibility first, not by milestone/history.
- When adding a new feature area, create a dedicated directory instead of expanding the top-level `language` directory.
