# M15 计划：重构扩展与方言精修（Refactoring & Dialect Refinement）

- **制定日期**：2026-10-06
- **基线**：本插件 2.2.0（2026-10-05，M14 完成）；上游 DreamShader 2.0.2 @ `b3bbf48`
- **来源**：`docs/agent-development-plan.md` 的 M15 候选（M14 顺延项）：Extract Function / Inline Uniform（复用 C20 handler 地基）、`.dsh` 混合方言逐声明分段补全、表达式推断后续切片
- **计划状态**：已完成（2026-10-06 两批落地，见 §5 执行记录，版本 2.3.0）。任务卡并入 `docs/agent-development-plan.md` 作为 M15。

---

## 1. 现状研究

### 1.1 重构能力现状（C20 地基盘点）

`DreamShader2ExtractVariableHandler`（`refactoring/`）已建立可复用的骨架：

- **目标手势**：选区精确覆盖（`coveringExpression`）/ 光标尾随（`expressionEndingAt`）两种，均按 2.0 表达式形态节点集判定。
- **锚定与拒绝**：`enclosingStatement` 沿祖先链找最近语句锚点，遇循环体直接拒绝、`if` 臂内按 RPAREN 位置拒绝（改变求值时机的提取一律不做）。
- **类型来源**：C17 推断 confident → 拼写反推（Scalar 按族 / Vector-Matrix 拼维度 / Texture 取 kind / Struct 取名）→ 构造器/转换显式 TYPE 词兜底 → 拒绝。
- **写入与模板**：`WriteCommandAction` 内 document 插入 + 替换，`TemplateBuilderImpl` 双位置联动就地重命名。

缺口：Introduce 家族只有 Extract Variable。**Extract Function**（语句集 → 新函数 + 调用替换）与 **Inline**（内联值替换 + 声明删除）完全缺失。上游语法已确认 V2 支持 `export`/`extern`/裸函数声明、参数 `in`/`out`/`inout` 修饰与默认值（`DreamShader2PsiParser.PARAMETER_MODIFIERS` = `in`/`out`/`inout`），Extract Function 的签名合成有完整语法支撑。

**Inline Uniform 的语义**：uniform 是文件级外部输入，带默认值初始化器。内联 = 把初始化表达式文本替换进全部使用处并删除声明。风险面与 C5 rename 安全网完全同构——`.dsi` 覆盖文件会因声明消失而悬挂（override 名不再被 schema 认可，由 `DreamShader2InstanceOverridePass` 报错）；跨文件使用（本文件被 `#include`）会让其他文件引用悬空。`DreamShaderRenameImpactChecker.impactsOf` 已能列出 OVERRIDE/CALL_SITE 影响，`DreamShader2ReferencesSearchExecutor` 提供轻量引用查询——影响面判定不需要新地基。

### 1.2 混合 `.dsh` 补全分发的现状

方言判定与门控（`DreamShaderCompletionSupport.DreamShaderCompletionDialects`，M13 C8）：

- `detect` 返回 `HEADER_MIXED` 时 `servesV1 == true`、`servesV2 == false`——**混合 `.dsh` 整文件由 1.x contributor 服务**（首批落地的保守决策，见 M13 执行记录「实现决策与偏差」）。
- 混合文件实际由 **2.0 解析器解析**（`routesToV2` 对任意 V2 标记为真），legacy 区段在 2.0 PSI 中是 **opaque `LEGACY_BLOCK` 节点**（`DreamShader2PsiParser` 捕获 `Shader(...) { ... }` / `Namespace Tools { ... }` 等整块）。V2 声明（uniform/global/struct/function/pragma/include/doc）是真实节点。
- 也就是说：PSI 树上**每个 offset 都可以判定它落在 legacy 区段还是 V2 声明内**，逐声明分段补全的数据源已经就位，只差消费：
  - 1.x contributor 的 gate 是文件级的（`DreamShaderCompletionContributor.kt:1792`），suggester 拿得到 `parameters.offset` 但不做区域判断；
  - 2.0 contributor 同样文件级拒绝混合文件（`servesV2` 为假）；
  - typed handler 的 `isV2File` 判定（`:1920`）同样是文件级，混合文件里 V2 词法弹出规则（C12）不生效，1.x 分析器规则全文件生效。

### 1.3 类型推断现存缺口（C17 之后）

