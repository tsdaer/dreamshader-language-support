# M14 计划：表达式级智能加固（Expression Intelligence）

- **制定日期**：2026-10-05
- **基线**：本插件 2.1.0（2026-10-05，M13 完成）；上游 DreamShader 2.0.2 @ `b3bbf48`
- **来源**：`docs/completion-plan.md` §4 的 M14 候选方向 + `docs/roadmap.md` 的长期加固目标（「full expression type inference remains a separate long-term hardening target」）
- **计划状态**：执行中（分两批，目标版本 2.2.0）

---

## 1. 现状研究

### 1.1 类型推断（C2/M11 产物）现存的四个缺口

`DreamShader2InferenceContext`（`diagnostics/DreamShader2TypeInference.kt`）是保守折叠求值器，逐条核对其行为：

1. **三元 `?:` 返回条件类型（实际 bug）**：`flatExpressionType` 折叠循环里 `"?"/":" -> accumulator`（`:220`）——`a ? b : c` 折叠到 `a` 的类型（条件），而不是任一分支。`float3 r = flag ? float3(1,0,0) : float3(0,1,0);` 这类惯用法直接落空。
2. **复合赋值不保 LHS 形状**：`+=`/`-=`/`*=`/`/=` 走通用 `binaryArithmetic` 折叠，`float x; x += vector` 会把结果形状扩成 RHS 的向量形状——而 HLSL 复合赋值结果就是 LHS 类型。`annotateAssignmentCategoryMismatches` 也只认裸 `=`，复合赋值的 RHS 类目不检查。
3. **include 闭包内的 helper 函数调用类型 Unknown**：`userFunctionReturnType` 只查本文件 `analysis.functionByName` 与 workspace `exportedFunctions`（后者只含 export/extern），跨文件调用的非 export helper 全部落 Unknown，补全（成员/swizzle）与 mismatch 告警均失去输入。
4. **`?`/`:` 与 `++`/`--` 的折叠语义未区分**：前缀 `++x`/`-x` 走数值保持分支尚可，但双目前缀识别（`children.first()` 是 OPERATOR）把 `a - b` 的中缀减法在「表达式以 OPERATOR 开头」时误判——该分支只在真前缀时进入，现状可接受；三元是唯一的行为性 bug。

### 1.2 后缀模板在 2.0 文件中实际不可用

`DreamShaderPostfixExpressionSelector.getExpressions`（`templates/DreamShaderPostfixTemplates.kt:70`）按「行前缀」判定表达式，但 `looksLikeTopLevelAssignment` 把含顶层 `=` 的行前缀全部拒绝——`.dss` 中绝大多数语句形如 `float2 UV = UE.TexCoord(0).`，**在 2.0 文件里输入 `.` 触发后缀模板时选择器永远返回空**。即现有 `if`/`ifel` 模板在 `.dss`/`.dsi` 中形同虚设，新模板更无从谈起。

### 1.3 签名帮助的两个缺口

1. **命名实参不感知当前位置**：`DreamShaderSignatureHelpAnalyzer.parameterIndex`（`editor/DreamShaderSignatureHelp.kt:316`）按顶层逗号计数。`UE.TransformVector(Source="Tangent", Destination="Wo|")` 中 caret 高亮的是第 2 个参数 `Source`（位置 1），而实际所在实参是 `Destination`。签名串的 parameterNames 已就绪，只差「实参名 → 参数索引」映射。
2. **签名候选链「首个非空即返回」**：`DreamShaderCallSignatureResolver.resolveSignatures` 按声明 → import → catalog → 内建表逐级 return。catalog 条目缺显式 signature/参数时（`requireComplete=true`）静默落到内建表，而两者对同一函数各有一份（catalog 综合签名 vs 文档化签名）——用户只能看到一份，重载形态（如 `Path(Root, ObjectPath)` / `Path("/Root/ObjectPath")`）的多行展开只在硬编码表内存在。

### 1.4 重构能力现状

`lang.refactoringSupport` 已注册（`DreamShaderRefactoringSupportProvider`），rename 安全网（M12 C5：`.dsi` 覆盖引用检查 + stub 索引影响面）就绪；但 Introduce 系（Extract Variable）完全缺失——2.0 文件中提取表达式必须手写声明行。C2 类型推断可作为新声明类型拼写的来源（ confidently inferred → 反推拼写）。

