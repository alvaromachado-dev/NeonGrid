import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Cada execução do GitHub Actions vira uma versão nova (1, 2, 3...)
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "dev.alvaro.neongrid"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.alvaro.neongrid"
        minSdk = 26
        targetSdk = 36
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    // Chave fixa: assim cada APK novo instala POR CIMA do anterior,
    // sem perder os apps fixados e as configurações.
    signingConfigs {
        create("neon") {
            storeFile = rootProject.file("keystore/neongrid.jks")
            storePassword = "neongrid"
            keyAlias = "neongrid"
            keyPassword = "neongrid"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("neon")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("neon")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
}
