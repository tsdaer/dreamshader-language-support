<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# dreamshader-language-support 更新日志

## [2.5.0] - 2026-10-07

### Added

- 重构里程碑 M17（计划：`docs/m17-plan.md`）：
  - **2.0 方言的内联函数**（C31）：`Refactoring` 菜单动作（`DreamShader.InlineFunction`）把本文件或 include 闭包中声明的用户函数调用展开为函数体语句。副作用矩阵在写入前完整执行：按值实参必须抗漂移（无副作用运算符、无用户/未知调用），`out`/`inout` 实参必须是朴素且互不别名的变量引用（函数体的形参写入据此写回调用方变量），按值形参在函数体内任何写位出现即拒绝（含成员/索引链），控制转移只允许末尾单一 `return`（语句位丢弃，值位改写到赋值目标或接收声明上），`break`/`continue` 必须封闭在函数体自身的循环内，递归拒绝，函数体局部不得与调用点可见名字重名，调用必须位于整条语句、赋值右侧或声明初始化器位置。被调函数声明保留。
  - **混合 `.dsh` legacy 区段的逐声明补全**（C32）：每个 legacy 块的文本获得一份缓存的 1.x 重解析（C27 结构视图规则共享给补全上下文），legacy 函数体内的光标现在能拿到 `isInDeclarationBody`/`isFunctionLike` 上下文——HLSL 内建、`UE`、局部符号——legacy `Settings` 段内能补设置键；此前按区域粗分的派发在块内一律按顶层回答。纯 V1/V2 文件与 V2 区段保持既有分支逐字节不变。
  - **提取函数的多写出参合并返回**（C33）：两个及以上纯 `out` 值在直线代码中全部合格时（选择区内无分支循环；每个都是同块裸声明且首次出现即写入），helper 返回生成的 `Extracted_Result` 结构体，调用点把字段拆回调用方变量（`A = result.A;`），原裸声明保留。选择区内的读取会把值变成 `inout` 从而拒绝合并；任一条件不满足维持 `out` 矩阵。

## [2.4.0] - 2026-10-06

### Added

- 重构里程碑 M16（计划：`docs/m16-plan.md`）：
  - **2.0 方言的内联变量**（C25）：`Refactoring` 菜单动作（`DreamShader.InlineVariable`）把单声明器局部变量的全部读取处替换为声明带括号的初始化器，并连同 `///` doc 注释一起删除声明。预检在写入前完整执行，以下场景拒绝（提示 + 文档零改动）：缺少初始化器、多声明器、初始化器引用形参/局部/全局（漂移风险）、包含赋值/复合赋值/`++`/`--`、调用用户函数或未知调用、使用处本身是写目标、目标位于 `/// @custom` 体内；作用域之外的同名使用不属于该声明，保持原样。
  - **提取函数的单一返回值**（C26）：当选区恰好写出一个值、从未读取它、区后仍使用、其最终写入是最后一条选中语句（`name = expr;`），且该名字是选区同块内的裸声明（声明与选区间无任何使用）时，helper 以返回值带回而非 `out` 参数——签名 `Type Name(inParams) { …; return name; }`，调用点变为接收声明 `Type name = Name(inParams);`，裸声明行随之删除，联动就地重命名同时覆盖新函数名、调用点名与接收变量名。形参目标、带初始化器或外层声明、存在中间使用的场景维持 `out` 矩阵。
  - **结构视图方言分流与混合 `.dsh` 分段**（C27）：结构视图工厂按方言路由——纯 V2 文件（`.dss`/`.dsi`、纯 V2 的 `.dsh`）走 2.0 模型（此前工厂从未注册 2.0 模型，落到 1.x 模型只剩空视图）；纯 legacy 文件逐字节维持 1.x 模型；混合 `.dsh` 为每个 legacy 块显示一个 Legacy 节点（以块内首个 1.x 声明命名），子节点来自带缓存的文本级 1.x 重扫描，可按绝对偏移导航。
  - **2.0 函数的内联参数提示**（C28）：1.x 解析为空时，本文件与 include 闭包内声明的 V2 用户函数提供参数名；混合 `.dsh` 按光标区域解析（legacy 区域维持 1.x 路径，`@custom` 体内保持为空），命名实参继续抑制提示。

### Changed

