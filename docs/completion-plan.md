# 补全智能化优化计划（M13 · Completion Intelligence）

- **制定日期**：2026-10-05
- **基线**：本插件 2.0.0（2026-10-04，M12 完成）；上游 DreamShader 2.0.2 @ `b3bbf48`
- **触发问题**（用户反馈）：「按下 `.` 之后给出的建议是乱的，尤其是新语言（2.0 方言）支持」
- **计划状态**：执行中（2026-10-05 首批落地，见 §6 执行记录）。任务卡并入 `docs/agent-development-plan.md` 作为 M13，版本目标 2.1.0。

---

## 1. 现状研究

### 1.1 补全入口与数据源盘点

| 入口 | 注册点 | 数据源 | 方言门控 |
|---|---|---|---|
| `DreamShaderCompletionContributor`（1.x） | `completion.contributor`，`PlatformPatterns.psiElement()` | 1.x 上下文分析器（PSI→再解析→lexer 三级回退）、settings 键值表、`Base.*` 输出成员、`UE.*`/`Substrate.*` catalog（manifest 合并链）、HLSL 内建、import 路径（FilenameIndex + 包根）、局部符号（正则） | **无**——只要 `file.language == DreamShaderLanguage` 就运行，包括 `.dss`/`.dsi` |
| `DreamShader2CompletionContributor`（2.0，M12 C3） | `completion.contributor`，`PlatformPatterns.psiElement()` | catalog 命名实参、C2 类型推断（仅 `Substrate` 接收者）、Bridge 符号索引（`.dsi` 父参数）、`#pragma material` 键值 | 按扩展名（仅 `.dss`/`.dsi`） |
| `DreamShaderPlainTextSymbolCompletionContributor` | `completion.plainTextSymbol` | `DreamShaderPsiUtil.declarationsIn`（1.x 声明模型） | 1.x——`.dss` 中无产出 |
| `DreamShaderCompletionTypedHandler` | `typedHandler` | 按 `.`/`(`/`:`/`=`/`"` 触发自动弹出，判定用 **1.x 上下文分析器** | 无 |

关键事实：五个扩展名共用同一个 `DreamShaderLanguage` 实例，方言（`DreamShaderDialect`）只是文件级提示，**两个补全 Contributor 在任何 DreamShader 文件里都会同时收到调用**。

### 1.2 「按 `.` 后建议混乱」根因（按影响排序）

1. **方言未分流**：1.x Contributor 没有 dialect gate。`.dss` 文本经 1.x 分析器解析后没有 `SECTION`/`DECLARATION` 节点 → `isTopLevel == true` → 在 `.dss` 里按 `UE.`、`S.`、`x.` 之后，1.x Contributor 依然贡献 1.x 顶层关键词（`Shader`、`ShaderFunction`、`namespace`、`import`…，`DreamShaderCompletionContributor.kt:902-913`）等完全无关条目，与 2.0 条目混在同一弹窗。
2. **2.0 按点覆盖面过窄**：`DreamShader2CompletionSuggester.suggest` 只覆盖 4 个上下文（命名实参 / Substrate builder 成员 / `.dsi` uniform 名槽位 / `#pragma material` 键值）。向量 swizzle、struct 字段、矩阵行、材质属性、`UE.`/`Substrate.` 命名空间前缀（2.0 文件内）、`#include "..."` 路径全部没有实现——C2 类型推断（`DreamShader2TypeInference.kt:28` 起，`typeOfName`/`structFields` 已就绪）并没有被消费到成员补全上。落空后弹窗被 1.x 关键词 + 平台 word 补全填满，即「乱」的直接来源。
3. **优先级缺失导致排序颠倒**：1.x 条目全部包 `PrioritizedLookupElement`（优先级 20–90），2.0 条目裸 `addElement`（默认 0）。即使 Substrate pin 出现，也永远排在 1.x 高优先级噪声之后——**相关项沉底，无关项置顶**。
4. **命名实参建议的位置判定过宽**：`namedArgumentCompletions` 只要 `ARGUMENT_LIST` 是祖先就触发（`DreamShader2CompletionContributor.kt:119`），不区分「参数名槽位」与「值位置/嵌套表达式」。`UE.LinearInterpolate(Tex.|)` 会建议 `B = ` 这类剩余实参而不是 `Tex` 的成员；建议链把命名实参放在第一位，进一步压住了其他上下文。
5. **`.` 自动弹出在 2.0 文件失效**：`DreamShaderCompletionAutoPopup.shouldAutoPopup`（`DreamShaderCompletionContributor.kt:1723`）用 1.x 分析器判定 `.` 是否弹窗，要求 `isInSectionBody || isFunctionLikeDeclaration`——2.0 文件两者皆 false，**在 `.dss`/`.dsi` 中按 `.` 根本不弹窗**（Ctrl+Space 才有，且内容如第 1 条所述）。
6. **每次补全的重复计算**：1.x suggest 每次调用全文件再解析一次（`analyzeWithParsedText`，`:106-115`）、`FilenameIndex.getAllFilesByExt` 扫描三种扩展名（`:1458-1465`）、import 闭包解析与 `collectDeclaredCallables` 各跑两遍（`:1561/:1582`）、`substrateWrittenMembers` 对全文件 `PsiTreeUtil.collectElements`（`DreamShader2CompletionContributor.kt:207`）。大文件下补全延迟明显。

