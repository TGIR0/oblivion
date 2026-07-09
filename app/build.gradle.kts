import com.android.build.api.variant.FilterConfiguration
import org.cyclonedx.gradle.CyclonedxDirectTask
import org.cyclonedx.model.Component

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.cyclonedx)
    alias(libs.plugins.detekt)
}

group = "org.bepass"
version = "9"

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val featureManifestUrl =
    providers
        .gradleProperty("oblivion.featureManifestUrl")
        .orElse(providers.environmentVariable("OBLIVION_FEATURE_MANIFEST_URL"))
        .orElse("")
val featureManifestKeysJson =
    providers
        .gradleProperty("oblivion.featureManifestKeysJson")
        .orElse(providers.environmentVariable("OBLIVION_FEATURE_MANIFEST_KEYS_JSON"))
        .orElse("[]")

android {
    namespace = "org.bepass.oblivion"
    compileSdk = 37
    ndkVersion = "30.0.14904198"

    defaultConfig {
        applicationId = "org.bepass.oblivion"
        minSdk = 24
        targetSdk = 37
        versionCode = 19
        versionName = "9"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = false
        buildConfigField(
            "String",
            "FEATURE_MANIFEST_URL",
            featureManifestUrl.get().asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "FEATURE_MANIFEST_KEYS_JSON",
            featureManifestKeysJson.get().asBuildConfigString(),
        )
    }

    signingConfigs {
        val keystoreProps =
            providers.of(org.bepass.oblivion.gradle.OptionalPropertiesValueSource::class) {
                parameters.file.set(rootProject.layout.projectDirectory.file("keystore.properties"))
            }.get()

        fun environmentSigning(prefix: String): Map<String, String> {
            val values =
                mapOf(
                    "storeFile" to
                        providers.environmentVariable("${prefix}_ANDROID_KEYSTORE_FILE").orNull,
                    "storePassword" to
                        providers.environmentVariable("${prefix}_ANDROID_KEYSTORE_PASSWORD").orNull,
                    "keyAlias" to
                        providers.environmentVariable("${prefix}_ANDROID_KEY_ALIAS").orNull,
                    "keyPassword" to
                        providers.environmentVariable("${prefix}_ANDROID_KEY_PASSWORD").orNull,
                ).mapValues { (_, value) -> value?.takeIf { it.isNotBlank() } }
            val provided = values.values.filterNotNull()
            if (provided.isNotEmpty() && provided.size != values.size) {
                throw GradleException("Incomplete $prefix Android release-signing environment")
            }
            return values.mapValues { it.value.orEmpty() }.filterValues { it.isNotEmpty() }
        }

        mapOf(
                "playRelease" to environmentSigning("PLAY"),
                "ossRelease" to
                    environmentSigning("OSS").ifEmpty {
                        keystoreProps
                    },
            )
            .forEach { (name, signingValues) ->
                if (signingValues.isNotEmpty()) {
                    create(name) {
                        storeFile = rootProject.file(signingValues.getValue("storeFile"))
                        storePassword = signingValues.getValue("storePassword")
                        keyAlias = signingValues.getValue("keyAlias")
                        keyPassword = signingValues.getValue("keyPassword")
                    }
                }
            }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("play") {
            dimension = "distribution"
            applicationId = "org.bepass.oblivion"
            signingConfigs.findByName("playRelease")?.let { signingConfig = it }
        }
        create("oss") {
            dimension = "distribution"
            applicationId = "org.bepass.oblivion.oss"
            versionNameSuffix = "-oss"
            signingConfigs.findByName("ossRelease")?.let { signingConfig = it }
        }
    }

    androidResources {
        generateLocaleConfig = true
    }

    lint {
        warningsAsErrors = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/*.kotlin_module",
            )
        }
    }

    splits {
        abi {
            isEnable =
                providers.gradleProperty("oblivion.abiSplits").map(String::toBoolean).orElse(false)
                    .get()
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = true
        }
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    sourceSets.getByName("main").jniLibs.directories.add(
        layout.buildDirectory.dir("generated/hev/jniLibs").get().asFile.absolutePath
    )

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.outputs.forEach { output ->
            val abi =
                output.filters
                    .firstOrNull { it.filterType == FilterConfiguration.FilterType.ABI }
                    ?.identifier ?: "universal"
            output.outputFileName.set("Oblivion-${variant.name}-$abi.apk")
        }
    }
}

val buildNativeCore =
  tasks.register<Exec>("buildNativeCore") {
    description = "Build the versioned native core AAR from pinned Go sources"
    group = "build"
    val script = rootProject.layout.projectDirectory.file("tun2socks/build-aar.ps1")
    val nativeSources =
      rootProject.fileTree("tun2socks") {
        include("**/*.go", "go.mod", "go.sum", "build-aar.ps1")
      }
    val usqueInputs =
      files(
        rootProject.layout.projectDirectory.file("native/core-upstreams.json"),
        rootProject.layout.projectDirectory.file("native/usque/prepare-usque.ps1"),
        rootProject.layout.projectDirectory.file("native/usque/oblivion-android.patch"),
        rootProject.layout.projectDirectory.file("native/usque/oblivion-lifecycle.patch"),
        rootProject.layout.projectDirectory.file("native/usque/oblivion-cloudflare-client.patch"),
          rootProject.layout.projectDirectory.file("native/warp-plus/prepare-warp-plus.ps1"),
          rootProject.layout.projectDirectory.file("native/warp-plus/oblivion-client.patch"),
          rootProject.layout.projectDirectory.file("native/warp-plus/oblivion-dependencies.patch"),
        )
    inputs.files(nativeSources, usqueInputs)
    outputs.file(layout.projectDirectory.file("libs/tun2socks.aar"))
    workingDir = rootProject.projectDir
    commandLine(
      if (System.getProperty("os.name").lowercase().contains("win")) "powershell" else "pwsh",
      "-NoProfile",
      "-NonInteractive",
      "-ExecutionPolicy",
      "Bypass",
      "-File",
      script.asFile.absolutePath,
    )
  }