1. **局部名解析无词法作用域**：`localDeclaredTypeIn`（`DreamShader2TypeInference.kt:263`）按「文本上先于 use site」接受任意块深度的 `VARIABLE_DECLARATION_STATEMENT`——早前 `if` 臂内声明的同名局部（use site 之后已出作用域）会**错误地**为后续使用提供自信类型，制造 mismatch 假阳性（违反保守红线的精神：宁可 Unknown 不误报）。
2. **操作数型内建覆盖面窄**：`callType` 的内建表只有 `lerp`/`saturate`/`clamp`/`abs` 走首参类型；`normalize`/`min`/`max`/`floor`/`ceil`/`round`/`frac` 等同族操作数型内建全部 Unknown，`dot`（→标量）与 `cross`（→三维向量）也未收口——这些是 swizzle/成员补全的高频入口。
3. **用户函数调用的实参无类目检查**：`annotateAssignmentCategoryMismatches` 覆盖赋值/声明初始化，但调用点实参（如 `Helper(1.0)` 而 `Helper(float3 v)` 声明在案）不做任何检查——形参类型是现成的自信来源，参数流到调用点只差消费。

---

## 2. 目标与非目标

**目标**
1. 2.0 重构家族补齐第二批：Extract Function（语句集提取）与 Inline Uniform（保守影响面下的内联），全部走 C20 的「不确定即拒绝、绝不产生半成品」契约。
2. 混合 `.dsh` 的补全按 caret 所在声明方言分段：legacy 区段 1.x 服务、V2 声明内 2.0 服务、顶层两方言并存（混合文件的真实语义），纯文件行为零变化。
3. 推断三件小切片：词法作用域收紧（修假阳性）、操作数型内建扩展、调用点实参类目检查（参数流消费）。

**非目标**
- 不做 Inline Variable / Inline Function（extract 家族先行，inline 家族先取影响面最可控的 uniform）。
- 不做「返回值智能猜测」：Extract Function 第一批全部用 `out` 参数带回选中区写、区后读的变量，不做单一返回值启发。
- 不做 `.dsh` 内逐声明分段的**解析器**改造（解析侧已由 V2 解析器 + LEGACY_BLOCK 捕获承担，本轮只动服务分发）。
- GLSL 别名提示解封继续 blocked（依赖上游 legacy alias 清单确认）。

---

## 3. 任务卡

优先级：P1 = 用户可见能力补齐；P2 = 修正与加固。

### C21（P1）Extract Function（2.0 重构第二批）
- **目标**：`.dss` 函数体内选中一个或多个完整语句 → 提取为文件级裸 helper 函数，原位替换为调用。
- **任务**：
  1. 新增 `DreamShader2ExtractFunctionHandler`（`RefactoringActionHandler`，注册 `refactoring.extractHandler` 与 EditorRefactoringActionAction 可达路径）；选区必须精确覆盖**完整语句集**（首尾语句边界对齐 PSI），落在单一 V2 函数体 BLOCK 内；`CUSTOM_BODY`（`@custom` 逐字捕获体）与 1.x 文件静默拒绝。
  2. 入参合成：选区内 `REFERENCE_EXPRESSION` 标识符 → 在**选中起点之前**可见的自由名（函数参数 + 按 C24 作用域规则的局部 + 文件级 uniform/global）→ 按首次出现序合成 `Type Name` 形参（类型拼写取声明拼写，声明类型不可知的名字直接拒绝——红线：不猜参数类型）。
  3. 出参合成：选中区内出现在赋值 LHS（`=` 与复合赋值）且**选中区结束之后仍被使用**的名字 → `out Type Name` 形参；按首次赋值序。无出参 → 返回 `void`。
  4. 函数体：选中语句文本按原缩进重排；新函数（裸 `<Type> Name(params) { ... }` 形式，不加 `export`——不改资产面）插入当前函数声明之后，空行分隔；调用点替换为 `Name(args)`（出参按 `Name = ` 命名实参? 否——第一批用位置实参，与入参序一致）。
  5. 拒绝场景（错误提示，文档零改动）：选区含 `return`/`break`/`continue`/`throw`（控制流转移改变语义）；选中区起点不在任何语句边界；选中内声明、选中后使用的局部（提升会改变作用域）；自由名类型不可知；入参名与出参名冲突（同一名字先读后写 → `inout`，第一批直接拒绝，保持矩阵简单）。
  6. 就地重命名：新函数名 `extracted` 基名 + 去重，`TemplateBuilder` 联动声明处与调用点。
