<#
.SYNOPSIS
  Audits version catalog (gradle/libs.versions.toml) against live Maven metadata.

.DESCRIPTION
  Compares each dependency version against the highest available on Maven/Gradle.
  Reports OK / OUTDATED / ERROR per entry and optionally exits with code 1 when
  any outdated dependency is found (useful for CI).

.PARAMETER FailOnOutdated
  Exit with code 1 if any outdated dependency is detected.

.PARAMETER Quiet
  Suppress coloured console output (plain text only).
#>

param(
  [switch]$FailOnOutdated,
  [switch]$Quiet
)

#region helpers — version comparison & HTTP retry

function Assert-NotQuiet($text, $fg) {
  if (-not $Quiet) { Write-Host $text -ForegroundColor $fg }
}

function Invoke-WithRetry {
  param([string]$Uri, [int]$MaxAttempts = 3, [int]$TimeoutSec = 15)
  $lastErr = $null
  for ($i = 1; $i -le $MaxAttempts; $i++) {
    try {
      return Invoke-RestMethod -Uri $Uri -Method Get -TimeoutSec $TimeoutSec
    } catch {
      $lastErr = $_
      if ($i -lt $MaxAttempts) { Start-Sleep -Seconds ($i * 2) }
    }
  }
  throw $lastErr
}

function Compare-Version {
  param([string]$a, [string]$b)
  $partsA = $a -split '[-.]'
  $partsB = $b -split '[-.]'
  $min = [Math]::Min($partsA.Length, $partsB.Length)

  for ($i = 0; $i -lt $min; $i++) {
    $na = 0; $nb = 0
    $aNum = [int]::TryParse($partsA[$i], [ref]$na)
    $bNum = [int]::TryParse($partsB[$i], [ref]$nb)
    if ($aNum -and $bNum) {
      if ($na -gt $nb) { return 1 }
      if ($na -lt $nb) { return -1 }
    } else {
      $cmp = [string]::Compare($partsA[$i], $partsB[$i], [StringComparison]::OrdinalIgnoreCase)
      if ($cmp -ne 0) { return $cmp }
    }
  }

  if ($partsA.Length -eq $partsB.Length) { return 0 }

  if ($partsA.Length -gt $partsB.Length) { $longer = $partsA } else { $longer = $partsB }
  $extra = $longer[$min]
  $isPreRelease = $extra -match '^(alpha|beta|rc|m|pre|dev|snapshot)'

  if ($partsA.Length -gt $partsB.Length) {
    if ($isPreRelease) { return -1 } else { return 1 }
  }
  if ($isPreRelease) { return 1 } else { return -1 }
}

#endregion

#region version catalog reader

function Get-TomlVersions {
  param([string]$Path)
  $result = @{}
  $inSection = $false
  try {
    foreach ($line in Get-Content $Path) {
      if ($line -match '^\s*\[versions\]\s*$') { $inSection = $true; continue }
      if ($inSection -and $line -match '^\s*\[') { break }
      if (-not $inSection) { continue }
      if ($line -match '^\s*([A-Za-z0-9_]+)\s*=\s*\"([^"]+)\"\s*$') {
        $result[$matches[1]] = $matches[2]
      }
    }
  } catch {
    throw "Failed to parse version catalog: $Path"
  }
  return $result
}

function Get-MavenHighestVersion {
  param(
    [string]$BaseUrl,
    [string]$Group,
    [string]$Artifact,
    [switch]$IncludePrerelease
  )
  $path = ($Group -replace '\.', '/') + "/$Artifact/maven-metadata.xml"
  $xml = Invoke-WithRetry "$BaseUrl/$path"
  $versions = $xml.metadata.versioning.versions.version
  if (-not $versions) { throw "No versions in metadata: ${Group}:${Artifact}" }
  if ($versions -is [string]) { $versions = @($versions) }
  $filtered = $versions | Where-Object { $_ -match '^[0-9]' }
  if (-not $IncludePrerelease) {
    $filtered = $filtered | Where-Object {
      $_ -notmatch '(?i)(alpha|beta|(^|[-.])rc[0-9]*|(^|[-.])m[0-9]+|eap|preview|dev|snapshot|milestone)'
    }
  }
  if (-not $filtered) { throw "No eligible numeric versions in metadata: ${Group}:${Artifact}" }
  $max = $filtered[0]
  foreach ($v in $filtered) {
    if ((Compare-Version $v $max) -gt 0) { $max = $v }
  }
  return $max
}