### 1.3 其他相关缺口（顺带确认）

- **2.0 无基础标识符补全**：`.dss` 中裸 Ctrl+Space 只有 1.x 顶层关键词 + 文档 word 匹配；locals、uniform、export 函数、struct 名一个都没有，而 M11 的四个 stub 索引和 `DreamShader2WorkspaceSymbols` API 已可直接消费。
- **`#include "path"` 无补全**（1.x `import "..."` 有）：2.0 用 `#include`，补全/自动弹出/引号处理都是 1.x 专属。
- **`.dsi` 覆盖值无类型感知补全**：父 schema（Bridge `instance.schema`）只用于名字建议，值（静态开关 true/false、颜色、`Path(...)` 资产）没有建议。
- **字符串内插入引号语义不一致**：2.0 pragma 值 `insertText = "\"$value\""`（`:307-309`）在用户已键入开引号时会产生 `""Surface"` 双引号叠加；1.x settings 值则裸插入、由用户自补引号。
- **2.0 条目无图标**、与 1.x 条目视觉不统一（1.x 有 `DreamShaderIcons` 分类图标）。
- **1.x 非命名空间接收者的 `.`**（graph 上下文）会倒出全量「大杂烩」（UE. + 各命名空间 + locals + 34 个 HLSL 内建 + 全部类型构造器），没有任何成员语义。

---

## 2. 目标与非目标

**目标**
1. 按 `.` 的弹窗内容由接收者类型与上下文决定：类型已知 → 只给该类型的合法成员（swizzle/字段/命名空间成员/pin），置顶且带结果类型；类型未知 → 给受控的降级列表（命名空间 + 函数 + locals），绝不再出现跨方言关键词。
2. 两个 Contributor 按方言互斥，2.0 条目进入与 1.x 相同的优先级体系。
3. 2.0 文件补全的基本盘补齐：标识符、`#include` 路径、`.dsi` 覆盖值。
4. 补全热路径去重计算，大文件补全延迟进入既定性能预算。

**非目标**
- 不重写 C2 类型推断（保守 Unknown 语义保留）。
- 不动 1.x 已验证的上下文（`Base.*`、settings、declaration head、import）行为，只加方言门控与降级收紧。
- Bridge 缺失时的静默降级哲学不变（离线兜底表保留）。

---

## 3. 任务卡

优先级：P0 = 直接决定「按点不乱」；P1 = 2.0 补全基本盘；P2 = 体验与性能加固。

### C8（P0）方言分流与补全门控
- **目标**：同一位置只由匹配方言的 Contributor 产出条目。
- **任务**：
  1. 1.x Contributor 加 dialect gate：`DreamShaderDialects` 判定 V2 文件（`.dss`/`.dsi` 及带 V2 标记的 `.dsh`）直接 return；`.dsh` 内 1.x 区段维持现状。
  2. 2.0 Contributor 反向 gate：非 V2 扩展名 return（现状已有，补测试锁定）。
  3. 上下文分析器提供方言感知入口（1.x 文本分析只对 V1 文本调用）。