- **提取函数 `inout` 修正**（C26）：复合赋值与 `++`/`--` 会读取自身旧值，其目标现在以 `inout` 参数带回而非 `out`（HLSL 语义下 `out` 将读取未定义初值）。
- **`.dsi` 覆盖值的 schema 驱动**（C29）：当 Bridge 符号索引携带有效的父参数 schema 且按名命中覆盖项时，由 schema 类型重推值模板，`StaticSwitch` kind 强制 `true`/`false`（即使覆盖声明拼写是 `int`/`float`），`Texture` kind 保持静默；schema 缺席或名字未命中时维持声明拼写路径不变。
- **流敏感写回类型收窄**（C30）：调用把普通变量传给已声明的 `out`/`inout` 形参（拼写类型自信）后，该变量的推断类型自调用结束起收窄为形参拼写——swizzle 补全与类型不匹配检查都能看到写回后的形状（命名实参按名匹配；更近的重新声明压过写回；形参拼写不可解析时静默）。

## [2.3.0] - 2026-10-06

### Added

- 重构里程碑 M15（计划：`docs/m15-plan.md`）：
  - **2.0 方言的提取函数**（C21）：在 `.dss` 函数体内选中完整语句后，会在当前函数之后插入一个文件级 helper 函数，并把选区替换为调用。签名由选区的数据流合成——选区读取的外部名字成为值参数（取声明类型拼写），选区内写入且区后仍使用的名字以 `out` 参数带回（选区同时读取时为 `inout`，如 `x = x + 1`），新函数名与调用点联动就地重命名。`return`/`break`/`continue`、部分选区、经由成员或索引的赋值、会改变作用域的声明、类型无法解析的名字一律拒绝（提示 + 文档零改动）；解析为函数或命名空间根的名字保持原样而不变成参数。
  - **2.0 方言的内联 Uniform**（C22）：`Refactoring` 菜单动作（`DreamShader.InlineUniform`）把当前 `.dss` 中单声明器 uniform 的全部使用处替换为其带括号的初始化器，并连同 `///` doc 块一起删除声明。写入前逐项预检：`.dsi` 材质实例覆盖（复用重命名安全网）、其他文件中的使用、其他初始化器读取该 uniform、缺少初始化器、多声明器声明——全部拒绝并保持文档不变。

### Changed

- **混合 `.dsh` 补全按光标区域分流**（C23）：HEADER_MIXED 的 `.dsh` 由 2.0 前端解析，光标所在节点决定服务方言——legacy 块由 1.x Contributor 应答，真实 V2 声明（uniform/global/struct/function/pragma/include/doc 块）由 2.0 Contributor 应答，声明之间的顶层空隙同时容纳两方言的关键词集。纯文件在所有偏移保持文件级结论；`.`/`(`/`=` 自动弹窗仅在 V2 区域应用 2.0 词法规则。
- **类型推断切片**（C24）：局部名解析改为词法作用域约束——声明的 scope owner（最近的块，循环初始化则为 `for` 头）必须同时包含使用点，兄弟块内先前的同名局部不再提供虚假自信类型，遮蔽按最内层优先解析；操作数型内建扩展 `normalize`/`min`/`max`/`floor`/`ceil`/`round`/`frac` 以及 `dot`（等形状时取更宽族的标量）与 `cross`（同族 3 向量）；用户声明的函数调用实参按被调形参类型给出弱警告级检查（本文件 + include 闭包解析、命名实参按名对位、仅自信对触发）。

## [2.2.0] - 2026-10-05

### 新增

- 表达式级智能里程碑 M14（计划：`docs/m14-plan.md`）：
  - **2.0 方言的提取变量重构**（C20）：在 `.dss`/`.dsi` 函数体内选中（或将光标落在其后）表达式，在所在语句之前插入带推断类型拼写的局部声明，出现处以去重后的建议名替换；声明处与使用处进入联动就地重命名模板。类型不可知、或位于 if 分支/循环体内（提出会改变求值时机）时给出提示并保持文档原样拒绝。1.x 文件保持平台默认行为。
  - **2.0 方言的后缀模板**（C18）：后缀模板选择器开始服务 `.dss`/`.dsi`——模板候选取最后一个顶层赋值（`=`、`+=` 等）之后的值片段而非整行，`$expr$` 包裹表达式形态的 PSI 节点而非叶子——`if`/`ifel` 首次在 2.0 文件可用；新增四个表达式模板：`lerp(expr, y, s)`、`saturate(expr)`、`expr.Sample()`、`expr.<分量>`。

