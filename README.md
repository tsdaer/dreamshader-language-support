# dreamshader-language-support (Rider)

[![JetBrains Plugin Version](https://img.shields.io/jetbrains/plugin/v/31926.svg?label=JetBrains%20Marketplace)](https://plugins.jetbrains.com/plugin/31926)
[![JetBrains Plugin Downloads](https://img.shields.io/jetbrains/plugin/d/31926.svg)](https://plugins.jetbrains.com/plugin/31926)
[![JetBrains Plugin Rating](https://img.shields.io/jetbrains/plugin/r/rating/31926.svg)](https://plugins.jetbrains.com/plugin/31926)
[![JetBrains Plugin Stars](https://img.shields.io/jetbrains/plugin/r/stars/31926.svg)](https://plugins.jetbrains.com/plugin/31926)

[View on JetBrains Marketplace](https://plugins.jetbrains.com/plugin/31926)

<!-- plugin-metadata:start -->
name: Dreamshader Language Extension
description: |
  Comprehensive DreamShaderLang support for JetBrains Rider, covering `.dsm`, `.dsf`, and `.dsh` files from language core to tooling, plus the DreamShader 2.0 dialect with editor-bridge artifacts, DSH-coded diagnostics, and the `dsc` toolchain.

  **Language core**
  - DreamShader 2.0 dialect support: `.dss` compilation units and `.dsi` material instances with dedicated icons, dialect-aware parsing and highlighting, `#pragma` / `uniform` / `export` declarations, and verbatim `/// @custom` bodies; mixed-dialect `.dsh` headers supported
  - Lexer, parser, PSI, and symbol model foundations
  - Syntax highlighting with semantic classification, color settings, and inline color previews for vector constructors, color aliases, hex colors, and conservative 0-255 channel values
  - Formatter with DreamShader code style settings, section assignment alignment, commenter, quote handling, brace matching, folding, and structure view

  **Editing and navigation**
  - Context-aware completion for sections, types, settings values, catalog-driven `UE.*` / `Substrate.*`, HLSL, and imports
  - 2.0 completion intelligence: receiver-typed members after `.` (vector swizzles, matrix cells, struct fields, texture `sample`, material attributes, `Substrate` builder pins, catalog namespaces), named arguments with documented catalog enum values, `#include` path candidates, `.dsi` override values typed by the declared spelling, and bare-identifier completion over the 2.0 symbol model - dialect-dispatched contributors on one shared priority scale, with the 1.x hot path serving context from the committed PSI and cached candidate sets
  - Goto declaration, find usages, references, Rename, Safe Delete, Copy/Paste Reference, breadcrumbs, Context Info, hover docs, named-argument-aware signature help (highlights the parameter matching the argument name, catalog and built-in signatures side by side as overloads), plain-text symbol completion, and rename suggestions, with `UE.*` / `Substrate.*` served from the shared material-expression catalog
  - Inlay parameter hints, Code Vision hints (usage counts above 2.0 export functions and a clickable Parent hint on `.dsi` instance pragmas), declaration/import gutter markers, and Bridge diagnostic line markers with source-location navigation
  - 2.0 editor experience: in-file usage highlighting, Surround With (`{ }` / `if` / `for`; named sections for 1.x), statement up/down and list element moving, comment-line joining, split/join of argument lists, code block navigation, in-file Copy Path (returns the `/Game/...` asset path inside the source root), conditional HLSL injection for the bare-HLSL bodies (silent when no HLSL language is installed), Extract Variable in 2.0 function bodies (inferred type spelling, linked in-place rename, conservative refusal where hoisting would change evaluation order), and postfix templates (`if` / `ifel` / `lerp` / `saturate` / `sample` / `swizzle`) that now serve the 2.0 dialect
  - IDE include/dependency integration for DreamShader `import "..."` statements

  **Diagnostics and project integration**
  - Cached semantic diagnostics (syntax, section-shape, semantic) plus Bridge diagnostics mapping, with parser recovery and targeted Bridge-failure-prevention checks
  - DSH diagnostic-code catalog (all 639 upstream codes): Bridge diagnostics show the code meaning in the tooltip, offer an Open DSH documentation intention, and carry preventive checks keyed to upstream codes - `DSH2248` (2.0 syntax in a 1.x file), `DSH3203` (angle-bracket `#include`, with a quick fix), `DSH2249` (asset block in a `.dsh` header) - plus legacy GLSL alias hints with one-click HLSL rewrites (`DSH5277`)
  - DreamShader 2.0 semantic intelligence: a cross-file symbol model (stub-indexed `uniform` / exported functions / `struct` / include edges, merged with the Bridge symbol index - Bridge authoritative, stub fallback), conservative type inference over the 2.0 PSI (ternary branches, compound assignments keeping the left-hand shape, include-closure helper returns), and upstream-keyed checks - `DSH2158` (positional after named argument), `DSH5293`/`DSH5295`/`DSH5296`/`DSH5297`/`DSH5298` (Substrate sugar operators, Slab parameter families, builder values), and `.dsi` overrides against the parent parameter schema (`DSH7258`/`DSH7264`/`DSH7259`/`DSH7268`, silent without Bridge data), plus weak-warning assignment type mismatches covering compound assignments
  - Inspections under Settings → Editor → Inspections → DreamShader (unknown type in Inputs/Outputs/Properties, included in Inspect Code batch scans)
  - `dsc` toolchain actions (Check / Format / Migrate with a `-Check` preview / Refresh Symbol Index) behind a configurable executable path
  - Decompiled-output support: `Decompiled/` and `Intermediate/DreamShader/` sources skip platform error highlighting and open in reading mode by default
  - Configurable project settings: project root, manifest path, Unreal Engine source root with auto-detect, material-expression scan toggle and cache path, status bar, code lens, inlay parameter hints, out placeholder suffix, import extension strategy, hover doc overrides, Bridge commands, and the `dsc` executable path
  - DreamShader 2.0 editor-bridge artifacts: `dreamshader-builtin-catalog.json` drives `UE.*` / `Substrate.*` completion and signature help (with a bundled offline fallback covering all 27 registered builtins), `dreamshader-symbol-index` v1 is consumed read-only, `bridge.db` meta is exposed, the status bar shows editor online/offline from the `status.json` heartbeat, and a **Reveal Node in Unreal Material Editor** action maps a `.dss`/`.dsi` line back to its material node
  - Bridge tool window and workflow actions (refresh diagnostics, recompile current or all, clean generated shaders, open Bridge paths)

  **Packages, templates, and hub**
  - Package tooling: package store dialog with search/filter/details/install-state markers, background install-update-remove, add-remove index sources, GitHub install/update/remove, open packages folder, and package-aware import resolution
  - Native File and Code Templates plus template tools for 2.0 material/function (`.dss`) and instance (`.dsi`), 1.x material/function/header, texture sample, noise material, package scaffold, and package wizard workflows
  - Material preview tool window backed by the portable DreamShader Bridge file transport, with an opt-in WebSocket transport (`ws://127.0.0.1:17864`) streaming live RGBA8 frames plus Graph probes attached at the editor's caret line
  - Unified DreamShader Hub entry and shared card-based UI surface for settings, diagnostics, preview, package, and template workflows
description_zh: |
  面向 JetBrains Rider 的完整 DreamShaderLang 支持，覆盖 `.dsm`、`.dsf`、`.dsh` 文件，从语言核心到工具集成，并支持 DreamShader 2.0 方言的编辑器桥接工件、DSH 编码诊断与 `dsc` 工具链。

  **语言核心**
  - DreamShader 2.0 方言支持：`.dss` 编译单元与 `.dsi` 材质实例（独立图标）、按方言分流的解析与高亮、`#pragma` / `uniform` / `export` 声明、`/// @custom` 原文函数体，混合方言 `.dsh` 头文件
  - 词法、语法、PSI 与符号模型基础
  - 语法高亮，含语义分类、颜色设置，以及向量构造、颜色别名、十六进制颜色和保守 0-255 通道值的内联预览
  - 格式化、DreamShader 代码样式设置、section 赋值对齐、注释、引号处理、括号匹配、折叠与结构视图

  **编辑与导航**
  - 上下文感知补全：sections、类型、设置值、catalog 驱动的 `UE.*` / `Substrate.*`、HLSL 与 imports
  - 2.0 补全智能化：按 `.` 后接收者类型的成员补全（向量 swizzle、矩阵单元、struct 字段、贴图 `sample`、material 属性、`Substrate` builder pin、catalog 命名空间）、命名实参与文档化的 catalog 枚举值、`#include` 路径候选、按声明类型给值的 `.dsi` 覆盖值，以及基于 2.0 符号模型的裸标识符补全——按方言分流的 Contributor 与统一优先级体系，1.x 热路径直接读已提交 PSI 与缓存候选集
  - 跳转声明、查找用法、引用、重命名、安全删除、Copy/Paste Reference、面包屑、Context Info、悬浮文档、命名实参感知的签名帮助（按实参名高亮对应参数，catalog 与内建签名按重载并列）、纯文本符号补全与重命名建议，`UE.*` / `Substrate.*` 由共享的 material-expression catalog 提供
  - 内联参数提示、Code Vision 提示（2.0 export 函数上方的引用计数与 `.dsi` instance pragma 上方可点击的 Parent 提示）、声明/import gutter 标记，以及可跳转源码位置的 Bridge 诊断行标记
  - 2.0 编辑体验：文件内用法高亮、Surround With（`{ }` / `if` / `for`；1.x 为命名 section）、语句上下移动与列表元素左右移动、注释行合并、实参列表拆行合并、结构块导航、文件内 Copy Path（源码根内返回 `/Game/...` 资产路径）、裸 HLSL 函数体的条件式 HLSL 注入（无 HLSL 语言时静默）、2.0 函数体内的提取变量（推断类型拼写、联动就地重命名，会改变求值时机处保守拒绝），以及开始服务 2.0 方言的后缀模板（`if` / `ifel` / `lerp` / `saturate` / `sample` / `swizzle`）
  - 将 DreamShader `import "..."` 接入 IDE include/dependency 机制

  **诊断与项目集成**
  - 带缓存的语义诊断（语法、section-shape、语义）以及 Bridge 诊断映射，包含 parser 恢复与针对 Bridge 失败的预防性检查
  - DSH 诊断码目录（覆盖上游全部 639 个码）：Bridge 诊断 tooltip 显示码义并提供「打开 DSH 文档」意图；按上游编号的预防性检查——`DSH2248`（1.x 文件中的 2.0 语法）、`DSH3203`（尖括号 `#include`，附快速修复）、`DSH2249`（`.dsh` 头文件中的资产块）；以及 GLSL 遗留别名提示与一键 HLSL 改写（`DSH5277`）
  - DreamShader 2.0 语义智能：跨文件符号模型（stub 索引化的 `uniform` / 导出函数 / `struct` / include 边，与 Bridge 符号索引按名合并——Bridge 权威、stub 兜底）、覆盖 2.0 PSI 的保守类型推断（三元取分支类型、复合赋值保持左侧形状、include 闭包 helper 返回类型），以及按上游编号的检查——`DSH2158`（位置实参跟在命名实参后）、`DSH5293`/`DSH5295`/`DSH5296`/`DSH5297`/`DSH5298`（Substrate 糖操作符、Slab 参数族、builder 值），`.dsi` 覆盖对照父参数 schema（`DSH7258`/`DSH7264`/`DSH7259`/`DSH7268`，无 Bridge 数据时静默），外加覆盖复合赋值的弱警告级赋值类型不匹配提示
  - Inspections 分组（Settings → Editor → Inspections → DreamShader：Inputs/Outputs/Properties 未知类型，纳入 Inspect Code 批量扫描）
  - `dsc` 工具链动作（检查 / 格式化 / 迁移（先 `-Check` 预演）/ 刷新符号索引），由可配置的 dsc 可执行路径启用
  - 反编译产物支持：`Decompiled/` 与 `Intermediate/DreamShader/` 源码跳过平台错误高亮，默认进入阅读模式
  - 可配置项目设置：项目根、manifest 路径、可自动检测的 Unreal Engine 源码根、material-expression 扫描开关与缓存路径、状态栏、code lens、内联参数提示、out 占位后缀、import 扩展名策略、悬浮文档覆盖、Bridge 命令与 `dsc` 可执行路径
  - DreamShader 2.0 编辑器桥接工件：`dreamshader-builtin-catalog.json` 驱动 `UE.*` / `Substrate.*` 补全与签名帮助（内置覆盖全部 27 个注册调用的离线兜底目录），只读消费 `dreamshader-symbol-index` v1，暴露 `bridge.db` meta 表，状态栏按 `status.json` 心跳显示编辑器在线/离线，并提供把 `.dss`/`.dsi` 源行映射回材质节点的「在虚幻材质编辑器中显示节点」动作
  - Bridge 工具窗口与工作流操作（刷新诊断、重编译当前或全部、清理生成的 shader、打开 Bridge 路径）

  **包、模板与 Hub**
  - 包工具：带搜索/筛选/详情/安装状态标记的 package store 对话框、后台安装-更新-移除、增删索引源、GitHub 安装/更新/移除、打开包目录，以及包感知的 import 解析
  - 原生 File and Code Templates，以及 2.0 材质/函数（`.dss`）与实例（`.dsi`）、1.x 材质/函数/头文件、贴图采样、噪声材质、包脚手架与包向导的模板工具
  - 基于 DreamShader Bridge 文件传输的材质预览工具窗口，并支持可选的 WebSocket 传输（`ws://127.0.0.1:17864`）流式获取 RGBA8 实时帧，附上与编辑器光标行联动的 Graph 探针
  - 统一的 DreamShader Hub 入口与卡片化共享 UI 表面，整合设置、诊断、预览、包与模板工作流
<!-- plugin-metadata:end -->

JetBrains Rider plugin for DreamShaderLang (`.dsm`, `.dsf`, `.dsh`, plus the DreamShader 2.0 dialect: `.dss`, `.dsi`).
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
