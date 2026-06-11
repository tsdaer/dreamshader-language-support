# dreamshader-language-support (Rider)

[![JetBrains Plugin Version](https://img.shields.io/jetbrains/plugin/v/31926.svg?label=JetBrains%20Marketplace)](https://plugins.jetbrains.com/plugin/31926)
[![JetBrains Plugin Downloads](https://img.shields.io/jetbrains/plugin/d/31926.svg)](https://plugins.jetbrains.com/plugin/31926)
[![JetBrains Plugin Rating](https://img.shields.io/jetbrains/plugin/r/rating/31926.svg)](https://plugins.jetbrains.com/plugin/31926)
[![JetBrains Plugin Stars](https://img.shields.io/jetbrains/plugin/r/stars/31926.svg)](https://plugins.jetbrains.com/plugin/31926)

[View on JetBrains Marketplace](https://plugins.jetbrains.com/plugin/31926)

<!-- plugin-metadata:start -->
name: Dreamshader Language Extension
description: Comprehensive DreamShaderLang support for JetBrains Rider with `.dsm`/`.dsf`/`.dsh` file types, lexer/parser/PSI and symbol model foundations, syntax highlighting plus semantic classification and color settings, formatter/commenter/brace matching/folding/structure view, context-aware completion (sections/types/settings values/UE.*/HLSL/imports), goto declaration/find usages/references/hover docs/signature help, semantic diagnostics (syntax/section-shape/semantic + Bridge diagnostics mapping), inlay parameter hints and Code Vision hints, configurable DreamShader project settings (project root/manifest path/status bar/code lens/out placeholder suffix/import extension strategy/hover doc overrides/Bridge commands), Bridge tool window and workflow actions (refresh diagnostics/recompile current or all/clean generated shaders/open Bridge paths), package tooling (package store dialog with search/filter/details/install-state markers/background install-update-remove/add-remove index sources/GitHub install/update/remove/open packages folder/package-aware import resolution), template tools (material/function/header and package scaffold), and unified DreamShader Hub entry for settings, diagnostics, package, and template workflows.
<!-- plugin-metadata:end -->

JetBrains Rider plugin for DreamShaderLang (`.dsm`, `.dsf`, `.dsh`).
Built on IntelliJ Platform SDK with platform module dependency (`com.intellij.modules.platform`).

Target reference (VS Code extension):
https://github.com/TypeDreamMoon/dreamshader-language-support

## Documentation

Long-form project knowledge now lives under [`docs/`](docs/README.md):

- [`docs/architecture.md`](docs/architecture.md) - runtime layers, data flow, and extension guidelines.
- [`docs/code-map.md`](docs/code-map.md) - categorized source and test file links.
- [`docs/project-structure.md`](docs/project-structure.md) - source/test layout rules.
- [`docs/language-baseline.md`](docs/language-baseline.md) - DreamShaderLang syntax baseline and coverage mapping.
- [`docs/package-baseline.md`](docs/package-baseline.md) - package-system baseline and Rider parity notes.
- [`docs/roadmap.md`](docs/roadmap.md) - goals, current progress, detailed TODOs, audit matrices, and changelog policy.
- [`docs/development.md`](docs/development.md) - local build/test/run, signing/publishing, Rider actions, settings, and troubleshooting.
- [`docs/0.0.4-development-plan.md`](docs/0.0.4-development-plan.md) - scanned/catalog-based `UE.*` built-in completion plan.

## Current Goal

Build a Rider plugin with feature parity to the VS Code DreamShaderLang extension, in phases:

- language core first
- editing and navigation second
- diagnostics and tool integration third
- package tooling and UX polish last

For detailed milestone status and TODOs, see [`docs/roadmap.md`](docs/roadmap.md).

## Quick Start

Requirements:

- JDK 17+ (recommended 21)
- Gradle user home pinned to `J:/Gradle` for local terminal runs

Build:

```powershell
$env:GRADLE_USER_HOME="J:/Gradle"; .\gradlew.bat build --no-configuration-cache
```

Test:

```powershell
$env:GRADLE_USER_HOME="J:/Gradle"; .\gradlew.bat test --no-configuration-cache
```

Run plugin in sandbox:

```powershell
$env:GRADLE_USER_HOME="J:/Gradle"; .\gradlew.bat runIde --no-configuration-cache
```

More workflow details are in [`docs/development.md`](docs/development.md).

## Rider Entry Points

- `Tools` -> `DreamShader` -> `Open DreamShader Hub`
- `Tools` -> `DreamShader` -> `DreamShader Packages`
- `Tools` -> `DreamShader` -> `DreamShader Templates`
- `Settings` -> `Tools` -> `DreamShader`

See [`docs/development.md`](docs/development.md) for the full action and setting reference.
