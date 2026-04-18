param(
  [switch]$FailOnOutdated
)

$ErrorActionPreference = "Stop"

function Get-TomlVersions([string]$path) {
  $versions = @{}
  $inVersions = $false

  foreach ($line in Get-Content $path) {
    if ($line -match '^\s*\[versions\]\s*$') {
      $inVersions = $true
      continue
    }
    if ($inVersions -and $line -match '^\s*\[.+\]\s*$') {
      break
    }
    if (-not $inVersions) {
      continue
    }

    if ($line -match '^\s*([A-Za-z0-9_]+)\s*=\s*\"([^\"]+)\"\s*$') {
      $versions[$matches[1]] = $matches[2]
    }
  }

  return $versions
}

function Get-MavenLatestVersion([string]$baseUrl, [string]$group, [string]$artifact) {
  $path = ($group -replace '\.', '/') + "/$artifact/maven-metadata.xml"
  $url = "$baseUrl/$path"

  $xml = $null
  $lastError = $null
  foreach ($attempt in 1..3) {
    try {
      $xml = Invoke-RestMethod -Uri $url -Method Get
      $lastError = $null
      break
    } catch {
      $lastError = $_
      Start-Sleep -Seconds ([math]::Min(5, $attempt * 2))
    }
  }
  if ($xml -eq $null) {
    throw $lastError
  }

  $latest = $xml.metadata.versioning.latest
  if ($latest) {
    return $latest
  }
  $release = $xml.metadata.versioning.release
  if ($release) {
    return $release
  }

  $versions = $xml.metadata.versioning.versions.version
  if ($versions) {
    return ($versions | Select-Object -Last 1)
  }

  throw "No versions found in metadata: $url"
}

function Get-GradleWrapperVersion([string]$path) {
  if (-not (Test-Path $path)) {
    return $null
  }

  $distributionUrlLine = Get-Content $path | Where-Object { $_ -match '^\s*distributionUrl=' } | Select-Object -First 1
  if (-not $distributionUrlLine) {
    return $null
  }

  $distributionUrl = $distributionUrlLine -replace '^\s*distributionUrl=', ''
  if ($distributionUrl -match 'gradle-([0-9A-Za-z\.\-\+]+)-(bin|all)\.zip') {
    return $matches[1]
  }

  return $null
}

function Get-GradleLatestReleaseCandidateVersion() {
  $url = "https://services.gradle.org/versions/release-candidate"

  $json = $null
  $lastError = $null
  foreach ($attempt in 1..3) {
    try {
      $json = Invoke-RestMethod -Uri $url -Method Get
      $lastError = $null
      break
    } catch {
      $lastError = $_
      Start-Sleep -Seconds ([math]::Min(5, $attempt * 2))
    }
  }
  if ($json -eq $null) {
    throw $lastError
  }

  if ($json.version) {
    return $json.version
  }

  throw "No version found in: $url"
}

$repos = @{
  google       = "https://dl.google.com/dl/android/maven2"
  mavenCentral = "https://repo1.maven.org/maven2"
  plugins      = "https://plugins.gradle.org/m2"
  jitpack      = "https://jitpack.io"
}

$catalogPath = Join-Path $PSScriptRoot "gradle/libs.versions.toml"
if (-not (Test-Path $catalogPath)) {
  throw "Version catalog not found: $catalogPath"
}

$current = Get-TomlVersions $catalogPath