### 变更

- **表达式类型推断加固**（C17，长期推断目标的首批收口）：三元表达式折叠取分支类型（丢弃条件类型，两分支按标量/向量提升规则合并，右嵌套链由内向外闭合），不再把条件类型当结果；复合赋值保持左侧形状，不再吸收右侧向量形状；用户函数调用经 include 闭包（含非 export helper）解析声明的返回类型，先于工作区导出视图；赋值类目不匹配告警覆盖复合赋值，DSH5293 已报 Substrate 操作数时保持静默不叠报。
- **命名实参感知的签名帮助**（C19）：参数信息的光标位置按实参名匹配对应参数（大小写不敏感，含字符串值内部的光标），`Destination = "Wo` 高亮 `Destination` 而非按逗号计数；仅在实参间隙与名字无匹配时回退位置索引。catalog 签名与同一可调用的文档化内建拼写按重载行并列展示（catalog 在前，按展示文本去重）。

## [2.1.0] - 2026-10-05

### 新增

- 补全智能化第二批（M13 收官，计划：`docs/completion-plan.md`）：
  - **catalog 枚举参数值槽位补全**（C11.2）：`Source = ` / `Source = "Wo` 处对 catalog 标注为 `Enum` 的参数给出文档化基名——向量变换（`UE.TransformVector`）为 `Tangent` 一类基，位置变换（`UE.TransformPosition`）为 `PeriodicWorld`/`TranslatedWorld` 一类基，值来自上游 `Docs/builtins/transform.md`。裸槽位插入带引号值、已开引号内裸插入，非 Enum 参数或闭合值槽位回退标识符补全。

### 变更

- **补全热路径加固**（C15）：1.x 补全上下文改为直接读已提交的 `originalFile` PSI（仅 lexer 级回退），每次弹窗不再全文件重解析；两个可调用候选收集器合并为按 PSI 修订缓存的单趟「import 闭包 + 签名解析」；全项目 FilenameIndex 扫描按项目修订缓存（`import "` 与 `#include "` 补全共享）且 import 候选惰性收集——非 import 上下文零索引访问。
- **引号感知插入矩阵**（D9 收尾）：以真实补全插入测试锁定 `#pragma material` 值的「已键入开引号 / 未键入 × 选值」矩阵（无双引号叠加）；1.x settings 值路径维持裸插入语义。

## [2.0.0] - 2026-10-04

### 新增

- M13 补全智能化里程碑（计划：`docs/completion-plan.md`），消除「按 `.` 后建议混乱」问题：
  - **方言感知补全分流**（C8）：纯 2.0 文件（`.dss`/`.dsi` 与带 V2 标记的 `.dsh`，按解析器同一套分流规则判定并缓存于文件 PSI）中 1.x 补全 Contributor 静默——在 `.dss` 中按 `.` 不再出现 1.x 顶层关键词；混合 `.dsh` 头文件保留 1.x 服务。
  - **接收者类型的 2.0 成员补全**（C9）：按 `.` 之后 2.0 Contributor 按 C2 推断的接收者类型分发——向量的 swizzle（`xyzw`/`rgba` 两组、tail 标注结果形状）、矩阵 `_mRC` 单元格、经符号模型的 struct 字段、贴图 `sample`、`material` 属性、`Substrate` builder pin（catalog 类型 + 离线兜底、排除已写入 pin）、`UE.`/`Substrate.` catalog 成员；未知接收者降级为命名空间根加可调用项，不再倒关键词。
  - **统一补全优先级**（C10）：共享排序表（`DreamShaderCompletionPriorities`）经单一 lookup 元素工厂供给两个 Contributor——2.0 条目不再以 0 优先级沉底，1.x suggester 的优先级字面量同样映射到这套常量。
  - **槽位收紧的命名实参**（C11）：`Name = ` 建议只在参数名槽位（`(` 后或顶层逗号后）出现；值位置与嵌套成员访问回落到标识符补全。
  - **方言感知自动弹出**（C12）：在 2.0 文件中键入 `.` / `(` / `=` 经词法规则自动弹出补全（成员访问、调用实参、`#pragma material` 值槽位），不再走 1.x 分析器重解析。
  - **2.0 基础标识符补全**（C13）：`.dss`/`.dsi` 裸 Ctrl+Space 现在建议光标前声明的局部变量、函数参数、带类型的 uniform/global、struct、文件内与工作区导出的可调用项、类型构造器、HLSL 内建与方言关键词。
  - **`#include` 路径补全**（C14）：2.0 文件中 `#include "` 建议工程内 `.dsh`/`.dss`/`.dsi` 源，字符串内原地替换。
  - **`.dsi` 覆盖值补全**（A10）：覆盖声明值槽位由声明的类型拼写驱动建议——`bool` 给 `true`/`false`，标量给占位值，向量/矩阵给构造器模板；资源类型覆盖保持静默。建议与父 schema 类型按构造一致，且无 Bridge 数据时可用。
  - **1.x 按点收紧**（C16）：graph 上下文中非命名空间接收者只建议作用域内可调用项与局部变量——不再倾倒命名空间根、内建函数与类型构造器。
  - **pragma 值引号修复**（D9 部分）：`#pragma material` 值在已开引号内裸插入（不再出现 `""` 叠加），裸槽位插入带引号形式。

