pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven {
            url = uri("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
            content {
                includeGroup("org.jetbrains.kotlin")
                includeGroupByRegex("org\\.jetbrains\\.kotlin\\..*")
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
            content {
                includeGroup("org.jetbrains.kotlin")
                includeGroupByRegex("org\\.jetbrains\\.kotlin\\..*")
            }
        }
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroup("com.github.erfansn")
            }
        }  // For GitHub libs like locale-config-x
        maven {
            url = uri("https://raw.githubusercontent.com/Psiphon-Labs/psiphon-tunnel-core-Android-library/master")
            content {
                includeGroup("ca.psiphon")
            }
        }
    }
}

rootProject.name = "Oblivion"
include(":app")
