<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# dreamshader-language-support 更新日志

## [0.0.7] - 2026-07-20

### 新增

- `Group("Name")` / `PropGroup("Name")` 属性分组作用域：支持独立的声明包装嵌套 sections，也支持 `Properties Group("Name") { ... }` section 头部修饰语法（上游主要形式）。包含补全片段、结构视图集成以及诊断中的 section 扁平化。
- 单返回值函数（`Function float X(...)`）：parser 接受 `Function`/`GraphFunction` 后的 `TYPE` 记号；`returnType()` PSI 访问器；签名帮助显示 `: <type>` 后缀；悬浮文档显示返回类型；`return` 语句在返回值函数体内有效。
- Bridge SQLite 数据库传输：从 `bridge.db`（上游 v1.5.0+）读取诊断，自动降级到 `diagnostics.json`。使用 `org.xerial:sqlite-jdbc` 通过 JDBC 短连接访问，避免文件锁定。
- Bridge 状态栏 `[DB]` 指示器：显示诊断来源为 SQLite 数据库还是 JSON 文件。
- 可配置的 `Source Directory` 设置（默认 `DShader`）：对应 Unreal 插件的 `SourceDirectory` 项目设置。未显式设置时自动检测常见模式（`DShader`、`Shaders`、`Source`）。导入解析、包管理和项目根检测现在均遵循此设置，不再硬编码 `DShader`。

### 变更

- 语言基线更新至上游 DreamShader v1.6.3 参考快照。
- Bridge UI 重新设计：工具窗口使用 IDE 风格 `JBTable`（22px 行高替代原来的 88px），含严重等级图标和多列布局（消息/文件/位置）。状态栏使用图标 + 紧凑的错误/警告计数，配合详细 tooltip。Hub 对话框使用紧凑垂直布局和 `TitledSeparator` 分区。
- `DreamShaderUi` 改写为 IDE 原生外观：卡片使用标准边框替代自定义圆角面板；分区使用 `TitledSeparator`；标签使用扁平不透明样式；所有自定义背景色别名至 IDE 主题色。
- Bridge 数据库状态在工具窗口工具栏（`bridge.db` / `diagnostics.json` / `no source`）和状态栏 tooltip 中可见。
- 移除 6 个损坏的 UI 设置测试（`DreamShaderSettingsToggleTest`）—— IntelliJ Platform 2026.1.3 固有兼容性问题。

### 修复

- 数据库来源的诊断现在正确继承外层文件包装对象的路径字段，修复了错误消息中缺失文件位置信息的问题。

## [0.0.6] - 2026-06-17

### 新增

- 新增声明相邻注释提取：紧邻 DreamShader 声明前的 `//` 与 `/* ... */` 注释现在会随内置声明说明一起显示在悬浮文档中。
- 新增共享的 `DreamShaderUi` 设计辅助层，为插件工作流面板提供统一的卡片容器、圆角边框、状态胶囊、弱化标签与输入对话框。

### 变更

- 将 DreamShader Live Templates 注册更新为当前 IntelliJ Platform 推荐的 `defaultLiveTemplates` 扩展点写法，并移除过时的 provider 实现。
- 集中 DreamShader 文件、声明、直接子声明/section、命名空间路径与声明注释等常用 PSI 遍历辅助逻辑，让导航、引用、结构视图、符号模型、诊断与纯文本符号补全共享同一套 tree-walking 行为。
- 优化跨文件引用搜索：不再全工作区遍历文件系统，而是通过 IntelliJ 词索引、同目录导入候选与 direct import 校验来缓存反向 importer 发现结果。
- 将 Bridge、Package Store、材质预览、设置、模板、Hub 与欢迎页等面板统一迁移到共享 DreamShader UI 表面，包含卡片式包列表/详情、更清晰的空状态、GitHub 操作提示与分组设置区块。

## [0.0.5] - 2026-06-15

### 新增