- M12 编辑体验里程碑（上游 `2.0.2` @ `b3bbf48`），插件版本与上游 2.0 对齐：
  - **2.0 上下文补全**（C3）：面向 `.dss`/`.dsi` 的 PSI 驱动补全——catalog `inputs[]` 驱动的命名实参补全、C2 类型驱动的 Substrate builder 成员补全、来自 Bridge 符号索引的 `.dsi` 父参数补全、`#pragma material(...)` 键值补全（Bridge settings.json 优先、离线兜底表次之）。
  - **2.0 语义高亮**（C4）：uniform 参数 / 函数参数 / 局部变量三级配色、显式 `Substrate`/`material` 类型变量专用色、`/// @key` 属性名与 `.dsi` 覆盖名着色，新增五个可配置颜色项。
  - **工作区用法与改名安全网**（C5）：第二个引用搜索执行器服务 stub 化 2.0 声明（排除派生文件、`.dsi` 覆盖声明可随改名同步），改名前影响面检查列出 `.dsi` 覆盖与同名 export/调用点文件。
  - **2.0 结构视图与 Code Vision**（C6）：includes、按 `/// @group` 分组的 uniforms、globals/structs/functions；`.dsi` 显示可导航 Parent 节点；export 函数上方显示引用计数，instance pragma 上方显示可点击的 "Parent: X"。1.x 文件保持既有结构视图不变。
  - **双向导航闭环**（C7）：共享源码行导航器支撑 reveal 失败通知上的「跳转到该行」，成功时把光标落到被揭示的源行；reveal gutter 标记渲染有测试覆盖。
  - **WebSocket 实时预览**（A7）：传输 B 协议——二进制帧信封、24 字节 RGBA8 原始帧头、png 配对规则——以可选回环客户端实现，`setProbe`/`clearProbe` 与活动编辑器光标行联动；文件传输保持默认回退，本地 mock WebSocket 服务器覆盖往返测试。
  - **平台编辑体验特性**（D1–D7）：文件内用法高亮、Surround With、仅重命名的建议式重构、注释行合并、2.0 列表左右移动、语句上下移动、2.0 逗号列表拆合、结构块支持、语言感知 Copy Path（源码根内返回 `/Game/...` 资产路径），以及裸 HLSL 函数体的条件式 HLSL 注入（无 HLSL 语言时静默）。

## [1.5.0] - 2026-10-03

### 新增