$checks =
  @(
    @{ key = "agp"; repo = "google"; group = "com.android.tools.build"; artifact = "gradle" },
    @{ key = "kotlin"; repo = "mavenCentral"; group = "org.jetbrains.kotlin"; artifact = "kotlin-gradle-plugin" },
    @{ key = "ksp"; repo = "plugins"; group = "com.google.devtools.ksp"; artifact = "com.google.devtools.ksp.gradle.plugin" },
    @{ key = "coreKtx"; repo = "google"; group = "androidx.core"; artifact = "core-ktx" },
    @{ key = "coreSplashscreen"; repo = "google"; group = "androidx.core"; artifact = "core-splashscreen" },
    @{ key = "profileinstaller"; repo = "google"; group = "androidx.profileinstaller"; artifact = "profileinstaller" },
    @{ key = "appcompat"; repo = "google"; group = "androidx.appcompat"; artifact = "appcompat" },
    @{ key = "material"; repo = "google"; group = "com.google.android.material"; artifact = "material" },
    @{ key = "okhttp"; repo = "mavenCentral"; group = "com.squareup.okhttp3"; artifact = "okhttp" },
    @{ key = "mmkv"; repo = "mavenCentral"; group = "com.tencent"; artifact = "mmkv" },
    @{ key = "coroutines"; repo = "mavenCentral"; group = "org.jetbrains.kotlinx"; artifact = "kotlinx-coroutines-android" },
    @{ key = "hilt"; repo = "mavenCentral"; group = "com.google.dagger"; artifact = "hilt-android" },
    @{ key = "firebaseBom"; repo = "google"; group = "com.google.firebase"; artifact = "firebase-bom" },
    @{ key = "composeBom"; repo = "google"; group = "androidx.compose"; artifact = "compose-bom" },
    @{ key = "activityCompose"; repo = "google"; group = "androidx.activity"; artifact = "activity-compose" },
    @{ key = "navigationCompose"; repo = "google"; group = "androidx.navigation"; artifact = "navigation-compose" },
    @{ key = "lifecycle"; repo = "google"; group = "androidx.lifecycle"; artifact = "lifecycle-runtime-compose" },
    @{ key = "hiltNavigationCompose"; repo = "google"; group = "androidx.hilt"; artifact = "hilt-navigation-compose" },
    @{ key = "coil"; repo = "mavenCentral"; group = "io.coil-kt.coil3"; artifact = "coil-compose" },
    @{ key = "leakcanary"; repo = "mavenCentral"; group = "com.squareup.leakcanary"; artifact = "leakcanary-android" },
    @{ key = "googleServices"; repo = "google"; group = "com.google.gms"; artifact = "google-services" },
    @{ key = "firebaseCrashlyticsGradle"; repo = "google"; group = "com.google.firebase"; artifact = "firebase-crashlytics-gradle" },
    @{ key = "firebasePerfPlugin"; repo = "google"; group = "com.google.firebase"; artifact = "perf-plugin" },
    @{ key = "detekt"; repo = "mavenCentral"; group = "io.gitlab.arturbosch.detekt"; artifact = "detekt-gradle-plugin" },
    @{ key = "spotless"; repo = "mavenCentral"; group = "com.diffplug.spotless"; artifact = "spotless-plugin-gradle" },
    @{ key = "dependencyAnalysis"; repo = "mavenCentral"; group = "com.autonomousapps"; artifact = "dependency-analysis-gradle-plugin" },
    @{ key = "localeConfigX"; repo = "jitpack"; group = "com.github.erfansn"; artifact = "locale-config-x" }
  )

$outdated = New-Object System.Collections.Generic.List[object]
$rows = foreach ($c in $checks) {
  $key = $c.key
  $have = $current[$key]
  if (-not $have) {
    continue
  }

  $latest = $null
  $status = $null
  $errText = $null
  try {
    $latest = Get-MavenLatestVersion $repos[$c.repo] $c.group $c.artifact
    $status = if ($have -eq $latest) { "OK" } else { "OUTDATED" }
    if ($status -eq "OUTDATED") {
      $outdated.Add([pscustomobject]@{ key = $key; current = $have; latest = $latest })
    }
  } catch {
    $latest = "<error>"
    $status = "ERROR"
    $errText = $_.Exception.Message
  }

  [pscustomobject]@{
    key     = $key
    current = $have
    latest  = $latest
    status  = $status
    error   = $errText
  }
}

$wrapperPropsPath = Join-Path $PSScriptRoot "gradle/wrapper/gradle-wrapper.properties"
$wrapperVersion = Get-GradleWrapperVersion $wrapperPropsPath
if ($wrapperVersion) {
  $latest = $null
  $status = $null
  $errText = $null
  try {
    $latest = Get-GradleLatestReleaseCandidateVersion
    $status = if ($wrapperVersion -eq $latest) { "OK" } else { "OUTDATED" }
    if ($status -eq "OUTDATED") {
      $outdated.Add([pscustomobject]@{ key = "gradleWrapperRc"; current = $wrapperVersion; latest = $latest })
    }
  } catch {
    $latest = "<error>"
    $status = "ERROR"
    $errText = $_.Exception.Message
  }

  $rows = @($rows) + [pscustomobject]@{
    key     = "gradleWrapperRc"
    current = $wrapperVersion
    latest  = $latest
    status  = $status
    error   = $errText
  }
}

$rows | Sort-Object key | Format-Table -AutoSize

if ($FailOnOutdated -and $outdated.Count -gt 0) {
  Write-Error ("Outdated versions detected: " + (($outdated | ForEach-Object { $_.key }) -join ", "))
  exit 1
}
