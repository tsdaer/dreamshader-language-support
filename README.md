# dreamshader-language-support (Rider)

[![JetBrains Plugin Version](https://img.shields.io/jetbrains/plugin/v/31926.svg?label=JetBrains%20Marketplace)](https://plugins.jetbrains.com/plugin/31926)
[![JetBrains Plugin Downloads](https://img.shields.io/jetbrains/plugin/d/31926.svg)](https://plugins.jetbrains.com/plugin/31926)
[![JetBrains Plugin Rating](https://img.shields.io/jetbrains/plugin/r/rating/31926.svg)](https://plugins.jetbrains.com/plugin/31926)
[![JetBrains Plugin Stars](https://img.shields.io/jetbrains/plugin/r/stars/31926.svg)](https://plugins.jetbrains.com/plugin/31926)

[View on JetBrains Marketplace](https://plugins.jetbrains.com/plugin/31926)

<!-- plugin-metadata:start -->
name: Dreamshader Language Extension
description: |
  Comprehensive DreamShaderLang support for JetBrains Rider, covering `.dsm`, `.dsf`, and `.dsh` files from language core to tooling.

  **Language core**
  - Lexer, parser, PSI, and symbol model foundations
  - Syntax highlighting with semantic classification and color settings
  - Formatter, commenter, brace matching, folding, and structure view

  **Editing and navigation**
  - Context-aware completion for sections, types, settings values, catalog-driven `UE.*` / `Substrate.*`, HLSL, and imports
  - Goto declaration, find usages, references, hover docs, and signature help, with `UE.*` / `Substrate.*` served from the shared material-expression catalog
  - Inlay parameter hints and Code Vision hints

  **Diagnostics and project integration**
  - Semantic diagnostics (syntax, section-shape, semantic) plus Bridge diagnostics mapping
  - Configurable project settings: project root, manifest path, Unreal Engine source root with auto-detect, material-expression scan toggle and cache path, status bar, code lens, out placeholder suffix, import extension strategy, hover doc overrides, and Bridge commands
  - Bridge tool window and workflow actions (refresh diagnostics, recompile current or all, clean generated shaders, open Bridge paths)

  **Packages, templates, and hub**
  - Package tooling: package store dialog with search/filter/details/install-state markers, background install-update-remove, add-remove index sources, GitHub install/update/remove, open packages folder, and package-aware import resolution
  - Template tools for material/function/header and package scaffolds
  - Unified DreamShader Hub entry for settings, diagnostics, package, and template workflows
description_zh: |
  面向 JetBrains Rider 的完整 DreamShaderLang 支持，覆盖 `.dsm`、`.dsf`、`.dsh` 文件，从语言核心到工具集成。

  **语言核心**
  - 词法、语法、PSI 与符号模型基础
  - 语法高亮，含语义分类与颜色设置
  - 格式化、注释、括号匹配、折叠与结构视图

  **编辑与导航**
  - 上下文感知补全：sections、类型、设置值、catalog 驱动的 `UE.*` / `Substrate.*`、HLSL 与 imports
  - 跳转声明、查找用法、引用、悬浮文档与签名帮助，`UE.*` / `Substrate.*` 由共享的 material-expression catalog 提供
  - 内联参数提示与 Code Vision 提示

  **诊断与项目集成**
  - 语义诊断（语法、section-shape、语义）以及 Bridge 诊断映射
  - 可配置项目设置：项目根、manifest 路径、可自动检测的 Unreal Engine 源码根、material-expression 扫描开关与缓存路径、状态栏、code lens、out 占位后缀、import 扩展名策略、悬浮文档覆盖与 Bridge 命令
  - Bridge 工具窗口与工作流操作（刷新诊断、重编译当前或全部、清理生成的 shader、打开 Bridge 路径）

  **包、模板与 Hub**
  - 包工具：带搜索/筛选/详情/安装状态标记的 package store 对话框、后台安装-更新-移除、增删索引源、GitHub 安装/更新/移除、打开包目录，以及包感知的 import 解析
  - 材质/函数/头文件与包脚手架的模板工具
  - 统一的 DreamShader Hub 入口，整合设置、诊断、包与模板工作流
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
- [`docs/plans/0.0.4-catalog-ue-builtins.md`](docs/plans/0.0.4-catalog-ue-builtins.md) - scanned/catalog-based `UE.*` built-in completion plan.

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