- **验收**：`.dss`/`.dsi` 中按 `.`/Ctrl+Space 的结果不含 `Shader`、`ShaderFunction`、`namespace`、`import` 等 1.x 顶层关键词；1.x 文件结果不含 `uniform`/`export`（回归测试锁定双向）。
- **涉及文件**：`DreamShaderCompletionContributor.kt`、`DreamShader2CompletionContributor.kt`、`DreamShaderCompletionContextAnalyzerTest`。

### C9（P0）2.0 按点成员补全矩阵（核心）
- **目标**：接收者类型驱动的成员补全，消费 C2 推断结果。
- **任务**（在 `DreamShader2CompletionSuggester` 内按接收者类型分发）：
  1. `Vector` → swizzle：按分量数生成合法组合（`xyzw`/`rgba`/`0..3` 序号族，可重复如 `xyxy`），tail 标注结果类型（`float2`/`float3`…），最高优先级。
  2. `Matrix` → `_m00` 行列族 / 行 swizzle（按基线文档确认拼写后实现）。
  3. `StructType(name)` → `structFields(typeName)`（M11 已有 API，含 include 闭包）。
  4. `Texture` → `Sample(...)`/`SampleLevel(...)` 成员（签名来自 catalog/类型库）。
  5. `Material` → 材质属性成员（`FrontMaterial` 等，M11 material-attribute typing 已覆盖）。
  6. `Substrate` → 现有 pin 列表保留，改为按 entry（`Substrate.Slab` 参数 + 兜底表）去重并带类型。
  7. 接收者为命名空间拼写（`UE`/`Substrate`/其他 catalog 命名空间）→ catalog 成员补全（把 1.x `namespaceMemberPrefix` 的 catalog 逻辑移植到 2.0 Contributor，摆脱 1.x `isGraphLikeContext` 前置条件）。
  8. 类型 `Unknown` → 降级列表：catalog 命名空间 + import 闭包内可调用项 + 本文件 locals；不倒关键词全量。
- **验收**：平台测试矩阵覆盖「接收者类型 × 期望成员」≥ 8 组（float3→swizzle、自定义 struct→字段、Substrate→pin、UE.→catalog、Unknown→降级），每项断言 tail/typeText 与优先级区间。
- **涉及文件**：`DreamShader2CompletionContributor.kt`、`DreamShader2TypeInference.kt`（只读消费）、`DreamShader2CompletionTest.kt`。

### C10（P0）统一优先级与去重
- **目标**：两个 Contributor 的条目在同一优先级体系内排序。
- **任务**：
  1. 制定并文档化统一优先级表（成员/swizzle 88–95，命名实参 76–80，catalog 命名空间 70–78，可调用 65，类型 35，关键词 40，局部 20，降级项 ≤ 15——对齐 1.x 现有 0–100 表）。
  2. 2.0 条目全部包 `PrioritizedLookupElement`；补 icon（uniform/function/struct/namespace/keyword 分类复用 `DreamShaderIcons`）。
  3. 跨 Contributor 同 label 去重（2.0 结果与 1.x 残余合并时保留 detail 更丰富者）。
- **验收**：混合弹窗中 Substrate pin/swizzle 排在 1.x 残余关键词之上；`DreamShader2CompletionTest` 断言优先级。
- **涉及文件**：两个 Contributor、新增 `DreamShaderCompletionPriorities.kt`（单一事实来源）。

### C11（P1）命名实参上下文收紧
- **目标**：命名实参只在「参数名槽位」出现。
- **任务**：
  1. 仅当光标位于空实参位（`(` 后、逗号后、实参开头前缀）时建议 `Name = `；值位置、嵌套成员访问、字符串内不触发（交给 C9 的成员补全）。
  2. `Name = ` 之后按 catalog 参数类型给值补全（枚举串、`Path(...)` 模板、字面量占位），先行只做 catalog 已标注枚举的参数。
