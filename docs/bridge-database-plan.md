# Bridge SQLite Database Integration Plan

## Background

The upstream Unreal plugin (TypeDreamMoon/DreamShader v1.5.0) writes Bridge diagnostics to **three** transports simultaneously:

| Transport | Path | Format |
|-----------|------|--------|
| JSON file | `Saved/DreamShader/Bridge/diagnostics.json` | Aggregated JSON |
| Directory | `Saved/DreamShader/Bridge/Diagnostics/*.json` | Per-file JSON + `index.json` |
| **SQLite database** | `Saved/DreamShader/Bridge/bridge.db` | SQLite with `diagnostics` + `meta` tables |

The IDE plugin currently only reads the JSON file transport. This plan covers adding SQLite database read support.

## SQLite Schema (from upstream)

```sql
CREATE TABLE IF NOT EXISTS diagnostics (
    path TEXT PRIMARY KEY,
    json TEXT NOT NULL,
    updated_at_utc TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS meta (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);
```

Each row maps a source file path (e.g. `DShader/Materials/M_Minimal.dsm`) to a JSON blob containing the diagnostics array for that file. The JSON shape per row matches the existing `diagnostics.json` per-file group format:

```json
{
  "path": "DShader/Materials/M_Minimal.dsm",
  "diagnostics": [
    {
      "message": "...",
      "code": "generate-error",
      "severity": "error",
      "line": 10,
      "column": 1,
      ...
    }
  ]
}
```

## Technology Choice: JetBrains Exposed + SQLite JDBC

| Consideration | Decision |
|---------------|----------|
| ORM | JetBrains Exposed (same vendor, Kotlin-native DSL) |
| SQLite driver | `org.xerial:sqlite-jdbc` (standard JDBC driver) |
| JDBC module | `org.jetbrains.exposed:exposed-jdbc` |
| Core module | `org.jetbrains.exposed:exposed-core` |

**Why Exposed over raw JDBC:**
- Kotlin-idiomatic DSL, zero string-SQL for schema mapping
- Table objects are compile-time type-safe
- Transaction management is built-in
- Lightweight (no Hibernate-style session factories)
- IntelliJ ships a bundled Exposed plugin for code completion/inspections

**Why NOT:**
- The IntelliJ Platform already bundles a subset of SQLite support. But Exposed's docs explicitly support SQLite via JDBC, and adding `exposed-core` + `exposed-jdbc` + `sqlite-jdbc` is clean and maintainable.
- The schema is trivial (2 tables, no joins, no migrations) — raw JDBC would also work, but Exposed maps more naturally to the existing Kotlin codebase style.

## Implementation Plan

### Step 1: Gradle Dependency Setup

**File:** `build.gradle.kts`

```kotlin
dependencies {
    // ... existing
    implementation("org.jetbrains.exposed:exposed-core:1.3.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:1.3.1")
    implementation("org.xerial:sqlite-jdbc:3.49.1.0")
}
```

No `exposed-dao` module needed — the DSL API is sufficient for read operations.

### Step 2: Database Schema Definition

**New file:** `bridge/DreamShaderBridgeDatabase.kt`

```kotlin
package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

internal object BridgeDiagnosticsTable : Table("diagnostics") {
    val path = text("path")
    val json = text("json")
    val updatedAtUtc = text("updated_at_utc")

    override val primaryKey = PrimaryKey(path)
}

internal object BridgeMetaTable : Table("meta") {
    val key = text("key")
    val value = text("value")

    override val primaryKey = PrimaryKey(key)
}
```

This mirrors the upstream schema exactly. No write operations needed — read-only from the IDE side.

### Step 3: Database Connection Setup

**New file:** `bridge/DreamShaderBridgeDatabaseConnection.kt` (or add to the schema file)

```kotlin
package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

import org.jetbrains.exposed.sql.Database
import java.io.File

internal object DreamShaderBridgeDatabaseConnection {
    private var connected: Database? = null
    private var connectedToPath: String? = null

    fun connect(dbPath: String): Database {
        val normalizedPath = File(dbPath).absolutePath.replace('\\', '/')
        if (connectedToPath == normalizedPath && connected != null) {
            return connected!!
        }
        disconnect()
        val db = Database.connect(
            url = "jdbc:sqlite:$normalizedPath",
            driver = "org.sqlite.JDBC"
        )
        connected = db
        connectedToPath = normalizedPath
        return db
    }

    fun disconnect() {
        connected = null
        connectedToPath = null
    }

    fun isConnected(): Boolean = connected != null
}
```

Key design decisions:
- Connection is lazily established and cached per path
- Path normalization avoids duplicate connections to the same database
- SQLite WAL mode is already set by the Unreal plugin; no need to set PRAGMAs here
- Disconnect is called explicitly when bridge directory changes

### Step 4: Database Reader

**New file:** `bridge/DreamShaderBridgeDatabaseReader.kt`

