# M17 计划：内联函数与 legacy 补全分段（Inline Function & Legacy Completion Segmentation）

- **制定日期**：2026-10-07
- **基线**：本插件 2.4.0（2026-10-06，M16 完成）；上游 DreamShader 2.0.2 @ `b3bbf48`
- **来源**：`docs/agent-development-plan.md` 的 M17 候选（M16 顺延项）：Inline Function（复用 C22/C25 预检骨架与 C30 写回表原语）、`.dsh` 混合方言逐声明分段的补全（结构视图分段已先行，补全侧仍按区域粗分）、多写出参合并返回（C26 启发的放宽通道）。LEGACY_BLOCK 解析器改造继续顺延（解析侧冻结红线），`.dsi` 覆盖值枚举清单继续 blocked（上游工件无枚举字段）。
- **计划状态**：已完成（2026-10-07 三批落地，见 §5 执行记录，版本 2.5.0）。任务卡并入 `docs/agent-development-plan.md` 作为 M17。

---

## 1. 现状研究

### 1.1 Inline 地基与函数展开的判定原语

`DreamShader2InlineVariableHandler`（C25）的「先查全量、后写一次」契约与纯度矩阵可直接升级为函数展开的**实参矩阵**：

- C25 的初始化器纯度判定（`initializerPurityProblem`）逐节点走 PSI：字面量/无副作用运算符/类型构造器/内建与 catalog 调用放行，用户函数与未知调用拒绝（`unsupportedCall`），副作用运算符拒绝（`sideEffects`），引用只放行 uniform（`readsMutable`）。**内联函数的差异**：实参在展开点仍只求值一次，但**文本替换会让它在形参的每个出现处重复求值**——因此纯度规则保留（副作用运算符、用户调用拒绝），而引用面放宽：实参读局部/形参/全局是安全的，因为所有替换后的出现彼此相邻，中间不可能插入改写（体写目标只有体自身局部与 out 形参，均被别的预检覆盖）。
- C30 写回表的两个原语直接复用：`firstKeywordText(parameter)`（首个有意义子节点为 `out`/`inout` KEYWORD）判定形参限定符；`plainReferenceArgument`（剥单子 EXPRESSION 包装后为 REFERENCE_EXPRESSION）判定实参可写回。
- C26 的调用点改写 machinery（`topLevelAssignmentOf` 的扁平 EXPRESSION 下钻、`bareDeclarationBefore`、`declarationRemovalRange` 整行删除、`reindentStatement`）为返回值改写与语句块替换提供现成形态。
- **值形参写穿是内联的最大语义陷阱**：值传递时体内 `p = …` 不影响调用方，替换后 `arg = …` 会写穿调用方变量。因此矩阵必须拒绝「值形参在体内任一写位出现」（含成员/索引基座写），只有 `out`/`inout` 形参可写，且对应实参必须是朴素引用（复用 C30 判定）。
- 目标解析：caret → `CALL_EXPRESSION` → 点路径（C25 `callPath` 同规则）→ `DreamShader2FileAnalysis.functionByName`（本文件）→ `DreamShader2WorkspaceSymbols.includeClosurePsi`（include 闭包，BFS、深度/环受限，`DreamShaderImportResolver` V2 扩展集）。解析不到 → 拒绝。

### 1.2 混合 `.dsh` 补全的现状：区域粗分的根源

- 分流层（M15 C23）是三值的且正确：`DreamShaderCompletionDialects.regionAt` 按顶层节点切（`LEGACY_BLOCK` → V1、V2 声明 → V2、顶层缝隙 → BOTH），两个贡献者各自 gate。
- **粗分在上下文层**：`DreamShaderCompletionContextAnalyzer.analyze(file, offset)` 的 PSI 分支用 `PsiTreeUtil.getParentOfType(element, DreamShaderDeclaration/Section)`——混合 `.dsh` 由 2.0 前端解析，legacy 块内是扁平共享 lexer token，**没有任何 1.x DECLARATION/SECTION 复合节点**。于是 legacy 函数体深处 `isTopLevel` 恒真、`declarationKeyword`/`currentSectionName` 恒 null：1.x 建议器在 legacy 函数体内给的是顶层关键词，而不是函数体 HLSL 内建、`Settings` 键值、section 形态。
- callable 候选同理为空：`collectCallableCandidates` 走 `DreamShaderPsiUtil.declarationsIn(file)`，混合文件返回空——legacy 区段内补不出本文件 1.x 函数/命名空间成员。
- **数据源已有先例**：M16 C27 的结构视图分段用 `PsiFileFactory.createFileFromText("legacy.dsh", DreamShaderLanguage, block.text)`（块文本经方言感知解析器路由回冻结的 1.x 前端）+ `DreamShaderPsiUtil.declarationsIn` + 块起点偏移回映射，`CachedValue` 挂文件 PSI。C32 把同一手法搬进补全：块内 caret → 子 PSI 上下文分析（偏移回映射），callable 候选并入子 PSI 声明。
- 自动弹出（typed handler）已在 C23 同步过区域分流，不在本卡范围。

