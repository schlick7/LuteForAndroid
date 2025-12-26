import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("androidx.navigation.safeargs.kotlin")
}

apply(from = "version.gradle.kts")

// Load local properties for sensitive data
val localProperties = Properties()
val localPropertiesFile = rootProject.file("gradle.properties.local")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

android {
    namespace = "com.example.luteforandroidv2"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.luteforandroidv2"
        minSdk = 26
        targetSdk = 33
        versionCode = 1106 // Using the patch number from strings.xml as versionCode
        versionName = "0.5.33" // Matching the version in strings.xml

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        register("release") {
            // Try to load from local properties first, then fall back to project properties
            storeFile = file(localProperties.getProperty("RELEASE_STORE_FILE") ?: project.properties["RELEASE_STORE_FILE"] ?: "my-release-key.keystore")
            storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD") ?: project.properties["RELEASE_STORE_PASSWORD"] as String?
            keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS") ?: project.properties["RELEASE_KEY_ALIAS"] as String? ?: "luteapp"
            keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD") ?: project.properties["RELEASE_KEY_PASSWORD"] as String?
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }
    buildFeatures { viewBinding = true }

    lint { disable += "WebViewLayout" }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.0")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("org.jsoup:jsoup:1.15.3")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