- **验收**：`UE.LinearInterpolate(Tex.|)` 不再出现命名实参；`UE.LinearInterpolate(` 后剩余 pin 置顶；`Source = "` 后出现枚举值。
- **涉及文件**：`DreamShader2CompletionContributor.kt`（`namedArgumentCompletions`）。

### C12（P1）`.` 自动弹出修复（方言感知）
- **目标**：按 `.` 在两种方言的正确上下文中都能弹窗。
- **任务**：
  1. `shouldAutoPopup` 增加方言分支：V2 表达式上下文（函数体/初始化器/命名实参值位置）按 `.` 弹出；判定走 `DreamShader2InferenceContext` 的轻量入口或 PSI 元素类型（`MEMBER_ACCESS_EXPRESSION` 祖先），避免全文件再解析。
  2. `.dsi` uniform 名槽位、`#pragma material(` 内 `=`/`"` 的弹出行为补齐。
- **验收**：`.dss` 函数体内 `x.` 自动弹窗且首项为 swizzle/成员；`.dsm` 现有弹出行为回归不变。
- **涉及文件**：`DreamShaderCompletionContributor.kt`（`DreamShaderCompletionAutoPopup`、TypedHandler）。

### C13（P1）2.0 基础标识符补全
- **目标**：`.dss` 裸 Ctrl+Space 有真实符号。
- **任务**：BASIC 补全在 2.0 文件给：本文件 locals/uniform（含 `/// @` 属性）、本文件与 import 闭包内的 export/extern 函数（签名 tail）、struct 名（stub 索引）、类型构造器与 HLSL 内建（低优先级）；Goto Symbol contributor 同步支持 2.0 声明（若仍 1.x-only）。
- **验收**：`.dss` 中输入 uniform 名前缀、export 函数前缀、struct 前缀均有结果且带类型文本；1.x 文件回归不变。
- **涉及文件**：`DreamShader2CompletionContributor.kt`、`navigation/`（Goto Symbol）。

### C14（P1）`#include "..."` 路径补全
- **任务**：`importCompletionPrefix` 增加 `#include "..."` 形态（V2 文件）；候选复用现有项目/包根解析器与 `DreamShaderFileIncludeProvider` 语义；`"` 自动弹出对 `#include` 生效。
- **验收**：`.dss` 中 `#include "` 弹出相对路径与包名，插入含路径补全（`replacementStartOffset` 路径已验证的模式）。
- **涉及文件**：`DreamShaderCompletionContributor.kt`、2.0 suggester。

### C15（P2）性能加固
- **任务**：
  1. 上下文分析对补全路径复用 `parameters.originalFile` PSI（方言感知 parser），V1 文本再解析仅作 lexer 级回退。
  2. `collectDeclaredCallables` 与 import 闭包每轮只算一次（两处调用合并）；候选集按 PSI modification count 缓存（沿用 2026-06 诊断缓存先例）。
  3. `FilenameIndex` 扫描改为懒加载/缓存（import 前缀命中才收集）。
  4. `substrateWrittenMembers` 限定在接收者所在函数体 PSI 内。
- **验收**：大文件补全 smoke（沿用 `DreamShaderLargeFilePerformanceSmokeTest` 模式）补全调用耗时进入预算；功能测试零回归。
- **涉及文件**：`DreamShaderCompletionContributor.kt`、`DreamShader2CompletionContributor.kt`。

### C16（P2）1.x 按点降级收紧
- **任务**：graph 上下文中非命名空间接收者的 `.` 不再输出全量大杂烩：去掉 UE./命名空间/类型构造器的无条件倾倒，保留可调用项 + locals（低优先级），`Base.*`/`UE.`/`::` 路径行为不变。
- **验收**：`.dsm` graph 中 `myVar.` 的结果条目数显著下降且不出现 `UE.`；`UE.` 显式输入行为不变（回归锁定）。
- **涉及文件**：`DreamShaderCompletionContributor.kt`（suggester graph-like 分支）。

