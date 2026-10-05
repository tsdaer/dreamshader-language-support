# M16 计划：内联家族与方言分段（Inlining & Dialect Segmentation）

- **制定日期**：2026-10-06
- **基线**：本插件 2.3.0（2026-10-06，M15 完成）；上游 DreamShader 2.0.2 @ `b3bbf48`
- **来源**：`docs/agent-development-plan.md` 的 M16 候选（M15 顺延项）：Inline Variable（复用 C22 预检骨架）、Extract Function 单一返回值启发、`.dsh` 混合方言的结构视图与 inlay 分段、out/inout 写回后的类型收窄、`.dsi` 覆盖值 schema 驱动升级
- **计划状态**：进行中

---

## 1. 现状研究

### 1.1 Inline 地基（C22 骨架盘点）

`DreamShader2InlineUniformHandler`（`refactoring/`）的可复用面：

- **目标解析**：caret 叶子（含回退一位）→ 声明名或使用处标识符 → `DreamShader2FileAnalysis.uniforms` 名字匹配回声明。
- **预检五项**（全过才进 `WriteCommandAction`，任一失败 error hint + 文档零改动）：单声明器、有初始化器、初始化器不被其他文件级初始化器读取、无 `.dsi` 覆盖（`DreamShaderRenameImpactChecker.impactsOf`）、无跨文件使用（`ReferencesSearch`）。
- **写入**：使用处倒序替换为带括号初始化器 → 声明连同前置 `DOC_BLOCK` 与行尾换行整行删除。

**局部变量内联与 uniform 内联的差异面**（C25 的判定核心）：

1. **语义漂移**：uniform 初始化器引用的只有文件级常量输入，替换进任意使用点语义不变；局部初始化器 `float x = y * 2.0;` 内联到使用点是**延迟求值**——若 `y` 在声明与使用点之间被改写，内联改变语义。必须对初始化器做「有效纯度」判定。
2. **写目标使用**：`x = ...` LHS、复合赋值、`++`/`--`、以及作为写入成员/索引访问的基座——内联会把这些变成字面量赋值。uniform 版没有这一问题（uniform 不能是写目标）。
3. **作用域**：局部 usages 必须词法可见（M15 C24.1 的 scope-owner ∈ 祖先链规则可直接复用）；跨文件预检不适用（局部天然文件内）。
4. **`@custom` 体**：逐字捕获体内没有结构化语句 PSI，usages 不可靠 → 内联目标落在 `CUSTOM_BODY` 内时拒绝。

### 1.2 结构视图与 inlay 的方言现状（含一处缺陷）

- **缺陷：2.0 结构视图工厂从未注册**。`DreamShader2StructureViewFactory`（M12 C6）没有对应的 `lang.psiStructureViewFactory` 注册——plugin.xml 只有 1.x 的 `DreamShaderStructureViewFactory`，其 gate 仅 `file is DreamShaderPsiFile`。`.dss`/`.dsi` 解析为 V2 PSI（无 `DreamShaderDeclaration`/`DreamShaderSection` 节点）→ 1.x 模型给出**只剩文件根的空视图**。M12 的 C6 验收只在直接实例化模型的测试中成立过，生产从未生效。
- **修复路线**：不动 EP 数量，在 1.x 工厂内按方言路由——`DreamShaderDialects.routesToV2`（dss/dsi 与带 V2 标记的 dsh）→ `DreamShader2StructureViewModel`，纯 legacy 文件维持 1.x 模型。避免双 EP 注册顺序的平台耦合。
- **混合 `.dsh` 分段的数据源**：V2 解析器把 legacy 区段捕获为扁平 `LEGACY_BLOCK` 复合节点（直接子为共享 lexer token，无内部结构，`DreamShader2PsiParser.parseLegacyBlockOrSkip`）——1.x 声明在 V2 PSI 中不可见。分段展示需要文本级 1.x 声明扫描：`PsiFileFactory.createFileFromText`（DreamShaderLanguage，块文本经 `DreamShaderDialectAwarePsiParser` 按内容路由回 1.x 解析器）→ 复用 `DreamShaderPsiUtil.declarationsIn`，offset 加块起点回映射；结果按文件 PSI `CachedValue` 缓存。
- **inlay**：`DreamShaderInlayParameterHintsProvider`（`codeInsight.parameterNameHints`，语言级注册）方言无关——签名解析走 `DreamShaderCallSignatureResolver`（1.x 声明头正则 + catalog 内建）。V2 用户函数（`export float Foo(...)` 等）不命中 1.x 正则 → **`.dss` 里用户函数调用今天没有参数名提示**；UE./HLSL 内建提示两方言共享有效。V2 侧 `DreamShader2FileAnalysis.functions` + `parameterList().parameters()` 已提供名字来源（C24.3 的 `calleeParameters` 同一解析序），只差 inlay 消费；混合 `.dsh` 的区域判定用 M15 C23 的 `servesV1At`/`servesV2At`。

