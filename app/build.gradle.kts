plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Note: In a professional app, you'd add id("com.google.gms.google-services"), 
    // but for this simple build, the standalone SDK works fine.
}

android {
    namespace = "com.somnath.codestack"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.somnath.codestack"
        minSdk = 24 
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
}

dependencies {
    // Import the BoM for the Firebase platform
    implementation(platform("com.google.firebase:firebase-bom:34.11.0"))
    
    // The NEW library that replaces the old 0.9.0 version
    implementation("com.google.firebase:firebase-vertexai")

    // UI Essentials
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
}
