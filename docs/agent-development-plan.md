# 智能体开发指导计划（Agent Development Plan）

- **制定日期**：2026-10-03
- **上游基线**：TypeDreamMoon/DreamShader **v2.0.2**（2026-10-02，commit `b3bbf48`）
- **本插件版本**：2.1.0（2026-10-05，M13），语言基线 DreamShader 1.8.0 + upstream main @8f84830（1.x）/ DreamShader 2.0.2 @b3bbf48（2.0）
- **上游参考副本**：`/tmp/dreamshader-ref`（浅克隆；重新获取见 §7.1 网络守则）
- **计划状态**：执行中。**M8 已完成**（2026-10-03，A1 + A2 + B1 + B5：上游语料库 72 文件 / language-v2 三文档示例 / 三份模板零 error 解析，1.x 全量测试零回归）；**M9 已完成**（2026-10-03，A3 + B2 + B6：builtin-catalog 合并链接入 + 内嵌 V2 兜底目录 / symbol-index v1 只读仓库 / bridge.db meta 表 / reveal-node 动作与 gutter 标记 / status.json+owner.lock 存活模型与状态栏在线离线）；**M10 已完成**（2026-10-03，A4 + A5 + A8 + A9 + B3 + B4 + B7 + D8 首批：2.0 模板与 New 菜单 / 639 码 DSH 码表 + tooltip 码义 + 文档意图 + DSH2248/2249/3203 预防性检查 / GLSL 别名提示 DSH5277 / dsc Check-Format-Migrate-Index 动作与设置项 / 派生文件 problemHighlightFilter + readerModeMatcher / 未知类型规则迁移 LocalInspectionTool / DB reader·watcher 内核·settings UI 测试债）；**M11 已完成**（2026-10-03，A6 + C1 + C2：2.0 声明 stub 化与四个 StubIndex（uniform / export·extern 函数 / struct / include 边，`stubElementTypeHolder` 接口注册）/ DreamShader2WorkspaceSymbols 按名合并（Bridge 权威、stub 兜底、include 闭包）/ 保守类型推断（拼写、逐分量提升、swizzle 收缩、catalog 返回类型、静默 Unknown）/ Substrate 糖检查 DSH5293·5295·5296·5297·5298 与 DSH2158 / `.dsi` 覆盖检查 DSH7258·7264·7259·7268（schema 门控降级）/ 赋值类目弱警告）。**M12 已完成**（2026-10-04，C3 + C4 + C5 + C6 + C7 + A7 + D1–D7：2.0 上下文补全（catalog 命名实参 / Substrate builder 成员 / `.dsi` 父参数 / `#pragma material` 键值）/ 2.0 语义高亮三级配色 + Substrate 变量值 + `@key` 与覆盖名着色 / V2 工作区引用搜索与改名影响面安全网 / 2.0 结构视图与 Code Vision / reveal-node「Open at line」双向闭环 / WebSocket 实时预览（帧解码器 + 回环客户端 + 探针行联动 + mock 服务器往返测试）/ D1–D7 平台编辑特性（用法高亮、Surround With、建议式重构、四件套编辑动作、结构块、Copy Path、HLSL 注入））。**M13 已完成**（2026-10-05，C8–C16 + A10 + D9 + T13：方言分流与补全门控 / 2.0 接收者类型驱动成员补全（swizzle、矩阵、struct 字段、贴图 sample、material 属性、Substrate pin、catalog 命名空间）/ 统一优先级与去重 / 命名实参槽位收紧与 catalog 枚举值补全 / `.` 自动弹出方言感知 / 2.0 裸标识符与 `#include` 路径补全 / `.dsi` 覆盖值类型感知补全 / 1.x 按点降级收紧 / 字符串插入引号规范化 / 补全热路径 PSI 复用与候选缓存）。roadmap.md 的 M0–M7 已全部完成。

本文档是面向智能体（AI agent）的可执行开发计划：每个任务卡都给出目标、涉及文件、验收标准与上游参考，智能体按里程碑顺序认领任务、独立完成、按 §6 守则提交。执行者不得跳过验收标准。

---

## 1. 背景与差距总览

上游 DreamShader 2.0.0（2026-09-21）是一次**编译器重写**而非功能更新：预处理 → 解析 → 绑定 → 图 IR → pass → 校验 → 输出，一条流水线、两个前端——`.dss` 走 2.0 解析器（HLSL 形态的声明式语法），`.dsm`/`.dsf` 走 legacy 前端读入同一棵树；`.dsh` 头文件**可同时容纳两种方言**，项目可逐文件迁移。2.0.1 / 2.0.2 是反编译器与 Substrate/Landscape 引脚的修复补丁。本插件目前对这一切**零感知**。