### 1.3 推断与 `.dsi` 覆盖值的现状

- **out/inout 写回收窄**：C24.3 的 `calleeParameters` 已能解析被调形参（本文件 → include 闭包；workspace 导出层无形参形状），C24.3 只做了「实参 vs 形参」告警。写回收窄 = 反向消费：实参为对已声明变量的朴素引用、对应形参带 `out`/`inout` 且拼写类型自信 → 该调用点之后变量的有效类型 = 形参类型。`typeOfName` 现按「声明 → 形参 → 文件级」解析，差「调用点写回」一层。
- **`.dsi` 覆盖值**：A10 按声明拼写给模板（bool → true/false、标量占位、向量/矩阵构造器）。Bridge schema（`instance.schema.parameters[]`：`name/kind/type/group/sort/pruned`，上游 `LangSymbolIndex.cpp` `AppendSymbolIndexInstanceSection`）在 `valid` 时是父参数类型的**权威**来源；`kind` 值集（上游 `IR.cpp` LexToString）：`Scalar`/`Vector`/`DoubleVector`/`StaticSwitch`/`Texture`/`StaticComponentMask` 等。**工件没有枚举值清单字段**——M16 候选中「精确到枚举值清单」按工件能力修正为：schema 类型绑定 + `StaticSwitch` kind 判定（true/false 即其「枚举」形态）。
- **C21 复合赋值判定的小洞**：`collectExpressionWrites` 把复合赋值/`++`/`--` 目标记为 write、从 reads 排除 → `x += 1` 产出 `out` 形参；HLSL 中读未写出的 `out` 形参是未定义读，应为 `inout`。随 C26 一并修正。

---

## 2. 目标与非目标

**目标**

1. 重构家族收尾：Introduce 家族补 **Inline Variable**（保守纯度预检下的局部变量内联）；Extract Function 增加**单一返回值启发**（严格条件下 `out` 参数升级为返回值）。
2. 导航与提示按方言分段落地：修复 2.0 结构视图未注册缺陷，补齐 V2 文件与混合 `.dsh` 的结构视图分段；inlay 参数提示补 V2 用户函数解析并按区域分段。
3. 小切片两件：out/inout 写回类型收窄（流敏感推断第一步）；`.dsi` 覆盖值在 Bridge schema 可用时升级为 schema 驱动。

**非目标**

- 不做 Inline Function（调用点展开需要实参-形参绑定与副作用矩阵，另行排期）。
- 不做「多写出参合并返回」或元组返回；单一返回值仅在严格条件下启用，其余维持 M15 的 out/inout 矩阵。
- 不做 LEGACY_BLOCK 的解析器改造——分段视图用文本级重解析（缓存），解析侧保持冻结。
- `.dsi` 枚举值清单继续 blocked（上游 symbol-index 工件无此字段，待上游扩展后解封）。

---

## 3. 任务卡

优先级：P1 = 用户可见能力补齐与缺陷修复；P2 = 推断与补全的保守升级。

