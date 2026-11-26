plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.pixellauncher"
    compileSdk = 34

    signingConfigs {
        // Постоянный ключ: каждая сборка подписана одинаково, поэтому APK обновляется поверх старого
        create("fixed") {
            storeFile = file("launcher.keystore")
            storePassword = "launcher123"
            keyAlias = "launcher"
            keyPassword = "launcher123"
        }
    }

    defaultConfig {
        applicationId = "com.nocebo.launcher"
        minSdk = 26
        targetSdk = 34
        // растёт с каждой сборкой в GitHub Actions
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0.3"
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("fixed") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}