| 能力维度 | 上游 2.0.2 | 本插件 1.1.0 | 差距 |
|---|---|---|---|
| 文件类型 | `.dss` / `.dsi` / `.dsm` / `.dsf` / `.dsh` | `.dsm` / `.dsf` / `.dsh` | `.dss`/`.dsi` 未注册 |
| 语法前端 | 两方言一 IR，完整表达式/控制流 | 仅 `FILE/DECLARATION/SECTION` 三种节点，函数体是裸 token | 无表达式语法 |
| 诊断 | 617 个 DSH 码，每码一页文档 | DSH 码原样透传，不识别、不解释 | 无码表 |
| Bridge 工件 | builtin-catalog、symbol-index、meta 表、reveal-node、分片诊断 | 读 bridge.db 诊断表 + 4 个 JSON，无 meta/symbol-index/reveal-node | 缺 2.0 新工件 |
| 预览 | 文件传输 + WebSocket(17864) 实时帧 | 仅文件传输 | 无实时预览 |
| CLI (`dsc`) | check/migrate/decompile/fmt/index/export-catalog… | 无集成（仅用户自定义 shell 命令） | 无工具链 |
| UE 版本 | 5.3–5.8，Substrate 按版本门控 | 不感知引擎版本 | 不感知 |

**总体判断**：1.x 支持已成熟且上游保留 legacy 前端（1.x 语法仍是受支持的第二前端），不需要废弃；下一周期的工作重心是**把 2.0 作为新增方言接入**，并利用 2.0 新产出的机器可读工件（symbol index、builtin catalog）把智能度抬到新台阶。

---

## 2. 工作流 A：上游新特性支持（必须做）

按优先级排列。P0 = 无此则 2.0 用户无法工作；P1 = 显著体验差距；P2 = 增强与前瞻。

### A1（P0）注册 `.dss` / `.dsi` 文件类型与方言模型
- **目标**：插件认识 2.0 文件。上游生成的 `.code-workspace` 只关联 `.dsm/.dsh/.dsf`（见上游 `Docs/tools/workspace.md:41-43`），**`.dss`/`.dsi` 必须由本插件自行注册**。
- **任务**：
  1. `DreamShaderFileType` 扩展为多扩展名（`dsm;dsf;dsh;dss;dsi`），或在语言下注册第二个 fileType `DreamShaderLang2`。二选一需先做原型验证 IntelliJ 平台对单 fileType 多扩展名的行为（推荐前者，改动小）。
  2. 引入 `DreamShaderDialect` 枚举（`V1` / `V2` / `HEADER_MIXED`），按扩展名 + 内容启发（`#pragma material(`/`export`/`uniform` = V2 标记；`Shader(`/`ShaderFunction(` 块 = V1 标记）判定，`.dsh` 允许混合（上游规则：按声明判定，错位报 `DSH2248`/`DSH2249`，见上游 `Docs/language/source-files.md`）。
  3. 文件图标区分（上游 `Resources/` 有对应图标语义可参考）。
- **验收**：五种扩展名文件在沙盒中均有高亮/补全入口；新增 `DreamShaderDialectTest` 覆盖判定矩阵（扩展名 × 内容标记 → 方言）。
- **上游参考**：`Docs/language-v2/index.md`（语言总览）、`Docs/language/source-files.md`（文件种类规则）。

### A2（P0）2.0 词法与语法前端（`.dss`/`.dsi` 解析器）
- **目标**：为 V2 方言建立真实语法树。这是本计划**最大的单项工程**，也是智能化（工作流 C）的地基。
- **任务**：
  1. 新增 `DreamShader2Lexer`（或扩展现有 lexer 增加 V2 状态）：`DOT`/`COMMA`/`SEMICOLON` 独立 token、`///` 文档注释 token、`#pragma`/`#include`/`#if` 预处理指令 token、字符串保留字面量（`"((LayerName=\"Grass\"))"` 这类引号内结构串）。
  2. 新增 `DreamShader2ParserDefinition` + PSI：节点至少覆盖 `TranslationUnit`、`PragmaDirective`、`UniformDeclaration`（含 `///` 属性块 @group/@sort/@slider/@static/@default/@name/@sampler/@asset/@library/@layer/@layerblend/@param/@custom）、`StructDeclaration`、`FunctionDeclaration`（`export`/`extern`/helper 三种）、`IncludeDirective`、`Statement`、`Expression`（调用/命名实参 `Name = value`/成员访问/swizzle/索引/构造器/控制流）。
  3. `.dsi` 专属：`#pragma instance(Parent = …)` 头、覆盖 uniform 声明。
  4. 解析器保持**容错恢复**（错误不阻断后续声明），与现有 1.x 宽容哲学一致。
  5. 现有 1.x 管线完全不动：方言判定后分流到旧 pipeline（1.x）或新 pipeline（2.0）。
- **验收**：上游 `Docs/language-v2/index.md`、`Docs/language-v2/substrate.md`、`Docs/language-v2/instances.md`、`Resources/Templates/*.dss|*.dsi` 的**全部示例**作为 fixture 解析无 error 节点；新增 `DreamShader2ParserTest`（每文档 ≥5 个用例）；`DreamShaderUpstreamExamplesTest` 扩充 V2 目录。
- **上游参考**：`Docs/language-v2/index.md`（310 行，声明表/语句/表达式/命名实参/不支持清单 DSH2160/DSH3201/DSH4360-4362 等）、`Docs/language/preprocessor.md`。