- 里程碑 M11 语义智能（上游 `2.0.2` @ `b3bbf48`）：
  - **跨文件符号模型**（C1）：2.0 声明——`uniform`、全局变量、`export`/`extern` 函数（仅工作区可见链接）、`struct` 与 include 边——全部支持 PSI stub，经四个平台 StubIndex 提供查询（以 `stubElementTypeHolder` 接口注册）。`DreamShader2WorkspaceSymbols` 按名字合并 Bridge `dreamshader-symbol-index` v1 工件（权威）与离线 stub 索引兜底：同名时 Bridge 条目遮蔽 stub 条目，Bridge 数据缺失或 schema 不符时静默降级为纯 stub。include 闭包经共享 import 路径语义解析，供下游消费。500 文件规模项目索引与查询在预算内完成。
  - **2.0 方言的保守类型推断**（C2）：覆盖 2.0 PSI 的值类型模型——数值标量/向量/矩阵、纹理种类、`SamplerState`、`material`、`Substrate`、`void` 与用户 struct。支持构造器/类型转换拼写、HLSL 逐分量提升（标量⊕向量仍为向量，`float` 拓宽 `half`，形状不同保持 Unknown）、swizzle 收缩（`float4.rgb` → `float3`）、material 属性类型（`FrontMaterial` → `Substrate`）、纹理 `Sample` → `float4`、catalog 驱动的 `UE.*` 返回类型（含离线兜底目录）、`Substrate.*` → `Substrate`、操作数透传的 `lerp`/`saturate`/`clamp`/`abs`，以及 include 闭包内的 struct/函数。推断不出的即 Unknown，绝不产生诊断。
  - **按上游编号的 Substrate 语法糖检查**（A6）：糖外操作符按 `DSH5293` 报告（`-`、`/`、Substrate × Substrate、一元 `-`、复合赋值，以及把 Substrate 值与数值混合的 `lerp`）；`Substrate.Slab` 冲突实参按 `DSH5295` 报告（全部十一组引脚冲突对，调用与 builder 两种形态）；调用形态的 `Haziness` 缺 `Roughness`、`Thickness` 缺 `Transmittance` 按 `DSH5296` 报告；builder 值在首次使用或整体赋值后的成员写入按 `DSH5297` 报告（`Substrate.Slab()` 重新绑定会重置 builder），声明所在 `if` 之外的分支内成员写入按 `DSH5298` 报告（循环体保持沉默——那是引擎 DSH4383 的辖域）。位置实参跟在命名实参之后按 `DSH2158` 报告。全部检查只读语法树事实或置信推断类型，不依赖 Bridge 数据。
  - **`.dsi` 覆盖对照父材质参数 schema 的检查**（A6）：当 Bridge 符号索引携带有效 `instance.schema` 时，检查未知参数名（`DSH7258`）、仅大小写匹配（`DSH7264`）、类型不匹配（`DSH7259`，含静态开关必须 `@static`、float3 保留父 alpha 两条规则）；仅作为图层/混合参数存在的名字按 `DSH7268` 报告（对照父源码自身的符号索引解析，同级目录优先）。schema 缺失或无效时零诊断——静默降级，绝不误报。
  - **赋值类型不匹配提示**（C2 消费方）：赋值右侧推断类目与目标声明类目不同（数值 vs material/Substrate/纹理/采样器/struct）时给出弱警告。仅警告级——引擎检查仍是权威——bool 与数值之间的合法转换不触发。

### 变更

- 语言基线：`docs/language-v2-baseline.md` 扩充 M11 语义映射（stub 索引、工作区合并、类型模型、A6 检查）。

## [1.4.0] - 2026-10-03

### 新增

- 里程碑 M10 的诊断与工具链（上游 `2.0.2` @ `b3bbf48`）：
  - **DSH 诊断码目录**（A5）：机器可读码表（`messages/dreamshader-dsh-codes.json`），覆盖上游全部 639 个 `DSHnnnn` 码，由可重复执行的 `scripts/generate_dsh_codes.py` 从上游 `Docs/diagnostics/README.md` 表格生成（diff 稳定；精选中文摘要维护在 `scripts/dsh-codes-zh.json`）。携带 DSH 码的 Bridge 诊断在 tooltip 中显示码义（精选码含中文），并通过「Open DSH documentation」Alt+Enter 意图打开上游文档对应页。
  - **按上游编号的预防性检查**（A5）：`.dsm`/`.dsf` 中的 2.0 语法（`uniform` / `export` / `extern` / `#pragma` / `#include`）按 `DSH2248` 报告；`#include <...>` 尖括号写法按 `DSH3203` 报告并附加引号快速修复；既有 `.dsh` 资产块形状检查引用 `DSH2249`。所有检查跳过注释与字符串，绝不在歧义模式上触发。
  - **GLSL 遗留别名提示**（B3）：1.x 文件中的 GLSL 拼写（函数别名 `mix` / `fract` / `mod`，类型别名 `vec3` 系 / `mat` 系，取自引擎遗留别名表）给出引用 `DSH5277` 的弱警告，并附一键替换为 HLSL 名的快速修复。文件内声明了同名函数、注释与字符串不触发；`.dss`/`.dsi` 交给引擎自己的 `DSH4250` 错误。
  - **`.dss` / `.dsi` 文件模板**（A4）：移植上游 `NewMaterial.dss` / `NewFunction.dss` / `NewInstance.dsi` 模板正文与 `{FILENAME}` / `{ASSETPATH}` / `{STEM}` / `{PARENT}` 占位符映射，同时提供 **Tools → DreamShader → Templates** 动作（实例模板带父材质输入对话框）与 **New → DreamShader** 菜单项（2.0 段在前、1.x 块在下，与上游 New 菜单一致）。渲染产物零 error 解析；`DShader/Materials/` 下创建的文件模板为 `/Game/Materials/...`。
  - **`dsc` CLI 工具链集成**（A8）：可配置的 `dsc` 可执行路径（项目级与应用级设置；`.ps1` 路径自动经 PowerShell 包装）启用 **Tools → DreamShader → DreamShader Bridge** 下的：dsc 检查当前文件、dsc 格式化当前文件、dsc 迁移当前文件（先跑 `migrate -Check` 预演，确认后才写入——上游会把 1.x 源移至 `Saved/DreamShader/Migrated/`）、dsc 刷新符号索引。未配置路径时动作禁用并给出提示。
  - **反编译产物支持**（A9）：`Decompiled/` 或 `Intermediate/DreamShader/` 下的源码识别为派生文件——`problemHighlightFilter` 扩展抑制平台对它们的错误高亮（无误报、Inspect Code 批量扫描跳过、手动编辑不受限），`readerModeMatcher` 扩展使其默认进入阅读模式。
  - **未知类型 inspection**（D8，首批 LocalInspectionTool 迁移）：Inputs/Outputs/Properties 未知类型规则从语义 annotator 迁入 `DreamShaderUnknownTypeInspection`（**Settings → Editor → Inspections → DreamShader** 分组）——级别可配置、可开关、纳入 Inspect Code 批量扫描，保留原建议快速修复。annotator 不再报告该规则，不重复。

