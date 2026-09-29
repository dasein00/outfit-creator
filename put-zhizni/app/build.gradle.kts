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
        versionCode = 2
        versionName = "2.0"
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
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.health.connect:connect-client:1.1.0-alpha07")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