function Get-GradleReleaseVersion {
  param([string]$Channel)
  $json = Invoke-WithRetry "https://services.gradle.org/versions/$Channel"
  if ($json.version) { return $json.version }
  throw "No version field in Gradle API response ($Channel)"
}

function Get-GradleWrapperVersion {
  param([string]$Path)
  if (-not (Test-Path $Path)) { return $null }
  $raw = Get-Content $Path -Raw
  if ($raw -match 'gradle-([0-9A-Za-z\.\-\+]+)-(bin|all)\.zip') {
    return $matches[1]
  }
  return $null
}

#endregion

#region repo definitions

$script:repos = @{
  google       = "https://dl.google.com/dl/android/maven2"
  mavenCentral = "https://repo1.maven.org/maven2"
  plugins      = "https://plugins.gradle.org/m2"
}

$script:checkDefs = @(
  @{ key = "agp";                repo = "google";       group = "com.android.tools.build";          artifact = "gradle" }
  @{ key = "kotlin";             repo = "mavenCentral";  group = "org.jetbrains.kotlin";             artifact = "kotlin-gradle-plugin" }
  @{ key = "ksp";                repo = "plugins";       group = "com.google.devtools.ksp";           artifact = "com.google.devtools.ksp.gradle.plugin" }
  @{ key = "coreKtx";            repo = "google";        group = "androidx.core";                    artifact = "core-ktx" }
  @{ key = "glide";              repo = "mavenCentral";  group = "com.github.bumptech.glide";         artifact = "glide" }
  @{ key = "timber";             repo = "mavenCentral";  group = "com.jakewharton.timber";            artifact = "timber" }
  @{ key = "composeBom";         repo = "google";        group = "androidx.compose";                 artifact = "compose-bom" }
  @{ key = "activityCompose";    repo = "google";        group = "androidx.activity";                artifact = "activity-compose" }
  @{ key = "navigationCompose";  repo = "google";        group = "androidx.navigation";              artifact = "navigation-compose" }
  @{ key = "lifecycle";          repo = "google";        group = "androidx.lifecycle";               artifact = "lifecycle-runtime-compose" }
  @{ key = "hiltNavigationCompose"; repo = "google";     group = "androidx.hilt";                    artifact = "hilt-navigation-compose" }
  @{ key = "okhttp";             repo = "mavenCentral";  group = "com.squareup.okhttp3";             artifact = "okhttp" }
  @{ key = "mmkv";               repo = "mavenCentral";  group = "com.tencent";                      artifact = "mmkv" }
  @{ key = "coroutines";         repo = "mavenCentral";  group = "org.jetbrains.kotlinx";            artifact = "kotlinx-coroutines-android" }
  @{ key = "hilt";               repo = "mavenCentral";  group = "com.google.dagger";                artifact = "hilt-android" }
  # Detekt 2.x remains pre-release but is the AGP 9 built-in-Kotlin compatible line.
  # This build-only exception is tracked by DETEKT-AGP9-001 in WARNING_WAIVERS.md.
  @{ key = "detekt";             repo = "mavenCentral";  group = "dev.detekt";                       artifact = "detekt-gradle-plugin"; allowPreRelease = $true }
  @{ key = "spotless";           repo = "mavenCentral";  group = "com.diffplug.spotless";            artifact = "spotless-plugin-gradle" }
  @{ key = "dependencyAnalysis"; repo = "mavenCentral";  group = "com.autonomousapps";               artifact = "dependency-analysis-gradle-plugin" }
)

#endregion

#region main

$root = if ($PSScriptRoot) { $PSScriptRoot } else { "." }
$catalogPath = Join-Path $root "gradle/libs.versions.toml"
if (-not (Test-Path $catalogPath)) { throw "Version catalog not found: $catalogPath" }

$current = Get-TomlVersions -Path $catalogPath
$outdated = [System.Collections.Generic.List[object]]::new()
$auditErrors = [System.Collections.Generic.List[object]]::new()
$results = [System.Collections.Generic.List[object]]::new()

Assert-NotQuiet "`n  Checking $(@($checkDefs).Count) packages ...`n" Cyan

$padLen = ($checkDefs | ForEach-Object { $_.key.Length } | Measure-Object -Maximum).Maximum + 2