### 变更

- Bridge 工具窗口（B7）：Message 列以链接色前缀渲染 DSH 码（单元格 tooltip 附码义），新增工具栏按钮打开所选诊断的 DSH 文档，活动文件探测识别 `.dss`/`.dsi`，汇总行始终追加诊断来源标签（修复字符串拼接优先级 bug：存在 info 级诊断时该标签丢失）。
- 设置页新增 `dsc` 可执行路径字段（A8）。

## [1.3.0] - 2026-10-03

### 新增

- DreamShader 编辑器桥接 2.0 工件（里程碑 M9，上游 `2.0.2` @ `b3bbf48`）：
  - `dreamshader-builtin-catalog.json`（2.0 绑定器节点目录，`dsc export-catalog` 亦写出）接入 material-expression 合并链最高优先级（高于 1.x 的 `material-expressions.json`）：类、输入引脚、字面量属性、别名与位置参数顺序驱动 `UE.*` / `Substrate.*` 补全与签名帮助。schema/版本不符时静默降级到下一数据源。
  - `dreamshader-symbol-index` v1 只读仓库：解析 `Saved/DreamShader/Index/<root>/<source>.index.json`（声明、提及、include 闭包、参数 schema、`.dsi` 实例 schema），带 schema 校验与按 (mtime, size) 的重读缓存——为后续跨文件符号模型打数据地基。
  - `reveal-node` 支持：新增「在虚幻材质编辑器中显示节点」动作（`.dss` / `.dsi` 文件），先检查编辑器心跳，经临时文件 + 原子 rename 请求队列写入，轮询 `Responses/<requestId>.json`，在成功行装订 gutter 图标，并透出 `DSH9050`-`DSH9057` 导航失败码。
  - 读取并暴露 `bridge.db` 的 `meta` 表（键值对：生成时间戳，以及编辑器写入的 `protocol` / `pluginVersion` / `engineDir` 行）。
  - `status.json` 心跳与 `owner.lock` 存活模型：状态栏 widget 显示编辑器在线/离线（含编辑器插件版本），材质预览与 reveal-node 的请求写入器拒绝对已死编辑器写请求（上游会直接丢弃）。

### 变更

- 离线兜底升级（B2）：依据上游 `Docs/builtins/ue.md` @2.0.2 内嵌完整 V2 目录（`dreamshader-builtin-catalog-fallback.json`）——27 个注册 `UE.*` 调用加 `UE.StaticSwitchParameter`、`UE.CollectionParam`、`UE.SceneTexture`、`UE.Expression`，与旧 6 条 manifest 合并，离线安装仍可补全全部注册调用。
- 语言基线：`docs/language-v2-baseline.md` 新增桥接工件消费映射（catalog / symbol index / meta / reveal-node / status）。