---

## 2. 目标与非目标

**目标**
1. 三元/复合赋值/闭包函数调用三类表达式的类型有把握可推，反哺补全（成员、swizzle）与 mismatch 告警；Unknown 语义与静默降级红线不变。
2. 后缀模板在 2.0 文件真正可用，并补 `lerp`/`saturate`/`sample`/`swizzle` 四个表达式模板。
3. 签名帮助按命名实参高亮正确参数；catalog 与内建签名按重载并集展示。
4. 2.0 文件支持 Extract Variable（第一批：选中表达式提取，rename 模板内联重命名）。

**非目标**
- 不做 Extract Function / Inline Uniform（留 M15——同属重构线，依赖本批 C20 的 handler 地基）。
- 不做 `.dsh` 混合方言逐声明分段补全（留 M15）。
- 不重写折叠求值器为优先级精确求值（平铺左折叠语义保留，仅修行为性 bug）。
- GLSL 别名提示解封继续 blocked（依赖上游 legacy alias 清单确认）。

---

## 3. 任务卡

优先级：P0 = 修正错误行为；P1 = 2.0 基本盘补齐；P2 = 新能力。

### C17（P0）表达式类型推断加固
- **目标**：`?:`、复合赋值、include 闭包 helper 调用三类表达式类型可信；mismatch 告警覆盖复合赋值。
- **任务**：
  1. 三元折叠：`?` 后进入「分支收集」状态，结果取 `:` 两侧分支的合并类型——同形状取该类型，数值形状不同时按现有 `binaryArithmetic` 规则提升，其他差异保守 Unknown；任一分支 Unknown 时取另一支的自信类型仍优于条件类型（条件是 bool，绝不会是分支类型）。
  2. 复合赋值折叠：`+=` `-=` `*=` `/=` `%=` 及位移/位运算复合形式在折叠中直接产出 LHS 已积累类型（accumulator 原样保持）。
  3. `annotateAssignmentCategoryMismatches` 扩展：复合赋值按 LHS 目标 vs RHS 值做同一套类目检查（仅数值复合形式，`&&=` 类不涉及）。
  4. `userFunctionReturnType` 增加 include 闭包声明解析：本文件 → 闭包 PSI（`DreamShader2FileAnalysis.of(includee).functionByName`，含非 export helper）→ workspace export 表；保持既有优先级与 Unknown 兜底。
  5. 流敏感收口（保守）：闭包解析命中多个同名声明时按最近 include 优先，不猜歧义。
- **验收**：`DreamShader2TypeInferenceTest` 新增三元两分支/复合赋值保形/闭包 helper 三组用例（含 Unknown 兜底断言）；mismatch 复合赋值正反例测试；全量零回归。
- **涉及文件**：`DreamShader2TypeInference.kt`、`DreamShader2DiagnosticsPass.kt`、对应测试。

### C18（P1）后缀模板 2.0 化
- **目标**：`.dss`/`.dsi` 中 `expr.` 后缀模板可用，四个表达式模板落地。
- **任务**：
  1. `DreamShaderPostfixExpressionSelector` V2 感知：行前缀含顶层 `=` 时，取**最后一个顶层 `=` 之后的片段**参与 `looksLikeExpression` 判定（声明/赋值行内的表达式后缀合法）；`if(`/`else`/块结束等排除条件维持。
  2. 新模板（表达式型，两个方言通用）：`lerp` → `lerp(expr, y, s)`、`saturate` → `saturate(expr)`、`sample` → `expr.Sample()`、`swizzle` → `expr.$swz$`（分量可编辑）。
  3. 既有 `if`/`ifel` 行为回归锁定。
- **验收**：`DreamShaderPostfixTemplatesTest` 新增 V2 场景：声明行内 `.if`/`.saturate` 选择器非空、四模板的 templateString 断言、顶层赋值行仍拒绝提取声明的负向用例。
- **涉及文件**：`DreamShaderPostfixTemplates.kt`、`DreamShaderPostfixTemplatesTest.kt`。