### C27（P1）结构视图方言分段（含注册缺陷修复）
- **目标**：V2 文件（`.dss`/`.dsi`、纯 V2 `.dsh`）有真实的 2.0 结构视图；混合 `.dsh` 按声明方言分段展示；纯 legacy 文件行为不变。
- **任务**：
  1. `DreamShaderStructureViewFactory` 按方言路由：`DreamShaderDialects.routesToV2(file)` → `DreamShader2StructureViewModel`（`.dsh` 扩展进 2.0 模型的 gate）；否则维持 1.x 模型（现状逐字节不变）。
  2. 混合 `.dsh`：`DreamShader2StructureViewModel` 顶层在 V2 声明之外为每个 `LEGACY_BLOCK` 增加 **Legacy 节点**（label 取块内首个 1.x 声明关键词+名，如 `Shader Main`；图标 1.x 声明图标），子节点来自块文本的 1.x 重解析（`PsiFileFactory` + `DreamShaderPsiUtil.declarationsIn`，offset 回映射，文件级 `CachedValue` 缓存）；子节点可导航到块内偏移。
  3. 文档零变化的回归面：纯 `.dsm`/`.dsf`/legacy `.dsh` 走 1.x 模型（现有结构视图测试锁定）；纯 `.dss`/`.dsi` 的 2.0 模型树形不变（现有 `DreamShader2StructureView` 测试迁移为经工厂路由获取）。
- **验收**：平台测试——`.dss` 经工厂得到 2.0 模型树（uniform 分组/struct/function）；混合 `.dsh` 同时出现 V2 声明与 Legacy 子树（含 1.x 声明名与导航偏移）；纯 legacy `.dsh` 树形与现状一致；`.dsi` Parent 节点不变。
- **涉及文件**：`navigation/DreamShaderStructureViewFactory.kt`、`navigation/DreamShader2StructureView.kt`、新测试。

### C28（P1）inlay 参数提示的 V2 解析与区域分段
- **目标**：`.dss` 用户函数调用有参数名提示；混合 `.dsh` 内提示按 caret 所在声明方言解析；1.x 行为不变。
- **任务**：
  1. `DreamShaderInlayParameterHintsProvider` 增加方言感知：1.x 解析（`DreamShaderCallSignatureResolver`）为空且区域为 V2（纯 V2 文件或 `servesV2At(file, offset)`）→ V2 解析：`findCallAtName` 的被调名 → `DreamShader2FileAnalysis`（本文件）→ include 闭包（`DreamShader2WorkspaceSymbols.includeClosurePsi`，与 `calleeParameters` 同序）命中函数 → `parameterList().parameters()` 名序列。
  2. 区域判定复用 C23 的 `servesV1At`/`servesV2At`；legacy 区域维持纯 1.x 路径（零行为变化）；`@custom` 体（无结构化声明）维持空。
  3. 命名实参跳过、settings 开关门控、`HintInfo.MethodInfo` 结构维持现状；V2 命中的 MethodInfo 用被调名小写。
- **验收**：`.dss` 中 `export float Foo(float a, float b)` 调用处出现 `a:`/`b:` 提示、命名实参不提示；include 闭包内声明的函数同样命中；1.x 文件现有 inlay 测试零回归；混合 `.dsh` legacy 区域走 1.x 解析（V1 声明命中、V2 名不串扰）。
- **涉及文件**：`editor/DreamShaderInlayParameterHintsProvider.kt`、新测试（`DreamShaderInlayParameterHintsProviderTest` 扩展）。

### C25（P1）Inline Variable（Introduce 家族收尾）
- **目标**：`.dss` 函数体内把带初始化器的单声明器局部变量内联进全部使用处并删除声明。
- **任务**：
  1. 新增 `DreamShader2InlineVariableHandler`（`RefactoringActionHandler`）+ `DreamShader.InlineVariable` action（`RefactoringMenu`，`Inline Uniform` 旁）；caret 在声明名或使用处上触发，目标解析回 `VARIABLE_DECLARATION_STATEMENT`（使用处按名字 + C24.1 可见性匹配）。
  2. 预检（全过才写，任一失败 error hint + 文档零改动）：
     - 单声明器、有初始化器（`=` 与 `;` 之间非空）；
     - 初始化器**有效纯**：子树无 `++`/`--`/赋值（含复合）运算符；引用面只允许字面量、类型构造器（`CONSTRUCTOR_EXPRESSION`）、`UE.*`/catalog 命名空间调用与 HLSL 内建调用——**引用任何形参/局部/全局一律拒绝**（漂移风险），引用 uniform 允许（文件级输入不可被函数体改写）；
     - 全部使用处是读（非 `=`/复合赋值 LHS、非 `++`/`--` 操作数、非写入成员/索引访问的基座）；
     - 使用处全部词法可见（声明 scope-owner ∈ 使用点祖先链）且在声明之后；
     - 声明与使用处均不在 `CUSTOM_BODY` 内。
  3. 写入：使用处倒序替换为带括号初始化器（与 C22 同一替换语义）→ 声明连同前置 doc 块与行尾整行删除。
