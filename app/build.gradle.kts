import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kapt)
}

android {
    namespace = "com.cornspace.aichat"
    compileSdk = 36

    val localProps = Properties()
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        localProps.load(localPropsFile.inputStream())
    }

    defaultConfig {
        applicationId = "com.cornspace.aichat"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.1"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        // Inject URLs from local.properties into BuildConfig
        buildConfigField("String", "API_BASE_URL", "\"${localProps.getProperty("API_BASE_URL", "https://api.cattysms.shop")}\"")
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
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation("androidx.test:monitor:1.6.1")
}

kapt {
    correctErrorTypes = true
}