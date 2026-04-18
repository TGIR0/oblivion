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