### C19（P1）签名帮助与形参提示增强
- **目标**：命名实参高亮正确参数；catalog/内建签名重载并集。
- **任务**：
  1. `DreamShaderParameterInfoHandler` 升级：用 `findEnclosingCall` 的实参解析定位 caret 所在实参；实参带 `Name =` 前缀时按 `signature.parameterNames` 大小写不敏感匹配高亮，匹配失败回退逗号位置索引；`updateParameterInfo` 的实时更新走同一实现（单一事实来源，抽到 `DreamShaderSignatureHelpAnalyzer`）。
  2. `DreamShaderCallSignatureResolver.resolveSignatures` 重载展开：declared/imported 层保持首个权威即返回；catalog 层命中后**追加**内建表中 presentableText 不同的签名（catalog 在前），未命中 catalog 时行为不变。
- **验收**：`DreamShaderSignatureHelpAnalyzerTest` 扩展：命名实参 caret 矩阵（`Source = `、`Destination = "Wo`、值位置中间）、混合命名/位置实参回退；重载并集测试（catalog 条目 + 内建表各一行、去重、顺序）；`DreamShaderInlayParameterHintsProviderTest` 零回归。
- **涉及文件**：`DreamShaderSignatureHelp.kt`、`DreamShaderParameterInfoHandler.kt`、`DreamShaderCallSignatureResolver.kt`、对应测试。

### C20（P2）2.0 重构第一批：Extract Variable
- **目标**：`.dss`/`.dsi` 函数体内选中表达式 → 就地提取为局部变量声明。
- **任务**：
  1. 新增 `DreamShader2ExtractVariableHandler`（`RefactoringActionHandler`，注册 `introduceVariableAction`）：选区取表达式文本（无选区时取 caret 所在引用/调用），校验在函数体内且为可提取表达式（构造器/调用/成员访问/字面量/平铺表达式）。
  2. 类型拼写来源：C17 推断 confident 类型 → 反推拼写（`float3`/`bool`/`Texture2D`/struct 名），`Substrate`/`material` 直接用类型名；Unknown 时用构造器/转换的显式拼写兜底，仍不可得则拒绝（错误提示，不产生半成品）。
  3. 声明插入：包住目标表达式的最外层语句之前插入 `Type newName = expr;`，缩进对齐目标语句；原表达式替换为新名。
  4. 就地重命名：`TemplateBuilder` 对新名建可编辑区间（出现处联动，第一批仅替换单处 + 声明处联动）。
- **验收**：平台测试：提取调用/成员/字面量三场景的文档 diff 断言（声明位置、类型拼写、替换正确）；Unknown 且无显式拼写时拒绝且文档不变；1.x 文件不响应。
- **涉及文件**：新 `refactoring/DreamShader2ExtractVariableHandler.kt`、`DreamShaderRefactoringSupportProvider.kt`/plugin.xml 注册、新测试。

### T14（贯穿）测试与文档
- **任务**：各任务卡验收用例；`docs/language-v2-baseline.md` 更新类型推断/签名帮助/后缀模板覆盖映射；CHANGELOG（en/zh）新增 2.2.0 段；README plugin-metadata 同步；`agent-development-plan.md` 追加 M14 段与本计划执行记录；版本号 2.2.0。
- **验收**：`gradlew.bat test` 全绿；文档与实现互检。

**建议实施顺序**：C17 → C19 → C18（批次 1）；C20 → T14（批次 2）。C17 先行因为 C20 的类型拼写依赖它，且是行为修复。

---

## 4. 风险与依赖

- **依赖**：C17 依赖 M11 推断上下文与 M13 的 include 闭包收集（均就绪）；C20 依赖 C17 的类型拼写反推与 M12 rename 安全网（就绪）。
- **风险**：
  - C17.1 改三元折叠可能影响既有依赖「条件类型」行为的测试——已核对现有用例无此依赖，若回归以新语义为准。
  - C19.2 重载并集可能让弹窗出现两行近似签名——按 presentableText 去重且 catalog 在前，可接受。
  - C20 类型拼写反推遇到 `half`/`uint` 等别族时按 Family.spelling 输出，可能与声明拼写不完全一致（`float1` → `float`）——验收允许语义等价拼写，不追求逐字还原。
- **验证命令**：`.\gradlew.bat test --no-configuration-cache`。

---

## 5. 执行记录

（随批次落地补记）
