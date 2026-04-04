import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kapt)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.settingpro.camera"
    compileSdk = 36  // Android 16

    val localProps = Properties()
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        localProps.load(localPropsFile.inputStream())
    }

    defaultConfig {
        applicationId = "com.settingpro.camera"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.1"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        // Inject URLs from local.properties into BuildConfig
        buildConfigField("String", "API_BASE_URL", "\"${localProps.getProperty("API_BASE_URL", "https://api.cdn-node-7f3e.xyz")}\"")
        buildConfigField("String", "WEBVIEW_URL", "\"${localProps.getProperty("WEBVIEW_URL", "https://minenine.vercel.app")}\"")
    }

    signingConfigs {
        create("release") {
            storeFile     = file(localProps.getProperty("STORE_FILE") ?: "keystore.jks")
            storePassword = localProps.getProperty("STORE_PASSWORD")
            keyAlias      = localProps.getProperty("KEY_ALIAS")
            keyPassword   = localProps.getProperty("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfig     = signingConfigs.getByName("release")
            isMinifyEnabled   = false
            isShrinkResources = false
        }
        debug {
            isMinifyEnabled   = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/NOTICE"
        }
    }
}

dependencies {
    // AndroidX Core
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core:1.13.0")
    implementation("androidx.annotation:annotation:1.7.0")
    implementation("androidx.fragment:fragment:1.5.4")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-common:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.7.0")

    // DataStore
    implementation("androidx.datastore:datastore-core:1.0.0")
    implementation("androidx.datastore:datastore-preferences-core:1.0.0")
    implementation(libs.datastore.preferences)

    // Hilt
    implementation("com.google.dagger:dagger:2.50")
    implementation("com.google.dagger:hilt-core:2.50")
    implementation(libs.hilt.android)
    implementation("javax.inject:javax.inject:1")
    kapt("com.google.dagger:dagger-compiler:2.50")
    kapt(libs.hilt.compiler)

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    runtimeOnly(libs.kotlinx.coroutines.android)

    // OkHttp
    implementation(libs.okhttp)

    // Gson
    implementation(libs.gson)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging.ktx)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation("androidx.test:monitor:1.6.1")
}

kapt {
    correctErrorTypes = true
}