### A3（P0）Bridge 2.0 工件接入
- **目标**：消费上游 2.0 新写的磁盘工件，IDE 侧获得权威目录与符号数据。
- **任务**：
  1. **`dreamshader-builtin-catalog.json`**（2.0 绑定器节点目录，引擎启动后导出）：接入 `DreamShaderMaterialExpressionManifest` 合并链（最高优先级，高于现有 BRIDGE_MANIFEST），用其中的类/引脚/别名/位置参数顺序驱动 `UE.*`/`Substrate.*` 补全与签名帮助。
  2. **`dreamshader-symbol-index` v1**（`Saved/DreamShader/Index/<root>/<source>.index.json`，每次编译后刷新，含声明/提及 span/include 闭包/参数 schema）：新增只读仓库服务（参照 `DreamShaderBridgeDiagnosticsRepository` 的模式），供工作流 C 使用；注意 schema 字符串 `dreamshader-symbol-index` 校验与 VFS 刷新。
  3. **`bridge.db` 的 `meta` 表**：读取并暴露 protocol/pluginVersion/engineDir（架构文档已声称支持但代码未读，属修复，见 B6）。
  4. **`reveal-node` 请求**（2.0 新动作，`{action:"reveal-node", file, line}` → 响应 `{version:1, assetPath, instanceAssetPath?, expressions[]}`，失败 `DSH9050–9057`、`stage:"navigate"`）：接入 `DreamShaderBridgeCommandExecutor` 的请求写器（复用 preview 的临时文件+原子写模式），新增编辑器动作「Reveal Node in Unreal Material Editor」与 gutter 行标记。
  5. **`status.json` 心跳与 `owner.lock`**：识别编辑器存活状态（`pluginVersion`、`busy`、心跳 2s、关停即删除），在状态栏 widget 显示「编辑器在线/离线」，避免向已死编辑器写请求。
- **验收**：各工件均有 DTO + 解析测试（用上游文档中的样例 JSON 作为 fixture）；catalog 合并优先级有回归测试；reveal-node 请求体与上游 `Docs/tools/bridge.md:261-269` 字段逐一对照。
- **上游参考**：`Docs/tools/bridge.md`（协议全表）、`Docs/api/lang-module.md`（symbol index）。

### A4（P1）`.dss` / `.dsi` 文件模板
- **目标**：`New → DreamShader` 菜单支持 2.0 模板，与上游 Content Browser 的 New 菜单对齐。
- **任务**：
  1. 移植上游 `Resources/Templates/NewMaterial.dss`、`NewFunction.dss`、`NewInstance.dsi` 三份模板正文，占位符映射：`{FILENAME}`/`{ASSETPATH}`/`{STEM}`/`{PARENT}`（`{STEM}` 是 2.0 新占位符——export 名 = 文件名主干 = 资产名）。
  2. `New DreamShader Instance` 动作 + `{PARENT}` 输入对话框；`.dss` 材质默认名 `M_NewMaterial`、函数 `MF_NewFunction`（上游约定）。
  3. 现有 1.x 模板保留不删（上游同样是两段式菜单：2.0 在前、1.x 在下）。
- **验收**：模板渲染测试（沿用现有 template commands 测试骨架）；渲染产物作为 A2 解析 fixture 应零错误。
- **上游参考**：`Resources/Templates/`、`Docs/tools/material-browser.md:261-295`。

### A5（P1）DSH 诊断码目录
- **目标**：Bridge 诊断里的 DSH 码不再是哑文本。
- **任务**：
  1. 从上游 `Docs/diagnostics/`（617 码）生成机器可读码表资源（`messages/dreamshader-dsh-codes.json`：`code → {severity, summary_zh, summary_en, docAnchor}`）。写一个一次性脚本从上游文档抽取，脚本入库 `scripts/` 以便随上游更新重跑。
  2. Bridge 诊断渲染：tooltip 显示码义 + 「Open documentation」动作（打开上游对应页或本地捆绑页）。
  3. 对高频码提供**插件侧预防性检查**（与现有 Bridge-failure-prevention 思路一致）：至少 `DSH2248`（2.0 语法出现在 `.dsm`/`.dsf`）、`DSH2249`（`.dsh` 中的 1.x 资产块错位）、`DSH3203`（`#include <...>` 尖括号）。
- **验收**：码表生成脚本可重复执行且 diff 稳定；诊断 tooltip 渲染测试；三个预防性检查各有正反例测试。
- **上游参考**：`Docs/diagnostics/index.md`、`Docs/diagnostics/DSH9xxx.md`、`Docs/diagnostics/README.md`。

### A6（P1）Substrate 语法糖与 `.dsi` 覆盖检查（诊断级）
- **目标**：对 2.0 特有语义做**保守启发式检查**（与插件现有 Substrate 1.x 检查同哲学：插件不做编译器，Bridge/`dsc check` 是权威）。
- **任务**：
  1. Substrate 糖：`A + B`/`A * w`/`lerp(A,B,t)` 合法性、非糖操作符报 `DSH5293` 同义提示、`Substrate.Slab` 参数族（`Haziness` 需 `Roughness`、`Thickness` 需 `Transmittance` → `DSH5296`）、builder 形态 `Substrate S = Substrate.Slab(); S.Roughness = r;` 的先用后写（`DSH5297`）。
  2. `.dsi` 覆盖检查（当 symbol index 可用时对照父材质参数 schema）：未知参数名（`DSH7258`）、类型不匹配（`DSH7259`）、layer/blend 专属参数不可覆盖（`DSH7268`）；symbol index 不可用时降级为「无法校验」不误报。
  3. 命名实参合法性：位置实参不得跟随命名实参（上游规则）。