### A10（P1）`.dsi` 覆盖值类型感知补全
- **任务**：父 schema 类型驱动：静态开关 → `true/false`（含 `@static` 语义）、数值 → 占位 + slider 范围提示、颜色 → hex/构造器模板、texture/sampler → `Path(...)` 资产模板；与 DSH7259 类型检查共用 schema 读取。
- **验收**：`.dsi` 中 `uniform StaticSwitch Test = ` 后给 true/false；类型不匹配的值不出现在建议中（schema 有效时）。
- **涉及文件**：`DreamShader2CompletionContributor.kt`、`DreamShaderSymbolIndexRepository.kt`（只读）。

### D9（P2）字符串插入与引号规范化
- **任务**：统一的「字符串上下文插入」策略（检测光标是否在未闭合引号内，裸值插入，尾引号交给 QuoteHandler/自动补对），消除 2.0 pragma 值双引号叠加；1.x/2.0 行为对齐。
- **验收**：平台测试覆盖「已键入开引号 / 未键入」×「选值」的插入结果矩阵。
- **涉及文件**：两个 Contributor 的 InsertHandler、`DreamShaderQuoteHandler.kt`。

### T13（贯穿）测试与文档
- **任务**：
  1. `DreamShader2CompletionTest` 扩为「上下文 × 接收者类型」矩阵（每任务卡验收项一一对应）。
  2. 1.x 侧新增方言门控回归测试（V2 文件输入 → 1.x 零条目）。
  3. `docs/language-v2-baseline.md` 补「补全覆盖映射」小节；CHANGELOG 按 M13 归并。
- **验收**：`gradlew.bat test` 全绿；`DreamShader2CompletionTest` 覆盖 ≥ 15 个上下文用例。

**建议实施顺序**：C8 → C10 → C9 → C12 → C11 → C13 → C14 → A10 → C15 → C16 → D9（C8+C10 先落地即可消除「乱」的主观感受；C9 是价值核心）。

---

## 4. M14 候选方向（功能拓展，非本轮范围）

- **表达式级完整类型推断加固**（roadmap 已标记的长期目标）：`?:`、复合赋值、用户函数返回流敏感推断，反哺补全与诊断。
- **2.0 重构**：Extract Variable/Function、Inline Uniform（stub 索引 + rename 安全网已具备地基）。
- **后缀模板 2.0 化**：`.lerp`/`.saturate`/`.sample`/`.swizzle`（postfixTemplateProvider 已有 1.x 先例）。
- **签名帮助增强**：catalog 重载展开、命名实参高亮当前位置（ParameterInfoHandler 升级）。
- **`.dsh` 混合方言补全**：按声明方言分段的上下文分析（当前按整文件判定）。
- **GLSL 别名提示解封**：依赖上游确认 legacy alias 清单（roadmap 现存 blocked 项）。

---

## 5. 风险与依赖

- **风险**：1.x 优先级表是既有用户心智，收紧 C16 可能引起「少了建议」的反馈——保留 Ctrl+Space 二次调用（`invocationCount >= 2` 时放宽为现行为）作为兼容阀。
- **依赖**：C9 依赖 M11 `DreamShader2InferenceContext`/`structFields`（已就绪）；C15 缓存先例来自 2026-06 优化存档（已就绪）；A10/C12 的 `.dsi` 部分在 Bridge 数据缺失时静默降级（不变式）。
- **验证命令**：`.\gradlew.bat test --no-configuration-cache`；沙盒手工验收路径见各任务卡验收标准。

---

## 6. 执行记录

### 2026-10-05 首批落地（C8/C9/C10/C11/C12/C13/C14/C16 + D9 部分）

**已交付**：

