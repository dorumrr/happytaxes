import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

// Load keystore.properties if it exists (standard Android approach)
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

// Disable baseline profiles for reproducible builds (F-Droid compatibility)
tasks.whenTaskAdded {
    if (name.contains("compileReleaseArtProfile") ||
        name.contains("mergeReleaseArtProfile") ||
        name.contains("ArtProfile")) {
        enabled = false
    }
}

android {
    namespace = "io.github.dorumrr.happytaxes"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.dorumrr.happytaxes"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Room schema export
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    // Disable baseline profiles for reproducible builds
    androidResources {
        ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:.*:!CVS:!thumbs.db:!picasa.ini:!*~:baseline.prof:baseline.profm"
    }

    // Disable dependency metadata for reproducible builds
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                val storeFilePath = keystoreProperties.getProperty("storeFile")
                if (storeFilePath != null && storeFilePath.isNotEmpty()) {
                    // Handle both absolute and relative paths
                    storeFile = if (File(storeFilePath).isAbsolute) {
                        File(storeFilePath)
                    } else {
                        rootProject.file(storeFilePath)
                    }
                    storePassword = keystoreProperties.getProperty("storePassword")
                    keyAlias = keystoreProperties.getProperty("keyAlias") ?: "happytaxes-release-key"
                    keyPassword = keystoreProperties.getProperty("keyPassword")
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Disable VCS info for reproducible builds (F-Droid compatibility)
            vcsInfo.include = false

            // Sign with production keystore if keystore.properties exists
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.findByName("release")
            } else {
                null
            }
        }
        debug {
            isMinifyEnabled = false
            // Debug builds use the default debug keystore automatically
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            // Standard Android license files
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Additional excludes for reproducible builds
            excludes += "/META-INF/versions/**"
            excludes += "**/kotlin_builtins"
            excludes += "META-INF/androidx.profileinstaller_profileinstaller.version"
            pickFirsts += listOf("META-INF/INDEX.LIST", "META-INF/io.netty.versions.properties")
        }
    }

    // Rename APK output files to include version
    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val versionName = defaultConfig.versionName ?: "unknown"
            output.outputFileName = if (name.contains("release")) {
                "happytaxes-v${versionName}.apk"
            } else {
                "happytaxes-v${versionName}-debug.apk"
            }
        }
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    // Paging
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    // SQLite (standard, no encryption)
    implementation(libs.androidx.sqlite)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)

    // JSON serialization (for Room converters and data parsing)
    implementation(libs.kotlinx.serialization.json)

    // Tesseract OCR
    implementation(libs.tesseract4android)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Image Compression
    implementation("id.zelory:compressor:3.0.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Image Loading (for Task 3: Receipt Viewer)
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Charts
    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)
    implementation(libs.vico.core)

    // CSV
    implementation(libs.commons.csv)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