- **验收**：每条规则正反例测试；关闭 Bridge 数据时不产生误报（降级路径测试）。
- **上游参考**：`Docs/language-v2/substrate.md`、`Docs/language-v2/instances.md`。

### A7（P2）WebSocket 实时预览
- **目标**：补齐预览实时性。上游支持 `ws://127.0.0.1:17864`（仅环回、固定端口、二进制帧 `[u32 LE 长度][tag: 1=JSON/2=PNG/3=RGBA8]`，消息 `previewMaterial/previewControl/setProbe/clearProbe` → `previewResult/previewFrame/probeState`，RGBA8 帧 24 字节头含宽高与轨道角）。
- **任务**：设置里已预留 `previewTransport`/`previewWebSocketPort`/`previewLiveFrameRate` 三个字段，激活之；文件传输保留为回退；实现探针（setProbe/clearProbe）与编辑器行联动。
- **验收**：对回退路径零回归（现有 preview 测试全绿）；WS 路径用本地 mock server 集成测试（不要求真实 UE 环境过测）。
- **上游参考**：上游 `Docs/tools/bridge.md` Preview 一节。

### A8（P2）`dsc` CLI 工具链集成
- **目标**：把上游 `dsc`（引擎命令行）作为插件的一等工具：**Check**（诊断权威刷新）、**Format**（`dsc fmt`， refusing-with-diagnostic 语义 `DSH9042-9044`）、**Migrate**（`dsc migrate`，1.x→2.0，带 `-Check` 预演）、**Index**（`dsc index` 手动刷新符号索引）。
- **任务**：设置页新增 `dsc` 可执行路径；`Tools → DreamShader` 新增 Check/Format/Migrate（预演模式：`-Check` 结果以 diff/列表呈现）动作；迁移前自动提示备份路径 `Saved/DreamShader/Migrated/`。
- **验收**：动作执行器测试（沿用 `DreamShaderBridgeActionsTest` 的 hook 模式）；无 `dsc` 配置时动作禁用并有提示。
- **上游参考**：`Docs/tools/migrate.md`、`Docs/tools/decompiler.md`、`Docs/tools/format.md`（若存在）。

### A9（P2）反编译产物只读支持
- **目标**：2.0 反编译输出目录 `DShader/Decompiled/**`（`.dss`/`.dsi`）在 IDE 中标记为派生只读文件（不参与「生成物缺失」诊断、重编译清理时识别），头部 `// Warning: DSH9064: …` 注释走 A5 的码表渲染。
- **任务**：
  1. 路径识别与派生标记。
  2. 注册 `com.intellij.problemHighlightFilter`：对 `Decompiled/`、`Intermediate/DreamShader/` 生成物、包缓存目录返回 false，避免平台对派生文件做无意义错误高亮（官方 minor features 页明确项）。
  3. 注册 `com.intellij.readerModeMatcher`：派生只读文件默认进入阅读模式（文档渲染、弱化标点），提升「只读浏览反编译产物」的体验。
- **验收**：路径识别测试；problemHighlightFilter 对派生/源码文件的真值表测试；只读标记不阻断用户手动编辑（仅去掉误报）。

---

## 3. 工作流 B：修复与优化（应该做）

| # | 问题 | 现状与证据 | 修复方案 | 验收 |
|---|---|---|---|---|
| B1 | lexer 标点粒度不足 | 无 `DOT/COMMA/SEMICOLON` token，全部是 `OPERATOR`；关键词大小写不敏感（`lexer/DreamShaderLexer.kt`）。对 1.x 无碍，但对 V2（成员访问 `m.BaseColor`、命名实参、严格语法的 `export`/`uniform` 区分大小写）是硬伤 | 随 A2 一并实现 V2 lexer 的独立标点 token；V1 保持现状以免回归 | V1 高亮 fixture 不变；V2 新 token 有测试 |
| B2 | 捆绑 fallback catalog 仅 6 条 | `/messages/material-expression-manifest.json` 只有 Sine/Cosine/Multiply/Add/Subtract/TextureSample | 用上游 `dsc export-catalog` 产物（或 CI 拉取）内嵌一份完整 V2 目录作为兜底；标注来源版本 | 离线安装后 `UE.` 补全 ≥ 27 个注册调用 |
| B3 | GLSL legacy-alias 诊断被 blocked | roadmap 记录「pending upstream confirmation」 | 上游 2.0 已把 1.x 宽容行为编号为 `DSH5275–5292`、静默行为改为 `DSH2200–2222`（`Docs/language-v2/index.md:184-187`）——**阻塞解除**，按上游编号实现提示级诊断（如 `vec3`→`float3` 建议） | 每个编号至少一条用例 |
| B4 | 测试债 | `DreamShaderBridgeDatabaseReader` 无专属测试；BridgeFileWatcher 单测模式下禁用；settings UI 测试在 0.0.7 被删 | DB reader 用内存 SQLite fixture 补测；watcher 抽出可测内核后补测；settings UI 至少恢复冒烟级测试 | 三个方向各 ≥1 个新测试 |
| B5 | 基线文档过期 | `language-baseline.md` 快照 = DreamShader 1.8.0 @8f84830（checked 2026-09-01） | 随 A1/A2 落地更新为「1.8.0 + 2.0.2 双基线」，新增 `language-v2-baseline.md` 专述 V2 覆盖映射 | 文档与实现互检（沿用现有 coverage-mapping 风格） |
| B6 | `bridge.db` meta 表未读 | 架构文档声称读 meta，代码没有（`DreamShaderBridgeDatabase.kt` 只读诊断表） | 已并入 A3.3；此处作为修复跟踪 | 同 A3 |
| B7 | 状态栏/工具窗对 2.0 诊断的展示 | 诊断表 `code` 列 2.0 会携带 DSH 码 | 随 A5 在 tooltip/详情列渲染码义 | 同 A5 |