- 新文件 `editor/DreamShaderCompletionSupport.kt`：`DreamShaderCompletionPriorities`（统一优先级表）、`DreamShaderCompletionDialects`（方言判定，CachedValue 缓存于文件 PSI，`servesV1`/`servesV2`）、`DreamShaderLookupElementFactory`（单一 lookup 元素构造路径）。
- C8：1.x Contributor 增加 `servesV1` 门控（纯 V2 文件静默）；2.0 Contributor 由扩展名门控升级为 `servesV2`（纯 V2 的 `.dsh` 进入 2.0 服务）。
- C9：`DreamShader2CompletionSuggester` 重写为接收者类型分发——swizzle（xyzw/rgba 全组合、tail 标注结果形状）、矩阵 `_mRC`、struct 字段（`structFields`）、贴图 `sample`、`material` 属性（与 `materialAttributeType` 表同步）、Substrate pin（catalog 类型 + 兜底 + 已写入排除，扫描范围收窄到所在函数体）、`UE.`/`Substrate.`/catalog 命名空间成员（`UE`/`Substrate` 两个内建命名空间无条件可建议）、Unknown 接收者降级为命名空间根 + 可调用项。
- C10：两个 Contributor 全部经工厂输出；1.x 的 19 处优先级字面量替换为共享常量；2.0 条目补齐图标。
- C11：`isParameterNameSlot`（深度感知扫描：`(` 后 / 顶层逗号后为名槽位，值位置、嵌套调用、已闭合列表、字符串内不触发）。
- C12：`shouldAutoPopup` 增加方言分支（V2 词法规则：`.` 需接收者前缀、`(`、`=` 的 pragma 值槽位），V2 路径不再触发 1.x 分析器全文件重解析。
- C13：裸标识符补全（光标前局部变量、函数参数、uniform/global 带类型、struct、文件内 + 工作区导出可调用项、类型构造器、HLSL 内建、方言关键词/指令）。
- C14：`#include "..."` 路径候选（复用 import 候选收集器，扩展名参数化为 `dsh/dss/dsi`）。
- C16：1.x graph 上下文未知接收者按点只给 callables + locals。
- A10：`.dsi` 覆盖值槽位补全，由**声明的类型拼写**驱动（bool → true/false、标量占位、向量/矩阵构造器模板、资源类型静默）——与 DSH7259 把声明类型与父 schema 类型绑定的规则一致，建议按构造匹配 schema 类型，且无 Bridge 数据时可用（与任务卡「schema 驱动」的偏差：声明类型驱动更符合离线降级哲学）。
- D9（部分）：pragma 值引号感知插入（已开引号内裸插入）。
- 测试：`DreamShader2CompletionTest` 扩为 28 例（门控矩阵 + 成员矩阵 + 槽位 + pragma 引号 + include + 基础标识符 + `.dsi` 覆盖值）；`DreamShaderCompletionSuggesterTest` 新增 C16 回归。

**测试修复（2026-10-05）**：`DreamShader2WorkspaceSymbolsTest` 全量运行 3 例失败的问题已修复。根因不是索引污染，而是「静默降级契约 × 测试重试机制」的错配——`DreamShader2WorkspaceSymbols.query` 按架构红线吞掉 `IndexNotReadyException` 返回 Bridge-only 结果，于是 stub 缺席表现为**数据缺失**而非异常；原重试辅助函数只捕获异常（且只包住查询、断言在外），一次都不会重试。单类运行时项目已进入 smart 模式所以恰好通过，全量运行时项目还在 dumb 窗口内即告失败。修复：重试辅助函数改为捕获 `AssertionError` + `IndexNotReadyException` 并把合并视图断言整体移入重试块（等 stub 索引真正可答，预算耗尽后重抛末次失败）。纯测试侧修复，生产静默降级行为不变。连续两次全量 743 例零失败。

**实现决策与偏差**：

- 混合 `.dsh`（V1+V2 混存）维持 1.x Contributor 服务（`detect` 返回 `HEADER_MIXED` 时不切换），避免 V1 区段失去补全；纯 V2 的 `.dsh` 才切入 2.0 服务。逐声明分段的混合补全留 M14。
- C11 任务 2（catalog 参数枚举值补全）与 A10（`.dsi` 覆盖值补全）顺延——值位置当前回落到 C13 标识符补全。
- C15 剩余项（1.x 上下文分析复用 PSI、callables/闭包合并计算、FilenameIndex 缓存）顺延；`substrateWrittenMembers` 的作用域收窄已随 C9 落地。
- D9 的 1.x settings 值路径行为不变（其裸插入语义自洽），仅修复 2.0 pragma 的双引号叠加。

**待办（下一批）**：C15 → C11 任务 2 → D9 收尾。