- **验收**：平台测试文档 diff 断言：单语句提取（读入参）、多语句提取、写出参（区后使用）、无自由变量零实参；拒绝矩阵（return、跨函数边界、部分语句、`@custom` 体、内声明外使用、类型不可知入参）；1.x 文件静默；调用点与新函数签名的文本断言。
- **涉及文件**：新 `refactoring/DreamShader2ExtractFunctionHandler.kt`、`plugin.xml`、bundle（en/zh）、新测试。

### C22（P1）Inline Uniform（2.0 重构第三批）
- **目标**：`.dss`/`.dsi`（`.dsi` 无 uniform 声明，实际 `.dss`）中把带初始化器的 uniform 内联进当前文件全部使用处并删除声明。
- **任务**：
  1. 新增 `DreamShader2InlineUniformHandler`（`inlineRefactoring` 家族注册或独立 action id）：caret 在 uniform 声明名或其使用处标识符上触发；目标解析回声明节点（使用处经 `DreamShader2FileAnalysis.uniforms` 的名字匹配）。
  2. 预检（全部通过才写，任一失败即拒绝并列明原因）：
     - 声明带初始化器表达式（`uniform float X = 1.0;` 的 `1.0`）；
     - 初始化器文本不含**其他 uniform 名**（被删声明的初始化器引用其他 uniform 是允许的——文本替换语义成立；但**其他 uniform 的初始化器**引用被删名时会悬空，拒绝）；
     - 影响面：`DreamShaderRenameImpactChecker.impactsOf` 无 OVERRIDE（`.dsi` 覆盖存在 → 拒绝并列出文件）；
     - 引用范围：`ReferencesSearch.search(声明)` 的使用处全部在当前文件（跨文件 → 拒绝并列出文件数）。
  3. 写入：倒序替换全部使用处为初始化器文本（带括号包裹——内联进表达式时保持优先级语义），最后删除声明行（含行尾换行与前置 `///` doc 块，保持行结构完整）。
- **验收**：平台测试：单使用/多使用替换 + 声明删除 + doc 块随删的 diff 断言；拒绝矩阵（无初始化器、`.dsi` 覆盖、跨文件使用、被其他 uniform 初始化器引用）；1.x 文件静默。
- **涉及文件**：新 `refactoring/DreamShader2InlineUniformHandler.kt`、`plugin.xml`、bundle（en/zh）、新测试。

### C23（P1）`.dsh` 混合方言逐声明分段补全
- **目标**：混合 `.dsh` 内 caret 落在 legacy 区段时 1.x 服务、V2 声明内 2.0 服务；纯文件行为零变化。
- **任务**：
  1. `DreamShaderCompletionDialects` 新增区域判定：`servesV1At(file, offset)` / `servesV2At(file, offset)`——文件级结果优先（纯 V2/V1 文件直接返回文件级结论，行为与现状逐字节一致）；仅 `HEADER_MIXED` 走 PSI 区域判定：leaf 向上最近命中 `LEGACY_BLOCK` → V1 区段，命中 V2 声明节点（uniform/global/struct/function/pragma/include/doc block）→ V2 区段，两者皆无（顶层空白区）→ **双服务**（混合文件顶层两种声明都合法，两边各出各的顶层关键词，保守且符合文件语义）。区域判定结果按文件 PSI `CachedValue`（区间列表）缓存，不逐 offset 重扫。
  2. 1.x contributor gate（`:1792`）改 `servesV1At(file, parameters.offset)`；2.0 contributor gate 对称改 `servesV2At`。
  3. typed handler：`isV2File` 改为 `servesV2At(file, caret)`——V2 区段走 C12 词法规则；legacy 区段走 1.x 分析器（现状）；顶层双服务区按 1.x 规则保守（`(` 弹窗两方言通用）。
- **验收**：混合 `.dsh` fixture 测试：legacy 函数体内 Ctrl+Space 无 `uniform`/`export`；V2 函数体内无 `Shader`/`namespace`；顶层两方言关键词并存；纯 `.dss`/`.dsm` 由现有 35 例补全测试与门控矩阵回归锁定（零改动预期）。
- **涉及文件**：`DreamShaderCompletionSupport.kt`、两个 CompletionContributor、`DreamShaderCompletionContributor.kt`（typed handler）、新测试。

