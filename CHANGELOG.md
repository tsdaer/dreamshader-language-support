<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# dreamshader-language-support Changelog

## [Unreleased]

## [0.0.2] - 2026-05-29

### Added

- `const` texture diagnostics now require explicit default assets (for example `Path(...)`) and report targeted semantic errors.
- Hover docs overrides now have a visual table editor (`Path` + `Content`) with validation summary and sample row insertion.
- Unsupported import extension quick-fix now surfaces clear “will update preferred default” behavior hints in UI.
- Added localized changelog strategy for the welcome page, with English default and a Chinese mirror section split by `### 中文`.

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

### 中文

## [未发布]

## [0.0.2] - 2026-05-29

### 新增

- `const` 纹理语义诊断现在要求显式默认资源（例如 `Path(...)`），并提供更精准的错误定位。
- Hover 文档覆盖项新增可视化表格编辑器（`Path` + `Content`），支持校验摘要与示例行插入。
- 不受支持的 import 扩展名快速修复在 UI 中新增“将更新默认偏好”的明确提示。
- 欢迎页变更说明支持本地化分段策略：默认显示英文，并通过 `### 中文` 提供中文镜像分段。

### 变更

- 不受支持 import 扩展名的快速修复排序更智能：优先可解析候选、突出首选默认项，并明确自动更新行为。
- 编辑辅助能力增强：`.dsm` import 补全更丰富，Hover 文档覆盖面更广，用户声明函数已接入签名提示/参数内联提示。
- Package Store 对话框状态管理进一步收敛，并补充 UI 回归测试覆盖（`DPKG-UI-001` 到 `DPKG-UI-006`）。

### 移除

- 移除了 Material Expression Manifest 解析中的内置库兜底，以避免陈旧隐式数据源。

## [0.0.1] - 2026-05-29

### 新增

- 语言核心：`.dsm` / `.dsf` / `.dsh` 文件类型、lexer/parser/PSI 基础设施、语法高亮、格式化、注释器、括号匹配、代码折叠与结构视图。
- 智能编辑：上下文感知补全（sections/types/settings values/`UE.*`/HLSL/imports）、跳转声明、引用/查找用法、Hover 文档与签名提示。
- 语义诊断流水线：语法 + section-shape + semantic 检查、快速修复、声明约束、Graph 控制流限制与 import 解析诊断。
- Bridge 集成：诊断仓库加载与映射、诊断工具窗口、刷新/打开/定位操作、命令式重编译/清理操作、状态栏组件。
- 项目设置：项目根目录、manifest 路径、code-lens 开关、import 扩展名偏好与自动更新、Bridge 命令、Hover 文档覆盖。
- 包工具链：Package Store 对话框、多源索引加载、安装/更新/移除流程、GitHub 包工作流、索引源管理与包感知 import 解析。
- 模板与工作流：材质/函数/头文件模板、包脚手架生成，以及统一入口 DreamShader Hub。
- 国际化：基于 `DreamShaderBundle.properties` 与 `DreamShaderBundle_zh_CN.properties` 的 UI/消息本地化。
- 质量与发布就绪：完善的单元/集成/UI 测试覆盖、大文件性能冒烟测试、Marketplace 签名发布流程、Release CI 的 changelog 自动补丁。

[Unreleased]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.2...HEAD
[0.0.2]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.1...0.0.2
[0.0.1]: https://github.com/tsdaer/dreamshader-language-support/commits/0.0.1