### 1.3 多写出参合并返回的现状

- C26 单一返回值启发只在「恰一 `out` 出参 + 末语句简单赋值 + 同块裸声明无中间使用」时开火，其余维持 out 矩阵。
- HLSL 无元组；上游 V2 有 `struct`。合并通道的形态：生成 `struct <Name>_Result { …; };`，helper 返回结构体值，调用点拆回各变量（`<Name>_Result r = <Name>(…); a = r.a; b = r.b;`）。
- 支配树分析的保守替代：**直线代码切片**——选择区内无 `if`/`for`/`while`/`do`（C21 已拒绝 return/break/continue），则每个写必然到达出口，结构体字段全初始化无需支配树。每个合并出参须满足 C26 的同名条件（裸声明、末语句终写、中间无使用）；**全部**出参合格才开火，否则维持 out 矩阵。

---

## 2. 目标与非目标

**目标**

1. 重构家族收尾：**Inline Function**（保守副作用矩阵下的调用点展开，`DreamShader.InlineFunction` 菜单动作）。
2. 补全按方言**逐声明分段**：混合 `.dsh` 的 legacy 块内恢复完整 1.x 上下文补全（声明体/section/settings/函数体内建）与本文件 callable 候选。
3. 提取函数放宽通道：多 `out` 出参在直线代码条件下**合并为结构体返回**。

**非目标**

- 不做 LEGACY_BLOCK 解析器结构化改造——补全分段沿用文本级重解析（缓存），解析侧维持冻结（架构红线 2）。
- `.dsi` 覆盖值枚举清单继续 blocked（上游 symbol-index 工件无枚举字段）。
- 不做内联的临时变量提升（实参不纯直接拒绝，不做 `__inlinedN` hoist）；不做多返回点/中位 return 的展开；不做内联时的自动重命名消解（名字冲突直接拒绝）。
- 不做元组语法（上游无此语言特性）；结构体由本插件生成，名字带后缀避免污染。

---

## 3. 任务卡

### C31（P1）Inline Function：保守副作用矩阵下的调用点展开

- **目标**：caret 落在用户函数调用上（本文件或 include 闭包声明、非 `@custom`、有结构化体）时，`DreamShader.InlineFunction` 把调用展开为体语句；`Refactor → Inline` 语义与 Inline Uniform/Variable 一致——预检全过才动文档，任一失败 error hint + 零改动。
- **任务**：
  1. 目标解析：caret 叶子（含回退一位）→ `CALL_EXPRESSION` 祖先 → 点路径 → 本文件 `functionByName` → include 闭包函数。`@custom` 体、无体/`extern`、解析不到 → 拒绝。
  2. 预检矩阵（拒绝即 hint）：
     - 递归：体内调用被内联函数名 → 拒绝。
     - return：至多一条**顶层末位** `return expr;`；嵌套块内 return、多条 return → 拒绝。`break`/`continue` 仅当封闭于体内自己的循环语句 → 放行，否则拒绝。
     - 值形参写穿：值形参名出现在体内任一写位（赋值 LHS、复合赋值、`++`/`--`、成员/索引基座写）→ 拒绝；`out`/`inout` 形参可写，对应实参必须是朴素引用（C30 `plainReferenceArgument`），否则拒绝。
     - 实参纯度：值形参实参按 C25 矩阵（无副作用运算符、无用户/未知调用；引用面放宽到本地点/形参/全局/uniform）。
     - 实参-形参绑定：位置实参按序绑定、命名实参按名绑定；位置随命名之后、计数不匹配、未知形参名 → 拒绝。
     - 名字冲突：体内局部声明名与调用点可见名（调用方形参 + 词法可见局部 + 文件级 uniform/global）碰撞 → 拒绝（v1 不重命名）。
  3. 展开写入（倒序替换）：
     - 调用为**整条表达式语句** → 语句替换为体语句（重缩进到调用语句缩进），末位 `return expr;` 丢弃（值弃用合法）。
     - 调用为**赋值 RHS 或初始化器整体**（`T x = Foo(…);` / `lhs = Foo(…);`，不允许复合赋值）→ 语句替换为：体语句 + 末位 `return expr;` 改写为 `<lhsText> = expr;`（初始化器情形先补 `T x;` 裸声明，多声明器拒绝）。
     - 调用处于更大表达式内部 → 拒绝。
     - 体语句文本经形参名 → 实参文本替换（PSI REFERENCE 叶位收集，倒序替换），再整体重缩进。
  4. 注册：`plugin.xml` action `DreamShader.InlineFunction`（RefactoringMenu last）、bundle en/zh、`DreamShader2InlineFunctionHandler` + action 类。
