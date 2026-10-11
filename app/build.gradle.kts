plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services) apply false
}


import java.util.Properties

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}
val openRouterApiKey: String = localProperties.getProperty("openrouter.api.key", "")

// Ключ подписи release: файл keystore.properties в корне проекта (в git не хранится).
// Если файла нет, release собирается без подписи (unsigned).
val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
val hasReleaseKeystore = keystorePropertiesFile.exists()
if (hasReleaseKeystore) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

// Включаем google-services только если пользователь положил свой google-services.json
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// `org.jetbrains.kotlin.android` подтягивается автоматически AGP 9.x + Kotlin 2.0+.
// Отдельный `kotlin-android` плагин НЕ нужен, иначе конфликт дублирующих расширений.

android {
    namespace = "com.vectorheart.asuna"
    compileSdk {
        version = release(35)
    }

    defaultConfig {
        applicationId = "com.vectorheart.asuna.liquidglass"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Только arm64-v8a: целевой телефон — Xiaomi Mi 11T. Без этого фильтра
        // в APK попадает x86_64 из litertlm-android и tasks-genai.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        buildConfigField("String", "OPENROUTER_API_KEY", "\"${openRouterApiKey}\"")
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKeystore) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile", "release.jks"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/INDEX.LIST",
                "/META-INF/io.netty.versions.properties",
                "/META-INF/DEPENDENCIES"
            )
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Kotlinx
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Networking
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)

    // DataStore + EncryptedSharedPreferences
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    // WebView (WebViewAssetLoader для безопасной загрузки assets/avatar/)
    implementation(libs.androidx.webkit)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Media3 (ExoPlayer) для локальной аудиотеки
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // STT (Vosk + Android SpeechRecognizer fallback)
    implementation(libs.vosk.android)
    implementation(libs.tasks.genai)
    // LiteRT-LM — движок для моделей .litertlm (Gemma 4, Bonsai).
    // MediaPipe tasks-genai .task-файлы открывает, .litertlm — нет (ТЗ, Фаза 1).
    implementation(libs.litertlm.android)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.coroutines.play.services)

    // Google Sign-In + Calendar
    implementation(libs.play.services.auth)
    implementation(libs.google.http.client)
    implementation(libs.google.api.services.calendar)

    // Permissions
    implementation(libs.accompanist.permissions)

    // Image loading
    implementation(libs.coil.compose)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