val buildHev =
  tasks.register<Exec>("buildHev") {
    description = "Build pinned hev-socks5-tunnel 2.15.0 from source"
    group = "build"
    val script = rootProject.layout.projectDirectory.file("native/hev/build-hev.ps1")
    val configPatch =
        rootProject.layout.projectDirectory.file("native/hev/oblivion-config-in-memory.patch")
    val lifecyclePatch =
        rootProject.layout.projectDirectory.file("native/hev/oblivion-lifecycle.patch")
    val work = layout.buildDirectory.dir("native/hev")
    val output = layout.buildDirectory.dir("generated/hev/jniLibs")
    inputs.files(script, configPatch, lifecyclePatch)
    outputs.dir(output)
    workingDir = rootProject.projectDir
    commandLine(
      if (System.getProperty("os.name").lowercase().contains("win")) "powershell" else "pwsh",
      "-NoProfile",
      "-NonInteractive",
      "-ExecutionPolicy",
      "Bypass",
      "-File",
      script.asFile.absolutePath,
      "-NdkDir",
      androidComponents.sdkComponents.ndkDirectory.get().asFile.absolutePath,
      "-WorkDir",
      work.get().asFile.absolutePath,
      "-OutputDir",
      output.get().asFile.absolutePath,
    )
  }

tasks.named("preBuild").configure { dependsOn(buildNativeCore, buildHev) }

tasks.withType<CyclonedxDirectTask>().configureEach {
    includeConfigs.set(listOf("ossReleaseRuntimeClasspath", "playReleaseRuntimeClasspath"))
    includeBuildEnvironment.set(false)
    includeMetadataResolution.set(true)
    includeLicenseText.set(false)
    includeBomSerialNumber.set(true)
    includeBuildSystem.set(true)
    projectType.set(Component.Type.APPLICATION)
    componentGroup.set("org.bepass")
    componentName.set("oblivion")
    componentVersion.set("9")
}

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_4)
        freeCompilerArgs.addAll(
            "-jvm-default=enable",
            "-opt-in=kotlin.RequiresOptIn",
            "-Xreturn-value-checker=check",
            "-Xname-based-destructuring=only-syntax",
        )
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(26))
    }
}

tasks.withType<JavaCompile>().configureEach {
    // Waiver GENERATED-BUILDCONFIG-001, expires 2026-09-30: AGP emits a leading Javadoc
    // comment that JDK 25 diagnoses as dangling. Project Java sources still use full -Xlint.
    options.compilerArgs.addAll(listOf("-Xlint:all,-dangling-doc-comments", "-Werror"))
    if (name.startsWith("hiltJavaCompile")) {
        // Waiver HILT-PROCESSING-001, expires 2026-09-30: Hilt's generated aggregation
        // annotations intentionally have no claiming processor in this javac round.
        options.compilerArgs.add("-Xlint:-processing")
    }
}

tasks.configureEach {
    if (
        name.startsWith("lintAnalyze") &&
            (name.endsWith("UnitTest") || name.endsWith("AndroidTest"))
    ) {
        // Lint's shared UAST model can include generated roots from both distribution flavors.
        // Materialize both Hilt component trees before any test-source analysis to prevent a
        // cross-variant file-not-found race when OSS and Play lint tasks execute in parallel.
        dependsOn("hiltJavaCompileOssDebug", "hiltJavaCompilePlayDebug")
    }
}

// Dependency locking: pin all transitive versions for reproducible builds
dependencyLocking {
    lockAllConfigurations()
}

configurations.configureEach {
    exclude(group = "org.json", module = "json")
}

dependencies {
    implementation(files("libs/tun2socks.aar"))
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.okhttp)
    implementation(libs.okhttp.dnsoverhttps)
    implementation(libs.mmkv)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okio)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.glide)
    implementation(libs.timber)
    compileOnly(libs.errorprone.annotations)
    ksp(libs.hilt.compiler)
    ksp(libs.kotlin.metadata.jvm)
    ksp(libs.glide.compiler)
    testImplementation(libs.bundles.testing.unit)
    androidTestImplementation(libs.bundles.testing.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
