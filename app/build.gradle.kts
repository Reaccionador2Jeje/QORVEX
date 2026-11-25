plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.atdev.qorvex"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.atdev.qorvex"
        minSdk = 26
        targetSdk = 35
        // CI can stamp preview tags without committing a new Gradle version for each release.
        versionCode = providers.gradleProperty("qorvexVersionCode").orElse("1").get().toInt()
        versionName = providers.gradleProperty("qorvexVersionName").orElse("0.4.0-dev").get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    buildFeatures { compose = true }

    packaging {
        jniLibs {
            // QEMU is an Android-native executable packaged under the .so name
            // so PackageManager extracts it into nativeLibraryDir for execve.
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    // Provide a real org.json implementation for local JVM unit tests.
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
