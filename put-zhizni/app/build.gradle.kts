plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ru.putzhizni.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "ru.putzhizni.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // Постоянный ключ подписи: обновления ставятся поверх без потери данных.
    signingConfigs {
        create("app") {
            storeFile = file("putzhizni.jks")
            storePassword = "putzhizni"
            keyAlias = "putzhizni"
            keyPassword = "putzhizni"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("app")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("app")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}
