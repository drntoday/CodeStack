plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.somnath.codestack"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.somnath.codestack"
        minSdk = 24 // Works on your Vivo Y30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
}

dependencies {
    // The "Chef" - Google Gemini AI
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
    
    // The "Interface" - Jetpack Compose
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
}
