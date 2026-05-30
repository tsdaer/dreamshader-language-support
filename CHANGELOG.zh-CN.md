<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# dreamshader-language-support 更新日志

## [未发布]

## [0.0.3] - 2026-05-30

### 新增

- 新增命名空间声明的导航能力，覆盖跳转声明、查找用法与结构/符号视图关联。
- Package Store 中 GitHub 包搜索在请求进行时会禁用进行中的操作按钮，避免重复触发。
- 新增同一作用域下的重复声明名语义诊断（覆盖顶层作用域与命名空间局部作用域），并允许不同命名空间作用域中的同名声明共存。
- 新增重复声明名快速修复：`将声明重命名为 '<NameN>'`，在当前作用域内建议唯一数字后缀名称。
- 新增 `Name="..."` 路径形式声明的重名覆盖：按路径叶子名参与重复检测，快速修复仅替换叶子名并保留路径前缀。

### 变更

- 重构 Code Vision 点击处理流程，提升交互稳定性并与包搜索状态切换保持一致。
- 增强 Hover 覆盖设置与欢迎页引导：内置条目支持编辑/重置，缺失项目根目录时的提示更清晰。
- 更新变更日志本地化流程：拆分语言文件（`CHANGELOG.md` / `CHANGELOG.zh-CN.md`）并同步 Release CI 的补丁策略。
- 更新 README 发布展示方式，加入 Marketplace 插件卡片与静态徽章。

## [0.0.2] - 2026-05-29

### 新增

- `const` 纹理语义诊断现在要求显式默认资源（例如 `Path(...)`），并提供更精准的错误定位。
- Hover 文档覆盖项新增可视化表格编辑器（`Path` + `Content`），支持校验摘要与示例行插入。
- 不受支持的 import 扩展名快速修复在 UI 中新增“将更新默认偏好”的明确提示。
- 欢迎页变更说明支持本地化分文件策略：英文使用 `CHANGELOG.md`，中文使用 `CHANGELOG.zh-CN.md`。

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

[未发布]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.3...HEAD
[0.0.3]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.2...0.0.3
[0.0.2]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.1...0.0.2
[0.0.1]: https://github.com/tsdaer/dreamshader-language-support/commits/0.0.1
