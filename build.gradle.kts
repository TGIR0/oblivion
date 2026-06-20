plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.dependency.analysis) apply false
}

spotless {
    kotlin {
        target("app/src/**/*.kt")
        targetExclude("**/build/**")
        ktfmt().googleStyle()
    }
    format("misc") {
        target(
            ".gitignore",
            "*.md",
            "*.properties",
            "*.gradle",
            "*.gradle.kts",
            ".github/**/*.yml",
            ".github/**/*.yaml",
            "gradle/**/*.toml",
            "gradle/**/*.properties"
        )
        targetExclude("**/build/**")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// Spotless 8.7+ supports Configuration Cache - remove explicit incompatibility
// If CC issues arise, re-add: tasks.withType<com.diffplug.gradle.spotless.SpotlessTask>().configureEach { notCompatibleWithConfigurationCache("Spotless CC issue") }

fun findPowerShellExecutable(): String {
    val osName = System.getProperty("os.name").lowercase()
    val isWindows = osName.contains("win")
    return if (isWindows) "powershell" else "pwsh"
}

val psExec = findPowerShellExecutable()

tasks.register<Exec>("versionAudit") {
    description = ""
    workingDir = rootProject.projectDir
    commandLine(psExec, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "version_audit.ps1")
}

tasks.register<Exec>("versionAuditFail") {
    description = ""
    workingDir = rootProject.projectDir
    commandLine(psExec, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "version_audit.ps1", "-FailOnOutdated")
}

tasks.register<Exec>("updateDeps") {
    description = "Check and apply dependency version updates in libs.versions.toml"
    workingDir = rootProject.projectDir
    commandLine(psExec, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "update_deps.ps1", "-Apply")
}

tasks.register<Exec>("checkDeps") {
    description = "Check for available dependency version updates (dry-run)"
    workingDir = rootProject.projectDir
    commandLine(psExec, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "update_deps.ps1")
}