### C24（P2）表达式推断切片三件
- **目标**：修一个假阳性、扩两类自信来源；红线不变（confident 才产出，Unknown 静默）。
- **任务**：
  1. **词法作用域收紧**：`localDeclaredTypeIn` 只接受 use site **祖先块链**上的声明（`PsiTreeUtil.getParentOfType` 链判定：声明语句的最近 BLOCK 祖先必须出现在 use site 的祖先链上，且 `for`/`while` 头内声明仅对其体内可见）——修复「早前出作用域的同名局部提供假自信类型」。
  2. **操作数型内建扩展**：`callType` 内建表扩至 `normalize`/`min`/`max`/`floor`/`ceil`/`round`/`frac`/`rcp`（首参类型，HLSL 逐分量语义）；新增 `dot`（→操作数族标量）、`cross`（→同族 3 向量，双操作数皆自信 3 向量才产出）。未列出的内建继续 Unknown。
  3. **调用点实参类目检查**：声明可解析的用户函数调用（本文件 → include 闭包 → workspace 导出表，与 `userFunctionReturnType` 同一解析序），实参表达式自信且与形参声明类目冲突（数值 vs bool、Substrate vs 数值、material vs 其他——复用既有类目表语义）→ warning；任一侧 Unknown 静默；`inout`/`out` 形参同规则；命名实参按名对位。类目定义与 `reportMismatch` 共用，文案复用 mismatch 家族（新 key，warning 级）。
- **验收**：`DreamShader2TypeInferenceTest` 新增作用域正反例（if 臂声明/for-init 可见性）与内建族矩阵；`DreamShader2DiagnosticsPass` 侧新增参数 mismatch 正例 + Unknown 静默 + 命名实参对位用例；全量零回归。
- **涉及文件**：`DreamShader2TypeInference.kt`、`DreamShader2DiagnosticsPass.kt`、bundle、对应测试。

### T15（贯穿）测试与文档
- **任务**：各任务卡验收用例；`docs/language-v2-baseline.md` 增 M15 覆盖映射（重构家族/混合方言分发/推断切片）并更新 Test Mapping；`agent-development-plan.md` 追加 M15 段与执行记录、列 M16 候选；CHANGELOG（en/zh）新增 2.3.0 段；README plugin-metadata 同步重构描述；版本号 2.3.0。
- **验收**：`gradlew.bat test` 全绿；文档与实现互检。

**建议实施顺序**：C23 → C24（批次 1：小而自洽的修正，先解除混合方言补全的错位）；C21 → C22 → T15（批次 2：重构线，C21 先于 C22 因为其作用域/可见性判定与 C24.1 共享逻辑）。

---

## 4. 风险与依赖

- **依赖**：C21 依赖 C24.1 的作用域规则（先落 C24.1 或同步实现共享辅助）；C22 依赖 C5 影响面检查与轻量引用搜索（均就绪）；C23 依赖 V2 解析器的 LEGACY_BLOCK 捕获（已就绪，M8 产物）。
- **风险**：
  - C21 的数据流矩阵（读/写/区后使用）是本轮最复杂面——判定一律保守拒绝优先（红线 1），拒绝路径与 C20 同为错误提示 + 零文档改动。
  - C22 删除声明是破坏性写——预检五项全部通过才进 `WriteCommandAction`，预检在任何不确定时拒绝；`.dsi` 覆盖悬挂由上游诊断兜底但不依赖它（预检前置）。
  - C23 顶层双服务可能在混合文件顶层出现两方言关键词并存的弹窗——这是混合文件的真实语义（upstream 按声明判定方言），在 baseline 文档中明确记录。
  - C24.3 是新告警面——仅 confident×confident 触发、warning 级，与既有 mismatch 家族一致；若上游实参隐式转换比预期宽松，由「保守漏报」原则兜底（数值族间 float↔half 不告警）。
- **验证命令**：`.\gradlew.bat test --no-configuration-cache`。

---

## 5. 执行记录

### 2026-10-06 两批落地——M15 完结，版本 2.3.0

**批次 1（C23/C24，提交 `2086145`）**：