foreach ($c in $checkDefs) {
  $have = $current[$c.key]
  if (-not $have) { continue }

  $row = [PSCustomObject]@{ Key = $c.key; Current = $have; Latest = ""; Status = ""; Error = "" }

  try {
    $latest = Get-MavenHighestVersion -BaseUrl $repos[$c.repo] -Group $c.group -Artifact $c.artifact -IncludePrerelease:($c.allowPreRelease -eq $true)
    $row.Latest = $latest
    $cmp = Compare-Version $have $latest
    if ($cmp -lt 0) {
      $row.Status = "OUTDATED"
      $outdated.Add($row)
      Assert-NotQuiet ("  {0,-$padLen} {1,-12} → {2,-16} <--" -f $c.key, $have, $latest) Yellow
    } else {
      $row.Status = "OK"
      Assert-NotQuiet ("  {0,-$padLen} {1,-12} ✓ {2,-16}" -f $c.key, $have, $latest) Green
    }
  } catch {
    $row.Latest = "<?>"
    $row.Status = "ERR"
    $row.Error = $_.Exception.Message
    $auditErrors.Add($row)
    Assert-NotQuiet ("  {0,-$padLen} {1,-12} ✗ {2}" -f $c.key, $have, $row.Error) Red
  }
  $results.Add($row)
}

# ── Gradle wrapper ────────────────────────────────────────────────
$wrapperPath = Join-Path $root "gradle/wrapper/gradle-wrapper.properties"
$wrapperVer = Get-GradleWrapperVersion -Path $wrapperPath

if ($wrapperVer) {
  foreach ($channel in @("current", "release-candidate")) {
    $label = if ($channel -eq "current") { "gradleStable" } else { "gradleRc" }
    $row = [PSCustomObject]@{ Key = $label; Current = $wrapperVer; Latest = ""; Status = ""; Error = "" }
    try {
      $latest = Get-GradleReleaseVersion -Channel $channel
      $row.Latest = $latest
      $cmp = Compare-Version $wrapperVer $latest
      if ($cmp -lt 0) {
        $row.Status = "OUTDATED"
        $outdated.Add($row)
        Assert-NotQuiet ("  {0,-$padLen} {1,-12} → {2,-16} <--" -f $label, $wrapperVer, $latest) Yellow
      } else {
        $row.Status = "OK"
        Assert-NotQuiet ("  {0,-$padLen} {1,-12} ✓ {2,-16}" -f $label, $wrapperVer, $latest) Green
      }
    } catch {
      if ($channel -eq "release-candidate") {
        $row.Latest = "—"
        $row.Status = "N/A"
        Assert-NotQuiet ("  {0,-$padLen} {1,-12} - {2}" -f $label, $wrapperVer, "(no RC available)") Gray
      } else {
        $row.Latest = "<?>"
        $row.Status = "ERR"
        $row.Error = $_.Exception.Message
        Assert-NotQuiet ("  {0,-$padLen} {1,-12} ✗ {2}" -f $label, $wrapperVer, $row.Error) Red
      }
    }
    $results.Add($row)
  }
}

# ── Summary table ─────────────────────────────────────────────────
  Write-Host
$table = $results | Sort-Object Key | ForEach-Object {
  $sym = switch ($_.Status) {
    "OK"       { " ✓ " }
    "OUTDATED" { " ⚠ " }
    "ERR"      { " ✗ " }
    "N/A"      { " – " }
    default    { " ? " }
  }
  [PSCustomObject]@{
    Package = $sym + $_.Key
    Current = $_.Current
    Latest  = if ($_.Latest) { $_.Latest } else { "" }
    Status  = $_.Status
    Info    = $_.Error
  }
}
$table | Format-Table -AutoSize -Property Package, Current, Latest, Status, Info

if ($auditErrors.Count -gt 0) {
  Assert-NotQuiet ("  $($auditErrors.Count) audit error(s); dependency freshness is unknown.") Red
} elseif ($outdated.Count -gt 0) {
  Assert-NotQuiet ("  $($outdated.Count) outdated / $($results.Count) checked") Yellow
} else {
  Assert-NotQuiet ("  All $($results.Count) dependencies up to date.") Green
}

if ($FailOnOutdated -and ($outdated.Count -gt 0 -or $auditErrors.Count -gt 0)) {
  $outdatedNames = ($outdated | ForEach-Object { $_.Key }) -join ", "
  $errorNames = ($auditErrors | ForEach-Object { $_.Key }) -join ", "
  Write-Error "Dependency audit failed. Outdated: [$outdatedNames]. Errors: [$errorNames]."
  exit 1
}

exit 0

#endregion