- **涉及文件**：`language/refactoring/DreamShader2InlineFunctionHandler.kt`（新）、`DreamShaderInlineFunctionAction.kt`（新）、`META-INF/plugin.xml`、`messages/DreamShaderBundle.properties`、`messages/DreamShaderBundle_zh_CN.properties`。
- **验收标准**：`DreamShader2InlineFunctionTest` 覆盖——语句位展开（void 体、值弃用）；赋值/初始化位展开（return 改写为 LHS 赋值、初始化器补裸声明）；形参替换含 `out`/`inout` 写回朴素实参；拒绝矩阵（递归、嵌套 return、值形参被写、out 实参非朴素、实参含副作用/用户调用、命名/位置错绑、名字冲突、`@custom`、更大表达式内、复合赋值 RHS）；1.x 文件静默；action 接线；文档零改动断言。
- **上游参考**：无新上游依赖（1.x/2.0 语义均在既有基线内）。

### C32（P2）混合 `.dsh` legacy 区段的逐声明补全上下文

- **目标**：legacy 块内 caret 的 1.x 补全恢复 PSI 级上下文——函数体内出 HLSL 内建/局部符号、`Settings`/`Options` 内出键值、声明头出参名——并补齐本文件 1.x callable 候选；V2 区段与纯文件行为零变化。
- **任务**：
  1. 共享 legacy 子解析（editor 侧新助手，C27 模式）：HEADER_MIXED 文件的每个 `LEGACY_BLOCK` → `createFileFromText`（方言感知解析器路由回 1.x 前端）→ 1.x FILE 根 + 块起点偏移；`CachedValue` 挂文件 PSI。
  2. `DreamShaderCompletionContextAnalyzer.analyze(file, offset)` 前置分支：文件为 `DreamShaderPsiFile` 且 `DreamShaderCompletionDialects.regionAt(file, offset) == Region.V1` 且命中某 legacy 块 → 子根上跑既有 parsed-root 上下文分析（`analyzeFromParsedRoot` 同逻辑，偏移回映射）。块内解析失败回落现状行为。
  3. callable 候选：`collectCallableCandidates` 合并各 legacy 块子根的 `declarationsIn`（1.x 声明/命名空间成员），与既有空结果取并集。
- **涉及文件**：`language/editor/DreamShaderCompletionContributor.kt`（分析器分支 + 候选合并）、新助手文件或 `DreamShaderCompletionSupport.kt` 内挂缓存。
- **验收标准**：`DreamShaderLegacyCompletionSegmentationTest`——混合 `.dsh` legacy 函数体内 caret 上下文为 `isInDeclarationBody && isFunctionLikeDeclaration`（非 topLevel）；`Settings` 块内 `isInSettingsOrOptionsSection`；建议器在该上下文产出内建/键值项（含负向：V2 区段不受影响、纯 `.dss` 零变化、纯 1.x 文件零变化）；callable 候选含 legacy 声明；缓存命中（同块重复询问不重复解析——以结果一致性断言）。
- **上游参考**：`Docs/language/source-files.md`（方言共存判定）。

### C33（P2）提取函数多写出参合并返回（直线代码切片）

- **目标**：C26 的放宽通道——选择区写出 **≥2** 个出参且全部满足 C26 同名条件、选择区为直线代码（无 `if`/`for`/`while`/`do`，C21 已禁控制转移）时，合并为生成的结构体返回，替代 out 矩阵；任一条件不满足维持 C26/C21 现状。
- **任务**：
  1. 触发判定（在 C26 upgrade 判定同层）：`outParams` 全部 `out` 限定符、每个名字 `bareDeclarationBefore` 合格、选择区语句子树无分支/循环节点。
  2. 生成：`struct <Name>_Result { <spelling> <name>; … };` 插在 helper 之前；helper 体内每个出参的**全部**写位与声明替换为 `result.<name>`（PSI 叶位文本手术，复用 C26 `reindentStatement`/`topLevelAssignmentOf`），体末 `return result;`；调用点 `Type… <Name>_Result ret = <Name>(inParams);` + 每出参一行 `<name> = ret.<name>;`；原裸声明保留（选择后仍有使用）。
  3. 命名：`<Name>_Result` 与 `ret` 避让可见名（数字后缀）；结构体名避让 `analysis.structs` 与闭包。