---

## 4. 工作流 C：智能化提升目标（值得做）

按依赖排序：C1–C2 是地基，C3–C7 是其上的收益。

### C1 跨文件符号模型（利用 symbol index v1 + 平台 StubIndex）
- 现状：`DreamShaderSymbolModel` 只有 DECLARATION/SECTION/NAMESPACE_MEMBER 三种扁平符号；references 是 import 闭包级而非工作区级。
- **技术路线（2026-10-03 官方文档审查后修订）**：V2 PSI 落地后，优先用**平台 `StubIndex`** 承载跨文件声明索引（uniform / export 函数 / struct / include 边各建一个 stub index，参照官方 go-to-symbol/symbols 主题的 stub-indexes 方案），而不是自建一套索引存储。理由：goto symbol / find usages / rename / 高亮全部自动获得平台集成与持久化（FastSerializer 缓存），免维护自己的失效逻辑；自建服务只保留一个薄职责——**桥接 Bridge 的 `dreamshader-symbol-index` v1 工件**（权威、引擎编译后刷新）与 stub index（插件离线兜底）之间的数据供给与失效。
- 为 `.dsi` 父材质解析、跨文件补全、工作区 find usages 提供底座；C5 的 rename 安全网直接跑在 stub index 查询上。
- 验收：stub index 序列化/重建测试（索引失效→重建幂等）；Bridge symbol index 与 stub index 合并优先级测试；大项目（≥500 文件 fixture）索引构建在后台线程完成、UI 不卡（沿用大文件性能冒烟测试的门槛）。

### C2 表达式类型推断（V2）
- 现状：无类型系统（语言基线自认「full expression type inference remains a separate long-term hardening target」）。
- 目标：在 V2 PSI 之上实现保守类型推断：`float`/`half`/`int`/`bool`/向量（含 swizzle 收缩 `float3.xy→float2`）/矩阵/纹理/`material`/`Substrate`。类型供三处消费：参数提示里的实参-形参类型标注、不匹配告警（警告级，绝不 error——权威在 Bridge）、C3 补全过滤。
- 验收：类型推断单测覆盖上游文档全部类型示例；推断失败必须静默降级（无类型 ≠ 诊断）。

### C3 上下文补全智能化
- 命名实参补全：`UE.LinearInterpolate(` 后按 catalog `inputs[]` 顺序提示未填参数并自动 `Name = `；已填参数不再提示（catalog 驱动，A3.1）。
- `Substrate` builder 成员补全：`Substrate S = Substrate.Slab(); S.⌷` → 18 个 BSDF 引脚按可用性过滤。
- `.dsi` 父参数补全：`uniform ⌷` → 父材质参数 schema（C1 symbol index）。
- `#pragma material(...)` 键值补全从硬编码迁移到 Bridge `settings.json` + catalog（现有 26 键硬编码作为兜底）。
- 验收：每项 ≥5 个补全场景测试（含负向：不相关上下文不出全）。

### C4 语义高亮与视觉升级（V2）
- 基于 C2 类型：`material`/`Substrate` 类型变量独立配色；`uniform` 参数 vs 局部变量 vs 函数参数三级语义色；`/// @` 属性名单独着色；`.dsi` 中与父材质参数对应的覆盖名高亮。
- 验收：语义 token 分类器测试扩展到 V2 fixture。

### C5 工作区级 find usages / rename 安全网
- 基于 C1：uniform 参数改名前检查 `.dsi` 覆盖引用；export 函数跨文件 usages 覆盖 include 闭包 + Decompiled 目录排除。
- 验收：改名影响面列表测试（含 `.dsi` 引用场景）。

### C6 结构视图 / 面包屑 / Code Vision（V2）
- 结构视图：uniforms 按 `@group` 分组、exports、structs、includes；`.dsi` 显示 Parent 节点可跳转。
- Code Vision：export 函数上方显示引用计数；`#pragma instance` 上方显示「Parent: M_Skin」导航。
- 验收：结构视图模型 + CodeVision provider 测试（沿用 1.x 测试骨架）。

### C7 双向 UE 导航闭环
- 正向（已有雏形）：`.dss` 行 → `reveal-node`（A3.4）打开 UE 材质编辑器选中节点。
- 反向（增强现有 `DreamShaderUnrealSourceLocator`）：UE 侧 `DreamShader.SourceSpans` 元数据（表达式 GUID → 行号）意味着可从资产回到源码行；在 Bridge 工具窗的诊断与 reveal-node 响应中提供「Open at line」。
- 验收：reveal-node 请求-响应往返 mock 测试；行标记渲染测试。

---

## 4b. 工作流 D：IntelliJ 平台特性补齐（体验与智能度）

