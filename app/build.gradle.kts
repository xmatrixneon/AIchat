import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kapt)
    alias(libs.plugins.compose.compiler)
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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
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
        compose = true
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
    // ❌ REMOVED: libs.androidx.core.ktx       → unused
    // ❌ REMOVED: libs.compose.ui.tooling.preview → unused
    // ❌ REMOVED: libs.lifecycle.runtime.ktx    → unused
    // ❌ REMOVED: libs.androidx.espresso.core   → unused

    implementation("androidx.appcompat:appcompat:1.7.0")

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation("androidx.compose.material:material-icons-extended")

    // ✅ transitive deps now declared directly (from report)
    implementation("androidx.activity:activity:1.8.2")
    implementation("androidx.annotation:annotation:1.7.0")
    implementation("androidx.compose.foundation:foundation:1.6.1")
    implementation("androidx.compose.foundation:foundation-layout:1.6.1")
    implementation("androidx.compose.material:material-icons-core:1.6.1")
    implementation("androidx.compose.runtime:runtime:1.6.1")
    implementation("androidx.compose.ui:ui-text:1.6.1")
    implementation("androidx.compose.ui:ui-unit:1.6.1")
    implementation("androidx.core:core:1.13.0")
    implementation("androidx.datastore:datastore-core:1.0.0")
    implementation("androidx.datastore:datastore-preferences-core:1.0.0")
    implementation("androidx.fragment:fragment:1.5.4")
    implementation("androidx.lifecycle:lifecycle-common:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.7.0")
    implementation("com.google.dagger:dagger:2.50")
    implementation("com.google.dagger:hilt-core:2.50")
    implementation("javax.inject:javax.inject:1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    kapt("com.google.dagger:dagger-compiler:2.50")

    // Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // OkHttp
    implementation(libs.okhttp)

    // ✅ changed: implementation → runtimeOnly (from report)
    runtimeOnly(libs.kotlinx.coroutines.android)

    // DataStore
    implementation(libs.datastore.preferences)

    // Gson
    implementation(libs.gson)

    // Lifecycle ViewModel
    implementation(libs.lifecycle.viewmodel.compose)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation("androidx.test:monitor:1.6.1")    // ✅ added from report
    androidTestImplementation(platform(libs.compose.bom))
    androidTestRuntimeOnly(libs.compose.ui.test.manifest)       // ✅ was androidTestImplementation
    debugImplementation(libs.compose.ui.tooling)
    debugRuntimeOnly(libs.compose.ui.test.manifest)             // ✅ was debugImplementation
}

kapt {
    correctErrorTypes = true
}