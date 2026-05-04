plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.dependency.analysis) apply false
}

repositories {
    mavenCentral()
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

tasks.withType<com.diffplug.gradle.spotless.SpotlessTask>().configureEach {
    notCompatibleWithConfigurationCache("Spotless is not configuration-cache compatible.")
}

fun findPowerShellExecutable(): String {
    val osName = System.getProperty("os.name").lowercase()
    val isWindows = osName.contains("win")
    return if (isWindows) "powershell" else "pwsh"
}

val psExec = findPowerShellExecutable()

tasks.register<Exec>("versionAudit") {
    workingDir = rootProject.projectDir
    commandLine(psExec, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "version_audit.ps1")
}

tasks.register<Exec>("versionAuditFail") {
    workingDir = rootProject.projectDir
    commandLine(psExec, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "version_audit.ps1", "-FailOnOutdated")
}