来源：2026-10-03 对官方文档 [Custom Language Support](https://plugins.jetbrains.com/docs/intellij/custom-language-support.html)、**[Additional Minor Features](https://plugins.jetbrains.com/docs/intellij/additional-minor-features.html)**、[PSI](https://plugins.jetbrains.com/docs/intellij/psi.html)、[Language Injection](https://plugins.jetbrains.com/docs/intellij/language-injection.html) 的逐项审查。已确认**无需任务**的项：TODO 视图（`getCommentTokens` 已返回注释 token，TODO 工具窗自动可用）、Brace/Quote/Commenter/Folding/Smart Enter/Context Info/Breadcrumbs/Color Preview/File Includes/Plain Text Completion/FQN/Naming Suggestions（均已注册）、`testSourcesFilter`（DSL 无测试文件概念）、`referenceInjector`（依赖 IntelliLang，价值低）。D 线任务均为**与上游无关的平台侧增强**，可在 M8 之后随时并行插入。

### D1（P1）文件内用法高亮
- **任务**：注册 `com.intellij.highlightUsagesHandlerFactory`——光标落在声明/引用上时高亮文件内全部用法（`Edit | Find Usages in File` 语义）；V2 可加 `return`/`break`/`continue` 的 exit-point 高亮（官方页明示的进阶用法）。
- **验收**：1.x 声明引用高亮测试（复用现有 references 基础，不依赖 V2 PSI）。

### D2（P1）Surround With
- **任务**：注册 `com.intellij.lang.surroundDescriptor`：V2 支持语句包 `if`/块 `{}`/`for`；1.x 支持声明包 section。连带 `surroundRangeHandler` 若平台要求。
- **验收**：`Surround With` 弹窗出现且模板缩进正确；V1 fixture 回归零 diff。

### D3（P1）建议式重构（rename / change signature 建议）
- **任务**：注册 `com.intellij.suggestedRefactoringSupport`：V2 中检测到 export 函数调用处签名与声明不一致时，在声明上建议「重命名/改签名」；uniform 在 `.dsi` 中有覆盖引用而声明被改名时提示同步。这是官方 minor features 页的收尾条目，也是纯插件侧的智能度项。
- **验收**：签名不匹配场景的建议触发测试；无歧义场景不误触发。

### D4（P2）编辑动作四件套
- **任务**：`joinLinesHandler`（多行表达式/字符串拼接）、`moveLeftRightHandler`（V2 命名实参间左右移动）、`statementUpDownMover`（V2 声明/语句上下移动、1.x section 上下移动且保持语法合法）、`listSplitJoinContext`（命名实参列表、uniform 块的拆行/合并意图）。
- **验收**：每动作 ≥3 个场景测试；移动后语法正确性由解析器无 error 节点断言。

### D5（P2）结构块支持与结构大括号
- **任务**：
  1. `com.intellij.codeBlockSupportHandler`：V2 `if/else`、`for`、函数体等复杂块的首尾导航与关键词高亮；1.x declaration/section 大括号块同样受益。
  2. `DreamShaderBraceMatcher` 中把 declaration/section 的大括号标记为 **structural brace**（官方 Brace Matching 一节：结构括号有更高匹配优先级），并评估是否需要 `com.intellij.heavyBracesHighlighter`（预计不需要，文件规模小）。
- **验收**：块首尾互跳测试；结构括号高亮行为测试。

### D6（P2）自定义 Copy Path
- **任务**：继承 `CopyPathProvider`（或 `DumbAwareCopyPathProvider`），返回语言专属路径：文件路径（项目相对）与 `.dss` 产物资产路径；注册到 `CopyReferencePopupGroup` 弹窗组。
- **验收**：两种路径形态的返回值测试；弹窗注册在 plugin.xml 中可被动作测试发现。

### D7（P2）条件式 HLSL 语言注入
- **目标**：1.x `Function`/`GraphFunction` 裸体与 2.0 `/// @custom` 函数体是裸 HLSL，注入 HLSL 语言后获得原生高亮/括号匹配（官方 Language Injection 页的 `MultiHostInjector` 路线）。
- **任务**：实现 `MultiHostInjector`，注入目标语言在运行时探测（`InjectedLanguage.findLanguageById("HLSL")`——Rider with Unreal 支持 HLSL；纯平台环境无此语言则注入器静默跳过）。`com.intellij.referenceInjector` 与 IntelliLang 配置文件路线评估后放弃（依赖 IntelliLang 插件、无 HLSL 语言可选）。
- **验收**：有/无 HLSL 语言的分支测试；注入片段与 host 文本的边界（括号平衡）测试。

### D8（工程优化）重检查 LocalInspectionTool 化
- **任务**：把 annotator pipeline 中**级别可配置价值高**的重规则（未知 `UE.Expression` 类、未知类型、导入解析失败等）迁移为 `LocalInspectionTool`：获得 Inspect Code 批量扫描、off-EDT 执行、用户可调级别/开关；annotator 保留必须即时反馈的轻规则。分批迁移，每批一条规则。
- **验收**：迁移后的规则在 `Settings → Editor → Inspections → DreamShader` 可配置；`Inspect Code` 能扫出跨文件样例问题；annotator 与 inspection 不重复报同一问题（去重测试）。

### D9（前瞻备忘，不排期）
- `polysymbols`（import 歧义时一个引用多目标 resolve）；平台 LSP API 备忘——若上游某天提供独立语言服务器，可用 `LspServerDescriptor` 重接数据源，本计划的 Bridge 文件协议路线不受影响。

---


## 5. 里程碑编排

延续 roadmap.md 的 M0–M7 编号。每个里程碑 = 一个可发布版本切片；里程碑内任务可并行认领，跨里程碑依赖用 ← 标注。

### M8 — 「2.0 立足」→ 目标版本 1.2.0
范围：A1 + A2 + B1 + B5（文档双基线）。依赖：无外部依赖，纯插件侧。
**发布门槛**：全部上游 V2 文档示例解析零 error；1.x 回归测试全绿；`.dss`/`.dsi` 在无 Bridge 数据的裸沙盒中可编辑。

### M9 — 「接上引擎」→ 目标版本 1.3.0
范围：A3（catalog/symbol-index/meta/reveal-node/status）+ B2 + B6。依赖：A1（方言已注册）。
**发布门槛**：有真实引擎环境时（用户提供），补全/签名帮助来自 builtin-catalog；reveal-node 完成一次真机往返。

### M10 — 「诊断与工具链」→ 目标版本 1.4.0（已完成 2026-10-03）
范围：A4 + A5 + A8 + A9（含 problemHighlightFilter/readerModeMatcher）+ B3 + B4 + B7 + D8（inspection 化首批：未知类型规则）。依赖：A2（模板渲染校验需要解析器）、A3（码表动作挂在诊断渲染上）。
**发布门槛**：639 码表入库（上游 2.0.2 实际码数）；`dsc check` 一键刷新诊断走通；`Settings → Editor → Inspections` 出现 DreamShader 分组。——均已达。码表由 `scripts/generate_dsh_codes.py` 从上游 README 表生成，`--source` 记录上游版本。

### M11 — 「语义智能」→ 目标版本 1.5.0（已完成 2026-10-03）
范围：A6 + C1（StubIndex 路线）+ C2。依赖：A2（PSI）、A3（symbol index）。
**发布门槛**：类型推断与 stub index 单测达标；Bridge 缺席时全部功能静默降级不误报。——均已达。工程落点：stub 元素类型经 Java holder 接口注册（平台 2026.1 要求接口且在索引初始化期扫描）；PSI 双构造 + stub-first 访问器；诊断挂入既有 annotator 流水线的 V2 分支；全量 658 测试绿（新增 17 个 M11 测试）。

### M12 — 「编辑体验」→ 目标版本 2.0.0（已完成 2026-10-04）
范围：C3 + C4 + C5 + C6 + C7 + A7 + D1 + D2 + D3 + D4 + D5 + D6 + D7。依赖：M11。
**发布门槛**：README/Marketplace 文案更新（含 plugin-metadata 块）；`gradlew build` + 全量测试绿；插件验证器通过。——README 中英文案、CHANGELOG（en/zh）、`language-v2-baseline.md` M12 语义图均已更新；全量测试绿（M12 新增 60+ 测试），版本号 2.0.0。

> 排期原则：M8 未完成前不开 M11/C 线的编码工作（C 依赖 V2 PSI）；A3 可与 A2 并行（纯 DTO/协议层）；工作流 D 各任务与上游无耦合，自 M8 起可随时并行插入，其中 D1/D2 在 M8 后即可实现（仅依赖现有 1.x references/PSI 基础）。

### M13 — 「补全智能化」→ 目标版本 2.1.0（已完成 2026-10-05）
范围：C8 + C9 + C10 + C11 + C12 + C13 + C14 + C15 + C16 + A10 + D9 + T13。任务卡全文（含根因分析与验收矩阵）见 `docs/completion-plan.md`；依赖 M11（`DreamShader2InferenceContext`/`structFields`）与 M12 C3。
**发布门槛**：`gradlew build` + 全量测试绿。——已更新：CHANGELOG（en/zh）2.1.0 段、`language-v2-baseline.md` M13 补全覆盖映射、README 元数据块补全描述；版本号 2.1.0；全量 757 例零失败。

---

## 6. 智能体工作守则（每个执行任务前必读）

### 6.1 构建 / 测试 / 运行
```powershell
.\gradlew.bat build --no-configuration-cache   # 构建
.\gradlew.bat test  --no-configuration-cache   # 测试（JUnit Platform + Jupiter）
.\gradlew.bat runIde --no-configuration-cache  # 沙盒 IDE
```
- Windows 环境，shell 为 Git Bash；PowerShell 命令用 `./gradlew.bat` 等价执行。
- 每个任务完成定义（DoD）：`build` 通过 + 新增测试通过 + **全量既有测试不回归**。

### 6.2 网络访问
- 需要访问 GitHub / 上游仓库时一律走本地代理：`curl -x http://127.0.0.1:7890 …`；git 克隆用 `git -c http.proxy=http://127.0.0.1:7890 clone …`。
- 上游参考副本刷新：`git -c http.proxy=http://127.0.0.1:7890 -C /tmp/dreamshader-ref pull`（浅克隆则重新 clone）。

### 6.3 提交与版本规范
- 提交信息：conventional commits + gitmoji，如 `feat: :sparkles: …`、`fix: :bug: …`、`docs: :memo: …`、`chore: :bookmark: bump version`（与仓库现有历史一致）。
- 版本唯一来源是 `gradle.properties` 的 `version`；CHANGELOG 政策：**无 Unreleased 段**，新日志写入当前版本段，发版时新增 `## [x.y.z] - YYYY-MM-DD` 段并更新底部 compare 链接。
- README 的 `<!-- plugin-metadata:start/end -->` 块是 Marketplace 文案来源，新增用户可见功能必须同步更新 `description` 与 `description_zh`。
- 语言/包行为变化必须同步 `language-baseline.md` / `package-baseline.md`（M8 起 V2 行为写入 `language-v2-baseline.md`），并在快照头注明上游版本与 commit。

### 6.4 架构红线
1. **插件不实现编译器**。语义正确性的权威是引擎侧（Bridge 工件与 `dsc check`）；插件侧检查一律保守启发式，宁可漏报不误报，Bridge 数据缺席时静默降级。
2. **1.x 行为不回归**。V2 走新管线，旧管线与旧 lexer 行为冻结；迁移到共享 lexer 时必须先证明高亮/诊断 fixture 零 diff。
3. **Bridge 目录只读，Requests 是唯一写通道**。除 `Requests/request-*.json`（临时文件 + 原子 rename）外不写任何 Bridge 路径；遵守 `owner.lock` 语义。
4. **平台级兼容**：继续只依赖 `com.intellij.modules.platform`，不引入 Rider 专属 API 与 `com.intellij.modules.rider`。
5. **性能红线**：新增分析代码必须走缓存/后台线程模式（参照 `DreamShaderSemanticAnnotationPipeline` 的 cached-inputs 模式），禁止在 EDT 做索引扫描。
6. **上游工件 schema 校验**：解析任何 Bridge JSON 前校验 schema 字符串（`DreamShader.MaterialExpressions` / `dreamshader-symbol-index` / `DreamShader.SubstrateBuiltins`）与 `protocol:1`，schema 不符即降级到下一数据源，不崩。

### 6.5 任务卡模板（后续在此文档追加任务时使用）
```
### X#（P?）标题
- 目标：
- 任务：
- 涉及文件：
- 验收标准：
- 上游参考：
```

---

## 7. 关键参考索引

### 7.1 上游文档（相对 `/tmp/dreamshader-ref`）
| 主题 | 路径 |
|---|---|
| V2 语言总览（声明/语句/表达式/命名实参/不支持清单） | `Docs/language-v2/index.md` |
| Substrate 语法糖与 builder | `Docs/language-v2/substrate.md` |
| 材质实例 `.dsi` | `Docs/language-v2/instances.md` |
| 预处理器（`#if`/`#define`/DS_* 内建） | `Docs/language/preprocessor.md` |
| 1.x 源文件规则（方言共存/错位诊断） | `Docs/language/source-files.md` |
| Bridge 协议全表（Requests/Responses/status/reveal-node/preview WS） | `Docs/tools/bridge.md` |
| 符号索引 schema | `Docs/api/lang-module.md` |
| 诊断码（617 码） | `Docs/diagnostics/`（`index.md`、`DSH9xxx.md`、每码一页） |
| 迁移工具 | `Docs/tools/migrate.md` |
| 反编译器 | `Docs/tools/decompiler.md` |
| 材质设置键 | `Docs/settings/material.md` |
| New 菜单模板 | `Resources/Templates/`、`Docs/tools/material-browser.md` |

### 7.2 官方平台文档（工作流 D 依据，2026-10-03 审查）
| 主题 | 链接 |
|---|---|
| Custom Language Support 总览（特性清单/教程树，含 surround-with、code-inspections-and-intentions、symbols/stub-indexes、polysymbols、LSP 等子主题） | <https://plugins.jetbrains.com/docs/intellij/custom-language-support.html> |
| Additional Minor Features（25 项小特性；D 线主要来源） | <https://plugins.jetbrains.com/docs/intellij/additional-minor-features.html> |
| PSI（elements/files/view-providers；跨文件符号见 CLS 的 symbols/go-to-symbol 主题） | <https://plugins.jetbrains.com/docs/intellij/psi.html> |
| Language Injection（MultiHostInjector / LanguageInjectionSupport；D7 依据） | <https://plugins.jetbrains.com/docs/intellij/language-injection.html> |

### 7.3 本插件代码锚点
| 主题 | 文件 |
|---|---|
| 文件类型/扩展名 | `src/main/kotlin/.../language/core/DreamShaderFileType.kt`、`META-INF/plugin.xml` |
| 方言判定落点（新增） | `src/main/kotlin/.../language/core/`（建议新文件 `DreamShaderDialect.kt`） |
| 现有 catalog 合并链 | `src/main/kotlin/.../language/editor/DreamShaderMaterialExpressionManifest.kt` |
| Bridge 路径/命令/诊断 | `src/main/kotlin/.../language/bridge/` |
| 语义管线（V1，冻结参照） | `src/main/kotlin/.../language/diagnostics/DreamShaderSemanticAnnotationPipeline.kt` |
| 导入解析（1.8 source-root 语义） | `src/main/kotlin/.../language/packages/DreamShaderImportResolver.kt` |
| 上游示例 fixture 测试 | `src/test/kotlin/.../integration/DreamShaderUpstreamExamplesTest.kt` |
