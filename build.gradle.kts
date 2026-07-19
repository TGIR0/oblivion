import java.nio.file.Files
import java.nio.file.attribute.DosFileAttributeView
import org.gradle.api.tasks.Delete

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.dependency.analysis) apply false
    alias(libs.plugins.cyclonedx) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// ── Dependency Locking (reproducible builds) ──────────────────
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.STRICT
}

// Go marks downloaded module-cache files read-only. Restore writability inside the
// project build directory so `gradlew clean` remains reliable on Windows.
val localGoModuleCache = layout.buildDirectory.dir("go-mod-cache")
tasks.named<Delete>("clean") {
    doFirst {
        val cache = localGoModuleCache.get().asFile
        if (cache.isDirectory) {
            if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
                Files.walk(cache.toPath()).use { paths ->
                    paths.forEach { path ->
                        Files.getFileAttributeView(path, DosFileAttributeView::class.java)
                            ?.setReadOnly(false)
                    }
                }
            } else {
                cache.walkBottomUp().forEach { entry -> entry.setWritable(true, false) }
            }
        }
    }
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
                    "scripts/**/*.py",
                    "scripts/**/*.sh",
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

tasks.register<Exec>("verifyCorePins") {
    description = "Verify exact native core source pins without network access"
    workingDir = rootProject.projectDir
    commandLine(
        psExec,
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        "native/verify-core-upstreams.ps1",
    )
}

tasks.register<Exec>("verifyCoreUpstreamDrift") {
    description = "Compare locked native core refs with their authoritative remotes"
    workingDir = rootProject.projectDir
    commandLine(
        psExec,
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        "native/verify-core-upstreams.ps1",
        "-CheckRemote",
    )
}

tasks.register<Exec>("verifyUsquePatch") {
    description = "Prepare the pinned usque source and verify the Android protection patch"
    workingDir = rootProject.projectDir
    val script = layout.projectDirectory.file("native/usque/prepare-usque.ps1")
    val patch = layout.projectDirectory.file("native/usque/oblivion-android.patch")
    val lifecyclePatch = layout.projectDirectory.file("native/usque/oblivion-lifecycle.patch")
    val clientPatch =
        layout.projectDirectory.file("native/usque/oblivion-cloudflare-client.patch")
    val lock = layout.projectDirectory.file("native/core-upstreams.json")
    val work = layout.buildDirectory.dir("native/usque")
    inputs.files(script, patch, lifecyclePatch, clientPatch, lock)
    outputs.file(work.map { it.file("verified.json") })
    commandLine(
        psExec,
        "-NoProfile",
        "-NonInteractive",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        script.asFile.absolutePath,
        "-WorkDir",
        work.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("verifyWarpPlusPatch") {
    description = "Prepare the pinned warp-plus source and verify the Android client patch"
    workingDir = rootProject.projectDir
    val script = layout.projectDirectory.file("native/warp-plus/prepare-warp-plus.ps1")
    val patch = layout.projectDirectory.file("native/warp-plus/oblivion-client.patch")
    val lock = layout.projectDirectory.file("native/core-upstreams.json")
    val work = layout.buildDirectory.dir("native/warp-plus")
    inputs.files(script, patch, lock)
    outputs.file(work.map { it.file("verified.json") })
    commandLine(
        psExec,
        "-NoProfile",
        "-NonInteractive",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        script.asFile.absolutePath,
        "-WorkDir",
        work.get().asFile.absolutePath,
    )
}
