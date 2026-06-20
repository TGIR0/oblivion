<#
.SYNOPSIS
  Checks and optionally applies dependency version updates in gradle/libs.versions.toml.

.DESCRIPTION
  Queries live Maven/Gradle metadata for each dependency in the version catalog.
  In dry-run mode (default), lists available updates. With -Apply, writes new
  versions into the TOML file and creates a .bak backup.

.PARAMETER Apply
  Write the new versions into the TOML file (creates .bak backup).

.PARAMETER Quiet
  Suppress coloured console output (plain text only).
#>

param(
  [switch]$Apply,
  [switch]$Quiet
)

$ErrorActionPreference = "Stop"

#region helpers — version comparison & HTTP retry

function Assert-NotQuiet($text, $fg) {
  if (-not $Quiet) {
    if ($fg) { Write-Host $text -ForegroundColor $fg } else { Write-Host $text }
  }
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

#region version catalog reader / writer

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

function Set-TomlVersion {
  param([string]$Path, [string]$Key, [string]$NewValue)
  $content = Get-Content $Path -Raw
  $pattern = "($Key\s*=\s*`")[^`"]+(`")"
  if ($content -notmatch $pattern) { throw "Key '$Key' not found in version catalog" }
  $content = $content -replace $pattern, "`${1}$NewValue`${2}"
  $content | Set-Content $Path -NoNewline
}

function Get-MavenHighestVersion {
  param([string]$BaseUrl, [string]$Group, [string]$Artifact)
  $path = ($Group -replace '\.', '/') + "/$Artifact/maven-metadata.xml"
  $xml = Invoke-WithRetry "$BaseUrl/$path"
  $versions = $xml.metadata.versioning.versions.version
  if (-not $versions) { throw "No versions in metadata: ${Group}:${Artifact}" }
  if ($versions -is [string]) { $versions = @($versions) }
  $filtered = $versions | Where-Object { $_ -match '^[0-9]' }
  if (-not $filtered) { throw "No numeric versions in metadata: ${Group}:${Artifact}" }
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
  @{ key = "detekt";             repo = "mavenCentral";  group = "dev.detekt";                       artifact = "detekt-gradle-plugin" }
  @{ key = "spotless";           repo = "mavenCentral";  group = "com.diffplug.spotless";            artifact = "spotless-plugin-gradle" }
  @{ key = "dependencyAnalysis"; repo = "mavenCentral";  group = "com.autonomousapps";               artifact = "dependency-analysis-gradle-plugin" }
)

#endregion

#region main

$root = if ($PSScriptRoot) { $PSScriptRoot } else { "." }
$catalogPath = Join-Path $root "gradle/libs.versions.toml"
$wrapperPath = Join-Path $root "gradle/wrapper/gradle-wrapper.properties"

if (-not (Test-Path $catalogPath)) { throw "Version catalog not found: $catalogPath" }

$current = Get-TomlVersions -Path $catalogPath
$updates = [System.Collections.Generic.List[object]]::new()

# ── dependencies ──────────────────────────────────────────────────
Assert-NotQuiet "`n=== Checking for updates ===`n" Cyan

foreach ($c in $checkDefs) {
  $have = $current[$c.key]
  if (-not $have) { continue }

  try {
    $latest = Get-MavenHighestVersion -BaseUrl $repos[$c.repo] -Group $c.group -Artifact $c.artifact
  } catch {
    Assert-NotQuiet ("  {0,-24} ERROR ($($_.Exception.Message))" -f "$($c.key) ...") Red
    continue
  }

  if ((Compare-Version $have $latest) -lt 0) {
    Assert-NotQuiet ("  {0,-24} {1,-12} → {2}" -f "$($c.key) ...", $have, $latest) Yellow
    $updates.Add([PSCustomObject]@{ Key = $c.key; Old = $have; New = $latest })
  } else {
    Assert-NotQuiet ("  {0,-24} {1,-12} (up to date)" -f "$($c.key) ...", $have) Green
  }
}

# ── Gradle wrapper ────────────────────────────────────────────────
$wrapperVer = Get-GradleWrapperVersion -Path $wrapperPath

if ($wrapperVer) {
  Assert-NotQuiet "`n=== Gradle wrapper ===`n" Cyan
  try {
    $gradleLatest = Get-GradleReleaseVersion -Channel "current"
    if ((Compare-Version $wrapperVer $gradleLatest) -lt 0) {
      Assert-NotQuiet ("  {0,-24} {1,-12} → {2}" -f "gradle ...", $wrapperVer, $gradleLatest) Yellow
      $updates.Add([PSCustomObject]@{ Key = "gradle"; Old = $wrapperVer; New = $gradleLatest })
    } else {
      Assert-NotQuiet ("  {0,-24} {1,-12} (up to date)" -f "gradle ...", $wrapperVer) Green
    }
  } catch {
    Assert-NotQuiet ("  gradle ... ERROR ($($_.Exception.Message))") Red
  }
}

# ── summary + apply ───────────────────────────────────────────────
if ($updates.Count -eq 0) {
  Assert-NotQuiet "`nAll packages up to date." Green
  exit 0
}

Assert-NotQuiet "`n$($updates.Count) update(s) available." Yellow

if (-not $Apply) {
  Assert-NotQuiet "Re-run with -Apply to write updates." Gray
  $updates | Format-Table -AutoSize -Property Key, Old, New
  exit 0
}

# ── write updates ─────────────────────────────────────────────────
Assert-NotQuiet "`n=== Applying updates ===`n" Cyan
$backupPath = "$catalogPath.bak"
Copy-Item $catalogPath $backupPath -Force

$errors = 0
foreach ($u in $updates) {
  try {
    if ($u.Key -eq "gradle") {
      Assert-NotQuiet ("  gradle wrapper: {0,-12} → {1,-12}  (run: gradlew wrapper --gradle-version $($u.New))" -f $u.Old, $u.New) Yellow
    } else {
      Set-TomlVersion -Path $catalogPath -Key $u.Key -NewValue $u.New
      Assert-NotQuiet ("  {0,-24} {1,-12} → {2,-12}" -f $u.Key, $u.Old, $u.New) Green
    }
  } catch {
    Assert-NotQuiet ("  {0,-24} ✗ {1}" -f $u.Key, $_.Exception.Message) Red
    $errors++
  }
}

if ($errors -eq 0) {
  Assert-NotQuiet "`nBackup: $backupPath" Gray
  Assert-NotQuiet "Done. $($updates.Count) version(s) updated." Green
}

$gradleUpdate = $updates | Where-Object { $_.Key -eq "gradle" } | Select-Object -First 1
if ($gradleUpdate) {
  Assert-NotQuiet "`n>>> To update Gradle wrapper: gradlew wrapper --gradle-version $($gradleUpdate.New)" Yellow
}

exit 0

#endregion
