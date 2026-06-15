# Development Guide

Local build, test, release, Rider action, settings, and troubleshooting notes. Update this when workflows or Gradle requirements change.

## Development

Requirements:
- JDK 17+ (recommended 21)

IDEA/IntelliJ Gradle configuration (project-verified):
- Gradle user home: `J:/Gradle`
- Gradle distribution: `Wrapper`
- Gradle JVM: `Oracle OpenJDK 21.0.2`

### Terminal Build Prerequisite (Important)

This project **must** run Gradle with Java 17+.
Commands now resolve the required Java runtime automatically, so terminal runs **no longer require explicit `JAVA_HOME`/`Path` setup**.
All terminal Gradle operations should pin Gradle user home to `J:/Gradle`.

Quick verification command:
```powershell
$env:GRADLE_USER_HOME="J:/Gradle"
.\gradlew.bat -version
```

Expected checks:
- `gradlew -version` reports JVM `17+` (project-verified with `21.x`)
- `GRADLE_USER_HOME` points to `J:/Gradle`

### Build Commands

Build:
```powershell
$env:GRADLE_USER_HOME="J:/Gradle"; .\gradlew.bat build --no-configuration-cache
```

Test:
```powershell
$env:GRADLE_USER_HOME="J:/Gradle"; .\gradlew.bat test --no-configuration-cache
```

Run plugin in sandbox:
```powershell
$env:GRADLE_USER_HOME="J:/Gradle"; .\gradlew.bat runIde --no-configuration-cache
```

### Marketplace Signing and Publishing (File-Based)

Default local secret directory (project-relative):
- `.secrets/`
- This folder is git-ignored and intended for local signing/publishing credentials only.

Default files read from `.secrets/`:
- `.secrets/jetbrains-chain.crt`
- `.secrets/jetbrains-private.pem`
- `.secrets/jetbrains-private-key-password.txt`
- `.secrets/jetbrains-publish-token.txt`

You can override secret directory with Gradle property:
```powershell
$env:GRADLE_USER_HOME="J:/Gradle"; .\gradlew.bat publishPlugin -PjetbrainsSecretsDir=.private
```

`build.gradle.kts` supports multiple signing input modes, with file mode preferred:
- `JETBRAINS_CERTIFICATE_CHAIN_FILE` / `jetbrainsCertificateChainFile`
- `JETBRAINS_PRIVATE_KEY_FILE` / `jetbrainsPrivateKeyFile`
- `JETBRAINS_PRIVATE_KEY_PASSWORD` / `jetbrainsPrivateKeyPassword`
- `JETBRAINS_PUBLISH_TOKEN` / `jetbrainsPublishToken`
- `JETBRAINS_PRIVATE_KEY_PASSWORD_FILE` / `jetbrainsPrivateKeyPasswordFile`
- `JETBRAINS_PUBLISH_TOKEN_FILE` / `jetbrainsPublishTokenFile`

Compatibility fallback is also enabled:
- `CERTIFICATE_CHAIN_FILE`, `PRIVATE_KEY_FILE`
- `CERTIFICATE_CHAIN`, `PRIVATE_KEY`
- `PRIVATE_KEY_PASSWORD`, `PUBLISH_TOKEN`
- `PRIVATE_KEY_PASSWORD_FILE`, `PUBLISH_TOKEN_FILE`

Local PowerShell example (no `openssl` required):
```powershell
$env:GRADLE_USER_HOME="J:/Gradle"

# Option A: Use project-local .secrets defaults (no extra env vars needed)
.\gradlew.bat signPlugin
.\gradlew.bat publishPlugin

# Option B: Explicit file paths via env vars
$env:JETBRAINS_CERTIFICATE_CHAIN_FILE="C:\secrets\jetbrains-chain.crt"
$env:JETBRAINS_PRIVATE_KEY_FILE="C:\secrets\jetbrains-private.pem"
$env:JETBRAINS_PRIVATE_KEY_PASSWORD_FILE="C:\secrets\jetbrains-private-key-password.txt"
$env:JETBRAINS_PUBLISH_TOKEN_FILE="C:\secrets\jetbrains-publish-token.txt"
.\gradlew.bat signPlugin
.\gradlew.bat publishPlugin
```

If you only have text secrets, save them directly as files (no OpenSSL conversion step):
```powershell
@'
-----BEGIN CERTIFICATE-----
...
-----END CERTIFICATE-----
'@ | Set-Content -Path C:\secrets\jetbrains-chain.crt -NoNewline

@'
-----BEGIN PRIVATE KEY-----
...
-----END PRIVATE KEY-----
'@ | Set-Content -Path C:\secrets\jetbrains-private.pem -NoNewline
```

