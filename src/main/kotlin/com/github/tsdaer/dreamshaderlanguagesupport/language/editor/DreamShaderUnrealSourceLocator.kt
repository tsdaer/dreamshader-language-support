package com.github.tsdaer.dreamshaderlanguagesupport.language.editor
import java.io.File

/**
 * Best-effort 定位 Unreal Engine 源码位置，用于自动填充 `unrealEngineSourceRoot`。
 *
 * 引擎既可能是从 GitHub clone 的源码版，也可能是 Launcher 发行版里额外下载了
 * 引擎源码的安装；两者都带 `Engine/Source`。因此探测不依赖目录命名约定，而是
 * 顺着工程文件指向的引擎位置走：
 *
 * 1. `.sln`：Unreal 生成的解决方案里，引擎子项目以相对路径引用引擎目录
 *    （如 `..\UnrealEngine\UntoonEngine\Engine\...`）。取最常见的 `...\Engine\`
 *    前缀，去掉末尾 `Engine` 即引擎根，相对 sln 目录解析为绝对路径。
 * 2. `.uproject`：读 `EngineAssociation`。GUID 形态查
 *    `HKCU\Software\Epic Games\Unreal Engine\Builds`（自定义 / 源码引擎）；
 *    版本号形态查 `HKLM\SOFTWARE\EpicGames\Unreal Engine\<ver>\InstalledDirectory`
 *    （Launcher 发行版）。
 *
 * 全部读盘 / 起子进程以 [runCatching] 包裹，失败静默回退，绝不抛出。
 */
internal object DreamShaderUnrealSourceLocator {
    /** 匹配 sln 中形如 `..\A\B\Engine\` 的引擎相对路径，捕获到 `Engine` 之前的部分。 */
    private val SLN_ENGINE_PATH_REGEX = Regex(
        """([.][.][\\/][^"=\r\n]*?[\\/])Engine[\\/]""",
        RegexOption.IGNORE_CASE
    )
    private val ENGINE_ASSOCIATION_REGEX = Regex(
        """"EngineAssociation"\s*:\s*"([^"]*)"""",
        RegexOption.IGNORE_CASE
    )
    private val GUID_REGEX = Regex("""\{?[0-9A-Fa-f]{8}-([0-9A-Fa-f]{4}-){3}[0-9A-Fa-f]{12}\}?""")

    /**
     * 相对引擎根、由窄到宽的扫描目录候选。命中第一个存在的即作为 `sourceRoot`，
     * 让扫描范围尽量小（材质表达式头文件就在 Materials 目录里）；不同 UE 版本 /
     * 模块拆分后该目录可能在 `Classes/Materials` 或 `Public/Materials`，都没有时
     * 回退到整个 `Engine/Source`。
     */
    private val SCAN_ROOT_PREFERENCES = listOf(
        "Engine/Source/Runtime/Engine/Classes/Materials",
        "Engine/Source/Runtime/Engine/Public/Materials",
        "Engine/Source"
    )

    /**
     * 探测到的一个引擎候选。
     *
     * @param engineRoot 引擎根（`Engine` 的父目录）。
     * @param sourceRoot 推荐填入设置的扫描根：优先指向最窄的 `.../Materials` 目录，
     *   该目录不存在时回退到 `<engineRoot>/Engine/Source`。
     * @param version 引擎版本（来自 Launcher 版本号或 `Build.version`），未知时为 null。
     */
    data class Candidate(
        val engineRoot: String,
        val sourceRoot: String,
        val version: String?
    )

    /**
     * 从 [start] 起定位引擎源码。先试 `.sln`，再回退到 `.uproject` → 注册表。
     *
     * 返回去重后的候选列表，sln 来源优先。
     */
    fun locate(start: File?): List<Candidate> {
        val dir = start?.let { resolveStartDir(it) } ?: return emptyList()
        val candidates = mutableListOf<Candidate>()
        val seen = linkedSetOf<String>()

        fun add(candidate: Candidate?) {
            if (candidate != null && seen.add(candidate.engineRoot)) candidates += candidate
        }

        findFilesByExtension(dir, "sln").forEach { sln ->
            locateFromSln(sln).forEach(::add)
        }
        if (candidates.isEmpty()) {
            findFilesByExtension(dir, "uproject").forEach { uproject ->
                add(locateFromUproject(uproject))
            }
        }
        return candidates
    }

