# DreamShaderLang 示例与模式

本页提供可复制的 DreamShaderLang 片段。示例按常见工作流排列：最小材质、共享头文件、Package、函数调用、Graph 语法、UE 节点、`ShaderFunction`、`VirtualFunction`、Substrate 材质，以及 `ShaderLayer` / `ShaderLayerBlend`。

## 1. 最小材质

```c
Shader(Name="DreamMaterials/M_Minimal")
{
    Properties = {
        vec3 Tint = vec3(1.0, 0.2, 0.2);
    }

    Settings = {
        Domain = "UI";
        ShadingModel = "Unlit";
    }

    Outputs = {
        vec3 Color;
        Base.EmissiveColor = Color;
    }

    Graph = {
        Color = Tint;
    }
}
```

## 2. 共享 `.dsh` 头文件

`DShader/Shared/Common.dsh`：

```c
Namespace(Name="Common")
{
    Function BuildPulse(in float t, in vec2 uv, out vec3 result) {
        vec2 p = uv - 0.5;
        float ring = sin(t * 2.0 + length(p) * 12.0) * 0.5 + 0.5;
        result = vec3(ring, ring * 0.5 + 0.1, 1.0 - ring * 0.35);
    }

    Function ApplyTint(in vec3 color, in vec3 tint, out vec3 result) {
        result = color * tint;
    }
}
```

## 3. 在 `.dsm` 中引入 `.dsh`

```c
import "Shared/Common.dsh";

Shader(Name="DreamMaterials/M_Imported")
{
    Properties = {
        vec3 Tint = vec3(1.0, 1.0, 1.0);
    }

    Settings = {
        Domain = "UI";
        ShadingModel = "Unlit";
    }

    Outputs = {
        vec3 Color;
        Base.EmissiveColor = Color;
    }

    Graph = {
        vec2 uv = UE.TexCoord(Index=0);
        float t = UE.Time();
        vec3 pulse;

        Common::BuildPulse(t, uv, pulse);
        Common::ApplyTint(pulse, Tint, Color);
    }
}
```

## 4. 使用 Package 采样纹理

安装 Package 后可以通过 `@scope/package/...` 导入。

```c
import "@typedreammoon/dreamshader-texture/Library/Texture.dsh";

Shader(Name="DreamMaterials/M_TexturePackage")
{
    Properties = {
        Texture2D MainTex = Path(Engine, "/EngineResources/DefaultTexture");
        vec3 Tint = vec3(1.0, 1.0, 1.0);
    }

    Settings = {
        Domain = "UI";
        ShadingModel = "Unlit";
    }

    Outputs = {
        vec3 Color;
        Base.EmissiveColor = Color;
    }

    Graph = {
        vec2 uv = UE.TexCoord(Index=0);
        vec3 sampled;

        Texture::Sample2DRGB(MainTex, uv, sampled);
        Color = sampled * Tint;
    }
}
```

Package 命令见 [Packages.md](Packages.md) 和 [VSCode.md](VSCode.md)。

## 5. `Function` 显式 out 调用

定义：

```c
Function ApplyTint(in vec3 color, in vec3 tint, out vec3 result) {
    result = color * tint;
}
```

调用：

```c
Graph = {
    vec3 src = vec3(1.0, 0.4, 0.2);
    vec3 tint = vec3(0.5, 1.0, 1.0);
    vec3 result;

    ApplyTint(src, tint, result);
}
```

`Function` 不支持 `result = ApplyTint(...)` 这种返回值风格。

## 6. `SelfContained` 函数

普通 `Function` 会生成 `.ush` 并由材质 Custom 节点 include。`SelfContained` / `Inline` 是上游文档里的生成策略概念；当前 Rider 插件语法层仍按普通 `Function` 解析，因此这里使用等价的可解析函数声明示例。

```c
Function Remap01(in float value, out float result) {
    result = saturate(value * 0.5 + 0.5);
}
```

## 7. 变量声明与 brace initializer

```c
Graph = {
    float a;
    float b = 1.0;
    float3 rgb = vec3(1.0, 0.5, 0.2);

    float4 c0 = float4(rgb, b);
    float4 c1 = {rgb, b};
}
```

## 8. 纹理默认值

```c
Properties = {
    Texture2D MainTex = Path(Game, "/Textures/T_Main");
    Texture2D DefaultTex = Path("/Engine/EngineResources/DefaultTexture");
    TextureCube SkyTex = Path(Engine, "/EngineResources/DefaultTextureCube");
}
```

## 9. 使用 `UE.*` 构图

```c
Graph = {
    float2 uv = UE.TexCoord(Index=0);
    float time = UE.Time();
    float pulse = UE.Expression(
        Class="Sine",
        OutputType="float1",
        Input=time);

    Color = vec3(pulse, pulse, pulse);
}
```

