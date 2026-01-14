// Apply the KSP plugin for annotation processing
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.ksp) // Add this line for KSP
}

android {
    namespace = "com.ap.expenses.manager"
    compileSdk = 36 // Updated to Android 15 stable

    defaultConfig {
        applicationId = "com.ap.expenses.manager"
        minSdk = 26
        targetSdk = 36 // Updated to Android 15 stable
        versionCode = 9
        versionName = "6.1.1(02-11-25)" // Standard version name format

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        viewBinding = true
    }

    // **IMPORTANT:** This block is required to prevent build errors from the Drive/POI libraries.
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1,DEPENDENCIES}"
        }
    }
}

dependencies {
    // Default dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.swiperefreshlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Custom dependencies from the version catalog
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.biometric)
    implementation(libs.apache.poi)
    implementation(libs.apache.poi.ooxml)
    implementation(libs.oneui.icons)

    implementation("com.google.code.gson:gson:2.13.2")
    implementation("com.google.android.material:material:1.13.0")
    implementation("io.github.oneuiproject:icons:1.1.0")
    // Core Material 3 library (includes Expressive components and theming)
    implementation("androidx.compose.material3:material3:1.4.0")

    // For access to the full suite of Material Symbols/Icons in code
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    implementation("io.github.yanndroid:oneui:2.4.1")




}