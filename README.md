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
  - Syntax highlighting with semantic classification, color settings, and inline color previews for vector constructors, color aliases, hex colors, and conservative 0-255 channel values
  - Formatter with DreamShader code style settings, section assignment alignment, commenter, quote handling, brace matching, folding, and structure view

  **Editing and navigation**
  - Context-aware completion for sections, types, settings values, catalog-driven `UE.*` / `Substrate.*`, HLSL, and imports
  - Goto declaration, find usages, references, Rename, Safe Delete, Copy/Paste Reference, breadcrumbs, Context Info, hover docs, signature help, plain-text symbol completion, and rename suggestions, with `UE.*` / `Substrate.*` served from the shared material-expression catalog
  - Inlay parameter hints, Code Vision hints, declaration/import gutter markers, and Bridge diagnostic line markers with source-location navigation
  - IDE include/dependency integration for DreamShader `import "..."` statements

  **Diagnostics and project integration**
  - Cached semantic diagnostics (syntax, section-shape, semantic) plus Bridge diagnostics mapping, with parser recovery and targeted Bridge-failure-prevention checks
  - Configurable project settings: project root, manifest path, Unreal Engine source root with auto-detect, material-expression scan toggle and cache path, status bar, code lens, inlay parameter hints, out placeholder suffix, import extension strategy, hover doc overrides, and Bridge commands
  - Bridge tool window and workflow actions (refresh diagnostics, recompile current or all, clean generated shaders, open Bridge paths)

  **Packages, templates, and hub**
  - Package tooling: package store dialog with search/filter/details/install-state markers, background install-update-remove, add-remove index sources, GitHub install/update/remove, open packages folder, and package-aware import resolution
  - Native File and Code Templates plus template tools for material/function/header, texture sample, noise material, package scaffold, and package wizard workflows
  - Material preview tool window backed by the portable DreamShader Bridge file transport
  - Unified DreamShader Hub entry and shared card-based UI surface for settings, diagnostics, preview, package, and template workflows
description_zh: |
  面向 JetBrains Rider 的完整 DreamShaderLang 支持，覆盖 `.dsm`、`.dsf`、`.dsh` 文件，从语言核心到工具集成。

  **语言核心**
  - 词法、语法、PSI 与符号模型基础
  - 语法高亮，含语义分类、颜色设置，以及向量构造、颜色别名、十六进制颜色和保守 0-255 通道值的内联预览
  - 格式化、DreamShader 代码样式设置、section 赋值对齐、注释、引号处理、括号匹配、折叠与结构视图

  **编辑与导航**
  - 上下文感知补全：sections、类型、设置值、catalog 驱动的 `UE.*` / `Substrate.*`、HLSL 与 imports
  - 跳转声明、查找用法、引用、重命名、安全删除、Copy/Paste Reference、面包屑、Context Info、悬浮文档、签名帮助、纯文本符号补全与重命名建议，`UE.*` / `Substrate.*` 由共享的 material-expression catalog 提供
  - 内联参数提示、Code Vision 提示、声明/import gutter 标记，以及可跳转源码位置的 Bridge 诊断行标记
  - 将 DreamShader `import "..."` 接入 IDE include/dependency 机制

  **诊断与项目集成**
  - 带缓存的语义诊断（语法、section-shape、语义）以及 Bridge 诊断映射，包含 parser 恢复与针对 Bridge 失败的预防性检查
  - 可配置项目设置：项目根、manifest 路径、可自动检测的 Unreal Engine 源码根、material-expression 扫描开关与缓存路径、状态栏、code lens、内联参数提示、out 占位后缀、import 扩展名策略、悬浮文档覆盖与 Bridge 命令
  - Bridge 工具窗口与工作流操作（刷新诊断、重编译当前或全部、清理生成的 shader、打开 Bridge 路径）

  **包、模板与 Hub**
  - 包工具：带搜索/筛选/详情/安装状态标记的 package store 对话框、后台安装-更新-移除、增删索引源、GitHub 安装/更新/移除、打开包目录，以及包感知的 import 解析
  - 原生 File and Code Templates，以及材质/函数/头文件、贴图采样、噪声材质、包脚手架与包向导的模板工具
  - 基于 DreamShader Bridge 文件传输的材质预览工具窗口
  - 统一的 DreamShader Hub 入口与卡片化共享 UI 表面，整合设置、诊断、预览、包与模板工作流