- 新增对齐 VS Code 1.5.3 的材质预览能力：通过 DreamShader Bridge 文件传输写出预览请求、读取预览结果与图片，提供右侧 ToolWindow、跟随编辑器与自动刷新延迟设置。
- 新增贴图采样与噪声材质 `.dsm` 模板操作。
- 新增“分步创建包”向导，支持填写包元数据并选择是否生成示例材质。
- 新增 DreamShader 原生代码样式设置，并让 formatter 读取语言专属选项，支持 section 间空行与 `::` 空格控制。
- 新增原生 File and Code Templates，包括 `New | DreamShader Material`、`DreamShader Function`、`DreamShader Header` 操作，以及可复用的 graph section 与 texture sample 代码模板。
- 新增 DreamShader Live Templates 与保守的 Postfix Templates，覆盖常见 shader/function/control-flow 编写模式。
- 新增 DreamShader gutter 标记，覆盖 import、顶层声明与 Bridge 诊断位置。
- 新增编辑器平台集成：引号处理、`import "..."` 的 include/dependency provider、Copy Reference 限定名、面包屑，以及常量 `float3` / `float4` / `vec3` / `vec4` 颜色预览。
- 新增更多 IntelliJ 小功能集成：声明 / section 的 Context Info、纯文本符号补全、重命名名称建议，以及 Copy/Paste Reference 的限定名反向解析。
- 新增 DreamShader 声明级重命名输入校验与安全删除可用性，复用已有的命名空间感知引用搜索，并保留 `Name="Path/Leaf"` 声明重命名时只替换叶子名的行为。
- 新增 Goto Symbol 与 Goto Class contributor，支持 DreamShader 资产声明、Function、GraphFunction、VirtualFunction、Namespace 及命名空间成员搜索。
- 新增拼写检查支持：覆盖 DreamShader 注释与 `VirtualFunction` 的 `Description` 字符串，同时避免对路径和代码风格字符串产生噪声。
- 新增保守的 `Substrate` 表达式诊断，覆盖算术、swizzle、向量构造参数与三元分支合并等可能在 Bridge 阶段失败的场景。
- 新增 `Path(...)` 对象段后缀警告，可提示 `.uasset`、`.umap` 与常见图片扩展名等会破坏 Bridge 的资源后缀。

### 变更

- 将参数内联提示从 Code Vision 设置中拆分出来，新增 `enableInlayParameterHints` 项目设置；关闭 Code Vision 不再隐藏参数提示。
- `ALIGN_SECTION_ASSIGNMENTS` 现在会实际对齐 section 内直接简单赋值，并按空行和嵌套 block 分组；Bridge 诊断 gutter marker 会优先跳转到诊断的源码行列。
- 更积极地缓存语义诊断输入：文件诊断现在会复用源码文本、整文件 token、声明上下文、section body、类型声明、可调用签名与 body 级 token 切片，减少热路径重复分析。
- 缓存 direct import 与 import closure 解析结果，让导航、签名帮助、引用搜索、内联提示、悬浮文档和 import 诊断在未修改文件上共享结果。
- `UE.<Name>` 跳转声明在源码扫描能解析到 Unreal 头文件目标时会优先跳转源码，同时保留 catalog/documentation 兜底。
- Hover 与签名渲染现在会保留声明式可调用参数的 `opt` 修饰符与默认值。
- 内联颜色预览扩展到 `Color(...)` / `LinearColor(...)`、`0xRRGGBB` / `0xRRGGBBAA`，以及保守的 0-255 整数通道形式，不再只限于向量构造。
- 声明头与 section 头格式错误现在由 parser 生成可恢复错误标记；原先重复的 token 级 malformed 语法诊断已移除。

## [0.0.4] - 2026-06-14

### 新增

- 新增基于 catalog 的 Material Expression manifest 解析，并接入 `UE.*` 补全；兼容 rich `expressions` 条目与旧 class-name fallback，同时为后续 `Substrate.*` wrapper 预留 namespace 数据路径。
- 将 `UE.<Name>(...)` 与 `Substrate.<Name>(...)` 的签名帮助接入共享的 material expression catalog；catalog 数据不完整时回退到内置签名表。
- 将 `UE.*` 与 `Substrate.*` 的悬浮文档接入共享 catalog，同时保留用户 hover documentation override 的优先级。
- 新增 best-effort 的 Unreal `UMaterialExpression` 头文件扫描器，输出 catalog 兼容的 manifest JSON，并接入 catalog 合并顺序（位于 bundled fallback 之前）。
- 新增 Unreal Engine 源码根目录自动检测（`.sln` / `.uproject` 加注册表查询），并新增源码根、扫描开关、扫描缓存路径等项目设置，接入设置 UI。
- 新增 Substrate 与 Layer 语义诊断：`Base.FrontMaterial` / `Base.MaterialAttributes` 绑定互斥、收紧 `ShaderLayer` / `ShaderLayerBlend` 输入输出 shape 规则、拒绝 `UMaterialExpressionCustom` 上的 `OutputType="Substrate"`。
- 扩展上游 `Examples.md` 解析覆盖 fixture，新增 Substrate 材质、Substrate `ShaderFunction` / `VirtualFunction`、Substrate escape-hatch，以及 `ShaderLayer` / `ShaderLayerBlend` 示例。