- C23：`DreamShaderCompletionDialects` 新增 `Region`（V1/V2/BOTH）与 `regionAt`/`servesV1At`/`servesV2At`——区域索引（顶层 `LEGACY_BLOCK` 范围 vs V2 声明范围）按文件 PSI CachedValue 缓存；纯文件在所有偏移返回文件级结论。1.x Contributor gate、2.0 Contributor gate、typed handler 的 `isV2File` 判定三处改 offset 感知（V2 区域走 C12 词法规则、legacy/顶层走 1.x 分析器）。混合 `.dsh` 顶层 BOTH：两方言的关键词集并存（混合头文件顶层合法地容纳两种声明，baseline 已记录）。
- C24.1：`localDeclaredTypeIn` 收紧为词法作用域——声明的 scope owner（最近 BLOCK，for-init 则为 `FOR_STATEMENT`）必须同时是使用点的祖先；近声明优先（遮蔽按最内层解析）。
- C24.2：`callType` 内建表扩至 `normalize`/`min`/`max`/`floor`/`ceil`/`round`/`frac`（首参类型）+ `dot`（等形状数值 → 更宽族标量）+ `cross`（同族 3 向量）。
- C24.3：`DreamShader2DiagnosticsPass.annotateCallArgumentMismatches`——调用实参对照被调形参声明类目（本文件 → include 闭包；workspace 导出层无形参形状不检查），命名实参按名对位（大小写不敏感），实参数超形参数静默（默认值/重载不可见）；新 bundle 键 `diagnostic.dsh2.argumentTypeMismatch`（en/zh）。
- 测试：`DreamShader2CompletionTest` +1（混合方言区域矩阵 + 纯文件整文件答案）；`DreamShader2TypeInferenceTest` +2（作用域正反例四组、内建族六组）；`DreamShader2SubstrateDiagnosticsTest` +1（位置/命名实参告警、同类目与 Unknown 静默）。全量 784 例零失败。

**批次 2（C21/C22，提交 `b72b32c`）**：

- C21：新 `refactoring/DreamShader2ExtractFunctionHandler.kt`——选区必须恰好覆盖同块内的完整语句（首语句起点对齐、末语句终点对齐）；数据流分析（读取 = 非写目标的 REFERENCE 集；写入 = 赋值 LHS/`++`/`--` 目标/声明初始化器；控制流转移与成员/索引写入拒绝）；签名合成（值参数 = 读取且解析为声明的名字，取声明拼写；`out`/`inout` = 写入且区后使用，读过即 `inout`；函数名与命名空间根保持原样）；新函数（裸 `void` 形式）插入当前函数之后，语句按原相对缩进重排到 4 空格基准；`ExtractMethod` 平台动作经 `getExtractMethodHandler()` 接线；TemplateBuilder 双位置联动。
- C22：新 `refactoring/DreamShader2InlineUniformHandler.kt` + `DreamShaderInlineUniformAction`（`DreamShader.InlineUniform` 注册进 `RefactoringMenu`）——目标解析（声明名或使用处 → `DreamShader2FileAnalysis.uniforms` 名字匹配）；五项预检全过才写：单声明器、有初始化器、无其他初始化器读取、无 `.dsi` 覆盖（`DreamShaderRenameImpactChecker.impactsOf`）、无跨文件使用（`ReferencesSearch`）；写入 = 使用处倒序替换为带括号初始化器 + 声明连同前置 doc 块与行尾整行删除。
- 测试：`DreamShader2ExtractFunctionTest` 9 例（单语句 in/out、多语句零参、`inout` 判定、平台动作接线、拒绝矩阵四组、1.x 静默）；`DreamShader2InlineUniformTest` 7 例（内联 diff、菜单动作接线、拒绝矩阵四组、1.x 静默）。全量 800 例零失败。

**实现决策与偏差**：

- C21 出参限定词实现了 `inout` 而非按计划卡拒绝「先读后写」——`x = x + 1` 用 `out` 在 HLSL 中读未定义初值，`inout` 才是正确拼写，且实现成本相同；计划卡的保守意图由「类型不可知即拒绝」承担。
- C21 对解析为函数或命名空间根（`UE.`）的自由名保持原样而非拒绝——为它们合成参数既不必要也会改变调用形态；未解析名的语义由既有诊断兜底。
- C22 限定 `.dss`（`.dsi` 的 uniform 是覆盖声明，内联即删覆盖，超出本批范围）；多声明器 uniform 拒绝（逐声明器初始化器提取留 M16 与 Inline Variable 一并考虑）。
- C23 顶层 BOTH 策略使混合文件顶层弹窗同时出现两方言关键词——这是混合头文件的真实语义（上游按声明判定方言），已写入 baseline 的 M15 映射表。
- 平台 `getExtractMethodHandler()` 覆写即被 `ExtractMethod` 动作调用（无元素感知重载需求），与 C20 的 `IntroduceVariable` 双重载模式不同。

**任务卡状态**：C21–C24、T15 全部完成，M15 收官（`build` 通过，全量 800 例零失败）。后续方向见 `agent-development-plan.md` M16 候选。