<!-- plugin-metadata:end -->

JetBrains Rider plugin for DreamShaderLang (`.dsm`, `.dsf`, `.dsh`).
Built on IntelliJ Platform SDK with platform module dependency (`com.intellij.modules.platform`).

## Documentation

Long-form project knowledge now lives under [`docs/`](docs/README.md):

- [`docs/architecture.md`](docs/architecture.md) - runtime layers, data flow, and extension guidelines.
- [`docs/code-map.md`](docs/code-map.md) - categorized source and test file links.
- [`docs/project-structure.md`](docs/project-structure.md) - source/test layout rules.
- [`docs/language-baseline.md`](docs/language-baseline.md) - DreamShaderLang syntax baseline and coverage mapping.
- [`docs/package-baseline.md`](docs/package-baseline.md) - package-system baseline and Rider parity notes.
- [`docs/roadmap.md`](docs/roadmap.md) - goals, current progress, detailed TODOs, audit matrices, and changelog policy.
- [`docs/development.md`](docs/development.md) - local build/test/run, signing/publishing, Rider actions, settings, and troubleshooting.

## Current Goal

Full-featured DreamShaderLang support for JetBrains Rider:

- language core with lexer, parser, PSI, and symbol model
- rich editing and navigation (completion, refactoring, code insight)
- diagnostics and Bridge tool integration
- package management and template scaffolding
- material preview and UX polish

## Quick Start

Requirements:

- JDK 17+ (recommended 21)

Build:

```powershell
.\gradlew.bat build --no-configuration-cache
```

Test:

```powershell
.\gradlew.bat test --no-configuration-cache
```

Gradle test execution now runs on JUnit Platform with JUnit Jupiter for non-platform tests. The remaining `junit:junit` dependency is kept only for current IntelliJ Platform test-framework compatibility.

Run plugin in sandbox:

```powershell
.\gradlew.bat runIde --no-configuration-cache
```

More workflow details are in [`docs/development.md`](docs/development.md).

## Build and Release Conventions

- `gradle.properties` is the source of truth for the plugin version. `build.gradle.kts` reads `version` from that file and uses it for plugin metadata, resource expansion, and release packaging.
- The plugin Marketplace description is generated from the `<!-- plugin-metadata:start -->` / `<!-- plugin-metadata:end -->` block in this README. Keep `name`, `description`, and `description_zh` in that block valid when updating release-facing copy.
- `CHANGELOG.md` is the English changelog consumed by `build.gradle.kts` for plugin change notes. `CHANGELOG.zh-CN.md` is used by the localized welcome page and by the GitHub Build workflow when composing bilingual draft release notes.
- Keep the current `gradle.properties` version section (`## [x.y.z] - YYYY-MM-DD`) as the first changelog section in both files. Until the next version is created, add all development log entries under that current version section instead of using an `Unreleased` section.
- The GitHub Build workflow creates draft release notes from the current version section, with a fallback to the first versioned changelog section. When bumping the version, add the new section above the previous one and update the compare links at the bottom.
- Gradle test discovery uses JUnit Platform without Vintage. Prefer JUnit Jupiter APIs for non-platform tests; `junit:junit` remains only as an IntelliJ Platform test compatibility dependency.

## Rider Entry Points

- `Tools` -> `DreamShader` -> `Open DreamShader Hub`
- `Tools` -> `DreamShader` -> `Show Material Preview`
- `Tools` -> `DreamShader` -> `DreamShader Packages`
- `Tools` -> `DreamShader` -> `DreamShader Templates`
- `New` -> `DreamShader Material` / `DreamShader Function` / `DreamShader Header`
- `Settings` -> `Tools` -> `DreamShader`
- `Settings` -> `Editor` -> `Code Style` -> `DreamShader`
- `Settings` -> `Editor` -> `File and Code Templates` -> `DreamShader`

See [`docs/development.md`](docs/development.md) for the full action and setting reference.
