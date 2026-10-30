plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.dasein.poryadok"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dasein.poryadok"
        minSdk = 26
        targetSdk = 35
        val run = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionCode = run
        versionName = "1.0.$run"
        // Телефоны — ARM; x86_64 нужен только эмулятору автотеста. 32-битный x86 (старые эмуляторы) не собираем:
        // минус ~30 МБ к APK.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: "poryadok.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "poryadok"
            keyAlias = System.getenv("KEY_ALIAS") ?: "poryadok"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "poryadok"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
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
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // Встроенные библиотеки (распознавание текста, перевод) — сжатыми: APK скачивается заметно быстрее
        // и реже «бьётся» при загрузке; при установке Android распаковывает их сам.
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Сжатие видео (рецепты, тренировки): перекодирование в 480p H.264 + AAC.
    implementation("androidx.media3:media3-transformer:1.5.1")
    implementation("androidx.media3:media3-effect:1.5.1")
    implementation("androidx.media3:media3-common:1.5.1")
    // Переводчик фраз и предложений без интернета (модель EN↔RU скачивается один раз).
    implementation("com.google.mlkit:translate:17.0.3")
    // Распознавание цифр со скриншотов приложений весов (Fitdays и др.) — на телефоне, без интернета.
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // Вырезание вещи с фото с любым фоном (модель Google Play services, на телефоне).
    implementation("com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1")

    // Встроенный браузер без заголовка X-Requested-With (Instagram отказывает страницам с ним).
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.health.connect:connect-client:1.1.0-alpha07")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // Текст PDF-выписок банков (СберБанк Онлайн и др.) — разбор на телефоне.
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    // Профиль предкомпиляции (baseline-prof.txt): код приложения компилируется заранее — быстрее запуск и прокрутка.
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")

    testImplementation("junit:junit:4.13.2")
}

// Список иконок, которые стоят в коде приложения: встроенный набор нельзя удалить, если его иконка где-то используется.
val usedIconsDir = layout.buildDirectory.dir("generated/usedIcons")
val genUsedIcons = tasks.register("genUsedIcons") {
    val src = fileTree("src/main/java") { include("**/*.kt") }
    val out = usedIconsDir
    inputs.files(src)
    outputs.dir(out)
    doLast {
        val texts = src.files.map { it.readText() }
        val ic = HashMap<String, String>()
        texts.forEach { t -> Regex("""val (\w+) = R\.drawable\.ic_(\w+)""").findAll(t).forEach { ic[it.groupValues[1]] = it.groupValues[2] } }
        val keys = sortedSetOf<String>()
        val lit = Regex(""""(ui:[a-z_0-9]+|dish/[a-z0-9]+/\d+|[a-z0-9]+/\d\d)"""")
        texts.forEach { t ->
            lit.findAll(t).forEach { keys += it.groupValues[1] }
            Regex("""R\.drawable\.ic_(\w+)""").findAll(t).forEach { keys += "ui:" + it.groupValues[1] }
            Regex("""\bIc\.(\w+)""").findAll(t).forEach { m -> ic[m.groupValues[1]]?.let { keys += "ui:$it" } }
        }
        val d = out.get().asFile.resolve("icons")
        d.mkdirs()
        d.resolve("used_in_code.txt").writeText(keys.joinToString("\n"))
    }
}
android.sourceSets.getByName("main").assets.srcDir(usedIconsDir)
tasks.named("preBuild") { dependsOn(genUsedIcons) }