CI release workflow (`.github/workflows/release.yml`) already uses file mode:
- Reads `CERTIFICATE_CHAIN` and `PRIVATE_KEY` from GitHub Secrets
- Writes them to `$RUNNER_TEMP/*.crt|*.pem`
- Exports `JETBRAINS_CERTIFICATE_CHAIN_FILE` and `JETBRAINS_PRIVATE_KEY_FILE`
- Executes `./gradlew publishPlugin`

### Package Tools Actions (Rider)

Location:
- `Tools` -> `DreamShader` -> `DreamShader Packages`

Available actions:
- Browse Package Store
- Install Package From GitHub
- Update Installed Package
- Remove Installed Package
- Open Packages Folder
- Add Package Index Source
- Remove Package Index Source

### Template Tools Actions (Rider)

Location:
- `Tools` -> `DreamShader` -> `DreamShader Templates`

Available actions:
- Create Material Template
- Create Function Template
- Create Header Template
- Create Package Scaffold

### DreamShader Hub (Rider)

Location:
- `Tools` -> `DreamShader` -> `Open DreamShader Hub`

`DreamShader Hub` provides a one-stop entry for common plugin workflows:
- Open DreamShader settings
- Open Bridge diagnostics panel
- Refresh Bridge diagnostics
- Recompile current/all and clean generated shaders
- Browse package store and install from GitHub
- Open packages folder and add package index source
- Create material/function/header templates and package scaffold

`Browse Package Store` now opens an interactive dialog with:
- search field (`name` / `displayName` / `description` / `tags`)
- package list + detail pane
- package list summary shows install marker, version, and tag preview
- refresh/add source/remove source
- install/update/remove selected package
- install/update/remove run as cancellable background tasks with progress
- installed state marker in list and details
- double-click package to install
- dynamic action enablement by install state
- `Installed only` filter
- `Updates possible only` filter (installed + git available + repository present)
- auto-select and highlight package after install/update

### DreamShader Settings (Rider)

Location:
- `Settings` -> `Tools` -> `DreamShader`

Configurable fields:
- `Project Root`
- `Material Manifest Path`
- `Show DreamShader status bar widget`
- `Enable DreamShader in-editor code lens hints` (controls IntelliJ Code Vision hints)
- `Enable DreamShader parameter inlay hints` (controls inline parameter-name hints before positional function/node arguments)
- `Out Placeholder Suffix` (used by missing `out` argument quick-fix placeholder generation; sanitized to identifier-safe form)
- `Preferred Import Extension` (default extension preference for unsupported import-extension quick-fix ordering: `.dsh` / `.dsf` / `.dsm`)
- `Auto-update preferred extension from quick-fix` (when enabled, applying unsupported import-extension quick-fix persists chosen extension as new preferred default)
- `Import extension quick-fix preview` (live preview line showing fallback preferred extension and localized auto-update status)
- `Hover Docs Overrides` (table-based visual editor for hover text overrides)
  - columns: `Path` and `Content`
  - row actions: `Add Row`, `Remove Row`, `Insert Sample`
  - real-time validation summary shows active entries, ignored lines, duplicate replacements, and first issue location/reason
  - persisted format remains backward-compatible `path.path=value` lines
- `Bridge Recompile Current Command`
- `Bridge Recompile All Command`
- `Bridge Clean Generated Command`

Bridge command placeholders:
- `%file%` = current DreamShader file absolute path
- `%projectRoot%` = project base path
- `%bridgeDir%` = resolved bridge directory path

### One-Liner Commands

AI/terminal recommended one-liners:
```powershell
$env:GRADLE_USER_HOME="J:/Gradle"; .\gradlew.bat build --no-configuration-cache
```

```powershell
$env:GRADLE_USER_HOME="J:/Gradle"; .\gradlew.bat test --no-configuration-cache
```

```powershell
$env:GRADLE_USER_HOME="J:/Gradle"; .\gradlew.bat runIde --no-configuration-cache
```

### Quick Troubleshooting

- If build reports JVM below 17:
```powershell
$env:GRADLE_USER_HOME="J:/Gradle"
.\gradlew.bat -version
```
Reopen terminal and run again, then confirm Gradle wrapper/IDE settings are synced.

- If Rider IDE can build but terminal cannot:
Rider uses configured Gradle JVM.
Terminal may use a different runtime context.
Verify terminal with `.\gradlew.bat -version`, and keep `$env:GRADLE_USER_HOME="J:/Gradle"` for consistency.