## [1.2.0] - 2026-10-03

### 新增

- DreamShader 2.0 方言支持（里程碑 M8，上游 `2.0.2` @ `b3bbf48`）：注册 `.dss` 编译单元与 `.dsi` 材质实例文件类型（独立项目图标），与 `.dsm`/`.dsf`/`.dsh` 共享 DreamShader 文件类型。
- 方言模型（`DreamShaderDialect`：`V1` / `V2` / `HEADER_MIXED`），按扩展名 + 内容标记判定方言。标记扫描使用冻结的 1.x lexer，注释与字符串不会造成误判，1.x 的 `#Region` 布局指令仍归 1.x。
- DreamShader 2.0 lexer：独立的 `.` / `,` / `;` 标点 token、`///` 文档注释 token、行首 `#pragma` / `#include` / `#if` 系指令 token、多字符运算符（`==`、`<=`、`+=`、`<<=` 等）、大小写敏感的关键词/类型判定（`export`、`uniform`、`Texture2D`、`Substrate` 等），以及保留原文的字面量（`.5`、`1.f`、`1e3`、`1.5e-3`、`2.5h`、`1u`、`0x1F`、`1L`）。
- DreamShader 2.0 宽容解析器（零 error 元素 PSI）：pragma/include 指令、`///` 文档块、uniform/全局/结构体/函数声明、参数表、完整语句集（块/变量/表达式/`if`/`for`/`while`/`do`/`return`/`break`/`continue`/`discard`/空语句）、调用、命名实参（含 `Pin[0] = ...` 引脚索引形态）、成员/索引链、构造器、类型转换与 `{...}` 初始化列表。`/// @custom` 函数体按原文捕获（括号平衡，对 `$`、`@`、行中 `#` 以及字符串/注释内的花括号完全透明）。
- 混合方言头文件：任一 2.0 标记即走 2.0 前端，旧 1.x 块以不透明块捕获；1.x 文件保持冻结的 1.x 管线不变。
- DreamShader 2.0 语法高亮，新增可配置颜色项（文档注释、预处理指令、标点）。
- 上游 2.0 一致性 fixture：上游 `Tests/Corpus/Lang` 语料库（72 个文件）、三份 `language-v2` 文档示例集、上游 `.dss`/`.dsi` 模板全部纳入测试——有效示例零 error 解析。

### 变更

- 语言基线拆分为双基线：`docs/language-baseline.md`（1.x，冻结）与新增 `docs/language-v2-baseline.md`（2.0 覆盖映射与测试映射）。

## [1.1.0] - 2026-09-01

### 新增

- 支持 DreamShader 1.8 多 Source Root import：项目与各插件的 `DShader` 默认相互隔离，使用 `Project:`、`Plugin.<Name>:` 及兼容的 `Plugins/<Name>:` 显式跨 Root 导入。
- 支持上游 Bridge 诊断的 `code`、`stage`、`detail`、`assetPath`、`source`、shader platform 与 quality level 字段，并在编辑器诊断中显示诊断码。
- 增加插件 Root、显式跨 Root 及路径 containment 回归覆盖。

### 变更

- 语言与集成基线由 DreamShaderLang 1.6.3 更新到 2026-09-01 检查的 DreamShader 1.8.0/当前 main。
- 材质预览请求先写入临时文件，再原子移动到 Bridge 请求队列，避免 Unreal 读取到未写完的 JSON。

### 修复

- DreamShader Root 内的源文件不再能通过 `..` 或绝对路径逃逸 source/packages Root。
- 插件源文件的非限定 import 不再错误地解析到项目 `DShader` 中的同名依赖。
- 修复 IntelliJ 临时/轻量虚拟文件中的 import 声明解析，恢复导入函数的悬浮文档和参数提示。
- 贴图采样与噪声材质生成器不再输出无法解析的 `Builtin/Texture.dsh` / `Builtin/Noise.dsh` import，改为生成自包含的 DreamShader 1.8 语法。

## [1.0.0] - 2026-07-28

### 新增

- `Template` 声明关键字与 `Layout` section 关键字，兼容 DreamShaderLang 1.5.1+ / 1.4.8+ 语法（词法、语法、语言规则、补全）。
- `enableGitHubPackageSearch` 项目级与应用级设置，用于开关 GitHub 包搜索。
- 应用级 `DreamShaderApplicationSettings`，为包索引 URL、GitHub Token、Bridge 命令等提供 IDE 全局回退。