    /** 解析 sln 文本里的引擎相对路径，返回校验过 `Engine/Source` 存在的候选。 */
    fun locateFromSln(sln: File): List<Candidate> {
        val text = runCatching { sln.readText() }.getOrNull() ?: return emptyList()
        val base = sln.parentFile ?: return emptyList()
        val counts = linkedMapOf<String, Int>()
        SLN_ENGINE_PATH_REGEX.findAll(text).forEach { match ->
            val prefix = match.groupValues[1]
            counts[prefix] = (counts[prefix] ?: 0) + 1
        }
        // 以出现次数降序尝试，命中第一个 Engine/Source 存在的引擎根即返回。
        return counts.entries
            .sortedByDescending { it.value }
            .mapNotNull { (prefix, _) ->
                val engineRoot = runCatching {
                    File(base, prefix.replace('\\', File.separatorChar)).canonicalFile
                }.getOrNull() ?: return@mapNotNull null
                candidateAt(engineRoot)
            }
            .distinctBy { it.engineRoot }
    }

    /** 读 uproject 的 EngineAssociation，经注册表解析出引擎根。 */
    fun locateFromUproject(uproject: File): Candidate? {
        val text = runCatching { uproject.readText() }.getOrNull() ?: return null
        val association = ENGINE_ASSOCIATION_REGEX.find(text)?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotBlank() } ?: return null
        val engineRoot = if (GUID_REGEX.matches(association)) {
            queryRegistry(
                "HKCU\\Software\\Epic Games\\Unreal Engine\\Builds",
                association
            )
        } else {
            queryRegistry(
                "HKLM\\SOFTWARE\\EpicGames\\Unreal Engine\\$association",
                "InstalledDirectory"
            )
        } ?: return null
        val dir = runCatching { File(engineRoot).canonicalFile }.getOrNull() ?: return null
        val sourceRoot = preferredScanRoot(dir)
        val versionFromBuild = parseBuildVersion(File(dir, "Engine/Build/Build.version"))
        val version = versionFromBuild ?: association.takeUnless { GUID_REGEX.matches(it) }
        return Candidate(normalize(dir.path), sourceRoot, version)
    }

    /** 若 [dir] 含 `Engine/Source` 则作为引擎根返回候选，否则 null。 */
    fun candidateAt(dir: File): Candidate? {
        val hasSource = runCatching { File(dir, "Engine/Source").isDirectory }.getOrDefault(false)
        if (!hasSource) return null
        return Candidate(
            engineRoot = normalize(dir.path),
            sourceRoot = preferredScanRoot(dir),
            version = parseBuildVersion(File(dir, "Engine/Build/Build.version"))
        )
    }

    /** 在引擎根下按 [SCAN_ROOT_PREFERENCES] 挑第一个存在的扫描目录。 */
    private fun preferredScanRoot(engineRoot: File): String {
        val chosen = SCAN_ROOT_PREFERENCES.firstOrNull { relative ->
            runCatching { File(engineRoot, relative).isDirectory }.getOrDefault(false)
        } ?: "Engine/Source"
        return normalize(File(engineRoot, chosen).path)
    }

    private fun resolveStartDir(start: File): File? {
        val canonical = runCatching { start.canonicalFile }.getOrNull() ?: start
        return if (runCatching { canonical.isDirectory }.getOrDefault(false)) canonical else canonical.parentFile
    }

    private fun findFilesByExtension(dir: File, extension: String): List<File> {
        return runCatching {
            dir.listFiles { file -> file.isFile && file.extension.equals(extension, ignoreCase = true) }
                ?.sortedBy { it.name }
                ?: emptyList()
        }.getOrDefault(emptyList())
    }

    /**
     * 用系统 `reg query` 读取注册表字符串值（JVM 无原生注册表 API）。
     *
     * 非 Windows、`reg` 不可用或键 / 值不存在时返回 null。
     */
    private fun queryRegistry(key: String, valueName: String): String? {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) return null
        val output = runCatching {
            val process = ProcessBuilder("reg", "query", key, "/v", valueName)
                .redirectErrorStream(true)
                .start()
            val text = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            text
        }.getOrNull() ?: return null
        // 输出形如：    ValueName    REG_SZ    C:\Path\To\Engine
        val line = output.lineSequence().firstOrNull {
            it.contains(valueName, ignoreCase = true) && it.contains("REG_")
        } ?: return null
        val marker = Regex("""REG_[A-Z_]+""").find(line) ?: return null
        return line.substring(marker.range.last + 1).trim().takeIf { it.isNotBlank() }
    }

    /** 从 `Build.version` JSON 抠出 `Major.Minor`（带非零 Patch 时为 `Major.Minor.Patch`）。 */
    private fun parseBuildVersion(file: File): String? {
        val text = runCatching { file.readText() }.getOrNull() ?: return null
        val major = intField(text, "MajorVersion") ?: return null
        val minor = intField(text, "MinorVersion") ?: return null
        val patch = intField(text, "PatchVersion")
        return if (patch != null && patch != 0) "$major.$minor.$patch" else "$major.$minor"
    }

    private fun intField(text: String, field: String): Int? =
        Regex("\"$field\"\\s*:\\s*(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull()

    private fun normalize(path: String): String = path.replace('\\', '/').trimEnd('/')
}