- **涉及文件**：`language/refactoring/DreamShader2ExtractFunctionHandler.kt`、bundle 双语。
- **验收标准**：`DreamShader2ExtractFunctionTest` 扩展——双出参直线选择 → 结构体返回形态 diff 断言（struct 生成、体写位改写、调用点拆回）；任一出参为 `inout`、有裸声明不合格、选择区含 `if`/`for` → 回落 out 矩阵（负向）；inParams 与返回值 upgrade（C26 单出参）互斥不被破坏（既有用例回归）。
- **上游参考**：`Docs/language-v2/index.md`（struct 语法）。

---

## 4. 批次编排

延续 M15/M16 的「计划 → 批次 → 收尾」节奏，每批次一个提交、DoD 为 build + 新增测试 + 全量不回归：

1. **Batch 1**：C31（Inline Function 全量矩阵，最大单卡）。
2. **Batch 2**：C32（legacy 补全分段）。
3. **Batch 3**：C33（多写出参合并返回）。
4. **收尾**：CHANGELOG（en/zh）2.5.0 段、`language-v2-baseline.md` M17 覆盖映射、README 元数据块、版本号 2.5.0、M17 段写回 `agent-development-plan.md`。

---

## 5. 执行记录

### Batch 1 — C31 Inline Function（2026-10-07）

`DreamShader2InlineFunctionHandler` + `DreamShader.InlineFunction` action + bundle 双语。实现要点与计划的偏差：

- 实参值文本必须取自**调用方文件**（实参 PSI 偏移属于调用方，被调函数可能在另一文件）——`bindArguments` 接收 `callerText`，纯度检查的 `resolveDeclaredSignatures` 仍用被调文件文本。
- 初始化器位形态：声明初始化器解析为单子 `EXPRESSION` 包装，分类器接受「包装的唯一有意义子节点即调用」；头部守卫为「spelling 与名字之间仅空白、spelling 之前仅修饰符关键词」（初版的 `endsWith` 文本守卫被名字前空格击穿，已修正）。
- 返回值表达式在改写为赋值前先做形参替换（`substitutedReturnText`）；裸 `return;` 在值位拒绝（`noReturnValue`）。
- 26 例测试：三种位置形态、缩进、out/inout 写回、命名实参、include 闭包、13 项拒绝矩阵、1.x 静默、action 接线。

### Batch 2 — C32 legacy 补全分段（2026-10-07）

`DreamShaderLegacyCompletionSubtrees`（`DreamShaderCompletionSupport.kt`，C27 规则共享）+ 分析器前置分支（`legacyRegionContextAt`：HEADER_MIXED 且命中 legacy 块 → 子根 `analyzeFromParsedRoot`，偏移回映射）+ `analyzeFromParsedRoot` 转 internal。研究修正：callable 候选走 `collectDeclaredCallables` 的**文本级**签名扫描，混合文件中本就可见 legacy 声明——C32 的缺口只在上下文层（`analyzeWithPsi` 在块内 `isTopLevel` 恒真），测试以「块内上下文标志 + suggester 产出（内建/`UE`/设置键）+ 三值区域不变 + callable 仍含 legacy 声明 + 纯文件不受影响」钉住。9 例测试。

### Batch 3 — C33 多写出参合并返回（2026-10-07）

`structReturnMerge`（≥2 纯 out + 直线代码 + 全部 C26 裸声明条件 + 首现即写）→ 生成 `struct <Name>_Result` 插在 helper 之前，体内全部引用改写 `result.<field>`，调用点 `Struct result = Name(inParams);` + 每值一行 `name = result.name;`（续行带调用语句缩进）。研究修正：选择区内任一读取都会把值判为 `inout`（C21 的 readNames 规则），故「读先于写」经 inout 限定符检查即拒绝——`firstOccurrenceIsWrite` 保留为防御性守卫。`Extract Function` 既有 `testMultipleOutValuesKeepOutMatrix` 升级为结构体断言，另补四例回落（inout 混入、读先于写、选择区含分支、初始化声明混入）。

### 收尾（2026-10-07）

CHANGELOG（en/zh）2.5.0 段、`language-v2-baseline.md` M17 覆盖映射与测试映射行、README 元数据块（en/zh）、版本号 2.5.0、本计划状态与执行记录、`agent-development-plan.md` M17 段 + M18 候选。全量 875 例零失败。