### 修复

- 模板服务现在从项目设置的 `sourceDirectory` 读取路径，不再硬编码 `"DShader"`。
- 诊断按文件缓存改为在文件变更时失效，不再因全局 PSI 修改而全部失效。
- Bridge SQLite 驱动/连接错误现以 WARN 级别记录日志，不再静默吞噬。
- Hub 对话框在点击任意操作按钮后自动关闭。
- 构建：`pluginSinceBuild` 回退值统一为 `261`（原为 `252`）。

### 移除

- 空占位包 `services/`、`startup/`、`toolWindow/`。
- README 中的上游 VS Code 扩展参考链接；文档现已独立。

## [0.0.10] - 2026-07-27

### 修复

- 插件验证器内部 API 警告：将 `PluginManagerCore.getPlugin()` 替换为基于资源的版本和变更日志读取（`dreamshader-plugin.properties` + `CHANGELOG.md`）。

## [0.0.9] - 2026-07-27

### 变更

- 插件 XML：移除可选的 `com.intellij.modules.jcef` 依赖 — 欢迎页已使用 `HTMLEditorProvider`，不再需要。
- 抑制继承自 IntelliJ Platform 基类的剩余不可避免的废弃 API 警告。

### 修复

- 插件验证器针对 `PluginManagerCore.getPlugin(PluginId)` 的内部 API 使用警告。

## [0.0.8] - 2026-07-27

### 变更

- 欢迎页：将自定义 JCEF 管线（`JCEFHtmlPanel` + 自定义 `FileEditorProvider` + `LightVirtualFile`）替换为标准 `HTMLEditorProvider` API，与 JetBrains 官方 whatsnew 页面方案一致。
- 欢迎页链接拦截：从原生 CEF 请求处理器切换为基于 `JsQueryHandler` 的 `window.jbCefQuery()` 通信方式。
- 移除可选的 JCEF 模块依赖及自定义 `config-file` 变通方案 — `HTMLEditorProvider` 内部处理 JCEF 可用性。

### 修复

- 在未加载 JCEF 模块的环境中出现的 `ClassNotFoundException: com.intellij.ui.jcef.JBCefApp` 错误。
- 因自定义 JCEF 初始化和生命周期管理不稳定导致的欢迎页加载失败。

### 移除

- `DreamShaderWelcomeWebView.kt`、`DreamShaderWelcomeVirtualFile`、`DreamShaderWelcomeWebViewProvider`（由 `HTMLEditorProvider` 替代）。
- `src/main/resources/includes/jcef.xml`（不再需要）。
- 冗余的 `forceSelectWelcomeEditor` 基于 Alarm 的编辑器选择重试机制。

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

[2.3.0]: https://github.com/tsdaer/dreamshader-language-support/compare/2.2.0...2.3.0
[2.2.0]: https://github.com/tsdaer/dreamshader-language-support/compare/2.1.0...2.2.0
[2.1.0]: https://github.com/tsdaer/dreamshader-language-support/compare/2.0.0...2.1.0
[2.0.0]: https://github.com/tsdaer/dreamshader-language-support/compare/1.5.0...2.0.0
[1.5.0]: https://github.com/tsdaer/dreamshader-language-support/compare/1.4.0...1.5.0
[1.4.0]: https://github.com/tsdaer/dreamshader-language-support/compare/1.3.0...1.4.0
[1.3.0]: https://github.com/tsdaer/dreamshader-language-support/compare/1.2.0...1.3.0
[1.2.0]: https://github.com/tsdaer/dreamshader-language-support/compare/1.1.0...1.2.0
[1.1.0]: https://github.com/tsdaer/dreamshader-language-support/compare/1.0.0...1.1.0
[1.0.0]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.10...1.0.0
[0.0.10]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.9...0.0.10
[0.0.9]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.8...0.0.9
[0.0.8]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.7...0.0.8
[0.0.7]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.6...0.0.7
[0.0.6]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.5...0.0.6
[0.0.5]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.4...0.0.5
[0.0.4]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.3...0.0.4
[0.0.3]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.2...0.0.3
[0.0.2]: https://github.com/tsdaer/dreamshader-language-support/compare/0.0.1...0.0.2
[0.0.1]: https://github.com/tsdaer/dreamshader-language-support/commits/0.0.1