### 变更

- 将所有 JSON 访问（Bridge `settings.json` / `diagnostics.json`、包索引、GitHub 搜索、包元数据 / lock 文件、材质表达式清单、包脚手架）统一到 `kotlinx.serialization`，替换各文件各自手写的正则 / 括号扫描解析器。同时修复了 `\uXXXX`/`\/`/`\b`/`\f` 转义处理、避免深层嵌套同名字段误匹配，并在写出 lock 文件与包元数据时正确转义。

### 移除

- 移除了硬编码的 `UE.*` 内置节点文档表（TexCoord/Time/Panner/WorldPosition/Expression）。`UE.<Name>` 悬浮文档现在完全经由材质表达式 catalog（Bridge / 配置 / 扫描 / 捆绑清单）获取，文档随实际 catalog 数据更新，而非陈旧的硬编码列表。用户悬浮文档覆盖（`ueBuiltins.<name>.description`）仍然优先生效。

## [0.0.3] - 2026-05-30

### 新增

- 新增命名空间声明支持并接入语言解析与导航流程，覆盖跳转声明、查找用法与结构/符号模型。
- Package Store 中 GitHub 包搜索在请求进行时会禁用操作按钮，避免重复触发。
- 新增跨文件 import 闭包解析能力，用于支持导入函数的签名分析。
- 新增引用搜索跨 import 链路扩展，并支持 `Name="..."` 形式声明的引用场景。
- 新增嵌套命名空间下未知类型与缺失 `out` 参数的语义诊断覆盖。
- 新增同一作用域下的重复声明名语义诊断（覆盖顶层作用域与命名空间局部作用域），并允许不同命名空间作用域中的同名声明共存。
- 新增重复声明名快速修复：`将声明重命名为 '<NameN>'`，在当前作用域内建议唯一数字后缀名称。
- 新增 `Name="..."` 路径形式声明的重名覆盖：按路径叶子名参与重复检测，快速修复仅替换叶子名并保留路径前缀。
- 新增更严格的未知类型诊断与 `const` 纹理默认资源路径校验。
- 新增欢迎文件编辑器的 dumb 模式支持。

### 变更

- 重构欢迎页中的插件版本与更新日志加载流程。
- 重构 Code Vision 点击处理流程，提升交互稳定性并与包搜索状态切换保持一致。
- 增强 Hover 覆盖设置与欢迎页引导：内置条目支持编辑/重置，缺失项目根目录时的提示更清晰。
- 更新变更日志本地化流程：拆分语言文件（`CHANGELOG.md` / `CHANGELOG.zh-CN.md`）并同步 Release CI 的补丁策略。
- 更新 README 发布展示方式，加入 Marketplace 插件卡片与静态徽章。
- 重构共享的 import/签名解析辅助方法，收敛导航与诊断逻辑。
- 更新 README 终端构建说明，强调 Java 17+ 自动解析与 `GRADLE_USER_HOME` 使用方式。

### 修复

- 修复发布流程中配置缓存与任务依赖顺序问题，提升发布链路稳定性。
- 修复 CHANGELOG 补丁与 Gradle 配置缓存之间的构建冲突。

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

[0.0.6]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.5...0.0.6
[0.0.5]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.4...0.0.5
[0.0.4]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.3...0.0.4
[0.0.3]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.2...0.0.3
[0.0.2]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.1...0.0.2
[0.0.1]: https://github.com/tsdaer/dreamshader-language-support/commits/0.0.1
