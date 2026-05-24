param(
    [string]$BaseFile = "src/main/resources/messages/DreamShaderBundle.properties",
    [string]$ZhFile = "src/main/resources/messages/DreamShaderBundle_zh_CN.properties"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-PropertyKeys {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Properties file not found: $Path"
    }
    $keys = New-Object System.Collections.Generic.HashSet[string]
    Get-Content -LiteralPath $Path | ForEach-Object {
        $line = $_.Trim()
        if ($line.Length -eq 0) { return }
        if ($line.StartsWith("#") -or $line.StartsWith("!")) { return }
        $idxEq = $line.IndexOf("=")
        $idxColon = $line.IndexOf(":")
        $idx = -1
        if ($idxEq -ge 0 -and $idxColon -ge 0) {
            $idx = [Math]::Min($idxEq, $idxColon)
        } elseif ($idxEq -ge 0) {
            $idx = $idxEq
        } elseif ($idxColon -ge 0) {
            $idx = $idxColon
        }
        if ($idx -le 0) { return }
        $key = $line.Substring(0, $idx).Trim()
        if ($key.Length -gt 0) {
            [void]$keys.Add($key)
        }
    }
    return $keys
}

$baseKeys = Get-PropertyKeys -Path $BaseFile
$zhKeys = Get-PropertyKeys -Path $ZhFile

$missingInZh = @($baseKeys | Where-Object { -not $zhKeys.Contains($_) } | Sort-Object)
$extraInZh = @($zhKeys | Where-Object { -not $baseKeys.Contains($_) } | Sort-Object)

if ($missingInZh.Count -eq 0 -and $extraInZh.Count -eq 0) {
    Write-Host "i18n key check passed."
    exit 0
}

if ($missingInZh.Count -gt 0) {
    Write-Host "Missing keys in zh_CN bundle:"
    $missingInZh | ForEach-Object { Write-Host "  - $_" }
}

if ($extraInZh.Count -gt 0) {
    Write-Host "Extra keys in zh_CN bundle:"
    $extraInZh | ForEach-Object { Write-Host "  - $_" }
}

exit 1
