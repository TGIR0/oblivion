plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.firebase.perf) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.dependency.analysis) apply false
}

repositories {
    mavenCentral()
}

spotless {
    java {
        target("app/src/**/*.java")
        targetExclude("**/build/**")
        googleJavaFormat()
    }
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

subprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            freeCompilerArgs.addAll(
                "-jvm-default=enable",
                "-opt-in=kotlin.RequiresOptIn"
            )
        }
    }
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