- **验收**：平台测试 diff 断言：单使用/多使用内联、声明与 doc 块删除；拒绝矩阵（无初始化器、多声明器、初始化器引用形参/局部/全局、初始化器含副作用、使用处为写目标、`@custom` 体、出作用域使用）；1.x 文件静默；action 接线测试。
- **涉及文件**：新 `refactoring/DreamShader2InlineVariableHandler.kt`、`plugin.xml`、bundle（en/zh）、新测试。

### C26（P1）Extract Function 单一返回值启发（+ inout 判定修正）
- **目标**：唯一存活写出参在严格条件下升级为返回值；复合赋值/自增减目标修正为 `inout`。
- **任务**：
  1. **判定修正**：`collectExpressionWrites` 中复合赋值与 `++`/`--` 目标同时记入 reads（读旧值语义）→ 限定词变 `inout`（`x++`、`x += 1` 场景）。
  2. **单一返回值启发**：当写出参集合**恰有一个**、限定词为 `out`（选择区未读过它）、且**选择区最后一个顶层语句是针对该名字的简单赋值/复合赋值语句**（表达式语句形态 `name = expr;`，保证返回值取到最终写入）时：
     - 新函数签名 `Type Name(inParams) { …; return name; }`（不再是 `void` + `out`）；
     - 调用点替换为 `Type name = Name(args);`——保留原变量名与类型拼写，后续代码零改动；
     - `inout` 场景、多写出参、末语句非简单赋值 → 维持 M15 的 out/inout 矩阵（启发只是升级路径，绝不放宽拒绝面）。
  3. 就地重命名联动三位置：新函数名、调用点名、接收变量名（TemplateBuilder 同名 variable）。
- **验收**：平台测试 diff 断言：单一纯 out + 末语句赋值 → 返回值形态（函数头、`return` 行、调用点声明替换）；`x += 1` 场景产出 `inout`（修正回归）；多写出参/`inout`/末语句为 `if` → 维持 out 矩阵；1.x 静默。
- **涉及文件**：`refactoring/DreamShader2ExtractFunctionHandler.kt`、bundle、测试扩展。

### C29（P2）`.dsi` 覆盖值 schema 驱动升级
- **目标**：Bridge schema 可用时，覆盖值建议绑定**父 schema 权威类型**与参数 kind；schema 缺席时维持声明拼写路径（现状不变）。
- **任务**：
  1. `instanceOverrideValueCompletions` 前置查询：覆盖名（当前行 `uniform <type> <name> =`）在 `instance.schema.parameters[]` 按名精确匹配 → 命中且 schema `valid`：以 schema `type` 拼写重推类型（`DreamShader2Type.fromSpelling`），`kind == "StaticSwitch"`（大小写不敏感）强制 true/false（即使声明拼写是 int/float——引擎侧 static 语义权威在 kind）；`kind == "Texture"` 静默（资源值是引擎业务）。
  2. 名字不命中或 schema 缺席/invalid → 声明拼写驱动（A10 现状逐字节不变）。
  3. 与 DSH7259 检查共用同一 schema 读取（`DreamShaderSymbolIndexRepository`，只读，不新增数据通道）。
- **偏差记录**：任务来源中「精确到枚举值清单」不可实现——symbol-index 工件无枚举字段（上游 `AppendSymbolIndexInstanceSection` 只出 name/kind/type/group/sort/pruned）；「枚举」形态即 StaticSwitch 的 true/false。上游扩展字段后解封。
- **验收**：`.dsi` fixture（注入 index 文件）测试：schema 类型与声明拼写一致时建议不变；schema `StaticSwitch` + 声明 `int` → 建议 true/false；schema `Texture` → 空；schema 缺席 → 现有 A10 测试零回归。
- **涉及文件**：`editor/DreamShader2CompletionContributor.kt`、测试扩展。

