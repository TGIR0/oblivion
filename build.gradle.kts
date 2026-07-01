plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.dependency.analysis) apply false
    alias(libs.plugins.cyclonedx)
    alias(libs.plugins.kotlin.serialization) apply false
}

// ── Dependency Locking (reproducible builds) ──────────────────
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.STRICT
}

// ── Spotless (code formatting) ────────────────────────────────
spotless {
    kotlin {
        target(fileTree("app/src") { include("**/*.kt") })
        ktfmt().googleStyle()
    }
    format("misc") {
        target(
            fileTree(rootDir) {
                include(
                    ".gitignore",
                    "*.md",
                    "*.properties",
                    "*.gradle",
                    "*.gradle.kts",
                    ".github/**/*.yml",
                    ".github/**/*.yaml",
                    "gradle/**/*.toml",
                    "gradle/**/*.properties",
                )
                exclude("**/build/**", "**/.gradle/**")
            }
        )
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// ── PowerShell helper ─────────────────────────────────────────
fun findPowerShellExecutable(): String {
    val osName = System.getProperty("os.name").lowercase()
    return if (osName.contains("win")) "powershell" else "pwsh"
}

val psExec = findPowerShellExecutable()

tasks.register<Exec>("versionAudit") {
    description = "Audit dependency versions"
    workingDir = rootProject.projectDir
    commandLine(psExec, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "version_audit.ps1")
}

tasks.register<Exec>("versionAuditFail") {
    description = "Audit and fail on outdated dependencies"
    workingDir = rootProject.projectDir
    commandLine(psExec, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "version_audit.ps1", "-FailOnOutdated")
}

tasks.register<Exec>("updateDeps") {
    description = "Apply dependency version updates in libs.versions.toml"
    workingDir = rootProject.projectDir
    commandLine(psExec, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "update_deps.ps1", "-Apply")
}

tasks.register<Exec>("checkDeps") {
    description = "Check for available dependency version updates (dry-run)"
    workingDir = rootProject.projectDir
    commandLine(psExec, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "update_deps.ps1")
}