```kotlin
package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.transactions.transaction

internal object DreamShaderBridgeDatabaseReader {

    private val jsonParser = Json { ignoreUnknownKeys = true }

    fun readDiagnostics(
        dbPath: String?,
        sourceFileFilter: String? = null
    ): List<DreamShaderBridgeDiagnostic> {
        if (dbPath.isNullOrBlank() || !java.io.File(dbPath).exists()) {
            return emptyList()
        }

        return try {
            DreamShaderBridgeDatabaseConnection.connect(dbPath)
            transaction {
                val query = if (sourceFileFilter != null) {
                    BridgeDiagnosticsTable.selectAll()
                        .where { BridgeDiagnosticsTable.path eq sourceFileFilter }
                } else {
                    BridgeDiagnosticsTable.selectAll()
                }

                query.flatMap { row ->
                    val rawJson = row[BridgeDiagnosticsTable.json]
                    parseDiagnosticsFromJson(rawJson)
                }.toList()
            }
        } catch (e: Exception) {
            // Silently fall back — database is optional, file-based transport is the primary path
            emptyList()
        }
    }

    fun readUpdatedAt(dbPath: String?): String? {
        if (dbPath.isNullOrBlank() || !java.io.File(dbPath).exists()) {
            return null
        }
        return try {
            DreamShaderBridgeDatabaseConnection.connect(dbPath)
            transaction {
                BridgeMetaTable.selectAll()
                    .where { BridgeMetaTable.key eq "diagnostics.updatedAt" }
                    .singleOrNull()
                    ?.get(BridgeMetaTable.value)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseDiagnosticsFromJson(rawJson: String): List<DreamShaderBridgeDiagnostic> {
        return try {
            val element = jsonParser.parseToJsonElement(rawJson)
            val diagnosticsArray = element.jsonObject["diagnostics"]?.jsonArray ?: return emptyList()
            diagnosticsArray.mapNotNull { parseDiagnostic(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseDiagnostic(element: JsonElement): DreamShaderBridgeDiagnostic? {
        val obj = element.jsonObject
        val path = obj["path"]?.jsonPrimitive?.contentOrNull ?: return null
        val message = obj["message"]?.jsonPrimitive?.contentOrNull ?: return null
        return DreamShaderBridgeDiagnostic(
            sourcePath = path,
            line = obj["line"]?.jsonPrimitive?.intOrNull ?: 1,
            column = obj["column"]?.jsonPrimitive?.intOrNull ?: 1,
            severity = obj["severity"]?.jsonPrimitive?.contentOrNull ?: "error",
            message = message
        )
    }
}
```

### Step 5: Integrate into Diagnostics Repository

**Modify:** `bridge/DreamShaderBridgeDiagnosticsRepository.kt`

Update the `refresh()` method to try database first, falling back to file:

```kotlin
internal fun refresh(activeFile: VirtualFile?): DreamShaderBridgeDiagnosticsSnapshot {
    val bridgeDirectory = DreamShaderBridgePathResolver.resolveBridgeDirectory(project, activeFile)

    // Try SQLite database first (upstream v1.5.0+ primary transport)
    val dbPath = if (bridgeDirectory != null) {
        File(bridgeDirectory, "bridge.db").takeIf { it.exists() }?.path
    } else null

    val diagnosticsFromDb = DreamShaderBridgeDatabaseReader.readDiagnostics(dbPath)

    if (diagnosticsFromDb.isNotEmpty()) {
        val snapshot = DreamShaderBridgeDiagnosticsSnapshot(
            diagnostics = diagnosticsFromDb,
            loadedFromPath = dbPath
        )
        cachedSnapshot = snapshot
        return snapshot
    }

    // Fall back to JSON file (legacy/unreal pre-1.5.0 transport)
    // ... existing file-reading logic ...
}
```

### Step 6: Connection Lifecycle Management

- **Connect:** When `refresh()` is called on `DreamShaderBridgeDiagnosticsRepository`
- **Disconnect:** When the bridge directory is invalidated (project close, setting change)
- **Hook:** Use `DreamShaderBridgeFileWatcher` (already exists) to detect changes to `bridge.db`
- **Add to file watcher:** Include `bridge.db` in the watched files list

### Step 7: Settings Integration (Optional)

Add a preference in `DreamShaderProjectSettings`:

| Field | Default | Description |
|-------|---------|-------------|
| `preferDatabaseBridge` | `true` | Prefer SQLite database over JSON file for bridge diagnostics |

This allows users to fall back to file-based transport if database access fails.

### Step 8: Tests

**New file:** `bridge/DreamShaderBridgeDatabaseReaderTest.kt`

```kotlin
class DreamShaderBridgeDatabaseReaderTest : BasePlatformTestCase() {
    fun testReadsDiagnosticsFromDatabase()
    fun testReadsUpdatedAtFromMeta()
    fun testReturnsEmptyWhenDatabaseMissing()
    fun testReturnsEmptyWhenDatabaseEmpty()
    fun testSingleFileFilterWorks()
}
```

Test approach: create temporary SQLite database in test sandbox, insert known rows, verify reader output.

### Dependency Addition Summary

```
org.jetbrains.exposed:exposed-core:1.3.1     ~120 KB (JDK only)
org.jetbrains.exposed:exposed-jdbc:1.3.1     ~40 KB (JDK only)
org.xerial:sqlite-jdbc:3.49.1.0              ~12 MB (includes native SQLite binaries)
```

Total plugin size increase: ~12 MB, mostly the native SQLite JNI driver.

## Implementation Order

```
Step 1  ── Gradle dependencies           (1 file, build.gradle.kts)
Step 2  ── Schema definition             (1 new file)
Step 3  ── Connection setup              (1 new file, or merge with step 2)
Step 4  ── Database reader               (1 new file)
Step 5  ── Repository integration        (modify existing file)
Step 6  ── File watcher update           (1-2 line change)
Step 7  ── Settings (optional)           (2 files)
Step 8  ── Tests                         (1 new test file)
```

## Risk Assessment

| Risk | Mitigation |
|------|-----------|
| sqlite-jdbc native library conflicts with IntelliJ's bundled SQLite | Use `ClassLoader` isolation; Exposed JDBC uses standard JDBC driver loading |
| Database locked by Unreal Editor while reading | SQLite WAL mode allows concurrent reads during writes |
| Large databases (>50MB) cause UI hangs | Read on background thread, cache results as `DreamShaderBridgeDiagnosticsSnapshot` |
| Plugin size increase from sqlite-jdbc | The native driver JAR contains compiled `.so`/`.dll` files per platform; can strip non-Windows binaries for Windows-only distribution |
