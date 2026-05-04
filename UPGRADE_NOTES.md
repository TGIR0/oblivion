# Upgrade Notes

This file records dependency/toolchain upgrades and any required rollbacks or workarounds.

## 2026-02-19

### Upgraded
- Kotlin: `2.2.10` → `2.3.20-RC` (`gradle/libs.versions.toml`)
- KSP: `2.3.5` → `2.3.6` (`gradle/libs.versions.toml`)
- Kotlin compiler args: `-Xjvm-default=all` (deprecated) → `-jvm-default=enable` (`build.gradle`, `app/build.gradle`)
- Added Kotlin EAP repository (restricted to `org.jetbrains.kotlin*`) to support Kotlin `2.3.20-RC` artifacts (`settings.gradle`)

### Notes
- Removed the gradle-versions-plugin (`dependencyUpdates`) because it crashes with `java.util.ConcurrentModificationException` on this Gradle/AGP stack. Use `version_audit.ps1` instead.
- `detekt-gradle-plugin` currently triggers a Gradle 9 deprecation warning (`ReportingExtension.file(String)`), so it must be updated before moving to Gradle 10.

## 2026-04-25 (initial changes)

### Upgraded

#### Core Toolchain
- Kotlin: `2.3.20-RC` → `2.4.0` (later reverted, see final corrections)
- KSP: `2.3.6` → `2.4.0-1.0.31` (later aligned with Kotlin downgrade, see final corrections)
- AGP: `8.14.0` → `9.1.0` (`gradle/libs.versions.toml`)
- Gradle: `9.2.1` → `9.3.1` (wrapper + `gradle-wrapper.properties`)
- JDK: `21` → `25` (toolchain JVM, daemon JVM, `gradle.properties`)
- NDK: `29.0.14206865` (unchanged, but verified on AGP 9)

#### AndroidX & Compose
- Compose BOM: `2026.02.00` → `2026.04.00`
- Navigation Compose: `2.9.7` → `2.10.0-alpha01`
- Activity Compose: `1.13.0-alpha01` → `1.13.0-alpha02`
- Core KTX: `1.18.0-rc01` → `1.18.0`
- Lifecycle: `2.10.0` (unchanged)
- AppCompat: `1.7.1` → `1.7.2`
- Material Design: `1.14.0-alpha09` → `1.14.0-alpha10`
- ConstraintLayout: `2.2.1` (unchanged)
- Fragment: `1.8.9` (unchanged)
- Core Splashscreen: `1.2.0` (unchanged)
- ProfileInstaller: `1.4.1` (unchanged)

#### Plugin Dependencies
- Google Services: `4.4.4` → `4.4.5`
- Firebase Plugins: Crashlytics `3.0.6` → `3.0.7`; Perf `2.0.2` → `2.0.3`
- Detekt: `1.23.8` → `1.24.0`
- Spotless: `8.2.1` → `8.3.0`
- Dependency Analysis: `3.5.1` → `3.6.0`

#### Core Libraries
- Hilt: `2.59.2` (unchanged)
- OkHttp: `5.3.2` (unchanged)
- Coroutines: `1.10.2` (unchanged)
- MMKV: `2.3.0` (unchanged here; later bumped, see final corrections)
- Coil: `3.3.0` → `3.4.0-alpha01`
- LeakCanary: `3.0-alpha-8` → `3.0-alpha-9`

### Massive Java to Kotlin Migration
- Converted all remaining Java source files to Kotlin, including:
  - `ApplicationLoader` (now fully Hilt-aware, with proper error isolation and Timber logging)
  - `BaseActivity`, `StateAwareBaseActivity`
  - All activities: `MainActivity`, `SettingsActivity`, `LogActivity`, `SplashScreenActivity`, `SplitTunnelActivity`, `InfoActivity`
  - All adapters: `BypassListAppsAdapter`, `EndpointsBottomSheet`, `SplitTunnelOptionsAdapter`
  - Utility classes: `FileManager`, `SystemUtils`, `ColorUtils`, `LocaleManager`, `HostPortParser`, etc.
  - Custom views: `Icon`, `TouchAwareSwitch`
  - DNS and network modules: `NetworkModule`, `DnsUriParserTest`, `DnsExecutionPlannerTest`, etc.
  - Build-time classes: `FileExistsValueSource`, `OptionalPropertiesValueSource`
  - Removed all Java sources; project is now fully Kotlin-based.
- Updated `app/build.gradle` to use `alias(libs...)` exclusively for plugins and dependencies.
- Removed deprecated `vectorDrawables.useSupportLibrary` and redundant `buildConfig` flag from `app/build.gradle`.
- Migrated release signing configuration from hardcoded values to external `keystore.properties` (gitignored), with a `validateReleaseSigning` task.
- Rewrote gomobile tasks (`buildTun2socksAar`) using `providers.exec()` for Configuration Cache compatibility.
- Switched `FileManager.initialize` call from `BaseActivity` to `ApplicationLoader` to avoid redundant initialisation.
- Refactored `FileManager` into a singleton `object`, added `Keys` constants, and replaced manual locking with `ReentrantReadWriteLock`.
- Centralised all VPN configuration building in `FileManager.getVpnConfig()` under a single read-lock for consistency.

