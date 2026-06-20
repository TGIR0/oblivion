plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "org.bepass.oblivion"
    compileSdk = 37
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "org.bepass.oblivion"
        minSdk = 24
        targetSdk = 37
        versionCode = 18
        versionName = "8"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystoreProps = providers.of(org.bepass.oblivion.gradle.OptionalPropertiesValueSource::class) {
                parameters.file.set(rootProject.layout.projectDirectory.file("keystore.properties"))
            }
            val props = keystoreProps.get()
            if (props.isNotEmpty()) {
                storeFile = file(props["storeFile"]!!)
                storePassword = props["storePassword"]!!
                keyAlias = props["keyAlias"]!!
                keyPassword = props["keyPassword"]!!
            }
        }
    }

    androidResources {
        generateLocaleConfig = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_26
        targetCompatibility = JavaVersion.VERSION_26
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

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
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

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_26)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_4)
        freeCompilerArgs.addAll(
            "-jvm-default=enable",
            "-opt-in=kotlin.RequiresOptIn",
            "-Xreturn-value-checker=check",
            "-Xname-based-destructuring=only-syntax",
            "-opt-in=kotlin.experimental.collectionLiterals",
        )
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(26))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-deprecation")
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
    implementation(libs.glide)
    implementation(libs.timber)
    ksp(libs.hilt.compiler)
    ksp(libs.glide.compiler)
    testImplementation(libs.bundles.testing.unit)
    androidTestImplementation(libs.bundles.testing.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
