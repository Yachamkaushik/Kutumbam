plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.kutumbam.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kutumbam.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    packaging {
        // The NPU backend dlopen()s the QNN libraries from nativeLibraryDir, so they must be extracted to disk.
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coroutines.android)
    implementation(libs.litertlm)
    implementation(libs.qnn.runtime)
    implementation(libs.mlkit.text)

    testImplementation(libs.junit)
}
