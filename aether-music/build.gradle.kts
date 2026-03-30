plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.aether.music"; compileSdk = 35
    defaultConfig { applicationId = "com.aether.music"; minSdk = 23; targetSdk = 35; versionCode = 1; versionName = "1.0.0" }
    buildTypes { release { isMinifyEnabled = true; isShrinkResources = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17"; freeCompilerArgs += "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api" }
    buildFeatures { compose = true }
}
dependencies {
    implementation(project(":aether-core"))
    // MediaStyle notification (notification lecteur standard Android)
    implementation("androidx.media:media:1.7.0")

    testImplementation("junit:junit:4.13.2")
}