### Lint & Code Quality
- Lint baseline (`lint-baseline.xml`) completely emptied after fixing all reported issues:
  - Updated dependency versions.
  - Removed unused resources (drawables, colors, strings, dimensions/styles, arrays).
  - Fixed icon density issues and removed redundant `png` copies in favour of `anydpi` vector drawables.
  - Replaced `LinearLayout` in `toast.xml` with a single `TextView` using compound drawables.
  - Removed overdraw on root elements by relying on theme backgrounds.
  - Hardcoded endpoint text moved to string resources.
- Upgraded `detekt.yml` to the latest official baseline, updated rule names and properties.
- **Deleted the lint baseline file entirely** (zero lint warnings now).
- Removed `gradle-versions-plugin` because it crashes on this Gradle/AGP stack.
  Use `version_audit.ps1` instead.

### ProGuard / R8
- Rewrote `proguard-rules.pro` with modern best practices:
  - Enabled line number retention and source file renaming for Crashlytics visibility.
  - Added `-assumenosideeffects` to strip `android.util.Log` calls in release builds.
  - Retained native library rules for `tun2socks` and `go`.

### Build Infrastructure
- Updated `gradle.properties`:
  - JVM args: `-Xmx2048M` (from 1536M); removed obsolete `-XX:MaxPermSize`.
  - Enabled parallel builds (`org.gradle.parallel=true`).
  - Set `org.gradle.warning.mode=all`.
  - Added `android.builtInKotlin=true` for AGP 9.x (though it is the default, explicit is safer).
  - `android.enableR8.fullMode=true` kept for clarity (default since AGP 8).
- Updated wrapper scripts (`gradlew`, `gradlew.bat`, `gradle-wrapper.properties`) to match the current Gradle wrapper version.
- Updated `devshell.nix` and `flake.nix` to target JDK 26 and Go 1.26 for future‑proofing (the project still compiles with JDK 25).
- Updated `version_audit.ps1` to compare against the **latest stable** Gradle version (not RCs), avoiding false positives.
- Revised `.gitignore` to track new build artifacts and Kotlin caches properly.

### Configuration & App Manifest
- Overhauled `AndroidManifest.xml`:
  - Added `android:foregroundServiceType="dataSync|specialUse"` and the corresponding `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` to the VPN service (required since Android 14).
  - Removed the dangerous `QUERY_ALL_PACKAGES` permission; kept only safe `<queries>` entries.
  - Ensured compatibility with Predictive Back gesture on Android 16+.
  - Removed deprecated `usesCleartextTraffic` attribute; network security is now controlled exclusively via `networkSecurityConfig`.
- Redesigned `dns_providers.json` into a structured catalog (`pinned`, `verified`, `community`) with Iranian providers, `selectionWeight` fields, and health‑check rules for the custom DNS engine.
- Resolved Git conflict in `dimens.xml`, keeping all spacing and radius tokens with proper documentation.

### Core Tunnel (Go) — tun2socks.go
- Complete rewrite of the tunnel core for safety and resource hygiene:
  - Eliminated global mutable state; all lifecycle is now managed by a `Tunnel` struct.
  - Added `Start`/`Wait`/`Stop` non‑blocking semantics with proper goroutine draining.
  - Added panic recovery in the main worker goroutine with full stack‑trace logging.
  - Capturing stdout/stderr is done with `os.Pipe`; both are restored cleanly on shutdown.
  - Used `sync.Pool` for zero‑allocation log pipeline buffers.
  - Input validation (`validateOptions`) reports all errors at once using `errors.Join`.
  - All file descriptors and goroutines are guaranteed to be released before `Stop` returns.

## 2026-04-25 (final corrections — post‑audit)

### Version Corrections
- **Kotlin:** `2.4.0` reverted to `2.3.21` — `2.4.0` is not yet released (planned June‑July 2026).
- **KSP:** `2.4.0-1.0.31` → `2.3.21-1.0.31` (aligned with Kotlin downgrade).
- **Gradle:** `9.3.1` → `9.4.1` — latest stable as of late April 2026.
- **MMKV:** `2.3.0` → `2.4.0` (released March 2026).
- **Coil:** `3.4.0-alpha01` → `3.4.0` (stable release).
- **Navigation Compose:** `2.10.0-alpha01` → `2.9.7` (reverted to stable; the alpha is not recommended for production).
- **Spotless:** `8.3.0` → `8.4.0` (latest release).
- **Go (devshell):** `1.25` → `1.26.1` (latest stable; aligned with `devshell.nix`).
- **JDK (devshell):** `25` → `26` (aligned with `devshell.nix`; the project’s compilation toolchain remains on JDK 25 until full validation).

### Config Adjustments
- Added `android.builtInKotlin=true` to `gradle.properties` (explicit for AGP 9.x).
- Updated `Gradle_Playbook.md` and `devshell.nix` to reflect final version numbers.
- Finalised `tun2socks.go` with all reviewed improvements (race‑condition fix, panic recovery, stdout/stderr capture hygiene, resource‑pool cleanup).
- Updated `flake.nix` to pin NDK 29.0.14206865 (matching the project) and JDK 26.

### Notes
- Kotlin `2.4.0` should be adopted when it reaches stable (target: mid‑2026).
- Gradle `9.4.1` officially supports JDK 26, but the project’s compilation toolchain remains on JDK 25 until all dependencies are verified against JDK 26.
- The dependency analysis plugin (`com.autonomousapps.dependency-analysis`) stays at `3.6.0` — no newer stable version available.
- All Java files have been deleted and the project is now **100% Kotlin** (including `buildSrc` and tests). No Java source remains.