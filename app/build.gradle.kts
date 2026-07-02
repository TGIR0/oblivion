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
}

group = "org.bepass"
version = "9"

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
    }

    signingConfigs {
        val keystoreProps =
            providers.of(org.bepass.oblivion.gradle.OptionalPropertiesValueSource::class) {
                parameters.file.set(rootProject.layout.projectDirectory.file("keystore.properties"))
            }.get()
        val environmentSigning =
            mapOf(
                "storeFile" to providers.environmentVariable("ANDROID_KEYSTORE_FILE").orNull,
                "storePassword" to
                    providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull,
                "keyAlias" to providers.environmentVariable("ANDROID_KEY_ALIAS").orNull,
                "keyPassword" to providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull,
            ).mapValues { (_, value) -> value?.takeIf { it.isNotBlank() } }
        val providedEnvironmentValues = environmentSigning.values.filterNotNull()
        if (
            providedEnvironmentValues.isNotEmpty() &&
                providedEnvironmentValues.size != environmentSigning.size
        ) {
            throw GradleException("Incomplete Android release-signing environment")
        }
        val signingValues =
            if (keystoreProps.isNotEmpty()) keystoreProps
            else environmentSigning.mapValues { it.value.orEmpty() }.filterValues { it.isNotEmpty() }
        if (signingValues.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(signingValues.getValue("storeFile"))
                storePassword = signingValues.getValue("storePassword")
                keyAlias = signingValues.getValue("keyAlias")
                keyPassword = signingValues.getValue("keyPassword")
            }
        }
    }

    androidResources {
        generateLocaleConfig = true
    }

    lint {
        disable += setOf(
            "EnsureInitializerMetadata",
            "MissingTranslation",
        )
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
            output.outputFileName.set("Oblivion.apk")
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
    inputs.files(nativeSources)
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
    includeConfigs.set(listOf("releaseRuntimeClasspath"))
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
    if (name == "lintAnalyzeDebugUnitTest" || name == "lintAnalyzeDebugAndroidTest") {
        dependsOn("hiltJavaCompileRelease")
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