## 10. `Graph` 中使用 `if` / `else`

```c
Graph = {
    float2 uv = UE.TexCoord(Index=0);
    float mask = UE.Expression(Class="ComponentMask", OutputType="float1", Input=uv, R=true);

    if (mask > 0.5) {
        Color = vec3(1.0, 0.2, 0.2);
    } else {
        Color = vec3(0.0, 0.0, 0.0);
    }
}
```

## 11. `ShaderFunction`

```c
ShaderFunction(Name="Functions/F_Tint")
{
    Inputs = {
        vec3 InColor;
        vec3 InTint;
    }

    Outputs = {
        vec3 OutColor;
    }

    Settings = {
        Description = "Tint helper";
        ExposeToLibrary = true;
        LibraryCategories = "DreamShader,Color";
    }

    Graph = {
        OutColor = InColor * InTint;
    }
}
```

## 12. `VirtualFunction`

`VirtualFunction` 用来声明项目里已经存在的 `UMaterialFunction`，不会生成或覆盖资产。

```c
VirtualFunction(Name="BufferWriter")
{
    Options = {
        Asset = Path(Plugins.MoonToon, "MaterialFunctions/Buffer/Writer");
        Description = "Existing MoonToon material function";
    }

    Inputs = {
        float3 Color;
        float Alpha;
    }

    Outputs = {
        float3 Result;
    }
}
```

调用：

```c
Graph = {
    float3 written = BufferWriter(Color, 1.0, Output="Result");
}
```

`Asset` 支持 `Path(Game, "...")`、`Path(Engine, "...")`、`Path(Plugin.PluginName, "...")` / `Path(Plugins.PluginName, "...")`，也支持完整 Unreal object path。Material Function 资产右键菜单和 Material Function 编辑器工具栏里的 `DreamShader` 下拉菜单可以复制定义、创建 `.dsh` 定义文件，并复制 `Graph` 调用示例。

## 13. Substrate 材质

绑定 `Base.FrontMaterial` 时生成器会自动设置 `ShadingModel="Substrate"`。不要在同一个 `Shader` 中同时绑定 `Base.FrontMaterial` 和 `Base.MaterialAttributes`。

```c
Shader(Name="DreamMaterials/M_Substrate")
{
    Properties = {
        vec3 BaseColor = vec3(0.8, 0.1, 0.1);
        float Roughness = 0.4;
    }

    Outputs = {
        Substrate Front;
        Base.FrontMaterial = Front;
    }

    Graph = {
        Front = Substrate.Slab(
            DiffuseAlbedo=BaseColor,
            Roughness=Roughness);
    }
}
```

## 14. Substrate `ShaderFunction` / `.dsf`

`Substrate` 可作为 `ShaderFunction` 的输入输出类型。

```c
ShaderFunction(Name="Functions/F_SubstrateTint")
{
    Inputs = {
        Substrate InMaterial;
        vec3 InTint;
    }

    Outputs = {
        Substrate OutMaterial;
    }

    Graph = {
        OutMaterial = InMaterial;
    }
}
```

## 15. Substrate `VirtualFunction`

```c
VirtualFunction(Name="SubstrateBlend")
{
    Options = {
        Asset = Path(Plugins.MoonToon, "MaterialFunctions/Substrate/Blend");
    }

    Inputs = {
        Substrate A;
        Substrate B;
    }

    Outputs = {
        Substrate Result;
    }
}
```

## 16. Substrate escape hatch via `UE.Expression`

`UMaterialExpressionCustom` 不支持 `OutputType="Substrate"`，但其它表达式可以作为 escape hatch。

```c
Graph = {
    Substrate slab = UE.Expression(
        Class="MaterialExpressionSubstrateSlabBSDF",
        OutputType="Substrate");

    Base.FrontMaterial = slab;
}
```

## 17. `ShaderLayer`

`ShaderLayer` 最多一个输入，且若存在必须是 `MaterialAttributes`；必须刚好声明一个 `MaterialAttributes` 输出。

```c
ShaderLayer(Name="Layers/L_Base")
{
    Inputs = {
        MaterialAttributes In;
    }

    Outputs = {
        MaterialAttributes Out;
    }

    Graph = {
        Out = In;
    }
}
```

## 18. `ShaderLayerBlend`

`ShaderLayerBlend` 必须刚好两个 `MaterialAttributes` 输入，并声明一个 `MaterialAttributes` 输出。

```c
ShaderLayerBlend(Name="Layers/LB_Mix")
{
    Inputs = {
        MaterialAttributes Bottom;
        MaterialAttributes Top;
    }

    Outputs = {
        MaterialAttributes Out;
    }

    Graph = {
        Out = Bottom;
    }
}
```
