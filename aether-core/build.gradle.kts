plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace   = "com.aether.core"
    compileSdk  = 35
    defaultConfig { minSdk = 23 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    api("androidx.compose.ui:ui")
    api("androidx.compose.material3:material3")
    api("androidx.compose.material:material-icons-extended")
    api("androidx.compose.animation:animation")
    api("androidx.compose.ui:ui-tooling-preview")
    api("androidx.navigation:navigation-compose:2.8.5")
    api("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    api("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    api("androidx.activity:activity-compose:1.9.3")
    api("androidx.core:core-ktx:1.15.0")
    api("androidx.core:core-splashscreen:1.0.1")
    api("androidx.datastore:datastore-preferences:1.1.1")
    api("androidx.biometric:biometric:1.1.0")
    api("androidx.security:security-crypto:1.1.0-alpha06")
    api("io.coil-kt:coil-compose:2.7.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
