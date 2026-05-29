<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# dreamshader-language-support Changelog

## [Unreleased]
### Added
- None yet.

## [0.0.2] - 2026-05-29
### Added
- `const` texture diagnostics now require explicit default assets (for example `Path(...)`) and report targeted semantic errors.
- Hover docs overrides now have a visual table editor (`Path` + `Content`) with validation summary and sample row insertion.
- Unsupported import extension quick-fix now surfaces clear “will update preferred default” behavior hints in UI.

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
#### 未发布
##### 新增
- 暂无。

#### [0.0.2] - 2026-05-29
##### 新增
- 新增 `const` 纹理默认资源显式声明诊断（例如要求 `Path(...)`），并提供针对性语义错误提示。
- 新增悬浮文档覆盖可视化表格编辑器（`Path` + `Content`），包含校验摘要与示例行插入。
- 新增导入扩展 quick-fix 的“将更新默认扩展”提示，让自动更新行为更可见。

##### 变更
- 导入扩展 quick-fix 排序逻辑增强：优先可解析候选、明确默认偏好，并清晰展示自动更新行为。
- 编辑器辅助能力增强：`.dsm` 导入补全更完整、悬浮文档覆盖面更广、用户声明函数已接入签名/参数内联提示。
- 包商店对话框状态流转更稳健，并补齐 `DPKG-UI-001` 到 `DPKG-UI-006` UI 回归测试覆盖。

##### 移除
- 移除材质表达式清单解析中的内置库 fallback，避免隐式陈旧数据源带来的行为偏差。

#### [0.0.1] - 2026-05-29
##### 新增
- 语言核心：`.dsm` / `.dsf` / `.dsh` 文件类型、lexer/parser/PSI 基础、语法高亮、formatter、commenter、括号匹配、折叠与结构视图。
- 智能编辑：上下文补全（sections/types/settings 值/`UE.*`/HLSL/imports）、跳转定义、引用/查找用法、悬浮文档与签名帮助。
- 语义诊断管线：语法 + section-shape + 语义检查、quick-fix、声明约束、Graph 控制流限制与导入解析诊断。
- Bridge 集成：诊断仓库加载/映射、诊断 Tool Window、刷新/打开/定位动作、命令式重编译/清理动作与状态栏组件。
- 项目设置：项目根目录、manifest 路径、code-lens 开关、导入扩展偏好/自动更新、Bridge 命令、悬浮覆盖设置。
- 包工具链：包商店对话框、多源索引加载、安装/更新/移除流程、GitHub 包工作流、索引源管理、包感知导入解析。
- 模板与工作流：材质/函数/头文件模板、包脚手架生成，以及 DreamShader Hub 一站式入口。
- 国际化：基于 bundle 的 UI/消息本地化，包含 `DreamShaderBundle.properties` 与 `DreamShaderBundle_zh_CN.properties`。
- 质量与发布能力：广泛的单测/集成/UI 测试覆盖、大文件性能冒烟测试、Marketplace 签名发布工作流与 release CI 的 changelog patch。
