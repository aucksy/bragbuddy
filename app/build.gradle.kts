import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Release signing is sourced from a local `keystore.properties` (never committed) for local builds,
// or from environment variables for CI. If neither is present the release build is simply left
// unsigned, so the project still configures and builds for everyone.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) FileInputStream(keystorePropertiesFile).use { load(it) }
}
val signingValue: (String, String) -> String? = { propKey, envKey ->
    // Treat blank env vars (an unset CI secret renders as "") as absent, so the release simply
    // builds unsigned instead of failing on a half-configured signing block.
    (keystoreProperties.getProperty(propKey) ?: System.getenv(envKey))?.takeIf { it.isNotBlank() }
}
val releaseStoreFilePath: String? = signingValue("storeFile", "KEYSTORE_FILE")
val releaseStorePassword: String? = signingValue("storePassword", "KEYSTORE_PASSWORD")
// Alias + key password are read ONLY from a local keystore.properties (never from CI env), and
// otherwise follow the project keystore's convention: alias "bragbuddy", key password == store
// password. This keeps CI signing dependent on just KEYSTORE_FILE + KEYSTORE_PASSWORD.
val keystorePropOnly: (String) -> String? = { key -> keystoreProperties.getProperty(key)?.takeIf { it.isNotBlank() } }
val releaseKeyAlias: String = keystorePropOnly("keyAlias") ?: "bragbuddy"
val releaseKeyPassword: String? = keystorePropOnly("keyPassword") ?: releaseStorePassword
val hasReleaseSigning: Boolean = releaseStoreFilePath != null && releaseStorePassword != null

android {
    namespace = "com.bragbuddy.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bragbuddy.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 30
        versionName = "0.26.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                // v1+v2 keeps compatibility across all supported API levels.
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // R8/resource shrinking is off for now: tuning its keep rules needs a local build
            // toolchain, but this project builds only in CI. The release APK is still fully signed
            // and shippable; minification can be re-enabled later once the rules are verified.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // Core / Lifecycle / Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Room (local-first record store — the durable raw log)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore (settings / prefs)
    implementation(libs.androidx.datastore.preferences)

    // WorkManager (daily reminder scheduling)
    implementation(libs.androidx.work.runtime.ktx)

    // JSON (AI response parsing — used from Phase 2)
    implementation(libs.kotlinx.serialization.json)

    // HTTP (cloud Whisper transcription; OpenRouter LLM later)
    implementation(libs.okhttp)

    // Google Sign-In (Phase 6 Drive backup — Drive v3 REST hit directly with the OAuth token)
    implementation(libs.play.services.auth)

    // Unit testing
    testImplementation(libs.junit)
    testImplementation(libs.json) // real org.json so BackupCodec round-trips on the JVM
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.google.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.androidx.room.testing)

    // Instrumentation testing
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