### C30（P2）流敏感推断：out/inout 写回类型收窄
- **目标**：`F(x)`（`F(out float3 v)` 声明在案）调用之后，`x` 的类型按形参拼写收窄；红线不变（任一侧不自信即静默）。
- **任务**：
  1. `DreamShader2InferenceContext` 增加函数级**写回表**：惰性扫描所在函数体的 `CALL_EXPRESSION`——被调可经 `calleeParameters` 解析（本文件 → include 闭包）、实参为朴素 `REFERENCE_EXPRESSION`、对应形参（位置对位；命名实参按名对位）带 `out`/`inout` 限定且拼写类型自信（非 Unknown）→ 记录 `(callOffset, varName → formalType)`。
  2. `typeOfName` 消费：局部/形参/文件级解析之前，取 useSite **之前最近**的写回记录命中者（形参类型覆盖声明类型——`out` 语义即重写）。仅函数体内生效；`for` 头等块外调用点同规则。
  3. 同名遮蔽：写回按名记录，与局部声明的最近声明优先规则合并——写回记录的 varName 解析到哪个声明不做区分（保守：同名场景写回优先级低于更近的重新声明）。
- **验收**：`DreamShader2TypeInferenceTest`：`out`/`inout` 写回正例（调用后 `x.` 出 swizzle 成员）；调用前不收窄；形参类型 Unknown 静默；命名实参写回；复合推断链（写回后调用 `normalize(x)` 收窄一致）。全量零回归。
- **涉及文件**：`diagnostics/DreamShader2TypeInference.kt`、测试扩展。

### T16（贯穿）测试与文档
- **任务**：各任务卡验收用例；`docs/language-v2-baseline.md` 增 M16 覆盖映射（内联家族/结构视图分段/inlay 分段/schema 覆盖值/写回收窄）并更新 Test Mapping；`agent-development-plan.md` 追加 M16 段、列 M17 候选；CHANGELOG（en/zh）新增 2.4.0 段；README plugin-metadata 同步内联与分段描述；版本号 2.4.0。
- **验收**：`gradlew.bat test` 全绿；文档与实现互检。

**建议实施顺序**：C27 → C28（批次 1：「分段」线，C27 先修注册缺陷）；C25 → C26（批次 2：「内联/重构」线，C25 复用 C22 骨架，C26 的 inout 修正与 C25 的写目标判定共享辅助）；C29 → C30 → T16（批次 3：小切片 + 收尾）。

---

## 4. 风险与依赖

- **依赖**：C27/C28 依赖 C23 的区域判定（已就绪）；C25 依赖 C22 骨架与 C24.1 作用域规则（均就绪）；C26 依赖 C21 数据流矩阵（已就绪）；C29/C30 依赖 symbol-index 仓库与 `calleeParameters`（均就绪）。
- **风险**：
  - C27 的 Legacy 子树依赖运行时 1.x 重解析——大块文本的开销由文件级 CachedValue 兜底（结构视图构建非 EDT 热路径，双保险）；重解析失败（畸形 legacy 文本）静默降级为无子节点的 Legacy 节点，不出诊断。
  - C28 的 V2 解析在 dumb 模式下 include 闭包为 Bridge-only/空——本文件声明仍可命中；与 `DreamShader2WorkspaceSymbols` 的静默降级契约一致。
  - C25 的纯度预检偏严（拒绝 uniform 以外的引用）是刻意的——内联的语义漂移不可静态判定时按红线 1 拒绝；后续可加「无中间写入证明」的放宽通道。
  - C26 启发要求「末语句为简单赋值」是保守近似（不做完整支配树分析）；条件写入场景维持 out 矩阵，宁可少升级不可错升级。
  - C30 写回表按名匹配在同名遮蔽场景可能过宽——以「更近的重新声明压过写回」兜底，最坏情况是多给一次自信类型（mismatch 告警面无新增，swizzle 补全面向的是合法成员）。
- **验证命令**：`.\gradlew.bat test --no-configuration-cache`。

---

## 5. 执行记录

（分批填写）
